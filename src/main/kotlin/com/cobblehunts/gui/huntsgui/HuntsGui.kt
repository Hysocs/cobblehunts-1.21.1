package com.cobblehunts.gui.huntsgui

import com.cobblehunts.CobbleHunts
import com.cobblehunts.gui.huntsgui.HuntsGlobalGui
import com.cobblehunts.gui.huntsgui.HuntsGuiUtils
import com.cobblehunts.gui.huntsgui.HuntsLeaderboard
import com.cobblehunts.gui.huntsgui.HuntsSoloGui
import com.cobblehunts.utils.HuntsConfig
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.mojang.serialization.JsonOps
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryOps
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import kotlin.math.min

object HuntsGui {
    private object MainSlots {
        const val GLOBAL = 12
        const val SOLO = 14
        const val LEADERBOARD = 8
    }

    private object MainTextures {
        const val GLOBAL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODc5ZTU0Y2JlODc4NjdkMTRiMmZiZGYzZjE4NzA4OTQzNTIwNDhkZmVjZDk2Mjg0NmRlYTg5M2IyMTU0Yzg1In19fQ=="
        const val SOLO = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2MxYjJmNTkyY2ZjOGQzNzJkY2Y1ZmQ0NGVlZDY5ZGRkYzY0NjAxZDc4NDZkNzI2MTlmNzA1MTFkODA0M2E4OSJ9fX0="
        const val LEADERBOARD = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzcyNWRhODJhYTBhZGU1ZDUyYmQyMDI0ZjRiYzFkMDE5ZmMwMzBlOWVjNWUwZWMxNThjN2Y5YTZhYTBjNDNiYSJ9fX0="
    }

    val dynamicGuiData = mutableMapOf<ServerPlayerEntity, Pair<String, MutableList<ItemStack>>>()
    private var mainHuntMapping: Map<Int, String> = emptyMap()

    // Loot pool state
    private val playerLootDifficulties = mutableMapOf<ServerPlayerEntity, String>()
    private val playerLootPages = mutableMapOf<ServerPlayerEntity, Int>()

    private object LootPoolTextures {
        const val PREV_PAGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTMzYWQ1YzIyZGIxNjQzNWRhYWQ2MTU5MGFiYTUxZDkzNzkxNDJkZDU1NmQ2YzQyMmE3MTEwY2EzYWJlYTUwIn19fQ=="
        const val NEXT_PAGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU0MDNjYzdiYmFjNzM2NzBiZDU0M2Y2YjA5NTViYWU3YjhlOTEyM2Q4M2JkNzYwZjYyMDRjNWFmZDhiZTdlMSJ9fX0="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
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
        val layout = MutableList(27) { HuntsGuiUtils.createFillerPane() }
        val globalEnabled = HuntsConfig.config.globalHuntsEnabled
        val soloEnabled = HuntsConfig.config.soloHuntsEnabled
        val mapping = mutableMapOf<Int, String>()

