package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import java.util.Base64
import java.util.UUID
import no.difi.meldingsutveksling.domain.Iso6523
import no.difi.meldingsutveksling.nhn.adapter.errors.ClientInputException
import no.difi.meldingsutveksling.nhn.adapter.errors.InvalidMshDataException
import no.difi.meldingsutveksling.nhn.adapter.errors.MessageTooLargeException
import no.difi.meldingsutveksling.nhn.adapter.messages.DialogmeldingKvitteringMessage
import no.difi.meldingsutveksling.nhn.adapter.messages.DialogmeldingKvitteringStatus
import no.difi.meldingsutveksling.nhn.adapter.messages.KvitteringStatusMessage
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.DialogmeldingDeserializer
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.difi.move.common.dokumentpakking.domain.Document
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.hdir.OrganizationIdType
import no.ks.fiks.nhn.msh.ApplicationMetadata
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.GetStatusResponse
import no.ks.fiks.nhn.msh.MessageWithMetadata
import no.ks.fiks.nhn.msh.MshInternalClient
import no.ks.fiks.nhn.msh.MultiTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.NotificationType
import no.ks.fiks.nhn.msh.OrganizationCommunicationParty
import no.ks.fiks.nhn.msh.OrganizationId
import no.ks.fiks.nhn.msh.Patient
import no.ks.fiks.nhn.msh.Receiver
import no.ks.fiks.nhn.msh.RequestParameters
import no.ks.fiks.nhn.msh.Sender
import no.ks.fiks.nhn.msh.SingleTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.UnreadNotification
import no.nhn.msh.v3.model.AppRecStatus
import no.nhn.msh.v3.model.GetBusinessDocumentResponse
import no.nhn.msh.v3.model.GetMessageResponse as NhnMessage
import no.nhn.msh.v3.model.PostAppRecRequest
import no.nhn.msh.v3.model.PostMessageRequest
import org.springframework.core.io.ByteArrayResource

private const val CONSUMER_ORG_NUMBER = "123456789"
private const val SUPPLIER_ORG_NUMBER = "987654321"
private const val SENDER_HER_ID = 1000002
private const val NOTIFICATIONS_TO_FETCH = 50

