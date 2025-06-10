package no.difi.meldingsutveksling.nhn.adapter;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalUnit;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import no.ks.fiks.helseid.CachedHttpDiscoveryOpenIdConfiguration;
import no.ks.fiks.helseid.Environment;
import no.ks.fiks.helseid.HelseIdClient;
import no.ks.fiks.nhn.msh.Client;
import no.ks.fiks.nhn.msh.Credentials;
import no.ks.fiks.nhn.msh.Environments;
import no.ks.fiks.nhn.msh.HelseIdConfiguration;

@Configuration
public class HelseIDClientConfig {

    @Value("${oauth2.helse-id.client-id}")
    private String clientID;

    @Value("${oauth2.helse-id.private-key}")
    private String jwkKey;


    @Bean
    public HttpClient httpClient() {
        return HttpClients.createDefault();
    }
    
    @Bean
    public no.ks.fiks.helseid.Configuration helseIdConfiguration() {
        return new no.ks.fiks.helseid.Configuration(clientID, 
                jwkKey, 
                Environment.Companion.getTEST(), 
                Duration.of(60, ChronoUnit.SECONDS), 
                Duration.of(10, ChronoUnit.SECONDS));
    }

    @Bean
    public HelseIdClient helseIdClient(@Autowired HttpClient httpClient,@Autowired no.ks.fiks.helseid.Configuration helseIdConfiguration) {
        return new HelseIdClient(helseIdConfiguration, 
                                 httpClient, 
                                 new CachedHttpDiscoveryOpenIdConfiguration(Environment.Companion.getTEST().getIssuer()));

    }
    
    @Bean
    public Client nhnClient(@Autowired no.ks.fiks.helseid.Configuration helseIdConfiguration) {
    	 return new Client(new no.ks.fiks.nhn.msh.Configuration(Environments.Companion.getTEST(), 
					"eFormidling", new HelseIdConfiguration(helseIdConfiguration.getClientId(), helseIdConfiguration.getJwk()), 
					new Credentials("dummy-flr-user", "dummy-flr-password"), new Credentials("dummy-ar-user", "dummy-ar-password")));
    }

    public String getClientID() {
        return clientID;
    }

    public String getJwkKey() {

        return jwkKey;
    }


}
