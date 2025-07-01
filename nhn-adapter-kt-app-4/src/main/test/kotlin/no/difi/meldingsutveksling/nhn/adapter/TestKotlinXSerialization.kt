package no.difi.meldingsutveksling.nhn.adapter

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.should
import io.kotest.mpp.newInstanceNoArgConstructor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult
import org.springframework.web.reactive.function.client.awaitBody

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
            .uri("http://localhost:8080/kotlinx")
            .bodyValue(TestKotlinX("Alexander","Test"))
            .exchange().expectBody(TestKotlinX::class.java)
        response.returnResult().responseBody!!.run {
            println("$this.name  ${this.value}")
        }
        // alternative 2
        val response2 = webtestClient.post()
            .uri("http://localhost:8080/kotlinx")
            .bodyValue(TestKotlinX("Alexander","Test"))
            .exchange()



        response2.returnResult<TestKotlinX>().responseBody.blockFirst().run {
            println("${this?.name}  ${this?.value}")
        }
        1 shouldBeEqual 1
    }

})