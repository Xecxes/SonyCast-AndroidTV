# App-level AirPlay video support (test5)

The original fork implemented mirroring/audio callbacks but did not connect UxPlay's
HTTP video playback callbacks or enable its HLS/video capabilities. Screen mirroring
therefore did not establish compatibility with video applications' AirPlay buttons.

This revision adds:

- AirPlay video capability bits 0 and 4 and the native HLS service option.
- Native-to-Kotlin play, scrub, rate, stop, and playback-info callbacks.
- A separate Media3 video activity, with immutable playback snapshots that can be
  read by the protocol thread without accessing ExoPlayer off its main thread.
- Registration of the actual selected server port for local HLS callback URLs.
- ALAC enabled by default and corrected native logger level interpretation.

Four UxPlay compatibility patches were backported from:
https://github.com/jqssun/android-airplay-server

Pinned source commit: `a46cca0a6a1274e9e33c0e750f0f1c1eb1d881ea`.

The patches from `app/src/main/cpp/patches/UxPlay/` were:

1. 0001-fix-hls-playlist-crash.patch
2. 0002-fix-uaf-on-playlist-refresh.patch
3. 0003-refresh-live-media-playlists-on-demand.patch
4. 0004-video-sender-compat.patch

The native callback design was also consulted from that project. Original source
and license notices remain in the vendored files. This application remains GPL-3.0.

Validation on Sony A80EK / Android 12: standard AirPlay binary-plist POST /play
displayed an H.264 MP4 over the LAN, /playback-info reported a 128.166 second duration
and advancing position, /rate paused and resumed it, and /scrub changed position.
Specific sender application compatibility must be recorded separately; the above
does not establish that Bilibili, protected content, or every HLS stream works.
