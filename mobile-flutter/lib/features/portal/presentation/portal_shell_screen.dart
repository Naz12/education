import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../../core/auth/session_store.dart';
import '../../../core/locale/app_locale.dart';
import '../../../l10n/app_strings.dart';
import '../../admin/assignments_admin_screen.dart';
import '../../admin/checklist_items_admin_screen.dart';
import '../../admin/checklists_admin_screen.dart';
import '../../admin/school_stuff_screen.dart';
import '../../admin/schools_admin_screen.dart';
import '../../admin/supervision_activity_screen.dart';
import '../../admin/users_admin_screen.dart';
import '../../assignments/presentation/assignments_screen.dart';
import '../../auth/presentation/login_screen.dart';
import '../../profile/presentation/profile_screen.dart';
import '../../reports/presentation/reports_screen.dart';
import 'home_portal_screen.dart';

/// Main shell after login: drawer navigation aligned with the web portal tabs.
class PortalShellScreen extends StatefulWidget {
  const PortalShellScreen({super.key});

  @override
  State<PortalShellScreen> createState() => _PortalShellScreenState();
}

class _PortalShellScreenState extends State<PortalShellScreen> {
  String _route = 'home';

  String _title(AppStrings s) {
    switch (_route) {
      case 'home':
        return s.home;
      case 'myAssignments':
        return s.myAssignments;
      case 'users':
        return s.users;
      case 'checklists':
        return s.checklists;
      case 'checklistItems':
        return s.checklistItems;
      case 'assignments':
        return s.assignments;
      case 'schools':
        return s.schools;
      case 'schoolStuff':
        return s.schoolStuff;
      case 'activity':
        return s.activity;
      case 'reports':
        return s.reports;
      default:
        return s.appTitle;
    }
  }

  Widget _body() {
    switch (_route) {
      case 'home':
        return const HomePortalScreen();
      case 'myAssignments':
        return const AssignmentsScreen();
      case 'users':
        return const UsersAdminScreen();
      case 'checklists':
        return ChecklistsAdminScreen(isSuperAdmin: SessionStore.isSuperAdmin);
      case 'checklistItems':
        return const ChecklistItemsAdminScreen();
      case 'assignments':
        return const AssignmentsAdminScreen();
      case 'schools':
        return const SchoolsAdminScreen();
      case 'schoolStuff':
        return const SchoolStuffScreen();
      case 'activity':
        return const SupervisionActivityScreen();
      case 'reports':
        return const ReportsScreen();
      default:
        return const HomePortalScreen();
    }
  }

  void _go(String route) {
    HapticFeedback.selectionClick();
    setState(() => _route = route);
    Navigator.pop(context);
  }

