package no.difi.meldingsutveksling.nhn.adapter.config

import com.zaxxer.hikari.HikariConfig
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.FileSystemResource

/**
 * The tests have an application.yml of their own, which replaces the main one, so the database settings of the main
 * one are checked here, bound as Spring Boot binds them.
 */
class DatasourceConfigTest :
    DescribeSpec({
        val mainConfig = FileSystemResource(Path.of("src/main/resources/application.yml"))
        val environment =
            StandardEnvironment().apply {
                YamlPropertySourceLoader().load("application.yml", mainConfig).forEach { propertySources.addLast(it) }
            }

        it("gives up soon on a database that is down") {
            val hikari = Binder.get(environment).bind("spring.datasource.hikari", HikariConfig::class.java).get()

            hikari.connectionTimeout shouldBe 2000
            hikari.validationTimeout shouldBe 1000
            hikari.dataSourceProperties.getProperty("connectTimeout") shouldBe "2000"
            hikari.dataSourceProperties.getProperty("socketTimeout") shouldBe "10000"
        }
    })
