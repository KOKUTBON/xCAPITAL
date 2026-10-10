import 'package:shared_preferences/shared_preferences.dart';

class TokenStore {
  static const _k = 'token';

  static Future<void> setToken(String token) async {
    final p = await SharedPreferences.getInstance();
    await p.setString(_k, token);
  }

  static Future<String?> getToken() async {
    final p = await SharedPreferences.getInstance();
    return p.getString(_k);
  }
}
