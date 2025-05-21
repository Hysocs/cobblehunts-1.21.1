package com.cobblehunts.gui

import com.cobblehunts.CobbleHunts
import com.cobblehunts.HuntInstance
import com.cobblehunts.gui.huntsgui.HuntsGlobalGui
import com.cobblehunts.gui.huntsgui.HuntsSoloGui
import com.cobblehunts.utils.*
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.Pokemon
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.RegistryOps
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting

object TurnInGui {
    private val PARTY_SLOTS = listOf(1, 10, 19, 28, 37, 46)
    private val TURN_IN_BUTTON_SLOTS = listOf(2, 11, 20, 29, 38, 47)
    private val TURN_IN_SLOTS = listOf(7, 16, 25, 34, 43, 52)
    private val CANCEL_BUTTON_SLOTS = listOf(6, 15, 24, 33, 42, 51)
    private const val CONFIRM_BUTTON_SLOT = 22
    private const val BACK_BUTTON_SLOT = 49
    private const val GUI_SIZE = 54

    private object Textures {
        const val TURN_IN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTYzMzlmZjJlNTM0MmJhMThiZGM0OGE5OWNjYTY1ZDEyM2NlNzgxZDg3ODI3MmY5ZDk2NGVhZDNiOGFkMzcwIn19fQ=="
        const val CANCEL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmViNTg4YjIxYTZmOThhZDFmZjRlMDg1YzU1MmRjYjA1MGVmYzljYWI0MjdmNDYwNDhmMThmYzgwMzQ3NWY3In19fQ=="
        const val CONFIRM = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTc5YTVjOTVlZTE3YWJmZWY0NWM4ZGMyMjQxODk5NjQ5NDRkNTYwZjE5YTQ0ZjE5ZjhhNDZhZWYzZmVlNDc1NiJ9fX0="
        const val NOT_TARGET = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODlhOTk1OTI4MDkwZDg0MmQ0YWZkYjIyOTZmZmUyNGYyZTk0NDI3MjIwNWNlYmE4NDhlZTQwNDZlMDFmMzE2OCJ9fX0="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    fun openTurnInGui(player: ServerPlayerEntity, rarity: String, huntIndex: Int? = null) {
        val selectedForTurnIn = MutableList(6) { null as Pokemon? }
        val title = "Turn In ${rarity.replaceFirstChar { it.titlecase() }} Hunt${huntIndex?.let { " #$it" } ?: ""}"
        CustomGui.openGui(
            player,
            title,
            generateTurnInLayout(player, selectedForTurnIn, rarity, huntIndex),
            { context -> handleTurnInInteraction(context, player, selectedForTurnIn, rarity, huntIndex) },
            { }
        )
    }

