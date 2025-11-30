package com.cobblehunts.gui.huntsgui

import com.cobblehunts.CobbleHunts
import com.cobblehunts.gui.TurnInGui
import com.cobblehunts.utils.HuntsConfig
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import kotlin.math.roundToInt
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.ClickType
import net.minecraft.util.Formatting

object HuntsGlobalGui {
    // In normal (3-row) mode the back button is at 22.
    // In extended (6-row) mode we want it centered in the bottom row (row 5, col 4 → slot 49).
    private object GlobalSlots {
        const val BACK_NORMAL = 22
        const val BACK_EXTENDED = 49
    }

    // (Original static mapping is only used in normal mode.)
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
        // Use extended mode when active hunts > 7
        val isExtended = HuntsConfig.config.activeGlobalHuntsAtOnce > 7
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

    // Generates the base layout – differing total slots and back button placement.
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

    // Computes the ideal horizontal positions for a full row when placing k items in a 7-column row.
    private fun computeIdealRowPositions(k: Int): List<Int> {
        return if (k == 1) {
            listOf(4)
        } else {
            (0 until k).map { i ->
                // The formula below uses linear interpolation between column 1 and 7.
                (((i + 0.5) / k * 7.0) + 0.5).roundToInt().coerceIn(1,7)
            }
        }
    }

