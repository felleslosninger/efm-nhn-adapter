package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import no.difi.move.common.dokumentpakking.domain.AsicEAttachable
import org.springframework.core.io.Resource
import org.springframework.util.MimeType

/** A file to put in the dokumentpakke. */
data class Attachment(private val filename: String, private val resource: Resource, private val mimeType: MimeType) :
    AsicEAttachable {
    override fun getFilename(): String = filename

    override fun getResource(): Resource = resource

    override fun getMimeType(): MimeType = mimeType
}
