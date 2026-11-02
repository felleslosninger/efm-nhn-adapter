package no.difi.meldingsutveksling.nhn.adapter.handlers

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import no.difi.commons.asic.jaxb.asic.AsicFile
import no.difi.commons.asic.jaxb.asic.AsicManifest
import no.difi.meldingsutveksling.nhn.adapter.errors.ClientInputException
import no.difi.meldingsutveksling.nhn.adapter.errors.MessageTooLargeException

private const val MAX_FILES = 2
private const val MAX_BYTES = 10L

class LimitedAsicListenerTest :
    DescribeSpec({
        fun listener() = LimitedAsicListener(MAX_FILES, MAX_BYTES)

        fun LimitedAsicListener.file(filename: String, size: Int) =
            onFile(filename, ByteArrayInputStream(ByteArray(size)))

        describe("LimitedAsicListener") {
            it("reads the files with their MIME types from the manifest") {
                val listener = listener()
                listener.onFile("dialogmelding.xml", ByteArrayInputStream("<xml/>".toByteArray()))
                listener.file("vedlegg.pdf", 4)
                listener.onManifest(
                    AsicManifest().apply {
                        file.add(
                            AsicFile().apply {
                                name = "dialogmelding.xml"
                                mimetype = "application/xml"
                            }
                        )
                        file.add(
                            AsicFile().apply {
                                name = "vedlegg.pdf"
                                mimetype = "application/pdf"
                            }
                        )
                    }
                )

                val documents = listener.documents()

                documents shouldHaveSize 2
                documents[0].filename shouldBe "dialogmelding.xml"
                documents[0].mimeType.toString() shouldBe "application/xml"
                documents[0].resource.contentAsByteArray shouldBe "<xml/>".toByteArray()
                documents[1].filename shouldBe "vedlegg.pdf"
                documents[1].mimeType.toString() shouldBe "application/pdf"
            }

            it("accepts files adding up to the max size") {
                val listener = listener()
                listener.file("a", 4)
                listener.file("b", 6)

                listener.documents() shouldHaveSize 2
            }

            it("stops reading when the files add up to more than the max size") {
                val listener = listener()
                listener.file("a", 4)

                shouldThrow<MessageTooLargeException> { listener.file("b", 7) }
            }

            it("stops reading a single file larger than the max size") {
                // The rest of the stream would be the unpacked content of a zip bomb
                val stream = ByteArrayInputStream(ByteArray(1000))

                shouldThrow<MessageTooLargeException> { listener().onFile("a", stream) }

                stream.available() shouldBe 1000 - (MAX_BYTES.toInt() + 1)
            }

            it("rejects more files than the max") {
                val listener = listener()
                listener.file("a", 1)
                listener.file("b", 1)

                shouldThrow<ClientInputException> { listener.file("c", 1) }.message shouldBe
                    "The dokumentpakke has more than $MAX_FILES files"
            }
        }
    })
