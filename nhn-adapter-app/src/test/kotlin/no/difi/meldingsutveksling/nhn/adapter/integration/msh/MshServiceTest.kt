package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.util.Base64
import java.util.UUID
import no.difi.meldingsutveksling.domain.Iso6523
import no.difi.meldingsutveksling.nhn.adapter.errors.ClientInputException
import no.difi.meldingsutveksling.nhn.adapter.errors.InvalidMshDataException
import no.difi.meldingsutveksling.nhn.adapter.messages.DialogmeldingKvitteringMessage
import no.difi.meldingsutveksling.nhn.adapter.messages.DialogmeldingKvitteringStatus
import no.difi.meldingsutveksling.nhn.adapter.messages.KvitteringStatusMessage
import no.difi.meldingsutveksling.nhn.adapter.security.ClientContext
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.nhn.msh.Client
import no.ks.fiks.nhn.msh.MshInternalClient
import no.ks.fiks.nhn.msh.MultiTenantHelseIdTokenParameters
import no.ks.fiks.nhn.msh.RequestParameters
import no.ks.fiks.nhn.msh.SingleTenantHelseIdTokenParameters
import no.nhn.msh.v2.model.AppRecStatus
import no.nhn.msh.v2.model.GetBusinessDocumentResponse
import no.nhn.msh.v2.model.PostAppRecRequest

private const val CONSUMER_ORG_NUMBER = "123456789"
private const val SUPPLIER_ORG_NUMBER = "987654321"
private const val SENDER_HER_ID = 1000002

