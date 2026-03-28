import 'package:flutter/material.dart';
import '../../../core/auth/session_store.dart';
import '../../../core/network/api_client.dart';
import '../../auth/presentation/login_screen.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  bool _loading = true;
  String? _error;
  Map<String, dynamic>? _me;
  Map<String, dynamic>? _status;
  String? _statusError;

  final _fullName = TextEditingController();
  final _email = TextEditingController();
  final _city = TextEditingController();
  final _subCity = TextEditingController();
  final _wereda = TextEditingController();
  final _currentPw = TextEditingController();
  final _newPw = TextEditingController();
  final _confirmPw = TextEditingController();

  bool _savingProfile = false;
  bool _changingPw = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _fullName.dispose();
    _email.dispose();
    _city.dispose();
    _subCity.dispose();
    _wereda.dispose();
    _currentPw.dispose();
    _newPw.dispose();
    _confirmPw.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
      _statusError = null;
    });
    try {
      final me = await ApiClient.fetchMe();
      Map<String, dynamic>? st;
      String? stErr;
      try {
        st = await ApiClient.fetchMyStatus();
      } catch (e) {
        st = null;
        stErr = ApiClient.messageFromError(e);
      }
      if (!mounted) return;
      setState(() {
        _me = me;
        _status = st;
        _statusError = stErr;
        _fullName.text = (me['fullName'] ?? '').toString();
        _email.text = (me['email'] ?? '').toString();
        _city.text = (me['city'] ?? '').toString();
        _subCity.text = (me['subCity'] ?? '').toString();
        _wereda.text = (me['wereda'] ?? '').toString();
      });
    } catch (e) {
      if (mounted) {
        setState(() => _error = ApiClient.messageFromError(e));
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _saveProfile() async {
    setState(() => _savingProfile = true);
    try {
      final updated = await ApiClient.patchMyProfile(
        fullName: _fullName.text.trim(),
        email: _email.text.trim(),
        city: _city.text.trim(),
        subCity: _subCity.text.trim(),
        wereda: _wereda.text.trim(),
      );
      if (!mounted) return;
      setState(() => _me = updated);
      SessionStore.currentUser = updated;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Profile updated')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(ApiClient.messageFromError(e))),
      );
    } finally {
      if (mounted) setState(() => _savingProfile = false);
    }
  }

  Future<void> _changePassword() async {
    if (_newPw.text != _confirmPw.text) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('New passwords do not match')),
      );
      return;
    }
    if (_newPw.text.length < 8) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('New password must be at least 8 characters')),
      );
      return;
    }
    setState(() => _changingPw = true);
    try {
      await ApiClient.changePassword(
        currentPassword: _currentPw.text,
        newPassword: _newPw.text,
      );
      if (!mounted) return;
      _currentPw.clear();
      _newPw.clear();
      _confirmPw.clear();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Password changed')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(ApiClient.messageFromError(e))),
      );
    } finally {
      if (mounted) setState(() => _changingPw = false);
    }
  }

  Widget _sectionTitle(String t) => Padding(
        padding: const EdgeInsets.fromLTRB(4, 20, 4, 8),
        child: Text(
          t,
          style: const TextStyle(
            fontWeight: FontWeight.w600,
            fontSize: 13,
            letterSpacing: 0.4,
            color: Color(0xFF64748B),
          ),
        ),
      );

  Widget _statusCard() {
    if (_statusError != null) {
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Text('Could not load status: $_statusError'),
        ),
      );
    }
    final s = _status;
    if (s == null) {
      return const SizedBox.shrink();
    }
    final w = s['supervisorWorkload'] as Map<String, dynamic>?;
    final c = s['coordinatorScope'] as Map<String, dynamic>?;
    final a = s['adminScope'] as Map<String, dynamic>?;

    if (w != null) {
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Supervisor workload', style: TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              Text('Assignments: ${w['totalAssignments'] ?? 0}'),
              Text('Completed: ${w['completedAssignments'] ?? 0} · Pending: ${w['pendingAssignments'] ?? 0}'),
              Text('In progress: ${w['inProgressAssignments'] ?? 0} · Overdue: ${w['overdueAssignments'] ?? 0}'),
              Text('Visits completed: ${w['visitsCompleted'] ?? 0}'),
            ],
          ),
        ),
      );
    }
    if (c != null) {
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Coordinator scope', style: TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              Text('Supervisors: ${c['supervisorsCount'] ?? 0}'),
              Text('Schools: ${c['schoolsCount'] ?? 0} · Teachers: ${c['teachersCount'] ?? 0}'),
              Text('Active assignments: ${c['activeAssignmentsCount'] ?? 0}'),
            ],
          ),
        ),
      );
    }
    if (a != null) {
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Organization scope', style: TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              Text('Users: ${a['usersCount'] ?? 0} · Schools: ${a['schoolsCount'] ?? 0}'),
              Text('Supervisors: ${a['supervisorsCount'] ?? 0} · Coordinators: ${a['coordinatorsCount'] ?? 0}'),
            ],
          ),
        ),
      );
    }
    return const Card(
      child: Padding(
        padding: EdgeInsets.all(16),
        child: Text('No role-specific metrics for this user.'),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Profile'),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), onPressed: _load),
          IconButton(
            icon: const Icon(Icons.logout),
            tooltip: 'Sign out',
            onPressed: () {
              SessionStore.clear();
              Navigator.of(context).pushAndRemoveUntil(
                MaterialPageRoute(builder: (_) => const LoginScreen()),
                (_) => false,
              );
            },
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(_error!, textAlign: TextAlign.center),
                        const SizedBox(height: 16),
                        FilledButton(onPressed: _load, child: const Text('Retry')),
                      ],
                    ),
                  ),
                )
              : ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    if (_me != null) ...[
                      Text(
                        (_me!['username'] ?? '').toString(),
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      if ((_me!['roles'] as List?)?.isNotEmpty == true)
                        Text(
                          (_me!['roles'] as List).join(', '),
                          style: TextStyle(color: Colors.grey.shade600, fontSize: 13),
                        ),
                    ],
                    _sectionTitle('ACCOUNT STATUS'),
                    _statusCard(),
                    _sectionTitle('PROFILE'),
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          children: [
                            TextField(
                              controller: _fullName,
                              decoration: const InputDecoration(labelText: 'Full name'),
                            ),
                            const SizedBox(height: 8),
                            TextField(
                              controller: _email,
                              decoration: const InputDecoration(labelText: 'Email'),
                              keyboardType: TextInputType.emailAddress,
                            ),
                            const SizedBox(height: 8),
                            TextField(
                              controller: _city,
                              decoration: const InputDecoration(labelText: 'City'),
                            ),
                            const SizedBox(height: 8),
                            TextField(
                              controller: _subCity,
                              decoration: const InputDecoration(labelText: 'Sub city'),
                            ),
                            const SizedBox(height: 8),
                            TextField(
                              controller: _wereda,
                              decoration: const InputDecoration(labelText: 'Wereda'),
                            ),
                            const SizedBox(height: 16),
                            SizedBox(
                              width: double.infinity,
                              child: FilledButton(
                                onPressed: _savingProfile ? null : _saveProfile,
                                child: Text(_savingProfile ? 'Saving…' : 'Save profile'),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    _sectionTitle('CHANGE PASSWORD'),
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          children: [
                            TextField(
                              controller: _currentPw,
                              obscureText: true,
                              decoration: const InputDecoration(labelText: 'Current password'),
                            ),
                            const SizedBox(height: 8),
                            TextField(
                              controller: _newPw,
                              obscureText: true,
                              decoration: const InputDecoration(labelText: 'New password (min 8)'),
                            ),
                            const SizedBox(height: 8),
                            TextField(
                              controller: _confirmPw,
                              obscureText: true,
                              decoration: const InputDecoration(labelText: 'Confirm new password'),
                            ),
                            const SizedBox(height: 16),
                            SizedBox(
                              width: double.infinity,
                              child: OutlinedButton(
                                onPressed: _changingPw ? null : _changePassword,
                                child: Text(_changingPw ? 'Updating…' : 'Update password'),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
    );
  }
}
