package io.github.jqssun.airplay.mirror

/**
 * Tiny framing protocol for in-app screen mirroring (Android device -> this receiver).
 *
 * After the TCP connection opens, the sender writes:
 *   [MAGIC int][width int][height int]            -- stream header (once)
 * then a sequence of frames, each:
 *   [payload length int][flags byte][payload ...]  -- one H.264 Annex-B access unit
 *
 * flags bit0 = keyframe (payload carries SPS+PPS+IDR). All integers big-endian (DataStream).
 */
object MirrorProtocol {
    const val SERVICE_TYPE = "_airmirror._tcp"
    const val MAGIC = 0x414D5231 // "AMR1"
    const val FLAG_KEYFRAME = 0x01

    // sane defaults for the encoder
    const val DEFAULT_BITRATE = 8_000_000
    const val DEFAULT_FPS = 30
    const val KEYFRAME_INTERVAL_S = 2
}
