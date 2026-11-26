package no.difi.meldingsutveksling.nhn.adapter.mshconfiguration

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import org.springframework.jdbc.CannotGetJdbcConnectionException

class DatabaseMigrationTest :
    DescribeSpec({
        fun flyway(vararg failures: Exception) =
            mockk<Flyway>().also { flyway ->
                var calls = 0
                every { flyway.migrate() } answers
                    {
                        if (calls < failures.size) throw failures[calls++]
                        calls++
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

        it("lets the adapter start when the database is down, and tries again until it is migrated") {
            val down = CannotGetJdbcConnectionException("Database down")
            val flyway = flyway(down, down)
            val migration = DatabaseMigration(Duration.ofMillis(10))

            migration.migrate(flyway)

            migration.isMigrated shouldBe false
            withTimeout(5.seconds) { migration.awaitMigrated() }
            migration.isMigrated shouldBe true
            verify(exactly = 3) { flyway.migrate() }
            migration.destroy()
        }

        it("stops trying on shutdown") {
            val calls = AtomicInteger()
            val flyway = mockk<Flyway>()
            every { flyway.migrate() } answers
                {
                    calls.incrementAndGet()
                    throw CannotGetJdbcConnectionException("Database down")
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
        }
    })
