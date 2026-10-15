package no.difi.meldingsutveksling.nhn.adapter.model

object AttachmentNames {
    const val DIALOGMELDING = "dialogmelding.xml"
    const val KVITTERING = "kvittering.xml"

    private const val DEFAULT_FILE_SUFFIX = "bin"

    private val fileSuffixByMimeType =
        mapOf(
            "application/pdf" to "pdf",
            "image/jpg" to "jpg",
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "text/plain" to "txt",
        )

    fun vedlegg(index: Int, mimeType: String): String =
        "vedlegg$index.${fileSuffixByMimeType[mimeType] ?: DEFAULT_FILE_SUFFIX}"
}
