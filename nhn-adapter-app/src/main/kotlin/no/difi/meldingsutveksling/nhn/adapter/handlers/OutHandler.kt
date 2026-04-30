package no.difi.meldingsutveksling.nhn.adapter.handlers

import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import no.difi.meldingsutveksling.nhn.adapter.extensions.textPlain
import no.difi.meldingsutveksling.nhn.adapter.extensions.toReceiver
import no.difi.meldingsutveksling.nhn.adapter.extensions.toSender
import no.difi.meldingsutveksling.nhn.adapter.integration.adresseregister.AdresseregisteretService
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.MshService
import no.difi.meldingsutveksling.nhn.adapter.logger
import no.difi.meldingsutveksling.nhn.adapter.model.Dialogmelding
import no.difi.meldingsutveksling.nhn.adapter.model.MessageStatus
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartNames
import no.difi.meldingsutveksling.nhn.adapter.model.OutgoingApplicationReceipt
import no.difi.meldingsutveksling.nhn.adapter.model.OutgoingBusinessDocument
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.jsonParser
import no.difi.meldingsutveksling.nhn.adapter.model.toMessageStatus
import no.difi.meldingsutveksling.nhn.adapter.model.toOriginal
import no.difi.meldingsutveksling.nhn.adapter.orElseThrowNotFound
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.difi.meldingsutveksling.nhn.adapter.security.SecurityService
import no.difi.move.common.dokumentpakking.domain.Document
import no.difi.move.common.io.ResourceUtils
import no.ks.fiks.hdir.Helsepersonell
import no.ks.fiks.hdir.HelsepersonellsFunksjoner
import no.ks.fiks.nhn.ar.AddressComponent
import no.ks.fiks.nhn.ar.PersonCommunicationParty
import no.ks.fiks.nhn.msh.ConversationRef
import no.ks.fiks.nhn.msh.DialogmeldingVersion
import no.ks.fiks.nhn.msh.HealthcareProfessional
import no.ks.fiks.nhn.msh.HttpClientException
import no.ks.fiks.nhn.msh.OutgoingVedlegg
import no.ks.fiks.nhn.msh.RecipientContact
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.json

