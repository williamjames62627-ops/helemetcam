package dev.recordable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;

public final class AndroidPlatform {
	private static final String[] POJAV_PACKAGES = new String[]{"net.kdt.pojavlaunch", "net.kdt.pojavlaunch.debug"};
	private static final String[] ZALITH_PACKAGES = new String[]{
		"com.movtery.zalithlauncher.v2", "com.movtery.zalithlauncher", "com.movtery.pojavzh", "com.movtery.zalern", "com.movtery.zalith"
	};
	private static final String[] FCL_PACKAGES = new String[]{"com.tungsten.fcl", "com.tungsten.fclauncher"};
	private static volatile AndroidPlatform.Launcher cachedLauncher;
	private static volatile String cachedPackageName;
	private static volatile boolean packageProbed;
	private static volatile AndroidPlatform.Architecture cachedArch;
	public static final String FFMPEG_EXEC_NAME = "recordable-ffmpeg";
	public static final String FFPROBE_EXEC_NAME = "recordable-ffprobe";
	public static final String TERMUX_BIN_DIR = "/data/data/com.termux/files/usr/bin";
	public static final String TERMUX_FFMPEG_PATH = "/data/data/com.termux/files/usr/bin/ffmpeg";
	public static final String TERMUX_FFPROBE_PATH = "/data/data/com.termux/files/usr/bin/ffprobe";

	private AndroidPlatform() {
	}

	public static AndroidPlatform.Launcher detectLauncher() {
		AndroidPlatform.Launcher cached = cachedLauncher;
		if (cached != null) {
			return cached;
		} else {
			AndroidPlatform.Launcher detected = probeLauncher();
			cachedLauncher = detected;
			RecordableMod.LOGGER.info("[AndroidPlatform] Launcher: {} (package={})", detected.displayName(), getPackageName());
			return detected;
		}
	}

	private static AndroidPlatform.Launcher probeLauncher() {
		String pkg = getPackageName();
		if (pkg != null) {
			AndroidPlatform.Launcher byPkg = classifyPackage(pkg);
			if (byPkg != AndroidPlatform.Launcher.UNKNOWN) {
				return byPkg;
			}
		}

		for (String p : POJAV_PACKAGES) {
			if (dataDirExists(p)) {
				return AndroidPlatform.Launcher.POJAV;
			}
		}

		for (String px : ZALITH_PACKAGES) {
			if (dataDirExists(px)) {
				return AndroidPlatform.Launcher.ZALITH;
			}
		}

		for (String pxx : FCL_PACKAGES) {
			if (dataDirExists(pxx)) {
				return AndroidPlatform.Launcher.FCL;
			}
		}

		return AndroidPlatform.Launcher.UNKNOWN;
	}

	private static AndroidPlatform.Launcher classifyPackage(String pkg) {
		String lower = pkg.toLowerCase(Locale.ROOT);

		for (String p : POJAV_PACKAGES) {
			if (lower.equals(p)) {
				return AndroidPlatform.Launcher.POJAV;
			}
		}

		for (String px : ZALITH_PACKAGES) {
			if (lower.equals(px)) {
				return AndroidPlatform.Launcher.ZALITH;
			}
		}

		for (String pxx : FCL_PACKAGES) {
			if (lower.equals(pxx)) {
				return AndroidPlatform.Launcher.FCL;
			}
		}

		if (lower.contains("pojav")) {
			return AndroidPlatform.Launcher.POJAV;
		} else if (lower.contains("zalith") || lower.contains("movtery")) {
			return AndroidPlatform.Launcher.ZALITH;
		} else {
			return !lower.contains("tungsten") && !lower.contains("fcl") ? AndroidPlatform.Launcher.UNKNOWN : AndroidPlatform.Launcher.FCL;
		}
	}

	private static boolean dataDirExists(String pkg) {
		return Files.exists(Path.of("/data/data/" + pkg), new LinkOption[0])
			|| Files.exists(Path.of("/data/user/0/" + pkg), new LinkOption[0])
			|| Files.exists(Path.of("/storage/emulated/0/Android/data/" + pkg), new LinkOption[0]);
	}

	public static boolean isPojavLauncher() {
		return detectLauncher() == AndroidPlatform.Launcher.POJAV || System.getenv("POJAV_NATIVEDIR") != null;
	}

