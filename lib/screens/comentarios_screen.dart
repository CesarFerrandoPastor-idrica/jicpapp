import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/modelos_auth.dart';
import '../api/modelos_proyecto.dart';
import '../api/proveedores.dart';
import '../api/sesion.dart';

const _dorado = Color(0xFFD4AF37);
const _superficie = Color(0xFF151515);

/// Hilo de comentarios de un proyecto, contra la API.
///
/// Se lee por páginas, de lo más reciente a lo más antiguo, y se pide la
/// siguiente al llegar al final de la lista. Lo que se publica aparece arriba
/// tal y como lo devuelve el servidor: no se pinta nada que no haya guardado.
class ComentariosScreen extends ConsumerStatefulWidget {
  const ComentariosScreen({
    super.key,
    required this.idProyecto,
    required this.nombreProyecto,
  });

  final int idProyecto;
  final String nombreProyecto;

  @override
  ConsumerState<ComentariosScreen> createState() => _ComentariosScreenState();
}

class _ComentariosScreenState extends ConsumerState<ComentariosScreen> {
  static const _tamanoDePagina = 20;

  /// El mismo límite que valida el servidor.
  static const _maximoCaracteres = 2000;

  final _texto = TextEditingController();
  final _scroll = ScrollController();

  final List<Comentario> _comentarios = [];
  int _paginaSiguiente = 0;
  bool _hayMas = true;

  bool _cargando = true;
  bool _cargandoMas = false;
  String? _error;

  bool _publicando = false;

  @override
  void initState() {
    super.initState();
    _texto.addListener(() => setState(() {}));
    _scroll.addListener(_alHacerScroll);
    _cargar();
  }

  @override
  void dispose() {
    _texto.dispose();
    _scroll.dispose();
    super.dispose();
  }

