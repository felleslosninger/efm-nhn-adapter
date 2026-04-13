package no.difi.meldingsutveksling.nhn.adapter.extensions

import java.util.*
import org.springframework.web.reactive.function.server.ServerRequest

fun ServerRequest.getMessageId(): UUID {
    return this.pathVariable("messageId").toMessageId()
}

fun ServerRequest.getReceiverHerId(): Int {
    return this.pathVariable("receiverHerId").toInt()
}
