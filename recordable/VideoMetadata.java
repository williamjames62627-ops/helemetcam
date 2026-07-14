package dev.recordable;

import dev.recordable.FFmpegEncoder.FfmpegStatus;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;

public final class VideoMetadata {
	private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final DateTimeFormatter DISPLAY_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final Pattern FILENAME_TIMESTAMP = Pattern.compile("(\\d{8}-\\d{6})");
	private static final Pattern DURATION_PATTERN = Pattern.compile("Duration:\\s*(\\d+):(\\d+):(\\d+(?:\\.\\d+)?)");
	private static final Pattern TIME_PATTERN = Pattern.compile("time=\\s*(\\d+):(\\d+):(\\d+(?:\\.\\d+)?)");
	private static final Object LOCK = new Object();
	private static final Map<Path, VideoMetadata.CacheEntry> CACHE = new HashMap();
	private static volatile VideoMetadata.ProbeStatus cachedProbeStatus;
	private static volatile long cachedProbeAtMs;
	public final Path file;
	public final String filename;
	public final long sizeBytes;
	public final String sizeDisplay;
	public final String durationDisplay;
	public final double durationSeconds;
	public final String recordedAtDisplay;
	public final long modifiedMillis;
	public final Path thumbnailPath;

	private VideoMetadata(
		Path file, long sizeBytes, String durationDisplay, double durationSeconds, String recordedAtDisplay, long modifiedMillis, Path thumbnailPath
	) {
		this.file = file;
		this.filename = file != null && file.getFileName() != null ? file.getFileName().toString() : "?";
		this.sizeBytes = sizeBytes;
		this.sizeDisplay = String.format(Locale.ROOT, "%.2f MB", (double)sizeBytes / 1048576.0);
		this.durationDisplay = durationDisplay;
		this.durationSeconds = durationSeconds;
		this.recordedAtDisplay = recordedAtDisplay;
		this.modifiedMillis = modifiedMillis;
		this.thumbnailPath = thumbnailPath;
	}

	public static VideoMetadata read(Path videoFile) {
		if (videoFile == null) {
			return new VideoMetadata(Path.of("?"), 0L, "?", -1.0, "?", 0L, null);
		} else {
			try {
				Path normalized = videoFile.toAbsolutePath().normalize();
				long size = safeSize(normalized);
				long modified = safeModifiedMillis(normalized);
				synchronized (LOCK) {
					VideoMetadata.CacheEntry cached = (VideoMetadata.CacheEntry)CACHE.get(normalized);
					if (cached != null && cached.sizeBytes == size && cached.modifiedMillis == modified) {
						return cached.metadata;
					}
				}

				double durationSeconds = probeDurationSeconds(normalized);
				String durationDisplay = durationSeconds <= 0.0 ? "?" : formatDuration(durationSeconds);
				String recordedAt = resolveRecordedAt(normalized, modified);
				Path thumbnail = ensureThumbnail(normalized, modified);
				VideoMetadata metadata = new VideoMetadata(normalized, size, durationDisplay, durationSeconds, recordedAt, modified, thumbnail);
				synchronized (LOCK) {
					CACHE.put(normalized, new VideoMetadata.CacheEntry(size, modified, metadata));
				}

				return metadata;
			} catch (Throwable var17) {
				RecordableMod.LOGGER.warn("Failed to read video metadata for {}", videoFile, var17);
				return new VideoMetadata(videoFile, 0L, "?", -1.0, "?", safeModifiedMillis(videoFile), null);
			}
		}
	}

