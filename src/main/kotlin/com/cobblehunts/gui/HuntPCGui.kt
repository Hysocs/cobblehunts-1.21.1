package com.cobblehunts.gui

import com.cobblehunts.CobbleHunts
import com.cobblehunts.utils.CatchingTracker
import com.cobblehunts.utils.HuntsConfig
import com.cobblehunts.utils.PcSelection
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.storage.pc.POKEMON_PER_BOX
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.Pokemon
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.UUID

object HuntPCGui {
    private val playerPages = mutableMapOf<UUID, Int>()
    private const val GUI_SIZE = 54
    private const val ITEMS_PER_PAGE = 30
    private const val PC_BOX_COLUMNS = 6

    private const val PREV_PAGE_SLOT = 45
    private const val BACK_SLOT = 49
    private const val NEXT_PAGE_SLOT = 53

    private object Textures {
        const val NEXT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU0MDNjYzdiYmFjNzM2NzBiZDU0M2Y2YjA5NTViYWU3YjhlOTEyM2Q4M2JkNzYwZjYyMDRjNWFmZDhiZTdlMSJ9fX0="
        const val PREVIOUS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTMzYWQ1YzIyZGIxNjQzNWRhYWQ2MTU5MGFiYTUxZDkzNzkxNDJkZDU1NmQ2YzQyMmE3MTEwY2EzYWJlYTUwIn19fQ=="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    fun open(player: ServerPlayerEntity, rarity: String, huntIndex: Int?, page: Int = 0) {
        playerPages[player.uuid] = page
        val title = "Select from PC"
        CustomGui.openGui(
            player,
            title,
            generateLayout(player, page, rarity, huntIndex),
            { context -> handleInteraction(context, player, rarity, huntIndex) },
            { playerPages.remove(player.uuid) }
        )
    }

    private fun refresh(player: ServerPlayerEntity, page: Int, rarity: String, huntIndex: Int?) {
        playerPages[player.uuid] = page
        CustomGui.refreshGui(player, generateLayout(player, page, rarity, huntIndex))
    }

    private fun generateLayout(player: ServerPlayerEntity, page: Int, rarity: String, huntIndex: Int?): List<ItemStack> {
        val layout = MutableList(GUI_SIZE) { ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) } }
        val pc = Cobblemon.storage.getPC(player)
        val activeHunt = TurnInGui.getActiveHunt(player, rarity, huntIndex)
        val huntStartTime = activeHunt?.startTime ?: 0L

        // Robust box retrieval
        val box = pc.boxes.getOrNull(page)

        for (pcSlot in 0 until POKEMON_PER_BOX) {
            val guiSlot = (pcSlot / PC_BOX_COLUMNS) * 9 + (pcSlot % PC_BOX_COLUMNS)

            val pokemon = box?.get(pcSlot)

            if (pokemon != null) {
                val item = PokemonItem.from(pokemon)
                val reasons = mutableListOf<String>()

                if (activeHunt != null) {
                    reasons.addAll(TurnInGui.getAttributeMismatchReasons(pokemon, activeHunt))
                    if (HuntsConfig.config.onlyAllowTurnInIfCapturedAfterHuntStarted) {
                        val captureTime = CatchingTracker.getCaptureTime(pokemon.uuid)
                        if (captureTime == null || captureTime < huntStartTime) {
                            reasons.add("Captured before hunt started")
                        }
                    }
                } else {
                    reasons.add("No active hunt found")
                }

                val isEligible = reasons.isEmpty()

                // Name is always white
                item.setCustomName(Text.literal(pokemon.species.name).styled { it.withColor(Formatting.WHITE) })

                val lore = mutableListOf<MutableText>()
                lore.addAll(TurnInGui.getStatsLore(pokemon))
                lore.add(Text.literal(" "))

                if (isEligible) {
                    lore.add(Text.literal("Click to select").styled { it.withColor(Formatting.GREEN).withBold(true) })
                } else {
                    lore.add(Text.literal("Not Eligible:").styled { it.withColor(Formatting.RED).withBold(true) })
                    reasons.forEach { reason ->
                        val textToShow = if (reason.startsWith("FORM_MISMATCH::")) {
                            try {
                                val parts = reason.split("::")
                                "Form: ${parts[2].substringAfter("Actual:")}"
                            } catch (e: Exception) { reason }
                        } else {
                            reason
                        }
                        lore.add(Text.literal("- $textToShow").styled { it.withColor(Formatting.RED) })
                    }
                }

                CustomGui.setItemLore(item, lore)
                layout[guiSlot] = item
            } else {
                layout[guiSlot] = ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
            }
        }


        val totalPages = pc.boxes.size
        val pageInfoLore = listOf(
            Text.literal("Current Page: ${page + 1} / $totalPages").styled { it.withColor(Formatting.GRAY) }
        )

        if (page > 0) {
            layout[PREV_PAGE_SLOT] = CustomGui.createPlayerHeadButton(
                "Prev", Text.literal("Previous Page"), pageInfoLore, Textures.PREVIOUS
            )
        }

        layout[BACK_SLOT] = CustomGui.createPlayerHeadButton(
            "Back", Text.literal("Back to Turn In"), emptyList(), Textures.BACK
        )

        if (page < totalPages - 1) {
            layout[NEXT_PAGE_SLOT] = CustomGui.createPlayerHeadButton(
                "Next", Text.literal("Next Page"), pageInfoLore, Textures.NEXT
            )
        }

        return layout
    }

    private fun handleInteraction(context: InteractionContext, player: ServerPlayerEntity, rarity: String, huntIndex: Int?) {
        val pc = Cobblemon.storage.getPC(player)
        val currentPage = playerPages.getOrDefault(player.uuid, 0)
        val activeHunt = TurnInGui.getActiveHunt(player, rarity, huntIndex)

        when (context.slotIndex) {
            PREV_PAGE_SLOT -> if (currentPage > 0) refresh(player, currentPage - 1, rarity, huntIndex)
            NEXT_PAGE_SLOT -> if (currentPage < pc.boxes.size - 1) refresh(player, currentPage + 1, rarity, huntIndex)

            BACK_SLOT -> TurnInGui.openTurnInGui(player, rarity, huntIndex)

            else -> {
                val row = context.slotIndex / 9
                val col = context.slotIndex % 9
                if (col < PC_BOX_COLUMNS && row < 5) {
                    val pcSlot = row * PC_BOX_COLUMNS + col
                    val pokemon = pc.boxes.getOrNull(currentPage)?.get(pcSlot)

                    if (pokemon != null && activeHunt != null) {
                        val reasons = TurnInGui.getAttributeMismatchReasons(pokemon, activeHunt).toMutableList()

                        if (HuntsConfig.config.onlyAllowTurnInIfCapturedAfterHuntStarted) {
                            val captureTime = CatchingTracker.getCaptureTime(pokemon.uuid)
                            if (captureTime == null || captureTime < (activeHunt.startTime ?: 0L)) {
                                reasons.add("Time")
                            }
                        }

                        if (reasons.isEmpty()) {
                            val selection = PcSelection(currentPage, pcSlot, pokemon.uuid)
                            TurnInGui.openTurnInGui(player, rarity, huntIndex, selection)
                        }
                    }
                }
            }
        }
    }
}