package dev.recordable.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class RecordableMixinPlugin implements IMixinConfigPlugin {
	private static Boolean is121Plus = null;
	private static Boolean is1216Plus = null;

	public void onLoad(String mixinPackage) {
		try {
			Version mcVersion = ((ModContainer)FabricLoader.getInstance()
					.getModContainer("minecraft")
					.orElseThrow(() -> new RuntimeException("minecraft mod container not found")))
				.getMetadata()
				.getVersion();
			is121Plus = mcVersion.compareTo(parseVersion("1.21")) >= 0;
			is1216Plus = mcVersion.compareTo(parseVersion("1.21.6")) >= 0;
		} catch (Throwable var3) {
			is121Plus = true;
			is1216Plus = true;
		}
	}

	private static Version parseVersion(String versionStr) {
		try {
			return Version.parse(versionStr);
		} catch (VersionParsingException var2) {
			throw new RuntimeException("Failed to parse version: " + versionStr, var2);
		}
	}

	public String getRefMapperConfig() {
		return null;
	}

	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf(46) + 1);

		return switch (simpleName) {
			case "GameRendererMixin" -> is121Plus;
			case "GameRendererMixin_1_20" -> !is121Plus;
			case "AdvancementToastMixin" -> is121Plus;
			case "AdvancementToastMixin_1_20" -> !is121Plus;
			case "GuiRenderStateMixin" -> is1216Plus;
			case "SoundEngineMixin", "TitleScreenMixin" -> true;
			default -> true;
		};
	}

	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	public List<String> getMixins() {
		return null;
	}

	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