	public static VideoMetadata readQuick(Path videoFile) {
		if (videoFile == null) {
			return new VideoMetadata(Path.of("?"), 0L, "?", -1.0, "?", 0L, null);
		} else {
			try {
				Path normalized = videoFile.toAbsolutePath().normalize();
				long size = safeSize(normalized);
				long modified = safeModifiedMillis(normalized);
				synchronized (LOCK) {
					VideoMetadata.CacheEntry cached = (VideoMetadata.CacheEntry)CACHE.get(normalized);
					if (cached != null && cached.sizeBytes == size && cached.modifiedMillis == modified) {
						return cached.metadata;
					}
				}

				String recordedAt = resolveRecordedAt(normalized, modified);
				Path thumbnail = ensureThumbnail(normalized, modified);
				return new VideoMetadata(normalized, size, "...", -1.0, recordedAt, modified, thumbnail);
			} catch (Throwable var10) {
				RecordableMod.LOGGER.warn("Failed to quick-read video metadata for {}", videoFile, var10);
				return new VideoMetadata(videoFile, 0L, "?", -1.0, "?", safeModifiedMillis(videoFile), null);
			}
		}
	}

	public static VideoMetadata probeDurationFor(Path videoFile) {
		if (videoFile == null) {
			return null;
		} else {
			try {
				Path normalized = videoFile.toAbsolutePath().normalize();
				long size = safeSize(normalized);
				long modified = safeModifiedMillis(normalized);
				synchronized (LOCK) {
					VideoMetadata.CacheEntry cached = (VideoMetadata.CacheEntry)CACHE.get(normalized);
					if (cached != null && cached.sizeBytes == size && cached.modifiedMillis == modified && cached.metadata.durationSeconds > 0.0) {
						return cached.metadata;
					}
				}

				double durationSeconds = probeDurationSeconds(normalized);
				String durationDisplay = durationSeconds <= 0.0 ? "?" : formatDuration(durationSeconds);
				String recordedAt = resolveRecordedAt(normalized, modified);
				Path thumbnail = ensureThumbnail(normalized, modified);
				VideoMetadata metadata = new VideoMetadata(normalized, size, durationDisplay, durationSeconds, recordedAt, modified, thumbnail);
				synchronized (LOCK) {
					CACHE.put(normalized, new VideoMetadata.CacheEntry(size, modified, metadata));
				}

				return metadata;
			} catch (Throwable var16) {
				RecordableMod.LOGGER.debug("Duration probe failed for {}", videoFile, var16);
				return null;
			}
		}
	}

	public static void clearCache() {
		synchronized (LOCK) {
			CACHE.clear();
		}
	}

	public static boolean isFfprobeAvailable() {
		return detectFfprobe().available;
	}

	private static double probeDurationSeconds(Path file) {
		if (file == null) {
			return -1.0;
		} else {
			VideoMetadata.ProbeStatus probe = detectFfprobe();
			if (probe.available) {
				double result = runFfprobeDuration(probe.executable, file);
				if (result > 0.0) {
					return result;
				}

				RecordableMod.LOGGER.debug("[Duration] ffprobe found but failed for {}, trying ffmpeg -i fallback", file.getFileName());
			} else {
				RecordableMod.LOGGER.debug("[Duration] ffprobe not available, trying ffmpeg -i fallback for {}", file.getFileName());
			}

			double fallback = runFfmpegDurationFallback(file);
			if (fallback > 0.0) {
				RecordableMod.LOGGER.debug("[Duration] ffmpeg -i fallback got duration {}s for {}", String.format(Locale.ROOT, "%.1f", fallback), file.getFileName());
				return fallback;
			} else {
				double decoded = runFfmpegDecodeDuration(file);
				if (decoded > 0.0) {
					RecordableMod.LOGGER.debug("[Duration] full-decode got duration {}s for {}", String.format(Locale.ROOT, "%.1f", decoded), file.getFileName());
				} else {
					RecordableMod.LOGGER.warn("[Duration] could not determine duration for {} (no header, decode failed)", file.getFileName());
				}

				return decoded;
			}
		}
	}

