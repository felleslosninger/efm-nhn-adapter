package no.difi.meldingsutveksling.nhn.adapter.logging

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.IOException
import java.net.ConnectException
import no.difi.meldingsutveksling.nhn.adapter.errors.ClientInputException
import no.difi.meldingsutveksling.nhn.adapter.errors.InvalidMshDataException
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptException
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import org.xml.sax.SAXParseException

private const val VALIDATION_MESSAGE = "cvc-datatype-valid.1.2.1: '010190-12345' is not a valid value for 'dateTime'."

private fun invalidXml() =
    ApplicationReceiptException(
        FeilmeldingForApplikasjonskvittering.UGYLDIG_XML,
        "The MsgHead is not valid",
        SAXParseException(VALIDATION_MESSAGE, null),
    )

class LoggableMessageTest :
    DescribeSpec({
        describe("loggableMessage") {
            it("includes the messages of the causes") {
                IOException("Request failed", ConnectException("Connection refused")).loggableMessage() shouldBe
                    "Request failed; caused by ConnectException: Connection refused"
            }

            it("names only the class of the causes of a ClientInputException") {
                ClientInputException("Invalid identifier", IllegalArgumentException("fastlege-for:010190-12345"))
                    .loggableMessage() shouldBe "Invalid identifier; caused by IllegalArgumentException"
            }

            it("names only the class of the causes of an ApplicationReceiptException") {
                invalidXml().loggableMessage() shouldBe "The MsgHead is not valid; caused by SAXParseException"
            }

            it("keeps the message of the exceptions above an ApplicationReceiptException") {
                InvalidMshDataException("Invalid data from MSH: The MsgHead is not valid", invalidXml())
                    .loggableMessage() shouldBe
                    "Invalid data from MSH: The MsgHead is not valid; caused by ApplicationReceiptException: " +
                        "The MsgHead is not valid; caused by SAXParseException"
            }
        }

        describe("hasDataInCauses") {
            it("is true below an ApplicationReceiptException") {
                InvalidMshDataException("Invalid data from MSH", invalidXml()).hasDataInCauses() shouldBe true
            }

            it("is true below a ClientInputException") {
                ClientInputException("Invalid input", IllegalStateException("x")).hasDataInCauses() shouldBe true
            }

            it("is false for an ApplicationReceiptException without a cause") {
                ApplicationReceiptException(FeilmeldingForApplikasjonskvittering.IKKE_STOTTET_FORMAT)
                    .hasDataInCauses() shouldBe false
            }

            it("is false for other errors") {
                InvalidMshDataException("Invalid data from MSH", IllegalStateException("No MsgInfo"))
                    .hasDataInCauses() shouldBe false
            }
        }
    })
