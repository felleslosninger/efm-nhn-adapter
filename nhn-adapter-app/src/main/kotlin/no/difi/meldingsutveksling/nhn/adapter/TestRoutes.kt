package no.difi.meldingsutveksling.nhn.adapter

import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import no.difi.meldingsutveksling.nhn.adapter.model.SerializableOutgoingApplicationReceipt
import no.difi.meldingsutveksling.nhn.adapter.model.toOriginal
import no.ks.fiks.hdir.Helsepersonell
import no.ks.fiks.hdir.HelsepersonellsFunksjoner
import no.ks.fiks.hdir.OrganizationIdType
import no.ks.fiks.helseid.AccessTokenRequestBuilder
import no.ks.fiks.helseid.HelseIdClient
import no.ks.fiks.helseid.TenancyType
import no.ks.fiks.helseid.TokenType
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.edi.BusinessDocumentSerializer.serializeNhnMessage
import no.ks.fiks.nhn.msh.ChildOrganization
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.ClientFactory
import no.ks.fiks.nhn.msh.Configuration
import no.ks.fiks.nhn.msh.DialogmeldingVersion
import no.ks.fiks.nhn.msh.HealthcareProfessional
import no.ks.fiks.nhn.msh.HelseIdConfiguration
import no.ks.fiks.nhn.msh.HelseIdTokenParameters
import no.ks.fiks.nhn.msh.MultiTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.Organization
import no.ks.fiks.nhn.msh.OrganizationId
import no.ks.fiks.nhn.msh.OrganizationReceiverDetails
import no.ks.fiks.nhn.msh.OutgoingBusinessDocument
import no.ks.fiks.nhn.msh.OutgoingMessage
import no.ks.fiks.nhn.msh.OutgoingVedlegg
import no.ks.fiks.nhn.msh.Patient
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

@OptIn(ExperimentalUuidApi::class)
fun CoRouterFunctionDsl.testRespondApprecFralegekontor(mshClient: Client) =
    POST("/messages/apprec") { request ->
        val onBehalfOf =
            request.queryParamOrNull("onBehalfOf")
                ?: return@POST ServerResponse.badRequest()
                    .bodyValueAndAwait(mapOf("error" to "Missing query parameter: onBehalfOf"))

        try {
            val receipt = request.awaitBody<SerializableOutgoingApplicationReceipt>()

            receipt.recieverHerId
                ?: return@POST ServerResponse.badRequest()
                    .bodyValueAndAwait(mapOf("error" to "recieverHerId is not defined"))
            val acknowledgedId = UUID.fromString(receipt.acknowledgedId.toString())

            // mark the message as it has bean read. I am not sure it is nessesary but it makes it
            // closer to what will happen real world
            mshClient.markMessageRead(
                acknowledgedId,
                receipt.senderHerId,
                RequestParameters(HelseIdTokenParameters(MultiTenantHelseIdTokenParameters(onBehalfOf))),
            )

            val apprecUUID =
                mshClient.sendApplicationReceipt(
                    receipt.toOriginal(),
                    RequestParameters(HelseIdTokenParameters(MultiTenantHelseIdTokenParameters(onBehalfOf))),
                )

            return@POST ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValueAndAwait("Apprec is send med message reference:$apprecUUID")
        } catch (e: Exception) {
            e.printStackTrace()
            logger.error(e) { "Unable to send application receipt" }
            return@POST ServerResponse.badRequest()
                .bodyValueAndAwait(mapOf("error" to "Failed to process request: ${e.message}"))
        }
    }

fun CoRouterFunctionDsl.testFlr(flrClient: DecoratingFlrClient) =
    POST("/flr/test") {
        val patientGP = flrClient.getPatientGP("16822449879")
        println(patientGP?.gpHerId)
        ServerResponse.ok().build().onErrorResume { e -> ServerResponse.ok().build() }.block()!!
    }

fun CoRouterFunctionDsl.testDphOut(
    helseIdClient: HelseIdClient,
    helseIdConfiguration: no.ks.fiks.helseid.Configuration,
) =
    POST("/dph/testOut") {
        val accessToken =
            helseIdClient
                .getAccessToken(
                    AccessTokenRequestBuilder()
                        .parentOrganizationNumber("931796003")
                        .tokenType(TokenType.BEARER)
                        .tenancyType(TenancyType.MULTI)
                        .build()
                )
                .accessToken

        val dpopAccessToken: String =
            helseIdClient
                .getAccessToken(
                    AccessTokenRequestBuilder()
                        .parentOrganizationNumber("931796003")
                        .tokenType(TokenType.DPOP)
                        .tenancyType(TenancyType.MULTI)
                        .build()
                )
                .accessToken
        val helseIdConfiguration =
            HelseIdConfiguration(
                helseIdConfiguration.environment,
                helseIdConfiguration.clientId,
                helseIdConfiguration.jwk,
            )
        val mshClient =
            ClientFactory.createClient(
                Configuration(helseIdConfiguration, "https://api.tjener.test.melding.nhn.no", "digidir")
            )
        val outgoingBusinessDocument =
            OutgoingBusinessDocument(
                UUID.randomUUID(),
                Organization(
                    "KS-DIGITALE FELLESTJENESTER AS",
                    listOf(OrganizationId("8142987", OrganizationIdType.HER_ID)),
                    ChildOrganization(
                        "Digdir multi-tenant test",
                        listOf(OrganizationId("8143154", OrganizationIdType.HER_ID)),
                    ),
                ),
                Receiver(
                    OrganizationReceiverDetails(
                        name = "Digidir test fastlegekontor",
                        ids = listOf(OrganizationId("8143541", OrganizationIdType.HER_ID)),
                    ),
                    OrganizationReceiverDetails(
                        name = "Peter Peterson",
                        ids = listOf(OrganizationId("8143548", OrganizationIdType.HER_ID)),
                    ),
                    Patient("14038342168", "Aleksander", null, "Petterson"),
                ),
                OutgoingMessage(
                    "<Message subject>",
                    "<Message body>",
                    HealthcareProfessional(
                        // Person responsible for this message at the sender
                        "<First name>",
                        "<Middle name>",
                        "<Last name>",
                        "11223344",
                        HelsepersonellsFunksjoner.HELSEFAGLIG_KONTAKT,
                    ),
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
        println(serializeNhnMessage(outgoingBusinessDocument))

        mshClient.sendMessage(
            outgoingBusinessDocument,
            RequestParameters(HelseIdTokenParameters(MultiTenantHelseIdTokenParameters("931796003"))),
        )

        val messageID =
            mshClient
                .getMessages(
                    8143548,
                    RequestParameters(HelseIdTokenParameters(MultiTenantHelseIdTokenParameters("310673145"))),
                )
                .iterator()
                .next()
                .id
        mshClient.getBusinessDocument(
            messageID,
            RequestParameters(HelseIdTokenParameters(MultiTenantHelseIdTokenParameters("310673145"))),
        )

        //  val messageOut = it.awaitBody<MessageOut>()
        //  println("MessageOut recieved ${messageOut.conversationId}")
        ServerResponse.ok().buildAndAwait()
    }

fun CoRouterFunctionDsl.testAr(arClient: AdresseregisteretClient) =
    POST("/ar/test") {
        val reciever = arClient.lookupHerId(8143548)
        println(reciever?.name)
        println(reciever?.name)
        ServerResponse.ok().buildAndAwait()
    }
