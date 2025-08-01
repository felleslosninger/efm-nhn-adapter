package no.difi.meldingsutveksling.nhn.adapter

data class Configuration(val nhn: Services)

data class Services(val services: NhnConfig)

data class NhnConfig(val username:String,val password:String)