package no.difi.meldingsutveksling.nhn.adapter.audit

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.UUID
import no.difi.meldingsutveksling.nhn.adapter.model.DialogmeldingKvitteringMessage
import no.difi.meldingsutveksling.nhn.adapter.model.DialogmeldingKvitteringStatus
import no.difi.meldingsutveksling.nhn.adapter.model.DialogmeldingMessage
import no.difi.meldingsutveksling.nhn.adapter.model.OutgoingApplicationReceipt
import no.difi.meldingsutveksling.nhn.adapter.model.OutgoingBusinessDocument
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.idporten.logging.audit.AuditEntry
import no.idporten.logging.audit.AuditLogger
import org.junit.jupiter.api.Assertions.assertEquals

class AuditLogServiceTest :
    DescribeSpec({
        val auditLogger = mockk<AuditLogger>(relaxed = true)
        val auditLogService = AuditLogService(auditLogger)
        val clientContext =
            mockk<ClientContext> {
                every { clientId } returns "test-client"
                every { orgNumber } returns "123456789"
                every { onBehalfOfOrgNumber } returns "987654321"
                every { scopes } returns setOf("test-scope")
            }

        beforeEach { clearMocks(auditLogger) }

        describe("AuditLogService") {
            it("should log arLookup") {
                val identifier = "test-id"
                auditLogService.arLookup(identifier, clientContext)

                val slot = slot<AuditEntry>()
                verify { auditLogger.log(capture(slot)) }
                val entry = slot.captured
                assertEquals(NHNAdapterAuditIdentifier.AR_LOOKUP, entry.auditId)
                assertEquals(identifier, entry.attributes["identifier"])
            }

            it("should log getStatus") {
                val messageId = UUID.randomUUID()
                auditLogService.getStatus(messageId, clientContext)

                val slot = slot<AuditEntry>()
                verify { auditLogger.log(capture(slot)) }
                assertEquals(messageId, slot.captured.attributes["messageId"])
                assertEquals(NHNAdapterAuditIdentifier.GET_STATUS, slot.captured.auditId)
            }

            it("should log sendApplicationReceipt") {
                val receipt =
                    OutgoingApplicationReceipt(
                        senderHerId = 123,
                        payload =
                            DialogmeldingKvitteringMessage(
                                relatedToMessageId = "rel-123",
                                status = DialogmeldingKvitteringStatus.OK,
                                messages = null,
                            ),
                    )
                val messageReference = UUID.randomUUID()
                auditLogService.sendApplicationReceipt(receipt, messageReference, clientContext)

                val slot = slot<AuditEntry>()
                verify { auditLogger.log(capture(slot)) }
                val entry = slot.captured
                assertEquals(NHNAdapterAuditIdentifier.SEND_APPLICATION_RECEIPT, entry.auditId)
                assertEquals(123, entry.attributes["senderHerId"])
                assertEquals(DialogmeldingKvitteringStatus.OK, entry.attributes["status"])
                assertEquals("rel-123", entry.attributes["relatedToMessageId"])
                assertEquals(messageReference, entry.attributes["messageReference"])
            }

            it("should log sendMessage") {
                val outgoingDoc =
                    OutgoingBusinessDocument(
                        messageId = UUID.randomUUID().toString(),
                        conversationId = "conv-123",
                        parentId = "parent-123",
                        senderHerId = 456,
                        receiverHerId = 789,
                        payload = DialogmeldingMessage("body", null, null),
                    )
                val messageReference = UUID.randomUUID()
                auditLogService.sendMessage(outgoingDoc, messageReference, clientContext)

                val slot = slot<AuditEntry>()
                verify { auditLogger.log(capture(slot)) }
                val entry = slot.captured
                assertEquals(NHNAdapterAuditIdentifier.SEND_MESSAGE, entry.auditId)
                assertEquals(outgoingDoc.messageId, entry.attributes["messageId"])
                assertEquals(outgoingDoc.conversationId, entry.attributes["conversationId"])
                assertEquals(outgoingDoc.parentId, entry.attributes["parentId"])
                assertEquals(456, entry.attributes["senderHerId"])
                assertEquals(789, entry.attributes["receiverHerId"])
                assertEquals(messageReference, entry.attributes["messageReference"])
            }

            it("should log getMessagesWithMetadata") {
                val receiverHerId = 999
                auditLogService.getMessagesWithMetadata(receiverHerId, clientContext)

                val slot = slot<AuditEntry>()
                verify { auditLogger.log(capture(slot)) }
                assertEquals(NHNAdapterAuditIdentifier.GET_MESSAGES_WITH_METADATA, slot.captured.auditId)
                assertEquals(receiverHerId, slot.captured.attributes["receiverHerId"])
            }

            it("should log getApplicationReceipt") {
                val id = UUID.randomUUID()
                auditLogService.getApplicationReceipt(id, clientContext)

                val slot = slot<AuditEntry>()
                verify { auditLogger.log(capture(slot)) }
                assertEquals(NHNAdapterAuditIdentifier.GET_APPLICATION_RECEIPT, slot.captured.auditId)
                assertEquals(id, slot.captured.attributes["id"])
            }

            it("should log getBusinessDocument") {
                val id = UUID.randomUUID()
                auditLogService.getBusinessDocument(id, clientContext)

                val slot = slot<AuditEntry>()
                verify { auditLogger.log(capture(slot)) }
                assertEquals(NHNAdapterAuditIdentifier.GET_BUSINESS_DOCUMENT, slot.captured.auditId)
                assertEquals(id, slot.captured.attributes["id"])
            }

            it("should log markMessageRead") {
                val messageId = UUID.randomUUID()
                val receiverHerId = 111
                auditLogService.markMessageRead(messageId, receiverHerId, clientContext)

                val slot = slot<AuditEntry>()
                verify { auditLogger.log(capture(slot)) }
                val entry = slot.captured
                assertEquals(NHNAdapterAuditIdentifier.MARK_MESSAGE_AS_READ, entry.auditId)
                assertEquals(messageId, entry.attributes["messageId"])
                assertEquals(receiverHerId, entry.attributes["receiverHerId"])
            }
        }

        afterEach { confirmVerified(auditLogger) }
    })