    // Updates the dynamic hunt items in extended mode.
    fun updateGlobalDynamicItems(player: ServerPlayerEntity, layout: MutableList<ItemStack>, isExtended: Boolean) {
        val data = CobbleHunts.getPlayerData(player)
        val hunts = CobbleHunts.globalHuntStates
        val currentTime = System.currentTimeMillis()
        if (!isExtended) {
            if (hunts.isNotEmpty()) {
                val n = hunts.size
                val pokemonSlots = huntSlotMap[n] ?: listOf()
                val indicatorSlots = pokemonSlots.map { it - 9 }
                for (i in 0 until n) {
                    val hunt = hunts[i]
                    val isCompleted = if (HuntsConfig.config.lockGlobalHuntsOnCompletionForAllPlayers)
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
                            setCustomName(
                                Text.literal("Completed")
                                    .setStyle(Style.EMPTY.withItalic(false))
                                    .styled { it.withColor(Formatting.GRAY) }
                            )
                        }
                    } else {
                        HuntsGuiUtils.createActivePokemonItem(hunt, "global")
                    }
                }
            } else {
                // Global hunts are on cooldown.
                // Determine the number of hunts that would be active based on config,
                // but cap to 7 because this mode uses huntSlotMap (which only supports up to 7 hunts).
                val desiredCount = minOf(HuntsConfig.config.activeGlobalHuntsAtOnce, 7)
                val slots = huntSlotMap[desiredCount] ?: listOf(13) // fallback slot if mapping missing
                val remainingCooldown = (CobbleHunts.globalCooldownEnd - currentTime) / 1000
                val timeString = if (remainingCooldown > 0)
                    HuntsGuiUtils.formatTime(remainingCooldown.toInt())
                else "Starting soon..."
                // Place a clock in each slot normally used for a global hunt.
                for (slot in slots) {
                    layout[slot] = ItemStack(Items.CLOCK).apply {
                        setCustomName(
                            Text.literal("Next Global Hunts in $timeString")
                                .setStyle(Style.EMPTY.withItalic(false))
                                .styled { it.withColor(Formatting.YELLOW) }
                        )
                    }
                }
            }
        } else {
            // Extended mode – use the previously defined grid logic.
            val n = hunts.size.coerceAtMost(28)
            if (n > 0) {
                val quotient = n / 4
                val remainder = n % 4
                val rowCounts = (0 until 4).map { i -> quotient + if (i < remainder) 1 else 0 }
                val maxCount = rowCounts.maxOrNull() ?: 1
                val baseIdeal = computeIdealRowPositions(maxCount)
                var ptr = 0
                for (row in 0 until 4) {
                    val count = rowCounts[row]
                    if (count == 0) continue
                    val rowPositions = if (count == 1) {
                        listOf(4)
                    } else {
                        (0 until count).map { j ->
                            val index = ((j.toDouble() * (maxCount - 1)) / (count - 1)).roundToInt()
                                .coerceIn(0, baseIdeal.size - 1)
                            baseIdeal[index]
                        }
                    }
                    for (j in 0 until count) {
                        if (ptr >= n) break
                        val hunt = hunts[ptr]
                        val slot = ((row + 1) * 9) + rowPositions[j]
                        val isCompleted = if (HuntsConfig.config.lockGlobalHuntsOnCompletionForAllPlayers)
                            CobbleHunts.globalCompletedHuntIndices.contains(ptr)
                        else
                            data.completedGlobalHunts.contains(ptr)
                        layout[slot] = if (isCompleted) {
                            ItemStack(Items.CLOCK).apply {
                                setCustomName(
                                    Text.literal("Completed")
                                        .setStyle(Style.EMPTY.withItalic(false))
                                        .styled { it.withColor(Formatting.GRAY) }
                                )
                            }
                        } else {
                            HuntsGuiUtils.createActivePokemonItem(hunt, "global")
                        }
                        ptr++
                    }
                }
            } else {
                // If no hunts are active, fill the grid with the cooldown message.
                val gridPositions = mutableListOf<Int>()
                for (row in 1..4) {
                    for (col in 1..7) {
                        gridPositions.add(row * 9 + col)
                    }
                }
                val remainingCooldown = (CobbleHunts.globalCooldownEnd - currentTime) / 1000
                val timeString = if (remainingCooldown > 0)
                    HuntsGuiUtils.formatTime(remainingCooldown.toInt())
                else "Starting soon..."
                gridPositions.forEach { slot ->
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
    }



    // Handles click interactions – for extended mode we re-calculate the grid positions.
    private fun handleGlobalInteraction(
        context: InteractionContext,
        player: ServerPlayerEntity,
        isExtended: Boolean
    ) {
        val slot = context.slotIndex
        val hunts = CobbleHunts.globalHuntStates

        if (!isExtended) {
            // --- FIX START ---
            val n = hunts.size
            val pokemonSlots = huntSlotMap[n] ?: listOf()
            if (slot in pokemonSlots) {
                val index = pokemonSlots.indexOf(slot)

                // Calculate completion status correctly for the specific player
                val isCompleted = if (HuntsConfig.config.lockGlobalHuntsOnCompletionForAllPlayers)
                    CobbleHunts.globalCompletedHuntIndices.contains(index)
                else
                    CobbleHunts.getPlayerData(player).completedGlobalHunts.contains(index)

                // Only allow opening if the hunt exists and is NOT completed
                if (index < hunts.size && !isCompleted) {
                    if (context.clickType == ClickType.RIGHT) {
                        HuntsGui.openLootPoolViewGui(player, "global")
                    } else if (context.clickType == ClickType.LEFT) {
                        TurnInGui.openTurnInGui(player, "global", index)
                    }
                }
            }
            // --- FIX END ---
        } else {
            // Extended mode logic (this part was already mostly correct, but ensured here)
            val n = hunts.size.coerceAtMost(28)
            if (n > 0) {
                val quotient = n / 4
                val remainder = n % 4
                val rowCounts = (0 until 4).map { i -> quotient + if (i < remainder) 1 else 0 }
                val maxCount = rowCounts.maxOrNull() ?: 1
                val baseIdeal = computeIdealRowPositions(maxCount)
                val slotMapping = mutableMapOf<Int, Int>()
                var ptr = 0
                for (row in 0 until 4) {
                    val count = rowCounts[row]
                    if (count == 0) continue
                    val rowPositions = if (count == 1) {
                        listOf(4)
                    } else {
                        (0 until count).map { j ->
                            val index = ((j.toDouble() * (maxCount - 1)) / (count - 1)).roundToInt().coerceIn(0, baseIdeal.size - 1)
                            baseIdeal[index]
                        }
                    }
                    for (j in 0 until count) {
                        if (ptr >= n) break
                        val guiSlot = ((row + 1) * 9) + rowPositions[j]
                        slotMapping[guiSlot] = ptr
                        ptr++
                    }
                }
                if (slot in slotMapping.keys) {
                    val index = slotMapping[slot]!!
                    val isCompleted = if (HuntsConfig.config.lockGlobalHuntsOnCompletionForAllPlayers)
                        CobbleHunts.globalCompletedHuntIndices.contains(index)
                    else
                        CobbleHunts.getPlayerData(player).completedGlobalHunts.contains(index)

                    if (!isCompleted) {
                        if (context.clickType == ClickType.RIGHT) {
                            HuntsGui.openLootPoolViewGui(player, "global")
                        } else if (context.clickType == ClickType.LEFT) {
                            TurnInGui.openTurnInGui(player, "global", index)
                        }
                    }
                }
            }
        }

        val backSlot = if (isExtended) GlobalSlots.BACK_EXTENDED else GlobalSlots.BACK_NORMAL
        if (slot == backSlot) {
            HuntsGui.openMainGui(player)
        }
    }
}
