package no.difi.meldingsutveksling.nhn.adapter.integration.msh

import no.kith.xmlstds.apprec._2012_02_15.AppRec

data class SendApplicationReceiptInput(val senderHerId: Int, val receipt: AppRec)
