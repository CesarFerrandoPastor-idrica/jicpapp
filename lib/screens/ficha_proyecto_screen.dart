import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/idempotencia.dart';
import '../api/modelos_auth.dart';
import '../api/modelos_proyecto.dart';
import '../api/proveedores.dart';
import '../api/sesion.dart';
import 'market_screen.dart' show iconoDeCategoria;

const _dorado = Color(0xFFD4AF37);
const _superficie = Color(0xFF151515);

/// Ficha de un proyecto del mercado, con datos reales y hoja de compra.
///
/// Los números de mercado (precio, disponibles, recaudado) se piden aparte a
/// `/proyectos/{id}/mercado` en lugar de fiarse de los de la lista: entre que se
/// cargó el mercado y se abre la ficha otro alumno puede haber comprado.
class FichaProyectoScreen extends ConsumerStatefulWidget {
  const FichaProyectoScreen({super.key, required this.proyecto});

  final ProyectoDeMercado proyecto;

  @override
  ConsumerState<FichaProyectoScreen> createState() => _FichaProyectoScreenState();
}

class _FichaProyectoScreenState extends ConsumerState<FichaProyectoScreen> {
  EstadoDeMercado? _mercado;
  Posicion? _miPosicion;
  double? _saldo;

  bool _cargando = true;
  String? _error;

  ProyectoDeMercado get _proyecto => widget.proyecto;

