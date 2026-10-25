import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/config/app_config.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_tokens.dart';
import '../../auth/presentation/auth_form_scaffold.dart';
import '../application/onboarding_controller.dart';
import '../domain/profile_draft.dart';

const _stepCount = 5;

String onboardingProblemMessage(OnboardingProblem problem) {
  return switch (problem) {
    OnboardingProblem.network =>
      "We couldn't reach PritiMitra. Check your connection and try again.",
    OnboardingProblem.rejected =>
      'Some details were not accepted. Check them and try again.',
    OnboardingProblem.conflict =>
      'Your details changed on the server. Refresh and try again.',
    OnboardingProblem.profileIncomplete =>
      'Add your name and at least one goal to finish.',
    OnboardingProblem.policyUnavailable =>
      'Registration is paused. Approved policy versions are not configured for this build.',
    OnboardingProblem.goalRequired => 'Choose at least one connection goal.',
    OnboardingProblem.nameRequired => 'Enter a display name.',
  };
}

/// Shows where the user is in the required path. The position comes from the server-reported step.
class OnboardingProgress extends StatelessWidget {
  const OnboardingProgress({required this.index, super.key});

  final int index;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Semantics(
      label: 'Step ${index + 1} of $_stepCount',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(AppRadii.pill),
            child: LinearProgressIndicator(
              value: (index + 1) / _stepCount,
              minHeight: 6,
              backgroundColor: AppColors.primarySoft,
              color: AppColors.primary,
            ),
          ),
          const SizedBox(height: AppSpacing.xs),
          Text(
            'Step ${index + 1} of $_stepCount',
            style: theme.textTheme.labelMedium?.copyWith(color: AppColors.muted),
          ),
        ],
      ),
    );
  }
}

/// Muted notice card used for privacy explanations.
class OnboardingNoteCard extends StatelessWidget {
  const OnboardingNoteCard({required this.message, super.key});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: AppColors.primarySoft,
        borderRadius: BorderRadius.circular(AppRadii.lg),
      ),
      child: Text(
        message,
        style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: AppColors.navy),
      ),
    );
  }
}

class DateOfBirthStep extends ConsumerStatefulWidget {
  const DateOfBirthStep({super.key});

  @override
  ConsumerState<DateOfBirthStep> createState() => _DateOfBirthStepState();
}

class _DateOfBirthStepState extends ConsumerState<DateOfBirthStep> {
  final _formKey = GlobalKey<FormState>();
  final _day = TextEditingController();
  final _month = TextEditingController();
  final _year = TextEditingController();
  bool _invalid = false;

  @override
  void dispose() {
    _day.dispose();
    _month.dispose();
    _year.dispose();
    super.dispose();
  }

  /// Returns null for impossible dates such as 31 February. Eligibility is decided by the server, not here.
  DateTime? _parse() {
    final day = int.tryParse(_day.text.trim());
    final month = int.tryParse(_month.text.trim());
    final year = int.tryParse(_year.text.trim());
    if (day == null || month == null || year == null) {
      return null;
    }
    if (year < 1900 || month < 1 || month > 12) {
      return null;
    }
    final date = DateTime(year, month, day);
    if (date.year != year || date.month != month || date.day != day) {
      return null;
    }
    if (date.isAfter(DateTime.now())) {
      return null;
    }
    return date;
  }

  Future<void> _submit() async {
    final date = _parse();
    if (date == null) {
      setState(() => _invalid = true);
      return;
    }
    setState(() => _invalid = false);
    await ref.read(onboardingControllerProvider.notifier).submitDateOfBirth(date);
  }

  @override
  Widget build(BuildContext context) {
    final flow = ref.watch(onboardingControllerProvider);
    return AuthFormScaffold(
      title: 'When is your birthday?',
      subtitle: 'PritiMitra is only for adults aged 18 and over.',
      children: [
        const OnboardingProgress(index: 0),
        const SizedBox(height: AppSpacing.lg),
        if (_invalid) const AuthMessage(message: 'Enter a valid date of birth.'),
        if (flow.problem != null)
          AuthMessage(message: onboardingProblemMessage(flow.problem!)),
        Form(
          key: _formKey,
          child: Row(
            children: [
              Expanded(
                child: _DateField(controller: _day, label: 'Day', maxLength: 2),
              ),
              const SizedBox(width: AppSpacing.xs),
              Expanded(
                child: _DateField(controller: _month, label: 'Month', maxLength: 2),
              ),
              const SizedBox(width: AppSpacing.xs),
              Expanded(
                flex: 2,
                child: _DateField(controller: _year, label: 'Year', maxLength: 4),
              ),
            ],
          ),
        ),
        const SizedBox(height: AppSpacing.lg),
        const OnboardingNoteCard(
          message:
              'We use your date of birth only to confirm you are 18 or older. It is never shown on your profile.',
        ),
        const SizedBox(height: AppSpacing.lg),
        FilledButton(
          onPressed: flow.busy ? null : _submit,
          child: Text(flow.busy ? 'Checking…' : 'Continue'),
        ),
      ],
    );
  }
}

