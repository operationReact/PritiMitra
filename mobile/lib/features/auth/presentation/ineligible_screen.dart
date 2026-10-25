import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_tokens.dart';
import '../application/auth_controller.dart';
import 'auth_form_scaffold.dart';

/// Dedicated screen for users the backend marks INELIGIBLE. It deliberately
/// gives no detail about the eligibility rule.
class IneligibleScreen extends ConsumerWidget {
  const IneligibleScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return AuthFormScaffold(
      title: 'PritiMitra is not available for your account',
      subtitle:
          'We are unable to offer PritiMitra to this account at this time.',
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
