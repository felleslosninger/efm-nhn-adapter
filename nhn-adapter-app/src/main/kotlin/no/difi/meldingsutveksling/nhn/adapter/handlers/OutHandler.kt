package no.difi.meldingsutveksling.nhn.adapter.handlers

import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.Json
import no.difi.meldingsutveksling.nhn.adapter.logger
import no.difi.meldingsutveksling.nhn.adapter.model.Fagmelding
import no.difi.meldingsutveksling.nhn.adapter.model.MessageOut
import no.difi.meldingsutveksling.nhn.adapter.model.toMessageStatus
import no.ks.fiks.hdir.Helsepersonell
import no.ks.fiks.hdir.HelsepersonellsFunksjoner
import no.ks.fiks.hdir.OrganizationIdType
import no.ks.fiks.hdir.PersonIdType
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.ar.PersonCommunicationParty
import no.ks.fiks.nhn.edi.BusinessDocumentSerializer.serializeNhnMessage
import no.ks.fiks.nhn.msh.ChildOrganization
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.DialogmeldingVersion
import no.ks.fiks.nhn.msh.HealthcareProfessional
import no.ks.fiks.nhn.msh.HelseIdTokenParameters
import no.ks.fiks.nhn.msh.MultiTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.Organization
import no.ks.fiks.nhn.msh.OrganizationId
import no.ks.fiks.nhn.msh.OrganizationReceiverDetails
import no.ks.fiks.nhn.msh.OutgoingBusinessDocument
import no.ks.fiks.nhn.msh.OutgoingMessage
import no.ks.fiks.nhn.msh.OutgoingVedlegg
import no.ks.fiks.nhn.msh.Patient
import no.ks.fiks.nhn.msh.PersonId
import no.ks.fiks.nhn.msh.PersonReceiverDetails
import no.ks.fiks.nhn.msh.Receiver
import no.ks.fiks.nhn.msh.RecipientContact
import no.ks.fiks.nhn.msh.RequestParameters
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.queryParamOrNull

object OutHandler {
    suspend fun statusHandler(it: ServerRequest, mshClient: Client): ServerResponse {
        val messageId = it.pathVariable("messageId")
        // @TODO to use onBehalfOf as request parameter exposes details of next
        // authentication/authorization step
        // potentialy put the onBehalfOf orgnummeret enten som Body eller som ekstra claim i maskin
        // to maski tokenet
        val onBehalfOf = it.queryParamOrNull("onBehalfOf")

        val requestParameters =
            onBehalfOf?.let { onBehalfOf ->
                RequestParameters(HelseIdTokenParameters(MultiTenantHelseIdTokenParameters(onBehalfOf)))
            }
        return ServerResponse.ok()
            .bodyValueAndAwait(
                mshClient.getStatus(UUID.fromString(messageId), requestParameters).map { it.toMessageStatus() }.first()
            )
    }

    suspend fun dphOut(request: ServerRequest, arClient: AdresseregisteretClient, mshClient: Client): ServerResponse {
        val messageOut = request.awaitBody<MessageOut>()
        val arDetailsSender = ArHandlers.arLookupByHerId(messageOut.sender.herid2.toInt(), arClient)

        val arDetailsReciever = ArHandlers.arLookupByHerId(messageOut.receiver.herid2.toInt(), arClient)

        // The fagmelding needs to be decyrpted
        val fagmelding = Json {}.decodeFromString(Fagmelding.serializer(), messageOut.fagmelding)

        // @TODO Dette er her er ikke helt presis. Vi kan leve med det for øyebliket men
        // vi skall endre det å ta hensyn til det som kommer fra AR og ikke bruke fnr-en å besteme
        val receiver =
            if (messageOut.receiver.patientFnr != null) {
                arClient
                    .lookupHerId(messageOut.receiver.herid2.toInt())
                    .let { it as PersonCommunicationParty }
                    .let { fastlege ->
                        PersonReceiverDetails(
                            ids = listOf(PersonId(id = fastlege.herId.toString(), type = PersonIdType.HER_ID)),
                            firstName = fastlege.firstName,
                            middleName = fastlege.middleName,
                            lastName = fastlege.lastName,
                        )
                    }
            } else {
                OrganizationReceiverDetails(
                    name = arDetailsReciever.communicationPartyName,
                    ids = listOf(OrganizationId(messageOut.receiver.herid2, OrganizationIdType.HER_ID)),
                )
            }

        val outGoingDocument =
            OutgoingBusinessDocument(
                UUID.randomUUID(),
                Organization(
                    arDetailsSender.communicationPartyParentName,
                    listOf(OrganizationId(messageOut.sender.herid1, OrganizationIdType.HER_ID)),
                    ChildOrganization(
                        arDetailsSender.communicationPartyName,
                        listOf(OrganizationId(messageOut.sender.herid2, OrganizationIdType.HER_ID)),
                    ),
                ),
                receiver =
                    Receiver(
                        OrganizationReceiverDetails(
                            name = arDetailsReciever.communicationPartyParentName,
                            ids = listOf(OrganizationId(messageOut.receiver.herid1, OrganizationIdType.HER_ID)),
                        ),
                        receiver,
                        // @TODO hvis reciever er nhn tjeneste hvordan setter vi pasient?
                        Patient(
                            messageOut.patient.fnr,
                            messageOut.patient.firstName,
                            messageOut.patient.middleName,
                            messageOut.patient.lastName,
                        ),
                    ),
                message =
                    OutgoingMessage(
                        fagmelding.subject,
                        fagmelding.body,
                        with(messageOut.patient) {
                            HealthcareProfessional(
                                this.firstName,
                                this.middleName,
                                this.lastName,
                                "8888888",
                                HelsepersonellsFunksjoner.FASTLEGE,
                            )
                        },
                        RecipientContact(
                            Helsepersonell.LEGE
                            // message
                        ),
                    ),
                OutgoingVedlegg(
                    OffsetDateTime.now(),
                    "<Description of the attachment>",
                    this.javaClass.getClassLoader().getResourceAsStream("small.pdf")!!,
                ),
                DialogmeldingVersion.V1_1,
            )

        logger.debug { serializeNhnMessage(outGoingDocument) }
        val messageReference =
            mshClient.sendMessage(
                outGoingDocument,
                RequestParameters(
                    HelseIdTokenParameters(MultiTenantHelseIdTokenParameters(messageOut.onBehalfOfOrgNum))
                ),
            )

        logger.info("EDI 2.0 message referance: $messageReference")
        logger.info("dialogmeldin messageId:${outGoingDocument.id}")

        logger.debug { "MessageOut recieved with messageReferance = $messageReference" }
        return ServerResponse.ok().contentType(MediaType.TEXT_PLAIN).bodyValueAndAwait(messageReference.toString())
    }
}
