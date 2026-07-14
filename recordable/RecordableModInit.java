package dev.recordable;

import dev.recordable.DiskSpaceGuardian.DiskCheckResult;
import dev.recordable.DiskSpaceGuardian.DiskStatus;
import dev.recordable.FFmpegEncoder.FfmpegStatus;
import dev.recordable.ReplayCompatBridge.RecordingController;
import dev.recordable.screen.CensorOverlayEditorScreen;
import dev.recordable.screen.FfmpegWelcomeScreen;
import dev.recordable.screen.RecordableSettingsScreen;
import dev.recordable.screen.VideoCollectionScreen;
import dev.recordable.theme.ThemeEngine;
import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStopping;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.class_1269;
import net.minecraft.class_2561;
import net.minecraft.class_304;
import net.minecraft.class_310;
import net.minecraft.class_3675.class_307;

public final class RecordableModInit implements ClientModInitializer {
	private static final Set<Integer> VANILLA_RESERVED_FKEYS = Set.of(290, 291, 292, 294);
	private static final int[] FKEY_PRIORITY_ORDER = new int[]{295, 296, 297, 298, 299, 300, 301, 293};
	private static final String KEY_CATEGORY_STRING = "key.categories.recordable.main";
	private static final int KB_TOGGLE = 0;
	private static final int KB_PAUSE = 1;
	private static final int KB_SETTINGS = 2;
	private static final int KB_VIDEOS = 3;
	private static final int KB_BOOKMARK = 4;
	private static final int KB_PTT = 5;
	private static final int KB_SAVE_REPLAY = 6;
	private static final int KB_TOGGLE_CENSOR = 7;
	private static final int KB_OPEN_CENSOR_EDITOR = 8;
	private static final class_304[] keyBindings = new class_304[9];
	private static AutoRecordManager autoRecordManager;
	private static AutoClipManager autoClipManager;
	private static boolean ffmpegWelcomeShown = false;

