package com.cobblehunts.gui

import com.cobblehunts.CobbleHunts
import com.cobblehunts.HuntInstance
import com.cobblehunts.gui.huntsgui.PlayerHuntsGui
import com.cobblehunts.utils.*
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.Pokemon
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import com.mojang.serialization.JsonOps
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.RegistryOps
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import kotlin.random.Random

object TurnInGui {
    private val partySlots = listOf(1, 10, 19, 28, 37, 46)
    private val turnInButtonSlots = listOf(2, 11, 20, 29, 38, 47)
    private val turnInSlots = listOf(7, 16, 25, 34, 43, 52)
    private val cancelButtonSlots = listOf(6, 15, 24, 33, 42, 51)

    private object Textures {
        const val TURN_IN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTYzMzlmZjJlNTM0MmJhMThiZGM0OGE5OWNjYTY1ZDEyM2NlNzgxZDg3ODI3MmY5ZDk2NGVhZDNiOGFkMzcwIn19fQ=="
        const val CANCEL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmViNTg4YjIxYTZmOThhZDFmZjRlMDg1YzU1MmRjYjA1MGVmYzljYWI0MjdmNDYwNDhmMThmYzgwMzQ3NWY3In19fQ=="
        const val CONFIRM = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTc5YTVjOTVlZTE3YWJmZWY0NWM4ZGMyMjQxODk5NjQ5NDRkNTYwZjE5YTQ0ZjE5ZjhhNDZhZWYzZmVlNDc1NiJ9fX0="
        const val NOT_TARGET = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODlhOTk1OTI4MDkwZDg0MmQ0YWZkYjIyOTZmZmUyNGYyZTk0NDI3MjIwNWNlYmE4NDhlZTQwNDZlMDFmMzE2OCJ9fX0="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    fun openTurnInGui(player: ServerPlayerEntity, rarity: String, huntIndex: Int? = null) {
        val selectedForTurnIn = mutableListOf<Pokemon?>(null, null, null, null, null, null)
        CustomGui.openGui(
            player,
            "Turn In $rarity Hunt" + (huntIndex?.let { " #$it" } ?: ""),
            generateTurnInLayout(player, selectedForTurnIn, rarity, huntIndex),
            { context -> handleTurnInInteraction(context, player, selectedForTurnIn, rarity, huntIndex) },
            { /* Cleanup can be added here if needed */ }
        )
    }

