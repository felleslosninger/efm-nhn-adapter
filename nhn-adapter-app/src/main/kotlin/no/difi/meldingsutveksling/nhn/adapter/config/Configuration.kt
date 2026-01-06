package no.difi.meldingsutveksling.nhn.adapter.config

import java.io.File
import okio.ByteString.Companion.decodeBase64

data class NhnConfig(val url: String, val username: String, val password: String)

data class HelseId(val clientId: String, val privateKey: String, val issuer: String, val audience: String)

data class CryptoConfig(
    val alias: String,
    val base64Value: String?,
    val file: String?,
    val password: String,
    val type: String,
)

fun CryptoConfig.keyStoreAsByteArray(): ByteArray {
    if (!this.base64Value.isNullOrBlank()) {
        return base64Value.decodeBase64()!!.toByteArray()
    } else if (!this.file.isNullOrBlank()) {
        return File(file).readBytes()
    } else {
        throw RuntimeException("KeyStore location configuration is missing")
    }
}
