import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../cubit/settings/settings_cubit.dart';
import '../cubit/settings/settings_state.dart';
import 'widgets/settings_sections.dart';

/// Customization and advanced configuration.
class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: BlocBuilder<SettingsCubit, SettingsState>(
        buildWhen: (previous, current) =>
            previous.settings != current.settings,
        builder: (context, state) {
          return const ListView(
            padding: EdgeInsets.fromLTRB(20, 8, 20, 120),
            children: [
              AppearanceSection(),
              SizedBox(height: 28),
              DownloadsSection(),
              SizedBox(height: 28),
              AdvancedSection(),
              SizedBox(height: 28),
              AboutSection(),
            ],
          );
        },
      ),
    );
  }
}
