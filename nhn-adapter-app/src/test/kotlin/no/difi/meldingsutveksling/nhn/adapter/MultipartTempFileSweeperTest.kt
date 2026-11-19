package no.difi.meldingsutveksling.nhn.adapter

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

private val MAX_AGE = Duration.ofHours(1)

class MultipartTempFileSweeperTest :
    DescribeSpec({
        lateinit var directory: Path

        beforeEach { directory = Files.createTempDirectory("multipart-temp-file-sweeper-test") }

        afterEach { directory.toFile().deleteRecursively() }

        fun fileWrittenAt(name: String, written: Instant): Path =
            Files.write(directory.resolve(name), ByteArray(10)).also {
                Files.setLastModifiedTime(it, FileTime.from(written))
            }

        fun remaining() = Files.list(directory).use { files -> files.map { it.fileName.toString() }.toList() }

        describe("sweep") {
            val now = Instant.parse("2026-11-19T12:00:00Z")

            it("deletes the files written more than the max age ago") {
                fileWrittenAt("old.multipart", now - MAX_AGE - Duration.ofSeconds(1))
                fileWrittenAt("new.multipart", now - MAX_AGE + Duration.ofSeconds(1))

                MultipartTempFileSweeper(directory, MAX_AGE).sweep(now) shouldBe 1

                remaining() shouldContainExactlyInAnyOrder listOf("new.multipart")
            }

            it("leaves directories alone") {
                Files.createDirectory(directory.resolve("old"))
                Files.setLastModifiedTime(directory.resolve("old"), FileTime.from(now - MAX_AGE.multipliedBy(2)))

                MultipartTempFileSweeper(directory, MAX_AGE).sweep(now) shouldBe 0

                remaining() shouldContainExactlyInAnyOrder listOf("old")
            }

            it("deletes nothing when the directory is gone") {
                MultipartTempFileSweeper(directory.resolve("gone"), MAX_AGE).sweep(now) shouldBe 0
            }
        }

        it("sweeps when the application starts") {
            fileWrittenAt("old.multipart", Instant.now() - MAX_AGE.multipliedBy(2))
            val sweeper = MultipartTempFileSweeper(directory, MAX_AGE)

            try {
                sweeper.afterPropertiesSet()

                eventually(5.seconds) { remaining() shouldBe emptyList() }
            } finally {
                sweeper.destroy()
            }
        }
    })
