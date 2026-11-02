package no.difi.meldingsutveksling.nhn.adapter.handlers

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import no.difi.meldingsutveksling.nhn.adapter.config.LimitsConfig
import no.difi.meldingsutveksling.nhn.adapter.errors.AdapterBusyException
import org.springframework.util.unit.DataSize

private const val THRESHOLD = 1000L
private const val LARGE = THRESHOLD
private const val SMALL = THRESHOLD - 1
private const val UNKNOWN_SIZE = -1L

class LargeSendLimiterTest :
    DescribeSpec({
        fun limiter(maxWait: Duration = Duration.ofSeconds(10)) =
            LargeSendLimiter(
                LimitsConfig(
                    maxRequestSize = DataSize.ofMegabytes(50),
                    maxDokumentpakkeFiles = 100,
                    largeSendThreshold = DataSize.ofBytes(THRESHOLD),
                    maxConcurrentLargeSends = 1,
                    largeSendMaxWait = maxWait,
                )
            )

        /** Starts a large send that holds the only permit until [release] completes. */
        suspend fun kotlinx.coroutines.CoroutineScope.holdPermit(
            limiter: LargeSendLimiter,
            release: CompletableDeferred<Unit>,
        ) {
            val started = CompletableDeferred<Unit>()
            launch {
                limiter.limit(LARGE) {
                    started.complete(Unit)
                    release.await()
                }
            }
            started.await()
        }

        describe("LargeSendLimiter") {
            it("never holds back a small send") {
                val limiter = limiter()
                val release = CompletableDeferred<Unit>()
                coroutineScope {
                    holdPermit(limiter, release)

                    limiter.limit(SMALL) { "sent" } shouldBe "sent"
                    release.complete(Unit)
                }
            }

            it("lets a large send wait for the permit") {
                val limiter = limiter()
                val release = CompletableDeferred<Unit>()
                coroutineScope {
                    holdPermit(limiter, release)

                    val waiting = async { limiter.limit(LARGE) { "sent" } }
                    yield()
                    waiting.isCompleted shouldBe false

                    release.complete(Unit)
                    waiting.await() shouldBe "sent"
                }
            }

            it("counts a send of unknown size as large") {
                val limiter = limiter(maxWait = Duration.ofMillis(100))
                val release = CompletableDeferred<Unit>()
                coroutineScope {
                    holdPermit(limiter, release)

                    shouldThrow<AdapterBusyException> { limiter.limit(UNKNOWN_SIZE) { "sent" } }
                    release.complete(Unit)
                }
            }

            it("gives up with AdapterBusyException when no permit is free in time") {
                val limiter = limiter(maxWait = Duration.ofMillis(100))
                val release = CompletableDeferred<Unit>()
                coroutineScope {
                    holdPermit(limiter, release)

                    shouldThrow<AdapterBusyException> { limiter.limit(LARGE) { "sent" } }
                    release.complete(Unit)
                }
            }

            it("releases the permit when the send fails") {
                val limiter = limiter(maxWait = Duration.ofMillis(100))

                shouldThrow<IllegalStateException> { limiter.limit(LARGE) { error("MSH failed") } }

                limiter.limit(LARGE) { "sent" } shouldBe "sent"
            }
        }
    })
