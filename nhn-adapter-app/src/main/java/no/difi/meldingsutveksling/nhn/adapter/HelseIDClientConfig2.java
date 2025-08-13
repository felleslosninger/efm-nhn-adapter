package no.difi.meldingsutveksling.nhn.adapter;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalUnit;
import java.util.List;
import java.util.UUID;

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import no.ks.fiks.hdir.Helsepersonell;
import no.ks.fiks.hdir.HelsepersonellsFunksjoner;
import no.ks.fiks.hdir.OrganisasjonIdType;
import no.ks.fiks.helseid.AccessTokenRequestBuilder;
import no.ks.fiks.helseid.CachedHttpDiscoveryOpenIdConfiguration;
import no.ks.fiks.helseid.Environment;
import no.ks.fiks.helseid.HelseIdClient;
import no.ks.fiks.helseid.TenancyType;
import no.ks.fiks.helseid.TokenType;
import no.ks.fiks.helseid.dpop.Endpoint;
import no.ks.fiks.helseid.dpop.HttpMethod;
import no.ks.fiks.helseid.dpop.ProofBuilder;
import no.ks.fiks.helseid.http.HttpRequestHelper;
import no.ks.fiks.nhn.edi.BusinessDocumentSerializer;
import no.ks.fiks.nhn.msh.BusinessDocumentMessage;
import no.ks.fiks.nhn.msh.Client;
import no.ks.fiks.nhn.msh.Credentials;
import no.ks.fiks.nhn.msh.DialogmeldingVersion;
import no.ks.fiks.nhn.msh.Environments;
import no.ks.fiks.nhn.msh.HealthcareProfessional;
import no.ks.fiks.nhn.msh.HelseIdConfiguration;
import no.ks.fiks.nhn.msh.HerIdReceiver;
import no.ks.fiks.nhn.msh.HerIdReceiverChild;
import no.ks.fiks.nhn.msh.HerIdReceiverParent;
import no.ks.fiks.nhn.msh.Id;
import no.ks.fiks.nhn.msh.IncomingBusinessDocument;
import no.ks.fiks.nhn.msh.OrganizationHerIdReceiverChild;
import no.ks.fiks.nhn.msh.OutgoingBusinessDocument;
import no.ks.fiks.nhn.msh.Patient;
import no.ks.fiks.nhn.msh.RecipientContact;
import no.ks.fiks.nhn.msh.Vedlegg;
import no.nhn.register.communicationparty.Organization;

@Configuration
public class HelseIDClientConfig2 {

  //  @Value("${oauth2.helse-id.client-id}")
    private String clientID;

  //  @Value("${oauth2.helse-id.private-key}")
    private String jwkKey;
    
    private String inlineClientID = "5eeab261-5110-475a-907c-f8c9cbfc3fb1";
    
