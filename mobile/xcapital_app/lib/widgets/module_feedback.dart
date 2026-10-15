import 'package:flutter/material.dart';

class ModuleFeedback extends StatelessWidget {
  const ModuleFeedback({super.key, required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    final text = message.trim();
    if (text.isEmpty) return const SizedBox.shrink();

    final spec = _specFor(text);
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(top: 8),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: spec.background,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: spec.border),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          if (spec.loading)
            SizedBox(
              width: 16,
              height: 16,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                valueColor: AlwaysStoppedAnimation<Color>(spec.accent),
              ),
            )
          else
            Icon(spec.icon, size: 16, color: spec.accent),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              text,
              style: TextStyle(fontSize: 12, color: spec.textColor),
            ),
          ),
        ],
      ),
    );
  }

  _FeedbackSpec _specFor(String text) {
    final lower = text.toLowerCase();

    if (_containsAny(lower, const [
      'loading',
      'refreshing',
      'running',
      'submitting',
      'creating',
      'saving',
      'updating',
      'triggering',
      'importing',
      'generating',
      'enrolling',
      'verifying',
      'registering',
    ])) {
      return const _FeedbackSpec.loading();
    }

    if (_containsAny(lower, const [
      'invalid',
      'failed',
      'unavailable',
      'error',
      'exception',
      'must be',
      'missing',
      'select',
    ])) {
      return const _FeedbackSpec.error();
    }

    if (_containsAny(lower, const [
      'created',
      'saved',
      'updated',
      'verified',
      'loaded',
      'triggered',
      'generated',
      'enrollment',
      'linked',
      'added',
      'removed',
      'deleted',
      'executed',
      'ok',
      'done',
      'refreshed',
    ])) {
      return const _FeedbackSpec.success();
    }

    return const _FeedbackSpec.info();
  }

  bool _containsAny(String text, List<String> patterns) {
    for (final pattern in patterns) {
      if (text.contains(pattern)) {
        return true;
      }
    }
    return false;
  }
}

class _FeedbackSpec {
  const _FeedbackSpec({
    required this.icon,
    required this.accent,
    required this.background,
    required this.border,
    required this.textColor,
    this.loading = false,
  });

  final IconData icon;
  final Color accent;
  final Color background;
  final Color border;
  final Color textColor;
  final bool loading;

  const _FeedbackSpec.info()
      : this(
          icon: Icons.info_outline,
          accent: const Color(0xFF334155),
          background: const Color(0xFFF8FAFC),
          border: const Color(0xFFCBD5E1),
          textColor: const Color(0xFF334155),
        );

  const _FeedbackSpec.success()
      : this(
          icon: Icons.check_circle_outline,
          accent: const Color(0xFF166534),
          background: const Color(0xFFF0FDF4),
          border: const Color(0xFF86EFAC),
          textColor: const Color(0xFF166534),
        );

  const _FeedbackSpec.error()
      : this(
          icon: Icons.error_outline,
          accent: const Color(0xFFB91C1C),
          background: const Color(0xFFFEF2F2),
          border: const Color(0xFFFCA5A5),
          textColor: const Color(0xFF991B1B),
        );

  const _FeedbackSpec.loading()
      : this(
          icon: Icons.sync,
          accent: const Color(0xFF1D4ED8),
          background: const Color(0xFFEFF6FF),
          border: const Color(0xFF93C5FD),
          textColor: const Color(0xFF1E3A8A),
          loading: true,
        );
}
