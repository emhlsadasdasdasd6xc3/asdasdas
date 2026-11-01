package com.example.resistanceicon;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gives players with Turtle Master (Resistance + Slowness) a purple glowing
 * outline that shows through walls. The outline itself is applied by EntityMixin.
 */
public class ResistanceIconClient implements ClientModInitializer {

    /** Outline colour (RGB). Change this to any colour you like. */
    public static final int OUTLINE_COLOR = 0xB026FF;

    // Vanilla effect colours used to recognise effects from other players' particles
    private static final int RESISTANCE_RGB = 0x9146F0;
    private static final int SLOWNESS_RGB = 0x5A6C81;
    // Set to false to outline ANY visible Resistance, not just Turtle Master
    private static final boolean REQUIRE_SLOWNESS = true;

    private static TrackedData<?> particleData;
    private static boolean searched = false;
    private static final Map<Class<?>, Optional<Field>> COLOR_FIELDS = new ConcurrentHashMap<>();

    @Override
    public void onInitializeClient() {
        // Nothing to set up; the work is done by EntityMixin.
    }

    /** True if this player should get the purple outline. */
    public static boolean isTurtleMaster(PlayerEntity player) {
        try {
            if (player.hasStatusEffect(StatusEffects.RESISTANCE)
                    && (player.hasStatusEffect(StatusEffects.SLOWNESS) || !REQUIRE_SLOWNESS)) {
                return true;
            }
            return looksLikeTurtleMaster(player);
        } catch (Throwable t) {
            return false;
        }
    }

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
        Optional<Field> field = COLOR_FIELDS.computeIfAbsent(particle.getClass(), c -> {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType() != int.class) continue;
                try {
                    f.setAccessible(true);
                    return Optional.of(f);
                } catch (Throwable ignored) {
                }
            }
            return Optional.empty();
        });
        if (field.isEmpty()) return -1;
        try {
            return field.get().getInt(particle) & 0xFFFFFF;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** True if the visible effect particles include a Resistance colour and a Slowness colour. */
    private static boolean looksLikeTurtleMaster(PlayerEntity player) {
        if (!searched) findParticleData(player);
        if (particleData == null) return false;
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
    }
}
