import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/modelos_auth.dart';
import '../api/modelos_proyecto.dart';
import '../api/proveedores.dart';
import '../api/sesion.dart';
import 'ficha_proyecto_screen.dart';

const _dorado = Color(0xFFD4AF37);
const _superficie = Color(0xFF151515);

/// Mercado del alumno: los proyectos publicados de su centro.
///
/// La lista se pide entera una vez y el buscador y las categorías filtran en
/// local. Las categorías vienen del servidor, igual que en Crear proyecto, para
/// que el filtro no se desincronice del catálogo de la base.
class MarketScreen extends ConsumerStatefulWidget {
  const MarketScreen({super.key});

  @override
  ConsumerState<MarketScreen> createState() => _MarketScreenState();
}

class _MarketScreenState extends ConsumerState<MarketScreen> {
  List<ProyectoDeMercado> _proyectos = const [];
  List<Catalogo> _categorias = const [];

  /// null = todas.
  int? _idCategoria;
  String _busqueda = '';

  bool _cargando = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  Future<void> _cargar() async {
    final idColegio = ref.read(sesionProvider).alumno?.idColegio;
    if (idColegio == null) {
      setState(() {
        _error = 'Esta pantalla es del alumnado';
        _cargando = false;
      });
      return;
    }

    setState(() => _error = null);
    try {
      final resultados = await Future.wait([
        ref.read(repositorioProyectosProvider).mercado(idColegio: idColegio),
        ref.read(repositorioProyectosProvider).categorias(),
      ]);
      if (!mounted) return;
      setState(() {
        _proyectos = resultados[0] as List<ProyectoDeMercado>;
        _categorias = resultados[1] as List<Catalogo>;
        _cargando = false;
      });
    } on ErrorApi catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.mensaje;
        _cargando = false;
      });
    }
  }

  List<ProyectoDeMercado> get _filtrados {
    final texto = _busqueda.trim().toLowerCase();
    return _proyectos.where((p) {
      final encajaCategoria = _idCategoria == null || p.idCategoria == _idCategoria;
      final encajaTexto = texto.isEmpty || p.nombre.toLowerCase().contains(texto);
      return encajaCategoria && encajaTexto;
    }).toList();
  }

  Future<void> _abrir(ProyectoDeMercado proyecto) async {
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => FichaProyectoScreen(proyecto: proyecto)),
    );
    // Al volver se relee: si ha comprado, el precio y la ronda ya no son los de antes.
    if (mounted) _cargar();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: TextField(
            onChanged: (valor) => setState(() => _busqueda = valor),
            style: const TextStyle(color: Colors.white),
            decoration: InputDecoration(
              hintText: 'Buscar por nombre del proyecto...',
              hintStyle: const TextStyle(color: Colors.white38),
              prefixIcon: const Icon(Icons.search, color: _dorado),
              filled: true,
              fillColor: _superficie,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(20),
                borderSide: BorderSide.none,
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(20),
                borderSide: const BorderSide(color: Colors.white12),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(20),
                borderSide: const BorderSide(color: _dorado),
              ),
            ),
          ),
        ),
        SizedBox(
          height: 60,
          child: ListView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            children: [
              _chip('Todos', null),
              for (final c in _categorias) _chip(c.nombre, c.id),
            ],
          ),
        ),
        Expanded(child: _cuerpo()),
      ],
    );
  }

  Widget _chip(String texto, int? idCategoria) {
    final seleccionado = _idCategoria == idCategoria;
    return GestureDetector(
      onTap: () => setState(() => _idCategoria = idCategoria),
      child: Container(
        margin: const EdgeInsets.only(right: 8),
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
        decoration: BoxDecoration(
          color: seleccionado ? _dorado : _superficie,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: seleccionado ? _dorado : Colors.white24),
        ),
        alignment: Alignment.center,
        child: Text(
          texto,
          style: TextStyle(
            color: seleccionado ? Colors.black : Colors.white70,
            fontWeight: seleccionado ? FontWeight.bold : FontWeight.w500,
          ),
        ),
      ),
    );
  }

  Widget _cuerpo() {
    if (_cargando) {
      return const Center(child: CircularProgressIndicator(color: _dorado));
    }
    if (_error != null) {
      return _pantallaDeError(_error!);
    }

    final proyectos = _filtrados;
    if (proyectos.isEmpty) {
      return _vacio(
        _proyectos.isEmpty
            ? 'Todavía no hay proyectos publicados en tu centro'
            : 'Ningún proyecto encaja con la búsqueda',
      );
    }

    final idAlumno = ref.watch(sesionProvider).alumno?.id;
    return RefreshIndicator(
      color: _dorado,
      backgroundColor: _superficie,
      onRefresh: _cargar,
      child: LayoutBuilder(
        builder: (context, constraints) {
          var columnas = 1;
          if (constraints.maxWidth >= 1200) {
            columnas = 4;
          } else if (constraints.maxWidth >= 800) {
            columnas = 3;
          } else if (constraints.maxWidth >= 600) {
            columnas = 2;
          }

          return GridView.builder(
            padding: const EdgeInsets.all(16),
            physics: const AlwaysScrollableScrollPhysics(),
            gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: columnas,
              crossAxisSpacing: 16,
              mainAxisSpacing: 16,
              mainAxisExtent: 280,
            ),
            itemCount: proyectos.length,
            itemBuilder: (context, i) => _TarjetaDeProyecto(
              proyecto: proyectos[i],
              esMio: proyectos[i].idCreador == idAlumno,
              onTap: () => _abrir(proyectos[i]),
            ),
          );
        },
      ),
    );
  }

  Widget _vacio(String texto) => RefreshIndicator(
        color: _dorado,
        backgroundColor: _superficie,
        onRefresh: _cargar,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [
            const SizedBox(height: 80),
            const Icon(Icons.storefront_outlined, color: Colors.white24, size: 56),
            const SizedBox(height: 16),
            Text(
              texto,
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.white54, fontSize: 15),
            ),
          ],
        ),
      );

  Widget _pantallaDeError(String mensaje) => Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.cloud_off, color: Colors.white24, size: 56),
              const SizedBox(height: 16),
              Text(
                mensaje,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.white54, fontSize: 14),
              ),
              const SizedBox(height: 24),
              OutlinedButton.icon(
                onPressed: () {
                  setState(() => _cargando = true);
                  _cargar();
                },
                icon: const Icon(Icons.refresh),
                label: const Text('Reintentar'),
                style: OutlinedButton.styleFrom(
                  foregroundColor: _dorado,
                  side: const BorderSide(color: _dorado),
                ),
              ),
            ],
          ),
        ),
      );
}

