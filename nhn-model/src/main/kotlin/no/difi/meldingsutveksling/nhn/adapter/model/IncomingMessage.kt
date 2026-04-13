package no.difi.meldingsutveksling.nhn.adapter.model

import java.time.OffsetDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import no.ks.fiks.hdir.MeldingensFunksjon
import no.ks.fiks.hdir.OrganizationIdType
import no.ks.fiks.hdir.PersonIdType
import no.ks.fiks.nhn.msh.ChildOrganization
import no.ks.fiks.nhn.msh.CommunicationParty
import no.ks.fiks.nhn.msh.ConversationRef
import no.ks.fiks.nhn.msh.Dialogmelding
import no.ks.fiks.nhn.msh.Foresporsel
import no.ks.fiks.nhn.msh.Id
import no.ks.fiks.nhn.msh.IncomingBusinessDocument
import no.ks.fiks.nhn.msh.MessageWithMetadata
import no.ks.fiks.nhn.msh.Notat
import no.ks.fiks.nhn.msh.Organization
import no.ks.fiks.nhn.msh.OrganizationCommunicationParty
import no.ks.fiks.nhn.msh.OrganizationId
import no.ks.fiks.nhn.msh.Patient
import no.ks.fiks.nhn.msh.PersonCommunicationParty
import no.ks.fiks.nhn.msh.PersonId
import no.ks.fiks.nhn.msh.Receiver
import no.ks.fiks.nhn.msh.Sender

@Serializable
data class IncomingMessage(
    val id: String,
    val contentType: String,
    val receiverHerId: Int,
    val senderHerId: Int,
    val businessDocumentId: String,
    @Serializable(with = OffsetDateTimeSerializer::class) val businessDocumentDate: OffsetDateTime?,
)

@OptIn(ExperimentalUuidApi::class)
fun MessageWithMetadata.toInMessage(): IncomingMessage =
    IncomingMessage(
        this.id.toString(),
        this.contentType,
        this.receiverHerId,
        this.senderHerId,
        this.businessDocumentId,
        this.businessDocumentDate,
    )

@Serializable
data class SerializableIncomingBusinessDocument(
    val id: String,
    val date: LocalDateTime?,
    val type: SerializableMeldingensFunksjon,
    val sender: SerializableSender,
    val receiver: SerializableReceiver,
    val payload: SerializableDialogmeldingMessage?,
//    val vedlegg: SerializableIncomingVedlegg?,
    val conversationRef: SerializableConversationRef?
)

@Serializable
data class SerializableMeldingensFunksjon(val verdi: String, val navn: String, val kodeverk: String)

fun MeldingensFunksjon.toSerializable() =
    SerializableMeldingensFunksjon(verdi = this.verdi, navn = this.navn, kodeverk = this.kodeverk)

@Serializable
data class SerializableOrganization(
    val name: String,
    @Serializable(with = IdListSerializer::class) val ids: List<Id>,
    val childOrganization: SerializableChildOrganization?,
)

@Serializable
data class SerializableChildOrganization(
    val name: String,
    @Serializable(with = IdListSerializer::class) val ids: List<Id>,
)

@Serializable
sealed class SerializableCommunicationParty {
    @Serializable(with = IdListSerializer::class)
    abstract val ids: List<Id>

    @Serializable
    @SerialName("organization")
    data class Organization(
        @Serializable(with = IdListSerializer::class) override val ids: List<Id>,
        val name: String,
    ) : SerializableCommunicationParty()

    @Serializable
    @SerialName("person")
    data class Person(
        @Serializable(with = IdListSerializer::class) override val ids: List<Id>,
        val firstName: String,
        val middleName: String? = null,
        val lastName: String,
    ) : SerializableCommunicationParty()
}

fun CommunicationParty.toSerializable(): SerializableCommunicationParty =
    when (this) {
        is OrganizationCommunicationParty ->
            SerializableCommunicationParty.Organization(ids = this.ids, name = this.name)

        is PersonCommunicationParty ->
            SerializableCommunicationParty.Person(
                ids = this.ids,
                firstName = this.firstName,
                middleName = this.middleName,
                lastName = this.lastName,
            )
    }

@Serializable
data class SerializableSender(
    val parent: SerializableCommunicationParty,
    val child: SerializableCommunicationParty,
)

fun Sender.toSerializable(): SerializableSender =
    SerializableSender(this.parent.toSerializable(), this.child.toSerializable())

@Serializable
data class SerializableReceiver(
    val parent: SerializableCommunicationParty,
    val child: SerializableCommunicationParty,
    val patient: SerializablePatient,
)

@Serializable
sealed interface SerializableReceiverDetails {
    @Serializable(with = IdListSerializer::class)
    val ids: List<Id>
}

@Serializable
@SerialName("organization")
data class SerializableOrganizationReceiverDetails(
    @Serializable(with = IdListSerializer::class) override val ids: List<Id>,
    val name: String,
) : SerializableReceiverDetails

