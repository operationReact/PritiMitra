import '../../../core/network/api_result.dart';
import '../../../core/network/pritimitra_api_client.dart';

class BlindDateBooking {
  const BlindDateBooking({required this.optInId, required this.startsAt, required this.timeZone,
    required this.status, required this.sessionId, required this.partnerProfilePublicId,
    required this.sessionStatus, required this.createdAt});
  final String optInId;
  final DateTime startsAt;
  final String timeZone;
  final String status;
  final String? sessionId;
  final String? partnerProfilePublicId;
  final String? sessionStatus;
  final DateTime createdAt;
  factory BlindDateBooking.fromJson(Map<String, dynamic> json) => BlindDateBooking(
    optInId: json['optInId'] as String, startsAt: DateTime.parse(json['startsAt'] as String),
    timeZone: json['timeZone'] as String, status: json['status'] as String,
    sessionId: json['sessionId'] as String?, partnerProfilePublicId: json['partnerProfilePublicId'] as String?,
    sessionStatus: json['sessionStatus'] as String?, createdAt: DateTime.parse(json['createdAt'] as String));
}

class BlindDateApi {
  BlindDateApi({required this.client});
  final PritiMitraApiClient? client;

  Future<ApiResult<List<BlindDateBooking>>> fetchMine() async {
    final api = client;
    if (api == null) return const ApiUnavailable();
    return _map(await api.getJson('/api/v1/blind-dates/my'), (json) =>
      (json['items'] as List<dynamic>)
        .map((item) => BlindDateBooking.fromJson(Map<String, dynamic>.from(item as Map))).toList(growable: false));
  }
  Future<ApiResult<Map<String, dynamic>>> optIn(DateTime istWallTime) async {
    final api = client;
    if (api == null) return const ApiUnavailable();
    final utc = DateTime.utc(istWallTime.year, istWallTime.month, istWallTime.day, istWallTime.hour, istWallTime.minute)
        .subtract(const Duration(hours: 5, minutes: 30));
    return api.postJson('/api/v1/blind-dates/opt-ins', body: {
      'startsAt': utc.toIso8601String(), 'timeZone': 'Asia/Kolkata',
    });
  }
  Future<ApiResult<void>> withdraw(String id) async {
    final api = client;
    if (api == null) return const ApiUnavailable();
    return api.deleteNoContent('/api/v1/blind-dates/opt-ins/$id');
  }
  Future<ApiResult<void>> cancelSession(String id) async {
    final api = client;
    if (api == null) return const ApiUnavailable();
    return api.deleteNoContent('/api/v1/blind-dates/sessions/$id');
  }
  ApiResult<T> _map<T>(ApiResult<Map<String, dynamic>> result, T Function(Map<String, dynamic>) parse) =>
    switch (result) {
      ApiSuccess(:final data) => _parse(data, parse),
      ApiUnauthorized(:final code) => ApiUnauthorized<T>(code: code),
      ApiForbidden(:final code) => ApiForbidden<T>(code: code),
      ApiUnavailable(:final statusCode, :final code) => ApiUnavailable<T>(statusCode: statusCode, code: code),
    };
  ApiResult<T> _parse<T>(Map<String, dynamic> json, T Function(Map<String, dynamic>) parse) {
    try { return ApiSuccess(parse(json)); } on Object { return const ApiUnavailable(); }
  }
}
