package net.pumpkincell.teambots.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EndOpeningMixin {
    @Inject(at = @At("HEAD"), method = "moveToWorld")
    public void moveToWorld(ServerWorld destination, CallbackInfoReturnable<Entity> cir) {

    }
}
