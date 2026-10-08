package com.jicp.api.seguridad

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class AutenticacionController(
    private val servicio: ServicioAutenticacion,
) {

    @PostMapping("/auth/login")
    fun login(@Valid @RequestBody peticion: LoginRequest): TokensResponse = servicio.login(peticion)

    @PostMapping("/auth/refresh")
    fun refrescar(@Valid @RequestBody peticion: RefrescarRequest): TokensResponse =
        servicio.refrescar(peticion)

    @PostMapping("/auth/logout")
    fun logout(@Valid @RequestBody peticion: RefrescarRequest): ResponseEntity<Void> {
        servicio.logout(peticion)
        return ResponseEntity.noContent().build()
    }

    /**
     * El perfil sale del token, no de la ruta: no existe /yo/{id} porque eso invitaria
     * a pedir el de otro.
     */
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/yo")
    fun yo(@AuthenticationPrincipal autenticado: UsuarioAutenticado): PerfilResponse =
        servicio.perfil(autenticado)

    /** Cambiar el propio email o la contrasena. Siempre con la contrasena actual. */
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/yo")
    fun actualizarYo(
        @Valid @RequestBody peticion: ActualizarMiCuentaRequest,
        @AuthenticationPrincipal autenticado: UsuarioAutenticado,
    ): PerfilResponse = servicio.actualizarMiCuenta(autenticado, peticion)
}
