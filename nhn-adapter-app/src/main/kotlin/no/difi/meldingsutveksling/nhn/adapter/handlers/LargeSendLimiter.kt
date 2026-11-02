package no.difi.meldingsutveksling.nhn.adapter.handlers

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import no.difi.meldingsutveksling.nhn.adapter.config.LimitsConfig
import no.difi.meldingsutveksling.nhn.adapter.errors.AdapterBusyException

/**
 * Limits how many large messages are sent at the same time.
 *
 * While a message is unpacked, built and encoded for MSH, it is held in memory several times over. A few large ones at
 * once could otherwise use up the adapter's memory. Small messages are never held back.
 */
class LargeSendLimiter(private val limits: LimitsConfig) {
    private val permits = Semaphore(limits.maxConcurrentLargeSends)

    /** Messages of this size or larger are large. */
    val threshold: Long = limits.largeSendThreshold.toBytes()

    /**
     * Runs [send] at once for a request smaller than the [threshold], and otherwise when a permit is free. The size of
     * a chunked request is unknown (`-1`) and counts as large. [send] is told whether it holds a permit.
     */
    suspend fun <T> limit(requestSize: Long, send: suspend (permitHeld: Boolean) -> T): T =
        if (requestSize in 0 ..< threshold) send(false) else withPermit { send(true) }

    /** Runs [send] when a permit is free. Throws [AdapterBusyException] if no permit is free in time. */
    suspend fun <T> withPermit(send: suspend () -> T): T {
        acquirePermit()
        try {
            return send()
        } finally {
            permits.release()
        }
    }

    private suspend fun acquirePermit() {
        // The timeout may expire, or the caller be cancelled, right after the permit was acquired
        var acquired = false
        try {
            withTimeout(limits.largeSendMaxWait.toMillis()) {
                permits.acquire()
                acquired = true
            }
        } catch (_: TimeoutCancellationException) {
            // A permit acquired in time is kept, and released after the send
            if (!acquired) {
                throw AdapterBusyException("Too many large messages are being sent, try later")
            }
        } catch (e: CancellationException) {
            if (acquired) {
                permits.release()
            }
            throw e
        }
    }
}
