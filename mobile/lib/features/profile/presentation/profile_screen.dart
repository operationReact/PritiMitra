import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/api_result.dart';
import '../../../core/router/app_router.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../shared/widgets/brand_header.dart';
import '../application/profile_controller.dart';
import '../domain/own_profile.dart';
import 'profile_photos_section.dart';

/// Connection goal wire values and labels, matching the backend `ConnectionGoal` enum.
const profileGoalLabels = <String, String>{
  'DATING': 'Dating',
  'FRIENDSHIP': 'Friendship',
  'COMMUNITY': 'Community',
  'MEET_PEOPLE': 'Meet people',
  'OPEN_TO_CONNECTION': 'Open to connection',
};

const _maxGoals = 4;

/// Owner profile view and edit. The server validates every field. This screen only reports results.
class ProfileScreen extends ConsumerStatefulWidget {
  const ProfileScreen({super.key});

  @override
  ConsumerState<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends ConsumerState<ProfileScreen> {
  final _formKey = GlobalKey<FormState>();
  final _displayName = TextEditingController();
  final _bio = TextEditingController();
  final _prompt = TextEditingController();
  Set<String> _goals = {};
  OwnProfile? _loaded;
  bool _saving = false;
  bool _togglingDiscovery = false;
  String? _message;
  bool _messageIsError = false;

  @override
  void dispose() {
    _displayName.dispose();
    _bio.dispose();
    _prompt.dispose();
    super.dispose();
  }

  void _hydrate(OwnProfile profile) {
    if (identical(_loaded, profile)) {
      return;
    }
    _loaded = profile;
    _displayName.text = profile.displayName;
    _bio.text = profile.bio ?? '';
    _prompt.text = profile.profilePrompt ?? '';
    _goals = {...profile.goals};
  }

  void _setMessage(String text, {required bool isError}) {
    setState(() {
      _message = text;
      _messageIsError = isError;
    });
  }

  Future<void> _save() async {
    final form = _formKey.currentState;
    final current = _loaded;
    if (form == null || current == null || !form.validate()) {
      return;
    }
    setState(() => _saving = true);
    final result = await ref
        .read(profileApiProvider)
        .saveProfile(
          current.copyWith(
            displayName: _displayName.text.trim(),
            bio: _bio.text.trim(),
            profilePrompt: _prompt.text.trim(),
            goals: _goals,
          ),
        );
    if (!mounted) {
      return;
    }
    setState(() => _saving = false);
    switch (result) {
      case ApiSuccess(:final data):
        _loaded = data;
        _setMessage('Profile saved.', isError: false);
      case _:
        _setMessage(profileActionErrorMessage(result), isError: true);
    }
  }

  Future<void> _setDiscoverable(bool value) async {
    setState(() => _togglingDiscovery = true);
    final result = await ref
        .read(profileApiProvider)
        .setDiscoverable(discoverable: value);
    if (!mounted) {
      return;
    }
    setState(() => _togglingDiscovery = false);
    switch (result) {
      case ApiSuccess(:final data):
        _loaded = data;
        _setMessage(
          value
              ? 'Your profile is now visible in discovery.'
              : 'Your profile is hidden from discovery.',
          isError: false,
        );
      case _:
        _setMessage(profileActionErrorMessage(result), isError: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final profile = ref.watch(ownProfileProvider);
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        elevation: 0,
        title: const BrandHeader(),
        titleSpacing: AppSpacing.md,
        actions: [
          TextButton(
            onPressed: () =>
                context.canPop() ? context.pop() : context.go(AppRoutes.home),
            child: const Text('Done'),
          ),
        ],
      ),
      body: SafeArea(
        child: profile.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => _LoadError(
            message: profileErrorMessage(error),
            onRetry: () => ref.invalidate(ownProfileProvider),
          ),
          data: (data) {
            _hydrate(data);
            return _buildForm(context, _loaded ?? data);
          },
        ),
      ),
    );
  }

