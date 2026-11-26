package no.difi.meldingsutveksling.nhn.adapter.errors

/** The database is not migrated yet, as it was down at startup. */
class DatabaseUnavailableException(message: String) : RuntimeException(message)
