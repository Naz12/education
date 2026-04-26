import 'package:flutter/material.dart';
import 'package:open_file/open_file.dart';
import 'package:path_provider/path_provider.dart';
import 'dart:io';
import '../../core/network/api_client.dart';

class SupervisionActivityScreen extends StatefulWidget {
  const SupervisionActivityScreen({super.key});

  @override
  State<SupervisionActivityScreen> createState() => _SupervisionActivityScreenState();
}

class _SupervisionActivityScreenState extends State<SupervisionActivityScreen> {
  List<dynamic> _summaries = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      _summaries = await ApiClient.fetchSupervisorSummaries();
    } catch (e) {
      _error = ApiClient.messageFromError(e);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _showVisits(String supervisorId, String label) async {
    showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => const AlertDialog(content: SizedBox(width: 48, height: 48, child: Center(child: CircularProgressIndicator()))),
    );
    List<dynamic> visits = [];
    try {
      visits = await ApiClient.fetchSupervisorVisits(supervisorId);
    } catch (_) {
      visits = [];
    }
    if (mounted) Navigator.pop(context);
    if (!mounted) return;
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.6,
        minChildSize: 0.3,
        maxChildSize: 0.95,
        builder: (_, scroll) => Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text(label, style: Theme.of(context).textTheme.titleMedium),
            ),
            Expanded(
              child: visits.isEmpty
                  ? const Center(child: Text('No completed visits.'))
                  : ListView.builder(
                      controller: scroll,
                      itemCount: visits.length,
                      itemBuilder: (context, i) {
                        final v = visits[i] as Map<String, dynamic>;
                        return ListTile(
                          title: Text(v['checklistTitle']?.toString() ?? ''),
                          subtitle: Text(
                            '${v['targetType']} · ${v['schoolName'] ?? ''}'
                            '${v['targetType'] == 'TEACHER' && v['teacherName'] != null ? ' · ${v['teacherName']}' : ''}'
                            '${v['targetType'] == 'SCHOOL_STAFF' && v['staffFullName'] != null ? ' · ${v['staffFullName']}' : ''}\n'
                            '${v['completedAt']} · ${v['locationStatus'] ?? ''}',
                          ),
                          isThreeLine: true,
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _exportActivity() async {
    try {
      final bytes = await ApiClient.downloadActivityExport();
      final dir = await getTemporaryDirectory();
      final file = File('${dir.path}/activity-export.xlsx');
      await file.writeAsBytes(bytes, flush: true);
      await OpenFile.open(file.path);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(ApiClient.messageFromError(e))));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Activity'),
        actions: [
          IconButton(icon: const Icon(Icons.download_outlined), tooltip: 'Export', onPressed: _exportActivity),
          IconButton(icon: const Icon(Icons.refresh), onPressed: _load),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Text(_error!))
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView.builder(
                    padding: const EdgeInsets.all(12),
                    itemCount: _summaries.length,
                    itemBuilder: (context, i) {
                      final s = _summaries[i] as Map<String, dynamic>;
                      final sid = s['supervisorId']?.toString() ?? '';
                      final name = '${s['fullName'] ?? ''} (${s['username'] ?? ''})';
                      return Card(
                        child: ListTile(
                          title: Text(name),
                          subtitle: Text(
                            'Visits: ${s['visitsCompleted'] ?? 0} · Done: ${s['completedAssignments'] ?? 0} · '
                            'Pending: ${s['pendingAssignments'] ?? 0} · Active: ${s['inProgressAssignments'] ?? 0} · '
                            'Overdue: ${s['overdueAssignments'] ?? 0}',
                          ),
                          isThreeLine: true,
                          trailing: const Icon(Icons.chevron_right),
                          onTap: () => _showVisits(sid, name),
                        ),
                      );
                    },
                  ),
                ),
    );
  }
}