  bool get _esMio =>
      _proyecto.idCreador != null &&
      _proyecto.idCreador == ref.watch(sesionProvider).alumno?.id;

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  Future<void> _cargar() async {
    setState(() => _error = null);
    try {
      final resultados = await Future.wait([
        ref.read(repositorioProyectosProvider).estadoDeMercado(_proyecto.id),
        ref.read(repositorioProyectosProvider).portafolio(),
        ref.read(repositorioProyectosProvider).saldo(),
      ]);
      if (!mounted) return;
      final posiciones = resultados[1] as List<Posicion>;
      setState(() {
        _mercado = resultados[0] as EstadoDeMercado;
        _miPosicion = posiciones
            .where((p) => p.idProyecto == _proyecto.id)
            .firstOrNull;
        _saldo = resultados[2] as double;
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

  Future<void> _abrirHojaDeCompra() async {
    final sesion = ref.read(sesionProvider.notifier);
    final recibo = await showModalBottomSheet<ReciboDeInversion>(
      context: context,
      isScrollControlled: true,
      // Arrastrar para cerrar se salta el PopScope de la hoja: con una compra en
      // vuelo, el alumno la veria desaparecer sin saber si ha comprado.
      enableDrag: false,
      backgroundColor: _superficie,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      builder: (_) => HojaDeCompra(
        proyecto: _proyecto,
        mercado: _mercado!,
        saldo: _saldo,
      ),
    );

    if (recibo != null) {
      sesion.anotarSaldoDelServidor(recibo.saldoCartera);
      if (mounted) await _mostrarRecibo(recibo);
    }
    // Se relee pase lo que pase: tras una compra, y tambien si la hoja se cerro
    // con el resultado en el aire, la verdad la tiene el servidor.
    if (mounted) _cargar();
  }

  Future<void> _mostrarRecibo(ReciboDeInversion r) => showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          backgroundColor: _superficie,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(28),
            side: BorderSide(color: _dorado.withValues(alpha: 0.3)),
          ),
          title: const Row(
            children: [
              Icon(Icons.check_circle, color: _dorado),
              SizedBox(width: 12),
              Text('Compra realizada',
                  style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
            ],
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              _lineaDeRecibo('Participaciones', formatoParticipaciones(r.participacionesCompradas)),
              _lineaDeRecibo('Precio unitario', '${r.precioUnitario.toStringAsFixed(2)} JICP'),
              _lineaDeRecibo('Importe', '${r.importe.toStringAsFixed(2)} JICP', destacado: true),
              const Divider(color: Colors.white12),
              _lineaDeRecibo('Ahora tienes', formatoParticipaciones(r.participacionesTotales)),
              _lineaDeRecibo('Precio medio', '${r.precioMedio.toStringAsFixed(2)} JICP'),
              _lineaDeRecibo('Siguiente precio', '${r.precioSiguiente.toStringAsFixed(2)} JICP'),
              const Divider(color: Colors.white12),
              _lineaDeRecibo('Saldo', '${r.saldoCartera.toStringAsFixed(2)} JICP', destacado: true),
            ],
          ),
          actions: [
            FilledButton(
              onPressed: () => Navigator.pop(context),
              style: FilledButton.styleFrom(
                backgroundColor: _dorado,
                foregroundColor: Colors.black,
              ),
              child: const Text('Entendido', style: TextStyle(fontWeight: FontWeight.bold)),
            ),
          ],
        ),
      );

  Widget _lineaDeRecibo(String etiqueta, String valor, {bool destacado = false}) =>
      Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(etiqueta, style: const TextStyle(color: Colors.white54)),
            Text(
              valor,
              style: TextStyle(
                color: destacado ? _dorado : Colors.white,
                fontWeight: FontWeight.bold,
              ),
            ),
          ],
        ),
      );

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0F0F0F),
      appBar: AppBar(
        backgroundColor: const Color(0xFF0F0F0F),
        title: Text(_proyecto.nombre, overflow: TextOverflow.ellipsis),
      ),
      body: RefreshIndicator(
        color: _dorado,
        backgroundColor: _superficie,
        onRefresh: _cargar,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(24),
          children: [
            _cabecera(),
            const SizedBox(height: 28),
            _tituloDeSeccion('Descripción del proyecto'),
            const SizedBox(height: 12),
            Text(
              _proyecto.descripcion,
              style: const TextStyle(color: Colors.white70, fontSize: 15, height: 1.6),
            ),
            const SizedBox(height: 32),
            _tituloDeSeccion('Mercado de participaciones'),
            const SizedBox(height: 16),
            ..._seccionDeMercado(),
          ],
        ),
      ),
    );
  }

  Widget _cabecera() => Row(
        children: [
          Container(
            width: 56,
            height: 56,
            decoration: BoxDecoration(
              color: _dorado.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(16),
            ),
            child: Icon(iconoDeCategoria(_proyecto.categoria), color: _dorado, size: 30),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  _proyecto.nombre,
                  style: const TextStyle(
                      color: Colors.white, fontSize: 24, fontWeight: FontWeight.bold),
                ),
                Text(
                  _esMio ? 'Tu proyecto' : 'Por ${_proyecto.nombreCreador ?? '—'}',
                  style: const TextStyle(
                      color: _dorado, fontSize: 15, fontWeight: FontWeight.w500),
                ),
              ],
            ),
          ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            decoration: BoxDecoration(
              color: _dorado.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: _dorado.withValues(alpha: 0.3)),
            ),
            child: Text(
              _proyecto.categoria,
              style: const TextStyle(
                  color: _dorado, fontWeight: FontWeight.bold, fontSize: 12),
            ),
          ),
        ],
      );

  List<Widget> _seccionDeMercado() {
    if (_cargando) {
      return const [
        Padding(
          padding: EdgeInsets.all(32),
          child: Center(child: CircularProgressIndicator(color: _dorado)),
        ),
      ];
    }
    if (_error != null) {
      return [
        Text(_error!, style: const TextStyle(color: Colors.redAccent)),
        const SizedBox(height: 12),
        Align(
          alignment: Alignment.centerLeft,
          child: OutlinedButton.icon(
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
        ),
      ];
    }

    final m = _mercado!;
    return [
      _fila('Precio actual', '${m.precioActual.toStringAsFixed(2)} JICP',
          Icons.monetization_on, destacado: true),
      _fila('Precio base', '${m.precioBase.toStringAsFixed(2)} JICP', Icons.flag_outlined),
      _fila(
        'Participaciones disponibles',
        '${formatoParticipaciones(m.participacionesDisponibles)} de '
            '${formatoParticipaciones(m.participacionesTotales)}',
        Icons.pie_chart_outline,
      ),
      _fila('Inversión inicial del fundador',
          '${_proyecto.inversionInicial.toStringAsFixed(2)} JICP', Icons.rocket_launch),
      _fila('Recaudado en tesorería', '${m.recaudado.toStringAsFixed(2)} JICP',
          Icons.account_balance),
      _fila('Inversores', '${m.inversores}', Icons.group),
      const Padding(
        padding: EdgeInsets.only(bottom: 24),
        child: Text(
          'El precio sube a medida que se llena la ronda: '
          'precio base × (1 + emitidas / totales).',
          style: TextStyle(color: Colors.white38, fontSize: 12),
        ),
      ),
      if (_miPosicion != null) ...[
        _tituloDeSeccion(_esMio ? 'Tus participaciones de fundador' : 'Tu inversión'),
        const SizedBox(height: 16),
        _tuPosicion(_miPosicion!),
        const SizedBox(height: 32),
      ],
      _botonDeCompra(m),
      const SizedBox(height: 24),
    ];
  }

  Widget _tuPosicion(Posicion p) {
    final gana = p.plusvalia >= 0;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: _dorado.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: _dorado.withValues(alpha: 0.3)),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _mini('Participaciones', formatoParticipaciones(p.participaciones)),
          _mini('Precio medio', p.precioMedio.toStringAsFixed(2)),
          _mini(
            'Valor actual',
            '${p.valorActual.toStringAsFixed(2)}\n'
                '(${gana ? '+' : ''}${p.plusvalia.toStringAsFixed(2)})',
          ),
        ],
      ),
    );
  }

  Widget _botonDeCompra(EstadoDeMercado m) {
    // Estos bloqueos son solo para no dejar pulsar algo que va a fallar. El
    // servidor repite las mismas comprobaciones y es el que decide.
    final String? motivo = _esMio
        ? 'Es tu proyecto: no puedes invertir en él'
        : m.rondaAgotada
            ? 'La ronda está completa'
            : null;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        FilledButton(
          onPressed: motivo == null ? _abrirHojaDeCompra : null,
          style: FilledButton.styleFrom(
            backgroundColor: _dorado,
            foregroundColor: Colors.black,
            disabledBackgroundColor: Colors.white10,
            disabledForegroundColor: Colors.white38,
            minimumSize: const Size(double.infinity, 60),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
          ),
          child: const Text('Comprar participaciones',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
        ),
        if (motivo != null) ...[
          const SizedBox(height: 8),
          Text(motivo,
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.white38, fontSize: 13)),
        ],
      ],
    );
  }

  Widget _tituloDeSeccion(String titulo) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(titulo.toUpperCase(),
              style: const TextStyle(
                  color: _dorado,
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 1.2)),
          const SizedBox(height: 4),
          Container(width: 40, height: 2, color: _dorado),
        ],
      );

  Widget _fila(String etiqueta, String valor, IconData icono, {bool destacado = false}) =>
      Padding(
        padding: const EdgeInsets.only(bottom: 20),
        child: Row(
          children: [
            Icon(icono, color: destacado ? _dorado : Colors.white24, size: 20),
            const SizedBox(width: 16),
            Expanded(
              child: Text(etiqueta,
                  style: const TextStyle(color: Colors.white54, fontSize: 14)),
            ),
            Text(
              valor,
              style: TextStyle(
                color: destacado ? _dorado : Colors.white,
                fontSize: 16,
                fontWeight: destacado ? FontWeight.bold : FontWeight.w500,
              ),
            ),
          ],
        ),
      );

  Widget _mini(String etiqueta, String valor) => Column(
        children: [
          Text(etiqueta, style: const TextStyle(color: Colors.white54, fontSize: 11)),
          const SizedBox(height: 4),
          Text(valor,
              textAlign: TextAlign.center,
              style: const TextStyle(
                  color: _dorado, fontWeight: FontWeight.bold, fontSize: 15)),
        ],
      );
}