	private static double runFfmpegDecodeDuration(Path file) {
		FfmpegStatus ffmpegStatus = FFmpegEncoder.detectFfmpeg();
		if (!ffmpegStatus.found()) {
			return -1.0;
		} else {
			try {
				Process process = FfmpegBundleManager.ffmpegProcess(
						ffmpegStatus.executable(), "-nostdin", "-hide_banner", "-i", file.toAbsolutePath().toString(), "-f", "null", "-"
					)
					.redirectErrorStream(true)
					.start();
				double lastTime = -1.0;
				BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

				String line;
				try {
					while ((line = reader.readLine()) != null) {
						Matcher m = TIME_PATTERN.matcher(line);

						while (m.find()) {
							int hours = Integer.parseInt(m.group(1));
							int minutes = Integer.parseInt(m.group(2));
							double seconds = Double.parseDouble(m.group(3));
							double t = (double)hours * 3600.0 + (double)minutes * 60.0 + seconds;
							if (t > lastTime) {
								lastTime = t;
							}
						}
					}
				} catch (Throwable var15) {
					try {
						reader.close();
					} catch (Throwable var14) {
						var15.addSuppressed(var14);
					}

					throw var15;
				}

				reader.close();
				boolean exited = process.waitFor(60L, TimeUnit.SECONDS);
				if (!exited) {
					process.destroyForcibly();
					RecordableMod.LOGGER.warn("[Duration] full-decode timed out (60s) for {}", file.getFileName());
					return lastTime;
				} else {
					return lastTime;
				}
			} catch (Throwable var16) {
				RecordableMod.LOGGER.debug("[Duration] full-decode error for {}: {}", file.getFileName(), var16.getMessage());
				return -1.0;
			}
		}
	}

	private static double runFfprobeDuration(String executable, Path file) {
		try {
			Process process = FfmpegBundleManager.ffmpegProcess(
					executable,
					"-nostdin",
					"-v",
					"error",
					"-select_streams",
					"v:0",
					"-show_entries",
					"format=duration:stream=duration",
					"-of",
					"default=noprint_wrappers=1:nokey=1",
					file.toAbsolutePath().toString()
				)
				.redirectErrorStream(true)
				.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

			String output;
			try {
				StringBuilder sb = new StringBuilder();

				String line;
				while ((line = reader.readLine()) != null) {
					if (sb.length() > 0) {
						sb.append('\n');
					}

					sb.append(line);
				}

				output = sb.toString().trim();
			} catch (Throwable var14) {
				try {
					reader.close();
				} catch (Throwable var12) {
					var14.addSuppressed(var12);
				}

				throw var14;
			}

			reader.close();
			boolean exited = process.waitFor(10L, TimeUnit.SECONDS);
			if (!exited) {
				process.destroyForcibly();
				RecordableMod.LOGGER.warn("[Duration] ffprobe timed out (10s) for {}", file.getFileName());
				return -1.0;
			} else if (output.isBlank()) {
				RecordableMod.LOGGER.debug("[Duration] ffprobe returned empty output for {}", file.getFileName());
				return -1.0;
			} else {
				for (String candidate : output.split("\n")) {
					String trimmed = candidate.trim();
					if (!trimmed.isEmpty()) {
						try {
							double parsed = Double.parseDouble(trimmed);
							if (parsed > 0.0) {
								return parsed;
							}
						} catch (NumberFormatException var13) {
						}
					}
				}

				RecordableMod.LOGGER.debug("[Duration] ffprobe output not parseable for {}: '{}'", file.getFileName(), output);
				return -1.0;
			}
		} catch (Throwable var15) {
			RecordableMod.LOGGER.debug("[Duration] ffprobe execution error for {}: {}", file.getFileName(), var15.getMessage());
			return -1.0;
		}
	}

