package no.difi.meldingsutveksling.nhn.adapter.config

import java.io.File

data class NhnConfig(val url: String, val username: String, val password: String)

data class HelseId(val clientId: String, val privateKey: String, val issuer: String, val audience: String)

data class SecurityConfig(val trustedIssuers: List<String>, val delegationSource: String)

data class TempFileConfig(val threshold: Int, val initialBufferSize: Int, val directory: File?)

data class CertificateConfig(val mode: String)

data class KeystoreConfig(
    val alias: String,
    val password: String,
    val type: String,
    val path: String,
    val lockProvider: Boolean = false,
)
