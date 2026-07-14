package dev.recordable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_2561;
import net.minecraft.class_310;

public final class ModCompatibilityChecker {
	private static final Map<String, String> KNOWN_CONFLICTS;
	private static List<String> detectedConflicts;

	private ModCompatibilityChecker() {
	}

	public static List<String> checkAndLog() {
		List<String> conflicts = new ArrayList();
		FabricLoader loader = FabricLoader.getInstance();

		for (Entry<String, String> entry : KNOWN_CONFLICTS.entrySet()) {
			if (loader.isModLoaded((String)entry.getKey())) {
				String msg = "[Record-able] Mod conflict detected: '" + (String)entry.getKey() + "' - " + (String)entry.getValue();
				RecordableMod.LOGGER.warn(msg);
				conflicts.add(msg);
			}
		}

		if (conflicts.isEmpty()) {
			RecordableMod.LOGGER.info("[Record-able] No known mod conflicts detected.");
		} else {
			RecordableMod.LOGGER.warn("[Record-able] {} conflicting mod(s) detected. Recording may be unstable - see warnings above.", conflicts.size());
		}

		detectedConflicts = Collections.unmodifiableList(conflicts);
		return detectedConflicts;
	}

	public static boolean hasConflicts() {
		return detectedConflicts != null && !detectedConflicts.isEmpty();
	}

	public static List<String> getDetectedConflicts() {
		return detectedConflicts != null ? detectedConflicts : Collections.emptyList();
	}

	public static void warnPlayerIfConflicts(class_310 client) {
		if (hasConflicts() && client != null && client.field_1724 != null) {
			if (RecordableConfig.get().notifyWarnings) {
				boolean bridge = false;

				try {
					bridge = RecordableConfig.get().replayCompatBridge;
				} catch (Throwable var7) {
				}

				List<String> hardConflicts = new ArrayList();
				boolean replayCoexisting = false;

				for (String conflict : detectedConflicts) {
					if (bridge && isReplayConflict(conflict)) {
						replayCoexisting = true;
					} else {
						hardConflicts.add(conflict);
					}
				}

				if (replayCoexisting) {
					client.field_1724.method_7353(class_2561.method_43470(ReplayCompatBridge.getCoexistenceMessage()), false);
				}

				if (!hardConflicts.isEmpty()) {
					client.field_1724.method_7353(class_2561.method_43470("§e[Record-able] §cWarning: conflicting mod(s) detected!"), false);

					for (String conflictx : hardConflicts) {
						String brief = conflictx.replace("[Record-able] Mod conflict detected: ", "");
						client.field_1724.method_7353(class_2561.method_43470("§7  • " + brief), false);
					}

					client.field_1724
						.method_7353(class_2561.method_43470("§eRecording may have A/V sync issues or instability. Consider disabling the conflicting mod(s)."), false);
				}
			}
		}
	}

	private static boolean isReplayConflict(String conflict) {
		if (conflict == null) {
			return false;
		} else {
			String c = conflict.toLowerCase();
			return c.contains("'flashback'") || c.contains("'replaymod'") || c.contains("'replay-mod'");
		}
	}

	public static boolean detectReplayPlayback(class_310 client) {
		try {
			if (client == null) {
				return false;
			} else if (ReplayCompatBridge.isReplayClass(client.method_1560())) {
				return true;
			} else {
				return ReplayCompatBridge.isReplayClass(client.field_1724) ? true : ReplayCompatBridge.isReplayClass(client.method_1576());
			}
		} catch (Throwable var2) {
			return false;
		}
	}

	static {
		Map<String, String> m = new LinkedHashMap();
		m.put(
			"flashback",
			"Flashback ALSO redirects Minecraft's OpenAL sound engine to capture game audio via loopback - exactly the same hook Record-able uses. When both are installed only one can own the loopback device, so Record-able may capture NO in-game audio (recordings end up silent or contain only background/system noise). For working game-audio capture, run only ONE recording mod at a time."
		);
		m.put("replaymod", "ReplayMod intercepts client packets and rendering, which can interfere with Record-able's screen capture and audio recording.");
		m.put("replay-mod", "Replay Mod (alternate ID) may conflict with Record-able's capture hooks.");
		m.put(
			"bettershields",
			"BetterShields has a known NPE crash when rendering shields in item frames (getCurrentRenderedPlayer() returns null). This crash kills the game while recording and may corrupt the current video file."
		);
		KNOWN_CONFLICTS = Collections.unmodifiableMap(m);
	}
}
