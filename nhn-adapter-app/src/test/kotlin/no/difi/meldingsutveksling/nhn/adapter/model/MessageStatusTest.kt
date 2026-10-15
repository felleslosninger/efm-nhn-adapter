package no.difi.meldingsutveksling.nhn.adapter.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

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
        }
    })
