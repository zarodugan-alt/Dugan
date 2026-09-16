import 'dart:io';

import 'package:chewie/chewie.dart';
import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';
import 'package:video_player/video_player.dart';

import '../../core/theme/context_x.dart';
import '../routes.dart';

/// Full-screen in-app player for downloaded files, built on Chewie with a
/// custom speed control overlay.
class PlayerScreen extends StatefulWidget {
  const PlayerScreen({super.key, required this.args});

  final PlayerScreenArgs args;

  @override
  State<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends State<PlayerScreen> {
  VideoPlayerController? _videoController;
  ChewieController? _chewieController;
  String? _error;
  double _speed = 1.0;

  static const _speeds = <double>[0.5, 0.75, 1.0, 1.25, 1.5, 2.0];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _initialize();
  }

  Future<void> _initialize() async {
    final file = File(widget.args.filePath);
    if (!await file.exists()) {
      if (mounted) {
        setState(() => _error = 'This file no longer exists on disk.');
      }
      return;
    }
    final controller = VideoPlayerController.file(file);
    try {
      await controller.initialize();
    } on Exception catch (e) {
      if (mounted) {
        setState(() => _error = 'Could not play this file — $e');
      }
      await controller.dispose();
      return;
    }
    if (!mounted) {
      await controller.dispose();
      return;
    }
    final chewie = ChewieController(
      videoPlayerController: controller,
      autoPlay: true,
      looping: false,
      aspectRatio: controller.value.aspectRatio,
      allowFullScreen: true,
      showControlsOnInitialize: true,
      errorBuilder: (context, errorMessage) => Center(
        child: Text(
          errorMessage,
          style: const TextStyle(color: Colors.white70),
        ),
      ),
    );
    setState(() {
      _videoController = controller;
      _chewieController = chewie;
    });
  }

  void _cycleSpeed() {
    final controller = _videoController;
    if (controller == null) {
      return;
    }
    final index = _speeds.indexOf(_speed);
    final next = _speeds[(index + 1) % _speeds.length];
    setState(() => _speed = next);
    controller.setSpeed(next);
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Pause playback when the app goes to background.
    if (state == AppLifecycleState.paused) {
      _videoController?.pause();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _chewieController?.dispose();
    _videoController?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.dugan;
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          if (_error != null)
            Center(
              child: Padding(
                padding: const EdgeInsets.all(32),
                child: Text(
                  _error!,
                  textAlign: TextAlign.center,
                  style: TextStyle(color: colors.textSecondary),
                ),
              ),
            )
          else if (_chewieController != null)
            Chewie(controller: _chewieController!)
          else
            const Center(
              child: CircularProgressIndicator(),
            ),
          SafeArea(
            child: Row(
              children: [
                IconButton(
                  icon: const Icon(Icons.arrow_back_rounded,
                      color: Colors.white),
                  onPressed: () => Navigator.of(context).pop(),
                ),
                Expanded(
                  child: Text(
                    widget.args.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                if (_videoController != null)
                  _SpeedChip(speed: _speed, onTap: _cycleSpeed),
                IconButton(
                  icon: const Icon(Icons.share_rounded, color: Colors.white),
                  onPressed: () =>
                      Share.shareXFiles([XFile(widget.args.filePath)]),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _SpeedChip extends StatelessWidget {
  const _SpeedChip({required this.speed, required this.onTap});

  final double speed;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      child: Material(
        color: Colors.white24,
        borderRadius: BorderRadius.circular(12),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(12),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            child: Text(
              '${speed}x',
              style: const TextStyle(
                color: Colors.white,
                fontSize: 12,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
