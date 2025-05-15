package no.difi.meldingsutveksling.nhn.adapter;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

	@GetMapping(path = "/hello")
	public String test() {
		return "Hello World";
	}

}
