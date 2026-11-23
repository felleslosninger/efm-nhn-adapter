package no.difi.meldingsutveksling.nhn.adapter.extensions

import java.util.UUID
import kotlinx.coroutines.reactor.awaitSingle
import no.difi.meldingsutveksling.nhn.adapter.errors.ClientInputException
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.bodyToMono

private const val MESSAGE_ID = "messageId"
private const val RECEIVER_HER_ID = "receiverHerId"
private const val HER_ID = "herId"
private const val NOTIFICATIONS_TO_FETCH = "notificationsToFetch"

fun ServerRequest.getMessageId(): UUID = pathVariable(MESSAGE_ID).toClientUuid(MESSAGE_ID)

fun ServerRequest.getReceiverHerId(): Int = requiredIntQueryParam(RECEIVER_HER_ID)

fun ServerRequest.getHerId(): Int = requiredIntQueryParam(HER_ID)

/** The number of notifications the client asks for, if any. */
fun ServerRequest.getNotificationsToFetch(): Int? = intQueryParam(NOTIFICATIONS_TO_FETCH)

private fun ServerRequest.requiredIntQueryParam(name: String): Int =
    intQueryParam(name) ?: throw ClientInputException("$name cannot be null")

private fun ServerRequest.intQueryParam(name: String): Int? =
    queryParam(name).map { it.toIntOrNull() ?: throw ClientInputException("$name must be an integer") }.orElse(null)

suspend fun ServerRequest.toJWEToken(): String = bodyToMono<String>().awaitSingle()
