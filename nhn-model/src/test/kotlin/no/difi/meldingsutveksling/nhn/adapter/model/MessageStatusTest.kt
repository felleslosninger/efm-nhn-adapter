package no.difi.meldingsutveksling.nhn.adapter.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.ks.fiks.nhn.msh.AppRecInfo
import no.ks.fiks.nhn.msh.AppRecStatus
import no.ks.fiks.nhn.msh.DeliveryState
import no.ks.fiks.nhn.msh.StatusInfo

class MessageStatusTest :
    DescribeSpec({
        describe("ApplicationReceiptStatus.fromValue") {
            it("accepts the display value") {
                ApplicationReceiptStatus.fromValue("OkErrorInMessagePart") shouldBe
                    ApplicationReceiptStatus.OK_ERROR_IN_MESSAGE_PART
            }

            it("accepts the constant name") {
                ApplicationReceiptStatus.fromValue("OK_ERROR_IN_MESSAGE_PART") shouldBe
                    ApplicationReceiptStatus.OK_ERROR_IN_MESSAGE_PART
            }

            it("rejects unknown values") {
                shouldThrow<IllegalArgumentException> { ApplicationReceiptStatus.fromValue("Unknown") }
            }
        }

        describe("TransportStatus.fromValue") {
            it("ignores case") { TransportStatus.fromValue("ACKNOWLEDGED") shouldBe TransportStatus.ACKNOWLEDGED }

            it("rejects a missing value") { shouldThrow<IllegalArgumentException> { TransportStatus.fromValue(null) } }
        }

        describe("StatusInfo.toMessageStatus") {
            fun appRecInfo(status: AppRecStatus?) =
                AppRecInfo(appRecStatus = status, appRecId = null, appRecErrors = null)

            it("maps the delivery state and application receipt status") {
                StatusInfo(1000002, true, DeliveryState.REJECTED, appRecInfo(AppRecStatus.OK_ERROR_IN_MESSAGE_PART))
                    .toMessageStatus() shouldBe
                    MessageStatus(
                        receiverHerId = 1000002,
                        transportStatus = TransportStatus.REJECTED,
                        apprecStatus = ApplicationReceiptStatus.OK_ERROR_IN_MESSAGE_PART,
                    )
            }

            it("maps every delivery state") {
                DeliveryState.entries.forEach { state ->
                    StatusInfo(1000002, true, state, null).toMessageStatus().transportStatus.value shouldBe state.value
                }
            }

            it("maps every application receipt status") {
                AppRecStatus.entries.forEach { status ->
                    StatusInfo(1000002, true, DeliveryState.ACKNOWLEDGED, appRecInfo(status))
                        .toMessageStatus()
                        .apprecStatus
                        ?.value shouldBe status.value
                }
            }

            it("maps an abandoned message") {
                StatusInfo(1000002, true, DeliveryState.ABANDONED, null).toMessageStatus() shouldBe
                    MessageStatus(receiverHerId = 1000002, transportStatus = TransportStatus.ABANDONED)
            }

            it("has no application receipt status before the receipt is received") {
                StatusInfo(1000002, false, DeliveryState.UNCONFIRMED, null).toMessageStatus() shouldBe
                    MessageStatus(receiverHerId = 1000002, transportStatus = TransportStatus.UNCONFIRMED)
            }

            it("has no application receipt status when the receipt has none") {
                StatusInfo(1000002, true, DeliveryState.ACKNOWLEDGED, appRecInfo(null)).toMessageStatus() shouldBe
                    MessageStatus(receiverHerId = 1000002, transportStatus = TransportStatus.ACKNOWLEDGED)
            }
        }
    })
