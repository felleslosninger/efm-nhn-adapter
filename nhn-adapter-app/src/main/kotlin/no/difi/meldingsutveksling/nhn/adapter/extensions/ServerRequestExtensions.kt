package no.difi.meldingsutveksling.nhn.adapter.extensions

import java.util.UUID
import org.springframework.web.reactive.function.server.ServerRequest

fun ServerRequest.getId(): UUID = this.pathVariable("id").toUUID()

fun ServerRequest.getMessageId(): UUID = this.pathVariable("messageId").toUUID()

fun ServerRequest.getReceiverHerId(): Int =
    this.queryParam("receiverHerId")
        .map { it.toInt() }
        .orElseThrow { IllegalArgumentException("receiverHerId cannot be null") }
