package dev.recordable;

import dev.recordable.JavaAudioCapture.AudioDeviceInfo;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class AudioCapture {
	private static volatile AudioCapture.AudioDeviceStatus cachedStatus;
	private static volatile AudioCapture.CacheKey cachedKey;
	private static volatile long cachedStatusAtMs;
	private static final long CACHE_TTL_MS = 10000L;
	private static final Pattern DSHOW_DEVICE_NAME = Pattern.compile("\"([^\"]+)\"");
	private static final Pattern DSHOW_AUDIO_DEVICE = Pattern.compile("\"([^\"]+)\"\\s+\\(audio\\)");
	private static final Pattern DSHOW_ALTERNATIVE_NAME = Pattern.compile("Alternative name\\s+\"([^\"]+)\"");
	private static final Pattern AVFOUNDATION_DEVICE_NAME = Pattern.compile("\\[(\\d+)]\\s+(.+)$");

	private AudioCapture() {
	}

	public static List<String> detectJavaSoundDevices() {
		return JavaAudioCapture.getAvailableDeviceNames();
	}

	public static boolean isJavaLoopbackAvailable() {
		return JavaAudioCapture.isLoopbackAvailable();
	}

	public static boolean isJavaCaptureAvailable() {
		return JavaAudioCapture.isAnyCaptureDeviceAvailable();
	}

	public static String getJavaAudioStatus() {
		List<AudioDeviceInfo> devices = JavaAudioCapture.detectAudioDevices();
		if (devices.isEmpty()) {
			return "No audio capture devices detected";
		} else {
			long loopbackCount = devices.stream().filter(AudioDeviceInfo::isLoopback).count();
			if (loopbackCount > 0L) {
				String bestDevice = (String)devices.stream().filter(AudioDeviceInfo::isLoopback).findFirst().map(AudioDeviceInfo::name).orElse("Unknown");
				return "Loopback: " + bestDevice + " (" + loopbackCount + " device" + (loopbackCount > 1L ? "s" : "") + ")";
			} else {
				return devices.size() + " capture device" + (devices.size() > 1 ? "s" : "") + " (no loopback)";
			}
		}
	}

	public static AudioCapture.AudioDeviceStatus detectAudioDevice(String ffmpegExecutable, String configuredDevice) {
		String executable = ffmpegExecutable != null && !ffmpegExecutable.isBlank() ? ffmpegExecutable.trim() : "ffmpeg";
		String configured = configuredDevice == null ? "" : configuredDevice.trim();
		if (isAutoDevicePreference(configured)) {
			configured = "auto";
		}

		AudioCapture.CacheKey requestedKey = new AudioCapture.CacheKey(executable, configured, getPlatform());
		long now = System.currentTimeMillis();
		AudioCapture.AudioDeviceStatus cached = cachedStatus;
		AudioCapture.CacheKey key = cachedKey;
		if (cached != null && key != null && requestedKey.equals(key) && now - cachedStatusAtMs < 10000L) {
			return cached;
		} else {
			AudioCapture.AudioDeviceStatus status = probeAudioDevice(executable, configured);
			cachedStatus = status;
			cachedKey = requestedKey;
			cachedStatusAtMs = now;
			return status;
		}
	}

	public static void clearCache() {
		cachedStatus = null;
		cachedKey = null;
		cachedStatusAtMs = 0L;
	}

	public static String getPlatform() {
		return PlatformUtils.getPlatformId();
	}

	public static boolean testAudioDevice(String ffmpegExecutable, AudioCapture.AudioDeviceStatus status) {
		if (status != null && status.available()) {
			String executable = ffmpegExecutable != null && !ffmpegExecutable.isBlank() ? ffmpegExecutable.trim() : "ffmpeg";
			List<String> args = status.ffmpegArgs();
			if (args == null || args.isEmpty()) {
				String cmd = status.platform() == null ? "" : status.platform().toLowerCase(Locale.ROOT);

				args = switch (cmd) {
					case "windows" -> buildWindowsInputArgs(status.deviceName());
					case "linux" -> List.of("-f", "pulse", "-i", status.deviceName());
					case "macos" -> List.of("-f", "avfoundation", "-i", ":" + status.deviceName());
					default -> Collections.emptyList();
				};
			}

			if (args.isEmpty()) {
				return false;
			} else {
				List<String> cmd = new ArrayList();
				cmd.add(executable);
				cmd.add("-nostdin");
				cmd.add("-hide_banner");
				cmd.add("-loglevel");
				cmd.add("error");
				cmd.addAll(args);
				cmd.add("-t");
				cmd.add("1");
				cmd.add("-f");
				cmd.add("null");
				cmd.add("-");
				AudioCapture.ProcessResult result = runCommand(6, (String[])cmd.toArray(String[]::new));
				if (!result.success()) {
					logProcessOutput("Audio device probe", result);
				}

				return result.success();
			}
		} else {
			return false;
		}
	}

	public static List<String> listMicrophoneDevices(String ffmpegExecutable) {
		String executable = ffmpegExecutable != null && !ffmpegExecutable.isBlank() ? ffmpegExecutable.trim() : "ffmpeg";

		try {
			String t = getPlatform();

			return switch (t) {
				case "windows" -> (List)listDShowAudioDevices(executable).stream().filter(d -> !isLoopbackDeviceName(d)).collect(Collectors.toList());
				case "macos" -> listAVFoundationAudioDevices(executable);
				case "linux" -> listPulseInputSources();
				default -> Collections.emptyList();
			};
		} catch (Throwable var4) {
			RecordableMod.LOGGER.debug("Failed to list microphone devices", var4);
			return Collections.emptyList();
		}
	}

	public static AudioCapture.AudioDeviceStatus detectMicrophoneDevice(String ffmpegExecutable, String configuredDevice) {
		String executable = ffmpegExecutable != null && !ffmpegExecutable.isBlank() ? ffmpegExecutable.trim() : "ffmpeg";
		boolean isAuto = isAutoDevicePreference(configuredDevice);
		String explicit = isAuto ? null : configuredDevice.trim();
		String platform = getPlatform();

		return switch (platform) {
			case "windows" -> probeWindowsMicrophone(executable, explicit);
			case "linux" -> probeLinuxMicrophone(explicit);
			case "macos" -> probeMacOSMicrophone(executable, explicit);
			default -> AudioCapture.AudioDeviceStatus.unavailable(platform, "Microphone capture not supported on this platform.");
		};
	}

	public static AudioCapture.MicTestResult testMicrophoneLevel(String ffmpegExecutable, String configuredDevice, int seconds) {
		String executable = ffmpegExecutable != null && !ffmpegExecutable.isBlank() ? ffmpegExecutable.trim() : "ffmpeg";
		int dur = Math.max(1, Math.min(5, seconds));
		AudioCapture.AudioDeviceStatus mic = detectMicrophoneDevice(executable, configuredDevice);
		if (mic != null && mic.available()) {
			Path tmp = null;

			AudioCapture.MicTestResult var16;
			try {
				tmp = Files.createTempFile("recordable-mictest-", ".wav");
				List<String> cmd = new ArrayList();
				cmd.add(executable);
				cmd.add("-hide_banner");
				cmd.add("-loglevel");
				cmd.add("error");
				cmd.add("-y");
				cmd.add("-thread_queue_size");
				cmd.add("1024");
				cmd.addAll(mic.ffmpegArgs());
				cmd.add("-t");
				cmd.add(Integer.toString(dur));
				cmd.add("-ac");
				cmd.add("1");
				cmd.add("-ar");
				cmd.add("48000");
				cmd.add(tmp.toAbsolutePath().toString());
				Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
				StringBuilder errBuf = new StringBuilder();
				BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));

				String ln;
				try {
					while ((ln = r.readLine()) != null) {
						if (errBuf.length() < 2000) {
							errBuf.append(ln).append('\n');
						}
					}
				} catch (Throwable var33) {
					try {
						r.close();
					} catch (Throwable var32) {
						var33.addSuppressed(var32);
					}

					throw var33;
				}

				r.close();
				if (!p.waitFor((long)dur + 8L, TimeUnit.SECONDS)) {
					p.destroyForcibly();
					return new AudioCapture.MicTestResult(false, mic.deviceName(), Double.NaN, Double.NaN, "Mic test timed out opening the device.");
				}

				if (!Files.exists(tmp, new LinkOption[0]) || Files.size(tmp) <= 44L) {
					return new AudioCapture.MicTestResult(false, mic.deviceName(), Double.NaN, Double.NaN, "Mic device did not open: " + errBuf.toString().trim());
				}

				double[] levels = measureWavLevels(executable, tmp);
				double meanDb = levels[0];
				double maxDb = levels[1];
				if (Double.isNaN(meanDb)) {
					return new AudioCapture.MicTestResult(true, mic.deviceName(), Double.NaN, Double.NaN, "Captured " + dur + "s but level could not be measured.");
				}

				if (!(maxDb <= -55.0)) {
					String msg = "Mic OK - peak " + fmtDb(maxDb) + ", avg " + fmtDb(meanDb) + ".";
					return new AudioCapture.MicTestResult(true, mic.deviceName(), meanDb, maxDb, msg);
				}

				String msg = "Only SILENCE captured (peak "
					+ fmtDb(maxDb)
					+ "). Enable mic access in Windows Privacy settings and unmute/raise the level (and check Razer Synapse).";
				var16 = new AudioCapture.MicTestResult(true, mic.deviceName(), meanDb, maxDb, msg);
			} catch (Exception var34) {
				return new AudioCapture.MicTestResult(false, mic.deviceName(), Double.NaN, Double.NaN, "Mic test failed: " + var34.getMessage());
			} finally {
				if (tmp != null) {
					try {
						Files.deleteIfExists(tmp);
					} catch (Exception var31) {
					}
				}
			}

			return var16;
		} else {
			return new AudioCapture.MicTestResult(
				false, configuredDevice == null ? "auto" : configuredDevice, Double.NaN, Double.NaN, mic == null ? "No microphone detected." : mic.message()
			);
		}
	}

	private static String fmtDb(double db) {
		return !Double.isNaN(db) && !Double.isInfinite(db) ? String.format(Locale.ROOT, "%.1f dB", db) : "n/a";
	}

	private static double[] measureWavLevels(String ffmpegExecutable, Path wav) {
		double mean = Double.NaN;
		double max = Double.NaN;

		try {
			List<String> cmd = List.of(
				ffmpegExecutable, "-nostdin", "-hide_banner", "-i", wav.toAbsolutePath().toString(), "-af", "volumedetect", "-vn", "-f", "null", "-"
			);
			Process probe = new ProcessBuilder(cmd).redirectErrorStream(true).start();
			BufferedReader r = new BufferedReader(new InputStreamReader(probe.getInputStream(), StandardCharsets.UTF_8));

			String ln;
			try {
				while ((ln = r.readLine()) != null) {
					if (ln.contains("mean_volume")) {
						mean = parseDbAfter(ln, "mean_volume:");
					} else if (ln.contains("max_volume")) {
						max = parseDbAfter(ln, "max_volume:");
					}
				}
			} catch (Throwable var12) {
				try {
					r.close();
				} catch (Throwable var11) {
					var12.addSuppressed(var11);
				}

				throw var12;
			}

			r.close();
			if (!probe.waitFor(10L, TimeUnit.SECONDS)) {
				probe.destroyForcibly();
			}
		} catch (Exception var13) {
		}

		return new double[]{mean, max};
	}

	private static double parseDbAfter(String line, String key) {
		try {
			String[] parts = line.split(Pattern.quote(key));
			if (parts.length > 1) {
				return Double.parseDouble(parts[1].trim().split("\\s+")[0]);
			}
		} catch (Exception var3) {
		}

		return Double.NaN;
	}

	private static AudioCapture.AudioDeviceStatus probeWindowsMicrophone(String ffmpegExecutable, String explicitDevice) {
		String device = explicitDevice;
		if (explicitDevice == null) {
			List<String> devices = listDShowAudioDevices(ffmpegExecutable);
			List<String> realInputs = new ArrayList();

			for (String d : devices) {
				if (!isLoopbackDeviceName(d)) {
					realInputs.add(d);
				}
			}

			device = pickPreferredDevice(realInputs, new String[]{"microphone", "mic", "headset", "input", "line in", "webcam"});
			if (device == null && !realInputs.isEmpty()) {
				device = (String)realInputs.get(0);
			}

			if (device == null) {
				return AudioCapture.AudioDeviceStatus.unavailable(
					"windows",
					"No microphone (DirectShow input) device detected. Only loopback devices were found. Enable/plug in a microphone, or pick a device explicitly in settings."
				);
			}
		}

		if (device != null && !device.isBlank()) {
			List<String> args = buildWindowsInputArgs(device);
			return AudioCapture.AudioDeviceStatus.found(device, "windows", "Microphone: " + device, args);
		} else {
			return AudioCapture.AudioDeviceStatus.unavailable("windows", "No microphone (DirectShow input) device detected.");
		}
	}

	private static boolean isLoopbackDeviceName(String deviceName) {
		if (deviceName == null) {
			return false;
		} else {
			String lower = deviceName.toLowerCase(Locale.ROOT);
			return lower.contains("stereo mix")
				|| lower.contains("stereomix")
				|| lower.contains("what u hear")
				|| lower.contains("what you hear")
				|| lower.contains("wave out mix")
				|| lower.contains("wave out")
				|| lower.contains("loopback")
				|| lower.contains("monitor of")
				|| lower.contains("rec. playback");
		}
	}

	private static AudioCapture.AudioDeviceStatus probeLinuxMicrophone(String explicitDevice) {
		if (explicitDevice != null) {
			return AudioCapture.AudioDeviceStatus.found(explicitDevice, "linux", "Microphone: " + explicitDevice, List.of("-f", "pulse", "-i", explicitDevice));
		} else {
			String source = findPulseInputSource();
			String target = source != null ? source : "default";
			return AudioCapture.AudioDeviceStatus.found(target, "linux", "Microphone: " + target, List.of("-f", "pulse", "-i", target));
		}
	}

	private static AudioCapture.AudioDeviceStatus probeMacOSMicrophone(String ffmpegExecutable, String explicitDevice) {
		if (explicitDevice != null) {
			String target = explicitDevice;
			if (!explicitDevice.matches("\\d+")) {
				List<String> devices = listAVFoundationAudioDevices(ffmpegExecutable);
				int idx = devices.indexOf(explicitDevice);
				if (idx >= 0) {
					target = Integer.toString(idx);
				}
			}

			return AudioCapture.AudioDeviceStatus.found(explicitDevice, "macos", "Microphone: " + explicitDevice, List.of("-f", "avfoundation", "-i", ":" + target));
		} else {
			return AudioCapture.AudioDeviceStatus.found("Default Input", "macos", "Microphone: default input", List.of("-f", "avfoundation", "-i", ":0"));
		}
	}

	private static List<String> listPulseInputSources() {
		AudioCapture.ProcessResult result = runCommand(3, "pactl", "list", "short", "sources");
		if (!result.success() && !result.hasAnyOutput()) {
			return Collections.emptyList();
		} else {
			List<String> inputs = new ArrayList();

			for (String line : mergeOutputLines(result)) {
				String[] parts = line.split("\\t");
				if (parts.length >= 2) {
					String sourceName = parts[1].trim();
					if (!sourceName.endsWith(".monitor") && !inputs.contains(sourceName)) {
						inputs.add(sourceName);
					}
				}
			}

			return inputs;
		}
	}

	private static String findPulseInputSource() {
		List<String> inputs = listPulseInputSources();
		if (inputs.isEmpty()) {
			return null;
		} else {
			for (String input : inputs) {
				String lower = input.toLowerCase(Locale.ROOT);
				if (lower.contains("input") || lower.contains("mic")) {
					return input;
				}
			}

			return (String)inputs.get(0);
		}
	}

	private static AudioCapture.AudioDeviceStatus probeAudioDevice(String ffmpegExecutable, String configuredDevice) {
		String platform = getPlatform();
		boolean isAuto = isAutoDevicePreference(configuredDevice);

		return switch (platform) {
			case "linux" -> probeLinuxAudio(isAuto ? null : configuredDevice);
			case "windows" -> probeWindowsAudio(ffmpegExecutable, isAuto ? null : configuredDevice);
			case "macos" -> probeMacOSAudio(ffmpegExecutable, isAuto ? null : configuredDevice);
			case "android" -> probeAndroidAudio();
			default -> AudioCapture.AudioDeviceStatus.unavailable(platform, "Unsupported operating system for audio capture.");
		};
	}

	private static AudioCapture.AudioDeviceStatus probeAndroidAudio() {
		try {
			OpenALLoopbackCapture loopback = OpenALLoopbackCapture.getInstance();
			if (loopback.isActive()) {
				return AudioCapture.AudioDeviceStatus.found(
					"OpenAL Loopback", "android", "Game audio captured directly via OpenAL loopback (48kHz Stereo).", Collections.emptyList()
				);
			}
		} catch (Throwable var2) {
		}

		try {
			if (OpenALLoopbackCapture.isLoopbackSupported()) {
				return AudioCapture.AudioDeviceStatus.found(
					"OpenAL Loopback (pending)", "android", "OpenAL loopback supported. Audio will activate when recording starts.", Collections.emptyList()
				);
			}
		} catch (Throwable var1) {
		}

		return AudioCapture.AudioDeviceStatus.found("OpenAL Capture", "android", "Audio captured via OpenAL capture device.", Collections.emptyList());
	}

	private static AudioCapture.AudioDeviceStatus probeLinuxAudio(String explicitDevice) {
		if (explicitDevice != null) {
			List<String> args = List.of("-f", "pulse", "-i", explicitDevice);
			return AudioCapture.AudioDeviceStatus.found(explicitDevice, "linux", "Using configured PulseAudio source: " + explicitDevice, args);
		} else {
			String monitorSource = findPulseMonitorSource();
			if (monitorSource != null) {
				List<String> args = List.of("-f", "pulse", "-i", monitorSource);
				return AudioCapture.AudioDeviceStatus.found(monitorSource, "linux", "PulseAudio monitor source detected.", args);
			} else {
				List<String> args = List.of("-f", "pulse", "-i", "default");
				return AudioCapture.AudioDeviceStatus.found("default", "linux", "Using default PulseAudio source (monitor source not found).", args);
			}
		}
	}

	private static String findPulseMonitorSource() {
		AudioCapture.ProcessResult result = runCommand(3, "pactl", "list", "short", "sources");
		if (!result.success() && !result.hasAnyOutput()) {
			return null;
		} else {
			List<String> monitors = new ArrayList();

			for (String line : mergeOutputLines(result)) {
				String[] parts = line.split("\\t");
				if (parts.length >= 2) {
					String sourceName = parts[1].trim();
					if (sourceName.endsWith(".monitor")) {
						monitors.add(sourceName);
					}
				}
			}

			if (monitors.isEmpty()) {
				return null;
			} else {
				for (String monitor : monitors) {
					if (monitor.contains("analog")) {
						return monitor;
					}
				}

				return (String)monitors.get(0);
			}
		}
	}

	private static AudioCapture.AudioDeviceStatus probeWindowsAudio(String ffmpegExecutable, String explicitDevice) {
		if (explicitDevice != null) {
			String trimmedDevice = explicitDevice.trim();
			if (trimmedDevice.isEmpty()) {
				return AudioCapture.AudioDeviceStatus.unavailable("windows", "Configured audio device name is empty.");
			} else {
				String dshowDevice = resolveDshowCaptureTarget(ffmpegExecutable, trimmedDevice);
				List<String> args = buildWindowsInputArgs(dshowDevice);
				return AudioCapture.AudioDeviceStatus.found(dshowDevice, "windows", "Using configured DirectShow device: " + dshowDevice, args);
			}
		} else {
			String dshowDevice = detectWindowsAudioDirectShow(ffmpegExecutable);
			if (dshowDevice != null) {
				String captureTarget = resolveDshowCaptureTarget(ffmpegExecutable, dshowDevice);
				List<String> args = buildWindowsInputArgs(captureTarget);
				return AudioCapture.AudioDeviceStatus.found(captureTarget, "windows", "DirectShow audio device detected (Stereo Mix/loopback input).", args);
			} else {
				return AudioCapture.AudioDeviceStatus.unavailable(
					"windows",
					"Stereo Mix was not detected. Recording will continue in video-only mode. Enable Stereo Mix in Windows Sound Settings > Recording > Show Disabled Devices, then retry."
				);
			}
		}
	}

	private static String detectWindowsAudioDirectShow(String ffmpegExecutable) {
		RecordableMod.LOGGER.info("Scanning Windows audio devices via DirectShow...");
		List<String> dshowDevices = listDShowAudioDevices(ffmpegExecutable);
		RecordableMod.LOGGER.info("DirectShow audio devices found: {}", dshowDevices);
		if (dshowDevices.isEmpty()) {
			return null;
		} else {
			String best = pickPreferredDevice(
				dshowDevices,
				new String[]{"stereo mix", "what u hear", "cable output", "voicemeeter", "vb-audio", "loopback", "speakers", "headphones", "headset", "realtek"}
			);
			return best != null ? best : (String)dshowDevices.get(0);
		}
	}

	private static List<String> buildWindowsInputArgs(String deviceName) {
		if (deviceName != null && !deviceName.isBlank()) {
			String trimmed = deviceName.trim();
			return List.of("-rtbufsize", "200M", "-f", "dshow", "-audio_buffer_size", "30", "-i", "audio=" + trimmed);
		} else {
			return Collections.emptyList();
		}
	}

	private static String resolveDshowCaptureTarget(String ffmpegExecutable, String preferredDevice) {
		if (preferredDevice != null && !preferredDevice.isBlank()) {
			String trimmed = preferredDevice.trim();
			if (trimmed.startsWith("@device_")) {
				return trimmed;
			} else {
				RecordableMod.LOGGER.info("Using DirectShow device name as-is: {}", trimmed);
				return trimmed;
			}
		} else {
			return preferredDevice;
		}
	}

	private static String findDshowAlternativeName(String ffmpegExecutable, String deviceName) {
		AudioCapture.ProcessResult result = runCommand(8, ffmpegExecutable, "-hide_banner", "-list_devices", "true", "-f", "dshow", "-i", "dummy");
		List<String> outputLines = mergeOutputLines(result);
		if (outputLines.isEmpty()) {
			return null;
		} else {
			boolean matchedDevice = false;

			for (String line : outputLines) {
				if (line != null) {
					String trimmed = line.trim();
					Matcher deviceMatcher = DSHOW_AUDIO_DEVICE.matcher(trimmed);
					if (deviceMatcher.find()) {
						String candidate = deviceMatcher.group(1).trim();
						matchedDevice = candidate.equalsIgnoreCase(deviceName);
					} else if (matchedDevice) {
						Matcher altMatcher = DSHOW_ALTERNATIVE_NAME.matcher(trimmed);
						if (altMatcher.find()) {
							String alt = altMatcher.group(1).trim();
							if (alt.toLowerCase(Locale.ROOT).contains("/wave_")) {
								return alt;
							}
						}
					}
				}
			}

			return null;
		}
	}

	private static String pickPreferredDevice(List<String> devices, String[] priorityTokens) {
		if (devices != null && !devices.isEmpty()) {
			for (String token : priorityTokens) {
				String loweredToken = token.toLowerCase(Locale.ROOT);

				for (String device : devices) {
					if (device != null && device.toLowerCase(Locale.ROOT).contains(loweredToken)) {
						return device;
					}
				}
			}

			return null;
		} else {
			return null;
		}
	}

	private static boolean isAutoDevicePreference(String configuredDevice) {
		if (configuredDevice == null) {
			return true;
		} else {
			String normalized = configuredDevice.trim().toLowerCase(Locale.ROOT);
			return normalized.isEmpty() || "auto".equals(normalized) || "openal".equals(normalized);
		}
	}

	private static List<String> listDShowAudioDevices(String ffmpegExecutable) {
		AudioCapture.ProcessResult result = runCommand(8, ffmpegExecutable, "-hide_banner", "-list_devices", "true", "-f", "dshow", "-i", "dummy");
		logProcessOutput("DirectShow device detection", result);
		List<String> outputLines = mergeOutputLines(result);
		if (outputLines.isEmpty()) {
			return Collections.emptyList();
		} else {
			List<String> devices = new ArrayList();

			for (String line : outputLines) {
				if (line != null) {
					String trimmed = line.trim();
					if (!trimmed.toLowerCase(Locale.ROOT).contains("alternative name")) {
						Matcher matcher = DSHOW_AUDIO_DEVICE.matcher(trimmed);

						while (matcher.find()) {
							String candidate = matcher.group(1).trim();
							if (!candidate.isEmpty() && !devices.contains(candidate)) {
								devices.add(candidate);
								RecordableMod.LOGGER.info("Found DirectShow audio device: {}", candidate);
							}
						}
					}
				}
			}

			return devices;
		}
	}

	private static AudioCapture.AudioDeviceStatus probeMacOSAudio(String ffmpegExecutable, String explicitDevice) {
		if (explicitDevice != null) {
			List<String> args;
			if (explicitDevice.matches("\\d+")) {
				args = List.of("-f", "avfoundation", "-i", ":" + explicitDevice);
			} else {
				args = List.of("-f", "avfoundation", "-i", ":" + explicitDevice);
			}

			return AudioCapture.AudioDeviceStatus.found(explicitDevice, "macos", "Using configured AVFoundation device: " + explicitDevice, args);
		} else {
			List<String> avDevices = listAVFoundationAudioDevices(ffmpegExecutable);
			String[] preferredDevices = new String[]{"BlackHole", "Soundflower", "Loopback", "Multi-Output"};

			for (int i = 0; i < avDevices.size(); i++) {
				String device = (String)avDevices.get(i);

				for (String preferred : preferredDevices) {
					if (device.toLowerCase(Locale.ROOT).contains(preferred.toLowerCase(Locale.ROOT))) {
						List<String> args = List.of("-f", "avfoundation", "-i", ":" + i);
						return AudioCapture.AudioDeviceStatus.found(device, "macos", "Virtual audio device detected for system audio capture.", args);
					}
				}
			}

			if (!avDevices.isEmpty()) {
				List<String> args = List.of("-f", "avfoundation", "-i", ":0");
				return AudioCapture.AudioDeviceStatus.found(
					(String)avDevices.get(0), "macos", "Using default audio input. Install BlackHole for system audio capture.", args
				);
			} else {
				return AudioCapture.AudioDeviceStatus.unavailable(
					"macos", "No AVFoundation audio devices detected. Install BlackHole or Soundflower for system audio capture."
				);
			}
		}
	}

	private static List<String> listAVFoundationAudioDevices(String ffmpegExecutable) {
		AudioCapture.ProcessResult result = runCommand(5, ffmpegExecutable, "-hide_banner", "-f", "avfoundation", "-list_devices", "true", "-i", "");
		if (!result.success() && !result.hasAnyOutput()) {
			return Collections.emptyList();
		} else {
			List<String> devices = new ArrayList();
			boolean inAudioSection = false;

			for (String line : mergeOutputLines(result)) {
				String trimmed = line.trim();
				String lower = trimmed.toLowerCase(Locale.ROOT);
				if (lower.contains("avfoundation audio devices")) {
					inAudioSection = true;
				} else if (lower.contains("avfoundation video devices")) {
					inAudioSection = false;
				} else if (inAudioSection) {
					Matcher matcher = AVFOUNDATION_DEVICE_NAME.matcher(trimmed);
					if (matcher.find()) {
						String name = matcher.group(2).trim();
						if (!name.isEmpty()) {
							devices.add(name);
						}
					}
				}
			}

			return devices;
		}
	}

	private static AudioCapture.ProcessResult runCommand(int timeoutSeconds, String... command) {
		List<String> stdoutLines = Collections.synchronizedList(new ArrayList());
		List<String> stderrLines = Collections.synchronizedList(new ArrayList());

		try {
			Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
			Thread stdoutThread = new Thread(() -> readProcessStream(process.getInputStream(), stdoutLines), "Record-able cmd stdout");
			Thread stderrThread = new Thread(() -> readProcessStream(process.getErrorStream(), stderrLines), "Record-able cmd stderr");
			stdoutThread.setDaemon(true);
			stderrThread.setDaemon(true);
			stdoutThread.start();
			stderrThread.start();
			boolean exited = process.waitFor((long)Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
			if (!exited) {
				process.destroyForcibly();
			}

			joinThreadQuietly(stdoutThread, 500L);
			joinThreadQuietly(stderrThread, 500L);
			int exitCode = exited ? process.exitValue() : -1;
			boolean success = exited && exitCode == 0;
			String error = success ? "" : (exited ? "Exit code " + exitCode : "Timed out");
			return new AudioCapture.ProcessResult(success, List.copyOf(stdoutLines), List.copyOf(stderrLines), exitCode, error);
		} catch (Exception var11) {
			RecordableMod.LOGGER.debug("Failed to execute command: {}", String.join(" ", command), var11);
			String message = var11.getMessage() == null ? var11.toString() : var11.getMessage();
			return new AudioCapture.ProcessResult(false, Collections.emptyList(), Collections.emptyList(), -1, message);
		}
	}

	private static void readProcessStream(InputStream stream, List<String> sink) {
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));

			String line;
			try {
				while ((line = reader.readLine()) != null) {
					sink.add(line);
				}
			} catch (Throwable var6) {
				try {
					reader.close();
				} catch (Throwable var5) {
					var6.addSuppressed(var5);
				}

				throw var6;
			}

			reader.close();
		} catch (Exception var7) {
			RecordableMod.LOGGER.debug("Failed to read process stream", var7);
		}
	}

	private static void joinThreadQuietly(Thread thread, long timeoutMs) {
		if (thread != null) {
			try {
				thread.join(Math.max(1L, timeoutMs));
			} catch (InterruptedException var4) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private static List<String> mergeOutputLines(AudioCapture.ProcessResult result) {
		if (result == null) {
			return Collections.emptyList();
		} else {
			List<String> merged = new ArrayList(result.stderrLines());
			if (merged.isEmpty()) {
				merged.addAll(result.stdoutLines());
			} else if (!result.stdoutLines().isEmpty()) {
				merged.addAll(result.stdoutLines());
			}

			return merged;
		}
	}

	private static void logProcessOutput(String label, AudioCapture.ProcessResult result) {
		if (result != null) {
			String stdoutContent = result.stdoutLines().isEmpty() ? "<empty>" : String.join(System.lineSeparator(), result.stdoutLines());
			String stderrContent = result.stderrLines().isEmpty() ? "<empty>" : String.join(System.lineSeparator(), result.stderrLines());
			RecordableMod.LOGGER.info("{} exitCode={} success={} error='{}'", new Object[]{label, result.exitCode(), result.success(), result.error()});
			RecordableMod.LOGGER.info("{} STDOUT:{}{}", new Object[]{label, System.lineSeparator(), stdoutContent});
			RecordableMod.LOGGER.info("{} STDERR:{}{}", new Object[]{label, System.lineSeparator(), stderrContent});
		}
	}

	public static record AudioDeviceStatus(boolean available, String deviceName, String platform, String message, List<String> ffmpegArgs) {
		public static AudioCapture.AudioDeviceStatus unavailable(String platform, String message) {
			return new AudioCapture.AudioDeviceStatus(false, "", platform, message, Collections.emptyList());
		}

		public static AudioCapture.AudioDeviceStatus found(String deviceName, String platform, String message, List<String> ffmpegArgs) {
			return new AudioCapture.AudioDeviceStatus(true, deviceName, platform, message, ffmpegArgs);
		}

		public String displayText() {
			return this.available ? "Audio: " + this.deviceName + " (" + this.platform + ")" : "Audio: Not available - " + this.message;
		}
	}

	private static record CacheKey(String ffmpegExecutable, String configuredDevice, String platform) {
		private CacheKey(String ffmpegExecutable, String configuredDevice, String platform) {
			ffmpegExecutable = ffmpegExecutable == null ? "" : ffmpegExecutable;
			configuredDevice = configuredDevice == null ? "" : configuredDevice;
			platform = platform == null ? "unknown" : platform;
			this.ffmpegExecutable = ffmpegExecutable;
			this.configuredDevice = configuredDevice;
			this.platform = platform;
		}

		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			} else {
				return !(obj instanceof AudioCapture.CacheKey other)
					? false
					: Objects.equals(this.ffmpegExecutable, other.ffmpegExecutable)
						&& Objects.equals(this.configuredDevice, other.configuredDevice)
						&& Objects.equals(this.platform, other.platform);
			}
		}

		public int hashCode() {
			return Objects.hash(new Object[]{this.ffmpegExecutable, this.configuredDevice, this.platform});
		}
	}

	public static record MicTestResult(boolean success, String deviceName, double meanDb, double maxDb, String message) {
		public boolean hasSignal() {
			return this.success && this.maxDb > -55.0;
		}
	}

	private static record ProcessResult(boolean success, List<String> stdoutLines, List<String> stderrLines, int exitCode, String error) {
		private boolean hasAnyOutput() {
			return !this.stdoutLines.isEmpty() || !this.stderrLines.isEmpty();
		}
	}
}
