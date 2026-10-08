package com.jicp.api.seguridad

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(PropiedadesJwt::class)
class SeguridadConfig(
    private val filtroJwt: FiltroJwt,
    private val puntoDeEntrada: PuntoDeEntradaNoAutenticado,
    private val accesoDenegado: ManejadorDeAccesoDenegado,
) {

    @Bean
    fun cadenaDeFiltros(http: HttpSecurity): SecurityFilterChain = http
        // Sin CSRF porque no hay sesion ni cookies: la credencial viaja en una cabecera
        // que un formulario de otro origen no puede fijar.
        .csrf { it.disable() }
        // Toma el bean CorsConfigurationSource. Sin esta linea la configuracion de
        // CORS existe pero la cadena de filtros no la aplica, y el preflight OPTIONS
        // se rechaza antes de llegar a ningun controlador.
        .cors { }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .httpBasic { it.disable() }
        .formLogin { it.disable() }
        .exceptionHandling {
            it.authenticationEntryPoint(puntoDeEntrada)
            it.accessDeniedHandler(accesoDenegado)
        }
        .authorizeHttpRequests {
            // Lo unico que se puede llamar sin token. Todo lo demas nace cerrado: un endpoint
            // nuevo exige autenticacion salvo que se abra aqui a proposito.
            //
            // Login, renovacion y cierre de sesion: por definicion se llaman sin token.
            it.requestMatchers("/api/v1/auth/**").permitAll()
            it.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
            it.requestMatchers("/actuator/health", "/actuator/info").permitAll()

            // Con token, pero sin decidir aqui QUIEN: eso lo hace @PreAuthorize en cada
            // controlador (por ejemplo, las altas son de ADMIN). Exigirlo en la cadena de
            // filtros hace que una peticion anonima reciba 401 y no el 403 de @PreAuthorize:
            // sin credenciales el problema es que no sabemos quien eres, no que no te dejemos.
            it.anyRequest().authenticated()
        }
        .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter::class.java)
        .build()
}