class _DateField extends StatelessWidget {
  const _DateField({
    required this.controller,
    required this.label,
    required this.maxLength,
  });

  final TextEditingController controller;
  final String label;
  final int maxLength;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: controller,
      keyboardType: TextInputType.number,
      maxLength: maxLength,
      decoration: InputDecoration(labelText: label, counterText: ''),
    );
  }
}

class TermsStep extends ConsumerStatefulWidget {
  const TermsStep({super.key});

  @override
  ConsumerState<TermsStep> createState() => _TermsStepState();
}

class _TermsStepState extends ConsumerState<TermsStep> {
  bool _terms = false;
  bool _privacy = false;

  @override
  Widget build(BuildContext context) {
    final flow = ref.watch(onboardingControllerProvider);
    final policiesConfigured = ref.read(appConfigProvider).hasPolicyVersions;
    final canContinue = policiesConfigured && _terms && _privacy && !flow.busy;
    return AuthFormScaffold(
      title: 'A few important choices',
      subtitle: 'Please read and accept both before you continue.',
      children: [
        const OnboardingProgress(index: 1),
        const SizedBox(height: AppSpacing.lg),
        if (!policiesConfigured)
          const AuthMessage(
            message:
                'Registration is paused. Approved policy versions are not configured for this build.',
          ),
        if (flow.problem != null)
          AuthMessage(message: onboardingProblemMessage(flow.problem!)),
        CheckboxListTile(
          contentPadding: EdgeInsets.zero,
          value: _terms,
          onChanged: policiesConfigured
              ? (value) => setState(() => _terms = value ?? false)
              : null,
          title: const Text('I agree to the Terms of Service'),
          controlAffinity: ListTileControlAffinity.leading,
        ),
        CheckboxListTile(
          contentPadding: EdgeInsets.zero,
          value: _privacy,
          onChanged: policiesConfigured
              ? (value) => setState(() => _privacy = value ?? false)
              : null,
          title: const Text('I have read the Privacy Notice'),
          controlAffinity: ListTileControlAffinity.leading,
        ),
        const SizedBox(height: AppSpacing.lg),
        FilledButton(
          onPressed: canContinue
              ? () => ref.read(onboardingControllerProvider.notifier).acceptConsents()
              : null,
          child: Text(flow.busy ? 'Saving…' : 'Agree and continue'),
        ),
      ],
    );
  }
}

class GoalsStep extends ConsumerStatefulWidget {
  const GoalsStep({super.key});

  @override
  ConsumerState<GoalsStep> createState() => _GoalsStepState();
}

class _GoalsStepState extends ConsumerState<GoalsStep> {
  late Set<ConnectionGoal> _selected;

  @override
  void initState() {
    super.initState();
    _selected = {...ref.read(onboardingControllerProvider).draft.goals};
  }

