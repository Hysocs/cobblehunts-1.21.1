package com.cobblehunts.gui.huntsgui


import com.cobblehunts.utils.HuntsConfig
import com.cobblehunts.utils.LeaderboardManager
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.setCustomName
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.ProfileComponent
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.UUID

object HuntsLeaderboard {
    private object LeaderboardSlots {
        const val BACK = 49
        const val INFO = 4
    }

    private object LeaderboardTextures {
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        const val INFO = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTcxYTIyODVjOTFjNmM3Mjc0NzYwNDgxOWVlNTIyM2E5MGFhNTFlNmU3OWU0ZjlhZjY2MjhlYzhmMGRkN2RmYyJ9fX0="
    }

    fun openLeaderboardGui(player: ServerPlayerEntity) {
        val layout = MutableList(54) { HuntsGuiUtils.createFillerPane() }
        CustomGui.openGui(
            player,
            "Leaderboard",
            layout,
            6,
            { context ->
                if (context.slotIndex == LeaderboardSlots.BACK) {
                    player.closeHandledScreen()
                    HuntsGui.openMainGui(player)
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
            val (playerName, points, storedTexture) = topPlayers[i]
            val slot = leaderboardSlots[i]
            val head = ItemStack(Items.PLAYER_HEAD)
            val rankText = Text.literal("Rank ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
            val rankNumber = Text.literal("${i + 1}").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GOLD) }
            val colon = Text.literal(": ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
            val playerNameText = Text.literal(playerName).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.AQUA) }
            val fullTitle = rankText.append(rankNumber).append(colon).append(playerNameText)
            head.setCustomName(fullTitle)

            val pointsText = Text.literal("Points: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
            val pointsValue = Text.literal("$points").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GREEN) }
            val loreLine = pointsText.append(pointsValue)
            CustomGui.setItemLore(head, listOf(loreLine))

            layout[slot] = head

            val offlineUUID = UUID.nameUUIDFromBytes("OfflinePlayer:$playerName".toByteArray())
            val initialProfile = if (storedTexture != null) {
                GameProfile(offlineUUID, playerName).apply {
                    properties.put("textures", Property("textures", storedTexture))
                }
            } else {
                GameProfile(offlineUUID, playerName)
            }
            head.set(DataComponentTypes.PROFILE, ProfileComponent(initialProfile))

            LeaderboardManager.updatePlayerTexture(playerName, player.server).thenAccept { texture ->
                player.server.execute {
                    val finalProfile = if (texture != null) {
                        GameProfile(offlineUUID, playerName).apply {
                            properties.put("textures", Property("textures", texture))
                        }
                    } else {
                        GameProfile(offlineUUID, playerName)
                    }
                    head.set(DataComponentTypes.PROFILE, ProfileComponent(finalProfile))
                    layout[slot] = head
                    CustomGui.refreshGui(player, layout)
                }
            }
        }

        val infoHead = CustomGui.createPlayerHeadButton(
            textureName = "Info",
            title = Text.literal("Points Information").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.WHITE) },
            lore = listOf(
                Text.literal("Complete hunts to earn points!").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) },
                Text.literal("Global Hunt: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("${HuntsConfig.settings.globalPoints} points").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GREEN) }),
                Text.literal("Easy Hunt: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("${HuntsConfig.settings.soloEasyPoints} points").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GREEN) }),
                Text.literal("Normal Hunt: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("${HuntsConfig.settings.soloNormalPoints} points").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GREEN) }),
                Text.literal("Medium Hunt: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("${HuntsConfig.settings.soloMediumPoints} points").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GREEN) }),
                Text.literal("Hard Hunt: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("${HuntsConfig.settings.soloHardPoints} points").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GREEN) })
            ),
            textureValue = LeaderboardTextures.INFO
        )
        layout[LeaderboardSlots.INFO] = infoHead

        layout[LeaderboardSlots.BACK] = CustomGui.createPlayerHeadButton(
            textureName = "Back",
            title = Text.literal("Back").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) },
            lore = listOf(Text.literal("Return to main menu").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }),
            textureValue = LeaderboardTextures.BACK
        )

        CustomGui.refreshGui(player, layout)
    }
}