package no.difi.meldingsutveksling.nhn.adapter.errors

/** The message, or one of its attachments, exceeds the size MSH accepts. Reported as 413. */
class MessageTooLargeException(message: String) : RuntimeException(message)
