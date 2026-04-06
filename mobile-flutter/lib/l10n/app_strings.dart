import 'package:flutter/material.dart';

/// English / Amharic UI strings. Use [of] with the current [BuildContext] locale.
class AppStrings {
  AppStrings._(this._am);

  final bool _am;

  static AppStrings of(BuildContext context) {
    final code = Localizations.localeOf(context).languageCode.toLowerCase();
    return AppStrings._(code.startsWith('am'));
  }

  String _e(String en, String am) => _am ? am : en;

  String get appTitle => _e('School Supervision', 'የትምህርት ቤት ክትትል');
  String get signIn => _e('Sign in', 'ግባ');
  String get signInToContinue => _e('Sign in to continue', 'ለመቀጠል ይግቡ');
  String get username => _e('Username', 'የተጠቃሚ ስም');
  String get password => _e('Password', 'የይለፍ ቃል');
  String get showPassword => _e('Show password', 'የይለፍ ቃል አሳይ');
  String get hidePassword => _e('Hide password', 'የይለፍ ቃል ደብቅ');
  String get demoAccounts => _e('Demo accounts', 'ማሳያ መለያዎች');
  String get signingIn => _e('Signing in…', 'በመግባት ላይ…');
  String signInFailed(String message) =>
      _e('Sign-in failed: $message', 'መግባት አልተሳካም፦ $message');
  String get language => _e('Language', 'ቋንቋ');
  String get english => _e('English', 'እንግሊዝኛ');
  String get amharic => _e('Amharic', 'አማርኛ');
  String get home => _e('Home', 'መነሻ');
  String get myAssignments => _e('My assignments', 'የእኔ ተግባሮች');
  String get users => _e('Users', 'ተጠቃሚዎች');
  String get checklists => _e('Checklists', 'የማረጋገጫ ዝርዝሮች');
  String get checklistItems => _e('Checklist items', 'የዝርዝር ነጥቦች');
  String get assignments => _e('Assignments', 'መመደቢያዎች');
  String get schools => _e('Schools', 'ትምህርት ቤቶች');
  String get schoolStuff => _e('School stuff', 'የትምህርት ቤት ሰራተኞች');
  String get activity => _e('Activity', 'እንቅስቃሴ');
  String get reports => _e('Reports', 'ሪፖርቶች');
  String get profile => _e('Profile', 'መገለጫ');
  String get signOut => _e('Sign out', 'ውጣ');
  String get administration => _e('Administration', 'አስተዳደር');
  String helloName(String name) => _e('Hello, $name', 'ሰላም፣ $name');
  String get homeBlurbAdmin => _e(
        'Use the menu to manage users, checklists, checklist items, assignments, schools, and school staff. Open Activity to audit supervisor visits, and Reports to download review PDFs.',
        'ተጠቃሚዎችን፣ ዝርዝሮችን፣ የዝርዝር ነጥቦችን፣ መመደቢያዎችን፣ ትምህርት ቤቶችን እና ሰራተኞችን ለማስተዳደር ምናሌውን ይጠቀሙ። የተቆጣጣሪ ጉብኝቶችን ለመመልከት እንቅስቃሴን፣ የPDF ሪፖርቶችን ለማውረድ ሪፖርቶችን ይክፈቱ።',
      );
  String get homeBlurbUser => _e(
        'Use Profile for your account and status. Open My assignments to run supervision visits.',
        'ለመለያዎ እና ሁኔታዎ መገለጫን ይጠቀሙ። የክትትል ጉብኝቶችን ለማከናወን የእኔ ተግባሮችን ይክፈቱ።',
      );
  String get mySupervisionWorkload => _e('My supervision workload', 'የክትትል ስራ ጭነት');
  String get total => _e('Total', 'ጠቅላላ');
  String get done => _e('Done', 'ተጠናቋል');
  String get pending => _e('Pending', 'በመጠባበቅ ላይ');
  String get inProgress => _e('In progress', 'በሂደት ላይ');
  String get overdue => _e('Overdue', 'ጊዜው ያለፈ');
  String get visits => _e('Visits', 'ጉብኝቶች');
  String get noSupervisorWorkload =>
      _e('No supervisor workload for this account.', 'ለዚህ መለያ የተቆጣጣሪ ስራ ጭነት የለም።');
  String get assignmentsCardTitle => _e('My assignments', 'የእኔ ተግባሮች');
  String get assignmentsCardSubtitle =>
      _e('Supervision tasks and checklists', 'የክትትል ተግባሮች እና ዝርዝሮች');
  String get profileTitle => _e('Profile', 'መገለጫ');
  String get signOutTooltip => _e('Sign out', 'ውጣ');
  String get retry => _e('Retry', 'እንደገና ሞክር');
  String get accountStatus => _e('ACCOUNT STATUS', 'የመለያ ሁኔታ');
  String get profileSection => _e('PROFILE', 'መገለጫ');
  String get changePasswordSection => _e('CHANGE PASSWORD', 'የይለፍ ቃል ለውጥ');
  String get fullName => _e('Full name', 'ሙሉ ስም');
  String get email => _e('Email', 'ኢሜይል');
  String get city => _e('City', 'ከተማ');
  String get subCity => _e('Sub city', 'ክፍለ ከተማ');
  String get wereda => _e('Wereda', 'ወረዳ');
  String get saving => _e('Saving…', 'በመቀመጥ ላይ…');
  String get saveProfile => _e('Save profile', 'መገለጫ አስቀምጥ');
  String get profileUpdated => _e('Profile updated', 'መገለጫ ተዘምኗል');
  String get currentPassword => _e('Current password', 'የአሁኑ የይለፍ ቃል');
  String get newPasswordMin8 => _e('New password (min 8)', 'አዲስ የይለፍ ቃል (ቢያንስ 8)');
  String get confirmNewPassword => _e('Confirm new password', 'አዲስ የይለፍ ቃል ያረጋግጡ');
  String get updating => _e('Updating…', 'በመዘመን ላይ…');
  String get updatePassword => _e('Update password', 'የይለፍ ቃል አዘምን');
  String get passwordsDoNotMatch =>
      _e('New passwords do not match', 'አዲሱ የይለፍ ቃላት አይጣጣሙም');
  String get passwordMinLength => _e(
        'New password must be at least 8 characters',
        'አዲሱ የይለፍ ቃል ቢያንስ 8 ቁምፊዎች መሆን አለበት',
      );
  String get passwordChanged => _e('Password changed', 'የይለፍ ቃል ተቀይሯል');
  String couldNotLoadStatus(String error) =>
      _e('Could not load status: $error', 'ሁኔታ መጫን አልተቻለም፦ $error');
  String get supervisorWorkload => _e('Supervisor workload', 'የተቆጣጣሪ ስራ ጭነት');
  String assignmentsCount(int count) =>
      _e('Assignments: $count', 'ተግባሮች፦ $count');
  String completedPendingLine(int c, int p) =>
      _e('Completed: $c · Pending: $p', 'ተጠናቋል፦ $c · በመጠባበቅ ላይ፦ $p');
  String inProgressOverdueLine(int i, int o) =>
      _e('In progress: $i · Overdue: $o', 'በሂደት ላይ፦ $i · ጊዜው ያለፈ፦ $o');
  String visitsCompletedLine(int v) =>
      _e('Visits completed: $v', 'የተጠናቀቁ ጉብኝቶች፦ $v');
  String get coordinatorScope => _e('Coordinator scope', 'የአስተባባሪ ወሰን');
  String coordinatorScopeLine1(int s) =>
      _e('Supervisors: $s', 'ተቆጣጣሪዎች፦ $s');
  String coordinatorScopeLine2(int sch, int t) =>
      _e('Schools: $sch · Teachers: $t', 'ትምህርት ቤቶች፦ $sch · መምህራን፦ $t');
  String activeAssignments(int n) =>
      _e('Active assignments: $n', 'ንቁ ተግባሮች፦ $n');
  String get organizationScope => _e('Organization scope', 'የድርጅት ወሰን');
  String orgScopeLine1(int u, int s) =>
      _e('Users: $u · Schools: $s', 'ተጠቃሚዎች፦ $u · ትምህርት ቤቶች፦ $s');
  String orgScopeLine2(int sv, int c) =>
      _e('Supervisors: $sv · Coordinators: $c', 'ተቆጣጣሪዎች፦ $sv · አስተባባሪዎች፦ $c');
  String get noRoleMetrics => _e(
        'No role-specific metrics for this user.',
        'ለዚህ ተጠቃሚ የሚመለከት ልዩ መለኪያ የለም።',
      );

