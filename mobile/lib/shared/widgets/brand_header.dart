import 'package:flutter/material.dart';

/// Approved PritiMitra lockup: image logo and light-background wordmark.
/// Sizes match the Figma header instance (icon 46x34, wordmark 97x28).
class BrandHeader extends StatelessWidget {
  const BrandHeader({super.key});

  static const iconSize = Size(46, 34);
  static const wordmarkSize = Size(97, 28);
  static const gap = 10.0;
  static const height = 64.0;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: height,
      child: Center(
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Image.asset(
              'assets/brand/image_logo.png',
              width: iconSize.width,
              height: iconSize.height,
              fit: BoxFit.contain,
              semanticLabel: 'PritiMitra logo',
            ),
            const SizedBox(width: gap),
            Image.asset(
              'assets/brand/wordmark.png',
              width: wordmarkSize.width,
              height: wordmarkSize.height,
              fit: BoxFit.contain,
              semanticLabel: 'PritiMitra',
            ),
          ],
        ),
      ),
    );
  }
}
