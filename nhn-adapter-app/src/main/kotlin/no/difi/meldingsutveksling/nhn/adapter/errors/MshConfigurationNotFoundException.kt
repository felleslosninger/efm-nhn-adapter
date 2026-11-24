package no.difi.meldingsutveksling.nhn.adapter.errors

class MshConfigurationNotFoundException(herId: Int) : RuntimeException("No MSH configuration of HER-id $herId")
