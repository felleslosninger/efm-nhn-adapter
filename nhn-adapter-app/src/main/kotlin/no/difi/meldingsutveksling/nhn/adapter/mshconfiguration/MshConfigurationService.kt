package no.difi.meldingsutveksling.nhn.adapter.mshconfiguration

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import mu.KotlinLogging
import no.difi.meldingsutveksling.nhn.adapter.errors.DatabaseUnavailableException
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
private const val REQUEST_TIMEOUT = 408
private const val TOO_MANY_REQUESTS = 429

/** How often the stored lock is registered again while an administrator keeps changing it, before giving up. */
private const val MAX_RECONCILE_ROUNDS = 3

/**
 * How long a HER-id is known to be registered before the database is asked again, e.g. after a delete elsewhere: an
 * hour, give or take a quarter, so that the HER-ids registered at startup do not all expire at once.
 */
private val REGISTERED_FOR = Duration.ofMinutes(45)
private val REGISTERED_SPREAD = Duration.ofMinutes(30)

/** How long a HER-id whose registration failed is left before it is tried again. */
private val FAILED_FOR = Duration.ofMinutes(5)

/** How long the database is left alone after it failed, so that one failure answers for all HER-ids. */
private val DATABASE_DOWN_FOR = Duration.ofSeconds(30)

/** The use of a HER-id is recorded at most this often, so that a busy HER-id does not write on every message. */
private val RECORD_USE_EVERY = Duration.ofMinutes(1)

/** The longest a client's request waits for the registration of its HER-id. The rest is done in the background. */
private val MAX_WAIT = Duration.ofSeconds(2)

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
 * it, and holds it up for at most [MAX_WAIT]. A HER-id known to be registered needs no database call, a database that
 * failed is left alone for a while, and the use of a HER-id is recorded in the background.
 *
 * What an administrator stored wins over what is registered at the same time on a client's use or at startup: after
 * each of these, the stored lock is read again and registered if it differs.
 */
