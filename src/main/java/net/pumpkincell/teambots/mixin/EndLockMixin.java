package net.pumpkincell.teambots.mixin;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import net.pumpkincell.teambots.TeamBotsMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public class EndLockMixin {
    @Inject(at = @At("HEAD"), method = "interactBlock", cancellable = true)
    public void interactBlock(ServerPlayerEntity player, World world, ItemStack stack, Hand hand,
        BlockHitResult blockHitResult, CallbackInfoReturnable<ActionResult> info) {
        if (stack.getItem() == Items.ENDER_EYE && world.getBlockState(blockHitResult.getBlockPos())
            .getBlock() == Blocks.END_PORTAL_FRAME && TeamBotsMod.config.getTimeLeftToEndOpeningMS() > 0) {
            if (world.getRandom().nextInt(5) == 0) {
                player.playSound(SoundEvents.ENTITY_CREEPER_PRIMED, SoundCategory.HOSTILE, 100, 1);
            }
            info.setReturnValue(ActionResult.FAIL);
        }
    }
}
