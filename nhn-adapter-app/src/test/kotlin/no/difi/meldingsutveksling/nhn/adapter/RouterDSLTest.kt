package no.difi.meldingsutveksling.nhn.adapter

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import no.difi.meldingsutveksling.nhn.adapter.model.ArDetails
import no.difi.meldingsutveksling.nhn.adapter.model.FeilmeldingForApplikasjonskvitteringSerializer
import no.difi.meldingsutveksling.nhn.adapter.model.IdSerializer
import no.difi.meldingsutveksling.nhn.adapter.model.StatusForMottakAvMeldingSerializer
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.hdir.StatusForMottakAvMelding
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.ar.CommunicationPartyParent
import no.ks.fiks.nhn.ar.OrganizationCommunicationParty
import no.ks.fiks.nhn.ar.PersonCommunicationParty
import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import no.ks.fiks.nhn.flr.PatientGP
import no.ks.fiks.nhn.msh.Id
import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.beans.factory.getBean
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.http.MediaType
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder
import org.springframework.test.web.reactive.server.WebTestClient

class RouterDSLTest() :
    FunSpec({
        context("Test AR lookup") {
            val arLookupContext = BeanRegistrarDsl {
                registerBean<FastlegeregisteretClient>(::mockk)
                registerBean<DecoratingFlrClient>() { DecoratingFlrClient(bean(), listOf()) }
                registerBean<AdresseregisteretClient> { mockk() }
                testCoRouter { ctx -> arLookup(ctx.bean(), ctx.bean()) }
            }
            val context =
                AnnotationConfigApplicationContext().apply {
                    register(arLookupContext)
                    refresh()
                }

            val webTestClient =
                WebTestClient.bindToWebHandler(context.getBean())
                    .configureClient()
                    .codecs { cfg ->
                        val json = Json {
                            ignoreUnknownKeys = true
                            classDiscriminator = "type"
                            serializersModule = SerializersModule {
                                contextual(StatusForMottakAvMelding::class, StatusForMottakAvMeldingSerializer)
                                contextual(
                                    FeilmeldingForApplikasjonskvittering::class,
                                    FeilmeldingForApplikasjonskvitteringSerializer,
                                )
                                contextual(Id::class, IdSerializer)
                            }
                        }

                        cfg.customCodecs().registerWithDefaultConfig(KotlinSerializationJsonDecoder(json))
                        cfg.customCodecs().registerWithDefaultConfig(KotlinSerializationJsonEncoder(json))
                    }
                    .build()

            afterTest() {
                clearMocks(context.getBean<FastlegeregisteretClient>(), context.getBean<AdresseregisteretClient>())
            }

            test("Happy lookup by FNR") {
                val flr = context.getBean<FastlegeregisteretClient>()
                val arClient = context.getBean<AdresseregisteretClient>()
                val PATIENT_FNR = "16822449879"
                val HERID2 = 454545
                val HERID1 = 1111
                val ORGNUM = "787878"

                every { flr.getPatientGP(PATIENT_FNR) } returns PatientGP("dummyId", HERID2)

                every { arClient.lookupHerId(HERID2) } returns
                    PersonCommunicationParty(
                        HERID2,
                        "Fastlege",
                        CommunicationPartyParent(HERID1, "ParentComunicationParty", ORGNUM),
                        listOf(),
                        listOf(),
                        "Peter",
                        "",
                        "Petterson",
                    )

                val result =
                    webTestClient
                        .get()
                        .uri("/arlookup/16822449879")
                        .accept(MediaType.APPLICATION_JSON)
                        .exchange()
                        .returnResult(ArDetails::class.java)

                result.status.is2xxSuccessful.shouldBeTrue()

                val arDetails = result.responseBody.blockFirst()
                arDetails.herid2 shouldBeEqual HERID2
                arDetails.herid1 shouldBeEqual HERID1
                arDetails.orgNumber shouldBeEqual ORGNUM
                arDetails.pemDigdirSertifikat.shouldNotBeNull()

                verify(exactly = 1) { flr.getPatientGP(any()) }
                verify(exactly = 1) { arClient.lookupHerId(any()) }
            }

            test("Identifier er HerId") {
                val flr = context.getBean<FastlegeregisteretClient>()
                val arClient = context.getBean<AdresseregisteretClient>()

                val HERID2 = 878787
                val HERID1 = 2222
                val ORGNUM = "787878"

                every { arClient.lookupHerId(HERID2) } returns
                    OrganizationCommunicationParty(
                        HERID2,
                        "Fastlege",
                        CommunicationPartyParent(HERID1, "ParentComunicationParty", ORGNUM),
                        listOf(),
                        listOf(),
                        ORGNUM,
                    )

                val result =
                    webTestClient
                        .get()
                        .uri("/arlookup/$HERID2")
                        .accept(MediaType.APPLICATION_JSON)
                        .exchange()
                        .returnResult(ArDetails::class.java)

                result.status.is2xxSuccessful.shouldBeTrue()

                verify(exactly = 0) { flr.getPatientGP(any()) }

                verify(exactly = 1) { arClient.lookupHerId(HERID2) }

                val arDetails = result.responseBody.blockFirst()
                arDetails.herid2 shouldBeEqual HERID2
                arDetails.herid1 shouldBeEqual HERID1
                arDetails.orgNumber shouldBeEqual ORGNUM
                arDetails.pemDigdirSertifikat.shouldNotBeNull()
            }
        }
        /*
        test("My first test") {
            logger.debug { "TTTTTTTTTTTTTTTTTTTTTT" }
            val env =
                StandardEnvironment().apply {
                    propertySources.addFirst(
                        MapPropertySource(
                            "testProperties",
                            mapOf(
                                "nhn.services.ar.url" to "http://test",
                                "nhn.service.ar.username" to "testUsername",
                                "nhn.service.ar.password" to "testPassword",
                            ),
                        )
                    )
                }

            val testRegistrar =
                BeanRegistrarDsl({
                        registerBean<DecoratingFlrClient>() {
                            IntegrationBeans.flrClient(
                                NhnConfig("https://ws-web.test.nhn.no/v2/flr", "her8143143", "SekkDuskKake87"),
                                this.env,
                            )
                        }

                        registerBean<AdresseregisteretClient>() {
                            IntegrationBeans.arClient(
                                NhnConfig("https://ws-web.test.nhn.no/v1/Ar", "her8143143", "SekkDuskKake87")
                            )
                        }
                        registerBean<ServerCodecConfigurer>() {
                            val json = Json {
                                ignoreUnknownKeys = true
                                classDiscriminator = "type"
                                serializersModule = SerializersModule {
                                    contextual(StatusForMottakAvMelding::class, StatusForMottakAvMeldingSerializer)
                                    contextual(
                                        FeilmeldingForApplikasjonskvittering::class,
                                        FeilmeldingForApplikasjonskvitteringSerializer,
                                    )
                                    contextual(Id::class, IdSerializer)
                                }
                            }
                            ServerCodecConfigurer.create().apply {
                                registerDefaults(false)
                                customCodecs().register(KotlinSerializationJsonDecoder(json))
                                customCodecs().register(KotlinSerializationJsonEncoder(json))
                            }
                        }

                        registerBean<Client>() { mockk<Client>() }
                        registerBean<RouterFunction<*>>() {
                            coRouter {
                                arLookup(bean(), bean())
                                dphOut(bean(), bean())
                            }
                        }
                    })
                    .apply { this.env = env }

            val context =
                AnnotationConfigApplicationContext().apply {
                    register(testRegistrar)
                    refresh()
                }

            val webTestClient =
                WebTestClient.bindToRouterFunction(context.getBean<RouterFunction<*>>()).configureClient().build()
            val result =
                webTestClient
                    .get()
                    .uri("/arlookup/16822449879")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .returnResult(ArDetails::class.java)

            val messageOut =
                MessageOut(
                    "testID",
                    "testconvId",
                    "testOnbehalfOf",
                    Sender("123", "321", "Sender"),
                    Receiver("123", "321", "testfnr"),
                    "fagmelding",
                    Patient("1234", "Peter", "Petterson", "petter", "121212"),
                )

            webTestClient.post().uri("/dph/out").bodyValue(messageOut).exchange().returnResult(Any::class.java)
            sleep(1000)
            val response = result.responseBody.blockFirst()
            println(response.toString())
        }

        */
    })
