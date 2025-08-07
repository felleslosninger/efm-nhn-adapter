package no.difi.meldingsutveksling.nhn.adapter

import no.ks.fiks.hdir.Helsepersonell
import no.ks.fiks.hdir.HelsepersonellsFunksjoner
import no.ks.fiks.hdir.OrganisasjonIdType
import no.ks.fiks.helseid.AccessTokenRequestBuilder
import no.ks.fiks.helseid.HelseIdClient
import no.ks.fiks.helseid.TenancyType
import no.ks.fiks.helseid.TokenType
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.edi.BusinessDocumentSerializer.serializeNhnMessage
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import no.ks.fiks.nhn.msh.*
import no.ks.fiks.nhn.msh.Configuration
import org.springframework.web.reactive.function.server.*
import java.time.OffsetDateTime
import java.util.*

fun CoRouterFunctionDsl.testHelloWorld() =

    this.GET("/helloWorld") {
        it.awaitBody<String>()
        ServerResponse.ok().bodyValueAndAwait("Hello World")
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


fun CoRouterFunctionDsl.testFlr(flrClient: FastlegeregisteretClient) =
    POST("/flr/test") {
        //       val flrClinet = FastlegeregisteretClient(Environment.TEST, Credentials(username = "****", password = "*****"))
        val patientGP = flrClient.getPatientGP("16822449879")
        println(patientGP?.gpHerId)
        ServerResponse.ok().buildAndAwait()
    }

fun CoRouterFunctionDsl.testDphOut(
    helseIdClient: HelseIdClient,
    helseIdConfiguration: no.ks.fiks.helseid.Configuration
) =
    POST("/dph/testOut") {
        val accessToken = helseIdClient.getAccessToken(
            AccessTokenRequestBuilder()
                .parentOrganizationNumber("931796003")
                .tokenType(TokenType.BEARER)
                .tenancyType(TenancyType.MULTI)
                .build()
        ).accessToken

        val dpopAccessToken: String = helseIdClient.getAccessToken(
            AccessTokenRequestBuilder()
                .parentOrganizationNumber("931796003")
                .tokenType(TokenType.DPOP)
                .tenancyType(TenancyType.MULTI)
                .build()
        ).accessToken

        val mshClient = Client(
            Configuration(
                Environments.TEST,
                "eFormidling", HelseIdConfiguration(helseIdConfiguration.clientId, helseIdConfiguration.jwk),
                Credentials("dummy-flr-user", "dummy-flr-password"), Credentials("dummy-ar-user", "dummy-ar-password")
            )
        )

        val outgoingBusinessDocument = OutgoingBusinessDocument(
            UUID.randomUUID(),
            Organization(
                "KS-DIGITALE FELLESTJENESTER AS",
                Id("8142987", OrganisasjonIdType.HER_ID),
                Organization("Digdir multi-tenant test", Id("8143154", OrganisasjonIdType.HER_ID), null)
            ),
            HerIdReceiver(
                HerIdReceiverParent("DIGITALISERINGSDIREKTORATET", Id("8143143", OrganisasjonIdType.HER_ID)),
                OrganizationHerIdReceiverChild("Service 1", Id("8143144", OrganisasjonIdType.HER_ID)),
                Patient("14038342168", "Aleksander", null, "Petterson")
            ),
            BusinessDocumentMessage(
                "<Message subject>",
                "<Message body>",
                HealthcareProfessional( // Person responsible for this message at the sender
                    "<First name>",
                    "<Middle name>",
                    "<Last name>",
                    "11223344",
                    HelsepersonellsFunksjoner.HELSEFAGLIG_KONTAKT // This persons role with respect to the patient
                ),
                RecipientContact(
                    Helsepersonell.LEGE // Professional group of the healthcare professional recieving the message
                )
            ),
            Vedlegg(
                OffsetDateTime.now(),
                "<Description of the attachment>",
                this.javaClass.getClassLoader().getResourceAsStream("small.pdf")
            ),
            DialogmeldingVersion.V1_1
        )
        println(serializeNhnMessage(outgoingBusinessDocument))

        mshClient.sendMessage(outgoingBusinessDocument, "931796003")
        val messageID = mshClient.getMessages(8143144, "991825827").iterator().next().id
        mshClient.getBusinessDocument(messageID, "991825827")


        val messageOut = it.awaitBody<MessageOut>()
        println("MessageOut recieved ${messageOut.conversationId}")
        ServerResponse.ok().buildAndAwait()
    }


fun CoRouterFunctionDsl.arLookupByFnr(flrClient: FastlegeregisteretClient, arClient: AdresseregisteretClient) =
    GET("/arlookup/fastlege/{fnr}") {
        val fnr = it.pathVariable("fnr")
        val gpHerId = flrClient.getPatientGP(fnr)?.gpHerId.orElseThrowNotFound("GP not found for fnr")
        val communicationParty =
            arClient.lookupHerId(gpHerId).orElseThrowNotFound("Comunication party not found in AR")
        val parentHerId = communicationParty.parent?.herId.orElseThrowNotFound("HerId nivå 1 not found")
        val arDetails = ArDetails(parentHerId, gpHerId, "testedi-address", "testsertifikat")
        ServerResponse.ok().bodyValueAndAwait(arDetails)
    }


fun CoRouterFunctionDsl.arLookupById() =
    GET("/arlookup/organisasjonellernoe/{herId2}") {
        ServerResponse.ok().buildAndAwait()
    }


fun CoRouterFunctionDsl.dphOut() =
    POST("/dph/out") {
        val messageOut = it.awaitBody<MessageOut>()
        println("MessageOut recieved ${messageOut.conversationId}")
        ServerResponse.ok().buildAndAwait()
    }


fun CoRouterFunctionDsl.testAr(arClient: AdresseregisteretClient) =
    POST("/ar/test") {
        //     val arClient = AdresseregisteretClient(
        //         no.ks.fiks.nhn.ar.Environment.TEST,
        //         no.ks.fiks.nhn.ar.Credentials(username = "*****", password = "*****"))
        val reciever = arClient.lookupHerId(8143060)
        println(reciever?.name)
        ServerResponse.ok().buildAndAwait()
    }


