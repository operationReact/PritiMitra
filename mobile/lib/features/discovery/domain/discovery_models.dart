/// One discoverable profile. Optional identity fields are null unless the owner chose to show them.
class DiscoveryCard {
  const DiscoveryCard({
    required this.publicId,
    required this.displayName,
    required this.bio,
    required this.profilePrompt,
    required this.genderIdentity,
    required this.pronouns,
    required this.sexualOrientation,
    required this.goals,
    required this.interests,
  });

  factory DiscoveryCard.fromJson(Map<String, dynamic> json) {
    return DiscoveryCard(
      publicId: json['publicId'] as String,
      displayName: json['displayName'] as String? ?? '',
      bio: json['bio'] as String?,
      profilePrompt: json['profilePrompt'] as String?,
      genderIdentity: json['genderIdentity'] as String?,
      pronouns: json['pronouns'] as String?,
      sexualOrientation: json['sexualOrientation'] as String?,
      goals: _strings(json['goals']),
      interests: _strings(json['interests']),
    );
  }

  final String publicId;
  final String displayName;
  final String? bio;
  final String? profilePrompt;
  final String? genderIdentity;
  final String? pronouns;
  final String? sexualOrientation;
  final List<String> goals;
  final List<String> interests;

  static List<String> _strings(Object? value) {
    if (value is List) {
      return value.whereType<String>().toList();
    }
    return const [];
  }
}

/// One page of discovery results. [nextCursor] is null when there are no more pages.
class DiscoveryPage {
  const DiscoveryPage({required this.items, required this.nextCursor});

  factory DiscoveryPage.fromJson(Map<String, dynamic> json) {
    final raw = json['items'];
    final items = raw is List
        ? raw
              .whereType<Map<String, dynamic>>()
              .map(DiscoveryCard.fromJson)
              .toList(growable: false)
        : const <DiscoveryCard>[];
    return DiscoveryPage(
      items: items,
      nextCursor: json['nextCursor'] as String?,
    );
  }

  final List<DiscoveryCard> items;
  final String? nextCursor;
}
