package no.difi.meldingsutveksling.nhn.adapter.errors

/** The message, or one of its attachments, exceeds the size MSH accepts. Reported as 413. */
open class MessageTooLargeException(message: String) : RuntimeException(message)

/** The request exceeds `limits.max-request-size`. Reported as 413. */
class RequestTooLargeException(maxBytes: Long) :
    MessageTooLargeException("The request exceeds the max size of $maxBytes bytes")
