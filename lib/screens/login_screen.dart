import 'package:flutter/material.dart';

import '../api/config_api.dart';
import '../api/modelos_auth.dart';
import '../api/sesion.dart';
import 'main_navigation.dart';
import 'teacher_navigation.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _formulario = GlobalKey<FormState>();

  bool _enviando = false;
  bool _ocultarPassword = true;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _acceder() async {
    if (!_formulario.currentState!.validate()) return;

    // El boton se bloquea mientras la peticion esta en vuelo: sin esto, un doble
    // toque lanzaria dos logins y dejaria dos sesiones abiertas.
    setState(() {
      _enviando = true;
      _error = null;
    });

    try {
      final perfil = await Sesion.instancia.entrar(
        email: _email.text,
        password: _password.text,
      );
      if (!mounted) return;

      // A donde se entra lo decide el ROL QUE DEVUELVE EL SERVIDOR, no un
      // selector de la pantalla. El cliente no elige con que permisos entra.
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(
          builder: (_) => switch (perfil.rol) {
            Rol.alumno => const MainNavigation(),
            Rol.profesor || Rol.admin => const TeacherNavigation(),
          },
        ),
      );
    } on ErrorApi catch (e) {
      if (!mounted) return;
      setState(() => _error = e.mensaje);
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF151515),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 32.0),
            child: Form(
              key: _formulario,
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  ClipOval(
                    child: Container(
                      width: 100,
                      height: 100,
                      color: Colors.black,
                      child: Image.asset(
                        'assets/logo.png',
                        fit: BoxFit.cover,
                        errorBuilder: (_, __, ___) => const Icon(
                          Icons.diamond_outlined,
                          color: Colors.white,
                          size: 50,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 32),
                  const Text(
                    'Iniciar Sesión',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 28,
                      fontWeight: FontWeight.w900,
                      letterSpacing: 1.2,
                    ),
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Accede a la bolsa social educativa',
                    style: TextStyle(color: Colors.white54, fontSize: 14),
                  ),
                  const SizedBox(height: 40),

                  _campoEmail(),
                  const SizedBox(height: 20),
                  _campoPassword(),

                  if (_error != null) ...[
                    const SizedBox(height: 20),
                    _avisoDeError(_error!),
                  ],

                  const SizedBox(height: 32),
                  _botonAcceder(),
                  const SizedBox(height: 24),
                  _pieDeAyuda(),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _campoEmail() => TextFormField(
        controller: _email,
        enabled: !_enviando,
        keyboardType: TextInputType.emailAddress,
        autocorrect: false,
        textInputAction: TextInputAction.next,
        style: const TextStyle(color: Colors.white),
        decoration: _decoracion('Email', Icons.alternate_email),
        validator: (valor) {
          final texto = valor?.trim() ?? '';
          if (texto.isEmpty) return 'Introduce tu email';
          if (!texto.contains('@')) return 'Ese email no tiene buena pinta';
          return null;
        },
      );

  Widget _campoPassword() => TextFormField(
        controller: _password,
        enabled: !_enviando,
        obscureText: _ocultarPassword,
        textInputAction: TextInputAction.done,
        onFieldSubmitted: (_) => _enviando ? null : _acceder(),
        style: const TextStyle(color: Colors.white),
        decoration: _decoracion('Contraseña', Icons.lock_outline).copyWith(
          suffixIcon: IconButton(
            icon: Icon(
              _ocultarPassword ? Icons.visibility_off : Icons.visibility,
              color: Colors.white38,
            ),
            onPressed: () =>
                setState(() => _ocultarPassword = !_ocultarPassword),
          ),
        ),
        validator: (valor) =>
            (valor ?? '').isEmpty ? 'Introduce tu contraseña' : null,
      );

  Widget _avisoDeError(String mensaje) => Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: Colors.redAccent.withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: Colors.redAccent.withValues(alpha: 0.4)),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Icon(Icons.error_outline, color: Colors.redAccent, size: 20),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                mensaje,
                style: const TextStyle(color: Colors.redAccent, fontSize: 13),
              ),
            ),
          ],
        ),
      );

  Widget _botonAcceder() => SizedBox(
        width: double.infinity,
        height: 55,
        child: FilledButton(
          onPressed: _enviando ? null : _acceder,
          style: FilledButton.styleFrom(
            backgroundColor: const Color(0xFFD4AF37),
            foregroundColor: Colors.black,
            disabledBackgroundColor: const Color(0xFFD4AF37).withValues(alpha: 0.4),
            shape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
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
              : const Text(
                  'Acceder',
                  style: TextStyle(fontWeight: FontWeight.w900, fontSize: 18),
                ),
        ),
      );

  /// Recordatorio de contra que backend se esta hablando. Solo en depuracion:
  /// en una build de release no pinta nada enseñar la URL de la API.
  Widget _pieDeAyuda() {
    bool enDepuracion = false;
    assert(() {
      enDepuracion = true;
      return true;
    }());
    if (!enDepuracion) return const SizedBox.shrink();

    return Text(
      'API: ${ConfigApi.urlBase}',
      textAlign: TextAlign.center,
      style: const TextStyle(color: Colors.white24, fontSize: 11),
    );
  }

  InputDecoration _decoracion(String hint, IconData icono) => InputDecoration(
        hintText: hint,
        hintStyle: const TextStyle(color: Colors.white38),
        prefixIcon: Icon(icono, color: const Color(0xFFD4AF37)),
        filled: true,
        fillColor: Colors.white.withValues(alpha: 0.05),
        errorStyle: const TextStyle(color: Colors.redAccent),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide.none,
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide.none,
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: Color(0xFFD4AF37), width: 1.5),
        ),
      );
}
