import 'package:flutter/material.dart';
import '../network/api_client.dart';

/// Cascading city → sub city → wereda (super-admin geography API).
class LocationWeredaPicker extends StatefulWidget {
  const LocationWeredaPicker({
    super.key,
    required this.initialCityId,
    required this.initialSubcityId,
    required this.initialWeredaId,
    required this.onChanged,
  });

  final String initialCityId;
  final String initialSubcityId;
  final String initialWeredaId;
  final void Function(String cityId, String subcityId, String weredaId)
      onChanged;

  @override
  State<LocationWeredaPicker> createState() => _LocationWeredaPickerState();
}

class _LocationWeredaPickerState extends State<LocationWeredaPicker> {
  List<dynamic> _cities = [];
  List<dynamic> _subcities = [];
  List<dynamic> _weredas = [];
  late String _cityId;
  late String _subcityId;
  late String _weredaId;
  bool _loadingCities = true;

  @override
  void initState() {
    super.initState();
    _cityId = widget.initialCityId;
    _subcityId = widget.initialSubcityId;
    _weredaId = widget.initialWeredaId;
    _bootstrap();
  }

  Future<void> _bootstrap() async {
    setState(() => _loadingCities = true);
    try {
      _cities = await ApiClient.fetchGeographyCities();
      if (_cityId.isNotEmpty) {
        _subcities = await ApiClient.fetchGeographySubcities(_cityId);
      } else {
        _subcities = [];
      }
      if (_subcityId.isNotEmpty) {
        _weredas = await ApiClient.fetchGeographyWeredas(_subcityId);
      } else {
        _weredas = [];
      }
    } catch (_) {
      _cities = [];
      _subcities = [];
      _weredas = [];
    }
    if (mounted) setState(() => _loadingCities = false);
  }

  Future<void> _onCity(String? v) async {
    final c = v ?? '';
    setState(() {
      _cityId = c;
      _subcityId = '';
      _weredaId = '';
      _subcities = [];
      _weredas = [];
    });
    widget.onChanged(_cityId, _subcityId, _weredaId);
    if (c.isEmpty) return;
    try {
      final list = await ApiClient.fetchGeographySubcities(c);
      if (mounted) setState(() => _subcities = list);
    } catch (_) {}
  }

  Future<void> _onSub(String? v) async {
    final s = v ?? '';
    setState(() {
      _subcityId = s;
      _weredaId = '';
      _weredas = [];
    });
    widget.onChanged(_cityId, _subcityId, _weredaId);
    if (s.isEmpty) return;
    try {
      final list = await ApiClient.fetchGeographyWeredas(s);
      if (mounted) setState(() => _weredas = list);
    } catch (_) {}
  }

  void _onWer(String? v) {
    setState(() => _weredaId = v ?? '');
    widget.onChanged(_cityId, _subcityId, _weredaId);
  }

  @override
  Widget build(BuildContext context) {
    if (_loadingCities) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 12),
        child: Center(
            child: SizedBox(
                width: 24,
                height: 24,
                child: CircularProgressIndicator(strokeWidth: 2))),
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        DropdownButtonFormField<String>(
          value: _cityId.isEmpty ? null : _cityId,
          decoration: const InputDecoration(labelText: 'City'),
          items: [
            for (final x in _cities)
              DropdownMenuItem<String>(
                value: (x as Map<String, dynamic>)['id']?.toString(),
                child: Text((x as Map<String, dynamic>)['name']?.toString() ?? ''),
              ),
          ],
          onChanged: _onCity,
        ),
        const SizedBox(height: 8),
        DropdownButtonFormField<String>(
          value: _subcityId.isEmpty ? null : _subcityId,
          decoration: const InputDecoration(labelText: 'Sub city'),
          items: [
            for (final x in _subcities)
              DropdownMenuItem<String>(
                value: (x as Map<String, dynamic>)['id']?.toString(),
                child: Text((x as Map<String, dynamic>)['name']?.toString() ?? ''),
              ),
          ],
          onChanged: _cityId.isEmpty ? null : _onSub,
        ),
        const SizedBox(height: 8),
        DropdownButtonFormField<String>(
          value: _weredaId.isEmpty ? null : _weredaId,
          decoration: const InputDecoration(labelText: 'Wereda'),
          items: [
            for (final x in _weredas)
              DropdownMenuItem<String>(
                value: (x as Map<String, dynamic>)['id']?.toString(),
                child: Text((x as Map<String, dynamic>)['name']?.toString() ?? ''),
              ),
          ],
          onChanged: _subcityId.isEmpty ? null : _onWer,
        ),
      ],
    );
  }
}