/// Hoja de compra de participaciones.
///
/// Aquí vive la parte delicada del cliente: la `Idempotency-Key`.
///
/// - Se genera **una por intento de compra**, al abrir la hoja.
/// - Si el servidor responde con un error (saldo, precio, ronda...), la compra
///   no se ha hecho: se puede corregir y volver a intentar con una clave nueva.
/// - Si **no llega respuesta**, no se sabe si se hizo. La hoja bloquea la
///   cantidad y solo deja reintentar con la **misma** clave: si la primera llegó
///   al servidor, este devuelve el recibo original en lugar de cobrar otra vez.
///   Jamás se da por fallida una compra sin respuesta.
class HojaDeCompra extends ConsumerStatefulWidget {
  const HojaDeCompra({
    super.key,
    required this.proyecto,
    required this.mercado,
    required this.saldo,
  });

  final ProyectoDeMercado proyecto;
  final EstadoDeMercado mercado;

  /// Último saldo leído del servidor; null si no se pudo leer.
  final double? saldo;

  @override
  ConsumerState<HojaDeCompra> createState() => _HojaDeCompraState();
}

class _HojaDeCompraState extends ConsumerState<HojaDeCompra> {
  final _cantidad = TextEditingController();

  late EstadoDeMercado _mercado = widget.mercado;
  String _clave = nuevaClaveDeIdempotencia();

