package no.difi.meldingsutveksling.nhn.adapter.crypto

import java.security.KeyStore
import java.security.cert.X509Certificate

class NhnTrustStore(
    private val config: CryptoConfig,
) {
    private val keyStore: KeyStore = loadKeyStore(config)

    fun getCertificateByKid(kid: String): X509Certificate? {
        val cert = keyStore.getCertificate(kid) ?: return null
        return cert as? X509Certificate
    }

    fun knownKids(): Set<String> =
        keyStore.aliases().toList().toSet()

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