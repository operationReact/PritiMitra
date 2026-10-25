import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/welcome/presentation/welcome_screen.dart';
import '../motion/app_motion.dart';

abstract final class AppRoutes {
  static const welcome = '/welcome';
}

final appRouterProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: AppRoutes.welcome,
    routes: [
      GoRoute(
        path: AppRoutes.welcome,
        pageBuilder: (context, state) => AppMotion.routePage(
          key: state.pageKey,
          child: const WelcomeScreen(),
        ),
      ),
    ],
  );
});