class OutHandler(
    private val mshService: MshService,
    private val parcelService: ParcelService,
    private val securityService: SecurityService,
    private val adresseregisteretService: AdresseregisteretService,
) {
    suspend fun getStatus(messageId: UUID, clientContext: ClientContext): ServerResponse {
        try {
            val statuses = mshService.getStatus(messageId, clientContext).map { it.toMessageStatus() }
            return ServerResponse.ok().json().bodyValueAndAwait(statuses)
        } catch (e: HttpClientException) {
            if (e.status == 404) {
                return ServerResponse.ok().json().bodyValueAndAwait(Collections.emptyList<MessageStatus>())
            }

            throw e
        }
    }

    suspend fun sendApplicationReceipt(request: ServerRequest, clientContext: ClientContext): ServerResponse {
        val jweToken = request.awaitBody<String>()
        val payload = parcelService.decryptAndVerify(jweToken, clientContext)
        val receipt = jsonParser.decodeFromString<OutgoingApplicationReceipt>(payload)
        val messageReference = mshService.sendApplicationReceipt(receipt.toOriginal(), clientContext)
        return ServerResponse.ok().textPlain().bodyValueAndAwait(messageReference.toString())
    }

    suspend fun sendMessage(request: ServerRequest, clientContext: ClientContext): ServerResponse {
        logger.info("Starting sendMessage")

        val multipartData = request.multipartData().awaitSingle()

        val jweToken =
            multipartData
                .getFirst(MultipartNames.FORRETNINGSMELDING)!!
                .content()
                .awaitSingle()
                .toString(StandardCharsets.UTF_8)
        val payload = parcelService.decryptAndVerify(jweToken, clientContext)
        val outgoingBusinessDocument = jsonParser.decodeFromString<OutgoingBusinessDocument>(payload)
        val dialogmeldingMessage = outgoingBusinessDocument.payload

        val dokumentpakke = multipartData.getFirst(MultipartNames.DOKUMENTPAKKE)!!

        val messageReference =
            withContext(Dispatchers.IO) {
                dokumentpakke.content().awaitSingle().asInputStream().use { inputStream ->
                    val asicFiles = parcelService.getAttachments(inputStream)

                    val dialogmelding = getDialogmelding(asicFiles, dialogmeldingMessage.hoveddokument)
                    val vedlegg = getVedlegg(asicFiles, dialogmeldingMessage.hoveddokument)

                    val sender =
                        securityService
                            .assertAccess(
                                clientContext,
                                adresseregisteretService.lookupByHerId(outgoingBusinessDocument.senderHerId),
                            )
                            .toSender()

                    val receiver =
                        adresseregisteretService
                            .lookupByHerId(outgoingBusinessDocument.receiverHerId)
                            .toReceiver(dialogmeldingMessage.pasient!!)

                    val outGoingDocument =
                        no.ks.fiks.nhn.msh.OutgoingBusinessDocument(
                            UUID.fromString(outgoingBusinessDocument.messageId),
                            sender = sender,
                            receiver = receiver,
                            message = getOutgoingMessage(dialogmelding, outgoingBusinessDocument.receiverHerId),
                            getOutgoingAttachment(vedlegg, dialogmeldingMessage.metadataFiler),
                            DialogmeldingVersion.V1_1,
                            when {
                                outgoingBusinessDocument.parentId != null ->
                                    ConversationRef(
                                        outgoingBusinessDocument.parentId,
                                        outgoingBusinessDocument.conversationId,
                                    )
                                else -> null
                            },
                        )

                    mshService.sendMessage(outGoingDocument, clientContext)
                }
            }

        return ServerResponse.ok().contentType(MediaType.TEXT_PLAIN).bodyValueAndAwait(messageReference.toString())
    }

    private fun getDialogmelding(attachments: List<Document>, hoveddokument: String): Dialogmelding {
        val document =
            attachments
                .find { it.filename == hoveddokument }
                .orElseThrowNotFound("dialogmelding.json mangler i ASiC-E filen!")

        val jsonString = ResourceUtils.toString(document.resource, StandardCharsets.UTF_8)

        try {
            return jsonParser.decodeFromString<Dialogmelding>(jsonString)
        } catch (e: SerializationException) {
            throw e
        }
    }

    private fun getVedlegg(attachments: List<Document>, hoveddokument: String): List<Document> =
        attachments.filter { it.filename != hoveddokument }

    private suspend fun getHealthcareProfessional(herId: Int): HealthcareProfessional {
        val professional: PersonCommunicationParty =
            adresseregisteretService.lookupByHerId(herId) as PersonCommunicationParty

        return HealthcareProfessional(
            professional.firstName,
            professional.middleName,
            professional.lastName,
            professional.electronicAddresses.find { it.type == AddressComponent.TELEFONNUMMER }?.address
                ?: "not found",
            HelsepersonellsFunksjoner.FASTLEGE,
        )
    }

    private suspend fun getOutgoingMessage(
        dialogmelding: Dialogmelding,
        fastlege: Int,
    ): no.ks.fiks.nhn.msh.OutgoingMessage {
        val notat = dialogmelding.notat!!

        return no.ks.fiks.nhn.msh.OutgoingMessage(
            notat.temaBeskrivelse!!,
            notat.innhold!!,
            getHealthcareProfessional(fastlege),
            RecipientContact(Helsepersonell.LEGE),
        )
    }

    private suspend fun getOutgoingAttachment(
        vedlegg: List<Document>,
        metadata: Map<String, String>,
    ): OutgoingVedlegg {
        val vedlegg0 = vedlegg.single()

        return withContext(Dispatchers.IO) {
            vedlegg0.resource.inputStream.use { inputStream ->
                OutgoingVedlegg(OffsetDateTime.now(), metadata.getValue(vedlegg0.filename), inputStream)
            }
        }
    }
}
