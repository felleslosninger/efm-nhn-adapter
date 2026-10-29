package no.difi.meldingsutveksling.nhn.adapter.extensions

import java.util.UUID
import kotlinx.coroutines.reactor.awaitSingle
import no.difi.meldingsutveksling.nhn.adapter.errors.ClientInputException
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.bodyToMono

private const val MESSAGE_ID = "messageId"
private const val RECEIVER_HER_ID = "receiverHerId"

fun ServerRequest.getMessageId(): UUID = pathVariable(MESSAGE_ID).toClientUuid(MESSAGE_ID)

fun ServerRequest.getReceiverHerId(): Int =
    queryParam(RECEIVER_HER_ID)
        .map { it.toIntOrNull() ?: throw ClientInputException("$RECEIVER_HER_ID must be an integer") }
        .orElseThrow { ClientInputException("$RECEIVER_HER_ID cannot be null") }

suspend fun ServerRequest.toJWEToken(): String = bodyToMono<String>().awaitSingle()
