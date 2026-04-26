import 'package:flutter/material.dart';
import 'package:signature/signature.dart';
import 'dart:convert';
import '../../../l10n/app_strings.dart';
import '../../../core/network/api_client.dart';

class SignatureScreen extends StatefulWidget {
  final Map<String, dynamic> answers;
  final String? reviewId;
  const SignatureScreen({super.key, required this.answers, this.reviewId});

  @override
  State<SignatureScreen> createState() => _SignatureScreenState();
}

class _SignatureScreenState extends State<SignatureScreen> {
  final SignatureController _teacherController = SignatureController();
  final SignatureController _directorController = SignatureController();
  bool _submitting = false;
  String? _message;

  Future<void> _submitSignatures() async {
    if (widget.reviewId == null) {
      if (!mounted) return;
      Navigator.pop(context);
      return;
    }
    setState(() {
      _submitting = true;
      _message = null;
    });
    try {
      final teacherBytes = await _teacherController.toPngBytes();
      final directorBytes = await _directorController.toPngBytes();
      if (teacherBytes == null || directorBytes == null) {
        setState(() {
          _submitting = false;
          _message = AppStrings.of(context).bothSignaturesRequired;
        });
        return;
      }
      await ApiClient.submitSignature(
        widget.reviewId!,
        'TEACHER',
        base64Encode(teacherBytes),
      );
      await ApiClient.submitSignature(
        widget.reviewId!,
        'SCHOOL_DIRECTOR',
        base64Encode(directorBytes),
      );
      if (!mounted) return;
      setState(() {
        _submitting = false;
        _message = AppStrings.of(context).signaturesSaved;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppStrings.of(context).signaturesSaved)),
      );
      await Future<void>.delayed(const Duration(milliseconds: 900));
      if (!mounted) return;
      Navigator.of(context).popUntil((route) => route.isFirst);
    } catch (e) {
      setState(() {
        _submitting = false;
        _message = AppStrings.of(context).signaturesFailed(e.toString());
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final s = AppStrings.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(s.signatures)),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: ListView(
          children: [
            Text(s.teacherSignature, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: Signature(controller: _teacherController, height: 140, backgroundColor: Colors.grey.shade200),
            ),
            const SizedBox(height: 16),
            Text(s.schoolDirectorSignature, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: Signature(controller: _directorController, height: 140, backgroundColor: Colors.grey.shade200),
            ),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: _submitting ? null : _submitSignatures,
              icon: const Icon(Icons.verified_outlined),
              label: Text(_submitting ? s.submitting : s.submitSignatures),
            ),
            if (_message != null) ...[
              const SizedBox(height: 12),
              Text(
                _message!,
                style: TextStyle(
                  color: _message!.startsWith('Failed') ? Colors.red : Colors.green.shade700,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
