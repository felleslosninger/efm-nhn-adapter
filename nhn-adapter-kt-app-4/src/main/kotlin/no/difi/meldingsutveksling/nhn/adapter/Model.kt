package no.difi.meldingsutveksling.nhn.adapter

import kotlinx.serialization.Serializable

sealed interface CommunicationParty {
     val herid1:String
     val herId2:String
}

@Serializable
data class Sender(override val herid1:String, override val herId2:String) : CommunicationParty
@Serializable
data class Reciever(override val herid1: String, override val herId2: String) : CommunicationParty


@Serializable
data class MessageOut(val messageId:String,
                      val conversationId:String,
                      val sender: Sender,
                      val reciever: Reciever,
                      val fagmelding:String,
                      )



