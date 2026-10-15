package no.difi.meldingsutveksling.nhn.adapter.messages

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class BusinessMessageTypeTest :
    DescribeSpec({
        describe("BusinessMessageType.fromType") {
            it("finds the type ignoring case") {
                BusinessMessageType.fromType("DIALOGMELDING_KVITTERING") shouldBe
                    BusinessMessageType.DIALOGMELDING_KVITTERING
            }

            it("rejects unknown types and lists the supported ones") {
                shouldThrow<IllegalArgumentException> { BusinessMessageType.fromType("arkivmelding") }
                    .message shouldContain "Expecting one of dialogmelding,dialogmelding_kvittering"
            }
        }

        describe("StandardBusinessDocumentType") {
            it("uses the type as field name and the message class as value type") {
                BusinessMessageType.DIALOGMELDING.fieldName shouldBe "dialogmelding"
                BusinessMessageType.DIALOGMELDING.valueType shouldBe DialogmeldingMessage::class.java
                BusinessMessageType.DIALOGMELDING_KVITTERING.valueType shouldBe
                    DialogmeldingKvitteringMessage::class.java
            }
        }
    })
