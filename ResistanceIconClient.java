package com.example.resistanceicon;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the vanilla Resistance effect sprite above the head of every
 * player who currently has the Resistance effect.
 *
 * Implementation: each frame we project the point above each player's head
 * into screen space and draw the sprite there as a HUD element.
 */
public class ResistanceIconClient implements ClientModInitializer {

    private static final Identifier ICON = Identifier.ofVanilla("mob_effect/resistance");
    private static final double MAX_DISTANCE = 64.0;
    private static final int MIN_SIZE = 8;
    private static final int MAX_SIZE = 32;

    @Override
    public void onInitializeClient() {
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.of("resistance-icon", "overlay"),
                ResistanceIconClient::render
        );
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || client.options.hudHidden) return;

        Camera camera = client.gameRenderer.getCamera();
        if (!camera.isReady()) return;

        float tickDelta = tickCounter.getTickProgress(false);
        boolean firstPerson = client.options.getPerspective().isFirstPerson();

        Vec3d camPos = camera.getCameraPos();
        Quaternionf invRot = camera.getRotation().conjugate(new Quaternionf());

        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();
        double fov = Math.toRadians(client.options.getFov().getValue());
        double tanHalfFov = Math.tan(fov / 2.0);
        double aspect = (double) screenW / screenH;

        for (PlayerEntity player : client.world.getPlayers()) {
            if (player == client.player && firstPerson) continue;
            if (player.isInvisible() || player.isSpectator()) continue;
            if (!player.hasStatusEffect(StatusEffects.RESISTANCE)) continue;
            if (player != client.player && !client.player.canSee(player)) continue;

            // Point just above the head (clear of the nametag).
            Vec3d pos = player.getLerpedPos(tickDelta).add(0, player.getHeight() + 0.9, 0);
            if (pos.squaredDistanceTo(camPos) > MAX_DISTANCE * MAX_DISTANCE) continue;

            // World -> camera space
            Vector3f v = new Vector3f(
                    (float) (pos.x - camPos.x),
                    (float) (pos.y - camPos.y),
                    (float) (pos.z - camPos.z));
            invRot.transform(v);

            double depth = -v.z; // camera looks down -Z
            if (depth < 0.1) continue; // behind the camera

            // Perspective projection -> NDC -> scaled GUI coordinates
            double ndcX = v.x / (depth * tanHalfFov * aspect);
            double ndcY = v.y / (depth * tanHalfFov);
            if (Math.abs(ndcX) > 1.2 || Math.abs(ndcY) > 1.2) continue;

            double sx = (ndcX * 0.5 + 0.5) * screenW;
            double sy = (0.5 - ndcY * 0.5) * screenH;

            // Icon is ~0.5 blocks tall in the world
            double sizePx = 0.5 / (depth * tanHalfFov) * (screenH / 2.0);
            int size = MathHelper.clamp((int) sizePx, MIN_SIZE, MAX_SIZE);

            context.drawGuiTexture(
                    RenderPipelines.GUI_TEXTURED,
                    ICON,
                    (int) sx - size / 2,
                    (int) sy - size / 2,
                    size, size);
        }
    }
}
