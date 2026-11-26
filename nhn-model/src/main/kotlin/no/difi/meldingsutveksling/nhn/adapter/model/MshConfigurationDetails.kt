package no.difi.meldingsutveksling.nhn.adapter.model

import java.time.OffsetDateTime
import kotlinx.serialization.Serializable

/**
 * The MSH configuration the adapter has registered for a HER-id. MSH requires one of every HER-id it handles messages
 * for.
 *
 * @property organizationNumber the organization that owns the HER-id
 * @property clientLocked whether only the adapter may act for the HER-id in MSH; any other client gets `423 Locked`
 * @property lastUsed the last time the adapter sent or received a message for the HER-id, or null if it has not
 */
@Serializable
data class MshConfigurationDetails(
    val herId: Int,
    val organizationNumber: String,
    val clientLocked: Boolean,
    @Serializable(with = OffsetDateTimeSerializer::class) val createdAt: OffsetDateTime,
    @Serializable(with = OffsetDateTimeSerializer::class) val updatedAt: OffsetDateTime,
    @Serializable(with = OffsetDateTimeSerializer::class) val lastUsed: OffsetDateTime?,
)

/** The MSH configuration to register for a HER-id. */
@Serializable data class MshConfigurationInput(val clientLocked: Boolean = false)
