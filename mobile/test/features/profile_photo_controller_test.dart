import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http_parser/http_parser.dart';
import 'package:pritimitra/core/network/api_result.dart';
import 'package:pritimitra/features/profile/application/profile_photo_controller.dart';
import 'package:pritimitra/features/profile/data/profile_photo_api.dart';
import 'package:pritimitra/features/profile/domain/profile_photo.dart';

final _pngBytes = Uint8List.fromList([
  0x89,
  0x50,
  0x4E,
  0x47,
  0x0D,
  0x0A,
  0x1A,
  0x0A,
  0,
  0,
  0,
  0,
]);

final _photo = ProfilePhoto(
  id: 'p1',
  contentType: 'image/png',
  sizeBytes: _pngBytes.length,
);

class _FakePicker implements PhotoPicker {
  _FakePicker(this.picked);

  PickedPhoto? picked;
  int calls = 0;
  Completer<void>? gate;

  @override
  Future<PickedPhoto?> pickImage() async {
    calls++;
    if (gate != null) {
      await gate!.future;
    }
    return picked;
  }
}

class _FakePhotoApi extends ProfilePhotoApi {
  _FakePhotoApi() : super(null);

  int listCalls = 0;
  int uploadCalls = 0;
  final deleted = <String>[];
  ApiResult<ProfilePhoto> Function(int call) onUpload = (_) =>
      ApiSuccess(_photo);
  ApiResult<void> deleteResult = const ApiSuccess(null);

  @override
  Future<ApiResult<List<ProfilePhoto>>> listPhotos() async {
    listCalls++;
    return ApiSuccess(<ProfilePhoto>[]);
  }

  @override
  Future<ApiResult<ProfilePhoto>> uploadPhoto({
    required Uint8List bytes,
    required String filename,
    required MediaType contentType,
    void Function(int sentBytes, int totalBytes)? onProgress,
  }) async {
    uploadCalls++;
    onProgress?.call(bytes.length, bytes.length);
    return onUpload(uploadCalls);
  }

  @override
  Future<ApiResult<void>> deletePhoto(String id) async {
    deleted.add(id);
    return deleteResult;
  }
}

PickedPhoto _picked({Uint8List? bytes, int? length, void Function()? onRead}) {
  final data = bytes ?? _pngBytes;
  return PickedPhoto(
    name: 'photo.png',
    length: length ?? data.length,
    readBytes: () async {
      onRead?.call();
      return data;
    },
  );
}

ProviderContainer _container(_FakePicker picker, _FakePhotoApi api) {
  final container = ProviderContainer(
    overrides: [
      photoPickerProvider.overrideWithValue(picker),
      profilePhotoApiProvider.overrideWithValue(api),
    ],
  );
  addTearDown(container.dispose);
  return container;
}

