import 'package:flutter/material.dart';

const List<HomeSection> homeSections = <HomeSection>[
  HomeSection(
    title: 'Access',
    subtitle: 'Authentication and core portfolio entry points.',
    items: <HomeNavItem>[
      HomeNavItem('Login', '/login', Icons.login),
      HomeNavItem('Portfolio Lab', '/portfolio-lab', Icons.analytics_outlined),
      HomeNavItem('Portfolio Builder', '/portfolio-builder', Icons.account_tree_outlined),
      HomeNavItem('Market Data', '/market-data', Icons.show_chart),
    ],
  ),
  HomeSection(
    title: 'Trading',
    subtitle: 'Execution, automation, and risk operations.',
    items: <HomeNavItem>[
      HomeNavItem('Manual Trade', '/manual-trade', Icons.swap_horiz),
      HomeNavItem('Auto Invest', '/auto-invest', Icons.auto_graph),
      HomeNavItem('Best Execution', '/best-execution', Icons.speed_outlined),
      HomeNavItem('Surveillance', '/surveillance', Icons.shield_outlined),
      HomeNavItem('Audit Log', '/audit', Icons.history),
    ],
  ),
  HomeSection(
    title: 'Client Wealth',
    subtitle: 'Banking, planning, and rewards workflows.',
    items: <HomeNavItem>[
      HomeNavItem('Banking', '/banking', Icons.account_balance),
      HomeNavItem('Funding / Statements', '/statements', Icons.receipt_long),
      HomeNavItem('Wealth Plan', '/wealth-plan', Icons.savings_outlined),
      HomeNavItem('Rewards', '/rewards', Icons.card_giftcard),
    ],
  ),
  HomeSection(
    title: 'Research & Signals',
    subtitle: 'Research, watchlists, and alerting modules.',
    items: <HomeNavItem>[
      HomeNavItem('Screeners', '/screeners', Icons.tune),
      HomeNavItem('Research', '/research', Icons.article_outlined),
      HomeNavItem('Watchlists', '/watchlists', Icons.bookmark_border),
      HomeNavItem('Alerts', '/alerts', Icons.notifications_active),
    ],
  ),
  HomeSection(
    title: 'Platform',
    subtitle: 'Simulation, AI, and org-level controls.',
    items: <HomeNavItem>[
      HomeNavItem('Simulation', '/simulation', Icons.timeline),
      HomeNavItem('AI', '/ai', Icons.psychology),
      HomeNavItem('Org Admin', '/org-admin', Icons.admin_panel_settings_outlined),
    ],
  ),
];

final List<HomeNavItem> homeNavItems = <HomeNavItem>[
  for (final section in homeSections) ...section.items,
];

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final TextEditingController _searchCtrl = TextEditingController();
  String _query = '';

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final filtered = _filteredSections(_query);
    return Scaffold(
      appBar: AppBar(
        title: const Text('xCAPITAL — Mobile Hub'),
      ),
      body: Column(
        children: [
          Container(
            width: double.infinity,
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 12),
            color: Theme.of(context).colorScheme.surfaceContainerLowest,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Enterprise module navigation',
                  style: TextStyle(fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 6),
                Text(
                  '${homeNavItems.length} routes',
                  style: const TextStyle(fontSize: 12, color: Colors.black54),
                ),
                const SizedBox(height: 10),
                TextField(
                  controller: _searchCtrl,
                  onChanged: (value) => setState(() => _query = value.trim().toLowerCase()),
                  decoration: const InputDecoration(
                    isDense: true,
                    hintText: 'Search modules',
                    prefixIcon: Icon(Icons.search),
                    border: OutlineInputBorder(),
                  ),
                ),
              ],
            ),
          ),
          Expanded(
            child: filtered.isEmpty
                ? const Center(
                    child: Text(
                      'No modules matched your search.',
                      style: TextStyle(color: Colors.black54),
                    ),
                  )
                : ListView.builder(
                    padding: const EdgeInsets.all(16),
                    itemCount: filtered.length,
                    itemBuilder: (context, index) {
                      final section = filtered[index];
                      return _SectionCard(section: section);
                    },
                  ),
          ),
        ],
      ),
    );
  }

  List<HomeSection> _filteredSections(String query) {
    if (query.isEmpty) {
      return homeSections;
    }
    final List<HomeSection> out = <HomeSection>[];
    for (final section in homeSections) {
      final items = section.items.where((item) {
        final label = item.label.toLowerCase();
        final route = item.route.toLowerCase();
        return label.contains(query) || route.contains(query);
      }).toList();
      if (items.isNotEmpty) {
        out.add(HomeSection(title: section.title, subtitle: section.subtitle, items: items));
      }
    }
    return out;
  }
}

class _SectionCard extends StatelessWidget {
  const _SectionCard({required this.section});

  final HomeSection section;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 14),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 8, 14, 2),
              child: Text(
                section.title,
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 0, 14, 8),
              child: Text(
                section.subtitle,
                style: const TextStyle(fontSize: 12, color: Colors.black54),
              ),
            ),
            const Divider(height: 1),
            for (int i = 0; i < section.items.length; i++) ...[
              ListTile(
                leading: Icon(section.items[i].icon),
                title: Text(section.items[i].label),
                subtitle: Text(section.items[i].route, style: const TextStyle(fontSize: 12)),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => Navigator.pushNamed(context, section.items[i].route),
              ),
              if (i != section.items.length - 1) const Divider(height: 1),
            ],
          ],
        ),
      ),
    );
  }
}

class HomeSection {
  final String title;
  final String subtitle;
  final List<HomeNavItem> items;

  const HomeSection({
    required this.title,
    required this.subtitle,
    required this.items,
  });
}

class HomeNavItem {
  final String label;
  final String route;
  final IconData icon;

  const HomeNavItem(this.label, this.route, this.icon);
}
