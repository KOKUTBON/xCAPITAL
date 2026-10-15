import 'package:flutter/material.dart';

/// xCAPITAL light theme — light-first palette mirroring
/// web design tokens in frontend/web/src/styles/tokens.css.
class XCapitalColors {
  // Canvas and surfaces
  static const Color canvas = Color(0xFFF5F5F7);
  static const Color surface = Color(0xFFFFFFFF);
  static const Color surfaceSecondary = Color(0xFFF5F5F7);
  static const Color border = Color(0xFFE5E5EA);
  static const Color borderStrong = Color(0xFFD1D1D6);

  // Text
  static const Color text = Color(0xFF1D1D1F);
  static const Color textSecondary = Color(0xFF6E6E73);
  static const Color textTertiary = Color(0xFF86868B);

  // Interface colors
  static const Color appleBlue = Color(0xFF0071E3);
  static const Color appleDark = Color(0xFF1D1D1F);
  static const Color appleGreen = Color(0xFF34C759);
  static const Color appleRed = Color(0xFFFF3B30);
  static const Color applePurple = Color(0xFFAF52DE);
  static const Color appleGold = Color(0xFFFF9500);

  // Legacy token aliases mapped to current colors for backward compatibility
  static const Color obsidian = Color(0xFFF5F5F7);
  static const Color surfaceRaised = Color(0xFFFFFFFF);
  static const Color champagne = Color(0xFF1D1D1F);
  static const Color champagneDim = Color(0xFF6E6E73);
  static const Color crimson = Color(0xFF0071E3);
  static const Color crimsonBright = Color(0xFFFF3B30);
  static const Color royalPurple = Color(0xFFAF52DE);
  static const Color purpleBright = Color(0xFFBF5AF2);
  static const Color gold = Color(0xFFFF9500);
  static const Color goldBright = Color(0xFF34C759);
}

ThemeData xCapitalTheme() {
  const scheme = ColorScheme(
    brightness: Brightness.light,
    primary: XCapitalColors.appleBlue,
    onPrimary: Colors.white,
    secondary: XCapitalColors.appleDark,
    onSecondary: Colors.white,
    tertiary: XCapitalColors.applePurple,
    onTertiary: Colors.white,
    error: XCapitalColors.appleRed,
    onError: Colors.white,
    surface: XCapitalColors.surface,
    onSurface: XCapitalColors.text,
    surfaceContainerHighest: XCapitalColors.surfaceSecondary,
    onSurfaceVariant: XCapitalColors.textSecondary,
    outline: XCapitalColors.border,
  );

  final base = ThemeData(useMaterial3: true, colorScheme: scheme);
  return base.copyWith(
    scaffoldBackgroundColor: XCapitalColors.canvas,
    appBarTheme: const AppBarTheme(
      backgroundColor: XCapitalColors.surface,
      foregroundColor: XCapitalColors.text,
      elevation: 0,
      scrolledUnderElevation: 1,
    ),
    cardTheme: CardThemeData(
      color: XCapitalColors.surface,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: XCapitalColors.border),
      ),
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: XCapitalColors.appleDark,
        foregroundColor: Colors.white,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
        elevation: 0,
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: XCapitalColors.text,
        side: const BorderSide(color: XCapitalColors.border),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: XCapitalColors.surface,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: XCapitalColors.borderStrong),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: const BorderSide(color: XCapitalColors.appleBlue, width: 2),
      ),
      labelStyle: const TextStyle(color: XCapitalColors.textSecondary),
    ),
    dividerColor: XCapitalColors.border,
  );
}
