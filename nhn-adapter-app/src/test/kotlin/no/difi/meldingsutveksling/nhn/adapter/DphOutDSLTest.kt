package no.difi.meldingsutveksling.nhn.adapter

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.serialization.json.Json
import no.difi.meldingsutveksling.nhn.adapter.handlers.HerIdNotFound
import no.difi.meldingsutveksling.nhn.adapter.model.Fagmelding
import no.ks.fiks.nhn.ar.AdresseregisteretClient
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.MultiTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.OrganizationReceiverDetails
import no.ks.fiks.nhn.msh.OutgoingBusinessDocument
import no.ks.fiks.nhn.msh.PersonReceiverDetails
import no.ks.fiks.nhn.msh.RequestParameters
import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.beans.factory.getBean
import org.springframework.context.annotation.AnnotationConfigApplicationContext

@OptIn(ExperimentalUuidApi::class)
class DphOutDSLTest :
    ShouldSpec({
        context("Testing DPH out ") {
            val arLookupContext = BeanRegistrarDsl {
                registerBean<AdresseregisteretClient> { mockk() }
                registerBean<Client> { mockk() }
                testCoRouter { ctx -> dphOut(ctx.bean(), ctx.bean()) }
            }
            val context =
                AnnotationConfigApplicationContext().apply {
                    register(arLookupContext)
                    refresh()
                }

            val webTestClient = webTestClient(context.getBean()) { this.responseTimeout(60.seconds.toJavaDuration()) }

            afterTest() { clearMocks(context.getBean<AdresseregisteretClient>()) }

            should("Should return BAD request if HerID is not found") {
                val HERID_SOM_FINNES_IKKE = "656237"
                val HERID_SOM_FINNES = "856268"

                val arService = context.getBean<AdresseregisteretClient>()

                val slot = slot<Int>()
                every { arService.lookupHerId(capture(slot)) } answers
                    {
                        slot.captured
                            .takeIf { it == HERID_SOM_FINNES.toInt() }
                            ?.let {
                                testNhnServiceCommunicationParty(
                                    HERID_SOM_FINNES.toInt(),
                                    PARENT_HER_ID.toInt(),
                                    ON_BEHALF_OF_ORGNUM,
                                )
                            } ?: throw HerIdNotFound()
                    }

                val messageOut =
                    messageOutTemplate.modify {
                        sender { herid2 = HERID_SOM_FINNES_IKKE }
                        reciever { herid2 = HERID_SOM_FINNES_IKKE }
                    }

                webTestClient
                    .post()
                    .uri("/dph/out")
                    .bodyValue(messageOut)
                    .exchange()
                    .returnResult(String::class.java)
                    .status
                    .is4xxClientError shouldBe true

                webTestClient
                    .post()
                    .uri("/dph/out")
                    .bodyValue(messageOut.modify { sender { herid2 = HERID_SOM_FINNES } })
                    .exchange()
                    .returnResult(String::class.java)
                    .status
                    .is4xxClientError shouldBe true

                verify(exactly = 2) { arService.lookupHerId(HERID_SOM_FINNES_IKKE.toInt()) }
                verify(exactly = 1) { arService.lookupHerId(HERID_SOM_FINNES.toInt()) }
                verify(exactly = 3) { arService.lookupHerId(any()) }
            }

            should("Send til fastlege da FNR er lagt til receiver") {
                val PARENT_HER_ID = "1212"
                val HER_ID_ORG = "856268"
                val HER_ID_PERSON = "65657"
                val slot = slot<Int>()
                val arService = context.getBean<AdresseregisteretClient>()
                val mshClient = context.getBean<Client>()
                every { arService.lookupHerId(capture(slot)) } answers
                    {
                        when (slot.captured.toString()) {
                            HER_ID_ORG,
                            HER_ID_PERSON ->
                                testFastlegeCommunicationParty(
                                    HER_ID_PERSON.toInt(),
                                    PARENT_HER_ID.toInt(),
                                    ON_BEHALF_OF_ORGNUM,
                                )
                            else -> throw HerIdNotFound()
                        }
                    }

                val businessDocumentSlot = slot<OutgoingBusinessDocument>()

                coEvery { mshClient.sendMessage(capture(businessDocumentSlot), any()) } throws
                    RuntimeException("Terminate test here")

                val mockMessageOutWithFNR =
                    messageOutTemplate.modify {
                        sender { herid2 = HER_ID_ORG }
                        reciever { herid2 = HER_ID_PERSON }
                        fagmelding =
                            Json {}
                                .encodeToString(
                                    (Json {}.decodeFromString(this.fagmelding) as Fagmelding).copy(
                                        responsibleHealthcareProfessionalId = HER_ID_PERSON
                                    )
                                )
                    }

                val mockMessageOutWithoutFNR = mockMessageOutWithFNR.modify { reciever { patientFnr = null } }

                webTestClient
                    .post()
                    .uri("/dph/out")
                    .bodyValue(mockMessageOutWithFNR)
                    .exchange()
                    .returnResult(String::class.java)
                    .status
                    .is5xxServerError shouldBe true

                businessDocumentSlot.captured.receiver.child::class shouldBe PersonReceiverDetails::class

                webTestClient
                    .post()
                    .uri("/dph/out")
                    .bodyValue(mockMessageOutWithoutFNR)
                    .exchange()
                    .returnResult(String::class.java)
                    .status
                    .is5xxServerError shouldBe true

                businessDocumentSlot.captured.receiver.child::class shouldBe OrganizationReceiverDetails::class
            }

            should("Return EDI message referanse when valid document is sendt") {
                val PARENT_HER_ID = "1212"
                val HER_ID_ORG = "856268"
                val HER_ID_PERSON = "65657"

                val slot = slot<Int>()
                val arService = context.getBean<AdresseregisteretClient>()
                val mshClient = context.getBean<Client>()
                every { arService.lookupHerId(capture(slot)) } answers
                    {
                        when (slot.captured.toString()) {
                            HER_ID_ORG,
                            HER_ID_PERSON ->
                                testFastlegeCommunicationParty(
                                    HER_ID_PERSON.toInt(),
                                    PARENT_HER_ID.toInt(),
                                    ON_BEHALF_OF_ORGNUM,
                                )
                            else -> throw HerIdNotFound()
                        }
                    }

                val businessDocumentSlot = slot<OutgoingBusinessDocument>()
                val slotRequestParam = slot<RequestParameters>()
                coEvery { mshClient.sendMessage(capture(businessDocumentSlot), capture(slotRequestParam)) } returns
                    Uuid.random().toJavaUuid()

                val mockMessageOut =
                    messageOutTemplate.modify {
                        reciever { herid2 = HER_ID_PERSON }
                        sender { herid2 = HER_ID_ORG }
                        fagmelding =
                            Json {}
                                .encodeToString(
                                    (Json {}.decodeFromString(this.fagmelding) as Fagmelding).copy(
                                        responsibleHealthcareProfessionalId = HER_ID_PERSON
                                    )
                                )
                    }

                val mockMessageOutWithoutFNR = mockMessageOut.modify { reciever { patientFnr = null } }

                var result =
                    webTestClient
                        .post()
                        .uri("/dph/out")
                        .bodyValue(mockMessageOut)
                        .exchange()
                        .returnResult(String::class.java)
                result.status.is2xxSuccessful shouldBe true
                Uuid.parse(result.responseBody.awaitFirst())

                (slotRequestParam.captured.helseId!!.tenant as MultiTenantHelseIdTokenParameters)
                    .parentOrganization shouldBe ON_BEHALF_OF_ORGNUM
                businessDocumentSlot.captured.receiver.child::class shouldBe PersonReceiverDetails::class

                result =
                    webTestClient
                        .post()
                        .uri("/dph/out")
                        .bodyValue(mockMessageOutWithoutFNR)
                        .exchange()
                        .returnResult(String::class.java)
                result.status.is2xxSuccessful shouldBe true
                Uuid.parse(result.responseBody.awaitFirst())

                businessDocumentSlot.captured.receiver.child::class shouldBe OrganizationReceiverDetails::class
            }
        }
    })

private val PARENT_HER_ID = "1212"

private val messageOutTemplate = testMessageOut {
    reciever { herid1 = PARENT_HER_ID }
    sender { herid1 = PARENT_HER_ID }
}
