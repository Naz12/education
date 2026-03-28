import 'package:flutter/material.dart';
import '../../core/grades/grade_codes.dart';
import '../../core/network/api_client.dart';
import '../../core/widgets/grade_code_picker.dart';

class SchoolsAdminScreen extends StatefulWidget {
  const SchoolsAdminScreen({super.key});

  @override
  State<SchoolsAdminScreen> createState() => _SchoolsAdminScreenState();
}

class _SchoolsAdminScreenState extends State<SchoolsAdminScreen> {
  final _filter = TextEditingController();
  List<dynamic> _schools = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _filter.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      _schools = await ApiClient.fetchSchools(q: _filter.text.trim().isEmpty ? null : _filter.text.trim());
    } catch (e) {
      _error = ApiClient.messageFromError(e);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _openSchoolForm({Map<String, dynamic>? existing}) async {
    final name = TextEditingController(text: existing?['name']?.toString() ?? '');
    final lat = TextEditingController(text: existing?['latitude']?.toString() ?? '9.03');
    final lon = TextEditingController(text: existing?['longitude']?.toString() ?? '38.74');
    final r = TextEditingController(text: existing?['allowedRadiusInMeters']?.toString() ?? '150');
    final id = existing?['id']?.toString();
    var selected = Set<String>.from(GradeCodes.parseList(existing?['supportedGradeCodes']));

    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setLocal) => AlertDialog(
          title: Text(id == null ? 'New school' : 'Edit school'),
          content: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(controller: name, decoration: const InputDecoration(labelText: 'Name')),
                const SizedBox(height: 12),
                Text('Grades this school serves', style: Theme.of(ctx).textTheme.titleSmall),
                const SizedBox(height: 8),
                GradeCodePicker(
                  selected: selected,
                  onChanged: (next) => setLocal(() => selected = Set<String>.from(next)),
                ),
                const SizedBox(height: 12),
                TextField(controller: lat, decoration: const InputDecoration(labelText: 'Latitude'), keyboardType: TextInputType.number),
                TextField(controller: lon, decoration: const InputDecoration(labelText: 'Longitude'), keyboardType: TextInputType.number),
                TextField(controller: r, decoration: const InputDecoration(labelText: 'Radius (m)'), keyboardType: TextInputType.number),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
            FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Save')),
          ],
        ),
      ),
    );
    if (ok != true || !mounted) return;
    try {
      final latN = double.tryParse(lat.text) ?? 0;
      final lonN = double.tryParse(lon.text) ?? 0;
      final rad = int.tryParse(r.text) ?? 150;
      final codes = GradeCodes.ordered.where(selected.contains).toList();
      if (id == null) {
        await ApiClient.createSchool(
          name: name.text.trim(),
          latitude: latN,
          longitude: lonN,
          allowedRadiusInMeters: rad,
          supportedGradeCodes: codes,
        );
      } else {
        await ApiClient.patchSchool(
          schoolId: id,
          name: name.text.trim(),
          latitude: latN,
          longitude: lonN,
          allowedRadiusInMeters: rad,
          supportedGradeCodes: codes,
        );
      }
      await _load();
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Saved')));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ApiClient.messageFromError(e))));
    }
  }

  Future<void> _confirmDelete(String schoolId) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete school?'),
        content: const Text('Blocked if teachers or assignments exist.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Delete')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ApiClient.deleteSchool(schoolId);
      await _load();
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Deleted')));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ApiClient.messageFromError(e))));
    }
  }

  String _gradesLine(Map<String, dynamic> m) {
    final g = GradeCodes.parseList(m['supportedGradeCodes']);
    if (g.isEmpty) return 'Grades: —';
    return 'Grades: ${g.join(', ')}';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Schools'),
        actions: [
          IconButton(icon: const Icon(Icons.add), onPressed: () => _openSchoolForm()),
          IconButton(icon: const Icon(Icons.refresh), onPressed: _load),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _filter,
                    decoration: const InputDecoration(
                      labelText: 'Filter by name',
                      isDense: true,
                    ),
                    onSubmitted: (_) => _load(),
                  ),
                ),
                TextButton(onPressed: _load, child: const Text('Apply')),
              ],
            ),
          ),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : _error != null
                    ? Center(child: Text(_error!))
                    : RefreshIndicator(
                        onRefresh: _load,
                        child: ListView.builder(
                          padding: const EdgeInsets.all(12),
                          itemCount: _schools.length,
                          itemBuilder: (context, i) {
                            final m = _schools[i] as Map<String, dynamic>;
                            final id = m['id']?.toString() ?? '';
                            return Card(
                              child: ListTile(
                                title: Text(m['name']?.toString() ?? ''),
                                subtitle: Text(
                                  '${_gradesLine(m)}\nLat ${m['latitude']} · Lon ${m['longitude']} · ${m['allowedRadiusInMeters']} m',
                                ),
                                isThreeLine: true,
                                trailing: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    IconButton(icon: const Icon(Icons.edit_outlined), onPressed: () => _openSchoolForm(existing: m)),
                                    IconButton(icon: const Icon(Icons.delete_outline), onPressed: () => _confirmDelete(id)),
                                  ],
                                ),
                              ),
                            );
                          },
                        ),
                      ),
          ),
        ],
      ),
    );
  }
}
