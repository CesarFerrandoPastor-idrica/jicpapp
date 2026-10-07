import 'package:flutter/material.dart';

import '../api/sesion.dart';
import 'market_screen.dart';
import 'learning_screen.dart';
import 'create_project_screen.dart';
import 'portfolio_screen.dart';
import 'profile_screen.dart';
import 'welcome_screen.dart';

class MainNavigation extends StatefulWidget {
  const MainNavigation({super.key});

  @override
  State<MainNavigation> createState() => _MainNavigationState();
}

class _MainNavigationState extends State<MainNavigation> {
  int _selectedIndex = 0;

  final List<String> _titles = [
    'Mercado',
    'Cursos y Aprendizaje',
    'Portafolio',
    'Crear Proyecto',
    'Perfil'
  ];

  final List<Widget> _screens = [
    const MarketScreen(),
    const LearningScreen(),
    const PortfolioScreen(),
    const CreateProjectScreen(),
    const ProfileScreen(),
  ];

  void _onItemTapped(int index) {
    setState(() {
      _selectedIndex = index;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_selectedIndex == 4 ? '' : _titles[_selectedIndex]),
        centerTitle: true,
        actions: _selectedIndex == 4 
          ? [
              PopupMenuButton<String>(
                icon: const Icon(Icons.more_vert, color: Color(0xFFD4AF37), size: 28),
                color: const Color(0xFF151515),
                onSelected: (value) {
                  if (value == 'logout') {
                    _cerrarSesion(context);
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
            ]
          : null,
      ),
      body: _screens[_selectedIndex],
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.only(bottom: 8.0, left: 16.0, right: 16.0),
          child: Container(
            decoration: BoxDecoration(
              color: const Color(0xFF151515),
              borderRadius: BorderRadius.circular(24),
              border: Border.all(color: const Color(0xFFD4AF37).withOpacity(0.3)),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(0.5),
                  blurRadius: 10,
                  offset: const Offset(0, 4),
                )
              ],
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
                onTap: _onItemTapped,
                items: const [
                  BottomNavigationBarItem(
                    icon: Icon(Icons.search),
                    label: 'Buscar',
                  ),
                  BottomNavigationBarItem(
                    icon: Icon(Icons.menu_book),
                    label: 'Cursos',
                  ),
                  BottomNavigationBarItem(
                    icon: Icon(Icons.account_balance_wallet_outlined),
                    activeIcon: Icon(Icons.account_balance_wallet),
                    label: 'Portafolio',
                  ),
                  BottomNavigationBarItem(
                    icon: Icon(Icons.add_circle_outline),
                    activeIcon: Icon(Icons.add_circle),
                    label: 'Crear',
                  ),
                  BottomNavigationBarItem(
                    icon: Icon(Icons.person_outline),
                    activeIcon: Icon(Icons.person),
                    label: 'Perfil',
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Cierra la sesion contra el servidor y vuelve a la pantalla de bienvenida.
///
/// Revocar el refresh token en el backend es lo que de verdad cierra la sesion:
/// borrar los tokens del movil sin avisar dejaria uno valido durante 30 dias.
Future<void> _cerrarSesion(BuildContext context) async {
  final navegador = Navigator.of(context);
  await Sesion.instancia.salir();
  navegador.pushAndRemoveUntil(
    MaterialPageRoute(builder: (context) => const WelcomeScreen()),
    (route) => false,
  );
}
