package no.difi.meldingsutveksling.nhn.adapter.config

import java.io.File
import java.time.Duration
import org.springframework.util.unit.DataSize

data class NhnConfig(val url: String, val username: String, val password: String, val timeout: TimeoutConfig)

data class HelseId(
    val clientId: String,
    val privateKey: String,
    val issuer: String,
    val audience: String,
    val timeout: TimeoutConfig,
)

/** [read] is the longest wait for data on an open connection, not for the whole call. */
data class TimeoutConfig(val connect: Duration, val read: Duration)

/** As [TimeoutConfig], and [request] limits a whole call to MSH, sending a large message included. */
data class MshTimeoutConfig(val connect: Duration, val read: Duration, val request: Duration)

/**
 * Limits on what a client may send, so that a few requests cannot use up the adapter's memory.
 *
 * [maxRequestSize] is checked against `Content-Length` before the body is read. [maxDokumentpakkeFiles] limits the
 * number of files in a decrypted dokumentpakke. A send with a request or unpacked dokumentpakke above
 * [largeSendThreshold] is held in memory several times over while the message to MSH is built, so at most
 * [maxConcurrentLargeSends] of them run at a time, and one waits at most [largeSendMaxWait] for its turn. A client has
 * [maxUploadTime] to send the whole request.
 */
data class LimitsConfig(
    val maxRequestSize: DataSize,
    val maxDokumentpakkeFiles: Int,
    val largeSendThreshold: DataSize,
    val maxConcurrentLargeSends: Int,
    val largeSendMaxWait: Duration,
    val maxUploadTime: Duration,
)

data class SecurityConfig(val trustedIssuers: List<String>, val delegationSource: String)

data class TempFileConfig(val threshold: Int, val initialBufferSize: Int, val directory: File?)

data class CertificateConfig(val mode: String)

/** [pseudonymizationKey] is the HMAC key used to pseudonymize fødselsnummer in the audit log. */
data class AuditConfig(val pseudonymizationKey: String)

data class KeystoreConfig(
    val alias: String,
    val password: String,
    val type: String,
    val path: String,
    val lockProvider: Boolean = false,
)
