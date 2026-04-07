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
  List<dynamic> _targetOptions = [];
  List<dynamic> _purposeOptions = [];
  bool _loading = true;
  String? _error;

  final _newTitle = TextEditingController();
  String _newTargetOptionId = '';
  String _newPurposeOptionId = '';
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
      _targetOptions = await ApiClient.fetchChecklistTargetOptions();
      _purposeOptions = await ApiClient.fetchChecklistPurposeOptions();
      if (_newTargetOptionId.isEmpty && _targetOptions.isNotEmpty) {
        _newTargetOptionId = (_targetOptions.first as Map)['id']?.toString() ?? '';
      }
      if (_newPurposeOptionId.isEmpty && _purposeOptions.isNotEmpty) {
        _newPurposeOptionId = (_purposeOptions.first as Map)['id']?.toString() ?? '';
      }
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

  String _routingForTargetId(String id) {
    for (final o in _targetOptions) {
      final m = o as Map<String, dynamic>;
      if (m['id']?.toString() == id) {
        return m['routingKind']?.toString() ?? 'SCHOOL';
      }
    }
    return 'SCHOOL';
  }

  Future<void> _createChecklist() async {
    if (_newGradeGroupId.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Select a grade group')));
      return;
    }
    if (_newTargetOptionId.isEmpty || _newPurposeOptionId.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Select a target and purpose (add options under the third tab if needed).')),
      );
      return;
    }
    try {
      final rk = _routingForTargetId(_newTargetOptionId);
      final auto = (rk == 'SCHOOL' || rk == 'DIRECTOR') && _newAutoAssignOnPublish;
      await ApiClient.createChecklist(
        title: _newTitle.text.trim(),
        targetOptionId: _newTargetOptionId,
        purposeOptionId: _newPurposeOptionId,
        gradeGroupId: _newGradeGroupId,
        autoAssignOnPublish: auto,
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
    String targetOpt = c['targetOptionId']?.toString() ?? '';
    String purposeOpt = c['purposeOptionId']?.toString() ?? '';
    String gg = c['gradeGroupId']?.toString() ?? '';
    var autoAssign = c['autoAssignOnPublish'] != false;
    final id = c['id']?.toString() ?? '';
    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setD) {
          final rk = _routingForTargetId(targetOpt);
          return AlertDialog(
            title: const Text('Edit checklist'),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextField(controller: title, decoration: const InputDecoration(labelText: 'Title')),
                  DropdownButtonFormField<String>(
                    value: targetOpt.isEmpty ? null : targetOpt,
                    decoration: const InputDecoration(labelText: 'Target'),
                    items: _targetOptions
                        .map((o) {
                          final m = o as Map<String, dynamic>;
                          final tid = m['id']?.toString() ?? '';
                          final label =
                              '${m['name']?.toString() ?? ''} (${m['routingKind']?.toString() ?? ''})';
                          return DropdownMenuItem(value: tid, child: Text(label));
                        })
                        .toList(),
                    onChanged: (v) => setD(() => targetOpt = v ?? ''),
                  ),
                  DropdownButtonFormField<String>(
                    value: purposeOpt.isEmpty ? null : purposeOpt,
                    decoration: const InputDecoration(labelText: 'Purpose'),
                    items: _purposeOptions
                        .map((o) {
                          final m = o as Map<String, dynamic>;
                          final pid = m['id']?.toString() ?? '';
                          return DropdownMenuItem(
                            value: pid,
                            child: Text(m['name']?.toString() ?? ''),
                          );
                        })
                        .toList(),
                    onChanged: (v) => setD(() => purposeOpt = v ?? ''),
                  ),
                  DropdownButtonFormField<String>(
                    value: gg.isEmpty ? null : gg,
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
                    subtitle: const Text('School or director routing'),
                    value: (rk == 'SCHOOL' || rk == 'DIRECTOR') && autoAssign,
                    onChanged: (rk == 'SCHOOL' || rk == 'DIRECTOR')
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
                    final rk2 = _routingForTargetId(targetOpt);
                    await ApiClient.patchChecklist(
                      checklistId: id,
                      title: title.text.trim(),
                      targetOptionId: targetOpt,
                      purposeOptionId: purposeOpt,
                      gradeGroupId: gg,
                      autoAssignOnPublish: (rk2 == 'SCHOOL' || rk2 == 'DIRECTOR') ? autoAssign : false,
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
          );
        },
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
              : DefaultTabController(
                  length: 3,
                  child: Column(
                    children: [
                      const TabBar(
                        tabs: [
                          Tab(text: 'New Checklist'),
                          Tab(text: 'Publish Items'),
                          Tab(text: 'Targets & purposes'),
                        ],
                      ),
                      Expanded(
                        child: TabBarView(
                          children: [
                            RefreshIndicator(
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
                                          Text('Title', style: Theme.of(context).textTheme.labelLarge),
                                          const SizedBox(height: 4),
                                          TextField(
                                            controller: _newTitle,
                                            decoration: const InputDecoration(
                                              hintText: 'e.g. Classroom observation',
                                              border: OutlineInputBorder(),
                                              isDense: true,
                                            ),
                                          ),
                                          const SizedBox(height: 12),
                                          Text('Target', style: Theme.of(context).textTheme.labelLarge),
                                          const SizedBox(height: 4),
                                          DropdownButtonFormField<String>(
                                            value: _newTargetOptionId.isEmpty ? null : _newTargetOptionId,
                                            decoration: const InputDecoration(
                                              border: OutlineInputBorder(),
                                              isDense: true,
                                              contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                                            ),
                                            hint: const Text('Select target'),
                                            items: _targetOptions
                                                .map((o) {
                                                  final m = o as Map<String, dynamic>;
                                                  final tid = m['id']?.toString() ?? '';
                                                  final label =
                                                      '${m['name']?.toString() ?? ''} (${m['routingKind']?.toString() ?? ''})';
                                                  return DropdownMenuItem(value: tid, child: Text(label));
                                                })
                                                .toList(),
                                            onChanged: (v) => setState(() => _newTargetOptionId = v ?? ''),
                                          ),
                                          const SizedBox(height: 12),
                                          Text('Purpose', style: Theme.of(context).textTheme.labelLarge),
                                          const SizedBox(height: 4),
                                          DropdownButtonFormField<String>(
                                            value: _newPurposeOptionId.isEmpty ? null : _newPurposeOptionId,
                                            decoration: const InputDecoration(
                                              border: OutlineInputBorder(),
                                              isDense: true,
                                              contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                                            ),
                                            hint: const Text('Select purpose'),
                                            items: _purposeOptions
                                                .map((o) {
                                                  final m = o as Map<String, dynamic>;
                                                  final pid = m['id']?.toString() ?? '';
                                                  return DropdownMenuItem(
                                                    value: pid,
                                                    child: Text(m['name']?.toString() ?? ''),
                                                  );
                                                })
                                                .toList(),
                                            onChanged: (v) => setState(() => _newPurposeOptionId = v ?? ''),
                                          ),
                                          const SizedBox(height: 12),
                                          Text('Grade group', style: Theme.of(context).textTheme.labelLarge),
                                          const SizedBox(height: 4),
                                          DropdownButtonFormField<String>(
                                            initialValue: _newGradeGroupId.isEmpty ? null : _newGradeGroupId,
                                            decoration: const InputDecoration(
                                              border: OutlineInputBorder(),
                                              isDense: true,
                                              contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                                            ),
                                            hint: const Text('Select a grade group'),
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
                                            subtitle: const Text('School or director routing'),
                                            value: (() {
                                              final rk = _routingForTargetId(_newTargetOptionId);
                                              return (rk == 'SCHOOL' || rk == 'DIRECTOR') && _newAutoAssignOnPublish;
                                            })(),
                                            onChanged: (() {
                                              final rk = _routingForTargetId(_newTargetOptionId);
                                              return (rk == 'SCHOOL' || rk == 'DIRECTOR')
                                                  ? (v) => setState(() => _newAutoAssignOnPublish = v)
                                                  : null;
                                            })(),
                                          ),
                                          const SizedBox(height: 8),
                                          FilledButton(onPressed: _createChecklist, child: const Text('Create checklist')),
                                        ],
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            RefreshIndicator(
                              onRefresh: _load,
                              child: ListView(
                                padding: const EdgeInsets.all(12),
                                children: [
                                  Text('Published checklists', style: Theme.of(context).textTheme.titleSmall),
                                  const SizedBox(height: 8),
                                  ..._checklists.map((c) {
                                    final m = c as Map<String, dynamic>;
                                    final id = m['id']?.toString() ?? '';
                                    final disabled = m['activeVersion'] == null;
                                    final target = m['targetType']?.toString() ?? '';
                                    final targetLabel = m['targetName']?.toString() ?? target;
                                    final autoLine = (target == 'SCHOOL' || target == 'DIRECTOR')
                                        ? (m['autoAssignOnPublish'] != false ? 'Auto-assign: on' : 'Auto-assign: off')
                                        : 'Auto-assign: —';
                                    final gradesLine = _checklistGradesSummary(m);
                                    return Card(
                                      child: ExpansionTile(
                                        title: Text(m['title']?.toString() ?? ''),
                                        subtitle: Text(
                                          '$targetLabel · ${m['purpose'] ?? ''} · v${m['activeVersion'] ?? '—'}\n$gradesLine\n$autoLine',
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
                            RefreshIndicator(
                              onRefresh: _load,
                              child: ListView(
                                padding: const EdgeInsets.all(12),
                                children: [
                                  Text('Targets', style: Theme.of(context).textTheme.titleSmall),
                                  const SizedBox(height: 8),
                                  ..._targetOptions.map((o) {
                                    final m = o as Map<String, dynamic>;
                                    final tid = m['id']?.toString() ?? '';
                                    return ListTile(
                                      title: Text(m['name']?.toString() ?? ''),
                                      subtitle: Text('Routing: ${m['routingKind'] ?? ''}'),
                                      trailing: Row(
                                        mainAxisSize: MainAxisSize.min,
                                        children: [
                                          IconButton(
                                            icon: const Icon(Icons.edit_outlined),
                                            onPressed: () => _promptTargetOption(
                                              id: tid,
                                              name: m['name']?.toString(),
                                              routing: m['routingKind']?.toString(),
                                            ),
                                          ),
                                          IconButton(
                                            icon: const Icon(Icons.delete_outline),
                                            onPressed: () => _deleteTargetOption(tid),
                                          ),
                                        ],
                                      ),
                                    );
                                  }),
                                  FilledButton.tonal(
                                    onPressed: () => _promptTargetOption(),
                                    child: const Text('Add target'),
                                  ),
                                  const SizedBox(height: 24),
                                  Text('Purposes', style: Theme.of(context).textTheme.titleSmall),
                                  const SizedBox(height: 8),
                                  ..._purposeOptions.map((o) {
                                    final m = o as Map<String, dynamic>;
                                    final pid = m['id']?.toString() ?? '';
                                    return ListTile(
                                      title: Text(m['name']?.toString() ?? ''),
                                      trailing: Row(
                                        mainAxisSize: MainAxisSize.min,
                                        children: [
                                          IconButton(
                                            icon: const Icon(Icons.edit_outlined),
                                            onPressed: () => _promptPurposeOption(
                                              id: pid,
                                              name: m['name']?.toString(),
                                            ),
                                          ),
                                          IconButton(
                                            icon: const Icon(Icons.delete_outline),
                                            onPressed: () => _deletePurposeOption(pid),
                                          ),
                                        ],
                                      ),
                                    );
                                  }),
                                  FilledButton.tonal(
                                    onPressed: () => _promptPurposeOption(),
                                    child: const Text('Add purpose'),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
    );
  }

  Future<void> _promptTargetOption({String? id, String? name, String? routing}) async {
    final nameCtrl = TextEditingController(text: name ?? '');
    var rk = routing ?? 'SCHOOL';
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setLocal) => AlertDialog(
          title: Text(id == null ? 'New target' : 'Edit target'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: nameCtrl,
                  decoration: const InputDecoration(labelText: 'Display name'),
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<String>(
                  value: rk,
                  decoration: const InputDecoration(labelText: 'Routing kind'),
                  items: const [
                    DropdownMenuItem(value: 'SCHOOL', child: Text('SCHOOL')),
                    DropdownMenuItem(value: 'TEACHER', child: Text('TEACHER')),
                    DropdownMenuItem(value: 'DIRECTOR', child: Text('DIRECTOR')),
                    DropdownMenuItem(value: 'SCHOOL_STAFF', child: Text('SCHOOL_STAFF')),
                  ],
                  onChanged: (v) => setLocal(() => rk = v ?? 'SCHOOL'),
                ),
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
    if (ok != true) return;
    final nm = nameCtrl.text.trim();
    if (nm.isEmpty) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Enter a name')));
      return;
    }
    try {
      if (id == null) {
        await ApiClient.postChecklistTargetOption(name: nm, routingKind: rk);
      } else {
        await ApiClient.patchChecklistTargetOption(id, {'name': nm, 'routingKind': rk});
      }
      await _load();
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Target saved')));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ApiClient.messageFromError(e))));
    }
  }

  Future<void> _deleteTargetOption(String id) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete target?'),
        content: const Text('Only allowed if no checklist uses this target.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Delete')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ApiClient.deleteChecklistTargetOption(id);
      await _load();
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Target deleted')));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ApiClient.messageFromError(e))));
    }
  }

  Future<void> _promptPurposeOption({String? id, String? name}) async {
    final nameCtrl = TextEditingController(text: name ?? '');
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(id == null ? 'New purpose' : 'Edit purpose'),
        content: TextField(
          controller: nameCtrl,
          decoration: const InputDecoration(labelText: 'Name'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Save')),
        ],
      ),
    );
    if (ok != true) return;
    final nm = nameCtrl.text.trim();
    if (nm.isEmpty) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Enter a name')));
      return;
    }
    try {
      if (id == null) {
        await ApiClient.postChecklistPurposeOption(name: nm);
      } else {
        await ApiClient.patchChecklistPurposeOption(id, {'name': nm});
      }
      await _load();
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Purpose saved')));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ApiClient.messageFromError(e))));
    }
  }

  Future<void> _deletePurposeOption(String id) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete purpose?'),
        content: const Text('Only allowed if no checklist uses this purpose.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Delete')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ApiClient.deleteChecklistPurposeOption(id);
      await _load();
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Purpose deleted')));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(ApiClient.messageFromError(e))));
    }
  }
}
