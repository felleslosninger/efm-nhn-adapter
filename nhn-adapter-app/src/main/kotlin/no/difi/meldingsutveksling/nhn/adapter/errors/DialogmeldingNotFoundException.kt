package no.difi.meldingsutveksling.nhn.adapter.errors

class DialogmeldingNotFoundException(hoveddokument: String) :
    RuntimeException("Hoveddokument '$hoveddokument' was not found in the dokumentpakke")
