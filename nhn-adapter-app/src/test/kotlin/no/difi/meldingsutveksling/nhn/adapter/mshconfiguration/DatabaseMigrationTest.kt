package no.difi.meldingsutveksling.nhn.adapter.mshconfiguration

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.sql.SQLNonTransientConnectionException
import java.sql.SQLSyntaxErrorException
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.flywaydb.core.api.output.MigrateResult

/** As Flyway reports a database it cannot connect to. */
private fun unreachable() =
    FlywayException(
        "Unable to obtain connection from database",
        SQLNonTransientConnectionException("Socket fail to connect to host", "08000"),
    )

class DatabaseMigrationTest :
    DescribeSpec({
        fun flyway(vararg failures: Exception) =
            mockk<Flyway>().also { flyway ->
                val calls = AtomicInteger()
                every { flyway.migrate() } answers
                    {
                        val call = calls.getAndIncrement()
                        if (call < failures.size) throw failures[call]
                        mockk<MigrateResult>()
                    }
            }

        it("migrates at startup") {
            val flyway = flyway()
            val migration = DatabaseMigration(Duration.ofMillis(10))

            migration.migrate(flyway)

            migration.isMigrated shouldBe true
            withTimeout(1.seconds) { migration.awaitMigrated() }
            verify(exactly = 1) { flyway.migrate() }
            migration.destroy()
        }

        it("lets the adapter start when the database cannot be reached, and tries again until it is migrated") {
            val flyway = flyway(unreachable(), unreachable())
            val migration = DatabaseMigration(Duration.ofMillis(10))

            migration.migrate(flyway)

            migration.isMigrated shouldBe false
            withTimeout(5.seconds) { migration.awaitMigrated() }
            migration.isMigrated shouldBe true
            verify(exactly = 3) { flyway.migrate() }
            migration.destroy()
        }

        it("stops the startup when the migration fails otherwise") {
            val failure = FlywayException("Migration V2__error.sql failed", SQLSyntaxErrorException("Syntax", "42000"))
            val migration = DatabaseMigration(Duration.ofMillis(10))

            shouldThrow<FlywayException> { migration.migrate(flyway(failure)) } shouldBe failure

            migration.isMigrated shouldBe false
            migration.destroy()
        }

        it("keeps trying when the migration fails otherwise after the database could not be reached") {
            val failure = FlywayException("Migration V2__error.sql failed", SQLSyntaxErrorException("Syntax", "42000"))
            val flyway = flyway(unreachable(), failure)
            val migration = DatabaseMigration(Duration.ofMillis(10))

            migration.migrate(flyway)

            withTimeout(5.seconds) { migration.awaitMigrated() }
            verify(exactly = 3) { flyway.migrate() }
            migration.destroy()
        }

        it("stops trying on shutdown, and ends the wait for it") {
            val calls = AtomicInteger()
            val flyway = mockk<Flyway>()
            every { flyway.migrate() } answers
                {
                    calls.incrementAndGet()
                    throw unreachable()
                }
            val migration = DatabaseMigration(Duration.ofMillis(10))

            migration.migrate(flyway)
            eventually(5.seconds) { calls.get() shouldBeGreaterThanOrEqual 2 }
            migration.destroy()
            // A try in progress may finish
            delay(50.milliseconds)
            val afterShutdown = calls.get()
            delay(100.milliseconds)

            calls.get() shouldBe afterShutdown
            migration.isMigrated shouldBe false
            // Ends at once, rather than on the timeout
            val ended = runCatching { withTimeout(1.seconds) { migration.awaitMigrated() } }.exceptionOrNull()
            (ended is CancellationException && ended !is TimeoutCancellationException) shouldBe true
        }
    })
