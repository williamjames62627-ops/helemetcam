package dev.recordable.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.recordable.screen.RecordableSettingsScreen;

public final class ModMenuIntegration implements ModMenuApi {
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return RecordableSettingsScreen::new;
	}
}
