package com.friendsmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.Vec3d;

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

        renderMarkers(ctx, mc);
    }

    /** Znacznik na ekranie w kierunku znajomego, ktory jest poza zasiegiem renderowania (dowolna odleglosc). */
    private static void renderMarkers(DrawContext ctx, MinecraftClient mc) {
        if (!FriendManager.markers || mc.currentScreen != null) return;

        Camera cam = mc.gameRenderer.getCamera();
        Vec3d cp = cam.getPos();
        double yaw = Math.toRadians(cam.getYaw());
        double pitch = Math.toRadians(cam.getPitch());
        Vec3d fwd = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3d up = right.crossProduct(fwd);

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        double tanH = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        double aspect = (double) w / h;

        for (String f : FriendManager.friends) {
            FriendManager.Info i = FriendManager.info(mc, f);
            if (!i.online || !i.known || !i.sameDim || i.loaded || !i.far) continue;

            Vec3d d = i.pos.add(0, 1.8, 0).subtract(cp);
            double cx = d.dotProduct(right);
            double cy = d.dotProduct(up);
            double cz = d.dotProduct(fwd);

            double nx, ny;
            if (cz > 0.001) {
                nx = cx / cz / (tanH * aspect);
                ny = cy / cz / tanH;
            } else { // za plecami - przyklej do krawedzi w odpowiednia strone
                double len = Math.hypot(cx, cy);
                if (len < 1e-6) { cx = 1; cy = 0; len = 1; }
                nx = cx / len * 1000.0;
                ny = cy / len * 1000.0;
            }
            double bx = 1.0 - 60.0 / (w / 2.0);
            double by = 1.0 - 36.0 / (h / 2.0);
            double s = Math.max(Math.abs(nx) / bx, Math.abs(ny) / by);
            if (s > 1.0) { nx /= s; ny /= s; }

            int sx = (int) (w / 2.0 * (1.0 + nx));
            int sy = (int) (h / 2.0 * (1.0 - ny));

            ctx.fill(sx - 2, sy - 2, sx + 3, sy + 3, 0xFFFFFF55);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, f, sx, sy + 5, 0xFFFFFFFF);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Math.round(i.dist) + " m", sx, sy + 15, 0xFFFFFF55);
        }
    }
}
