package no.difi.meldingsutveksling.nhn.adapter.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiError(
    val status: Int,
    val reason: String,
    val errorCode: String,
    val message: String,
    val details: String? = null,
    val path: String?,
    val requestId: String?,
    val timestamp: String,
)
