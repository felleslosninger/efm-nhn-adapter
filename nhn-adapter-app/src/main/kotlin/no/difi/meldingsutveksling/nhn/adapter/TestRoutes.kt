package no.difi.meldingsutveksling.nhn.adapter

import java.time.OffsetDateTime
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
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
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait
import org.springframework.web.reactive.function.server.queryParamOrNull

@OptIn(ExperimentalUuidApi::class)
fun CoRouterFunctionDsl.testRespondApprecFralegekontor(mshClient: Client) =
    POST("/messages/apprec") { request ->
        //   val messageId = request.pathVariable("messageId");
        //   val senderHerId = request.pathVariable("senderHerId");
        val onBehalfOf = request.queryParamOrNull("onBehalfOf")

        try {
            val receipt = request.awaitBody<SerializableOutgoingApplicationReceipt>()

            mshClient.markMessageRead(
                UUID.fromString(receipt.acknowledgedId.toString()),
                8143548,
                RequestParameters(HelseIdTokenParameters(MultiTenantHelseIdTokenParameters("310673145"))),
            )

            val apprecUUID =
                mshClient.sendApplicationReceipt(
                    receipt.toOriginal(),
                    onBehalfOf?.let {
                        RequestParameters(HelseIdTokenParameters(MultiTenantHelseIdTokenParameters(it)))
                    },
                )

            //  val apprecInfo =  mshClient.getApprecInfo(UUID.fromString(
            // "e7a20a2c-948f-4fac-bb00-3c517c8df45c"),RequestParameters(HelseIdTokenParameters(MultiTenantHelseIdTokenParameters("931796003"))))
            //   println(apprecInfo)
            val inReciept =
                mshClient.getApplicationReceipt(
                    apprecUUID,
                    RequestParameters(HelseIdTokenParameters(MultiTenantHelseIdTokenParameters("931796003"))),
                )
            println(inReciept)

            return@POST ServerResponse.ok()
                .bodyValueAndAwait(
                    mapOf(
                        "messageId" to inReciept.acknowledgedBusinessDocumentId,
                        "senderHerId" to inReciept.sender.id.id,
                        "receipt" to inReciept.toSerializable(),
                    )
                )
        } catch (e: Exception) {
            logger.error(e) { "unable to send" }
            return@POST ServerResponse.badRequest()
                .bodyValueAndAwait(mapOf("error" to "Failed to deserialize: ${e.message}"))
        }
        // ServerResponse.ok().bodyValueAndAwait("Apprec sendt")
    }

fun CoRouterFunctionDsl.testKotlinX() =
    POST("/kotlinx") {
        val kotlinX = it.awaitBody<TestKotlinX>()
        ServerResponse.ok().bodyValueAndAwait(kotlinX.copy(name = "Test2"))
    }

fun CoRouterFunctionDsl.testKotlinxSealedclass() =
    POST("/kotlinxsealedclass") {
        val messageOut = it.awaitBody<CommunicationParty>()
        println("Communication Party: $messageOut")
        ServerResponse.ok().bodyValueAndAwait(messageOut)
    }

fun CoRouterFunctionDsl.testFlr(flrClient: DecoratingFlrClient) =
    POST("/flr/test") {
        val patientGP = flrClient.getPatientGP("16822449879")
        println(patientGP?.gpHerId)
        ServerResponse.ok().buildAndAwait()
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
        ServerResponse.ok().buildAndAwait()
    }
