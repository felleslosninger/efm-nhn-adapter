package no.difi.meldingsutveksling.nhn.adapter.extensions

import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.ServerResponse

fun ServerResponse.BodyBuilder.textPlain(): ServerResponse.BodyBuilder = contentType(MediaType.TEXT_PLAIN)

fun ServerResponse.BodyBuilder.multipartMixed(): ServerResponse.BodyBuilder = contentType(MediaType.MULTIPART_MIXED)
