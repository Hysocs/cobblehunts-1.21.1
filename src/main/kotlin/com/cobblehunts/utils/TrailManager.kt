package com.cobblehunts.utils

import com.everlastingutils.scheduling.SchedulerManager
import net.minecraft.block.Blocks
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket
import net.minecraft.particle.BlockStateParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object TrailManager {
    private const val SCHEDULER_ID = "cobblehunts-trail-particles"

    // Result Constants for the Mixin
    const val RESULT_NONE = 0
    const val RESULT_STEP = 1
    const val RESULT_FINISH = 2

    private data class TrailContext(
        val targetEntityUuid: UUID,
        val currentTargetPos: BlockPos,
        val expirationTime: Long
    )

    private val playerTrails = ConcurrentHashMap<UUID, TrailContext>()

    fun init(server: MinecraftServer) {
        SchedulerManager.scheduleAtFixedRate(SCHEDULER_ID, server, 0, 250, TimeUnit.MILLISECONDS, runAsync = false) {
            tickParticles(server)
        }
    }

    fun shutdown() {
        SchedulerManager.shutdown(SCHEDULER_ID)
        playerTrails.clear()
    }

    fun startTrail(player: PlayerEntity, startPos: BlockPos, target: Entity, pokemonName: String) {
        val world = player.world as? ServerWorld ?: return

        // Grab config values
        val brushConfig = HuntsConfig.settings.trackingBrush
        val startDist = brushConfig.trailStartDistance
        val timeoutMs = brushConfig.trailTimeoutSeconds * 1000L

        // Calculate the first point immediately based on direction
        val startVec = startPos.toCenterPos()
        val targetVec = target.pos
        val dx = targetVec.x - startVec.x
        val dz = targetVec.z - startVec.z
        val angle = atan2(dz, dx)

        val nextX = startVec.x + (startDist * cos(angle))
        val nextZ = startVec.z + (startDist * sin(angle))

        // Find a valid block to place the trail on
        val firstMarkerPos = getBestClearPosition(world, nextX, nextZ, startPos.y)

        // Set expiration to NOW + Configured Timeout
        val expiry = System.currentTimeMillis() + timeoutMs
        playerTrails[player.uuid] = TrailContext(target.uuid, firstMarkerPos, expiry)

        player.sendMessage(Text.literal("§6You found footprints for §e$pokemonName§6!"), false)
        player.sendMessage(Text.literal("§7You have ${brushConfig.trailTimeoutSeconds} seconds to follow the trail..."), true)

        // Visual Blast to show direction
        if (player is ServerPlayerEntity) {
            spawnDirectionalBlast(player, startPos, firstMarkerPos)
        }
    }

    private fun tickParticles(server: MinecraftServer) {
        if (playerTrails.isEmpty()) return

        val currentTime = System.currentTimeMillis()
        val iterator = playerTrails.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val playerUuid = entry.key
            val context = entry.value

            val player = server.playerManager.getPlayer(playerUuid)

            // 1. Check Timeout
            if (currentTime > context.expirationTime) {
                player?.sendMessage(Text.literal("§cThe trail went cold... you took too long."), true)
                player?.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.0f)
                iterator.remove()
                continue
            }

            // 2. Spawn particles if player is online
            if (player != null) {
                spawnPacketParticles(player, context.currentTargetPos)
            }
        }
    }

    /**
     * Checks if the brushed block matches the trail target.
     * Returns an Int code indicating the result for durability logic.
     */
    fun attemptAdvance(player: PlayerEntity, world: ServerWorld, hitPos: BlockPos): Int {
        val context = playerTrails[player.uuid] ?: return RESULT_NONE

        // Allow clicking the target block OR the block directly above/below it
        if (context.currentTargetPos == hitPos || context.currentTargetPos.up() == hitPos || context.currentTargetPos == hitPos.down()) {
            val target = world.getEntity(context.targetEntityUuid)

            if (target == null) {
                player.sendMessage(Text.literal("§cThe trail went cold (Pokémon despawned)."), true)
                playerTrails.remove(player.uuid)
                return RESULT_NONE
            }

            // Visual Confirmation
            if (player is ServerPlayerEntity) {
                val packet = ParticleS2CPacket(
                    BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultState),
                    true,
                    hitPos.x + 0.5, hitPos.y + 1.2, hitPos.z + 0.5,
                    0.2f, 0.2f, 0.2f, 0.1f, 20
                )
                player.networkHandler.sendPacket(packet)
            }

            // Check Distance
            val currentVec = hitPos.toCenterPos()
            val targetVec = target.pos
            val dx = targetVec.x - currentVec.x
            val dz = targetVec.z - currentVec.z
            val dist = sqrt(dx*dx + dz*dz)

            // FINISH CONDITION
            if (dist < 10.0) {
                player.sendMessage(Text.literal("§aYou found the source of the prints!"), true)
                if (player is ServerPlayerEntity) {
                    val p = ParticleS2CPacket(
                        ParticleTypes.HAPPY_VILLAGER, true,
                        target.x, target.y + 0.5, target.z,
                        0.5f, 0.5f, 0.5f, 0.0f, 20
                    )
                    player.networkHandler.sendPacket(p)
                    val f = ParticleS2CPacket(
                        ParticleTypes.FLASH, true,
                        target.x, target.y + 1.0, target.z,
                        0.0f, 0.0f, 0.0f, 0.0f, 1
                    )
                    player.networkHandler.sendPacket(f)
                }
                playerTrails.remove(player.uuid)
                return RESULT_FINISH
            }

            // STEP CONDITION
            val brushConfig = HuntsConfig.settings.trackingBrush
            val stepDist = brushConfig.trailStepDistance

            val angle = atan2(dz, dx)
            val nextX = currentVec.x + (stepDist * cos(angle))
            val nextZ = currentVec.z + (stepDist * sin(angle))
            val nextPos = getBestClearPosition(world, nextX, nextZ, hitPos.y)

            if (player is ServerPlayerEntity) {
                spawnDirectionalBlast(player, hitPos, nextPos)
            }

            // REFRESH TIMER from config
            val timeoutMs = brushConfig.trailTimeoutSeconds * 1000L
            val newExpiry = System.currentTimeMillis() + timeoutMs

            playerTrails[player.uuid] = context.copy(
                currentTargetPos = nextPos,
                expirationTime = newExpiry
            )
            player.sendMessage(Text.literal("§aFootprints found! Timer reset to ${brushConfig.trailTimeoutSeconds}s."), true)

            return RESULT_STEP
        }

        return RESULT_NONE
    }

    private fun spawnPacketParticles(player: ServerPlayerEntity, pos: BlockPos) {
        val world = player.world
        var particlePos = pos.up()
        for (i in 0..5) {
            if (world.getBlockState(particlePos).isAir) break
            particlePos = particlePos.up()
        }

        val x = particlePos.x + 0.5
        val y = particlePos.y + 0.05
        val z = particlePos.z + 0.5

        // Footprint Dust
        val packet = ParticleS2CPacket(
            BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultState),
            true, x, y, z, 0.2f, 0.0f, 0.2f, 0.0f, 3
        )
        player.networkHandler.sendPacket(packet)

        // Subtle Smoke
        val smokePacket = ParticleS2CPacket(
            ParticleTypes.CAMPFIRE_COSY_SMOKE,
            true, x, y + 0.2, z, 0.1f, 0.1f, 0.1f, 0.01f, 1
        )
        player.networkHandler.sendPacket(smokePacket)
    }

    private fun spawnDirectionalBlast(player: ServerPlayerEntity, from: BlockPos, to: BlockPos) {
        val fromVec = from.toCenterPos().add(0.0, 0.8, 0.0)
        val toVec = to.toCenterPos().add(0.0, 0.8, 0.0)
        val dir = toVec.subtract(fromVec).normalize()

        // Debris Blast
        for (i in 0..15) {
            val spreadX = (Math.random() - 0.5) * 0.1
            val spreadY = (Math.random() - 0.5) * 0.1
            val spreadZ = (Math.random() - 0.5) * 0.1

            val dirtPacket = ParticleS2CPacket(
                BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultState),
                true, fromVec.x, fromVec.y, fromVec.z,
                (dir.x + spreadX).toFloat(), (dir.y + spreadY + 0.2).toFloat(), (dir.z + spreadZ).toFloat(),
                0.6f, 0
            )
            player.networkHandler.sendPacket(dirtPacket)
        }

        // Cloud Stream
        for (i in 0..5) {
            val cloudPacket = ParticleS2CPacket(
                ParticleTypes.CLOUD, true, fromVec.x, fromVec.y, fromVec.z,
                dir.x.toFloat(), (dir.y + 0.1).toFloat(), dir.z.toFloat(), 0.3f, 0
            )
            player.networkHandler.sendPacket(cloudPacket)
        }
    }

    private fun getBestClearPosition(world: ServerWorld, x: Double, z: Double, refY: Int): BlockPos {
        val xInt = MathHelper.floor(x)
        val zInt = MathHelper.floor(z)
        val initialGround = getGroundPosition(world, xInt, zInt, refY)
        if (world.getBlockState(initialGround.up()).isAir) return initialGround

        val radius = 2
        var bestPos = initialGround
        var closestDistSq = Double.MAX_VALUE

        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                if (dx == 0 && dz == 0) continue
                val neighborGround = getGroundPosition(world, xInt + dx, zInt + dz, refY)
                if (world.getBlockState(neighborGround.up()).isAir) {
                    val dSq = (dx*dx + dz*dz).toDouble()
                    if (dSq < closestDistSq) {
                        closestDistSq = dSq
                        bestPos = neighborGround
                    }
                }
            }
        }
        return bestPos
    }

    private fun getGroundPosition(world: ServerWorld, x: Int, z: Int, refY: Int): BlockPos {
        var checkPos = BlockPos(x, refY + 4, z)
        for (i in 0..10) {
            val state = world.getBlockState(checkPos)
            val below = checkPos.down()
            val stateBelow = world.getBlockState(below)
            if (!state.isOpaqueFullCube(world, checkPos) && stateBelow.isOpaqueFullCube(world, below)) {
                return below
            }
            checkPos = checkPos.down()
        }
        return BlockPos(x, refY, z)
    }
}