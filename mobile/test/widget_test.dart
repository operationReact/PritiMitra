import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pritimitra/app.dart';
import 'package:pritimitra/shared/widgets/brand_header.dart';

void main() {
  testWidgets('app boots to the rich welcome screen with brand and CTA', (tester) async {
    await tester.binding.setSurfaceSize(const Size(390, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(const ProviderScope(child: PritiMitraApp()));
    await tester.pumpAndSettle();

    expect(find.byType(BrandHeader), findsOneWidget);
    expect(find.text('Your people.'), findsOneWidget);
    expect(find.text('Get started — it’s free'), findsOneWidget);
    expect(find.text('Connection first. Consent always.'), findsOneWidget);
    expect(find.byType(Image), findsNWidgets(2));
  });

  testWidgets('brand header keeps approved icon and wordmark sizes', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: Scaffold(body: BrandHeader())));
    final images = tester.widgetList<Image>(find.byType(Image)).toList();
    expect(images, hasLength(2));
    expect(tester.getSize(find.byType(Image).at(0)), const Size(46, 34));
    expect(tester.getSize(find.byType(Image).at(1)), const Size(97, 28));
  });

  testWidgets('primary CTA explains auth is not wired yet', (tester) async {
    await tester.binding.setSurfaceSize(const Size(390, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(const ProviderScope(child: PritiMitraApp()));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Get started — it’s free'));
    await tester.pumpAndSettle();
    expect(find.text('Account setup is the next implementation phase.'), findsOneWidget);
  });
}
