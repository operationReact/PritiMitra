import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_tokens.dart';
import '../../../shared/widgets/brand_header.dart';

/// Shared layout for authentication forms: approved brand header, heading, and a constrained body.
class AuthFormScaffold extends StatelessWidget {
  const AuthFormScaffold({
    required this.title,
    required this.children,
    this.subtitle,
    super.key,
  });

  final String title;
  final String? subtitle;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const SizedBox(height: AppSpacing.md),
                  const Align(
                    alignment: Alignment.centerLeft,
                    child: BrandHeader(),
                  ),
                  const SizedBox(height: AppSpacing.xl),
                  Text(title, style: theme.textTheme.headlineMedium),
                  if (subtitle != null) ...[
                    const SizedBox(height: AppSpacing.xs),
                    Text(subtitle!, style: theme.textTheme.bodyLarge),
                  ],
                  const SizedBox(height: AppSpacing.xl),
                  ...children,
                  const SizedBox(height: AppSpacing.xxl),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Inline status text. Errors use the error color; neutral notices use the muted color.
class AuthMessage extends StatelessWidget {
  const AuthMessage({required this.message, this.isError = true, super.key});

  final String message;
  final bool isError;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.md),
      child: Text(
        message,
        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
          color: isError ? AppColors.error : AppColors.muted,
        ),
      ),
    );
  }
}

/// Basic client-side check. The server remains the source of truth.
bool looksLikeEmail(String value) {
  return RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(value.trim());
}
