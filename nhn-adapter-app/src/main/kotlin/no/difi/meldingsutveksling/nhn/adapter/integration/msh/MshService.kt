package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import java.util.Base64
import java.util.UUID
import mu.KotlinLogging
import no.difi.meldingsutveksling.nhn.adapter.errors.InvalidMshDataException
import no.difi.meldingsutveksling.nhn.adapter.errors.MessageTooLargeException
import no.difi.meldingsutveksling.nhn.adapter.errors.fromMshData
import no.difi.meldingsutveksling.nhn.adapter.extensions.toClientUuid
import no.difi.meldingsutveksling.nhn.adapter.messages.DialogmeldingKvitteringMessage
import no.difi.meldingsutveksling.nhn.adapter.messages.KvitteringStatusMessage
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.nhn.msh.ApplicationMetadata
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.CommunicationParty
import no.ks.fiks.nhn.msh.HelseIdTenantParameters
import no.ks.fiks.nhn.msh.HelseIdTokenParameters
import no.ks.fiks.nhn.msh.MessageWithMetadata
import no.ks.fiks.nhn.msh.MshInternalClient
import no.ks.fiks.nhn.msh.MultiTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.NotificationType
import no.ks.fiks.nhn.msh.RequestParameters
import no.ks.fiks.nhn.msh.SingleTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.StatusInfo
import no.ks.fiks.nhn.msh.UnreadNotification
import no.nhn.msh.v3.model.AppRecError
import no.nhn.msh.v3.model.AppRecStatus
import no.nhn.msh.v3.model.GetBusinessDocumentResponse
import no.nhn.msh.v3.model.PostAppRecRequest
import no.nhn.msh.v3.model.PostMessageRequest

/** The largest business document MSH accepts, Base64 encoded. */
const val MESSAGE_MAX_BYTES = 50 * 1000 * 1000

/**
 * The business document holds the documents of a message, the vedlegg Base64 encoded, and is Base64 encoded itself.
 * Documents adding up to more than 3/4 of [MESSAGE_MAX_BYTES] therefore make a message that MSH will not accept.
 */
const val DOCUMENTS_MAX_BYTES = MESSAGE_MAX_BYTES / 4 * 3

/** The most unread notifications MSH returns at a time. */
internal const val MAX_NOTIFICATIONS_TO_FETCH = 1000

private const val CONTENT_TYPE = "application/xml"
private const val CONTENT_TRANSFER_ENCODING = "base64"

private val log = KotlinLogging.logger {}

/**
 * Sends and receives messages through NHN's Message Service Handler (MSH) on behalf of the calling client.
 *
 * [notificationsToFetch] is how many unread notifications are read from MSH at a time, at most
 * [MAX_NOTIFICATIONS_TO_FETCH]. The messages to a receiver are listed from them, so it limits how many messages are
 * listed at a time.
 */
