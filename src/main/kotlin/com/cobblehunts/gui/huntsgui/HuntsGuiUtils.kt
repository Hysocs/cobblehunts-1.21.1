package com.cobblehunts.gui.huntsgui

import com.cobblehunts.CobbleHunts
import com.cobblehunts.HuntInstance
import com.cobblehunts.utils.HuntsConfig
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.item.PokemonItem
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.setCustomName
import com.google.gson.JsonElement
import com.mojang.serialization.DynamicOps
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import com.everlastingutils.utils.LogDebug
import com.cobblehunts.CobbleHunts.MOD_ID

object HuntsGuiUtils {
    fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
    }

    fun getRarityColor(difficulty: String): Formatting {
        return when (difficulty) {
            "easy" -> Formatting.GREEN
            "normal" -> Formatting.BLUE
            "medium" -> Formatting.GOLD
            "hard" -> Formatting.RED
            "global" -> Formatting.DARK_PURPLE
            else -> Formatting.WHITE
        }
    }

    fun formatTime(seconds: Int): String {
        if (seconds <= 0) return "0s"  // Optional: also shorten "0 seconds" for consistency
        val minutes = seconds / 60
        val secs = seconds % 60
        return if (minutes > 0) {
            if (secs > 0) "${minutes}m ${secs}s" else "${minutes}m"
        } else {
            "${secs}s"
        }
    }

    // --- Active Hunt Item ---
    fun createActivePokemonItem(instance: HuntInstance, difficulty: String, remainingSeconds: Long? = null): ItemStack {
        val entry = instance.entry
        val properties = PokemonProperties.parse(
            "${entry.species}${if (entry.form != null) " form=${entry.form}" else ""}${if (entry.aspects.contains("shiny")) " aspect=shiny" else ""}"
        )
        val pokemon = properties.create()
        val item = PokemonItem.from(pokemon)

        val displayName = "${entry.species.replaceFirstChar { it.titlecase() }}${if (entry.form != null) " (${entry.form.replaceFirstChar { it.titlecase() }})" else ""}${if (entry.aspects.contains("shiny")) ", Shiny" else ""}"
        item.setCustomName(Text.literal(displayName).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.WHITE) })

        val lore = mutableListOf<Text>()
        val difficultyText = if (difficulty == "global") "Global Hunt" else "${difficulty.replaceFirstChar { it.uppercase() }} Mission"
        lore.add(Text.literal(difficultyText).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(getRarityColor(difficulty)) }
            .append(Text.literal(" (Active)").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GREEN) }))

        lore.add(Text.literal("Hunt Requirements:").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.AQUA) })
        addHuntRequirements(lore, instance)

        // Time logic handled by passed parameter or fallback
        val timeLeft = remainingSeconds ?: instance.endTime?.let { (it - System.currentTimeMillis()) / 1000 } ?: 0

        lore.add(Text.literal("Time Left: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
            .append(Text.literal(formatTime(timeLeft.toInt())).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.AQUA) }))

        lore.add(Text.literal("Left-click to turn in").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GREEN) })
        lore.add(Text.literal("Right-click to view loot pool").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) })
        CustomGui.setItemLore(item, lore)
        return item
    }

    // --- Preview / Ready Item ---
    fun createPreviewPokemonItem(instance: HuntInstance, difficulty: String): ItemStack {
        val entry = instance.entry
        val properties = PokemonProperties.parse(
            "${entry.species}${if (entry.form != null) " form=${entry.form}" else ""}${if (entry.aspects.contains("shiny")) " aspect=shiny" else ""}"
        )
        val pokemon = properties.create()
        val item = PokemonItem.from(pokemon)

        val displayName = "${entry.species.replaceFirstChar { it.titlecase() }}${if (entry.form != null) " (${entry.form.replaceFirstChar { it.titlecase() }})" else ""}${if (entry.aspects.contains("shiny")) ", Shiny" else ""}"
        item.setCustomName(Text.literal(displayName).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.WHITE) })

        val lore = mutableListOf<Text>()
        val difficultyDisplay = if (difficulty == "global") "Global Hunt" else "${difficulty.replaceFirstChar { it.uppercase() }} Mission"
        lore.add(Text.literal(difficultyDisplay).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(getRarityColor(difficulty)) })

        lore.add(Text.literal("Hunt Requirements:").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.AQUA) })
        addHuntRequirements(lore, instance)

        val timeLimit = when (difficulty) {
            "easy" -> HuntsConfig.settings.soloEasyTimeLimit
            "normal" -> HuntsConfig.settings.soloNormalTimeLimit
            "medium" -> HuntsConfig.settings.soloMediumTimeLimit
            "hard" -> HuntsConfig.settings.soloHardTimeLimit
            else -> 300
        }
        val cooldown = when (difficulty) {
            "easy" -> HuntsConfig.settings.soloEasyCooldown
            "normal" -> HuntsConfig.settings.soloNormalCooldown
            "medium" -> HuntsConfig.settings.soloMediumCooldown
            "hard" -> HuntsConfig.settings.soloHardCooldown
            else -> 60
        }

        lore.add(Text.literal("Time Limit: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
            .append(Text.literal(formatTime(timeLimit)).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.AQUA) }))
        lore.add(Text.literal("Cooldown: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
            .append(Text.literal(formatTime(cooldown)).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.AQUA) }))

        lore.add(Text.literal("Left-click to start this hunt").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) })
        lore.add(Text.literal("Right-click to see loot table").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) })
        CustomGui.setItemLore(item, lore)
        return item
    }

    // --- State-Aware Item Generator ---
    fun getSoloDynamicItem(player: ServerPlayerEntity, difficulty: String, state: CobbleHunts.SoloHuntDisplayState): ItemStack {
        return when (state) {
            is CobbleHunts.SoloHuntDisplayState.Active -> {
                createActivePokemonItem(state.instance, difficulty, state.remainingSeconds)
            }
            is CobbleHunts.SoloHuntDisplayState.Cooldown -> {
                val timeString = formatTime(state.remainingSeconds.toInt())
                ItemStack(Items.CLOCK).apply {
                    setCustomName(Text.literal("Cooldown: $timeString").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.YELLOW) })
                }
            }
            is CobbleHunts.SoloHuntDisplayState.Ready -> {
                if (HuntsConfig.settings.autoAcceptSoloHunts) {
                    // If auto-accept is on but we are in Ready state, wait for scheduler to pick it up
                    ItemStack(Items.CLOCK).apply {
                        setCustomName(Text.literal("Starting...").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) })
                    }
                } else {
                    state.preview?.let { createPreviewPokemonItem(it, difficulty) }
                        ?: ItemStack(Items.GREEN_STAINED_GLASS_PANE).apply {
                            setCustomName(Text.literal("No Preview Available").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) })
                        }
                }
            }
            is CobbleHunts.SoloHuntDisplayState.Locked -> {
                ItemStack(Items.BARRIER).apply {
                    setCustomName(Text.literal("Locked").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.RED) })
                }
            }
        }
    }

    // --- Helpers (Requirements, Colors, Loot) ---

    fun addHuntRequirements(lore: MutableList<Text>, instance: HuntInstance) {
        val entry = instance.entry
        if (entry.form != null) {
            lore.add(Text.literal("- Form: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal(entry.form.replaceFirstChar { it.titlecase() }).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.WHITE) }))
        }
        if (entry.aspects.contains("shiny")) {
            lore.add(Text.literal("- Shiny").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GOLD) })
        }
        if (instance.requiredGender != null) {
            val genderText = instance.requiredGender.replaceFirstChar { it.titlecase() }
            lore.add(Text.literal("- Gender: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal(genderText).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(getGenderColor(genderText)) }))
        }
        if (instance.requiredNature != null) {
            val natureText = instance.requiredNature.replaceFirstChar { it.titlecase() }
            lore.add(Text.literal("- Nature: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal(natureText).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GREEN) }))
        }
        if (instance.requiredIVs.isNotEmpty()) {
            val ivsText = instance.requiredIVs.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
            lore.add(Text.literal("- IVs above 20: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal(ivsText).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GOLD) }))
        }
    }

    fun getGenderColor(gender: String): Formatting {
        return when (gender.lowercase()) {
            "male" -> Formatting.BLUE
            "female" -> Formatting.LIGHT_PURPLE
            "genderless" -> Formatting.WHITE
            else -> Formatting.WHITE
        }
    }

    fun createLootRewardDisplay(reward: Any, ops: DynamicOps<JsonElement>): ItemStack {
        return when (reward) {
            is com.cobblehunts.utils.ItemReward -> {
                val item = reward.serializableItemStack.toItemStack(ops)
                val lore = listOf(Text.literal("Chance: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("%.1f%%".format(reward.chance * 100)).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.RED) }))
                CustomGui.setItemLore(item, lore)
                item
            }
            is com.cobblehunts.utils.CommandReward -> {
                val displayItem = reward.serializableItemStack?.toItemStack(ops)
                    ?: ItemStack(Items.COMMAND_BLOCK).apply {
                        setCustomName(Text.literal("Command Reward").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GOLD) })
                    }
                val lore = listOf(Text.literal("Chance: ").setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("%.1f%%".format(reward.chance * 100)).setStyle(Style.EMPTY.withItalic(false)).styled { it.withColor(Formatting.RED) }))
                CustomGui.setItemLore(displayItem, lore)
                displayItem
            }
            else -> createFillerPane()
        }
    }
}