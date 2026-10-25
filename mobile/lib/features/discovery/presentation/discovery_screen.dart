import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/api_result.dart';
import '../../../core/router/app_router.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../shared/widgets/brand_header.dart';
import '../../auth/application/auth_controller.dart';
import '../../profile/presentation/profile_screen.dart' show profileGoalLabels;
import '../data/discovery_api.dart';
import '../domain/discovery_models.dart';

final discoveryApiProvider = Provider<DiscoveryApi>((ref) {
  return DiscoveryApi(client: ref.watch(apiClientProvider));
});

/// Discovery feed. Only the server decides who appears. The client never filters for privacy or eligibility.
class DiscoveryScreen extends ConsumerStatefulWidget {
  const DiscoveryScreen({super.key});

  @override
  ConsumerState<DiscoveryScreen> createState() => _DiscoveryScreenState();
}

class _DiscoveryScreenState extends ConsumerState<DiscoveryScreen> {
  final List<DiscoveryCard> _cards = [];
  final Set<String> _liked = {};
  final Set<String> _pending = {};
  String? _nextCursor;
  bool _loading = true;
  bool _loadingMore = false;
  bool _failed = false;
  bool _reachedEnd = false;
  String? _notice;

  @override
  void initState() {
    super.initState();
    _loadFirstPage();
  }

  Future<void> _loadFirstPage() async {
    setState(() {
      _loading = true;
      _failed = false;
      _reachedEnd = false;
      _notice = null;
      _cards.clear();
      _nextCursor = null;
    });
    await _fetch(first: true);
  }

  Future<void> _loadMore() async {
    if (_loadingMore || _reachedEnd || _nextCursor == null) {
      return;
    }
    setState(() => _loadingMore = true);
    await _fetch(first: false);
  }

  Future<void> _fetch({required bool first}) async {
    final result = await ref
        .read(discoveryApiProvider)
        .fetchPage(cursor: first ? null : _nextCursor);
    if (!mounted) {
      return;
    }
    switch (result) {
      case ApiSuccess(:final data):
        setState(() {
          _cards.addAll(data.items);
          _nextCursor = data.nextCursor;
          _reachedEnd = data.nextCursor == null;
          _loading = false;
          _loadingMore = false;
        });
      case _:
        setState(() {
          _loading = false;
          _loadingMore = false;
          _failed = first;
          _notice = _discoveryMessage(result);
        });
    }
  }

  /// Likes or unlikes with an optimistic update. The state is rolled back when the server rejects the change.
  Future<void> _toggleLike(DiscoveryCard card) async {
    final publicId = card.publicId;
    if (_pending.contains(publicId)) {
      return;
    }
    final wasLiked = _liked.contains(publicId);
    setState(() {
      _pending.add(publicId);
      if (wasLiked) {
        _liked.remove(publicId);
      } else {
        _liked.add(publicId);
      }
    });
    final api = ref.read(discoveryApiProvider);
    final result = wasLiked
        ? await api.unlike(publicId)
        : await api.like(publicId);
    if (!mounted) {
      return;
    }
    setState(() {
      _pending.remove(publicId);
      if (result is! ApiSuccess) {
        if (wasLiked) {
          _liked.add(publicId);
        } else {
          _liked.remove(publicId);
        }
        _notice = _discoveryMessage(result);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        elevation: 0,
        title: const BrandHeader(),
        titleSpacing: AppSpacing.md,
        actions: [
          TextButton(
            onPressed: () => context.go(AppRoutes.profile),
            child: const Text('My profile'),
          ),
        ],
      ),
      body: SafeArea(child: _body(context)),
    );
  }

  Widget _body(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_failed) {
      return _DiscoveryMessage(
        message: _notice ?? 'Discovery is unavailable right now.',
        actionLabel: 'Try again',
        onAction: _loadFirstPage,
      );
    }
    if (_cards.isEmpty) {
      return _DiscoveryMessage(
        message: 'No profiles to show yet. Check back later.',
        actionLabel: 'Refresh',
        onAction: _loadFirstPage,
      );
    }
    return RefreshIndicator(
      onRefresh: _loadFirstPage,
      child: ListView.builder(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.md,
          AppSpacing.sm,
          AppSpacing.md,
          AppSpacing.xxxl,
        ),
        itemCount: _cards.length + 1,
        itemBuilder: (context, index) {
          if (index == _cards.length) {
            return _footer();
          }
          final card = _cards[index];
          return Padding(
            padding: const EdgeInsets.only(bottom: AppSpacing.md),
            child: _DiscoveryCardView(
              card: card,
              liked: _liked.contains(card.publicId),
              busy: _pending.contains(card.publicId),
              onToggleLike: () => _toggleLike(card),
            ),
          );
        },
      ),
    );
  }

  Widget _footer() {
    if (_notice != null) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: AppSpacing.md),
        child: Text(
          _notice!,
          textAlign: TextAlign.center,
          style: const TextStyle(color: AppColors.error),
        ),
      );
    }
    if (_reachedEnd) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: AppSpacing.md),
        child: Text("You're all caught up.", textAlign: TextAlign.center),
      );
    }
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.md),
      child: Center(
        child: OutlinedButton(
          onPressed: _loadingMore ? null : _loadMore,
          child: _loadingMore
              ? const SizedBox(
                  height: 18,
                  width: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Text('Load more'),
        ),
      ),
    );
  }
}

