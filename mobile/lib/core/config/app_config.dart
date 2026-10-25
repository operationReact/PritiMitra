import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Build-time configuration supplied with `--dart-define` or `--dart-define-from-file`.
///
/// No real values belong in source control. See `config/dart_defines.example.json`.
class AppConfig {
  const AppConfig({
    this.cognitoUserPoolId = const String.fromEnvironment('COGNITO_USER_POOL_ID'),
    this.cognitoClientId = const String.fromEnvironment('COGNITO_CLIENT_ID'),
    this.cognitoRegion = const String.fromEnvironment('COGNITO_REGION'),
    this.apiBaseUrl = const String.fromEnvironment('API_BASE_URL'),
  });

  final String cognitoUserPoolId;
  final String cognitoClientId;
  final String cognitoRegion;
  final String apiBaseUrl;

  bool get isAuthConfigured =>
      cognitoUserPoolId.trim().isNotEmpty &&
      cognitoClientId.trim().isNotEmpty &&
      cognitoRegion.trim().isNotEmpty;

  /// Null when [apiBaseUrl] is missing or is not an absolute http(s) URL.
  Uri? get apiBaseUri {
    final uri = Uri.tryParse(apiBaseUrl.trim());
    if (uri == null || !uri.isAbsolute || uri.host.isEmpty) {
      return null;
    }
    if (uri.scheme != 'https' && uri.scheme != 'http') {
      return null;
    }
    return uri;
  }
}

final appConfigProvider = Provider<AppConfig>((ref) => const AppConfig());
