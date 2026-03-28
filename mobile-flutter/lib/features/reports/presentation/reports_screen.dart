import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:open_file/open_file.dart';
import 'package:path_provider/path_provider.dart';
import '../../../core/network/api_client.dart';

class ReportsScreen extends StatefulWidget {
  const ReportsScreen({super.key});

  @override
  State<ReportsScreen> createState() => _ReportsScreenState();
}

class _ReportsScreenState extends State<ReportsScreen> {
  final _reviewId = TextEditingController();
  bool _busy = false;
  String? _message;

  @override
  void dispose() {
    _reviewId.dispose();
    super.dispose();
  }

  Future<void> _openPdf() async {
    final id = _reviewId.text.trim();
    if (id.isEmpty) return;
    setState(() {
      _busy = true;
      _message = null;
    });
    try {
      final bytes = await ApiClient.downloadReviewPdfBytes(id);
      if (bytes.isEmpty) throw Exception('Empty PDF response');
      final dir = await getTemporaryDirectory();
      final file = File('${dir.path}/review_$id.pdf');
      await file.writeAsBytes(bytes, flush: true);
      final result = await OpenFile.open(file.path);
      if (mounted) {
        setState(() => _message = result.type == ResultType.done ? 'Opened PDF' : result.message);
      }
    } catch (e) {
      if (mounted) setState(() => _message = ApiClient.messageFromError(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Reports')),
      body: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Download a completed review as PDF (uses your login, same as the web portal).',
              style: TextStyle(color: Colors.grey.shade700, height: 1.4),
            ),
            const SizedBox(height: 20),
            TextField(
              controller: _reviewId,
              decoration: const InputDecoration(
                labelText: 'Review ID (UUID)',
                hintText: 'Paste review id',
              ),
            ),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: _busy ? null : _openPdf,
              icon: _busy
                  ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                  : const Icon(Icons.picture_as_pdf_outlined),
              label: Text(_busy ? 'Downloading…' : 'Download & open PDF'),
            ),
            if (_message != null) ...[
              const SizedBox(height: 16),
              Text(_message!, style: TextStyle(color: _message!.contains('fail') || _message!.contains('Error') ? Colors.red : Colors.green.shade800)),
            ],
            if (kIsWeb) ...[
              const SizedBox(height: 24),
              Text(
                'On web builds, file open may be limited; use Android/iOS for best results.',
                style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
