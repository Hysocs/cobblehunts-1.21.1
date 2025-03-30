package com.cobblehunts.gui

import com.cobblehunts.CobbleHunts
import com.cobblehunts.HuntInstance
import com.cobblehunts.utils.HuntPokemonEntry
import com.cobblehunts.utils.HuntsConfig
import com.cobblehunts.utils.LeaderboardManager
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.item.PokemonItem
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import com.google.gson.JsonElement
import com.mojang.authlib.GameProfile
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.JsonOps
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.RegistryOps
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ClickType
import net.minecraft.util.Formatting
import net.minecraft.util.Uuids
import kotlin.math.min

object PlayerHuntsGui {
    private object MainSlots {
        const val GLOBAL = 12
        const val SOLO = 14
        const val LEADERBOARD = 8
    }

    private object MainTextures {
        const val GLOBAL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODc5ZTU0Y2JlODc4NjdkMTRiMmZiZGYzZjE4NzA4OTQzNTIwNDhkZmVjZDk2Mjg0NmRlYTg5M2IyMTU0Yzg1In19fQ=="
        const val SOLO = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2MxYjJmNTkyY2ZjOGQzNzJkY2Y1ZmQ0NGVlZDY5ZGRkYzY0NjAxZDc4NDZkNzI2MTlmNzA1MTFkODA0M2E4OSJ9fX0="
        const val LEADERBOARD = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzcyNWRhODJhYTBhZGU1ZDUyYmQyMDI0ZjRiYzFkMDE5ZmMwMzBlOWVjNWUwZWMxNThjN2Y5YTZhYTBjNDNiYSJ9fX0="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        const val INFO = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTcxYTIyODVjOTFjNmM3Mjc0NzYwNDgxOWVlNTIyM2E5MGFhNTFlNmU3OWU0ZjlhZjY2MjhlYzhmMGRkN2RmYyJ9fX0="
    }

    private val dynamicGuiData = mutableMapOf<ServerPlayerEntity, Pair<String, MutableList<ItemStack>>>()

    fun refreshDynamicGuis() {
        dynamicGuiData.forEach { (player, pair) ->
            val (guiType, staticLayout) = pair
            val newLayout = staticLayout.toMutableList()
            when (guiType) {
                "global" -> {
                    newLayout[4] = if (CobbleHunts.globalHuntState != null) {
                        ItemStack(Items.GREEN_STAINED_GLASS_PANE).apply { setCustomName(Text.literal(" ")) }
                    } else {
                        ItemStack(Items.BLACK_STAINED_GLASS_PANE).apply { setCustomName(Text.literal(" ")) }
                    }
                    newLayout[GlobalSlots.POKEMON] = getGlobalDynamicItem()
                }
                "solo" -> {
                    CobbleHunts.refreshPreviewPokemon(player)
                    val difficulties = listOf("easy", "medium", "hard")
                    val indicatorSlots = listOf(SoloSlots.INDICATOR_EASY, SoloSlots.INDICATOR_MEDIUM, SoloSlots.INDICATOR_HARD)
                    val pokemonSlots = listOf(SoloSlots.POKEMON_EASY, SoloSlots.POKEMON_MEDIUM, SoloSlots.POKEMON_HARD)
                    val data = CobbleHunts.getPlayerData(player)
                    difficulties.forEachIndexed { index, difficulty ->
                        val activeInstance = data.activePokemon[difficulty]
                        val isActive = activeInstance != null && (activeInstance.endTime == null || System.currentTimeMillis() < activeInstance.endTime!!)
                        newLayout[indicatorSlots[index]] = if (isActive) {
                            ItemStack(Items.GREEN_STAINED_GLASS_PANE).apply { setCustomName(Text.literal(" ")) }
                        } else {
                            ItemStack(Items.BLACK_STAINED_GLASS_PANE).apply { setCustomName(Text.literal(" ")) }
                        }
                        newLayout[pokemonSlots[index]] = getSoloDynamicItem(player, difficulty)
                    }
                }
            }
            CustomGui.refreshGui(player, newLayout)
            dynamicGuiData[player] = Pair(guiType, newLayout)
        }
    }

