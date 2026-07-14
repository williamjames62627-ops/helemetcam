package dev.recordable.screen;

import dev.recordable.compat.RenderHelper;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.minecraft.class_2561;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_339;
import net.minecraft.class_342;
import net.minecraft.class_6382;

public final class ColorPickerWidget extends class_339 {
	public static final int WIDGET_HEIGHT = 20;
	private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
	private static final int INPUT_WIDTH = 80;
	private static final int INNER_GAP = 5;
	private final class_327 textRenderer;
	private final class_342 textField;
	private final Consumer<String> onColorChanged;
	private final class_2561 label;

	public ColorPickerWidget(class_327 textRenderer, int x, int y, int width, int height, class_2561 label, String currentValue, Consumer<String> onColorChanged) {
		super(x, y, width, height, class_2561.method_43473());
		this.textRenderer = textRenderer;
		this.onColorChanged = onColorChanged;
		this.label = (class_2561)(label == null ? class_2561.method_43473() : label);
		this.textField = new class_342(textRenderer, x, y, 80, Math.max(16, height - 2), class_2561.method_43470("#RRGGBB"));
		this.textField.method_1880(7);
		this.textField.method_1852(sanitizeColor(currentValue));
		this.textField.method_1863(value -> {
			String candidate = normalize(value);
			if (isValidHex(candidate) && this.onColorChanged != null) {
				this.onColorChanged.accept(candidate.toUpperCase(Locale.ROOT));
			}
		});
		this.updateTextFieldLayout(x, y);
	}

	public String getColor() {
		String candidate = normalize(this.textField.method_1882());
		return isValidHex(candidate) ? candidate.toUpperCase(Locale.ROOT) : "#FF0000";
	}

	public void method_48229(int x, int y) {
		this.method_46421(x);
		this.method_46419(y);
		this.updateTextFieldLayout(x, y);
	}

	protected void method_48579(class_332 context, int mouseX, int mouseY, float delta) {
		int x = this.method_46426();
		int y = this.method_46427();
		int previewColor = parseColor(this.getColor());
		context.method_25294(x, y, x + this.field_22758, y + this.field_22759, -1441458923);
		context.method_25294(x, y, x + this.field_22758, y + 1, -12434878);
		context.method_25294(x, y + this.field_22759 - 1, x + this.field_22758, y + this.field_22759, -12434878);
		context.method_25294(x, y, x + 1, y + this.field_22759, -12434878);
		context.method_25294(x + this.field_22758 - 1, y, x + this.field_22758, y + this.field_22759, -12434878);
		int labelAreaWidth = this.getLabelAreaWidth();
		class_2561 renderLabel = this.getRenderLabel(labelAreaWidth);
		RenderHelper.drawText(context, this.textRenderer, renderLabel, x + 2, y + Math.max(0, (this.field_22759 - 8) / 2), -1);
		int previewSize = Math.max(12, this.field_22759 - 4);
		int previewX = x + labelAreaWidth + 5;
		int previewY = y + 2;
		context.method_25294(previewX, previewY, previewX + previewSize, previewY + previewSize, 0xFF000000 | previewColor);
		context.method_25294(previewX, previewY, previewX + previewSize, previewY + 1, -1);
		context.method_25294(previewX, previewY + previewSize - 1, previewX + previewSize, previewY + previewSize, -1);
		this.textField.method_25394(context, mouseX, mouseY, delta);
		if (!isValidHex(normalize(this.textField.method_1882()))) {
			RenderHelper.drawText(context, this.textRenderer, class_2561.method_43470("!"), x + this.field_22758 - 10, y + this.field_22759 / 2 - 4, -39322);
		}
	}

	public boolean method_25402(double mouseX, double mouseY, int button) {
		boolean handled = this.textField.method_25402(mouseX, mouseY, button);
		this.method_25365(this.textField.method_25370());
		return handled || super.method_25402(mouseX, mouseY, button);
	}

	public boolean method_25406(double mouseX, double mouseY, int button) {
		return this.textField.method_25406(mouseX, mouseY, button) || super.method_25406(mouseX, mouseY, button);
	}

	public boolean method_25403(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		return this.textField.method_25403(mouseX, mouseY, button, deltaX, deltaY) || super.method_25403(mouseX, mouseY, button, deltaX, deltaY);
	}

	public boolean method_25404(int keyCode, int scanCode, int modifiers) {
		return this.textField.method_25404(keyCode, scanCode, modifiers) || super.method_25404(keyCode, scanCode, modifiers);
	}

	public boolean method_25400(char chr, int modifiers) {
		return this.textField.method_25400(chr, modifiers) || super.method_25400(chr, modifiers);
	}

	public void method_25365(boolean focused) {
		super.method_25365(focused);
		this.textField.method_25365(focused);
	}

	protected void method_47399(class_6382 builder) {
		this.method_37021(builder);
	}

	private void updateTextFieldLayout(int x, int y) {
		int previewSize = Math.max(12, this.field_22759 - 4);
		int labelAreaWidth = this.getLabelAreaWidth();
		int inputX = x + labelAreaWidth + 5 + previewSize + 5;
		int maxInputWidth = Math.max(56, this.field_22758 - (inputX - x) - 5);
		this.textField.method_46421(inputX);
		this.textField.method_46419(y + Math.max(0, (this.field_22759 - 18) / 2));
		this.textField.method_25358(Math.min(80, maxInputWidth));
	}

	private int getLabelAreaWidth() {
		int previewSize = Math.max(12, this.field_22759 - 4);
		return Math.max(30, this.field_22758 - (80 + previewSize + 15));
	}

	private class_2561 getRenderLabel(int labelAreaWidth) {
		String raw = this.label.getString();
		if (raw != null && !raw.isBlank()) {
			String text = raw;
			if (this.textRenderer.method_1727(raw) > labelAreaWidth) {
				int trimmedWidth = Math.max(10, labelAreaWidth - this.textRenderer.method_1727("…"));
				text = this.textRenderer.method_27523(raw, trimmedWidth) + "…";
			}

			return class_2561.method_43470(text);
		} else {
			return class_2561.method_43473();
		}
	}

	private static boolean isValidHex(String value) {
		return HEX_COLOR.matcher(value).matches();
	}

	private static String normalize(String value) {
		String normalized = value == null ? "" : value.trim();
		if (!normalized.startsWith("#")) {
			normalized = "#" + normalized;
		}

		return normalized;
	}

	private static String sanitizeColor(String value) {
		String normalized = normalize(value);
		return !isValidHex(normalized) ? "#FF0000" : normalized.toUpperCase(Locale.ROOT);
	}

	private static int parseColor(String hex) {
		try {
			return Integer.parseInt(hex.substring(1), 16);
		} catch (Throwable var2) {
			return 16711680;
		}
	}
}
