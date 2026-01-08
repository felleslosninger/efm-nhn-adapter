package no.difi.meldingsutveksling.nhn.adapter.crypto

class EncryptionException(override val message: String, e: Exception? = null) : Exception(message, e)