class MshService(
    private val mshClient: Client,
    private val internalClient: MshInternalClient,
    private val applicationMetadata: ApplicationMetadata,
    private val notificationsToFetch: Int,
) {
    init {
        require(notificationsToFetch in 1..MAX_NOTIFICATIONS_TO_FETCH) {
            "notificationsToFetch must be between 1 and $MAX_NOTIFICATIONS_TO_FETCH, but is $notificationsToFetch"
        }
    }

    /**
     * MSH has no list of the messages to a receiver: each new message is announced by a NewMessage notification, which
     * stays unread until it is deleted. The messages to [receiverHerId] are therefore the ones its unread NewMessage
     * notifications refer to, and are listed until [markMessageRead] deletes their notifications.
     *
     * The other notifications are about the state of sent messages, which is looked up with [getStatus] instead. They
     * are deleted, as they would otherwise take up the unread notifications and keep new messages from being listed.
     */
    suspend fun getMessagesWithMetadata(receiverHerId: Int, clientContext: ClientContext): List<MessageWithMetadata> {
        val requestParameters = requestParameters(clientContext)
        val (newMessages, others) =
            mshClient
                .getUnreadNotifications(listOf(receiverHerId), notificationsToFetch, requestParameters)
                .partition { it.type == NotificationType.NEW_MESSAGE }

        if (others.isNotEmpty()) {
            log.debug { "Deleting ${others.size} notifications about sent messages for $receiverHerId" }
            mshClient.deleteNotifications(others.map { it.notificationId }, requestParameters)
        }

        return newMessages
            .map { it.relatedMessageIdOrThrow() }
            .distinct()
            .map { mshClient.getMessage(it, requestParameters) }
    }

    /** Reads the sender from the message metadata in MSH. */
    suspend fun getSenderHerId(id: UUID, clientContext: ClientContext): Int {
        val message = internalClient.getMessage(id.toString(), requestParameters(clientContext))
        return message.senderHerId ?: throw InvalidMshDataException("Invalid data from MSH: Message $id has no sender")
    }

    suspend fun getStatus(id: UUID, clientContext: ClientContext): List<StatusInfo> =
        mshClient.getStatus(id.toString(), requestParameters(clientContext)).statusList.orEmpty()

    /**
     * Marks the message downloaded, which deletes its business document from MSH, and then deletes its NewMessage
     * notifications, so that it is no longer listed by [getMessagesWithMetadata]. The notifications are looked for
     * among as many as MSH returns, so that they are found also when [notificationsToFetch] is lower.
     */
    suspend fun markMessageRead(id: UUID, receiverHerId: Int, clientContext: ClientContext) {
        val requestParameters = requestParameters(clientContext)
        mshClient.markMessageDownloaded(id.toString(), receiverHerId, requestParameters)

        val notificationIds =
            mshClient
                .getUnreadNotifications(listOf(receiverHerId), MAX_NOTIFICATIONS_TO_FETCH, requestParameters)
                .filter { it.type == NotificationType.NEW_MESSAGE && it.relatedMessageId.equals(id.toString(), true) }
                .map { it.notificationId }
        if (notificationIds.isNotEmpty()) {
            mshClient.deleteNotifications(notificationIds, requestParameters)
        }
    }

    suspend fun getBusinessDocument(id: String, clientContext: ClientContext): BusinessDocumentResponse {
        val businessDocument =
            internalClient.getBusinessDocument(id.toClientUuid("id").toString(), requestParameters(clientContext))
        return fromMshData { IncomingBusinessDocumentMapper.toBusinessDocumentResponse(businessDocument.decodeXml()) }
    }

    suspend fun sendMessage(input: SendMessageInput, clientContext: ClientContext): String {
        // Checked before encoding, so that a message that is too large is not copied once more
        val businessDocument = BusinessDocumentSerializer.serializeNhnMessage(input).toByteArray()
        if (base64Length(businessDocument.size) > MESSAGE_MAX_BYTES) {
            throw MessageTooLargeException("Message exceeds the max size of $MESSAGE_MAX_BYTES bytes")
        }
        val encodedBusinessDocument = Base64.getEncoder().encodeToString(businessDocument)

        val id =
            internalClient.postMessage(
                PostMessageRequest()
                    .businessDocument(encodedBusinessDocument)
                    .senderHerId(herId(input.sender.child, input.sender.parent, "sender"))
                    .receiverHerIds(listOf(herId(input.receiver.child, input.receiver.parent, "receiver")))
                    .contentType(CONTENT_TYPE)
                    .contentTransferEncoding(CONTENT_TRANSFER_ENCODING)
                    .messageTypeIdentificator(BusinessDocumentSerializer.MESSAGE_TYPE.verdi)
                    .applicationName(applicationMetadata.applicationName)
                    .applicationVersion(applicationMetadata.applicationVersion),
                requestParameters(clientContext),
            )
        return id ?: throw InvalidMshDataException("Invalid data from MSH: No id for the sent message")
    }

    suspend fun sendApplicationReceipt(
        senderHerId: Int,
        receipt: DialogmeldingKvitteringMessage,
        clientContext: ClientContext,
    ): String {
        val id =
            internalClient.postAppRec(
                id = receipt.relatedToMessageId.toClientUuid("relatedToMessageId").toString(),
                request = receipt.toPostAppRecRequest(senderHerId, applicationMetadata),
                requestParams = requestParameters(clientContext),
            )
        return id ?: throw InvalidMshDataException("Invalid data from MSH: No id for the sent application receipt")
    }

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

/** The length of [size] bytes Base64 encoded, with padding. */
internal fun base64Length(size: Int): Long = (size + 2L) / 3 * 4

/** The HER-id a message is addressed with: the one of the child, such as a person, else the one of its parent. */
private fun herId(child: CommunicationParty, parent: CommunicationParty, role: String): Int =
    child.herId ?: parent.herId ?: throw IllegalArgumentException("The $role has no HER-id")

private fun UnreadNotification.relatedMessageIdOrThrow(): String =
    relatedMessageId
        ?: throw InvalidMshDataException("Invalid data from MSH: Notification $notificationId refers to no message")

private fun GetBusinessDocumentResponse.decodeXml(): String {
    require(contentTransferEncoding == CONTENT_TRANSFER_ENCODING) {
        "'$contentTransferEncoding' is not a supported transfer encoding"
    }
    require(contentType == CONTENT_TYPE) { "'$contentType' is not a supported content type" }
    return String(Base64.getDecoder().decode(businessDocument), Charsets.UTF_8)
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
