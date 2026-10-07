package com.jicp.api

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Los tests corren contra un PostgreSQL real levantado con Testcontainers.
 * Una base en memoria (H2) no serviria: no comparte tipos, ni constraints,
 * ni el comportamiento de bloqueos que necesitaremos para la contabilidad de JICP.
 */
@TestConfiguration(proxyBeanMethods = false)
class ContenedoresConfig {

    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer<*> =
        PostgreSQLContainer(DockerImageName.parse("postgres:16"))
}
