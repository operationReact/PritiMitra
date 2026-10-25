import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/router/app_router.dart';
import '../../../core/theme/app_tokens.dart';
import '../application/auth_controller.dart';
import '../domain/auth_error.dart';
import 'auth_form_scaffold.dart';

class ConfirmSignUpScreen extends ConsumerStatefulWidget {
  const ConfirmSignUpScreen({this.initialEmail, super.key});

  final String? initialEmail;

  @override
  ConsumerState<ConfirmSignUpScreen> createState() =>
      _ConfirmSignUpScreenState();
}

class _ConfirmSignUpScreenState extends ConsumerState<ConfirmSignUpScreen> {
  final _formKey = GlobalKey<FormState>();
  late final _email = TextEditingController(text: widget.initialEmail ?? '');
  final _code = TextEditingController();
  bool _busy = false;
  String? _error;
  String? _notice;

  @override
  void dispose() {
    _email.dispose();
    _code.dispose();
    super.dispose();
  }

  Future<void> _confirm() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    await _run(() async {
      await ref
          .read(authGatewayProvider)
          .confirmSignUp(email: _email.text.trim(), code: _code.text.trim());
      if (mounted) {
        context.go(AppRoutes.signIn);
      }
    });
  }

  Future<void> _resend() async {
    if (!looksLikeEmail(_email.text)) {
      setState(() => _error = 'Enter your email first.');
      return;
    }
    await _run(() async {
      await ref
          .read(authGatewayProvider)
          .resendSignUpCode(email: _email.text.trim());
      if (mounted) setState(() => _notice = 'A new code is on its way.');
    });
  }

  Future<void> _run(Future<void> Function() action) async {
    setState(() {
      _busy = true;
      _error = null;
      _notice = null;
    });
    try {
      await action();
    } on AuthOperationException catch (error) {
      if (mounted) setState(() => _error = error.userMessage);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AuthFormScaffold(
      title: 'Confirm your email',
      subtitle: 'Enter the code we sent you.',
      children: [
        if (_notice != null)
          AuthMessage(message: _notice!, isError: false),
        if (_error != null) AuthMessage(message: _error!),
        Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TextFormField(
                controller: _email,
                keyboardType: TextInputType.emailAddress,
                decoration: const InputDecoration(labelText: 'Email'),
                validator: (value) => looksLikeEmail(value ?? '')
                    ? null
                    : 'Enter a valid email address.',
              ),
              const SizedBox(height: AppSpacing.md),
              TextFormField(
                controller: _code,
                keyboardType: TextInputType.number,
                autofillHints: const [AutofillHints.oneTimeCode],
                decoration: const InputDecoration(labelText: 'Code'),
                validator: (value) =>
                    (value ?? '').trim().isEmpty ? 'Enter the code.' : null,
              ),
            ],
          ),
        ),
        const SizedBox(height: AppSpacing.lg),
        FilledButton(
          onPressed: _busy ? null : _confirm,
          child: const Text('Confirm email'),
        ),
        TextButton(
          onPressed: _busy ? null : _resend,
          child: const Text('Resend code'),
        ),
      ],
    );
  }
}

class ForgotPasswordScreen extends ConsumerStatefulWidget {
  const ForgotPasswordScreen({super.key});

  @override
  ConsumerState<ForgotPasswordScreen> createState() =>
      _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends ConsumerState<ForgotPasswordScreen> {
  final _formKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    final email = _email.text.trim();
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(authGatewayProvider).resetPassword(email: email);
    } on AuthOperationException catch (error) {
      // Only connectivity and rate-limit problems block the next step. Other
      // errors do not reveal whether the account exists, so the user still
      // reaches the code entry screen.
      if (mounted &&
          (error.kind == AuthErrorKind.network ||
              error.kind == AuthErrorKind.limitExceeded)) {
        setState(() => _error = error.userMessage);
        return;
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
    if (mounted && _error == null) {
      context.go(AppRoutes.resetPassword, extra: email);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AuthFormScaffold(
      title: 'Forgot password',
      subtitle: 'We will email you a code to reset it.',
      children: [
        if (_error != null) AuthMessage(message: _error!),
        Form(
          key: _formKey,
          child: TextFormField(
            controller: _email,
            keyboardType: TextInputType.emailAddress,
            autofillHints: const [AutofillHints.email],
            decoration: const InputDecoration(labelText: 'Email'),
            validator: (value) =>
                looksLikeEmail(value ?? '') ? null : 'Enter a valid email address.',
          ),
        ),
        const SizedBox(height: AppSpacing.lg),
        FilledButton(
          onPressed: _busy ? null : _submit,
          child: const Text('Send code'),
        ),
        TextButton(
          onPressed: () => context.go(AppRoutes.signIn),
          child: const Text('Back to sign in'),
        ),
      ],
    );
  }
}

class ResetPasswordScreen extends ConsumerStatefulWidget {
  const ResetPasswordScreen({this.initialEmail, super.key});

  final String? initialEmail;

  @override
  ConsumerState<ResetPasswordScreen> createState() =>
      _ResetPasswordScreenState();
}

class _ResetPasswordScreenState extends ConsumerState<ResetPasswordScreen> {
  final _formKey = GlobalKey<FormState>();
  late final _email = TextEditingController(text: widget.initialEmail ?? '');
  final _code = TextEditingController();
  final _password = TextEditingController();
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    _code.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    final messenger = ScaffoldMessenger.of(context);
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(authGatewayProvider).confirmResetPassword(
        email: _email.text.trim(),
        code: _code.text.trim(),
        newPassword: _password.text,
      );
      if (mounted) {
        context.go(AppRoutes.signIn);
        messenger.showSnackBar(
          const SnackBar(
            content: Text('Your password is updated. Sign in to continue.'),
            behavior: SnackBarBehavior.floating,
          ),
        );
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
      title: 'Reset password',
      subtitle: 'Enter the code and choose a new password.',
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
                decoration: const InputDecoration(labelText: 'Email'),
                validator: (value) => looksLikeEmail(value ?? '')
                    ? null
                    : 'Enter a valid email address.',
              ),
              const SizedBox(height: AppSpacing.md),
              TextFormField(
                controller: _code,
                keyboardType: TextInputType.number,
                autofillHints: const [AutofillHints.oneTimeCode],
                decoration: const InputDecoration(labelText: 'Code'),
                validator: (value) =>
                    (value ?? '').trim().isEmpty ? 'Enter the code.' : null,
              ),
              const SizedBox(height: AppSpacing.md),
              TextFormField(
                controller: _password,
                obscureText: true,
                autofillHints: const [AutofillHints.newPassword],
                decoration: const InputDecoration(
                  labelText: 'New password',
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
          child: const Text('Update password'),
        ),
      ],
    );
  }
}
