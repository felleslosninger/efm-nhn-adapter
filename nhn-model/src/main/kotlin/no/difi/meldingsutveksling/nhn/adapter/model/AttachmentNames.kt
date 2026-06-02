package no.difi.meldingsutveksling.nhn.adapter.model

object AttachmentNames {
    const val DIALOGMELDING = "dialogmelding.xml"
    const val KVITTERING = "kvittering.xml"

    fun vedlegg(index: Int, mimeType: String): String {
        return "vedlegg$index." + toFileSuffix(mimeType)
    }

    private fun toFileSuffix(mimeType: String): String =
        when (mimeType) {
            "application/pdf" -> "pdf"
            "image/jpg" -> "jpg"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "text/plain" -> "txt"
            else -> "bin"
        }
}