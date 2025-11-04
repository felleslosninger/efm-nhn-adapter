package no.difi.meldingsutveksling.nhn.adapter

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.builtins.ListSerializer
import no.difi.meldingsutveksling.nhn.adapter.model.SerializableApplicationReceiptInfo
import no.difi.meldingsutveksling.nhn.adapter.model.toOriginal
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.hdir.StatusForMottakAvMelding
import no.ks.fiks.nhn.msh.ApplicationReceiptInfo
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.IncomingApplicationReceiptError
import no.ks.fiks.nhn.msh.MultiTenantHelseIdTokenParameters
import org.springframework.beans.factory.BeanRegistrarDsl
import org.springframework.beans.factory.getBean
import org.springframework.context.annotation.AnnotationConfigApplicationContext

@OptIn(ExperimentalUuidApi::class)
class InHandlerDSLTest :
    ShouldSpec({
        context("Test reciept") {
            val receiptContext = BeanRegistrarDsl {
                registerBean<Client>() { mockk() }
                testCoRouter { ctx -> this.incomingReciept(ctx.bean()) }
            }

            val context =
                AnnotationConfigApplicationContext().apply {
                    register(receiptContext)
                    refresh()
                }

            val webTestClient = webTestClient(context.getBean()) { this.responseTimeout(60.seconds.toJavaDuration()) }

            should("When identifier is illegal responds with BAD REQUEST") {
                webTestClient
                    .get()
                    .uri(Routes.INCOMING_RECEIPT, "ivalididentifier")
                    .exchange()
                    .expectStatus()
                    .isBadRequest
                    .expectBody(ApiError::class.java)
                    .returnResult()
                    .responseBody
                    .shouldNotBeNull()
                    .message
                    ?.shouldBe("Message id is wrong format")
            }

            should("missing onbehaldof org number responds with BAD REQUEST") {
                webTestClient
                    .get()
                    .uri(Routes.INCOMING_RECEIPT, Uuid.random().toString())
                    .exchange()
                    .expectStatus()
                    .isBadRequest
                    .expectBody(ApiError::class.java)
                    .returnResult()
                    .responseBody
                    .shouldNotBeNull()
                    .message
                    ?.shouldBe("On behalf of organisation is not provided.")
            }

            should("When input is valid getApplicationReceiptsForMessage is invoked with correct arguments") {
                val validIdentifier = Uuid.random()
                val onBehalfOfOrgnummer = "234234324"
                // val requestPar slot<RequestParameters>()
                coEvery {
                    context
                        .getBean<Client>()
                        .getApplicationReceiptsForMessage(
                            match { it.toString() == validIdentifier.toString() },
                            match {
                                (it.helseId?.tenant as MultiTenantHelseIdTokenParameters).parentOrganization ==
                                    onBehalfOfOrgnummer.toString()
                            },
                        )
                } returns listOf<ApplicationReceiptInfo>()

                webTestClient
                    .get()
                    .uri { uriBuilder ->
                        uriBuilder
                            .path(Routes.INCOMING_RECEIPT)
                            .queryParam("onBehalfOf", onBehalfOfOrgnummer)
                            .build(validIdentifier.toString())
                    }
                    .exchange()

                coVerify(exactly = 1) {
                    context
                        .getBean<Client>()
                        .getApplicationReceiptsForMessage(
                            UUID.fromString(validIdentifier.toString()),
                            match {
                                (it.helseId?.tenant as? MultiTenantHelseIdTokenParameters)?.parentOrganization ==
                                    onBehalfOfOrgnummer
                            },
                        )
                }
            }

            should("Happy case") {
                val validIdentifier = Uuid.random()
                val onBehalfOfOrgnummer = "234234324"
                val applicationReceipt =
                    ApplicationReceiptInfo(
                        4324,
                        StatusForMottakAvMelding.AVVIST,
                        listOf(
                            IncomingApplicationReceiptError(
                                FeilmeldingForApplikasjonskvittering.PASIENT_EKSISTERER_IKKE_HOS_MOTTAKER,
                                "details",
                                "A3",
                                null,
                                null,
                            )
                        ),
                    )

                coEvery {
                    context
                        .getBean<Client>()
                        .getApplicationReceiptsForMessage(
                            match { it.toString() == validIdentifier.toString() },
                            match {
                                (it.helseId?.tenant as MultiTenantHelseIdTokenParameters).parentOrganization ==
                                    onBehalfOfOrgnummer.toString()
                            },
                        )
                } returns listOf(applicationReceipt)

                webTestClient
                    .get()
                    .uri { uriBuilder ->
                        uriBuilder
                            .path(Routes.INCOMING_RECEIPT)
                            .queryParam("onBehalfOf", onBehalfOfOrgnummer)
                            .build(validIdentifier.toString())
                    }
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .returnResult()
                    .responseBody
                    .run {
                        this.shouldNotBeNull()
                        val response =
                            jsonNhn.decodeFromString(
                                ListSerializer(SerializableApplicationReceiptInfo.serializer()),
                                this.toString(Charsets.UTF_8),
                            )
                        response.shouldNotBeNull()
                        response.shouldHaveSize(1)
                        with(response.first()) {
                            shouldNotBeNull()
                            shouldBeTypeOf<SerializableApplicationReceiptInfo>()
                            recieverHerId shouldBeEqual applicationReceipt.receiverHerId
                            status.shouldNotBeNull()
                            status shouldBeEqual applicationReceipt.status!!
                            errors.map { it.toOriginal() } shouldBe applicationReceipt.errors
                        }
                        response.first().shouldNotBeNull()
                        response.first().shouldBeTypeOf<SerializableApplicationReceiptInfo>()
                    }

                coVerify(exactly = 1) {
                    context
                        .getBean<Client>()
                        .getApplicationReceiptsForMessage(
                            UUID.fromString(validIdentifier.toString()),
                            match {
                                (it.helseId?.tenant as? MultiTenantHelseIdTokenParameters)?.parentOrganization ==
                                    onBehalfOfOrgnummer
                            },
                        )
                }
            }
        }
    })
