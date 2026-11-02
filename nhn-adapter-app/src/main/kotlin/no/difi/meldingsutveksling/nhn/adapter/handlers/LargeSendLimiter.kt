package no.difi.meldingsutveksling.nhn.adapter.handlers

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

    /**
     * Runs [send] at once for a request smaller than the threshold, and otherwise when a permit is free. The size of a
     * chunked request is unknown (`-1`) and counts as large. Throws [AdapterBusyException] if no permit is free in
     * time.
     */
    suspend fun <T> limit(requestSize: Long, send: suspend () -> T): T {
        if (requestSize in 0 ..< limits.largeSendThreshold.toBytes()) {
            return send()
        }
        acquirePermit()
        try {
            return send()
        } finally {
            permits.release()
        }
    }

    private suspend fun acquirePermit() {
        // The timeout may expire right after the permit was acquired. The permit is then kept and
        // must be released.
        var acquired = false
        try {
            withTimeout(limits.largeSendMaxWait.toMillis()) {
                permits.acquire()
                acquired = true
            }
        } catch (_: TimeoutCancellationException) {
            if (!acquired) {
                throw AdapterBusyException("Too many large messages are being sent, try later")
            }
        }
    }
}
