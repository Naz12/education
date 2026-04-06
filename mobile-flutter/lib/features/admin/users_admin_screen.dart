import 'package:flutter/material.dart';
import '../../core/auth/session_store.dart';
import '../../core/grades/grade_codes.dart';
import '../../core/network/api_client.dart';
import '../../core/widgets/grade_code_picker.dart';
import '../../core/widgets/location_wereda_picker.dart';

class UsersAdminScreen extends StatefulWidget {
  const UsersAdminScreen({super.key});

  @override
  State<UsersAdminScreen> createState() => _UsersAdminScreenState();
}

class _UsersAdminScreenState extends State<UsersAdminScreen> {
  List<dynamic> _coordinators = [];
  List<dynamic> _supervisors = [];
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
      if (SessionStore.isSuperAdmin) {
        _coordinators = await ApiClient.fetchClusterCoordinators();
      } else {
        _coordinators = [];
      }
      _supervisors = await ApiClient.fetchSupervisorsDirectory();
    } catch (e) {
      _error = ApiClient.messageFromError(e);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _showCoordinatorDialog() async {
    final fullName = TextEditingController();
    final username = TextEditingController();
    final password = TextEditingController();
    final email = TextEditingController();
    final phone = TextEditingController();
    final locIds = <String>['', '', ''];
    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setD) => AlertDialog(
          title: const Text('New cluster coordinator'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                    controller: fullName,
                    decoration: const InputDecoration(labelText: 'Full name')),
                TextField(
                    controller: username,
                    decoration: const InputDecoration(labelText: 'Username')),
                TextField(
                    controller: password,
                    obscureText: true,
                    decoration: const InputDecoration(labelText: 'Password')),
                TextField(
                    controller: email,
                    decoration: const InputDecoration(labelText: 'Email')),
                TextField(
                    controller: phone,
                    decoration: const InputDecoration(labelText: 'Phone')),
                const SizedBox(height: 8),
                Text('Assign wereda',
                    style: TextStyle(
                        color: Colors.grey.shade700,
                        fontWeight: FontWeight.w600)),
                const SizedBox(height: 4),
                LocationWeredaPicker(
                  key: const ValueKey('new_coord_loc'),
                  initialCityId: '',
                  initialSubcityId: '',
                  initialWeredaId: '',
                  onChanged: (c, s, w) => setD(() {
                    locIds[0] = c;
                    locIds[1] = s;
                    locIds[2] = w;
                  }),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
            FilledButton(
              onPressed: () async {
                if (locIds[2].isEmpty) {
                  ScaffoldMessenger.of(ctx).showSnackBar(const SnackBar(
                      content: Text('Select city, sub city, and wereda')));
                  return;
                }
                try {
                  await ApiClient.createClusterCoordinator({
                    'fullName': fullName.text.trim(),
                    'username': username.text.trim(),
                    'password': password.text,
                    'email':
                        email.text.trim().isEmpty ? null : email.text.trim(),
                    'phone':
                        phone.text.trim().isEmpty ? null : phone.text.trim(),
                    'weredaId': locIds[2],
                  });
                  if (ctx.mounted) Navigator.pop(ctx);
                  _load();
                  if (mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Coordinator created')));
                  }
                } catch (e) {
                  if (ctx.mounted) {
                    ScaffoldMessenger.of(ctx).showSnackBar(
                        SnackBar(content: Text(ApiClient.messageFromError(e))));
                  }
                }
              },
              child: const Text('Create'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _editCoordinator(Map<String, dynamic> row) async {
    final id = row['id']?.toString() ?? '';
    if (id.isEmpty) return;
    final fullName =
        TextEditingController(text: row['fullName']?.toString() ?? '');
    final email = TextEditingController(text: row['email']?.toString() ?? '');
    final phone = TextEditingController(text: row['phone']?.toString() ?? '');
    final locIds = <String>[
      row['cityId']?.toString() ?? '',
      row['subcityId']?.toString() ?? '',
      row['weredaId']?.toString() ?? '',
    ];
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setD) => AlertDialog(
          title: const Text('Edit coordinator'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                    controller: fullName,
                    decoration: const InputDecoration(labelText: 'Full name')),
                TextField(
                    controller: email,
                    decoration: const InputDecoration(labelText: 'Email')),
                TextField(
                    controller: phone,
                    decoration: const InputDecoration(labelText: 'Phone')),
                const SizedBox(height: 8),
                LocationWeredaPicker(
                  key: ValueKey('edit_coord_$id'),
                  initialCityId: locIds[0],
                  initialSubcityId: locIds[1],
                  initialWeredaId: locIds[2],
                  onChanged: (c, s, w) => setD(() {
                    locIds[0] = c;
                    locIds[1] = s;
                    locIds[2] = w;
                  }),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(ctx, false),
                child: const Text('Cancel')),
            FilledButton(
                onPressed: () => Navigator.pop(ctx, true),
                child: const Text('Save')),
          ],
        ),
      ),
    );
    if (ok != true) return;
    try {
      final body = <String, dynamic>{
        'fullName': fullName.text.trim(),
        'email': email.text.trim().isEmpty ? null : email.text.trim(),
        'phone': phone.text.trim().isEmpty ? null : phone.text.trim(),
      };
      if (locIds[2].isNotEmpty) {
        body['weredaId'] = locIds[2];
      }
      await ApiClient.patchClusterCoordinator(id, body);
      await _load();
      if (mounted)
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('Coordinator updated')));
    } catch (e) {
      if (mounted)
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(ApiClient.messageFromError(e))));
    }
  }

  Future<void> _deleteCoordinator(String id) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete coordinator?'),
        content: const Text(
            'Deletion is blocked if schools or supervisors are assigned.'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Delete')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ApiClient.deleteClusterCoordinator(id);
      await _load();
      if (mounted)
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('Coordinator deleted')));
    } catch (e) {
      if (mounted)
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(ApiClient.messageFromError(e))));
    }
  }

  Future<void> _showSupervisorDialog() async {
    final fullName = TextEditingController();
    final username = TextEditingController();
    final password = TextEditingController();
    final email = TextEditingController();
    final phone = TextEditingController();
    final locIds = <String>['', '', ''];
    final gradeHolder = <Set<String>>[<String>{}];
    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setD) {
          return AlertDialog(
            title: const Text('New supervisor'),
            content: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextField(
                      controller: fullName,
                      decoration:
                          const InputDecoration(labelText: 'Full name')),
                  TextField(
                      controller: username,
                      decoration: const InputDecoration(labelText: 'Username')),
                  TextField(
                      controller: password,
                      obscureText: true,
                      decoration: const InputDecoration(labelText: 'Password')),
                  TextField(
                      controller: email,
                      decoration: const InputDecoration(labelText: 'Email')),
                  TextField(
                      controller: phone,
                      decoration: const InputDecoration(labelText: 'Phone')),
                  if (SessionStore.isSuperAdmin) ...[
                    const SizedBox(height: 8),
                    Text('Assign wereda',
                        style: TextStyle(
                            color: Colors.grey.shade700,
                            fontWeight: FontWeight.w600)),
                    const SizedBox(height: 4),
                    LocationWeredaPicker(
                      key: const ValueKey('new_sup_loc'),
                      initialCityId: '',
                      initialSubcityId: '',
                      initialWeredaId: '',
                      onChanged: (c, s, w) => setD(() {
                        locIds[0] = c;
                        locIds[1] = s;
                        locIds[2] = w;
                      }),
                    ),
                  ],
                  const SizedBox(height: 12),
                  Text('Grades this supervisor can supervise',
                      style: TextStyle(
                          color: Colors.grey.shade700,
                          fontWeight: FontWeight.w600)),
                  const SizedBox(height: 8),
                  GradeCodePicker(
                    selected: gradeHolder[0],
                    onChanged: (next) =>
                        setD(() => gradeHolder[0] = Set<String>.from(next)),
                  ),
                ],
              ),
            ),
            actions: [
              TextButton(
                  onPressed: () => Navigator.pop(ctx),
                  child: const Text('Cancel')),
              FilledButton(
                onPressed: () async {
                  if (gradeHolder[0].isEmpty) {
                    ScaffoldMessenger.of(ctx).showSnackBar(const SnackBar(
                        content: Text('Select at least one grade')));
                    return;
                  }
                  if (SessionStore.isSuperAdmin && locIds[2].isEmpty) {
                    ScaffoldMessenger.of(ctx).showSnackBar(const SnackBar(
                        content: Text('Select city, sub city, and wereda')));
                    return;
                  }
                  final codes = GradeCodes.ordered
                      .where(gradeHolder[0].contains)
                      .toList();
                  try {
                    final body = SessionStore.isSuperAdmin
                        ? {
                            'fullName': fullName.text.trim(),
                            'username': username.text.trim(),
                            'password': password.text,
                            'email': email.text.trim().isEmpty
                                ? null
                                : email.text.trim(),
                            'phone': phone.text.trim().isEmpty
                                ? null
                                : phone.text.trim(),
                            'weredaId': locIds[2],
                            'supervisedGradeCodes': codes,
                          }
                        : {
                            'fullName': fullName.text.trim(),
                            'username': username.text.trim(),
                            'password': password.text,
                            'email': email.text.trim().isEmpty
                                ? null
                                : email.text.trim(),
                            'phone': phone.text.trim().isEmpty
                                ? null
                                : phone.text.trim(),
                            'supervisedGradeCodes': codes,
                          };
                    await ApiClient.createSupervisorUser(body);
                    if (ctx.mounted) Navigator.pop(ctx);
                    _load();
                    if (mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('Supervisor created')));
                    }
                  } catch (e) {
                    if (ctx.mounted) {
                      ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(
                          content: Text(ApiClient.messageFromError(e))));
                    }
                  }
                },
                child: const Text('Create'),
              ),
            ],
          );
        },
      ),
    );
  }

  Future<void> _editSupervisor(Map<String, dynamic> row) async {
    final id = row['id']?.toString() ?? '';
    if (id.isEmpty) return;
    final fullName =
        TextEditingController(text: row['fullName']?.toString() ?? '');
    final email = TextEditingController(text: row['email']?.toString() ?? '');
    final phone = TextEditingController(text: row['phone']?.toString() ?? '');
    final locIds = <String>[
      row['cityId']?.toString() ?? '',
      row['subcityId']?.toString() ?? '',
      row['weredaId']?.toString() ?? '',
    ];
    final initialGrades = GradeCodes.parseList(row['supervisedGradeCodes']);
    final gradeHolder = <Set<String>>[
      Set<String>.from(
          initialGrades.isEmpty ? GradeCodes.ordered : initialGrades)
    ];
    final ok = await showDialog<Map<String, dynamic>?>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setD) {
          return AlertDialog(
            title: const Text('Edit supervisor'),
            content: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  TextField(
                      controller: fullName,
                      decoration:
                          const InputDecoration(labelText: 'Full name')),
                  TextField(
                      controller: email,
                      decoration: const InputDecoration(labelText: 'Email')),
                  TextField(
                      controller: phone,
                      decoration: const InputDecoration(labelText: 'Phone')),
                  if (SessionStore.isSuperAdmin) ...[
                    const SizedBox(height: 8),
                    LocationWeredaPicker(
                      key: ValueKey('edit_sup_$id'),
                      initialCityId: locIds[0],
                      initialSubcityId: locIds[1],
                      initialWeredaId: locIds[2],
                      onChanged: (c, s, w) => setD(() {
                        locIds[0] = c;
                        locIds[1] = s;
                        locIds[2] = w;
                      }),
                    ),
                  ],
                  const SizedBox(height: 12),
                  Text('Grades this supervisor can supervise',
                      style: TextStyle(
                          color: Colors.grey.shade700,
                          fontWeight: FontWeight.w600)),
                  const SizedBox(height: 8),
                  GradeCodePicker(
                    selected: gradeHolder[0],
                    onChanged: (next) =>
                        setD(() => gradeHolder[0] = Set<String>.from(next)),
                  ),
                ],
              ),
            ),
            actions: [
              TextButton(
                  onPressed: () => Navigator.pop(ctx, null),
                  child: const Text('Cancel')),
              FilledButton(
                  onPressed: () {
                    if (gradeHolder[0].isEmpty) {
                      ScaffoldMessenger.of(ctx).showSnackBar(const SnackBar(
                          content: Text('Select at least one grade')));
                      return;
                    }
                    Navigator.pop(ctx, {
                      'grades': Set<String>.from(gradeHolder[0]),
                      'weredaId': locIds[2],
                    });
                  },
                  child: const Text('Save')),
            ],
          );
        },
      ),
    );
    if (ok == null) return;
    final gradeSelected = ok['grades'] as Set<String>;
    try {
      final codes = GradeCodes.ordered.where(gradeSelected.contains).toList();
      final patch = <String, dynamic>{
        'fullName': fullName.text.trim(),
        'email': email.text.trim().isEmpty ? null : email.text.trim(),
        'phone': phone.text.trim().isEmpty ? null : phone.text.trim(),
        'supervisedGradeCodes': codes,
      };
      if (SessionStore.isSuperAdmin && (ok['weredaId'] as String).isNotEmpty) {
        patch['weredaId'] = ok['weredaId'];
      }
      await ApiClient.patchSupervisorUser(id, patch);
      await _load();
      if (mounted)
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('Supervisor updated')));
    } catch (e) {
      if (mounted)
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(ApiClient.messageFromError(e))));
    }
  }

  Future<void> _deleteSupervisor(String id) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete supervisor?'),
        content: const Text('Deletion is blocked if assignments exist.'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Delete')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ApiClient.deleteSupervisorUser(id);
      await _load();
      if (mounted)
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('Supervisor deleted')));
    } catch (e) {
      if (mounted)
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(ApiClient.messageFromError(e))));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Users'),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), onPressed: _load),
          PopupMenuButton<String>(
            onSelected: (v) {
              if (v == 'coord') _showCoordinatorDialog();
              if (v == 'sup') _showSupervisorDialog();
            },
            itemBuilder: (context) => [
              if (SessionStore.isSuperAdmin)
                const PopupMenuItem(
                    value: 'coord', child: Text('Add coordinator')),
              const PopupMenuItem(value: 'sup', child: Text('Add supervisor')),
            ],
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(
                  child: Padding(
                      padding: const EdgeInsets.all(24), child: Text(_error!)))
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView(
                    padding: const EdgeInsets.all(12),
                    children: [
                      if (SessionStore.isSuperAdmin) ...[
                        Text('Cluster coordinators',
                            style: Theme.of(context).textTheme.titleMedium),
                        const SizedBox(height: 8),
                        if (_coordinators.isEmpty)
                          const Text('None',
                              style: TextStyle(color: Colors.grey))
                        else
                          ..._coordinators.map((c) {
                            final m = c as Map<String, dynamic>;
                            return Card(
                              child: ListTile(
                                title: Text(m['fullName']?.toString() ?? ''),
                                subtitle: Text(m['username']?.toString() ?? ''),
                                trailing: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    IconButton(
                                        icon: const Icon(Icons.edit_outlined),
                                        onPressed: () => _editCoordinator(m)),
                                    IconButton(
                                      icon: const Icon(Icons.delete_outline),
                                      onPressed: () => _deleteCoordinator(
                                          m['id']?.toString() ?? ''),
                                    ),
                                  ],
                                ),
                              ),
                            );
                          }),
                        const SizedBox(height: 24),
                      ],
                      Text('Supervisors',
                          style: Theme.of(context).textTheme.titleMedium),
                      const SizedBox(height: 8),
                      if (_supervisors.isEmpty)
                        const Text('None', style: TextStyle(color: Colors.grey))
                      else
                        ..._supervisors.map((c) {
                          final m = c as Map<String, dynamic>;
                          final g =
                              GradeCodes.parseList(m['supervisedGradeCodes']);
                          final gradesLine = g.isEmpty
                              ? 'All grades (legacy)'
                              : 'Grades: ${g.join(', ')}';
                          return Card(
                            child: ListTile(
                              title: Text(m['fullName']?.toString() ?? ''),
                              subtitle: Text(
                                '${m['username'] ?? ''}\n$gradesLine',
                              ),
                              isThreeLine: true,
                              trailing: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  IconButton(
                                      icon: const Icon(Icons.edit_outlined),
                                      onPressed: () => _editSupervisor(m)),
                                  IconButton(
                                    icon: const Icon(Icons.delete_outline),
                                    onPressed: () => _deleteSupervisor(
                                        m['id']?.toString() ?? ''),
                                  ),
                                ],
                              ),
                            ),
                          );
                        }),
                    ],
                  ),
                ),
    );
  }
}
