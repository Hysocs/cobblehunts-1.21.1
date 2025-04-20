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
import kotlin.random.Random

object TurnInGui {
    // GUI slot constants
    private val PARTY_SLOTS = listOf(1, 10, 19, 28, 37, 46)
    private val TURN_IN_BUTTON_SLOTS = listOf(2, 11, 20, 29, 38, 47)
    private val TURN_IN_SLOTS = listOf(7, 16, 25, 34, 43, 52)
    private val CANCEL_BUTTON_SLOTS = listOf(6, 15, 24, 33, 42, 51)
    private const val CONFIRM_BUTTON_SLOT = 22
    private const val BACK_BUTTON_SLOT = 49
    private const val GUI_SIZE = 54

    // Texture constants
    private object Textures {
        const val TURN_IN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTYzMzlmZjJlNTM0MmJhMThiZGM0OGE5OWNjYTY1ZDEyM2NlNzgxZDg3ODI3MmY5ZDk2NGVhZDNiOGFkMzcwIn19fQ=="
        const val CANCEL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmViNTg4YjIxYTZmOThhZDFmZjRlMDg1YzU1MmRjYjA1MGVmYzljYWI0MjdmNDYwNDhmMThmYzgwMzQ3NWY3In19fQ=="
        const val CONFIRM = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTc5YTVjOTVlZTE3YWJmZWY0NWM4ZGMyMjQxODk5NjQ5NDRkNTYwZjE5YTQ0ZjE5ZjhhNDZhZWYzZmVlNDc1NiJ9fX0="
        const val NOT_TARGET = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODlhOTk1OTI4MDkwZDg0MmQ0YWZkYjIyOTZmZmUyNGYyZTk0NDI3MjIwNWNlYmE4NDhlZTQwNDZlMDFmMzE2OCJ9fX0="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    fun openTurnInGui(player: ServerPlayerEntity, rarity: String, huntIndex: Int? = null) {
        val selectedForTurnIn = MutableList(6) { null as Pokemon? }
        val title = "Turn In $rarity Hunt${huntIndex?.let { " #$it" } ?: ""}"
        CustomGui.openGui(
            player,
            title,
            generateTurnInLayout(player, selectedForTurnIn, rarity, huntIndex),
            { context -> handleTurnInInteraction(context, player, selectedForTurnIn, rarity, huntIndex) },
            { /* Cleanup if needed */ }
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

        // show party
        PARTY_SLOTS.forEachIndexed { index, slot ->
            layout[slot] = createPartySlotItem(party.getOrNull(index), selectedForTurnIn[index])
        }

        // turn‑in buttons now receive 'player' so they can disable used mons
        TURN_IN_BUTTON_SLOTS.forEachIndexed { index, slot ->
            layout[slot] = createTurnInButton(
                player,
                party.getOrNull(index),
                selectedForTurnIn[index],
                activeHunt
            )
        }

        // selected Pokémon preview
        TURN_IN_SLOTS.forEachIndexed { index, slot ->
            layout[slot] = selectedForTurnIn[index]?.let { createPokemonItem(it) }
                ?: ItemStack(Items.RED_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
        }

        // cancel buttons
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

        // confirm button (only when exactly one is selected)
        if (selectedForTurnIn.count { it != null } == 1) {
            layout[CONFIRM_BUTTON_SLOT] = createConfirmButton()
        }

        // back button
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
        // 1) already used → Not Eligible
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

        // 2) fallback to your existing logic:
        if (pokemon == null || selected != null) {
            return ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
                .apply { setCustomName(Text.literal("")) }
        }

        if (!pokemon.species.name.equals(activeHunt.entry.species, ignoreCase = true)) {
            return CustomGui.createPlayerHeadButton(
                textureName = "NotTarget",
                title = Text.literal("Incorrect Species").styled { it.withColor(Formatting.RED) },
                lore = listOf(
                    Text.literal("This is not the required Pokémon species")
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
            CustomGui.createPlayerHeadButton(
                textureName = "NotTarget",
                title = Text.literal("Missing Requirements").styled { it.withColor(Formatting.RED) },
                lore = mismatches.map {
                    Text.literal(it).styled { it.withColor(Formatting.GRAY) }
                },
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

        if (selectedForTurnIn[index] != null || !pokemon.species.name.equals(activeHunt.entry.species, ignoreCase = true)) {
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

        // 1) pull the one you clicked on
        val selectedPokemon = selectedForTurnIn.firstOrNull { it != null } ?: return

        // 2) never allow double‑turn‑in of the same mon
        if (data.usedPokemon.contains(selectedPokemon.uuid)) {
            // optionally: player.sendMessage(Text.literal("That Pokémon has already been turned in!"), false)
            return
        }

        // 3) normal security check
        val party = Cobblemon.storage.getParty(player)
        if (!party.contains(selectedPokemon)) return

        // 4) time check
        val activeHunt = getActiveHunt(player, rarity, huntIndex) ?: return
        if (activeHunt.endTime != null && System.currentTimeMillis() >= activeHunt.endTime!!) {
            player.closeHandledScreen()
            return
        }

        // 5) "only after hunt started" check
        if (HuntsConfig.config.onlyAllowTurnInIfCapturedAfterHuntStarted) {
            val captureTime = CatchingTracker.getCaptureTime(selectedPokemon.uuid)
            val huntStart = activeHunt.startTime ?: 0L
            if (captureTime == null || captureTime < huntStart) return
        }

        // 6) record usage first, so even if removal is disabled, it can’t be re‑used
        data.usedPokemon.add(selectedPokemon.uuid)

        // 7) if configured, physically remove
        if (HuntsConfig.config.takeMonOnTurnIn) {
            CobbleHunts.removedPokemonCache
                .getOrPut(player.uuid) { mutableListOf() }
                .add(selectedPokemon)
            party.remove(selectedPokemon)
        }

        // 8) hand out rewards & points, mark hunt complete
        processRewardsAndLeaderboard(player, rarity, selectedPokemon, activeHunt.rewards)
        markHuntComplete(player, rarity, huntIndex)

        // 9) global broadcast + optional GUI reopen
        if (rarity == "global") {
            val ops = RegistryOps.of(JsonOps.INSTANCE, player.server.registryManager)
            val rewardStrings = activeHunt.rewards.map { getRewardString(it, ops) }
            val rewardMessage = rewardStrings.joinToString(", ")
            val speciesName = activeHunt.entry.species
                .lowercase()
                .replaceFirstChar { it.titlecase() }
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
        rewards: List<LootReward> // Changed from `reward: LootReward?`
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
        val e = hunt.entry

        if (!pokemon.species.name.equals(e.species, ignoreCase = true)) {
            reasons += "Incorrect species"
        }
        if (e.form != null && !pokemon.form.name.equals(e.form, ignoreCase = true)) {
            reasons += "Incorrect form"
        }
        if (!pokemon.aspects.containsAll(e.aspects)) {
            reasons += "Missing required aspects"
        }
        hunt.requiredGender?.let { req ->
            if (!pokemon.gender.name.equals(req, ignoreCase = true)) {
                reasons += "Incorrect gender"
            }
        }
        hunt.requiredNature?.let { req ->
            if (!pokemon.nature.name.path.equals(req, ignoreCase = true)) {
                reasons += "Incorrect nature"
            }
        }
        if (hunt.requiredIVs.isNotEmpty()) {
            val low = hunt.requiredIVs.filter { iv ->
                val stat = when (iv) {
                    "hp" -> Stats.HP
                    "attack" -> Stats.ATTACK
                    "defence" -> Stats.DEFENCE
                    "special_attack" -> Stats.SPECIAL_ATTACK
                    "special_defence" -> Stats.SPECIAL_DEFENCE
                    "speed" -> Stats.SPEED
                    else -> null
                }
                stat != null && (pokemon.ivs[stat] ?: 0) < 20
            }
            if (low.isNotEmpty()) {
                reasons += "Low IVs in: ${low.joinToString(", ")}"
            }
        }

        return reasons
    }


    /**
     * When auto‑turn‑in is enabled, on capture we find any matching solo or global hunt,
     * select that Pokémon in the same slot it occupied in the party GUI, and then call
     * handleConfirmTurnIn(...) to reuse all of your existing “confirm” logic.
     */
    /**
     * When auto‑turn‑in is enabled and on capture, this will:
     *  • only run solo hunts if soloHuntsEnabled == true
     *  • only run global hunts if globalHuntsEnabled == true
     *  • find any matching hunt, select that Pokémon slot, and invoke handleConfirmTurnIn(...)
     */
    fun autoTurnInOnCapture(player: ServerPlayerEntity, pokemon: Pokemon) {
        // bail early if the feature’s off entirely
        if (!HuntsConfig.config.autoTurnInOnCapture) return

        val party = CobbleHunts.getPlayerParty(player)
        val slot = party.indexOf(pokemon).takeIf { it >= 0 } ?: return

        // 1) SOLO HUNTS
        if (HuntsConfig.config.soloHuntsEnabled) {
            for (difficulty in listOf("easy", "normal", "medium", "hard")) {
                // also skip if that tier specifically disabled
                val tierEnabled = when (difficulty) {
                    "easy" -> HuntsConfig.config.soloEasyEnabled
                    "normal" -> HuntsConfig.config.soloNormalEnabled
                    "medium" -> HuntsConfig.config.soloMediumEnabled
                    "hard" -> HuntsConfig.config.soloHardEnabled
                    else -> false
                }
                if (!tierEnabled) continue

                val hunt = CobbleHunts.getPlayerData(player).activePokemon[difficulty] ?: continue
                if (!hunt.entry.species.equals(pokemon.species.name, ignoreCase = true)) continue

                val selected = MutableList<Pokemon?>(6) { null }
                selected[slot] = pokemon
                handleConfirmTurnIn(player, selected, difficulty, null, openGui = false)
                return
            }
        }

        // 2) GLOBAL HUNTS
        if (HuntsConfig.config.globalHuntsEnabled) {
            CobbleHunts.globalHuntStates.withIndex().forEach { (index, hunt) ->
                if (!hunt.entry.species.equals(pokemon.species.name, ignoreCase = true)) return@forEach

                val selected = MutableList<Pokemon?>(6) { null }
                selected[slot] = pokemon
                handleConfirmTurnIn(player, selected, "global", index, openGui = false)
                return
            }
        }
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