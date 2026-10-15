# Mobile (Android + iOS) — Flutter

Flutter client for xCAPITAL backend.

XC-001 baseline: validated with Flutter 3.41.5 / Dart 3.11.3. The application lockfile is tracked for reproducible dependency resolution. Tests use a local fake API server and do not qualify a live backend or device release.

Run:
```bash
cd mobile/xcapital_app
flutter pub get
flutter run --dart-define=API_BASE=http://10.0.2.2:8080
```

Route parity test:
```bash
cd mobile/xcapital_app
flutter test test/home_route_parity_test.dart
```

Mobile integration tests (deterministic fixtures, local fake API server):
```bash
cd mobile/xcapital_app
flutter test test/integration
```

Run all mobile tests with CI parity:
```bash
cd mobile/xcapital_app
flutter test test/home_route_parity_test.dart
flutter test test/integration
```

Manual enterprise smoke checklist:
- `docs/MOBILE_ENTERPRISE_SMOKE_CHECKLIST.md`
