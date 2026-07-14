package dev.recordable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class WatermarkSlot {
	public boolean enabled = false;
	public String name = "Watermark";
	public WatermarkSlot.Kind kind = WatermarkSlot.Kind.TEXT;
	public String imagePath = "";
	public String text = "Record-able";
	public String textColor = "#FFFFFFFF";
	public List<String> textColors = new ArrayList();
	public boolean textShadow = true;
	public WatermarkSlot.Position position = WatermarkSlot.Position.BOTTOM_RIGHT;
	public int customX = 0;
	public int customY = 0;
	public int padding = 8;
	public int opacity = 80;
	public int scale = 100;
	public int rotation = 0;
	public WatermarkSlot.Animation animation = WatermarkSlot.Animation.NONE;
	public int animationDurationMs = 1000;
	public boolean useTimeRange = false;
	public int startSeconds = 0;
	public int endSeconds = 0;

	public WatermarkSlot() {
	}

	public WatermarkSlot(String name, WatermarkSlot.Kind kind) {
		this.name = name;
		this.kind = kind;
	}

	public WatermarkSlot copy() {
		WatermarkSlot w = new WatermarkSlot();
		w.enabled = this.enabled;
		w.name = this.name;
		w.kind = this.kind;
		w.imagePath = this.imagePath;
		w.text = this.text;
		w.textColor = this.textColor;
		w.textColors = this.textColors == null ? new ArrayList() : new ArrayList(this.textColors);
		w.textShadow = this.textShadow;
		w.position = this.position;
		w.customX = this.customX;
		w.customY = this.customY;
		w.padding = this.padding;
		w.opacity = this.opacity;
		w.scale = this.scale;
		w.rotation = this.rotation;
		w.animation = this.animation;
		w.animationDurationMs = this.animationDurationMs;
		w.useTimeRange = this.useTimeRange;
		w.startSeconds = this.startSeconds;
		w.endSeconds = this.endSeconds;
		return w;
	}

	public void sanitize() {
		if (this.name == null || this.name.isBlank()) {
			this.name = "Watermark";
		}

		if (this.kind == null) {
			this.kind = WatermarkSlot.Kind.TEXT;
		}

		if (this.position == null) {
			this.position = WatermarkSlot.Position.BOTTOM_RIGHT;
		}

		if (this.animation == null) {
			this.animation = WatermarkSlot.Animation.NONE;
		}

		if (this.imagePath == null) {
			this.imagePath = "";
		}

		if (this.text == null) {
			this.text = "";
		}

		if (this.textColor == null || this.textColor.isBlank()) {
			this.textColor = "#FFFFFFFF";
		}

		if (this.textColors == null) {
			this.textColors = new ArrayList();
		}

		if (this.textColors.isEmpty()) {
			this.textColors.add(this.textColor);
		}

		this.textColors.removeIf(c -> c == null || c.isBlank());
		if (this.textColors.isEmpty()) {
			this.textColors.add("#FFFFFFFF");
		}

		while (this.textColors.size() > 10) {
			this.textColors.remove(this.textColors.size() - 1);
		}

		this.textColor = (String)this.textColors.get(0);
		this.padding = clamp(this.padding, 0, 1000);
		this.opacity = clamp(this.opacity, 0, 100);
		this.scale = clamp(this.scale, 10, 200);
		this.rotation = clamp(this.rotation, -180, 180);
		this.animationDurationMs = clamp(this.animationDurationMs, 100, 10000);
		this.customX = clamp(this.customX, -10000, 10000);
		this.customY = clamp(this.customY, -10000, 10000);
		this.startSeconds = Math.max(0, this.startSeconds);
		if (this.endSeconds < 0) {
			this.endSeconds = 0;
		}
	}

	public List<String> effectiveColors() {
		if (this.textColors != null && !this.textColors.isEmpty()) {
			List<String> out = new ArrayList();

			for (String c : this.textColors) {
				if (c != null && !c.isBlank()) {
					out.add(c);
				}
			}

			if (!out.isEmpty()) {
				return out;
			}
		}

		List<String> out = new ArrayList();
		out.add(this.textColor != null && !this.textColor.isBlank() ? this.textColor : "#FFFFFFFF");
		return out;
	}

	public String resolveText(String username) {
		String out = this.text == null ? "" : this.text;
		LocalDateTime now = LocalDateTime.now();
		out = out.replace("{username}", username == null ? "Player" : username);
		out = out.replace("{date}", now.toLocalDate().toString());
		return out.replace("{time}", now.toLocalTime().withNano(0).toString());
	}

	public boolean visibleAt(double recordingSeconds) {
		if (!this.enabled) {
			return false;
		} else if (!this.useTimeRange) {
			return true;
		} else {
			return recordingSeconds < (double)this.startSeconds ? false : this.endSeconds <= 0 || !(recordingSeconds > (double)this.endSeconds);
		}
	}

	private static int clamp(int v, int lo, int hi) {
		return v < lo ? lo : Math.min(v, hi);
	}

	public static enum Animation {
		NONE,
		FADE,
		SLIDE,
		PULSE;
	}

	public static enum Kind {
		IMAGE,
		TEXT;
	}

	public static enum Position {
		TOP_LEFT,
		TOP_CENTER,
		TOP_RIGHT,
		MIDDLE_LEFT,
		CENTER,
		MIDDLE_RIGHT,
		BOTTOM_LEFT,
		BOTTOM_CENTER,
		BOTTOM_RIGHT,
		CUSTOM;
	}
}
