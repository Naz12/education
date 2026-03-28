import 'package:flutter/material.dart';
import '../../core/grades/grade_codes.dart';
import '../../core/network/api_client.dart';
import '../../core/widgets/grade_code_picker.dart';
import 'checklist_items_admin_screen.dart';
import 'type_defaults_screen.dart';

class ChecklistsAdminScreen extends StatefulWidget {
  const ChecklistsAdminScreen({super.key, required this.isSuperAdmin});

  final bool isSuperAdmin;

  @override
  State<ChecklistsAdminScreen> createState() => _ChecklistsAdminScreenState();
}

class _ChecklistsAdminScreenState extends State<ChecklistsAdminScreen> {
  List<dynamic> _checklists = [];
  List<dynamic> _gradeGroups = [];
  bool _loading = true;
  String? _error;

  final _newTitle = TextEditingController();
  String _newTarget = 'SCHOOL';
  String _newPurpose = 'CLINICAL_SUPERVISION';
  String _newGradeGroupId = '';
  bool _newAutoAssignOnPublish = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _newTitle.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      _checklists = await ApiClient.fetchChecklists();
      _gradeGroups = await ApiClient.fetchGradeGroups();
    } catch (e) {
      _error = ApiClient.messageFromError(e);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  static String _gradeGroupMenuLabel(Map<String, dynamic> m) {
    final codes = GradeCodes.parseList(m['gradeCodes']);
    final desc = m['gradesDescription']?.toString() ?? '';
    final dn = m['displayName']?.toString() ?? '';
    if (codes.isNotEmpty) return '$dn — ${codes.join(', ')}';
    if (desc.isNotEmpty) return '$dn — $desc';
    return dn;
  }

  static String _checklistGradesSummary(Map<String, dynamic> m) {
    final codes = GradeCodes.parseList(m['gradeGroupGradeCodes']);
    final name = m['gradeGroupDisplayName']?.toString() ?? '';
    if (codes.isNotEmpty) {
      return name.isEmpty ? codes.join(', ') : '$name: ${codes.join(', ')}';
    }
    return m['gradeGroupDisplayName']?.toString() ?? m['gradesDescription']?.toString() ?? '';
  }

  Future<void> _createGradeGroup() async {
    final name = TextEditingController();
    var selected = <String>{};
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setLocal) => AlertDialog(
          title: const Text('New grade group'),
          content: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(controller: name, decoration: const InputDecoration(labelText: 'Label')),
                const SizedBox(height: 12),
                Text('Grades in this group', style: Theme.of(ctx).textTheme.titleSmall),
                const SizedBox(height: 8),
                GradeCodePicker(
                  selected: selected,
                  onChanged: (next) => setLocal(() => selected = Set<String>.from(next)),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
            FilledButton(
              onPressed: () {
                if (name.text.trim().isEmpty) {
                  ScaffoldMessenger.of(ctx).showSnackBar(const SnackBar(content: Text('Enter a label')));
                  return;
                }
                if (selected.isEmpty) {
                  ScaffoldMessenger.of(ctx).showSnackBar(const SnackBar(content: Text('Select at least one grade')));
                  return;
                }
                Navigator.pop(ctx, true);
              },
              child: const Text('Save'),
            ),
          ],
        ),
      ),
    );
    if (ok != true) return;
    try {
      final codes = GradeCodes.ordered.where(selected.contains).toList();
      await ApiClient.createGradeGroup(displayName: name.text.trim(), gradeCodes: codes);
      await _load();
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Grade group created')));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ApiClient.messageFromError(e))));
    }
  }

  Future<void> _createChecklist() async {
    if (_newGradeGroupId.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Select a grade group')));
      return;
    }
    try {
      await ApiClient.createChecklist(
        title: _newTitle.text.trim(),
        targetType: _newTarget,
        purpose: _newPurpose,
        gradeGroupId: _newGradeGroupId,
        autoAssignOnPublish:
            (_newTarget == 'SCHOOL' || _newTarget == 'DIRECTOR') ? _newAutoAssignOnPublish : false,
      );
      _newTitle.clear();
      setState(() => _newAutoAssignOnPublish = true);
      await _load();
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Checklist created')));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ApiClient.messageFromError(e))));
    }
  }

  Future<void> _editChecklist(Map<String, dynamic> c) async {
    final title = TextEditingController(text: c['title']?.toString() ?? '');
    String target = c['targetType']?.toString() ?? 'SCHOOL';
    String purpose = c['purpose']?.toString() ?? 'CLINICAL_SUPERVISION';
    String gg = c['gradeGroupId']?.toString() ?? '';
    var autoAssign = c['autoAssignOnPublish'] != false;
    final id = c['id']?.toString() ?? '';
    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setD) => AlertDialog(
          title: const Text('Edit checklist'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(controller: title, decoration: const InputDecoration(labelText: 'Title')),
                DropdownButtonFormField<String>(
                  initialValue: target,
                  decoration: const InputDecoration(labelText: 'Target'),
                  items: const [
                    DropdownMenuItem(value: 'SCHOOL', child: Text('School')),
                    DropdownMenuItem(value: 'TEACHER', child: Text('Teacher')),
                  ],
                  onChanged: (v) => setD(() => target = v ?? 'SCHOOL'),
                ),
                DropdownButtonFormField<String>(
                  initialValue: purpose,
                  decoration: const InputDecoration(labelText: 'Purpose'),
                  items: const [
                    DropdownMenuItem(value: 'CLINICAL_SUPERVISION', child: Text('Clinical')),
                    DropdownMenuItem(value: 'ADMINISTRATIVE_SUPERVISION', child: Text('Administrative')),
                  ],
                  onChanged: (v) => setD(() => purpose = v ?? 'CLINICAL_SUPERVISION'),
                ),
                DropdownButtonFormField<String>(
                  initialValue: gg.isEmpty ? null : gg,
                  decoration: const InputDecoration(labelText: 'Grade group'),
                  items: _gradeGroups
                      .map((g) {
                        final m = g as Map<String, dynamic>;
                        return DropdownMenuItem(
                          value: m['id']?.toString(),
                          child: Text(_gradeGroupMenuLabel(m)),
                        );
                      })
                      .toList(),
                  onChanged: (v) => setD(() => gg = v ?? ''),
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Auto-assign on publish'),
                  subtitle: const Text('School or director target'),
                  value: (target == 'SCHOOL' || target == 'DIRECTOR') && autoAssign,
                  onChanged: (target == 'SCHOOL' || target == 'DIRECTOR')
                      ? (v) => setD(() => autoAssign = v)
                      : null,
                ),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
            FilledButton(
              onPressed: () async {
                try {
                  await ApiClient.patchChecklist(
                    checklistId: id,
                    title: title.text.trim(),
                    targetType: target,
                    purpose: purpose,
                    gradeGroupId: gg,
                    autoAssignOnPublish:
                        (target == 'SCHOOL' || target == 'DIRECTOR') ? autoAssign : false,
                  );
                  if (ctx.mounted) Navigator.pop(ctx);
                  await _load();
                } catch (e) {
                  if (ctx.mounted) {
                    ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(content: Text(ApiClient.messageFromError(e))));
                  }
                }
              },
              child: const Text('Save'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _deleteChecklist(String id) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete checklist?'),
        content: const Text('Blocked if used by assignments or has review answers.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Delete')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ApiClient.deleteChecklist(id);
      await _load();
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ApiClient.messageFromError(e))));
    }
  }

  Future<void> _toggle(String id, bool enable) async {
    try {
      await ApiClient.setChecklistEnabled(id, enable);
      await _load();
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ApiClient.messageFromError(e))));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Checklists'),
        actions: [
          if (widget.isSuperAdmin)
            IconButton(
              icon: const Icon(Icons.tune),
              tooltip: 'Default item types',
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const TypeDefaultsScreen()),
              ),
            ),
          IconButton(icon: const Icon(Icons.layers_outlined), onPressed: _createGradeGroup),
          IconButton(icon: const Icon(Icons.refresh), onPressed: _load),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Text(_error!))
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView(
                    padding: const EdgeInsets.all(12),
                    children: [
                      Card(
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              Text('New checklist', style: Theme.of(context).textTheme.titleSmall),
                              const SizedBox(height: 8),
                              TextField(controller: _newTitle, decoration: const InputDecoration(labelText: 'Title')),
                              DropdownButtonFormField<String>(
                                initialValue: _newTarget,
                                decoration: const InputDecoration(labelText: 'Target'),
                                items: const [
                                  DropdownMenuItem(value: 'SCHOOL', child: Text('School')),
                                  DropdownMenuItem(value: 'TEACHER', child: Text('Teacher')),
                                  DropdownMenuItem(value: 'DIRECTOR', child: Text('Director')),
                                  DropdownMenuItem(value: 'SCHOOL_STAFF', child: Text('School staff')),
                                ],
                                onChanged: (v) => setState(() => _newTarget = v ?? 'SCHOOL'),
                              ),
                              DropdownButtonFormField<String>(
                                initialValue: _newPurpose,
                                decoration: const InputDecoration(labelText: 'Purpose'),
                                items: const [
                                  DropdownMenuItem(value: 'CLINICAL_SUPERVISION', child: Text('Clinical')),
                                  DropdownMenuItem(value: 'ADMINISTRATIVE_SUPERVISION', child: Text('Administrative')),
                                ],
                                onChanged: (v) => setState(() => _newPurpose = v ?? 'CLINICAL_SUPERVISION'),
                              ),
                              DropdownButtonFormField<String>(
                                initialValue: _newGradeGroupId.isEmpty ? null : _newGradeGroupId,
                                decoration: const InputDecoration(labelText: 'Grade group'),
                                items: _gradeGroups
                                    .map((g) {
                                      final m = g as Map<String, dynamic>;
                                      return DropdownMenuItem(
                                        value: m['id']?.toString(),
                                        child: Text(_gradeGroupMenuLabel(m)),
                                      );
                                    })
                                    .toList(),
                                onChanged: (v) => setState(() => _newGradeGroupId = v ?? ''),
                              ),
                              SwitchListTile(
                                contentPadding: EdgeInsets.zero,
                                title: const Text('Auto-assign when published'),
                                subtitle: const Text('Assigns supervisors to matching schools (school target only)'),
                                value: _newTarget == 'SCHOOL' && _newAutoAssignOnPublish,
                                onChanged: _newTarget == 'SCHOOL' ? (v) => setState(() => _newAutoAssignOnPublish = v) : null,
                              ),
                              const SizedBox(height: 8),
                              FilledButton(onPressed: _createChecklist, child: const Text('Create checklist')),
                            ],
                          ),
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text('Published checklists', style: Theme.of(context).textTheme.titleSmall),
                      const SizedBox(height: 8),
                      ..._checklists.map((c) {
                        final m = c as Map<String, dynamic>;
                        final id = m['id']?.toString() ?? '';
                        final disabled = m['activeVersion'] == null;
                        final target = m['targetType']?.toString() ?? '';
                        final autoLine = (target == 'SCHOOL' || target == 'DIRECTOR')
                            ? (m['autoAssignOnPublish'] != false ? 'Auto-assign: on' : 'Auto-assign: off')
                            : 'Auto-assign: —';
                        final gradesLine = _checklistGradesSummary(m);
                        return Card(
                          child: ExpansionTile(
                            title: Text(m['title']?.toString() ?? ''),
                            subtitle: Text(
                              '$target · ${m['purpose'] ?? ''} · v${m['activeVersion'] ?? '—'}\n$gradesLine\n$autoLine',
                            ),
                            children: [
                              ListTile(
                                leading: const Icon(Icons.edit_note),
                                title: const Text('Edit items'),
                                onTap: () {
                                  Navigator.push(
                                    context,
                                    MaterialPageRoute(
                                      builder: (_) => ChecklistItemsAdminScreen(initialChecklistId: id),
                                    ),
                                  );
                                },
                              ),
                              ListTile(
                                leading: const Icon(Icons.edit_outlined),
                                title: const Text('Edit details'),
                                onTap: () => _editChecklist(m),
                              ),
                              ListTile(
                                leading: Icon(disabled ? Icons.play_circle_outline : Icons.pause_circle_outline),
                                title: Text(disabled ? 'Enable' : 'Disable'),
                                onTap: () => _toggle(id, disabled),
                              ),
                              ListTile(
                                leading: const Icon(Icons.delete_outline),
                                title: const Text('Delete'),
                                onTap: () => _deleteChecklist(id),
                              ),
                            ],
                          ),
                        );
                      }),
                    ],
                  ),
                ),
    );
  }
}
