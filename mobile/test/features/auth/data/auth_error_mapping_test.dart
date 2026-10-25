import 'package:amplify_auth_cognito/amplify_auth_cognito.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pritimitra/core/config/app_config.dart';
import 'package:pritimitra/features/auth/data/amplify_auth_gateway.dart';
import 'package:pritimitra/features/auth/domain/auth_error.dart';

void main() {
  group('mapAmplifyAuthError', () {
    AuthErrorKind kindOf(Object error) => mapAmplifyAuthError(error).kind;

    test('credential and existence failures collapse to one generic kind', () {
      expect(
        kindOf(const NotAuthorizedServiceException('nope')),
        AuthErrorKind.invalidCredentials,
      );
      expect(
        kindOf(const UserNotFoundException('nope')),
        AuthErrorKind.invalidCredentials,
      );
      expect(
        mapAmplifyAuthError(const UserNotFoundException('nope')).userMessage,
        mapAmplifyAuthError(const NotAuthorizedServiceException('nope'))
            .userMessage,
      );
    });

    test('confirmation and code errors map to their own kinds', () {
      expect(
        kindOf(const UserNotConfirmedException('nope')),
        AuthErrorKind.userNotConfirmed,
      );
      expect(kindOf(const CodeMismatchException('nope')), AuthErrorKind.invalidCode);
      expect(kindOf(const ExpiredCodeException('nope')), AuthErrorKind.expiredCode);
    });

    test('rate limits and network failures are distinguishable', () {
      expect(
        kindOf(const LimitExceededException('nope')),
        AuthErrorKind.limitExceeded,
      );
      expect(kindOf(const NetworkException('nope')), AuthErrorKind.network);
    });

    test('unrecognised errors become unknown without leaking detail', () {
      final mapped = mapAmplifyAuthError(StateError('secret internals'));
      expect(mapped.kind, AuthErrorKind.unknown);
      expect(mapped.userMessage, isNot(contains('secret')));
    });

    test('existing AuthOperationException passes through unchanged', () {
      const original = AuthOperationException(AuthErrorKind.invalidCode);
      expect(identical(mapAmplifyAuthError(original), original), isTrue);
    });
  });

  group('buildAmplifyOutputs', () {
    test('builds the Gen2 auth block from dart-define values', () {
      const config = AppConfig(
        cognitoUserPoolId: 'eu-west-1_EXAMPLE',
        cognitoClientId: 'exampleclientid',
        cognitoRegion: 'eu-west-1',
        apiBaseUrl: 'https://api.example.test',
      );
      final outputs = buildAmplifyOutputs(config);
      final auth = outputs['auth'] as Map<String, dynamic>;

      expect(outputs['version'], '1');
      expect(auth['aws_region'], 'eu-west-1');
      expect(auth['user_pool_id'], 'eu-west-1_EXAMPLE');
      expect(auth['user_pool_client_id'], 'exampleclientid');
      expect(auth['username_attributes'], ['email']);
      expect(auth['unauthenticated_identities_enabled'], isFalse);
    });
  });
}
