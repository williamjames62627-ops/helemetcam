package dev.recordable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class StorageManager {
	private static final String[] VIDEO_EXTS = new String[]{".mp4", ".mkv", ".webm", ".mov", ".avi", ".gif"};

	private StorageManager() {
	}

	public static boolean isVideoFile(String name) {
		String lower = name.toLowerCase(Locale.ROOT);

		for (String ext : VIDEO_EXTS) {
			if (lower.endsWith(ext)) {
				return true;
			}
		}

		return false;
	}

	public static List<StorageManager.StoredFile> listRecordings(RecordableConfig config) {
		List<StorageManager.StoredFile> out = new ArrayList();
		Path dir = config.getOutputDirectory();
		if (dir != null && Files.isDirectory(dir, new LinkOption[0])) {
			try {
				Stream<Path> stream = Files.list(dir);

				try {
					stream.filter(x$0 -> Files.isRegularFile(x$0, new LinkOption[0])).filter(p -> isVideoFile(p.getFileName().toString())).forEach(p -> {
						long size = safeSize(p);
						long mod = safeModified(p);
						boolean prot = config.storageProtectedFiles != null && config.storageProtectedFiles.contains(p.getFileName().toString());
						out.add(new StorageManager.StoredFile(p, p.getFileName().toString(), size, mod, prot));
					});
				} catch (Throwable var7) {
					if (stream != null) {
						try {
							stream.close();
						} catch (Throwable var6) {
							var7.addSuppressed(var6);
						}
					}

					throw var7;
				}

				if (stream != null) {
					stream.close();
				}
			} catch (IOException var8) {
				RecordableMod.LOGGER.warn("StorageManager: failed to list {}: {}", dir, var8.getMessage());
			}

			out.sort(Comparator.comparingLong(StorageManager.StoredFile::modifiedMillis).reversed());
			return out;
		} else {
			return out;
		}
	}

	public static StorageManager.StorageStats computeStats(RecordableConfig config) {
		List<StorageManager.StoredFile> files = listRecordings(config);
		long total = 0L;

		for (StorageManager.StoredFile f : files) {
			total += f.sizeBytes();
		}

		long free = -1L;
		long diskTotal = -1L;
		int usedPct = 0;

		try {
			Path dir = config.getOutputDirectory();
			if (dir != null) {
				if (!Files.exists(dir, new LinkOption[0])) {
					Files.createDirectories(dir);
				}

				FileStore store = Files.getFileStore(dir);
				diskTotal = store.getTotalSpace();
				free = store.getUsableSpace();
				if (diskTotal > 0L) {
					usedPct = (int)(100L - free * 100L / diskTotal);
				}
			}
		} catch (Exception var11) {
			RecordableMod.LOGGER.debug("StorageManager: disk stat failed: {}", var11.getMessage());
		}

		return new StorageManager.StorageStats(total, files.size(), free, diskTotal, usedPct);
	}

	public static void toggleProtected(RecordableConfig config, String filename) {
		if (config.storageProtectedFiles == null) {
			config.storageProtectedFiles = new ArrayList();
		}

		if (config.storageProtectedFiles.contains(filename)) {
			config.storageProtectedFiles.remove(filename);
		} else {
			config.storageProtectedFiles.add(filename);
		}

		config.save();
	}

	public static boolean isProtected(RecordableConfig config, String filename) {
		return config.storageProtectedFiles != null && config.storageProtectedFiles.contains(filename);
	}

	public static boolean deleteRecording(RecordableConfig config, Path file) {
		String name = file.getFileName().toString();
		if (isProtected(config, name)) {
			RecordableMod.LOGGER.info("StorageManager: refusing to delete protected file {}", name);
			return false;
		} else {
			boolean ok = deleteWithSidecars(file);
			if (ok) {
				VideoMetadata.clearCache();
			}

			return ok;
		}
	}

	public static StorageManager.CleanupResult runCleanup(RecordableConfig config, boolean force) {
		List<String> deleted = new ArrayList();
		long freed = 0L;
		if (!force && !config.autoCleanupEnabled) {
			return new StorageManager.CleanupResult(0, 0L, deleted);
		} else {
			List<StorageManager.StoredFile> files = listRecordings(config);
			long now = System.currentTimeMillis();
			long ageCutoffMs = (long)config.autoCleanupOlderThanDays * 24L * 60L * 60L * 1000L;

			for (StorageManager.StoredFile f : files) {
				if (!f.protectedFlag() && now - f.modifiedMillis() > ageCutoffMs && deleteWithSidecars(f.path())) {
					deleted.add(f.filename());
					freed += f.sizeBytes();
				}
			}

			if (config.autoCleanupMaxTotalMB > 0) {
				long capBytes = (long)config.autoCleanupMaxTotalMB * 1024L * 1024L;
				List<StorageManager.StoredFile> remaining = listRecordings(config);
				long total = 0L;

				for (StorageManager.StoredFile fx : remaining) {
					total += fx.sizeBytes();
				}

				remaining.sort(Comparator.comparingLong(StorageManager.StoredFile::modifiedMillis));

				for (StorageManager.StoredFile fx : remaining) {
					if (total <= capBytes) {
						break;
					}

					if (!fx.protectedFlag() && deleteWithSidecars(fx.path())) {
						deleted.add(fx.filename());
						freed += fx.sizeBytes();
						total -= fx.sizeBytes();
					}
				}
			}

			if (!deleted.isEmpty()) {
				VideoMetadata.clearCache();
			}

			return new StorageManager.CleanupResult(deleted.size(), freed, deleted);
		}
	}

	public static Path compressRecording(RecordableConfig config, Path source) {
		if (source != null && Files.isRegularFile(source, new LinkOption[0])) {
			String ffmpeg = FfmpegBundleManager.getBundledFfmpegPath();
			if (ffmpeg != null && !ffmpeg.isBlank()) {
				String base = source.getFileName().toString();
				int dot = base.lastIndexOf(46);
				String stem = dot > 0 ? base.substring(0, dot) : base;
				Path out = source.resolveSibling(stem + "_compressed.mp4");
				int crf = Math.max(0, Math.min(51, config.storageCompressionCrf));

				try {
					List<String> cmd = new ArrayList();
					cmd.add(ffmpeg);
					cmd.add("-y");
					cmd.add("-i");
					cmd.add(source.toString());
					cmd.add("-c:v");
					cmd.add("libx264");
					cmd.add("-crf");
					cmd.add(String.valueOf(crf));
					cmd.add("-preset");
					cmd.add("medium");
					cmd.add("-c:a");
					cmd.add("aac");
					cmd.add(out.toString());
					Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
					InputStream in = p.getInputStream();

					try {
						in.readAllBytes();
					} catch (Throwable var14) {
						if (in != null) {
							try {
								in.close();
							} catch (Throwable var13) {
								var14.addSuppressed(var13);
							}
						}

						throw var14;
					}

					if (in != null) {
						in.close();
					}

					int code = p.waitFor();
					if (code == 0 && Files.isRegularFile(out, new LinkOption[0])) {
						VideoMetadata.clearCache();
						return out;
					}

					RecordableMod.LOGGER.warn("StorageManager: compression exited with code {}", code);
					Files.deleteIfExists(out);
				} catch (Exception var15) {
					RecordableMod.LOGGER.warn("StorageManager: compression failed: {}", var15.getMessage());
				}

				return null;
			} else {
				RecordableMod.LOGGER.warn("StorageManager: ffmpeg unavailable, cannot compress");
				return null;
			}
		} else {
			return null;
		}
	}

	private static boolean deleteWithSidecars(Path file) {
		boolean ok;
		try {
			ok = Files.deleteIfExists(file);
		} catch (IOException var8) {
			RecordableMod.LOGGER.warn("StorageManager: failed to delete {}: {}", file, var8.getMessage());
			return false;
		}

		String base = file.getFileName().toString();
		int dot = base.lastIndexOf(46);
		String stem = dot > 0 ? base.substring(0, dot) : base;

		try {
			Files.deleteIfExists(file.resolveSibling(stem + "_markers.txt"));
		} catch (IOException var7) {
		}

		try {
			Files.deleteIfExists(file.resolveSibling(stem + "_chapters.txt"));
		} catch (IOException var6) {
		}

		return ok;
	}

	private static long safeSize(Path p) {
		try {
			return Files.size(p);
		} catch (IOException var2) {
			return 0L;
		}
	}

	private static long safeModified(Path p) {
		try {
			return Files.getLastModifiedTime(p).toMillis();
		} catch (IOException var2) {
			return 0L;
		}
	}

	public static String humanReadable(long bytes) {
		if (bytes < 0L) {
			return "Unknown";
		} else if (bytes < 1024L) {
			return bytes + " B";
		} else {
			double kb = (double)bytes / 1024.0;
			if (kb < 1024.0) {
				return String.format(Locale.ROOT, "%.1f KB", kb);
			} else {
				double mb = kb / 1024.0;
				return mb < 1024.0 ? String.format(Locale.ROOT, "%.1f MB", mb) : String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
			}
		}
	}

	public static record CleanupResult(int filesDeleted, long bytesFreed, List<String> deletedNames) {
		public String bytesFreedDisplay() {
			return StorageManager.humanReadable(this.bytesFreed);
		}
	}

	public static record StorageStats(long recordingsBytes, int recordingCount, long diskFreeBytes, long diskTotalBytes, int diskUsedPercent) {
		public String recordingsDisplay() {
			return StorageManager.humanReadable(this.recordingsBytes);
		}

		public String diskFreeDisplay() {
			return StorageManager.humanReadable(this.diskFreeBytes);
		}

		public String diskTotalDisplay() {
			return StorageManager.humanReadable(this.diskTotalBytes);
		}
	}

	public static record StoredFile(Path path, String filename, long sizeBytes, long modifiedMillis, boolean protectedFlag) {
		public String sizeDisplay() {
			return StorageManager.humanReadable(this.sizeBytes);
		}
	}
}