	public void onInitializeClient() {
		RecordableMod.setMessageSender((client, message, actionBar) -> sendClientMessage((class_310)client, message, actionBar));
		ReplayCompatBridge.setRecordingController(new RecordingController() {
			@Override
			public boolean isRecording() {
				return RecordingManager.getInstance().isRecording();
			}

			@Override
			public void startRecording(Object client) {
				RecordingManager.getInstance().startRecording((class_310)client, "replay");
			}

			@Override
			public void stopRecording(Object client) {
				RecordingManager.getInstance().stopRecording((class_310)client);
			}
		});
		RecordableConfig config = RecordableConfig.load();
		if (config.migrateOldConfig()) {
			config.save();
		}

		try {
			FfmpegBundleManager.runDiagnostics();
		} catch (Exception var5) {
			RecordableMod.LOGGER.warn("[RecordableMod] FFmpeg diagnostics failed: {}", var5.getMessage());
		}

		ThemeEngine.get().loadFromConfig();
		RecordingManager.getInstance().initialize();
		autoRecordManager = new AutoRecordManager();
		autoRecordManager.initialize();
		Set<Integer> claimedKeys = collectClaimedKeyBindings();
		keyBindings[0] = registerKeyBinding(
			createKeyBinding("key.recordable.toggle_recording", class_307.field_1668, resolveHotkeyDefault(config.hotkeyToggleRecording, 45, claimedKeys))
		);
		keyBindings[1] = registerKeyBinding(
			createKeyBinding("key.recordable.pause_resume", class_307.field_1668, resolveHotkeyDefault(config.hotkeyPauseResume, 61, claimedKeys))
		);
		keyBindings[2] = registerKeyBinding(
			createKeyBinding("key.recordable.open_settings", class_307.field_1668, resolveHotkeyDefault(config.hotkeyOpenSettings, 298, claimedKeys))
		);
		keyBindings[3] = registerKeyBinding(
			createKeyBinding("key.recordable.open_video_collection", class_307.field_1668, resolveHotkeyDefault(config.hotkeyOpenVideoCollection, 301, claimedKeys))
		);
		keyBindings[4] = registerKeyBinding(createKeyBinding("key.recordable.add_bookmark", class_307.field_1668, -1));
		keyBindings[5] = registerKeyBinding(
			createKeyBinding("key.recordable.push_to_talk", class_307.field_1668, resolveHotkeyDefault(config.hotkeyPushToTalk, 86, claimedKeys))
		);
		keyBindings[6] = registerKeyBinding(createKeyBinding("key.recordable.save_replay_buffer", class_307.field_1668, -1));
		keyBindings[7] = registerKeyBinding(createKeyBinding("key.recordable.toggle_censor_overlay", class_307.field_1668, config.hotkeyToggleCensorOverlay));
		keyBindings[8] = registerKeyBinding(createKeyBinding("key.recordable.open_censor_editor", class_307.field_1668, config.hotkeyOpenCensorEditor));
		autoClipManager = new AutoClipManager();
		autoClipManager.initialize();
		RecordableMod.setAutoClipManager(autoClipManager);
		AttackEntityCallback.EVENT.register((AttackEntityCallback)(player, world, hand, entity, hitResult) -> {
			try {
				class_310 client = class_310.method_1551();
				if (autoClipManager != null && client != null && client.field_1724 != null && player == client.field_1724) {
					autoClipManager.onPlayerAttackEntity(entity);
				}
			} catch (Throwable var6) {
				RecordableMod.LOGGER.debug("Failed to process attack for auto-clip kill tracking.", var6);
			}

			return class_1269.field_5811;
		});
		ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
			if (!ffmpegWelcomeShown && client != null && client.field_1755 == null) {
				RecordableConfig cfg = RecordableConfig.get();
				if (cfg != null && !cfg.ffmpegFirstRunShown) {
					FfmpegStatus status = FFmpegEncoder.detectFfmpeg();
					if (!status.found()) {
						ffmpegWelcomeShown = true;
						client.method_1507(new FfmpegWelcomeScreen(null));
						return;
					}

					cfg.ffmpegFirstRunShown = true;
					cfg.save();
					ffmpegWelcomeShown = true;
				}
			}

			while (kbWasPressed(0)) {
				if (RecordingManager.isInGameState(client)) {
					if (!RecordingManager.getInstance().isRecording()) {
						ModCompatibilityChecker.warnPlayerIfConflicts(client);
						DiskCheckResult diskCheck = DiskSpaceGuardian.check(RecordableConfig.get().getOutputDirectory(), RecordableConfig.get());
						if (diskCheck.status() == DiskStatus.BLOCKED) {
							RecordableMod.sendClientMessage(ChatCategory.WARNINGS, client, diskCheck.message(), false);
							continue;
						}

						if (diskCheck.status() == DiskStatus.WARNING) {
							RecordableMod.sendClientMessage(ChatCategory.WARNINGS, client, diskCheck.message(), false);
						}
					}

					RecordingManager.getInstance().toggleRecording(client);
				}
			}

			while (kbWasPressed(1)) {
				if (RecordingManager.getInstance().isRecording() || RecordingManager.getInstance().isPaused()) {
					RecordingManager.getInstance().togglePause(client);
				}
			}

			while (kbWasPressed(2)) {
				if (client != null && !(client.field_1755 instanceof RecordableSettingsScreen)) {
					client.method_1507(new RecordableSettingsScreen(client.field_1755));
				}
			}

			while (kbWasPressed(3)) {
				if (client != null && !(client.field_1755 instanceof VideoCollectionScreen)) {
					client.method_1507(new VideoCollectionScreen(client.field_1755));
				}
			}

			while (kbWasPressed(4)) {
				if (RecordableConfig.get().bookmarksEnabled && RecordingManager.getInstance().isRecording()) {
					String desc = RecordingManager.getInstance().addBookmark();
					if (desc != null) {
						long ts = RecordingManager.getInstance().getEffectiveRecordingMillis();
						RecordableMod.sendClientMessage(ChatCategory.BOOKMARKS, client, "§a\ud83d\udd16 " + desc + " §7at " + RecordingManager.formatDuration(ts), true);
					}
				}
			}

			while (kbWasPressed(6)) {
				if (RecordableConfig.get().replayBufferEnabled && ReplayBuffer.getInstance().isActive()) {
					ReplayBuffer.getInstance().saveBuffer(client);
				} else {
					RecordableMod.sendClientMessage(ChatCategory.REPLAY_BUFFER, client, "§e⚠ Replay Buffer is not active. Enable it in Record-able settings.", true);
				}
			}

			while (kbWasPressed(7)) {
				toggleCensorOverlay(client);
			}

			while (kbWasPressed(8)) {
				openCensorEditor(client);
			}

			class_304 pttKb = keyBindings[5];
			MicrophoneState.setPushToTalkHeld(pttKb != null && pttKb.method_1434());
			if (autoClipManager != null) {
				autoClipManager.onClientTick(client);
			}

			if (autoRecordManager != null) {
				autoRecordManager.onClientTick(client);
			}

			RecordingManager.getInstance().onClientTick(client);
			ReplayCompatBridge.onClientTick(client, ModCompatibilityChecker.detectReplayPlayback(client));
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)client -> {
			if (autoRecordManager != null) {
				autoRecordManager.onClientStopping(client);
			}

			ReplayBuffer.getInstance().stop();
			RecordingManager.getInstance().shutdown();
		});
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				RecordingManager rm = RecordingManager.getInstance();
				if (rm.isActiveOrStopping()) {
					RecordableMod.LOGGER.info("JVM shutdown detected while recording. Finalizing...");
					Thread worker = new Thread(() -> {
						try {
							rm.forceShutdown();
						} catch (Throwable var2xx) {
							RecordableMod.LOGGER.warn("forceShutdown failed.", var2xx);
						}
					}, "Record-able Shutdown Worker");
					worker.setDaemon(true);
					worker.start();
					worker.join(45000L);
					if (worker.isAlive()) {
						RecordableMod.LOGGER.warn("Recording finalization timed out after 45 s.");
					}
				}
			} catch (Throwable var2x) {
				RecordableMod.LOGGER.warn("Failed to finalize recording during JVM shutdown.", var2x);
			}
		}, "Record-able Shutdown Hook"));
		RecordingOverlay.register();
		PerformanceMetrics.getInstance();
		RecordableMod.LOGGER
			.info("Record-able audio sync offset: {}ms (preset: {})", RecordableConfig.get().getEffectiveAudioDelay(), RecordableConfig.get().audioDelayPreset);
		ModCompatibilityChecker.checkAndLog();
		RecordableMod.LOGGER.info("Record-able MultiVersion: {}", VersionHelper.getVersionInfo());

		try {
			String freeSpace = DiskSpaceGuardian.getFormattedFreeSpace(config.getOutputDirectory());
			RecordableMod.LOGGER.info("Record-able initialized. Recording enabled: {}. Free disk space: {}", config.enabled, freeSpace);
		} catch (Exception var4) {
			RecordableMod.LOGGER.info("Record-able initialized. Recording enabled: {}", config.enabled);
		}
	}

	private static class_304 registerKeyBinding(class_304 keyBinding) {
		return KeyBindingHelper.registerKeyBinding(keyBinding);
	}

	private static boolean kbWasPressed(int index) {
		class_304 kb = keyBindings[index];
		return kb != null && kb.method_1436();
	}

	private static void toggleCensorOverlay(class_310 client) {
		RecordableConfig config = RecordableConfig.get();
		if (config != null) {
			config.censorOverlayHidden = !config.censorOverlayHidden;
			config.save();
			String msg = config.censorOverlayHidden ? "§eCensor overlay hidden" : "§aCensor overlay shown";
			RecordableMod.sendClientMessage(ChatCategory.GENERAL, client, msg, true);
		}
	}

	private static void openCensorEditor(class_310 client) {
		if (client != null) {
			if (!(client.field_1755 instanceof CensorOverlayEditorScreen)) {
				client.method_1507(new CensorOverlayEditorScreen(client.field_1755));
			}
		}
	}

	public static String getBoundKeyDisplay(RecordableModInit.Hotkey hotkey) {
		if (hotkey == null) {
			return "-";
		} else {
			class_304 kb = keyBindings[hotkey.index];
			if (kb == null) {
				return "-";
			} else {
				return kb.method_1415() ? "Not Bound" : kb.method_16007().getString();
			}
		}
	}

	private static class_304 createKeyBinding(String translationKey, class_307 type, int code) {
		return new class_304(translationKey, type, code, "key.categories.recordable.main");
	}

	private static Set<Integer> collectClaimedKeyBindings() {
		Set<Integer> claimed = new LinkedHashSet(VANILLA_RESERVED_FKEYS);

		try {
			class_310 mc = class_310.method_1551();
			if (mc != null && mc.field_1690 != null) {
				for (class_304 kb : mc.field_1690.field_1839) {
					int code = KeyBindingHelper.getBoundKeyOf(kb).method_1444();
					if (code != -1) {
						claimed.add(code);
					}
				}
			}
		} catch (Throwable var7) {
			RecordableMod.LOGGER.debug("Could not read existing key bindings for auto-assignment.", var7);
		}

		return claimed;
	}

	private static int findAvailableFKey(Set<Integer> claimed) {
		for (int fkey : FKEY_PRIORITY_ORDER) {
			if (!claimed.contains(fkey)) {
				return fkey;
			}
		}

		return -1;
	}

	private static int resolveHotkeyDefault(int configValue, int compileDefault, Set<Integer> claimed) {
		if (configValue == -1 && compileDefault == -1) {
			return -1;
		} else if (configValue != compileDefault) {
			if (configValue != -1) {
				claimed.add(configValue);
			}

			return configValue;
		} else if (compileDefault != -1 && !claimed.contains(compileDefault)) {
			claimed.add(compileDefault);
			return compileDefault;
		} else {
			int assigned = findAvailableFKey(claimed);
			if (assigned != -1) {
				claimed.add(assigned);
				RecordableMod.LOGGER
					.info(
						"Auto-assigned F-key {} for hotkey (default {} was {})",
						new Object[]{glfwKeyName(assigned), glfwKeyName(compileDefault), compileDefault == -1 ? "unbound" : "taken"}
					);
			}

			return assigned;
		}
	}

	private static String glfwKeyName(int keyCode) {
		if (keyCode == -1) {
			return "UNBOUND";
		} else {
			return keyCode >= 290 && keyCode <= 301 ? "F" + (keyCode - 290 + 1) : "key=" + keyCode;
		}
	}

	public static void sendClientMessage(class_310 client, String message, boolean actionBar) {
		if (message != null && !message.isBlank()) {
			try {
				class_310 mc = client == null ? class_310.method_1551() : client;
				if (mc != null && !mc.method_18854()) {
					mc.execute(() -> sendClientMessage(mc, message, actionBar));
					return;
				}

				if (mc != null && mc.field_1724 != null) {
					mc.field_1724.method_7353(class_2561.method_43470(message), actionBar);
				}
			} catch (Throwable var4) {
				RecordableMod.LOGGER.warn("Failed to display Record-able client message: {}", message, var4);
			}
		}
	}

	public static enum Hotkey {
		TOGGLE_RECORDING(0),
		PAUSE_RESUME(1),
		OPEN_SETTINGS(2),
		OPEN_VIDEOS(3),
		ADD_BOOKMARK(4),
		PUSH_TO_TALK(5),
		SAVE_REPLAY_BUFFER(6),
		TOGGLE_CENSOR_OVERLAY(7),
		OPEN_CENSOR_EDITOR(8);

		private final int index;

		private Hotkey(int index) {
			this.index = index;
		}
	}
}
