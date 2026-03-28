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
  List<dynamic> _rows = [];
  bool _loading = true;
  String? _error;
  String? _message;
  String? _openingReviewId;

  @override
  void initState() {
    super.initState();
    _load();
  }

  String _formatCompleted(String? iso) {
    if (iso == null || iso.isEmpty) return '—';
    final d = DateTime.tryParse(iso);
    if (d == null) return iso;
    return d.toLocal().toString().split('.').first;
  }

  String _placeLabel(Map<String, dynamic> row) {
    final tt = row['targetType']?.toString() ?? '';
    if (tt == 'TEACHER' && row['teacherName'] != null) {
      return row['teacherName'].toString();
    }
    if (tt == 'SCHOOL_STAFF' && row['staffFullName'] != null) {
      return row['staffFullName'].toString();
    }
    return row['schoolName']?.toString() ?? '—';
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      _rows = await ApiClient.fetchSubmittedReports();
    } catch (e) {
      _error = ApiClient.messageFromError(e);
      _rows = [];
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _openPdf(String reviewId) async {
    setState(() {
      _openingReviewId = reviewId;
      _message = null;
    });
    try {
      final bytes = await ApiClient.downloadReviewPdfBytes(reviewId);
      if (bytes.isEmpty) throw Exception('Empty PDF response');
      final dir = await getTemporaryDirectory();
      final file = File('${dir.path}/review_$reviewId.pdf');
      await file.writeAsBytes(bytes, flush: true);
      final result = await OpenFile.open(file.path);
      if (mounted) {
        setState(() => _message =
            result.type == ResultType.done ? 'Opened PDF' : result.message);
      }
    } catch (e) {
      if (mounted) setState(() => _message = ApiClient.messageFromError(e));
    } finally {
      if (mounted) setState(() => _openingReviewId = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Reports'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loading ? null : _load,
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Text(_error!, textAlign: TextAlign.center),
                  ),
                )
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView(
                    padding: const EdgeInsets.all(12),
                    children: [
                      Text(
                        'Submitted reviews (same list as the web portal). Tap to download and open the PDF.',
                        style: TextStyle(color: Colors.grey.shade700, height: 1.35),
                      ),
                      const SizedBox(height: 12),
                      if (_message != null)
                        Padding(
                          padding: const EdgeInsets.only(bottom: 8),
                          child: Text(
                            _message!,
                            style: TextStyle(
                              color: _message!.toLowerCase().contains('fail') ||
                                      _message!.toLowerCase().contains('error')
                                  ? Colors.red
                                  : Colors.green.shade800,
                            ),
                          ),
                        ),
                      if (_rows.isEmpty)
                        const Padding(
                          padding: EdgeInsets.symmetric(vertical: 32),
                          child: Center(child: Text('No submitted reports yet.')),
                        )
                      else
                        ..._rows.map((raw) {
                          final row = Map<String, dynamic>.from(raw as Map);
                          final reviewId = row['reviewId']?.toString() ?? '';
                          final opening = _openingReviewId == reviewId;
                          return Card(
                            child: ListTile(
                              title: Text(row['checklistTitle']?.toString() ?? '—'),
                              subtitle: Text(
                                '${_formatCompleted(row['completedAt']?.toString())}\n'
                                '${row['targetType'] ?? '—'} · ${_placeLabel(row)}',
                              ),
                              isThreeLine: true,
                              trailing: opening
                                  ? const SizedBox(
                                      width: 28,
                                      height: 28,
                                      child: CircularProgressIndicator(strokeWidth: 2),
                                    )
                                  : const Icon(Icons.picture_as_pdf_outlined),
                              onTap: opening || reviewId.isEmpty ? null : () => _openPdf(reviewId),
                            ),
                          );
                        }),
                      if (kIsWeb) ...[
                        const SizedBox(height: 16),
                        Text(
                          'On web builds, opening files may be limited; use Android/iOS for best results.',
                          style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                        ),
                      ],
                    ],
                  ),
                ),
    );
  }
}