class MshServiceTest :
    DescribeSpec({
        val mshClient = mockk<Client>()
        val internalClient = mockk<MshInternalClient>()
        val applicationMetadata = ApplicationMetadata("efm-nhn-adapter", "1.2.3")
        val mshService = MshService(mshClient, internalClient, applicationMetadata, NOTIFICATIONS_TO_FETCH)

        fun clientContext(supplier: String? = null) =
            mockk<ClientContext>().also {
                every { it.consumer } returns Iso6523.parse("0192:$CONSUMER_ORG_NUMBER")
                every { it.supplier } returns supplier?.let { orgNumber -> Iso6523.parse("0192:$orgNumber") }
            }

        describe("request parameters") {
            val messageId = UUID.randomUUID()

            it("uses a single-tenant HelseID token when the client acts on its own behalf") {
                val requestParameters = slot<RequestParameters>()
                coEvery { mshClient.getStatus(messageId.toString(), capture(requestParameters)) } returns
                    GetStatusResponse(null)

                mshService.getStatus(messageId, clientContext())

                val tenant = requestParameters.captured.helseId?.tenant
                tenant.shouldBeInstanceOf<SingleTenantHelseIdTokenParameters>()
                tenant.childOrganization shouldBe CONSUMER_ORG_NUMBER
            }

            it("uses a multi-tenant HelseID token for the consumer when the client acts through a supplier") {
                val requestParameters = slot<RequestParameters>()
                coEvery { mshClient.getStatus(messageId.toString(), capture(requestParameters)) } returns
                    GetStatusResponse(null)

                mshService.getStatus(messageId, clientContext(supplier = SUPPLIER_ORG_NUMBER))

                val tenant = requestParameters.captured.helseId?.tenant
                tenant.shouldBeInstanceOf<MultiTenantHelseIdTokenParameters>()
                // The consumer is the customer (parent) organization the supplier acts on behalf of
                tenant.parentOrganization shouldBe CONSUMER_ORG_NUMBER
                tenant.childOrganization.shouldBeNull()
            }
        }

        fun notification(type: NotificationType, messageId: String?) =
            UnreadNotification(
                notificationId = UUID.randomUUID().toString(),
                relatedMessageId = messageId,
                type = type,
                notificationReceiverHerId = SENDER_HER_ID,
                notificationTriggeredByHerId = null,
                description = null,
                createdAt = null,
            )

        fun metadata(id: String) =
            MessageWithMetadata(
                id = id,
                contentType = "application/xml",
                receiverHerIds = listOf(SENDER_HER_ID),
                senderHerId = 2000002,
                businessDocumentId = UUID.randomUUID().toString(),
                businessDocumentDate = null,
                businessDocumentMsgType = "DIALOG_HELSEFAGLIG",
            )

        describe("getMessagesWithMetadata") {
            beforeEach { clearMocks(mshClient) }

            it("rejects a number of notifications to fetch that MSH does not accept") {
                listOf(0, MAX_NOTIFICATIONS_TO_FETCH + 1).forEach { notificationsToFetch ->
                    shouldThrow<IllegalArgumentException> {
                        MshService(mshClient, internalClient, applicationMetadata, notificationsToFetch)
                    }
                }
            }

            it("returns the messages the unread NewMessage notifications refer to, each once") {
                val first = UUID.randomUUID().toString()
                val second = UUID.randomUUID().toString()
                coEvery { mshClient.getUnreadNotifications(listOf(SENDER_HER_ID), any(), any()) } returns
                    listOf(
                        notification(NotificationType.NEW_MESSAGE, first),
                        notification(NotificationType.NEW_MESSAGE, second),
                        notification(NotificationType.NEW_MESSAGE, first),
                    )
                coEvery { mshClient.getMessage(any(), any()) } answers { metadata(firstArg()) }

                mshService.getMessagesWithMetadata(SENDER_HER_ID, clientContext()).map { it.id } shouldBe
                    listOf(first, second)

                coVerify(exactly = 1) {
                    mshClient.getUnreadNotifications(listOf(SENDER_HER_ID), NOTIFICATIONS_TO_FETCH, any())
                }
                coVerify(exactly = 0) { mshClient.deleteNotifications(any(), any()) }
            }

            it("deletes the notifications about sent messages") {
                val messageId = UUID.randomUUID().toString()
                val others =
                    listOf(
                        notification(NotificationType.MESSAGE_SENT_STATE_UPDATED, UUID.randomUUID().toString()),
                        notification(NotificationType.MESSAGE_DELIVERY_STATE_UPDATED, UUID.randomUUID().toString()),
                        notification(NotificationType.MESSAGE_APPREC_INFO_UPDATED, UUID.randomUUID().toString()),
                        notification(NotificationType.REFUSED_MESSAGE, null),
                    )
                coEvery { mshClient.getUnreadNotifications(any(), any(), any()) } returns
                    others + notification(NotificationType.NEW_MESSAGE, messageId)
                coEvery { mshClient.deleteNotifications(any(), any()) } just Runs
                coEvery { mshClient.getMessage(any(), any()) } answers { metadata(firstArg()) }

                mshService.getMessagesWithMetadata(SENDER_HER_ID, clientContext()).map { it.id } shouldBe
                    listOf(messageId)

                coVerify(exactly = 1) { mshClient.deleteNotifications(others.map { it.notificationId }, any()) }
            }

            it("returns no messages when there are no unread notifications") {
                coEvery { mshClient.getUnreadNotifications(any(), any(), any()) } returns emptyList()

                mshService.getMessagesWithMetadata(SENDER_HER_ID, clientContext()) shouldBe emptyList()

                coVerify(exactly = 0) { mshClient.getMessage(any(), any()) }
            }

            it("reports a NewMessage notification without a message as invalid data from MSH") {
                coEvery { mshClient.getUnreadNotifications(any(), any(), any()) } returns
                    listOf(notification(NotificationType.NEW_MESSAGE, null))

                shouldThrow<InvalidMshDataException> {
                        mshService.getMessagesWithMetadata(SENDER_HER_ID, clientContext())
                    }
                    .message shouldContain "refers to no message"
            }
        }

        describe("markMessageRead") {
            val messageId = UUID.randomUUID()

            beforeEach { clearMocks(mshClient) }

            it("marks the message downloaded before deleting its NewMessage notifications") {
                val newMessage = notification(NotificationType.NEW_MESSAGE, messageId.toString())
                coEvery { mshClient.markMessageDownloaded(any(), any(), any()) } just Runs
                coEvery { mshClient.getUnreadNotifications(any(), any(), any()) } returns
                    listOf(
                        newMessage,
                        notification(NotificationType.NEW_MESSAGE, UUID.randomUUID().toString()),
                        notification(NotificationType.MESSAGE_DELIVERY_STATE_UPDATED, messageId.toString()),
                    )
                coEvery { mshClient.deleteNotifications(any(), any()) } just Runs

                mshService.markMessageRead(messageId, SENDER_HER_ID, clientContext())

                coVerifyOrder {
                    mshClient.markMessageDownloaded(messageId.toString(), SENDER_HER_ID, any())
                    mshClient.getUnreadNotifications(listOf(SENDER_HER_ID), MAX_NOTIFICATIONS_TO_FETCH, any())
                    mshClient.deleteNotifications(listOf(newMessage.notificationId), any())
                }
            }

            it("deletes nothing when the notification is already deleted") {
                coEvery { mshClient.markMessageDownloaded(any(), any(), any()) } just Runs
                coEvery { mshClient.getUnreadNotifications(any(), any(), any()) } returns emptyList()

                mshService.markMessageRead(messageId, SENDER_HER_ID, clientContext())

                coVerify(exactly = 0) { mshClient.deleteNotifications(any(), any()) }
            }
        }

        describe("getStatus") {
            it("returns no statuses when MSH has none") {
                coEvery { mshClient.getStatus(any(), any()) } returns GetStatusResponse(null)

                mshService.getStatus(UUID.randomUUID(), clientContext()) shouldBe emptyList()
            }
        }

        describe("getSenderHerId") {
            val messageId = UUID.randomUUID()

            it("returns the sender when MSH leaves out metadata it does not need") {
                coEvery { internalClient.getMessage(messageId.toString(), any()) } returns
                    NhnMessage().id(messageId.toString()).senderHerId(SENDER_HER_ID)

                mshService.getSenderHerId(messageId, clientContext()) shouldBe SENDER_HER_ID
            }

            it("reports a message without a sender as invalid data from MSH") {
                coEvery { internalClient.getMessage(messageId.toString(), any()) } returns
                    NhnMessage().id(messageId.toString()).receiverHerIds(setOf(SENDER_HER_ID))

                shouldThrow<InvalidMshDataException> { mshService.getSenderHerId(messageId, clientContext()) }
                    .message shouldContain "has no sender"
            }
        }

        describe("getBusinessDocument") {
            val messageId = UUID.randomUUID()

            it("rejects an unsupported transfer encoding") {
                coEvery { internalClient.getBusinessDocument(messageId.toString(), any()) } returns
                    GetBusinessDocumentResponse()
                        .contentType("application/xml")
                        .contentTransferEncoding("quoted-printable")
                        .businessDocument("")

                shouldThrow<InvalidMshDataException> {
                        mshService.getBusinessDocument(messageId.toString(), clientContext())
                    }
                    .message shouldContain "quoted-printable"
            }

            it("rejects an unsupported content type") {
                coEvery { internalClient.getBusinessDocument(messageId.toString(), any()) } returns
                    GetBusinessDocumentResponse()
                        .contentType("application/json")
                        .contentTransferEncoding("base64")
                        .businessDocument("")

                shouldThrow<InvalidMshDataException> {
                        mshService.getBusinessDocument(messageId.toString(), clientContext())
                    }
                    .message shouldContain "application/json"
            }

            it("reports a business document it cannot interpret as invalid data from MSH") {
                coEvery { internalClient.getBusinessDocument(messageId.toString(), any()) } returns
                    GetBusinessDocumentResponse()
                        .contentType("application/xml")
                        .contentTransferEncoding("base64")
                        .businessDocument(Base64.getEncoder().encodeToString("<Unknown/>".toByteArray()))

                shouldThrow<InvalidMshDataException> {
                        mshService.getBusinessDocument(messageId.toString(), clientContext())
                    }
                    .message shouldContain "Unknown"
            }

            it("rejects an id that is not a UUID as invalid client input") {
                shouldThrow<ClientInputException> { mshService.getBusinessDocument("not-a-uuid", clientContext()) }
                    .message shouldBe "id must be an UUID!"
            }
        }

        describe("sendApplicationReceipt") {
            val relatedToMessageId = UUID.randomUUID()
            val appRecId = UUID.randomUUID().toString()

            suspend fun sendReceipt(receipt: DialogmeldingKvitteringMessage): PostAppRecRequest {
                val request = slot<PostAppRecRequest>()
                coEvery { internalClient.postAppRec(relatedToMessageId.toString(), capture(request), any()) } returns
                    appRecId

                mshService.sendApplicationReceipt(SENDER_HER_ID, receipt, clientContext()) shouldBe appRecId
                request.captured.appRecSenderHerId shouldBe SENDER_HER_ID
                request.captured.applicationName shouldBe "efm-nhn-adapter"
                request.captured.applicationVersion shouldBe "1.2.3"
                return request.captured
            }

            it("maps the receipt status") {
                val request =
                    sendReceipt(
                        DialogmeldingKvitteringMessage(
                            relatedToMessageId = relatedToMessageId.toString(),
                            status = DialogmeldingKvitteringStatus.OK,
                            messages = null,
                        )
                    )

                request.appRecStatus shouldBe AppRecStatus.OK
                request.appRecErrorList.shouldBeNull()
            }

            it("maps known error codes to their code list values") {
                val error = FeilmeldingForApplikasjonskvittering.UGYLDIG_XML
                val request =
                    sendReceipt(
                        DialogmeldingKvitteringMessage(
                            relatedToMessageId = relatedToMessageId.toString(),
                            status = DialogmeldingKvitteringStatus.REJECTED,
                            messages = listOf(KvitteringStatusMessage(error.verdi, "Invalid XML")),
                        )
                    )

                request.appRecStatus shouldBe AppRecStatus.REJECTED
                request.appRecErrorList.shouldNotBeNull() shouldHaveSize 1
                with(request.appRecErrorList.shouldNotBeNull().single()) {
                    errorCode shouldBe error.verdi
                    description shouldBe error.navn
                    details shouldBe "Invalid XML"
                    oid shouldBe error.kodeverk
                }
            }

            it("maps unknown error codes to UKJENT") {
                val request =
                    sendReceipt(
                        DialogmeldingKvitteringMessage(
                            relatedToMessageId = relatedToMessageId.toString(),
                            status = DialogmeldingKvitteringStatus.OK_ERROR_IN_MESSAGE_PART,
                            messages = listOf(KvitteringStatusMessage("not-a-code", "Something went wrong")),
                        )
                    )

                request.appRecStatus shouldBe AppRecStatus.OK_ERROR_IN_MESSAGE_PART
                with(request.appRecErrorList.shouldNotBeNull().single()) {
                    errorCode shouldBe FeilmeldingForApplikasjonskvittering.UKJENT.verdi
                    details shouldBe "Something went wrong"
                }
            }
        }

        describe("sendMessage") {
            fun organization(herId: Int, name: String) =
                OrganizationCommunicationParty(
                    ids = listOf(OrganizationId(herId.toString(), OrganizationIdType.HER_ID)),
                    address = null,
                    name = name,
                )

            fun vedlegg(name: String, size: Int) =
                Document.builder()
                    .filename(name)
                    .resource(ByteArrayResource(ByteArray(size)))
                    .mimeType("application/pdf")
                    .build()

            fun input(vedlegg: List<Document>) =
                SendMessageInput(
                    id = UUID.randomUUID(),
                    sender = Sender(organization(2000001, "Legekontoret"), organization(2000003, "Resepsjonen")),
                    receiver =
                        Receiver(
                            organization(1000001, "Test kommune"),
                            organization(1000002, "Helse og omsorg"),
                            Patient("16822449879", "Kari", null, "Nordmann"),
                        ),
                    dialogmelding = DialogmeldingDeserializer.deserializeDialogmelding(DIALOGMELDING),
                    vedlegg = vedlegg,
                    metadataFiler = null,
                    conversationRef = null,
                )

            it("sends the business document Base64 encoded") {
                val request = slot<PostMessageRequest>()
                val mshMessageId = UUID.randomUUID().toString()
                coEvery { internalClient.postMessage(capture(request), any()) } returns mshMessageId

                mshService.sendMessage(input(listOf(vedlegg("vedlegg.pdf", 10))), clientContext()) shouldBe
                    mshMessageId

                request.captured.contentTransferEncoding shouldBe "base64"
                String(Base64.getDecoder().decode(request.captured.businessDocument)) shouldContain "MsgHead"
                // The HER-ids of the children, which the message is addressed to
                request.captured.senderHerId shouldBe 2000003
                request.captured.receiverHerIds shouldBe listOf(1000002)
                request.captured.messageTypeIdentificator shouldBe "DIALOG_HELSEFAGLIG"
                request.captured.applicationName shouldBe "efm-nhn-adapter"
                request.captured.applicationVersion shouldBe "1.2.3"
            }

            it("reports a message MSH gives no id as invalid data from MSH") {
                coEvery { internalClient.postMessage(any(), any()) } returns null

                shouldThrow<InvalidMshDataException> {
                    mshService.sendMessage(input(listOf(vedlegg("vedlegg.pdf", 10))), clientContext())
                }
            }

            it("rejects attachments that are each within the limits, but too large together once encoded") {
                clearMocks(internalClient)
                // Within DOCUMENTS_MAX_BYTES, but Base64 encoded in the MsgHead and around it
                val vedlegg = listOf(vedlegg("a.pdf", 15_000_000), vedlegg("b.pdf", 15_000_000))

                shouldThrow<MessageTooLargeException> { mshService.sendMessage(input(vedlegg), clientContext()) }
                    .message shouldBe "Message exceeds the max size of $MESSAGE_MAX_BYTES bytes"

                coVerify(exactly = 0) { internalClient.postMessage(any(), any()) }
            }
        }

        describe("base64Length") {
            it("rounds up to whole blocks of four characters, padding included") {
                base64Length(0) shouldBe 0
                base64Length(1) shouldBe 4
                base64Length(3) shouldBe 4
                base64Length(4) shouldBe 8
            }

            it("allows exactly MESSAGE_MAX_BYTES encoded") {
                base64Length(MESSAGE_MAX_BYTES / 4 * 3) shouldBe MESSAGE_MAX_BYTES.toLong()
                base64Length(MESSAGE_MAX_BYTES / 4 * 3 + 1) shouldBe MESSAGE_MAX_BYTES + 4L
            }
        }
    })

private val DIALOGMELDING: String =
    MshServiceTest::class.java.getResource("/dialogmelding/dialogmelding.xml")!!.readText()
