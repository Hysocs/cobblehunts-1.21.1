package com.cobblehunts.gui.huntsgui

import com.cobblehunts.CobbleHunts
import com.cobblehunts.CobbleHunts.MOD_ID
import com.cobblehunts.gui.TurnInGui
import com.cobblehunts.utils.HuntsConfig
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import com.everlastingutils.utils.LogDebug
import kotlin.math.roundToInt
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.ClickType
import net.minecraft.util.Formatting

object HuntsGlobalGui {
    private object GlobalSlots {
        const val BACK_NORMAL = 22
        const val BACK_EXTENDED = 49
    }

    private val huntSlotMap = mapOf(
        1 to listOf(13),
        2 to listOf(12, 14),
        3 to listOf(11, 13, 15),
        4 to listOf(10, 12, 14, 16),
        5 to listOf(9, 11, 13, 15, 17),
        6 to listOf(9, 11, 12, 14, 15, 17),
        7 to listOf(10, 11, 12, 13, 14, 15, 16)
    )

    private object GlobalTextures {
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    fun openGlobalHuntsGui(player: ServerPlayerEntity) {
        val isExtended = HuntsConfig.settings.activeGlobalHuntsAtOnce > 7
        val rows = if (isExtended) 6 else 3
        val layout = generateGlobalLayout(player, isExtended).toMutableList()
        updateGlobalDynamicItems(player, layout, isExtended)
        HuntsGui.dynamicGuiData[player] = Pair("global", layout)
        CustomGui.openGui(
            player,
            "Global Hunts",
            layout,
            rows,
            { context -> handleGlobalInteraction(context, player, isExtended) },
            { _ -> HuntsGui.dynamicGuiData.remove(player) }
        )
    }