  bool _enVuelo = false;

  /// Se envió la compra y no llegó respuesta: puede haberse hecho.
  bool _resultadoIncierto = false;
  String? _error;

  /// El precio que vio el alumno al decidir. Se fija junto a la clave: un
  /// reintento con la misma clave tiene que mandar exactamente la misma petición.
  late double _precioEnviado = _mercado.precioActual;
  double? _cantidadEnviada;

  static final _formato = RegExp(r'^\d+([.,]\d{1,4})?$');

  @override
  void initState() {
    super.initState();
    _cantidad.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _cantidad.dispose();
    super.dispose();
  }

  double? get _participaciones {
    final texto = _cantidad.text.trim();
    if (!_formato.hasMatch(texto)) return null;
    return double.tryParse(texto.replaceAll(',', '.'));
  }

  /// Importe con el precio mostrado. Orientativo: el que se cobra es el del recibo.
  double? get _importe {
    final n = _participaciones;
    return n == null ? null : n * _mercado.precioActual;
  }

  /// Motivo por el que no se puede pulsar comprar, o null si se puede.
  String? get _bloqueo {
    final texto = _cantidad.text.trim();
    if (texto.isEmpty) return '';
    final n = _participaciones;
    if (n == null) return 'Escribe un número con hasta 4 decimales';
    if (n <= 0) return 'Tienes que comprar al menos una fracción';
    if (n > _mercado.participacionesDisponibles) {
      return 'Solo quedan ${formatoParticipaciones(_mercado.participacionesDisponibles)} '
          'participaciones';
    }
    final saldo = widget.saldo;
    if (saldo != null && _importe! > saldo + 0.004) {
      return 'No te llega el saldo (${saldo.toStringAsFixed(2)} JICP)';
    }
    return null;
  }

  Future<void> _comprar() async {
    final cantidad = _resultadoIncierto ? _cantidadEnviada! : _participaciones!;
    if (!_resultadoIncierto) {
      _cantidadEnviada = cantidad;
      _precioEnviado = _mercado.precioActual;
    }

    setState(() {
      _enVuelo = true;
      _error = null;
    });

    try {
      final recibo = await ref.read(repositorioProyectosProvider).invertir(
        idProyecto: widget.proyecto.id,
        participaciones: cantidad,
        precioUnitarioEsperado: _precioEnviado,
        claveIdempotencia: _clave,
      );
      if (mounted) Navigator.pop(context, recibo);
    } on ErrorApi catch (e) {
      if (!mounted) return;

      if (e.codigo == null) {
        // Sin respuesta: la compra esta en el aire. Se conserva la clave.
        setState(() {
          _enVuelo = false;
          _resultadoIncierto = true;
          _error = '${e.mensaje}\n\nNo sabemos si la compra llegó. Reintenta: '
              'si ya se hizo, no se cobrará dos veces.';
        });
        return;
      }

      // El servidor la rechazo y no ha escrito nada. El siguiente intento es otro
      // intento: clave nueva. Y se relee el precio, que es lo que suele cambiar.
      setState(() {
        _enVuelo = false;
        _resultadoIncierto = false;
        _clave = nuevaClaveDeIdempotencia();
        _error = e.mensaje;
      });
      await _releerMercado();
    }
  }

