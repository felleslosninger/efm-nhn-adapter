package no.difi.meldingsutveksling.nhn.adapter.errors

class DialogmeldingNotFoundException(hoveddokument: String) :
    ClientInputException("Hoveddokument '$hoveddokument' was not found in the dokumentpakke")
