package no.difi.meldingsutveksling.nhn.adapter.mshconfiguration

import kotlinx.coroutines.runBlocking
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener

/**
 * Registers the stored MSH configurations in MSH again once the adapter has started, as NHN recommends, or once the
 * database is migrated, if it was down at startup. It runs in a thread of its own, so that a slow MSH or database does
 * not hold up the startup.
 */
class MshConfigurationStartupRegistration(
    private val mshConfigurationService: MshConfigurationService,
    private val databaseMigration: DatabaseMigration,
) : ApplicationListener<ApplicationReadyEvent> {
    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        Thread.ofVirtual().name("msh-configuration-registration").start {
            runBlocking {
                databaseMigration.awaitMigrated()
                mshConfigurationService.registerAllInMsh()
            }
        }
    }
}
