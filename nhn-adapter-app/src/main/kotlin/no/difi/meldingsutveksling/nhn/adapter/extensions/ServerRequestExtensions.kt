package no.difi.meldingsutveksling.nhn.adapter.extensions

import java.util.UUID
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.bodyToMono

fun ServerRequest.getMessageId(): UUID = this.pathVariable("messageId").toUUID()

fun ServerRequest.getReceiverHerId(): Int =
    this.queryParam("receiverHerId")
        .map { it.toInt() }
        .orElseThrow { IllegalArgumentException("receiverHerId cannot be null") }

suspend fun ServerRequest.toJWEToken() = bodyToMono<String>().awaitSingle()
