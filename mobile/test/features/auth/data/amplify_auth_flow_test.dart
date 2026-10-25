import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:pritimitra/core/config/app_config.dart';
import 'package:pritimitra/features/auth/data/amplify_auth_gateway.dart';

/// First amplify_auth_cognito_dart release with Cognito refresh-token rotation.
const _minimumRotationAwareVersion = (0, 11, 14);

(int, int, int) _parseVersion(String value) {
  final parts = value.split('.').map(int.parse).toList();
  return (parts[0], parts[1], parts[2]);
}

bool _isAtLeast((int, int, int) actual, (int, int, int) minimum) {
  if (actual.$1 != minimum.$1) return actual.$1 > minimum.$1;
  if (actual.$2 != minimum.$2) return actual.$2 > minimum.$2;
  return actual.$3 >= minimum.$3;
}

String _lockedVersion(String package) {
  final lines = File('pubspec.lock').readAsLinesSync();
  final start = lines.indexWhere((line) => line == '  $package:');
  expect(start, isNonNegative, reason: '$package missing from pubspec.lock');
  for (var i = start + 1; i < lines.length; i++) {
    final line = lines[i];
    if (line.startsWith('  ') && !line.startsWith('    ')) break;
    final match = RegExp(r'^\s+version: "([^"]+)"').firstMatch(line);
    if (match != null) return match.group(1)!;
  }
  fail('No version found for $package in pubspec.lock');
}

void main() {
  const config = AppConfig(
    cognitoRegion: 'ap-south-1',
    cognitoUserPoolId: 'ap-south-1_example',
    cognitoClientId: 'example-client-id',
  );

  group('Amplify Cognito flow contract', () {
    test('mobile config sets no auth flow override, so SRP remains the default',
        () {
      final auth = buildAmplifyOutputs(config)['auth'] as Map<String, dynamic>;
      final flowKeys = auth.keys.where(
        (key) => key.toLowerCase().contains('flow'),
      );
      expect(flowKeys, isEmpty);
      expect(
        auth.toString(),
        isNot(contains('REFRESH_TOKEN_AUTH')),
        reason: 'Refresh-token rotation requires GetTokensFromRefreshToken.',
      );
    });

    test('locked amplify_auth_cognito_dart supports refresh-token rotation',
        () {
      final locked = _parseVersion(_lockedVersion('amplify_auth_cognito_dart'));
      expect(
        _isAtLeast(locked, _minimumRotationAwareVersion),
        isTrue,
        reason: 'Refresh-token rotation landed in amplify_auth_cognito_dart '
            '0.11.14. Upgrade before relying on rotation.',
      );
    });
  });
}
