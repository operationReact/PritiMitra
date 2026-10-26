import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_tokens.dart';
import '../application/profile_controller.dart';
import '../application/profile_photo_controller.dart';
import '../domain/profile_photo.dart';

/// Matches the backend per-profile photo limit. The server still enforces it.
const _maxProfilePhotos = 6;

class ProfilePhotosSection extends ConsumerWidget {
  const ProfilePhotosSection({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final photos = ref.watch(ownPhotosProvider);
    final actions = ref.watch(profilePhotoActionsProvider);
    final count = photos.asData?.value.length ?? 0;
    final atLimit = count >= _maxProfilePhotos;
    final canAdd =
        !actions.uploading && !atLimit && actions.deletingIds.isEmpty;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Expanded(child: Text('Photos', style: theme.textTheme.titleMedium)),
            Text(
              '$count of $_maxProfilePhotos',
              style: theme.textTheme.bodySmall?.copyWith(
                color: AppColors.muted,
              ),
            ),
          ],
        ),
        const SizedBox(height: AppSpacing.xxs),
        Text(
          'Photos are not identity-verified. Only you can see them until '
          'moderation is available.',
          style: theme.textTheme.bodySmall?.copyWith(color: AppColors.muted),
        ),
        const SizedBox(height: AppSpacing.sm),
        OutlinedButton.icon(
          onPressed: canAdd
              ? () => ref
                    .read(profilePhotoActionsProvider.notifier)
                    .pickAndUpload()
              : null,
          icon: const Icon(Icons.add_photo_alternate_outlined),
          label: Text(atLimit ? 'Photo limit reached' : 'Add photo'),
        ),
        if (actions.uploading) ...[
          const SizedBox(height: AppSpacing.sm),
          _UploadProgress(progress: actions.progress),
        ],
        if (actions.error != null) ...[
          const SizedBox(height: AppSpacing.sm),
          _PhotoError(
            message: actions.error!,
            onRetry: actions.retryable
                ? () => ref
                      .read(profilePhotoActionsProvider.notifier)
                      .retryUpload()
                : null,
          ),
        ],
        const SizedBox(height: AppSpacing.md),
        photos.when(
          loading: () => const Center(
            child: Padding(
              padding: EdgeInsets.all(AppSpacing.lg),
              child: CircularProgressIndicator(),
            ),
          ),
          error: (error, _) => _LoadError(
            message: profileErrorMessage(error),
            onRetry: () => ref.invalidate(ownPhotosProvider),
          ),
          data: (items) {
            if (items.isEmpty) {
              return const _EmptyPhotos();
            }
            return _PhotoGrid(photos: items, deletingIds: actions.deletingIds);
          },
        ),
      ],
    );
  }
}

class _UploadProgress extends StatelessWidget {
  const _UploadProgress({required this.progress});

  final double? progress;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    // Transport progress reaching 100% does not mean the server has accepted the photo.
    final checking = progress != null && progress! >= 1;
    return Semantics(
      liveRegion: true,
      label: checking ? 'Checking your photo' : 'Uploading photo',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            checking ? 'Checking your photo…' : 'Uploading…',
            style: theme.textTheme.bodySmall,
          ),
          const SizedBox(height: AppSpacing.xxs),
          LinearProgressIndicator(
            value: checking ? null : progress,
            color: AppColors.primary,
            backgroundColor: AppColors.primarySoft,
          ),
        ],
      ),
    );
  }
}

class _PhotoError extends StatelessWidget {
  const _PhotoError({required this.message, this.onRetry});

  final String message;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Semantics(
      liveRegion: true,
      child: Container(
        padding: const EdgeInsets.all(AppSpacing.sm),
        decoration: BoxDecoration(
          color: AppColors.errorSoft,
          borderRadius: BorderRadius.circular(AppRadii.md),
        ),
        child: Row(
          children: [
            const Icon(Icons.error_outline, color: AppColors.error),
            const SizedBox(width: AppSpacing.xs),
            Expanded(
              child: Text(
                message,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: AppColors.navy,
                ),
              ),
            ),
            if (onRetry != null)
              TextButton(onPressed: onRetry, child: const Text('Retry')),
          ],
        ),
      ),
    );
  }
}

class _LoadError extends StatelessWidget {
  const _LoadError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(message, textAlign: TextAlign.center),
        const SizedBox(height: AppSpacing.xs),
        TextButton(onPressed: onRetry, child: const Text('Try again')),
      ],
    );
  }
}

class _EmptyPhotos extends StatelessWidget {
  const _EmptyPhotos();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: AppColors.lavenderSurface,
        borderRadius: BorderRadius.circular(AppRadii.md),
        border: Border.all(color: AppColors.borderLavender),
      ),
      child: Column(
        children: [
          const Icon(Icons.photo_library_outlined, color: AppColors.primary),
          const SizedBox(height: AppSpacing.xs),
          Text(
            'No photos yet. Add a JPEG or PNG up to 5 MB.',
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
        ],
      ),
    );
  }
}

class _PhotoGrid extends ConsumerWidget {
  const _PhotoGrid({required this.photos, required this.deletingIds});

  final List<ProfilePhoto> photos;
  final Set<String> deletingIds;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return GridView.count(
      crossAxisCount: 3,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      crossAxisSpacing: AppSpacing.xs,
      mainAxisSpacing: AppSpacing.xs,
      children: [
        for (final photo in photos)
          _PhotoTile(
            photo: photo,
            deleting: deletingIds.contains(photo.id),
            onDelete: () => _confirmDelete(context, ref, photo),
          ),
      ],
    );
  }

  Future<void> _confirmDelete(
    BuildContext context,
    WidgetRef ref,
    ProfilePhoto photo,
  ) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Remove this photo?'),
        content: const Text('This cannot be undone.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Remove'),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      await ref
          .read(profilePhotoActionsProvider.notifier)
          .deletePhoto(photo.id);
    }
  }
}

class _PhotoTile extends ConsumerWidget {
  const _PhotoTile({
    required this.photo,
    required this.deleting,
    required this.onDelete,
  });

  final ProfilePhoto photo;
  final bool deleting;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final content = ref.watch(profilePhotoContentProvider(photo.id));
    return Semantics(
      label: 'Your profile photo',
      child: ClipRRect(
        borderRadius: BorderRadius.circular(AppRadii.md),
        child: Stack(
          fit: StackFit.expand,
          children: [
            content.when(
              loading: () => const ColoredBox(
                color: AppColors.lavenderSurface,
                child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
              ),
              error: (_, _) => const ColoredBox(
                color: AppColors.lavenderSurface,
                child: Icon(
                  Icons.broken_image_outlined,
                  color: AppColors.muted,
                ),
              ),
              data: (bytes) => Image.memory(bytes, fit: BoxFit.cover),
            ),
            Positioned(
              top: AppSpacing.xxs,
              right: AppSpacing.xxs,
              child: Material(
                color: AppColors.surface.withValues(alpha: 0.9),
                shape: const CircleBorder(),
                child: IconButton(
                  tooltip: 'Remove photo',
                  visualDensity: VisualDensity.compact,
                  onPressed: deleting ? null : onDelete,
                  icon: deleting
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.delete_outline, color: AppColors.navy),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
