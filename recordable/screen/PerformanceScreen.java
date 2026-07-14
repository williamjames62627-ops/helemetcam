package dev.recordable.screen;

import dev.recordable.RecordableConfig;
import dev.recordable.RecordableMod;
import dev.recordable.SmoothMotion;
import dev.recordable.theme.CycleButton;
import dev.recordable.theme.ThemedButton;
import dev.recordable.theme.ThemedPanel;
import dev.recordable.theme.ThemedToggle;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_4185;
import net.minecraft.class_437;
import net.minecraft.class_7919;

public final class PerformanceScreen extends class_437 {
	private static final int WIDGET_HEIGHT = 20;
	private static final int ROW_SPACING = 24;
	private static final int PANEL_W = 340;
	private static final int[] MIN_FPS_VALUES = new int[]{30, 45, 60, 90, 120};
	private final class_437 parent;
	private int panelX;
	private int panelY;
	private int panelBottom;

	public PerformanceScreen(class_437 parent) {
		super(class_2561.method_43470("Performance"));
		this.parent = parent;
	}

	protected void method_25426() {
		super.method_25426();
		RecordableConfig config = RecordableConfig.get();
		if (config == null) {
			this.method_25419();
		} else {
			config.selectedDevicePreset = RecordableConfig.sanitizeDevicePreset(config.selectedDevicePreset);
			this.panelX = (this.field_22789 - 340) / 2;
			this.panelY = 30;
			this.panelBottom = this.field_22790 - 20;
			int innerW = 316;
			int gap = 8;
			int halfW = (innerW - gap) / 2;
			int colL = this.panelX + 12;
			int colR = colL + halfW + gap;
			int y = this.panelY + 34;
			CycleButton presetButton = CycleButton.create(
				colL, y, halfW, 20, class_2561.method_43470("Preset: " + RecordableConfig.getDevicePresetDisplayName(config.selectedDevicePreset)), b -> {
					config.selectedDevicePreset = nextPreset(config.selectedDevicePreset);
					config.save();
					b.method_25355(class_2561.method_43470("Preset: " + RecordableConfig.getDevicePresetDisplayName(config.selectedDevicePreset)));
				}, b -> {
					config.selectedDevicePreset = prevPreset(config.selectedDevicePreset);
					config.save();
					b.method_25355(class_2561.method_43470("Preset: " + RecordableConfig.getDevicePresetDisplayName(config.selectedDevicePreset)));
				}
			);
			presetButton.method_47400(
				tip(
					"Pick a one-click quality profile tuned for your device class (low-end, balanced, high-end and more). Left-click cycles forward, right-click goes back. Nothing is changed until you press Apply Preset."
				)
			);
			this.method_37063(presetButton);
			class_4185 applyPresetButton = ThemedButton.create(colR, y, halfW, 20, class_2561.method_43470("Apply Preset"), b -> {
				try {
					config.applyDevicePreset(config.selectedDevicePreset);
					config.save();
				} catch (Throwable var4x) {
					RecordableMod.LOGGER.warn("Failed to apply device preset {}.", config.selectedDevicePreset, var4x);
				}

				if (this.field_22787 != null) {
					this.field_22787.method_1507(new PerformanceScreen(this.parent));
				}
			});
			applyPresetButton.method_47400(
				tip(
					"Applies the selected device preset right now, overwriting your resolution, FPS, quality and related recording settings with values tuned for that device."
				)
			);
			this.method_37063(applyPresetButton);
			y += 24;
			class_4185 smoothMotionToggle = ThemedToggle.create(colL, y, halfW, 20, "Smooth Motion", config.smoothMotionEnabled, v -> {
				config.smoothMotionEnabled = v;
				config.save();
			});
			smoothMotionToggle.method_47400(
				tip("Adds frame blending / motion blur to recordings so fast movement looks smoother and more cinematic. Costs a little extra processing while recording.")
			);
			this.method_37063(smoothMotionToggle);
			CycleButton motionModeButton = CycleButton.create(
				colR, y, halfW, 20, class_2561.method_43470("Motion: " + SmoothMotion.describe(config.smoothMotionMode)), b -> {
					config.smoothMotionMode = nextMotionMode(config.smoothMotionMode);
					config.save();
					b.method_25355(class_2561.method_43470("Motion: " + SmoothMotion.describe(config.smoothMotionMode)));
				}, b -> {
					config.smoothMotionMode = nextMotionMode(config.smoothMotionMode);
					config.save();
					b.method_25355(class_2561.method_43470("Motion: " + SmoothMotion.describe(config.smoothMotionMode)));
				}
			);
			motionModeButton.method_47400(
				tip("Chooses how Smooth Motion is produced: Blend mixes nearby frames together, Motion estimates movement between frames. Left or right-click to switch.")
			);
			this.method_37063(motionModeButton);
			y += 24;
			class_4185 framePoolingToggle = ThemedToggle.create(colL, y, halfW, 20, "Frame Pooling", config.frameBufferPoolingEnabled, v -> {
				config.frameBufferPoolingEnabled = v;
				config.save();
			});
			framePoolingToggle.method_47400(
				tip("Reuses frame memory buffers instead of allocating new ones every frame. Reduces stutter and garbage-collection lag during long recordings.")
			);
			this.method_37063(framePoolingToggle);
			class_4185 perfOptimizerToggle = ThemedToggle.create(colR, y, halfW, 20, "Perf Optimizer", config.perfOptimizerEnabled, v -> {
				config.perfOptimizerEnabled = v;
				config.save();
			});
			perfOptimizerToggle.method_47400(
				tip("Automatically lowers recording quality on the fly when your game FPS drops too low, so gameplay stays smooth. Works together with the Min FPS target.")
			);
			this.method_37063(perfOptimizerToggle);
			y += 24;
			class_4185 autoAdjustToggle = ThemedToggle.create(colL, y, halfW, 20, "Auto Adjust", config.perfAutoAdjust, v -> {
				config.perfAutoAdjust = v;
				config.save();
			});
			autoAdjustToggle.method_47400(
				tip("Lets the Performance Optimizer actually change settings by itself. When off, the optimizer only watches and warns but will not modify anything.")
			);
			this.method_37063(autoAdjustToggle);
			class_4185 optimizerOverlayToggle = ThemedToggle.create(colR, y, halfW, 20, "Optimizer Overlay", config.perfShowStatsOverlay, v -> {
				config.perfShowStatsOverlay = v;
				config.save();
			});
			optimizerOverlayToggle.method_47400(
				tip(
					"Shows a small live diagnostic overlay with the optimizer's current decisions and FPS readings. Handy for tuning; it is only baked into recordings if Bake in Overlay is on."
				)
			);
			this.method_37063(optimizerOverlayToggle);
			y += 24;
			class_4185 perfStatsHudToggle = ThemedToggle.create(colL, y, halfW, 20, "Perf Stats HUD", config.showPerformanceStats, v -> {
				config.showPerformanceStats = v;
				config.save();
			});
			perfStatsHudToggle.method_47400(
				tip("Shows an on-screen performance readout (FPS, frame time, dropped frames) while recording. This is separate from the main recording info overlay.")
			);
			this.method_37063(perfStatsHudToggle);
			CycleButton minFpsButton = CycleButton.create(colR, y, halfW, 20, class_2561.method_43470("Min FPS: " + config.perfMinFps), b -> {
				config.perfMinFps = nextMinFps(config.perfMinFps);
				config.save();
				b.method_25355(class_2561.method_43470("Min FPS: " + config.perfMinFps));
			}, b -> {
				config.perfMinFps = prevMinFps(config.perfMinFps);
				config.save();
				b.method_25355(class_2561.method_43470("Min FPS: " + config.perfMinFps));
			});
			minFpsButton.method_47400(
				tip(
					"The target FPS the Performance Optimizer tries to protect. If your game FPS falls below this, the optimizer lowers recording quality to recover. Left or right-click to change."
				)
			);
			this.method_37063(minFpsButton);
			y += 24;
			this.method_37063(
				ThemedButton.create((this.field_22789 - 120) / 2, this.panelBottom - 26, 120, 20, class_2561.method_43470("Done"), b -> this.method_25419())
			);
		}
	}

