package com.cobblehunts.gui.huntsgui

import com.cobblehunts.CobbleHunts
import com.cobblehunts.HuntInstance
import com.cobblehunts.gui.TurnInGui
import com.cobblehunts.utils.HuntsConfig
import com.cobblehunts.utils.LeaderboardManager
import com.cobblehunts.utils.LeaderboardManager.fetchMojangProfile
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.item.PokemonItem
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import com.everlastingutils.utils.logDebug
import com.google.gson.JsonElement
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.JsonOps
import kotlinx.serialization.json.*
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.ProfileComponent
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.RegistryOps
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.ClickType
import net.minecraft.util.Formatting
import net.minecraft.util.Uuids
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CompletableFuture
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

    // Maps for loot pool pagination
    private val playerLootDifficulties = mutableMapOf<ServerPlayerEntity, String>()
    private val playerLootPages = mutableMapOf<ServerPlayerEntity, Int>()

    // Textures for loot pool navigation
    private object LootPoolTextures {
        const val PREV_PAGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTMzYWQ1YzIyZGIxNjQzNWRhYWQ2MTU5MGFiYTUxZDkzNzkxNDJkZDU1NmQ2YzQyMmE3MTEwY2EzYWJlYTUwIn19fQ=="
        const val NEXT_PAGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU0MDNjYzdiYmFjNzM2NzBiZDU0M2Y2YjA5NTViYWU3YjhlOTEyM2Q4M2JkNzYwZjYyMDRjNWFmZDhiZTdlMSJ9fX0="
    }

    // Predefined slot mappings for different numbers of global hunts
    private val huntSlotMap = mapOf(
        1 to listOf(13),
        2 to listOf(12, 14),
        3 to listOf(12, 13, 14),
        4 to listOf(10, 12, 14, 16),
        5 to listOf(11, 12, 13, 14, 15),
        6 to listOf(10, 11, 13, 14, 15, 16),
        7 to listOf(10, 11, 12, 13, 14, 15, 16)
    )

    /** Checks for expired solo hunts and sets cooldowns accordingly. */
    private fun checkExpiredHunts(player: ServerPlayerEntity) {
        val data = CobbleHunts.getPlayerData(player)
        val currentTime = System.currentTimeMillis()
        val difficulties = listOf("easy", "normal", "medium", "hard")
        difficulties.forEach { difficulty ->
            val activeInstance = data.activePokemon[difficulty]
            if (activeInstance != null && activeInstance.endTime != null && currentTime >= activeInstance.endTime!!) {
                data.activePokemon.remove(difficulty)
                val cooldownDuration = when (difficulty) {
                    "easy" -> HuntsConfig.config.soloEasyCooldown
                    "normal" -> HuntsConfig.config.soloNormalCooldown
                    "medium" -> HuntsConfig.config.soloMediumCooldown
                    "hard" -> HuntsConfig.config.soloHardCooldown
                    else -> 0
                }
                data.cooldowns[difficulty] = currentTime + cooldownDuration * 1000L
            }
        }
    }

    /** Refreshes only the Pokémon items in dynamic GUIs every second. */
    fun refreshDynamicGuis() {
        dynamicGuiData.forEach { (player, pair) ->
            val (guiType, layout) = pair
            when (guiType) {
                "global" -> {
                    updateGlobalDynamicItems(player, layout)
                }
                "solo" -> {
                    checkExpiredHunts(player)
                    CobbleHunts.refreshPreviewPokemon(player)
                    updateSoloDynamicItems(player, layout)
                }
            }
            CustomGui.refreshGui(player, layout)
        }
    }

    private fun updateGlobalDynamicItems(player: ServerPlayerEntity, layout: MutableList<ItemStack>) {
        val data = CobbleHunts.getPlayerData(player)
        val hunts = CobbleHunts.globalHuntStates
        if (hunts.isNotEmpty()) {
            val n = hunts.size
            val pokemonSlots = huntSlotMap[n] ?: listOf()
            val indicatorSlots = pokemonSlots.map { it - 9 }
            for (i in 0 until n) {
                val hunt = hunts[i]
                val isCompleted = if (HuntsConfig.config.lockGlobalHuntsOnCompletionForAllPlayers) {
                    CobbleHunts.globalCompletedHuntIndices.contains(i)
                } else {
                    data.completedGlobalHunts.contains(i)
                }
                layout[indicatorSlots[i]] = if (isCompleted) {
                    ItemStack(Items.RED_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
                } else {
                    ItemStack(Items.GREEN_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
                }
                layout[pokemonSlots[i]] = if (isCompleted) {
                    ItemStack(Items.CLOCK).apply {
                        setCustomName(
                            Text.literal("Completed")
                                .setStyle(Style.EMPTY.withItalic(false))
                                .styled { it.withColor(Formatting.GRAY) }
                        )
                    }
                } else {
                    createActivePokemonItem(hunt, "global")
                }
            }
        } else {
            // Global hunts are on cooldown: fill each expected Pokemon slot with a clock item
            val currentTime = System.currentTimeMillis()
            val remainingCooldown = (CobbleHunts.globalCooldownEnd - currentTime) / 1000
            val timeString = if (remainingCooldown > 0) formatTime(remainingCooldown.toInt()) else "Starting soon..."
            val n = HuntsConfig.config.activeGlobalHuntsAtOnce
            val pokemonSlots = huntSlotMap[n] ?: listOf(13)
            for (slot in pokemonSlots) {
                layout[slot] = ItemStack(Items.CLOCK).apply {
                    setCustomName(
                        Text.literal("Next Global Hunts in $timeString")
                            .setStyle(Style.EMPTY.withItalic(false))
                            .styled { it.withColor(Formatting.YELLOW) }
                    )
                }
            }
        }
    }



    /** Updates only the dynamic items (indicators and Pokémon) in the solo hunts GUI. */
    private fun updateSoloDynamicItems(player: ServerPlayerEntity, layout: MutableList<ItemStack>) {
        val data = CobbleHunts.getPlayerData(player)
        val difficulties = listOf("easy", "normal", "medium", "hard")
        val indicatorSlots = listOf(
            SoloSlots.INDICATOR_EASY,
            SoloSlots.INDICATOR_NORMAL,
            SoloSlots.INDICATOR_MEDIUM,
            SoloSlots.INDICATOR_HARD
        )
        val pokemonSlots = listOf(
            SoloSlots.POKEMON_EASY,
            SoloSlots.POKEMON_NORMAL,
            SoloSlots.POKEMON_MEDIUM,
            SoloSlots.POKEMON_HARD
        )

        difficulties.forEachIndexed { index, difficulty ->
            val activeInstance = data.activePokemon[difficulty]
            val isActive = activeInstance != null && (activeInstance.endTime == null || System.currentTimeMillis() < activeInstance.endTime!!)
            layout[indicatorSlots[index]] = if (isActive) {
                ItemStack(Items.GREEN_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
            } else {
                ItemStack(Items.BLACK_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
            }
            layout[pokemonSlots[index]] = getSoloDynamicItem(player, difficulty)
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
            title = Text.literal("Global Hunts").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GREEN) },
            lore = listOf(Text.literal("Click to view global hunts").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) }),
            textureValue = MainTextures.GLOBAL
        )
        layout[MainSlots.SOLO] = CustomGui.createPlayerHeadButton(
            textureName = "SoloHunts",
            title = Text.literal("Solo Hunts").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.AQUA) },
            lore = listOf(Text.literal("Click to view solo hunts").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) }),
            textureValue = MainTextures.SOLO
        )
        layout[MainSlots.LEADERBOARD] = CustomGui.createPlayerHeadButton(
            textureName = "Leaderboard",
            title = Text.literal("Leaderboard").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GOLD) },
            lore = listOf(Text.literal("View top players").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) }),
            textureValue = MainTextures.LEADERBOARD
        )
        return layout
    }

    private fun handleMainInteraction(context: InteractionContext, player: ServerPlayerEntity) {
        when (context.slotIndex) {
            MainSlots.GLOBAL -> {
                if (!CobbleHunts.hasHuntPermission(player, "global")) {
                    player.sendMessage(
                        Text.literal("You do not have permission for Global Hunts!")
                            .setStyle(Style.EMPTY.withItalic(false))
                            .styled { it.withColor(Formatting.RED) },
                        false
                    )
                    return
                }
                openGlobalHuntsGui(player)
            }
            MainSlots.SOLO -> openSoloHuntsGui(player)
            MainSlots.LEADERBOARD -> openLeaderboardGui(player)
        }
    }


    fun openLeaderboardGui(player: ServerPlayerEntity) {
        val layout = MutableList(54) { createFillerPane() }
        CustomGui.openGui(
            player,
            "Leaderboard",
            layout,
            6,
            { context ->
                if (context.slotIndex == 49) {
                    player.closeHandledScreen()
                    openMainGui(player)
                }
            },
            { }
        )
        generateLeaderboardLayout(player, layout)
    }


    private fun generateLeaderboardLayout(player: ServerPlayerEntity, layout: MutableList<ItemStack>) {
        val topPlayers = LeaderboardManager.getTopPlayers(16)
        val leaderboardSlots = listOf(13, 21, 22, 23, 29, 30, 31, 32, 33, 37, 38, 39, 40, 41, 42, 43)

        for (i in 0 until minOf(topPlayers.size, 16)) {
            val (playerName, points) = topPlayers[i]
            val slot = leaderboardSlots[i]

            logDebug("DEBUG: Processing leaderboard entry for '$playerName' at slot $slot with $points points", "cobblehunts")

            // Create a default head with an offline profile (fallback)
            val defaultHead = ItemStack(Items.PLAYER_HEAD)
            val offlineUUID = java.util.UUID.nameUUIDFromBytes("OfflinePlayer:$playerName".toByteArray())
            logDebug("DEBUG: Computed offlineUUID for '$playerName': $offlineUUID", "cobblehunts")
            val defaultOfflineProfile = GameProfile(offlineUUID, playerName)
            defaultHead.set(DataComponentTypes.PROFILE, ProfileComponent(defaultOfflineProfile))

            val rankText = Text.literal("Rank ")
                .setStyle(Style.EMPTY.withItalic(false))
                .styled { it.withColor(Formatting.GRAY) }
            val rankNumber = Text.literal("${i + 1}")
                .setStyle(Style.EMPTY.withItalic(false))
                .styled { it.withColor(Formatting.GOLD) }
            val colon = Text.literal(": ")
                .setStyle(Style.EMPTY.withItalic(false))
                .styled { it.withColor(Formatting.GRAY) }
            val playerNameText = Text.literal(playerName)
                .setStyle(Style.EMPTY.withItalic(false))
                .styled { it.withColor(Formatting.AQUA) }
            val fullTitle = rankText.append(rankNumber).append(colon).append(playerNameText)
            defaultHead.setCustomName(fullTitle)

            val pointsText = Text.literal("Points: ")
                .setStyle(Style.EMPTY.withItalic(false))
                .styled { it.withColor(Formatting.GRAY) }
            val pointsValue = Text.literal("$points")
                .setStyle(Style.EMPTY.withItalic(false))
                .styled { it.withColor(Formatting.GREEN) }
            val loreLine = pointsText.append(pointsValue)
            CustomGui.setItemLore(defaultHead, listOf(loreLine))

            layout[slot] = defaultHead

            // Update the head asynchronously
            CompletableFuture.supplyAsync {
                logDebug("DEBUG: Checking if player '$playerName' is online", "cobblehunts")
                player.server.playerManager.getPlayer(playerName)
            }.thenCompose { onlinePlayer ->
                if (onlinePlayer != null) {
                    logDebug("DEBUG: Player '$playerName' is online; using their gameProfile", "cobblehunts")
                    CompletableFuture.completedFuture(onlinePlayer.gameProfile)
                } else {
                    logDebug("DEBUG: Player '$playerName' is offline; checking userCache", "cobblehunts")
                    val cachedProfile = player.server.userCache?.findByName(playerName)?.orElse(null)
                    if (cachedProfile != null && cachedProfile.properties.containsKey("textures") &&
                        cachedProfile.properties["textures"]!!.isNotEmpty()
                    ) {
                        logDebug("DEBUG: Found valid cached profile for '$playerName' with textures", "cobblehunts")
                        CompletableFuture.completedFuture(cachedProfile)
                    } else {
                        logDebug("DEBUG: No valid cached profile for '$playerName', fetching from Mojang API", "cobblehunts")
                        // fetchMojangProfile now returns a CompletableFuture<GameProfile?>
                        fetchMojangProfile(playerName).thenApply { mojangProfile ->
                            if (mojangProfile != null && mojangProfile.properties.containsKey("textures") &&
                                mojangProfile.properties["textures"]!!.isNotEmpty()
                            ) {
                                logDebug("DEBUG: Fetched valid Mojang profile for '$playerName'", "cobblehunts")
                                mojangProfile
                            } else {
                                logDebug("DEBUG: Failed to fetch Mojang profile for '$playerName', using offline profile", "cobblehunts")
                                GameProfile(java.util.UUID.nameUUIDFromBytes("OfflinePlayer:$playerName".toByteArray()), playerName)
                            }
                        }
                    }
                }
            }.thenAccept { finalProfile ->
                player.server.execute {
                    // Update the head using finalProfile...
                    val head = ItemStack(Items.PLAYER_HEAD)
                    head.set(DataComponentTypes.PROFILE, ProfileComponent(finalProfile))
                    head.setCustomName(fullTitle)
                    CustomGui.setItemLore(head, listOf(loreLine))
                    layout[slot] = head
                    CustomGui.refreshGui(player, layout)
                }
            }
        }

        val infoHead = CustomGui.createPlayerHeadButton(
            textureName = "Info",
            title = Text.literal("Points Information")
                .setStyle(Style.EMPTY.withItalic(false))
                .styled { it.withColor(Formatting.WHITE) },
            lore = listOf(
                Text.literal("Complete hunts to earn points!")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.GRAY) },
                Text.literal("Global Hunt: ")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("${HuntsConfig.config.globalPoints} points")
                        .setStyle(Style.EMPTY.withItalic(false))
                        .styled { it.withColor(Formatting.GREEN) }),
                Text.literal("Easy Hunt: ")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("${HuntsConfig.config.soloEasyPoints} points")
                        .setStyle(Style.EMPTY.withItalic(false))
                        .styled { it.withColor(Formatting.GREEN) }),
                Text.literal("Normal Hunt: ")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("${HuntsConfig.config.soloNormalPoints} points")
                        .setStyle(Style.EMPTY.withItalic(false))
                        .styled { it.withColor(Formatting.GREEN) }),
                Text.literal("Medium Hunt: ")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("${HuntsConfig.config.soloMediumPoints} points")
                        .setStyle(Style.EMPTY.withItalic(false))
                        .styled { it.withColor(Formatting.GREEN) }),
                Text.literal("Hard Hunt: ")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("${HuntsConfig.config.soloHardPoints} points")
                        .setStyle(Style.EMPTY.withItalic(false))
                        .styled { it.withColor(Formatting.GREEN) })
            ),
            textureValue = MainTextures.INFO
        )
        layout[4] = infoHead

        layout[49] = CustomGui.createPlayerHeadButton(
            textureName = "Back",
            title = Text.literal("Back")
                .setStyle(Style.EMPTY.withItalic(false))
                .styled { it.withColor(Formatting.YELLOW) },
            lore = listOf(
                Text.literal("Return to main menu")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.GRAY) }
            ),
            textureValue = MainTextures.BACK
        )

        CustomGui.refreshGui(player, layout)
    }





    private object GlobalSlots {
        const val BACK = 22
    }

    private object GlobalTextures {
        const val BACK = MainTextures.BACK
    }

    fun openGlobalHuntsGui(player: ServerPlayerEntity) {
        val layout = generateGlobalLayout(player).toMutableList()
        // Immediately update dynamic items to display current cooldown info
        updateGlobalDynamicItems(player, layout)
        dynamicGuiData[player] = Pair("global", layout)
        CustomGui.openGui(
            player,
            "Global Hunts",
            layout,
            3,
            { context -> handleGlobalInteraction(context, player) },
            { _ -> dynamicGuiData.remove(player) }
        )
    }


    private fun generateGlobalLayout(player: ServerPlayerEntity): List<ItemStack> {
        val layout = MutableList(27) { createFillerPane() }
        val data = CobbleHunts.getPlayerData(player)
        val hunts = CobbleHunts.globalHuntStates
        val currentTime = System.currentTimeMillis()

        if (hunts.isNotEmpty()) {
            val n = hunts.size
            val pokemonSlots = huntSlotMap[n] ?: listOf()
            val indicatorSlots = pokemonSlots.map { it - 9 }
            for (i in 0 until n) {
                val hunt = hunts[i]
                val isCompleted = data.completedGlobalHunts.contains(i)
                layout[indicatorSlots[i]] = if (isCompleted) {
                    ItemStack(Items.RED_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
                } else {
                    ItemStack(Items.GREEN_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
                }
                layout[pokemonSlots[i]] = if (isCompleted) {
                    ItemStack(Items.CLOCK).apply {
                        setCustomName(Text.literal("Completed").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) })
                    }
                } else {
                    createActivePokemonItem(hunt, "global")
                }
            }
        } else {
            val remainingCooldown = (CobbleHunts.globalCooldownEnd - currentTime) / 1000
            layout[13] = if (remainingCooldown > 0) {
                val timeString = formatTime(remainingCooldown.toInt())
                ItemStack(Items.CLOCK).apply {
                    setCustomName(Text.literal("Next Global Hunts in $timeString").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) })
                }
            } else {
                ItemStack(Items.CLOCK).apply {
                    setCustomName(Text.literal("Starting soon...").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) })
                }
            }
        }

        layout[GlobalSlots.BACK] = CustomGui.createPlayerHeadButton(
            textureName = "Back",
            title = Text.literal("Back").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) },
            lore = listOf(Text.literal("Return to main menu").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }),
            textureValue = GlobalTextures.BACK
        )
        return layout
    }

    private fun handleGlobalInteraction(context: InteractionContext, player: ServerPlayerEntity) {
        val slot = context.slotIndex
        val data = CobbleHunts.getPlayerData(player)
        val hunts = CobbleHunts.globalHuntStates
        if (hunts.isNotEmpty()) {
            val n = hunts.size
            val pokemonSlots = huntSlotMap[n] ?: listOf()
            if (slot in pokemonSlots) {
                val index = pokemonSlots.indexOf(slot)
                // Check if global lock is enabled and hunt already completed globally.
                if (index < hunts.size && !(HuntsConfig.config.lockGlobalHuntsOnCompletionForAllPlayers &&
                            CobbleHunts.globalCompletedHuntIndices.contains(index))
                ) {
                    if (context.clickType == ClickType.RIGHT) {
                        openLootPoolViewGui(player, "global")
                    } else if (context.clickType == ClickType.LEFT) {
                        TurnInGui.openTurnInGui(player, "global", index)
                    }
                }
            }
        }
        if (slot == GlobalSlots.BACK) {
            openMainGui(player)
        }
    }


    private object SoloSlots {
        const val INDICATOR_EASY = 1
        const val INDICATOR_NORMAL = 3
        const val INDICATOR_MEDIUM = 5
        const val INDICATOR_HARD = 7
        const val POKEMON_EASY = 10
        const val POKEMON_NORMAL = 12
        const val POKEMON_MEDIUM = 14
        const val POKEMON_HARD = 16
        const val BACK = 22
    }

    private object SoloTextures {
        const val TURN_IN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmZhZGMzMGI0YTdjOTBlOWExOWY5ZDJmZjU0OTQyZDYzYmI5NGJmOTc3NzljNGY2NzI2NDU0MzEzYzgyYzRmOCJ9fX0="
        const val BACK = MainTextures.BACK
    }

    fun openSoloHuntsGui(player: ServerPlayerEntity) {
        checkExpiredHunts(player)
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
        val difficulties = listOf("easy", "normal", "medium", "hard")
        val indicatorSlots = listOf(
            SoloSlots.INDICATOR_EASY,
            SoloSlots.INDICATOR_NORMAL,
            SoloSlots.INDICATOR_MEDIUM,
            SoloSlots.INDICATOR_HARD
        )
        val pokemonSlots = listOf(
            SoloSlots.POKEMON_EASY,
            SoloSlots.POKEMON_NORMAL,
            SoloSlots.POKEMON_MEDIUM,
            SoloSlots.POKEMON_HARD
        )
        val data = CobbleHunts.getPlayerData(player)

        difficulties.forEachIndexed { index, difficulty ->
            val activeInstance = data.activePokemon[difficulty]
            val isActive = activeInstance != null && (activeInstance.endTime == null || System.currentTimeMillis() < activeInstance.endTime!!)
            layout[indicatorSlots[index]] = if (isActive) {
                ItemStack(Items.GREEN_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
            } else {
                ItemStack(Items.BLACK_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
            }
            layout[pokemonSlots[index]] = getSoloDynamicItem(player, difficulty)
        }

        layout[SoloSlots.BACK] = CustomGui.createPlayerHeadButton(
            textureName = "Back",
            title = Text.literal("Back").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) },
            lore = listOf(Text.literal("Return to main menu").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }),
            textureValue = SoloTextures.BACK
        )
        return layout
    }

    private fun handleSoloInteraction(context: InteractionContext, player: ServerPlayerEntity) {
        val difficulties = listOf("easy", "normal", "medium", "hard")
        val pokemonSlots = listOf(
            SoloSlots.POKEMON_EASY,
            SoloSlots.POKEMON_NORMAL,
            SoloSlots.POKEMON_MEDIUM,
            SoloSlots.POKEMON_HARD
        )

        if (context.slotIndex in pokemonSlots) {
            val index = pokemonSlots.indexOf(context.slotIndex)
            val difficulty = difficulties[index]
            if (!CobbleHunts.hasHuntPermission(player, difficulty)) {
                player.sendMessage(
                    Text.literal("You do not have permission for $difficulty hunts!")
                        .setStyle(Style.EMPTY.withItalic(false))
                        .styled { it.withColor(Formatting.RED) },
                    false
                )
                return
            }
            val data = CobbleHunts.getPlayerData(player)
            val activeInstance = data.activePokemon[difficulty]
            if (context.clickType == ClickType.RIGHT) {
                openLootPoolViewGui(player, difficulty)
                return
            }
            if (activeInstance == null) {
                if (CobbleHunts.isOnCooldown(player, difficulty)) {
                    player.sendMessage(Text.literal("You are on cooldown for $difficulty missions!").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.RED) }, false)
                } else {
                    val instance = CobbleHunts.getPreviewPokemon(player, difficulty)
                    if (instance != null) {
                        CobbleHunts.activateMission(player, difficulty, instance)
                        player.sendMessage(Text.literal("Activated $difficulty mission for ${instance.entry.species}!").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GREEN) }, false)
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

    private fun createActivePokemonItem(instance: HuntInstance, difficulty: String): ItemStack {
        val entry = instance.entry
        val properties = PokemonProperties.parse(
            "${entry.species}${if (entry.form != null) " form=${entry.form}" else ""}${if (entry.aspects.contains("shiny")) " aspect=shiny" else ""}"
        )
        val pokemon = properties.create()
        val item = PokemonItem.from(pokemon)
        val displayName = "${entry.species.replaceFirstChar { it.titlecase()}}${if (entry.form != null) " (${entry.form.replaceFirstChar { it.titlecase() }})" else ""}${if (entry.aspects.contains("shiny")) ", Shiny" else ""}"
        item.setCustomName(Text.literal(displayName).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.WHITE) })

        val lore = mutableListOf<Text>()
        val difficultyText = if (difficulty == "global") "Global Hunt" else "${difficulty.replaceFirstChar { it.uppercase() }} Mission"
        lore.add(
            Text.literal(difficultyText).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(getRarityColor(difficulty)) }
                .append(Text.literal(" (Active)").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GREEN) })
        )
        lore.add(Text.literal("Hunt Requirements:").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.AQUA) })
        addHuntRequirements(lore, instance)

        val remainingTime = instance.endTime?.let { (it - System.currentTimeMillis()) / 1000 } ?: 0
        val timeLeftString = formatTime(remainingTime.toInt())
        lore.add(
            Text.literal("Time Left: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal(timeLeftString).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.AQUA) })
        )

        lore.add(Text.literal("Left-click to turn in").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GREEN) })
        lore.add(Text.literal("Right-click to view loot pool").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) })
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
        item.setCustomName(Text.literal(displayName).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.WHITE) })

        val lore = mutableListOf<Text>()
        val difficultyDisplay = if (difficulty == "global") "Global Hunt" else "${difficulty.replaceFirstChar { it.uppercase() }} Mission"
        lore.add(Text.literal(difficultyDisplay).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(
            getRarityColor(difficulty)
        ) })
        lore.add(Text.literal("Hunt Requirements:").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.AQUA) })
        addHuntRequirements(lore, instance)

        val timeLimit = when (difficulty) {
            "easy" -> HuntsConfig.config.soloEasyTimeLimit
            "normal" -> HuntsConfig.config.soloNormalTimeLimit
            "medium" -> HuntsConfig.config.soloMediumTimeLimit
            "hard" -> HuntsConfig.config.soloHardTimeLimit
            "global" -> HuntsConfig.config.globalTimeLimit
            else -> 0
        }
        val cooldown = when (difficulty) {
            "easy" -> HuntsConfig.config.soloEasyCooldown
            "normal" -> HuntsConfig.config.soloNormalCooldown
            "medium" -> HuntsConfig.config.soloMediumCooldown
            "hard" -> HuntsConfig.config.soloHardCooldown
            "global" -> HuntsConfig.config.globalCooldown
            else -> 0
        }
        val timeLimitString = formatTime(timeLimit)
        val cooldownString = formatTime(cooldown)
        lore.add(
            Text.literal("Time Limit: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal(timeLimitString).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.AQUA) })
        )
        lore.add(
            Text.literal("Cooldown: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal(cooldownString).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.AQUA) })
        )

        lore.add(
            if (difficulty == "global")
                Text.literal("Left-click to turn in").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GREEN) }
            else
                Text.literal("Left-click to start this hunt").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) }
        )
        lore.add(Text.literal("Right-click to see loot table").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) })
        CustomGui.setItemLore(item, lore)
        return item
    }

    private fun addHuntRequirements(lore: MutableList<Text>, instance: HuntInstance) {
        val entry = instance.entry
        if (entry.form != null) {
            lore.add(
                Text.literal("- Form: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal(entry.form.replaceFirstChar { it.titlecase() }).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.WHITE) })
            )
        }
        if (entry.aspects.contains("shiny")) {
            lore.add(Text.literal("- Shiny").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GOLD) })
        }
        if (instance.requiredGender != null) {
            val genderText = instance.requiredGender.replaceFirstChar { it.titlecase() }
            lore.add(
                Text.literal("- Gender: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal(genderText).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(
                        getGenderColor(genderText)
                    ) })
            )
        }
        if (instance.requiredNature != null) {
            val natureText = instance.requiredNature.replaceFirstChar { it.titlecase() }
            lore.add(
                Text.literal("- Nature: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal(natureText).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GREEN) })
            )
        }
        if (instance.requiredIVs.isNotEmpty()) {
            val ivsText = instance.requiredIVs.joinToString(", ") { it.replaceFirstChar { it.uppercase() } }
            lore.add(
                Text.literal("- IVs above 20: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal(ivsText).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GOLD) })
            )
        }
    }

    private fun getGenderColor(gender: String): Formatting {
        return when (gender.lowercase()) {
            "male" -> Formatting.BLUE
            "female" -> Formatting.LIGHT_PURPLE
            "genderless" -> Formatting.WHITE
            else -> Formatting.WHITE
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
                        setCustomName(Text.literal("No Preview Available").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) })
                    }
                }
            }
            else -> {
                val cooldownEnd = data.cooldowns[difficulty] ?: 0
                val remainingTime = (cooldownEnd - System.currentTimeMillis()) / 1000
                val timeString = formatTime(remainingTime.toInt())
                ItemStack(Items.CLOCK).apply {
                    setCustomName(Text.literal("Cooldown: $timeString").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) })
                }
            }
        }
    }

    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
    }

    private fun getRarityColor(difficulty: String): Formatting {
        return when (difficulty) {
            "easy" -> Formatting.GREEN
            "normal" -> Formatting.BLUE
            "medium" -> Formatting.GOLD
            "hard" -> Formatting.RED
            "global" -> Formatting.DARK_PURPLE
            else -> Formatting.WHITE
        }
    }

    private fun openLootPoolViewGui(player: ServerPlayerEntity, difficulty: String) {
        playerLootDifficulties[player] = difficulty
        playerLootPages[player] = 0
        val title = "${if (difficulty == "global") "Global" else difficulty.replaceFirstChar { it.uppercase() }} Loot Pool"
        CustomGui.openGui(
            player,
            title,
            generateLootLayout(player, difficulty, 0),
            3,
            { context -> handleLootPoolInteraction(context, player) },
            { _ -> cleanupLootPoolData(player) }
        )
    }

    private fun generateLootLayout(player: ServerPlayerEntity, difficulty: String, page: Int): List<ItemStack> {
        val layout = MutableList(27) { createFillerPane() }
        val lootList = when (difficulty) {
            "global" -> HuntsConfig.config.globalLoot
            "easy" -> HuntsConfig.config.soloEasyLoot
            "normal" -> HuntsConfig.config.soloNormalLoot
            "medium" -> HuntsConfig.config.soloMediumLoot
            "hard" -> HuntsConfig.config.soloHardLoot
            else -> emptyList()
        }
        val registryOps: DynamicOps<JsonElement> = RegistryOps.of(JsonOps.INSTANCE, player.server.registryManager)
        val startIndex = page * 18
        val endIndex = min(startIndex + 18, lootList.size)
        for (i in startIndex until endIndex) {
            val reward = lootList[i]
            layout[i - startIndex] = createLootRewardDisplay(reward, registryOps)
        }
        if (page > 0) {
            layout[18] = CustomGui.createPlayerHeadButton(
                textureName = "Previous",
                title = Text.literal("Previous Page").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) },
                lore = listOf(Text.literal("Go to previous page").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }),
                textureValue = LootPoolTextures.PREV_PAGE
            )
        }
        layout[22] = CustomGui.createPlayerHeadButton(
            textureName = "Back",
            title = Text.literal("Back").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) },
            lore = listOf(Text.literal("Return to ${if (difficulty == "global") "Global" else "Solo"} Hunts").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }),
            textureValue = SoloTextures.BACK
        )
        if (endIndex < lootList.size) {
            layout[26] = CustomGui.createPlayerHeadButton(
                textureName = "Next",
                title = Text.literal("Next Page").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GREEN) },
                lore = listOf(Text.literal("Go to next page").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }),
                textureValue = LootPoolTextures.NEXT_PAGE
            )
        }
        return layout
    }

    private fun handleLootPoolInteraction(context: InteractionContext, player: ServerPlayerEntity) {
        val difficulty = playerLootDifficulties[player] ?: return
        var page = playerLootPages[player] ?: 0
        when (context.slotIndex) {
            18 -> if (page > 0) {
                page--
                playerLootPages[player] = page
                CustomGui.refreshGui(player, generateLootLayout(player, difficulty, page))
            }
            22 -> {
                if (difficulty == "global") {
                    openGlobalHuntsGui(player)
                } else {
                    openSoloHuntsGui(player)
                }
            }
            26 -> {
                val lootList = when (difficulty) {
                    "global" -> HuntsConfig.config.globalLoot
                    "easy" -> HuntsConfig.config.soloEasyLoot
                    "normal" -> HuntsConfig.config.soloNormalLoot
                    "medium" -> HuntsConfig.config.soloMediumLoot
                    "hard" -> HuntsConfig.config.soloHardLoot
                    else -> emptyList()
                }
                if ((page + 1) * 18 < lootList.size) {
                    page++
                    playerLootPages[player] = page
                    CustomGui.refreshGui(player, generateLootLayout(player, difficulty, page))
                }
            }
        }
    }

    private fun cleanupLootPoolData(player: ServerPlayerEntity) {
        playerLootDifficulties.remove(player)
        playerLootPages.remove(player)
    }

    private fun createLootRewardDisplay(reward: Any, ops: DynamicOps<JsonElement>): ItemStack {
        return when (reward) {
            is com.cobblehunts.utils.ItemReward -> {
                val item = reward.serializableItemStack.toItemStack(ops)
                val lore = listOf(
                    Text.literal("Chance: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
                        .append(Text.literal("%.1f%%".format(reward.chance * 100)).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.RED) })
                )
                CustomGui.setItemLore(item, lore)
                item
            }
            is com.cobblehunts.utils.CommandReward -> {
                val displayItem = reward.serializableItemStack?.toItemStack(ops)
                    ?: ItemStack(Items.COMMAND_BLOCK).apply {
                        setCustomName(Text.literal("Command Reward").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GOLD) })
                    }
                val lore = listOf(
                    Text.literal("Chance: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
                        .append(Text.literal("%.1f%%".format(reward.chance * 100)).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.RED) })
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