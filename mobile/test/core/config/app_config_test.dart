import 'package:flutter_test/flutter_test.dart';
import 'package:pritimitra/core/config/app_config.dart';

void main() {
  group('AppConfig.isAuthConfigured', () {
    test('is false when any Cognito value is blank', () {
      expect(
        const AppConfig(
          cognitoUserPoolId: '',
          cognitoClientId: 'client',
          cognitoRegion: 'eu-west-1',
        ).isAuthConfigured,
        isFalse,
      );
      expect(
        const AppConfig(
          cognitoUserPoolId: 'pool',
          cognitoClientId: '   ',
          cognitoRegion: 'eu-west-1',
        ).isAuthConfigured,
        isFalse,
      );
      expect(
        const AppConfig(
          cognitoUserPoolId: 'pool',
          cognitoClientId: 'client',
          cognitoRegion: '',
        ).isAuthConfigured,
        isFalse,
      );
    });

    test('is true when all Cognito values are present', () {
      expect(
        const AppConfig(
          cognitoUserPoolId: 'eu-west-1_example',
          cognitoClientId: 'example-client-id',
          cognitoRegion: 'eu-west-1',
        ).isAuthConfigured,
        isTrue,
      );
    });
  });

  group('AppConfig.apiBaseUri', () {
    test('accepts absolute https and http URLs', () {
      expect(
        const AppConfig(apiBaseUrl: 'https://api.example.test').apiBaseUri,
        Uri.parse('https://api.example.test'),
      );
      expect(
        const AppConfig(apiBaseUrl: 'http://10.0.2.2:8080').apiBaseUri,
        Uri.parse('http://10.0.2.2:8080'),
      );
    });

    test('rejects missing, relative, and non-http schemes', () {
      expect(const AppConfig(apiBaseUrl: '').apiBaseUri, isNull);
      expect(const AppConfig(apiBaseUrl: '/api').apiBaseUri, isNull);
      expect(const AppConfig(apiBaseUrl: 'ftp://example.test').apiBaseUri, isNull);
      expect(const AppConfig(apiBaseUrl: 'https://').apiBaseUri, isNull);
    });
  });
}
