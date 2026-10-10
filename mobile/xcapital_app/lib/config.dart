class Config {
  static const String _defaultApiBase = String.fromEnvironment(
    'API_BASE',
    defaultValue: 'http://10.0.2.2:8080',
  );

  static String? _apiBaseOverride;

  static String get apiBase => _apiBaseOverride ?? _defaultApiBase;

  static void overrideApiBaseForTests(String? value) {
    _apiBaseOverride = value;
  }
}
