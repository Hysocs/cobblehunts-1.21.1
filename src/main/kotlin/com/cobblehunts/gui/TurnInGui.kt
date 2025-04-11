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

        // Set party display slots
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

        // Get the active hunt instance based on hunt type.
        val activeHunt = if (rarity == "global") {
            huntIndex?.let { CobbleHunts.globalHuntStates.getOrNull(it) }
        } else {
            CobbleHunts.getPlayerData(player).activePokemon[rarity]
        }

        // Build the turn‑in selection buttons.
        turnInButtonSlots.forEachIndexed { index, slot ->
            val pokemon = party.getOrNull(index)
            val isSelected = selectedForTurnIn[index] != null
            layout[slot] = if (pokemon != null && !isSelected && activeHunt != null) {
                if (pokemon.species.name.equals(activeHunt.entry.species, ignoreCase = true)) {
                    if (HuntsConfig.config.onlyAllowTurnInIfCapturedAfterHuntStarted) {
                        val captureTime = CatchingTracker.getCaptureTime(pokemon.uuid)
                        val huntStartTime = activeHunt.startTime ?: 0L
                        if (captureTime == null || captureTime < huntStartTime) {
                            // Show error button if captured too early.
                            CustomGui.createPlayerHeadButton(
                                textureName = "NotTarget",
                                title = Text.literal("Not Eligible").styled { it.withColor(Formatting.RED) },
                                lore = listOf(
                                    Text.literal("Captured before hunt started").styled { it.withColor(Formatting.RED) }
                                ),
                                textureValue = Textures.NOT_TARGET
                            )
                        } else {
                            val mismatches = getAttributeMismatchReasons(pokemon, activeHunt)
                            if (mismatches.isEmpty()) {
                                CustomGui.createPlayerHeadButton(
                                    textureName = "TurnIn",
                                    title = Text.literal("Turn In").styled { it.withColor(Formatting.YELLOW) },
                                    lore = listOf(
                                        Text.literal("Click to select for turn-in").styled { it.withColor(Formatting.GRAY) }
                                    ),
                                    textureValue = Textures.TURN_IN
                                )
                            } else {
                                val lore = mismatches.map { Text.literal(it).styled { it.withColor(Formatting.GRAY) } }
                                CustomGui.createPlayerHeadButton(
                                    textureName = "NotTarget",
                                    title = Text.literal("Missing Requirements").styled { it.withColor(Formatting.RED) },
                                    lore = lore,
                                    textureValue = Textures.NOT_TARGET
                                )
                            }
                        }
                    } else {
                        val mismatches = getAttributeMismatchReasons(pokemon, activeHunt)
                        if (mismatches.isEmpty()) {
                            CustomGui.createPlayerHeadButton(
                                textureName = "TurnIn",
                                title = Text.literal("Turn In").styled { it.withColor(Formatting.YELLOW) },
                                lore = listOf(
                                    Text.literal("Click to select for turn-in").styled { it.withColor(Formatting.GRAY) }
                                ),
                                textureValue = Textures.TURN_IN
                            )
                        } else {
                            val lore = mismatches.map { Text.literal(it).styled { it.withColor(Formatting.GRAY) } }
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
                        lore = listOf(
                            Text.literal("This is not the required Pokémon species").styled { it.withColor(Formatting.GRAY) }
                        ),
                        textureValue = Textures.NOT_TARGET
                    )
                }
            } else {
                ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
            }
        }

        // Display selected Pokémon in the turn‑in slots.
        turnInSlots.forEachIndexed { index, slot ->
            val selectedPokemon = selectedForTurnIn[index]
            layout[slot] = if (selectedPokemon != null) createPokemonItem(selectedPokemon)
            else ItemStack(Items.RED_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
        }

        // Set cancel buttons for any already-selected party slots.
        cancelButtonSlots.forEachIndexed { index, slot ->
            layout[slot] = if (selectedForTurnIn[index] != null) {
                CustomGui.createPlayerHeadButton(
                    textureName = "Cancel",
                    title = Text.literal("Cancel Turn In").styled { it.withColor(Formatting.RED) },
                    lore = listOf(
                        Text.literal("Click to move back to party").styled { it.withColor(Formatting.GRAY) }
                    ),
                    textureValue = Textures.CANCEL
                )
            } else {
                ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
            }
        }

        // Create the confirm (accept) button at slot 22.
        // Its lore now reflects whether the Pokémon will be taken or just checked and returned.
        if (selectedForTurnIn.count { it != null } == 1) {
            val confirmLore = if (HuntsConfig.config.takeMonOnTurnIn) {
                listOf(
                    Text.literal("Confirm: Your Pokémon will be taken from your party.")
                        .styled { it.withColor(Formatting.GRAY) }
                )
            } else {
                listOf(
                    Text.literal("Confirm: Your Pokémon will only be checked and then returned.")
                        .styled { it.withColor(Formatting.GRAY) }
                )
            }
            layout[22] = CustomGui.createPlayerHeadButton(
                textureName = "Accept",
                title = Text.literal("Accept Turn In").styled { it.withColor(Formatting.GREEN) },
                lore = confirmLore,
                textureValue = Textures.CONFIRM
            )
        } else {
            layout[22] = createFillerPane()
        }

        // Back button
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
            // Handle selection of a party Pokémon for turn-in.
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
                    // Check capture time if configured.
                    if (HuntsConfig.config.onlyAllowTurnInIfCapturedAfterHuntStarted) {
                        val captureTime = CatchingTracker.getCaptureTime(pokemon.uuid)
                        val huntStartTime = activeHunt.startTime ?: 0L
                        if (captureTime == null || captureTime < huntStartTime) {
                            // Do nothing; in a full implementation you might update the GUI to show an error.
                            return
                        }
                    }
                    val mismatches = getAttributeMismatchReasons(pokemon, activeHunt)
                    if (mismatches.isEmpty()) {
                        selectedForTurnIn[index] = pokemon
                        player.server.execute {
                            CustomGui.refreshGui(player, generateTurnInLayout(player, selectedForTurnIn, rarity, huntIndex))
                        }
                    } else {
                        // Optionally handle mismatches (for example, by updating the GUI).
                    }
                }
            }
        } else if (slot in cancelButtonSlots) {
            // Handle cancellation of a selection.
            val index = cancelButtonSlots.indexOf(slot)
            if (selectedForTurnIn[index] != null) {
                selectedForTurnIn[index] = null
                player.server.execute {
                    CustomGui.refreshGui(player, generateTurnInLayout(player, selectedForTurnIn, rarity, huntIndex))
                }
            }
        } else if (slot == 22) {
            // Confirm turn-in button clicked.
            if (selectedForTurnIn.count { it != null } == 1) {
                val selectedPokemon = selectedForTurnIn.first { it != null }
                val party = Cobblemon.storage.getParty(player)
                if (selectedPokemon != null && party.contains(selectedPokemon)) {
                    // Only remove the Pokémon from the party if takeMonOnTurnIn is true.
                    if (HuntsConfig.config.takeMonOnTurnIn) {
                        CobbleHunts.removedPokemonCache.getOrPut(player.uuid) { mutableListOf() }.add(selectedPokemon)
                        party.remove(selectedPokemon)
                    }
                } else {
                    // Security check failed: Do nothing.
                    return
                }
                val data = CobbleHunts.getPlayerData(player)
                val activeHuntConfirmed = if (rarity == "global") {
                    huntIndex?.let { CobbleHunts.globalHuntStates.getOrNull(it) }
                } else {
                    CobbleHunts.getPlayerData(player).activePokemon[rarity]
                }
                if (activeHuntConfirmed != null && (activeHuntConfirmed.endTime == null || System.currentTimeMillis() < activeHuntConfirmed.endTime!!)) {
                    // Re-check capture time if configured.
                    if (HuntsConfig.config.onlyAllowTurnInIfCapturedAfterHuntStarted) {
                        val captureTime = CatchingTracker.getCaptureTime(selectedPokemon.uuid)
                        val huntStartTime = activeHuntConfirmed.startTime ?: 0L
                        if (captureTime == null || captureTime < huntStartTime) {
                            return
                        }
                    }
                    // Award leaderboard points.
                    val points = when (rarity) {
                        "easy"   -> HuntsConfig.config.soloEasyPoints
                        "normal" -> HuntsConfig.config.soloNormalPoints
                        "medium" -> HuntsConfig.config.soloMediumPoints
                        "hard"   -> HuntsConfig.config.soloHardPoints
                        "global" -> HuntsConfig.config.globalPoints
                        else     -> 0
                    }
                    if (points > 0) {
                        LeaderboardManager.addPoints(player.name.string, points)
                    }
                    // Select and process reward.
                    val lootPool = when (rarity) {
                        "easy"   -> HuntsConfig.config.soloEasyLoot
                        "normal" -> HuntsConfig.config.soloNormalLoot
                        "medium" -> HuntsConfig.config.soloMediumLoot
                        "hard"   -> HuntsConfig.config.soloHardLoot
                        "global" -> HuntsConfig.config.globalLoot
                        else     -> emptyList()
                    }
                    val reward = selectRewardFromLootPool(lootPool)
                    if (reward != null) {
                        when (reward) {
                            is ItemReward -> {
                                val ops = RegistryOps.of(JsonOps.INSTANCE, player.server.registryManager)
                                val itemStack = reward.serializableItemStack.toItemStack(ops)
                                player.inventory.offerOrDrop(itemStack)
                            }
                            is CommandReward -> {
                                val commandToExecute = reward.command.replace("%player%", player.name.string)
                                try {
                                    player.server.commandManager.executeWithPrefix(player.server.commandSource, commandToExecute)
                                } catch (e: Exception) {
                                    // Optionally handle command execution failure.
                                }
                            }
                        }
                    }
                    // Mark the hunt as complete.
                    if (rarity == "global" && huntIndex != null) {
                        if (HuntsConfig.config.lockGlobalHuntsOnCompletionForAllPlayers) {
                            CobbleHunts.globalCompletedHuntIndices.add(huntIndex)
                        } else {
                            data.completedGlobalHunts.add(huntIndex)
                        }
                    } else {
                        data.activePokemon.remove(rarity)
                        val cooldownTime = when (rarity) {
                            "easy"   -> HuntsConfig.config.soloEasyCooldown
                            "normal" -> HuntsConfig.config.soloNormalCooldown
                            "medium" -> HuntsConfig.config.soloMediumCooldown
                            "hard"   -> HuntsConfig.config.soloHardCooldown
                            else     -> 0
                        }
                        if (cooldownTime > 0) {
                            data.cooldowns[rarity] = System.currentTimeMillis() + (cooldownTime * 1000L)
                        }
                    }
                    // Finally, close the GUI and refresh the main hunt screen.
                    player.closeHandledScreen()
                    if (rarity == "global") {
                        PlayerHuntsGui.openGlobalHuntsGui(player)
                    }
                } else {
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
