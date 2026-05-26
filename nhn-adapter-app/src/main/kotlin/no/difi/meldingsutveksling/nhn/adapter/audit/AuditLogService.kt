package no.difi.meldingsutveksling.nhn.adapter.audit

import java.util.UUID
import no.difi.meldingsutveksling.nhn.adapter.model.OutgoingApplicationReceipt
import no.difi.meldingsutveksling.nhn.adapter.model.OutgoingBusinessDocument
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.idporten.logging.audit.AuditEntry
import no.idporten.logging.audit.AuditLogger

class AuditLogService(private val auditLogger: AuditLogger) {
    fun arLookup(identifier: String, clientContext: ClientContext) =
        auditLogger.log(
            AuditEntry.builder()
                .auditId(NHNAdapterAuditIdentifier.AR_LOOKUP)
                .clientContext(clientContext)
                .attribute("identifier", identifier)
                .build()
        )

    fun getStatus(messageId: UUID, clientContext: ClientContext) =
        auditLogger.log(
            AuditEntry.builder()
                .auditId(NHNAdapterAuditIdentifier.GET_STATUS)
                .clientContext(clientContext)
                .attribute("messageId", messageId)
                .build()
        )

    fun sendApplicationReceipt(
        receipt: OutgoingApplicationReceipt,
        messageReference: UUID,
        clientContext: ClientContext,
    ) =
        auditLogger.log(
            AuditEntry.builder()
                .auditId(NHNAdapterAuditIdentifier.SEND_APPLICATION_RECEIPT)
                .clientContext(clientContext)
                .attribute("senderHerId", receipt.senderHerId)
                .attribute("status", receipt.payload.status)
                .attribute("relatedToMessageId", receipt.payload.relatedToMessageId)
                .attribute("messageReference", messageReference)
                .build()
        )

    fun sendMessage(
        outgoingBusinessDocument: OutgoingBusinessDocument,
        messageReference: UUID,
        clientContext: ClientContext,
    ) =
        auditLogger.log(
            AuditEntry.builder()
                .auditId(NHNAdapterAuditIdentifier.SEND_MESSAGE)
                .clientContext(clientContext)
                .attribute("messageId", outgoingBusinessDocument.messageId)
                .attribute("conversationId", outgoingBusinessDocument.conversationId)
                .attribute("parentId", outgoingBusinessDocument.parentId)
                .attribute("senderHerId", outgoingBusinessDocument.senderHerId)
                .attribute("receiverHerId", outgoingBusinessDocument.receiverHerId)
                .attribute("messageReference", messageReference)
                .build()
        )

    fun getMessagesWithMetadata(receiverHerId: Int, clientContext: ClientContext) =
        auditLogger.log(
            AuditEntry.builder()
                .auditId(NHNAdapterAuditIdentifier.GET_MESSAGES_WITH_METADATA)
                .clientContext(clientContext)
                .attribute("receiverHerId", receiverHerId)
                .build()
        )

    fun getApplicationReceipt(id: UUID, clientContext: ClientContext) =
        auditLogger.log(
            AuditEntry.builder()
                .auditId(NHNAdapterAuditIdentifier.GET_APPLICATION_RECEIPT)
                .clientContext(clientContext)
                .attribute("id", id)
                .build()
        )

    fun getBusinessDocument(id: UUID, clientContext: ClientContext) =
        auditLogger.log(
            AuditEntry.builder()
                .auditId(NHNAdapterAuditIdentifier.GET_BUSINESS_DOCUMENT)
                .clientContext(clientContext)
                .attribute("id", id)
                .build()
        )

    fun markMessageRead(messageId: UUID, receiverHerId: Int, clientContext: ClientContext) =
        auditLogger.log(
            AuditEntry.builder()
                .auditId(NHNAdapterAuditIdentifier.MARK_MESSAGE_AS_READ)
                .clientContext(clientContext)
                .attribute("messageId", messageId)
                .attribute("receiverHerId", receiverHerId)
                .build()
        )
}

private fun AuditEntry.AuditEntryBuilder.clientContext(clientContext: ClientContext): AuditEntry.AuditEntryBuilder =
    this.clientOnbehalfofOrgno(clientContext.onBehalfOfOrgNumber)
        .clientId(clientContext.clientId)
        .clientOrgno(clientContext.orgNumber)
        .scopes(clientContext.scopes)
