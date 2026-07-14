package dev.recordable;

import java.lang.reflect.Constructor;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.class_2960;

public final class VersionHelper {
	private static volatile Boolean cachedIs121Plus = null;
	private static volatile Constructor<class_2960> identifierCtor = null;

	private VersionHelper() {
	}

	public static class_2960 id(String namespace, String path) {
		try {
			return class_2960.method_43902(namespace, path);
		} catch (NoSuchMethodError var3) {
			return createIdentifier120(namespace, path);
		}
	}

	private static class_2960 createIdentifier120(String namespace, String path) {
		try {
			if (identifierCtor == null) {
				Constructor<class_2960> ctor = class_2960.class.getDeclaredConstructor(String.class, String.class);
				ctor.setAccessible(true);
				identifierCtor = ctor;
			}

			return (class_2960)identifierCtor.newInstance(namespace, path);
		} catch (Exception var3) {
			throw new RuntimeException("Failed to create Identifier for " + namespace + ":" + path, var3);
		}
	}

	public static class_2960 modId(String path) {
		return id("recordable", path);
	}

	public static boolean is121Plus() {
		if (cachedIs121Plus != null) {
			return cachedIs121Plus;
		} else {
			try {
				Version mcVersion = ((ModContainer)FabricLoader.getInstance()
						.getModContainer("minecraft")
						.orElseThrow(() -> new RuntimeException("minecraft mod container not found")))
					.getMetadata()
					.getVersion();
				cachedIs121Plus = mcVersion.compareTo(parseVersion("1.21")) >= 0;
			} catch (Throwable var1) {
				cachedIs121Plus = true;
			}

			return cachedIs121Plus;
		}
	}

	public static boolean is120x() {
		return !is121Plus();
	}

	public static String getVersionInfo() {
		try {
			String version = ((ModContainer)FabricLoader.getInstance().getModContainer("minecraft").orElseThrow()).getMetadata().getVersion().getFriendlyString();
			return "MC " + version + " (Fabric Loader detected)";
		} catch (Throwable var1) {
			return "MC version unknown (Fabric Loader query failed)";
		}
	}

	private static Version parseVersion(String versionStr) {
		try {
			return Version.parse(versionStr);
		} catch (VersionParsingException var2) {
			throw new RuntimeException("Failed to parse version: " + versionStr, var2);
		}
	}
}
