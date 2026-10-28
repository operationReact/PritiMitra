enum DesignExperience {
  spark,
  essence;

  String get storageValue => name;

  static DesignExperience fromStorage(String? value) {
    return DesignExperience.values.firstWhere(
      (experience) => experience.storageValue == value,
      orElse: () => DesignExperience.spark,
    );
  }
}

abstract final class DesignExperiencePreference {
  static const storageKey = 'pritimitra.design-experience.v1';
  static const defaultExperience = DesignExperience.spark;
}