	public static boolean isZalithLauncher() {
		return detectLauncher() == AndroidPlatform.Launcher.ZALITH;
	}

	public static boolean isFoldCraftLauncher() {
		return detectLauncher() == AndroidPlatform.Launcher.FCL;
	}

	public static String getPackageName() {
		if (packageProbed) {
			return cachedPackageName;
		} else {
			synchronized (AndroidPlatform.class) {
				if (packageProbed) {
					return cachedPackageName;
				} else {
					cachedPackageName = probePackageName();
					packageProbed = true;
					return cachedPackageName;
				}
			}
		}
	}

	private static String probePackageName() {
		List<String> candidates = new ArrayList();

		try {
			candidates.add(FabricLoader.getInstance().getGameDir().toAbsolutePath().toString());
		} catch (Exception var4) {
		}

		candidates.add(System.getProperty("user.dir", ""));
		candidates.add(System.getProperty("user.home", ""));
		candidates.add(System.getProperty("java.io.tmpdir", ""));
		candidates.add(System.getenv("POJAV_NATIVEDIR"));
		candidates.add(System.getenv("HOME"));

		for (String c : candidates) {
			String pkg = extractPackageName(c);
			if (pkg != null) {
				return pkg;
			}
		}

		return null;
	}

	static String extractPackageName(String path) {
		if (path != null && !path.isEmpty()) {
			String[] prefixes = new String[]{"/data/data/", "/data/user/0/", "/data/user/", "/storage/emulated/0/Android/data/", "/sdcard/Android/data/"};

			for (String prefix : prefixes) {
				int idx = path.indexOf(prefix);
				if (idx >= 0) {
					String rest = path.substring(idx + prefix.length());
					if (prefix.equals("/data/user/") && !rest.isEmpty() && Character.isDigit(rest.charAt(0))) {
						int s = rest.indexOf(47);
						if (s < 0) {
							continue;
						}

						rest = rest.substring(s + 1);
					}

					int slash = rest.indexOf(47);
					String candidate = slash > 0 ? rest.substring(0, slash) : rest;
					if (looksLikePackage(candidate)) {
						return candidate;
					}
				}
			}

			return null;
		} else {
			return null;
		}
	}

	private static boolean looksLikePackage(String s) {
		return s != null && s.contains(".") && !s.contains(" ") && s.length() > 3 && s.matches("[A-Za-z0-9_.]+");
	}

	public static AndroidPlatform.Architecture detectArchitecture() {
		AndroidPlatform.Architecture cached = cachedArch;
		if (cached != null) {
			return cached;
		} else {
			AndroidPlatform.Architecture detected = probeArchitecture();
			cachedArch = detected;
			RecordableMod.LOGGER
				.info("[AndroidPlatform] Architecture: {} (abi={}, os.arch={})", new Object[]{detected.name(), detected.abi(), System.getProperty("os.arch", "?")});
			return detected;
		}
	}

	private static AndroidPlatform.Architecture probeArchitecture() {
		String osArch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
		if (osArch.contains("aarch64") || osArch.contains("arm64")) {
			return AndroidPlatform.Architecture.ARM64;
		} else if (osArch.contains("armv8")) {
			return AndroidPlatform.Architecture.ARM64;
		} else if (osArch.contains("arm")) {
			return AndroidPlatform.Architecture.ARM32;
		} else if (osArch.contains("x86_64") || osArch.contains("amd64")) {
			return AndroidPlatform.Architecture.X86_64;
		} else if (!osArch.contains("x86") && !osArch.contains("i686") && !osArch.contains("i386")) {
			try {
				Path cpuinfo = Path.of("/proc/cpuinfo");
				if (Files.exists(cpuinfo, new LinkOption[0])) {
					String content = Files.readString(cpuinfo).toLowerCase(Locale.ROOT);
					if (!content.contains("aarch64") && !content.contains("armv8")) {
						if (!content.contains("armv7") && !content.contains("arm")) {
							return AndroidPlatform.Architecture.UNKNOWN;
						}

						return AndroidPlatform.Architecture.ARM32;
					}

					return AndroidPlatform.Architecture.ARM64;
				}
			} catch (Exception var3) {
			}

			return AndroidPlatform.Architecture.UNKNOWN;
		} else {
			return AndroidPlatform.Architecture.X86;
		}
	}

	public static boolean isArm64() {
		return detectArchitecture() == AndroidPlatform.Architecture.ARM64;
	}

