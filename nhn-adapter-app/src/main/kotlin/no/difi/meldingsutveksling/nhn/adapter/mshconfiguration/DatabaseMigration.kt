package no.difi.meldingsutveksling.nhn.adapter.mshconfiguration

import java.time.Duration
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.flywaydb.core.Flyway
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy

private val logger = KotlinLogging.logger {}

/**
 * Migrates the database with Flyway at startup, as Spring Boot does, but does not keep the adapter from starting when
 * the database is down: messaging does not depend on it. Until it is migrated, the MSH configurations are left alone,
 * and the migration is tried again every [retryEvery] in the background.
 */
class DatabaseMigration(private val retryEvery: Duration = Duration.ofSeconds(30)) :
    FlywayMigrationStrategy, DisposableBean {
    private val migrated = CompletableDeferred<Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val isMigrated: Boolean
        get() = migrated.isCompleted

    suspend fun awaitMigrated() = migrated.await()

    override fun migrate(flyway: Flyway) {
        if (tryMigrate(flyway, first = true)) return
        scope.launch {
            do {
                delay(retryEvery.toKotlinDuration())
            } while (!tryMigrate(flyway, first = false))
        }
    }

    private fun tryMigrate(flyway: Flyway, first: Boolean): Boolean =
        try {
            flyway.migrate()
            migrated.complete(Unit)
            if (!first) logger.info { "Migrated the database, which could not be migrated at startup" }
            true
        } catch (e: Exception) {
            if (first) {
                logger.error(e) {
                    "Could not migrate the database. The adapter starts without the MSH configurations, and tries " +
                        "again every ${retryEvery.toKotlinDuration()}"
                }
            } else {
                logger.warn { "Could not migrate the database: ${e.message}" }
            }
            false
        }

    override fun destroy() {
        scope.cancel()
    }
}
