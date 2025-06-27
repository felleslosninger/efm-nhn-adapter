package no.difi.meldingsutveksling.nhn.adapter

import okhttp3.internal.wait
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.support.beans
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter
import org.springframework.web.reactive.function.server.router


@SpringBootApplication
class Main {

}

fun main(args: Array<String>) {

    runApplication<Main>(*args) {
    }
}