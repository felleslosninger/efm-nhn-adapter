package no.difi.meldingsutveksling.nhn.adapter.extensions

import java.util.UUID
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.bodyToMono

private const val MESSAGE_ID = "messageId"
private const val RECEIVER_HER_ID = "receiverHerId"

fun ServerRequest.getMessageId(): UUID =
    pathVariable(MESSAGE_ID).let {
        try {
            UUID.fromString(it)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("$MESSAGE_ID must be an UUID!", e)
        }
    }

fun ServerRequest.getReceiverHerId(): Int =
    queryParam(RECEIVER_HER_ID)
        .map { it.toIntOrNull() ?: throw IllegalArgumentException("$RECEIVER_HER_ID must be an integer") }
        .orElseThrow { IllegalArgumentException("$RECEIVER_HER_ID cannot be null") }

suspend fun ServerRequest.toJWEToken(): String = bodyToMono<String>().awaitSingle()
