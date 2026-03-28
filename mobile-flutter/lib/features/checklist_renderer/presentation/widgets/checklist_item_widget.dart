import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class ChecklistItemWidget extends StatelessWidget {
  final Map<String, dynamic> item;
  final dynamic value;
  final ValueChanged<dynamic> onChanged;
  const ChecklistItemWidget({super.key, required this.item, required this.value, required this.onChanged});

  void _hapticLight() => HapticFeedback.lightImpact();

  @override
  Widget build(BuildContext context) {
    final type = item['type'] as String? ?? 'TEXT';
    final options = (item['options'] is Map) ? (item['options'] as Map<String, dynamic>) : <String, dynamic>{};
    final validation = (item['validation'] is Map) ? (item['validation'] as Map<String, dynamic>) : <String, dynamic>{};
    final choices = (options['choices'] is List) ? (options['choices'] as List) : <dynamic>[];
    final selectedYesNoIndex = value is bool
        ? (value ? 0 : 1)
        : (value is String ? choices.indexWhere((c) => c.toString() == value) : -1);
    final minRating = (validation['min'] is num) ? (validation['min'] as num).toDouble() : 1.0;
    final maxRating = (validation['max'] is num) ? (validation['max'] as num).toDouble() : 5.0;
    final currentRating = (value is num) ? (value as num).toDouble() : ((minRating + maxRating) / 2);
    final stepsRating = (maxRating - minRating).round() > 0 ? (maxRating - minRating).round() : 1;
    final cs = Theme.of(context).colorScheme;
    final requiredFlag = item['required'] == true;

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Card(
        clipBehavior: Clip.antiAlias,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Text(
                      item['question']?.toString() ?? '',
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(
                            fontWeight: FontWeight.w600,
                            height: 1.35,
                          ),
                    ),
                  ),
                  if (requiredFlag)
                    Padding(
                      padding: const EdgeInsets.only(left: 8, top: 2),
                      child: Text(
                        'Required',
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w600,
                          color: cs.primary,
                        ),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 14),
              switch (type) {
                'YES_NO' => (choices.length <= 2
                    ? Row(
                        children: [
                          Expanded(
                            child: FilledButton.tonal(
                              onPressed: () {
                                _hapticLight();
                                onChanged(choices.isNotEmpty ? choices[0].toString() : true);
                              },
                              style: FilledButton.styleFrom(
                                padding: const EdgeInsets.symmetric(vertical: 14),
                                backgroundColor: selectedYesNoIndex == 0 ? cs.primaryContainer : null,
                                foregroundColor: selectedYesNoIndex == 0 ? cs.onPrimaryContainer : null,
                              ),
                              child: Text(choices.isNotEmpty ? choices[0].toString() : 'Yes'),
                            ),
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: FilledButton.tonal(
                              onPressed: () {
                                _hapticLight();
                                onChanged(choices.length >= 2 ? choices[1].toString() : false);
                              },
                              style: FilledButton.styleFrom(
                                padding: const EdgeInsets.symmetric(vertical: 14),
                                backgroundColor: selectedYesNoIndex == 1 ? cs.primaryContainer : null,
                                foregroundColor: selectedYesNoIndex == 1 ? cs.onPrimaryContainer : null,
                              ),
                              child: Text(choices.length >= 2 ? choices[1].toString() : 'No'),
                            ),
                          ),
                        ],
                      )
                    : Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: choices.asMap().entries.map((entry) {
                          final idx = entry.key;
                          final label = entry.value.toString();
                          final selected = selectedYesNoIndex == idx;
                          return FilterChip(
                            label: Text(label),
                            selected: selected,
                            showCheckmark: true,
                            onSelected: (_) {
                              _hapticLight();
                              onChanged(label);
                            },
                          );
                        }).toList(),
                      )),
                'SINGLE_CHOICE' => DropdownButtonFormField<String>(
                    initialValue: value is String ? value : null,
                    decoration: const InputDecoration(
                      border: OutlineInputBorder(),
                      isDense: true,
                      contentPadding: EdgeInsets.symmetric(horizontal: 14, vertical: 14),
                    ),
                    items: choices
                        .map((c) => DropdownMenuItem<String>(
                              value: c.toString(),
                              child: Text(c.toString()),
                            ))
                        .toList(),
                    hint: const Text('Select an option'),
                    onChanged: (v) {
                      _hapticLight();
                      onChanged(v);
                    },
                  ),
                'MULTIPLE_CHOICE' => Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: choices.map((c) {
                      final selected =
                          (value is List) ? (value as List).map((x) => x.toString()).contains(c.toString()) : false;
                      return FilterChip(
                        label: Text(c.toString()),
                        selected: selected,
                        showCheckmark: true,
                        onSelected: (next) {
                          _hapticLight();
                          final current = (value is List) ? value as List : <dynamic>[];
                          final updated = current.map((x) => x.toString()).toSet();
                          if (next) {
                            updated.add(c.toString());
                          } else {
                            updated.remove(c.toString());
                          }
                          onChanged(updated.isEmpty ? null : updated.toList());
                        },
                      );
                    }).toList(),
                  ),
                'RATING' => Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Slider(
                        min: minRating,
                        max: maxRating,
                        divisions: stepsRating,
                        value: currentRating.clamp(minRating, maxRating),
                        onChanged: (v) {
                          HapticFeedback.selectionClick();
                          onChanged(v.round());
                        },
                      ),
                      Text(
                        'Selected: ${value is num ? (value as num).round() : currentRating.round()}',
                        style: TextStyle(fontSize: 13, color: cs.onSurfaceVariant, fontWeight: FontWeight.w500),
                      ),
                    ],
                  ),
                'PHOTO' => OutlinedButton.icon(
                    onPressed: () {
                      _hapticLight();
                      onChanged(['photo_placeholder.jpg']);
                    },
                    icon: const Icon(Icons.add_photo_alternate_outlined),
                    label: const Text('Attach photo'),
                  ),
                _ => TextField(
                    onChanged: onChanged,
                    minLines: 1,
                    maxLines: 4,
                    decoration: const InputDecoration(
                      hintText: 'Type your answer',
                      alignLabelWithHint: true,
                    ),
                  ),
              }
            ],
          ),
        ),
      ),
    );
  }
}