String _discoveryMessage(ApiResult<Object?> result) {
  return switch (result) {
    ApiUnauthorized() => 'Your session has expired. Sign in again to continue.',
    ApiForbidden(:final code) when code == 'PROFILE_NOT_FOUND' =>
      'That profile is no longer available.',
    ApiForbidden() => 'Discovery is not available for your account.',
    _ => 'We could not reach PritiMitra. Check your connection and try again.',
  };
}

class _DiscoveryCardView extends StatelessWidget {
  const _DiscoveryCardView({
    required this.card,
    required this.liked,
    required this.busy,
    required this.onToggleLike,
  });

  final DiscoveryCard card;
  final bool liked;
  final bool busy;
  final VoidCallback onToggleLike;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final goals = card.goals.map((g) => profileGoalLabels[g] ?? g);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(AppRadii.xl),
        border: Border.all(color: AppColors.border),
      ),
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(card.displayName, style: theme.textTheme.titleLarge),
            if (card.pronouns != null) ...[
              const SizedBox(height: AppSpacing.xxs),
              Text(
                card.pronouns!,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: AppColors.muted,
                ),
              ),
            ],
            if (card.bio != null && card.bio!.isNotEmpty) ...[
              const SizedBox(height: AppSpacing.sm),
              Text(card.bio!, style: theme.textTheme.bodyMedium),
            ],
            if (card.profilePrompt != null &&
                card.profilePrompt!.isNotEmpty) ...[
              const SizedBox(height: AppSpacing.sm),
              Text(
                card.profilePrompt!,
                style: theme.textTheme.bodyMedium?.copyWith(
                  fontStyle: FontStyle.italic,
                ),
              ),
            ],
            if (goals.isNotEmpty) ...[
              const SizedBox(height: AppSpacing.sm),
              Wrap(
                spacing: AppSpacing.xs,
                runSpacing: AppSpacing.xs,
                children: [for (final goal in goals) Chip(label: Text(goal))],
              ),
            ],
            const SizedBox(height: AppSpacing.md),
            Align(
              alignment: Alignment.centerRight,
              child: FilledButton.icon(
                onPressed: busy ? null : onToggleLike,
                icon: Icon(liked ? Icons.favorite : Icons.favorite_border),
                label: Text(liked ? 'Liked' : 'Like'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _DiscoveryMessage extends StatelessWidget {
  const _DiscoveryMessage({
    required this.message,
    required this.actionLabel,
    required this.onAction,
  });

  final String message;
  final String actionLabel;
  final VoidCallback onAction;

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
            OutlinedButton(onPressed: onAction, child: Text(actionLabel)),
          ],
        ),
      ),
    );
  }
}
