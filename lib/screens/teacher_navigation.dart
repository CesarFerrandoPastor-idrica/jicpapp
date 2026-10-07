import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/sesion.dart';
import '../mock_data.dart';
import 'market_screen.dart';
import 'welcome_screen.dart';

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
    'Explorar Proyectos',
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
              icon: Icon(_isRankingSearchActive ? Icons.close : Icons.search, color: const Color(0xFFD4AF37)),
              onPressed: () => setState(() {
                _isRankingSearchActive = !_isRankingSearchActive;
                if (!_isRankingSearchActive) _rankingSearchQuery = '';
              }),
            ),
          if (_selectedIndex == 3)
            PopupMenuButton<String>(
              icon: const Icon(Icons.more_vert, color: Color(0xFFD4AF37), size: 28),
              color: const Color(0xFF151515),
              onSelected: (value) {
                if (value == 'logout') {
                  _cerrarSesion(context, ref);
                }
              },
              itemBuilder: (context) => [
                const PopupMenuItem(
                  value: 'edit',
                  child: Row(
                    children: [
                      Icon(Icons.edit, color: Colors.white, size: 20),
                      SizedBox(width: 12),
                      Text('Editar Perfil', style: TextStyle(color: Colors.white)),
                    ],
                  ),
                ),
                const PopupMenuItem(
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
        const TeacherMarketScreen(),
        const TeacherCreateCourseScreen(),
        TeacherStudentRankingScreen(searchQuery: _rankingSearchQuery),
        const TeacherProfileScreen(),
      ][_selectedIndex],
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.only(bottom: 8.0, left: 16.0, right: 16.0),
          child: Container(
            decoration: BoxDecoration(
              color: const Color(0xFF151515),
              borderRadius: BorderRadius.circular(24),
              border: Border.all(color: const Color(0xFFD4AF37).withOpacity(0.3)),
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(24),
              child: BottomNavigationBar(
                backgroundColor: Colors.transparent,
                type: BottomNavigationBarType.fixed,
                elevation: 0,
                selectedItemColor: const Color(0xFFD4AF37),
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

class TeacherMarketScreen extends StatefulWidget {
  const TeacherMarketScreen({super.key});

  @override
  State<TeacherMarketScreen> createState() => _TeacherMarketScreenState();
}

class _TeacherMarketScreenState extends State<TeacherMarketScreen> {
  String _searchQuery = '';

  @override
  Widget build(BuildContext context) {
    final projects = MockData.featuredProjects.where((p) => 
      p.title.toLowerCase().contains(_searchQuery.toLowerCase())).toList();
    
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(16),
          child: TextField(
            onChanged: (value) => setState(() => _searchQuery = value),
            style: const TextStyle(color: Colors.white),
            decoration: InputDecoration(
              hintText: 'Buscar proyecto...',
              hintStyle: const TextStyle(color: Colors.white38),
              prefixIcon: const Icon(Icons.search, color: Color(0xFFD4AF37)),
              filled: true,
              fillColor: const Color(0xFF151515),
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: BorderSide.none),
            ),
          ),
        ),
        Expanded(
          child: GridView.builder(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 2,
              crossAxisSpacing: 16,
              mainAxisSpacing: 16,
              childAspectRatio: 0.75,
            ),
            itemCount: projects.length,
            itemBuilder: (context, index) {
              final project = projects[index];
              return InkWell(
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => ProjectDetailScreen(project: project, isTeacher: true),
                  ),
                ),
                child: Container(
                  decoration: BoxDecoration(
                    color: const Color(0xFF151515),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: Colors.white10),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        child: ClipRRect(
                          borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
                          child: Image.network(project.imageUrl, fit: BoxFit.cover, width: double.infinity),
                        ),
                      ),
                      Padding(
                        padding: const EdgeInsets.all(12),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(project.title, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold), maxLines: 1, overflow: TextOverflow.ellipsis),
                            Text('Por: ${project.creator}', style: const TextStyle(color: Colors.white38, fontSize: 11)),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}

class TeacherCreateCourseScreen extends StatefulWidget {
  const TeacherCreateCourseScreen({super.key});

  @override
  State<TeacherCreateCourseScreen> createState() => _TeacherCreateCourseScreenState();
}

class _TeacherCreateCourseScreenState extends State<TeacherCreateCourseScreen> {
  bool _isCreating = false;

  @override
  Widget build(BuildContext context) {
    if (_isCreating) {
      return _buildCreationForm(context);
    }

    return _buildCourseList(context);
  }

  Widget _buildCourseList(BuildContext context) {
    final courses = MockData.myCourses;

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(24.0),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'Mis Cursos',
                style: TextStyle(color: Colors.white, fontSize: 24, fontWeight: FontWeight.bold),
              ),
              ElevatedButton.icon(
                onPressed: () => setState(() => _isCreating = true),
                icon: const Icon(Icons.add, color: Colors.black),
                label: const Text('Crear Curso', style: TextStyle(color: Colors.black)),
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFFD4AF37),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                ),
              ),
            ],
          ),
        ),
        Expanded(
          child: ListView.builder(
            padding: const EdgeInsets.symmetric(horizontal: 24),
            itemCount: courses.length,
            itemBuilder: (context, index) {
              final course = courses[index];
              return Container(
                margin: const EdgeInsets.only(bottom: 20),
                decoration: BoxDecoration(
                  color: const Color(0xFF151515),
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: Colors.white10),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    ClipRRect(
                      borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
                      child: Image.network(
                        course.imageUrl,
                        height: 140,
                        width: double.infinity,
                        fit: BoxFit.cover,
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            course.title,
                            style: const TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold),
                          ),
                          const SizedBox(height: 8),
                          Text(
                            course.description,
                            style: const TextStyle(color: Colors.white54, fontSize: 13),
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                          ),
                          const SizedBox(height: 16),
                          Row(
                            children: [
                              Expanded(
                                child: OutlinedButton.icon(
                                  onPressed: () {
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      SnackBar(content: Text('Añadir ejercicios a: ${course.title}')),
                                    );
                                  },
                                  icon: const Icon(Icons.assignment, size: 18),
                                  label: const Text('Añadir Ejercicios'),
                                  style: OutlinedButton.styleFrom(
                                    foregroundColor: const Color(0xFFD4AF37),
                                    side: const BorderSide(color: Color(0xFFD4AF37)),
                                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                                  ),
                                ),
                              ),
                              const SizedBox(width: 12),
                              IconButton(
                                onPressed: () {},
                                icon: const Icon(Icons.edit, color: Colors.white54),
                                style: IconButton.styleFrom(
                                  backgroundColor: Colors.white.withOpacity(0.05),
                                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildCreationForm(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              IconButton(
                onPressed: () => setState(() => _isCreating = false),
                icon: const Icon(Icons.arrow_back, color: Colors.white),
              ),
              const Text('Crear Nuevo Curso', style: TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.bold)),
            ],
          ),
          const SizedBox(height: 24),
          _buildField('Título del Curso', Icons.title),
          const SizedBox(height: 16),
          _buildField('Descripción', Icons.description, maxLines: 4),
          const SizedBox(height: 16),
          _buildField('URL Imagen/Video', Icons.link),
          const SizedBox(height: 32),
          const Text('Asignar a Alumnos', style: TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            children: ['Todos', 'Clase A', 'Clase B', 'Juan Perez', 'Maria Garcia']
                .map((name) => FilterChip(
                      label: Text(name),
                      onSelected: (_) {},
                      backgroundColor: const Color(0xFF151515),
                      selectedColor: const Color(0xFFD4AF37),
                      labelStyle: const TextStyle(color: Colors.white, fontSize: 12),
                    ))
                .toList(),
          ),
          const SizedBox(height: 48),
          SizedBox(
            width: double.infinity,
            height: 55,
            child: FilledButton(
              onPressed: () {
                setState(() => _isCreating = false);
                ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Curso creado y asignado con éxito')));
              },
              style: FilledButton.styleFrom(backgroundColor: const Color(0xFFD4AF37), foregroundColor: Colors.black),
              child: const Text('Publicar Curso', style: TextStyle(fontWeight: FontWeight.bold)),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildField(String hint, IconData icon, {int maxLines = 1}) {
    return TextField(
      maxLines: maxLines,
      style: const TextStyle(color: Colors.white),
      decoration: InputDecoration(
        hintText: hint,
        hintStyle: const TextStyle(color: Colors.white24),
        prefixIcon: Icon(icon, color: const Color(0xFFD4AF37)),
        filled: true,
        fillColor: const Color(0xFF151515),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: BorderSide.none),
      ),
    );
  }
}

class TeacherStudentRankingScreen extends StatelessWidget {
  final String searchQuery;
  const TeacherStudentRankingScreen({super.key, required this.searchQuery});

  @override
  Widget build(BuildContext context) {
    final rankings = MockData.studentRankings.where((s) => 
      s.name.toLowerCase().contains(searchQuery.toLowerCase())).toList();
    
    return ListView.builder(
      padding: const EdgeInsets.all(24),
      itemCount: rankings.length,
      itemBuilder: (context, index) {
        final r = rankings[index];
        return Container(
          margin: const EdgeInsets.only(bottom: 16),
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            color: const Color(0xFF151515),
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: Colors.white10),
          ),
          child: Row(
            children: [
              Text('#${index + 1}', style: const TextStyle(color: Color(0xFFD4AF37), fontSize: 24, fontWeight: FontWeight.w900)),
              const SizedBox(width: 20),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(r.name, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16)),
                    const SizedBox(height: 4),
                    Row(
                      children: [
                        const Icon(Icons.trending_up, color: Colors.greenAccent, size: 14),
                        const SizedBox(width: 4),
                        Text('${r.investmentScore} pts', style: const TextStyle(color: Colors.white54, fontSize: 12)),
                        const SizedBox(width: 16),
                        const Icon(Icons.star, color: Color(0xFFD4AF37), size: 14),
                        const SizedBox(width: 4),
                        Text('${r.starsScore}', style: const TextStyle(color: Colors.white54, fontSize: 12)),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class TeacherProfileScreen extends StatelessWidget {
  const TeacherProfileScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final t = MockData.currentTeacher;
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          CircleAvatar(
            radius: 70,
            backgroundColor: const Color(0xFFD4AF37),
            backgroundImage: NetworkImage(t.profileImageUrl),
          ),
          const SizedBox(height: 24),
          Text('${t.name} ${t.surname ?? ""}', style: const TextStyle(color: Colors.white, fontSize: 28, fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          Text(t.educationCenter ?? "Centro Educativo", style: const TextStyle(color: Color(0xFFD4AF37), fontSize: 18)),
          const SizedBox(height: 32),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
            decoration: BoxDecoration(
              color: const Color(0xFF151515),
              borderRadius: BorderRadius.circular(16),
            ),
            child: const Text('Profesor Verificado', style: TextStyle(color: Colors.greenAccent, fontWeight: FontWeight.bold)),
          ),
        ],
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
