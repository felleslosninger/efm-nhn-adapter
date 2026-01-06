package no.difi.meldingsutveksling.nhn.adapter

import io.kotest.core.spec.style.FunSpec
import java.io.File
import no.difi.meldingsutveksling.nhn.adapter.config.CryptoConfig
import no.difi.meldingsutveksling.nhn.adapter.crypto.KeystoreManager
import no.difi.meldingsutveksling.nhn.adapter.crypto.toBase64Der

class KrypteringDekrypteringTest :
    FunSpec({
        xtest("take out the public certificate") {
            val keyStoreFile: File = File("/Users/alexander/eformidling-test-auth.jks")
            val keyStoreConfig =
                CryptoConfig(
                    "testalias",
                    String(keyStoreFile.inputStream().readAllBytes()),
                    file = null,
                    password = "MeldingTeHumor2023!",
                    type = "JKS",
                )
            val keyStore = KeystoreManager(keyStoreConfig)

            val sertifikate = keyStore.getPublicCertificate().toBase64Der()
            println(sertifikate)
        }
    })
