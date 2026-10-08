import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/modelos_auth.dart';
import '../api/modelos_aula.dart';
import '../api/proveedores.dart';
import 'enlace_de_curso.dart';

const _dorado = Color(0xFFD4AF37);
const _superficie = Color(0xFF151515);

/// Los cursos que el profesorado ha asignado al alumno, con su progreso.
class LearningScreen extends ConsumerStatefulWidget {
  const LearningScreen({super.key});

  @override
  ConsumerState<LearningScreen> createState() => _LearningScreenState();
}

class _LearningScreenState extends ConsumerState<LearningScreen> {
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

  @override
  Widget build(BuildContext context) {
    if (_cargando) return const Center(child: CircularProgressIndicator(color: _dorado));
    if (_error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.cloud_off, color: Colors.white24, size: 56),
              const SizedBox(height: 16),
              Text(_error!, textAlign: TextAlign.center, style: const TextStyle(color: Colors.white54)),
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
                  'Todavía no tienes cursos asignados.\nTu profesor te los asignará desde su cuenta.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Colors.white54, fontSize: 15),
                ),
              ],
            )
          : ListView.builder(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.all(16),
              itemCount: _cursos.length,
              itemBuilder: (context, i) => _TarjetaDeCurso(curso: _cursos[i]),
            ),
    );
  }
}

class _TarjetaDeCurso extends StatelessWidget {
  const _TarjetaDeCurso({required this.curso});

  final Curso curso;

  @override
  Widget build(BuildContext context) {
    final progreso = (curso.progreso ?? 0) / 100;
    final completado = curso.completado;

    return Container(
      margin: const EdgeInsets.only(bottom: 20),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: _superficie,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.white10),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(curso.titulo,
                    style: const TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold)),
              ),
              Icon(
                completado ? Icons.workspace_premium : Icons.play_circle_fill,
                color: completado ? Colors.amber : _dorado,
                size: 32,
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text('Con ${curso.profesor}', style: const TextStyle(color: _dorado, fontSize: 13)),
          const SizedBox(height: 10),
          Text(curso.descripcion, style: const TextStyle(color: Colors.white60, fontSize: 13, height: 1.4)),
          if (curso.urlRecurso != null) ...[
            const SizedBox(height: 12),
            EnlaceDeCurso(url: curso.urlRecurso!),
          ],
          const SizedBox(height: 16),
          ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: LinearProgressIndicator(
              value: progreso.clamp(0.0, 1.0),
              backgroundColor: Colors.white10,
              valueColor: AlwaysStoppedAnimation<Color>(completado ? Colors.green : _dorado),
              minHeight: 12,
            ),
          ),
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                completado ? 'Curso completado 🎉' : (progreso == 0 ? 'Sin empezar' : 'En progreso...'),
                style: TextStyle(
                  fontWeight: FontWeight.w600,
                  color: completado ? Colors.green : Colors.white54,
                ),
              ),
              Text('${(progreso * 100).round()}%',
                  style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16)),
            ],
          ),
        ],
      ),
    );
  }
}
