import 'package:flutter/material.dart';
import '../grades/grade_codes.dart';

/// Checkbox grid for canonical grades (matches web portal).
class GradeCodePicker extends StatelessWidget {
  const GradeCodePicker({
    super.key,
    required this.selected,
    required this.onChanged,
    this.enabled = true,
    /// When non-empty, only these codes are shown (e.g. school’s supported grades).
    this.codes,
  });

  final Set<String> selected;
  final void Function(Set<String> next) onChanged;
  final bool enabled;
  final List<String>? codes;

  @override
  Widget build(BuildContext context) {
    final list = (codes != null && codes!.isNotEmpty) ? codes! : GradeCodes.ordered;
    return Wrap(
      spacing: 8,
      runSpacing: 4,
      children: [
        for (final code in list)
          FilterChip(
            label: Text(GradeCodes.displayLabel(code)),
            selected: selected.contains(code),
            onSelected: enabled
                ? (on) {
                    final next = Set<String>.from(selected);
                    if (on) {
                      next.add(code);
                    } else {
                      next.remove(code);
                    }
                    onChanged(next);
                  }
                : null,
            showCheckmark: true,
          ),
      ],
    );
  }
}
