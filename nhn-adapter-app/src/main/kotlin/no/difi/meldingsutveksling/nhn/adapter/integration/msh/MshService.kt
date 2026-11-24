package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import io.ktor.client.call.NoTransformationFoundException
import io.ktor.serialization.ContentConvertException
import java.util.Base64
import java.util.UUID
import no.difi.meldingsutveksling.nhn.adapter.errors.InvalidIncomingDocumentException
import no.difi.meldingsutveksling.nhn.adapter.errors.InvalidMshDataException
import no.difi.meldingsutveksling.nhn.adapter.errors.MessageTooLargeException
import no.difi.meldingsutveksling.nhn.adapter.errors.fromMshData
import no.difi.meldingsutveksling.nhn.adapter.extensions.toClientUuid
import no.difi.meldingsutveksling.nhn.adapter.messages.DialogmeldingKvitteringMessage
import no.difi.meldingsutveksling.nhn.adapter.messages.KvitteringStatusMessage
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptException
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.nhn.msh.ApplicationMetadata
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.CommunicationParty
import no.ks.fiks.nhn.msh.HelseIdTenantParameters
import no.ks.fiks.nhn.msh.HelseIdTokenParameters
import no.ks.fiks.nhn.msh.MessageWithMetadata
import no.ks.fiks.nhn.msh.MshConfiguration
import no.ks.fiks.nhn.msh.MshInternalClient
import no.ks.fiks.nhn.msh.MultiTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.RequestParameters
import no.ks.fiks.nhn.msh.SingleTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.StatusInfo
import no.ks.fiks.nhn.msh.UnreadNotification
import no.nhn.msh.v3.model.AppRecError
import no.nhn.msh.v3.model.AppRecStatus
import no.nhn.msh.v3.model.GetBusinessDocumentResponse
import no.nhn.msh.v3.model.PostAppRecRequest
import no.nhn.msh.v3.model.PostMessageRequest

/** The largest request MSH accepts to send a message (API version 3), counted in decimal megabytes to be safe. */
const val MSH_REQUEST_MAX_BYTES = 35 * 1000 * 1000

/**
 * The largest business document MSH accepts, Base64 encoded: the request to MSH also holds the sender, receivers and
 * other metadata, which a margin is left for. Divisible by 4, as Base64 is.
 */
const val MESSAGE_MAX_BYTES = MSH_REQUEST_MAX_BYTES - 64 * 1000

/**
 * The business document holds the documents of a message, the vedlegg Base64 encoded, and is Base64 encoded itself.
 * Documents adding up to more than 3/4 of [MESSAGE_MAX_BYTES] therefore make a message that MSH will not accept.
 */
const val DOCUMENTS_MAX_BYTES = MESSAGE_MAX_BYTES / 4 * 3

/** The most unread notifications MSH returns at a time. */
const val MAX_NOTIFICATIONS_TO_FETCH = 1000

/** The most notifications MSH deletes at a time. */
const val MAX_NOTIFICATIONS_TO_DELETE = 1000

private const val CONTENT_TYPE = "application/xml"
private const val CONTENT_TRANSFER_ENCODING = "base64"

/**
 * Sends and receives messages through NHN's Message Service Handler (MSH) on behalf of the calling client.
 *
 * Incoming messages are found the way MSH gives them: each new message is announced by a NewMessage notification,
 * which stays unread until the client deletes it. The notifications are passed on as they are, so that the client
 * decides what to do with each of them.
 */
