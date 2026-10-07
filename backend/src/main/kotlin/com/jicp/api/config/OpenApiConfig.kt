package com.jicp.api.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("JICP API")
                .version("v1")
                .description(
                    "API de la plataforma educativa JICP - Bolsa Social Educativa.\n\n" +
                        "Para probar los endpoints autenticados: POST /api/v1/auth/login, " +
                        "copia el accessToken y pegalo en el boton Authorize.",
                ),
        )
        // Declara el esquema para que Swagger UI muestre el boton Authorize y anada
        // la cabecera Authorization a cada peticion. Sin esto no hay forma de probar
        // /yo desde el navegador.
        .components(
            Components().addSecuritySchemes(
                ESQUEMA_JWT,
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Pega solo el accessToken; Swagger anade el prefijo Bearer."),
            ),
        )

    private companion object {
        const val ESQUEMA_JWT = "bearerAuth"
    }
}
