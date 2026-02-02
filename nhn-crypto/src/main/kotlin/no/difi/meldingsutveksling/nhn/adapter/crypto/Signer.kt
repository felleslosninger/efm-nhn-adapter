package no.difi.meldingsutveksling.nhn.adapter.crypto

import com.nimbusds.jose.JOSEObjectType
import kotlinx.serialization.json.Json
import org.erdtman.jcs.JsonCanonicalizer
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.RSASSASigner
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.ECPrivateKey
import java.util.Base64
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class Signer(
    private val cryptoConfig: CryptoConfig,
    private val signatureField: String = "signature",
) {

    private val json = Json { explicitNulls = false }
    private val privateKey: PrivateKey
    private val alg: JWSAlgorithm
    private val kid: String

    init {
        val entry = loadPrivateKeyEntry(cryptoConfig)
        privateKey = entry.privateKey
        alg = when (privateKey) {
            is RSAPrivateKey -> JWSAlgorithm.RS256
            is ECPrivateKey -> JWSAlgorithm.ES256
            else -> error("Support RSA or ECP private keys")
        }
        kid = kidFromCertificate(entry.certificate as X509Certificate)
    }

    fun sign(rawJson: String): String {
        val canonicalBytes = canonicalize(rawJson)
        val sigBytes = signCanonicalBytes(canonicalBytes)
        val sigB64 = Base64.getEncoder().encodeToString(sigBytes)

        val root = json.parseToJsonElement(rawJson) as JsonObject

        val sigObj = JsonObject(
            mapOf(
                "alg" to JsonPrimitive(alg.name),
                "kid" to JsonPrimitive(kid),
                "value" to JsonPrimitive(sigB64)
            )
        )

        val signed = JsonObject(root + (signatureField to sigObj))
        return signed.toString()
    }

    private fun canonicalize(rawJson: String): ByteArray {
        val element = json.parseToJsonElement(rawJson) as JsonObject
        val withoutSig = JsonObject(element.filterKeys { it != signatureField })
        val stableString = Json.encodeToString(JsonElement.serializer(), withoutSig)

        val canonical = JsonCanonicalizer(stableString).encodedString
        return canonical.toByteArray(Charsets.UTF_8)
    }

    private fun signCanonicalBytes(bytes: ByteArray): ByteArray {
        val signature = when (privateKey) {
            is RSAPrivateKey -> {
                val sig = java.security.Signature.getInstance("SHA256withRSA")
                sig.initSign(privateKey)
                sig.update(bytes)
                sig.sign()
            }
            is ECPrivateKey -> {
                val sig = java.security.Signature.getInstance("SHA256withECDSA")
                sig.initSign(privateKey)
                sig.update(bytes)
                sig.sign()
            }
            else -> error("Support RSA or EC private keys")
        }
        return signature
    }

    private fun kidFromCertificate(cert: X509Certificate): String {
        val der = cert.encoded
        val digest = MessageDigest.getInstance("SHA-256").digest(der)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
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