@Serializable
data class SerializableConversationRef(
    val refToParent: String?,
    val refToConversation: String?,
)

@Serializable
@SerialName("person")
data class SerializablePersonReceiverDetails(
    @Serializable(with = IdListSerializer::class) override val ids: List<Id>,
    val firstName: String,
    val middleName: String?,
    val lastName: String,
) : SerializableReceiverDetails

@Serializable
data class SerializableIncomingVedlegg
@OptIn(ExperimentalTime::class)
constructor(@Contextual val date: Instant?, val description: String?, val mimeType: String?, val data: ByteArray?)

fun ChildOrganization.toSerializable() = SerializableChildOrganization(name = name, ids = ids)

fun ConversationRef.toSerializable() = SerializableConversationRef(this.refToParent, this.refToConversation)

fun Organization.toSerializable() =
    SerializableOrganization(name = name, ids = ids, childOrganization = childOrganization?.toSerializable())

fun Patient.toSerializable() = SerializablePatient(this.fnr, this.firstName, this.middleName, this.lastName)

fun Receiver.toSerializable() =
    SerializableReceiver(this.parent.toSerializable(), this.child.toSerializable(), this.patient.toSerializable())

@Serializable
data class SerializablePatient(val fnr: String, val firstName: String, val middleName: String?, val lastName: String)

@Serializable
data class SerializableDialogmeldingMessage(val hoveddokument: String, val metadataFiler: Map<String, String>)


@Serializable
data class SerializableDialogmelding(@Transient val foresporsel: Foresporsel? = null, val notat: SerializableNotat?)

@Serializable
data class SerializableNotat(
    val tema: String,
    val temaBeskrivelse: String?,
    val innhold: String?,
    val dato: LocalDate?,
)

fun Notat.toSerializable() =
    SerializableNotat(this.tema.verdi, this.temaBeskrivelse, this.innhold, this.dato?.toKotlinLocalDate())

fun Dialogmelding.toSerializable() = SerializableDialogmelding(this.foresporsel, this.notat?.toSerializable())

fun IncomingBusinessDocument.toSerializable() =
    SerializableIncomingBusinessDocument(
        this.id,
        this.date?.toLocalDateTime()?.toKotlinLocalDateTime(),
        this.type.toSerializable(),
        this.sender.toSerializable(),
        this.receiver.toSerializable(),
        this.toSerializableDialogmeldingMessage(),
        this.conversationRef?.toSerializable(),
    )


fun IncomingBusinessDocument.toSerializableDialogmeldingMessage(): SerializableDialogmeldingMessage {
    val metadataFiler = HashMap<String, String>()

    this.vedlegg?.let {
        it.description?.let { description -> metadataFiler.put(AttachmentNames.vedlegg(0), description) }
    }

    return SerializableDialogmeldingMessage(AttachmentNames.dialogmelding, metadataFiler.toMap())
}

object IdListSerializer : KSerializer<List<Id>> {
    private val delegate = ListSerializer(IdSerializer)

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<Id>) {
        delegate.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): List<Id> = delegate.deserialize(decoder)
}

object IdSerializer : KSerializer<Id> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Id") {
            element("id", PrimitiveSerialDescriptor("id", PrimitiveKind.STRING))
            element("type", PrimitiveSerialDescriptor("type", PrimitiveKind.STRING))
        }

    override fun serialize(encoder: Encoder, value: Id) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.id)
            encodeStringElement(
                descriptor,
                1,
                when (value) {
                    is PersonId -> "PERSON_ID:${value.type}" // Adjust based on PersonIdType
                    is OrganizationId -> "ORGANIZATION_ID:${value.type}" // Adjust based on OrganizationIdType
                },
            )
        }
    }

    override fun deserialize(decoder: Decoder): Id =
        decoder.decodeStructure(descriptor) {
            var id: String? = null
            var type: String? = null
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    -1 -> break
                    0 -> id = decodeStringElement(descriptor, 0)
                    1 -> type = decodeStringElement(descriptor, 1)
                    else -> throw IllegalArgumentException("Unexpected index: $index")
                }
            }
            if (id == null || type == null) {
                throw IllegalArgumentException("Missing id or type")
            }
            // Adjust based on actual PersonIdType and OrganizationIdType values
            val pair: Pair<String, String> = Pair(type.split(":").first(), type.split(":").last())
            when (pair.first) {
                // Example: Replace with actual PersonIdType/OrganizationIdType string values
                "PERSON_ID" -> PersonId(id, PersonIdType.valueOf(pair.second)) // Adjust enum
                "ORGANIZATION_ID" -> OrganizationId(id, OrganizationIdType.valueOf(pair.second)) // Adjust enum
                else -> throw IllegalArgumentException("Unknown Id type: $type")
            }
        }
}
