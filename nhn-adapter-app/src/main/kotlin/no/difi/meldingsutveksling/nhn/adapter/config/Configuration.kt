package no.difi.meldingsutveksling.nhn.adapter.config

import java.io.File
import java.time.Duration

data class CacheConfig(val maxSize: Long = 1000, val cacheTtl: Duration = Duration.ofMinutes(5))

data class NhnConfig(val url: String, val username: String, val password: String)

data class HelseId(val clientId: String, val privateKey: String, val issuer: String, val audience: String)

data class VirksertConfig(val url: String, val mode: String, val process: String)

data class SecurityConfig(val trustedIssuers: List<String>, val delegationSource: String)

data class TempFileConfig(val threshold: Int, val initialBufferSize: Int, val directory: File?)
