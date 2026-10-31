import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/router/app_router.dart';
import 'core/theme/design_experience_controller.dart';
import 'core/theme/design_experience_theme.dart';

class PritiMitraApp extends ConsumerWidget {
  const PritiMitraApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final experience = ref.watch(designExperienceProvider);
    return MaterialApp.router(
      title: 'PritiMitra',
      debugShowCheckedModeBanner: false,
      theme: DesignExperienceTheme.forExperience(experience),
      routerConfig: ref.watch(appRouterProvider),
    );
  }
}
