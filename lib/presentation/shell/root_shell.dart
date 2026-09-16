import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/context_x.dart';
import '../../core/widgets/gradient_backdrop.dart';
import '../bloc/downloads/downloads_bloc.dart';
import '../cubit/settings/settings_cubit.dart';
import '../downloads/downloads_screen.dart';
import '../history/history_screen.dart';
import '../home/home_screen.dart';
import '../settings/settings_screen.dart';

/// Holds the selected tab of the app shell.
class RootShellCubit extends Cubit<int> {
  RootShellCubit() : super(0);

  void selectTab(int index) => emit(index.clamp(0, 3).toInt());

  void showDownloads() => emit(1);
}

/// Main app scaffold: bottom navigation across Home, Downloads, History and
/// Settings, with the optional gradient backdrop.
class RootShell extends StatelessWidget {
  const RootShell({super.key});

  @override
  Widget build(BuildContext context) {
    return BlocProvider(
      create: (_) => RootShellCubit(),
      child: BlocBuilder<RootShellCubit, int>(
        builder: (context, index) {
          final colors = context.dugan;
          return Scaffold(
            extendBody: true,
            body: GradientBackdrop(
              enabled: context
                  .select((SettingsCubit cubit) => cubit.state.settings.gradientBackground),
              child: IndexedStack(
                index: index,
                children: const [
                  HomeScreen(),
                  DownloadsScreen(),
                  HistoryScreen(),
                  SettingsScreen(),
                ],
              ),
            ),
            bottomNavigationBar: _ShellNavigationBar(
              index: index,
              badgeCount: context.select(
                (DownloadsBloc bloc) => bloc.state.active.length,
              ),
              colors: colors,
            ),
          );
        },
      ),
    );
  }
}

class _ShellNavigationBar extends StatelessWidget {
  const _ShellNavigationBar({
    required this.index,
    required this.badgeCount,
    required this.colors,
  });

  final int index;
  final int badgeCount;
  final DuganColors colors;

  @override
  Widget build(BuildContext context) {
    return NavigationBar(
      selectedIndex: index,
      onDestinationSelected: (value) =>
          context.read<RootShellCubit>().selectTab(value),
      destinations: [
        const NavigationDestination(
          icon: Icon(Icons.home_outlined),
          selectedIcon: Icon(Icons.home_rounded),
          label: 'Home',
        ),
        NavigationDestination(
          icon: Badge(
            isLabelVisible: badgeCount > 0,
            label: Text('$badgeCount'),
            backgroundColor: colors.accentPrimary,
            child: const Icon(Icons.download_outlined),
          ),
          selectedIcon: Badge(
            isLabelVisible: badgeCount > 0,
            label: Text('$badgeCount'),
            backgroundColor: colors.accentPrimary,
            child: const Icon(Icons.download_rounded),
          ),
          label: 'Downloads',
        ),
        const NavigationDestination(
          icon: Icon(Icons.video_library_outlined),
          selectedIcon: Icon(Icons.video_library_rounded),
          label: 'Library',
        ),
        const NavigationDestination(
          icon: Icon(Icons.settings_outlined),
          selectedIcon: Icon(Icons.settings_rounded),
          label: 'Settings',
        ),
      ],
    );
  }
}
