package no.difi.meldingsutveksling.nhn.adapter

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.should
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class TestKotlinXSerialization(@Autowired val webTestClient: WebTestClient,@Autowired val context: ApplicationContext) : FunSpec({


    test("Hello world") {

       val webtestClient = WebTestClient.bindToApplicationContext(context).configureClient().codecs {configurer ->
            configurer.defaultCodecs().jackson2JsonEncoder(Jackson2JsonEncoder())
            configurer.defaultCodecs().jackson2JsonDecoder(Jackson2JsonDecoder())
        }.build()


        1 shouldBeEqual 1
    }

})