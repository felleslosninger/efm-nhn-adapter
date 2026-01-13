package no.difi.meldingsutveksling.nhn.adapter.crypto

import java.io.File
import kotlin.RuntimeException
import okio.ByteString.Companion.decodeBase64


data class CryptoConfig(
    val alias: String,
    val base64Value: String?,
    val file: String?,
    val password: String,
    val type: String,
)

fun CryptoConfig.keyStoreAsByteArray(): ByteArray {
    if (!this.base64Value.isNullOrBlank()) {
        return base64Value.substring(base64Value.indexOf(":") + 1).decodeBase64()!!.toByteArray()
    } else if (!this.file.isNullOrBlank()) {
        return File(file).readBytes()
    } else {
        throw RuntimeException("KeyStore location configuration is missing")
    }
}