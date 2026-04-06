import 'package:flutter/material.dart';

/// Provides [setLocale] for drawer/profile language switches.
class AppLocaleScope extends InheritedWidget {
  const AppLocaleScope({
    super.key,
    required this.setLocale,
    required super.child,
  });

  final void Function(Locale locale) setLocale;

  static void setLocaleOf(BuildContext context, Locale locale) {
    final scope = context.dependOnInheritedWidgetOfExactType<AppLocaleScope>();
    scope?.setLocale(locale);
  }

  @override
  bool updateShouldNotify(covariant AppLocaleScope oldWidget) => false;
}
