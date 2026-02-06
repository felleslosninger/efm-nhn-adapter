package no.difi.meldingsutveksling.nhn.adapter

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import java.util.Date
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.difi.meldingsutveksling.nhn.adapter.crypto.CryptoConfig
import no.difi.meldingsutveksling.nhn.adapter.crypto.InvalidSignatureException
import no.difi.meldingsutveksling.nhn.adapter.crypto.NhnTrustStore
import no.difi.meldingsutveksling.nhn.adapter.crypto.SignatureValidator
import no.difi.meldingsutveksling.nhn.adapter.crypto.Signer
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

class SignatureValideringTest :
    ShouldSpec({
        should("Should sign a json") {
            val cryptoConfig =
                CryptoConfig(
                    "unit-test",
                    null,
                    SignatureValideringTest::class.java.classLoader.getResource("unit-test-keystore.p12").file,
                    "test",
                    "PKCS12",
                )
            val signer = Signer(cryptoConfig)

            val signedJson = signer.sign("""{
                "testKey":"testValue"
            
            }""")
            println(signedJson)
            val json = Json { ignoreUnknownKeys = true }.decodeFromString<JsonObject>(signedJson)

            json["testKey"]!!.jsonPrimitive.content shouldBe "testValue"

            val signature = json["signature"].shouldNotBeNull()
            signature.jsonObject["alg"]?.jsonPrimitive?.content shouldBe "RS256"
            val signatureObj = signature as? JsonObject ?: error("signature is not an object: $signature")
            signatureObj["value"].shouldNotBeNull()
        }

        should("should validate a signature") {
            val cryptoConfig =
                CryptoConfig(
                    "unit-test",
                    null,
                    SignatureValideringTest::class.java.classLoader.getResource("unit-test-keystore.p12").file,
                    "test",
                    "PKCS12",
                )
            val signer = Signer(cryptoConfig)

            val signedJson = signer.sign("""{
                "testKey":"testValue"
            
            }""")

            val signatureValidator = SignatureValidator(cryptoConfig.let { NhnTrustStore(it) })
            signatureValidator.validate(signedJson)
        }

        should("unsigned signature should not validate") {
            val trustStore =
                CryptoConfig(
                        "unit-test",
                        null,
                        SignatureValideringTest::class.java.classLoader.getResource("unit-test-keystore.p12").file,
                        "test",
                        "PKCS12",
                    )
                    .let { NhnTrustStore(it) }

            val unsigned = """{
                "testKey":"testValue"
            
            }"""

            val signatureValidator = SignatureValidator(trustStore)
            var ex = shouldThrow<InvalidSignatureException> { signatureValidator.validate(unsigned) }

            ex.message shouldBe "Json is not signed."

            val signatureValueIsMissing =
                """{
                "testKey":"testValue",
                "signature": {
                    "algorithm": "RS256"
                }
            
            }"""

            ex = shouldThrow<InvalidSignatureException> { signatureValidator.validate(signatureValueIsMissing) }
            ex.message shouldBe "Signature should be placed in a value field."
        }

        should("validation should fail when signature is validated against wrong sertifikate") {
            val tempP12 =
                createPkcs12forTest(
                    alias = "temp",
                    password = "temp-pass",
                    subjectDn = "CN=temp-signing-key, OU=Test, O=Local, C=NO",
                )

            val signingConfig = CryptoConfig("temp", null, tempP12.absolutePath, "temp-pass", "PKCS12")

            val signedJson = Signer(signingConfig).sign("""{ "testKey":"testValue" }""")

            val unitTestP12Path =
                SignatureValideringTest::class.java.classLoader.getResource("unit-test-keystore.p12")!!.file

            val validatingConfig = CryptoConfig("unit-test", null, unitTestP12Path, "test", "PKCS12")

            val validator = SignatureValidator(NhnTrustStore(validatingConfig))

            val ex = shouldThrow<InvalidSignatureException> { validator.validate(signedJson) }
            ex.message shouldBe "Can not find certificate for given kid"
        }
    })

private fun createPkcs12forTest(alias: String, password: String, subjectDn: String, validityDays: Int = 365): File {
    if (Security.getProvider("BC") == null) {
        Security.addProvider(BouncyCastleProvider())
    }

    val keyPairGen = KeyPairGenerator.getInstance("RSA")
    keyPairGen.initialize(2048, SecureRandom())
    val keyPair = keyPairGen.generateKeyPair()

    val now = Date()
    val notAfter = Date(now.time + validityDays.toLong() * 24L * 60L * 60L * 1000L)

    val subject = X500Name(subjectDn)
    val serial = BigInteger(160, SecureRandom())

    val certBuilder = JcaX509v3CertificateBuilder(subject, serial, now, notAfter, subject, keyPair.public)

    val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(keyPair.private)

    val cert = JcaX509CertificateConverter().setProvider("BC").getCertificate(certBuilder.build(signer))

    val ks = KeyStore.getInstance("PKCS12")
    ks.load(null, null)
    ks.setKeyEntry(alias, keyPair.private, password.toCharArray(), arrayOf(cert))

    val file = kotlin.io.path.createTempFile("signing-", ".p12").toFile().apply { deleteOnExit() }
    FileOutputStream(file).use { out -> ks.store(out, password.toCharArray()) }
    return file
}