  /// Empieza el hilo desde el principio. Se usa al abrir y al deslizar hacia abajo.
  Future<void> _cargar() async {
    setState(() => _error = null);
    try {
      final pagina = await ref
          .read(repositorioProyectosProvider)
          .comentarios(widget.idProyecto, tamano: _tamanoDePagina);
      if (!mounted) return;
      setState(() {
        _comentarios
          ..clear()
          ..addAll(pagina.comentarios);
        _paginaSiguiente = 1;
        _hayMas = !pagina.esLaUltima;
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

  void _alHacerScroll() {
    if (_scroll.position.extentAfter < 300) _cargarMas();
  }

  Future<void> _cargarMas() async {
    if (!_hayMas || _cargandoMas || _cargando) return;
    setState(() => _cargandoMas = true);
    try {
      final pagina = await ref.read(repositorioProyectosProvider).comentarios(
            widget.idProyecto,
            pagina: _paginaSiguiente,
            tamano: _tamanoDePagina,
          );
      if (!mounted) return;
      setState(() {
        // Si alguien comentó mientras se leía, la paginación se desplaza y la
        // página siguiente repite comentarios ya pintados. Se descartan por id.
        final yaEstan = _comentarios.map((c) => c.id).toSet();
        _comentarios.addAll(pagina.comentarios.where((c) => !yaEstan.contains(c.id)));
        _paginaSiguiente++;
        _hayMas = !pagina.esLaUltima;
        _cargandoMas = false;
      });
    } on ErrorApi catch (e) {
      if (!mounted) return;
      setState(() => _cargandoMas = false);
      _avisar(e.mensaje);
    }
  }

  bool get _sePuedePublicar => !_publicando && _texto.text.trim().isNotEmpty;

  Future<void> _publicar() async {
    if (!_sePuedePublicar) return;
    setState(() => _publicando = true);
    try {
      final nuevo = await ref
          .read(repositorioProyectosProvider)
          .comentar(widget.idProyecto, _texto.text);
      if (!mounted) return;
      setState(() {
        _comentarios.insert(0, nuevo);
        _publicando = false;
      });
      _texto.clear();
      FocusScope.of(context).unfocus();
      if (_scroll.hasClients) _scroll.jumpTo(0);
    } on ErrorApi catch (e) {
      // El texto se conserva: que el alumno no pierda lo que ha escrito.
      if (!mounted) return;
      setState(() => _publicando = false);
      _avisar(e.mensaje);
    }
  }

  void _avisar(String mensaje) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(mensaje), behavior: SnackBarBehavior.floating),
    );
  }

  @override
  Widget build(BuildContext context) {
    final sesion = ref.watch(sesionProvider);

    return Scaffold(
      backgroundColor: const Color(0xFF0F0F0F),
      appBar: AppBar(
        backgroundColor: const Color(0xFF0F0F0F),
        title: Text(
          'Comentarios: ${widget.nombreProyecto}',
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(color: Colors.white, fontSize: 18),
        ),
      ),
      body: Column(
        children: [
          Expanded(child: _hilo(sesion.alumno?.id)),
          // Solo el alumnado comenta: el servidor responde 403 a cualquier otro rol.
          if (sesion.rol == Rol.alumno) _cajaDeTexto(),
        ],
      ),
    );
  }

  Widget _hilo(int? miIdAlumno) {
    if (_cargando) {
      return const Center(child: CircularProgressIndicator(color: _dorado));
    }
    if (_error != null) return _pantallaDeError(_error!);

    return RefreshIndicator(
      color: _dorado,
      backgroundColor: _superficie,
      onRefresh: _cargar,
      child: _comentarios.isEmpty
          ? ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: const [
                SizedBox(height: 80),
                Icon(Icons.forum_outlined, color: Colors.white24, size: 56),
                SizedBox(height: 16),
                Text(
                  'Todavía no hay comentarios.\n¡Sé el primero!',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Colors.white54, fontSize: 15),
                ),
              ],
            )
          : ListView.builder(
              controller: _scroll,
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.all(16),
              itemCount: _comentarios.length + (_hayMas ? 1 : 0),
              itemBuilder: (context, i) {
                if (i == _comentarios.length) {
                  return const Padding(
                    padding: EdgeInsets.all(16),
                    child: Center(
                      child: CircularProgressIndicator(color: _dorado, strokeWidth: 2),
                    ),
                  );
                }
                final c = _comentarios[i];
                return _TarjetaDeComentario(
                  comentario: c,
                  esMio: c.idAlumno == miIdAlumno,
                );
              },
            ),
    );
  }

  Widget _cajaDeTexto() => Container(
        padding: EdgeInsets.fromLTRB(
            16, 12, 16, MediaQuery.of(context).padding.bottom + 12),
        decoration: const BoxDecoration(
          color: _superficie,
          border: Border(top: BorderSide(color: Colors.white10)),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Expanded(
              child: TextField(
                controller: _texto,
                enabled: !_publicando,
                minLines: 1,
                maxLines: 4,
                maxLength: _maximoCaracteres,
                textCapitalization: TextCapitalization.sentences,
                style: const TextStyle(color: Colors.white),
                decoration: InputDecoration(
                  hintText: 'Escribe un comentario...',
                  hintStyle: const TextStyle(color: Colors.white24),
                  // El contador solo estorba hasta que se acerca al límite.
                  counterText: _texto.text.length > _maximoCaracteres - 200
                      ? null
                      : '',
                  filled: true,
                  fillColor: Colors.black,
                  contentPadding:
                      const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(24),
                    borderSide: BorderSide.none,
                  ),
                  enabledBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(24),
                    borderSide: const BorderSide(color: Colors.white10),
                  ),
                  focusedBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(24),
                    borderSide: const BorderSide(color: _dorado),
                  ),
                ),
              ),
            ),
            const SizedBox(width: 12),
            Padding(
              padding: const EdgeInsets.only(bottom: 2),
              child: IconButton.filled(
                tooltip: 'Publicar',
                onPressed: _sePuedePublicar ? _publicar : null,
                style: IconButton.styleFrom(
                  backgroundColor: _dorado,
                  foregroundColor: Colors.black,
                  disabledBackgroundColor: Colors.white10,
                ),
                icon: _publicando
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(
                            strokeWidth: 2, color: Colors.black),
                      )
                    : const Icon(Icons.send, size: 20),
              ),
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
              Text(mensaje,
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: Colors.white54, fontSize: 14)),
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

class _TarjetaDeComentario extends StatelessWidget {
  const _TarjetaDeComentario({required this.comentario, required this.esMio});

  final Comentario comentario;
  final bool esMio;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: _superficie,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: esMio ? _dorado.withValues(alpha: 0.35) : Colors.white10,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  esMio ? 'Tú' : comentario.autor,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                      color: _dorado, fontWeight: FontWeight.bold, fontSize: 14),
                ),
              ),
              Text(
                haceCuanto(comentario.fecha, DateTime.now()),
                style: const TextStyle(color: Colors.white38, fontSize: 11),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            comentario.texto,
            style: const TextStyle(color: Colors.white70, fontSize: 14, height: 1.4),
          ),
        ],
      ),
    );
  }
}

/// "Ahora", "hace 5 min", "hace 3 h", "hace 2 días" o la fecha.
///
/// La fecha del servidor no lleva zona horaria. Si el reloj del móvil va algo
/// por detrás, la diferencia sale negativa: se trata como "Ahora" en lugar de
/// pintar "hace -2 min".
String haceCuanto(DateTime fecha, DateTime ahora) {
  final d = ahora.difference(fecha);
  if (d.inMinutes < 1) return 'Ahora';
  if (d.inMinutes < 60) return 'hace ${d.inMinutes} min';
  if (d.inHours < 24) return 'hace ${d.inHours} h';
  if (d.inDays < 7) return d.inDays == 1 ? 'hace 1 día' : 'hace ${d.inDays} días';
  String dos(int n) => n.toString().padLeft(2, '0');
  return '${dos(fecha.day)}/${dos(fecha.month)}/${fecha.year}';
}
