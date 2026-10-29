package no.difi.meldingsutveksling.nhn.adapter.audit

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import no.difi.meldingsutveksling.domain.NhnIdentifier

private const val ALGORITHM = "HmacSHA256"
private const val MIN_KEY_LENGTH = 32
private const val HMAC_PREFIX = "hmac:"
private val FASTLEGE_FOR_PREFIX = NhnIdentifier.Type.FASTLEGE_FOR.prefix() + ":"

/** A HER-id has at most 9 digits, so a fødselsnummer (11 digits) given as a HER-id is not let through. */
private val HER_ID_IDENTIFIER = Regex("""${NhnIdentifier.Type.HER_ID.prefix()}:\d{1,9}""")

/**
 * Replaces the fødselsnummer in a lookup identifier with a keyed hash (HMAC-SHA256), so that the audit log does not
 * reveal who a lookup was for. The same fødselsnummer always gives the same hash, so lookups for a known person can
 * still be found, by computing the hash with the key: `printf %s <fnr> | openssl dgst -sha256 -hmac <key>`.
 */
class IdentifierPseudonymizer(key: String) {
    private val keySpec: SecretKeySpec

    init {
        require(key.length >= MIN_KEY_LENGTH) {
            "The pseudonymization key must be at least $MIN_KEY_LENGTH characters"
        }
        keySpec = SecretKeySpec(key.toByteArray(), ALGORITHM)
    }

    /**
     * `her-id:<HER-id>` is returned as is, `fastlege-for:<x>` as `fastlege-for:hmac:<hash of x>`. The latter also
     * holds when `x` is not a valid fødselsnummer (e.g. a wrong check digit), so that a failed lookup is found with
     * the same command as a successful one. Anything else is invalid input that may still contain a fødselsnummer, so
     * it is hashed as a whole.
     */
    fun pseudonymize(identifier: String): String =
        when {
            HER_ID_IDENTIFIER.matches(identifier) -> identifier
            identifier.startsWith(FASTLEGE_FOR_PREFIX) ->
                FASTLEGE_FOR_PREFIX + hmac(identifier.removePrefix(FASTLEGE_FOR_PREFIX))
            else -> hmac(identifier)
        }

    private fun hmac(value: String): String =
        HMAC_PREFIX + Mac.getInstance(ALGORITHM).apply { init(keySpec) }.doFinal(value.toByteArray()).toHexString()
}
