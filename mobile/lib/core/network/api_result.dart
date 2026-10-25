/// Typed outcome of a PritiMitra API call. Callers pattern-match instead of
/// inspecting raw HTTP status codes.
sealed class ApiResult<T> {
  const ApiResult();
}

final class ApiSuccess<T> extends ApiResult<T> {
  const ApiSuccess(this.data);

  final T data;
}

/// 401 after one forced token refresh, or no usable session at all.
final class ApiUnauthorized<T> extends ApiResult<T> {
  const ApiUnauthorized({this.code});

  /// Stable machine-readable code from the problem body, when present.
  final String? code;
}

/// 403 with a business code such as `AGE_INELIGIBLE`, `EMAIL_NOT_VERIFIED` or `ACCOUNT_SUSPENDED`.
final class ApiForbidden<T> extends ApiResult<T> {
  const ApiForbidden({this.code});

  final String? code;
}

/// Network failure, timeout, 5xx, or a response the client cannot interpret.
final class ApiUnavailable<T> extends ApiResult<T> {
  const ApiUnavailable({this.statusCode});

  final int? statusCode;
}
