import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:go_router/go_router.dart';

import '../../../core/router/app_router.dart';
import '../../../core/theme/app_tokens.dart';
import '../../auth/application/auth_controller.dart';
import '../../auth/presentation/auth_form_scaffold.dart';

/// Protected product home. Only ACTIVE accounts reach it.
class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return AuthFormScaffold(
      title: 'You are signed in',
      subtitle: 'Discover people who match your goals, or update your profile.',
      children: [
        const SizedBox(height: AppSpacing.xs),
        FilledButton(
          onPressed: () => context.go(AppRoutes.discover),
          child: const Text('Discover people'),
        ),
        const SizedBox(height: AppSpacing.xs),
        OutlinedButton(
          onPressed: () => context.go(AppRoutes.profile),
          child: const Text('My profile'),
        ),
        const SizedBox(height: AppSpacing.xs),
        TextButton(
          onPressed: () => ref.read(authControllerProvider.notifier).signOut(),
          child: const Text('Sign out'),
        ),
      ],
    );
  }
}
