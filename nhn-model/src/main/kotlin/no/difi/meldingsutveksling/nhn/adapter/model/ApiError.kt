package no.difi.meldingsutveksling.nhn.adapter.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiError(
    val status: Int,
    val reason: String,
    val errorCode: String,
    val message: String,
    val details: String? = null,
    /** Identifies a technical error in the adapter or a service behind it, e.g. `E7780` for MSH. */
    val technicalCode: String? = null,
    val path: String?,
    val requestId: String?,
    /** The OpenTelemetry trace id, for finding the request in the adapter's logs and traces. */
    val traceId: String? = null,
    val timestamp: String,
)