    fun openMainGui(player: ServerPlayerEntity) {
        dynamicGuiData.remove(player)
        CustomGui.openGui(
            player,
            "Hunts Menu",
            generateMainLayout(),
            3,
            { context -> handleMainInteraction(context, player) },
            { }
        )
    }

    private fun generateMainLayout(): List<ItemStack> {
        val layout = MutableList(27) { createFillerPane() }
        layout[MainSlots.GLOBAL] = CustomGui.createPlayerHeadButton(
            textureName = "GlobalHunts",
            title = Text.literal("Global Hunts").styled { it.withColor(Formatting.GREEN) },
            lore = listOf(Text.literal("Click to view global hunts").styled { it.withColor(Formatting.YELLOW) }),
            textureValue = MainTextures.GLOBAL
        )
        layout[MainSlots.SOLO] = CustomGui.createPlayerHeadButton(
            textureName = "SoloHunts",
            title = Text.literal("Solo Hunts").styled { it.withColor(Formatting.AQUA) },
            lore = listOf(Text.literal("Click to view solo hunts").styled { it.withColor(Formatting.YELLOW) }),
            textureValue = MainTextures.SOLO
        )
        layout[MainSlots.LEADERBOARD] = CustomGui.createPlayerHeadButton(
            textureName = "Leaderboard",
            title = Text.literal("Leaderboard").styled { it.withColor(Formatting.GOLD) },
            lore = listOf(Text.literal("View top players").styled { it.withColor(Formatting.YELLOW) }),
            textureValue = MainTextures.LEADERBOARD
        )
        return layout
    }

    private fun handleMainInteraction(context: InteractionContext, player: ServerPlayerEntity) {
        when (context.slotIndex) {
            MainSlots.GLOBAL -> openGlobalHuntsGui(player)
            MainSlots.SOLO -> openSoloHuntsGui(player)
            MainSlots.LEADERBOARD -> openLeaderboardGui(player)
        }
    }

    private fun openLeaderboardGui(player: ServerPlayerEntity) {
        dynamicGuiData.remove(player)
        CustomGui.openGui(
            player,
            "Leaderboard",
            generateLeaderboardLayout(player),
            3,
            { context -> if (context.slotIndex == 22) openMainGui(player) },
            { }
        )
    }

