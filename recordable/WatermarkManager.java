package dev.recordable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.recordable.WatermarkSlot.Animation;
import dev.recordable.WatermarkSlot.Kind;
import dev.recordable.WatermarkSlot.Position;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public final class WatermarkManager {
	private static final String PRESET_FILE = "recordable_watermark_presets.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type PRESET_MAP_TYPE = (new TypeToken<LinkedHashMap<String, List<WatermarkSlot>>>() {
	}).getType();

	private WatermarkManager() {
	}

	private static Path presetPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("recordable_watermark_presets.json");
	}

	public static Map<String, List<WatermarkSlot>> loadPresets() {
		Path path = presetPath();
		if (!Files.isRegularFile(path, new LinkOption[0])) {
			return new LinkedHashMap();
		} else {
			try {
				Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);

				Map var11;
				label73: {
					try {
						Map<String, List<WatermarkSlot>> map = (Map<String, List<WatermarkSlot>>)GSON.fromJson(reader, PRESET_MAP_TYPE);
						if (map == null) {
							var11 = new LinkedHashMap();
							break label73;
						}

						for (List<WatermarkSlot> slots : map.values()) {
							if (slots != null) {
								for (WatermarkSlot s : slots) {
									if (s != null) {
										s.sanitize();
									}
								}
							}
						}

						var11 = map;
					} catch (Throwable var8) {
						if (reader != null) {
							try {
								reader.close();
							} catch (Throwable var7) {
								var8.addSuppressed(var7);
							}
						}

						throw var8;
					}

					if (reader != null) {
						reader.close();
					}

					return var11;
				}

				if (reader != null) {
					reader.close();
				}

				return var11;
			} catch (Exception var9) {
				RecordableMod.LOGGER.warn("WatermarkManager: failed to load presets: {}", var9.getMessage());
				return new LinkedHashMap();
			}
		}
	}

	private static void savePresets(Map<String, List<WatermarkSlot>> presets) {
		try {
			Writer writer = Files.newBufferedWriter(presetPath(), StandardCharsets.UTF_8);

			try {
				GSON.toJson(presets, PRESET_MAP_TYPE, writer);
			} catch (Throwable var5) {
				if (writer != null) {
					try {
						writer.close();
					} catch (Throwable var4) {
						var5.addSuppressed(var4);
					}
				}

				throw var5;
			}

			if (writer != null) {
				writer.close();
			}
		} catch (Exception var6) {
			RecordableMod.LOGGER.warn("WatermarkManager: failed to save presets: {}", var6.getMessage());
		}
	}

	public static void savePreset(String name, List<WatermarkSlot> slots) {
		if (name != null && !name.isBlank()) {
			Map<String, List<WatermarkSlot>> presets = loadPresets();
			List<WatermarkSlot> copy = new ArrayList();
			if (slots != null) {
				for (WatermarkSlot s : slots) {
					if (s != null) {
						copy.add(s.copy());
					}
				}
			}

			presets.put(name.trim(), copy);
			savePresets(presets);
		}
	}

	public static void deletePreset(String name) {
		Map<String, List<WatermarkSlot>> presets = loadPresets();
		if (presets.remove(name) != null) {
			savePresets(presets);
		}
	}

	public static List<WatermarkSlot> getPreset(String name) {
		List<WatermarkSlot> slots = (List<WatermarkSlot>)loadPresets().get(name);
		if (slots == null) {
			return null;
		} else {
			List<WatermarkSlot> copy = new ArrayList();

			for (WatermarkSlot s : slots) {
				if (s != null) {
					copy.add(s.copy());
				}
			}

			return copy;
		}
	}

	public static List<WatermarkSlot> builtinUsernameStamp() {
		List<WatermarkSlot> slots = new ArrayList();
		WatermarkSlot text = new WatermarkSlot("Username Stamp", Kind.TEXT);
		text.enabled = true;
		text.text = "{username} • {date}";
		text.position = Position.BOTTOM_RIGHT;
		text.opacity = 70;
		slots.add(text);
		return slots;
	}

	public static List<WatermarkSlot> builtinChannelName() {
		List<WatermarkSlot> slots = new ArrayList();
		WatermarkSlot text = new WatermarkSlot("Channel Name", Kind.TEXT);
		text.enabled = true;
		text.text = "Record-able";
		text.position = Position.TOP_CENTER;
		text.scale = 130;
		text.opacity = 60;
		text.animation = Animation.FADE;
		slots.add(text);
		return slots;
	}
}
