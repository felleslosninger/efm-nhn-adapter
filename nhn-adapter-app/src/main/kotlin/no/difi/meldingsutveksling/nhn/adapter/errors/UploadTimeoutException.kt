package no.difi.meldingsutveksling.nhn.adapter.errors

/** The client did not finish sending the request in time. Reported as 408. */
class UploadTimeoutException(message: String) : RuntimeException(message)
