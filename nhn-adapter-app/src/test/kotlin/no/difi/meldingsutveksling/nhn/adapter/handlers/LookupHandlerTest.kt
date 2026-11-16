package no.difi.meldingsutveksling.nhn.adapter.handlers

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import no.difi.meldingsutveksling.nhn.adapter.audit.AuditLogService
import no.difi.meldingsutveksling.nhn.adapter.audit.IdentifierPseudonymizer
import no.difi.meldingsutveksling.nhn.adapter.errors.ClientInputException
import no.idporten.logging.audit.AuditEntry
import no.idporten.logging.audit.AuditLogger

class LookupHandlerTest :
    DescribeSpec({
        val auditLogger = mockk<AuditLogger>(relaxed = true)
        val handler =
            LookupHandler(
                AuditLogService(auditLogger),
                mockk(relaxed = true),
                mockk(relaxed = true),
                IdentifierPseudonymizer("test-pseudonymization-key-of-at-least-32-characters"),
            )
        val logged = mutableListOf<AuditEntry>()

        beforeTest {
            clearMocks(auditLogger)
            logged.clear()
        }

        describe("arLookup with an invalid identifier") {
            // A fødselsnummer with a hyphen or a space, a digit too few or too many: not caught by
            // maskFnr
            listOf("010190-12345", "010190 12345", "0101901234", "010190123456").forEach { pid ->
                it("does not write '$pid' to the audit log") {
                    shouldThrow<ClientInputException> { handler.arLookup("fastlege-for:$pid", mockk(relaxed = true)) }

                    verify { auditLogger.log(capture(logged)) }
                    val entry = logged.single()
                    entry.getAttribute("success") shouldBe false
                    entry.toString() shouldNotContain pid
                    entry.getAttribute("identifier").toString() shouldNotContain pid
                    entry.getAttribute("exception").toString() shouldNotContain pid.take(6)
                }
            }
        }
    })
