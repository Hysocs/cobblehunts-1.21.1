// src/main/java/com/cobblehunts/mixin/ItemMixin.java
package com.cobblehunts.mixin;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Item.class)
public class ItemMixin {

    @Inject(method = "onStoppedUsing", at = @At("HEAD"))
    public void onScannerStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data != null && data.copyNbt().getBoolean("cobblehunts:is_scanner")) {

            if (user instanceof ServerPlayerEntity player) {

                HitResult hit = player.raycast(5.0, 0.0f, false);
                if (hit.getType() == HitResult.Type.BLOCK) {
                    BlockPos pos = ((BlockHitResult) hit).getBlockPos();

                    world.setBlockBreakingInfo(-user.getId(), pos, -1);
                }
            }
        }
    }
}
