import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:http_parser/http_parser.dart';
import 'package:pritimitra/core/network/api_result.dart';
import 'package:pritimitra/core/network/pritimitra_api_client.dart';
import 'package:pritimitra/features/auth/data/auth_gateway.dart';
import 'package:pritimitra/features/profile/data/profile_photo_api.dart';
import 'package:pritimitra/features/profile/domain/profile_photo.dart';

class _FakeTokens implements AccessTokenSource {
  @override
  Future<String?> accessToken({bool forceRefresh = false}) async => 'access-1';
}

ProfilePhotoApi _api(MockClientHandler handler) {
  final client = PritiMitraApiClient(
    baseUri: Uri.parse('https://api.example.test'),
    tokens: _FakeTokens(),
    httpClient: MockClient(handler),
  );
  return ProfilePhotoApi(client);
}

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

void main() {
  group('listPhotos', () {
    test('parses owner-only metadata and never exposes storage keys', () async {
      final api = _api((request) async {
        expect(request.url.path, '/api/v1/profile/photos');
        return http.Response(
          jsonEncode([
            {
              'id': 'p1',
              'contentType': 'image/jpeg',
              'sizeBytes': 1024,
              'reviewStatus': 'PENDING',
              'createdAt': '2026-01-01T00:00:00Z',
            },
          ]),
          200,
        );
      });

      final result = await api.listPhotos();

      final photos = (result as ApiSuccess<List<ProfilePhoto>>).data;
      expect(photos.single.id, 'p1');
      expect(photos.single.contentType, 'image/jpeg');
      expect(photos.single.sizeBytes, 1024);
    });

    test('malformed payload is unavailable, not a crash', () async {
      final api = _api((_) async => http.Response('[{"id": 3}]', 200));

      final result = await api.listPhotos();

      expect(result, isA<ApiUnavailable<List<ProfilePhoto>>>());
    });

    test('a missing client is unavailable and makes no request', () async {
      const api = ProfilePhotoApi(null);

      expect(await api.listPhotos(), isA<ApiUnavailable<List<ProfilePhoto>>>());
    });
  });

  test('photoContent returns the owner image bytes', () async {
    final api = _api(
      (request) async => http.Response.bytes(
        _pngBytes,
        200,
        headers: {'content-type': 'image/png'},
      ),
    );

    final result = await api.photoContent('p1');

    expect((result as ApiSuccess<Uint8List>).data, _pngBytes);
  });

  test(
    'uploadPhoto sends the file field and returns the parsed photo',
    () async {
      late String body;
      final api = _api((request) async {
        body = utf8.decode(request.bodyBytes, allowMalformed: true);
        return http.Response(
          jsonEncode({
            'id': 'p2',
            'contentType': 'image/png',
            'sizeBytes': 12,
            'reviewStatus': 'PENDING',
            'createdAt': '2026-01-01T00:00:00Z',
          }),
          201,
        );
      });

      final result = await api.uploadPhoto(
        bytes: _pngBytes,
        filename: 'photo.png',
        contentType: MediaType('image', 'png'),
      );

      expect((result as ApiSuccess<ProfilePhoto>).data.id, 'p2');
      expect(body, contains('name="file"'));
      expect(body, contains('filename="photo.png"'));
      expect(body.toLowerCase(), contains('content-type: image/png'));
    },
  );

  test(
    'upload maps size, limit and validation codes to safe results',
    () async {
      final cases = {
        413: 'PHOTO_TOO_LARGE',
        409: 'PHOTO_LIMIT_REACHED',
        400: 'INVALID_PHOTO',
      };
      for (final entry in cases.entries) {
        final api = _api(
          (_) async =>
              http.Response(jsonEncode({'code': entry.value}), entry.key),
        );
        final result = await api.uploadPhoto(
          bytes: _pngBytes,
          filename: 'photo.png',
          contentType: MediaType('image', 'png'),
        );
        expect(
          result,
          isA<ApiUnavailable<ProfilePhoto>>(),
          reason: '${entry.key}',
        );
        final failure = result as ApiUnavailable<ProfilePhoto>;
        expect(failure.statusCode, entry.key);
        expect(failure.code, entry.value);
      }
    },
  );

  test('deleting a photo returns success on 204', () async {
    final api = _api((request) async {
      expect(request.method, 'DELETE');
      expect(request.url.path, '/api/v1/profile/photos/p1');
      return http.Response('', 204);
    });

    expect(await api.deletePhoto('p1'), isA<ApiSuccess<void>>());
  });

  test('deleting an already-removed photo is treated as success', () async {
    final api = _api(
      (_) async => http.Response(jsonEncode({'code': 'PHOTO_NOT_FOUND'}), 404),
    );

    expect(await api.deletePhoto('p1'), isA<ApiSuccess<void>>());
  });

  test('deleting fails closed on server errors', () async {
    final api = _api((_) async => http.Response('', 503));

    final result = await api.deletePhoto('p1');

    expect(result, isA<ApiUnavailable<void>>());
  });
}
