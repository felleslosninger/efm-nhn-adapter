package no.difi.meldingsutveksling.nhn.adapter.extensions

import no.difi.meldingsutveksling.nhn.adapter.model.ContentTypes
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.ServerResponse

fun ServerResponse.BodyBuilder.jose() = contentType(MediaType.parseMediaType(ContentTypes.APPLICATION_JOSE))

fun ServerResponse.BodyBuilder.textPlain() = contentType(MediaType.TEXT_PLAIN)

fun ServerResponse.BodyBuilder.multipartMixed() = contentType(MediaType.MULTIPART_MIXED)