	private static class_7919 tip(String s) {
		return class_7919.method_47407(class_2561.method_43470(s));
	}

	private static String nextPreset(String current) {
		String[] all = RecordableConfig.DEVICE_PRESETS;

		for (int i = 0; i < all.length; i++) {
			if (all[i].equals(current)) {
				return all[(i + 1) % all.length];
			}
		}

		return all.length > 0 ? all[0] : current;
	}

	private static String prevPreset(String current) {
		String[] all = RecordableConfig.DEVICE_PRESETS;

		for (int i = 0; i < all.length; i++) {
			if (all[i].equals(current)) {
				return all[(i - 1 + all.length) % all.length];
			}
		}

		return all.length > 0 ? all[0] : current;
	}

	private static int nextMinFps(int current) {
		for (int i = 0; i < MIN_FPS_VALUES.length; i++) {
			if (MIN_FPS_VALUES[i] == current) {
				return MIN_FPS_VALUES[(i + 1) % MIN_FPS_VALUES.length];
			}
		}

		return MIN_FPS_VALUES[0];
	}

	private static int prevMinFps(int current) {
		for (int i = 0; i < MIN_FPS_VALUES.length; i++) {
			if (MIN_FPS_VALUES[i] == current) {
				return MIN_FPS_VALUES[(i - 1 + MIN_FPS_VALUES.length) % MIN_FPS_VALUES.length];
			}
		}

		return MIN_FPS_VALUES[0];
	}

	private static String nextMotionMode(String mode) {
		return "blend".equals(SmoothMotion.sanitizeMode(mode)) ? "motion" : "blend";
	}

	public void method_25420(class_332 context, int mouseX, int mouseY, float delta) {
		super.method_25420(context, mouseX, mouseY, delta);
		context.method_51452();
		ThemedPanel.drawPanel(context, this.panelX, this.panelY, this.panelX + 340, this.panelBottom);
		context.method_27534(this.field_22793, this.field_22785, this.field_22789 / 2, this.panelY + 12, -1);
		context.method_27534(
			this.field_22793, class_2561.method_43470("All performance options live here."), this.field_22789 / 2, this.panelY + 34 + 96 + 20 + 14, -5197648
		);
	}

	public void method_25394(class_332 context, int mouseX, int mouseY, float delta) {
		super.method_25394(context, mouseX, mouseY, delta);
	}

	public void method_25419() {
		if (this.field_22787 != null) {
			this.field_22787.method_1507(this.parent);
		}
	}
}
