import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/auth/application/auth_controller.dart';
import '../../features/auth/domain/auth_state.dart';
import '../../features/auth/presentation/account_status_screen.dart';
import '../../features/auth/presentation/confirm_sign_up_screen.dart';
import '../../features/auth/presentation/ineligible_screen.dart';
import '../../features/auth/presentation/sign_in_screen.dart';
import '../../features/auth/presentation/sign_up_screen.dart';
import '../../features/discovery/presentation/discovery_screen.dart';
import '../../features/home/presentation/home_screen.dart';
import '../../features/onboarding/presentation/onboarding_screen.dart';
import '../../features/profile/presentation/profile_screen.dart';
import '../../features/welcome/presentation/welcome_screen.dart';
import '../motion/app_motion.dart';

abstract final class AppRoutes {
  static const welcome = '/welcome';
  static const signIn = '/sign-in';
  static const signUp = '/sign-up';
  static const confirmSignUp = '/confirm-sign-up';
  static const forgotPassword = '/forgot-password';
  static const resetPassword = '/reset-password';
  static const accountStatus = '/account-status';
  static const onboarding = '/onboarding';
  static const ineligible = '/ineligible';
  static const home = '/home';
  static const discover = '/discover';
  static const profile = '/profile';

  /// Protected pages that only ACTIVE accounts may open.
  static const activePaths = {home, discover, profile};

  static const publicPaths = {
    welcome,
    signIn,
    signUp,
    confirmSignUp,
    forgotPassword,
    resetPassword,
  };
}

/// Pure redirect rule so routing can be unit-tested without a widget tree.
///
/// Returns null when [location] is allowed for [state]. Otherwise returns the
/// path the user should be sent to.
String? redirectForAccess({
  required String location,
  required AuthState state,
}) {
  final path = Uri.parse(location).path;
  return switch (decideAccess(state)) {
    AccessDecision.loading =>
      AppRoutes.publicPaths.contains(path) ? null : AppRoutes.welcome,
    AccessDecision.signIn =>
      AppRoutes.publicPaths.contains(path) ? null : AppRoutes.signIn,
    AccessDecision.accountStatus => switch (path) {
      AppRoutes.accountStatus || AppRoutes.confirmSignUp => null,
      _ => AppRoutes.accountStatus,
    },
    AccessDecision.onboarding =>
      path == AppRoutes.onboarding ? null : AppRoutes.onboarding,
    AccessDecision.ineligible =>
      path == AppRoutes.ineligible ? null : AppRoutes.ineligible,
    AccessDecision.home =>
      AppRoutes.activePaths.contains(path) ? null : AppRoutes.home,
  };
}

/// Lets GoRouter re-run redirects whenever the auth state changes.
final _authRefreshProvider = Provider<ValueNotifier<int>>((ref) {
  final notifier = ValueNotifier<int>(0);
  ref.listen(authControllerProvider, (_, _) => notifier.value++);
  ref.onDispose(notifier.dispose);
  return notifier;
});

final appRouterProvider = Provider<GoRouter>((ref) {
  final router = GoRouter(
    initialLocation: AppRoutes.welcome,
    refreshListenable: ref.watch(_authRefreshProvider),
    redirect: (context, state) => redirectForAccess(
      location: state.uri.toString(),
      state: ref.read(authControllerProvider),
    ),
    routes: [
      _page(AppRoutes.welcome, const WelcomeScreen()),
      _page(AppRoutes.signIn, const SignInScreen()),
      _page(AppRoutes.signUp, const SignUpScreen()),
      GoRoute(
        path: AppRoutes.confirmSignUp,
        pageBuilder: (context, state) => AppMotion.routePage(
          key: state.pageKey,
          child: ConfirmSignUpScreen(initialEmail: state.extra as String?),
        ),
      ),
      _page(AppRoutes.forgotPassword, const ForgotPasswordScreen()),
      GoRoute(
        path: AppRoutes.resetPassword,
        pageBuilder: (context, state) => AppMotion.routePage(
          key: state.pageKey,
          child: ResetPasswordScreen(initialEmail: state.extra as String?),
        ),
      ),
      _page(AppRoutes.accountStatus, const AccountStatusScreen()),
      _page(AppRoutes.onboarding, const OnboardingScreen()),
      _page(AppRoutes.ineligible, const IneligibleScreen()),
      _page(AppRoutes.home, const HomeScreen()),
      _page(AppRoutes.discover, const DiscoveryScreen()),
      _page(AppRoutes.profile, const ProfileScreen()),
    ],
  );
  ref.onDispose(router.dispose);
  return router;
});

GoRoute _page(String path, Widget child) {
  return GoRoute(
    path: path,
    pageBuilder: (context, state) =>
        AppMotion.routePage(key: state.pageKey, child: child),
  );
}
