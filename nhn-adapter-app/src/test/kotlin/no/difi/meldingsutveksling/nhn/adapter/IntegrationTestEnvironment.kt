package no.difi.meldingsutveksling.nhn.adapter

import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext

/**
 * A freshly generated self-signed keystore used both by the adapter and by the tests acting as integrasjonspunktet, so
 * that the JOSE/CMS round trips are real without depending on a certificate that expires.
 */
object IntegrationTestEnvironment {
    const val CONSUMER_ORG_NUMBER = "123456789"

    private const val KEYSTORE_ALIAS = "nhn-adapter-it"
    private const val KEYSTORE_PASSWORD = "changeit"

    val keystorePath: Path by lazy { createSelfSignedKeystore() }

    private fun createSelfSignedKeystore(): Path {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=nhn-adapter-it, O=Digdir, C=NO")
        val now = Instant.now()
        val certificate =
            JcaX509CertificateConverter()
                .getCertificate(
                    JcaX509v3CertificateBuilder(
                            name,
                            BigInteger.valueOf(now.toEpochMilli()),
                            Date.from(now.minus(1, ChronoUnit.DAYS)),
                            Date.from(now.plus(365, ChronoUnit.DAYS)),
                            name,
                            keyPair.public,
                        )
                        .build(JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private))
                )

        val keyStore =
            KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setKeyEntry(KEYSTORE_ALIAS, keyPair.private, KEYSTORE_PASSWORD.toCharArray(), arrayOf(certificate))
            }

        return Files.createTempFile("nhn-adapter-it-", ".p12").also { path ->
            path.toFile().deleteOnExit()
            Files.newOutputStream(path).use { keyStore.store(it, KEYSTORE_PASSWORD.toCharArray()) }
        }
    }

    class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(applicationContext: ConfigurableApplicationContext) {
            TestPropertyValues.of(
                    "keystore.path=${keystorePath.toUri()}",
                    "keystore.alias=$KEYSTORE_ALIAS",
                    "keystore.password=$KEYSTORE_PASSWORD",
                    "keystore.type=PKCS12",
                )
                .applyTo(applicationContext)
        }
    }
}
