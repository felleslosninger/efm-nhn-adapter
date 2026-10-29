package no.difi.meldingsutveksling.nhn.adapter.audit

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import kotlin.coroutines.cancellation.CancellationException
import no.idporten.logging.audit.AuditEntry
import no.idporten.logging.audit.AuditLogger

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