  void _toggle(ConnectionGoal goal) {
    setState(() {
      if (!_selected.add(goal)) {
        _selected.remove(goal);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final flow = ref.watch(onboardingControllerProvider);
    return AuthFormScaffold(
      title: 'What are you here for?',
      subtitle: 'Choose one or more. You can change this later.',
      children: [
        const OnboardingProgress(index: 2),
        const SizedBox(height: AppSpacing.lg),
        if (flow.problem != null)
          AuthMessage(message: onboardingProblemMessage(flow.problem!)),
        for (final goal in ConnectionGoal.values)
          Padding(
            padding: const EdgeInsets.only(bottom: AppSpacing.sm),
            child: _GoalCard(
              goal: goal,
              selected: _selected.contains(goal),
              onTap: flow.busy ? null : () => _toggle(goal),
            ),
          ),
        const SizedBox(height: AppSpacing.lg),
        FilledButton(
          onPressed: flow.busy
              ? null
              : () => ref
                  .read(onboardingControllerProvider.notifier)
                  .saveGoals(_selected),
          child: Text(flow.busy ? 'Saving…' : 'Continue'),
        ),
      ],
    );
  }
}

class _GoalCard extends StatelessWidget {
  const _GoalCard({
    required this.goal,
    required this.selected,
    required this.onTap,
  });

  final ConnectionGoal goal;
  final bool selected;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Semantics(
      button: true,
      selected: selected,
      label: '${goal.title}. ${goal.subtitle}',
      child: InkWell(
        borderRadius: BorderRadius.circular(AppRadii.lg),
        onTap: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          padding: const EdgeInsets.all(AppSpacing.md),
          decoration: BoxDecoration(
            color: selected ? AppColors.primarySoft : AppColors.surface,
            borderRadius: BorderRadius.circular(AppRadii.lg),
            border: Border.all(
              color: selected ? AppColors.primary : AppColors.border,
              width: selected ? 2 : 1,
            ),
          ),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(goal.title, style: theme.textTheme.titleMedium),
                    const SizedBox(height: AppSpacing.xxs),
                    Text(
                      goal.subtitle,
                      style: theme.textTheme.bodyMedium?.copyWith(color: AppColors.muted),
                    ),
                  ],
                ),
              ),
              Icon(
                selected ? Icons.check_circle : Icons.radio_button_unchecked,
                color: selected ? AppColors.primary : AppColors.placeholder,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class ProfileStep extends ConsumerStatefulWidget {
  const ProfileStep({super.key});

  @override
  ConsumerState<ProfileStep> createState() => _ProfileStepState();
}

class _ProfileStepState extends ConsumerState<ProfileStep> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _name;
  late final TextEditingController _bio;

  @override
  void initState() {
    super.initState();
    final draft = ref.read(onboardingControllerProvider).draft;
    _name = TextEditingController(text: draft.displayName ?? '');
    _bio = TextEditingController(text: draft.bio ?? '');
  }

  @override
  void dispose() {
    _name.dispose();
    _bio.dispose();
    super.dispose();
  }

  void _submit() {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    ref.read(onboardingControllerProvider.notifier).saveProfile(
          displayName: _name.text,
          bio: _bio.text,
        );
  }

  @override
  Widget build(BuildContext context) {
    final flow = ref.watch(onboardingControllerProvider);
    return AuthFormScaffold(
      title: "Let's make it you.",
      subtitle: 'Your name and a short bio help people get to know you.',
      children: [
        const OnboardingProgress(index: 3),
        const SizedBox(height: AppSpacing.lg),
        if (flow.problem != null)
          AuthMessage(message: onboardingProblemMessage(flow.problem!)),
        Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TextFormField(
                controller: _name,
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(labelText: 'Display name'),
                validator: (value) =>
                    (value ?? '').trim().isEmpty ? 'Enter a display name.' : null,
              ),
              const SizedBox(height: AppSpacing.md),
              TextFormField(
                controller: _bio,
                maxLines: 4,
                decoration: const InputDecoration(
                  labelText: 'Short bio (optional)',
                  alignLabelWithHint: true,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: AppSpacing.lg),
        const OnboardingNoteCard(
          message:
              'Profile photos and identity checks are not available yet, so nothing on your profile is verified.',
        ),
        const SizedBox(height: AppSpacing.lg),
        FilledButton(
          onPressed: flow.busy ? null : _submit,
          child: Text(flow.busy ? 'Saving…' : 'Preview profile'),
        ),
      ],
    );
  }
}

class ReviewStep extends ConsumerWidget {
  const ReviewStep({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final flow = ref.watch(onboardingControllerProvider);
    final draft = flow.draft;
    final theme = Theme.of(context);
    return AuthFormScaffold(
      title: 'Looking good, ${_firstName(draft)}.',
      subtitle: 'Check your profile before you finish.',
      children: [
        const OnboardingProgress(index: 4),
        const SizedBox(height: AppSpacing.lg),
        if (flow.problem != null)
          AuthMessage(message: onboardingProblemMessage(flow.problem!)),
        Container(
          padding: const EdgeInsets.all(AppSpacing.lg),
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(AppRadii.xl),
            border: Border.all(color: AppColors.borderLavender),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(draft.displayName ?? '', style: theme.textTheme.titleLarge),
              if (draft.bio != null) ...[
                const SizedBox(height: AppSpacing.xs),
                Text(draft.bio!, style: theme.textTheme.bodyMedium),
              ],
              const SizedBox(height: AppSpacing.md),
              Wrap(
                spacing: AppSpacing.xs,
                runSpacing: AppSpacing.xs,
                children: [
                  for (final goal in draft.goals)
                    Chip(
                      label: Text(goal.title),
                      backgroundColor: AppColors.primarySoft,
                      side: BorderSide.none,
                    ),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: AppSpacing.lg),
        const OnboardingNoteCard(
          message: 'Your profile stays private by default.',
        ),
        const SizedBox(height: AppSpacing.lg),
        FilledButton(
          onPressed: flow.busy
              ? null
              : () => ref.read(onboardingControllerProvider.notifier).complete(),
          child: Text(flow.busy ? 'Finishing…' : 'Finish profile'),
        ),
      ],
    );
  }

  static String _firstName(ProfileDraft draft) {
    final name = (draft.displayName ?? '').trim();
    if (name.isEmpty) {
      return 'there';
    }
    return name.split(RegExp(r'\s+')).first;
  }
}
