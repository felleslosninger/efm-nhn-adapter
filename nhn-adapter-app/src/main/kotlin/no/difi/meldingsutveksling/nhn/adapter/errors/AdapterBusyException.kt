package no.difi.meldingsutveksling.nhn.adapter.errors

/** The adapter is busy with other requests and cannot take this one now. Reported as 503, so the client retries. */
class AdapterBusyException(message: String) : RuntimeException(message)
