import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/router/app_router.dart';
import '../../../core/theme/app_tokens.dart';
import '../application/auth_controller.dart';
import '../domain/auth_error.dart';
import 'auth_form_scaffold.dart';

class SignUpScreen extends ConsumerStatefulWidget {
  const SignUpScreen({super.key});

  @override
  ConsumerState<SignUpScreen> createState() => _SignUpScreenState();
}

class _SignUpScreenState extends ConsumerState<SignUpScreen> {
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
          .read(authGatewayProvider)
          .signUp(email: email, password: _password.text);
      if (mounted) {
        context.go(AppRoutes.confirmSignUp, extra: email);
      }
    } on AuthOperationException catch (error) {
      if (mounted) setState(() => _error = error.userMessage);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AuthFormScaffold(
      title: 'Create your account',
      subtitle: 'We will email you a code to confirm it is you.',
      children: [
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
                autofillHints: const [AutofillHints.newPassword],
                textInputAction: TextInputAction.done,
                onFieldSubmitted: (_) => _submit(),
                decoration: const InputDecoration(
                  labelText: 'Password',
                  helperText: 'At least 8 characters.',
                ),
                validator: (value) => (value ?? '').length < 8
                    ? 'Use at least 8 characters.'
                    : null,
              ),
            ],
          ),
        ),
        const SizedBox(height: AppSpacing.lg),
        FilledButton(
          onPressed: _busy ? null : _submit,
          child: Text(_busy ? 'Creating account…' : 'Create account'),
        ),
        TextButton(
          onPressed: () => context.go(AppRoutes.signIn),
          child: const Text('Already have an account? Sign in'),
        ),
      ],
    );
  }
}
