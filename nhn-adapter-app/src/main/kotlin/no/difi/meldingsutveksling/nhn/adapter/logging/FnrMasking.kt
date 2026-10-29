package no.difi.meldingsutveksling.nhn.adapter.logging

import net.logstash.logback.encoder.LogstashEncoder
import net.logstash.logback.mask.MaskingJsonGeneratorDecorator
import net.logstash.logback.mask.RegexValueMasker

/**
 * Eleven digits standing alone, the shape of a fødselsnummer or D-nummer. The check digits are not verified, so that a
 * mistyped fødselsnummer is masked too.
 */
private val FNR = Regex("""(?<!\d)\d{11}(?!\d)""")

private const val FNR_MASK = "***********"

/** Masks anything that looks like a fødselsnummer, e.g. in error messages from Fastlegeregisteret. */
fun String.maskFnr(): String = FNR.replace(this, FNR_MASK)

/**
 * [LogstashEncoder] that masks anything that looks like a fødselsnummer in every value it writes, the message and
 * stack trace included. The application log uses it (see logback-spring.xml), since exceptions from third-party
 * clients may contain the fødselsnummer that was looked up.
 */
class FnrMaskingLogstashEncoder : LogstashEncoder() {
    private val fnrMasking =
        MaskingJsonGeneratorDecorator().apply { addValueMasker(RegexValueMasker(FNR.toPattern(), FNR_MASK)) }

    init {
        addDecorator(fnrMasking)
    }

    // Logback only starts the components it creates from the configuration itself
    override fun start() {
        fnrMasking.start()
        super.start()
    }

    override fun stop() {
        super.stop()
        fnrMasking.stop()
    }
}
