package no.difi.meldingsutveksling.nhn.adapter.mshconfiguration

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mu.KotlinLogging
import no.difi.meldingsutveksling.nhn.adapter.errors.MshConfigurationNotFoundException
import no.difi.meldingsutveksling.nhn.adapter.integration.adresseregisteret.AdresseregisteretService
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.MshService
import no.difi.meldingsutveksling.nhn.adapter.logging.loggableMessage
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.difi.meldingsutveksling.nhn.adapter.security.isMultiTenant
import no.difi.meldingsutveksling.nhn.adapter.security.parentOrganizationNumber
import no.ks.fiks.nhn.msh.HttpException
import no.ks.fiks.nhn.msh.MshConfiguration

private val logger = KotlinLogging.logger {}

private const val NOT_FOUND = 404

/** How long a HER-id is known to be registered before the database is asked again, e.g. after a delete elsewhere. */
private val REGISTERED_FOR = Duration.ofHours(1)

/** How long a HER-id whose registration failed is left before it is tried again. */
private val FAILED_FOR = Duration.ofMinutes(5)

/** The use of a HER-id is recorded at most this often, so that a busy HER-id does not write on every message. */
private val RECORD_USE_EVERY = Duration.ofMinutes(1)

private const val MAX_CACHED_HER_IDS = 100_000L

/**
 * The MSH configurations of the HER-ids the adapter handles messages for. MSH requires one of every HER-id: without
 * it, MSH may reject requests for the HER-id and deletes its notifications after 4 days.
 *
 * A HER-id is registered the first time a client uses it, or by an administrator. The configurations are stored, so
 * that the administrator can see which HER-ids are in use, and so that they can be registered in MSH again at startup,
 * as NHN recommends.
 *
 * Messaging must not depend on the database or on the registration: what is done for a client's request never fails
 * it. A HER-id known to be registered needs no database call, and the use of a HER-id is recorded in the background.
 */
