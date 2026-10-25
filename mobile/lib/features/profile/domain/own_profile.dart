/// The owner's own profile as returned by `GET /api/v1/profile`. Never contains date of birth,
/// email, identity keys or consent history.
class OwnProfile {
  const OwnProfile({
    required this.publicId,
    required this.displayName,
    required this.bio,
    required this.profilePrompt,
    required this.genderIdentity,
    required this.genderIdentityVisible,
    required this.pronouns,
    required this.pronounsVisible,
    required this.sexualOrientation,
    required this.sexualOrientationVisible,
    required this.goals,
    required this.interests,
    required this.discoveryInterestedIn,
    required this.discoveryMinAge,
    required this.discoveryMaxAge,
    required this.discoveryMaxDistanceKm,
    required this.discoverable,
  });

  factory OwnProfile.fromJson(Map<String, dynamic> json) {
    return OwnProfile(
      publicId: json['publicId'] as String,
      displayName: json['displayName'] as String? ?? '',
      bio: json['bio'] as String?,
      profilePrompt: json['profilePrompt'] as String?,
      genderIdentity: json['genderIdentity'] as String?,
      genderIdentityVisible: json['genderIdentityVisible'] as bool? ?? false,
      pronouns: json['pronouns'] as String?,
      pronounsVisible: json['pronounsVisible'] as bool? ?? false,
      sexualOrientation: json['sexualOrientation'] as String?,
      sexualOrientationVisible:
          json['sexualOrientationVisible'] as bool? ?? false,
      goals: _stringSet(json['goals']),
      interests: _stringSet(json['interests']),
      discoveryInterestedIn: _stringSet(json['discoveryInterestedIn']),
      discoveryMinAge: json['discoveryMinAge'] as int?,
      discoveryMaxAge: json['discoveryMaxAge'] as int?,
      discoveryMaxDistanceKm: json['discoveryMaxDistanceKm'] as int?,
      discoverable: json['discoverable'] as bool? ?? false,
    );
  }

  final String publicId;
  final String displayName;
  final String? bio;
  final String? profilePrompt;
  final String? genderIdentity;
  final bool genderIdentityVisible;
  final String? pronouns;
  final bool pronounsVisible;
  final String? sexualOrientation;
  final bool sexualOrientationVisible;
  final Set<String> goals;
  final Set<String> interests;
  final Set<String> discoveryInterestedIn;
  final int? discoveryMinAge;
  final int? discoveryMaxAge;
  final int? discoveryMaxDistanceKm;
  final bool discoverable;

  OwnProfile copyWith({
    String? displayName,
    String? bio,
    String? profilePrompt,
    Set<String>? goals,
    bool? genderIdentityVisible,
    bool? pronounsVisible,
    bool? sexualOrientationVisible,
    bool? discoverable,
  }) {
    return OwnProfile(
      publicId: publicId,
      displayName: displayName ?? this.displayName,
      bio: bio ?? this.bio,
      profilePrompt: profilePrompt ?? this.profilePrompt,
      genderIdentity: genderIdentity,
      genderIdentityVisible:
          genderIdentityVisible ?? this.genderIdentityVisible,
      pronouns: pronouns,
      pronounsVisible: pronounsVisible ?? this.pronounsVisible,
      sexualOrientation: sexualOrientation,
      sexualOrientationVisible:
          sexualOrientationVisible ?? this.sexualOrientationVisible,
      goals: goals ?? this.goals,
      interests: interests,
      discoveryInterestedIn: discoveryInterestedIn,
      discoveryMinAge: discoveryMinAge,
      discoveryMaxAge: discoveryMaxAge,
      discoveryMaxDistanceKm: discoveryMaxDistanceKm,
      discoverable: discoverable ?? this.discoverable,
    );
  }

  /// Full replacement body for `PUT /api/v1/profile`. Every editable field is sent so nothing is wiped.
  /// Read-only fields (publicId, discoverable) are omitted; discoverability has its own endpoint.
  Map<String, dynamic> toUpdateJson() {
    return {
      'displayName': displayName,
      'bio': bio,
      'profilePrompt': profilePrompt,
      'genderIdentity': genderIdentity,
      'genderIdentityVisible': genderIdentityVisible,
      'pronouns': pronouns,
      'pronounsVisible': pronounsVisible,
      'sexualOrientation': sexualOrientation,
      'sexualOrientationVisible': sexualOrientationVisible,
      'goals': goals.toList(),
      'interests': interests.toList(),
      'discoveryInterestedIn': discoveryInterestedIn.toList(),
      'discoveryMinAge': discoveryMinAge,
      'discoveryMaxAge': discoveryMaxAge,
      'discoveryMaxDistanceKm': discoveryMaxDistanceKm,
    };
  }

  static Set<String> _stringSet(Object? value) {
    if (value is List) {
      return value.whereType<String>().toSet();
    }
    return <String>{};
  }
}
