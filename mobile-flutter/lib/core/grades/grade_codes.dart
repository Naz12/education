/// Canonical grade codes — must stay aligned with backend `GradeCodes.ORDERED`.
class GradeCodes {
  GradeCodes._();

  static const List<String> ordered = [
    'KG1',
    'KG2',
    'KG3',
    '1',
    '2',
    '3',
    '4',
    '5',
    '6',
    '7',
    '8',
    '9',
    '10',
    '11',
    '12',
  ];

  static String displayLabel(String code) =>
      code.startsWith('KG') ? code.replaceFirst(RegExp(r'^KG'), 'KG ') : code;

  /// Parse JSON list from API; keeps only known codes, stable order.
  static List<String> parseList(dynamic raw) {
    if (raw is! List) return [];
    final out = <String>[];
    for (final e in raw) {
      final s = e?.toString() ?? '';
      if (ordered.contains(s)) out.add(s);
    }
    out.sort((a, b) => ordered.indexOf(a).compareTo(ordered.indexOf(b)));
    return out;
  }
}
