package com.cobblehunts.gui

import com.cobblehunts.CobbleHunts
import com.cobblehunts.HuntInstance
import com.cobblehunts.gui.huntsgui.HuntsGlobalGui
import com.cobblehunts.gui.huntsgui.HuntsSoloGui
import com.cobblehunts.utils.*
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonItems
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
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting

object TurnInGui {
    private val PARTY_SLOTS = listOf(1, 10, 19, 28, 37, 46)
    private val TURN_IN_BUTTON_SLOTS = listOf(2, 11, 20, 29, 38, 47)

    private const val PC_BUTTON_SLOT = 22
    private const val SELECTED_PREVIEW_SLOT = 24
    private const val CONFIRM_BUTTON_SLOT = 25
    private const val CANCEL_BUTTON_SLOT = 33
    private const val BACK_BUTTON_SLOT = 49
    private const val GUI_SIZE = 54

    private object Textures {
        const val TURN_IN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTYzMzlmZjJlNTM0MmJhMThiZGM0OGE5OWNjYTY1ZDEyM2NlNzgxZDg3ODI3MmY5ZDk2NGVhZDNiOGFkMzcwIn19fQ=="
        const val CANCEL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmViNTg4YjIxYTZmOThhZDFmZjRlMDg1YzU1MmRjYjA1MGVmYzljYWI0MjdmNDYwNDhmMThmYzgwMzQ3NWY3In19fQ=="
        const val CONFIRM = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTc5YTVjOTVlZTE3YWJmZWY0NWM4ZGMyMjQxODk5NjQ5NDRkNTYwZjE5YTQ0ZjE5ZjhhNDZhZWYzZmVlNDc1NiJ9fX0="
        const val NOT_TARGET = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODlhOTk1OTI4MDkwZDg0MmQ0YWZkYjIyOTZmZmUyNGYyZTk0NDI3MjIwNWNlYmE4NDhlZTQwNDZlMDFmMzE2OCJ9fX0="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    fun openTurnInGui(player: ServerPlayerEntity, rarity: String, huntIndex: Int? = null, currentSelection: HuntSelection? = null) {
        val title = "Turn In ${rarity.replaceFirstChar { it.titlecase() }} Hunt${huntIndex?.let { " #$it" } ?: ""}"
        CustomGui.openGui(
            player,
            title,
            generateTurnInLayout(player, currentSelection, rarity, huntIndex),
            { context -> handleTurnInInteraction(context, player, currentSelection, rarity, huntIndex) },
            { }
        )
    }

