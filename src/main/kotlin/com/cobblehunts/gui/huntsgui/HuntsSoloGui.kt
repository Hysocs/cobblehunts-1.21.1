package com.cobblehunts.gui.huntsgui

import com.cobblehunts.CobbleHunts
import com.cobblehunts.gui.HuntsGui
import com.cobblehunts.gui.TurnInGui
import com.cobblehunts.utils.EconomyAdapter
import com.cobblehunts.utils.HuntsConfig
import com.cobblehunts.utils.RerollService
import com.cobblehunts.utils.rerollRules
import com.everlastingutils.command.CommandManager
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
        const val REROLL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWIzOTE0NTc2YjBiOGU4NzQ5ZTE1ZmJmZWUwNWExNmVhNDEzZDBkNTg1M2M5MDM0MjA5NGViNmFmODI5ZjlmZiJ9fX0="
    }

    private val soloHuntSlotMap = mapOf(
        1 to listOf(13),
        2 to listOf(12, 14),
        3 to listOf(11, 13, 15),
        4 to listOf(10, 12, 14, 16)
    )

    fun openSoloHuntsGui(player: ServerPlayerEntity) {
        checkExpiredHunts(player)
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

        val config = HuntsConfig.config
        val data = CobbleHunts.getPlayerData(player)

        val economyEnabled = config.economyEnabled
        val rerollSlots = slots.map { it + 9 }
        val backSlot = if (SoloSlots.BACK in rerollSlots) 26 else SoloSlots.BACK

        for ((index, difficulty) in enabledDifficulties.withIndex()) {
            val indicatorSlot = slots[index] - 9
            val previewSlot = slots[index]
            val rerollSlot = previewSlot + 9

            val activeInstance = data.activePokemon[difficulty]
            val isActive = activeInstance != null && (activeInstance.endTime == null || System.currentTimeMillis() < activeInstance.endTime!!)
            layout[indicatorSlot] = if (isActive) {
                ItemStack(Items.GREEN_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
            } else {
                ItemStack(Items.BLACK_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
            }
            layout[slots[index]] = HuntsGuiUtils.getSoloDynamicItem(player, difficulty)

            val rules = data.rerollRules(difficulty)

            val lore = mutableListOf<Text>().apply {
                if (economyEnabled) {
                    add(Text.literal("Cost: ${EconomyAdapter.symbol()}${rules.cost} ${EconomyAdapter.currencyId()}")
                        .setStyle(Style.EMPTY.withItalic(false))
                        .styled { it.withColor(Formatting.GRAY) }
                    )
                }
                add (
                    Text.literal("Rerolls: ${if (rules.unlimited) "Unlimited" else "${rules.used}/${rules.limit}"}")
                        .setStyle(Style.EMPTY.withItalic(false))
                        .styled { it.withColor(if (rules.unlimited || rules.used < rules.limit) Formatting.YELLOW else Formatting.RED) }
                )
            }

            layout[rerollSlot] = CustomGui.createPlayerHeadButton(
                textureName = "Reroll $difficulty",
                title = Text.literal("Reroll (${difficulty.replaceFirstChar { it.uppercaseChar() }})")
                    .setStyle(Style.EMPTY.withItalic(false))
                    .styled { it.withColor(Formatting.GREEN) },
                lore = lore,
                textureValue = SoloTextures.REROLL
            )
        }

        layout[backSlot] = CustomGui.createPlayerHeadButton(
            textureName = "Back",
            title = Text.literal("Back").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) },
            lore = listOf(Text.literal("Return to main menu").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }),
            textureValue = SoloTextures.BACK
        )
        return layout
    }

    fun updateSoloDynamicItems(player: ServerPlayerEntity, layout: MutableList<ItemStack>) {
        val enabledDifficulties = getEnabledSoloDifficulties()
        val count = enabledDifficulties.size
        val slots = soloHuntSlotMap[count] ?: listOf()
        val data = CobbleHunts.getPlayerData(player)
        data.resetRerollsIfNeeded()

        for ((index, difficulty) in enabledDifficulties.withIndex()) {
            val indicatorSlot = slots[index] - 9

            val activeInstance = data.activePokemon[difficulty]
            val isActive = activeInstance != null && (activeInstance.endTime == null || System.currentTimeMillis() < activeInstance.endTime!!)
            layout[indicatorSlot] = if (isActive) {
                ItemStack(Items.GREEN_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
            } else {
                ItemStack(Items.BLACK_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
            }
            layout[slots[index]] = HuntsGuiUtils.getSoloDynamicItem(player, difficulty)
        }
    }

    private fun handleSoloInteraction(context: InteractionContext, player: ServerPlayerEntity) {
        val enabledDifficulties = getEnabledSoloDifficulties()
        val count = enabledDifficulties.size
        val slots = soloHuntSlotMap[count] ?: listOf()

        val perms = HuntsConfig.config.permissions
        val source = player.server.commandSource.withEntity(player).withPosition(player.pos)
        val rerollSlots = slots.map { it + 9 }
        val backSlot = if (SoloSlots.BACK in rerollSlots) 26 else SoloSlots.BACK

        for ((index, difficulty) in enabledDifficulties.withIndex()) {
            val rerollSlot = slots[index] + 9
            if (context.slotIndex == rerollSlot) {
                if (!CommandManager.hasPermissionOrOp(source,perms.rerollPermission,perms.permissionLevel, perms.opLevel)) {
                    player.sendMessage(Text.literal("You do not have permission to reroll hunts.").styled { it.withColor(Formatting.RED) }, false)
                    return
                }

                if (CobbleHunts.isOnCooldown(player, difficulty)) {
                    player.sendMessage(
                        Text.literal("You are on cooldown for $difficulty missions!")
                            .setStyle(Style.EMPTY.withItalic(false))
                            .styled { it.withColor(Formatting.RED) },
                        false
                    )
                    return
                }

                if (RerollService.tryRerollPreview(player, "solo$difficulty")) {
                    val newLayout = generateSoloLayout(player).toMutableList()
                    HuntsGui.dynamicGuiData[player] = Pair("solo", newLayout)
                    CustomGui.refreshGui(player, newLayout)
                }
                return
            }
        }

        if (context.slotIndex in slots) {
            val index = slots.indexOf(context.slotIndex)
            val difficulty = enabledDifficulties[index]
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
                HuntsGui.openLootPoolViewGui(player, difficulty)
                return
            }
            if (activeInstance == null) {
                if (CobbleHunts.isOnCooldown(player, difficulty)) {
                    player.sendMessage(
                        Text.literal("You are on cooldown for $difficulty missions!")
                            .setStyle(Style.EMPTY.withItalic(false))
                            .styled { it.withColor(Formatting.RED) },
                        false
                    )
                } else {
                    val instance = CobbleHunts.getPreviewPokemon(player, difficulty)
                    if (instance != null) {
                        CobbleHunts.activateMission(player, difficulty, instance)
                        player.sendMessage(
                            Text.literal("Activated $difficulty mission for ${instance.entry.species}!")
                                .setStyle(Style.EMPTY.withItalic(false))
                                .styled { it.withColor(Formatting.GREEN) },
                            false
                        )
                        val newLayout = generateSoloLayout(player).toMutableList()
                        HuntsGui.dynamicGuiData[player] = Pair("solo", newLayout)
                        CustomGui.refreshGui(player, newLayout)
                    }
                }
            } else {
                TurnInGui.openTurnInGui(player, difficulty)
            }
        } else if (context.slotIndex == backSlot) {
            HuntsGui.openMainGui(player)
        }
    }

    fun checkExpiredHunts(player: ServerPlayerEntity) {
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

    private fun getEnabledSoloDifficulties(): List<String> {
        val config = HuntsConfig.config
        return listOf(
            "easy" to config.soloEasyEnabled,
            "normal" to config.soloNormalEnabled,
            "medium" to config.soloMediumEnabled,
            "hard" to config.soloHardEnabled
        ).filter { it.second }.map { it.first }
    }
}