/// Connection goals as the backend enum wire values.
enum ConnectionGoal {
  dating('DATING', 'Dating', 'Romantic connections'),
  friendship('FRIENDSHIP', 'Friendship', 'New friends and chosen family'),
  community('COMMUNITY', 'Community', 'Shared experiences and support'),
  meetPeople('MEET_PEOPLE', 'Meeting people', 'Let the conversation decide');

  const ConnectionGoal(this.wireValue, this.title, this.subtitle);

  final String wireValue;
  final String title;
  final String subtitle;

  static ConnectionGoal? fromWire(String value) {
    for (final goal in values) {
      if (goal.wireValue == value) {
        return goal;
      }
    }
    return null;
  }
}

/// The editable profile fields the onboarding screens own.
class ProfileDraft {
  const ProfileDraft({
    this.displayName,
    this.bio,
    this.profilePrompt,
    this.goals = const {},
  });

  final String? displayName;
  final String? bio;
  final String? profilePrompt;
  final Set<ConnectionGoal> goals;

  ProfileDraft copyWith({
    String? displayName,
    String? bio,
    String? profilePrompt,
    Set<ConnectionGoal>? goals,
  }) {
    return ProfileDraft(
      displayName: displayName ?? this.displayName,
      bio: bio ?? this.bio,
      profilePrompt: profilePrompt ?? this.profilePrompt,
      goals: goals ?? this.goals,
    );
  }

  /// Returns null when the response shape is invalid.
  static ProfileDraft? fromJson(Map<String, dynamic> json) {
    final rawGoals = json['goals'];
    if (rawGoals is! List) {
      return null;
    }
    final goals = <ConnectionGoal>{};
    for (final value in rawGoals) {
      if (value is String) {
        final goal = ConnectionGoal.fromWire(value);
        if (goal != null) {
          goals.add(goal);
        }
      }
    }
    return ProfileDraft(
      displayName: json['displayName'] as String?,
      bio: json['bio'] as String?,
      profilePrompt: json['profilePrompt'] as String?,
      goals: goals,
    );
  }

  /// Visibility flags are always false here. Profiles stay private until the user opts in.
  Map<String, dynamic> toJson() {
    return {
      'displayName': displayName,
      'bio': bio,
      'profilePrompt': profilePrompt,
      'goals': goals.map((goal) => goal.wireValue).toList(growable: false),
      'genderIdentityVisible': false,
      'pronounsVisible': false,
      'sexualOrientationVisible': false,
    };
  }
}
