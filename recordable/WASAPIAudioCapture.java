package dev.recordable;

public final class WASAPIAudioCapture {
	public static boolean isAvailable() {
		return false;
	}

	public boolean startCapture() {
		return false;
	}

	public void stopCapture() {
	}
}
