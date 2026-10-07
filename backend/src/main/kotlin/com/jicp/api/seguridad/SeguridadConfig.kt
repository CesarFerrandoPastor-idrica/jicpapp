package com.jicp.api.seguridad

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
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
            // Perfil del usuario del token: no tiene sentido sin autenticar.
            it.requestMatchers("/api/v1/yo").authenticated()

            // Login, renovacion y cierre de sesion: por definicion se llaman sin token.
            it.requestMatchers("/api/v1/auth/**").permitAll()

            // El alta de alumno es todavia el registro publico. Cuando exista
            // POST /auth/registro, esta linea se cae y el endpoint pasa a ser de profesor.
            it.requestMatchers(HttpMethod.POST, "/api/v1/alumnos").permitAll()

            // El alta y la edicion de profesorado exigen token: crear credenciales no es
            // lo mismo que leer un catalogo, asi que estos endpoints se cierran ya aunque
            // el resto del CRUD siga abierto. Quien puede hacerlo lo decide @PreAuthorize
            // en ProfesorController; el primer profesor lo crea ProfesorInicial desde el
            // servidor, no por red.
            it.requestMatchers(HttpMethod.POST, "/api/v1/profesores").authenticated()
            it.requestMatchers(HttpMethod.PUT, "/api/v1/profesores/**").authenticated()

            // Endpoints que mueven dinero o dependen de quien eres. Se exige token aqui,
            // en la cadena de filtros, para que una peticion anonima reciba 401 y no el 403
            // que devolveria @PreAuthorize: sin credenciales el problema es que no sabemos
            // quien eres, no que no te dejemos.
            it.requestMatchers(HttpMethod.POST, "/api/v1/proyectos").authenticated()
            it.requestMatchers(HttpMethod.POST, "/api/v1/inversiones").authenticated()
            it.requestMatchers(HttpMethod.POST, "/api/v1/proyectos/*/comentarios").authenticated()
            it.requestMatchers("/api/v1/cartera/**", "/api/v1/portafolio").authenticated()

            it.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
            it.requestMatchers("/actuator/health", "/actuator/info").permitAll()

            // El resto de la API sigue abierta a proposito: la app Flutter todavia no manda
            // token y cerrar todo de golpe la dejaria sin backend. Se ira cerrando endpoint
            // por endpoint segun cada pantalla se conecte. Ver README, roadmap fase 2.
            it.anyRequest().permitAll()
        }
        .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter::class.java)
        .build()
}
