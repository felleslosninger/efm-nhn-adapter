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

        describe("maskFnrInUrl") {
            it("masks the fødselsnummer in a lookup path") {
                "/nhn-adapter/api/lookup/fastlege-for:$FNR".maskFnrInUrl() shouldBe
                    "/nhn-adapter/api/lookup/fastlege-for:***********"
            }

            it("masks the whole value of a fastlege-for identifier, also when it is not eleven digits") {
                listOf(
                        "${FNR.take(6)}-${FNR.drop(6)}",
                        "${FNR.take(6)}%20${FNR.drop(6)}",
                        "${FNR}0",
                        FNR.dropLast(1),
                        "abc",
                        "",
                        "$FNR%",
                    )
                    .forEach { value ->
                        "/nhn-adapter/api/lookup/fastlege-for:$value".maskFnrInUrl() shouldBe
                            "/nhn-adapter/api/lookup/fastlege-for:***********"
                    }
            }

            it("masks a fastlege-for identifier that is percent-encoded or in another case, and decodes it") {
                "/api/lookup/fastlege-for%3a$FNR".maskFnrInUrl() shouldBe "/api/lookup/fastlege-for:***********"
                "/api/lookup/fastlege-for%253A$FNR".maskFnrInUrl() shouldBe "/api/lookup/fastlege-for%3A***********"
                "/api/lookup/fastlege%2Dfor:${FNR.dropLast(1)}%3${FNR.last()}".maskFnrInUrl() shouldBe
                    "/api/lookup/fastlege-for:***********"
                "/api/lookup/FASTLEGE-FOR:$FNR".maskFnrInUrl() shouldBe "/api/lookup/FASTLEGE-FOR:***********"
            }

            it("masks a fødselsnummer after a percent-escape that ends in a digit") {
                "q=%20$FNR".maskFnrInUrl() shouldBe "q= ***********"
                "q=%22$FNR%22".maskFnrInUrl() shouldBe "q=\"***********\""
            }

            it("stops at the end of the path segment") {
                "/api/lookup/fastlege-for:$FNR/x?y=1".maskFnrInUrl() shouldBe
                    "/api/lookup/fastlege-for:***********/x?y=1"
            }

            it("masks a fødselsnummer elsewhere in the URL") {
                "/api/messages/out/$FNR/statuses".maskFnrInUrl() shouldBe "/api/messages/out/***********/statuses"
                "fnr=$FNR&x=1".maskFnrInUrl() shouldBe "fnr=***********&x=1"
            }

            it("leaves a URL without a fødselsnummer as it is, also when it is percent-encoded") {
                listOf("/nhn-adapter/api/lookup/her-id:8143143", "/api/lookup/her-id%3A8143143", "/a%2Fb%").forEach {
                    it.maskFnrInUrl() shouldBe it
                }
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
