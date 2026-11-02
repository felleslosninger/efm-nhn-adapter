package no.difi.meldingsutveksling.nhn.adapter.handlers

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import no.difi.meldingsutveksling.nhn.adapter.errors.MessageTooLargeException
import org.springframework.core.io.ByteArrayResource

private const val MAX_BYTES = 1000L

class UnpackedSizeTest :
    DescribeSpec({
        fun zip(vararg entries: Pair<String, Int>) =
            ByteArrayOutputStream()
                .also { bytes ->
                    ZipOutputStream(bytes).use { zip ->
                        entries.forEach { (name, size) ->
                            zip.putNextEntry(ZipEntry(name))
                            zip.write(ByteArray(size))
                            zip.closeEntry()
                        }
                    }
                }
                .toByteArray()
                .let { ByteArrayResource(it) }

        describe("unpackedSize") {
            it("adds up the size of every entry, META-INF included") {
                unpackedSize(zip("a.xml" to 100, "META-INF/manifest.xml" to 200), MAX_BYTES) shouldBe 300
            }

            it("accepts entries adding up to the max size") {
                unpackedSize(zip("a.xml" to 400, "b.pdf" to 600), MAX_BYTES) shouldBe MAX_BYTES
            }

            it("rejects a META-INF entry that unpacks to more than the max size") {
                // The ASiC reader would hold all of it in memory
                shouldThrow<MessageTooLargeException> {
                        unpackedSize(zip("META-INF/manifest.xml" to 10_000_000), MAX_BYTES)
                    }
                    .message shouldBe "The files in the dokumentpakke exceed the max size of $MAX_BYTES bytes"
            }

            it("rejects entries adding up to more than the max size") {
                shouldThrow<MessageTooLargeException> { unpackedSize(zip("a.xml" to 600, "b.pdf" to 401), MAX_BYTES) }
            }
        }
    })