        if (globalEnabled && soloEnabled) {
            layout[MainSlots.GLOBAL] = CustomGui.createPlayerHeadButton(
                textureName = "GlobalHunts",
                title = Text.literal("Global Hunts")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.GREEN) },
                lore = listOf(Text.literal("Click to view global hunts")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.YELLOW) }),
                textureValue = MainTextures.GLOBAL
            )
            mapping[MainSlots.GLOBAL] = "global"

            layout[MainSlots.SOLO] = CustomGui.createPlayerHeadButton(
                textureName = "SoloHunts",
                title = Text.literal("Solo Hunts")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.AQUA) },
                lore = listOf(Text.literal("Click to view solo hunts")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.YELLOW) }),
                textureValue = MainTextures.SOLO
            )
            mapping[MainSlots.SOLO] = "solo"
        } else if (globalEnabled) {
            layout[13] = CustomGui.createPlayerHeadButton(
                textureName = "GlobalHunts",
                title = Text.literal("Global Hunts")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.GREEN) },
                lore = listOf(Text.literal("Click to view global hunts")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.YELLOW) }),
                textureValue = MainTextures.GLOBAL
            )
            mapping[13] = "global"
        } else if (soloEnabled) {
            layout[13] = CustomGui.createPlayerHeadButton(
                textureName = "SoloHunts",
                title = Text.literal("Solo Hunts")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.AQUA) },
                lore = listOf(Text.literal("Click to view solo hunts")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.YELLOW) }),
                textureValue = MainTextures.SOLO
            )
            mapping[13] = "solo"
        }

        if (HuntsConfig.config.enableLeaderboard) {
            layout[MainSlots.LEADERBOARD] = CustomGui.createPlayerHeadButton(
                textureName = "Leaderboard",
                title = Text.literal("Leaderboard")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.GOLD) },
                lore = listOf(Text.literal("View top players")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.YELLOW) }),
                textureValue = MainTextures.LEADERBOARD
            )
        } else {
            layout[MainSlots.LEADERBOARD] = HuntsGuiUtils.createFillerPane()
        }
        mainHuntMapping = mapping
        return layout
    }

    private fun handleMainInteraction(context: InteractionContext, player: ServerPlayerEntity) {
        val slot = context.slotIndex
        if (mainHuntMapping.containsKey(slot)) {
            when (mainHuntMapping[slot]) {
                "global" -> {
                    if (!CobbleHunts.hasHuntPermission(player, "global")) {
                        player.sendMessage(
                            Text.literal("You do not have permission for Global Hunts!")
                                .setStyle(Style.EMPTY.withItalic(false))
                                .styled { it.withColor(Formatting.RED) },
                            false
                        )
                        return
                    }
                    HuntsGlobalGui.openGlobalHuntsGui(player)
                }
                "solo" -> HuntsSoloGui.openSoloHuntsGui(player)
            }
            return
        }
        if (slot == MainSlots.LEADERBOARD && HuntsConfig.config.enableLeaderboard) {
            HuntsLeaderboard.openLeaderboardGui(player)
        }
    }

    fun refreshDynamicGuis() {
        dynamicGuiData.forEach { (player, pair) ->
            val (guiType, layout) = pair
            when (guiType) {
                "global" -> {
                    // Calculate isExtended flag from config
                    val isExtended = HuntsConfig.config.activeGlobalHuntsAtOnce > 7
                    HuntsGlobalGui.updateGlobalDynamicItems(player, layout, isExtended)
                }
                "solo" -> {
                    HuntsSoloGui.checkExpiredHunts(player)
                    CobbleHunts.refreshPreviewPokemon(player)
                    HuntsSoloGui.updateSoloDynamicItems(player, layout)
                }
            }
            CustomGui.refreshGui(player, layout)
        }
    }

    // Loot Pool Functions
    fun openLootPoolViewGui(player: ServerPlayerEntity, difficulty: String) {
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
        val layout = MutableList(27) { HuntsGuiUtils.createFillerPane() }
        val lootList = when (difficulty) {
            "global" -> HuntsConfig.config.globalLoot
            "easy" -> HuntsConfig.config.soloEasyLoot
            "normal" -> HuntsConfig.config.soloNormalLoot
            "medium" -> HuntsConfig.config.soloMediumLoot
            "hard" -> HuntsConfig.config.soloHardLoot
            else -> emptyList()
        }
        val registryOps = RegistryOps.of(JsonOps.INSTANCE, player.server.registryManager)
        val startIndex = page * 18
        val endIndex = min(startIndex + 18, lootList.size)
        for (i in startIndex until endIndex) {
            val reward = lootList[i]
            layout[i - startIndex] = HuntsGuiUtils.createLootRewardDisplay(reward, registryOps)
        }
        if (page > 0) {
            layout[18] = CustomGui.createPlayerHeadButton(
                textureName = "Previous",
                title = Text.literal("Previous Page")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.YELLOW) },
                lore = listOf(Text.literal("Go to previous page")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.GRAY) }),
                textureValue = LootPoolTextures.PREV_PAGE
            )
        }
        layout[22] = CustomGui.createPlayerHeadButton(
            textureName = "Back",
            title = Text.literal("Back")
                .setStyle(Style.EMPTY.withItalic(false))
                .styled { it.withColor(Formatting.YELLOW) },
            lore = listOf(Text.literal("Return to ${if (difficulty == "global") "Global" else "Solo"} Hunts")
                .setStyle(Style.EMPTY.withItalic(false))
                .styled { it.withColor(Formatting.GRAY) }),
            textureValue = LootPoolTextures.BACK
        )
        if (endIndex < lootList.size) {
            layout[26] = CustomGui.createPlayerHeadButton(
                textureName = "Next",
                title = Text.literal("Next Page")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.GREEN) },
                lore = listOf(Text.literal("Go to next page")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.GRAY) }),
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
                    HuntsGlobalGui.openGlobalHuntsGui(player)
                } else {
                    HuntsSoloGui.openSoloHuntsGui(player)
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
}
