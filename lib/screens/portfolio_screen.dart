import 'package:flutter/material.dart';

import '../api/modelos_auth.dart';
import '../api/modelos_proyecto.dart';
import '../api/sesion.dart';

const _dorado = Color(0xFFD4AF37);
const _superficie = Color(0xFF151515);

/// Portafolio del alumno, con dos caras del mismo dinero:
///
/// - **Mis Proyectos**: los que ha fundado o en los que participa. Salen de
///   `alumno_proyecto`, donde vive el rol.
/// - **Invertidos**: participaciones compradas en proyectos de otros, valoradas
///   a precio de hoy por el servidor.
class PortfolioScreen extends StatefulWidget {
  const PortfolioScreen({super.key});

  @override
  State<PortfolioScreen> createState() => _PortfolioScreenState();
}

class _PortfolioScreenState extends State<PortfolioScreen> {
  List<ProyectoConRol> _proyectos = const [];
  List<Posicion> _posiciones = const [];
  double? _saldo;

  bool _cargando = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  Future<void> _cargar() async {
    final idAlumno = Sesion.instancia.perfil?.alumno?.id;
    if (idAlumno == null) {
      setState(() {
        _error = 'Esta pantalla es del alumnado';
        _cargando = false;
      });
      return;
    }

    setState(() => _error = null);
    try {
      // En paralelo: son independientes y encadenarlas solo haria la espera mas larga.
      final resultados = await Future.wait([
        Sesion.instancia.proyectos.misProyectos(idAlumno),
        Sesion.instancia.proyectos.portafolio(),
        Sesion.instancia.proyectos.saldo(),
      ]);
      if (!mounted) return;
      setState(() {
        _proyectos = resultados[0] as List<ProyectoConRol>;
        _posiciones = resultados[1] as List<Posicion>;
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

  /// Lo que valen hoy TODAS las participaciones del alumno, incluidas las de sus
  /// propios proyectos. Se suma el `valorActual` que ya calculo el servidor: la
  /// app no revaloriza nada por su cuenta.
  double get _valorDeParticipaciones =>
      _posiciones.fold(0.0, (suma, p) => suma + p.valorActual);

  /// Ids de los proyectos en los que participa, para no contarlos dos veces.
  Set<int> get _idsPropios => _proyectos.map((p) => p.idProyecto).toSet();

  /// Solo las inversiones en proyectos AJENOS.
  ///
  /// El fundador recibe participaciones por su inversion inicial, asi que sus
  /// propios proyectos tambien salen en `/portafolio`. Es correcto —las tiene de
  /// verdad—, pero enseñarlas aqui las duplicaria con la pestaña de al lado, asi
  /// que la separacion se hace al pintar y no pidiendole al servidor que mienta.
  List<Posicion> get _inversionesAjenas =>
      _posiciones.where((p) => !_idsPropios.contains(p.idProyecto)).toList();

  /// Las participaciones que el propio alumno tiene de un proyecto suyo.
  Posicion? _miParticipacionEn(int idProyecto) {
    for (final p in _posiciones) {
      if (p.idProyecto == idProyecto) return p;
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    if (_cargando) {
      return const Center(child: CircularProgressIndicator(color: _dorado));
    }
    if (_error != null) {
      return _pantallaDeError(_error!);
    }

    return Column(
      children: [
        _cabeceraDeSaldo(),
        Expanded(
          child: DefaultTabController(
            length: 2,
            child: Column(
              children: [
                TabBar(
                  indicatorColor: _dorado,
                  labelColor: _dorado,
                  unselectedLabelColor: Colors.white54,
                  labelStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                  unselectedLabelStyle:
                      const TextStyle(fontSize: 14, fontWeight: FontWeight.normal),
                  tabs: [
                    Tab(text: 'Mis Proyectos (${_proyectos.length})'),
                    Tab(text: 'Invertidos (${_inversionesAjenas.length})'),
                  ],
                ),
                Expanded(
                  child: TabBarView(
                    children: [
                      _listaDeProyectos(),
                      _listaDePosiciones(),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _cabeceraDeSaldo() => Container(
        padding: const EdgeInsets.all(24),
        decoration: const BoxDecoration(
          color: _superficie,
          border: Border(bottom: BorderSide(color: Colors.white10)),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            _dato('Saldo disponible', _saldo ?? 0,
                Icons.account_balance_wallet_outlined),
            Container(width: 1, height: 56, color: Colors.white10),
            _dato('Valor participaciones', _valorDeParticipaciones, Icons.trending_up),
          ],
        ),
      );

  Widget _dato(String etiqueta, double valor, IconData icono) => Expanded(
        child: Column(
          children: [
            Icon(icono, color: _dorado, size: 28),
            const SizedBox(height: 8),
            Text(etiqueta,
                style: const TextStyle(color: Colors.white54, fontSize: 12)),
            const SizedBox(height: 4),
            Text(
              '${valor.toStringAsFixed(2)} JICP',
              style: const TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.bold,
                fontSize: 17,
              ),
            ),
          ],
        ),
      );

  Widget _listaDeProyectos() {
    if (_proyectos.isEmpty) {
      return _vacio(
        Icons.rocket_launch_outlined,
        'Todavía no tienes proyectos',
        'Ve a la pestaña Crear y lanza el tuyo. Tu inversión inicial se '
            'convierte en participaciones.',
      );
    }

    return RefreshIndicator(
      color: _dorado,
      backgroundColor: _superficie,
      onRefresh: _cargar,
      child: ListView.builder(
        padding: const EdgeInsets.all(16),
        physics: const AlwaysScrollableScrollPhysics(),
        itemCount: _proyectos.length,
        itemBuilder: (context, i) => _tarjetaDeProyecto(_proyectos[i]),
      ),
    );
  }

  Widget _tarjetaDeProyecto(ProyectoConRol p) {
    final mias = _miParticipacionEn(p.idProyecto);
    return Container(
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: _superficie,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: p.soyElCreador ? _dorado.withValues(alpha: 0.35) : Colors.white12,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    p.nombre,
                    style: const TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.bold,
                      fontSize: 16,
                    ),
                  ),
                ),
                _etiqueta(p.rol, p.soyElCreador ? _dorado : Colors.white38),
              ],
            ),
            const SizedBox(height: 6),
            Row(
              children: [
                _etiqueta(p.categoria, Colors.white30, tenue: true),
                const SizedBox(width: 8),
                _etiqueta(p.estado, Colors.white30, tenue: true),
              ],
            ),
            const SizedBox(height: 16),

            // Progreso de la ronda: cuánto capital se ha colocado ya.
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: p.progresoDeRonda,
                minHeight: 6,
                backgroundColor: Colors.white10,
                valueColor: const AlwaysStoppedAnimation(_dorado),
              ),
            ),
            const SizedBox(height: 8),
            Text(
              '${p.participacionesEmitidas.toStringAsFixed(2)} de '
              '${p.participacionesTotales.toStringAsFixed(0)} participaciones colocadas',
              style: const TextStyle(color: Colors.white38, fontSize: 12),
            ),
            const SizedBox(height: 14),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                _cifra('Tu aportación', '${p.inversionInicial.toStringAsFixed(2)} JICP'),
                if (mias != null)
                  _cifra('Tus participaciones', mias.participaciones.toStringAsFixed(2)),
                _cifra('Precio ahora', '${p.precioActual.toStringAsFixed(2)} JICP',
                    destacado: true),
              ],
            ),
          ],
        ),
    );
  }

  Widget _listaDePosiciones() {
    final posiciones = _inversionesAjenas;
    if (posiciones.isEmpty) {
      return _vacio(
        Icons.pie_chart_outline,
        'Todavía no has invertido',
        'Entra en el Mercado y compra participaciones de los proyectos de tus '
            'compañeros.',
      );
    }

    return RefreshIndicator(
      color: _dorado,
      backgroundColor: _superficie,
      onRefresh: _cargar,
      child: ListView.builder(
        padding: const EdgeInsets.all(16),
        physics: const AlwaysScrollableScrollPhysics(),
        itemCount: posiciones.length,
        itemBuilder: (context, i) => _tarjetaDePosicion(posiciones[i]),
      ),
    );
  }

  Widget _tarjetaDePosicion(Posicion p) {
    final gana = p.plusvalia >= 0;
    final color = gana ? Colors.greenAccent : Colors.redAccent;

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: _superficie,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.white12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  p.nombreProyecto,
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.bold,
                    fontSize: 16,
                  ),
                ),
              ),
              Text(
                '${gana ? '+' : ''}${p.rentabilidad.toStringAsFixed(1)}%',
                style: TextStyle(color: color, fontWeight: FontWeight.bold, fontSize: 15),
              ),
            ],
          ),
          const SizedBox(height: 6),
          _etiqueta(p.categoria, Colors.white30, tenue: true),
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              _cifra('Participaciones', p.participaciones.toStringAsFixed(2)),
              _cifra('Precio medio', p.precioMedio.toStringAsFixed(2)),
              _cifra('Ahora', p.precioActual.toStringAsFixed(2), destacado: true),
            ],
          ),
          const SizedBox(height: 12),
          const Divider(color: Colors.white12, height: 1),
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text('Valor actual',
                  style: TextStyle(color: Colors.white54, fontSize: 13)),
              Text(
                '${p.valorActual.toStringAsFixed(2)} JICP  '
                '(${gana ? '+' : ''}${p.plusvalia.toStringAsFixed(2)})',
                style: TextStyle(color: color, fontSize: 13, fontWeight: FontWeight.bold),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _cifra(String etiqueta, String valor, {bool destacado = false}) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(etiqueta, style: const TextStyle(color: Colors.white38, fontSize: 11)),
          const SizedBox(height: 2),
          Text(
            valor,
            style: TextStyle(
              color: destacado ? _dorado : Colors.white,
              fontSize: 14,
              fontWeight: FontWeight.bold,
            ),
          ),
        ],
      );

  Widget _etiqueta(String texto, Color color, {bool tenue = false}) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(
          color: color.withValues(alpha: tenue ? 0.08 : 0.15),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: color.withValues(alpha: tenue ? 0.2 : 0.4)),
        ),
        child: Text(
          texto,
          style: TextStyle(
            color: tenue ? Colors.white54 : color,
            fontSize: 11,
            fontWeight: tenue ? FontWeight.normal : FontWeight.bold,
          ),
        ),
      );

  Widget _vacio(IconData icono, String titulo, String detalle) => RefreshIndicator(
        color: _dorado,
        backgroundColor: _superficie,
        onRefresh: _cargar,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [
            const SizedBox(height: 80),
            Icon(icono, color: Colors.white24, size: 56),
            const SizedBox(height: 16),
            Text(
              titulo,
              textAlign: TextAlign.center,
              style: const TextStyle(color: Colors.white54, fontSize: 16),
            ),
            const SizedBox(height: 8),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 48),
              child: Text(
                detalle,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.white24, fontSize: 13),
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
              Text(
                mensaje,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.white54, fontSize: 14),
              ),
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