	public static boolean isArm32() {
		return detectArchitecture() == AndroidPlatform.Architecture.ARM32;
	}

	public static boolean isAndroidArm() {
		return PlatformUtils.isAndroid() && (isArm64() || isArm32());
	}

	public static boolean isStandardLinuxArm() {
		if (PlatformUtils.isAndroid()) {
			return false;
		} else {
			String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
			return osName.contains("linux") && (isArm64() || isArm32());
		}
	}

	public static Path findMinecraftDirectory() {
		try {
			Path gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
			if (Files.isDirectory(gameDir, new LinkOption[0])) {
				return gameDir;
			}
		} catch (Exception var2) {
			RecordableMod.LOGGER.debug("[AndroidPlatform] Fabric game dir unavailable: {}", var2.getMessage());
		}

		for (Path p : knownMinecraftCandidates()) {
			if (Files.isDirectory(p, new LinkOption[0])) {
				return p;
			}
		}

		return FabricLoader.getInstance().getGameDir();
	}

	private static List<Path> knownMinecraftCandidates() {
		List<Path> out = new ArrayList();
		String pkg = getPackageName();
		if (pkg != null) {
			out.add(Path.of("/storage/emulated/0/Android/data/" + pkg + "/files/.minecraft"));
			out.add(Path.of("/sdcard/Android/data/" + pkg + "/files/.minecraft"));
			out.add(Path.of("/data/data/" + pkg + "/files/.minecraft"));
			out.add(Path.of("/data/user/0/" + pkg + "/files/.minecraft"));
		}

		out.add(Path.of("/storage/emulated/0/games/PojavLauncher/.minecraft"));
		out.add(Path.of("/sdcard/games/PojavLauncher/.minecraft"));
		return out;
	}

	public static Path getTermuxFfmpegPath() {
		Path p = Path.of("/data/data/com.termux/files/usr/bin/ffmpeg");
		return Files.isRegularFile(p, new LinkOption[0]) ? p : null;
	}

	public static Path getTermuxFfprobePath() {
		Path p = Path.of("/data/data/com.termux/files/usr/bin/ffprobe");
		return Files.isRegularFile(p, new LinkOption[0]) ? p : null;
	}

	public static boolean hasTermuxFfmpeg() {
		return getTermuxFfmpegPath() != null;
	}

	public static List<Path> getExecCapableDirectories() {
		Set<Path> dirs = new LinkedHashSet();
		if (Files.isDirectory(Path.of("/data/data/com.termux/files/usr/bin"), new LinkOption[0])) {
			addDir(dirs, Path.of("/data/data/com.termux/files/usr/bin"));
		}

		String nativeDir = System.getenv("POJAV_NATIVEDIR");
		if (nativeDir != null && !nativeDir.isBlank()) {
			addDir(dirs, Path.of(nativeDir));
		}

		String tmpDir = System.getProperty("java.io.tmpdir", "");
		if (!tmpDir.isBlank()) {
			addDir(dirs, Path.of(tmpDir).resolve("recordable"));
			addDir(dirs, Path.of(tmpDir));
		}

		String pkg = getPackageName();
		if (pkg != null) {
			addDir(dirs, Path.of("/data/data/" + pkg + "/files/recordable"));
			addDir(dirs, Path.of("/data/user/0/" + pkg + "/files/recordable"));
			addDir(dirs, Path.of("/data/data/" + pkg + "/cache/recordable"));
			addDir(dirs, Path.of("/data/user/0/" + pkg + "/cache/recordable"));
		}

		String tmpEnv = System.getenv("TMPDIR");
		if (tmpEnv != null && !tmpEnv.isBlank()) {
			addDir(dirs, Path.of(tmpEnv).resolve("recordable"));
		}

		return new ArrayList(dirs);
	}

	private static void addDir(Set<Path> set, Path p) {
		if (p != null) {
			set.add(p.toAbsolutePath().normalize());
		}
	}