    private fun generateLeaderboardLayout(player: ServerPlayerEntity): List<ItemStack> {
        val layout = MutableList(27) { createFillerPane() }
        val topPlayers = LeaderboardManager.getTopPlayers(10)
        val leaderboardSlots = listOf(11, 12, 13, 14, 15)

        val infoHead = CustomGui.createPlayerHeadButton(
            textureName = "Info",
            title = Text.literal("Points Information").styled { it.withColor(Formatting.WHITE) },
            lore = listOf(
                Text.literal("Complete hunts to earn points!").styled { it.withColor(Formatting.GRAY) },
                Text.literal("Global Hunt: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("${HuntsConfig.config.globalPoints} points").styled { it.withColor(Formatting.GREEN) }),
                Text.literal("Easy Hunt: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("${HuntsConfig.config.soloEasyPoints} points").styled { it.withColor(Formatting.GREEN) }),
                Text.literal("Medium Hunt: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("${HuntsConfig.config.soloMediumPoints} points").styled { it.withColor(Formatting.GREEN) }),
                Text.literal("Hard Hunt: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("${HuntsConfig.config.soloHardPoints} points").styled { it.withColor(Formatting.GREEN) })
            ),
            textureValue = MainTextures.INFO
        )
        layout[4] = infoHead

        for (i in 0 until min(topPlayers.size, 5)) {
            val (playerName, points) = topPlayers[i]
            val head = ItemStack(Items.PLAYER_HEAD)
            val userCache = player.server.userCache
            val gameProfile = userCache?.findByName(playerName)?.orElse(null)
            val profile = gameProfile ?: GameProfile(Uuids.getOfflinePlayerUuid(playerName), playerName)
            val profileComponent = net.minecraft.component.type.ProfileComponent(profile)
            head.set(net.minecraft.component.DataComponentTypes.PROFILE, profileComponent)

            val rankText = Text.literal("Rank ").styled { it.withColor(Formatting.GRAY) }
            val rankNumber = Text.literal("${i + 1}").styled { it.withColor(Formatting.GOLD) }
            val colon = Text.literal(": ").styled { it.withColor(Formatting.GRAY) }
            val playerNameText = Text.literal(playerName).styled { it.withColor(Formatting.AQUA) }
            val fullTitle = rankText.append(rankNumber).append(colon).append(playerNameText)
            head.setCustomName(fullTitle)

            val pointsText = Text.literal("Points: ").styled { it.withColor(Formatting.GRAY) }
            val pointsValue = Text.literal("$points").styled { it.withColor(Formatting.GREEN) }
            val loreLine = pointsText.append(pointsValue)
            CustomGui.setItemLore(head, listOf(loreLine))

            if (i < leaderboardSlots.size) {
                layout[leaderboardSlots[i]] = head
            }
        }

        layout[22] = CustomGui.createPlayerHeadButton(
            textureName = "Back",
            title = Text.literal("Back").styled { it.withColor(Formatting.YELLOW) },
            lore = listOf(Text.literal("Return to main menu").styled { it.withColor(Formatting.GRAY) }),
            textureValue = MainTextures.BACK
        )
        return layout
    }

    private object GlobalSlots {
        const val POKEMON = 13
        const val BACK = 22
    }

    private object GlobalTextures {
        const val BACK = MainTextures.BACK
    }

    fun openGlobalHuntsGui(player: ServerPlayerEntity) {
        val layout = generateGlobalLayout()
        dynamicGuiData[player] = Pair("global", layout.toMutableList())
        CustomGui.openGui(
            player,
            "Global Hunts",
            layout,
            3,
            { context -> handleGlobalInteraction(context, player) },
            { _ -> dynamicGuiData.remove(player) }
        )
    }

    private fun generateGlobalLayout(): List<ItemStack> {
        val layout = MutableList(27) { createFillerPane() }
        // Add indicator slot at position 4
        layout[4] = if (CobbleHunts.globalHuntState != null) {
            ItemStack(Items.GREEN_STAINED_GLASS_PANE).apply { setCustomName(Text.literal(" ")) }
        } else {
            ItemStack(Items.BLACK_STAINED_GLASS_PANE).apply { setCustomName(Text.literal(" ")) }
        }
        // Pokémon slot
        layout[GlobalSlots.POKEMON] = getGlobalDynamicItem()
        layout[GlobalSlots.BACK] = CustomGui.createPlayerHeadButton(
            textureName = "Back",
            title = Text.literal("Back").styled { it.withColor(Formatting.YELLOW) },
            lore = listOf(Text.literal("Return to main menu").styled { it.withColor(Formatting.GRAY) }),
            textureValue = GlobalTextures.BACK
        )
        return layout
    }

    private fun handleGlobalInteraction(context: InteractionContext, player: ServerPlayerEntity) {
        when (context.slotIndex) {
            GlobalSlots.POKEMON -> {
                if (context.clickType == ClickType.RIGHT) {
                    openLootPoolViewGui(player, "global")
                } else if (context.clickType == ClickType.LEFT) {
                    TurnInGui.openTurnInGui(player, "global")
                }
            }
            GlobalSlots.BACK -> openMainGui(player)
        }
    }

    private object SoloSlots {
        const val INDICATOR_EASY = 1
        const val INDICATOR_MEDIUM = 4
        const val INDICATOR_HARD = 7
        const val POKEMON_EASY = 10
        const val POKEMON_MEDIUM = 13
        const val POKEMON_HARD = 16
        const val BACK = 22
    }

    private object SoloTextures {
        const val TURN_IN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmZhZGMzMGI0YTdjOTBlOWExOWY5ZDJmZjU0OTQyZDYzYmI5NGJmOTc3NzljNGY2NzI2NDU0MzEzYzgyYzRmOCJ9fX0="
        const val BACK = MainTextures.BACK
    }

    fun openSoloHuntsGui(player: ServerPlayerEntity) {
        CobbleHunts.refreshPreviewPokemon(player)
        val layout = generateSoloLayout(player)
        dynamicGuiData[player] = Pair("solo", layout.toMutableList())
        CustomGui.openGui(
            player,
            "Solo Hunts",
            layout,
            3,
            { context -> handleSoloInteraction(context, player) },
            { _ -> dynamicGuiData.remove(player) }
        )
    }

    private fun generateSoloLayout(player: ServerPlayerEntity): List<ItemStack> {
        val layout = MutableList(27) { createFillerPane() }
        val difficulties = listOf("easy", "medium", "hard")
        val indicatorSlots = listOf(SoloSlots.INDICATOR_EASY, SoloSlots.INDICATOR_MEDIUM, SoloSlots.INDICATOR_HARD)
        val pokemonSlots = listOf(SoloSlots.POKEMON_EASY, SoloSlots.POKEMON_MEDIUM, SoloSlots.POKEMON_HARD)
        val data = CobbleHunts.getPlayerData(player)

        difficulties.forEachIndexed { index, difficulty ->
            val activeInstance = data.activePokemon[difficulty]
            val isActive = activeInstance != null && (activeInstance.endTime == null || System.currentTimeMillis() < activeInstance.endTime!!)
            layout[indicatorSlots[index]] = if (isActive) {
                ItemStack(Items.GREEN_STAINED_GLASS_PANE).apply { setCustomName(Text.literal(" ")) }
            } else {
                ItemStack(Items.BLACK_STAINED_GLASS_PANE).apply { setCustomName(Text.literal(" ")) }
            }
            layout[pokemonSlots[index]] = getSoloDynamicItem(player, difficulty)
        }

        layout[SoloSlots.BACK] = CustomGui.createPlayerHeadButton(
            textureName = "Back",
            title = Text.literal("Back").styled { it.withColor(Formatting.YELLOW) },
            lore = listOf(Text.literal("Return to main menu").styled { it.withColor(Formatting.GRAY) }),
            textureValue = SoloTextures.BACK
        )
        return layout
    }

    private fun handleSoloInteraction(context: InteractionContext, player: ServerPlayerEntity) {
        val difficulties = listOf("easy", "medium", "hard")
        val pokemonSlots = listOf(SoloSlots.POKEMON_EASY, SoloSlots.POKEMON_MEDIUM, SoloSlots.POKEMON_HARD)

        if (context.slotIndex in pokemonSlots) {
            val index = pokemonSlots.indexOf(context.slotIndex)
            val difficulty = difficulties[index]
            val data = CobbleHunts.getPlayerData(player)
            val activeInstance = data.activePokemon[difficulty]
            if (context.clickType == ClickType.RIGHT) {
                openLootPoolViewGui(player, difficulty)
                return
            }
            if (activeInstance == null) {
                if (CobbleHunts.isOnCooldown(player, difficulty)) {
                    player.sendMessage(Text.literal("You are on cooldown for $difficulty missions!"), false)
                } else {
                    val instance = CobbleHunts.getPreviewPokemon(player, difficulty)
                    if (instance != null) {
                        CobbleHunts.activateMission(player, difficulty, instance)
                        player.sendMessage(Text.literal("Activated $difficulty mission for ${instance.entry.species}!"), false)
                        dynamicGuiData[player]?.let { (guiType, _) ->
                            if (guiType == "solo") {
                                val newLayout = generateSoloLayout(player).toMutableList()
                                dynamicGuiData[player] = Pair("solo", newLayout)
                                CustomGui.refreshGui(player, newLayout)
                            }
                        }
                    }
                }
            } else {
                TurnInGui.openTurnInGui(player, difficulty)
            }
        } else if (context.slotIndex == SoloSlots.BACK) {
            openMainGui(player)
        }
    }

    private fun getGlobalDynamicItem(): ItemStack {
        return if (CobbleHunts.globalHuntState != null) {
            createActivePokemonItem(CobbleHunts.globalHuntState!!.instance, "global")
        } else {
            val remainingCooldown = (CobbleHunts.globalCooldownEnd - System.currentTimeMillis()) / 1000
            if (remainingCooldown > 0) {
                val timeString = formatTime(remainingCooldown.toInt())
                ItemStack(Items.CLOCK).apply {
                    setCustomName(Text.literal("Next Global Hunt in $timeString").styled { it.withColor(Formatting.YELLOW) })
                }
            } else {
                ItemStack(Items.CLOCK).apply {
                    setCustomName(Text.literal("Starting soon...").styled { it.withColor(Formatting.YELLOW) })
                }
            }
        }
    }

    private fun getSoloDynamicItem(player: ServerPlayerEntity, difficulty: String): ItemStack {
        val data = CobbleHunts.getPlayerData(player)
        return when {
            data.activePokemon[difficulty]?.let { instance ->
                instance.endTime == null || System.currentTimeMillis() < instance.endTime!!
            } == true -> createActivePokemonItem(data.activePokemon[difficulty]!!, difficulty)
            !CobbleHunts.isOnCooldown(player, difficulty) -> {
                val previewInstance = data.previewPokemon[difficulty]
                if (previewInstance != null) {
                    createPreviewPokemonItem(previewInstance, difficulty)
                } else {
                    ItemStack(Items.GREEN_STAINED_GLASS_PANE).apply {
                        setCustomName(Text.literal("No Preview Available").styled { it.withColor(Formatting.GRAY) })
                    }
                }
            }
            else -> {
                val cooldownEnd = data.cooldowns[difficulty] ?: 0
                val remainingTime = (cooldownEnd - System.currentTimeMillis()) / 1000
                val timeString = formatTime(remainingTime.toInt())
                ItemStack(Items.CLOCK).apply {
                    setCustomName(Text.literal("Cooldown: $timeString").styled { it.withColor(Formatting.YELLOW) })
                }
            }
        }
    }

    private fun createActivePokemonItem(instance: HuntInstance, difficulty: String): ItemStack {
        val entry = instance.entry
        val properties = PokemonProperties.parse(
            "${entry.species}${if (entry.form != null) " form=${entry.form}" else ""}${if (entry.aspects.contains("shiny")) " aspect=shiny" else ""}"
        )
        val pokemon = properties.create()
        val item = PokemonItem.from(pokemon)
        val displayName = "${entry.species.replaceFirstChar { it.titlecase()}}${if (entry.form != null) " (${entry.form.replaceFirstChar { it.titlecase() }})" else ""}${if (entry.aspects.contains("shiny")) ", Shiny" else ""}"
        item.setCustomName(Text.literal(displayName).styled { it.withColor(Formatting.WHITE) })

        val lore = mutableListOf<Text>()
        lore.add(Text.literal("Required for Turn-In:").styled { it.withColor(Formatting.AQUA) })
        if (entry.form != null) {
            lore.add(Text.literal("- Form: ${entry.form.replaceFirstChar { it.titlecase() }}").styled { it.withColor(Formatting.GRAY) })
        }
        if (entry.aspects.contains("shiny")) {
            lore.add(Text.literal("- Shiny").styled { it.withColor(Formatting.GRAY) })
        }
        if (instance.requiredGender != null) {
            val genderText = instance.requiredGender.replaceFirstChar { it.titlecase() }
            lore.add(Text.literal("- Gender: $genderText").styled { it.withColor(Formatting.LIGHT_PURPLE) })
        }
        if (instance.requiredIVs.isNotEmpty()) {
            val ivsText = instance.requiredIVs.joinToString(", ") { it.replaceFirstChar { it.uppercase() } }
            lore.add(Text.literal("- IVs above 20: $ivsText").styled { it.withColor(Formatting.GRAY) })
        }

        val remainingTime = instance.endTime?.let { (it - System.currentTimeMillis()) / 1000 } ?: 0
        val timeLeftString = formatTime(remainingTime.toInt())
        lore.add(
            Text.literal("Time Left: ").styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal(timeLeftString).styled { it.withColor(Formatting.AQUA) })
        )

        lore.add(Text.literal("Left-click to turn in").styled { it.withColor(Formatting.GREEN) })
        lore.add(Text.literal("Right-click to view loot pool").styled { it.withColor(Formatting.YELLOW) }) // Added this line

        CustomGui.setItemLore(item, lore)
        return item
    }

    private fun createPreviewPokemonItem(entry: HuntPokemonEntry, difficulty: String): ItemStack {
        val properties = PokemonProperties.parse(
            "${entry.species}${if (entry.form != null) " form=${entry.form}" else ""}${if (entry.aspects.contains("shiny")) " aspect=shiny" else ""}"
        )
        val pokemon = properties.create()
        val item = PokemonItem.from(pokemon)
        val displayName = "${entry.species.replaceFirstChar { it.titlecase()}}${if (entry.form != null) " (${entry.form.replaceFirstChar { it.titlecase() }})" else ""}${if (entry.aspects.contains("shiny")) ", Shiny" else ""}"
        item.setCustomName(Text.literal(displayName).styled { it.withColor(Formatting.WHITE) })
        val lore = mutableListOf<Text>()
        lore.add(Text.literal("Capture this Pokémon to complete the hunt.").styled { it.withColor(Formatting.GRAY) })
        lore.add(Text.literal("Right-click to see loot table").styled { it.withColor(Formatting.YELLOW) })
        CustomGui.setItemLore(item, lore)
        return item
    }

    private fun createPreviewPokemonItem(instance: HuntInstance, difficulty: String): ItemStack {
        val entry = instance.entry
        val properties = PokemonProperties.parse(
            "${entry.species}${if (entry.form != null) " form=${entry.form}" else ""}${if (entry.aspects.contains("shiny")) " aspect=shiny" else ""}"
        )
        val pokemon = properties.create()
        val item = PokemonItem.from(pokemon)
        val displayName = "${entry.species.replaceFirstChar { it.titlecase()}}${if (entry.form != null) " (${entry.form.replaceFirstChar { it.titlecase() }})" else ""}${if (entry.aspects.contains("shiny")) ", Shiny" else ""}"
        item.setCustomName(Text.literal(displayName).styled { it.withColor(Formatting.WHITE) })

        val lore = mutableListOf<Text>()
        val difficultyDisplay = if (difficulty == "global") "Global Hunt" else "${difficulty.replaceFirstChar { it.uppercase() }} Mission"
        lore.add(Text.literal(difficultyDisplay).styled { it.withColor(getRarityColor(difficulty)) })
        lore.add(Text.literal("Capture this Pokémon to complete the hunt.").styled { it.withColor(Formatting.GRAY) })

        if (instance.requiredGender != null) {
            val genderText = instance.requiredGender.replaceFirstChar { it.titlecase() }
            lore.add(Text.literal("Required Gender: ").styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal(genderText).styled { it.withColor(Formatting.LIGHT_PURPLE) }))
        }
        if (instance.requiredIVs.isNotEmpty()) {
            val ivsText = instance.requiredIVs.joinToString(", ") { it.replaceFirstChar { it.uppercase() } }
            lore.add(Text.literal("Required IVs above 20: ").styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal(ivsText).styled { it.withColor(Formatting.GOLD) }))
        }

        val timeLimit = when (difficulty) {
            "easy" -> HuntsConfig.config.soloEasyTimeLimit
            "medium" -> HuntsConfig.config.soloMediumTimeLimit
            "hard" -> HuntsConfig.config.soloHardTimeLimit
            "global" -> HuntsConfig.config.globalTimeLimit
            else -> 0
        }
        val cooldown = when (difficulty) {
            "easy" -> HuntsConfig.config.soloEasyCooldown
            "medium" -> HuntsConfig.config.soloMediumCooldown
            "hard" -> HuntsConfig.config.soloHardCooldown
            "global" -> HuntsConfig.config.globalCooldown
            else -> 0
        }
        val timeLimitString = formatTime(timeLimit)
        val cooldownString = formatTime(cooldown)
        lore.add(Text.literal("Time Limit: ").styled { it.withColor(Formatting.GRAY) }
            .append(Text.literal(timeLimitString).styled { it.withColor(Formatting.AQUA) }))
        lore.add(Text.literal("Cooldown: ").styled { it.withColor(Formatting.GRAY) }
            .append(Text.literal(cooldownString).styled { it.withColor(Formatting.AQUA) }))

        lore.add(
            if (difficulty == "global")
                Text.literal("Left-click to turn in").styled { it.withColor(Formatting.GREEN) }
            else
                Text.literal("Left-click to start this hunt").styled { it.withColor(Formatting.YELLOW) }
        )
        lore.add(Text.literal("Right-click to see loot table").styled { it.withColor(Formatting.YELLOW) })
        CustomGui.setItemLore(item, lore)
        return item
    }

    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal(" ")) }
    }

    private fun getRarityColor(difficulty: String): Formatting {
        return when (difficulty) {
            "easy" -> Formatting.GREEN
            "medium" -> Formatting.BLUE
            "hard" -> Formatting.GOLD
            "global" -> Formatting.DARK_PURPLE
            else -> Formatting.WHITE
        }
    }

    private fun openLootPoolViewGui(player: ServerPlayerEntity, difficulty: String) {
        val lootList = when (difficulty) {
            "global" -> HuntsConfig.config.globalLoot
            "easy" -> HuntsConfig.config.soloEasyLoot
            "medium" -> HuntsConfig.config.soloMediumLoot
            "hard" -> HuntsConfig.config.soloHardLoot
            else -> emptyList()
        }
        val registryOps: DynamicOps<JsonElement> = RegistryOps.of(JsonOps.INSTANCE, player.server.registryManager)
        val layout = MutableList(27) { createFillerPane() }
        val ITEMS_PER_PAGE = 18
        for (i in 0 until min(lootList.size, ITEMS_PER_PAGE)) {
            val reward = lootList[i]
            layout[i] = createLootRewardDisplay(reward, registryOps)
        }
        layout[22] = CustomGui.createPlayerHeadButton(
            textureName = "Back",
            title = Text.literal("Back").styled { it.withColor(Formatting.YELLOW) },
            lore = listOf(Text.literal("Return to ${if (difficulty == "global") "Global" else "Solo"} Hunts").styled { it.withColor(Formatting.GRAY) }),
            textureValue = SoloTextures.BACK
        )
        CustomGui.openGui(
            player,
            "${if (difficulty == "global") "Global" else difficulty.replaceFirstChar { it.uppercase() }} Loot Pool",
            layout,
            3,
            { context ->
                if (context.slotIndex == 22) {
                    if (difficulty == "global") {
                        openGlobalHuntsGui(player)
                    } else {
                        openSoloHuntsGui(player)
                    }
                }
            },
            { }
        )
    }

    private fun createLootRewardDisplay(reward: Any, ops: DynamicOps<JsonElement>): ItemStack {
        return when (reward) {
            is com.cobblehunts.utils.ItemReward -> {
                val item = reward.serializableItemStack.toItemStack(ops)
                val lore = listOf(
                    Text.literal("Chance: ").styled { it.withColor(Formatting.GRAY) }
                        .append(Text.literal("%.1f%%".format(reward.chance * 100)).styled { it.withColor(Formatting.RED) })
                )
                CustomGui.setItemLore(item, lore)
                item
            }
            is com.cobblehunts.utils.CommandReward -> {
                val displayItem = reward.serializableItemStack?.toItemStack(ops)
                    ?: ItemStack(Items.COMMAND_BLOCK).apply {
                        setCustomName(Text.literal("Command Reward").styled { it.withColor(Formatting.GOLD) })
                    }
                val lore = listOf(
                    Text.literal("Chance: ").styled { it.withColor(Formatting.GRAY) }
                        .append(Text.literal("%.1f%%".format(reward.chance * 100)).styled { it.withColor(Formatting.RED) })
                )
                CustomGui.setItemLore(displayItem, lore)
                displayItem
            }
            else -> createFillerPane()
        }
    }

    private fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return if (minutes > 0) {
            if (secs > 0) {
                "$minutes minute${if (minutes != 1) "s" else ""} $secs second${if (secs != 1) "s" else ""}"
            } else {
                "$minutes minute${if (minutes != 1) "s" else ""}"
            }
        } else {
            "$secs second${if (secs != 1) "s" else ""}"
        }
    }
}