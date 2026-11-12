package com.nebula.client.mixin;

import com.nebula.client.core.ClientManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void nebula$onKey(long window, int action, KeyEvent event, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (action == GLFW.GLFW_PRESS && window == mc.getWindow().handle() && mc.screen == null && ClientManager.isReady()) {
			// Consume the press that opened a screen, otherwise vanilla forwards it to that screen and it closes again.
			if (ClientManager.get().modules().onKeyPressed(event.key())) {
				ci.cancel();
			}
		}
	}
}