class MshService(
    private val mshClient: Client,
    private val internalClient: MshInternalClient,
    private val applicationMetadata: ApplicationMetadata,
) {
    suspend fun getUnreadNotifications(
        herId: Int,
        notificationsToFetch: Int,
        clientContext: ClientContext,
    ): List<UnreadNotification> =
        fromMshResponse(clientContext) { mshClient.getUnreadNotifications(listOf(herId), notificationsToFetch, it) }

    suspend fun deleteNotifications(notificationIds: List<String>, clientContext: ClientContext) =
        fromMshResponse(clientContext) { mshClient.deleteNotifications(notificationIds, it) }

    /**
     * Registers the MSH configuration of the HER-ids, which MSH requires of every HER-id it handles messages for. A
     * configuration already registered is replaced.
     */
    suspend fun setConfigurations(configurations: List<MshConfiguration>, clientContext: ClientContext) =
        fromMshResponse(clientContext) { mshClient.setMshConfigurations(configurations, it) }

    /** As [setConfigurations], in the HelseID tenant of the organization that owns the HER-ids. */
    suspend fun setConfigurations(configurations: List<MshConfiguration>, organizationNumber: String) =
        fromMshResponse(requestParameters(organizationNumber)) { mshClient.setMshConfigurations(configurations, it) }

    /** Deletes the MSH configuration of the HER-id, in the HelseID tenant of the organization that owns it. */
    suspend fun deleteConfiguration(herId: Int, organizationNumber: String) =
        fromMshResponse(requestParameters(organizationNumber)) { mshClient.deleteMshConfigurations(listOf(herId), it) }

    suspend fun getMessage(id: UUID, clientContext: ClientContext): MessageWithMetadata =
        fromMshResponse(clientContext) { mshClient.getMessage(id.toString(), it) }

    /** Reads the sender from the message metadata in MSH. */
    suspend fun getSenderHerId(id: UUID, clientContext: ClientContext): Int {
        val message = fromMshResponse(clientContext) { internalClient.getMessage(id.toString(), it) }
        return message.senderHerId ?: throw InvalidMshDataException("Invalid data from MSH: Message $id has no sender")
    }

    suspend fun getStatus(id: UUID, clientContext: ClientContext): List<StatusInfo> =
        fromMshResponse(clientContext) { mshClient.getStatus(id.toString(), it).statusList.orEmpty() }

    /**
     * Marks the message downloaded by [receiverHerId], which deletes its business document from MSH. Its NewMessage
     * notification is left for the client to delete.
     */
    suspend fun markMessageDownloaded(id: UUID, receiverHerId: Int, clientContext: ClientContext) =
        fromMshResponse(clientContext) { mshClient.markMessageDownloaded(id.toString(), receiverHerId, it) }

    suspend fun getBusinessDocument(id: String, clientContext: ClientContext): BusinessDocumentResponse {
        val messageId = id.toClientUuid("id").toString()
        val businessDocument = fromMshResponse(clientContext) { internalClient.getBusinessDocument(messageId, it) }
        val bytes = fromMshData { businessDocument.decodeBytes() }
        return fromMshData {
            try {
                IncomingBusinessDocumentMapper.toBusinessDocumentResponse(decodeText(bytes))
            } catch (e: ApplicationReceiptException) {
                // The sender's document is invalid, which trying again does not fix
                throw InvalidIncomingDocumentException(e, isAppRec = isAppRec(bytes))
            }
        }
    }

    suspend fun sendMessage(input: SendMessageInput, clientContext: ClientContext): String {
        // Checked before encoding, so that a message that is too large is not copied once more
        val businessDocument = BusinessDocumentSerializer.serializeNhnMessage(input).toByteArray()
        if (base64Length(businessDocument.size) > MESSAGE_MAX_BYTES) {
            throw MessageTooLargeException("Message exceeds the max size of $MESSAGE_MAX_BYTES bytes")
        }
        val encodedBusinessDocument = Base64.getEncoder().encodeToString(businessDocument)

        val request =
            PostMessageRequest()
                .businessDocument(encodedBusinessDocument)
                .senderHerId(herId(input.sender.child, input.sender.parent, "sender"))
                .receiverHerIds(listOf(herId(input.receiver.child, input.receiver.parent, "receiver")))
                .contentType(CONTENT_TYPE)
                .contentTransferEncoding(CONTENT_TRANSFER_ENCODING)
                .messageTypeIdentificator(BusinessDocumentSerializer.MESSAGE_TYPE.verdi)
                .applicationName(applicationMetadata.applicationName)
                .applicationVersion(applicationMetadata.applicationVersion)
        val id = fromMshResponse(clientContext) { internalClient.postMessage(request, it) }
        return id ?: throw InvalidMshDataException("Invalid data from MSH: No id for the sent message")
    }

    suspend fun sendApplicationReceipt(
        senderHerId: Int,
        receipt: DialogmeldingKvitteringMessage,
        clientContext: ClientContext,
    ): String {
        val messageId = receipt.relatedToMessageId.toClientUuid("relatedToMessageId").toString()
        val request = receipt.toPostAppRecRequest(senderHerId, applicationMetadata)
        val id = fromMshResponse(clientContext) { internalClient.postAppRec(messageId, request, it) }
        return id ?: throw InvalidMshDataException("Invalid data from MSH: No id for the sent application receipt")
    }

    /**
     * Calls MSH through msh-client with the request parameters for [clientContext], reporting a response that cannot
     * be read as [InvalidMshDataException]. [block] must hold the call only, so that the errors caught can only come
     * from the response.
     *
     * msh-client reads the response after the call, so a body that is not the expected JSON throws a ktor
     * [ContentConvertException] or [NoTransformationFoundException]. It also maps some responses without checking that
     * MSH gave the required fields, so a field MSH leaves out throws a [NullPointerException]. A failing call throws
     * an `MshException`, which passes through.
     *
     * When MSH has accepted a message to send, but its answer cannot be read, the message is sent although this
     * reports an error. A client that sends it again may send it twice.
     */
    private inline fun <T> fromMshResponse(clientContext: ClientContext, block: (RequestParameters) -> T): T =
        fromMshResponse(requestParameters(clientContext), block)

    private inline fun <T> fromMshResponse(requestParameters: RequestParameters, block: (RequestParameters) -> T): T =
        try {
            block(requestParameters)
        } catch (e: NullPointerException) {
            throw InvalidMshDataException("Invalid data from MSH: A required field is missing", e)
        } catch (e: ContentConvertException) {
            throw InvalidMshDataException("Invalid data from MSH: The response could not be read", e)
        } catch (e: NoTransformationFoundException) {
            throw InvalidMshDataException("Invalid data from MSH: The response could not be read", e)
        }

    private fun requestParameters(clientContext: ClientContext): RequestParameters =
        RequestParameters(HelseIdTokenParameters(tenantParameters(clientContext)))

    /** For a call made by the adapter itself, e.g. for an administrator, in the HelseID tenant of the organization. */
    private fun requestParameters(organizationNumber: String): RequestParameters =
        RequestParameters(HelseIdTokenParameters(SingleTenantHelseIdTokenParameters(organizationNumber)))

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

/** The length of [size] bytes Base64 encoded, with padding. */
internal fun base64Length(size: Int): Long = (size + 2L) / 3 * 4

/** The HER-id a message is addressed with: the one of the child, such as a person, else the one of its parent. */
private fun herId(child: CommunicationParty, parent: CommunicationParty, role: String): Int =
    child.herId ?: parent.herId ?: throw IllegalArgumentException("The $role has no HER-id")

/** The bytes of the business document. A document that is missing is MSH's fault, not the sender's. */
private fun GetBusinessDocumentResponse.decodeBytes(): ByteArray {
    require(contentTransferEncoding == CONTENT_TRANSFER_ENCODING) {
        "'$contentTransferEncoding' is not a supported transfer encoding"
    }
    require(contentType == CONTENT_TYPE) { "'$contentType' is not a supported content type" }
    val bytes = Base64.getDecoder().decode(businessDocument.orEmpty())
    require(bytes.any { !it.toInt().toChar().isWhitespace() }) { "The business document is empty" }
    return bytes
}

private fun DialogmeldingKvitteringMessage.toPostAppRecRequest(
    senderHerId: Int,
    applicationMetadata: ApplicationMetadata,
): PostAppRecRequest =
    PostAppRecRequest()
        .appRecSenderHerId(senderHerId)
        .appRecStatus(AppRecStatus.valueOf(status.name))
        .appRecErrorList(messages?.map { it.toAppRecError() })
        .applicationName(applicationMetadata.applicationName)
        .applicationVersion(applicationMetadata.applicationVersion)

private fun KvitteringStatusMessage.toAppRecError(): AppRecError {
    val error =
        FeilmeldingForApplikasjonskvittering.entries.firstOrNull { it.verdi == code }
            ?: FeilmeldingForApplikasjonskvittering.UKJENT

    return AppRecError().errorCode(error.verdi).details(text).description(error.navn).oid(error.kodeverk)
}
