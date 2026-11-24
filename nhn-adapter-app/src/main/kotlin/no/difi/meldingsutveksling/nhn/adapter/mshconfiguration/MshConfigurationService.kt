package no.difi.meldingsutveksling.nhn.adapter.mshconfiguration

import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException
import mu.KotlinLogging
import no.difi.meldingsutveksling.nhn.adapter.errors.MshConfigurationNotFoundException
import no.difi.meldingsutveksling.nhn.adapter.integration.adresseregisteret.AdresseregisteretService
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.MshService
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.difi.meldingsutveksling.nhn.adapter.security.parentOrganizationNumber
import no.ks.fiks.nhn.msh.MshConfiguration

private val logger = KotlinLogging.logger {}

/**
 * The MSH configurations of the HER-ids the adapter handles messages for. MSH requires one of every HER-id: without
 * it, MSH may reject requests for the HER-id and deletes its notifications after 4 days.
 *
 * A HER-id is registered the first time a client uses it, or by an administrator. The configurations are stored, so
 * that the administrator can see which HER-ids are in use, and so that they can be registered in MSH again at startup,
 * as NHN recommends.
 */
class MshConfigurationService(
    private val repository: MshConfigurationRepository,
    private val mshService: MshService,
    private val adresseregisteretService: AdresseregisteretService,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun list(): List<StoredMshConfiguration> = repository.findAll()

    /**
     * Registers the configuration of the HER-id in MSH, and stores it. A configuration already registered is replaced.
     *
     * @throws no.difi.meldingsutveksling.nhn.adapter.errors.HerIdNotFoundException if the HER-id is not in
     *   Adresseregisteret
     */
    suspend fun register(herId: Int, clientLocked: Boolean): StoredMshConfiguration {
        val organizationNumber = adresseregisteretService.lookupByHerId(herId).parentOrganizationNumber
        mshService.setConfigurations(listOf(MshConfiguration(herId, clientLocked)), organizationNumber)
        repository.save(herId, organizationNumber, clientLocked, clock.instant())
        logger.info { "Registered the MSH configuration of HER-id $herId, clientLocked = $clientLocked" }
        return checkNotNull(repository.find(herId)) { "The MSH configuration of HER-id $herId was not stored" }
    }

    /**
     * Deletes the configuration of the HER-id from MSH and the database. A HER-id still in use is registered again the
     * next time a client uses it.
     *
     * @throws MshConfigurationNotFoundException if the adapter has no configuration of the HER-id
     */
    suspend fun delete(herId: Int) {
        val configuration = repository.find(herId) ?: throw MshConfigurationNotFoundException(herId)
        mshService.deleteConfiguration(herId, configuration.organizationNumber)
        repository.delete(herId)
        logger.info { "Deleted the MSH configuration of HER-id $herId" }
    }

    /**
     * Registers the HER-id, which the client is about to use, unless it is registered. Never fails: the client's
     * request is made either way, and a failed registration is tried again the next time.
     */
    suspend fun ensureRegistered(herId: Int, clientContext: ClientContext) =
        bestEffort(herId) {
            if (repository.find(herId) == null) {
                registerForClient(herId, clientContext, used = false)
            }
        }

    /**
     * Records that a message was sent or received for the HER-id, and registers it unless it is registered. Never
     * fails, as the message is sent or received already.
     */
    suspend fun recordUse(herId: Int, clientContext: ClientContext) =
        bestEffort(herId) {
            if (!repository.updateLastUsed(herId, clock.instant())) {
                registerForClient(herId, clientContext, used = true)
            }
        }

    /** Registers every stored configuration in MSH again. A failure is logged, and the next one is tried. */
    suspend fun registerAllInMsh() {
        repository
            .findAll()
            .groupBy { it.organizationNumber }
            .forEach { (organizationNumber, configurations) ->
                try {
                    mshService.setConfigurations(
                        configurations.map { MshConfiguration(it.herId, it.clientLocked) },
                        organizationNumber,
                    )
                    logger.info {
                        "Registered the MSH configurations of the HER-ids ${configurations.map { it.herId }} again"
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn(e) { "Could not register the MSH configurations of organization $organizationNumber" }
                }
            }
    }

    /** The client has access to the HER-id, so it belongs to the organization the client acts for. */
    private suspend fun registerForClient(herId: Int, clientContext: ClientContext, used: Boolean) {
        mshService.setConfigurations(listOf(MshConfiguration(herId, clientLocked = false)), clientContext)
        repository.saveIfAbsent(herId, clientContext.onBehalfOfOrgNumber, clock.instant(), used)
        logger.info { "Registered the MSH configuration of HER-id $herId on its first use" }
    }

    private suspend fun bestEffort(herId: Int, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Could not record the use of HER-id $herId in its MSH configuration" }
        }
    }
}