  Future<void> _releerMercado() async {
    try {
      final m = await ref.read(repositorioProyectosProvider).estadoDeMercado(widget.proyecto.id);
      if (mounted) setState(() => _mercado = m);
    } on ErrorApi {
      // Si no se puede releer, se queda el precio anterior; el servidor volvera a
      // rechazar la compra si de verdad ha cambiado.
    }
  }

  @override
  Widget build(BuildContext context) {
    final bloqueo = _bloqueo;
    final puedeComprar = !_enVuelo && (_resultadoIncierto || bloqueo == null);

    return PopScope(
      // Con la peticion en vuelo no se deja cerrar: el alumno veria la hoja
      // desaparecer sin saber si ha comprado.
      canPop: !_enVuelo,
      child: Padding(
        padding: EdgeInsets.only(
          left: 24,
          right: 24,
          top: 24,
          bottom: MediaQuery.of(context).viewInsets.bottom + 24,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Comprar en ${widget.proyecto.nombre}',
                style: const TextStyle(
                    color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold)),
            const SizedBox(height: 16),
            _dato('Precio por participación',
                '${_mercado.precioActual.toStringAsFixed(2)} JICP'),
            _dato('Disponibles',
                formatoParticipaciones(_mercado.participacionesDisponibles)),
            if (widget.saldo != null)
              _dato('Tu saldo', '${widget.saldo!.toStringAsFixed(2)} JICP'),
            const SizedBox(height: 16),
            TextField(
              controller: _cantidad,
              enabled: !_enVuelo && !_resultadoIncierto,
              autofocus: true,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              style: const TextStyle(
                  color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold),
              decoration: InputDecoration(
                labelText: 'Participaciones',
                labelStyle: const TextStyle(color: Colors.white54),
                filled: true,
                fillColor: Colors.black,
                hintText: '0',
                hintStyle: const TextStyle(color: Colors.white10),
                border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(16), borderSide: BorderSide.none),
                focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(16),
                  borderSide: const BorderSide(color: _dorado, width: 2),
                ),
              ),
            ),
            const SizedBox(height: 12),
            if (_importe != null)
              _dato('Importe', '${_importe!.toStringAsFixed(2)} JICP', destacado: true),
            if (bloqueo != null && bloqueo.isNotEmpty && !_resultadoIncierto)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text(bloqueo,
                    style: const TextStyle(color: Colors.white54, fontSize: 13)),
              ),
            if (_error != null)
              Container(
                margin: const EdgeInsets.only(top: 12),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.redAccent.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: Colors.redAccent.withValues(alpha: 0.4)),
                ),
                child: Text(_error!,
                    style: const TextStyle(color: Colors.redAccent, fontSize: 13)),
              ),
            const SizedBox(height: 20),
            FilledButton(
              onPressed: puedeComprar ? _comprar : null,
              style: FilledButton.styleFrom(
                backgroundColor: _dorado,
                foregroundColor: Colors.black,
                disabledBackgroundColor: Colors.white10,
                minimumSize: const Size(double.infinity, 56),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              ),
              child: _enVuelo
                  ? const SizedBox(
                      width: 22,
                      height: 22,
                      child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.black),
                    )
                  : Text(
                      _resultadoIncierto ? 'Reintentar compra' : 'Confirmar compra',
                      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _dato(String etiqueta, String valor, {bool destacado = false}) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 3),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(etiqueta, style: const TextStyle(color: Colors.white54)),
            Text(valor,
                style: TextStyle(
                  color: destacado ? _dorado : Colors.white,
                  fontWeight: FontWeight.bold,
                  fontSize: destacado ? 17 : 14,
                )),
          ],
        ),
      );
}

/// Participaciones sin ceros de sobra: `4`, `2.5`, `0.1234`.
String formatoParticipaciones(double n) {
  final texto = n.toStringAsFixed(4);
  return texto.contains('.')
      ? texto.replaceFirst(RegExp(r'0+$'), '').replaceFirst(RegExp(r'\.$'), '')
      : texto;
}
