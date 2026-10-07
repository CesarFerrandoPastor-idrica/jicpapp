package com.jicp.api.seguridad

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * El actor de la peticion, reconstruido a partir del token firmado.
 *
 * Es lo que reciben los controladores con `@AuthenticationPrincipal`, y la unica fuente
 * valida de "quien esta pidiendo esto": el id que venga en el body o en la query se ignora.
 */
data class UsuarioAutenticado(
    val idUsuario: Int,
    val email: String,
    val rol: Rol,
) {
    /** Con el prefijo ROLE_ que espera `hasRole(...)` en las anotaciones @PreAuthorize. */
    val autorizaciones: List<GrantedAuthority>
        get() = listOf(SimpleGrantedAuthority("ROLE_${rol.name}"))
}
