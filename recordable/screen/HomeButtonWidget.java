package dev.recordable.screen;

import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_4185;
import net.minecraft.class_437;
import net.minecraft.class_7919;

public final class HomeButtonWidget {
	private static final int SIZE = 20;

	private HomeButtonWidget() {
	}

	public static class_4185 create(class_437 hostScreen) {
		int hostWidth = hostScreen == null ? 0 : hostScreen.field_22789;
		int hostHeight = hostScreen == null ? 0 : hostScreen.field_22790;
		int centerX = hostWidth / 2;
		int buttonAreaRight = centerX + 100;
		int x = buttonAreaRight + 4;
		int y = hostHeight / 4 + 48 + 24;
		return class_4185.method_46430(class_2561.method_43470("\ud83c\udfac"), button -> {
			class_310 client = class_310.method_1551();
			if (client != null) {
				client.method_1507(new VideoCollectionScreen(hostScreen));
			}
		}).method_46434(x, y, 20, 20).method_46436(class_7919.method_47407(class_2561.method_43470("Video Collection (Record-able)"))).method_46431();
	}
}
