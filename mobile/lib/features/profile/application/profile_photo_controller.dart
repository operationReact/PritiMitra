import 'dart:typed_data';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http_parser/http_parser.dart';
import 'package:image_picker/image_picker.dart';

import '../../../core/network/api_result.dart';
import '../../auth/application/auth_controller.dart';
import '../data/profile_photo_api.dart';
import '../domain/profile_photo.dart';
import 'profile_controller.dart';

final profilePhotoApiProvider = Provider<ProfilePhotoApi>((ref) {
  return ProfilePhotoApi(ref.watch(apiClientProvider));
});

/// A picked image. Bytes are read lazily so an oversized file is rejected before it is loaded.
class PickedPhoto {
  const PickedPhoto({
    required this.name,
    required this.length,
    required this.readBytes,
  });

  final String name;
  final int length;
  final Future<Uint8List> Function() readBytes;
}

abstract interface class PhotoPicker {
  /// Returns null when the user cancels.
  Future<PickedPhoto?> pickImage();
}

class ImagePickerPhotoPicker implements PhotoPicker {
  const ImagePickerPhotoPicker();

  @override
  Future<PickedPhoto?> pickImage() async {
    final file = await ImagePicker().pickImage(
      source: ImageSource.gallery,
      requestFullMetadata: false,
    );
    if (file == null) {
      return null;
    }
    return PickedPhoto(
      name: file.name,
      length: await file.length(),
      readBytes: file.readAsBytes,
    );
  }
}

final photoPickerProvider = Provider<PhotoPicker>((ref) {
  return const ImagePickerPhotoPicker();
});

final ownPhotosProvider = FutureProvider.autoDispose<List<ProfilePhoto>>((
  ref,
) async {
  final result = await ref.read(profilePhotoApiProvider).listPhotos();
  return switch (result) {
    ApiSuccess(:final data) => data,
    _ => throw ProfileLoadFailure(result),
  };
});

/// Owner-only photo bytes, fetched through the authenticated client.
final profilePhotoContentProvider = FutureProvider.autoDispose
    .family<Uint8List, String>((ref, id) async {
      final result = await ref.read(profilePhotoApiProvider).photoContent(id);
      return switch (result) {
        ApiSuccess(:final data) => data,
        _ => throw ProfileLoadFailure(result),
      };
    });

class PendingUpload {
  const PendingUpload({
    required this.bytes,
    required this.name,
    required this.type,
  });

  final Uint8List bytes;
  final String name;
  final MediaType type;
}

class PhotoActionState {
  const PhotoActionState({
    this.uploading = false,
    this.progress,
    this.error,
    this.retryable = false,
    this.pending,
    this.deletingIds = const {},
  });

  final bool uploading;

  /// Fraction of bytes handed to the transport. Not server acceptance.
  final double? progress;
  final String? error;
  final bool retryable;
  final PendingUpload? pending;
  final Set<String> deletingIds;

  PhotoActionState copyWith({
    bool? uploading,
    double? progress,
    String? error,
    bool? retryable,
    PendingUpload? pending,
    bool clearPending = false,
    Set<String>? deletingIds,
  }) {
    return PhotoActionState(
      uploading: uploading ?? this.uploading,
      progress: progress,
      error: error,
      retryable: retryable ?? this.retryable,
      pending: clearPending ? null : (pending ?? this.pending),
      deletingIds: deletingIds ?? this.deletingIds,
    );
  }
}

final profilePhotoActionsProvider =
    NotifierProvider.autoDispose<ProfilePhotoActions, PhotoActionState>(
      ProfilePhotoActions.new,
    );

class ProfilePhotoActions extends Notifier<PhotoActionState> {
  /// Synchronous re-entry guard. State updates are not visible until the next frame.
  bool _busy = false;

  @override
  PhotoActionState build() => const PhotoActionState();

  Future<void> pickAndUpload() async {
    if (_busy) {
      return;
    }
    _busy = true;
    try {
      final picked = await ref.read(photoPickerProvider).pickImage();
      if (picked == null || !ref.mounted) {
        return;
      }
      if (picked.length > maxProfilePhotoBytes) {
        _set(
          const PhotoActionState(
            error: 'That photo is larger than 5 MB. Choose a smaller image.',
          ),
        );
        return;
      }
      final bytes = await picked.readBytes();
      if (!ref.mounted) {
        return;
      }
      final type = detectPhotoContentType(bytes);
      if (type == null) {
        _set(
          const PhotoActionState(
            error:
                'Only JPEG and PNG photos are supported. Choose another photo.',
          ),
        );
        return;
      }
      await _upload(PendingUpload(bytes: bytes, name: picked.name, type: type));
    } finally {
      _busy = false;
    }
  }

