import 'package:flutter/material.dart';

import '../../../../shared/widgets/brand_header.dart';

/// Foundation placeholder for the Figma "Auth / Welcome" screen.
/// Full content lands in the onboarding phase.
class WelcomeScreen extends StatelessWidget {
  const WelcomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            const BrandHeader(),
            Expanded(
              child: Center(
                child: Text(
                  'Welcome',
                  style: Theme.of(context).textTheme.headlineMedium,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
