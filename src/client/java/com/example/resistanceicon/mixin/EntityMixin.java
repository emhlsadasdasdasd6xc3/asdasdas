package com.example.resistanceicon.mixin;

import com.example.resistanceicon.ResistanceIconClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    // Make Turtle Master players glow (outline visible through walls)
    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void resistanceicon$glow(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof PlayerEntity player && ResistanceIconClient.isTurtleMaster(player)) {
            cir.setReturnValue(true);
        }
    }

    // Make that outline purple
    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void resistanceicon$color(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof PlayerEntity player && ResistanceIconClient.isTurtleMaster(player)) {
            cir.setReturnValue(ResistanceIconClient.OUTLINE_COLOR);
        }
    }
}
