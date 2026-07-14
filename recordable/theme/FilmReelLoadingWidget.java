package dev.recordable.theme;

import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_339;
import net.minecraft.class_6382;

public class FilmReelLoadingWidget extends class_339 {
	private String statusText;
	private float progress = -1.0F;

	public FilmReelLoadingWidget(int x, int y, int width, int height) {
		super(x, y, width, height, class_2561.method_43470("Loading..."));
		this.statusText = "Loading...";
	}

	public void setStatusText(String text) {
		this.statusText = text;
	}

	public void setProgress(float progress) {
		this.progress = progress;
	}

	protected void method_48579(class_332 context, int mouseX, int mouseY, float delta) {
		ThemeColors colors = ThemeEngine.get().colors();
		int x = this.method_46426();
		int y = this.method_46427();
		int w = this.method_25368();
		int h = this.method_25364();
		context.method_25294(x, y, x + w, y + h, colors.sectionBackground);
		int reelRadius = Math.min(h / 3, 12);
		int leftReelX = x + w / 3;
		int rightReelX = x + 2 * w / 3;
		int reelY = y + h / 2 - 4;
		long tick = System.currentTimeMillis();
		this.drawSpinningReel(context, leftReelX, reelY, reelRadius, tick, colors);
		this.drawSpinningReel(context, rightReelX, reelY, reelRadius, tick + 500L, colors);
		int stripY = reelY - 1;
		context.method_25294(leftReelX + reelRadius, stripY, rightReelX - reelRadius, stripY + 3, colors.accent & -2130706433);

		for (int sx = leftReelX + reelRadius + 2; sx < rightReelX - reelRadius - 2; sx += 6) {
			int dotOffset = (int)(tick / 80L % 6L);
			context.method_25294(sx + dotOffset, stripY, sx + dotOffset + 2, stripY + 1, colors.textMuted);
		}

		int barY = y + h - 6;
		if (this.progress >= 0.0F) {
			VhsEffectsRenderer.renderTapeLoadingBar(context, x + 4, barY, x + w - 4, this.progress);
		} else {
			int shimmerWidth = w / 4;
			int shimmerPos = (int)(tick / 8L % (long)(w + shimmerWidth)) - shimmerWidth;
			int shimmerLeft = Math.max(x + 4, x + shimmerPos);
			int shimmerRight = Math.min(x + w - 4, x + shimmerPos + shimmerWidth);
			ctx(context, x + 4, barY, x + w - 4, barY + 3, colors.panelBorder);
			if (shimmerRight > shimmerLeft) {
				ctx(context, shimmerLeft, barY, shimmerRight, barY + 3, colors.accent);
			}
		}

		if (this.statusText != null) {
			class_310 mc = class_310.method_1551();
			context.method_25300(mc.field_1772, this.statusText, x + w / 2, y + 3, colors.textSecondary);
		}
	}

	private void drawSpinningReel(class_332 context, int cx, int cy, int radius, long tick, ThemeColors colors) {
		context.method_25294(cx - radius, cy - radius, cx + radius, cy + radius, colors.panelBorder);
		context.method_25294(cx - radius + 1, cy - radius + 1, cx + radius - 1, cy + radius - 1, colors.sectionBackground);
		double angle = (double)(tick % 1500L) / 1500.0 * Math.PI * 2.0;

		for (int i = 0; i < 3; i++) {
			double a = angle + (double)i * Math.PI * 2.0 / 3.0;
			int dx = (int)(Math.cos(a) * (double)(radius - 2));
			int dy = (int)(Math.sin(a) * (double)(radius - 2));
			context.method_25294(cx - 1, cy - 1, cx + dx, cy + dy, colors.accent);
		}

		context.method_25294(cx - 2, cy - 2, cx + 2, cy + 2, colors.accent);
	}

	private static void ctx(class_332 context, int x1, int y1, int x2, int y2, int color) {
		context.method_25294(x1, y1, x2, y2, color);
	}

	protected void method_47399(class_6382 builder) {
	}
}
