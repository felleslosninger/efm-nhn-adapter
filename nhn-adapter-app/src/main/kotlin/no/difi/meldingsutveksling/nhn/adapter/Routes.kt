package no.difi.meldingsutveksling.nhn.adapter

import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.Json
import no.ks.fiks.hdir.Helsepersonell
import no.ks.fiks.hdir.HelsepersonellsFunksjoner
import no.ks.fiks.hdir.OrganizationIdType
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.msh.ChildOrganization
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.DialogmeldingVersion
import no.ks.fiks.nhn.msh.HealthcareProfessional
import no.ks.fiks.nhn.msh.Organization
import no.ks.fiks.nhn.msh.OrganizationId
import no.ks.fiks.nhn.msh.OrganizationReceiverDetails
import no.ks.fiks.nhn.msh.OutgoingBusinessDocument
import no.ks.fiks.nhn.msh.OutgoingMessage
import no.ks.fiks.nhn.msh.OutgoingVedlegg
import no.ks.fiks.nhn.msh.Patient
import no.ks.fiks.nhn.msh.Receiver
import no.ks.fiks.nhn.msh.RecipientContact
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait

fun CoRouterFunctionDsl.arLookupByFnr(flrClient: DecoratingFlrClient, arClient: AdresseregisteretClient) =
    GET("/arlookup/fastlege/{fnr}") {
        val fnr = it.pathVariable("fnr")
        val gpHerId = flrClient.getPatientGP(fnr)?.gpHerId.orElseThrowNotFound("GP not found for fnr")
        val communicationParty = arClient.lookupHerId(gpHerId).orElseThrowNotFound("Comunication party not found in AR")

        val parentHerId = communicationParty.parent?.herId.orElseThrowNotFound("HerId nivå 1 not found")

        val arDetails = ArDetails(parentHerId, gpHerId, "testedi-address", "testsertifikat")
        ServerResponse.ok().bodyValueAndAwait(arDetails)
    }

fun CoRouterFunctionDsl.arLookupById() =
    GET("/arlookup/organisasjonellernoe/{herId2}") { ServerResponse.ok().buildAndAwait() }

fun CoRouterFunctionDsl.dphOut(mshClient: Client) =
    POST("/dph/out") {
        val messageOut = it.awaitBody<MessageOut>()
        // The fagmelding needs to be decyrpted
        val fagmelding = Json {}.decodeFromString(Fagmelding.serializer(), messageOut.fagmelding)

        val outdoc: OutgoingBusinessDocument =
            OutgoingBusinessDocument(
                UUID.randomUUID(),
                Organization(
                    "KS-DIGITALE FELLESTJENESTER AS",
                    listOf(OrganizationId(messageOut.sender.herid1, OrganizationIdType.HER_ID)),
                    ChildOrganization(
                        "Digdir multi-tenant test",
                        listOf(OrganizationId(messageOut.sender.herid2, OrganizationIdType.HER_ID)),
                    ),
                ),
                receiver =
                    Receiver(
                        OrganizationReceiverDetails(
                            name = "DIGITALISERINGSDIREKTORATET",
                            ids = listOf(OrganizationId(messageOut.reciever.herid1, OrganizationIdType.HER_ID)),
                        ),
                        OrganizationReceiverDetails(
                            name = "Service 1",
                            ids = listOf(OrganizationId(messageOut.reciever.herid2, OrganizationIdType.HER_ID)),
                        ),
                        Patient("14038342168", "Aleksander", null, "Petterson"),
                    ),
                message =
                    OutgoingMessage(
                        fagmelding.subject,
                        fagmelding.body,
                        with(messageOut.healthcareProfressional) {
                            HealthcareProfessional(
                                this.firstName,
                                this.middleName,
                                this.lastName,
                                this.phoneNumber,
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
        println("MessageOut recieved ${messageOut.conversationId}")
        ServerResponse.ok().buildAndAwait()
    }
