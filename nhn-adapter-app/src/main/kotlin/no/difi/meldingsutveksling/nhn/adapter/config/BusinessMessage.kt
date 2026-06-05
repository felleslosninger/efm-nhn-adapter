package no.difi.meldingsutveksling.nhn.adapter.config

import java.io.InputStream
import java.util.Arrays
import java.util.function.Supplier
import java.util.stream.Collectors
import kotlin.time.Instant
import no.difi.meldingsutveksling.domain.BusinessMessage
import no.difi.meldingsutveksling.jackson.StandardBusinessDocumentType

const val PROCESS = "urn:no:difi:profile:helse:helse:ver1.0"

enum class BusinessMessageType(
    val type: String,
    val standard: String,
    val version: String,
    val clazz: Class<out BusinessMessage?>,
) : StandardBusinessDocumentType {
    DIALOGMELDING("dialogmelding", "urn:no:difi:helse:xsd::dialogmelding", "2.0", DialogmeldingMessage::class.java),
    DIALOGMELDING_KVITTERING(
        "dialogmelding_kvittering",
        "urn:no:difi:helse:xsd::dialogmelding_kvittering",
        "2.0",
        DialogmeldingKvitteringMessage::class.java,
    );

    override fun getFieldName(): String? {
        return type
    }

    override fun getValueType(): Class<*>? {
        return clazz
    }

    companion object {
        fun fromType(type: String): StandardBusinessDocumentType {
            return Arrays.stream(entries.toTypedArray())
                .filter { p: BusinessMessageType -> p.type.equals(type, ignoreCase = true) }
                .findAny()
                .orElseThrow(
                    Supplier {
                        IllegalArgumentException(
                            String.format(
                                "Unknown BusinessMessageType = %s. Expecting one of %s",
                                type,
                                Arrays.stream(entries.toTypedArray())
                                    .map { obj: BusinessMessageType -> obj.type }
                                    .collect(Collectors.joining(",")),
                            )
                        )
                    }
                )
        }
    }
}

data class DialogmeldingMessage(
    val hoveddokument: String,
    val pasient: Pasient?,
    val metadataFiler: Map<String, AttachmentMetadata>?,
) : BusinessMessage

data class IncomingAttachment(
    val issueDate: Instant?,
    val description: String?,
    val mimeType: String,
    val data: InputStream?,
)

data class AttachmentMetadata(val issueDate: String?, val description: String?)

data class Pasient(val fnr: String, val fornavn: String, val mellomnavn: String?, val etternavn: String)

data class DialogmeldingKvitteringMessage(
    val relatedToMessageId: String,
    val status: DialogmeldingKvitteringStatus,
    val messages: List<KvitteringStatusMessage>?,
    val rawReceipt: String? = null,
) : BusinessMessage

enum class DialogmeldingKvitteringStatus {
    OK,
    REJECTED,
    OK_ERROR_IN_MESSAGE_PART
}

data class KvitteringStatusMessage(val code: String, val text: String)
