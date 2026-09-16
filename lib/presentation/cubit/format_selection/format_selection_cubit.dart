import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../domain/entities/app_settings.dart';
import '../../../domain/entities/download_request.dart';
import '../../../domain/entities/media_info.dart';
import 'format_selection_state.dart';

/// Manages the format sheet selection: chosen streams, audio options,
/// subtitles, filename and estimated size.
class FormatSelectionCubit extends Cubit<FormatSelectionState> {
  FormatSelectionCubit({
    required MediaInfo media,
    required AppSettings settings,
  }) : super(FormatSelectionState.initial(media, settings));

  void selectVideo(String? formatId) {
    if (formatId == null) {
      emit(state.copyWith(videoFormatId: null));
      return;
    }
    emit(
      state.copyWith(
        videoFormatId: formatId,
        audioOnly: false,
      ),
    );
  }

  void selectAudio(String? formatId) {
    emit(state.copyWith(audioFormatId: formatId));
  }

  void toggleMergeWithBestAudio() {
    emit(state.copyWith(mergeWithBestAudio: !state.mergeWithBestAudio));
  }

  void setAudioOnly(bool value) {
    emit(
      state.copyWith(
        audioOnly: value,
        videoFormatId: value ? null : state.videoFormatId,
      ),
    );
  }

  void setAudioContainer(String container) {
    emit(state.copyWith(audioContainer: container));
  }

  void setAudioBitrate(int kbps) {
    emit(state.copyWith(audioBitrateKbps: kbps));
  }

  void toggleEmbedMetadata() {
    emit(state.copyWith(embedMetadata: !state.embedMetadata));
  }

  void toggleEmbedThumbnail() {
    emit(state.copyWith(embedThumbnail: !state.embedThumbnail));
  }

  void toggleEmbedSubtitles() {
    emit(state.copyWith(embedSubtitles: !state.embedSubtitles));
  }

  void toggleSubtitleLanguage(String language) {
    final langs = {...state.subtitleLanguages};
    if (!langs.remove(language)) {
      langs.add(language);
    }
    emit(
      state.copyWith(
        subtitleLanguages: langs,
        embedSubtitles: langs.isNotEmpty ? true : state.embedSubtitles,
      ),
    );
  }

  void filenameChanged(String value) {
    emit(state.copyWith(filenameTemplate: value));
  }

  void toggleLayout() {
    emit(state.copyWith(showGrid: !state.showGrid));
  }

  /// Builds the download request from the current selection.
  DownloadRequest buildRequest() {
    return DownloadRequest(
      media: state.media,
      videoFormatId: state.audioOnly ? null : state.videoFormatId,
      audioFormatId: state.audioOnly ? null : state.audioFormatId,
      mergeWithBestAudio: state.mergeWithBestAudio,
      audioOnly: state.audioOnly,
      mergeContainer: 'mp4',
      audioContainer: state.audioContainer,
      audioBitrateKbps: state.audioBitrateKbps,
      embedMetadata: state.embedMetadata,
      embedThumbnail: state.embedThumbnail,
      embedSubtitles: state.embedSubtitles && state.subtitleLanguages.isNotEmpty,
      subtitleLanguages: state.subtitleLanguages.toList(),
      filenameTemplate: state.filenameTemplate.trim().isEmpty
          ? '%(title)s [%(id)s].%(ext)s'
          : state.filenameTemplate.trim(),
    );
  }
}