void main() {
  test('detects JPEG and PNG from signatures, ignoring other bytes', () {
    expect(
      detectPhotoContentType(Uint8List.fromList([0xFF, 0xD8, 0xFF, 0xE0]))
          ?.mimeType,
      'image/jpeg',
    );
    expect(detectPhotoContentType(_pngBytes)?.mimeType, 'image/png');
    expect(
      detectPhotoContentType(Uint8List.fromList('GIF89a'.codeUnits)),
      isNull,
    );
    expect(detectPhotoContentType(Uint8List.fromList([0x89, 0x50])), isNull);
  });

  test('rejects oversized files before reading their bytes', () async {
    var read = false;
    final picker = _FakePicker(
      _picked(length: maxProfilePhotoBytes + 1, onRead: () => read = true),
    );
    final api = _FakePhotoApi();
    final container = _container(picker, api);
    final sub = container.listen(profilePhotoActionsProvider, (_, _) {});
    addTearDown(sub.close);

    await container.read(profilePhotoActionsProvider.notifier).pickAndUpload();

    expect(read, isFalse);
    expect(api.uploadCalls, 0);
    expect(container.read(profilePhotoActionsProvider).error, contains('5 MB'));
  });

  test('rejects files whose signature is not JPEG or PNG', () async {
    final picker = _FakePicker(
      _picked(bytes: Uint8List.fromList('GIF89a-data'.codeUnits)),
    );
    final api = _FakePhotoApi();
    final container = _container(picker, api);
    final sub = container.listen(profilePhotoActionsProvider, (_, _) {});
    addTearDown(sub.close);

    await container.read(profilePhotoActionsProvider.notifier).pickAndUpload();

    expect(api.uploadCalls, 0);
    expect(
      container.read(profilePhotoActionsProvider).error,
      contains('JPEG and PNG'),
    );
  });

  test('a cancelled pick changes nothing', () async {
    final picker = _FakePicker(null);
    final api = _FakePhotoApi();
    final container = _container(picker, api);
    final sub = container.listen(profilePhotoActionsProvider, (_, _) {});
    addTearDown(sub.close);

    await container.read(profilePhotoActionsProvider.notifier).pickAndUpload();

    expect(api.uploadCalls, 0);
    expect(container.read(profilePhotoActionsProvider).error, isNull);
  });

  test('a successful upload clears state and refreshes the list', () async {
    final picker = _FakePicker(_picked());
    final api = _FakePhotoApi();
    final container = _container(picker, api);
    final sub = container.listen(profilePhotoActionsProvider, (_, _) {});
    addTearDown(sub.close);

    await container.read(profilePhotoActionsProvider.notifier).pickAndUpload();

    final state = container.read(profilePhotoActionsProvider);
    expect(api.uploadCalls, 1);
    expect(state.uploading, isFalse);
    expect(state.error, isNull);
    expect(state.pending, isNull);
    final listed = container.listen(ownPhotosProvider, (_, _) {});
    addTearDown(listed.close);
    await container.read(ownPhotosProvider.future);
    expect(api.listCalls, greaterThanOrEqualTo(1));
  });

  test('a second pick while busy is ignored', () async {
    final gate = Completer<void>();
    final picker = _FakePicker(_picked())..gate = gate;
    final api = _FakePhotoApi();
    final container = _container(picker, api);
    final sub = container.listen(profilePhotoActionsProvider, (_, _) {});
    addTearDown(sub.close);
    final notifier = container.read(profilePhotoActionsProvider.notifier);

    final first = notifier.pickAndUpload();
    final second = notifier.pickAndUpload();
    gate.complete();
    await Future.wait([first, second]);

    expect(picker.calls, 1);
    expect(api.uploadCalls, 1);
  });

  test(
    'a 503 failure is retryable and retry reuses the prepared bytes',
    () async {
      final picker = _FakePicker(_picked());
      final api = _FakePhotoApi()
        ..onUpload = (call) => call == 1
            ? ApiUnavailable<ProfilePhoto>(
                statusCode: 503,
                code: 'PHOTO_STORAGE_UNAVAILABLE',
              )
            : ApiSuccess(_photo);
      final container = _container(picker, api);
      final sub = container.listen(profilePhotoActionsProvider, (_, _) {});
      addTearDown(sub.close);
      final notifier = container.read(profilePhotoActionsProvider.notifier);

      await notifier.pickAndUpload();
      var state = container.read(profilePhotoActionsProvider);
      expect(state.retryable, isTrue);
      expect(state.error, contains('temporarily unavailable'));
      expect(state.pending, isNotNull);

      await notifier.retryUpload();
      state = container.read(profilePhotoActionsProvider);
      expect(api.uploadCalls, 2);
      expect(state.error, isNull);
      expect(picker.calls, 1);
    },
  );

  test(
    'a rejected image is not retryable and drops the pending upload',
    () async {
      final picker = _FakePicker(_picked());
      final api = _FakePhotoApi()
        ..onUpload = (_) => const ApiUnavailable<ProfilePhoto>(
          statusCode: 413,
          code: 'PHOTO_TOO_LARGE',
        );
      final container = _container(picker, api);
      final sub = container.listen(profilePhotoActionsProvider, (_, _) {});
      addTearDown(sub.close);
      final notifier = container.read(profilePhotoActionsProvider.notifier);

      await notifier.pickAndUpload();
      final state = container.read(profilePhotoActionsProvider);

      expect(state.retryable, isFalse);
      expect(state.pending, isNull);
      expect(state.error, contains('5 MB'));

      await notifier.retryUpload();
      expect(api.uploadCalls, 1);
    },
  );

  test(
    'an expired session asks the user to sign in and is not retryable',
    () async {
      final picker = _FakePicker(_picked());
      final api = _FakePhotoApi()
        ..onUpload = (_) => const ApiUnauthorized<ProfilePhoto>();
      final container = _container(picker, api);
      final sub = container.listen(profilePhotoActionsProvider, (_, _) {});
      addTearDown(sub.close);

      await container
          .read(profilePhotoActionsProvider.notifier)
          .pickAndUpload();
      final state = container.read(profilePhotoActionsProvider);

      expect(state.error, contains('Sign in again'));
      expect(state.retryable, isFalse);
    },
  );

  test('a successful delete refreshes the list', () async {
    final api = _FakePhotoApi();
    final container = _container(_FakePicker(null), api);
    final sub = container.listen(profilePhotoActionsProvider, (_, _) {});
    addTearDown(sub.close);

    final removed = await container
        .read(profilePhotoActionsProvider.notifier)
        .deletePhoto('p1');

    expect(removed, isTrue);
    expect(api.deleted, ['p1']);
    expect(container.read(profilePhotoActionsProvider).deletingIds, isEmpty);
  });

  test('a failed delete keeps the photo and shows a safe message', () async {
    final api = _FakePhotoApi()
      ..deleteResult = const ApiUnavailable(statusCode: 500);
    final container = _container(_FakePicker(null), api);
    final sub = container.listen(profilePhotoActionsProvider, (_, _) {});
    addTearDown(sub.close);

    final removed = await container
        .read(profilePhotoActionsProvider.notifier)
        .deletePhoto('p1');

    expect(removed, isFalse);
    final state = container.read(profilePhotoActionsProvider);
    expect(state.error, isNotNull);
    expect(state.deletingIds, isEmpty);
  });
}
