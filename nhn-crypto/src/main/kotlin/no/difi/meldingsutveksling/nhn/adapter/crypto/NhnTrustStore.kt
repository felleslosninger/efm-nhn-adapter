package no.difi.meldingsutveksling.nhn.adapter.crypto

import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Base64

class NhnTrustStore(
    private val config: CryptoConfig,
) {
    private final val kidToCertificate: Map<String, X509Certificate> = buildMap {
         val trustStore = loadKeyStore(config)
        val aliases = trustStore.aliases()
        while (aliases.hasMoreElements()) {
            val alias = aliases.nextElement()
            val cert = trustStore.getCertificate(alias) as? X509Certificate ?: continue
            put(kidFromCertificate(cert), cert)
        }
    }

    private fun kidFromCertificate(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun getCertificateByKid(kid: String): X509Certificate = kidToCertificate[kid] ?: throw InvalidSignatureException("Can not find certificate for given kid")



    fun knownKids(): Set<String> = kidToCertificate.keys

    private fun loadKeyStore(cfg: CryptoConfig): KeyStore {
        val ks = KeyStore.getInstance(cfg.type)
        ks.load(cfg.keyStoreAsByteArray().inputStream(), cfg.password.toCharArray())

        return ks
    }

    private fun <T> java.util.Enumeration<T>.toList(): List<T> {
        val list = mutableListOf<T>()
        while (this.hasMoreElements()) list += this.nextElement()
        return list
    }
}