class MshConfigurationService(
    private val repository: MshConfigurationRepository,
    private val mshService: MshService,
    private val adresseregisteretService: AdresseregisteretService,
    private val clock: Clock = Clock.systemUTC(),
    private val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val registered: Cache<Int, Unit> = cache(REGISTERED_FOR)
    private val failed: Cache<Int, Unit> = cache(FAILED_FOR)
    private val recorded: Cache<Int, Unit> = cache(RECORD_USE_EVERY)

    /** HER-ids the registration at startup failed for, which are registered again on their next use. */
    private val unregistered: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    suspend fun list(): List<StoredMshConfiguration> = repository.findAll()

    /**
     * Registers the configuration of the HER-id in MSH, and stores it. A configuration already registered is replaced,
     * in the HelseID tenant it was registered with.
     *
     * @throws no.difi.meldingsutveksling.nhn.adapter.errors.HerIdNotFoundException if the HER-id is not in
     *   Adresseregisteret
     */
    suspend fun register(herId: Int, clientLocked: Boolean): StoredMshConfiguration {
        val organizationNumber = adresseregisteretService.lookupByHerId(herId).parentOrganizationNumber
        val previous = repository.find(herId)
        val multiTenant = previous?.multiTenant ?: false
        val now = clock.instant()
        // Stored before it is registered, so that a first use at the same time registers the same,
        // see registerForClient
        repository.save(herId, organizationNumber, multiTenant, clientLocked, now)
        try {
            mshService.setConfigurations(
                listOf(MshConfiguration(herId, clientLocked)),
                organizationNumber,
                multiTenant,
            )
        } catch (e: Exception) {
            restore(herId, previous)
            throw e
        }
        markRegistered(herId)
        logger.info { "Registered the MSH configuration of HER-id $herId, clientLocked = $clientLocked" }
        return StoredMshConfiguration(
            herId = herId,
            organizationNumber = organizationNumber,
            multiTenant = multiTenant,
            clientLocked = clientLocked,
            createdAt = previous?.createdAt ?: now,
            updatedAt = now,
            lastUsed = previous?.lastUsed,
        )
    }

    /**
     * Deletes the configuration of the HER-id from MSH and the database. One that MSH does not have, is deleted from
     * the database. A HER-id still in use is registered again the next time a client uses it.
     *
     * @throws MshConfigurationNotFoundException if the adapter has no configuration of the HER-id
     */
    suspend fun delete(herId: Int) {
        val configuration = repository.find(herId) ?: throw MshConfigurationNotFoundException(herId)
        try {
            mshService.deleteConfiguration(herId, configuration.organizationNumber, configuration.multiTenant)
        } catch (e: HttpException) {
            if (e.status != NOT_FOUND) throw e
            logger.info { "MSH had no configuration of HER-id $herId" }
        }
        repository.delete(herId)
        registered.invalidate(herId)
        logger.info { "Deleted the MSH configuration of HER-id $herId" }
    }

    /**
     * Registers the HER-id, which the client is about to use, unless it is registered. Never fails: the client's
     * request is made either way, and a failed registration is tried again later.
     */
    suspend fun ensureRegistered(herId: Int, clientContext: ClientContext) {
        if (isKnown(herId)) return
        bestEffort(herId) {
            val configuration = repository.find(herId)
            if (configuration != null && herId !in unregistered) {
                registered.put(herId, Unit)
            } else {
                registerForClient(herId, clientContext, configuration)
            }
        }
    }

    /**
     * Records in the background that a message was sent or received for the HER-id, and registers it unless it is
     * registered. Returns at once, as the message is sent or received already.
     */
    fun recordUse(herId: Int, clientContext: ClientContext) {
        if (recorded.asMap().putIfAbsent(herId, Unit) != null) return
        backgroundScope.launch {
            bestEffort(herId) {
                if (!repository.updateLastUsed(herId, clock.instant()) && failed.getIfPresent(herId) == null) {
                    registerForClient(herId, clientContext, null)
                    repository.updateLastUsed(herId, clock.instant())
                }
            }
        }
    }

    /**
     * Registers every stored configuration in MSH again, a request per HelseID tenant. When one fails, each HER-id of
     * it is tried on its own, so that one HER-id MSH rejects does not keep the others from being registered. A HER-id
     * that still fails is registered again on its next use.
     */
    suspend fun registerAllInMsh() {
        try {
            repository
                .findAll()
                .groupBy { it.organizationNumber to it.multiTenant }
                .forEach { (tenant, configurations) ->
                    val (organizationNumber, multiTenant) = tenant
                    try {
                        setInMsh(configurations, organizationNumber, multiTenant)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logFailure("register the MSH configurations of organization $organizationNumber again", e)
                        configurations.forEach { registerAgain(it) }
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Could not register the MSH configurations again" }
        }
    }

    private suspend fun registerAgain(configuration: StoredMshConfiguration) {
        try {
            setInMsh(listOf(configuration), configuration.organizationNumber, configuration.multiTenant)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            unregistered.add(configuration.herId)
            logFailure("register the MSH configuration of HER-id ${configuration.herId} again", e)
        }
    }

    private suspend fun setInMsh(
        configurations: List<StoredMshConfiguration>,
        organizationNumber: String,
        multiTenant: Boolean,
    ) {
        mshService.setConfigurations(
            configurations.map { MshConfiguration(it.herId, it.clientLocked) },
            organizationNumber,
            multiTenant,
        )
        configurations.forEach { markRegistered(it.herId) }
        logger.info { "Registered the MSH configurations of the HER-ids ${configurations.map { it.herId }} again" }
    }

    /**
     * Registers the HER-id with the HelseID tenant of the client, which has access to it, and so acts for the
     * organization that owns it. A configuration an administrator stored at the same time wins: it is registered again
     * if it differs.
     */
    private suspend fun registerForClient(herId: Int, clientContext: ClientContext, stored: StoredMshConfiguration?) {
        val clientLocked = stored?.clientLocked ?: false
        mshService.setConfigurations(listOf(MshConfiguration(herId, clientLocked)), clientContext)
        val organizationNumber = clientContext.onBehalfOfOrgNumber
        val multiTenant = clientContext.isMultiTenant()
        if (stored == null) {
            if (!repository.saveIfAbsent(herId, organizationNumber, multiTenant, clock.instant())) {
                repository
                    .find(herId)
                    ?.takeIf { it.clientLocked != clientLocked }
                    ?.let { setInMsh(listOf(it), it.organizationNumber, it.multiTenant) }
            }
        } else if (stored.organizationNumber != organizationNumber || stored.multiTenant != multiTenant) {
            // The tenant the registration at startup failed with: the client's is the one that
            // works
            repository.save(herId, organizationNumber, multiTenant, clientLocked, clock.instant())
        }
        markRegistered(herId)
        logger.info { "Registered the MSH configuration of HER-id $herId on its use" }
    }

    private suspend fun restore(herId: Int, previous: StoredMshConfiguration?) {
        try {
            if (previous == null) {
                repository.delete(herId)
            } else {
                with(previous) { repository.save(herId, organizationNumber, multiTenant, clientLocked, updatedAt) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Could not restore the stored MSH configuration of HER-id $herId" }
        }
    }

    /** Forgets what is known of the HER-ids, as after a restart. For tests that share one instance. */
    internal fun clearCaches() {
        listOf(registered, failed, recorded).forEach { it.invalidateAll() }
        unregistered.clear()
    }

    private fun isKnown(herId: Int): Boolean =
        registered.getIfPresent(herId) != null || failed.getIfPresent(herId) != null

    private fun markRegistered(herId: Int) {
        unregistered.remove(herId)
        failed.invalidate(herId)
        registered.put(herId, Unit)
    }

    private suspend fun bestEffort(herId: Int, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed.put(herId, Unit)
            logFailure("register or record the use of HER-id $herId in its MSH configuration", e)
        }
    }

    /** MSH rejecting the request, e.g. a HER-id locked by another client, needs no stack trace. */
    private fun logFailure(what: String, e: Exception) {
        if (e is HttpException && e.status in 400..499) {
            logger.warn { "Could not $what: ${e.loggableMessage()}" }
        } else {
            logger.warn(e) { "Could not $what" }
        }
    }
}

private fun <K : Any> cache(expireAfterWrite: Duration): Cache<K, Unit> =
    Caffeine.newBuilder().expireAfterWrite(expireAfterWrite).maximumSize(MAX_CACHED_HER_IDS).build()
