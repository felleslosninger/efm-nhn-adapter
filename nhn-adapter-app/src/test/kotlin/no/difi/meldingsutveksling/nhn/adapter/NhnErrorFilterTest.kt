package no.difi.meldingsutveksling.nhn.adapter

import io.kotest.core.spec.style.DescribeSpec
import no.difi.meldingsutveksling.nhn.adapter.errors.DialogmeldingNotFoundException
import no.difi.meldingsutveksling.nhn.adapter.errors.HerIdNotFoundException
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptException
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.nhn.ar.AdresseregisteretApiException
import no.ks.fiks.nhn.ar.AdresseregisteretException
import no.ks.fiks.nhn.flr.FastlegeregisteretException
import no.ks.fiks.nhn.msh.HttpException
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.server.RouterFunctions
import reactor.core.publisher.Mono

private const val LOOKUP_PATH = "/api/lookup/123"
private const val OUTGOING_PATH = ApiPaths.OUTGOING_MESSAGES

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

        fun expectApiError(
            path: String,
            thrown: Throwable,
            status: HttpStatus,
            details: String?,
            errorCode: FeilmeldingForApplikasjonskvittering = FeilmeldingForApplikasjonskvittering.ANNEN_FEIL,
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
                .jsonPath("$.details")
                .isEqualTo(details)
                .jsonPath("$.path")
                .isEqualTo(path)
        }

        describe("nhnErrorFilter") {
            it("maps an unknown HER-id on lookup to 404") {
                expectApiError(LOOKUP_PATH, HerIdNotFoundException(), HttpStatus.NOT_FOUND, "HER-id not found")
            }

            it("maps an unknown HER-id elsewhere to 400") {
                expectApiError(OUTGOING_PATH, HerIdNotFoundException(), HttpStatus.BAD_REQUEST, "HER-id not found")
            }

            it("maps invalid arguments to 400") {
                expectApiError(
                    OUTGOING_PATH,
                    IllegalArgumentException("Invalid input"),
                    HttpStatus.BAD_REQUEST,
                    "Invalid input",
                )
            }

            it("uses a generic message for invalid arguments without a message") {
                expectApiError(OUTGOING_PATH, IllegalArgumentException(), HttpStatus.BAD_REQUEST, "Client error")
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

            it("maps application receipt errors to 400 with their error code") {
                expectApiError(
                    OUTGOING_PATH,
                    ApplicationReceiptException(FeilmeldingForApplikasjonskvittering.UGYLDIG_XML, "Invalid XML"),
                    HttpStatus.BAD_REQUEST,
                    "Invalid XML",
                    FeilmeldingForApplikasjonskvittering.UGYLDIG_XML,
                )
            }

            it("maps Adresseregisteret API errors to 502 with the registry error code") {
                expectApiError(
                    LOOKUP_PATH,
                    AdresseregisteretApiException("SomeError", "Fault", "Fault"),
                    HttpStatus.BAD_GATEWAY,
                    "Not able to process, try later. ErrorCode: SomeError",
                )
            }

            it("maps technical Adresseregisteret errors to 502") {
                expectApiError(
                    LOOKUP_PATH,
                    AdresseregisteretException("Technical error", RuntimeException("Timeout")),
                    HttpStatus.BAD_GATEWAY,
                    "Not able to process, try later. ErrorCode: E7778",
                )
            }

            it("maps Fastlegeregisteret errors to 502") {
                expectApiError(
                    LOOKUP_PATH,
                    FastlegeregisteretException("Technical error", RuntimeException("Timeout")),
                    HttpStatus.BAD_GATEWAY,
                    "Not able to process, try later. ErrorCode: E7779",
                )
            }

            it("passes on the status of MSH HTTP errors") {
                val httpException = HttpException(404, 200, "Message not found")
                expectApiError(OUTGOING_PATH, httpException, HttpStatus.NOT_FOUND, httpException.message)
            }

            it("maps unexpected errors to 500") {
                expectApiError(
                    OUTGOING_PATH,
                    IllegalStateException("Boom"),
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Not able to process, try later. ErrorCode: E7777",
                )
            }
        }
    })