    private fun generateTurnInLayout(
        player: ServerPlayerEntity,
        selectedForTurnIn: MutableList<Pokemon?>,
        rarity: String,
        huntIndex: Int?
    ): List<ItemStack> {
        val layout = MutableList(GUI_SIZE) { createFillerPane() }
        val party = CobbleHunts.getPlayerParty(player)
        val activeHunt = getActiveHunt(player, rarity, huntIndex) ?: return layout

        PARTY_SLOTS.forEachIndexed { index, slot ->
            layout[slot] = createPartySlotItem(party.getOrNull(index), selectedForTurnIn[index])
        }

        TURN_IN_BUTTON_SLOTS.forEachIndexed { index, slot ->
            layout[slot] = createTurnInButton(
                player,
                party.getOrNull(index),
                selectedForTurnIn[index],
                activeHunt
            )
        }

        TURN_IN_SLOTS.forEachIndexed { index, slot ->
            layout[slot] = selectedForTurnIn[index]?.let { createPokemonItem(it) }
                ?: ItemStack(Items.RED_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
        }

        CANCEL_BUTTON_SLOTS.forEachIndexed { index, slot ->
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
            layout[CONFIRM_BUTTON_SLOT] = createConfirmButton()
        }

        layout[BACK_BUTTON_SLOT] = CustomGui.createPlayerHeadButton(
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
        when (context.slotIndex) {
            in TURN_IN_BUTTON_SLOTS -> handleTurnInSelection(context.slotIndex, player, selectedForTurnIn, rarity, huntIndex)
            in CANCEL_BUTTON_SLOTS -> handleCancelSelection(context.slotIndex, selectedForTurnIn, player, rarity, huntIndex)
            CONFIRM_BUTTON_SLOT -> handleConfirmTurnIn(player, selectedForTurnIn, rarity, huntIndex)
            BACK_BUTTON_SLOT -> navigateBack(player, rarity)
        }
    }

    private fun getActiveHunt(player: ServerPlayerEntity, rarity: String, huntIndex: Int?): HuntInstance? {
        return if (rarity == "global") {
            huntIndex?.let { CobbleHunts.globalHuntStates.getOrNull(it) }
        } else {
            CobbleHunts.getPlayerData(player).activePokemon[rarity]
        }
    }

    private fun createPartySlotItem(pokemon: Pokemon?, selected: Pokemon?): ItemStack {
        return when {
            pokemon == null -> ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
            selected != null -> ItemStack(Items.RED_STAINED_GLASS_PANE).apply {
                setCustomName(Text.literal("Selected").styled { it.withColor(Formatting.RED) })
            }
            else -> createPokemonItem(pokemon)
        }
    }

    private fun createTurnInButton(
        player: ServerPlayerEntity,
        pokemon: Pokemon?,
        selected: Pokemon?,
        activeHunt: HuntInstance
    ): ItemStack {
        if (pokemon != null) {
            val used = CobbleHunts.getPlayerData(player).usedPokemon.contains(pokemon.uuid)
            if (used) {
                return CustomGui.createPlayerHeadButton(
                    textureName = "NotTarget",
                    title = Text.literal("Not Eligible").styled { it.withColor(Formatting.RED) },
                    lore = listOf(
                        Text.literal("This Pokémon has already been turned in")
                            .styled { it.withColor(Formatting.GRAY) }
                    ),
                    textureValue = Textures.NOT_TARGET
                )
            }
        }

        if (pokemon == null || selected != null) {
            return ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
                .apply { setCustomName(Text.literal("")) }
        }

        if (!SpeciesMatcher.matches(pokemon, activeHunt.entry.species)) {
            val expectedPrettyName = SpeciesMatcher.getPrettyName(activeHunt.entry.species)
            return CustomGui.createPlayerHeadButton(
                textureName = "NotTarget",
                title = Text.literal("Incorrect Species").styled { it.withColor(Formatting.RED) },
                lore = listOf(
                    Text.literal("This is not ${expectedPrettyName}.")
                        .styled { it.withColor(Formatting.GRAY) }
                ),
                textureValue = Textures.NOT_TARGET
            )
        }

        if (HuntsConfig.config.onlyAllowTurnInIfCapturedAfterHuntStarted) {
            val captureTime = CatchingTracker.getCaptureTime(pokemon.uuid)
            val huntStart = activeHunt.startTime ?: 0L
            if (captureTime == null || captureTime < huntStart) {
                return CustomGui.createPlayerHeadButton(
                    textureName = "NotTarget",
                    title = Text.literal("Not Eligible").styled { it.withColor(Formatting.RED) },
                    lore = listOf(
                        Text.literal("Captured before hunt started")
                            .styled { it.withColor(Formatting.RED) }
                    ),
                    textureValue = Textures.NOT_TARGET
                )
            }
        }

        val mismatches = getAttributeMismatchReasons(pokemon, activeHunt)

        return if (mismatches.isEmpty()) {
            CustomGui.createPlayerHeadButton(
                textureName = "TurnIn",
                title = Text.literal("Turn In").styled { it.withColor(Formatting.YELLOW) },
                lore = listOf(
                    Text.literal("Click to select for turn‑in")
                        .styled { it.withColor(Formatting.GRAY) }
                ),
                textureValue = Textures.TURN_IN
            )
        } else {
            val loreEntries = mismatches.map { mismatchString ->
                var textToShow: String
                if (mismatchString.startsWith("FORM_MISMATCH::")) {
                    try {
                        val parts = mismatchString.split("::")
                        val expectedForm = parts[1].substringAfter("Expected:")
                        val actualForm = parts[2].substringAfter("Actual:")
                        textToShow = "Incorrect form. Hunt requires: $expectedForm. Your Pokémon's form: $actualForm."
                    } catch (e: Exception) {
                        textToShow = "Incorrect form (details unavailable)."
                    }
                } else {
                    textToShow = mismatchString
                }
                Text.literal(textToShow).styled { it.withColor(Formatting.GRAY) }
            }

            CustomGui.createPlayerHeadButton(
                textureName = "NotTarget",
                title = Text.literal("Missing Requirements").styled { it.withColor(Formatting.RED) },
                lore = loreEntries,
                textureValue = Textures.NOT_TARGET
            )
        }
    }


    private fun createConfirmButton(): ItemStack {
        val lore = if (HuntsConfig.config.takeMonOnTurnIn) {
            listOf(Text.literal("Confirm: Your Pokémon will be taken from your party.").styled { it.withColor(Formatting.GRAY) })
        } else {
            listOf(Text.literal("Confirm: Your Pokémon will only be checked and then returned.").styled { it.withColor(Formatting.GRAY) })
        }
        return CustomGui.createPlayerHeadButton(
            textureName = "Accept",
            title = Text.literal("Accept Turn In").styled { it.withColor(Formatting.GREEN) },
            lore = lore,
            textureValue = Textures.CONFIRM
        )
    }

    private fun handleTurnInSelection(
        slot: Int,
        player: ServerPlayerEntity,
        selectedForTurnIn: MutableList<Pokemon?>,
        rarity: String,
        huntIndex: Int?
    ) {
        val index = TURN_IN_BUTTON_SLOTS.indexOf(slot)
        val party = CobbleHunts.getPlayerParty(player)
        val pokemon = party.getOrNull(index) ?: return
        val activeHunt = getActiveHunt(player, rarity, huntIndex) ?: return

        if (selectedForTurnIn[index] != null || !SpeciesMatcher.matches(pokemon, activeHunt.entry.species)) {
            return
        }

        if (HuntsConfig.config.onlyAllowTurnInIfCapturedAfterHuntStarted) {
            val captureTime = CatchingTracker.getCaptureTime(pokemon.uuid)
            val huntStartTime = activeHunt.startTime ?: 0L
            if (captureTime == null || captureTime < huntStartTime) {
                return
            }
        }

        if (getAttributeMismatchReasons(pokemon, activeHunt).isEmpty()) {
            selectedForTurnIn[index] = pokemon
            refreshGui(player, selectedForTurnIn, rarity, huntIndex)
        }
    }

    private fun handleCancelSelection(
        slot: Int,
        selectedForTurnIn: MutableList<Pokemon?>,
        player: ServerPlayerEntity,
        rarity: String,
        huntIndex: Int?
    ) {
        val index = CANCEL_BUTTON_SLOTS.indexOf(slot)
        if (selectedForTurnIn[index] != null) {
            selectedForTurnIn[index] = null
            refreshGui(player, selectedForTurnIn, rarity, huntIndex)
        }
    }

    private fun handleConfirmTurnIn(
        player: ServerPlayerEntity,
        selectedForTurnIn: MutableList<Pokemon?>,
        rarity: String,
        huntIndex: Int?,
        openGui: Boolean = true
    ) {
        val data = CobbleHunts.getPlayerData(player)
        val selectedPokemon = selectedForTurnIn.firstOrNull { it != null } ?: return

        if (data.usedPokemon.contains(selectedPokemon.uuid)) {
            return
        }

        val party = Cobblemon.storage.getParty(player)
        if (!party.contains(selectedPokemon)) return

        val activeHunt = getActiveHunt(player, rarity, huntIndex) ?: return
        if (activeHunt.endTime != null && System.currentTimeMillis() >= activeHunt.endTime!!) {
            player.closeHandledScreen()
            return
        }

        if (HuntsConfig.config.onlyAllowTurnInIfCapturedAfterHuntStarted) {
            val captureTime = CatchingTracker.getCaptureTime(selectedPokemon.uuid)
            val huntStart = activeHunt.startTime ?: 0L
            if (captureTime == null || captureTime < huntStart) return
        }

        data.usedPokemon.add(selectedPokemon.uuid)

        if (HuntsConfig.config.takeMonOnTurnIn) {
            CobbleHunts.removedPokemonCache
                .getOrPut(player.uuid) { mutableListOf() }
                .add(selectedPokemon)
            party.remove(selectedPokemon)
        }

        processRewardsAndLeaderboard(player, rarity, selectedPokemon, activeHunt.rewards)
        markHuntComplete(player, rarity, huntIndex)

        if (rarity == "global") {
            val ops = RegistryOps.of(JsonOps.INSTANCE, player.server.registryManager)
            val rewardStrings = activeHunt.rewards.map { getRewardString(it, ops) }
            val rewardMessage = rewardStrings.joinToString(", ")
            val speciesName = SpeciesMatcher.getPrettyName(activeHunt.entry.species)
            val msg = HuntsConfig.config.globalHuntCompletionMessage
                .replace("%player%", player.name.string)
                .replace("%pokemon%", speciesName)
                .replace("%reward%", rewardMessage)
            CobbleHunts.broadcast(player.server, msg)

            player.closeHandledScreen()
            if (openGui) {
                HuntsGlobalGui.openGlobalHuntsGui(player)
            }
        } else {
            player.closeHandledScreen()
        }
    }

    private fun getRewardString(reward: LootReward, ops: RegistryOps<JsonElement>): String {
        return when (reward) {
            is ItemReward -> {
                val itemStack = reward.serializableItemStack.toItemStack(ops)
                val displayName = itemStack.get(DataComponentTypes.CUSTOM_NAME)?.string?.replace("§[0-9a-fk-or]".toRegex(), "") ?: itemStack.item.name.string
                "${itemStack.count} $displayName"
            }
            is CommandReward -> {
                reward.serializableItemStack?.let { serializableItemStack ->
                    val itemStack = serializableItemStack.toItemStack(ops)
                    itemStack.get(DataComponentTypes.CUSTOM_NAME)?.string?.replace("§[0-9a-fk-or]".toRegex(), "") ?: itemStack.item.name.string
                } ?: "a special reward"
            }
        }
    }

    private fun processRewardsAndLeaderboard(
        player: ServerPlayerEntity,
        rarity: String,
        pokemon: Pokemon,
        rewards: List<LootReward>
    ) {
        val points = when (rarity) {
            "easy" -> HuntsConfig.config.soloEasyPoints
            "normal" -> HuntsConfig.config.soloNormalPoints
            "medium" -> HuntsConfig.config.soloMediumPoints
            "hard" -> HuntsConfig.config.soloHardPoints
            "global" -> HuntsConfig.config.globalPoints
            else -> 0
        }
        if (points > 0) {
            LeaderboardManager.addPoints(player.name.string, points)
        }

        if (rewards.isNotEmpty()) {
            val ops = RegistryOps.of(JsonOps.INSTANCE, player.server.registryManager)
            val rewardStrings = rewards.map { getRewardString(it, ops) }
            val rewardMessage = rewardStrings.joinToString(", ")
            rewards.forEach { reward ->
                when (reward) {
                    is ItemReward -> {
                        val itemStack = reward.serializableItemStack.toItemStack(ops)
                        player.inventory.offerOrDrop(itemStack)
                    }
                    is CommandReward -> {
                        val command = reward.command.replace("%player%", player.name.string)
                        try {
                            player.server.commandManager.executeWithPrefix(player.server.commandSource, command)
                        } catch (e: Exception) {
                            // Log error if needed
                        }
                    }
                }
            }
            if (rarity != "global") {
                val message = HuntsConfig.config.soloHuntCompletionMessage
                    .replace("%reward%", rewardMessage)
                CobbleHunts.broadcast(player.server, message, player)
            }
        }
    }
    private fun markHuntComplete(player: ServerPlayerEntity, rarity: String, huntIndex: Int?) {
        val data = CobbleHunts.getPlayerData(player)
        if (rarity == "global" && huntIndex != null) {
            if (HuntsConfig.config.lockGlobalHuntsOnCompletionForAllPlayers) {
                CobbleHunts.globalCompletedHuntIndices.add(huntIndex)
            } else {
                data.completedGlobalHunts.add(huntIndex)
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
            }
        }
    }

    private fun navigateBack(player: ServerPlayerEntity, rarity: String) {
        player.server.execute {
            if (rarity == "global") {
                HuntsGlobalGui.openGlobalHuntsGui(player)
            } else {
                HuntsSoloGui.openSoloHuntsGui(player)
            }
        }
    }

    private fun refreshGui(player: ServerPlayerEntity, selectedForTurnIn: MutableList<Pokemon?>, rarity: String, huntIndex: Int?) {
        player.server.execute {
            CustomGui.refreshGui(player, generateTurnInLayout(player, selectedForTurnIn, rarity, huntIndex))
        }
    }

    private fun getAttributeMismatchReasons(pokemon: Pokemon, hunt: HuntInstance): List<String> {
        val reasons = mutableListOf<String>()
        val e = hunt.entry // HuntPokemonEntry from the config

        // Species check is now done before calling this usually, but kept for robustness
        if (!SpeciesMatcher.matches(pokemon, e.species)) {
            reasons += "Incorrect species" // This might be redundant if createTurnInButton already handles it
        }

        // Form check
        val expectedFormIdentifier = e.form // from HuntPokemonEntry (config), can be null for standard
        val actualFormName = pokemon.form.name // from Pokemon object, e.g., "Standard", "Alolan"
        val speciesStandardFormName = pokemon.species.standardForm.name // e.g., "Standard", "Base", "Normal"

        if (expectedFormIdentifier == null) { // Hunt expects standard/default form
            // A Pokemon's actual form name should match its species' defined standard form name
            if (!actualFormName.equals(speciesStandardFormName, ignoreCase = true)) {
                reasons += "FORM_MISMATCH::Expected:${speciesStandardFormName.replaceFirstChar { it.titlecase() }}::Actual:${actualFormName.replaceFirstChar { it.titlecase() }}"
            }
        } else { // Hunt expects a specific named form
            if (!actualFormName.equals(expectedFormIdentifier, ignoreCase = true)) {
                reasons += "FORM_MISMATCH::Expected:${expectedFormIdentifier.replaceFirstChar { it.titlecase() }}::Actual:${actualFormName.replaceFirstChar { it.titlecase() }}"
            }
        }

        if (!pokemon.aspects.containsAll(e.aspects)) {
            reasons += "Missing required aspects: ${e.aspects.minus(pokemon.aspects).joinToString()}"
        }
        hunt.requiredGender?.let { req ->
            if (!pokemon.gender.name.equals(req, ignoreCase = true)) {
                reasons += "Incorrect gender. Expected: ${req.replaceFirstChar { it.titlecase() }}. Actual: ${pokemon.gender.name.replaceFirstChar { it.titlecase() }}."
            }
        }
        hunt.requiredNature?.let { req ->
            if (!pokemon.nature.name.path.equals(req, ignoreCase = true)) {
                reasons += "Incorrect nature. Expected: ${req.replaceFirstChar { it.titlecase() }}. Actual: ${pokemon.nature.name.path.substringAfterLast('/').replaceFirstChar { it.titlecase() }}."
            }
        }
        if (hunt.requiredIVs.isNotEmpty()) {
            val lowIVStats = mutableListOf<String>()
            hunt.requiredIVs.forEach { ivStatName ->
                val stat = Stats.values().find { it.name.equals(ivStatName, ignoreCase = true) || it.displayName.string.equals(ivStatName, ignoreCase = true) }
                if (stat != null && (pokemon.ivs[stat] ?: 0) < 20) {
                    lowIVStats.add(stat.displayName.string)
                }
            }
            if (lowIVStats.isNotEmpty()) {
                reasons += "Low IVs (must be >= 20) in: ${lowIVStats.joinToString(", ")}"
            }
        }
        return reasons
    }

    fun autoTurnInOnCapture(player: ServerPlayerEntity, pokemon: Pokemon) {
        if (!HuntsConfig.config.autoTurnInOnCapture) return

        val party = CobbleHunts.getPlayerParty(player)
        val slot = party.indexOf(pokemon).takeIf { it >= 0 } ?: return

        if (HuntsConfig.config.soloHuntsEnabled) {
            for (difficulty in listOf("easy", "normal", "medium", "hard")) {
                val tierEnabled = when (difficulty) {
                    "easy" -> HuntsConfig.config.soloEasyEnabled
                    "normal" -> HuntsConfig.config.soloNormalEnabled
                    "medium" -> HuntsConfig.config.soloMediumEnabled
                    "hard" -> HuntsConfig.config.soloHardEnabled
                    else -> false
                }
                if (!tierEnabled) continue

                val hunt = CobbleHunts.getPlayerData(player).activePokemon[difficulty] ?: continue
                if (!SpeciesMatcher.matches(pokemon, hunt.entry.species)) continue
                if (getAttributeMismatchReasons(pokemon, hunt).isNotEmpty()) continue


                val selected = MutableList<Pokemon?>(6) { null }
                selected[slot] = pokemon
                handleConfirmTurnIn(player, selected, difficulty, null, openGui = false)
                return
            }
        }

        if (HuntsConfig.config.globalHuntsEnabled) {
            CobbleHunts.globalHuntStates.withIndex().forEach { (index, hunt) ->
                if (!SpeciesMatcher.matches(pokemon, hunt.entry.species)) return@forEach
                if (getAttributeMismatchReasons(pokemon, hunt).isNotEmpty()) return@forEach

                val selected = MutableList<Pokemon?>(6) { null }
                selected[slot] = pokemon
                handleConfirmTurnIn(player, selected, "global", index, openGui = false)
                return
            }
        }
    }

    private fun createPokemonItem(pokemon: Pokemon): ItemStack {
        val item = PokemonItem.from(pokemon)
        item.setCustomName(Text.literal(pokemon.species.name).styled { it.withColor(Formatting.WHITE) })
        return item
    }

    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
    }
}