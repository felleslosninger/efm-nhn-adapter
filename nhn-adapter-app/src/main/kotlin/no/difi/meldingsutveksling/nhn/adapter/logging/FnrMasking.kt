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

/**
 * The value of a `fastlege-for:` identifier in a URL, up to the next `/`, `?` or `#`. The colon may still be
 * percent-encoded, if it was encoded twice.
 */
private val FASTLEGE_FOR_VALUE = Regex("""(fastlege-for(?::|%3A))[^/?#]*""", RegexOption.IGNORE_CASE)

private val PERCENT_ESCAPES = Regex("""(%[0-9A-Fa-f]{2})+""")

/** Masks anything that looks like a fødselsnummer, e.g. in error messages from Fastlegeregisteret. */
fun String.maskFnr(): String = FNR.replace(this, FNR_MASK)

/**
 * Masks the fødselsnummer in a raw URL path or query, e.g. `/nhn-adapter/api/lookup/fastlege-for:***********`. The
 * whole value of a `fastlege-for:` identifier is masked, also when it is written with a hyphen or has a digit too
 * many, which [maskFnr] alone would let through.
 *
 * The URL is percent-decoded first, so that an encoded fødselsnummer or identifier is found too, e.g.
 * `fastlege-for%3A<fnr>` or `%20<fnr>`, where [maskFnr] would take `20` for digits of the number. A URL with something
 * to mask is returned decoded, any other URL as it is.
 */
fun String.maskFnrInUrl(): String {
    val decoded = percentDecoded()
    val masked = FASTLEGE_FOR_VALUE.replace(decoded) { "${it.groupValues[1]}$FNR_MASK" }.maskFnr()
    return if (masked == decoded) this else masked
}

/** Decodes the valid percent-escapes as UTF-8, and leaves an invalid one, e.g. a `%` at the end, as it is. */
private fun String.percentDecoded(): String =
    PERCENT_ESCAPES.replace(this) { escapes ->
        val bytes = escapes.value.chunked(3).map { it.substring(1).toInt(16).toByte() }.toByteArray()
        bytes.decodeToString()
    }

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
