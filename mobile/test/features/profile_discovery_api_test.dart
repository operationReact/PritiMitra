import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pritimitra/core/network/api_result.dart';
import 'package:pritimitra/core/network/pritimitra_api_client.dart';
import 'package:pritimitra/features/auth/data/auth_gateway.dart';
import 'package:pritimitra/features/discovery/data/discovery_api.dart';
import 'package:pritimitra/features/discovery/domain/discovery_models.dart';
import 'package:pritimitra/features/profile/application/profile_controller.dart';
import 'package:pritimitra/features/profile/data/profile_api.dart';
import 'package:pritimitra/features/profile/domain/own_profile.dart';

class _FixedToken implements AccessTokenSource {
  @override
  Future<String?> accessToken({bool forceRefresh = false}) async => 'token';
}

PritiMitraApiClient _client(MockClientHandler handler) {
  return PritiMitraApiClient(
    baseUri: Uri.parse('https://api.example.test'),
    tokens: _FixedToken(),
    httpClient: MockClient(handler),
  );
}

Map<String, dynamic> _profileJson() => {
  'publicId': 'pub-1',
  'displayName': 'Asha',
  'bio': 'Hello',
  'profilePrompt': null,
  'genderIdentity': 'Woman',
  'genderIdentityVisible': true,
  'pronouns': null,
  'pronounsVisible': false,
  'sexualOrientation': null,
  'sexualOrientationVisible': false,
  'goals': ['FRIENDSHIP'],
  'interests': ['music'],
  'discoveryInterestedIn': ['WOMEN'],
  'discoveryMinAge': 18,
  'discoveryMaxAge': 40,
  'discoveryMaxDistanceKm': 25,
  'discoverable': true,
  'email': 'must-not-appear@example.test',
  'dateOfBirth': '2000-01-01',
};

