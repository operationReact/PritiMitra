import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pritimitra/app.dart';
import 'package:pritimitra/shared/widgets/brand_header.dart';

void main() {
  testWidgets('app boots to the welcome screen with the brand lockup', (
    tester,
  ) async {
    await tester.pumpWidget(const ProviderScope(child: PritiMitraApp()));
    await tester.pumpAndSettle();

    expect(find.byType(BrandHeader), findsOneWidget);
    expect(find.text('Welcome'), findsOneWidget);
    expect(find.byType(Image), findsNWidgets(2));
  });

  testWidgets('brand header keeps Figma icon and wordmark sizes', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(home: Scaffold(body: BrandHeader())),
    );

    final images = tester.widgetList<Image>(find.byType(Image)).toList();
    expect(images, hasLength(2));
    expect(tester.getSize(find.byType(Image).at(0)), const Size(46, 34));
    expect(tester.getSize(find.byType(Image).at(1)), const Size(97, 28));
  });
}
