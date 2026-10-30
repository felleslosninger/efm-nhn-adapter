package no.difi.meldingsutveksling.nhn.adapter

import io.kotest.core.spec.style.DescribeSpec
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import java.net.ConnectException
import no.difi.meldingsutveksling.nhn.adapter.errors.ClientInputException
import no.difi.meldingsutveksling.nhn.adapter.errors.DialogmeldingNotFoundException
import no.difi.meldingsutveksling.nhn.adapter.errors.HerIdNotFoundException
import no.difi.meldingsutveksling.nhn.adapter.errors.InvalidMshDataException
import no.difi.meldingsutveksling.nhn.adapter.errors.MessageTooLargeException
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptException
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.nhn.ar.rest.AdresseregisteretApiException
import no.ks.fiks.nhn.ar.rest.AdresseregisteretException
import no.ks.fiks.nhn.flr.FastlegeregisteretException
import no.ks.fiks.nhn.msh.HttpClientException
import no.ks.fiks.nhn.msh.HttpException
import no.ks.fiks.nhn.msh.HttpServerException
import org.hamcrest.Matchers.matchesPattern
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.server.HandlerFilterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

private const val LOOKUP_PATH = "/api/lookup/123"
private const val OUTGOING_PATH = ApiPaths.OUTGOING_MESSAGES
private const val TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
private const val ISO_8601_INSTANT = """\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z"""