	private static double runFfmpegDurationFallback(Path file) {
		FfmpegStatus ffmpegStatus = FFmpegEncoder.detectFfmpeg();
		if (!ffmpegStatus.found()) {
			RecordableMod.LOGGER.warn("[Duration] ffmpeg not found either -- cannot determine duration for {}", file.getFileName());
			return -1.0;
		} else {
			try {
				Process process = FfmpegBundleManager.ffmpegProcess(ffmpegStatus.executable(), "-nostdin", "-hide_banner", "-i", file.toAbsolutePath().toString())
					.redirectErrorStream(true)
					.start();
				BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

				String output;
				try {
					StringBuilder sb = new StringBuilder();

					String line;
					while ((line = reader.readLine()) != null) {
						if (sb.length() > 0) {
							sb.append('\n');
						}

						sb.append(line);
					}

					output = sb.toString();
				} catch (Throwable var11) {
					try {
						reader.close();
					} catch (Throwable var10) {
						var11.addSuppressed(var10);
					}

					throw var11;
				}

				reader.close();
				boolean exited = process.waitFor(10L, TimeUnit.SECONDS);
				if (!exited) {
					process.destroyForcibly();
					RecordableMod.LOGGER.warn("[Duration] ffmpeg -i timed out (10s) for {}", file.getFileName());
					return -1.0;
				} else {
					Matcher matcher = DURATION_PATTERN.matcher(output);
					if (matcher.find()) {
						int hours = Integer.parseInt(matcher.group(1));
						int minutes = Integer.parseInt(matcher.group(2));
						double seconds = Double.parseDouble(matcher.group(3));
						return (double)hours * 3600.0 + (double)minutes * 60.0 + seconds;
					} else {
						RecordableMod.LOGGER.debug("[Duration] ffmpeg -i output did not contain duration for {}", file.getFileName());
						return -1.0;
					}
				}
			} catch (Throwable var12) {
				RecordableMod.LOGGER.debug("[Duration] ffmpeg -i fallback error for {}: {}", file.getFileName(), var12.getMessage());
				return -1.0;
			}
		}
	}

	private static VideoMetadata.ProbeStatus detectFfprobe() {
		VideoMetadata.ProbeStatus cached = cachedProbeStatus;
		if (cached != null) {
			return cached;
		} else {
			long now = System.currentTimeMillis();
			RecordableMod.LOGGER.debug("[ffprobe] Starting detection...");
			String configured = System.getenv("RECORDABLE_FFPROBE_PATH");
			if (configured != null && !configured.isBlank()) {
				RecordableMod.LOGGER.debug("[ffprobe] Step 1: checking env var RECORDABLE_FFPROBE_PATH = '{}'", configured.trim());
				VideoMetadata.ProbeStatus userStatus = probeExecutable(configured.trim());
				if (userStatus.available) {
					RecordableMod.LOGGER.debug("[ffprobe] Found via env var: {}", configured.trim());
					cachedProbeStatus = userStatus;
					cachedProbeAtMs = now;
					return userStatus;
				}

				RecordableMod.LOGGER.debug("[ffprobe] Env var path not working");
			}

			try {
				Path bundleDir = FfmpegBundleManager.getBundleDirectory();
				RecordableMod.LOGGER
					.debug("[ffprobe] Step 2: checking bundle dir: {} (exists={})", bundleDir, bundleDir != null && Files.isDirectory(bundleDir, new LinkOption[0]));
				if (bundleDir != null) {
					String probeName = PlatformUtils.isWindows() ? "ffprobe.exe" : "ffprobe";
					Path bundledProbe = bundleDir.resolve(probeName);
					boolean isFile = Files.isRegularFile(bundledProbe, new LinkOption[0]);
					boolean isReadable = isFile && Files.isReadable(bundledProbe);
					RecordableMod.LOGGER.debug("[ffprobe]   Candidate: {} (isFile={}, isReadable={})", new Object[]{bundledProbe, isFile, isReadable});
					if (isFile && isReadable) {
						VideoMetadata.ProbeStatus bundledStatus = probeExecutable(bundledProbe.toAbsolutePath().toString());
						if (bundledStatus.available) {
							RecordableMod.LOGGER.debug("[ffprobe] Found in bundle dir: {}", bundledProbe);
							cachedProbeStatus = bundledStatus;
							cachedProbeAtMs = now;
							return bundledStatus;
						}

						RecordableMod.LOGGER.debug("[ffprobe]   File exists but execution check failed");
					}
				}
			} catch (Throwable var12) {
				RecordableMod.LOGGER.warn("[ffprobe] Error checking bundled path: {}", var12.getMessage());
			}

			try {
				FfmpegStatus ffmpegStatus = FFmpegEncoder.detectFfmpeg();
				RecordableMod.LOGGER
					.debug("[ffprobe] Step 3: ffmpeg found={}, executable='{}'", ffmpegStatus.found(), ffmpegStatus.found() ? ffmpegStatus.executable() : "n/a");
				if (ffmpegStatus.found()) {
					Path ffmpegPath = Path.of(ffmpegStatus.executable());
					Path parent = ffmpegPath.getParent();
					RecordableMod.LOGGER.debug("[ffprobe]   ffmpeg parent dir: {}", parent);
					if (parent != null) {
						String probeName = PlatformUtils.isWindows() ? "ffprobe.exe" : "ffprobe";
						Path siblingProbe = parent.resolve(probeName);
						boolean siblingExists = Files.isRegularFile(siblingProbe, new LinkOption[0]);
						RecordableMod.LOGGER.debug("[ffprobe]   Sibling candidate: {} (exists={})", siblingProbe, siblingExists);
						if (siblingExists) {
							VideoMetadata.ProbeStatus siblingStatus = probeExecutable(siblingProbe.toAbsolutePath().toString());
							if (siblingStatus.available) {
								RecordableMod.LOGGER.debug("[ffprobe] Found as sibling of ffmpeg: {}", siblingProbe);
								cachedProbeStatus = siblingStatus;
								cachedProbeAtMs = now;
								return siblingStatus;
							}

							RecordableMod.LOGGER.debug("[ffprobe]   Sibling exists but execution check failed");
						}
					} else {
						RecordableMod.LOGGER.debug("[ffprobe]   ffmpeg path has no parent (bare command name on PATH)");
					}
				}
			} catch (Throwable var11) {
				RecordableMod.LOGGER.warn("[ffprobe] Error deriving from ffmpeg path: {}", var11.getMessage());
			}

			RecordableMod.LOGGER.debug("[ffprobe] Step 4: trying system PATH 'ffprobe'...");
			VideoMetadata.ProbeStatus systemStatus = probeExecutable("ffprobe");
			RecordableMod.LOGGER.debug("[ffprobe] System PATH result: available={}", systemStatus.available);
			if (!systemStatus.available) {
				RecordableMod.LOGGER.debug("[ffprobe] NOT FOUND via any method. Duration will use ffmpeg -i fallback.");
			}

			cachedProbeStatus = systemStatus;
			cachedProbeAtMs = now;
			return systemStatus;
		}
	}

