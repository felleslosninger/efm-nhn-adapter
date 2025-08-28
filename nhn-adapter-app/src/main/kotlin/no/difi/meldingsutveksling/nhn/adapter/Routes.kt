package no.difi.meldingsutveksling.nhn.adapter

import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import no.idporten.identifiers.validation.PersonIdentifierValidator
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
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait

val logger = KotlinLogging.logger {}

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
    return arLookupByHerId(gpHerId.toInt(), arClient)
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

fun CoRouterFunctionDsl.dphOut(mshClient: Client, arClient: AdresseregisteretClient) =
    POST("/dph/out") {
        val messageOut = it.awaitBody<MessageOut>()
        val arDetailsSender = arLookupByHerId(messageOut.sender.herid2.toInt(), arClient)

        val arDetailsReciever = arLookupByHerId(messageOut.reciever.herid2.toInt(), arClient)

        // The fagmelding needs to be decyrpted
        val fagmelding = Json {}.decodeFromString(Fagmelding.serializer(), messageOut.fagmelding)

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
                            ids = listOf(OrganizationId(messageOut.reciever.herid1, OrganizationIdType.HER_ID)),
                        ),
                        OrganizationReceiverDetails(
                            name = arDetailsReciever.communicationPartyName,
                            ids = listOf(OrganizationId(messageOut.reciever.herid2, OrganizationIdType.HER_ID)),
                        ),
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
            .also {
                logger.debug { serializeNhnMessage(it) }
                mshClient.sendMessage(
                    it,
                    RequestParameters(
                        HelseIdTokenParameters(MultiTenantHelseIdTokenParameters(messageOut.onBehalfOfOrgNum))
                    ),
                )
            }

        logger.debug { "MessageOut recieved ${messageOut.conversationId}" }
        ServerResponse.ok().buildAndAwait()
    }
