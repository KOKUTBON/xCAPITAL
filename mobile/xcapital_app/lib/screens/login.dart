import 'package:flutter/material.dart';
import '../widgets/module_feedback.dart';
import '../services/api.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});
  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _credentialsFormKey = GlobalKey<FormState>();
  final _mfaFormKey = GlobalKey<FormState>();
  final email = TextEditingController();
  final password = TextEditingController();
  final mfaCode = TextEditingController();
  String msg = '';
  bool busy = false;
  bool mfaRequired = false;
  String? mfaToken;
  String? mfaTokenExpiresAt;

  @override
  void dispose() {
    email.dispose();
    password.dispose();
    mfaCode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('xCAPITAL — Login')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: SingleChildScrollView(
          child: Column(
            children: [
              Image.asset('assets/xcapital-logo.png', width: 72, height: 72),
              const SizedBox(height: 12),
              Form(
                key: _credentialsFormKey,
                child: Column(
                  children: [
                    TextFormField(
                      controller: email,
                      keyboardType: TextInputType.emailAddress,
                      autofillHints: const [AutofillHints.username],
                      decoration: const InputDecoration(labelText: 'Email'),
                      validator: (value) {
                        final input = (value ?? '').trim();
                        if (input.isEmpty) return 'Email is required.';
                        if (!RegExp(r'^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$').hasMatch(input)) {
                          return 'Enter a valid email address.';
                        }
                        return null;
                      },
                    ),
                    TextFormField(
                      controller: password,
                      obscureText: true,
                      autofillHints: const [AutofillHints.password],
                      decoration: const InputDecoration(labelText: 'Password'),
                      validator: (value) {
                        final input = (value ?? '').trim();
                        if (input.isEmpty) return 'Password is required.';
                        if (input.length < 8) return 'Password must be at least 8 characters.';
                        return null;
                      },
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  ElevatedButton(
                    onPressed: busy ? null : () async {
                      if (!_credentialsFormKey.currentState!.validate()) return;
                      setState(() {
                        busy = true;
                        msg = 'Registering...';
                        mfaRequired = false;
                        mfaToken = null;
                        mfaTokenExpiresAt = null;
                      });
                      try {
                        await Api.register(email.text.trim(), password.text);
                        setState(() => msg = 'Registered.');
                        if (mounted) Navigator.pop(context);
                      } catch (e) {
                        setState(() => msg = e.toString());
                      } finally {
                        if (mounted) setState(() => busy = false);
                      }
                    },
                    child: const Text('Register'),
                  ),
                  const SizedBox(width: 12),
                  OutlinedButton(
                    onPressed: busy ? null : () async {
                      if (!_credentialsFormKey.currentState!.validate()) return;
                      setState(() {
                        busy = true;
                        msg = 'Logging in...';
                        mfaRequired = false;
                        mfaToken = null;
                        mfaTokenExpiresAt = null;
                      });
                      try {
                        final result = await Api.login(email.text.trim(), password.text);
                        if (result.mfaRequired) {
                          setState(() {
                            mfaRequired = true;
                            mfaToken = result.mfaToken;
                            mfaTokenExpiresAt = result.mfaTokenExpiresAt;
                            msg = 'MFA required. Enter your code.';
                          });
                          return;
                        }
                        setState(() => msg = 'Logged in.');
                        if (mounted) Navigator.pop(context);
                      } catch (e) {
                        setState(() => msg = e.toString());
                      } finally {
                        if (mounted) setState(() => busy = false);
                      }
                    },
                    child: const Text('Login'),
                  ),
                ],
              ),
              if (mfaRequired) ...[
                const SizedBox(height: 12),
                Form(
                  key: _mfaFormKey,
                  child: TextFormField(
                    controller: mfaCode,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'MFA Code'),
                    validator: (value) {
                      final input = (value ?? '').trim();
                      if (input.isEmpty) return 'MFA code is required.';
                      if (!RegExp(r'^\\d{6,8}$').hasMatch(input)) {
                        return 'Enter a 6-8 digit code.';
                      }
                      return null;
                    },
                  ),
                ),
                if (mfaTokenExpiresAt != null && mfaTokenExpiresAt!.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: Text(
                      'Expires at $mfaTokenExpiresAt',
                      style: const TextStyle(fontSize: 12, color: Colors.black54),
                    ),
                  ),
                const SizedBox(height: 8),
                OutlinedButton(
                  onPressed: busy ? null : () async {
                    if (!_mfaFormKey.currentState!.validate()) return;
                    if (mfaToken == null || mfaToken!.isEmpty) {
                      setState(() => msg = 'Missing MFA token. Retry login.');
                      return;
                    }
                    setState(() {
                      busy = true;
                      msg = 'Verifying MFA...';
                    });
                    try {
                      final result = await Api.verifyMfa(mfaCode.text.trim(), mfaToken: mfaToken);
                      if (result.token == null) {
                        setState(() => msg = 'MFA verification failed.');
                        return;
                      }
                      setState(() => msg = 'MFA verified.');
                      if (mounted) Navigator.pop(context);
                    } catch (e) {
                      setState(() => msg = e.toString());
                    } finally {
                      if (mounted) setState(() => busy = false);
                    }
                  },
                  child: const Text('Verify MFA'),
                ),
              ],
              const SizedBox(height: 10),
              ModuleFeedback(message: msg),
            ],
          ),
        ),
      ),
    );
  }
}