    private fun generateTurnInLayout(
        player: ServerPlayerEntity,
        currentSelection: HuntSelection?,
        rarity: String,
        huntIndex: Int?
    ): List<ItemStack> {
        val layout = MutableList(GUI_SIZE) { createFillerPane() }
        val party = CobbleHunts.getPlayerParty(player) // This correctly returns a List<Pokemon>
        val activeHunt = getActiveHunt(player, rarity, huntIndex) ?: return layout

        PARTY_SLOTS.forEachIndexed { index, slot ->
            // Since 'party' is a List, we can safely use getOrNull or a bounds check.
            // A manual check is safest if you're having environment issues.
            val pokemon = if (index < party.size) party[index] else null
            val isThisSelected = pokemon != null && currentSelection is PartySelection && currentSelection.uuid == pokemon.uuid
            layout[slot] = createPartySlotItem(pokemon, isThisSelected)
        }

        TURN_IN_BUTTON_SLOTS.forEachIndexed { index, slot ->
            val pokemonInSlot = if (index < party.size) party[index] else null
            val isThisSelected = pokemonInSlot != null && currentSelection is PartySelection && currentSelection.uuid == pokemonInSlot.uuid
            layout[slot] = createTurnInButton(player, pokemonInSlot, isThisSelected, activeHunt)
        }

        layout[PC_BUTTON_SLOT] = ItemStack(CobblemonItems.PC).apply {
            setCustomName(Text.literal("Open PC").styled { it.withColor(Formatting.AQUA).withBold(true) })
            CustomGui.setItemLore(this, listOf(
                Text.literal("Click to select a Pokémon").styled { it.withColor(Formatting.GRAY) },
                Text.literal("from your PC boxes.").styled { it.withColor(Formatting.GRAY) }
            ))
        }

        val selectedPokemon = resolveSelection(player, currentSelection)
        if (selectedPokemon != null) {
            layout[SELECTED_PREVIEW_SLOT] = createPokemonItem(selectedPokemon, showStats = true)
            layout[CONFIRM_BUTTON_SLOT] = createConfirmButton(currentSelection is PcSelection)

            layout[CANCEL_BUTTON_SLOT] = CustomGui.createPlayerHeadButton(
                textureName = "Cancel",
                title = Text.literal("Cancel Selection").styled { it.withColor(Formatting.RED) },
                lore = listOf(Text.literal("Click to deselect").styled { it.withColor(Formatting.GRAY) }),
                textureValue = Textures.CANCEL
            )
        } else {
            layout[SELECTED_PREVIEW_SLOT] = ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("No Selection")) }
            layout[CONFIRM_BUTTON_SLOT] = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
            layout[CANCEL_BUTTON_SLOT] = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
        }

        layout[BACK_BUTTON_SLOT] = CustomGui.createPlayerHeadButton(
            textureName = "Back",
            title = Text.literal("Back").styled { it.withColor(Formatting.YELLOW) },
            lore = listOf(Text.literal("Return to previous menu").styled { it.withColor(Formatting.GRAY) }),
            textureValue = Textures.BACK
        )

        return layout
    }

    private fun resolveSelection(player: ServerPlayerEntity, selection: HuntSelection?): Pokemon? {
        if (selection == null) {
            return null
        }

        // The 'when' statement is now an expression that calculates the value of 'foundPokemon'.
        // There are no 'return' statements inside the branches.
        val foundPokemon: Pokemon? = when (selection) {
            is PartySelection -> {
                val party = Cobblemon.storage.getParty(player)
                val pokemonAtIndex = party.get(selection.partyIndex)

                // Use an if/else expression to determine the value for this branch.
                if (pokemonAtIndex?.uuid == selection.uuid) {
                    pokemonAtIndex // This is the resulting value if the check succeeds.
                } else {
                    // Otherwise, the result is whatever party.find returns.
                    party.find { it.uuid == selection.uuid }
                }
            }
            is PcSelection -> {
                val pc = Cobblemon.storage.getPC(player)
                val box = if (selection.boxIndex < pc.boxes.size) pc.boxes[selection.boxIndex] else null

                if (box == null) {
                    null // The result is null if the box doesn't exist.
                } else {
                    val pokemonInSlot = box.get(selection.pcSlot)
                    // Use another if/else expression to find the pokemon.
                    if (pokemonInSlot?.uuid == selection.uuid) {
                        pokemonInSlot // Result is the pokemon from the original slot.
                    } else {
                        box.find { it?.uuid == selection.uuid } // Fallback result.
                    }
                }
            }
        }

        // A single, clear return statement at the end of the function.
        return foundPokemon
    }

    private fun handleTurnInInteraction(
        context: InteractionContext,
        player: ServerPlayerEntity,
        currentSelection: HuntSelection?,
        rarity: String,
        huntIndex: Int?
    ) {
        when (context.slotIndex) {
            in TURN_IN_BUTTON_SLOTS -> {
                val partyIndex = TURN_IN_BUTTON_SLOTS.indexOf(context.slotIndex)
                val party = CobbleHunts.getPlayerParty(player)
                // Use getOrNull for safety as the party list can have nulls
                val pokemon = party.getOrNull(partyIndex) ?: return

                val activeHunt = getActiveHunt(player, rarity, huntIndex) ?: return

                // --- ALL VALIDATION CHECKS MUST HAPPEN HERE ---

                // 1. Check if already used
                if (CobbleHunts.getPlayerData(player).usedPokemon.contains(pokemon.uuid)) {
                    return
                }

                // 2. Check Species
                if (!SpeciesMatcher.matches(pokemon, activeHunt.entry.species)) {
                    return
                }

                // 3. Check Attributes (Gender, Nature, IVs, etc.)
                if (getAttributeMismatchReasons(pokemon, activeHunt).isNotEmpty()) {
                    return
                }

                // 4. ADDED: Check Capture Time (This was the missing check)
                if (HuntsConfig.settings.onlyAllowTurnInIfCapturedAfterHuntStarted) {
                    val captureTime = CatchingTracker.getCaptureTime(pokemon.uuid)
                    val huntStart = activeHunt.startTime ?: 0L
                    if (captureTime == null || captureTime < huntStart) {
                        // If the Pokémon was captured too early, do not select it. Just stop.
                        return
                    }
                }

                // If all checks pass, THEN we can select it.
                refreshGui(player, PartySelection(partyIndex, pokemon.uuid), rarity, huntIndex)
            }
            PC_BUTTON_SLOT -> {
                HuntPCGui.open(player, rarity, huntIndex)
            }
            CANCEL_BUTTON_SLOT -> {
                if (currentSelection != null) { // Only refresh if something is selected
                    refreshGui(player, null, rarity, huntIndex)
                }
            }
            CONFIRM_BUTTON_SLOT -> {
                if (currentSelection != null) {
                    handleConfirmTurnIn(player, currentSelection, rarity, huntIndex)
                }
            }
            BACK_BUTTON_SLOT -> navigateBack(player, rarity)
        }
    }

    fun getActiveHunt(player: ServerPlayerEntity, rarity: String, huntIndex: Int?): HuntInstance? {
        return if (rarity == "global") {
            huntIndex?.let { CobbleHunts.globalHuntStates.getOrNull(it) }
        } else {
            CobbleHunts.getPlayerData(player).activePokemon[rarity]
        }
    }



    private fun createPartySlotItem(pokemon: Pokemon?, isSelected: Boolean): ItemStack {
        return when {
            pokemon == null -> ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
            isSelected -> ItemStack(Items.RED_STAINED_GLASS_PANE).apply {
                setCustomName(Text.literal("Selected").styled { it.withColor(Formatting.RED) })
            }
            else -> createPokemonItem(pokemon, showStats = true)
        }
    }

    private fun createPokemonItem(pokemon: Pokemon, showStats: Boolean = false): ItemStack {
        val item = PokemonItem.from(pokemon)
        item.setCustomName(Text.literal(pokemon.species.name).styled { it.withColor(Formatting.WHITE) })
        if (showStats) {
            CustomGui.setItemLore(item, getStatsLore(pokemon))
        }
        return item
    }

    fun getStatsLore(pokemon: Pokemon): List<MutableText> {
        val lore = mutableListOf<MutableText>()

        // Level
        lore.add(Text.literal("Level: ${pokemon.level}").styled { it.withColor(Formatting.AQUA) })

        // Aspects (includes gender)
        if (pokemon.aspects.isNotEmpty()) {
            val aspectStr = pokemon.aspects.joinToString(", ") { it.replaceFirstChar { c -> c.titlecase() } }
            lore.add(Text.literal("Aspects: $aspectStr").styled { it.withColor(Formatting.GRAY) })
        }

        // Nature
        val natureText = if (pokemon.mintedNature != null)
            "Nature: ${pokemon.nature.name.path.replaceFirstChar { it.titlecase() }} (Mint: ${pokemon.mintedNature!!.name.path.replaceFirstChar { it.titlecase() }})"
        else
            "Nature: ${pokemon.nature.name.path.replaceFirstChar { it.titlecase() }}"
        lore.add(Text.literal(natureText).styled { it.withColor(Formatting.GREEN) })

        // Ability
        lore.add(Text.literal("Ability: ${pokemon.ability.name.replaceFirstChar { it.titlecase() }}").styled { it.withColor(Formatting.YELLOW) })

        // IVs
        val ivsText = Text.literal("IVs: ").styled { it.withColor(Formatting.GRAY).withItalic(false) }
        val stats = listOf(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED)
        val shortNames = listOf("HP", "Atk", "Def", "SpA", "SpD", "Spe")
        val colors = listOf(Formatting.RED, Formatting.GOLD, Formatting.YELLOW, Formatting.AQUA, Formatting.GREEN, Formatting.LIGHT_PURPLE)

        stats.forEachIndexed { index, stat ->
            if (index > 0) ivsText.append(Text.literal(" "))
            ivsText.append(Text.literal("${shortNames[index]}: ").styled { it.withColor(colors[index]) })
            ivsText.append(Text.literal("${pokemon.ivs[stat]}").styled { it.withColor(Formatting.WHITE) })
        }
        lore.add(ivsText)

        // EVs
        val evsText = Text.literal("EVs: ").styled { it.withColor(Formatting.GRAY).withItalic(false) }
        stats.forEachIndexed { index, stat ->
            if (index > 0) evsText.append(Text.literal(" "))
            evsText.append(Text.literal("${shortNames[index]}: ").styled { it.withColor(colors[index]) })
            evsText.append(Text.literal("${pokemon.evs[stat]}").styled { it.withColor(Formatting.WHITE) })
        }
        lore.add(evsText)

        return lore
    }

    private fun createTurnInButton(
        player: ServerPlayerEntity,
        pokemon: Pokemon?,
        isAlreadySelected: Boolean,
        activeHunt: HuntInstance
    ): ItemStack {
        if (pokemon != null) {
            if (CobbleHunts.getPlayerData(player).usedPokemon.contains(pokemon.uuid)) {
                return CustomGui.createPlayerHeadButton(
                    "NotTarget", Text.literal("Already Used").styled { it.withColor(Formatting.RED) }, emptyList(), Textures.NOT_TARGET
                )
            }
        }
        if (pokemon == null) {
            return ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
        }
        if (!SpeciesMatcher.matches(pokemon, activeHunt.entry.species)) {
            return CustomGui.createPlayerHeadButton(
                "NotTarget", Text.literal("Incorrect Species").styled { it.withColor(Formatting.RED) }, emptyList(), Textures.NOT_TARGET
            )
        }
        if (HuntsConfig.settings.onlyAllowTurnInIfCapturedAfterHuntStarted) {
            val captureTime = CatchingTracker.getCaptureTime(pokemon.uuid)
            val huntStart = activeHunt.startTime ?: 0L
            if (captureTime == null || captureTime < huntStart) {
                return CustomGui.createPlayerHeadButton(
                    "NotTarget", Text.literal("Captured before hunt").styled { it.withColor(Formatting.RED) }, emptyList(), Textures.NOT_TARGET
                )
            }
        }

        val mismatches = getAttributeMismatchReasons(pokemon, activeHunt)
        return if (mismatches.isEmpty()) {
            CustomGui.createPlayerHeadButton(
                "TurnIn", Text.literal(if(isAlreadySelected) "Selected" else "Select").styled { it.withColor(Formatting.YELLOW) },
                listOf(Text.literal("Click to select").styled { it.withColor(Formatting.GRAY) }),
                Textures.TURN_IN
            )
        } else {
            CustomGui.createPlayerHeadButton(
                "NotTarget", Text.literal("Missing Req").styled { it.withColor(Formatting.RED) },
                mismatches.map { Text.literal(it).styled { s -> s.withColor(Formatting.GRAY) } },
                Textures.NOT_TARGET
            )
        }
    }

    private fun createConfirmButton(isPcSelection: Boolean): ItemStack {
        val lore = mutableListOf<Text>()
        if (HuntsConfig.settings.takeMonOnTurnIn) {
            lore.add(Text.literal("Confirm: Pokémon will be taken.").styled { it.withColor(Formatting.GRAY) })
        } else {
            lore.add(Text.literal("Confirm: Pokémon will be checked & returned.").styled { it.withColor(Formatting.GRAY) })
        }
        if (isPcSelection) {
            lore.add(Text.literal("Source: PC").styled { it.withColor(Formatting.AQUA) })
        } else {
            lore.add(Text.literal("Source: Party").styled { it.withColor(Formatting.GREEN) })
        }

        return CustomGui.createPlayerHeadButton(
            "Accept", Text.literal("Confirm Turn In").styled { it.withColor(Formatting.GREEN) }, lore, Textures.CONFIRM
        )
    }

    fun handleConfirmTurnIn(
        player: ServerPlayerEntity,
        selection: HuntSelection,
        rarity: String,
        huntIndex: Int?,
        openGui: Boolean = true
    ) {
        val data = CobbleHunts.getPlayerData(player)

        if (rarity == "global" && huntIndex != null) {
            val isCompleted = if (HuntsConfig.settings.lockGlobalHuntsOnCompletionForAllPlayers)
                CobbleHunts.globalCompletedHuntIndices.contains(huntIndex)
            else
                data.completedGlobalHunts.contains(huntIndex)

            if (isCompleted) {
                player.sendMessage(Text.literal("You have already completed this hunt!").styled { it.withColor(Formatting.RED) })
                player.closeHandledScreen()
                return
            }
        } else {
            // SOLO: Check if the player is on cooldown (meaning they just finished it)
            if (CobbleHunts.isOnCooldown(player, rarity)) {
                player.sendMessage(Text.literal("You are on cooldown for this hunt!").styled { it.withColor(Formatting.RED) })
                player.closeHandledScreen()
                return
            }
        }

        val pokemon = resolveSelection(player, selection) ?: return

        // Prevent using the same pokemon for multiple hunts if tracked
        if (data.usedPokemon.contains(pokemon.uuid)) return

        val activeHunt = getActiveHunt(player, rarity, huntIndex) ?: return

        // Time limit check
        if (activeHunt.endTime != null && System.currentTimeMillis() >= activeHunt.endTime!!) {
            player.sendMessage(Text.literal("This hunt has expired!").styled { it.withColor(Formatting.RED) })
            player.closeHandledScreen()
            return
        }

        // Config check: Must be captured after start?
        if (HuntsConfig.settings.onlyAllowTurnInIfCapturedAfterHuntStarted) {
            val captureTime = CatchingTracker.getCaptureTime(pokemon.uuid)
            val huntStart = activeHunt.startTime ?: 0L
            if (captureTime == null || captureTime < huntStart) return
        }

        // Mark pokemon as used for this specific hunt system
        data.usedPokemon.add(pokemon.uuid)

        // Handle taking the pokemon if config enabled
        if (HuntsConfig.settings.takeMonOnTurnIn) {
            CobbleHunts.removedPokemonCache.getOrPut(player.uuid) { mutableListOf() }.add(pokemon)
            when (selection) {
                is PartySelection -> Cobblemon.storage.getParty(player).remove(pokemon)
                is PcSelection -> Cobblemon.storage.getPC(player).remove(pokemon)
            }
        }

        processRewardsAndLeaderboard(player, rarity, pokemon, activeHunt.rewards)
        markHuntComplete(player, rarity, huntIndex)

        if (rarity == "global") {
            val ops = RegistryOps.of(JsonOps.INSTANCE, player.server.registryManager)
            val rewardStrings = activeHunt.rewards.map { getRewardString(it, ops) }
            val rewardMessage = rewardStrings.joinToString(", ")
            val speciesName = SpeciesMatcher.getPrettyName(activeHunt.entry.species)
            val msg = HuntsConfig.settings.globalHuntCompletionMessage
                .replace("%player%", player.name.string)
                .replace("%pokemon%", speciesName)
                .replace("%reward%", rewardMessage)
            CobbleHunts.broadcast(player.server, msg)

            player.closeHandledScreen()
            if (openGui) HuntsGlobalGui.openGlobalHuntsGui(player)
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

    private fun processRewardsAndLeaderboard(player: ServerPlayerEntity, rarity: String, pokemon: Pokemon, rewards: List<LootReward>) {
        val points = when (rarity) {
            "easy" -> HuntsConfig.settings.soloEasyPoints
            "normal" -> HuntsConfig.settings.soloNormalPoints
            "medium" -> HuntsConfig.settings.soloMediumPoints
            "hard" -> HuntsConfig.settings.soloHardPoints
            "global" -> HuntsConfig.settings.globalPoints
            else -> 0
        }
        if (points > 0) LeaderboardManager.addPoints(player.name.string, points)
        if (rewards.isNotEmpty()) {
            val ops = RegistryOps.of(JsonOps.INSTANCE, player.server.registryManager)
            val rewardStrings = rewards.map { getRewardString(it, ops) }
            val rewardMessage = rewardStrings.joinToString(", ")
            rewards.forEach { reward ->
                when (reward) {
                    is ItemReward -> player.inventory.offerOrDrop(reward.serializableItemStack.toItemStack(ops))
                    is CommandReward -> {
                        val command = reward.command.replace("%player%", player.name.string)
                        try { player.server.commandManager.executeWithPrefix(player.server.commandSource, command) } catch (e: Exception) {}
                    }
                }
            }
            if (rarity != "global") {
                val message = HuntsConfig.settings.soloHuntCompletionMessage.replace("%reward%", rewardMessage)
                CobbleHunts.broadcast(player.server, message, player)
            }
        }
    }
    private fun markHuntComplete(player: ServerPlayerEntity, rarity: String, huntIndex: Int?) {
        val data = CobbleHunts.getPlayerData(player)
        if (rarity == "global" && huntIndex != null) {
            if (HuntsConfig.settings.lockGlobalHuntsOnCompletionForAllPlayers) CobbleHunts.globalCompletedHuntIndices.add(huntIndex)
            else data.completedGlobalHunts.add(huntIndex)
        } else {
            data.activePokemon.remove(rarity)
            val cooldownTime = when (rarity) {
                "easy" -> HuntsConfig.settings.soloEasyCooldown
                "normal" -> HuntsConfig.settings.soloNormalCooldown
                "medium" -> HuntsConfig.settings.soloMediumCooldown
                "hard" -> HuntsConfig.settings.soloHardCooldown
                else -> 0
            }
            if (cooldownTime > 0) data.cooldowns[rarity] = System.currentTimeMillis() + (cooldownTime * 1000L)
        }
    }

    private fun navigateBack(player: ServerPlayerEntity, rarity: String) {
        player.server.execute {
            if (rarity == "global") HuntsGlobalGui.openGlobalHuntsGui(player)
            else HuntsSoloGui.openSoloHuntsGui(player)
        }
    }
    private fun refreshGui(player: ServerPlayerEntity, selection: HuntSelection?, rarity: String, huntIndex: Int?) {

        player.server.execute {
            openTurnInGui(player, rarity, huntIndex, selection)
        }
    }

    fun getAttributeMismatchReasons(pokemon: Pokemon, hunt: HuntInstance): List<String> {
        val reasons = mutableListOf<String>()
        val e = hunt.entry
        if (!SpeciesMatcher.matches(pokemon, e.species)) reasons += "Incorrect species"
        val expectedFormIdentifier = e.form
        val actualFormName = pokemon.form.name
        val speciesStandardFormName = pokemon.species.standardForm.name
        if (expectedFormIdentifier == null) {
            if (!actualFormName.equals(speciesStandardFormName, ignoreCase = true)) reasons += "FORM_MISMATCH::Expected:${speciesStandardFormName.replaceFirstChar { it.titlecase() }}::Actual:${actualFormName.replaceFirstChar { it.titlecase() }}"
        } else {
            if (!actualFormName.equals(expectedFormIdentifier, ignoreCase = true)) reasons += "FORM_MISMATCH::Expected:${expectedFormIdentifier.replaceFirstChar { it.titlecase() }}::Actual:${actualFormName.replaceFirstChar { it.titlecase() }}"
        }
        if (!pokemon.aspects.containsAll(e.aspects)) reasons += "Missing required aspects: ${e.aspects.minus(pokemon.aspects).joinToString()}"
        hunt.requiredGender?.let { req -> if (!pokemon.gender.name.equals(req, ignoreCase = true)) reasons += "Incorrect gender. Expected: ${req.replaceFirstChar { it.titlecase() }}. Actual: ${pokemon.gender.name.replaceFirstChar { it.titlecase() }}." }
        hunt.requiredNature?.let { req -> if (!pokemon.nature.name.path.equals(req, ignoreCase = true)) reasons += "Incorrect nature. Expected: ${req.replaceFirstChar { it.titlecase() }}. Actual: ${pokemon.nature.name.path.substringAfterLast('/').replaceFirstChar { it.titlecase() }}." }
        if (hunt.requiredIVs.isNotEmpty()) {
            val lowIVStats = mutableListOf<String>()
            hunt.requiredIVs.forEach { ivStatName ->
                val stat = Stats.values().find { it.name.equals(ivStatName, ignoreCase = true) || it.displayName.string.equals(ivStatName, ignoreCase = true) }
                if (stat != null && (pokemon.ivs[stat] ?: 0) < 20) lowIVStats.add(stat.displayName.string)
            }
            if (lowIVStats.isNotEmpty()) reasons += "Low IVs (must be >= 20) in: ${lowIVStats.joinToString(", ")}"
        }
        return reasons
    }
    private fun createFillerPane(): ItemStack = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
}