package no.difi.meldingsutveksling.nhn.adapter

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class TestKotlinXSerialization(@Autowired val webTestClient: WebTestClient,@Autowired val context: ApplicationContext) : FunSpec({


    test("Hello world") {

       val webtestClient = WebTestClient.bindToApplicationContext(context).configureClient().codecs {configurer ->
            configurer.defaultCodecs().jackson2JsonEncoder(Jackson2JsonEncoder())
            configurer.defaultCodecs().jackson2JsonDecoder(Jackson2JsonDecoder())
        }.build()
        // alternative 1
        val response = webtestClient.post()
            .uri("http://localhost:8080/kotlinxsealedclass")
            .bodyValue(ClientCommunicationParty("Reciever","testHerId1","testHerId2"))
            .exchange().expectBody(ClientCommunicationParty::class.java)
        response.returnResult().responseBody!!.run {
            println("${this.herid1}  ${this.herid2}")
        }
        // alternative 2
        val response2 = webtestClient.post()
            .uri("http://localhost:8080/kotlinxsealedclass")
            .bodyValue(ClientCommunicationParty("Sender","testHerId1","testHerId2"))
            .exchange()



        response2.returnResult<ClientCommunicationParty>().responseBody.blockFirst().run {
            println("${this?.herid1}  ${this?.herid2}")
        }
        1 shouldBeEqual 1
    }

})


data class ClientCommunicationParty(var type:String, val herid1:String, val herid2:String)