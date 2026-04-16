package no.difi.meldingsutveksling.nhn.adapter

import mu.KotlinLogging
import no.difi.meldingsutveksling.nhn.adapter.extensions.getId
import no.difi.meldingsutveksling.nhn.adapter.extensions.getMessageId
import no.difi.meldingsutveksling.nhn.adapter.extensions.getReceiverHerId
import no.difi.meldingsutveksling.nhn.adapter.handlers.InHandler
import no.difi.meldingsutveksling.nhn.adapter.handlers.LookupHandler
import no.difi.meldingsutveksling.nhn.adapter.handlers.OutHandler
import no.difi.meldingsutveksling.nhn.adapter.model.ContentTypes
import no.difi.meldingsutveksling.nhn.adapter.security.getClientContext
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl

val logger = KotlinLogging.logger {}

fun CoRouterFunctionDsl.outHandler(outHandler: OutHandler) {
    GET("/messages/out/{messageId}/statuses") { outHandler.getStatus(it.getMessageId(), getClientContext()) }
    POST("/messages/out", accept(MediaType.MULTIPART_FORM_DATA)) { outHandler.sendMessage(it, getClientContext()) }
    POST("/messages/out/receipt", accept(MediaType.parseMediaType(ContentTypes.APPLICATION_JOSE))) {
        outHandler.sendApplicationReceipt(it, getClientContext())
    }
}

fun CoRouterFunctionDsl.lookupHandler(lookupHandler: LookupHandler) {
    GET("/lookup/{identifier}") { lookupHandler.arLookup(it.pathVariable("identifier")) }
}

fun CoRouterFunctionDsl.inHandler(inHandler: InHandler) {
    GET("/messages/in") { inHandler.getMessagesWithMetadata(it.getReceiverHerId(), getClientContext()) }
    GET("/messages/in/{id}") { inHandler.getBusinessDocument(it.getId(), getClientContext()) }
    GET("/messages/in/{id}/receipt") { inHandler.getApplicationReceipt(it.getId(), getClientContext()) }
    POST("/messages/in/{messageId}/read") {
        inHandler.markMessageRead(it.getMessageId(), it.getReceiverHerId(), getClientContext())
    }
}
