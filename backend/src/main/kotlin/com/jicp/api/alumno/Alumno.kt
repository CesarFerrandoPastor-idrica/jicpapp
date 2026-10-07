package com.jicp.api.alumno

import com.jicp.api.colegio.Colegio
import com.jicp.api.seguridad.Usuario
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "alumno")
class Alumno(

    @Column(name = "nombre", nullable = false, length = 100)
    var nombre: String,

    @Column(name = "apellido", nullable = false, length = 150)
    var apellido: String,

    /**
     * Credenciales y rol. El email y la contrasena viven en usuario, no aqui: es el mismo
     * login que usara el profesorado, asi que duplicarlos obligaria a buscar en dos tablas
     * y permitiria que un alumno y un profesor compartiesen email.
     *
     * CascadeType.PERSIST para que dar de alta a un alumno cree su usuario en la misma
     * transaccion; sin REMOVE, porque borrar un alumno no debe borrar su rastro de acceso.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false, cascade = [CascadeType.PERSIST])
    @JoinColumn(name = "id_usuario", nullable = false, unique = true)
    var usuario: Usuario,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_colegio", nullable = false)
    var colegio: Colegio,

    /**
     * Concesion inicial de JICP al darse de alta.
     * NO es el saldo actual: cuando exista el libro contable, el saldo sera la suma
     * de los apuntes y esta columna quedara como el importe del INITIAL_GRANT.
     */
    @Column(name = "jicp_inicial", nullable = false, precision = 12, scale = 2)
    var jicpInicial: BigDecimal = BigDecimal.ZERO,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_alumno")
    var id: Int? = null,
) {
    @CreationTimestamp
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    var fechaRegistro: LocalDateTime = LocalDateTime.now()
}
