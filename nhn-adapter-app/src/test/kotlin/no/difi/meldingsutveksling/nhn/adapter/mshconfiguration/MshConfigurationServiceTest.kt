package no.difi.meldingsutveksling.nhn.adapter.mshconfiguration

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

private const val HER_ID = 8144796
private const val OTHER_HER_ID = 8144797
private const val ORG_NUMBER = "123456789"
private val NOW = Instant.parse("2026-11-24T12:00:00Z")

private fun stored(herId: Int = HER_ID, clientLocked: Boolean = false, multiTenant: Boolean = false) =
    StoredMshConfiguration(herId, ORG_NUMBER, multiTenant, clientLocked, NOW, NOW, null)

class MshConfigurationServiceTest :
    DescribeSpec({
        val repository = mockk<MshConfigurationRepository>()
        val mshService = mockk<MshService>()
        val adresseregisteretService = mockk<AdresseregisteretService>()
        // Unconfined: what recordUse does in the background is done when it returns
        val service =
            MshConfigurationService(
                repository,
                mshService,
                adresseregisteretService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                CoroutineScope(Dispatchers.Unconfined),
            )

        fun clientContext(supplier: Boolean = false) =
            mockk<ClientContext>().also {
                every { it.consumer } returns Iso6523.parse("0192:$ORG_NUMBER")
                every { it.onBehalfOfOrgNumber } returns ORG_NUMBER
                every { it.supplier } returns if (supplier) Iso6523.parse("0192:987654321") else null
            }

        beforeEach {
            clearAllMocks()
            service.clearCaches()
            coEvery { mshService.setConfigurations(any(), any<ClientContext>()) } just Runs
            coEvery { mshService.setConfigurations(any(), any<String>(), any()) } just Runs
            coEvery { mshService.deleteConfiguration(any(), any(), any()) } just Runs
            coEvery { repository.saveIfAbsent(any(), any(), any(), any()) } returns true
            coEvery { repository.save(any(), any(), any(), any(), any()) } just Runs
            coEvery { repository.delete(any()) } returns true
            coEvery { repository.updateTenant(any(), any(), any(), any()) } just Runs
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

                coVerify { repository.delete(HER_ID) }
            }

            it("restores the previous configuration when MSH rejects the new one") {
                coEvery { repository.find(HER_ID) } returns stored(clientLocked = false)
                coEvery { mshService.setConfigurations(any(), any<String>(), any()) } throws
                    HttpServerException(500, null, "Error")

                shouldThrow<HttpServerException> { service.register(HER_ID, clientLocked = true) }

                coVerify { repository.save(HER_ID, ORG_NUMBER, false, false, NOW) }
                coVerify(exactly = 0) { repository.delete(any()) }
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

            it("holds up the client's request for at most 2 seconds") {
                val slowService =
                    MshConfigurationService(
                        repository,
                        mshService,
                        adresseregisteretService,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        CoroutineScope(Dispatchers.Default),
                    )
                coEvery { repository.find(HER_ID) } coAnswers
                    {
                        delay(10.seconds)
                        null
                    }

                measureTime { slowService.ensureRegistered(HER_ID, clientContext()) } shouldBeLessThan 5.seconds
            }
        }

        describe("register when the administrator gives up") {
            it("restores the stored configuration, as MSH may not have got it") {
                coEvery { repository.find(HER_ID) } returns null
                coEvery { mshService.setConfigurations(any(), any<String>(), any()) } coAnswers { awaitCancellation() }

                val registration = launch { service.register(HER_ID, clientLocked = true) }
                delay(200.milliseconds)
                registration.cancelAndJoin()

                coVerify { repository.save(HER_ID, ORG_NUMBER, false, true, NOW) }
                coVerify { repository.delete(HER_ID) }
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
                coVerify { repository.updateTenant(HER_ID, ORG_NUMBER, true, NOW) }
            }

            it("does not fail when the database is down") {
                coEvery { repository.findAll() } throws RuntimeException("Database down")

                service.registerAllInMsh()
            }
        }
    })
