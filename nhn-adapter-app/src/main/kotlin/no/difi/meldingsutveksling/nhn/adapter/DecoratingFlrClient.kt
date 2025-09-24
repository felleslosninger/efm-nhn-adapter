package no.difi.meldingsutveksling.nhn.adapter

import no.ks.fiks.nhn.flr.FastlegeregisteretClient
import no.ks.fiks.nhn.flr.PatientGP

class DecoratingFlrClient(val fastlegeregisteretClient: FastlegeregisteretClient, val activeProfiles: List<String>) {
    fun getPatientGP(fnr: String): PatientGP? =
        fastlegeregisteretClient.getPatientGP(fnr)?.let { gp ->
            if (activeProfiles.any { it in setOf("local", "dev") }) {
                when (fnr) {
                    "21905297101" -> gp.copy(gpHerId = 8143025)
                    "31777207884" -> gp.copy(gpHerId = 8140506)
                    "15720255178" -> gp.copy(gpHerId = 8142342)
                    else -> gp.copy(gpHerId = 8143548)
                }
            } else {
                gp
            }
        }
}
