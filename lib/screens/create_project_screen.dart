import 'package:flutter/material.dart';

import '../api/modelos_auth.dart';
import '../api/modelos_proyecto.dart';
import '../api/sesion.dart';

const _dorado = Color(0xFFD4AF37);
const _superficie = Color(0xFF151515);

class CreateProjectScreen extends StatefulWidget {
  const CreateProjectScreen({super.key});

  @override
  State<CreateProjectScreen> createState() => _CreateProjectScreenState();
}

class _CreateProjectScreenState extends State<CreateProjectScreen> {
  final _formulario = GlobalKey<FormState>();
  final _nombre = TextEditingController();
  final _descripcion = TextEditingController();
  final _desglose = TextEditingController();
  final _inversion = TextEditingController();
  final _precioBase = TextEditingController(text: '100');
  final _ronda = TextEditingController(text: '50');

  Catalogo? _categoria;
  List<Catalogo> _categorias = const [];

  bool _cargandoCatalogo = true;
  bool _enviando = false;
  String? _error;
  ProyectoCreado? _creado;

  @override
  void initState() {
    super.initState();
    _cargarDatosIniciales();
  }

  @override
  void dispose() {
    for (final c in [_nombre, _descripcion, _desglose, _inversion, _precioBase, _ronda]) {
      c.dispose();
    }
    super.dispose();
  }

