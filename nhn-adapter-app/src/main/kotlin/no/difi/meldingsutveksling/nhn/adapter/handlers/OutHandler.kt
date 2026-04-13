package no.difi.meldingsutveksling.nhn.adapter.handlers

import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withContext
import no.difi.meldingsutveksling.domain.NhnIdentifier
import no.difi.meldingsutveksling.domain.PartnerIdentifier
import no.difi.meldingsutveksling.nhn.adapter.integration.adresseregister.AdresseregisteretService
import no.difi.meldingsutveksling.nhn.adapter.integration.msh.MshService
import no.difi.meldingsutveksling.nhn.adapter.logger
import no.difi.meldingsutveksling.nhn.adapter.model.Dialogmelding
import no.difi.meldingsutveksling.nhn.adapter.model.DialogmeldingMessage
import no.difi.meldingsutveksling.nhn.adapter.model.FileNames
import no.difi.meldingsutveksling.nhn.adapter.model.MultipartNames
import no.difi.meldingsutveksling.nhn.adapter.model.OutgoingMessage
import no.difi.meldingsutveksling.nhn.adapter.model.Person
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.jsonParser
import no.difi.meldingsutveksling.nhn.adapter.model.toMessageStatus
import no.difi.meldingsutveksling.nhn.adapter.orElseThrowNotFound
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.difi.meldingsutveksling.nhn.adapter.security.SecurityService
import no.difi.move.common.dokumentpakking.domain.Document
import no.ks.fiks.hdir.Helsepersonell
import no.ks.fiks.hdir.HelsepersonellsFunksjoner
import no.ks.fiks.hdir.OrganizationIdType
import no.ks.fiks.hdir.PersonIdType
import no.ks.fiks.nhn.ar.AddressComponent
import no.ks.fiks.nhn.ar.CommunicationParty
import no.ks.fiks.nhn.ar.PersonCommunicationParty
import no.ks.fiks.nhn.msh.DialogmeldingVersion
import no.ks.fiks.nhn.msh.HealthcareProfessional
import no.ks.fiks.nhn.msh.OrganizationCommunicationParty
import no.ks.fiks.nhn.msh.OrganizationId
import no.ks.fiks.nhn.msh.OutgoingBusinessDocument
import no.ks.fiks.nhn.msh.OutgoingVedlegg
import no.ks.fiks.nhn.msh.Patient
import no.ks.fiks.nhn.msh.PersonId
import no.ks.fiks.nhn.msh.Receiver
import no.ks.fiks.nhn.msh.RecipientContact
import no.ks.fiks.nhn.msh.Sender
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

