package no.difi.meldingsutveksling.nhn.adapter

import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import mu.KotlinLogging
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean

private val logger = KotlinLogging.logger {}

/**
 * Deletes the files in [directory] that were last written more than [maxAge] ago. [directory] is where Spring stores
 * the multipart parts above `spring.webflux.multipart.max-in-memory-size`.
 *
 * Spring deletes the files of a request when the request is done. But Spring 7.0.10 sometimes leaves the file of a
 * part behind when the body fails while the part is written, as when [RequestBodyLimit] rejects a chunked request or
 * the client aborts the upload. A client could otherwise fill the disk by sending too large requests again and again,
 * as each such file stays until the pod is replaced. [maxAge] is to be far above the time a request keeps its parts.
 *
 * Sweeps when the application starts, and then every quarter of [maxAge], so that a file left behind is deleted at
 * most 1.25 × [maxAge] after it was written.
 */
class MultipartTempFileSweeper(val directory: Path, private val maxAge: Duration) : InitializingBean, DisposableBean {
    private val executor =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("multipart-temp-file-sweeper").daemon().factory()
        )

    override fun afterPropertiesSet() {
        val interval = maxAge.dividedBy(4).toMillis()
        executor.scheduleWithFixedDelay(::sweepAndLogErrors, 0, interval, TimeUnit.MILLISECONDS)
    }

    override fun destroy() {
        executor.shutdownNow()
    }

    /** Deletes the files last written before [now] minus [maxAge], and returns how many were deleted. */
    fun sweep(now: Instant = Instant.now()): Int {
        val cutoff = now - maxAge
        val files =
            try {
                Files.list(directory).use { it.toList() }
            } catch (_: NoSuchFileException) {
                // Spring creates the directory at startup. Nothing to sweep if it is gone.
                return 0
            }
        var deleted = 0
        var deletedBytes = 0L
        for (file in files) {
            try {
                val attributes = Files.readAttributes(file, BasicFileAttributes::class.java)
                if (
                    attributes.isRegularFile &&
                        attributes.lastModifiedTime().toInstant() < cutoff &&
                        Files.deleteIfExists(file)
                ) {
                    deleted++
                    deletedBytes += attributes.size()
                }
            } catch (_: NoSuchFileException) {
                // Deleted by Spring in the meantime
            } catch (e: IOException) {
                logger.warn(e) { "Could not delete the multipart temp file $file" }
            }
        }
        if (deleted > 0) {
            logger.warn {
                "Deleted $deleted multipart temp files ($deletedBytes bytes) left behind for more than $maxAge in $directory"
            }
        }
        return deleted
    }

    /** An exception would stop the executor from running the sweep again. */
    private fun sweepAndLogErrors() {
        try {
            sweep()
        } catch (e: Exception) {
            logger.error(e) { "Could not sweep the multipart temp files in $directory" }
        }
    }
}