class MshConfigurationService(
    private val repository: MshConfigurationRepository,
    private val mshService: MshService,
    private val adresseregisteretService: AdresseregisteretService,
    private val clock: Clock = Clock.systemUTC(),
    private val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /** Whether the database is migrated, see [DatabaseMigration]. Until then, it is left alone on messaging. */
    private val isDatabaseMigrated: () -> Boolean = { true },
) {
    private val registered: Cache<Int, Unit> =
        Caffeine.newBuilder()
            .expireAfter(Expiry.creating<Int, Unit> { _, _ -> REGISTERED_FOR + randomSpread() })
            .maximumSize(MAX_CACHED_HER_IDS)
            .build()
    private val failed: Cache<Int, Unit> = cache(FAILED_FOR)
    private val recorded: Cache<Int, Unit> = cache(RECORD_USE_EVERY)

    /** HER-ids whose registration may not have reached MSH, which are registered again on their next use. */
    private val unregistered: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    /** The registrations in progress in the background, at most one per HER-id. */
    private val inProgress = ConcurrentHashMap<Int, Job>()

    @Volatile private var databaseDownUntil: Instant = Instant.EPOCH

    suspend fun list(): List<StoredMshConfiguration> {
        requireMigrated()
        return repository.findAll()
    }

    /**
     * Registers the configuration of the HER-id in MSH, and stores it. A configuration already registered is replaced,
     * in the HelseID tenant it was registered with.
     *
     * @throws no.difi.meldingsutveksling.nhn.adapter.errors.HerIdNotFoundException if the HER-id is not in
     *   Adresseregisteret
     */
    suspend fun register(herId: Int, clientLocked: Boolean): StoredMshConfiguration {
        requireMigrated()
        val organizationNumber = adresseregisteretService.lookupByHerId(herId).parentOrganizationNumber
        val previous = repository.find(herId)
        val multiTenant = previous?.multiTenant ?: false
        // As stored, so that the restore below can tell whether the row is still this one
        val now = clock.instant().truncatedTo(ChronoUnit.MICROS)
        // Stored before it is registered, so that a client's use at the same time registers the
        // same
        repository.save(herId, organizationNumber, multiTenant, clientLocked, now)
        try {
            mshService.setConfigurations(
                listOf(MshConfiguration(herId, clientLocked)),
                organizationNumber,
                multiTenant,
            )
        } catch (e: Exception) {
            // Also when the administrator gave up, which cancels this
            withContext(NonCancellable) { restore(herId, previous, now) }
            throw e
        }
        markRegistered(herId)
        logger.info { "Registered the MSH configuration of HER-id $herId, clientLocked = $clientLocked" }
        // Another administrator may have stored another lock meanwhile
        reconcile(herId, clientLocked)
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
        requireMigrated()
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
     * Registers the HER-id, which the client is about to use, unless it is registered. Never fails, and waits at most
     * [MAX_WAIT]: the client's request is made either way, the registration goes on in the background, and one that
     * fails is tried again later.
     */
    suspend fun ensureRegistered(herId: Int, clientContext: ClientContext) {
        if (isKnown(herId) || isDatabaseDown()) return
        val registration =
            registration(herId) {
                val configuration = repository.find(herId)
                if (configuration != null && herId !in unregistered) {
                    registered.put(herId, Unit)
                } else {
                    registerForClient(herId, clientContext, configuration)
                }
            }
        withTimeoutOrNull(MAX_WAIT.toKotlinDuration()) { registration.join() }
    }

    /**
     * Records in the background that a message was sent or received for the HER-id, and registers it unless it is
     * registered. Returns at once, as the message is sent or received already.
     */
    fun recordUse(herId: Int, clientContext: ClientContext) {
        if (isDatabaseDown() || recorded.asMap().putIfAbsent(herId, Unit) != null) return
        backgroundScope.launch {
            bestEffort(herId) {
                val updated = repository.updateLastUsed(herId, clock.instant())
                if ((herId in unregistered || !updated) && failed.getIfPresent(herId) == null) {
                    registration(herId) { registerForClient(herId, clientContext, repository.find(herId)) }.join()
                    if (!updated) repository.updateLastUsed(herId, clock.instant())
                }
            }
        }
    }

    /**
     * The registration of the HER-id in the background: the one in progress, if any, so that many requests for a
     * HER-id at once, or while MSH is slow, register it only once.
     */
    private fun registration(herId: Int, block: suspend () -> Unit): Job {
        var created: Job? = null
        val job =
            inProgress.computeIfAbsent(herId) {
                // Started once it is in the map, so that it cannot remove itself before it is there
                backgroundScope.launch(start = CoroutineStart.LAZY) { bestEffort(herId, block) }.also { created = it }
            }
        // Once it is in the map: a job that never runs, as the scope is cancelled, is complete
        // already, and runs the
        // handler at once. Also then it is removed.
        created?.let { new -> new.invokeOnCompletion { inProgress.remove(herId, new) } }
        job.start()
        return job
    }

    /**
     * Registers every stored configuration in MSH again, a request per HelseID tenant. When MSH rejects one, each
     * HER-id of it is tried on its own, so that one HER-id MSH rejects does not keep the others from being registered.
     * A HER-id that is not registered, e.g. as MSH did not answer, is registered again on its next use.
     */
    suspend fun registerAllInMsh() {
        try {
            val configurations = repository.findAll()
            configurations
                .groupBy { it.organizationNumber to it.multiTenant }
                .forEach { (tenant, group) -> registerAgain(group, tenant.first, tenant.second) }
            reconcileAll(configurations)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Could not register the MSH configurations again" }
        }
    }

    /** An administrator may have stored another lock while they were registered, also on another instance. */
    private suspend fun reconcileAll(configurations: List<StoredMshConfiguration>) {
        val stored =
            try {
                repository.findAll().associateBy { it.herId }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "Registered the MSH configurations again, but could not check the stored locks" }
                return
            }
        configurations
            .filter { stored[it.herId]?.clientLocked.let { lock -> lock != null && lock != it.clientLocked } }
            .forEach { reconcile(it.herId, it.clientLocked) }
    }

    private suspend fun registerAgain(
        configurations: List<StoredMshConfiguration>,
        organizationNumber: String,
        multiTenant: Boolean,
    ) {
        try {
            setInMsh(configurations, organizationNumber, multiTenant)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure("register the MSH configurations of organization $organizationNumber again", e)
            if (e.isRejection() && configurations.size > 1) {
                configurations.forEach { registerAgain(listOf(it), organizationNumber, multiTenant) }
            } else {
                configurations.forEach { markUnregistered(it.herId) }
            }
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
     * organization that owns it.
     */
    private suspend fun registerForClient(herId: Int, clientContext: ClientContext, stored: StoredMshConfiguration?) {
        val clientLocked = stored?.clientLocked ?: false
        mshService.setConfigurations(listOf(MshConfiguration(herId, clientLocked)), clientContext)
        val organizationNumber = clientContext.onBehalfOfOrgNumber
        val multiTenant = clientContext.isMultiTenant()
        if (stored == null) {
            repository.saveIfAbsent(herId, organizationNumber, multiTenant, clock.instant())
        } else if (stored.organizationNumber != organizationNumber || stored.multiTenant != multiTenant) {
            // The registration at startup failed with the stored tenant: the client's is the one
            // that works
            repository.updateTenant(herId, organizationNumber, multiTenant)
        }
        markRegistered(herId)
        logger.info { "Registered the MSH configuration of HER-id $herId on its use" }
        reconcile(herId, clientLocked)
    }

    /**
     * Registers the stored configuration again as long as its lock is not the one last registered, as an administrator
     * stored another meanwhile. Never fails: a HER-id it fails for, or whose lock keeps changing, is registered again
     * on its next use.
     */
    private suspend fun reconcile(herId: Int, registeredLock: Boolean) {
        try {
            var lock = registeredLock
            repeat(MAX_RECONCILE_ROUNDS) {
                val stored = repository.find(herId) ?: return
                if (stored.clientLocked == lock) return
                setInMsh(listOf(stored), stored.organizationNumber, stored.multiTenant)
                lock = stored.clientLocked
            }
            markUnregistered(herId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            markUnregistered(herId)
            logFailure("register the stored MSH configuration of HER-id $herId", e)
        }
    }

    /**
     * Puts back the configuration stored before [storedAt], unless another one was stored since. MSH may have got the
     * configuration although the request failed, e.g. on a timeout, so the HER-id is registered again on its next use,
     * as stored.
     */
    private suspend fun restore(herId: Int, previous: StoredMshConfiguration?, storedAt: Instant) {
        try {
            val restored =
                if (previous == null) {
                    repository.deleteIfUpdatedAt(herId, storedAt)
                } else {
                    repository.restore(previous, storedAt)
                }
            if (!restored) logger.info { "Kept the MSH configuration of HER-id $herId another one stored meanwhile" }
        } catch (e: Exception) {
            logger.warn(e) { "Could not restore the stored MSH configuration of HER-id $herId" }
        } finally {
            // After the write: a client that registered the HER-id meanwhile may have cleared a
            // mark made before it
            markUnregistered(herId)
        }
    }

    /**
     * Forgets what is known of the HER-ids and the database, as after a restart. For tests that share one instance.
     */
    /** For tests. */
    internal fun registrationsInProgress(): Int = inProgress.size

    internal fun clearCaches() {
        listOf(registered, failed, recorded).forEach { it.invalidateAll() }
        unregistered.clear()
        inProgress.clear()
        databaseDownUntil = Instant.EPOCH
    }

    private fun isKnown(herId: Int): Boolean =
        registered.getIfPresent(herId) != null || failed.getIfPresent(herId) != null

    /** The table may not be there yet, which would fail as a bug rather than as a database that is down. */
    private fun requireMigrated() {
        if (!isDatabaseMigrated()) throw DatabaseUnavailableException("The database is not migrated yet")
    }

    private fun isDatabaseDown(): Boolean = !isDatabaseMigrated() || clock.instant() < databaseDownUntil

    private fun markRegistered(herId: Int) {
        unregistered.remove(herId)
        failed.invalidate(herId)
        registered.put(herId, Unit)
    }

    private fun markUnregistered(herId: Int) {
        unregistered.add(herId)
        registered.invalidate(herId)
    }

    private suspend fun bestEffort(herId: Int, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isDatabaseOutage()) {
                databaseDownUntil = clock.instant() + DATABASE_DOWN_FOR
            } else {
                failed.put(herId, Unit)
            }
            logFailure("register or record the use of HER-id $herId in its MSH configuration", e)
        }
    }

    /** MSH rejecting the request, e.g. a HER-id locked by another client, needs no stack trace. */
    private fun logFailure(what: String, e: Exception) {
        if (e.isRejection()) {
            logger.warn { "Could not $what: ${e.loggableMessage()}" }
        } else {
            logger.warn(e) { "Could not $what" }
        }
    }
}

/** MSH rejecting the request, as opposed to MSH being busy (408, 429), which another try may get through. */
private fun Exception.isRejection(): Boolean =
    this is HttpException && status in 400..499 && status != REQUEST_TIMEOUT && status != TOO_MANY_REQUESTS

private fun randomSpread(): Duration =
    Duration.ofSeconds(ThreadLocalRandom.current().nextLong(REGISTERED_SPREAD.toSeconds() + 1))

private fun <K : Any> cache(expireAfterWrite: Duration): Cache<K, Unit> =
    Caffeine.newBuilder().expireAfterWrite(expireAfterWrite).maximumSize(MAX_CACHED_HER_IDS).build()
