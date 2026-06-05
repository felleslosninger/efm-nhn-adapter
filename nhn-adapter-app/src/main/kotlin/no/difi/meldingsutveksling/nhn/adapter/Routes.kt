package no.difi.meldingsutveksling.nhn.adapter

import mu.KotlinLogging
import no.difi.meldingsutveksling.nhn.adapter.extensions.getMessageId
import no.difi.meldingsutveksling.nhn.adapter.extensions.getReceiverHerId
import no.difi.meldingsutveksling.nhn.adapter.handlers.InHandler
import no.difi.meldingsutveksling.nhn.adapter.handlers.LookupHandler
import no.difi.meldingsutveksling.nhn.adapter.handlers.OutHandler
import no.difi.meldingsutveksling.nhn.adapter.security.getClientContext
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl

val logger = KotlinLogging.logger {}
val APPLICATION_JOSE = MediaType.parseMediaType("application/jose")

fun CoRouterFunctionDsl.outHandler(outHandler: OutHandler) {
    GET("/messages/out/{messageId}/statuses", accept(MediaType.APPLICATION_JSON)) {
        outHandler.getStatus(it.getMessageId(), getClientContext())
    }
    POST("/messages/out", contentType(MediaType.MULTIPART_FORM_DATA).and(accept(MediaType.TEXT_PLAIN))) {
        outHandler.sendMessage(it, getClientContext())
    }
}

fun CoRouterFunctionDsl.lookupHandler(lookupHandler: LookupHandler) {
    GET("/lookup/{identifier}") { lookupHandler.arLookup(it.pathVariable("identifier"), getClientContext()) }
}

fun CoRouterFunctionDsl.inHandler(inHandler: InHandler) {
    GET("/messages/in", accept(MediaType.APPLICATION_JSON)) {
        inHandler.getMessagesWithMetadata(it.getReceiverHerId(), getClientContext())
    }
    POST("/messages/in", contentType(APPLICATION_JOSE).and(accept(MediaType.MULTIPART_MIXED))) {
        inHandler.getBusinessDocument(it, getClientContext())
    }
    POST("/messages/in/{messageId}/read") {
        inHandler.markMessageRead(it.getMessageId(), it.getReceiverHerId(), getClientContext())
    }
}
