package com.cobblehunts.utils

import com.everlastingutils.command.CommandManager
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import com.cobblehunts.CobbleHunts
import com.cobblehunts.gui.huntsgui.HuntsGui
import com.cobblehunts.gui.huntseditorgui.HuntsEditorMainGui

import com.cobblemon.mod.common.Cobblemon
import kotlin.math.max

object HuntsCommands {
    private val manager = CommandManager("cobblehunts", HuntsConfig.config.permissions.permissionLevel, HuntsConfig.config.permissions.opLevel)

    fun registerCommands() {
        manager.command("hunts", permission = HuntsConfig.config.permissions.huntsPermission) {
            executes { context -> executeMainCommand(context) }
            subcommand("editor", permission = "cobblehunts.editor") {
                executes { context -> executeEditorCommand(context) }
            }
            subcommand("reload", permission = "cobblehunts.reload") {
                executes { context -> executeReloadCommand(context) }
            }
            subcommand("cooldowns", permission = "cobblehunts.cooldowns") {
                // Extend cooldown command
                subcommand("extend") {
                    then(RequiredArgumentBuilder.argument<ServerCommandSource, String>("huntType", StringArgumentType.word()).apply {
                        suggests { _, builder ->
                            builder.suggest("global")
                            builder.suggest("soloeasy")
                            builder.suggest("solonormal")
                            builder.suggest("solomedium")
                            builder.suggest("solohard")
                            builder.buildFuture()
                        }
                        then(RequiredArgumentBuilder.argument<ServerCommandSource, Int>("time", IntegerArgumentType.integer(1)).apply {
                            // Without target: applies to executing player
                            executes { context -> executeExtendCooldown(context) }
                            then(RequiredArgumentBuilder.argument<ServerCommandSource, String>("target", StringArgumentType.word()).apply {
                                suggests { ctx, builder ->
                                    ctx.source.server.playerManager.playerList.forEach { player ->
                                        builder.suggest(player.name.string)
                                    }
                                    builder.buildFuture()
                                }
                                executes { context -> executeExtendCooldown(context) }
                            })
                        })
                    })
                }
                // Reset cooldown command
                subcommand("reset") {
                    then(RequiredArgumentBuilder.argument<ServerCommandSource, String>("huntType", StringArgumentType.word()).apply {
                        suggests { _, builder ->
                            builder.suggest("global")
                            builder.suggest("soloeasy")
                            builder.suggest("solonormal")
                            builder.suggest("solomedium")
                            builder.suggest("solohard")
                            builder.buildFuture()
                        }
                        // Without target: applies to executing player
                        executes { context -> executeResetCooldown(context) }
                        then(RequiredArgumentBuilder.argument<ServerCommandSource, String>("target", StringArgumentType.word()).apply {
                            suggests { ctx, builder ->
                                ctx.source.server.playerManager.playerList.forEach { player ->
                                    builder.suggest(player.name.string)
                                }
                                builder.buildFuture()
                            }
                            executes { context -> executeResetCooldown(context) }
                        })
                    })
                }
                // Decrease cooldown command
                subcommand("decrease") {
                    then(RequiredArgumentBuilder.argument<ServerCommandSource, String>("huntType", StringArgumentType.word()).apply {
                        suggests { _, builder ->
                            builder.suggest("global")
                            builder.suggest("soloeasy")
                            builder.suggest("solonormal")
                            builder.suggest("solomedium")
                            builder.suggest("solohard")
                            builder.buildFuture()
                        }
                        then(RequiredArgumentBuilder.argument<ServerCommandSource, Int>("time", IntegerArgumentType.integer(1)).apply {
                            executes { context -> executeDecreaseCooldown(context) }
                            then(RequiredArgumentBuilder.argument<ServerCommandSource, String>("target", StringArgumentType.word()).apply {
                                suggests { ctx, builder ->
                                    ctx.source.server.playerManager.playerList.forEach { player ->
                                        builder.suggest(player.name.string)
                                    }
                                    builder.buildFuture()
                                }
                                executes { context -> executeDecreaseCooldown(context) }
                            })
                        })
                    })
                }
            }
            // New subcommand to revert a turn-in with player and Pokémon name.
            subcommand("revertturnin", permission = "cobblehunts.revertturnin") {
                then(RequiredArgumentBuilder.argument<ServerCommandSource, String>("target", StringArgumentType.word()).apply {
                    suggests { ctx, builder ->
                        ctx.source.server.playerManager.playerList.forEach { player ->
                            builder.suggest(player.name.string)
                        }
                        builder.buildFuture()
                    }
                    then(RequiredArgumentBuilder.argument<ServerCommandSource, String>("pokemon", StringArgumentType.greedyString()).apply {
                        suggests { ctx, builder ->
                            // Use the target player's removed cache to suggest Pokémon species.
                            val targetName = ctx.getArgument("target", String::class.java)
                            val targetPlayer = ctx.source.server.playerManager.getPlayer(targetName)
                            if (targetPlayer != null) {
                                val removedList = CobbleHunts.removedPokemonCache.getOrDefault(targetPlayer.uuid, mutableListOf())
                                removedList.distinctBy { it.species.name.lowercase() }.forEach { p ->
                                    builder.suggest(p.species.name)
                                }
                            }
                            builder.buildFuture()
                        }
                        executes { context -> executeRevertTurnIn(context) }
                    })
                })
            }
        }
        manager.register()
    }

