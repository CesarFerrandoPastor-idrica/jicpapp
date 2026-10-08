import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/modelos_auth.dart';
import '../api/modelos_aula.dart';
import '../api/proveedores.dart';

const _dorado = Color(0xFFD4AF37);
const _superficie = Color(0xFF151515);

/// Ranking del alumnado del centro del profesor, contra la API.
///
/// El orden lo decide el servidor (rentabilidad frente a su saldo inicial). El
/// buscador solo filtra lo que se pinta: la posición de cada uno no cambia.
class RankingScreen extends ConsumerStatefulWidget {
  const RankingScreen({super.key, required this.busqueda});

  final String busqueda;

  @override
  ConsumerState<RankingScreen> createState() => _RankingScreenState();
}

class _RankingScreenState extends ConsumerState<RankingScreen> {
  List<EntradaDeRanking> _ranking = const [];
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
      final ranking = await ref.read(repositorioAulaProvider).ranking();
      if (!mounted) return;
      setState(() {
        _ranking = ranking;
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

    final texto = widget.busqueda.trim().toLowerCase();
    final filas = texto.isEmpty
        ? _ranking
        : _ranking.where((e) => e.nombreCompleto.toLowerCase().contains(texto)).toList();

    return RefreshIndicator(
      color: _dorado,
      backgroundColor: _superficie,
      onRefresh: _cargar,
      child: filas.isEmpty
          ? ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: [
                const SizedBox(height: 80),
                const Icon(Icons.emoji_events_outlined, color: Colors.white24, size: 56),
                const SizedBox(height: 16),
                Text(
                  _ranking.isEmpty
                      ? 'Tu centro todavía no tiene alumnado'
                      : 'Ningún alumno encaja con la búsqueda',
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: Colors.white54, fontSize: 15),
                ),
              ],
            )
          : ListView.builder(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.all(24),
              itemCount: filas.length,
              itemBuilder: (context, i) => _FilaDeRanking(entrada: filas[i]),
            ),
    );
  }
}

class _FilaDeRanking extends StatelessWidget {
  const _FilaDeRanking({required this.entrada});

  final EntradaDeRanking entrada;

  @override
  Widget build(BuildContext context) {
    final e = entrada;
    final gana = e.rentabilidad >= 0;
    final color = gana ? Colors.greenAccent : Colors.redAccent;
    final podio = e.posicion <= 3;

    return Container(
      margin: const EdgeInsets.only(bottom: 14),
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: _superficie,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: podio ? _dorado.withValues(alpha: 0.35) : Colors.white10),
      ),
      child: Row(
        children: [
          SizedBox(
            width: 48,
            child: Text('#${e.posicion}',
                style: TextStyle(
                  color: podio ? _dorado : Colors.white38,
                  fontSize: 22,
                  fontWeight: FontWeight.w900,
                )),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(e.nombreCompleto,
                    style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16)),
                const SizedBox(height: 4),
                Text(
                  'Patrimonio ${e.patrimonio.toStringAsFixed(2)} JICP',
                  style: const TextStyle(color: Colors.white54, fontSize: 12),
                ),
                Text(
                  'Saldo ${e.saldo.toStringAsFixed(2)} · Participaciones ${e.valorParticipaciones.toStringAsFixed(2)}',
                  style: const TextStyle(color: Colors.white38, fontSize: 11),
                ),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Icon(gana ? Icons.trending_up : Icons.trending_down, color: color, size: 18),
              Text('${gana ? '+' : ''}${e.rentabilidad.toStringAsFixed(2)}%',
                  style: TextStyle(color: color, fontWeight: FontWeight.bold, fontSize: 15)),
            ],
          ),
        ],
      ),
    );
  }
}
