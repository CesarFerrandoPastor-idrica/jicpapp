package com.jicp.api.proyecto

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** Catalogo de categorias. Coincide con los filtros del mercado en la app Flutter. */
@Entity
@Table(name = "categoria_proyecto")
class CategoriaProyecto(

    @Column(name = "nombre", nullable = false, length = 100, unique = true)
    var nombre: String,

    @Column(name = "descripcion", columnDefinition = "text")
    var descripcion: String? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_categoria")
    var id: Int? = null,
)
