import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/modelos_auth.dart';
import '../api/modelos_aula.dart';
import '../api/proveedores.dart';
import '../api/sesion.dart';
import 'enlace_de_curso.dart';

const _dorado = Color(0xFFD4AF37);
const _superficie = Color(0xFF151515);

/// Los cursos que imparte el profesor, contra la API.
class CursosProfesorScreen extends ConsumerStatefulWidget {
  const CursosProfesorScreen({super.key});

  @override
  ConsumerState<CursosProfesorScreen> createState() =>
      _CursosProfesorScreenState();
}

class _CursosProfesorScreenState extends ConsumerState<CursosProfesorScreen> {
  List<Curso> _cursos = const [];
  bool _cargando = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  Future<void> _cargar() async {
    setState(() => _error = null);
    try {
      final cursos = await ref.read(repositorioAulaProvider).cursos();
      if (!mounted) return;
      setState(() {
        _cursos = cursos;
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

  Future<void> _crear() async {
    final creado = await Navigator.push<Curso>(
      context,
      MaterialPageRoute(builder: (_) => const CrearCursoScreen()),
    );
    if (creado == null || !mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          'Curso "${creado.titulo}" creado y asignado a '
          '${creado.alumnosAsignados} alumno${creado.alumnosAsignados == 1 ? '' : 's'}',
        ),
        backgroundColor: _dorado,
        behavior: SnackBarBehavior.floating,
      ),
    );
    _cargar();
  }

  Future<void> _editar(Curso curso) async {
    final editado = await Navigator.push<Curso>(
      context,
      MaterialPageRoute(builder: (_) => CrearCursoScreen(curso: curso)),
    );
    if (editado != null && mounted) _cargar();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(24, 24, 24, 12),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'Mis Cursos',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 24,
                  fontWeight: FontWeight.bold,
                ),
              ),
              FilledButton.icon(
                onPressed: _crear,
                icon: const Icon(Icons.add),
                label: const Text('Crear Curso'),
                style: FilledButton.styleFrom(
                  backgroundColor: _dorado,
                  foregroundColor: Colors.black,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
              ),
            ],
          ),
        ),
        Expanded(child: _cuerpo()),
      ],
    );
  }

  Widget _cuerpo() {
    if (_cargando) {
      return const Center(child: CircularProgressIndicator(color: _dorado));
    }
    if (_error != null) {
      return _PantallaDeError(
        mensaje: _error!,
        alReintentar: () {
          setState(() => _cargando = true);
          _cargar();
        },
      );
    }

    return RefreshIndicator(
      color: _dorado,
      backgroundColor: _superficie,
      onRefresh: _cargar,
      child: _cursos.isEmpty
          ? ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: const [
                SizedBox(height: 80),
                Icon(Icons.menu_book_outlined, color: Colors.white24, size: 56),
                SizedBox(height: 16),
                Text(
                  'Todavía no has creado ningún curso',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Colors.white54, fontSize: 15),
                ),
              ],
            )
          : ListView.builder(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
              itemCount: _cursos.length,
              itemBuilder: (context, i) => _TarjetaDeCurso(
                curso: _cursos[i],
                alEditar: () => _editar(_cursos[i]),
              ),
            ),
    );
  }
}

class _TarjetaDeCurso extends StatelessWidget {
  const _TarjetaDeCurso({required this.curso, required this.alEditar});

  final Curso curso;
  final VoidCallback alEditar;

