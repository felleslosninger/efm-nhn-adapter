package no.difi.meldingsutveksling.nhn.adapter.extensions

import java.security.cert.X509Certificate
import kotlin.io.encoding.Base64

fun X509Certificate.toBase64Der(): String = Base64.encode(encoded)
