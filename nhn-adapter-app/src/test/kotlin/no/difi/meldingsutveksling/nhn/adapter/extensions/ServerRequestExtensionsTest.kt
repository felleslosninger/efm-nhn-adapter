package no.difi.meldingsutveksling.nhn.adapter.extensions

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.UUID
import no.difi.meldingsutveksling.nhn.adapter.errors.ClientInputException
import org.springframework.mock.web.reactive.function.server.MockServerRequest

class ServerRequestExtensionsTest :
    DescribeSpec({
        describe("getMessageId") {
            it("parses the message id path variable") {
                val messageId = UUID.randomUUID()

                MockServerRequest.builder()
                    .pathVariable("messageId", messageId.toString())
                    .build()
                    .getMessageId() shouldBe messageId
            }

            it("rejects a message id that is not a UUID") {
                shouldThrow<ClientInputException> {
                        MockServerRequest.builder().pathVariable("messageId", "not-a-uuid").build().getMessageId()
                    }
                    .message shouldBe "messageId must be an UUID!"
            }
        }

        describe("getReceiverHerId") {
            it("parses the receiver HER-id query parameter") {
                MockServerRequest.builder().queryParam("receiverHerId", "1000002").build().getReceiverHerId() shouldBe
                    1000002
            }

            it("rejects a receiver HER-id that is not an integer") {
                shouldThrow<ClientInputException> {
                        MockServerRequest.builder().queryParam("receiverHerId", "abc").build().getReceiverHerId()
                    }
                    .message shouldBe "receiverHerId must be an integer"
            }

            it("rejects a missing receiver HER-id") {
                shouldThrow<ClientInputException> { MockServerRequest.builder().build().getReceiverHerId() }
                    .message shouldBe "receiverHerId cannot be null"
            }
        }

        describe("getHerId") {
            it("parses the HER-id query parameter") {
                MockServerRequest.builder().queryParam("herId", "1000002").build().getHerId() shouldBe 1000002
            }

            it("rejects a missing HER-id") {
                shouldThrow<ClientInputException> { MockServerRequest.builder().build().getHerId() }.message shouldBe
                    "herId cannot be null"
            }
        }

        describe("getNotificationsToFetch") {
            it("parses the number of notifications to fetch") {
                MockServerRequest.builder()
                    .queryParam("notificationsToFetch", "50")
                    .build()
                    .getNotificationsToFetch() shouldBe 50
            }

            it("is null when the client does not ask for a number") {
                MockServerRequest.builder().build().getNotificationsToFetch() shouldBe null
            }

            it("rejects a number that is not an integer") {
                shouldThrow<ClientInputException> {
                        MockServerRequest.builder()
                            .queryParam("notificationsToFetch", "many")
                            .build()
                            .getNotificationsToFetch()
                    }
                    .message shouldBe "notificationsToFetch must be an integer"
            }
        }
    })