void main() {
  group('OwnProfile', () {
    test('parses the owner profile and ignores fields it does not model', () {
      final profile = OwnProfile.fromJson(_profileJson());

      expect(profile.publicId, 'pub-1');
      expect(profile.goals, {'FRIENDSHIP'});
      expect(profile.discoverable, isTrue);
      expect(profile.discoveryMaxAge, 40);
    });

    test('update body sends editable fields and omits read-only ones', () {
      final body = OwnProfile.fromJson(_profileJson()).toUpdateJson();

      expect(body['displayName'], 'Asha');
      expect(body['goals'], ['FRIENDSHIP']);
      expect(body['discoveryMinAge'], 18);
      expect(body.containsKey('publicId'), isFalse);
      expect(body.containsKey('discoverable'), isFalse);
      expect(body.containsKey('email'), isFalse);
      expect(body.containsKey('dateOfBirth'), isFalse);
    });
  });

  group('DiscoveryPage', () {
    test('parses cards and a null cursor on the last page', () {
      final page = DiscoveryPage.fromJson({
        'items': [
          {
            'publicId': 'pub-2',
            'displayName': 'Ravi',
            'goals': ['COMMUNITY'],
            'interests': <String>[],
          },
          'not-a-map',
        ],
        'nextCursor': null,
      });

      expect(page.items, hasLength(1));
      expect(page.items.single, isA<DiscoveryCard>());
      expect(page.items.single.publicId, 'pub-2');
      expect(page.items.single.bio, isNull);
      expect(page.nextCursor, isNull);
    });

    test('tolerates a missing items list', () {
      final page = DiscoveryPage.fromJson({'nextCursor': 'abc'});

      expect(page.items, isEmpty);
      expect(page.nextCursor, 'abc');
    });
  });

  group('PritiMitraApiClient.deleteJson', () {
    test('sends DELETE to the requested path', () async {
      late http.Request captured;
      final client = _client((request) async {
        captured = request;
        return http.Response(jsonEncode({'liked': false}), 200);
      });

      final result = await client.deleteJson('/api/v1/likes/pub-9');

      expect(result, isA<ApiSuccess<Map<String, dynamic>>>());
      expect(captured.method, 'DELETE');
      expect(captured.url.path, '/api/v1/likes/pub-9');
    });
  });

  group('DiscoveryApi', () {
    test('encodes the cursor and page size in the query', () async {
      late Uri captured;
      final api = DiscoveryApi(
        client: _client((request) async {
          captured = request.url;
          return http.Response(
            jsonEncode({'items': [], 'nextCursor': null}),
            200,
          );
        }),
      );

      await api.fetchPage(cursor: 'a+b/c', size: 10);

      expect(captured.queryParameters['size'], '10');
      expect(captured.queryParameters['cursor'], 'a+b/c');
    });

    test(
      'like uses PUT and unlike uses DELETE on the encoded public id',
      () async {
        final methods = <String>[];
        final paths = <String>[];
        final api = DiscoveryApi(
          client: _client((request) async {
            methods.add(request.method);
            paths.add(request.url.path);
            return http.Response(
              jsonEncode({'liked': request.method == 'PUT'}),
              200,
            );
          }),
        );

        final liked = await api.like('pub 1');
        final unliked = await api.unlike('pub 1');

        expect(methods, ['PUT', 'DELETE']);
        expect(paths.every((p) => p == '/api/v1/likes/pub%201'), isTrue);
        expect(liked, isA<ApiSuccess<bool>>());
        expect((liked as ApiSuccess<bool>).data, isTrue);
        expect((unliked as ApiSuccess<bool>).data, isFalse);
      },
    );

    test('without a client it reports unavailable and makes no call', () async {
      final result = await DiscoveryApi(client: null).fetchPage();

      expect(result, isA<ApiUnavailable<DiscoveryPage>>());
    });

    test('fetchLikes reads the outgoing list and keeps unavailable entries nameless', () async {
      late Uri captured;
      final api = DiscoveryApi(
        client: _client((request) async {
          captured = request.url;
          return http.Response(
            jsonEncode({
              'items': [
                {'publicId': 'pub-a', 'displayName': 'Asha', 'available': true},
                {'publicId': 'pub-b', 'displayName': null, 'available': false},
              ],
              'nextCursor': 'next',
            }),
            200,
          );
        }),
      );

      final result = await api.fetchLikes(cursor: 'c+1', size: 5);

      expect(captured.path, '/api/v1/likes');
      expect(captured.queryParameters['size'], '5');
      expect(captured.queryParameters['cursor'], 'c+1');
      final page = (result as ApiSuccess<LikedProfilePage>).data;
      expect(page.items.map((p) => p.publicId), ['pub-a', 'pub-b']);
      expect(page.items[0].available, isTrue);
      expect(page.items[1].available, isFalse);
      expect(page.items[1].displayName, isNull);
      expect(page.nextCursor, 'next');
    });

    test(
      'a 404 from the server stays a status-coded unavailable result',
      () async {
        final api = DiscoveryApi(
          client: _client(
            (request) async =>
                http.Response(jsonEncode({'code': 'PROFILE_NOT_FOUND'}), 404),
          ),
        );

        final result = await api.like('missing');

        expect(result, isA<ApiUnavailable<bool>>());
        final unavailable = result as ApiUnavailable<bool>;
        expect(unavailable.statusCode, 404);
        expect(unavailable.code, 'PROFILE_NOT_FOUND');
      },
    );
  });

  group('ProfileApi', () {
    test('returns the parsed profile on success', () async {
      final api = ProfileApi(
        client: _client((request) async {
          expect(request.url.path, '/api/v1/profile');
          return http.Response(jsonEncode(_profileJson()), 200);
        }),
      );

      final result = await api.fetchOwnProfile();

      expect(result, isA<ApiSuccess<OwnProfile>>());
    });

    test(
      'maps an unparseable success body to unavailable instead of throwing',
      () async {
        final api = ProfileApi(
          client: _client(
            (request) async => http.Response(jsonEncode({'x': 1}), 200),
          ),
        );

        final result = await api.fetchOwnProfile();

        expect(result, isA<ApiUnavailable<OwnProfile>>());
      },
    );

    test('keeps PROFILE_INCOMPLETE from a 409 so the UI can explain it', () async {
      final api = ProfileApi(
        client: _client(
          (request) async =>
              http.Response(jsonEncode({'code': 'PROFILE_INCOMPLETE'}), 409),
        ),
      );

      final result = await api.setDiscoverable(discoverable: true);

      expect(result, isA<ApiUnavailable<OwnProfile>>());
      expect((result as ApiUnavailable<OwnProfile>).code, 'PROFILE_INCOMPLETE');
      expect(
        profileActionErrorMessage(result),
        'Add a display name and at least one goal before turning on discovery.',
      );
    });

    test('saveProfile sends a PUT with the full editable body', () async {
      late http.Request captured;
      final api = ProfileApi(
        client: _client((request) async {
          captured = request;
          return http.Response(jsonEncode(_profileJson()), 200);
        }),
      );

      await api.saveProfile(OwnProfile.fromJson(_profileJson()));

      expect(captured.method, 'PUT');
      expect(captured.url.path, '/api/v1/profile');
      expect(jsonDecode(captured.body)['displayName'], 'Asha');
    });
  });
}
