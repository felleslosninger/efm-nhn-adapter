package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import java.util.Base64
import java.util.UUID
import no.difi.meldingsutveksling.nhn.adapter.messages.DialogmeldingKvitteringMessage
import no.difi.meldingsutveksling.nhn.adapter.messages.KvitteringStatusMessage
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.HelseIdTenantParameters
import no.ks.fiks.nhn.msh.HelseIdTokenParameters
import no.ks.fiks.nhn.msh.MessageWithMetadata
import no.ks.fiks.nhn.msh.MshInternalClient
import no.ks.fiks.nhn.msh.MultiTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.RequestParameters
import no.ks.fiks.nhn.msh.SingleTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.StatusInfo
import no.nhn.msh.v2.model.AppRecError
import no.nhn.msh.v2.model.AppRecStatus
import no.nhn.msh.v2.model.GetBusinessDocumentResponse
import no.nhn.msh.v2.model.PostAppRecRequest
import no.nhn.msh.v2.model.PostMessageRequest

private const val MESSAGE_MAX_BYTES = 50 * 1000 * 1000

private const val CONTENT_TYPE = "application/xml"
private const val CONTENT_TRANSFER_ENCODING = "base64"

/** Sends and receives messages through NHN's Message Service Handler (MSH) on behalf of the calling client. */
class MshService(private val mshClient: Client, private val internalClient: MshInternalClient) {
    suspend fun getMessagesWithMetadata(receiverHerId: Int, clientContext: ClientContext): List<MessageWithMetadata> =
        mshClient.getMessagesWithMetadata(receiverHerId, requestParameters(clientContext))

    suspend fun getStatus(id: UUID, clientContext: ClientContext): List<StatusInfo> =
        mshClient.getStatus(id, requestParameters(clientContext))

    suspend fun markMessageRead(id: UUID, receiverHerId: Int, clientContext: ClientContext) =
        mshClient.markMessageRead(id, receiverHerId, requestParameters(clientContext))

    suspend fun getBusinessDocument(id: String, clientContext: ClientContext): BusinessDocumentResponse {
        val businessDocument =
            internalClient.getBusinessDocument(UUID.fromString(id), requestParameters(clientContext))
        return IncomingBusinessDocumentMapper.toBusinessDocumentResponse(businessDocument.decodeXml())
    }

    suspend fun sendMessage(input: SendMessageInput, clientContext: ClientContext): UUID {
        val businessDocument = BusinessDocumentSerializer.serializeNhnMessage(input)
        val encodedBusinessDocument = Base64.getEncoder().encodeToString(businessDocument.toByteArray())
        require(encodedBusinessDocument.length <= MESSAGE_MAX_BYTES) {
            "Message exceeds the max size of $MESSAGE_MAX_BYTES bytes"
        }

        return internalClient.postMessage(
            PostMessageRequest()
                .contentType(CONTENT_TYPE)
                .contentTransferEncoding(CONTENT_TRANSFER_ENCODING)
                .businessDocument(encodedBusinessDocument),
            requestParameters(clientContext),
        )
    }

    suspend fun sendApplicationReceipt(
        senderHerId: Int,
        receipt: DialogmeldingKvitteringMessage,
        clientContext: ClientContext,
    ): UUID =
        internalClient.postAppRec(
            id = UUID.fromString(receipt.relatedToMessageId),
            senderHerId = senderHerId,
            request = receipt.toPostAppRecRequest(),
            requestParams = requestParameters(clientContext),
        )

    private fun requestParameters(clientContext: ClientContext): RequestParameters =
        RequestParameters(HelseIdTokenParameters(tenantParameters(clientContext)))

    /** A client acting through a supplier (e.g. a system vendor) needs a multi-tenant HelseID token. */
    private fun tenantParameters(clientContext: ClientContext): HelseIdTenantParameters {
        val childOrganization = clientContext.consumer.organizationIdentifier
        return if (clientContext.supplier != null) {
            MultiTenantHelseIdTokenParameters(childOrganization)
        } else {
            SingleTenantHelseIdTokenParameters(childOrganization)
        }
    }
}

private fun GetBusinessDocumentResponse.decodeXml(): String {
    require(contentTransferEncoding == CONTENT_TRANSFER_ENCODING) {
        "'$contentTransferEncoding' is not a supported transfer encoding"
    }
    require(contentType == CONTENT_TYPE) { "'$contentType' is not a supported content type" }
    return String(Base64.getDecoder().decode(businessDocument), Charsets.UTF_8)
}

private fun DialogmeldingKvitteringMessage.toPostAppRecRequest(): PostAppRecRequest =
    PostAppRecRequest()
        .appRecStatus(AppRecStatus.valueOf(status.name))
        .appRecErrorList(messages?.map { it.toAppRecError() })

private fun KvitteringStatusMessage.toAppRecError(): AppRecError {
    val error =
        FeilmeldingForApplikasjonskvittering.entries.firstOrNull { it.verdi == code }
            ?: FeilmeldingForApplikasjonskvittering.UKJENT

    return AppRecError().errorCode(error.verdi).details(text).description(error.navn).oid(error.kodeverk)
}
