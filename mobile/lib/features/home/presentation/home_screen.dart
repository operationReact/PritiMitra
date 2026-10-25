import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_tokens.dart';
import '../../auth/application/auth_controller.dart';
import '../../auth/presentation/auth_form_scaffold.dart';

/// Placeholder for the protected product home. Only ACTIVE accounts reach it.
class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return AuthFormScaffold(
      title: 'You are signed in',
      subtitle: 'Discovery and profiles are not built yet.',
      children: [
        const SizedBox(height: AppSpacing.xs),
        TextButton(
          onPressed: () => ref.read(authControllerProvider.notifier).signOut(),
          child: const Text('Sign out'),
        ),
      ],
    );
  }
}