	private static VideoMetadata.ProbeStatus probeExecutable(String executable) {
		try {
			Process process = FfmpegBundleManager.ffmpegProcess(executable, "-version").redirectErrorStream(true).start();
			boolean exited = process.waitFor(5L, TimeUnit.SECONDS);
			if (!exited) {
				process.destroyForcibly();
				RecordableMod.LOGGER.debug("[ffprobe] Probe timed out (5s) for: {}", executable);
				return new VideoMetadata.ProbeStatus(false, executable);
			} else {
				boolean ok = process.exitValue() == 0;
				if (!ok) {
					RecordableMod.LOGGER.debug("[ffprobe] Probe returned exit code {} for: {}", process.exitValue(), executable);
				}

				return new VideoMetadata.ProbeStatus(ok, executable);
			}
		} catch (Throwable var4) {
			RecordableMod.LOGGER.debug("[ffprobe] Probe exception for '{}': {}", executable, var4.getMessage());
			return new VideoMetadata.ProbeStatus(false, executable);
		}
	}

	private static Path ensureThumbnail(Path videoFile, long modifiedMillis) {
		if (videoFile == null) {
			return null;
		} else {
			FfmpegStatus ffmpeg = FFmpegEncoder.detectFfmpeg();
			if (!ffmpeg.found()) {
				RecordableMod.LOGGER.debug("Skipping thumbnail for {} because ffmpeg was not found.", videoFile);
				return null;
			} else {
				Path thumbnailDir = FabricLoader.getInstance().getGameDir().resolve("recordable").resolve("thumbnails");

				try {
					Files.createDirectories(thumbnailDir);
					if (!Files.isDirectory(thumbnailDir, new LinkOption[0]) || !Files.isWritable(thumbnailDir)) {
						RecordableMod.LOGGER.warn("Thumbnail directory is not writable: {}", thumbnailDir);
						return null;
					}

					String key = sha1(videoFile.toString() + ":" + modifiedMillis);
					Path thumbnailPath = thumbnailDir.resolve(key + ".png");
					if (Files.exists(thumbnailPath, new LinkOption[0]) && safeSize(thumbnailPath) > 0L) {
						return thumbnailPath;
					}

					ProcessBuilder builder = FfmpegBundleManager.ffmpegProcess(
						ffmpeg.executable(),
						"-nostdin",
						"-hide_banner",
						"-loglevel",
						"error",
						"-y",
						"-i",
						videoFile.toAbsolutePath().toString(),
						"-ss",
						"00:00:00.500",
						"-frames:v",
						"1",
						"-vf",
						"thumbnail,scale=192:-1",
						thumbnailPath.toAbsolutePath().toString()
					);
					builder.redirectErrorStream(true);
					Process process = builder.start();
					boolean exited = process.waitFor(4L, TimeUnit.SECONDS);
					if (!exited) {
						process.destroyForcibly();
						RecordableMod.LOGGER.debug("Thumbnail extraction timed out for {}", videoFile);
						return null;
					}

					String ffmpegOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
					if (process.exitValue() == 0 && Files.exists(thumbnailPath, new LinkOption[0]) && safeSize(thumbnailPath) > 0L) {
						return thumbnailPath;
					}

					if (!ffmpegOutput.isBlank()) {
						RecordableMod.LOGGER.debug("Thumbnail extraction failed for {}: {}", videoFile, ffmpegOutput);
					} else {
						RecordableMod.LOGGER.debug("Thumbnail extraction failed for {} with exit code {}", videoFile, process.exitValue());
					}
				} catch (Throwable var11) {
					RecordableMod.LOGGER.debug("Thumbnail extraction failed for {}", videoFile, var11);
				}

				return null;
			}
		}
	}