class MshServiceTest :
    DescribeSpec({
        val mshClient = mockk<Client>()
        val internalClient = mockk<MshInternalClient>()
        val mshService = MshService(mshClient, internalClient)

        fun clientContext(supplier: String? = null) =
            mockk<ClientContext>().also {
                every { it.consumer } returns Iso6523.parse("0192:$CONSUMER_ORG_NUMBER")
                every { it.supplier } returns supplier?.let { orgNumber -> Iso6523.parse("0192:$orgNumber") }
            }

        describe("request parameters") {
            val messageId = UUID.randomUUID()

            it("uses a single-tenant HelseID token when the client acts on its own behalf") {
                val requestParameters = slot<RequestParameters>()
                coEvery { mshClient.getStatus(messageId, capture(requestParameters)) } returns emptyList()

                mshService.getStatus(messageId, clientContext())

                val tenant = requestParameters.captured.helseId?.tenant
                tenant.shouldBeInstanceOf<SingleTenantHelseIdTokenParameters>()
                tenant.childOrganization shouldBe CONSUMER_ORG_NUMBER
            }

            it("uses a multi-tenant HelseID token for the consumer when the client acts through a supplier") {
                val requestParameters = slot<RequestParameters>()
                coEvery { mshClient.getStatus(messageId, capture(requestParameters)) } returns emptyList()

                mshService.getStatus(messageId, clientContext(supplier = SUPPLIER_ORG_NUMBER))

                val tenant = requestParameters.captured.helseId?.tenant
                tenant.shouldBeInstanceOf<MultiTenantHelseIdTokenParameters>()
                // The consumer is the customer (parent) organization the supplier acts on behalf of
                tenant.parentOrganization shouldBe CONSUMER_ORG_NUMBER
                tenant.childOrganization.shouldBeNull()
            }
        }

        describe("getBusinessDocument") {
            val messageId = UUID.randomUUID()

            it("rejects an unsupported transfer encoding") {
                coEvery { internalClient.getBusinessDocument(messageId, any()) } returns
                    GetBusinessDocumentResponse()
                        .contentType("application/xml")
                        .contentTransferEncoding("quoted-printable")
                        .businessDocument("")

                shouldThrow<InvalidMshDataException> {
                        mshService.getBusinessDocument(messageId.toString(), clientContext())
                    }
                    .message shouldContain "quoted-printable"
            }

            it("rejects an unsupported content type") {
                coEvery { internalClient.getBusinessDocument(messageId, any()) } returns
                    GetBusinessDocumentResponse()
                        .contentType("application/json")
                        .contentTransferEncoding("base64")
                        .businessDocument("")

                shouldThrow<InvalidMshDataException> {
                        mshService.getBusinessDocument(messageId.toString(), clientContext())
                    }
                    .message shouldContain "application/json"
            }

            it("reports a business document it cannot interpret as invalid data from MSH") {
                coEvery { internalClient.getBusinessDocument(messageId, any()) } returns
                    GetBusinessDocumentResponse()
                        .contentType("application/xml")
                        .contentTransferEncoding("base64")
                        .businessDocument(Base64.getEncoder().encodeToString("<Unknown/>".toByteArray()))

                shouldThrow<InvalidMshDataException> {
                        mshService.getBusinessDocument(messageId.toString(), clientContext())
                    }
                    .message shouldContain "Unknown"
            }

            it("rejects an id that is not a UUID as invalid client input") {
                shouldThrow<ClientInputException> { mshService.getBusinessDocument("not-a-uuid", clientContext()) }
                    .message shouldBe "id must be an UUID!"
            }
        }

        describe("sendApplicationReceipt") {
            val relatedToMessageId = UUID.randomUUID()
            val appRecId = UUID.randomUUID()

            suspend fun sendReceipt(receipt: DialogmeldingKvitteringMessage): PostAppRecRequest {
                val request = slot<PostAppRecRequest>()
                coEvery {
                    internalClient.postAppRec(relatedToMessageId, SENDER_HER_ID, capture(request), any())
                } returns appRecId

                mshService.sendApplicationReceipt(SENDER_HER_ID, receipt, clientContext()) shouldBe appRecId
                return request.captured
            }

            it("maps the receipt status") {
                val request =
                    sendReceipt(
                        DialogmeldingKvitteringMessage(
                            relatedToMessageId = relatedToMessageId.toString(),
                            status = DialogmeldingKvitteringStatus.OK,
                            messages = null,
                        )
                    )

                request.appRecStatus shouldBe AppRecStatus.OK
                request.appRecErrorList.shouldBeNull()
            }

            it("maps known error codes to their code list values") {
                val error = FeilmeldingForApplikasjonskvittering.UGYLDIG_XML
                val request =
                    sendReceipt(
                        DialogmeldingKvitteringMessage(
                            relatedToMessageId = relatedToMessageId.toString(),
                            status = DialogmeldingKvitteringStatus.REJECTED,
                            messages = listOf(KvitteringStatusMessage(error.verdi, "Invalid XML")),
                        )
                    )

                request.appRecStatus shouldBe AppRecStatus.REJECTED
                request.appRecErrorList.shouldNotBeNull() shouldHaveSize 1
                with(request.appRecErrorList.shouldNotBeNull().single()) {
                    errorCode shouldBe error.verdi
                    description shouldBe error.navn
                    details shouldBe "Invalid XML"
                    oid shouldBe error.kodeverk
                }
            }

            it("maps unknown error codes to UKJENT") {
                val request =
                    sendReceipt(
                        DialogmeldingKvitteringMessage(
                            relatedToMessageId = relatedToMessageId.toString(),
                            status = DialogmeldingKvitteringStatus.OK_ERROR_IN_MESSAGE_PART,
                            messages = listOf(KvitteringStatusMessage("not-a-code", "Something went wrong")),
                        )
                    )

                request.appRecStatus shouldBe AppRecStatus.OK_ERROR_IN_MESSAGE_PART
                with(request.appRecErrorList.shouldNotBeNull().single()) {
                    errorCode shouldBe FeilmeldingForApplikasjonskvittering.UKJENT.verdi
                    details shouldBe "Something went wrong"
                }
            }
        }
    })
