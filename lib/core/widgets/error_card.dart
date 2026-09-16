import 'package:flutter/material.dart';

import '../error/failures.dart';
import '../theme/context_x.dart';
import 'glass_card.dart';

/// Inline error card with a retry action and, when the failure requires
/// authentication, a "Sign in" CTA that opens the WebView login flow.
class ErrorCard extends StatelessWidget {
  const ErrorCard({
    super.key,
    required this.failure,
    required this.onRetry,
    this.onLogin,
  });

  final Failure failure;
  final VoidCallback onRetry;
  final VoidCallback? onLogin;

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    final needsAuth = failure is AuthRequiredFailure;
    return GlassCard(
      borderColor: colors.error.withOpacity(0.35),
      child: Row(
        children: [
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: colors.error.withOpacity(0.14),
            ),
            child: Icon(
              needsAuth ? Icons.lock_outline_rounded : Icons.error_outline_rounded,
              color: colors.error,
              size: 22,
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Download failed',
                  style: Theme.of(context)
                      .textTheme
                      .titleSmall!
                      .copyWith(color: colors.textPrimary),
                ),
                const SizedBox(height: 2),
                Text(
                  failure.message,
                  style: Theme.of(context)
                      .textTheme
                      .bodySmall!
                      .copyWith(color: colors.textSecondary),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          if (needsAuth && onLogin != null)
            IconButton(
              tooltip: 'Sign in',
              onPressed: onLogin,
              icon: Icon(Icons.login_rounded, color: colors.accentSecondary),
            )
          else
            IconButton(
              tooltip: 'Retry',
              onPressed: onRetry,
              icon: Icon(Icons.refresh_rounded, color: colors.accentSecondary),
            ),
        ],
      ),
    );
  }
}
