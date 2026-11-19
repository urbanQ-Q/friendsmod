package com.friendsmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public final class FriendsHud {
    private static final String[] ARROWS = {"\u2191", "\u2197", "\u2192", "\u2198", "\u2193", "\u2199", "\u2190", "\u2196"};

    private FriendsHud() {}

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;

        int y = 4;
        for (String f : FriendManager.friends) {
            FriendManager.Info i = FriendManager.info(mc, f);
            if (!i.online || !i.known || !i.far) continue; // pokazuj tylko gdy dalej niz 20 kratek

            String text;
            if (i.sameDim) {
                double dx = i.pos.x - mc.player.getX();
                double dz = i.pos.z - mc.player.getZ();
                float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float rel = net.minecraft.util.math.MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
                int idx = ((Math.round(rel / 45f) % 8) + 8) % 8;
                text = f + " " + ARROWS[idx] + " " + (int) i.dist + " m  [" + i.coords() + "]";
            } else {
                text = f + " (" + FriendManager.dimLabel(i.dim) + ")  [" + i.coords() + "]";
            }
            int w = mc.textRenderer.getWidth(text);
            ctx.drawTextWithShadow(mc.textRenderer, text, ctx.getScaledWindowWidth() - w - 4, y, 0xFFFFFF55);
            y += 10;
        }
    }
}
