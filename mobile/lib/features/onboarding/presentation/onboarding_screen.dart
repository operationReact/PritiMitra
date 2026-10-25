import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../auth/presentation/auth_form_scaffold.dart';
import '../application/onboarding_controller.dart';
import '../domain/onboarding_state.dart';
import 'onboarding_steps.dart';

/// Routes the user through the server-reported onboarding step. Loading and retry states are handled here.
class OnboardingScreen extends ConsumerWidget {
  const OnboardingScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final flow = ref.watch(onboardingControllerProvider);
    switch (flow.load) {
      case OnboardingLoad.loading:
        return const Scaffold(
          body: Center(
            child: CircularProgressIndicator(semanticsLabel: 'Loading your setup'),
          ),
        );
      case OnboardingLoad.failed:
        return AuthFormScaffold(
          title: "We couldn't load your setup",
          subtitle: 'Check your connection and try again.',
          children: [
            if (flow.problem != null)
              AuthMessage(message: onboardingProblemMessage(flow.problem!)),
            FilledButton(
              onPressed: () => ref.read(onboardingControllerProvider.notifier).load(),
              child: const Text('Try again'),
            ),
          ],
        );
      case OnboardingLoad.ready:
        return switch (flow.step ?? OnboardingStep.dateOfBirth) {
          OnboardingStep.dateOfBirth => const DateOfBirthStep(),
          OnboardingStep.terms => const TermsStep(),
          OnboardingStep.goals => const GoalsStep(),
          OnboardingStep.profile => const ProfileStep(),
          OnboardingStep.review => const ReviewStep(),
        };
    }
  }
}
