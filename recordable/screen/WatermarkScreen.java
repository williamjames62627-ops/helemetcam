package dev.recordable.screen;

import dev.recordable.RecordableConfig;
import dev.recordable.RecordableMod;
import dev.recordable.WatermarkImageStore;
import dev.recordable.WatermarkManager;
import dev.recordable.WatermarkSlot;
import dev.recordable.WatermarkSlot.Animation;
import dev.recordable.WatermarkSlot.Kind;
import dev.recordable.WatermarkSlot.Position;
import dev.recordable.compat.RenderHelper;
import dev.recordable.theme.CycleButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_342;
import net.minecraft.class_357;
import net.minecraft.class_4185;
import net.minecraft.class_437;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class WatermarkScreen extends class_437 {
	private static final int PANEL_COLOR = -804253680;
	private static final int PANEL_BORDER_COLOR = -12434878;
	private static final int TEXT_COLOR = -3092272;
	private static final int HEADER_COLOR = -1;
	private static final int HIGHLIGHT_COLOR = -7811960;
	private static final int WARNING_COLOR = -13244;
	private static final int ROW_HEIGHT = 22;
	private final class_437 parent;
	private int panelLeft;
	private int panelTop;
	private int panelWidth;
	private int panelBottom;
	private int listTop;
	private int listLeft;
	private int listWidth;
	private int editLeft;
	private int editWidth;
	private int selectedIndex = -1;
	private int gradientStopIndex = 0;
	private class_342 textField;
	private class_342 hexField;
	private String statusMessage = "";

	public WatermarkScreen(class_437 parent) {
		super(class_2561.method_43471("screen.recordable.watermark.title"));
		this.parent = parent;
	}

	private List<WatermarkSlot> slots() {
		return RecordableConfig.get().watermarkSlots;
	}

	protected void method_25426() {
		super.method_25426();
		this.panelWidth = Math.max(420, Math.min((int)((double)this.field_22789 * 0.9), 720));
		this.panelLeft = (this.field_22789 - this.panelWidth) / 2;
		this.panelTop = Math.max(8, (int)((double)this.field_22790 * 0.05));
		this.panelBottom = Math.min(this.field_22790 - 8, this.panelTop + Math.max(340, (int)((double)this.field_22790 * 0.88)));
		this.listLeft = this.panelLeft + 12;
		this.listWidth = (this.panelWidth - 36) / 2;
		this.editLeft = this.listLeft + this.listWidth + 12;
		this.editWidth = this.panelWidth - 24 - this.listWidth - 12;
		this.listTop = this.panelTop + 78;
		if (this.selectedIndex >= this.slots().size()) {
			this.selectedIndex = this.slots().size() - 1;
		}

		this.rebuildWidgets();
	}

	private void rebuildWidgets() {
		this.method_37067();
		RecordableConfig config = RecordableConfig.get();
		int topY = this.panelTop + 32;
		this.method_37063(class_4185.method_46430(class_2561.method_43470("Watermarks: " + (config.watermarksEnabled ? "ON" : "OFF")), button -> {
			config.watermarksEnabled = !config.watermarksEnabled;
			config.save();
			this.rebuildWidgets();
		}).method_46434(this.listLeft, topY, 120, 18).method_46431());
		this.method_37063(class_4185.method_46430(class_2561.method_43470("Live Preview: " + (config.showWatermarksLive ? "ON" : "OFF")), button -> {
			config.showWatermarksLive = !config.showWatermarksLive;
			config.save();
			this.rebuildWidgets();
		}).method_46434(this.listLeft + 128, topY, 130, 18).method_46431());
		boolean canAdd = this.slots().size() < 4;
		class_4185 addText = class_4185.method_46430(class_2561.method_43470("+ Text"), button -> {
			if (this.slots().size() < 4) {
				WatermarkSlot sx = new WatermarkSlot("Text " + (this.slots().size() + 1), Kind.TEXT);
				sx.enabled = true;
				this.slots().add(sx);
				this.selectedIndex = this.slots().size() - 1;
				config.save();
				this.rebuildWidgets();
			}
		}).method_46434(this.editLeft, topY, (this.editWidth - 16) / 3, 18).method_46431();
		addText.field_22763 = canAdd;
		this.method_37063(addText);
		class_4185 addImage = class_4185.method_46430(class_2561.method_43470("+ Image"), button -> {
			if (this.slots().size() < 4) {
				WatermarkSlot sx = new WatermarkSlot("Image " + (this.slots().size() + 1), Kind.IMAGE);
				sx.enabled = true;
				this.slots().add(sx);
				this.selectedIndex = this.slots().size() - 1;
				config.save();
				this.rebuildWidgets();
			}
		}).method_46434(this.editLeft + (this.editWidth - 16) / 3 + 8, topY, (this.editWidth - 16) / 3, 18).method_46431();
		addImage.field_22763 = canAdd;
		this.method_37063(addImage);
		this.method_37063(class_4185.method_46430(class_2561.method_43470("Preset: User"), button -> {
			if (this.slots().size() < 4) {
				this.slots().addAll(WatermarkManager.builtinUsernameStamp());

				while (this.slots().size() > 4) {
					this.slots().remove(this.slots().size() - 1);
				}

				config.watermarksEnabled = true;
				config.save();
				this.statusMessage = "Applied username preset.";
				this.rebuildWidgets();
			}
		}).method_46434(this.editLeft + 2 * ((this.editWidth - 16) / 3) + 16, topY, (this.editWidth - 16) / 3, 18).method_46431());
		List<WatermarkSlot> list = this.slots();

		for (int i = 0; i < list.size(); i++) {
			int idx = i;
			WatermarkSlot s = (WatermarkSlot)list.get(i);
			int rowY = this.listTop + i * 22;
			this.method_37063(class_4185.method_46430(class_2561.method_43470((s.enabled ? "[x] " : "[ ] ") + truncate(s.name, 14)), button -> {
				this.selectedIndex = idx;
				this.rebuildWidgets();
			}).method_46434(this.listLeft, rowY, this.listWidth - 88, 18).method_46431());
			this.method_37063(class_4185.method_46430(class_2561.method_43470(s.enabled ? "On" : "Off"), button -> {
				s.enabled = !s.enabled;
				RecordableConfig.get().save();
				this.rebuildWidgets();
			}).method_46434(this.listLeft + this.listWidth - 84, rowY, 38, 18).method_46431());
			this.method_37063(class_4185.method_46430(class_2561.method_43470("Del"), button -> {
				this.slots().remove(idx);
				if (this.selectedIndex >= this.slots().size()) {
					this.selectedIndex = this.slots().size() - 1;
				}

				RecordableConfig.get().save();
				this.rebuildWidgets();
			}).method_46434(this.listLeft + this.listWidth - 42, rowY, 38, 18).method_46431());
		}

		if (this.selectedIndex >= 0 && this.selectedIndex < list.size()) {
			this.buildEditor((WatermarkSlot)list.get(this.selectedIndex));
		}

		int backW = 120;
		this.method_37063(
			class_4185.method_46430(class_2561.method_43471("screen.recordable.watermark.back"), button -> this.method_25419())
				.method_46434((this.field_22789 - backW) / 2, this.panelBottom - 28, backW, 20)
				.method_46431()
		);
	}

	private void buildEditor(WatermarkSlot slot) {
		RecordableConfig config = RecordableConfig.get();
		int y = this.listTop;
		int w = this.editWidth;
		this.method_37063(CycleButton.create(this.editLeft, y, w, 18, class_2561.method_43470("Type: " + slot.kind), button -> {
			slot.kind = slot.kind == Kind.TEXT ? Kind.IMAGE : Kind.TEXT;
			config.save();
			this.rebuildWidgets();
		}, button -> {
			slot.kind = slot.kind == Kind.TEXT ? Kind.IMAGE : Kind.TEXT;
			config.save();
			this.rebuildWidgets();
		}));
		y += 22;
		if (slot.kind == Kind.TEXT) {
			this.textField = new class_342(this.field_22793, this.editLeft, y, w, 18, class_2561.method_43470("Text"));
			this.textField.method_1880(256);
			this.textField.method_1852(slot.text);
			this.textField.method_1863(value -> {
				slot.text = value;
				config.save();
			});
			this.method_37063(this.textField);
		} else {
			this.textField = null;
			String fileLabel = slot.imagePath != null && !slot.imagePath.isBlank() ? "Image: " + truncate(slot.imagePath, 22) : "Browse… (no image)";
			this.method_37063(
				class_4185.method_46430(class_2561.method_43470(fileLabel), button -> this.openImagePicker(slot)).method_46434(this.editLeft, y, w, 18).method_46431()
			);
		}

		y += 22;
		if (slot.kind == Kind.TEXT) {
			List<String> colors = slot.textColors;
			if (colors == null || colors.isEmpty()) {
				colors = new ArrayList();
				colors.add(slot.textColor != null && !slot.textColor.isBlank() ? slot.textColor : "#FFFFFFFF");
				slot.textColors = colors;
			}

			if (this.gradientStopIndex >= colors.size()) {
				this.gradientStopIndex = colors.size() - 1;
			}

			if (this.gradientStopIndex < 0) {
				this.gradientStopIndex = 0;
			}

			List<String> stops = colors;
			int stopIdx = this.gradientStopIndex;
			this.hexField = new class_342(this.field_22793, this.editLeft, y, w, 18, class_2561.method_43470("Hex"));
			this.hexField.method_1880(9);
			this.hexField.method_1852((String)stops.get(stopIdx));
			this.hexField.method_1863(value -> {
				String norm = normalizeHex(value);
				if (norm != null) {
					stops.set(stopIdx, norm);
					slot.textColor = (String)stops.get(0);
					config.save();
				}
			});
			this.method_37063(this.hexField);
			y += 22;
			int third = (w - 8) / 3;
			this.method_37063(class_4185.method_46430(class_2561.method_43470("Stop " + (stopIdx + 1) + "/" + stops.size()), button -> {
				this.gradientStopIndex = (stopIdx + 1) % stops.size();
				this.rebuildWidgets();
			}).method_46434(this.editLeft, y, third, 18).method_46431());
			class_4185 addColor = class_4185.method_46430(class_2561.method_43470("+ Color"), button -> {
				if (stops.size() < 10) {
					stops.add((String)stops.get(stops.size() - 1));
					this.gradientStopIndex = stops.size() - 1;
					slot.textColor = (String)stops.get(0);
					config.save();
					this.rebuildWidgets();
				}
			}).method_46434(this.editLeft + third + 4, y, third, 18).method_46431();
			addColor.field_22763 = stops.size() < 10;
			this.method_37063(addColor);
			class_4185 delColor = class_4185.method_46430(class_2561.method_43470("- Color"), button -> {
				if (stops.size() > 1) {
					stops.remove(stopIdx);
					if (this.gradientStopIndex >= stops.size()) {
						this.gradientStopIndex = stops.size() - 1;
					}

					slot.textColor = (String)stops.get(0);
					config.save();
					this.rebuildWidgets();
				}
			}).method_46434(this.editLeft + 2 * (third + 4), y, third, 18).method_46431();
			delColor.field_22763 = stops.size() > 1;
			this.method_37063(delColor);
			y += 22;
		}

		this.method_37063(CycleButton.create(this.editLeft, y, w, 18, class_2561.method_43470("Pos: " + slot.position), button -> {
			Position[] vals = Position.values();
			slot.position = vals[(slot.position.ordinal() + 1) % vals.length];
			config.save();
			this.rebuildWidgets();
		}, button -> {
			Position[] vals = Position.values();
			slot.position = vals[(slot.position.ordinal() - 1 + vals.length) % vals.length];
			config.save();
			this.rebuildWidgets();
		}));
		y += 22;
		this.method_37063(CycleButton.create(this.editLeft, y, w, 18, class_2561.method_43470("Anim: " + slot.animation), button -> {
			Animation[] vals = Animation.values();
			slot.animation = vals[(slot.animation.ordinal() + 1) % vals.length];
			config.save();
			this.rebuildWidgets();
		}, button -> {
			Animation[] vals = Animation.values();
			slot.animation = vals[(slot.animation.ordinal() - 1 + vals.length) % vals.length];
			config.save();
			this.rebuildWidgets();
		}));
		y += 22;
		this.method_37063(new WatermarkScreen.IntSlider(this.editLeft, y, w, 18, "Opacity", slot.opacity, 0, 100, v -> {
			slot.opacity = v;
			config.save();
		}));
		y += 22;
		this.method_37063(new WatermarkScreen.IntSlider(this.editLeft, y, w, 18, "Scale", slot.scale, 10, 400, v -> {
			slot.scale = v;
			config.save();
		}));
		y += 22;
		this.method_37063(new WatermarkScreen.IntSlider(this.editLeft, y, w, 18, "Rotation", slot.rotation, -180, 180, v -> {
			slot.rotation = v;
			config.save();
		}));
	}

	public void method_25394(class_332 context, int mouseX, int mouseY, float delta) {
		this.method_25420(context, mouseX, mouseY, delta);
		int accent = 0xFF000000 | RecordableConfig.get().getMenuAccentColorRgb();
		int left = this.panelLeft - 6;
		int right = this.panelLeft + this.panelWidth + 6;
		context.method_25294(left, this.panelTop - 6, right, this.panelBottom, -804253680);
		context.method_25294(left, this.panelTop - 6, right, this.panelTop - 5, accent);
		context.method_25294(left, this.panelBottom - 1, right, this.panelBottom, -12434878);
		context.method_25294(left, this.panelTop - 6, left + 1, this.panelBottom, -12434878);
		context.method_25294(right - 1, this.panelTop - 6, right, this.panelBottom, -12434878);
		context.method_27534(this.field_22793, this.field_22785, this.field_22789 / 2, this.panelTop, -1);
		RenderHelper.drawText(context, this.field_22793, class_2561.method_43470("§lSlots (" + this.slots().size() + "/4)"), this.listLeft, this.listTop - 12, -1);
		if (this.selectedIndex >= 0 && this.selectedIndex < this.slots().size()) {
			RenderHelper.drawText(
				context,
				this.field_22793,
				class_2561.method_43470("§lEdit: " + truncate(((WatermarkSlot)this.slots().get(this.selectedIndex)).name, 18)),
				this.editLeft,
				this.listTop - 12,
				-1
			);
		} else {
			RenderHelper.drawText(context, this.field_22793, class_2561.method_43470("Select a slot to edit"), this.editLeft, this.listTop - 12, -3092272);
		}

		if (this.slots().isEmpty()) {
			RenderHelper.drawText(context, this.field_22793, class_2561.method_43470("No watermarks. Use + Text / + Image."), this.listLeft, this.listTop + 4, -3092272);
		}

		if (!RecordableConfig.get().watermarksEnabled) {
			context.method_27534(
				this.field_22793, class_2561.method_43470("Watermarks are OFF - enable to render on recordings."), this.field_22789 / 2, this.panelBottom - 44, -13244
			);
		} else if (!this.statusMessage.isEmpty()) {
			context.method_27534(this.field_22793, class_2561.method_43470(this.statusMessage), this.field_22789 / 2, this.panelBottom - 44, -7811960);
		}

		super.method_25394(context, mouseX, mouseY, delta);
	}

	private static String truncate(String s, int max) {
		if (s == null) {
			return "";
		} else {
			return s.length() > max ? s.substring(0, max - 1) + "…" : s;
		}
	}

	public void method_25419() {
		if (this.field_22787 != null) {
			this.field_22787.method_1507(this.parent);
		}
	}

	private static String normalizeHex(String in) {
		if (in == null) {
			return null;
		} else {
			String t = in.trim();
			if (t.startsWith("#")) {
				t = t.substring(1);
			}

			if (t.length() != 6 && t.length() != 8) {
				return null;
			} else {
				for (int i = 0; i < t.length(); i++) {
					char c = t.charAt(i);
					boolean hex = c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
					if (!hex) {
						return null;
					}
				}

				return "#" + t.toUpperCase(Locale.ROOT);
			}
		}
	}

	private static int[] parseRgb(String hex) {
		int argb = parseColorArgb(hex);
		return new int[]{argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF};
	}

	private static int parseColorArgb(String hex) {
		if (hex == null) {
			return -1;
		} else {
			String s = hex.trim();
			if (s.startsWith("#")) {
				s = s.substring(1);
			}

			try {
				if (s.length() == 6) {
					return 0xFF000000 | (int)(Long.parseLong(s, 16) & 16777215L);
				}

				if (s.length() == 8) {
					return (int)(Long.parseLong(s, 16) & 4294967295L);
				}
			} catch (NumberFormatException var3) {
			}

			return -1;
		}
	}

	private void setColorComponent(WatermarkSlot slot, int index, int value) {
		int[] rgb = parseRgb(slot.textColor);
		rgb[index] = Math.max(0, Math.min(255, value));
		slot.textColor = String.format("#%02X%02X%02X", rgb[0], rgb[1], rgb[2]);
	}

	private void openImagePicker(WatermarkSlot slot) {
		Thread t = new Thread(() -> {
			String chosen = null;

			try {
				MemoryStack stack = MemoryStack.stackPush();

				try {
					PointerBuffer filters = stack.mallocPointer(3);
					filters.put(stack.UTF8("*.png"));
					filters.put(stack.UTF8("*.jpg"));
					filters.put(stack.UTF8("*.jpeg"));
					filters.flip();
					chosen = TinyFileDialogs.tinyfd_openFileDialog("Select watermark image", "", filters, "Images (*.png, *.jpg, *.jpeg)", false);
				} catch (Throwable var7) {
					if (stack != null) {
						try {
							stack.close();
						} catch (Throwable var6) {
							var7.addSuppressed(var6);
						}
					}

					throw var7;
				}

				if (stack != null) {
					stack.close();
				}
			} catch (Throwable var8) {
				RecordableMod.LOGGER.warn("[Record-able] File picker failed: {}", var8.toString());
			}

			String result = chosen;
			class_310.method_1551().execute(() -> {
				if (result != null && !result.isBlank()) {
					String stored = WatermarkImageStore.importImage(result);
					if (stored != null) {
						slot.imagePath = stored;
						RecordableConfig.get().save();
						this.statusMessage = "Imported image: " + stored;
					} else {
						this.statusMessage = "Could not import that image.";
					}
				}

				this.rebuildWidgets();
			});
		}, "recordable-watermark-picker");
		t.setDaemon(true);
		t.start();
	}

	private static final class IntSlider extends class_357 {
		private final String label;
		private final int min;
		private final int max;
		private final IntConsumer setter;

		private IntSlider(int x, int y, int width, int height, String label, int current, int min, int max, IntConsumer setter) {
			super(x, y, width, height, class_2561.method_43473(), (double)(current - min) / (double)(max - min));
			this.label = label;
			this.min = min;
			this.max = max;
			this.setter = setter;
			this.method_25346();
		}

		private int current() {
			return (int)Math.round(this.field_22753 * (double)(this.max - this.min)) + this.min;
		}

		protected void method_25346() {
			this.method_25355(class_2561.method_43470(this.label + ": " + this.current()));
		}

		protected void method_25344() {
			this.setter.accept(this.current());
			this.method_25346();
		}
	}
}
