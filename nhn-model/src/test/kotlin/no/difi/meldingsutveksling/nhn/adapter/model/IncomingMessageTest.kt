package no.difi.meldingsutveksling.nhn.adapter.model

import io.kotest.assertions.throwables.shouldThrow
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
            fun metadata(
                receiverHerIds: List<Int>? = listOf(1000002),
                senderHerId: Int? = 2000002,
                businessDocumentId: String? = "document-1",
                businessDocumentMsgType: String? = "APPREC",
            ) =
                MessageWithMetadata(
                    id = messageId.toString(),
                    contentType = "application/xml",
                    receiverHerIds = receiverHerIds,
                    senderHerId = senderHerId,
                    businessDocumentId = businessDocumentId,
                    businessDocumentDate = businessDocumentDate,
                    businessDocumentMsgType = businessDocumentMsgType,
                )

            it("maps the message metadata") { metadata().toInMessage(1000002) shouldBe incomingMessage }

            it("is an application receipt only when the MsgType is APPREC") {
                metadata(businessDocumentMsgType = "apprec").toInMessage(1000002).isAppRec shouldBe true
                metadata(businessDocumentMsgType = "DIALOG_HELSEFAGLIG").toInMessage(1000002).isAppRec shouldBe false
            }

            it("gives the message to the requested receiver when there are several receivers") {
                metadata(receiverHerIds = listOf(1000001, 1000002, 1000003)).toInMessage(1000002) shouldBe
                    incomingMessage
            }

            it("rejects a message when MSH does not list the receivers, as the receiver cannot be checked") {
                shouldThrow<IllegalArgumentException> { metadata(receiverHerIds = null).toInMessage(1000002) }
            }

            it("rejects a message not received by the requested receiver") {
                shouldThrow<IllegalArgumentException> {
                    metadata(receiverHerIds = listOf(1000001)).toInMessage(1000002)
                }
            }

            it("rejects a message without sender") {
                shouldThrow<IllegalArgumentException> { metadata(senderHerId = null).toInMessage(1000002) }
            }

            it("rejects a message without business document id") {
                shouldThrow<IllegalArgumentException> { metadata(businessDocumentId = null).toInMessage(1000002) }
            }

            it("rejects a message without MsgType") {
                shouldThrow<IllegalArgumentException> { metadata(businessDocumentMsgType = null).toInMessage(1000002) }
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
