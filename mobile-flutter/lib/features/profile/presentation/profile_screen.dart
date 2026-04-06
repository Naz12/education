import 'package:flutter/material.dart';
import '../../../core/auth/session_store.dart';
import '../../../core/locale/app_locale.dart';
import '../../../core/network/api_client.dart';
import '../../../l10n/app_strings.dart';
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
        SnackBar(content: Text(AppStrings.of(context).profileUpdated)),
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
        SnackBar(content: Text(AppStrings.of(context).passwordsDoNotMatch)),
      );
      return;
    }
    if (_newPw.text.length < 8) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppStrings.of(context).passwordMinLength)),
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
        SnackBar(content: Text(AppStrings.of(context).passwordChanged)),
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

  Widget _statusCard(AppStrings loc) {
    if (_statusError != null) {
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Text(loc.couldNotLoadStatus(_statusError!)),
        ),
      );
    }
    final st = _status;
    if (st == null) {
      return const SizedBox.shrink();
    }
    final w = st['supervisorWorkload'] as Map<String, dynamic>?;
    final c = st['coordinatorScope'] as Map<String, dynamic>?;
    final a = st['adminScope'] as Map<String, dynamic>?;

    if (w != null) {
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(loc.supervisorWorkload, style: const TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              Text(loc.supervisorWorkloadBlock(w)),
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
              Text(loc.coordinatorScope, style: const TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              Text(loc.coordinatorScopeBlock(c)),
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
              Text(loc.organizationScope, style: const TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              Text(loc.organizationScopeBlock(a)),
            ],
          ),
        ),
      );
    }
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Text(loc.noRoleMetrics),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final s = AppStrings.of(context);
    final lang = Localizations.localeOf(context).languageCode.toLowerCase().startsWith('am') ? 'am' : 'en';
    return Scaffold(
      appBar: AppBar(
        title: Text(s.profileTitle),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), onPressed: _load),
          IconButton(
            icon: const Icon(Icons.logout),
            tooltip: s.signOutTooltip,
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
                        FilledButton(onPressed: _load, child: Text(s.retry)),
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
                    _sectionTitle(s.accountStatus),
                    _statusCard(s),
                    Padding(
                      padding: const EdgeInsets.fromLTRB(4, 8, 4, 4),
                      child: Text(s.language, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13, color: Color(0xFF64748B))),
                    ),
                    Card(
                      child: Column(
                        children: [
                          RadioListTile<String>(
                            title: Text(s.english),
                            value: 'en',
                            groupValue: lang,
                            onChanged: (v) {
                              if (v != null) AppLocaleScope.setLocaleOf(context, Locale(v));
                              setState(() {});
                            },
                          ),
                          RadioListTile<String>(
                            title: Text(s.amharic),
                            value: 'am',
                            groupValue: lang,
                            onChanged: (v) {
                              if (v != null) AppLocaleScope.setLocaleOf(context, Locale(v));
                              setState(() {});
                            },
                          ),
                        ],
                      ),
                    ),
                    _sectionTitle(s.profileSection),
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          children: [
                            TextField(
                              controller: _fullName,
                              decoration: InputDecoration(labelText: s.fullName),
                            ),
                            const SizedBox(height: 8),
                            TextField(
                              controller: _email,
                              decoration: InputDecoration(labelText: s.email),
                              keyboardType: TextInputType.emailAddress,
                            ),
                            const SizedBox(height: 8),
                            TextField(
                              controller: _city,
                              decoration: InputDecoration(labelText: s.city),
                            ),
                            const SizedBox(height: 8),
                            TextField(
                              controller: _subCity,
                              decoration: InputDecoration(labelText: s.subCity),
                            ),
                            const SizedBox(height: 8),
                            TextField(
                              controller: _wereda,
                              decoration: InputDecoration(labelText: s.wereda),
                            ),
                            const SizedBox(height: 16),
                            SizedBox(
                              width: double.infinity,
                              child: FilledButton(
                                onPressed: _savingProfile ? null : _saveProfile,
                                child: Text(_savingProfile ? s.saving : s.saveProfile),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    _sectionTitle(s.changePasswordSection),
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          children: [
                            TextField(
                              controller: _currentPw,
                              obscureText: true,
                              decoration: InputDecoration(labelText: s.currentPassword),
                            ),
                            const SizedBox(height: 8),
                            TextField(
                              controller: _newPw,
                              obscureText: true,
                              decoration: InputDecoration(labelText: s.newPasswordMin8),
                            ),
                            const SizedBox(height: 8),
                            TextField(
                              controller: _confirmPw,
                              obscureText: true,
                              decoration: InputDecoration(labelText: s.confirmNewPassword),
                            ),
                            const SizedBox(height: 16),
                            SizedBox(
                              width: double.infinity,
                              child: OutlinedButton(
                                onPressed: _changingPw ? null : _changePassword,
                                child: Text(_changingPw ? s.updating : s.updatePassword),
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