  void _signOut() {
    HapticFeedback.mediumImpact();
    SessionStore.clear();
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => const LoginScreen()),
      (_) => false,
    );
  }

  Widget _drawerTile({
    required IconData icon,
    required String title,
    required String route,
    VoidCallback? onTap,
  }) {
    final selected = _route == route;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      child: Material(
        color: selected ? Theme.of(context).colorScheme.primaryContainer.withOpacity(0.55) : Colors.transparent,
        borderRadius: BorderRadius.circular(12),
        child: ListTile(
          leading: Icon(icon, color: selected ? Theme.of(context).colorScheme.primary : null),
          title: Text(title),
          selected: selected,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          onTap: onTap ?? () => _go(route),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final can = SessionStore.canAdmin;
    final me = SessionStore.currentUser;
    final subtitle = me == null ? '' : '${me['fullName'] ?? SessionStore.username} · ${SessionStore.roles.join(', ')}';
    final cs = Theme.of(context).colorScheme;
    final s = AppStrings.of(context);
    final title = _title(s);

    return Scaffold(
      appBar: AppBar(
        title: AnimatedSwitcher(
          duration: const Duration(milliseconds: 220),
          switchInCurve: Curves.easeOut,
          switchOutCurve: Curves.easeIn,
          transitionBuilder: (child, anim) => FadeTransition(opacity: anim, child: child),
          child: Text(title, key: ValueKey(title)),
        ),
      ),
      drawer: Drawer(
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.only(topRight: Radius.circular(20), bottomRight: Radius.circular(20)),
        ),
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.only(bottom: 12),
            children: [
              DrawerHeader(
                margin: EdgeInsets.zero,
                padding: const EdgeInsets.fromLTRB(20, 12, 20, 12),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [
                      cs.primary,
                      Color.lerp(cs.primary, cs.primaryContainer, 0.4) ?? cs.primary,
                    ],
                  ),
                ),
                child: Align(
                  alignment: Alignment.bottomLeft,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      Row(
                        children: [
                          CircleAvatar(
                            radius: 22,
                            backgroundColor: Colors.white.withOpacity(0.25),
                            child: const Icon(Icons.account_circle_rounded, size: 32, color: Colors.white),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Text(
                              s.appTitle,
                              style: const TextStyle(
                                fontWeight: FontWeight.w700,
                                fontSize: 17,
                                color: Colors.white,
                                letterSpacing: -0.2,
                              ),
                            ),
                          ),
                        ],
                      ),
                      if (subtitle.isNotEmpty) ...[
                        const SizedBox(height: 10),
                        Text(
                          subtitle,
                          style: TextStyle(fontSize: 12, color: Colors.white.withOpacity(0.92), height: 1.35),
                          maxLines: 3,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 8),
              _drawerTile(icon: Icons.home_outlined, title: s.home, route: 'home'),
              if (!can)
                _drawerTile(
                  icon: Icons.assignment_outlined,
                  title: s.myAssignments,
                  route: 'myAssignments',
                ),
              if (can) ...[
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 16, 20, 6),
                  child: Text(
                    s.administration,
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 1.1,
                      color: cs.onSurfaceVariant,
                    ),
                  ),
                ),
                _drawerTile(icon: Icons.people_outline, title: s.users, route: 'users'),
                _drawerTile(icon: Icons.fact_check_outlined, title: s.checklists, route: 'checklists'),
                _drawerTile(icon: Icons.edit_note_rounded, title: s.checklistItems, route: 'checklistItems'),
                _drawerTile(icon: Icons.assignment_turned_in_outlined, title: s.assignments, route: 'assignments'),
                _drawerTile(icon: Icons.school_outlined, title: s.schools, route: 'schools'),
                _drawerTile(icon: Icons.groups_outlined, title: s.schoolStuff, route: 'schoolStuff'),
                _drawerTile(icon: Icons.timeline_outlined, title: s.activity, route: 'activity'),
                _drawerTile(icon: Icons.picture_as_pdf_outlined, title: s.reports, route: 'reports'),
              ],
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 4, 16, 0),
                child: Text(s.language, style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: cs.onSurfaceVariant)),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child: Column(
                  children: [
                    RadioListTile<String>(
                      dense: true,
                      value: 'en',
                      groupValue: Localizations.localeOf(context).languageCode.toLowerCase().startsWith('am') ? 'am' : 'en',
                      onChanged: (v) {
                        if (v != null) AppLocaleScope.setLocaleOf(context, Locale(v));
                        setState(() {});
                        Navigator.pop(context);
                      },
                      title: Text(s.english),
                    ),
                    RadioListTile<String>(
                      dense: true,
                      value: 'am',
                      groupValue: Localizations.localeOf(context).languageCode.toLowerCase().startsWith('am') ? 'am' : 'en',
                      onChanged: (v) {
                        if (v != null) AppLocaleScope.setLocaleOf(context, Locale(v));
                        setState(() {});
                        Navigator.pop(context);
                      },
                      title: Text(s.amharic),
                    ),
                  ],
                ),
              ),
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 8),
                child: Divider(height: 1),
              ),
              _drawerTile(
                icon: Icons.person_outline_rounded,
                title: s.profile,
                route: '',
                onTap: () {
                  HapticFeedback.selectionClick();
                  Navigator.pop(context);
                  Navigator.push(
                    context,
                    PageRouteBuilder<void>(
                      pageBuilder: (_, __, ___) => const ProfileScreen(),
                      transitionDuration: const Duration(milliseconds: 280),
                      transitionsBuilder: (_, animation, __, child) {
                        return SlideTransition(
                          position: Tween<Offset>(begin: const Offset(0.04, 0), end: Offset.zero)
                              .animate(CurvedAnimation(parent: animation, curve: Curves.easeOutCubic)),
                          child: FadeTransition(opacity: animation, child: child),
                        );
                      },
                    ),
                  );
                },
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child: ListTile(
                  leading: Icon(Icons.logout_rounded, color: cs.error),
                  title: Text(s.signOut, style: TextStyle(color: cs.error, fontWeight: FontWeight.w600)),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  onTap: _signOut,
                ),
              ),
            ],
          ),
        ),
      ),
      body: AnimatedSwitcher(
        duration: const Duration(milliseconds: 260),
        switchInCurve: Curves.easeOutCubic,
        switchOutCurve: Curves.easeInCubic,
        transitionBuilder: (child, animation) {
          final offset = Tween<Offset>(begin: const Offset(0, 0.02), end: Offset.zero)
              .animate(CurvedAnimation(parent: animation, curve: Curves.easeOutCubic));
          return FadeTransition(
            opacity: animation,
            child: SlideTransition(position: offset, child: child),
          );
        },
        child: KeyedSubtree(
          key: ValueKey<String>(_route),
          child: _body(),
        ),
      ),
    );
  }
}