    private String inlineJWK = """
             {"alg":"PS256","d":"TE7UT9MTPLqZeyk2O5fiKOncM-XMZwzPpJKj4ZL5gzSyfjyFNUtWM1w9Ta5LQQr2hojgw1iafa8bNfSdl8Z_Ly0Zu_60y42SB2BuWTSo-y3D7Yb1oZlO4HDN5ZtdKibS3xPObGJheqsN68YL3GxxeZ1ixOxYkUCDWMrMA_D-IAKVgp3uLFr5mYNifkzyBVPNWvTbfZ9Au9RsiZk7pmCWFC8P7wxIqxJzkhjIjx41K1SjkdhbbXsCRxzojAK3hopTLqOk-4XrgA-5flavnrnaXJ6Wtazutn56EtdpSHUKPpyUqcM5EmQI0xByVzAJCnwnvr6COctyA6WpFBIB3YpGaAmv2SMMuGbo11JVPPDjXVxgcUYugPzmNzdoUorkTkQIM3JR3MHpOgrFqk8Zh3GdUXdi1aHqwG5F_m3RoLMr3jwhgWj9Q9tXBkbLyUGoQLmSUy1LTErYM-Zm9LxFBnfkM7H5WLKILtZEaAEYvb2gwU0G264JfWH6fMfW_URzpj-hy682v13J08dR3-FjTu8N-xcMdZU7j3meFEfPH_sKn_55UNsUVAH18P1CNzg-SHcaNpy0Rpfz4tzOth88C-C_E3BsaNyKsJjzQ9TD80tkAewu1i3gIYo6I3XdzbIQszT4VzA8zABHKGkFkmP6lVLxf4ghpRIO22Q09HQ8Jb0ii_E","dp":"ZuJUZTeGM-QlAVd7jTwrzSnePeM2dr8CZ68d7A7YjHXqU00R_vX-PUqxfv_muT94AH835Ore0a4gy79RpWCLIs94kmu_3B9XcZRKV6dAnTpI_x9kybtUO5kTXTgtIQf3hnOQkYF9HZW3qEorLFnk4uw07eLov0egM6bTIpKEkzbgGh1diPSKNcoHlDzVBVZbK6pWCQ-tcwx-WX4V63ckniXkNb7ApJ7iGsSiMsUHlrakzmbQbFNhVhUXBX16g6ESARN9oRg2eenBHFhvpanzD0pf68L6G4lFSVO_B9igCbAW4pYqX1YEslcAw7j2pzsw3cFUvFWxjarM4XH4ehxayw","dq":"TVg-CcEtJsSNut_rkGDgnpHG09gz70kdEY_okoKre0eTrWkkrrheNQoiyq2AZ6EnHjWY5MJZgi81SEBCTd1zEmbnywehqJ8bhOyc1E5scweI8SEX8jg6VCP5hIKfahL8RSG-DgK_nKGjb18zvX7GvkWgfpqT2biqdBPz5OBneiB7QBElxnpIPRU46HU4No2S7VL9LEVnz3rBBwuRzXpJKaFhIAGYl6232WPeEAk_CzukHIOEaAmdxKfX7213769kVbcbRzj0PXXa_MH4JRQhsZENEEFZ2aHaxdSao1Jgzobq7H8RxlFigrJnVNqmZCuBpqNaBcusk7Lwc1LICFtQWQ","e":"AQAB","key_ops":["sign"],"kty":"RSA","n":"-kZebepCdrWlAmrds3ck4b1KtrfS3PAFJHCK1BfRV7xtmg8PKp491l5AB_AgLI-wR7ynUksMQGK2JG8LaELqAt-ZNt1XlTu_ZAnPY24uemkyZDkg7ssYL1jqlZqEVj9ANcqe6qqSKtk9Z0lAbCIaXetnayuAXctTn_DkiqI4b1DPGdi0UCnkpA0rmz4w-BsXolm2SeF8zFVHO35qIC-C0MqT0iMz08No4digMbFkYp7mbzQBaD1-BAm7fubOfJpgGUzw8tdf-9bRaweimK5abgmgLFLEeGRzHZ0YuX1ORiQRZwdj362I8ZcrHnHsTXK-NiUQOSksfuGYP6_tVzJzfq-qJl87YoE0e-jaMjTXygcRtExT_tW4ZlCV4xnEvzWN7hZqxGTWyxfQ9Qc3zTjk7DW4sIxDlg0UE06mrZ1gPlbvfjOnHQ8cLMvNdB_YT5sNflStFuiHmnnOyZclTXJjGCf92snqsK1Kl0WcIYXAbKMsLS3C-QbQNwQJtslk1TECoBpPtIpRYSnpfzEfaYPDmGRCcCERSgSqX-rub_XVOYWaCpmES2FmBI5Q70EE8EOd_vPapmzUPXz82jwcOfDAKuZsNOZTWamx62z5inWT9Yy307TLFOPzZ5_ZNxpuYmKkBw_gId38ivlZbZeTLQnB6Pr_wIRj5GT18_ZsAqJ2zM8","p":"_uTn1K67aNwJiX5ZVKWSysyisksyqVoUfADd_qwDtZ925n5rrDFAPgXQbBehP6arRdXUPU4Q8OcL1Z0NCtmxC1uNoTilNrgBK5NNFMEgqpXFlKQhVyDHCYuHydcMSuBF0V1jcnTzDVp3ztsTMhJ-_yFCoXpXBbQkdT5hHjNDa7wYg1uYM6jRDrPXYFMVjbPD5xXR6OMjB7FukNaafOicNUAx0QPia1upuyRKW0ByOzCp6LZQQdgWKAfKmt1nH_rx3M69C_g3EQFzrTbD39jo5HFMCaq4YajkByvfK3pVKlcpVt24xLqVU_MjuhFEs-drhFegDboWnrIcW4nWIsAbow","q":"-1xVO1Mizte2qyUF62O3YTH1W2oZVP2-rpbJ4Bn5Hm4GGyBL3ITmMm1s9KebugwEw9ylkWUNfQ0ellL0K5CakWdwOEFcWA9JP5oPF8e_OZupR1gjykdMzqWpH8OFlPBDVXXfPqVBA5iDoXvc6770qg_gxi1GW4JtSOxSrZCYrCOC_X3TxGMAeW4WdydJJMLOJZm2g5mUqlgBcdPiW88IQB1ijafoJnf70yixKkOtFbDt8z_3narrCCugUvBtpUAOsGXF1l1S-jZjtiEeev03jEKAo3yco43N4KlnNNkUcymHpb22SYRDYlggYyUVil7Gn0EUwfHc53FDb-55JYrc5Q","qi":"vdsYMV0mWRVmLYZONgcS2GSE84LI-bKhstT1LNnj8YZJrWgT1uD8iH_TIp3l0YR-gBrvqCOIgeuTPcjZg38Kj8tYQKIwKcO5TIRwqYXzma0MlNqaQ7yw3EaOcXSBRq5dG-84kD8WSZSjicHf2HxQLsZqYmqaBlOQqktBRzxTyO8XUDATB1ejVwl8n-lZJ_9niUqUZi1pcq8Azwt_imxiFSxEp9FwnicbrQW8XBBhLXOi59H21nA2lzLiHH6QRRKv5EADm9i9TpNzKKr4U_q92XWxq8G9atpAW3axr6EPgj_IFhCttCMXdww67z_D6Z1Hc7caLAk0gzfDaCDuL7OHkQ","kid":"A5RrHLQxGT2XKbni36-q_og8lLKhPkGgohX-q1ZsBPY"}
            """;


