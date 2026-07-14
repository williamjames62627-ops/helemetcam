package dev.recordable;

public enum EncoderType {
	FFMPEG("FFmpeg");

	private final String displayName;

	private EncoderType(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return this.displayName;
	}
}
