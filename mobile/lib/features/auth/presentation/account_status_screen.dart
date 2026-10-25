import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_tokens.dart';
import '../application/auth_controller.dart';
import '../domain/account_status.dart';
import '../domain/auth_state.dart';
import 'auth_form_scaffold.dart';

/// Shown to signed-in users whose backend status is not ACTIVE yet.
class AccountStatusScreen extends ConsumerWidget {
  const AccountStatusScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(authControllerProvider);
    final status = state is AuthSignedIn ? state.status : null;
    final controller = ref.read(authControllerProvider.notifier);

    return AuthFormScaffold(
      title: _titleFor(status),
      subtitle: _messageFor(status),
      children: [
        if (status == null)
          FilledButton(
            onPressed: controller.retryAccountStatus,
            child: const Text('Try again'),
          ),
        const SizedBox(height: AppSpacing.xs),
        TextButton(
          onPressed: controller.signOut,
          child: const Text('Sign out'),
        ),
      ],
    );
  }

  static String _titleFor(AccountStatus? status) {
    return switch (status) {
      AccountStatus.pendingEmailVerification => 'Verify your email',
      AccountStatus.onboardingIncomplete => 'Finish setting up',
      AccountStatus.suspended => 'Account unavailable',
      _ => 'We could not load your account',
    };
  }

  static String _messageFor(AccountStatus? status) {
    return switch (status) {
      AccountStatus.pendingEmailVerification =>
        'Confirm your email to continue. Sign out and sign in again after you confirm.',
      AccountStatus.onboardingIncomplete =>
        'Profile setup is not available in this build yet.',
      AccountStatus.suspended =>
        'This account cannot use PritiMitra right now.',
      _ => 'Check your connection and try again.',
    };
  }
}
