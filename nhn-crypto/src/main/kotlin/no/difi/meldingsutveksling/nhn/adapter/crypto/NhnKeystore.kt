package no.difi.meldingsutveksling.nhn.adapter.crypto

import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Enumeration
import kotlin.io.encoding.Base64
import mu.KotlinLogging
import org.bouncycastle.jce.provider.BouncyCastleProvider


class NhnKeystore(private val config: CryptoConfig) {
    val logger:org.slf4j.Logger = KotlinLogging.logger {}
    init {
        Security.addProvider(BouncyCastleProvider())
    }

    private val keyStore =
        KeyStore.getInstance(config.type).apply {
            try {
                this.load(ByteArrayInputStream(config.keyStoreAsByteArray()), config.password.toCharArray())
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

    /*
    fun getKidByOrgnummer2(orgnummer:String) : String {
        keyStore.aliases().iterator().asSequence().find {
            val certificate = keyStore.getCertificate(it) as X509Certificate
            val holder =
                X509CertificateHolder(certificate.encoded)

            val subject: X500Name? = holder.subject
            val rdns: Array<RDN>? = subject!!.getRDNs(BCStyle.ORGANIZATION_IDENTIFIER)
            rdns?.find {
                IETFUtils.valueToString (
                    it.first.value
                ) == orgnummer
            } != null
        }.map {
            keyStore.getCertificate(it)
        }
    }

     */

    fun getKidByOrgnummer(orgnummer:String) =
        getPublicCertificate().let { kidFromCertificate(it)}


    private fun kidFromCertificate(cert: X509Certificate): String {
        val der = cert.encoded
        val digest = MessageDigest.getInstance("SHA-256").digest(der)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun getPrivateKey(serialnumber: BigInteger): PrivateKey? =
        keyStore
            .aliases()
            .iterator()
            .asSequence()
            .firstOrNull { alias -> (keyStore.getCertificate(alias) as X509Certificate).serialNumber == serialnumber }
            ?.let { alias -> keyStore.getKey(alias, config.password.toCharArray()) as PrivateKey? }
}

fun X509Certificate.toBase64Der(): String = Base64.Default.encode(this.encoded)
