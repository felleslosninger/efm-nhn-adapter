package no.difi.meldingsutveksling.nhn.adapter.mshconfiguration

import java.net.SocketException
import java.net.SocketTimeoutException
import java.sql.SQLException
import java.sql.SQLNonTransientConnectionException
import java.sql.SQLRecoverableException
import java.sql.SQLTransientException
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.RecoverableDataAccessException
import org.springframework.dao.TransientDataAccessException

private const val MAX_CAUSES = 10

/**
 * The database is down or busy, or the connection to it failed, which is the same for every HER-id. An error in the
 * data of one HER-id, e.g. one that does not fit, is not, and nor is a failure of anything else, e.g. of MSH.
 */
fun Throwable.isDatabaseOutage(): Boolean =
    this is DataAccessResourceFailureException ||
        this is TransientDataAccessException ||
        this is RecoverableDataAccessException ||
        // Also when the driver's error was not translated as such
        (this is DataAccessException && hasConnectionFailure())

/**
 * A failed connection somewhere in the chain of causes. Only of an error from the database, as a [SocketException] of
 * an HTTP client is no database outage.
 */
internal fun Throwable.hasConnectionFailure(): Boolean =
    generateSequence(this) { it.cause }.take(MAX_CAUSES).any { it.isConnectionFailure() }

/** SQL state class 08 is a connection exception. */
private fun Throwable.isConnectionFailure(): Boolean =
    this is SQLTransientException ||
        this is SQLRecoverableException ||
        this is SQLNonTransientConnectionException ||
        (this is SQLException && sqlState?.startsWith("08") == true) ||
        this is SocketException ||
        this is SocketTimeoutException
