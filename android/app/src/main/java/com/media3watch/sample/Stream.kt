package com.media3watch.sample

import androidx.media3.common.MimeTypes

enum class StreamType {
    MP4,
    HLS,
    DASH,
}

data class Stream(
    val title: String,
    val url: String,
    val type: StreamType,
    val mimeType: String? = null,
)

val defaultStreams = listOf(
    Stream(
        title = "Big Buck Bunny",
        url = "https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_320x180.mp4",
        type = StreamType.MP4,
    ),
    Stream(
        title = "Apple Bipbop",
        url = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8",
        type = StreamType.HLS,
        mimeType = MimeTypes.APPLICATION_M3U8,
    ),
    Stream(
        title = "Tears of Steel",
        url = "https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd",
        type = StreamType.DASH,
        mimeType = MimeTypes.APPLICATION_MPD,
    ),
)
