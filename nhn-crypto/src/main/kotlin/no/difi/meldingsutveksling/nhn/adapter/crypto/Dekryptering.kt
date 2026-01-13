package no.difi.meldingsutveksling.nhn.adapter.crypto

import java.security.PrivateKey
import kotlin.io.encoding.Base64
import org.bouncycastle.cms.CMSEnvelopedData
import org.bouncycastle.cms.KeyTransRecipientId
import org.bouncycastle.cms.RecipientId
import org.bouncycastle.cms.RecipientInformation
import org.bouncycastle.cms.RecipientInformationStore
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient

interface Dekrypter {
    fun dekrypter(byteArray: ByteArray): ByteArray
}

class Dekryptering(private val keyStore: NhnKeystore) : Dekrypter {
    override fun dekrypter(byteArray: ByteArray): ByteArray {
        val bytes = Base64.decode(byteArray)

        try {
            val envelopedData = CMSEnvelopedData(bytes)
            val recipients: RecipientInformationStore = envelopedData.recipientInfos
            if (recipients.recipients.size > 1) {
                throw DecryptionException("There can be only one recipient for encrypted docyment.")
            }
            val recipient = recipients.recipients.first()
            val key: PrivateKey = getPrivateKeyMatch(recipient)
            return getDeenvelopedContent(recipient, key)
        } catch (e: DecryptionException) {
            throw e
        } catch (e: Exception) {
            throw DecryptionException("Feil ved dekryptering", e)
        }
    }

    private fun getDeenvelopedContent(recipient: RecipientInformation, key: PrivateKey): ByteArray =
        recipient.getContent(JceKeyTransEnvelopedRecipient(key)) ?: throw DecryptionException("Meldingen er tom.")

    private fun getPrivateKeyMatch(recipient: RecipientInformation): PrivateKey {
        if (recipient.rid.type == RecipientId.keyTrans) {
            val rid = recipient.rid as KeyTransRecipientId
            return keyStore.getPrivateKey(rid.serialNumber)
                ?: throw DecryptionException("Fant ingen gyldige privatsertifikat for dekryptering")
        }
        throw DecryptionException(
            "Fant ikke riktig sertifikat for mottaker med serienummer: ${(recipient.rid as KeyTransRecipientId).serialNumber}"
        )
    }
}
