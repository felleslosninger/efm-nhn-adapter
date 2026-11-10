package no.difi.meldingsutveksling.nhn.adapter.audit

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.net.ConnectException
import kotlin.coroutines.cancellation.CancellationException
import no.idporten.logging.audit.AuditEntry
import no.idporten.logging.audit.AuditLogger
import no.ks.fiks.nhn.msh.MshException

class AuditLogServiceTest :
    DescribeSpec({
        val auditLogger = mockk<AuditLogger>(relaxed = true)
        val auditLogService = AuditLogService(auditLogger)
        val logged = mutableListOf<AuditEntry>()

        beforeTest {
            clearMocks(auditLogger)
            logged.clear()
        }

        fun newEntry() = AuditEntry.builder().auditId(NHNAdapterAuditIdentifier.GET_STATUS)

        fun verifyLogged() = verify { auditLogger.log(capture(logged)) }

        // Mirrors how the handlers use log: returning from the enclosing function inside the action
        fun returnFromInsideAction(skip: Boolean): String {
            auditLogService.log(newEntry(), skipOnSuccess = { skip }) {
                return "result"
            }
        }

        describe("AuditLogService.log") {
            it("logs success when the action returns from the enclosing function") {
                returnFromInsideAction(skip = false) shouldBe "result"

                verifyLogged()
                logged.single().getAttribute("success") shouldBe true
            }

            it("does not log when skipOnSuccess returns true") {
                returnFromInsideAction(skip = true) shouldBe "result"

                verify(exactly = 0) { auditLogger.log(any()) }
            }

            it("always logs failures, even when skipOnSuccess returns true") {
                shouldThrow<IllegalStateException> {
                    auditLogService.log(newEntry(), skipOnSuccess = { true }) { error("boom") }
                }

                verifyLogged()
                logged.single().getAttribute("success") shouldBe false
                logged.single().getAttribute("exception") shouldBe "boom"
            }

            it("masks fødselsnummer in the exception message") {
                shouldThrow<IllegalStateException> {
                    auditLogService.log(newEntry()) { error("Ukjent pasient 16822449879") }
                }

                verifyLogged()
                logged.single().getAttribute("exception") shouldBe "Ukjent pasient ***********"
            }

            it("includes the causes in the exception message") {
                val error =
                    MshException(
                        "Unknown error from MSH API",
                        IOException("Request failed", ConnectException("Connection refused")),
                    )
                shouldThrow<MshException> { auditLogService.log(newEntry()) { throw error } }

                verifyLogged()
                logged.single().getAttribute("exception") shouldBe
                    "Unknown error from MSH API; caused by IOException: Request failed; " +
                        "caused by ConnectException: Connection refused"
            }

            it("masks fødselsnummer in the causes") {
                shouldThrow<IllegalStateException> {
                    auditLogService.log(newEntry()) {
                        throw IllegalStateException("Lookup failed", IOException("Ukjent pasient 16822449879"))
                    }
                }

                verifyLogged()
                logged.single().getAttribute("exception") shouldBe
                    "Lookup failed; caused by IOException: Ukjent pasient ***********"
            }

            it("describes exceptions without a message by their class name") {
                shouldThrow<IllegalStateException> {
                    auditLogService.log(newEntry()) { throw IllegalStateException(null, NullPointerException()) }
                }

                verifyLogged()
                logged.single().getAttribute("exception") shouldBe
                    "IllegalStateException; caused by NullPointerException"
            }

            it("logs an Error as a failure") {
                shouldThrow<NotImplementedError> { auditLogService.log(newEntry()) { TODO("not yet") } }

                verifyLogged()
                logged.single().getAttribute("success") shouldBe false
            }

            it("logs a cancellation as a cancelled failure") {
                shouldThrow<CancellationException> {
                    auditLogService.log(newEntry(), skipOnSuccess = { true }) {
                        throw CancellationException("MonoCoroutine was cancelled")
                    }
                }

                verifyLogged()
                logged.single().getAttribute("success") shouldBe false
                logged.single().getAttribute("exception") shouldBe CANCELLED_MESSAGE
            }
        }
    })