    private fun executeMainCommand(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player as? ServerPlayerEntity ?: run {
            source.sendError(Text.literal("This command can only be used by players."))
            return 0
        }
        HuntsGui.openMainGui(player)
        return 1
    }

    private fun executeEditorCommand(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player as? ServerPlayerEntity ?: run {
            source.sendError(Text.literal("This command can only be used by players."))
            return 0
        }
        HuntsEditorMainGui.openGui(player)
        return 1
    }

    private fun executeReloadCommand(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        HuntsConfig.reloadBlocking()
        HuntsConfig.saveConfig()
        CobbleHunts.updateDebugState()
        CatchingTracker.cleanupCache()
        // Restart hunts so they refresh with the new configuration
        CobbleHunts.restartHunts()
        source.sendMessage(Text.literal("CobbleHunts config and hunts reloaded."))
        return 1
    }

    // Helper: attempts to retrieve the "target" argument; if not present, uses the executing player.
    private fun getTargetPlayer(context: CommandContext<ServerCommandSource>): ServerPlayerEntity? {
        return try {
            val targetName = context.getArgument("target", String::class.java)
            context.source.server.playerManager.getPlayer(targetName)
        } catch (e: IllegalArgumentException) {
            context.source.player as? ServerPlayerEntity
        }
    }

    private fun executeExtendCooldown(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val huntType = context.getArgument("huntType", String::class.java)
        if (huntType == "global") {
            val time = try {
                context.getArgument("time", Int::class.java)
            } catch (e: IllegalArgumentException) {
                0
            }
            CobbleHunts.globalCooldownEnd += time * 1000L
            source.sendMessage(Text.literal("Global hunt cooldown extended by $time seconds."))
        } else {
            val time = try {
                context.getArgument("time", Int::class.java)
            } catch (e: IllegalArgumentException) {
                source.sendError(Text.literal("Time must be specified for non-global cooldowns."))
                return 0
            }
            val timeMillis = time * 1000L
            val difficulty = when (huntType) {
                "soloeasy" -> "easy"
                "solonormal" -> "normal"
                "solomedium" -> "medium"
                "solohard" -> "hard"
                else -> {
                    source.sendError(Text.literal("Invalid hunt type: $huntType"))
                    return 0
                }
            }
            val target = getTargetPlayer(context)
            if (target == null) {
                source.sendError(Text.literal("Player not found or command executed from console without a target."))
                return 0
            }
            val data = CobbleHunts.getPlayerData(target)
            val currentEnd = data.cooldowns[difficulty] ?: 0
            data.cooldowns[difficulty] = currentEnd + timeMillis
            source.sendMessage(Text.literal("$huntType cooldown extended by $time seconds for ${target.name.string}."))
        }
        return 1
    }