    @Bean
    public HttpClient httpClient() {
        return HttpClients.createDefault();
    }

   // @Bean
    public HelseIdClient helseIdClient(@Autowired HttpClient httpClient) {
        return new HelseIdClient(new no.ks.fiks.helseid.Configuration(clientID, 
                jwkKey, 
                Environment.Companion.getTEST(), 
                Duration.of(60, ChronoUnit.SECONDS), 
                Duration.of(10, ChronoUnit.SECONDS)), 
                                 httpClient, 
                                 new CachedHttpDiscoveryOpenIdConfiguration(Environment.Companion.getTEST().getIssuer()));

    }
    
    @Bean
    public no.ks.fiks.helseid.Configuration helseIdConfiguration() {
        return new no.ks.fiks.helseid.Configuration(inlineClientID, 
                inlineJWK, 
                Environment.Companion.getTEST(), 
                Duration.of(60, ChronoUnit.SECONDS), 
                Duration.of(10, ChronoUnit.SECONDS));
    }
    
    @Bean
    public HelseIdClient helseIdClientInline(@Autowired HttpClient httpClient, @Autowired no.ks.fiks.helseid.Configuration configuration) throws Exception {
        
        HelseIdClient client = new HelseIdClient(configuration, 
                                 httpClient, 
                                 new CachedHttpDiscoveryOpenIdConfiguration(Environment.Companion.getTEST().getIssuer()));
        String accessToken = client.getAccessToken(new AccessTokenRequestBuilder()
        		.parentOrganizationNumber("931796003")
        		.tokenType(TokenType.BEARER)
        		.tenancyType(TenancyType.MULTI)
        		.build()).getAccessToken();
        
        String dpopAccessToken = client.getAccessToken(new AccessTokenRequestBuilder()
        		.parentOrganizationNumber("931796003")
        		.tokenType(TokenType.DPOP)
        		.tenancyType(TenancyType.MULTI)
        		.build()).getAccessToken();
        
       
        
        Client mshClient = new Client(new no.ks.fiks.nhn.msh.Configuration(Environments.Companion.getTEST(), 
        							"eFormidling", new HelseIdConfiguration(configuration.getClientId(), configuration.getJwk()), 
        							new Credentials("dummy-flr-user", "dummy-flr-password"), new Credentials("dummy-ar-user", "dummy-ar-password")));
   
        
        OutgoingBusinessDocument outgoingBusinessDocument = new OutgoingBusinessDocument(UUID.randomUUID(),
        		new no.ks.fiks.nhn.msh.Organization("KS-DIGITALE FELLESTJENESTER AS", new Id("8142987", OrganisasjonIdType.HER_ID), new no.ks.fiks.nhn.msh.Organization("Digdir multi-tenant test", new Id("8143154", OrganisasjonIdType.HER_ID), null)),
        		new HerIdReceiver( new HerIdReceiverParent("DIGITALISERINGSDIREKTORATET", new Id("8143143", OrganisasjonIdType.HER_ID)), new OrganizationHerIdReceiverChild("Service 1", new Id("8143144", OrganisasjonIdType.HER_ID)), new Patient("14038342168", "Aleksander", null, "Petterson")),
        		new BusinessDocumentMessage(
                        "<Message subject>",
                        "<Message body>",
                        new HealthcareProfessional(
                            // Person responsible for this message at the sender
                            "<First name>",
                            "<Middle name>",
                            "<Last name>",
                            "11223344",
                            HelsepersonellsFunksjoner.HELSEFAGLIG_KONTAKT // This persons role with respect to the patient
                        ),
                        new RecipientContact(
                           
                            Helsepersonell.LEGE // Professional group of the healthcare professional recieving the message
                        )
                    ), new Vedlegg(OffsetDateTime.now(), "<Description of the attachment>", this.getClass().getClassLoader().getResourceAsStream("small.pdf")), 
        		DialogmeldingVersion.V1_1);
        System.out.println(BusinessDocumentSerializer.INSTANCE.serializeNhnMessage(outgoingBusinessDocument));
        
        mshClient.sendMessage(outgoingBusinessDocument,"931796003");
        UUID messageID = mshClient.getMessages(8143144,"991825827").iterator().next().getId();
        mshClient.getBusinessDocument(messageID,"991825827");

        
        
        return client;

    }

    public String getClientID() {
        return clientID;
    }

    public String getJwkKey() {

        return jwkKey;
    }


}
