package no.difi.meldingsutveksling.nhn.adapter

import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import no.ks.fiks.nhn.flr.PatientGP

class DecoratingFlrClient(val fastlegeregisteretClient: FastlegeregisteretClient, val activeProfiles: List<String>) {
    fun getPatientGP(fnr: String): PatientGP? =
        fastlegeregisteretClient.getPatientGP(fnr)?.let { gp ->
            if (activeProfiles.any { it in setOf("local", "dev") }) gp.copy(gpHerId = 8143145) else gp
        }
}