  /// Retries only failures the server or network marked as recoverable.
  Future<void> retryUpload() async {
    final pending = state.pending;
    if (_busy || pending == null || !state.retryable) {
      return;
    }
    _busy = true;
    try {
      await _upload(pending);
    } finally {
      _busy = false;
    }
  }

  /// Returns true when the photo is gone from the server.
  Future<bool> deletePhoto(String id) async {
    if (_busy || state.deletingIds.contains(id)) {
      return false;
    }
    _busy = true;
    _set(state.copyWith(deletingIds: {...state.deletingIds, id}));
    try {
      final result = await ref.read(profilePhotoApiProvider).deletePhoto(id);
      if (!ref.mounted) {
        return false;
      }
      final remaining = {...state.deletingIds}..remove(id);
      if (result case ApiSuccess()) {
        _set(state.copyWith(deletingIds: remaining, error: null));
        ref.invalidate(ownPhotosProvider);
        return true;
      }
      _set(
        state.copyWith(
          deletingIds: remaining,
          error: photoErrorMessage(result),
          retryable: false,
        ),
      );
      return false;
    } finally {
      _busy = false;
    }
  }

  Future<void> _upload(PendingUpload pending) async {
    _set(
      PhotoActionState(
        uploading: true,
        progress: 0,
        pending: pending,
        deletingIds: state.deletingIds,
      ),
    );
    final result = await ref
        .read(profilePhotoApiProvider)
        .uploadPhoto(
          bytes: pending.bytes,
          filename: pending.name,
          contentType: pending.type,
          onProgress: (sent, total) {
            if (total > 0) {
              _set(
                state.copyWith(
                  uploading: true,
                  progress: sent / total,
                  pending: pending,
                ),
              );
            }
          },
        );
    if (!ref.mounted) {
      return;
    }
    switch (result) {
      case ApiSuccess():
        _set(PhotoActionState(deletingIds: state.deletingIds));
        ref.invalidate(ownPhotosProvider);
      default:
        _set(
          state.copyWith(
            uploading: false,
            progress: null,
            error: photoErrorMessage(result),
            retryable: isRecoverablePhotoFailure(result),
            pending: pending,
            clearPending: !isRecoverablePhotoFailure(result),
          ),
        );
    }
  }

  void _set(PhotoActionState next) {
    if (ref.mounted) {
      state = next;
    }
  }
}

bool isRecoverablePhotoFailure(ApiResult<Object?> result) {
  return switch (result) {
    ApiUnavailable(:final statusCode) =>
      statusCode == null || statusCode >= 500,
    _ => false,
  };
}

/// Maps a photo API outcome to a safe, user-facing message. Never includes raw server text.
String photoErrorMessage(ApiResult<Object?> result) {
  return switch (result) {
    ApiUnauthorized() => 'Your session has expired. Sign in again to continue.',
    ApiForbidden(:final code) when code == 'ACCOUNT_NOT_ELIGIBLE' =>
      'Your account is not eligible to add photos yet.',
    ApiForbidden() => 'This change is not allowed for your account.',
    ApiUnavailable(:final statusCode, :final code) => _unavailableMessage(
      statusCode,
      code,
    ),
    _ => 'Something went wrong. Try again.',
  };
}

String _unavailableMessage(int? statusCode, String? code) {
  return switch (code) {
    'PHOTO_TOO_LARGE' =>
      'That photo is larger than 5 MB. Choose a smaller image.',
    'PHOTO_LIMIT_REACHED' =>
      'You have reached the photo limit. Remove a photo to add another.',
    'INVALID_PHOTO' || 'PHOTO_DIMENSIONS_TOO_LARGE' =>
      'This photo could not be accepted. Choose a different JPEG or PNG.',
    'PHOTO_NOT_FOUND' => 'This photo is no longer available.',
    'PHOTO_STORAGE_UNAVAILABLE' =>
      'Photo storage is temporarily unavailable. Try again shortly.',
    _ when statusCode == 413 =>
      'That photo is larger than 5 MB. Choose a smaller image.',
    _ when statusCode != null && statusCode >= 500 =>
      'Photo storage is temporarily unavailable. Try again shortly.',
    _ => 'We could not reach PritiMitra. Check your connection and try again.',
  };
}
