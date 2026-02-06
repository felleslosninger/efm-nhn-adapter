package no.difi.meldingsutveksling.nhn.adapter

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.util.encodeBase64
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.io.encoding.Base64
import no.difi.meldingsutveksling.nhn.adapter.crypto.CryptoConfig
import no.difi.meldingsutveksling.nhn.adapter.crypto.DecryptionException
import no.difi.meldingsutveksling.nhn.adapter.crypto.Dekrypter
import no.difi.meldingsutveksling.nhn.adapter.crypto.Dekryptering
import no.difi.meldingsutveksling.nhn.adapter.crypto.Kryptering
import no.difi.meldingsutveksling.nhn.adapter.crypto.NhnKeystore
import no.difi.meldingsutveksling.nhn.adapter.crypto.toBase64Der

class KrypteringDekrypteringTest :
    FunSpec({
        context("Encryption Decryption") {
            val keyStore = NhnKeystore(testCryptoConfig)
            val stringToEncript = "This is a string to encript"

            test("Should encypt data") {
                val sertifikate = keyStore.getPublicCertificate().toBase64Der()
                val factory: CertificateFactory? = CertificateFactory.getInstance("X.509")
                val cert: X509Certificate =
                    factory!!.generateCertificate(Base64.decode(sertifikate.toByteArray()).inputStream())
                        as X509Certificate
                val kryptering = Kryptering()

                kryptering.krypter(stringToEncript.toByteArray(), cert).also { println(it.encodeBase64()) }
            }

            test("Should decrypt data") {
                val encryptedBase64String =
                    "MIAGCSqGSIb3DQEHA6CAMIACAQAxggF9MIIBeQIBADBhMEkxCzAJBgNVBAYTAkFVMRMwEQYDVQQIDApTb21lLVN0YXRlMQ8wDQYDVQQKDAZEaWdkaXIxFDASBgNVBAsMC0Vmb3JtaWRsaW5nAhQvUQRXbaEloeb+V+WxAOCA7B2m6TANBgkqhkiG9w0BAQEFAASCAQCmXfRIOGoT2yaJ2BfAO88b1rhJ5kT6OPt9BcE9wPgjU0uqKoy8mFNqNn2mn0B/PQDOaS06ZktJmN26VBnXfGIlWdfuMzp5p+INr4UvZcozfeQFMoePtBt4dc2oQbKHX17woP+kaBUfnpoedYxrfAWAuhaDjiVY3ppCWZ78V1u0S2P0XhuWg4lU/ygV3nSi50HXMFasFnfev0Dd31BNCIjaxHiGmOykKXVh7oR5D4jsw/xg8JLf2f3SPmu+HhjzzNqyst8iW3zbq4hnbalgRgfQjdhlf8RwYiwlYlfbdW/AbAkawGjq5wQPrEU2Rw2BGLl4bZsosuTyJg9axYSuELPgMIAGCSqGSIb3DQEHATAeBglghkgBZQMEAS4wEQQMyQ5svplHJCEAp6tOAgEQoIAEK8PWrGQVBm5kcD9kbOW0PpmMd5ltSIrcF5+SdRz1T4kljmdRMCEm11Q5eoQAAAAAAAAAAAAA"
                val dekrypt = Dekryptering(keyStore)

                val dekryptedString = dekrypt.dekrypter(encryptedBase64String.toByteArray()).decodeToString()

                dekryptedString shouldBe stringToEncript
            }

            test("Decryption should fail when private key is missing") {
                val encryptedBase64WithWrongKey =
                    "MIAGCSqGSIb3DQEHA6CAMIACAQAxggIZMIICFQIBADB9MG4xCzAJBgNVBAYTAk5PMRgwFgYDVQRhDA9OVFJOTy05ODMxNjMzMjcxEzARBgNVBAoMCkJ1eXBhc3MgQVMxMDAuBgNVBAMMJ0J1eXBhc3MgQ2xhc3MgMyBUZXN0NCBDQSBHMiBTVCBCdXNpbmVzcwILAZl75qx7xrySb8UwDQYJKoZIhvcNAQEBBQAEggGAWMwBIlnDHTFx6RUREbyGqw4A/Bsah7nzATFxfR/+JJOxneHZhWbG1U/Iag0AFe6bk+3ypotyGrhRdJFv6yz1Cjd4r2YGIGZQGBuI+53edL7eYRI9RwB2rT46bL4WMgSbqLnyVUqiWoqJowXeWJluldbt1lq1vhe+JABRzGfKvuc4bRTpUEl/hx9Wlaa+8K1Y2VRoRe8hEUFmVdb5FAn5l8RNDiK07Kj6QvgRD2XWqwj8n/qD3ylmwu8PhoMOSLedwPRKTcTeVNUECxI8Phzg0/OskMgiIxDnU9Vf7Ujtp5sdvxMzPP69emx/mJCYaLrqC3N+C08RsOuSrJKUUYyUCejrb6g6lrGVqmbMXPo+XjoeTpL8WKNtJK9TL9t0qbhuYXaB61lTuWo2zoZq4+jf+flMfyofrX4buoHAhd0ZsEoB87sA9XWBouDM/huIYiWHb/p+S6x0gRZri/QTbgiao/DOjGNwlLW1IjyTMdXIT1xwjVVLc9XrVCK6riMLKQOxMIAGCSqGSIb3DQEHATAeBglghkgBZQMEAS4wEQQMI+6eInnso1PN0RAVAgEQoIAEKzq/BvLUpDpwa6X4K7xZv2XChUJMnG5qG5ua6oVVU/42XvyrwGtqGTX9+VEAAAAAAAAAAAAA"
                val dekrypt = Dekryptering(keyStore)

                shouldThrow<DecryptionException> {
                        dekrypt.dekrypter(encryptedBase64WithWrongKey.toByteArray()).decodeToString()
                    }
                    .message shouldBe "Fant ingen gyldige privatsertifikat for dekryptering"
            }

            test("Should read public key from keystore") {
                val sertifikate = keyStore.getPublicCertificate().toBase64Der()
                val factory: CertificateFactory? = CertificateFactory.getInstance("X.509")
                val cert: X509Certificate =
                    factory!!.generateCertificate(Base64.decode(sertifikate.toByteArray()).inputStream())
                        as X509Certificate

                cert shouldNotBe null
            }
        }
    })

val testCryptoConfig =
    CryptoConfig(
        "unit-test",
        Thread.currentThread()
            .contextClassLoader
            .getResourceAsStream("unit-test-keystore.p12")
            .readAllBytes()
            .encodeBase64(),
        password = "test",
        file = null,
        type = "PKCS12",
    )

val dummyDekryptor =
    object : Dekrypter {
        override fun dekrypter(byteArray: ByteArray): ByteArray = byteArray
    }
