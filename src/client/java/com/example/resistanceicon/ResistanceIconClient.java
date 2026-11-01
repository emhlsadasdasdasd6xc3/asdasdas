package com.example.resistanceicon;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

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
    // Vanilla Resistance effect colour (0x9146F0)
    private static final int RESISTANCE_RGB = 0x9146F0;
    // Vanilla Slowness effect colour (0x5A6C81)
    private static final int SLOWNESS_RGB = 0x5A6C81;
    // Set to false to show the icon for ANY visible Resistance, not just Turtle Master
    private static final boolean REQUIRE_SLOWNESS = false;

    private static TrackedData<?> particleData;
    private static boolean searched = false;

    /** Finds LivingEntity's tracked "effect particles" field by type, so no mappings are needed. */
    private static void findParticleData(PlayerEntity sample) {
        searched = true;
        for (Field f : LivingEntity.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != TrackedData.class) continue;
            try {
                f.setAccessible(true);
                TrackedData<?> td = (TrackedData<?>) f.get(null);
                Object value = sample.getDataTracker().get(td);
                if (value instanceof List<?>) {
                    particleData = td;
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** Reads the colour (int field) of an effect particle without needing its class name. Returns -1 if none. */
    private static int readColor(Object particle) {
        try {
            for (Field f : particle.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType() != int.class) continue;
                f.setAccessible(true);
                return f.getInt(particle) & 0xFFFFFF;
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    /**
     * True if the player's visible effect particles include both a Resistance
     * colour and a Slowness colour (i.e. Turtle Master).
     */
    private static boolean looksLikeTurtleMaster(PlayerEntity player) {
        if (!searched) findParticleData(player);
        if (particleData == null) return false;
        try {
            Object value = player.getDataTracker().get(particleData);
            if (!(value instanceof List<?> list) || list.isEmpty()) return false;
            boolean resistance = false;
            boolean slowness = false;
            for (Object o : list) {
                int rgb = readColor(o);
                if (rgb < 0) continue;
                if (rgb == RESISTANCE_RGB) resistance = true;
                if (rgb == SLOWNESS_RGB) slowness = true;
            }
            return resistance && (slowness || !REQUIRE_SLOWNESS);
        } catch (Throwable t) {
            return false;
        }
    }

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
            boolean turtleMaster = player.hasStatusEffect(StatusEffects.RESISTANCE)
                    && (player.hasStatusEffect(StatusEffects.SLOWNESS) || !REQUIRE_SLOWNESS);
            if (!turtleMaster && !looksLikeTurtleMaster(player)) continue;
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
