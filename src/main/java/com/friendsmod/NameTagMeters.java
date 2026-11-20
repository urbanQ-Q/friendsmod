package com.friendsmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Rysuje dystans w metrach nad nickiem znajomego (nad nametagiem). */
public final class NameTagMeters {
    private static final float LINE_HEIGHT = 9.0f * 1.15f * 0.025f;

    private NameTagMeters() {}

    private static String findFriend(String label) {
        for (String tok : label.split("[^A-Za-z0-9_]+")) {
            if (!tok.isEmpty() && FriendManager.friends.contains(tok)) return tok;
        }
        return null;
    }

    public static void draw(EntityRenderState state, Text text, MatrixStack matrices,
                            VertexConsumerProvider vcp, int light) {
        if (!FriendManager.nametagMeters) return;
        if (!(state instanceof PlayerEntityRenderState ps) || ps.nameLabelPos == null) return;

        String friend = findFriend(text.getString());
        if (friend == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        FriendManager.Info info = FriendManager.info(mc, friend);
        if (!info.known || !info.sameDim || !info.far) return;

        String label = Math.round(info.dist) + " m";
        int lines = ps.playerName != null ? 2 : 1; // gdy pod nickiem jest wynik scoreboardu, nick jest wyzej
        Vec3d pos = ps.nameLabelPos;

        matrices.push();
        matrices.translate(pos.x, pos.y + 0.5 + LINE_HEIGHT * lines, pos.z);
        matrices.multiply(mc.gameRenderer.getCamera().getRotation());
        matrices.scale(0.025f, -0.025f, 0.025f);
        Matrix4f m = matrices.peek().getPositionMatrix();
        TextRenderer tr = mc.textRenderer;
        float x = -tr.getWidth(label) / 2.0f;
        boolean seeThrough = !ps.sneaking;
        tr.draw(label, x, 0, 0x80FFFF55, false, m, vcp,
                seeThrough ? TextRenderer.TextLayerType.SEE_THROUGH : TextRenderer.TextLayerType.NORMAL,
                0x40000000, light);
        if (seeThrough) {
            tr.draw(label, x, 0, 0xFFFFFF55, false, m, vcp, TextRenderer.TextLayerType.NORMAL, 0, 15728880);
        }
        matrices.pop();
    }
}
