package no.difi.meldingsutveksling.nhn.adapter.config

import no.difi.meldingsutveksling.nhn.adapter.BeanRegistration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration @Import(BeanRegistration::class) class AppConfig
