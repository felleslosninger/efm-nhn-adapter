package no.difi.meldingsutveksling.nhn.adapter

import no.difi.meldingsutveksling.nhn.adapter.extensions.getHerId
import no.difi.meldingsutveksling.nhn.adapter.extensions.getMessageId
import no.difi.meldingsutveksling.nhn.adapter.extensions.getNotificationsToFetch
import no.difi.meldingsutveksling.nhn.adapter.extensions.getReceiverHerId
import no.difi.meldingsutveksling.nhn.adapter.handlers.InHandler
import no.difi.meldingsutveksling.nhn.adapter.handlers.LookupHandler
import no.difi.meldingsutveksling.nhn.adapter.handlers.NotificationHandler
import no.difi.meldingsutveksling.nhn.adapter.handlers.OutHandler
import no.difi.meldingsutveksling.nhn.adapter.model.ContentTypes
import no.difi.meldingsutveksling.nhn.adapter.security.getClientContext
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl

object ApiPaths {
    const val LOOKUP = "/api/lookup/{identifier}"
    const val OUTGOING_MESSAGES = "/api/messages/out"
    const val OUTGOING_MESSAGE_STATUSES = "/api/messages/out/{messageId}/statuses"
    const val INCOMING_MESSAGES = "/api/messages/in"
    const val INCOMING_MESSAGE = "/api/messages/in/{messageId}"
    const val INCOMING_MESSAGE_DOWNLOADED = "/api/messages/in/{messageId}/downloaded"
    const val UNREAD_NOTIFICATIONS = "/api/notifications/unread"
    const val DELETE_NOTIFICATIONS = "/api/notifications/delete"
}

private val APPLICATION_JOSE = MediaType.parseMediaType(ContentTypes.APPLICATION_JOSE)

fun CoRouterFunctionDsl.outHandler(outHandler: OutHandler) {
    GET(ApiPaths.OUTGOING_MESSAGE_STATUSES, accept(MediaType.APPLICATION_JSON)) {
        outHandler.getStatus(it.getMessageId(), getClientContext())
    }
    POST(ApiPaths.OUTGOING_MESSAGES, contentType(MediaType.MULTIPART_FORM_DATA).and(accept(MediaType.TEXT_PLAIN))) {
        outHandler.sendMessage(it, getClientContext())
    }
}

fun CoRouterFunctionDsl.lookupHandler(lookupHandler: LookupHandler) {
    GET(ApiPaths.LOOKUP) { lookupHandler.arLookup(it.pathVariable("identifier"), getClientContext()) }
}

fun CoRouterFunctionDsl.inHandler(inHandler: InHandler) {
    GET(ApiPaths.INCOMING_MESSAGE, accept(MediaType.APPLICATION_JSON)) {
        inHandler.getMessage(it.getMessageId(), it.getReceiverHerId(), getClientContext())
    }
    POST(ApiPaths.INCOMING_MESSAGES, contentType(APPLICATION_JOSE).and(accept(MediaType.MULTIPART_MIXED))) {
        inHandler.getBusinessDocument(it, getClientContext())
    }
    POST(ApiPaths.INCOMING_MESSAGE_DOWNLOADED) {
        inHandler.markMessageDownloaded(it.getMessageId(), it.getReceiverHerId(), getClientContext())
    }
}

fun CoRouterFunctionDsl.notificationHandler(notificationHandler: NotificationHandler) {
    GET(ApiPaths.UNREAD_NOTIFICATIONS, accept(MediaType.APPLICATION_JSON)) {
        notificationHandler.getUnreadNotifications(it.getHerId(), it.getNotificationsToFetch(), getClientContext())
    }
    POST(ApiPaths.DELETE_NOTIFICATIONS, contentType(MediaType.APPLICATION_JSON)) {
        notificationHandler.deleteNotifications(it, getClientContext())
    }
}
