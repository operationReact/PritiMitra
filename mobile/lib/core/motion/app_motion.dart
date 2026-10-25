import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

/// Shared motion tokens. Prefer these to per-screen animation values.
abstract final class AppMotion {
  static const instant = Duration.zero;
  static const fast = Duration(milliseconds: 150);
  static const standard = Duration(milliseconds: 260);
  static const slow = Duration(milliseconds: 420);
  static const entranceDuration = Duration(milliseconds: 560);

  static const enter = Curves.easeOutCubic;
  static const exit = Curves.easeInCubic;
  static const emphasized = Cubic(0.2, 0, 0, 1);

  static bool reduceMotion(BuildContext context) =>
      MediaQuery.maybeDisableAnimationsOf(context) ?? false;

  static Duration resolve(BuildContext context, Duration duration) =>
      reduceMotion(context) ? instant : duration;

  /// Fade-and-rise entrance for hero content and feature sections.
  static Widget entrance({
    required BuildContext context,
    required Widget child,
    Duration duration = entranceDuration,
    double verticalOffset = 14,
  }) {
    if (reduceMotion(context)) return child;
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(begin: 0, end: 1),
      duration: duration,
      curve: enter,
      child: child,
      builder: (context, value, child) => Opacity(
        opacity: value.clamp(0.0, 1.0).toDouble(),
        child: Transform.translate(
          offset: Offset(0, (1 - value) * verticalOffset),
          child: child,
        ),
      ),
    );
  }

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
        if (reduceMotion(context)) return child;
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
