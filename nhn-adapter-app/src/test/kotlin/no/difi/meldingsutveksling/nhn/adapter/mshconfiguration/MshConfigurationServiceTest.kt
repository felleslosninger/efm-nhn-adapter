package no.difi.meldingsutveksling.nhn.adapter.mshconfiguration

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import no.difi.meldingsutveksling.domain.Iso6523
import no.difi.meldingsutveksling.nhn.adapter.ArTestData
import no.difi.meldingsutveksling.nhn.adapter.errors.MshConfigurationNotFoundException
import no.difi.meldingsutveksling.nhn.adapter.integration.adresseregisteret.AdresseregisteretService
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.MshService
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.ks.fiks.nhn.msh.HttpClientException
import no.ks.fiks.nhn.msh.HttpServerException
import no.ks.fiks.nhn.msh.MshConfiguration
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.CannotGetJdbcConnectionException

private const val HER_ID = 8144796
private const val OTHER_HER_ID = 8144797
private const val ORG_NUMBER = "123456789"
private val NOW = Instant.parse("2026-11-24T12:00:00Z")

private fun stored(herId: Int = HER_ID, clientLocked: Boolean = false, multiTenant: Boolean = false) =
    StoredMshConfiguration(herId, ORG_NUMBER, multiTenant, clientLocked, NOW, NOW, null)

private class MutableClock(var instant: Instant) : Clock() {
    override fun instant(): Instant = instant

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this
}