	private static String resolveRecordedAt(Path videoFile, long modifiedMillis) {
		try {
			String name = videoFile != null && videoFile.getFileName() != null ? videoFile.getFileName().toString() : "";
			Matcher matcher = FILENAME_TIMESTAMP.matcher(name);
			if (matcher.find()) {
				LocalDateTime parsed = LocalDateTime.parse(matcher.group(1), FILE_TS);
				return DISPLAY_TS.format(parsed);
			}
		} catch (Throwable var6) {
		}

		Instant instant = Instant.ofEpochMilli(Math.max(0L, modifiedMillis));
		return DISPLAY_TS.format(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
	}

	private static String formatDuration(double durationSeconds) {
		long seconds = Math.max(0L, Math.round(durationSeconds));
		long hours = seconds / 3600L;
		long minutes = seconds % 3600L / 60L;
		long remaining = seconds % 60L;
		return hours > 0L ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remaining) : String.format(Locale.ROOT, "%02d:%02d", minutes, remaining);
	}

	private static long safeSize(Path path) {
		if (path == null) {
			return 0L;
		} else {
			try {
				return Files.exists(path, new LinkOption[0]) ? Files.size(path) : 0L;
			} catch (IOException var2) {
				return 0L;
			}
		}
	}

	private static long safeModifiedMillis(Path path) {
		if (path == null) {
			return 0L;
		} else {
			try {
				FileTime modified = Files.getLastModifiedTime(path);
				return modified.toMillis();
			} catch (IOException var2) {
				return 0L;
			}
		}
	}

	private static String sha1(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder builder = new StringBuilder(hashed.length * 2);

			for (byte b : hashed) {
				builder.append(String.format(Locale.ROOT, "%02x", b));
			}

			return builder.toString();
		} catch (Throwable var8) {
			return Integer.toHexString(value.hashCode());
		}
	}

	private static record CacheEntry(long sizeBytes, long modifiedMillis, VideoMetadata metadata) {
	}

	private static record ProbeStatus(boolean available, String executable) {
	}
}
