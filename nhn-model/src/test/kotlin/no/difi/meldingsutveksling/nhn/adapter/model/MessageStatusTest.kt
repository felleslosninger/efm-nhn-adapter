package no.difi.meldingsutveksling.nhn.adapter.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
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
            it("maps the delivery state and application receipt status") {
                StatusInfo(1000002, DeliveryState.REJECTED, AppRecStatus.OK_ERROR_IN_MESSAGE_PART)
                    .toMessageStatus() shouldBe
                    MessageStatus(
                        receiverHerId = 1000002,
                        transportStatus = TransportStatus.REJECTED,
                        apprecStatus = ApplicationReceiptStatus.OK_ERROR_IN_MESSAGE_PART,
                    )
            }

            it("has no application receipt status before the receipt is received") {
                StatusInfo(1000002, DeliveryState.UNCONFIRMED, null).toMessageStatus() shouldBe
                    MessageStatus(receiverHerId = 1000002, transportStatus = TransportStatus.UNCONFIRMED)
            }
        }
    })
