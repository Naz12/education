import 'package:flutter/material.dart';
import '../../core/auth/session_store.dart';
import '../../core/network/api_client.dart';

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
    final city = TextEditingController();
    final subCity = TextEditingController();
    final wereda = TextEditingController();
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
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
              TextField(
                  controller: city,
                  decoration: const InputDecoration(labelText: 'City')),
              TextField(
                  controller: subCity,
                  decoration: const InputDecoration(labelText: 'Sub city')),
              TextField(
                  controller: wereda,
                  decoration: const InputDecoration(labelText: 'Wereda')),
            ],
          ),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          FilledButton(
            onPressed: () async {
              try {
                await ApiClient.createClusterCoordinator({
                  'fullName': fullName.text.trim(),
                  'username': username.text.trim(),
                  'password': password.text,
                  'email': email.text.trim().isEmpty ? null : email.text.trim(),
                  'phone': phone.text.trim().isEmpty ? null : phone.text.trim(),
                  'city': city.text.trim().isEmpty ? null : city.text.trim(),
                  'subCity':
                      subCity.text.trim().isEmpty ? null : subCity.text.trim(),
                  'wereda':
                      wereda.text.trim().isEmpty ? null : wereda.text.trim(),
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
    );
  }

  Future<void> _editCoordinator(Map<String, dynamic> row) async {
    final id = row['id']?.toString() ?? '';
    if (id.isEmpty) return;
    final fullName =
        TextEditingController(text: row['fullName']?.toString() ?? '');
    final email = TextEditingController(text: row['email']?.toString() ?? '');
    final phone = TextEditingController(text: row['phone']?.toString() ?? '');
    final city = TextEditingController(text: row['city']?.toString() ?? '');
    final subCity =
        TextEditingController(text: row['subCity']?.toString() ?? '');
    final wereda = TextEditingController(text: row['wereda']?.toString() ?? '');
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
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
              TextField(
                  controller: city,
                  decoration: const InputDecoration(labelText: 'City')),
              TextField(
                  controller: subCity,
                  decoration: const InputDecoration(labelText: 'Sub city')),
              TextField(
                  controller: wereda,
                  decoration: const InputDecoration(labelText: 'Wereda')),
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
    );
    if (ok != true) return;
    try {
      await ApiClient.patchClusterCoordinator(id, {
        'fullName': fullName.text.trim(),
        'email': email.text.trim().isEmpty ? null : email.text.trim(),
        'phone': phone.text.trim().isEmpty ? null : phone.text.trim(),
        'city': city.text.trim().isEmpty ? null : city.text.trim(),
        'subCity': subCity.text.trim().isEmpty ? null : subCity.text.trim(),
        'wereda': wereda.text.trim().isEmpty ? null : wereda.text.trim(),
      });
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
    final city = TextEditingController();
    final subCity = TextEditingController();
    final wereda = TextEditingController();
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('New supervisor'),
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
              if (SessionStore.isSuperAdmin) ...[
                TextField(
                    controller: city,
                    decoration: const InputDecoration(labelText: 'City')),
                TextField(
                    controller: subCity,
                    decoration: const InputDecoration(labelText: 'Sub city')),
                TextField(
                    controller: wereda,
                    decoration: const InputDecoration(labelText: 'Wereda')),
              ],
            ],
          ),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          FilledButton(
            onPressed: () async {
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
                        'city':
                            city.text.trim().isEmpty ? null : city.text.trim(),
                        'subCity': subCity.text.trim().isEmpty
                            ? null
                            : subCity.text.trim(),
                        'wereda': wereda.text.trim().isEmpty
                            ? null
                            : wereda.text.trim(),
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
                  ScaffoldMessenger.of(ctx).showSnackBar(
                      SnackBar(content: Text(ApiClient.messageFromError(e))));
                }
              }
            },
            child: const Text('Create'),
          ),
        ],
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
    final city = TextEditingController(text: row['city']?.toString() ?? '');
    final subCity =
        TextEditingController(text: row['subCity']?.toString() ?? '');
    final wereda = TextEditingController(text: row['wereda']?.toString() ?? '');
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Edit supervisor'),
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
              if (SessionStore.isSuperAdmin) ...[
                TextField(
                    controller: city,
                    decoration: const InputDecoration(labelText: 'City')),
                TextField(
                    controller: subCity,
                    decoration: const InputDecoration(labelText: 'Sub city')),
                TextField(
                    controller: wereda,
                    decoration: const InputDecoration(labelText: 'Wereda')),
              ],
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
    );
    if (ok != true) return;
    try {
      await ApiClient.patchSupervisorUser(id, {
        'fullName': fullName.text.trim(),
        'email': email.text.trim().isEmpty ? null : email.text.trim(),
        'phone': phone.text.trim().isEmpty ? null : phone.text.trim(),
        if (SessionStore.isSuperAdmin)
          'city': city.text.trim().isEmpty ? null : city.text.trim(),
        if (SessionStore.isSuperAdmin)
          'subCity': subCity.text.trim().isEmpty ? null : subCity.text.trim(),
        if (SessionStore.isSuperAdmin)
          'wereda': wereda.text.trim().isEmpty ? null : wereda.text.trim(),
      });
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
                          return Card(
                            child: ListTile(
                              title: Text(m['fullName']?.toString() ?? ''),
                              subtitle: Text(m['username']?.toString() ?? ''),
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
