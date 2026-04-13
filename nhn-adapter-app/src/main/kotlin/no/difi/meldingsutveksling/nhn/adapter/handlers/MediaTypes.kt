package no.difi.meldingsutveksling.nhn.adapter.handlers

import no.difi.asic.AsicUtils
import org.springframework.http.MediaType

object MediaTypes {

    val APPLICATION_JOSE = MediaType.parseMediaType("application/jose")
    var APPLICATION_ASICE: MediaType = MediaType.parseMediaType(AsicUtils.MIMETYPE_ASICE)
}