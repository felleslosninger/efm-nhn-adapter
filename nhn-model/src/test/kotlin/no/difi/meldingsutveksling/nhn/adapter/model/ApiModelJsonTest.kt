package no.difi.meldingsutveksling.nhn.adapter.model

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.KxJson

/** The JSON field names are the contract with integrasjonspunktet. */
class ApiModelJsonTest :
    DescribeSpec({
        fun fieldNames(json: String): Set<String> = Json.parseToJsonElement(json).jsonObject.keys

        describe("ArDetails") {
            val arDetails =
                ArDetails(
                    parentHerId = 1000001,
                    parentName = "Test kommune",
                    orgNumber = "123456789",
                    herId = 1000002,
                    name = "Helse og omsorg",
                    derCertificate = "MIIB",
                )

            it("has the expected JSON fields") {
                fieldNames(KxJson.encode(arDetails, ArDetails.serializer())) shouldBe
                    setOf("parentHerId", "parentName", "orgNumber", "herId", "name", "derCertificate")
            }

            it("round trips through JSON") {
                KxJson.decode(KxJson.encode(arDetails, ArDetails.serializer()), ArDetails.serializer()) shouldBe
                    arDetails
            }
        }

        describe("ApiError") {
            val apiError =
                ApiError(
                    status = 404,
                    reason = "Not Found",
                    errorCode = "X99",
                    message = "Annen feil",
                    details = "HER-id not found",
                    technicalCode = "E7780",
                    path = "/api/lookup/123",
                    requestId = "request-1",
                    traceId = "4bf92f3577b34da6a3ce929d0e0e4736",
                    timestamp = "2026-10-14T12:00:00Z",
                )

            it("has the expected JSON fields") {
                fieldNames(KxJson.encode(apiError, ApiError.serializer())) shouldBe
                    setOf(
                        "status",
                        "reason",
                        "errorCode",
                        "message",
                        "details",
                        "technicalCode",
                        "path",
                        "requestId",
                        "traceId",
                        "timestamp",
                    )
            }

            it("decodes an error without the optional fields") {
                val withoutOptionals = apiError.copy(details = null, technicalCode = null, traceId = null)
                val json = KxJson.encode(withoutOptionals, ApiError.serializer())

                KxJson.decode(
                    JsonObject(Json.parseToJsonElement(json).jsonObject - "details" - "technicalCode" - "traceId"),
                    ApiError.serializer(),
                ) shouldBe withoutOptionals
            }
        }
    })
