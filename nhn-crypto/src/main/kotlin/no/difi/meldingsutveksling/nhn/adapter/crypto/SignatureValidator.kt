package no.difi.meldingsutveksling.nhn.adapter.crypto


import kotlinx.serialization.json.*
import org.erdtman.jcs.JsonCanonicalizer
import java.security.KeyStore
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.interfaces.ECPublicKey
import java.util.Base64
import mu.KotlinLogging


class SignatureValidator(
    val trustStore: NhnTrustStore,
    private val signatureField: String = "signature",
) {
    private val log = KotlinLogging.logger {}

    companion object {
        const val SHA256withRSA:String = "SHA256withRSA"
        const val SHA256withECDSA:String = "SHA256withECDSA"
    }
    private val json = Json { ignoreUnknownKeys = true }


    fun validate(rawJson: String) {
        try {
            val root = json.parseToJsonElement(rawJson).jsonObject
            val sigObj = root[signatureField]?.jsonObject ?: throw InvalidSignatureException("Json is not signed.")

            val sigB64 = sigObj["value"]?.jsonPrimitive?.content ?: throw InvalidSignatureException("Signature should be placed in a value field.")
            val kid = sigObj["kid"]?.jsonPrimitive?.content?: throw InvalidSignatureException("Signature is missing kid")
            val sigBytes = try {
                Base64.getDecoder().decode(sigB64)
            } catch (e: IllegalArgumentException) {
                log.error("Signature is not valid. Not able to base64 decode")
                throw InvalidSignatureException("Signature is not valid. Not able to base64 decode",e)
            }

            val canonicalBytes = canonicalize(rawJson)
            val publicKey = trustStore.getCertificateByKid(kid).publicKey
            val algo = when (publicKey) {
                is RSAPublicKey -> SHA256withRSA
                is ECPublicKey -> SHA256withECDSA
                else -> throw InvalidSignatureException(
                    "Unsupported public key type: ${publicKey::class.java.name}"
                )
            }

            val verifier = Signature.getInstance(algo)
            verifier.initVerify(publicKey)
            verifier.update(canonicalBytes)

            if (!verifier.verify(sigBytes)) {
                throw InvalidSignatureException("Signature verification failed")
            }
        }
        catch (e: InvalidSignatureException){
            log.error("Signature validation failed: ${e.message}")
            throw e
        }
        catch (e: Exception) {
            log.error("Unexpected error occured during signature validation ${e.message}")
            throw InvalidSignatureException("Signature is not valid",e)
        }

    }

    private fun canonicalize(rawJson: String): ByteArray {
        val element = json.parseToJsonElement(rawJson).jsonObject
        val withoutSig = JsonObject(element.filterKeys { it != signatureField })

        val stable = Json.encodeToString(JsonElement.serializer(), withoutSig)

        val canonical = JsonCanonicalizer(stable).encodedString
        return canonical.toByteArray(Charsets.UTF_8)
    }

}