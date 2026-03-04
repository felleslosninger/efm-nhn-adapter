package no.difi.meldingsutveksling.nhn.adapter.model

import java.time.OffsetDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid
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
import no.ks.fiks.nhn.msh.IncomingVedlegg
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
data class InMessage
constructor(
    val id: String,
    val contentType: String,
    val receiverHerId: Int,
    val senderHerId: Int,
    val businessDocumentId: String,
    @Serializable(with = OffsetDateTimeSerializer::class) val businessDocumentDate: OffsetDateTime?,
)

@OptIn(ExperimentalUuidApi::class)
fun MessageWithMetadata.toInMessage(): InMessage =
    InMessage(
        this.id.toString(),
        this.contentType,
        this.receiverHerId,
        this.senderHerId,
        this.businessDocumentId,
        this.businessDocumentDate,
    )

@Serializable
data class SerializeableIncomingBusinessDocument(
    val id: String,
    val date: LocalDateTime?,
    val type: SerializeableMeldingensFunksjon,
    val sender: SerializeableSender,
    val receiver: SerializeableReceiver,
    val message: SerializeableDialogmelding?,
    val vedlegg: SerializeableIncomingVedlegg?,
    val conversationRef: SerializeableConversationRef?
)

@Serializable data class SerializeableMeldingensFunksjon(val verdi: String, val navn: String, val kodeverk: String)

fun MeldingensFunksjon.toSerializeable() =
    SerializeableMeldingensFunksjon(verdi = this.verdi, navn = this.navn, kodeverk = this.kodeverk)

@Serializable
data class SerializeableOrganization(
    val name: String,
    @Serializable(with = IdListSerializer::class) val ids: List<Id>,
    val childOrganization: SerializeableChildOrganization?,
)

@Serializable
data class SerializeableChildOrganization(
    val name: String,
    @Serializable(with = IdListSerializer::class) val ids: List<Id>,
)

@Serializable
sealed class SerializeableCommunicationParty {
    @Serializable(with = IdListSerializer::class) abstract val ids: List<Id>

    @Serializable
    @SerialName("organization")
    data class Organization(
        @Serializable(with = IdListSerializer::class) override val ids: List<Id>,
        val name: String,
    ) : SerializeableCommunicationParty()

    @Serializable
    @SerialName("person")
    data class Person(
        @Serializable(with = IdListSerializer::class) override val ids: List<Id>,
        val firstName: String,
        val middleName: String? = null,
        val lastName: String,
    ) : SerializeableCommunicationParty()
}

fun CommunicationParty.toSerializeable(): SerializeableCommunicationParty =
    when (this) {
        is OrganizationCommunicationParty ->
            SerializeableCommunicationParty.Organization(ids = this.ids, name = this.name)
        is PersonCommunicationParty ->
            SerializeableCommunicationParty.Person(
                ids = this.ids,
                firstName = this.firstName,
                middleName = this.middleName,
                lastName = this.lastName,
            )
    }

@Serializable
data class SerializeableSender(
    val parent: SerializeableCommunicationParty,
    val child: SerializeableCommunicationParty,
)

fun Sender.toSerializeable(): SerializeableSender =
    SerializeableSender(this.parent.toSerializeable(), this.parent.toSerializeable())

@Serializable
data class SerializeableReceiver(
    val parent: SerializeableCommunicationParty,
    val child: SerializeableCommunicationParty,
    val patient: SerializeablePatient,
)

@Serializable
sealed interface SerializeableReceiverDetails {
    @Serializable(with = IdListSerializer::class) val ids: List<Id>
}

@Serializable
@SerialName("organization")
data class SerializeableOrganizationReceiverDetails(
    @Serializable(with = IdListSerializer::class) override val ids: List<Id>,
    val name: String,
) : SerializeableReceiverDetails

@Serializable
data class SerializeableConversationRef(
    val refToParent: String?,
    val refToConversation: String?,
)

@Serializable
@SerialName("person")
data class SerializeablePersonReceiverDetails(
    @Serializable(with = IdListSerializer::class) override val ids: List<Id>,
    val firstName: String,
    val middleName: String?,
    val lastName: String,
) : SerializeableReceiverDetails

@Serializable
data class SerializeableIncomingVedlegg
@OptIn(ExperimentalTime::class)
constructor(@Contextual val date: Instant?, val description: String?, val mimeType: String?, val data: ByteArray?)

fun ChildOrganization.toSerializeable() = SerializeableChildOrganization(name = name, ids = ids)

fun ConversationRef.toSerializeable() = SerializeableConversationRef(this.refToParent,this.refToConversation)

fun Organization.toSerializable() =
    SerializeableOrganization(name = name, ids = ids, childOrganization = childOrganization?.toSerializeable())

fun Patient.toSerializeable() = SerializeablePatient(this.fnr, this.firstName, this.middleName, this.lastName)

fun Receiver.toSerializable() =
    SerializeableReceiver(this.parent.toSerializeable(), this.child.toSerializeable(), this.patient.toSerializeable())

@Serializable
data class SerializeablePatient(val fnr: String, val firstName: String, val middleName: String?, val lastName: String)

@Serializable
data class SerializeableDialogmelding(@Transient val foresporsel: Foresporsel? = null, val notat: SerializeableNotat?)

@Serializable
data class SerializeableNotat(
    val tema: String,
    val temaBeskrivelse: String?,
    val innhold: String?,
    val dato: LocalDate?,
)

fun Notat.toSerializable() =
    SerializeableNotat(this.tema.verdi, this.temaBeskrivelse, this.innhold, this.dato?.toKotlinLocalDate())

fun Dialogmelding.toSerializeable() = SerializeableDialogmelding(this.foresporsel, this.notat?.toSerializable())

@OptIn(ExperimentalTime::class)
fun IncomingVedlegg.toSerializeable() =
    SerializeableIncomingVedlegg(
        this.date?.toInstant()?.toKotlinInstant(),
        this.description,
        this.mimeType,
        this.data?.readAllBytes(),
    )

fun IncomingBusinessDocument.toSerializeable() =
    SerializeableIncomingBusinessDocument(
        this.id,
        this.date?.toLocalDateTime()?.toKotlinLocalDateTime(),
        this.type.toSerializeable(),
        this.sender.toSerializeable(),
        this.receiver.toSerializable(),
        this.message?.toSerializeable(),
        this.vedlegg?.toSerializeable(),
        this.conversationRef?.toSerializeable(),
    )

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
                    else -> throw IllegalArgumentException("Unknown Id subtype: ${value::class}")
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
            val pair: Pair<String,String> = Pair(type.split(":").first(),type.split(":").last())
            when (pair.first) {
                // Example: Replace with actual PersonIdType/OrganizationIdType string values
                "PERSON_ID" -> PersonId(id, PersonIdType.valueOf(pair.second)) // Adjust enum
                "ORGANIZATION_ID" -> OrganizationId(id, OrganizationIdType.valueOf(pair.second)) // Adjust enum
                else -> throw IllegalArgumentException("Unknown Id type: $type")
            }
        }
}
