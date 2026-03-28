import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../../core/auth/session_store.dart';
import '../../../core/network/api_client.dart';
import '../../portal/presentation/portal_shell_screen.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> with SingleTickerProviderStateMixin {
  final _usernameController = TextEditingController(text: 'supervisor1');
  final _passwordController = TextEditingController(text: 'Supervisor@12345');
  bool _loading = false;
  String? _error;
  bool _obscurePassword = true;

  late AnimationController _entrance;
  late Animation<double> _fade;
  late Animation<Offset> _slide;

  @override
  void initState() {
    super.initState();
    _entrance = AnimationController(vsync: this, duration: const Duration(milliseconds: 900));
    _fade = CurvedAnimation(parent: _entrance, curve: Curves.easeOutCubic);
    _slide = Tween<Offset>(begin: const Offset(0, 0.06), end: Offset.zero).animate(_fade);
    _entrance.forward();
  }

  @override
  void dispose() {
    _entrance.dispose();
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _signIn() async {
    HapticFeedback.lightImpact();
    FocusScope.of(context).unfocus();
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await ApiClient.login(_usernameController.text.trim(), _passwordController.text);
      final me = await ApiClient.fetchMe();
      SessionStore.currentUser = me;
      if (!mounted) return;
      HapticFeedback.mediumImpact();
      Navigator.of(context).pushReplacement(
        PageRouteBuilder<void>(
          pageBuilder: (_, __, ___) => const PortalShellScreen(),
          transitionDuration: const Duration(milliseconds: 380),
          transitionsBuilder: (_, animation, __, child) {
            return FadeTransition(
              opacity: animation,
              child: SlideTransition(
                position: Tween<Offset>(begin: const Offset(0, 0.04), end: Offset.zero)
                    .animate(CurvedAnimation(parent: animation, curve: Curves.easeOutCubic)),
                child: child,
              ),
            );
          },
        ),
      );
    } catch (e) {
      HapticFeedback.heavyImpact();
      setState(() => _error = ApiClient.messageFromError(e));
      if (mounted) {
        final cs = Theme.of(context).colorScheme;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              'Sign-in failed: ${ApiClient.messageFromError(e)}',
              style: TextStyle(color: cs.onErrorContainer),
            ),
            backgroundColor: cs.errorContainer,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Scaffold(
      body: DecoratedBox(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [
              cs.primaryContainer.withOpacity(0.85),
              cs.surface,
              cs.secondaryContainer.withOpacity(0.35),
            ],
          ),
        ),
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 420),
                child: FadeTransition(
                  opacity: _fade,
                  child: SlideTransition(
                    position: _slide,
                    child: Card(
                      clipBehavior: Clip.antiAlias,
                      child: Padding(
                        padding: const EdgeInsets.all(24),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            Row(
                              children: [
                                DecoratedBox(
                                  decoration: BoxDecoration(
                                    color: cs.primary.withOpacity(0.12),
                                    borderRadius: BorderRadius.circular(14),
                                  ),
                                  child: Padding(
                                    padding: const EdgeInsets.all(12),
                                    child: Icon(Icons.school_rounded, size: 32, color: cs.primary),
                                  ),
                                ),
                                const SizedBox(width: 14),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        'School Supervision',
                                        style: Theme.of(context).textTheme.titleLarge?.copyWith(
                                              fontWeight: FontWeight.w700,
                                              letterSpacing: -0.3,
                                            ),
                                      ),
                                      Text(
                                        'Sign in to continue',
                                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                                              color: cs.onSurfaceVariant,
                                            ),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 20),
                            ExpansionTile(
                              tilePadding: EdgeInsets.zero,
                              childrenPadding: const EdgeInsets.only(top: 8, bottom: 4),
                              title: Text(
                                'Demo accounts',
                                style: TextStyle(
                                  fontSize: 13,
                                  fontWeight: FontWeight.w600,
                                  color: cs.primary,
                                ),
                              ),
                              children: [
                                Text(
                                  'superadmin / Admin@12345\n'
                                  'clustercoordinator / Coordinator@12345\n'
                                  'supervisor1 / Supervisor@12345',
                                  style: TextStyle(
                                    fontSize: 12,
                                    height: 1.45,
                                    color: Colors.grey.shade700,
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 8),
                            TextField(
                              controller: _usernameController,
                              textInputAction: TextInputAction.next,
                              autofillHints: const [AutofillHints.username],
                              decoration: const InputDecoration(
                                labelText: 'Username',
                                prefixIcon: Icon(Icons.person_outline_rounded),
                              ),
                            ),
                            const SizedBox(height: 12),
                            TextField(
                              controller: _passwordController,
                              obscureText: _obscurePassword,
                              onSubmitted: (_) => _loading ? null : _signIn(),
                              autofillHints: const [AutofillHints.password],
                              decoration: InputDecoration(
                                labelText: 'Password',
                                prefixIcon: const Icon(Icons.lock_outline_rounded),
                                suffixIcon: IconButton(
                                  tooltip: _obscurePassword ? 'Show password' : 'Hide password',
                                  onPressed: () {
                                    HapticFeedback.selectionClick();
                                    setState(() => _obscurePassword = !_obscurePassword);
                                  },
                                  icon: Icon(
                                    _obscurePassword ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                                  ),
                                ),
                              ),
                            ),
                            if (_error != null) ...[
                              const SizedBox(height: 12),
                              Semantics(
                                label: 'Login error',
                                child: Material(
                                  color: cs.errorContainer.withOpacity(0.9),
                                  borderRadius: BorderRadius.circular(12),
                                  child: Padding(
                                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                                    child: Row(
                                      children: [
                                        Icon(Icons.error_outline_rounded, color: cs.onErrorContainer, size: 20),
                                        const SizedBox(width: 10),
                                        Expanded(
                                          child: Text(
                                            _error!,
                                            style: TextStyle(
                                              color: cs.onErrorContainer,
                                              fontSize: 13,
                                              height: 1.35,
                                            ),
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                ),
                              ),
                            ],
                            const SizedBox(height: 22),
                            SizedBox(
                              height: 50,
                              child: FilledButton(
                                onPressed: _loading ? null : _signIn,
                                child: AnimatedSwitcher(
                                  duration: const Duration(milliseconds: 200),
                                  child: _loading
                                      ? SizedBox(
                                          key: const ValueKey('l'),
                                          height: 22,
                                          width: 22,
                                          child: CircularProgressIndicator(
                                            strokeWidth: 2.5,
                                            color: cs.onPrimary,
                                          ),
                                        )
                                      : const Text('Sign in', key: ValueKey('t')),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
