package no.difi.meldingsutveksling.nhn.adapter.crypto

import io.ktor.util.toCharArray
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Enumeration
import kotlin.io.encoding.Base64
import no.difi.meldingsutveksling.nhn.adapter.config.CryptoConfig
import no.difi.meldingsutveksling.nhn.adapter.config.keyStoreAsByteArray
import no.difi.meldingsutveksling.nhn.adapter.logger
import org.bouncycastle.jce.provider.BouncyCastleProvider

class KeystoreManager(private val config: CryptoConfig) {
    init {
        Security.addProvider(BouncyCastleProvider())
    }

    private val keyStore =
        KeyStore.getInstance(config.type).apply {
            try {
                load(ByteArrayInputStream(config.keyStoreAsByteArray()), config.password.toCharArray())
            } catch (e: Exception) {
                logger.error("Failed to load keystore", e)
            }
        }

    fun getPublicCertificate(): X509Certificate =
        keyStore.aliases().iterator<String>().next().let { keyStore.getCertificate(it) as X509Certificate }

    fun getCertificate(alias: String): X509Certificate = keyStore.getCertificate(alias) as X509Certificate

    private fun hasCertEntry(alias: String): Boolean =
        keyStore.isCertificateEntry(alias) && keyStore.getCertificate(alias).publicKey is X509Certificate

    private fun hasPrivateKeyEntry(alias: String): Boolean =
        keyStore.isKeyEntry(alias) && keyStore.getKey(alias, config.password.toCharArray()) is PrivateKey

    fun aliases(): Enumeration<String> = keyStore.aliases()

    fun getPrivateKey(serialnumber: BigInteger): PrivateKey? =
        keyStore
            .aliases()
            .iterator()
            .asSequence()
            .firstOrNull { alias -> (keyStore.getCertificate(alias) as X509Certificate).serialNumber == serialnumber }
            ?.let { alias -> keyStore.getKey(alias, config.password.toCharArray()) as PrivateKey? }
}

fun X509Certificate.toBase64Der(): String = Base64.Default.encode(this.encoded)
