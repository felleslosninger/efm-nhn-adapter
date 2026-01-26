package no.difi.meldingsutveksling.nhn.adapter.crypto

class InvalidSignatureException(override val message:String, override val cause: Throwable?=null) : Exception(message,cause)