package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import java.util.Base64
import java.util.UUID
import no.difi.meldingsutveksling.nhn.adapter.model.BusinessDocumentResponse
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.BusinessDocumentDeserializer
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.HelseIdTenantParameters
import no.ks.fiks.nhn.msh.HelseIdTokenParameters
import no.ks.fiks.nhn.msh.IncomingApplicationReceipt
import no.ks.fiks.nhn.msh.MessageWithMetadata
import no.ks.fiks.nhn.msh.MshInternalClient
import no.ks.fiks.nhn.msh.MultiTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.OutgoingApplicationReceipt
import no.ks.fiks.nhn.msh.RequestParameters
import no.ks.fiks.nhn.msh.SingleTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.StatusInfo
import no.nhn.msh.v2.model.PostMessageRequest

private const val MESSAGE_MAX_BYTES = 50 * 1000 * 1000

private const val CONTENT_TYPE = "application/xml"
private const val CONTENT_TRANSFER_ENCODING = "base64"

class MshService(private val mshClient: Client, private val internalClient: MshInternalClient) {
    suspend fun sendMessage(businessDocument: SendMessageInput, clientContext: ClientContext): UUID {
        val businessDocument = BusinessDocumentSerializer.serializeNhnMessage(businessDocument)
        val encodedBusinessDocument = Base64.getEncoder().encodeToString(businessDocument.toByteArray())

        if (encodedBusinessDocument.length > MESSAGE_MAX_BYTES) {
            throw IllegalArgumentException("Message too large $MESSAGE_MAX_BYTES bytes")
        }

        return internalClient.postMessage(
            PostMessageRequest()
                .contentType(CONTENT_TYPE)
                .contentTransferEncoding(CONTENT_TRANSFER_ENCODING)
                .businessDocument(encodedBusinessDocument),
            getRequestParameters(clientContext),
        )
    }

    suspend fun getBusinessDocument(id: UUID, clientContext: ClientContext): BusinessDocumentResponse {
        internalClient.getBusinessDocument(id, getRequestParameters(clientContext)).let {
            if (it.contentTransferEncoding != CONTENT_TRANSFER_ENCODING) {
                throw IllegalArgumentException("'${it.contentTransferEncoding}' is not a supported transfer encoding")
            }
            if (it.contentType != CONTENT_TYPE) {
                throw IllegalArgumentException("'${it.contentType}' is not a supported content type")
            }
            val xml = String(Base64.getDecoder().decode(it.businessDocument))
            return BusinessDocumentDeserializer.deserializeMsgHead(xml)
        }
    }

    suspend fun getMessagesWithMetadata(receiverHerId: Int, clientContext: ClientContext): List<MessageWithMetadata> =
        mshClient.getMessagesWithMetadata(receiverHerId, getRequestParameters(clientContext))

    suspend fun getApplicationReceipt(id: UUID, clientContext: ClientContext): IncomingApplicationReceipt =
        mshClient.getApplicationReceipt(id, getRequestParameters(clientContext))

    suspend fun sendApplicationReceipt(receipt: OutgoingApplicationReceipt, clientContext: ClientContext): UUID =
        mshClient.sendApplicationReceipt(receipt, getRequestParameters(clientContext))

    suspend fun markMessageRead(id: UUID, receiverHerId: Int, clientContext: ClientContext) =
        mshClient.markMessageRead(id, receiverHerId, getRequestParameters(clientContext))

    suspend fun getStatus(id: UUID, clientContext: ClientContext): List<StatusInfo> =
        mshClient.getStatus(id, getRequestParameters(clientContext))

    private fun getRequestParameters(clientContext: ClientContext): RequestParameters =
        RequestParameters(HelseIdTokenParameters(getHelseIdTenantParameters(clientContext)))

    private fun getHelseIdTenantParameters(clientContext: ClientContext): HelseIdTenantParameters {
        val parentOrganization = clientContext.supplier?.organizationIdentifier
        val childOrganization = clientContext.consumer.organizationIdentifier

        return if (parentOrganization != null) {
            MultiTenantHelseIdTokenParameters(childOrganization)
        } else {
            SingleTenantHelseIdTokenParameters(childOrganization)
        }
    }
}
