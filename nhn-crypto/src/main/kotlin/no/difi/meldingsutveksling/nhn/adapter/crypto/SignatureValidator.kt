package no.difi.meldingsutveksling.nhn.adapter.crypto


import kotlinx.serialization.json.*
import org.erdtman.jcs.JsonCanonicalizer
import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.*
import java.security.KeyStore
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.security.interfaces.ECPublicKey

class SignatureValidator(
    private val cryptoConfig: CryptoConfig,
    private val signatureField: String = "signature",
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val publicKey: PublicKey

    init {
        val entry = loadPrivateKeyEntry(cryptoConfig)
        publicKey = entry.certificate.publicKey
    }

    fun validate(rawJson: String): Boolean {
        val root = json.parseToJsonElement(rawJson) as? JsonObject ?: return false
        val sig = root[signatureField]?.jsonPrimitive?.content ?: return false

        val canonicalBytes = canonicalize(rawJson)
        val jws = JWSObject.parse(sig)

        // Ensure payload matches canonical bytes (prevents signature swapping)
        if (!jws.payload.toBytes().contentEquals(canonicalBytes)) return false

        val verifier: JWSVerifier = when (publicKey) {
            is RSAPublicKey -> RSASSAVerifier(publicKey)
            is ECPublicKey -> ECDSAVerifier(publicKey)
            else -> return false
        }

        return jws.verify(verifier)
    }

    private fun canonicalize(rawJson: String): ByteArray {
        val element = json.parseToJsonElement(rawJson) as JsonObject
        val withoutSig = JsonObject(element.filterKeys { it != signatureField })
        val canonical = JsonCanonicalizer(withoutSig.toString()).encodedString
        return canonical.toByteArray(Charsets.UTF_8)
    }

    private fun loadPrivateKeyEntry(config: CryptoConfig): KeyStore.PrivateKeyEntry {
        val ks = KeyStore.getInstance(config.type)
        ks.load(config.keyStoreAsByteArray().inputStream(), config.password.toCharArray())
        return ks.getEntry(
            config.alias,
            KeyStore.PasswordProtection(config.password.toCharArray())
        ) as KeyStore.PrivateKeyEntry
    }
}