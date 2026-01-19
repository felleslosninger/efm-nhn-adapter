package no.difi.meldingsutveksling.nhn.adapter.crypto

class EncryptionException(override val message: String, e: Throwable? = null) : Exception(message, e)