  Widget _buildForm(BuildContext context, OwnProfile profile) {
    final theme = Theme.of(context);
    return Form(
      key: _formKey,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.md,
          AppSpacing.md,
          AppSpacing.md,
          AppSpacing.xxxl,
        ),
        children: [
          Text('Your profile', style: theme.textTheme.headlineSmall),
          const SizedBox(height: AppSpacing.xxs),
          Text(
            'Only what you choose to share appears in discovery.',
            style: theme.textTheme.bodyMedium?.copyWith(color: AppColors.muted),
          ),
          const SizedBox(height: AppSpacing.lg),
          _DiscoverabilityCard(
            discoverable: profile.discoverable,
            busy: _togglingDiscovery,
            onChanged: _setDiscoverable,
          ),
          if (_message != null) ...[
            const SizedBox(height: AppSpacing.md),
            _Notice(message: _message!, isError: _messageIsError),
          ],
          const SizedBox(height: AppSpacing.lg),
          const ProfilePhotosSection(),
          const SizedBox(height: AppSpacing.lg),
          TextFormField(
            controller: _displayName,
            maxLength: 60,
            decoration: const InputDecoration(labelText: 'Display name'),
            validator: (value) {
              if (value == null || value.trim().isEmpty) {
                return 'Enter a display name.';
              }
              return null;
            },
          ),
          const SizedBox(height: AppSpacing.sm),
          TextFormField(
            controller: _bio,
            maxLength: 500,
            maxLines: 4,
            decoration: const InputDecoration(labelText: 'About you'),
          ),
          const SizedBox(height: AppSpacing.sm),
          TextFormField(
            controller: _prompt,
            maxLength: 200,
            maxLines: 2,
            decoration: const InputDecoration(labelText: 'Profile prompt'),
          ),
          const SizedBox(height: AppSpacing.lg),
          Text('Connection goals', style: theme.textTheme.titleMedium),
          const SizedBox(height: AppSpacing.xs),
          Wrap(
            spacing: AppSpacing.xs,
            runSpacing: AppSpacing.xs,
            children: [
              for (final entry in profileGoalLabels.entries)
                FilterChip(
                  label: Text(entry.value),
                  selected: _goals.contains(entry.key),
                  onSelected: (selected) {
                    setState(() {
                      if (selected && _goals.length < _maxGoals) {
                        _goals.add(entry.key);
                      } else {
                        _goals.remove(entry.key);
                      }
                    });
                  },
                ),
            ],
          ),
          const SizedBox(height: AppSpacing.xl),
          FilledButton(
            onPressed: _saving ? null : _save,
            child: _saving
                ? const SizedBox(
                    height: 20,
                    width: 20,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: AppColors.surface,
                    ),
                  )
                : const Text('Save profile'),
          ),
        ],
      ),
    );
  }
}

class _DiscoverabilityCard extends StatelessWidget {
  const _DiscoverabilityCard({
    required this.discoverable,
    required this.busy,
    required this.onChanged,
  });

  final bool discoverable;
  final bool busy;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: AppColors.lavenderSurface,
        borderRadius: BorderRadius.circular(AppRadii.lg),
        border: Border.all(color: AppColors.borderLavender),
      ),
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Show my profile in discovery',
                    style: theme.textTheme.titleSmall,
                  ),
                  const SizedBox(height: AppSpacing.xxs),
                  Text(
                    'Off by default. Turning it on needs a completed profile.',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: AppColors.muted,
                    ),
                  ),
                ],
              ),
            ),
            Switch(value: discoverable, onChanged: busy ? null : onChanged),
          ],
        ),
      ),
    );
  }
}

class _Notice extends StatelessWidget {
  const _Notice({required this.message, required this.isError});

  final String message;
  final bool isError;

  @override
  Widget build(BuildContext context) {
    final color = isError ? AppColors.error : AppColors.success;
    return Semantics(
      liveRegion: true,
      child: Text(message, style: TextStyle(color: color)),
    );
  }
}

class _LoadError extends StatelessWidget {
  const _LoadError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: AppSpacing.md),
            OutlinedButton(onPressed: onRetry, child: const Text('Try again')),
          ],
        ),
      ),
    );
  }
}
