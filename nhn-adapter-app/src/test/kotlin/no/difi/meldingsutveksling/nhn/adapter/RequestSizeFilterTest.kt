package no.difi.meldingsutveksling.nhn.adapter

import io.kotest.core.spec.style.DescribeSpec
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.util.unit.DataSize
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse

private val MAX_REQUEST_SIZE = DataSize.ofKilobytes(1)

class RequestSizeFilterTest :
    DescribeSpec({
        // Filtered as in RoutesConfiguration. The builder's filter() would apply the filters in the
        // opposite order.
        val client =
            WebTestClient.bindToRouterFunction(
                    RouterFunctions.route()
                        .POST(ApiPaths.OUTGOING_MESSAGES) { ServerResponse.ok().build() }
                        .build()
                        .filter(requestSizeFilter(MAX_REQUEST_SIZE))
                        .filter(nhnErrorFilter())
                )
                .build()

        fun post(size: Long) =
            client.post().uri(ApiPaths.OUTGOING_MESSAGES).bodyValue(ByteArray(size.toInt())).exchange()

        describe("requestSizeFilter") {
            it("lets through a request of the max size") { post(MAX_REQUEST_SIZE.toBytes()).expectStatus().isOk }

            it("rejects a larger request with 413") {
                post(MAX_REQUEST_SIZE.toBytes() + 1)
                    .expectStatus()
                    .isEqualTo(HttpStatus.CONTENT_TOO_LARGE)
                    .expectBody()
                    .jsonPath("$.details")
                    .isEqualTo("The request exceeds the max size of ${MAX_REQUEST_SIZE.toBytes()} bytes")
            }
        }
    })
