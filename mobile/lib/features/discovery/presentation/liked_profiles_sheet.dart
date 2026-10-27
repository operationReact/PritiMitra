import 'package:flutter/material.dart';

import '../../../core/network/api_result.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_tokens.dart';
import '../data/discovery_api.dart';
import '../domain/discovery_models.dart';

/// Lists the profiles the viewer has liked, read from the server so the list survives refresh and navigation.
/// Removing a like is confirmed only after the server accepts it.
class LikedProfilesSheet extends StatefulWidget {
  const LikedProfilesSheet({
    super.key,
    required this.api,
    required this.onUnliked,
  });

  final DiscoveryApi api;
  final void Function(String publicId) onUnliked;

  @override
  State<LikedProfilesSheet> createState() => _LikedProfilesSheetState();
}

class _LikedProfilesSheetState extends State<LikedProfilesSheet> {
  final List<LikedProfile> _items = [];
  final Set<String> _busy = {};
  String? _nextCursor;
  bool _loading = true;
  bool _loadingMore = false;
  String? _error;
  String? _notice;

  @override
  void initState() {
    super.initState();
    _load(first: true);
  }

  Future<void> _load({required bool first}) async {
    setState(() {
      if (first) {
        _loading = true;
        _items.clear();
        _nextCursor = null;
      } else {
        _loadingMore = true;
      }
      _error = null;
    });
    final result = await widget.api.fetchLikes(
      cursor: first ? null : _nextCursor,
    );
    if (!mounted) {
      return;
    }
    switch (result) {
      case ApiSuccess(:final data):
        setState(() {
          _items.addAll(data.items);
          _nextCursor = data.nextCursor;
          _loading = false;
          _loadingMore = false;
        });
      case _:
        setState(() {
          _loading = false;
          _loadingMore = false;
          _error = _likesMessage(result);
        });
    }
  }

  Future<void> _unlike(LikedProfile profile) async {
    if (_busy.contains(profile.publicId)) {
      return;
    }
    setState(() {
      _busy.add(profile.publicId);
      _notice = null;
    });
    final result = await widget.api.unlike(profile.publicId);
    if (!mounted) {
      return;
    }
    setState(() => _busy.remove(profile.publicId));
    if (result is ApiSuccess) {
      widget.onUnliked(profile.publicId);
      setState(() => _items.removeWhere((p) => p.publicId == profile.publicId));
    } else {
      setState(() => _notice = _likesMessage(result));
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final height = MediaQuery.sizeOf(context).height * 0.75;
    return SizedBox(
      height: height,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.md,
          0,
          AppSpacing.md,
          AppSpacing.md,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Your likes', style: theme.textTheme.titleLarge),
            const SizedBox(height: AppSpacing.xs),
            Text(
              'Remove a like at any time. Likes never create matches on their own.',
              style: theme.textTheme.bodySmall,
            ),
            const SizedBox(height: AppSpacing.md),
            if (_notice != null)
              Padding(
                padding: const EdgeInsets.only(bottom: AppSpacing.sm),
                child: Text(
                  _notice!,
                  style: const TextStyle(color: AppColors.error),
                ),
              ),
            Expanded(child: _body(context)),
          ],
        ),
      ),
    );
  }

  Widget _body(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(_error!, textAlign: TextAlign.center),
            const SizedBox(height: AppSpacing.sm),
            OutlinedButton(
              onPressed: () => _load(first: true),
              child: const Text('Try again'),
            ),
          ],
        ),
      );
    }
    if (_items.isEmpty) {
      return const Center(
        child: Text(
          "You haven't liked anyone yet.",
          textAlign: TextAlign.center,
        ),
      );
    }
    return ListView.separated(
      itemCount: _items.length + 1,
      separatorBuilder: (_, _) => const SizedBox(height: AppSpacing.sm),
      itemBuilder: (context, index) {
        if (index == _items.length) {
          return _footer();
        }
        final profile = _items[index];
        final busy = _busy.contains(profile.publicId);
        return DecoratedBox(
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(AppRadii.lg),
            border: Border.all(color: AppColors.border),
          ),
          child: ListTile(
            title: Text(
              profile.available
                  ? (profile.displayName?.isNotEmpty == true
                        ? profile.displayName!
                        : 'Member')
                  : 'Profile unavailable',
            ),
            subtitle: profile.available
                ? null
                : const Text('This profile is no longer visible.'),
            trailing: TextButton(
              onPressed: busy ? null : () => _unlike(profile),
              child: busy
                  ? const SizedBox(
                      height: 16,
                      width: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Text('Unlike'),
            ),
          ),
        );
      },
    );
  }

  Widget _footer() {
    if (_nextCursor == null) {
      return const SizedBox.shrink();
    }
    return Center(
      child: OutlinedButton(
        onPressed: _loadingMore ? null : () => _load(first: false),
        child: _loadingMore
            ? const SizedBox(
                height: 18,
                width: 18,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : const Text('Load more'),
      ),
    );
  }
}

String _likesMessage(ApiResult<Object?> result) {
  return switch (result) {
    ApiUnauthorized() => 'Your session has expired. Sign in again to continue.',
    ApiForbidden() => 'Likes are not available for your account.',
    _ => 'We could not load your likes. Check your connection and try again.',
  };
}