class OutHandler(
    private val mshService: MshService,
    private val parcelService: ParcelService,
    private val securityService: SecurityService,
    private val adresseregisteretService: AdresseregisteretService
) {
    suspend fun getStatus(messageId: UUID, clientContext: ClientContext): ServerResponse {
        val data = mshService.getStatus(messageId, clientContext).map { it.toMessageStatus() }
        data.firstOrNull()?.let {
            val communicationParty = adresseregisteretService.lookupByHerId(it.receiverHerId)
            securityService.assertAccess(clientContext, communicationParty)
        }

        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValueAndAwait(data)
    }

    suspend fun sendApplicationReceipt(
        request: ServerRequest,
        messageId: UUID,
        clientContext: ClientContext
    ): ServerResponse {
//        return ServerResponse.ok().contentType(MediaType.TEXT_PLAIN)
//            .bodyValueAndAwait(withClientContext { clientContext ->
//                runBlocking {
//                    val jweToken = request.awaitBody<String>()
//                    val payload = parcelService.decryptAndVerify(jweToken)
//                    val messageOut = jsonParser.decodeFromString<SerializedOutgoingApplicationReceipt>(payload)
//
//                    val receipt = OutgoingApplicationReceipt(
//                        acknowledgedId = messageId,
//                        )
//
//                    val messageReference = mshService.sendApplicationReceipt(receipt)
//                    messageReference.toString()
//                }
//            }.awaitSingle())

        return ServerResponse.ok().bodyValueAndAwait("NOT IMPLEMENTED!")
    }

    suspend fun sendMessage(request: ServerRequest, clientContext: ClientContext): ServerResponse {
        logger.info("Starting sendMessage")

        val multipartData = request.multipartData().awaitSingle()

        val jweToken =
            multipartData.getFirst(MultipartNames.forretningsmelding)!!.content().awaitSingle()
                .toString(StandardCharsets.UTF_8)
        val payload = parcelService.decryptAndVerify(jweToken, clientContext)
        val outgoingMessage = jsonParser.decodeFromString<OutgoingMessage>(payload)
        val dialogmeldingMessage = outgoingMessage.payload

        val dokumentpakke = multipartData.getFirst(MultipartNames.dokumentpakke)!!
        val asicFiles = withContext(Dispatchers.IO) {
            dokumentpakke.content().awaitSingle().asInputStream()
                .use { inputStream -> parcelService.getAttachments(inputStream) }
        }

        val dialogmelding = getDialogmelding(asicFiles)
        val vedlegg = getVedlegg(asicFiles)

        val sender =
            securityService.assertAccess(
                clientContext, adresseregisteretService.lookupByHerId(
                    NhnIdentifier.parse(
                        outgoingMessage.sender
                    ).identifier.toInt()
                )
            )

        val receiver =
            adresseregisteretService.lookupByPartnerIdentifier(PartnerIdentifier.parse(outgoingMessage.receiver))

        val outGoingDocument =
            OutgoingBusinessDocument(
                UUID.randomUUID(),
                getSender(sender),
                receiver = getReceiver(receiver, dialogmeldingMessage.pasient),
                message = getOutgoingMessage(dialogmeldingMessage, dialogmelding),
                getOutgoingAttachment(vedlegg, dialogmeldingMessage.metadataFiler),
                DialogmeldingVersion.V1_1,
                null,
            )

        logger.info { outGoingDocument }
        val messageReference = mshService.sendMessage(outGoingDocument, clientContext)

        logger.info("EDI 2.0 message referance: $messageReference")
        logger.info("dialogmeldin messageId:${outGoingDocument.id}")

        logger.info { "MessageOut recieved with messageReferance = $messageReference" }

        return ServerResponse.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .bodyValueAndAwait(messageReference.toString())
    }

    private suspend fun getDialogmelding(attachments: List<Document>): Dialogmelding {
        val document = attachments.find { it.filename == FileNames.forretningsmelding }
            .orElseThrowNotFound("forretningsmelding mangler i ASiC-E filen!")

        val jsonString = withContext(Dispatchers.IO) {
            document.resource.inputStream.bufferedReader().use { it.readText() }
        }

        return jsonParser.decodeFromString<Dialogmelding>(jsonString)
    }

    private fun getVedlegg(attachments: List<Document>): List<Document> {
        return attachments.filter { it.filename != FileNames.forretningsmelding }
    }

    private fun getSender(communicationParty: CommunicationParty): Sender {
        return Sender(
            OrganizationCommunicationParty(
                name = communicationParty.parent!!.name,
                ids = listOf(
                    OrganizationId(
                        communicationParty.parent!!.herId.toString(),
                        OrganizationIdType.HER_ID
                    )
                ),
            ),
            OrganizationCommunicationParty(
                name = communicationParty.name,
                ids = listOf(OrganizationId(communicationParty.herId.toString(), OrganizationIdType.HER_ID)),
            ),
        )
    }

    private fun getReceiver(communicationParty: CommunicationParty, patient: Person): Receiver {
        return Receiver(
            OrganizationCommunicationParty(
                name = communicationParty.parent!!.name,
                ids = listOf(
                    OrganizationId(
                        communicationParty.parent!!.herId.toString(),
                        OrganizationIdType.HER_ID
                    )
                ),
            ), if (communicationParty is PersonCommunicationParty) {
                no.ks.fiks.nhn.msh.PersonCommunicationParty(
                    ids = listOf(PersonId(communicationParty.herId.toString(), PersonIdType.HER_ID)),
                    firstName = communicationParty.firstName,
                    lastName = communicationParty.lastName,
                    middleName = communicationParty.middleName,
                )
            } else {
                OrganizationCommunicationParty(
                    name = communicationParty.name,
                    ids = listOf(OrganizationId(communicationParty.herId.toString(), OrganizationIdType.HER_ID)),
                )
            },
            Patient(
                patient.fnr,
                patient.firstName,
                patient.middleName,
                patient.lastName
            )
        )
    }

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
        dialogmeldingMessage: DialogmeldingMessage,
        dialogmelding: Dialogmelding
    ): no.ks.fiks.nhn.msh.OutgoingMessage {
        val notat = dialogmelding.notat

        return no.ks.fiks.nhn.msh.OutgoingMessage(
            notat.temaBeskrivelse,
            notat.innhold,
            getHealthcareProfessional(dialogmeldingMessage.fastlege),
            RecipientContact(Helsepersonell.LEGE),
        )
    }

    private suspend fun getOutgoingAttachment(
        vedlegg: List<Document>,
        metadata: Map<String, String>
    ): OutgoingVedlegg {
        val vedlegg0 = vedlegg.single()

        return withContext(Dispatchers.IO) {
            vedlegg0.resource.inputStream.use { inputStream ->
                OutgoingVedlegg(
                    OffsetDateTime.now(),
                    metadata.getValue(vedlegg0.filename),
                    inputStream,
                )
            }
        }
    }
}
