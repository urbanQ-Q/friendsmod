package com.friendsmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class FriendsMod implements ClientModInitializer {
    public static KeyBinding OPEN_KEY;

    @Override
    public void onInitializeClient() {
        FriendManager.load();

        OPEN_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.friendsmod.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.friendsmod"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_KEY.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new FriendsScreen(null));
            }
            Protocol.tick(client);
        });

        // Ukrywa wiadomosci protokolu z czatu (true = pokaz, false = ukryj)
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
                !Protocol.handleIncoming(net.minecraft.client.MinecraftClient.getInstance(), message));

        HudRenderCallback.EVENT.register(FriendsHud::render);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            FriendManager.locations.clear();
            Protocol.clear();
        });

        // Przycisk "Friends" w menu pauzy
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof GameMenuScreen) {
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Friends"),
                        b -> client.setScreen(new FriendsScreen(screen)))
                        .dimensions(w - 105, 10, 95, 20).build());
            }
        });
    }
}
