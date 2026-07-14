package dev.recordable;

public record RecordingBookmark(long timestampMs, String description) {
	public String toFileLine() {
		long totalSeconds = this.timestampMs / 1000L;
		long hours = totalSeconds / 3600L;
		long minutes = totalSeconds % 3600L / 60L;
		long seconds = totalSeconds % 60L;
		return String.format("[%02d:%02d:%02d] %s", hours, minutes, seconds, this.description);
	}
}
