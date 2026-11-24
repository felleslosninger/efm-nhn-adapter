package no.difi.meldingsutveksling.nhn.adapter.mshconfiguration

import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.jdbc.core.simple.JdbcClient

/** A HER-id's MSH configuration as stored by the adapter. */
data class StoredMshConfiguration(
    val herId: Int,
    val organizationNumber: String,
    val clientLocked: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastUsed: Instant?,
)

/** The MSH configurations in the database. JDBC blocks, so every call runs on [Dispatchers.IO]. */
class MshConfigurationRepository(private val jdbcClient: JdbcClient) {

    suspend fun findAll(): List<StoredMshConfiguration> =
        withContext(Dispatchers.IO) {
            jdbcClient.sql("SELECT * FROM msh_configuration ORDER BY her_id").query(::toConfiguration).list()
        }

    suspend fun find(herId: Int): StoredMshConfiguration? =
        withContext(Dispatchers.IO) {
            jdbcClient
                .sql("SELECT * FROM msh_configuration WHERE her_id = :herId")
                .param("herId", herId)
                .query(::toConfiguration)
                .optional()
                .orElse(null)
        }

    /** Stores the configuration, replacing one of the same HER-id. The time it was created and last used are kept. */
    suspend fun save(herId: Int, organizationNumber: String, clientLocked: Boolean, now: Instant) {
        withContext(Dispatchers.IO) {
            jdbcClient
                .sql(
                    """
                    INSERT INTO msh_configuration
                        (her_id, organization_number, client_locked, created_at, updated_at, last_used)
                    VALUES (:herId, :organizationNumber, :clientLocked, :now, :now, NULL)
                    ON DUPLICATE KEY UPDATE
                        organization_number = VALUES(organization_number),
                        client_locked = VALUES(client_locked),
                        updated_at = VALUES(updated_at)
                    """
                )
                .param("herId", herId)
                .param("organizationNumber", organizationNumber)
                .param("clientLocked", clientLocked)
                .param("now", Timestamp.from(now))
                .update()
        }
    }

    /**
     * Stores the configuration unless the HER-id has one, which may have been stored since it was looked for.
     *
     * @return whether it was stored
     */
    suspend fun saveIfAbsent(herId: Int, organizationNumber: String, now: Instant, used: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            jdbcClient
                .sql(
                    """
                    INSERT IGNORE INTO msh_configuration
                        (her_id, organization_number, client_locked, created_at, updated_at, last_used)
                    VALUES (:herId, :organizationNumber, FALSE, :now, :now, :lastUsed)
                    """
                )
                .param("herId", herId)
                .param("organizationNumber", organizationNumber)
                .param("now", Timestamp.from(now))
                .param("lastUsed", if (used) Timestamp.from(now) else null)
                .update() > 0
        }

    /** @return whether there was a configuration of the HER-id to update */
    suspend fun updateLastUsed(herId: Int, now: Instant): Boolean =
        withContext(Dispatchers.IO) {
            jdbcClient
                .sql("UPDATE msh_configuration SET last_used = :now WHERE her_id = :herId")
                .param("now", Timestamp.from(now))
                .param("herId", herId)
                .update() > 0
        }

    /** @return whether there was a configuration of the HER-id to delete */
    suspend fun delete(herId: Int): Boolean =
        withContext(Dispatchers.IO) {
            jdbcClient.sql("DELETE FROM msh_configuration WHERE her_id = :herId").param("herId", herId).update() > 0
        }

    private fun toConfiguration(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int) =
        StoredMshConfiguration(
            herId = rs.getInt("her_id"),
            organizationNumber = rs.getString("organization_number"),
            clientLocked = rs.getBoolean("client_locked"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            lastUsed = rs.getTimestamp("last_used")?.toInstant(),
        )
}
