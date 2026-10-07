package com.jicp.api.alumno

import com.jicp.api.colegio.ColegioService
import com.jicp.api.contabilidad.ContabilidadService
import com.jicp.api.seguridad.Rol
import com.jicp.api.seguridad.Usuario
import com.jicp.api.seguridad.UsuarioRepository
import com.jicp.api.shared.ConflictoException
import com.jicp.api.shared.RecursoNoEncontradoException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AlumnoService(
    private val repositorio: AlumnoRepository,
    private val usuarios: UsuarioRepository,
    private val colegios: ColegioService,
    private val contabilidad: ContabilidadService,
    private val passwordEncoder: PasswordEncoder,
) {

    fun listar(idColegio: Int?, pageable: Pageable): Page<AlumnoResponse> {
        val pagina = if (idColegio == null) {
            repositorio.findAll(pageable)
        } else {
            repositorio.findByColegioId(idColegio, pageable)
        }
        return pagina.map { it.toResponse() }
    }

    fun obtener(id: Int): AlumnoResponse = buscarOFallar(id).toResponse()

    @Transactional
    fun crear(peticion: CrearAlumnoRequest): AlumnoResponse {
        // En minusculas para que el login no dependa de como escriba el email quien lo teclea.
        val email = peticion.email.trim().lowercase()

        // El email es unico en todo el sistema, no solo entre alumnos: la comprobacion va
        // contra usuario. El indice unico de la tabla sigue siendo la garantia real.
        if (usuarios.existsByEmail(email)) {
            throw ConflictoException("Ya existe un usuario registrado con el email $email")
        }

        val colegio = colegios.buscarOFallar(peticion.idColegio)

        val alumno = Alumno(
            nombre = peticion.nombre.trim(),
            apellido = peticion.apellido.trim(),
            usuario = Usuario(
                email = email,
                passwordHash = passwordEncoder.encode(peticion.password),
                rol = Rol.ALUMNO,
            ),
            colegio = colegio,
            jicpInicial = peticion.jicpInicial,
        )
        val guardado = repositorio.save(alumno)

        // La cartera nace con el alumno, y su saldo entra por el libro contable: la
        // concesion inicial se apunta contra la cuenta de emision en lugar de escribir un
        // numero a mano. Asi el dinero de la clase cuadra a cero desde la primera fila.
        contabilidad.abrirCartera(guardado)
        contabilidad.concederSaldoInicial(guardado, peticion.jicpInicial)

        return guardado.toResponse()
    }

    @Transactional
    fun actualizar(id: Int, peticion: ActualizarAlumnoRequest): AlumnoResponse {
        val alumno = buscarOFallar(id)
        alumno.nombre = peticion.nombre.trim()
        alumno.apellido = peticion.apellido.trim()
        return alumno.toResponse()
    }

    fun buscarOFallar(id: Int): Alumno =
        repositorio.findById(id).orElseThrow { RecursoNoEncontradoException("alumno", id) }
}
