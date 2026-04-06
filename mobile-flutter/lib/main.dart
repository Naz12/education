import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'core/locale/app_locale.dart';
import 'core/network/api_client.dart';
import 'features/auth/presentation/login_screen.dart';
import 'l10n/app_strings.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const SupervisionApp());
}

class SupervisionApp extends StatefulWidget {
  const SupervisionApp({super.key});

  @override
  State<SupervisionApp> createState() => _SupervisionAppState();
}

class _SupervisionAppState extends State<SupervisionApp> {
  Locale _locale = const Locale('en');

  @override
  void initState() {
    super.initState();
    _loadLocale();
  }

  Future<void> _loadLocale() async {
    final prefs = await SharedPreferences.getInstance();
    final code = prefs.getString('app_locale') ?? '';
    final next = code == 'am' ? const Locale('am') : const Locale('en');
    if (!mounted) return;
    setState(() => _locale = next);
    ApiClient.setAppLanguageCode(next.languageCode);
  }

  Future<void> _setLocale(Locale locale) async {
    final code = locale.languageCode.toLowerCase().startsWith('am') ? 'am' : 'en';
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('app_locale', code);
    ApiClient.setAppLanguageCode(code);
    if (mounted) setState(() => _locale = Locale(code));
  }

  @override
  Widget build(BuildContext context) {
    const seed = Color(0xFF2563EB);
    final colorScheme = ColorScheme.fromSeed(seedColor: seed);
    final noto = GoogleFonts.notoSans();
    final ethiopicFamily = GoogleFonts.notoSansEthiopic().fontFamily;

    return AppLocaleScope(
      setLocale: _setLocale,
      child: MaterialApp(
        onGenerateTitle: (ctx) => AppStrings.of(ctx).appTitle,
        debugShowCheckedModeBanner: false,
        locale: _locale,
        supportedLocales: const [Locale('en'), Locale('am')],
        localizationsDelegates: const [
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        theme: ThemeData(
          useMaterial3: true,
          fontFamily: noto.fontFamily,
          fontFamilyFallback:
              ethiopicFamily != null ? <String>[ethiopicFamily] : null,
          colorScheme: colorScheme,
          scaffoldBackgroundColor: const Color(0xFFF1F5F9),
          splashFactory: InkSparkle.splashFactory,
          pageTransitionsTheme: const PageTransitionsTheme(
            builders: {
              TargetPlatform.android: FadeUpwardsPageTransitionsBuilder(),
              TargetPlatform.iOS: CupertinoPageTransitionsBuilder(),
              TargetPlatform.macOS: CupertinoPageTransitionsBuilder(),
              TargetPlatform.linux: FadeUpwardsPageTransitionsBuilder(),
              TargetPlatform.windows: FadeUpwardsPageTransitionsBuilder(),
            },
          ),
          appBarTheme: AppBarTheme(
            centerTitle: false,
            elevation: 0,
            scrolledUnderElevation: 2,
            backgroundColor: colorScheme.surface,
            foregroundColor: colorScheme.onSurface,
            surfaceTintColor: colorScheme.surfaceTint.withOpacity(0.08),
          ),
          cardTheme: CardThemeData(
            color: Colors.white,
            elevation: 0,
            margin: EdgeInsets.zero,
            surfaceTintColor: colorScheme.surfaceTint.withOpacity(0.06),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
              side: BorderSide(color: colorScheme.outlineVariant.withOpacity(0.5)),
            ),
          ),
          filledButtonTheme: FilledButtonThemeData(
            style: FilledButton.styleFrom(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
          ),
          outlinedButtonTheme: OutlinedButtonThemeData(
            style: OutlinedButton.styleFrom(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
          ),
          snackBarTheme: SnackBarThemeData(
            behavior: SnackBarBehavior.floating,
            elevation: 4,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          ),
          listTileTheme: ListTileThemeData(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          ),
          inputDecorationTheme: InputDecorationTheme(
            filled: true,
            fillColor: Colors.white,
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: BorderSide(color: colorScheme.outlineVariant),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: BorderSide(color: colorScheme.outlineVariant),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: BorderSide(color: colorScheme.primary, width: 1.5),
            ),
          ),
          floatingActionButtonTheme: FloatingActionButtonThemeData(
            elevation: 2,
            highlightElevation: 4,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          ),
        ),
        home: const LoginScreen(),
      ),
    );
  }
}