class MshConfigurationServiceTest :
    DescribeSpec({
        val repository = mockk<MshConfigurationRepository>()
        val mshService = mockk<MshService>()
        val adresseregisteretService = mockk<AdresseregisteretService>()
        // Unconfined: what is done in the background is done when the call returns
        fun service(
            clock: Clock = Clock.fixed(NOW, ZoneOffset.UTC),
            scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
        ) = MshConfigurationService(repository, mshService, adresseregisteretService, clock, scope)

        val service = service()

        fun clientContext(supplier: Boolean = false) =
            mockk<ClientContext>().also {
                every { it.consumer } returns Iso6523.parse("0192:$ORG_NUMBER")
                every { it.onBehalfOfOrgNumber } returns ORG_NUMBER
                every { it.supplier } returns if (supplier) Iso6523.parse("0192:987654321") else null
            }

        beforeEach {
            // Not clearAllMocks: the unit test specs run in parallel
            clearMocks(repository, mshService, adresseregisteretService)
            service.clearCaches()
            coEvery { mshService.setConfigurations(any(), any<ClientContext>()) } just Runs
            coEvery { mshService.setConfigurations(any(), any<String>(), any()) } just Runs
            coEvery { mshService.deleteConfiguration(any(), any(), any()) } just Runs
            coEvery { repository.saveIfAbsent(any(), any(), any(), any()) } returns true
            coEvery { repository.save(any(), any(), any(), any(), any()) } just Runs
            coEvery { repository.delete(any()) } returns true
            coEvery { repository.deleteIfUpdatedAt(any(), any()) } returns true
            coEvery { repository.restore(any(), any()) } returns true
            coEvery { repository.updateTenant(any(), any(), any()) } just Runs
            coEvery { repository.updateLastUsed(any(), any()) } returns true
            coEvery { adresseregisteretService.lookupByHerId(HER_ID) } returns
                ArTestData.organization(HER_ID, "Kommune", ORG_NUMBER)
        }

        describe("ensureRegistered") {
            it("registers a HER-id that is not stored, with the tenant of the client") {
                coEvery { repository.find(HER_ID) } returns null
                val client = clientContext(supplier = true)

                service.ensureRegistered(HER_ID, client)

                coVerify { mshService.setConfigurations(listOf(MshConfiguration(HER_ID, false)), client) }
                coVerify { repository.saveIfAbsent(HER_ID, ORG_NUMBER, true, NOW) }
            }

            it("does not register a stored HER-id, and asks the database only once") {
                coEvery { repository.find(HER_ID) } returns stored()

                service.ensureRegistered(HER_ID, clientContext())
                service.ensureRegistered(HER_ID, clientContext())

                coVerify(exactly = 1) { repository.find(HER_ID) }
                coVerify(exactly = 0) { mshService.setConfigurations(any(), any<ClientContext>()) }
            }

            it("registers the lock an administrator stored meanwhile") {
                coEvery { repository.find(HER_ID) } returnsMany listOf(null, stored(clientLocked = true))
                coEvery { repository.saveIfAbsent(any(), any(), any(), any()) } returns false

                service.ensureRegistered(HER_ID, clientContext())

                coVerify { mshService.setConfigurations(listOf(MshConfiguration(HER_ID, true)), ORG_NUMBER, false) }
            }

            it("does not fail when the database is down, and does not try again at once") {
                coEvery { repository.find(HER_ID) } throws RuntimeException("Database down")

                service.ensureRegistered(HER_ID, clientContext())
                service.ensureRegistered(HER_ID, clientContext())

                coVerify(exactly = 1) { repository.find(HER_ID) }
            }

            it("does not fail when MSH rejects the registration, and does not try again at once") {
                coEvery { repository.find(HER_ID) } returns null
                coEvery { mshService.setConfigurations(any(), any<ClientContext>()) } throws
                    HttpClientException(423, null, "Locked")

                service.ensureRegistered(HER_ID, clientContext())
                service.ensureRegistered(HER_ID, clientContext())

                coVerify(exactly = 1) { mshService.setConfigurations(any(), any<ClientContext>()) }
                coVerify(exactly = 0) { repository.saveIfAbsent(any(), any(), any(), any()) }
            }
        }

        describe("recordUse") {
            it("records the use at most once a minute") {
                service.recordUse(HER_ID, clientContext())
                service.recordUse(HER_ID, clientContext())

                coVerify(exactly = 1) { repository.updateLastUsed(HER_ID, NOW) }
            }

            it("registers a HER-id that is not stored, and records its use") {
                coEvery { repository.updateLastUsed(HER_ID, NOW) } returnsMany listOf(false, true)
                coEvery { repository.find(HER_ID) } returnsMany listOf(null, stored())

                service.recordUse(HER_ID, clientContext())

                coVerify { repository.saveIfAbsent(HER_ID, ORG_NUMBER, false, NOW) }
                coVerify(exactly = 2) { repository.updateLastUsed(HER_ID, NOW) }
            }

            it("does not fail when the database is down") {
                coEvery { repository.updateLastUsed(any(), any()) } throws RuntimeException("Database down")

                service.recordUse(HER_ID, clientContext())
            }
        }

        describe("register") {
            it("keeps the tenant the HER-id is registered with, and answers with what it stored") {
                coEvery { repository.find(HER_ID) } returnsMany
                    listOf(stored(multiTenant = true), stored(multiTenant = true, clientLocked = true))

                val result = service.register(HER_ID, clientLocked = true)

                coVerify { repository.save(HER_ID, ORG_NUMBER, true, true, NOW) }
                coVerify(exactly = 1) {
                    mshService.setConfigurations(listOf(MshConfiguration(HER_ID, true)), ORG_NUMBER, true)
                }
                result.clientLocked shouldBe true
                result.multiTenant shouldBe true
            }

            it("registers the lock another administrator stored meanwhile") {
                coEvery { repository.find(HER_ID) } returnsMany listOf(null, stored(clientLocked = false))

                service.register(HER_ID, clientLocked = true)

                coVerify { mshService.setConfigurations(listOf(MshConfiguration(HER_ID, false)), ORG_NUMBER, false) }
            }

            it("deletes a new configuration again when MSH rejects it") {
                coEvery { repository.find(HER_ID) } returns null
                coEvery { mshService.setConfigurations(any(), any<String>(), any()) } throws
                    HttpServerException(500, null, "Error")

                shouldThrow<HttpServerException> { service.register(HER_ID, clientLocked = false) }

                coVerify { repository.deleteIfUpdatedAt(HER_ID, NOW) }
            }

            it("restores the previous configuration when MSH rejects the new one") {
                coEvery { repository.find(HER_ID) } returns stored(clientLocked = false)
                coEvery { mshService.setConfigurations(any(), any<String>(), any()) } throws
                    HttpServerException(500, null, "Error")

                shouldThrow<HttpServerException> { service.register(HER_ID, clientLocked = true) }

                coVerify { repository.restore(stored(clientLocked = false), NOW) }
                coVerify(exactly = 0) { repository.deleteIfUpdatedAt(any(), any()) }
            }

            it("registers the restored configuration again on use, as MSH may have got the new one") {
                coEvery { repository.find(HER_ID) } returns stored(clientLocked = false)
                coEvery { mshService.setConfigurations(any(), any<String>(), any()) } throws IOException("Timeout")
                shouldThrow<IOException> { service.register(HER_ID, clientLocked = true) }

                service.ensureRegistered(HER_ID, clientContext())

                coVerify {
                    mshService.setConfigurations(listOf(MshConfiguration(HER_ID, false)), any<ClientContext>())
                }
            }
        }

        describe("delete") {
            it("deletes a configuration MSH does not have from the database") {
                coEvery { repository.find(HER_ID) } returns stored(multiTenant = true)
                coEvery { mshService.deleteConfiguration(HER_ID, ORG_NUMBER, true) } throws
                    HttpClientException(404, null, "Not found")

                service.delete(HER_ID)

                coVerify { repository.delete(HER_ID) }
            }

            it("keeps the configuration when MSH fails otherwise") {
                coEvery { repository.find(HER_ID) } returns stored()
                coEvery { mshService.deleteConfiguration(any(), any(), any()) } throws
                    HttpClientException(423, null, "Locked")

                shouldThrow<HttpClientException> { service.delete(HER_ID) }

                coVerify(exactly = 0) { repository.delete(any()) }
            }

            it("fails for a HER-id that has no configuration") {
                coEvery { repository.find(HER_ID) } returns null

                shouldThrow<MshConfigurationNotFoundException> { service.delete(HER_ID) }
            }
        }

        describe("a database that is down") {
            it("is left alone for a while after one failure, for all HER-ids") {
                coEvery { repository.find(any()) } throws DataAccessResourceFailureException("Database down")

                service.ensureRegistered(HER_ID, clientContext())
                service.ensureRegistered(OTHER_HER_ID, clientContext())
                service.recordUse(OTHER_HER_ID, clientContext())

                coVerify(exactly = 1) { repository.find(any()) }
                coVerify(exactly = 0) { repository.updateLastUsed(any(), any()) }
            }

            it("is not left alone after an error in the data of one HER-id") {
                coEvery { repository.find(HER_ID) } throws DataIntegrityViolationException("Data too long")
                coEvery { repository.find(OTHER_HER_ID) } returns stored(OTHER_HER_ID)

                service.ensureRegistered(HER_ID, clientContext())
                service.ensureRegistered(OTHER_HER_ID, clientContext())

                coVerify(exactly = 1) { repository.find(OTHER_HER_ID) }
            }

            it("is asked again after 30 seconds") {
                val clock = MutableClock(NOW)
                val service = service(clock = clock)
                coEvery { repository.find(any()) } throws CannotGetJdbcConnectionException("Database down")

                service.ensureRegistered(HER_ID, clientContext())
                clock.instant = NOW.plusSeconds(29)
                service.ensureRegistered(OTHER_HER_ID, clientContext())
                clock.instant = NOW.plusSeconds(31)
                service.ensureRegistered(OTHER_HER_ID, clientContext())

                coVerify(exactly = 1) { repository.find(HER_ID) }
                coVerify(exactly = 1) { repository.find(OTHER_HER_ID) }
            }

            it("holds up the client's request for at most 2 seconds, and registers in the background") {
                val scope = CoroutineScope(Dispatchers.Default)
                val service = service(scope = scope)
                coEvery { repository.find(HER_ID) } coAnswers
                    {
                        delay(3.seconds)
                        null
                    }
                try {
                    measureTime { service.ensureRegistered(HER_ID, clientContext()) } shouldBeLessThan 2.5.seconds

                    eventually(5.seconds) { coVerify { mshService.setConfigurations(any(), any<ClientContext>()) } }
                } finally {
                    scope.cancel()
                }
            }
        }

        describe("many requests for a HER-id at once") {
            it("register it once") {
                val scope = CoroutineScope(Dispatchers.Default)
                val service = service(scope = scope)
                coEvery { repository.find(HER_ID) } returns null
                coEvery { mshService.setConfigurations(any(), any<ClientContext>()) } coAnswers
                    {
                        delay(500.milliseconds)
                    }
                try {
                    coroutineScope { repeat(20) { launch { service.ensureRegistered(HER_ID, clientContext()) } } }

                    coVerify(exactly = 1) { mshService.setConfigurations(any(), any<ClientContext>()) }
                } finally {
                    scope.cancel()
                }
            }
        }

        describe("register when the administrator gives up") {
            it("restores the stored configuration, as MSH may not have got it") {
                coEvery { repository.find(HER_ID) } returns null
                coEvery { mshService.setConfigurations(any(), any<String>(), any()) } coAnswers { awaitCancellation() }
                // Suspends, as the real one does, and so fails in a cancelled coroutine: only a
                // restore that is not
                // cancelled gets past it
                var restored = false
                coEvery { repository.deleteIfUpdatedAt(any(), any()) } coAnswers
                    {
                        yield()
                        restored = true
                        true
                    }

                val registration = launch { service.register(HER_ID, clientLocked = true) }
                delay(200.milliseconds)
                registration.cancelAndJoin()

                coVerify { repository.save(HER_ID, ORG_NUMBER, false, true, NOW) }
                restored shouldBe true
            }
        }

        describe("register, and the restore when it fails") {
            it("stores the time as the database does, to the microsecond, so that the restore can find it") {
                val service =
                    service(clock = Clock.fixed(Instant.parse("2026-11-24T12:00:00.123456789Z"), ZoneOffset.UTC))
                val storedAt = Instant.parse("2026-11-24T12:00:00.123456Z")
                coEvery { repository.find(HER_ID) } returns stored()
                coEvery { mshService.setConfigurations(any(), any<String>(), any()) } throws IOException("Timeout")

                shouldThrow<IOException> { service.register(HER_ID, clientLocked = true) }

                coVerify { repository.save(HER_ID, ORG_NUMBER, false, true, storedAt) }
                coVerify { repository.restore(stored(), storedAt) }
            }

            it("registers the HER-id again on use, also when a client registered it during the restore") {
                coEvery { repository.find(HER_ID) } returns stored(clientLocked = true)
                coEvery { mshService.setConfigurations(any(), any<String>(), any()) } throws IOException("Timeout")
                coEvery { repository.restore(any(), any()) } coAnswers
                    {
                        // A client's use takes the stored lock as registered, before it is put back
                        service.ensureRegistered(HER_ID, clientContext())
                        coEvery { repository.find(HER_ID) } returns stored(clientLocked = false)
                        true
                    }

                shouldThrow<IOException> { service.register(HER_ID, clientLocked = true) }
                service.ensureRegistered(HER_ID, clientContext())

                coVerify {
                    mshService.setConfigurations(listOf(MshConfiguration(HER_ID, false)), any<ClientContext>())
                }
            }

            it("registers the stored lock until it holds, and gives up after three rounds") {
                coEvery { repository.find(HER_ID) } returnsMany
                    listOf(
                        null,
                        stored(clientLocked = true),
                        stored(clientLocked = false),
                        stored(clientLocked = true),
                        stored(clientLocked = false),
                    )

                service.ensureRegistered(HER_ID, clientContext())

                coVerify(exactly = 3) { mshService.setConfigurations(any(), ORG_NUMBER, false) }
                // Not settled, so registered again on the next use
                coEvery { repository.find(HER_ID) } returns stored()
                service.ensureRegistered(HER_ID, clientContext())
                coVerify(exactly = 2) { mshService.setConfigurations(any(), any<ClientContext>()) }
            }
        }

        describe("recordUse while a registration is in progress") {
            it("waits for it, rather than starting one of its own") {
                val scope = CoroutineScope(Dispatchers.Default)
                val service = service(scope = scope)
                coEvery { repository.find(HER_ID) } returns null
                coEvery { repository.updateLastUsed(HER_ID, NOW) } returnsMany listOf(false, true)
                coEvery { mshService.setConfigurations(any(), any<ClientContext>()) } coAnswers
                    {
                        delay(500.milliseconds)
                    }
                try {
                    launch { service.ensureRegistered(HER_ID, clientContext()) }
                    delay(100.milliseconds)
                    service.recordUse(HER_ID, clientContext())

                    eventually(5.seconds) { coVerify(exactly = 2) { repository.updateLastUsed(HER_ID, NOW) } }
                    coVerify(exactly = 1) { mshService.setConfigurations(any(), any<ClientContext>()) }
                } finally {
                    scope.cancel()
                }
            }
        }

        describe("registerAllInMsh") {
            it("does not try each HER-id on its own when MSH does not answer, but registers them again on use") {
                coEvery { repository.findAll() } returns listOf(stored(HER_ID), stored(OTHER_HER_ID))
                coEvery { repository.find(any()) } answers { stored(firstArg()) }
                coEvery { mshService.setConfigurations(any(), any<String>(), any()) } throws IOException("Timeout")
                // Known as registered before the registration at startup fails
                service.ensureRegistered(HER_ID, clientContext())

                service.registerAllInMsh()
                service.ensureRegistered(HER_ID, clientContext())
                service.recordUse(OTHER_HER_ID, clientContext())

                coVerify(exactly = 1) { mshService.setConfigurations(any(), any<String>(), any()) }
                coVerify {
                    mshService.setConfigurations(listOf(MshConfiguration(HER_ID, false)), any<ClientContext>())
                }
                coVerify {
                    mshService.setConfigurations(listOf(MshConfiguration(OTHER_HER_ID, false)), any<ClientContext>())
                }
            }

            it("registers a lock an administrator stored meanwhile") {
                coEvery { repository.findAll() } returnsMany
                    listOf(listOf(stored(HER_ID)), listOf(stored(HER_ID, clientLocked = true)))
                coEvery { repository.find(HER_ID) } returns stored(HER_ID, clientLocked = true)

                service.registerAllInMsh()

                coVerify { mshService.setConfigurations(listOf(MshConfiguration(HER_ID, true)), ORG_NUMBER, false) }
            }

            it("registers the configurations a request per tenant") {
                coEvery { repository.findAll() } returns
                    listOf(stored(HER_ID), stored(OTHER_HER_ID, clientLocked = true, multiTenant = true))

                service.registerAllInMsh()

                coVerify { mshService.setConfigurations(listOf(MshConfiguration(HER_ID, false)), ORG_NUMBER, false) }
                coVerify {
                    mshService.setConfigurations(listOf(MshConfiguration(OTHER_HER_ID, true)), ORG_NUMBER, true)
                }
            }

            it("registers them, also when the stored locks cannot be checked afterwards") {
                coEvery { repository.findAll() } returnsMany
                    listOf(listOf(stored(HER_ID))) andThenThrows
                    CannotGetJdbcConnectionException("Database down")

                service.registerAllInMsh()
                service.ensureRegistered(HER_ID, clientContext())

                coVerify(exactly = 1) { mshService.setConfigurations(any(), ORG_NUMBER, false) }
                coVerify(exactly = 0) { repository.find(any()) }
            }

            listOf(408 to "Request timeout", 429 to "Too many requests").forEach { (status, reason) ->
                it("does not try each HER-id on its own when MSH answers $status") {
                    coEvery { repository.findAll() } returns listOf(stored(HER_ID), stored(OTHER_HER_ID))
                    coEvery { mshService.setConfigurations(any(), any<String>(), any()) } throws
                        HttpClientException(status, null, reason)

                    service.registerAllInMsh()

                    coVerify(exactly = 1) { mshService.setConfigurations(any(), any<String>(), any()) }
                }
            }

            it("does not try each HER-id on its own when MSH is busy, and leaves the rest for their use") {
                coEvery { repository.findAll() } returns listOf(stored(HER_ID), stored(OTHER_HER_ID))
                coEvery { mshService.setConfigurations(any(), any<String>(), any()) } throws
                    HttpClientException(429, null, "Too many requests")

                service.registerAllInMsh()

                coVerify(exactly = 1) { mshService.setConfigurations(any(), any<String>(), any()) }
            }

            it("tries each HER-id on its own when MSH rejects the request, and the failed one again on use") {
                coEvery { repository.findAll() } returns listOf(stored(HER_ID), stored(OTHER_HER_ID))
                coEvery { repository.find(any()) } answers { stored(firstArg()) }
                coEvery { mshService.setConfigurations(match { it.size == 2 }, any<String>(), any()) } throws
                    HttpClientException(403, null, "Not authorized for HerId(s): $HER_ID")
                coEvery {
                    mshService.setConfigurations(listOf(MshConfiguration(HER_ID, false)), any<String>(), any())
                } throws HttpClientException(403, null, "Not authorized")

                service.registerAllInMsh()

                coVerify {
                    mshService.setConfigurations(listOf(MshConfiguration(OTHER_HER_ID, false)), ORG_NUMBER, false)
                }

                // The registered one needs nothing on use, the other is registered with the
                // client's tenant
                service.ensureRegistered(OTHER_HER_ID, clientContext())
                service.ensureRegistered(HER_ID, clientContext(supplier = true))

                coVerify(exactly = 0) { repository.find(OTHER_HER_ID) }
                coVerify {
                    mshService.setConfigurations(listOf(MshConfiguration(HER_ID, false)), any<ClientContext>())
                }
                coVerify { repository.updateTenant(HER_ID, ORG_NUMBER, true) }
            }

            it("does not fail when the database is down") {
                coEvery { repository.findAll() } throws RuntimeException("Database down")

                service.registerAllInMsh()
            }
        }
    })
