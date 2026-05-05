package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import java.util.UUID
import no.difi.move.common.dokumentpakking.domain.Document
import no.kith.xmlstds.dialog._2013_01_23.Dialogmelding
import no.ks.fiks.nhn.msh.ConversationRef
import no.ks.fiks.nhn.msh.Receiver
import no.ks.fiks.nhn.msh.Sender

data class SendMessageInput(
    val id: UUID,
    val sender: Sender,
    val receiver: Receiver,
    val dialogmelding: Dialogmelding,
    val vedlegg: List<Document>,
    val metadataFiler: Map<String, String?>,
    val conversationRef: ConversationRef?,
)
