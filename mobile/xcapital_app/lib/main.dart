import 'package:flutter/material.dart';
import 'theme/xcapital_theme.dart';
import 'screens/login.dart';
import 'screens/home.dart';
import 'screens/portfolio_lab.dart';
import 'screens/portfolio_builder.dart';
import 'screens/audit_log.dart';
import 'screens/market_data.dart';
import 'screens/auto_invest.dart';
import 'screens/simulation.dart';
import 'screens/ai.dart';
import 'screens/watchlists.dart';
import 'screens/alerts.dart';
import 'screens/statements.dart';
import 'screens/research.dart';
import 'screens/manual_trade.dart';
import 'screens/banking.dart';
import 'screens/wealth_plan.dart';
import 'screens/screener.dart';
import 'screens/rewards.dart';
import 'screens/surveillance.dart';
import 'screens/best_execution.dart';
import 'screens/org_admin.dart';

final Map<String, WidgetBuilder> appRoutes = <String, WidgetBuilder>{
  '/': (_) => const HomeScreen(),
  '/home': (_) => const HomeScreen(),
  '/login': (_) => const LoginScreen(),
  '/portfolio-lab': (_) => const PortfolioLabScreen(),
  '/portfolio-builder': (_) => const PortfolioBuilderScreen(),
  '/audit': (_) => const AuditLogScreen(),
  '/market-data': (_) => const MarketDataScreen(),
  '/auto-invest': (_) => const AutoInvestScreen(),
  '/simulation': (_) => const SimulationScreen(),
  '/ai': (_) => const AiScreen(),
  '/watchlists': (_) => const WatchlistsScreen(),
  '/alerts': (_) => const AlertsScreen(),
  '/statements': (_) => const StatementsScreen(),
  '/research': (_) => const ResearchScreen(),
  '/manual-trade': (_) => const ManualTradeScreen(),
  '/banking': (_) => const BankingScreen(),
  '/wealth-plan': (_) => const WealthPlanScreen(),
  '/screeners': (_) => const ScreenerScreen(),
  '/rewards': (_) => const RewardsScreen(),
  '/surveillance': (_) => const SurveillanceScreen(),
  '/best-execution': (_) => const BestExecutionScreen(),
  '/org-admin': (_) => const OrgAdminScreen(),
};

void main() {
  runApp(const XCapitalApp());
}

class XCapitalApp extends StatelessWidget {
  const XCapitalApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'xCAPITAL',
      theme: xCapitalTheme(),
      routes: appRoutes,
      initialRoute: '/',
    );
  }
}