class _TarjetaDeProyecto extends StatelessWidget {
  const _TarjetaDeProyecto({
    required this.proyecto,
    required this.esMio,
    required this.onTap,
  });

  final ProyectoDeMercado proyecto;
  final bool esMio;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final p = proyecto;
    return Material(
      color: const Color(0xFF111111),
      borderRadius: BorderRadius.circular(24),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(24),
            border: Border.all(
              color: esMio ? _dorado.withValues(alpha: 0.35) : Colors.white12,
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: _dorado.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(iconoDeCategoria(p.categoria), color: _dorado),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          p.nombre,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 17,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          esMio ? 'Tu proyecto' : 'Por: ${p.nombreCreador ?? '—'}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: _dorado,
                            fontSize: 12,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Expanded(
                child: Text(
                  p.descripcion,
                  maxLines: 4,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: Colors.white60, fontSize: 12, height: 1.4),
                ),
              ),
              const SizedBox(height: 12),
              ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: LinearProgressIndicator(
                  value: p.progresoDeRonda,
                  minHeight: 5,
                  backgroundColor: Colors.white10,
                  valueColor: const AlwaysStoppedAnimation(_dorado),
                ),
              ),
              const SizedBox(height: 6),
              Text(
                '${(p.progresoDeRonda * 100).toStringAsFixed(0)}% de la ronda colocada',
                style: const TextStyle(color: Colors.white38, fontSize: 11),
              ),
              const SizedBox(height: 12),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('Precio participación',
                          style: TextStyle(color: Colors.white38, fontSize: 10)),
                      Text(
                        '${p.precioOrientativo.toStringAsFixed(2)} JICP',
                        style: const TextStyle(
                            color: Colors.white, fontWeight: FontWeight.w900),
                      ),
                    ],
                  ),
                  FilledButton(
                    onPressed: onTap,
                    style: FilledButton.styleFrom(
                      backgroundColor: _dorado,
                      foregroundColor: Colors.black,
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12)),
                      padding: const EdgeInsets.symmetric(horizontal: 24),
                    ),
                    child: const Text('Abrir',
                        style: TextStyle(fontWeight: FontWeight.bold)),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Icono para cada categoría del catálogo. Si aparece una nueva, cae en el genérico.
IconData iconoDeCategoria(String categoria) => switch (categoria) {
      'Tecnología' => Icons.memory,
      'Salud' => Icons.favorite_outline,
      'Finanzas' => Icons.account_balance_outlined,
      'Educación' => Icons.school_outlined,
      'Media' => Icons.movie_outlined,
      _ => Icons.rocket_launch_outlined,
    };