    private fun generateGlobalLayout(player: ServerPlayerEntity, isExtended: Boolean): List<ItemStack> {
        val size = if (isExtended) 54 else 27
        val layout = MutableList(size) { HuntsGuiUtils.createFillerPane() }
        val backSlot = if (isExtended) GlobalSlots.BACK_EXTENDED else GlobalSlots.BACK_NORMAL
        layout[backSlot] = CustomGui.createPlayerHeadButton(
            textureName = "Back",
            title = Text.literal("Back")
                .setStyle(Style.EMPTY.withItalic(false))
                .styled { it.withColor(Formatting.YELLOW) },
            lore = listOf(
                Text.literal("Return to main menu")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.GRAY) }
            ),
            textureValue = GlobalTextures.BACK
        )
        return layout
    }

    private fun computeIdealRowPositions(k: Int): List<Int> {
        return if (k == 1) {
            listOf(4)
        } else {
            (0 until k).map { i ->
                (((i + 0.5) / k * 7.0) + 0.5).roundToInt().coerceIn(1,7)
            }
        }
    }

    fun updateGlobalDynamicItems(player: ServerPlayerEntity, layout: MutableList<ItemStack>, isExtended: Boolean) {
        val data = CobbleHunts.getPlayerData(player)
        val state = CobbleHunts.getGlobalHuntDisplayState()

        if (!isExtended) {
            when (state) {
                is CobbleHunts.GlobalHuntDisplayState.Active -> {
                    val n = state.hunts.size.coerceAtMost(7)
                    val pokemonSlots = huntSlotMap[n] ?: listOf()
                    val indicatorSlots = pokemonSlots.map { it - 9 }

                    for (i in 0 until n) {
                        val hunt = state.hunts[i]
                        val isCompleted = if (HuntsConfig.settings.lockGlobalHuntsOnCompletionForAllPlayers)
                            CobbleHunts.globalCompletedHuntIndices.contains(i)
                        else
                            data.completedGlobalHunts.contains(i)

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
                            HuntsGuiUtils.createActivePokemonItem(hunt, "global", state.remainingSeconds)
                        }
                    }
                }

                is CobbleHunts.GlobalHuntDisplayState.Cooldown -> {
                    val desiredCount = minOf(HuntsConfig.settings.activeGlobalHuntsAtOnce, 7)
                    val slots = huntSlotMap[desiredCount] ?: listOf(13)
                    val timeString = HuntsGuiUtils.formatTime(state.remainingSeconds.toInt())

                    for (slot in slots) {
                        layout[slot] = ItemStack(Items.CLOCK).apply {
                            setCustomName(Text.literal("Cooldown: $timeString").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) })
                        }
                    }
                }

                is CobbleHunts.GlobalHuntDisplayState.StartingSoon -> {
                    val desiredCount = minOf(HuntsConfig.settings.activeGlobalHuntsAtOnce, 7)
                    val slots = huntSlotMap[desiredCount] ?: listOf(13)
                    for (slot in slots) {
                        layout[slot] = ItemStack(Items.CLOCK).apply {
                            setCustomName(Text.literal("Starting soon...").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) })
                        }
                    }
                }
            }
        } else {
            when (state) {
                is CobbleHunts.GlobalHuntDisplayState.Active -> {
                    val n = state.hunts.size.coerceAtMost(28)
                    val quotient = n / 4
                    val remainder = n % 4
                    val rowCounts = (0 until 4).map { i -> quotient + if (i < remainder) 1 else 0 }
                    val maxCount = rowCounts.maxOrNull() ?: 1
                    val baseIdeal = computeIdealRowPositions(maxCount)
                    var ptr = 0

                    for (row in 0 until 4) {
                        val count = rowCounts[row]
                        if (count == 0) continue
                        val rowPositions = if (count == 1) listOf(4) else (0 until count).map { j ->
                            val index = ((j.toDouble() * (maxCount - 1)) / (count - 1)).roundToInt().coerceIn(0, baseIdeal.size - 1)
                            baseIdeal[index]
                        }
                        for (j in 0 until count) {
                            if (ptr >= n) break
                            val hunt = state.hunts[ptr]
                            val slot = ((row + 1) * 9) + rowPositions[j]
                            val isCompleted = if (HuntsConfig.settings.lockGlobalHuntsOnCompletionForAllPlayers)
                                CobbleHunts.globalCompletedHuntIndices.contains(ptr)
                            else
                                data.completedGlobalHunts.contains(ptr)

                            layout[slot] = if (isCompleted) {
                                ItemStack(Items.CLOCK).apply {
                                    setCustomName(Text.literal("Completed").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) })
                                }
                            } else {
                                HuntsGuiUtils.createActivePokemonItem(hunt, "global", state.remainingSeconds)
                            }
                            ptr++
                        }
                    }
                }
                is CobbleHunts.GlobalHuntDisplayState.Cooldown -> {
                    val timeString = HuntsGuiUtils.formatTime(state.remainingSeconds.toInt())
                    for (row in 1..4) {
                        for (col in 1..7) {
                            layout[row * 9 + col] = ItemStack(Items.CLOCK).apply {
                                setCustomName(Text.literal("Cooldown: $timeString").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) })
                            }
                        }
                    }
                }
                is CobbleHunts.GlobalHuntDisplayState.StartingSoon -> {
                    for (row in 1..4) {
                        for (col in 1..7) {
                            layout[row * 9 + col] = ItemStack(Items.CLOCK).apply {
                                setCustomName(Text.literal("Starting soon...").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) })
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleGlobalInteraction(
        context: InteractionContext,
        player: ServerPlayerEntity,
        isExtended: Boolean
    ) {
        val slot = context.slotIndex
        val state = CobbleHunts.getGlobalHuntDisplayState()

        if (state !is CobbleHunts.GlobalHuntDisplayState.Active) {
            val backSlot = if (isExtended) GlobalSlots.BACK_EXTENDED else GlobalSlots.BACK_NORMAL
            if (slot == backSlot) HuntsGui.openMainGui(player)
            return
        }

        val hunts = state.hunts
        var selectedIndex = -1

        if (!isExtended) {
            val n = hunts.size.coerceAtMost(7)
            val pokemonSlots = huntSlotMap[n] ?: listOf()
            if (slot in pokemonSlots) {
                selectedIndex = pokemonSlots.indexOf(slot)
            }
        } else {
            val n = hunts.size.coerceAtMost(28)
            val quotient = n / 4
            val remainder = n % 4
            val rowCounts = (0 until 4).map { i -> quotient + if (i < remainder) 1 else 0 }
            val maxCount = rowCounts.maxOrNull() ?: 1
            val baseIdeal = computeIdealRowPositions(maxCount)
            var ptr = 0
            for (row in 0 until 4) {
                val count = rowCounts[row]
                if (count == 0) continue
                val rowPositions = if (count == 1) listOf(4) else (0 until count).map { j ->
                    val index = ((j.toDouble() * (maxCount - 1)) / (count - 1)).roundToInt().coerceIn(0, baseIdeal.size - 1)
                    baseIdeal[index]
                }
                for (j in 0 until count) {
                    if (ptr >= n) break
                    val guiSlot = ((row + 1) * 9) + rowPositions[j]
                    if (guiSlot == slot) {
                        selectedIndex = ptr
                        break
                    }
                    ptr++
                }
                if (selectedIndex != -1) break
            }
        }

        if (selectedIndex != -1) {
            val isCompleted = if (HuntsConfig.settings.lockGlobalHuntsOnCompletionForAllPlayers)
                CobbleHunts.globalCompletedHuntIndices.contains(selectedIndex)
            else
                CobbleHunts.getPlayerData(player).completedGlobalHunts.contains(selectedIndex)

            if (!isCompleted) {
                if (context.clickType == ClickType.RIGHT) {
                    HuntsGui.openLootPoolViewGui(player, "global")
                } else if (context.clickType == ClickType.LEFT) {
                    TurnInGui.openTurnInGui(player, "global", selectedIndex)
                }
            }
        }

        val backSlot = if (isExtended) GlobalSlots.BACK_EXTENDED else GlobalSlots.BACK_NORMAL
        if (slot == backSlot) {
            HuntsGui.openMainGui(player)
        }
    }
}