package no.difi.meldingsutveksling.nhn.adapter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import no.ks.fiks.helseid.HelseIdClient;

@RestController
public class TestController {
    
    @Autowired
    private HelseIDClientConfig config;
    
    @Autowired
    private HelseIdClient helseIdClient;

	@GetMapping(path = "/hello")
	public String test() {
		return "Hello World" + config.getClientID().length() + "," + config.getJwkKey().length();
	}
	@GetMapping(path = "/testHelseId")
	public String testHelseId() {
	   return helseIdClient.getAccessToken().getAccessToken();
	}

}
