package no.difi.meldingsutveksling.nhn.adapter.mshconfiguration

import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient

/** A HER-id's MSH configuration as stored by the adapter. */
data class StoredMshConfiguration(
    val herId: Int,
    val organizationNumber: String,
    /** Whether it is registered with a multi-tenant HelseID token, as for a client acting through a supplier. */
    val multiTenant: Boolean,
    val clientLocked: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastUsed: Instant?,
)

/**
 * The MSH configurations in the database. JDBC blocks, so the calls run on a dispatcher of their own, no wider than
 * the connection pool: a database that is slow or down holds up these calls only, not the other work on
 * [Dispatchers.IO].
 *
 * The times are stored in UTC as `DATETIME`, which the database does not convert, whatever the time zone of the JVM.
 */
class MshConfigurationRepository(
    private val jdbcClient: JdbcClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(DEFAULT_POOL_SIZE),
) {
    suspend fun findAll(): List<StoredMshConfiguration> =
        withContext(dispatcher) {
            jdbcClient.sql("SELECT * FROM msh_configuration ORDER BY her_id").query(::toConfiguration).list()
        }

    suspend fun find(herId: Int): StoredMshConfiguration? =
        withContext(dispatcher) {
            jdbcClient
                .sql("SELECT * FROM msh_configuration WHERE her_id = :herId")
                .param("herId", herId)
                .query(::toConfiguration)
                .optional()
                .orElse(null)
        }

    /** Stores the configuration, replacing one of the same HER-id. The time it was created and last used are kept. */
    suspend fun save(
        herId: Int,
        organizationNumber: String,
        multiTenant: Boolean,
        clientLocked: Boolean,
        now: Instant,
    ) {
        withContext(dispatcher) {
            jdbcClient
                .sql(
                    """
                    INSERT INTO msh_configuration
                        (her_id, organization_number, multi_tenant, client_locked, created_at, updated_at)
                    VALUES (:herId, :organizationNumber, :multiTenant, :clientLocked, :now, :now)
                    ON DUPLICATE KEY UPDATE
                        organization_number = VALUES(organization_number),
                        multi_tenant = VALUES(multi_tenant),
                        client_locked = VALUES(client_locked),
                        updated_at = VALUES(updated_at)
                    """
                )
                .param("herId", herId)
                .param("organizationNumber", organizationNumber)
                .param("multiTenant", multiTenant)
                .param("clientLocked", clientLocked)
                .param("now", now.toUtc())
                .update()
        }
    }

    /**
     * Stores the configuration, without a lock, unless the HER-id has one, which may have been stored since it was
     * looked for.
     *
     * @return whether it was stored
     */
    suspend fun saveIfAbsent(herId: Int, organizationNumber: String, multiTenant: Boolean, now: Instant): Boolean =
        withContext(dispatcher) {
            try {
                jdbcClient
                    .sql(
                        """
                        INSERT INTO msh_configuration
                            (her_id, organization_number, multi_tenant, client_locked, created_at, updated_at)
                        VALUES (:herId, :organizationNumber, :multiTenant, FALSE, :now, :now)
                        """
                    )
                    .param("herId", herId)
                    .param("organizationNumber", organizationNumber)
                    .param("multiTenant", multiTenant)
                    .param("now", now.toUtc())
                    .update()
                true
            } catch (_: DuplicateKeyException) {
                false
            }
        }

    /** Changes the HelseID tenant of the configuration, and nothing else. */
    suspend fun updateTenant(herId: Int, organizationNumber: String, multiTenant: Boolean, now: Instant) {
        withContext(dispatcher) {
            jdbcClient
                .sql(
                    """
                    UPDATE msh_configuration
                    SET organization_number = :organizationNumber, multi_tenant = :multiTenant, updated_at = :now
                    WHERE her_id = :herId
                    """
                )
                .param("organizationNumber", organizationNumber)
                .param("multiTenant", multiTenant)
                .param("now", now.toUtc())
                .param("herId", herId)
                .update()
        }
    }

    /** @return whether there was a configuration of the HER-id to update */
    suspend fun updateLastUsed(herId: Int, now: Instant): Boolean =
        withContext(dispatcher) {
            jdbcClient
                .sql("UPDATE msh_configuration SET last_used = :now WHERE her_id = :herId")
                .param("now", now.toUtc())
                .param("herId", herId)
                .update() > 0
        }

    /**
     * Puts back [previous], unless the configuration was stored again after [storedAt].
     *
     * @return whether it was put back
     */
    suspend fun restore(previous: StoredMshConfiguration, storedAt: Instant): Boolean =
        withContext(dispatcher) {
            jdbcClient
                .sql(
                    """
                    UPDATE msh_configuration
                    SET organization_number = :organizationNumber, multi_tenant = :multiTenant,
                        client_locked = :clientLocked, updated_at = :updatedAt
                    WHERE her_id = :herId AND updated_at = :storedAt
                    """
                )
                .param("organizationNumber", previous.organizationNumber)
                .param("multiTenant", previous.multiTenant)
                .param("clientLocked", previous.clientLocked)
                .param("updatedAt", previous.updatedAt.toUtc())
                .param("herId", previous.herId)
                .param("storedAt", storedAt.toUtc())
                .update() > 0
        }

    /**
     * Deletes the configuration of the HER-id, unless it was stored again after [storedAt].
     *
     * @return whether it was deleted
     */
    suspend fun deleteIfUpdatedAt(herId: Int, storedAt: Instant): Boolean =
        withContext(dispatcher) {
            jdbcClient
                .sql("DELETE FROM msh_configuration WHERE her_id = :herId AND updated_at = :storedAt")
                .param("herId", herId)
                .param("storedAt", storedAt.toUtc())
                .update() > 0
        }

    /** @return whether there was a configuration of the HER-id to delete */
    suspend fun delete(herId: Int): Boolean =
        withContext(dispatcher) {
            jdbcClient.sql("DELETE FROM msh_configuration WHERE her_id = :herId").param("herId", herId).update() > 0
        }

    private fun toConfiguration(rs: ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int) =
        StoredMshConfiguration(
            herId = rs.getInt("her_id"),
            organizationNumber = rs.getString("organization_number"),
            multiTenant = rs.getBoolean("multi_tenant"),
            clientLocked = rs.getBoolean("client_locked"),
            createdAt = rs.getInstant("created_at")!!,
            updatedAt = rs.getInstant("updated_at")!!,
            lastUsed = rs.getInstant("last_used"),
        )

    private companion object {
        /** Hikari's default maximum pool size. */
        const val DEFAULT_POOL_SIZE = 10
    }
}

private fun Instant.toUtc(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneOffset.UTC)

private fun ResultSet.getInstant(column: String): Instant? =
    getObject(column, LocalDateTime::class.java)?.toInstant(ZoneOffset.UTC)
