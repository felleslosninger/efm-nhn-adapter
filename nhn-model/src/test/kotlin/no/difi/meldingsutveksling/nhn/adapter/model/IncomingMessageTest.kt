package no.difi.meldingsutveksling.nhn.adapter.model

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.KxJson
import no.ks.fiks.nhn.msh.MessageWithMetadata

class IncomingMessageTest :
    DescribeSpec({
        val messageId = UUID.randomUUID()
        val businessDocumentDate = OffsetDateTime.of(2026, 10, 14, 12, 0, 0, 0, ZoneOffset.ofHours(2))
        val incomingMessage =
            IncomingMessage(
                id = messageId.toString(),
                receiverHerId = 1000002,
                senderHerId = 2000002,
                businessDocumentId = "document-1",
                businessDocumentDate = businessDocumentDate,
                isAppRec = true,
            )

        describe("MessageWithMetadata.toInMessage") {
            it("maps the message metadata") {
                MessageWithMetadata(
                        id = messageId,
                        contentType = "application/xml",
                        receiverHerId = 1000002,
                        senderHerId = 2000002,
                        businessDocumentId = "document-1",
                        businessDocumentDate = businessDocumentDate,
                        isAppRec = true,
                    )
                    .toInMessage() shouldBe incomingMessage
            }
        }

        describe("JSON serialization") {
            it("writes the business document date as an ISO offset date-time") {
                KxJson.encode(incomingMessage, IncomingMessage.serializer()) shouldContain
                    """"businessDocumentDate":"2026-10-14T12:00:00+02:00""""
            }

            it("round trips through JSON") {
                val json = KxJson.encode(incomingMessage, IncomingMessage.serializer())

                KxJson.decode(json, IncomingMessage.serializer()) shouldBe incomingMessage
            }

            it("ignores unknown keys") {
                KxJson.decode("""{"id":"document-1","unknown":true}""", GetDocumentInput.serializer()) shouldBe
                    GetDocumentInput("document-1")
            }

            it("decodes a JSON element") {
                KxJson.decode(
                    buildJsonObject { put("id", JsonPrimitive("document-1")) },
                    GetDocumentInput.serializer(),
                ) shouldBe GetDocumentInput("document-1")
            }
        }
    })