    private fun executeResetCooldown(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val huntType = context.getArgument("huntType", String::class.java)
        if (huntType == "global") {
            CobbleHunts.globalCooldownEnd = 0
            source.sendMessage(Text.literal("Global hunt cooldown reset."))
        } else {
            val difficulty = when (huntType) {
                "soloeasy" -> "easy"
                "solonormal" -> "normal"
                "solomedium" -> "medium"
                "solohard" -> "hard"
                else -> {
                    source.sendError(Text.literal("Invalid hunt type: $huntType"))
                    return 0
                }
            }
            val target = getTargetPlayer(context)
            if (target == null) {
                source.sendError(Text.literal("Player not found or command executed from console without a target."))
                return 0
            }
            val data = CobbleHunts.getPlayerData(target)
            data.cooldowns[difficulty] = 0
            source.sendMessage(Text.literal("$huntType cooldown reset for ${target.name.string}."))
        }
        return 1
    }

    private fun executeDecreaseCooldown(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val huntType = context.getArgument("huntType", String::class.java)
        if (huntType == "global") {
            val time = try {
                context.getArgument("time", Int::class.java)
            } catch (e: IllegalArgumentException) {
                source.sendError(Text.literal("Time must be specified for non-global cooldowns."))
                return 0
            }
            val timeMillis = time * 1000L
            val currentTime = System.currentTimeMillis()
            CobbleHunts.globalCooldownEnd = max(currentTime, CobbleHunts.globalCooldownEnd - timeMillis)
            source.sendMessage(Text.literal("Global hunt cooldown decreased by $time seconds."))
        } else {
            val time = try {
                context.getArgument("time", Int::class.java)
            } catch (e: IllegalArgumentException) {
                source.sendError(Text.literal("Time must be specified for non-global cooldowns."))
                return 0
            }
            val timeMillis = time * 1000L
            val currentTime = System.currentTimeMillis()
            val difficulty = when (huntType) {
                "soloeasy" -> "easy"
                "solonormal" -> "normal"
                "solomedium" -> "medium"
                "solohard" -> "hard"
                else -> {
                    source.sendError(Text.literal("Invalid hunt type: $huntType"))
                    return 0
                }
            }
            val target = getTargetPlayer(context)
            if (target == null) {
                source.sendError(Text.literal("Player not found or command executed from console without a target."))
                return 0
            }
            val data = CobbleHunts.getPlayerData(target)
            val currentEnd = data.cooldowns[difficulty] ?: 0
            data.cooldowns[difficulty] = max(currentTime, currentEnd - timeMillis)
            source.sendMessage(Text.literal("$huntType cooldown decreased by $time seconds for ${target.name.string}."))
        }
        return 1
    }

    // New: Revert turn-in command.
    private fun executeRevertTurnIn(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val targetName = context.getArgument("target", String::class.java)
        val pokemonName = context.getArgument("pokemon", String::class.java)
        val targetPlayer = source.server.playerManager.getPlayer(targetName)
        if (targetPlayer == null) {
            source.sendError(Text.literal("Target player not found."))
            return 0
        }
        val removedList = CobbleHunts.removedPokemonCache.getOrPut(targetPlayer.uuid) { mutableListOf() }
        val pokemon = removedList.firstOrNull { it.species.name.equals(pokemonName, ignoreCase = true) }
        if (pokemon == null) {
            source.sendError(Text.literal("No removed Pokémon with species \"$pokemonName\" found for ${targetPlayer.name.string}."))
            return 0
        }
        val party = Cobblemon.storage.getParty(targetPlayer)
        party.add(pokemon)
        removedList.remove(pokemon)
        source.sendMessage(Text.literal("Reverted turn-in: Returned ${pokemon.species.name} to ${targetPlayer.name.string}'s party."))
        return 1
    }
}