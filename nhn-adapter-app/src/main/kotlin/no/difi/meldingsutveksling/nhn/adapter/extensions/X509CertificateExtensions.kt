package no.difi.meldingsutveksling.nhn.adapter.extensions

import java.io.StringWriter
import java.security.cert.X509Certificate
import kotlin.io.encoding.Base64
import org.bouncycastle.openssl.jcajce.JcaPEMWriter

fun X509Certificate.toPEM(): String {
    StringWriter().use { stringWriter ->
        JcaPEMWriter(stringWriter).use { jcaPEMWriter ->
            jcaPEMWriter.writeObject(this)
            jcaPEMWriter.flush()
            return stringWriter.toString()
        }
    }
}

fun X509Certificate.toBase64Der(): String = Base64.encode(this.encoded)
