package no.difi.meldingsutveksling.nhn.adapter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface CommunicationParty {
     val herid1:String
     val herid2:String
}

@Serializable
@SerialName("Sender")
data class Sender(override val herid1:String, override val herid2:String) : CommunicationParty
@Serializable
@SerialName("Reciever")
data class Reciever(override val herid1: String, override val herid2: String) : CommunicationParty


@Serializable
data class MessageOut(val messageId:String,
                      val conversationId:String,
                      val sender: Sender,
                      val reciever: Reciever,
                      val fagmelding:String,
                      )
@Serializable
data class ArDetails(val herid1:String, val herid2:String,val ediAdress:String)



