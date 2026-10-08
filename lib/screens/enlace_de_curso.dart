import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

const _dorado = Color(0xFFD4AF37);

/// El enlace de un curso (vídeo, documento...), pulsable.
///
/// Se abre fuera de la app, en el navegador o en la aplicación que el sistema tenga
/// para ese tipo de enlace (YouTube, el lector de PDF...). El servidor solo acepta
/// enlaces http(s), así que aquí no puede llegar un `javascript:` ni un esquema raro.
class EnlaceDeCurso extends StatelessWidget {
  const EnlaceDeCurso({super.key, required this.url});

  final String url;

  Future<void> _abrir(BuildContext context) async {
    final uri = Uri.tryParse(url);
    final abierto = uri != null && await launchUrl(uri, mode: LaunchMode.externalApplication);
    if (!abierto && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('No se ha podido abrir el enlace'),
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(8),
      onTap: () => _abrir(context),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          children: [
            const Icon(Icons.link, color: _dorado, size: 18),
            const SizedBox(width: 6),
            Expanded(
              child: Text(
                url,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(color: _dorado, decoration: TextDecoration.underline),
              ),
            ),
            const Icon(Icons.open_in_new, color: Colors.white38, size: 16),
          ],
        ),
      ),
    );
  }
}