  static int _i(dynamic v) {
    if (v == null) return 0;
    if (v is int) return v;
    if (v is num) return v.toInt();
    return int.tryParse(v.toString()) ?? 0;
  }

  /// Status lines from API maps (supervisor workload).
  String supervisorWorkloadBlock(Map<String, dynamic> w) {
    return '${assignmentsCount(_i(w['totalAssignments']))}\n'
        '${completedPendingLine(_i(w['completedAssignments']), _i(w['pendingAssignments']))}\n'
        '${inProgressOverdueLine(_i(w['inProgressAssignments']), _i(w['overdueAssignments']))}\n'
        '${visitsCompletedLine(_i(w['visitsCompleted']))}';
  }

  String coordinatorScopeBlock(Map<String, dynamic> c) {
    return '${coordinatorScopeLine1(_i(c['supervisorsCount']))}\n'
        '${coordinatorScopeLine2(_i(c['schoolsCount']), _i(c['teachersCount']))}\n'
        '${activeAssignments(_i(c['activeAssignmentsCount']))}';
  }

  String organizationScopeBlock(Map<String, dynamic> a) {
    return '${orgScopeLine1(_i(a['usersCount']), _i(a['schoolsCount']))}\n'
        '${orgScopeLine2(_i(a['supervisorsCount']), _i(a['coordinatorsCount']))}';
  }
}