    private fun generateTurnInLayout(
        player: ServerPlayerEntity,
        selectedForTurnIn: MutableList<Pokemon?>,
        rarity: String,
        huntIndex: Int?
    ): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }
        val party = CobbleHunts.getPlayerParty(player)

        partySlots.forEachIndexed { index, slot ->
            val pokemon = party.getOrNull(index)
            layout[slot] = if (pokemon != null) {
                if (selectedForTurnIn[index] == null) createPokemonItem(pokemon)
                else ItemStack(Items.RED_STAINED_GLASS_PANE).apply {
                    setCustomName(Text.literal("Selected").styled { it.withColor(Formatting.RED) })
                }
            } else {
                ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
            }
        }

        val activeHunt = if (rarity == "global") {
            huntIndex?.let { CobbleHunts.globalHuntStates.getOrNull(it) }
        } else {
            CobbleHunts.getPlayerData(player).activePokemon[rarity]
        }

        turnInButtonSlots.forEachIndexed { index, slot ->
            val pokemon = party.getOrNull(index)
            val isSelected = selectedForTurnIn[index] != null
            layout[slot] = if (pokemon != null && !isSelected && activeHunt != null) {
                if (pokemon.species.name.equals(activeHunt.entry.species, ignoreCase = true)) {
                    // Check if Pokémon was captured after the hunt started if config is enabled
                    if (HuntsConfig.config.onlyAllowTurnInIfCapturedAfterHuntStarted) {
                        val captureTime = CatchingTracker.getCaptureTime(pokemon.uuid)
                        val huntStartTime = activeHunt.startTime ?: 0L
                        if (captureTime == null || captureTime < huntStartTime) {
                            // Block moving it to turn in; show error button
                            CustomGui.createPlayerHeadButton(
                                textureName = "NotTarget",
                                title = Text.literal("Not Eligible").styled { it.withColor(Formatting.RED) },
                                lore = listOf(Text.literal("Captured before hunt started").styled { it.withColor(Formatting.RED) }),
                                textureValue = Textures.NOT_TARGET
                            )
                        } else {
                            // Valid capture time; check for attribute mismatches
                            val mismatchReasons = getAttributeMismatchReasons(pokemon, activeHunt)
                            if (mismatchReasons.isEmpty()) {
                                CustomGui.createPlayerHeadButton(
                                    textureName = "TurnIn",
                                    title = Text.literal("Turn In").styled { it.withColor(Formatting.YELLOW) },
                                    lore = listOf(Text.literal("Click to select for turn-in").styled { it.withColor(Formatting.GRAY) }),
                                    textureValue = Textures.TURN_IN
                                )
                            } else {
                                val lore = mismatchReasons.map { Text.literal(it).styled { it.withColor(Formatting.GRAY) } }
                                CustomGui.createPlayerHeadButton(
                                    textureName = "NotTarget",
                                    title = Text.literal("Missing Requirements").styled { it.withColor(Formatting.RED) },
                                    lore = lore,
                                    textureValue = Textures.NOT_TARGET
                                )
                            }
                        }
                    } else {
                        // Config flag is off – simply check for attribute mismatches
                        val mismatchReasons = getAttributeMismatchReasons(pokemon, activeHunt)
                        if (mismatchReasons.isEmpty()) {
                            CustomGui.createPlayerHeadButton(
                                textureName = "TurnIn",
                                title = Text.literal("Turn In").styled { it.withColor(Formatting.YELLOW) },
                                lore = listOf(Text.literal("Click to select for turn-in").styled { it.withColor(Formatting.GRAY) }),
                                textureValue = Textures.TURN_IN
                            )
                        } else {
                            val lore = mismatchReasons.map { Text.literal(it).styled { it.withColor(Formatting.GRAY) } }
                            CustomGui.createPlayerHeadButton(
                                textureName = "NotTarget",
                                title = Text.literal("Missing Requirements").styled { it.withColor(Formatting.RED) },
                                lore = lore,
                                textureValue = Textures.NOT_TARGET
                            )
                        }
                    }
                } else {
                    CustomGui.createPlayerHeadButton(
                        textureName = "NotTarget",
                        title = Text.literal("Incorrect Species").styled { it.withColor(Formatting.RED) },
                        lore = listOf(Text.literal("This is not the required Pokémon species").styled { it.withColor(Formatting.GRAY) }),
                        textureValue = Textures.NOT_TARGET
                    )
                }
            } else {
                ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
            }
        }

        turnInSlots.forEachIndexed { index, slot ->
            val selectedPokemon = selectedForTurnIn[index]
            layout[slot] = if (selectedPokemon != null) createPokemonItem(selectedPokemon)
            else ItemStack(Items.RED_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
        }

        cancelButtonSlots.forEachIndexed { index, slot ->
            layout[slot] = if (selectedForTurnIn[index] != null) {
                CustomGui.createPlayerHeadButton(
                    textureName = "Cancel",
                    title = Text.literal("Cancel Turn In").styled { it.withColor(Formatting.RED) },
                    lore = listOf(Text.literal("Click to move back to party").styled { it.withColor(Formatting.GRAY) }),
                    textureValue = Textures.CANCEL
                )
            } else {
                ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
            }
        }

        if (selectedForTurnIn.count { it != null } == 1) {
            layout[22] = CustomGui.createPlayerHeadButton(
                textureName = "Accept",
                title = Text.literal("Accept Turn In").styled { it.withColor(Formatting.GREEN) },
                lore = listOf(Text.literal("Click to accept selection").styled { it.withColor(Formatting.GRAY) }),
                textureValue = Textures.CONFIRM
            )
        } else {
            layout[22] = createFillerPane()
        }

        layout[49] = CustomGui.createPlayerHeadButton(
            textureName = "Back",
            title = Text.literal("Back").styled { it.withColor(Formatting.YELLOW) },
            lore = listOf(Text.literal("Return to previous menu").styled { it.withColor(Formatting.GRAY) }),
            textureValue = Textures.BACK
        )

        return layout
    }

    private fun handleTurnInInteraction(
        context: InteractionContext,
        player: ServerPlayerEntity,
        selectedForTurnIn: MutableList<Pokemon?>,
        rarity: String,
        huntIndex: Int?
    ) {
        val slot = context.slotIndex
        if (slot in turnInButtonSlots) {
            val index = turnInButtonSlots.indexOf(slot)
            val party = CobbleHunts.getPlayerParty(player)
            val pokemon = party.getOrNull(index)
            val activeHunt = if (rarity == "global") {
                huntIndex?.let { CobbleHunts.globalHuntStates.getOrNull(it) }
            } else {
                CobbleHunts.getPlayerData(player).activePokemon[rarity]
            }
            if (pokemon != null && selectedForTurnIn[index] == null && activeHunt != null) {
                if (pokemon.species.name.equals(activeHunt.entry.species, ignoreCase = true)) {
                    // NEW: Check capture time before selecting Pokémon
                    if (HuntsConfig.config.onlyAllowTurnInIfCapturedAfterHuntStarted) {
                        val captureTime = CatchingTracker.getCaptureTime(pokemon.uuid)
                        val huntStartTime = activeHunt.startTime ?: 0L
                        if (captureTime == null || captureTime < huntStartTime) {
                            player.sendMessage(
                                Text.literal("You cannot turn in a Pokémon captured before the hunt started.")
                                    .styled { it.withColor(Formatting.RED) },
                                false
                            )
                            return
                        }
                    }
                    val mismatchReasons = getAttributeMismatchReasons(pokemon, activeHunt)
                    if (mismatchReasons.isEmpty()) {
                        selectedForTurnIn[index] = pokemon
                        player.server.execute {
                            CustomGui.refreshGui(player, generateTurnInLayout(player, selectedForTurnIn, rarity, huntIndex))
                        }
                    } else {
                        val message = Text.literal("This Pokémon is missing the following requirements:")
                            .styled { it.withColor(Formatting.RED) }
                        mismatchReasons.forEach { reason ->
                            message.append("\n- $reason")
                        }
                        player.sendMessage(message, false)
                    }
                } else {
                    player.sendMessage(Text.literal("Incorrect species").styled { it.withColor(Formatting.RED) }, false)
                }
            }
        } else if (slot in cancelButtonSlots) {
            val index = cancelButtonSlots.indexOf(slot)
            if (selectedForTurnIn[index] != null) {
                selectedForTurnIn[index] = null
                player.server.execute {
                    CustomGui.refreshGui(player, generateTurnInLayout(player, selectedForTurnIn, rarity, huntIndex))
                }
            }
        } else if (slot == 22) {
            if (selectedForTurnIn.count { it != null } == 1) {
                val selectedPokemon = selectedForTurnIn.first { it != null }
                // Re-fetch the player's party for a security check.
                val party = Cobblemon.storage.getParty(player)
                if (selectedPokemon != null && party.contains(selectedPokemon)) {
                    println("Security check passed: Removing Pokémon ${selectedPokemon.species.name} from party.")
                    // Cache the removed Pokémon for potential revert.
                    CobbleHunts.removedPokemonCache.getOrPut(player.uuid) { mutableListOf() }.add(selectedPokemon)
                    // Remove the Pokémon from the party.
                    party.remove(selectedPokemon)
                } else {
                    println("Security check failed: Pokémon ${selectedPokemon?.species?.name ?: "null"} not found in party!")
                    player.sendMessage(
                        Text.literal("Security check failed: Pokémon not found in your party.")
                            .setStyle(Style.EMPTY.withItalic(false))
                            .styled { it.withColor(Formatting.RED) },
                        false
                    )
                    return
                }
                val data = CobbleHunts.getPlayerData(player)
                val activeHunt = if (rarity == "global") {
                    huntIndex?.let { CobbleHunts.globalHuntStates.getOrNull(it) }
                } else {
                    CobbleHunts.getPlayerData(player).activePokemon[rarity]
                }
                if (activeHunt != null && (activeHunt.endTime == null || System.currentTimeMillis() < activeHunt.endTime!!)) {
                    // NEW: Check capture time again before awarding rewards
                    if (HuntsConfig.config.onlyAllowTurnInIfCapturedAfterHuntStarted) {
                        val captureTime = CatchingTracker.getCaptureTime(selectedPokemon.uuid)
                        val huntStartTime = activeHunt.startTime ?: 0L
                        if (captureTime == null || captureTime < huntStartTime) {
                            player.sendMessage(
                                Text.literal("You cannot complete this hunt because the Pokémon was captured before it started.")
                                    .styled { it.withColor(Formatting.RED) },
                                false
                            )
                            return
                        }
                    }
                    // Award leaderboard points
                    val points = when (rarity) {
                        "easy" -> HuntsConfig.config.soloEasyPoints
                        "normal" -> HuntsConfig.config.soloNormalPoints
                        "medium" -> HuntsConfig.config.soloMediumPoints
                        "hard" -> HuntsConfig.config.soloHardPoints
                        "global" -> HuntsConfig.config.globalPoints
                        else -> 0
                    }
                    if (points > 0) {
                        println("Awarding $points points to ${player.name.string}")
                        LeaderboardManager.addPoints(player.name.string, points)
                        player.sendMessage(
                            Text.literal("You earned $points Leaderboard points!")
                                .styled { it.withColor(Formatting.GOLD) },
                            false
                        )
                    }
                    // Select reward from loot pool
                    val lootPool = when (rarity) {
                        "easy" -> HuntsConfig.config.soloEasyLoot
                        "normal" -> HuntsConfig.config.soloNormalLoot
                        "medium" -> HuntsConfig.config.soloMediumLoot
                        "hard" -> HuntsConfig.config.soloHardLoot
                        "global" -> HuntsConfig.config.globalLoot
                        else -> emptyList()
                    }
                    println("Loot pool size for $rarity: ${lootPool.size}")
                    val reward = selectRewardFromLootPool(lootPool)
                    if (reward != null) {
                        println("Selected reward type: ${reward.javaClass.simpleName}")
                        when (reward) {
                            is ItemReward -> {
                                val ops = RegistryOps.of(JsonOps.INSTANCE, player.server.registryManager)
                                val itemStack = reward.serializableItemStack.toItemStack(ops)
                                println("Giving item: ${itemStack.item.name.string}")
                                player.inventory.offerOrDrop(itemStack)
                                player.sendMessage(
                                    Text.literal("You received ${itemStack.name.string}!")
                                        .styled { it.withColor(Formatting.GREEN) },
                                    false
                                )
                            }
                            is CommandReward -> {
                                val commandToExecute = reward.command.replace("%player%", player.name.string)
                                println("Original command: ${reward.command}")
                                println("Executing command: $commandToExecute")
                                try {
                                    player.server.commandManager.executeWithPrefix(player.server.commandSource, commandToExecute)
                                    println("Command executed successfully: $commandToExecute")
                                    player.sendMessage(
                                        Text.literal("Hunt completed!")
                                            .styled { it.withColor(Formatting.GREEN) },
                                        false
                                    )
                                } catch (e: Exception) {
                                    println("Command execution failed for: $commandToExecute with error: ${e.message}")
                                    player.sendMessage(
                                        Text.literal("Failed to execute reward command: ${e.message}")
                                            .styled { it.withColor(Formatting.RED) },
                                        false
                                    )
                                }
                            }
                        }
                    } else {
                        println("No reward selected from loot pool for $rarity")
                        player.sendMessage(
                            Text.literal("Hunt completed, but no reward was available.")
                                .styled { it.withColor(Formatting.YELLOW) },
                            false
                        )
                    }
                    // Complete the hunt
                    println("Completing hunt for rarity: $rarity" + (huntIndex?.let { " #$it" } ?: ""))
                    if (rarity == "global" && huntIndex != null) {
                        if (HuntsConfig.config.lockGlobalHuntsOnCompletionForAllPlayers) {
                            CobbleHunts.globalCompletedHuntIndices.add(huntIndex)
                            println("Global hunt #$huntIndex locked as completed globally.")
                        } else {
                            data.completedGlobalHunts.add(huntIndex)
                            println("Marked global hunt #$huntIndex as completed for ${player.name.string}")
                        }
                    } else {
                        data.activePokemon.remove(rarity)
                        val cooldownTime = when (rarity) {
                            "easy" -> HuntsConfig.config.soloEasyCooldown
                            "normal" -> HuntsConfig.config.soloNormalCooldown
                            "medium" -> HuntsConfig.config.soloMediumCooldown
                            "hard" -> HuntsConfig.config.soloHardCooldown
                            else -> 0
                        }
                        if (cooldownTime > 0) {
                            data.cooldowns[rarity] = System.currentTimeMillis() + (cooldownTime * 1000L)
                            println("Set cooldown for $rarity to ${cooldownTime}s")
                        }
                    }
                    player.closeHandledScreen()
                    if (rarity == "global") {
                        PlayerHuntsGui.openGlobalHuntsGui(player)
                    }
                } else {
                    println("Hunt expired or inactive for rarity: $rarity" + (huntIndex?.let { " #$it" } ?: ""))
                    player.sendMessage(
                        Text.literal("The hunt has expired or is no longer active.")
                            .styled { it.withColor(Formatting.RED) },
                        false
                    )
                    player.closeHandledScreen()
                }
            }
        } else if (slot == 49) {
            player.server.execute {
                if (rarity == "global") {
                    PlayerHuntsGui.openGlobalHuntsGui(player)
                } else {
                    PlayerHuntsGui.openSoloHuntsGui(player)
                }
            }
        }
    }

    private fun getAttributeMismatchReasons(pokemon: Pokemon, activeHunt: HuntInstance): List<String> {
        val reasons = mutableListOf<String>()
        val requiredEntry = activeHunt.entry

        if (!pokemon.species.name.equals(requiredEntry.species, ignoreCase = true)) {
            reasons.add("Incorrect species")
        }
        if (requiredEntry.form != null && !pokemon.form.name.equals(requiredEntry.form, ignoreCase = true)) {
            reasons.add("Incorrect form")
        }
        if (!pokemon.aspects.containsAll(requiredEntry.aspects)) {
            reasons.add("Missing required aspects")
        }
        if (activeHunt.requiredGender != null && !pokemon.gender.name.equals(activeHunt.requiredGender, ignoreCase = true)) {
            reasons.add("Incorrect gender")
        }
        if (activeHunt.requiredNature != null && !pokemon.nature.name.path.equals(activeHunt.requiredNature, ignoreCase = true)) {
            reasons.add("Incorrect nature")
        }
        if (activeHunt.requiredIVs.isNotEmpty()) {
            val lowIVs = activeHunt.requiredIVs.filter { iv ->
                val stat = when (iv.lowercase()) {
                    "hp" -> Stats.HP
                    "attack" -> Stats.ATTACK
                    "defense" -> Stats.DEFENCE
                    "special_attack" -> Stats.SPECIAL_ATTACK
                    "special_defense" -> Stats.SPECIAL_DEFENCE
                    "speed" -> Stats.SPEED
                    else -> null
                }
                stat != null && (pokemon.ivs[stat] ?: 0) < 20
            }
            if (lowIVs.isNotEmpty()) {
                reasons.add("Low IVs in: ${lowIVs.joinToString(", ")}")
            }
        }
        return reasons
    }

    private fun selectRewardFromLootPool(lootPool: List<LootReward>): LootReward? {
        if (lootPool.isEmpty()) return null
        val totalChance = lootPool.sumOf { it.chance }
        val randomValue = Random.nextDouble() * totalChance
        var cumulativeChance = 0.0
        for (reward in lootPool) {
            cumulativeChance += reward.chance
            if (randomValue <= cumulativeChance) {
                return reward
            }
        }
        return lootPool.last()
    }

    private fun createPokemonItem(pokemon: Pokemon): ItemStack {
        val item = PokemonItem.from(pokemon)
        val displayName = pokemon.species.name.replaceFirstChar { it.titlecase() }
        item.setCustomName(Text.literal(displayName).styled { it.withColor(Formatting.WHITE) })
        return item
    }

    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
    }
}
