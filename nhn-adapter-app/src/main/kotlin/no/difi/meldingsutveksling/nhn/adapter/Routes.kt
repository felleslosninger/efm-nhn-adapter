package no.difi.meldingsutveksling.nhn.adapter

import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import no.idporten.validators.identifier.PersonIdentifierValidator
import no.ks.fiks.hdir.Helsepersonell
import no.ks.fiks.hdir.HelsepersonellsFunksjoner
import no.ks.fiks.hdir.OrganizationIdType
import no.ks.fiks.nhn.ar.AdresseregisteretClient
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
import no.ks.fiks.nhn.msh.Receiver
import no.ks.fiks.nhn.msh.RecipientContact
import no.ks.fiks.nhn.msh.RequestParameters
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait
import org.springframework.web.reactive.function.server.queryParamOrNull

val logger = KotlinLogging.logger {}

fun CoRouterFunctionDsl.statusCheck(mshClient: Client) =
    GET("/dph/status/{messageId}") { it ->
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
        ServerResponse.ok()
            .bodyValueAndAwait(
                mshClient.getStatus(UUID.fromString(messageId), requestParameters).map { it.toMessageStatus() }.first()
            )
    }

fun CoRouterFunctionDsl.arLookup(flrClient: DecoratingFlrClient, arClient: AdresseregisteretClient) =
    GET("/arlookup/{identifier}") {
        val fnr = it.pathVariable("identifier")
        PersonIdentifierValidator.setSyntheticPersonIdentifiersAllowed(true)
        val arDetails =
            when (PersonIdentifierValidator.isValid(fnr)) {
                true -> arLookupByFnr(fnr, flrClient, arClient)
                false -> arLookupByHerId(fnr.toInt(), arClient)
            }

        ServerResponse.ok().bodyValueAndAwait(arDetails)
    }

private fun arLookupByFnr(fnr: String, flrClient: DecoratingFlrClient, arClient: AdresseregisteretClient): ArDetails {
    val gpHerId = flrClient.getPatientGP(fnr)?.gpHerId.orElseThrowNotFound("GP not found for fnr")
    return arLookupByHerId(gpHerId, arClient)
}

private fun arLookupByHerId(herId: Int, arClient: AdresseregisteretClient): ArDetails {
    val communicationParty = arClient.lookupHerId(herId).orElseThrowNotFound("Comunication party not found in AR")
    val comunicationPartyName = communicationParty.name

    val parentHerId = communicationParty.parent?.herId.orElseThrowNotFound("HerId nivå 1 not found")
    val orgNumber = communicationParty.parent!!.organizationNumber
    val comunicationPartyParentName = communicationParty.parent?.name ?: "empty"

    return ArDetails(
        parentHerId,
        comunicationPartyParentName,
        orgNumber = orgNumber,
        herId,
        comunicationPartyName,
        "testedi-address",
        "testsertifikat",
    )
}

fun CoRouterFunctionDsl.arLookupById() =
    GET("/arlookup/organisasjonellernoe/{herId2}") { ServerResponse.ok().buildAndAwait() }

@OptIn(ExperimentalUuidApi::class)
fun CoRouterFunctionDsl.incomingReciept(mshClient: Client) =
    GET("/dph/in/{messageId}/reciept") {
        val messageId: UUID =
            it.pathVariable("messageId")
                .runCatching { UUID.fromString(this) }
                .getOrElse {
                    return@GET ServerResponse.badRequest().bodyValueAndAwait("Message id is wrong format")
                }
        // @TODO to use onBehalfOf as request parameter exposes details of next
        // authentication/authorization step
        // potentialy put the onBehalfOf orgnummeret enten som Body eller som ekstra claim i maskin
        // to maski tokenet
        val onBehalfOf = it.queryParamOrNull("onBehalfOf")
        val requestParameters =
            onBehalfOf?.let { onBehalfOf ->
                RequestParameters(HelseIdTokenParameters(MultiTenantHelseIdTokenParameters(onBehalfOf)))
            }
        val incomingApplicationReceipt = mshClient.getApplicationReceiptsForMessage(messageId, requestParameters)

        ServerResponse.ok().bodyValueAndAwait(incomingApplicationReceipt.map { it.toSerializable() })
    }

fun CoRouterFunctionDsl.dphOut(mshClient: Client, arClient: AdresseregisteretClient) =
    POST("/dph/out") {
        val messageOut = it.awaitBody<MessageOut>()
        val arDetailsSender = arLookupByHerId(messageOut.sender.herid2.toInt(), arClient)

        val arDetailsReciever = arLookupByHerId(messageOut.receiver.herid2.toInt(), arClient)

        // The fagmelding needs to be decyrpted
        val fagmelding = Json {}.decodeFromString(Fagmelding.serializer(), messageOut.fagmelding)

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
                        OrganizationReceiverDetails(
                            name = arDetailsReciever.communicationPartyName,
                            ids = listOf(OrganizationId(messageOut.receiver.herid2, OrganizationIdType.HER_ID)),
                        ),
                        // @TODO hvis reciever er nhn tjeneste hvordan setter vi pasient?
                        no.ks.fiks.nhn.msh.Patient(
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

        logger.debug { "MessageOut recieved with messageReferance = $messageReference" }
        ServerResponse.ok().contentType(MediaType.TEXT_PLAIN).bodyValueAndAwait(messageReference.toString())
    }
