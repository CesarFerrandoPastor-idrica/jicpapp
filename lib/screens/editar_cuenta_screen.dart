import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/modelos_auth.dart';
import '../api/sesion.dart';

const _dorado = Color(0xFFD4AF37);
const _superficie = Color(0xFF151515);

/// Cambiar el email o la contraseña de la propia cuenta.
///
/// El nombre y los apellidos se enseñan pero no se editan: no los cambia el propio
/// usuario. Para cualquier cambio se pide la contraseña actual, que es lo que impide
/// que alguien con el móvil de otro en la mano se quede con su cuenta.
class EditarCuentaScreen extends ConsumerStatefulWidget {
  const EditarCuentaScreen({super.key});

  @override
  ConsumerState<EditarCuentaScreen> createState() => _EditarCuentaScreenState();
}

class _EditarCuentaScreenState extends ConsumerState<EditarCuentaScreen> {
  final _formulario = GlobalKey<FormState>();
  late final _email = TextEditingController(text: ref.read(sesionProvider).perfil?.email ?? '');
  final _passwordNueva = TextEditingController();
  final _repetida = TextEditingController();
  final _passwordActual = TextEditingController();

  bool _enviando = false;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    _passwordNueva.dispose();
    _repetida.dispose();
    _passwordActual.dispose();
    super.dispose();
  }

  Future<void> _guardar() async {
    if (!_formulario.currentState!.validate()) return;

    final emailActual = ref.read(sesionProvider).perfil?.email;
    final email = _email.text.trim().toLowerCase();
    final cambiaEmail = email != emailActual;
    final cambiaPassword = _passwordNueva.text.isNotEmpty;
    if (!cambiaEmail && !cambiaPassword) {
      setState(() => _error = 'No has cambiado nada');
      return;
    }

    setState(() {
      _enviando = true;
      _error = null;
    });
    try {
      await ref.read(sesionProvider.notifier).actualizarMiCuenta(
            passwordActual: _passwordActual.text,
            email: cambiaEmail ? email : null,
            passwordNueva: cambiaPassword ? _passwordNueva.text : null,
          );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Cambios guardados'),
          backgroundColor: _dorado,
          behavior: SnackBarBehavior.floating,
        ),
      );
      Navigator.pop(context);
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
    final perfil = ref.watch(sesionProvider).perfil;
    final nombre = perfil?.profesor?.nombreCompleto ?? perfil?.alumno?.nombreCompleto;

    return Scaffold(
      appBar: AppBar(title: const Text('Editar perfil')),
      body: Form(
        key: _formulario,
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            if (nombre != null) ...[
              _campoFijo('Nombre', nombre),
              const SizedBox(height: 4),
              const Text('El nombre lo gestiona el centro y no se puede cambiar desde aquí.',
                  style: TextStyle(color: Colors.white38, fontSize: 12)),
              const SizedBox(height: 24),
            ],
            _campo(
              controlador: _email,
              etiqueta: 'Email',
              icono: Icons.alternate_email,
              teclado: TextInputType.emailAddress,
              validador: (v) {
                final texto = (v ?? '').trim();
                if (texto.isEmpty) return 'Escribe tu email';
                return texto.contains('@') && texto.contains('.') ? null : 'Ese email no tiene buena pinta';
              },
            ),
            const SizedBox(height: 24),
            const Text('Cambiar contraseña (opcional)',
                style: TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.bold)),
            const SizedBox(height: 12),
            _campo(
              controlador: _passwordNueva,
              etiqueta: 'Contraseña nueva',
              icono: Icons.lock_outline,
              oculto: true,
              validador: (v) {
                final texto = v ?? '';
                if (texto.isEmpty) return null;
                return texto.length < 8 ? 'Al menos 8 caracteres' : null;
              },
            ),
            const SizedBox(height: 12),
            _campo(
              controlador: _repetida,
              etiqueta: 'Repite la contraseña nueva',
              icono: Icons.lock_outline,
              oculto: true,
              validador: (v) => (v ?? '') == _passwordNueva.text ? null : 'No coincide con la nueva',
            ),
            const SizedBox(height: 32),
            const Text('Para guardar, confirma con tu contraseña actual',
                style: TextStyle(color: Colors.white70, fontSize: 14)),
            const SizedBox(height: 12),
            _campo(
              controlador: _passwordActual,
              etiqueta: 'Contraseña actual',
              icono: Icons.key,
              oculto: true,
              validador: (v) => (v ?? '').isEmpty ? 'Escribe tu contraseña actual' : null,
            ),
            if (_error != null) ...[
              const SizedBox(height: 16),
              Text(_error!, style: const TextStyle(color: Colors.redAccent)),
            ],
            const SizedBox(height: 32),
            SizedBox(
              height: 55,
              child: FilledButton(
                onPressed: _enviando ? null : _guardar,
                style: FilledButton.styleFrom(
                  backgroundColor: _dorado,
                  foregroundColor: Colors.black,
                  disabledBackgroundColor: Colors.white10,
                ),
                child: _enviando
                    ? const SizedBox(
                        width: 22,
                        height: 22,
                        child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.black),
                      )
                    : const Text('Guardar cambios', style: TextStyle(fontWeight: FontWeight.bold)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _campoFijo(String etiqueta, String valor) => InputDecorator(
        decoration: InputDecoration(
          labelText: etiqueta,
          labelStyle: const TextStyle(color: Colors.white54),
          prefixIcon: const Icon(Icons.person_outline, color: Colors.white38),
          filled: true,
          fillColor: _superficie,
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: BorderSide.none),
        ),
        child: Text(valor, style: const TextStyle(color: Colors.white54, fontSize: 16)),
      );

  Widget _campo({
    required TextEditingController controlador,
    required String etiqueta,
    required IconData icono,
    String? Function(String?)? validador,
    bool oculto = false,
    TextInputType? teclado,
  }) {
    return TextFormField(
      controller: controlador,
      obscureText: oculto,
      keyboardType: teclado,
      validator: validador,
      style: const TextStyle(color: Colors.white),
      decoration: InputDecoration(
        labelText: etiqueta,
        labelStyle: const TextStyle(color: Colors.white54),
        prefixIcon: Icon(icono, color: _dorado),
        filled: true,
        fillColor: _superficie,
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: BorderSide.none),
      ),
    );
  }
}
