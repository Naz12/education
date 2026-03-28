import 'package:flutter_test/flutter_test.dart';
import 'package:school_supervision_mobile/main.dart';

void main() {
  testWidgets('App shows login screen', (WidgetTester tester) async {
    await tester.pumpWidget(const SupervisionApp());
    expect(find.text('School Supervision'), findsOneWidget);
    expect(find.text('Sign in to continue'), findsOneWidget);
  });
}
