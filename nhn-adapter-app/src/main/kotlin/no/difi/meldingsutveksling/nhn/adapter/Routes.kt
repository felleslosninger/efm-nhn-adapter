package no.difi.meldingsutveksling.nhn.adapter

import mu.KotlinLogging
import no.difi.meldingsutveksling.nhn.adapter.extensions.getMessageId
import no.difi.meldingsutveksling.nhn.adapter.extensions.getReceiverHerId
import no.difi.meldingsutveksling.nhn.adapter.handlers.InHandler
import no.difi.meldingsutveksling.nhn.adapter.handlers.LookupHandler
import no.difi.meldingsutveksling.nhn.adapter.handlers.MediaTypes
import no.difi.meldingsutveksling.nhn.adapter.handlers.OutHandler
import no.difi.meldingsutveksling.nhn.adapter.security.getClientContext
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl

val logger = KotlinLogging.logger {}

fun CoRouterFunctionDsl.outHandler(outHandler: OutHandler) {
    GET("/messages/out/{messageId}/statuses") { outHandler.getStatus(it.getMessageId(), getClientContext()) }
    POST("/messages/out", accept(MediaType.MULTIPART_FORM_DATA)) { outHandler.sendMessage(it, getClientContext()) }
    POST("/messages/out/{messageId}/receipt", accept(MediaTypes.APPLICATION_JOSE)) { outHandler.sendApplicationReceipt(it, it.getMessageId(), getClientContext()) }
}

fun CoRouterFunctionDsl.lookupHandler(lookupHandler: LookupHandler) {
    GET("/lookup/{identifier}") { lookupHandler.arLookup(it.pathVariable("identifier")) }
}

fun CoRouterFunctionDsl.inHandler(inHandler: InHandler) {
    GET("/messages/in/{receiverHerId}") { inHandler.getMessagesWithMetadata(it.getReceiverHerId(), getClientContext()) }
    GET("/messages/in/{messageId}/businessDocument") { inHandler.getBusinessDocument(it.getMessageId(), getClientContext()) }
    GET("/messages/in/{messageId}/receipt") { inHandler.getApplicationReceiptsForMessage(it.getMessageId(), getClientContext()) }
    POST("/messages/in/{receiverHerId}/{messageId}/read") {
        inHandler.markMessageRead(it.getMessageId(), it.getReceiverHerId(), getClientContext())
    }
}