class NhnErrorFilterTest :
    DescribeSpec({
        var error: Throwable = RuntimeException()

        val client =
            WebTestClient.bindToRouterFunction(
                    RouterFunctions.route()
                        .GET(ApiPaths.LOOKUP) { Mono.error(error) }
                        .GET(ApiPaths.OUTGOING_MESSAGES) { Mono.error(error) }
                        .filter(nhnErrorFilter())
                        .build()
                )
                .build()

        fun WebTestClient.BodyContentSpec.jsonPathIsEqualToOrAbsent(path: String, value: Any?) =
            if (value == null) jsonPath(path).doesNotExist() else jsonPath(path).isEqualTo(value)

        fun expectApiError(
            path: String,
            thrown: Throwable,
            status: HttpStatus,
            details: String?,
            errorCode: FeilmeldingForApplikasjonskvittering = FeilmeldingForApplikasjonskvittering.ANNEN_FEIL,
            technicalCode: String? = null,
        ) {
            error = thrown
            client
                .get()
                .uri(path)
                .exchange()
                .expectStatus()
                .isEqualTo(status)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(status.value())
                .jsonPath("$.reason")
                .isEqualTo(status.reasonPhrase)
                .jsonPath("$.errorCode")
                .isEqualTo(errorCode.verdi)
                .jsonPath("$.message")
                .isEqualTo(errorCode.navn)
                .jsonPathIsEqualToOrAbsent("$.details", details)
                .jsonPathIsEqualToOrAbsent("$.technicalCode", technicalCode)
                // No span is current without the OpenTelemetry Java agent
                .jsonPath("$.traceId")
                .doesNotExist()
                .jsonPath("$.path")
                .isEqualTo(path)
                .jsonPath("$.timestamp")
                .value(String::class.java, matchesPattern(ISO_8601_INSTANT))
        }

        fun expectTechnicalError(path: String, thrown: Throwable, status: HttpStatus, technicalCode: String) =
            expectApiError(
                path,
                thrown,
                status,
                "Not able to process, try later. ErrorCode: $technicalCode",
                technicalCode = technicalCode,
            )

        describe("nhnErrorFilter") {
            it("maps an unknown HER-id on lookup to 404") {
                expectApiError(LOOKUP_PATH, HerIdNotFoundException(), HttpStatus.NOT_FOUND, "HER-id not found")
            }

            it("maps an unknown HER-id elsewhere to 400") {
                expectApiError(OUTGOING_PATH, HerIdNotFoundException(), HttpStatus.BAD_REQUEST, "HER-id not found")
            }

            it("maps invalid client input to 400") {
                expectApiError(
                    OUTGOING_PATH,
                    ClientInputException("Invalid input"),
                    HttpStatus.BAD_REQUEST,
                    "Invalid input",
                )
            }

            it("does not pass on the message of the cause of invalid client input") {
                expectApiError(
                    OUTGOING_PATH,
                    ClientInputException("Invalid input", IllegalStateException("Unexpected JSON token at offset 4")),
                    HttpStatus.BAD_REQUEST,
                    "Invalid input",
                )
            }

            it("does not treat an IllegalArgumentException as invalid client input") {
                expectTechnicalError(
                    OUTGOING_PATH,
                    IllegalArgumentException("Invalid input"),
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "E7777",
                )
            }

            it("maps a too large message to 413") {
                expectApiError(
                    OUTGOING_PATH,
                    MessageTooLargeException("Message exceeds the max size"),
                    HttpStatus.CONTENT_TOO_LARGE,
                    "Message exceeds the max size",
                )
            }

            it("maps invalid data from MSH to 502 without the details") {
                expectTechnicalError(
                    OUTGOING_PATH,
                    InvalidMshDataException("Invalid data from MSH: Found no Dialogmelding in MsgHead"),
                    HttpStatus.BAD_GATEWAY,
                    "E7780",
                )
            }

            it("maps a missing hoveddokument to 400") {
                expectApiError(
                    OUTGOING_PATH,
                    DialogmeldingNotFoundException("dialogmelding.xml"),
                    HttpStatus.BAD_REQUEST,
                    "Hoveddokument 'dialogmelding.xml' was not found in the dokumentpakke",
                )
            }

            it("maps access denied to 403") {
                expectApiError(
                    LOOKUP_PATH,
                    AccessDeniedException("Not your HER-id"),
                    HttpStatus.FORBIDDEN,
                    "Access denied",
                )
            }

            it("maps application receipt errors to 400 with their error code, without the message") {
                expectApiError(
                    OUTGOING_PATH,
                    ApplicationReceiptException(
                        FeilmeldingForApplikasjonskvittering.UGYLDIG_XML,
                        "cvc-complex-type.2.4.a: Invalid content was found starting with element 'Foo'",
                    ),
                    HttpStatus.BAD_REQUEST,
                    null,
                    FeilmeldingForApplikasjonskvittering.UGYLDIG_XML,
                )
            }

            it("maps Adresseregisteret API errors to 502") {
                expectTechnicalError(
                    LOOKUP_PATH,
                    AdresseregisteretApiException(500, "Server error"),
                    HttpStatus.BAD_GATEWAY,
                    "E7778",
                )
            }

            it("maps technical Adresseregisteret errors to 502") {
                expectTechnicalError(
                    LOOKUP_PATH,
                    AdresseregisteretException("Technical error", ConnectException("Connection refused")),
                    HttpStatus.BAD_GATEWAY,
                    "E7778",
                )
            }

            it("maps Adresseregisteret errors without a cause to 502") {
                expectTechnicalError(
                    LOOKUP_PATH,
                    AdresseregisteretException("No cause", null),
                    HttpStatus.BAD_GATEWAY,
                    "E7778",
                )
            }

            it("maps Fastlegeregisteret errors to 502") {
                expectTechnicalError(
                    LOOKUP_PATH,
                    FastlegeregisteretException("Technical error", ConnectException("Connection refused")),
                    HttpStatus.BAD_GATEWAY,
                    "E7779",
                )
            }

            it("passes on the status of MSH client errors, without the MSH message") {
                expectApiError(
                    OUTGOING_PATH,
                    HttpClientException(404, body = "Message not found"),
                    HttpStatus.NOT_FOUND,
                    "The request was rejected by MSH",
                )
            }

            listOf(
                    HttpClientException(401, body = "Invalid token"),
                    HttpClientException(403, body = "Not allowed"),
                    HttpServerException(500, body = "Internal error"),
                    HttpException(499, 200, "Unknown status"),
                )
                .forEach { httpException ->
                    it("maps MSH status ${httpException.status} to 502 without the MSH message") {
                        expectTechnicalError(OUTGOING_PATH, httpException, HttpStatus.BAD_GATEWAY, "E7780")
                    }
                }

            it("maps unexpected errors to 500") {
                expectTechnicalError(
                    OUTGOING_PATH,
                    IllegalStateException("Boom"),
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "E7777",
                )
            }

            it("includes the trace id of the current span") {
                val spanContext =
                    SpanContext.create(TRACE_ID, "00f067aa0ba902b7", TraceFlags.getSampled(), TraceState.getDefault())
                // Stands in for the OpenTelemetry Java agent, which makes the span of the request
                // current
                val withCurrentSpan =
                    HandlerFilterFunction<ServerResponse, ServerResponse> { request, next ->
                        Mono.defer {
                            val scope = Span.wrap(spanContext).makeCurrent()
                            next.handle(request).doFinally { scope.close() }
                        }
                    }
                error = IllegalStateException("Boom")

                WebTestClient.bindToRouterFunction(
                        RouterFunctions.route()
                            .GET(ApiPaths.OUTGOING_MESSAGES) { Mono.error(error) }
                            .filter(withCurrentSpan)
                            .filter(nhnErrorFilter())
                            .build()
                    )
                    .build()
                    .get()
                    .uri(OUTGOING_PATH)
                    .exchange()
                    .expectBody()
                    .jsonPath("$.traceId")
                    .isEqualTo(TRACE_ID)
            }
        }
    })