  @override
  Widget build(BuildContext context) {
    final n = curso.alumnosAsignados ?? 0;
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: _superficie,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: Colors.white10),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  curso.titulo,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
              IconButton(
                tooltip: 'Editar curso',
                onPressed: alEditar,
                icon: const Icon(Icons.edit, color: Colors.white54),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            curso.descripcion,
            maxLines: 3,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              color: Colors.white54,
              fontSize: 13,
              height: 1.4,
            ),
          ),
          if (curso.urlRecurso != null) ...[
            const SizedBox(height: 10),
            EnlaceDeCurso(url: curso.urlRecurso!),
          ],
          const SizedBox(height: 14),
          Row(
            children: [
              const Icon(Icons.group_outlined, color: Colors.white38, size: 18),
              const SizedBox(width: 6),
              Text(
                '$n alumno${n == 1 ? '' : 's'} asignado${n == 1 ? '' : 's'}',
                style: const TextStyle(color: Colors.white54, fontSize: 13),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// Formulario para crear un curso y elegir a qué alumnos del centro se asigna.
///
/// Con [curso] sirve para editarlo: se cambia el contenido (título, descripción y
/// enlace) y los alumnos asignados no se tocan.
class CrearCursoScreen extends ConsumerStatefulWidget {
  const CrearCursoScreen({super.key, this.curso});

  final Curso? curso;

  @override
  ConsumerState<CrearCursoScreen> createState() => _CrearCursoScreenState();
}

class _CrearCursoScreenState extends ConsumerState<CrearCursoScreen> {
  final _formulario = GlobalKey<FormState>();
  final _titulo = TextEditingController();
  final _descripcion = TextEditingController();
  final _url = TextEditingController();

  List<AlumnoDelCentro> _alumnos = const [];
  final Set<int> _elegidos = {};

  bool _cargandoAlumnos = true;
  bool _enviando = false;
  String? _error;

  bool get _editando => widget.curso != null;

  @override
  void initState() {
    super.initState();
    final curso = widget.curso;
    if (curso != null) {
      _titulo.text = curso.titulo;
      _descripcion.text = curso.descripcion;
      _url.text = curso.urlRecurso ?? '';
      _cargandoAlumnos = false;
    } else {
      _cargarAlumnos();
    }
  }

  @override
  void dispose() {
    _titulo.dispose();
    _descripcion.dispose();
    _url.dispose();
    super.dispose();
  }

  Future<void> _cargarAlumnos() async {
    final idColegio = ref.read(sesionProvider).idColegio;
    if (idColegio == null) {
      setState(() {
        _error = 'Esta cuenta no pertenece a ningún centro';
        _cargandoAlumnos = false;
      });
      return;
    }
    try {
      final alumnos = await ref
          .read(repositorioAulaProvider)
          .alumnosDelCentro(idColegio);
      if (!mounted) return;
      setState(() {
        _alumnos = alumnos;
        _cargandoAlumnos = false;
      });
    } on ErrorApi catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.mensaje;
        _cargandoAlumnos = false;
      });
    }
  }

  bool get _todosElegidos =>
      _alumnos.isNotEmpty && _elegidos.length == _alumnos.length;

  Future<void> _publicar() async {
    if (!_formulario.currentState!.validate()) return;
    if (_editando) return _guardarCambios();
    if (_elegidos.isEmpty) {
      setState(() => _error = 'Elige al menos un alumno');
      return;
    }

    setState(() {
      _enviando = true;
      _error = null;
    });
    try {
      final curso = await ref
          .read(repositorioAulaProvider)
          .crearCurso(
            titulo: _titulo.text,
            descripcion: _descripcion.text,
            urlRecurso: _url.text,
            idsAlumnos: _elegidos.toList(),
          );
      if (mounted) Navigator.pop(context, curso);
    } on ErrorApi catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.mensaje;
        _enviando = false;
      });
    }
  }

  Future<void> _guardarCambios() async {
    setState(() {
      _enviando = true;
      _error = null;
    });
    try {
      final curso = await ref
          .read(repositorioAulaProvider)
          .actualizarCurso(
            id: widget.curso!.id,
            titulo: _titulo.text,
            descripcion: _descripcion.text,
            urlRecurso: _url.text,
          );
      if (mounted) Navigator.pop(context, curso);
    } on ErrorApi catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.mensaje;
        _enviando = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_editando ? 'Editar Curso' : 'Crear Nuevo Curso'),
      ),
      body: Form(
        key: _formulario,
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            _campo(
              controlador: _titulo,
              etiqueta: 'Título del curso',
              icono: Icons.title,
              maximo: 150,
              validador: (v) =>
                  (v ?? '').trim().isEmpty ? 'Ponle un título' : null,
            ),
            const SizedBox(height: 16),
            _campo(
              controlador: _descripcion,
              etiqueta: 'Descripción',
              icono: Icons.description,
              lineas: 4,
              maximo: 5000,
              validador: (v) =>
                  (v ?? '').trim().isEmpty ? 'Describe el curso' : null,
            ),
            const SizedBox(height: 16),
            _campo(
              controlador: _url,
              etiqueta: 'Enlace a vídeo o documento (opcional)',
              icono: Icons.link,
              maximo: 500,
              teclado: TextInputType.url,
              validador: (v) {
                final texto = (v ?? '').trim();
                if (texto.isEmpty) return null;
                // La misma regla que aplica el servidor.
                return RegExp(r'^https?://\S+$').hasMatch(texto)
                    ? null
                    : 'Tiene que empezar por http:// o https://';
              },
            ),
            if (!_editando) ...[
              const SizedBox(height: 32),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'Asignar a alumnos',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  if (_alumnos.isNotEmpty)
                    TextButton(
                      onPressed: () => setState(() {
                        if (_todosElegidos) {
                          _elegidos.clear();
                        } else {
                          _elegidos.addAll(_alumnos.map((a) => a.id));
                        }
                      }),
                      child: Text(
                        _todosElegidos ? 'Quitar todos' : 'Todos',
                        style: const TextStyle(color: _dorado),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 8),
              _selectorDeAlumnos(),
            ],
            if (_error != null) ...[
              const SizedBox(height: 16),
              Text(_error!, style: const TextStyle(color: Colors.redAccent)),
            ],
            const SizedBox(height: 32),
            SizedBox(
              height: 55,
              child: FilledButton(
                onPressed: _enviando ? null : _publicar,
                style: FilledButton.styleFrom(
                  backgroundColor: _dorado,
                  foregroundColor: Colors.black,
                  disabledBackgroundColor: Colors.white10,
                ),
                child: _enviando
                    ? const SizedBox(
                        width: 22,
                        height: 22,
                        child: CircularProgressIndicator(
                          strokeWidth: 2.5,
                          color: Colors.black,
                        ),
                      )
                    : Text(
                        _editando
                            ? 'Guardar cambios'
                            : _elegidos.isEmpty
                            ? 'Publicar curso'
                            : 'Publicar y asignar a ${_elegidos.length}',
                        style: const TextStyle(fontWeight: FontWeight.bold),
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _selectorDeAlumnos() {
    if (_cargandoAlumnos) {
      return const Padding(
        padding: EdgeInsets.all(16),
        child: Center(child: CircularProgressIndicator(color: _dorado)),
      );
    }
    if (_alumnos.isEmpty) {
      return const Text(
        'Tu centro todavía no tiene alumnado dado de alta',
        style: TextStyle(color: Colors.white54),
      );
    }
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        for (final a in _alumnos)
          FilterChip(
            label: Text(a.nombreCompleto),
            selected: _elegidos.contains(a.id),
            onSelected: (si) => setState(
              () => si ? _elegidos.add(a.id) : _elegidos.remove(a.id),
            ),
            backgroundColor: _superficie,
            selectedColor: _dorado,
            checkmarkColor: Colors.black,
            labelStyle: TextStyle(
              color: _elegidos.contains(a.id) ? Colors.black : Colors.white,
              fontSize: 13,
            ),
          ),
      ],
    );
  }

  Widget _campo({
    required TextEditingController controlador,
    required String etiqueta,
    required IconData icono,
    required int maximo,
    String? Function(String?)? validador,
    int lineas = 1,
    TextInputType? teclado,
  }) {
    return TextFormField(
      controller: controlador,
      maxLines: lineas,
      maxLength: maximo,
      keyboardType: teclado,
      validator: validador,
      style: const TextStyle(color: Colors.white),
      decoration: InputDecoration(
        labelText: etiqueta,
        labelStyle: const TextStyle(color: Colors.white54),
        counterText: '',
        prefixIcon: Icon(icono, color: _dorado),
        filled: true,
        fillColor: _superficie,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide.none,
        ),
      ),
    );
  }
}

class _PantallaDeError extends StatelessWidget {
  const _PantallaDeError({required this.mensaje, required this.alReintentar});

  final String mensaje;
  final VoidCallback alReintentar;

  @override
  Widget build(BuildContext context) => Center(
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
            onPressed: alReintentar,
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
