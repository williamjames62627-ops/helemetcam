package dev.recordable;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;

public final class WatermarkImageStore {
	private static final byte[] PNG_SIGNATURE = new byte[]{-119, 80, 78, 71, 13, 10, 26, 10};

	private WatermarkImageStore() {
	}

	public static Path getWatermarksDir() {
		Path dir = FabricLoader.getInstance().getGameDir().resolve("recordable").resolve("watermarks");

		try {
			Files.createDirectories(dir);
		} catch (IOException var2) {
			RecordableMod.LOGGER.warn("[Record-able] Could not create watermark dir {}: {}", dir, var2.getMessage());
		}

		return dir;
	}

	public static boolean isSupported(String pathOrName) {
		if (pathOrName == null) {
			return false;
		} else {
			String p = pathOrName.toLowerCase(Locale.ROOT);
			return p.endsWith(".png") || p.endsWith(".jpg") || p.endsWith(".jpeg");
		}
	}

	public static Path resolve(String filename) {
		if (filename != null && !filename.isBlank()) {
			String name;
			try {
				name = Paths.get(filename).getFileName().toString();
			} catch (Exception var3) {
				return null;
			}

			return name.isBlank() ? null : getWatermarksDir().resolve(name);
		} else {
			return null;
		}
	}

	public static String importImage(String sourcePath) {
		if (sourcePath != null && !sourcePath.isBlank()) {
			try {
				Path src = Paths.get(sourcePath);
				if (!Files.isRegularFile(src, new LinkOption[0])) {
					RecordableMod.LOGGER.warn("[Record-able] Watermark image not a file: {}", sourcePath);
					return null;
				} else {
					String base = src.getFileName().toString();
					if (!isSupported(base)) {
						RecordableMod.LOGGER.warn("[Record-able] Unsupported watermark image type: {}", base);
						return null;
					} else {
						Path dir = getWatermarksDir();
						String stem = base;
						String ext = "";
						int dot = base.lastIndexOf(46);
						if (dot > 0) {
							stem = base.substring(0, dot);
							ext = base.substring(dot);
						}

						Path dest = dir.resolve(base);
						if (Files.exists(dest, new LinkOption[0])) {
							if (sameContent(src, dest)) {
								return dest.getFileName().toString();
							}

							int i = 1;

							do {
								dest = dir.resolve(stem + "_" + i + ext);
							} while (Files.exists(dest, new LinkOption[0]) && ++i < 10000);
						}

						Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
						RecordableMod.LOGGER.info("[Record-able] Imported watermark image: {} -> {}", sourcePath, dest.getFileName());
						return dest.getFileName().toString();
					}
				}
			} catch (Exception var9) {
				RecordableMod.LOGGER.warn("[Record-able] Failed to import watermark image {}: {}", sourcePath, var9.getMessage());
				return null;
			}
		} else {
			return null;
		}
	}

	public static List<String> listImages() {
		List<String> out = new ArrayList();
		Path dir = getWatermarksDir();

		try {
			if (Files.isDirectory(dir, new LinkOption[0])) {
				Files.list(dir)
					.filter(x$0 -> Files.isRegularFile(x$0, new LinkOption[0]))
					.filter(p -> isSupported(p.getFileName().toString()))
					.sorted()
					.forEach(p -> out.add(p.getFileName().toString()));
			}
		} catch (IOException var3) {
			RecordableMod.LOGGER.warn("[Record-able] Could not list watermark images: {}", var3.getMessage());
		}

		return out;
	}

	public static byte[] readAsPngBytes(Path path) {
		if (path == null) {
			return null;
		} else {
			try {
				if (!Files.isRegularFile(path, new LinkOption[0])) {
					return null;
				} else {
					byte[] raw = Files.readAllBytes(path);
					if (isPng(raw)) {
						return raw;
					} else {
						ByteArrayInputStream in = new ByteArrayInputStream(raw);

						BufferedImage img;
						try {
							img = ImageIO.read(in);
						} catch (Throwable var7) {
							try {
								in.close();
							} catch (Throwable var6) {
								var7.addSuppressed(var6);
							}

							throw var7;
						}

						in.close();
						if (img == null) {
							RecordableMod.LOGGER.warn("[Record-able] Watermark image not decodable: {}", path.getFileName());
							return null;
						} else {
							if (img.getType() != 2) {
								BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(), 2);
								Graphics2D g = argb.createGraphics();
								g.drawImage(img, 0, 0, null);
								g.dispose();
								img = argb;
							}

							ByteArrayOutputStream out = new ByteArrayOutputStream();
							if (!ImageIO.write(img, "png", out)) {
								RecordableMod.LOGGER.warn("[Record-able] No PNG writer available for watermark: {}", path.getFileName());
								return null;
							} else {
								return out.toByteArray();
							}
						}
					}
				}
			} catch (Throwable var8) {
				RecordableMod.LOGGER.warn("[Record-able] Failed to decode watermark image '{}': {}", path.getFileName(), var8.toString());
				return null;
			}
		}
	}

	private static boolean isPng(byte[] data) {
		if (data != null && data.length >= PNG_SIGNATURE.length) {
			for (int i = 0; i < PNG_SIGNATURE.length; i++) {
				if (data[i] != PNG_SIGNATURE[i]) {
					return false;
				}
			}

			return true;
		} else {
			return false;
		}
	}

	private static boolean sameContent(Path a, Path b) {
		try {
			if (Files.size(a) != Files.size(b)) {
				return false;
			} else {
				byte[] ba = Files.readAllBytes(a);
				byte[] bb = Files.readAllBytes(b);
				if (ba.length != bb.length) {
					return false;
				} else {
					for (int i = 0; i < ba.length; i++) {
						if (ba[i] != bb[i]) {
							return false;
						}
					}

					return true;
				}
			}
		} catch (Exception var5) {
			return false;
		}
	}
}
