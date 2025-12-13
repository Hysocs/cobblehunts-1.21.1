package com.cobblehunts.gui.huntsgui

import com.cobblehunts.CobbleHunts
import com.cobblehunts.gui.TurnInGui
import com.cobblehunts.utils.HuntsConfig
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.ClickType
import net.minecraft.util.Formatting

object HuntsSoloGui {
    private object SoloSlots {
        const val BACK = 22
    }

    private object SoloTextures {
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    private val soloHuntSlotMap = mapOf(
        1 to listOf(13),
        2 to listOf(12, 14),
        3 to listOf(11, 13, 15),
        4 to listOf(10, 12, 14, 16)
    )

    fun openSoloHuntsGui(player: ServerPlayerEntity) {
        // We ensure previews exist if we are in a state to accept them
        CobbleHunts.refreshPreviewPokemon(player)

        val layout = generateSoloLayout(player).toMutableList()
        HuntsGui.dynamicGuiData[player] = Pair("solo", layout)

        CustomGui.openGui(
            player,
            "Solo Hunts",
            layout,
            3,
            { context -> handleSoloInteraction(context, player) },
            { _ -> HuntsGui.dynamicGuiData.remove(player) }
        )
    }

    private fun generateSoloLayout(player: ServerPlayerEntity): List<ItemStack> {
        val layout = MutableList(27) { HuntsGuiUtils.createFillerPane() }
        val enabledDifficulties = getEnabledSoloDifficulties()
        val count = enabledDifficulties.size
        val slots = soloHuntSlotMap[count] ?: listOf()

        for ((index, difficulty) in enabledDifficulties.withIndex()) {
            val indicatorSlot = slots[index] - 9
            val state = CobbleHunts.getSoloHuntDisplayState(player, difficulty)

            // Indicator Light
            layout[indicatorSlot] = when (state) {
                is CobbleHunts.SoloHuntDisplayState.Active -> ItemStack(Items.GREEN_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
                else -> ItemStack(Items.BLACK_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
            }

            // Main Icon
            layout[slots[index]] = HuntsGuiUtils.getSoloDynamicItem(player, difficulty, state)
        }

        layout[SoloSlots.BACK] = CustomGui.createPlayerHeadButton(
            textureName = "Back",
            title = Text.literal("Back").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) },
            lore = listOf(Text.literal("Return to main menu").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }),
            textureValue = SoloTextures.BACK
        )
        return layout
    }

    // Called by the GUI refresher task
    fun updateSoloDynamicItems(player: ServerPlayerEntity, layout: MutableList<ItemStack>) {
        val enabledDifficulties = getEnabledSoloDifficulties()
        val count = enabledDifficulties.size
        val slots = soloHuntSlotMap[count] ?: listOf()

        for ((index, difficulty) in enabledDifficulties.withIndex()) {
            val indicatorSlot = slots[index] - 9
            val state = CobbleHunts.getSoloHuntDisplayState(player, difficulty)

            // Indicator
            layout[indicatorSlot] = when (state) {
                is CobbleHunts.SoloHuntDisplayState.Active -> ItemStack(Items.GREEN_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
                else -> ItemStack(Items.BLACK_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
            }
            // Main Item
            layout[slots[index]] = HuntsGuiUtils.getSoloDynamicItem(player, difficulty, state)
        }
    }

    private fun handleSoloInteraction(context: InteractionContext, player: ServerPlayerEntity) {
        val enabledDifficulties = getEnabledSoloDifficulties()
        val count = enabledDifficulties.size
        val slots = soloHuntSlotMap[count] ?: listOf()

        if (context.slotIndex in slots) {
            val index = slots.indexOf(context.slotIndex)
            val difficulty = enabledDifficulties[index]
            val state = CobbleHunts.getSoloHuntDisplayState(player, difficulty)

            // Permission Check
            if (state is CobbleHunts.SoloHuntDisplayState.Locked) {
                player.sendMessage(Text.literal("You do not have permission for $difficulty hunts!").styled { it.withColor(Formatting.RED) }, false)
                return
            }

            // Right Click: View Loot
            if (context.clickType == ClickType.RIGHT) {
                HuntsGui.openLootPoolViewGui(player, difficulty)
                return
            }

            // Left Click Action based on State
            when (state) {
                is CobbleHunts.SoloHuntDisplayState.Active -> {
                    // Turn In
                    TurnInGui.openTurnInGui(player, difficulty)
                }
                is CobbleHunts.SoloHuntDisplayState.Ready -> {
                    // Start Hunt
                    state.preview?.let {
                        CobbleHunts.activateMission(player, difficulty, it)
                        player.sendMessage(Text.literal("Activated $difficulty mission for ${it.entry.species}!").styled { s -> s.withColor(Formatting.GREEN) }, false)

                        // Immediately refresh GUI
                        val newLayout = generateSoloLayout(player).toMutableList()
                        HuntsGui.dynamicGuiData[player] = Pair("solo", newLayout)
                        CustomGui.refreshGui(player, newLayout)
                    }
                }
                is CobbleHunts.SoloHuntDisplayState.Cooldown -> {
                    player.sendMessage(Text.literal("You are on cooldown for $difficulty missions!").styled { it.withColor(Formatting.RED) }, false)
                }
                is CobbleHunts.SoloHuntDisplayState.Locked -> {} // Handled above
            }
        } else if (context.slotIndex == SoloSlots.BACK) {
            HuntsGui.openMainGui(player)
        }
    }

    private fun getEnabledSoloDifficulties(): List<String> {
        val settings = HuntsConfig.settings
        return listOf(
            "easy" to settings.soloEasyEnabled,
            "normal" to settings.soloNormalEnabled,
            "medium" to settings.soloMediumEnabled,
            "hard" to settings.soloHardEnabled
        ).filter { it.second }.map { it.first }
    }

}