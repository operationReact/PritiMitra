import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

/// Shared motion system. Every screen uses these tokens and transition
/// builders rather than defining its own animation values.
abstract final class AppMotion {
  static const fast = Duration(milliseconds: 150);
  static const standard = Duration(milliseconds: 260);
  static const slow = Duration(milliseconds: 420);

  static const enter = Curves.easeOutCubic;
  static const exit = Curves.easeInCubic;
  static const emphasized = Cubic(0.2, 0, 0, 1);

  /// Returns [duration], or zero when the platform asks for reduced motion.
  static Duration resolve(BuildContext context, Duration duration) {
    final disabled = MediaQuery.maybeDisableAnimationsOf(context) ?? false;
    return disabled ? Duration.zero : duration;
  }

  /// Fade and slight rise used for route transitions across the app.
  static CustomTransitionPage<T> routePage<T>({
    required LocalKey key,
    required Widget child,
  }) {
    return CustomTransitionPage<T>(
      key: key,
      child: child,
      transitionDuration: standard,
      reverseTransitionDuration: fast,
      transitionsBuilder: (context, animation, secondary, child) {
        if (MediaQuery.maybeDisableAnimationsOf(context) ?? false) {
          return child;
        }
        final curved = CurvedAnimation(parent: animation, curve: emphasized);
        return FadeTransition(
          opacity: curved,
          child: SlideTransition(
            position: Tween<Offset>(
              begin: const Offset(0, 0.04),
              end: Offset.zero,
            ).animate(curved),
            child: child,
          ),
        );
      },
    );
  }
}
