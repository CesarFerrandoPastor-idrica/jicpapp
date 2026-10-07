package com.jicp.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
class CriptografiaConfig {

    /**
     * Las contrasenas se guardan siempre hasheadas con BCrypt, nunca en claro
     * ni cifradas de forma reversible.
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