  /// Las categorías se piden al servidor, no se escriben en la app: el
  /// `idCategoria` que se envía tiene que ser el de la base, y una lista escrita
  /// a mano se desincroniza en cuanto se añade una categoría nueva.
  Future<void> _cargarDatosIniciales() async {
    try {
      final categorias = await Sesion.instancia.proyectos.categorias();
      await Sesion.instancia.refrescarSaldo();
      if (!mounted) return;
      setState(() {
        _categorias = categorias;
        _cargandoCatalogo = false;
      });
    } on ErrorApi catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.mensaje;
        _cargandoCatalogo = false;
      });
    }
  }

  Future<void> _publicar() async {
    if (!_formulario.currentState!.validate()) return;
    if (_categoria == null) {
      setState(() => _error = 'Elige una categoría para tu proyecto');
      return;
    }

    setState(() {
      _enviando = true;
      _error = null;
    });

    try {
      final creado = await Sesion.instancia.proyectos.crear(
        nombre: _nombre.text,
        descripcion: _descripcionCompleta(),
        idCategoria: _categoria!.id,
        idEstado: EstadosProyecto.publicado,
        inversionInicial: _numero(_inversion.text),
        precioBase: _numero(_precioBase.text),
        participacionesTotales: _numero(_ronda.text),
      );

      // El saldo se relee del servidor. Nunca se calcula "saldo - inversion" en
      // local: lo que manda es lo que diga la cartera.
      await Sesion.instancia.refrescarSaldo();

      if (!mounted) return;
      setState(() {
        _creado = creado;
        _enviando = false;
      });
      _limpiarFormulario();
    } on ErrorApi catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.mensaje;
        _enviando = false;
      });
    }
  }

  /// El desglose de gastos todavía no tiene columna propia en el backend, así que
  /// se adjunta a la descripción en lugar de descartarlo: perder lo que el alumno
  /// ha escrito sería peor que guardarlo en un sitio imperfecto.
  String _descripcionCompleta() {
    final desglose = _desglose.text.trim();
    if (desglose.isEmpty) return _descripcion.text.trim();
    return '${_descripcion.text.trim()}\n\nDesglose de la inversión:\n$desglose';
  }

  void _limpiarFormulario() {
    _nombre.clear();
    _descripcion.clear();
    _desglose.clear();
    _inversion.clear();
    _precioBase.text = '100';
    _ronda.text = '50';
    setState(() => _categoria = null);
  }

  static double _numero(String texto) =>
      double.tryParse(texto.trim().replaceAll(',', '.')) ?? 0;

  @override
  Widget build(BuildContext context) {
    if (_cargandoCatalogo) {
      return const Center(child: CircularProgressIndicator(color: _dorado));
    }

    return SingleChildScrollView(
      physics: const BouncingScrollPhysics(),
      padding: const EdgeInsets.all(24.0),
      child: Form(
        key: _formulario,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              'Nuevo Proyecto Emprendedor',
              style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: Colors.white),
            ),
            const SizedBox(height: 8),
            const Text(
              'Completa los datos de tu idea de negocio',
              style: TextStyle(color: Colors.white38, fontSize: 14),
            ),
            const SizedBox(height: 20),

            _tarjetaDeSaldo(),
            const SizedBox(height: 24),

            _campo(
              controlador: _nombre,
              etiqueta: 'Nombre del Proyecto',
              icono: Icons.rocket_launch,
              maxCaracteres: 150,
              validador: (v) => (v ?? '').trim().isEmpty ? 'Ponle un nombre' : null,
            ),
            const SizedBox(height: 16),

            _selectorDeCategoria(),
            const SizedBox(height: 16),

            _campo(
              controlador: _descripcion,
              etiqueta: 'Descripción del Proyecto',
              icono: Icons.description,
              lineas: 3,
              validador: (v) =>
                  (v ?? '').trim().length < 10 ? 'Explica tu idea con algo más de detalle' : null,
            ),
            const SizedBox(height: 16),

            _campoPendiente('Patente del Proyecto / Registro', Icons.verified_user),
            const SizedBox(height: 16),

            _selectorDeImagen(),
            const SizedBox(height: 16),

            _campo(
              controlador: _desglose,
              etiqueta: 'Desglose de inversión inicial',
              icono: Icons.list_alt,
              lineas: 3,
              pista: 'Ej: Materiales 50 JICP, Servidor 20 JICP...',
            ),
            const SizedBox(height: 24),

            _bloqueDeMercado(),
            const SizedBox(height: 16),

            if (_error != null) ...[
              _aviso(_error!, Colors.redAccent, Icons.error_outline),
              const SizedBox(height: 16),
            ],
            if (_creado != null) ...[
              _resumenDeAlta(_creado!),
              const SizedBox(height: 16),
            ],

            FilledButton(
              onPressed: _enviando ? null : _publicar,
              style: FilledButton.styleFrom(
                backgroundColor: _dorado,
                foregroundColor: Colors.black,
                disabledBackgroundColor: _dorado.withValues(alpha: 0.4),
                padding: const EdgeInsets.symmetric(vertical: 18),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                elevation: 5,
              ),
              child: _enviando
                  ? const SizedBox(
                      height: 22,
                      width: 22,
                      child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.black),
                    )
                  : const Text(
                      'Publicar Proyecto',
                      style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                    ),
            ),
            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }

  /// Saldo disponible, leído del servidor. Se enseña para que el alumno sepa qué
  /// puede permitirse antes de rellenar nada.
  Widget _tarjetaDeSaldo() {
    return AnimatedBuilder(
      animation: Sesion.instancia,
      builder: (context, _) {
        final saldo = Sesion.instancia.saldo;
        return Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          decoration: BoxDecoration(
            color: _dorado.withValues(alpha: 0.08),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: _dorado.withValues(alpha: 0.3)),
          ),
          child: Row(
            children: [
              const Icon(Icons.account_balance_wallet_outlined, color: _dorado, size: 22),
              const SizedBox(width: 12),
              const Text('Tu saldo', style: TextStyle(color: Colors.white70, fontSize: 14)),
              const Spacer(),
              Text(
                saldo == null ? '—' : '${saldo.toStringAsFixed(2)} JICP',
                style: const TextStyle(
                  color: _dorado,
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  /// Los tres números que definen el mercado del proyecto, juntos y explicados:
  /// por separado no se entiende que el precio suba solo.
  Widget _bloqueDeMercado() {
    final base = _numero(_precioBase.text);
    final ronda = _numero(_ronda.text);
    final inversion = _numero(_inversion.text);
    final participaciones = base > 0 ? inversion / base : 0;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: _superficie,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.white12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Row(
            children: [
              Icon(Icons.trending_up, color: _dorado, size: 20),
              SizedBox(width: 8),
              Text(
                'Cómo se financia',
                style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 15),
              ),
            ],
          ),
          const SizedBox(height: 4),
          const Text(
            'El precio sube según se llena la ronda: cuando esté completa costará el doble.',
            style: TextStyle(color: Colors.white38, fontSize: 12),
          ),
          const SizedBox(height: 16),

          _campo(
            controlador: _inversion,
            etiqueta: 'Tu inversión inicial (JICP)',
            icono: Icons.monetization_on,
            numerico: true,
            alCambiar: (_) => setState(() {}),
            validador: (v) {
              final n = _numero(v ?? '');
              if (n <= 0) return 'Pon lo que aportas tú al proyecto';
              final saldo = Sesion.instancia.saldo;
              // Aviso inmediato, no una garantía: quien decide es el servidor.
              if (saldo != null && n > saldo) return 'No te llega el saldo (tienes $saldo)';
              return null;
            },
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: _campo(
                  controlador: _precioBase,
                  etiqueta: 'Precio inicial',
                  icono: Icons.sell_outlined,
                  numerico: true,
                  alCambiar: (_) => setState(() {}),
                  validador: (v) => _numero(v ?? '') <= 0 ? 'Debe ser > 0' : null,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _campo(
                  controlador: _ronda,
                  etiqueta: 'Participaciones',
                  icono: Icons.pie_chart_outline,
                  numerico: true,
                  alCambiar: (_) => setState(() {}),
                  validador: (v) {
                    final n = _numero(v ?? '');
                    if (n <= 0) return 'Debe ser > 0';
                    final base = _numero(_precioBase.text);
                    if (base > 0 && _numero(_inversion.text) / base > n) {
                      return 'Ronda pequeña';
                    }
                    return null;
                  },
                ),
              ),
            ],
          ),

          if (base > 0 && ronda > 0 && inversion > 0) ...[
            const SizedBox(height: 14),
            const Divider(color: Colors.white12, height: 1),
            const SizedBox(height: 12),
            _linea('Te quedas con', '${participaciones.toStringAsFixed(2)} participaciones'),
            const SizedBox(height: 6),
            _linea(
              'El siguiente pagará',
              '${(base * (1 + participaciones / ronda)).toStringAsFixed(2)} JICP',
            ),
          ],
        ],
      ),
    );
  }

  Widget _linea(String etiqueta, String valor) => Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(etiqueta, style: const TextStyle(color: Colors.white54, fontSize: 13)),
          Text(
            valor,
            style: const TextStyle(color: _dorado, fontSize: 13, fontWeight: FontWeight.bold),
          ),
        ],
      );

  Widget _resumenDeAlta(ProyectoCreado p) => Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.green.withValues(alpha: 0.10),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: Colors.green.withValues(alpha: 0.4)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.check_circle_outline, color: Colors.green, size: 20),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    '"${p.nombre}" ya está en el mercado',
                    style: const TextStyle(
                      color: Colors.green,
                      fontWeight: FontWeight.bold,
                      fontSize: 14,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            _linea('Has aportado', '${p.inversionInicial.toStringAsFixed(2)} JICP'),
            const SizedBox(height: 6),
            _linea('Tus participaciones', p.participacionesEmitidas.toStringAsFixed(2)),
            const SizedBox(height: 6),
            _linea('Precio para el siguiente', '${p.precioSiguiente.toStringAsFixed(2)} JICP'),
          ],
        ),
      );

  Widget _aviso(String mensaje, Color color, IconData icono) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: color.withValues(alpha: 0.4)),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icono, color: color, size: 20),
            const SizedBox(width: 12),
            Expanded(
              child: Text(mensaje, style: TextStyle(color: color, fontSize: 13)),
            ),
          ],
        ),
      );

  Widget _selectorDeCategoria() {
    return FormField<Catalogo>(
      validator: (_) => _categoria == null ? 'Elige una categoría' : null,
      builder: (estado) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            decoration: BoxDecoration(
              color: _superficie,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: estado.hasError ? Colors.redAccent : Colors.white12,
              ),
            ),
            child: DropdownButtonHideUnderline(
              child: DropdownButton<Catalogo>(
                value: _categoria,
                hint: const Text('Seleccionar Categoría',
                    style: TextStyle(color: Colors.white54)),
                dropdownColor: _superficie,
                isExpanded: true,
                icon: const Icon(Icons.arrow_drop_down, color: _dorado),
                style: const TextStyle(color: Colors.white, fontSize: 16),
                items: _categorias
                    .map((c) => DropdownMenuItem(value: c, child: Text(c.nombre)))
                    .toList(),
                onChanged: _enviando
                    ? null
                    : (valor) {
                        setState(() => _categoria = valor);
                        estado.didChange(valor);
                      },
              ),
            ),
          ),
          if (estado.hasError)
            Padding(
              padding: const EdgeInsets.only(left: 12, top: 6),
              child: Text(
                estado.errorText!,
                style: const TextStyle(color: Colors.redAccent, fontSize: 12),
              ),
            ),
        ],
      ),
    );
  }

  /// Campo que la pantalla ya tenía pero que el backend todavía no guarda.
  ///
  /// Se deja visible y deshabilitado en lugar de aceptar texto que se perdería:
  /// un campo que dice "todavía no" es mejor que uno que se traga lo que escribes.
  Widget _campoPendiente(String etiqueta, IconData icono) => TextField(
        enabled: false,
        style: const TextStyle(color: Colors.white38),
        decoration: InputDecoration(
          labelText: '$etiqueta (próximamente)',
          labelStyle: const TextStyle(color: Colors.white24),
          prefixIcon: Icon(icono, color: Colors.white24, size: 20),
          filled: true,
          fillColor: _superficie.withValues(alpha: 0.5),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(16),
            borderSide: BorderSide.none,
          ),
          disabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(16),
            borderSide: const BorderSide(color: Colors.white10),
          ),
        ),
      );

  Widget _selectorDeImagen() => Container(
        height: 100,
        decoration: BoxDecoration(
          color: _superficie.withValues(alpha: 0.5),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: Colors.white10),
        ),
        child: const Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.add_a_photo, color: Colors.white24),
            SizedBox(height: 8),
            Text(
              'Imagen Principal (próximamente)',
              style: TextStyle(color: Colors.white24, fontSize: 12),
            ),
          ],
        ),
      );

  Widget _campo({
    required TextEditingController controlador,
    required String etiqueta,
    required IconData icono,
    int lineas = 1,
    bool numerico = false,
    int? maxCaracteres,
    String? pista,
    String? Function(String?)? validador,
    void Function(String)? alCambiar,
  }) {
    return TextFormField(
      controller: controlador,
      enabled: !_enviando,
      maxLines: lineas,
      maxLength: maxCaracteres,
      keyboardType: numerico
          ? const TextInputType.numberWithOptions(decimal: true)
          : TextInputType.multiline,
      onChanged: alCambiar,
      validator: validador,
      style: const TextStyle(color: Colors.white),
      decoration: InputDecoration(
        labelText: etiqueta,
        hintText: pista,
        counterText: '',
        hintStyle: const TextStyle(color: Colors.white24, fontSize: 12),
        labelStyle: const TextStyle(color: Colors.white54),
        errorStyle: const TextStyle(color: Colors.redAccent),
        prefixIcon: Icon(icono, color: _dorado, size: 20),
        filled: true,
        fillColor: _superficie,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide.none,
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: Colors.white12),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: _dorado),
        ),
      ),
    );
  }
}
