import 'package:flutter/material.dart';

import '../../../../shared/widgets/brand_header.dart';
import '../../../core/motion/app_motion.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_tokens.dart';

/// Responsive introduction using the PritiMitra landing visual language.
/// Real registration and login will be connected in the authentication phase.
class WelcomeScreen extends StatelessWidget {
  const WelcomeScreen({super.key});

  void _showComingSoon(BuildContext context) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        const SnackBar(
          content: Text('Account setup is the next implementation phase.'),
          behavior: SnackBarBehavior.floating,
        ),
      );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          physics: const BouncingScrollPhysics(),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 430),
              child: Padding(
                padding: const EdgeInsets.fromLTRB(24, 12, 24, 24),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Row(
                      children: [
                        const Expanded(
                          child: Align(
                            alignment: Alignment.centerLeft,
                            child: BrandHeader(),
                          ),
                        ),
                        const SizedBox(width: AppSpacing.sm),
                        SizedBox(
                          height: 40,
                          child: FilledButton(
                            style: FilledButton.styleFrom(
                              minimumSize: const Size(0, 40),
                              padding: const EdgeInsets.symmetric(
                                horizontal: 18,
                              ),
                              shape: const StadiumBorder(),
                              textStyle: const TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            onPressed: () => _showComingSoon(context),
                            child: const Text('Join free'),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 20),
                    AppMotion.entrance(
                      context: context,
                      duration: AppMotion.entranceDuration,
                      child: const _ConnectionIllustration(),
                    ),
                    const SizedBox(height: 22),
                    AppMotion.entrance(
                      context: context,
                      duration: AppMotion.slow,
                      verticalOffset: 8,
                      child: Column(
                        children: [
                          Text(
                            'A MORE HUMAN WAY TO MEET',
                            textAlign: TextAlign.center,
                            style: Theme.of(context).textTheme.labelSmall
                                ?.copyWith(
                                  color: AppColors.primary,
                                  letterSpacing: 1.1,
                                  fontSize: 11,
                                ),
                          ),
                          const SizedBox(height: 14),
                          Text(
                            'Your people.',
                            textAlign: TextAlign.center,
                            style: Theme.of(context).textTheme.headlineLarge,
                          ),
                          const SizedBox(height: 2),
                          Text(
                            'Your kind of connection.',
                            textAlign: TextAlign.center,
                            style: Theme.of(context).textTheme.headlineMedium
                                ?.copyWith(
                                  color: AppColors.primary,
                                  fontSize: 27,
                                  letterSpacing: -0.7,
                                ),
                          ),
                          const SizedBox(height: 18),
                          Text(
                            'Find dating, friendship and community at your own pace.',
                            textAlign: TextAlign.center,
                            style: Theme.of(context).textTheme.bodyLarge,
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 24),
                    AppMotion.entrance(
                      context: context,
                      duration: AppMotion.slow,
                      verticalOffset: 6,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          FilledButton(
                            onPressed: () => _showComingSoon(context),
                            child: const Text('Get started — it’s free'),
                          ),
                          const SizedBox(height: 4),
                          TextButton(
                            onPressed: () => _showComingSoon(context),
                            child: const Text(
                              'Already have an account? Log in',
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 12),
                    const _TrustCard(),
                    const SizedBox(height: 36),
                    Text(
                      '18+   ·   Inclusive by design   ·   Privacy by choice',
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.labelSmall?.copyWith(
                        color: AppColors.muted,
                        fontSize: 10,
                        fontWeight: FontWeight.w500,
                        letterSpacing: 0,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _ConnectionIllustration extends StatelessWidget {
  const _ConnectionIllustration();

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 220,
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: AppColors.lavender,
        borderRadius: BorderRadius.circular(AppRadii.xxl),
        boxShadow: [
          BoxShadow(
            color: AppColors.primary.withValues(alpha: 0.06),
            blurRadius: 28,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      child: LayoutBuilder(
        builder: (context, constraints) => Stack(
          alignment: Alignment.center,
          children: [
            Positioned(
              left: constraints.maxWidth * 0.15,
              top: 38,
              child: const _OrbitCircle(color: AppColors.primary),
            ),
            Positioned(
              right: constraints.maxWidth * 0.15,
              top: 38,
              child: const _OrbitCircle(color: AppColors.coral),
            ),
            AppMotion.entrance(
              context: context,
              duration: AppMotion.slow,
              verticalOffset: 10,
              child: const Text(
                '♥',
                style: TextStyle(
                  color: Color(0xFFB91329),
                  fontSize: 74,
                  height: 1.1,
                  shadows: [
                    Shadow(
                      color: Color(0x334A1220),
                      blurRadius: 5,
                      offset: Offset(0, 4),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _OrbitCircle extends StatelessWidget {
  const _OrbitCircle({required this.color});

  final Color color;

  @override
  Widget build(BuildContext context) => Container(
    width: 108,
    height: 108,
    decoration: BoxDecoration(
      shape: BoxShape.circle,
      color: color,
      gradient: LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [color, color.withValues(alpha: 0.88)],
      ),
      boxShadow: [
        BoxShadow(
          color: color.withValues(alpha: 0.14),
          blurRadius: 18,
          offset: const Offset(0, 8),
        ),
      ],
    ),
  );
}

class _TrustCard extends StatelessWidget {
  const _TrustCard();

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(AppSpacing.md),
    decoration: BoxDecoration(
      color: AppColors.surface,
      borderRadius: BorderRadius.circular(AppRadii.lg),
      border: Border.all(color: const Color(0x55E6E3EE)),
    ),
    child: const Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Connection first. Consent always.',
          style: TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w800,
            color: AppColors.navy,
          ),
        ),
        SizedBox(height: AppSpacing.xs),
        Text(
          'You control what you share, and when.',
          style: TextStyle(fontSize: 13, height: 1.35, color: AppColors.muted),
        ),
      ],
    ),
  );
}
