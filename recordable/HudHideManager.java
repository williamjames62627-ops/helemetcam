package dev.recordable;

public final class HudHideManager {
	private static boolean isRecording = false;

	public static void onRecordingStart() {
		isRecording = true;
	}

	public static void onRecordingStop() {
		isRecording = false;
	}

	public static boolean shouldHideChat() {
		return isRecording && RecordableConfig.get().hideChat;
	}

	public static boolean shouldHideCrosshair() {
		return isRecording && RecordableConfig.get().hideCrosshair;
	}

	public static boolean shouldHideHotbar() {
		return isRecording && RecordableConfig.get().hideHotbar;
	}

	public static boolean shouldHideBossBar() {
		return isRecording && RecordableConfig.get().hideBossBar;
	}

	public static boolean shouldHideHand() {
		return isRecording && RecordableConfig.get().hideHand;
	}

	public static boolean shouldHideScoreboard() {
		return isRecording && RecordableConfig.get().hideScoreboard;
	}

	public static boolean shouldHideVignette() {
		return isRecording && RecordableConfig.get().hideVignette;
	}

	public static boolean isAnyCleanRecordingEnabled() {
		RecordableConfig cfg = RecordableConfig.get();
		return cfg.hideChat || cfg.hideCrosshair || cfg.hideHotbar || cfg.hideBossBar || cfg.hideHand || cfg.hideScoreboard || cfg.hideVignette;
	}
}
