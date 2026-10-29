package no.difi.meldingsutveksling.nhn.adapter.errors

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import jakarta.xml.bind.UnmarshalException
import kotlin.coroutines.cancellation.CancellationException
import no.difi.meldingsutveksling.nhn.adapter.model.serialization.ApplicationReceiptException
import no.ks.fiks.hdir.FeilmeldingForApplikasjonskvittering
import no.ks.fiks.nhn.msh.HttpServerException
import org.springframework.security.access.AccessDeniedException

class InvalidMshDataExceptionTest :
    DescribeSpec({
        describe("fromMshData") {
            it("returns the result of the block") { fromMshData { "result" } shouldBe "result" }

            listOf(
                    IllegalArgumentException("Unexpected value"),
                    IllegalStateException("Missing HER-id"),
                    NullPointerException("No sender"),
                    NoSuchElementException("No content"),
                    ApplicationReceiptException(FeilmeldingForApplikasjonskvittering.UGYLDIG_XML, "Invalid XML"),
                    UnmarshalException("Unexpected element"),
                )
                .forEach { error ->
                    it("reports ${error::class.simpleName} as invalid data from MSH") {
                        val thrown = shouldThrow<InvalidMshDataException> { fromMshData { throw error } }

                        thrown.message shouldBe "Invalid data from MSH: ${error.message}"
                        thrown.cause shouldBeSameInstanceAs error
                    }
                }

            listOf(
                    CancellationException("Cancelled"),
                    AccessDeniedException("Not your HER-id"),
                    HerIdNotFoundException(),
                    HttpServerException(500, body = "Internal error"),
                )
                .forEach { error ->
                    it("lets ${error::class.simpleName} pass through unchanged") {
                        shouldThrow<Exception> { fromMshData { throw error } } shouldBeSameInstanceAs error
                    }
                }
        }
    })
