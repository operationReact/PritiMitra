import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/router/app_router.dart';
import '../../../core/theme/app_tokens.dart';
import '../application/auth_controller.dart';
import '../domain/auth_error.dart';
import '../domain/auth_state.dart';
import 'auth_form_scaffold.dart';

class SignInScreen extends ConsumerStatefulWidget {
  const SignInScreen({super.key});

  @override
  ConsumerState<SignInScreen> createState() => _SignInScreenState();
}

class _SignInScreenState extends ConsumerState<SignInScreen> {
  final _formKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    final email = _email.text.trim();
    try {
      await ref
          .read(authControllerProvider.notifier)
          .signIn(email: email, password: _password.text);
    } on AuthOperationException catch (error) {
      if (!mounted) return;
      if (error.kind == AuthErrorKind.userNotConfirmed) {
        await _sendCodeAndConfirm(email);
        return;
      }
      setState(() => _error = error.userMessage);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _sendCodeAndConfirm(String email) async {
    try {
      await ref.read(authGatewayProvider).resendSignUpCode(email: email);
    } on AuthOperationException {
      // The confirm screen offers another resend, so a failure here is not fatal.
    }
    if (mounted) {
      context.go(AppRoutes.confirmSignUp, extra: email);
    }
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authControllerProvider);
    final sessionExpired =
        authState is AuthSignedOut && authState.sessionExpired;
    return AuthFormScaffold(
      title: 'Welcome back',
      subtitle: 'Sign in to continue.',
      children: [
        if (sessionExpired)
          const AuthMessage(
            message: 'Your session has ended. Please sign in again.',
            isError: false,
          ),
        if (_error != null) AuthMessage(message: _error!),
        Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TextFormField(
                controller: _email,
                keyboardType: TextInputType.emailAddress,
                autofillHints: const [AutofillHints.email],
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(labelText: 'Email'),
                validator: (value) => looksLikeEmail(value ?? '')
                    ? null
                    : 'Enter a valid email address.',
              ),
              const SizedBox(height: AppSpacing.md),
              TextFormField(
                controller: _password,
                obscureText: true,
                autofillHints: const [AutofillHints.password],
                textInputAction: TextInputAction.done,
                onFieldSubmitted: (_) => _submit(),
                decoration: const InputDecoration(labelText: 'Password'),
                validator: (value) =>
                    (value ?? '').isEmpty ? 'Enter your password.' : null,
              ),
            ],
          ),
        ),
        const SizedBox(height: AppSpacing.lg),
        FilledButton(
          onPressed: _busy ? null : _submit,
          child: Text(_busy ? 'Signing in…' : 'Sign in'),
        ),
        const SizedBox(height: AppSpacing.xs),
        TextButton(
          onPressed: () => context.push(AppRoutes.forgotPassword),
          child: const Text('Forgot password?'),
        ),
        TextButton(
          onPressed: () => context.push(AppRoutes.signUp),
          child: const Text('New here? Create an account'),
        ),
      ],
    );
  }
}
