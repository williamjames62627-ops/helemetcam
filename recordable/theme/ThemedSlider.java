package dev.recordable.theme;

import java.util.function.Consumer;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_357;

public class ThemedSlider extends class_357 {
	private final Consumer<Double> onChange;
	private final String labelFormat;
	private final double minVal;
	private final double maxVal;

	public ThemedSlider(int x, int y, int w, int h, String labelFormat, double minVal, double maxVal, double currentVal, Consumer<Double> onChange) {
		super(x, y, w, h, class_2561.method_43470(String.format(labelFormat, (int)currentVal)), (currentVal - minVal) / (maxVal - minVal));
		this.onChange = onChange;
		this.labelFormat = labelFormat;
		this.minVal = minVal;
		this.maxVal = maxVal;
	}

	protected void method_25346() {
		double val = this.minVal + this.field_22753 * (this.maxVal - this.minVal);
		this.method_25355(class_2561.method_43470(String.format(this.labelFormat, (int)val)));
	}

	protected void method_25344() {
		double val = this.minVal + this.field_22753 * (this.maxVal - this.minVal);
		this.onChange.accept(val);
	}

	public double getActualValue() {
		return this.minVal + this.field_22753 * (this.maxVal - this.minVal);
	}

	public static void drawThemedBar(class_332 context, int x, int y, int w, int h, double progress, boolean hovered) {
		ThemeColors colors = ThemeEngine.get().colors();
		context.method_25294(x, y, x + w, y + h, colors.buttonBackground);
		context.method_25294(x, y, x + w, y + 1, colors.buttonBorder);
		context.method_25294(x, y + h - 1, x + w, y + h, colors.buttonBorder);
		context.method_25294(x, y, x + 1, y + h, colors.buttonBorder);
		context.method_25294(x + w - 1, y, x + w, y + h, colors.buttonBorder);
		int filledWidth = (int)(progress * (double)(w - 4));
		int fillColor = hovered ? colors.accentHover : colors.accent;
		context.method_25294(x + 2, y + 2, x + 2 + filledWidth, y + h - 2, fillColor);
		ThemePreset preset = ThemeEngine.get().preset();
		if (preset == ThemePreset.VHS || preset == ThemePreset.CINEMA) {
			int tickCount = 10;

			for (int i = 0; i <= tickCount; i++) {
				int tx = x + 2 + (int)((float)(w - 4) * ((float)i / (float)tickCount));
				context.method_25294(tx, y + h - 4, tx + 1, y + h - 1, colors.textMuted & 1627389951);
			}
		}

		int thumbX = x + 2 + filledWidth - 2;
		context.method_25294(thumbX, y + 1, thumbX + 4, y + h - 1, colors.textPrimary);
		if (hovered) {
			context.method_25294(thumbX - 1, y, thumbX + 5, y + h, colors.accentHover & 1090519039);
		}
	}
}
