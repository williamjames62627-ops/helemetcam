package dev.recordable;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class DiskSpaceGuardian {
	private DiskSpaceGuardian() {
	}

	public static DiskSpaceGuardian.DiskCheckResult check(Path outputDir, RecordableConfig config) {
		try {
			if (!Files.exists(outputDir, new LinkOption[0])) {
				Files.createDirectories(outputDir);
			}

			FileStore store = Files.getFileStore(outputDir);
			long totalBytes = store.getTotalSpace();
			long freeBytes = store.getUsableSpace();
			if (totalBytes <= 0L) {
				return new DiskSpaceGuardian.DiskCheckResult(DiskSpaceGuardian.DiskStatus.OK, -1L, -1L, 0, "Could not determine disk space.");
			} else {
				long totalMB = totalBytes / 1048576L;
				long freeMB = freeBytes / 1048576L;
				int usedPercent = (int)(100L - freeBytes * 100L / totalBytes);
				if (usedPercent >= config.diskSpaceBlockPercent || freeMB < 100L) {
					return new DiskSpaceGuardian.DiskCheckResult(
						DiskSpaceGuardian.DiskStatus.BLOCKED,
						freeMB,
						totalMB,
						usedPercent,
						"§c⛔ Disk is " + usedPercent + "% full (" + freeMB + " MB free). Recording blocked to prevent disk full errors."
					);
				} else {
					return usedPercent < config.diskSpaceWarnPercent && freeMB >= (long)config.diskSpaceMinFreeMB
						? new DiskSpaceGuardian.DiskCheckResult(
							DiskSpaceGuardian.DiskStatus.OK, freeMB, totalMB, usedPercent, "Disk space OK: " + freeMB + " MB free (" + usedPercent + "% used)."
						)
						: new DiskSpaceGuardian.DiskCheckResult(
							DiskSpaceGuardian.DiskStatus.WARNING,
							freeMB,
							totalMB,
							usedPercent,
							"§e⚠ Disk is " + usedPercent + "% full (" + freeMB + " MB free). Recording may be cut short."
						);
				}
			}
		} catch (Exception var12) {
			RecordableMod.LOGGER.warn("Failed to check disk space for {}: {}", outputDir, var12.getMessage());
			return new DiskSpaceGuardian.DiskCheckResult(DiskSpaceGuardian.DiskStatus.OK, -1L, -1L, 0, "Could not check disk space: " + var12.getMessage());
		}
	}

	public static long getFreeSpaceMB(Path path) {
		try {
			if (!Files.exists(path, new LinkOption[0])) {
				Files.createDirectories(path);
			}

			FileStore store = Files.getFileStore(path);
			return store.getUsableSpace() / 1048576L;
		} catch (Exception var2) {
			RecordableMod.LOGGER.debug("Could not get free space for {}", path, var2);
			return -1L;
		}
	}

	public static String getFormattedFreeSpace(Path path) {
		long freeMB = getFreeSpaceMB(path);
		if (freeMB < 0L) {
			return "Unknown";
		} else {
			return freeMB >= 1024L ? String.format("%.1f GB", (double)freeMB / 1024.0) : freeMB + " MB";
		}
	}

	public static record DiskCheckResult(DiskSpaceGuardian.DiskStatus status, long freeSpaceMB, long totalSpaceMB, int usedPercent, String message) {
	}

	public static enum DiskStatus {
		OK,
		WARNING,
		BLOCKED;
	}
}
