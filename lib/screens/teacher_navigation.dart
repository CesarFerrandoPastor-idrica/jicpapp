import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/sesion.dart';
import 'cursos_profesor_screen.dart';
import 'editar_cuenta_screen.dart';
import 'market_screen.dart';
import 'ranking_screen.dart';
import 'welcome_screen.dart';

const _dorado = Color(0xFFD4AF37);
const _superficie = Color(0xFF151515);

/// Navegación del profesorado: mercado de su centro, sus cursos, el ranking de su
/// alumnado y su perfil. Todo contra la API.
class TeacherNavigation extends ConsumerStatefulWidget {
  const TeacherNavigation({super.key});

  @override
  ConsumerState<TeacherNavigation> createState() => _TeacherNavigationState();
}

class _TeacherNavigationState extends ConsumerState<TeacherNavigation> {
  int _selectedIndex = 0;
  bool _isRankingSearchActive = false;
  String _rankingSearchQuery = '';

  final List<String> _titles = [
    'Proyectos de mi centro',
    'Gestión de Cursos',
    'Ranking de Alumnos',
    'Perfil Profesor'
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: (_selectedIndex == 2 && _isRankingSearchActive)
            ? TextField(
                autofocus: true,
                style: const TextStyle(color: Colors.white),
                decoration: const InputDecoration(
                  hintText: 'Buscar alumno...',
                  hintStyle: TextStyle(color: Colors.white38),
                  border: InputBorder.none,
                ),
                onChanged: (value) => setState(() => _rankingSearchQuery = value),
              )
            : Text(_selectedIndex == 3 ? '' : _titles[_selectedIndex]),
        centerTitle: true,
        actions: [
          if (_selectedIndex == 2)
            IconButton(
              icon: Icon(_isRankingSearchActive ? Icons.close : Icons.search, color: _dorado),
              onPressed: () => setState(() {
                _isRankingSearchActive = !_isRankingSearchActive;
                if (!_isRankingSearchActive) _rankingSearchQuery = '';
              }),
            ),
          if (_selectedIndex == 3)
            PopupMenuButton<String>(
              icon: const Icon(Icons.more_vert, color: _dorado, size: 28),
              color: _superficie,
              onSelected: (value) {
                if (value == 'logout') _cerrarSesion(context, ref);
                if (value == 'editar') {
                  Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => const EditarCuentaScreen()),
                  );
                }
              },
              itemBuilder: (context) => const [
                PopupMenuItem(
                  value: 'editar',
                  child: Row(
                    children: [
                      Icon(Icons.edit, color: Colors.white, size: 20),
                      SizedBox(width: 12),
                      Text('Editar perfil', style: TextStyle(color: Colors.white)),
                    ],
                  ),
                ),
                PopupMenuItem(
                  value: 'logout',
                  child: Row(
                    children: [
                      Icon(Icons.logout, color: Colors.redAccent, size: 20),
                      SizedBox(width: 12),
                      Text('Cerrar Sesión', style: TextStyle(color: Colors.redAccent)),
                    ],
                  ),
                ),
              ],
            ),
        ],
      ),
      body: [
        const MarketScreen(),
        const CursosProfesorScreen(),
        RankingScreen(busqueda: _rankingSearchQuery),
        const _PerfilProfesor(),
      ][_selectedIndex],
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.only(bottom: 8.0, left: 16.0, right: 16.0),
          child: Container(
            decoration: BoxDecoration(
              color: _superficie,
              borderRadius: BorderRadius.circular(24),
              border: Border.all(color: _dorado.withValues(alpha: 0.3)),
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(24),
              child: BottomNavigationBar(
                backgroundColor: Colors.transparent,
                type: BottomNavigationBarType.fixed,
                elevation: 0,
                selectedItemColor: _dorado,
                unselectedItemColor: Colors.white54,
                iconSize: 28,
                showSelectedLabels: false,
                showUnselectedLabels: false,
                currentIndex: _selectedIndex,
                onTap: (index) => setState(() => _selectedIndex = index),
                items: const [
                  BottomNavigationBarItem(icon: Icon(Icons.search), label: 'Mercado'),
                  BottomNavigationBarItem(icon: Icon(Icons.menu_book), label: 'Cursos'),
                  BottomNavigationBarItem(icon: Icon(Icons.emoji_events), label: 'Ranking'),
                  BottomNavigationBarItem(icon: Icon(Icons.person), label: 'Perfil'),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Perfil del profesor con lo que devolvió `GET /yo` al entrar.
class _PerfilProfesor extends ConsumerWidget {
  const _PerfilProfesor();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final perfil = ref.watch(sesionProvider).perfil;
    final profesor = perfil?.profesor;
    final iniciales = profesor == null
        ? '?'
        : '${profesor.nombre.characters.first}${profesor.apellido.characters.first}'.toUpperCase();

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            CircleAvatar(
              radius: 64,
              backgroundColor: _dorado,
              child: Text(iniciales,
                  style: const TextStyle(color: Colors.black, fontSize: 40, fontWeight: FontWeight.bold)),
            ),
            const SizedBox(height: 24),
            Text(profesor?.nombreCompleto ?? perfil?.email ?? '',
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.white, fontSize: 28, fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            if (profesor != null)
              Text(profesor.nombreColegio,
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: _dorado, fontSize: 18)),
            if (perfil != null) ...[
              const SizedBox(height: 4),
              Text(perfil.email, style: const TextStyle(color: Colors.white38, fontSize: 14)),
            ],
            const SizedBox(height: 32),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
              decoration: BoxDecoration(color: _superficie, borderRadius: BorderRadius.circular(16)),
              child: const Text('Profesor', style: TextStyle(color: Colors.greenAccent, fontWeight: FontWeight.bold)),
            ),
          ],
        ),
      ),
    );
  }
}

/// Cierra la sesion contra el servidor y vuelve a la pantalla de bienvenida.
///
/// Revocar el refresh token en el backend es lo que de verdad cierra la sesion:
/// borrar los tokens del movil sin avisar dejaria uno valido durante 30 dias.
Future<void> _cerrarSesion(BuildContext context, WidgetRef ref) async {
  final navegador = Navigator.of(context);
  await ref.read(sesionProvider.notifier).salir();
  navegador.pushAndRemoveUntil(
    MaterialPageRoute(builder: (context) => const WelcomeScreen()),
    (route) => false,
  );
}
