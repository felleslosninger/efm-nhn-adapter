package no.difi.meldingsutveksling.nhn.adapter.crypto


import kotlinx.serialization.json.*
import org.erdtman.jcs.JsonCanonicalizer
import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.*
import java.security.KeyStore
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.interfaces.ECPublicKey
import java.util.Base64

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
        val root = json.parseToJsonElement(rawJson).jsonObject
        val sigObj = root[signatureField]?.jsonObject ?: return false

        val sigB64 = sigObj["value"]?.jsonPrimitive?.content ?: return false
        val sigBytes = try {
            Base64.getDecoder().decode(sigB64)
        } catch (e: IllegalArgumentException) {
            return false
        }

        val canonicalBytes = canonicalize(rawJson)

        val algo = when (publicKey) {
            is RSAPublicKey -> "SHA256withRSA"
            is ECPublicKey -> "SHA256withECDSA"
            else -> return false
        }

        val verifier = Signature.getInstance(algo)
        verifier.initVerify(publicKey)
        verifier.update(canonicalBytes)

        return verifier.verify(sigBytes)

    }

    private fun canonicalize(rawJson: String): ByteArray {
        val element = json.parseToJsonElement(rawJson).jsonObject
        val withoutSig = JsonObject(element.filterKeys { it != signatureField })

        val stable = Json.encodeToString(JsonElement.serializer(), withoutSig)

        val canonical = JsonCanonicalizer(stable).encodedString
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