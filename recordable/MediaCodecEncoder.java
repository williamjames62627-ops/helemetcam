package dev.recordable;

import dev.recordable.PlatformUtils.Platform;

public final class MediaCodecEncoder {
	private MediaCodecEncoder() {
	}

	public static boolean isAvailable() {
		if (PlatformUtils.detectPlatform() != Platform.ANDROID) {
			return false;
		} else {
			try {
				Class.forName("android.media.MediaCodec");
				RecordableMod.LOGGER.info("Android MediaCodec API detected - hardware encoding available (future feature).");
				return false;
			} catch (ClassNotFoundException var1) {
				return false;
			}
		}
	}

	public static String getStatus() {
		return PlatformUtils.detectPlatform() == Platform.ANDROID ? "Android detected - MediaCodec support coming soon" : "Not available (desktop platform)";
	}
}
