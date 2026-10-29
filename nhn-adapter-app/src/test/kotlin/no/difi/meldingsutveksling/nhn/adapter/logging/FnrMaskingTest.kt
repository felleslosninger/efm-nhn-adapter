package no.difi.meldingsutveksling.nhn.adapter.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.slf4j.LoggerFactory

private const val FNR = "16822449879"

class FnrMaskingTest :
    DescribeSpec({
        describe("maskFnr") {
            it("masks a fødselsnummer") { "Ukjent pasient $FNR.".maskFnr() shouldBe "Ukjent pasient ***********." }

            it("masks a fødselsnummer in a lookup identifier") {
                "Invalid NHN identifier: fastlege-for:$FNR".maskFnr() shouldBe
                    "Invalid NHN identifier: fastlege-for:***********"
            }

            it("leaves shorter and longer numbers") {
                val message = "HER-id 2000002, org.nr. 987654321, epoch 1761734400000"
                message.maskFnr() shouldBe message
            }
        }

        describe("FnrMaskingLogstashEncoder") {
            val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
            val encoder =
                FnrMaskingLogstashEncoder().apply {
                    context = loggerContext
                    start()
                }

            it("masks the fødselsnummer in the message and the stack trace") {
                val cause = IllegalStateException("SOAP fault for $FNR")
                val event =
                    LoggingEvent(
                        javaClass.name,
                        loggerContext.getLogger("test"),
                        Level.ERROR,
                        "Lookup of $FNR failed",
                        RuntimeException("Unknown error for $FNR", cause),
                        null,
                    )

                val json = encoder.encode(event).decodeToString()

                json shouldContain "Lookup of ***********"
                json shouldContain "SOAP fault for ***********"
                json shouldNotContain FNR
            }

            it("is the encoder of the application log") {
                val logbackConfig = checkNotNull(javaClass.getResource("/logback-spring.xml")).readText()

                logbackConfig shouldContain "encoder class=\"${FnrMaskingLogstashEncoder::class.qualifiedName}\""
            }
        }
    })