	public static Path resolveFfmpegExecDir() {
		Path firstWritable = null;

		for (Path dir : getExecCapableDirectories()) {
			try {
				Files.createDirectories(dir);
			} catch (IOException var4) {
				RecordableMod.LOGGER.debug("[AndroidPlatform] Cannot create {}: {}", dir, var4.getMessage());
				continue;
			}

			if (Files.isWritable(dir)) {
				if (firstWritable == null) {
					firstWritable = dir;
				}

				if (isDirExecCapable(dir)) {
					RecordableMod.LOGGER.info("[AndroidPlatform] Selected exec-capable FFmpeg dir: {}", dir);
					return dir;
				}
			}
		}

		if (firstWritable != null) {
			RecordableMod.LOGGER.warn("[AndroidPlatform] No verified exec-capable dir; falling back to writable dir (exec may be blocked): {}", firstWritable);
		} else {
			RecordableMod.LOGGER.warn("[AndroidPlatform] No writable directory found for FFmpeg binary");
		}

		return firstWritable;
	}

	private static boolean isDirExecCapable(Path dir) {
		Path probe = dir.resolve(".recordable-exec-probe");

		boolean e;
		try {
			Files.write(probe, "#!/system/bin/sh\nexit 0\n".getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
			if (probe.toFile().setExecutable(true, false) || Files.isExecutable(probe)) {
				Process proc = new ProcessBuilder(new String[]{probe.toAbsolutePath().toString()}).redirectErrorStream(true).start();
				boolean exited = proc.waitFor(3L, TimeUnit.SECONDS);
				if (!exited) {
					proc.destroyForcibly();
					return false;
				}

				return proc.exitValue() == 0;
			}

			e = false;
		} catch (Exception var16) {
			RecordableMod.LOGGER.debug("[AndroidPlatform] Exec probe failed in {}: {}", dir, var16.getMessage());
			return false;
		} finally {
			try {
				Files.deleteIfExists(probe);
			} catch (IOException var15) {
			}
		}

		return e;
	}

	public static Path resolveRecordingsOutputDir() {
		Path mcDir = findMinecraftDirectory();
		Path recordings = mcDir.resolve("recordable").resolve("recordings");

		try {
			Files.createDirectories(recordings);
		} catch (IOException var3) {
			RecordableMod.LOGGER.warn("[AndroidPlatform] Could not create recordings dir {}: {}", recordings, var3.getMessage());
		}

		return recordings.toAbsolutePath().normalize();
	}

	public static void logDiagnostics() {
		if (PlatformUtils.isAndroid()) {
			RecordableMod.LOGGER.info("[AndroidPlatform] ── Android environment ──");
			RecordableMod.LOGGER.info("[AndroidPlatform]   Launcher:     {}", detectLauncher().displayName());
			RecordableMod.LOGGER.info("[AndroidPlatform]   Package:      {}", getPackageName());
			RecordableMod.LOGGER.info("[AndroidPlatform]   Architecture: {} ({})", detectArchitecture().name(), detectArchitecture().abi());
			RecordableMod.LOGGER.info("[AndroidPlatform]   AndroidArm:   {}", isAndroidArm());
			RecordableMod.LOGGER.info("[AndroidPlatform]   .minecraft:   {}", findMinecraftDirectory());
			RecordableMod.LOGGER.info("[AndroidPlatform]   POJAV_NATIVEDIR: {}", System.getenv("POJAV_NATIVEDIR"));
			RecordableMod.LOGGER.info("[AndroidPlatform]   java.io.tmpdir:  {}", System.getProperty("java.io.tmpdir", "?"));
			RecordableMod.LOGGER.info("[AndroidPlatform]   Termux ffmpeg:   {}", getTermuxFfmpegPath());
			RecordableMod.LOGGER.info("[AndroidPlatform]   Exec candidates: {}", getExecCapableDirectories());
			RecordableMod.LOGGER.info("[AndroidPlatform]   Recordings dir:  {}", resolveRecordingsOutputDir());
		}
	}

	public static enum Architecture {
		ARM64("arm64-v8a"),
		ARM32("armeabi-v7a"),
		X86_64("x86_64"),
		X86("x86"),
		UNKNOWN("unknown");

		private final String abi;

		private Architecture(String abi) {
			this.abi = abi;
		}

		public String abi() {
			return this.abi;
		}
	}

	public static enum Launcher {
		POJAV("PojavLauncher"),
		ZALITH("Zalith Launcher"),
		FCL("FoldCraftLauncher"),
		UNKNOWN("Unknown Android launcher");

		private final String displayName;

		private Launcher(String displayName) {
			this.displayName = displayName;
		}

		public String displayName() {
			return this.displayName;
		}
	}
}
