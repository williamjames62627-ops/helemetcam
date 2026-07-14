package dev.recordable;

import net.fabricmc.loader.api.FabricLoader;

public final class ReplayCompatBridge {
	private static final String[] REPLAY_MOD_IDS = new String[]{"flashback", "replaymod", "replay-mod"};
	private static final String[] REPLAY_CLASS_HINTS = new String[]{"flashback", "replaymod"};
	private static volatile ReplayCompatBridge.RecordingController controller;
	private static volatile Boolean replayModPresent;
	private static volatile boolean playbackActiveLast = false;
	private static volatile boolean recordingStartedByBridge = false;

	private ReplayCompatBridge() {
	}

	public static void setRecordingController(ReplayCompatBridge.RecordingController c) {
		controller = c;
	}

	public static boolean isReplayModPresent() {
		Boolean cached = replayModPresent;
		if (cached != null) {
			return cached;
		} else {
			boolean present = false;

			try {
				FabricLoader loader = FabricLoader.getInstance();

				for (String id : REPLAY_MOD_IDS) {
					if (loader.isModLoaded(id)) {
						present = true;
						break;
					}
				}
			} catch (Throwable var7) {
			}

			replayModPresent = present;
			return present;
		}
	}

	public static String getPresentReplayModId() {
		try {
			FabricLoader loader = FabricLoader.getInstance();

			for (String id : REPLAY_MOD_IDS) {
				if (loader.isModLoaded(id)) {
					return id;
				}
			}
		} catch (Throwable var5) {
		}

		return null;
	}

	public static String getPresentReplayModName() {
		String id = getPresentReplayModId();
		if (id == null) {
			return "another recording mod";
		} else {
			switch (id) {
				case "flashback":
					return "Flashback";
				case "replaymod":
				case "replay-mod":
					return "Replay Mod";
				default:
					return id;
			}
		}
	}

	public static boolean shouldYieldAudioDevice() {
		try {
			RecordableConfig config = RecordableConfig.get();
			return config != null && config.replayCompatBridge && config.replayYieldAudioDevice ? isReplayModPresent() : false;
		} catch (Throwable var1) {
			return false;
		}
	}

	public static String getCoexistenceMessage() {
		String name = getPresentReplayModName();
		boolean yield = false;

		try {
			RecordableConfig config = RecordableConfig.get();
			yield = config != null && config.replayYieldAudioDevice;
		} catch (Throwable var3) {
		}

		return yield
			? "§a[Record-able] §7Compatibility mode active with "
				+ name
				+ ": yielding game-audio capture to it (Record-able uses system audio); replay playback can auto-record."
			: "§a[Record-able] §7Compatibility mode active with " + name + ": replay playback can auto-record. Record-able keeps its own game audio.";
	}

	public static boolean isReplayClass(Object o) {
		if (o == null) {
			return false;
		} else {
			try {
				for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
					String name = c.getName().toLowerCase();

					for (String hint : REPLAY_CLASS_HINTS) {
						if (name.contains(hint)) {
							return true;
						}
					}
				}
			} catch (Throwable var7) {
			}

			return false;
		}
	}

	public static void onClientTick(Object client, boolean playbackActive) {
		try {
			RecordableConfig config = RecordableConfig.get();
			if (config == null || !config.replayCompatBridge || !config.replayAutoRecordPlayback) {
				playbackActiveLast = playbackActive;
				return;
			}

			ReplayCompatBridge.RecordingController ctrl = controller;
			if (ctrl == null) {
				return;
			}

			if (playbackActive && !playbackActiveLast) {
				if (!ctrl.isRecording()) {
					ctrl.startRecording(client);
					recordingStartedByBridge = true;
					RecordableMod.sendClientMessage(
						ChatCategory.RECORDING, client, "§a● Auto-recording " + getPresentReplayModName() + " playback (Record-able compatibility bridge).", true
					);
				}
			} else if (!playbackActive && playbackActiveLast) {
				if (recordingStartedByBridge && ctrl.isRecording()) {
					ctrl.stopRecording(client);
				}

				recordingStartedByBridge = false;
			}

			playbackActiveLast = playbackActive;
		} catch (Throwable var4) {
			RecordableMod.LOGGER.debug("ReplayCompatBridge tick failed", var4);
		}
	}

	public interface RecordingController {
		boolean isRecording();

		void startRecording(Object object);

		void stopRecording(Object object);
	}
}
