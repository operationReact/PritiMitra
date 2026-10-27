import '../../../core/network/api_result.dart';
import '../../../core/network/pritimitra_api_client.dart';

class CommunityGroup {
  const CommunityGroup({required this.publicId, required this.slug, required this.name,
    required this.description, required this.joined, required this.memberCount});
  final String publicId, slug, name, description;
  final bool joined;
  final int memberCount;
  factory CommunityGroup.fromJson(Map<String, dynamic> json) => CommunityGroup(
    publicId: json['publicId'] as String,
    slug: json['slug'] as String,
    name: json['name'] as String,
    description: json['description'] as String,
    joined: json['joined'] as bool,
    memberCount: (json['memberCount'] as num).toInt());
}
class CommunityPost {
  const CommunityPost({required this.postPublicId, required this.groupPublicId, required this.displayName,
    required this.body, required this.likeCount, required this.heartCount, required this.supportCount,
    required this.myReaction, required this.createdAt, required this.mine});
  final String postPublicId, groupPublicId, displayName, body;
  final int likeCount, heartCount, supportCount;
  final String? myReaction;
  final DateTime createdAt;
  final bool mine;
  factory CommunityPost.fromJson(Map<String, dynamic> json) => CommunityPost(
    postPublicId: json['postPublicId'] as String,
    groupPublicId: json['groupPublicId'] as String,
    displayName: json['displayName'] as String,
    body: json['body'] as String,
    likeCount: (json['likeCount'] as num).toInt(),
    heartCount: (json['heartCount'] as num).toInt(),
    supportCount: (json['supportCount'] as num).toInt(),
    myReaction: json['myReaction'] as String?,
    createdAt: DateTime.parse(json['createdAt'] as String),
    mine: json['mine'] as bool);
}
class CommunityPostPage {
  const CommunityPostPage({required this.items, required this.nextCursor});
  final List<CommunityPost> items;
  final String? nextCursor;
  factory CommunityPostPage.fromJson(Map<String, dynamic> json) => CommunityPostPage(
    items: (json['items'] as List<dynamic>)
      .map((item) => CommunityPost.fromJson(Map<String, dynamic>.from(item as Map))).toList(growable: false),
    nextCursor: json['nextCursor'] as String?);
}
class CommunityApi {
  CommunityApi({required this.client});
  final PritiMitraApiClient? client;

  Future<ApiResult<List<CommunityGroup>>> fetchGroups() async {
    final api = client;
    if (api == null) return const ApiUnavailable();
    return _map(await api.getJson('/api/v1/community/groups'), (json) =>
      (json['items'] as List<dynamic>)
        .map((item) => CommunityGroup.fromJson(Map<String, dynamic>.from(item as Map))).toList(growable: false));
  }
  Future<ApiResult<CommunityPostPage>> fetchPosts(String groupId, {String? cursor, int size = 20}) async {
    final api = client;
    if (api == null) return const ApiUnavailable();
    final query = <String, String>{'size': '$size'};
    if (cursor != null) query['cursor'] = cursor;
    return _map(await api.getJson('/api/v1/community/groups/$groupId/posts?' + Uri(queryParameters: query).query),
      CommunityPostPage.fromJson);
  }
  Future<ApiResult<void>> join(String groupId) async {
    final api = client;
    if (api == null) return const ApiUnavailable();
    return _map<void>(await api.putJson('/api/v1/community/groups/$groupId/membership', {}), (_) {});
  }
  Future<ApiResult<void>> leave(String groupId) async {
    final api = client;
    if (api == null) return const ApiUnavailable();
    return api.deleteNoContent('/api/v1/community/groups/$groupId/membership');
  }
  Future<ApiResult<Map<String, dynamic>>> createPost(String groupId, String body) async {
    final api = client;
    if (api == null) return const ApiUnavailable();
    return api.postJson('/api/v1/community/groups/$groupId/posts', body: {'body': body});
  }
  Future<ApiResult<Map<String, dynamic>>> react(String postId, String reaction) async {
    final api = client;
    if (api == null) return const ApiUnavailable();
    return api.putJson('/api/v1/community/posts/$postId/reaction', {'reaction': reaction});
  }
  Future<ApiResult<void>> removeReaction(String postId) async {
    final api = client;
    if (api == null) return const ApiUnavailable();
    return api.deleteNoContent('/api/v1/community/posts/$postId/reaction');
  }
  Future<ApiResult<void>> removePost(String postId) async {
    final api = client;
    if (api == null) return const ApiUnavailable();
    return api.deleteNoContent('/api/v1/community/posts/$postId');
  }
  Future<ApiResult<Map<String, dynamic>>> reportPost(String postId) async {
    final api = client;
    if (api == null) return const ApiUnavailable();
    return api.postJson('/api/v1/reports', body: {
      'targetType': 'COMMUNITY_POST', 'targetId': postId, 'category': 'UNWANTED_CONTENT',
    });
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
