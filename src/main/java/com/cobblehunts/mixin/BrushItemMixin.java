package com.cobblehunts.mixin;

import com.cobblehunts.CobbleHunts;
import com.cobblehunts.PlayerHuntData;
import com.cobblehunts.HuntInstance;
import com.cobblehunts.utils.HuntsConfig;
import com.cobblehunts.utils.SpeciesMatcher;
import com.cobblehunts.utils.TrailManager;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BrushItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(BrushItem.class)
public abstract class BrushItemMixin {

    private static final int BRUSH_CYCLE_TICKS = 32;

    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    public void onUseScannerBrush(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        ItemStack stack = context.getStack();
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data != null && data.copyNbt().getBoolean("cobblehunts:is_scanner")) {
            PlayerEntity player = context.getPlayer();
            if (player != null) {
                player.setCurrentHand(context.getHand());
                cir.setReturnValue(ActionResult.CONSUME);
            }
        }
    }

    @Inject(method = "usageTick", at = @At("HEAD"), cancellable = true)
    public void onScannerUsageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks, CallbackInfo ci) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (data == null || !data.copyNbt().getBoolean("cobblehunts:is_scanner")) {
            return;
        }

        ci.cancel();

        if (!(user instanceof ServerPlayerEntity player)) return;
        if (!(world instanceof ServerWorld serverWorld)) return;

        HitResult hit = player.raycast(5.0, 0.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.stopUsingItem();
            world.setBlockBreakingInfo(-user.getId(), BlockPos.ORIGIN, -1);
            return;
        }

        BlockHitResult blockHit = (BlockHitResult) hit;
        BlockPos pos = blockHit.getBlockPos();
        Direction side = blockHit.getSide();

        int useDuration = stack.getMaxUseTime(user) - remainingUseTicks;

        // Visuals: Use negative entity ID to force client render
        int breakProgress = (int) (((float) useDuration / BRUSH_CYCLE_TICKS) * 9.0F);
        world.setBlockBreakingInfo(-user.getId(), pos, breakProgress);

        if (useDuration % 5 == 0) {
            world.playSound(null, pos, SoundEvents.ITEM_BRUSH_BRUSHING_GENERIC, SoundCategory.PLAYERS, 0.5f, 1.0f);

            double x = pos.getX() + 0.5 + (side.getOffsetX() * 0.55);
            double y = pos.getY() + 0.5 + (side.getOffsetY() * 0.55);
            double z = pos.getZ() + 0.5 + (side.getOffsetZ() * 0.55);

            serverWorld.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, world.getBlockState(pos)),
                    x, y, z,
                    5, 0.1, 0.1, 0.1, 0.0
            );
        }

        if (useDuration >= BRUSH_CYCLE_TICKS) {
            // Reset animation
            world.setBlockBreakingInfo(-user.getId(), pos, -1);
            player.stopUsingItem();
            performScanLogic(player, serverWorld, stack, pos);
        }
    }

    @Unique
    private void performScanLogic(ServerPlayerEntity player, ServerWorld world, ItemStack stack, BlockPos pos) {
        // Access configuration
        var brushConfig = HuntsConfig.INSTANCE.getSettings().getTrackingBrush();

        // 1. Try to advance an existing trail
        int result = TrailManager.INSTANCE.attemptAdvance(player, world, pos);

        if (result == TrailManager.RESULT_STEP) {
            // Config: Damage on Step
            applyDurability(player, stack, brushConfig.getDamageOnStep());
            return;
        } else if (result == TrailManager.RESULT_FINISH) {
            // Config: Damage on Finish
            applyDurability(player, stack, brushConfig.getDamageOnFinish());
            return;
        }

        // 2. New Scan Logic
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = data.copyNbt();

        long lastUsed = nbt.getLong("cobblehunts:last_used");
        long currentTime = System.currentTimeMillis();

        // Config: Scan Cooldown
        long cooldownMillis = brushConfig.getScanCooldownSeconds() * 1000L;

        if (currentTime - lastUsed < cooldownMillis) {
            long remaining = ((cooldownMillis - (currentTime - lastUsed)) / 1000L) + 1L;
            player.sendMessage(Text.literal("§cBrush recharging: " + remaining + "s"), true);
            player.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.0f);
            return;
        }

        // Apply Cooldown
        NbtCompound newNbt = nbt.copy();
        newNbt.putLong("cobblehunts:last_used", currentTime);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(newNbt));

        // Config: Damage on Scan (Always applied on attempt)
        applyDurability(player, stack, brushConfig.getDamageOnScan());

        List<HuntInstance> activeHunts = new ArrayList<>();
        PlayerHuntData playerData = CobbleHunts.INSTANCE.getPlayerData(player);
        activeHunts.addAll(playerData.getActivePokemon().values());

        List<HuntInstance> globalHunts = CobbleHunts.INSTANCE.getGlobalHuntStates();
        for (int i = 0; i < globalHunts.size(); i++) {
            if (!playerData.getCompletedGlobalHunts().contains(i)) {
                activeHunts.add(globalHunts.get(i));
            }
        }

        if (activeHunts.isEmpty()) {
            player.sendMessage(Text.literal("§eNo active hunts to scan."), true);
            // No extra "failed" damage applied here, just the base scan cost
            return;
        }

        // Config: Scan Radius
        double scanRadius = brushConfig.getScanRadius();
        Box box = new Box(player.getBlockPos()).expand(scanRadius);
        List<PokemonEntity> entities = world.getEntitiesByClass(PokemonEntity.class, box, e -> true);

        PokemonEntity foundEntity = null;
        double closestDist = Double.MAX_VALUE;

        for (HuntInstance hunt : activeHunts) {
            for (PokemonEntity entity : entities) {
                if (SpeciesMatcher.INSTANCE.matches(entity.getPokemon(), hunt.getEntry().getSpecies())) {
                    double d = player.squaredDistanceTo(entity);
                    if (d < closestDist) {
                        closestDist = d;
                        foundEntity = entity;
                    }
                }
            }
        }

        if (foundEntity != null) {
            String name = foundEntity.getPokemon().getSpecies().getName();
            TrailManager.INSTANCE.startTrail(player, pos, foundEntity, name);
            world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 1.0f);
        } else {

            player.sendMessage(Text.literal("§7No matching hunt Pokémon footprints found nearby."), true);
            world.playSound(null, pos, SoundEvents.BLOCK_REDSTONE_TORCH_BURNOUT, SoundCategory.PLAYERS, 0.5f, 1.0f);

            // Config: Damage on Failed Scan (Extra penalty applied ONLY here)
            applyDurability(player, stack, brushConfig.getDamageOnFailedScan());
        }
    }

    @Unique
    private void applyDurability(ServerPlayerEntity player, ItemStack stack, int amount) {
        if (player.isCreative() || amount <= 0) return;
        // The callback handles breaking events
        stack.damage(amount, player, EquipmentSlot.MAINHAND);
    }
}