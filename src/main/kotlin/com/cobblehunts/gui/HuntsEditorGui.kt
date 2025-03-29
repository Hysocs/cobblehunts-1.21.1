package com.cobblehunts.gui

import com.cobblehunts.utils.HuntsConfig
import com.cobblehunts.utils.HuntPokemonEntry
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeatures
import com.cobblemon.mod.common.api.pokemon.feature.ChoiceSpeciesFeatureProvider
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.everlastingutils.gui.*
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ClickType
import net.minecraft.util.Formatting
import org.joml.Vector4f
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

enum class SortMethod {
    ALPHABETICAL, TYPE, SELECTED, SEARCH
}

object HuntsEditorMainGui {
    private object Slots {
        const val GLOBAL = 21
        const val SOLO = 23
        const val SETTINGS = 40
    }

    private object Textures {
        const val GLOBAL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODc5ZTU0Y2JlODc4NjdkMTRiMmZiZGYzZjE4NzA4OTQzNTIwNDhkZmVjZDk2Mjg0NmRlYTg5M2IyMTU0Yzg1In19fQ=="
        const val SOLO = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2MxYjJmNTkyY2ZjOGQzNzJkY2Y1ZmQ0NGVlZDY5ZGRkYzY0NjAxZDc4NDZkNzI2MTlmNzA1MTFkODA0M2E4OSJ9fX0="
        const val SETTINGS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGI0ODk4YzE0NDI4ZGQyZTQwMTFkOWJlMzc2MGVjNmJhYjUyMWFlNTY1MWY2ZTIwYWQ1MzQxYTBmNWFmY2UyOCJ9fX0="
    }

    fun openGui(player: ServerPlayerEntity) {
        CustomGui.openGui(
            player,
            "Hunts Editor",
            generateMainLayout(),
            { context -> handleMainInteraction(context, player) },
            { /* No cleanup needed */ }
        )
    }

    private fun generateMainLayout(): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }
        layout[Slots.GLOBAL] = CustomGui.createPlayerHeadButton(
            textureName = "GlobalHunts",
            title = Text.literal("Global Hunts").styled { it.withColor(Formatting.GREEN) },
            lore = listOf(
                Text.literal("Left-click: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("Edit Pokémon").styled { it.withColor(Formatting.YELLOW) }),
                Text.literal("Right-click: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("Edit Loot Pool").styled { it.withColor(Formatting.YELLOW) })
            ),
            textureValue = Textures.GLOBAL
        )
        layout[Slots.SOLO] = CustomGui.createPlayerHeadButton(
            textureName = "SoloHunts",
            title = Text.literal("Solo Hunts").styled { it.withColor(Formatting.AQUA) },
            lore = listOf(
                Text.literal("Click to edit tiers").styled { it.withColor(Formatting.YELLOW) }
            ),
            textureValue = Textures.SOLO
        )
        layout[Slots.SETTINGS] = CustomGui.createPlayerHeadButton(
            textureName = "Settings",
            title = Text.literal("Hunts Global Settings").styled { it.withColor(Formatting.YELLOW) },
            lore = listOf(
                Text.literal("Click to edit global timers").styled { it.withColor(Formatting.YELLOW) }
            ),
            textureValue = Textures.SETTINGS
        )
        return layout
    }

    private fun handleMainInteraction(context: InteractionContext, player: ServerPlayerEntity) {
        when (context.slotIndex) {
            Slots.GLOBAL -> {
                if (context.clickType == ClickType.LEFT) {
                    HuntsPokemonSelectionGui.openGui(player, "global", "global")
                } else if (context.clickType == ClickType.RIGHT) {
                    LootPoolSelectionGui.openGui(player, "global", "global")
                }
            }
            Slots.SOLO -> HuntsTierSelectionGui.openGui(player, "solo")
            Slots.SETTINGS -> HuntsGlobalSettingsGui.openGui(player)
        }
    }
}

object HuntsTierSelectionGui {
    private object Slots {
        const val EASY = 21
        const val MEDIUM = 22
        const val HARD = 23
        const val BACK = 49
    }

    private object Textures {
        const val EASY = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2MxYjJmNTkyY2ZjOGQzNzJkY2Y1ZmQ0NGVlZDY5ZGRkYzY0NjAxZDc4NDZkNzI2MTlmNzA1MTFkODA0M2E4OSJ9fX0="
        const val MEDIUM = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU0ZWJlZmVmYThjYjNjNTg2MGFjODQxMjY1OWUxMjNiYTE1NGQ0YjcwOTZjM2IxMjNjMGQxZmNhNjNjOTc5OSJ9fX0="
        const val HARD = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjU0NGIwOWQ1NTljZWJiNDA2YjcyMjcwMGYzYmQzN2NiMTZkMjhkMmY2ODE0YjU4M2M2NDI3MDA4NGJjNjhiMyJ9fX0="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    fun openGui(player: ServerPlayerEntity, type: String) {
        CustomGui.openGui(
            player,
            "Select Tier for ${type.replaceFirstChar { it.titlecase() }} Hunts",
            generateTierLayout(),
            { context -> handleTierInteraction(context, player, type) },
            { /* No cleanup needed */ }
        )
    }

    private fun generateTierLayout(): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }
        layout[Slots.EASY] = CustomGui.createPlayerHeadButton(
            textureName = "Easy",
            title = Text.literal("Easy Tier").styled { it.withColor(Formatting.GREEN) },
            lore = listOf(
                Text.literal("Left-click: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("Edit Pokémon").styled { it.withColor(Formatting.YELLOW) }),
                Text.literal("Right-click: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("Edit Loot Pool").styled { it.withColor(Formatting.YELLOW) })
            ),
            textureValue = Textures.EASY
        )
        layout[Slots.MEDIUM] = CustomGui.createPlayerHeadButton(
            textureName = "Medium",
            title = Text.literal("Medium Tier").styled { it.withColor(Formatting.BLUE) },
            lore = listOf(
                Text.literal("Left-click: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("Edit Pokémon").styled { it.withColor(Formatting.YELLOW) }),
                Text.literal("Right-click: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("Edit Loot Pool").styled { it.withColor(Formatting.YELLOW) })
            ),
            textureValue = Textures.MEDIUM
        )
        layout[Slots.HARD] = CustomGui.createPlayerHeadButton(
            textureName = "Hard",
            title = Text.literal("Hard Tier").styled { it.withColor(Formatting.GOLD) },
            lore = listOf(
                Text.literal("Left-click: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("Edit Pokémon").styled { it.withColor(Formatting.YELLOW) }),
                Text.literal("Right-click: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("Edit Loot Pool").styled { it.withColor(Formatting.YELLOW) })
            ),
            textureValue = Textures.HARD
        )
        layout[Slots.BACK] = CustomGui.createPlayerHeadButton(
            textureName = "Back",
            title = Text.literal("Back").styled { it.withColor(Formatting.YELLOW) },
            lore = listOf(
                Text.literal("Return to main menu").styled { it.withColor(Formatting.GRAY) }
            ),
            textureValue = Textures.BACK
        )
        return layout
    }

    private fun handleTierInteraction(context: InteractionContext, player: ServerPlayerEntity, type: String) {
        when (context.slotIndex) {
            Slots.EASY, Slots.MEDIUM, Slots.HARD -> {
                val tier = when (context.slotIndex) {
                    Slots.EASY -> "easy"
                    Slots.MEDIUM -> "medium"
                    Slots.HARD -> "hard"
                    else -> return
                }
                if (context.clickType == ClickType.LEFT) {
                    HuntsPokemonSelectionGui.openGui(player, type, tier)
                } else if (context.clickType == ClickType.RIGHT) {
                    LootPoolSelectionGui.openGui(player, type, tier)
                }
            }
            Slots.BACK -> HuntsEditorMainGui.openGui(player)
        }
    }
}

object HuntsPokemonSelectionGui {
    private val playerPages = ConcurrentHashMap<ServerPlayerEntity, Int>()
    private val playerSortMethods = ConcurrentHashMap<ServerPlayerEntity, SortMethod>()
    private val playerSearchTerms = ConcurrentHashMap<ServerPlayerEntity, String>()
    private val playerPreviousSortMethods = ConcurrentHashMap<ServerPlayerEntity, SortMethod>()
    private val playerTypes = ConcurrentHashMap<ServerPlayerEntity, String>()
    private val playerTiers = ConcurrentHashMap<ServerPlayerEntity, String>()

    private object Slots {
        const val BACK = 49
        const val PREV_PAGE = 45
        const val NEXT_PAGE = 53
        const val SORT_METHOD = 4
    }

    private object Textures {
        const val PREV_PAGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTMzYWQ1YzIyZGIxNjQzNWRhYWQ2MTU5MGFiYTUxZDkzNzkxNDJkZDU1NmQ2YzQyMmE3MTEwY2EzYWJlYTUwIn19fQ=="
        const val NEXT_PAGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU0MDNjYzdiYmFjNzM2NzBiZDU0M2Y2YjA5NTViYWU3YjhlOTEyM2Q4M2JkNzYwZjYyMDRjNWFmZDhiZTdlMSJ9fX0="
        const val SORT_METHOD = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDExZTk5N2QwZjE4YjZhMjk1YTRiZjBiZDdhYWZjZjE2ZWRhZDgzMjEwN2QyZmYyNzFjNzgxZDU2ZGQ5MWE3MyJ9fX0="
        const val CANCEL_SEARCH = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    data class SpeciesFormVariant(val species: Species, val form: FormData, val additionalAspects: Set<String>) {
        fun toKey(): String = "${species.showdownId()}_${if (form.name.equals("Standard", ignoreCase = true)) "normal" else form.name.lowercase()}_${additionalAspects.map { it.lowercase() }.sorted().joinToString(",")}"
    }

    fun openGui(player: ServerPlayerEntity, type: String, tier: String) {
        playerTypes[player] = type
        playerTiers[player] = tier
        playerPages[player] = 0
        playerSortMethods[player] = SortMethod.ALPHABETICAL
        playerSearchTerms[player] = ""
        val title = if (type == "global") "Select Pokémon for Global Hunts" else "Select Pokémon for ${type.replaceFirstChar { it.titlecase() }} ${tier.replaceFirstChar { it.titlecase() }}"
        CustomGui.openGui(
            player,
            title,
            generateSelectionLayout(type, tier, 0, SortMethod.ALPHABETICAL, ""),
            { context -> handleInteraction(context, player) },
            { _ -> cleanupPlayerData(player) }
        )
    }

    private fun generateSelectionLayout(type: String, tier: String, page: Int, sortMethod: SortMethod, searchTerm: String): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }
        layout[Slots.SORT_METHOD] = createSortMethodButton(sortMethod, searchTerm)
        val monsPerPage = 36
        val selectedPokemon = HuntsConfig.getPokemonList(type, tier)
        val variantsList = getVariantsForPage(selectedPokemon, page, sortMethod, searchTerm, monsPerPage)
        for (i in variantsList.indices) {
            val slot = i + 9
            val variant = variantsList[i]
            val entry = selectedPokemon.find {
                it.species == variant.species.showdownId() &&
                        it.form == (if (variant.form.name == "Standard") null else variant.form.name) &&
                        it.aspects == variant.additionalAspects
            }
            layout[slot] = if (entry != null) createSelectedPokemonItem(tier, variant, entry) else createUnselectedPokemonItem(variant)
        }
        val totalVariants = getTotalVariantsCount(selectedPokemon, sortMethod, searchTerm)
        if (page > 0) {
            layout[Slots.PREV_PAGE] = CustomGui.createPlayerHeadButton(
                "Previous",
                Text.literal("Previous").styled { it.withColor(Formatting.YELLOW) },
                listOf(
                    Text.literal("Go to previous page").styled { it.withColor(Formatting.GRAY) }
                ),
                Textures.PREV_PAGE
            )
        }
        layout[Slots.BACK] = CustomGui.createPlayerHeadButton(
            "Back",
            Text.literal("Back").styled { it.withColor(Formatting.RED) },
            lore = listOf(
                Text.literal("Return to previous menu").styled { it.withColor(Formatting.GRAY) }
            ),
            textureValue = Textures.BACK
        )
        if ((page + 1) * monsPerPage < totalVariants) {
            layout[Slots.NEXT_PAGE] = CustomGui.createPlayerHeadButton(
                "Next",
                Text.literal("Next").styled { it.withColor(Formatting.GREEN) },
                lore = listOf(
                    Text.literal("Go to next page").styled { it.withColor(Formatting.GRAY) }
                ),
                textureValue = Textures.NEXT_PAGE
            )
        }
        return layout
    }

    private fun handleInteraction(context: InteractionContext, player: ServerPlayerEntity) {
        val type = playerTypes[player] ?: return
        val tier = playerTiers[player] ?: return
        var page = playerPages[player] ?: 0
        val sortMethod = playerSortMethods[player] ?: SortMethod.ALPHABETICAL
        val searchTerm = playerSearchTerms[player] ?: ""
        when (context.slotIndex) {
            Slots.PREV_PAGE -> if (page > 0) {
                page--
                playerPages[player] = page
                CustomGui.refreshGui(player, generateSelectionLayout(type, tier, page, sortMethod, searchTerm))
            }
            Slots.BACK -> if (type == "global") HuntsEditorMainGui.openGui(player) else HuntsTierSelectionGui.openGui(player, type)
            Slots.SORT_METHOD -> when (context.clickType) {
                ClickType.LEFT -> {
                    val newSortMethod = when (sortMethod) {
                        SortMethod.ALPHABETICAL -> SortMethod.TYPE
                        SortMethod.TYPE -> SortMethod.SELECTED
                        SortMethod.SELECTED -> SortMethod.ALPHABETICAL
                        SortMethod.SEARCH -> SortMethod.ALPHABETICAL
                    }
                    playerSortMethods[player] = newSortMethod
                    if (newSortMethod != SortMethod.SEARCH) playerSearchTerms[player] = ""
                    CustomGui.refreshGui(player, generateSelectionLayout(type, tier, page, newSortMethod, ""))
                }
                ClickType.RIGHT -> {
                    playerPreviousSortMethods[player] = sortMethod
                    openSearchGui(player, type, tier, sortMethod, page)
                }
            }
            Slots.NEXT_PAGE -> {
                val totalVariants = getTotalVariantsCount(HuntsConfig.getPokemonList(type, tier), sortMethod, searchTerm)
                if ((page + 1) * 36 < totalVariants) {
                    page++
                    playerPages[player] = page
                    CustomGui.refreshGui(player, generateSelectionLayout(type, tier, page, sortMethod, searchTerm))
                }
            }
            in 9..44 -> {
                val selectedPokemon = HuntsConfig.getPokemonList(type, tier)
                val variantsList = getVariantsForPage(selectedPokemon, page, sortMethod, searchTerm, 36)
                val index = context.slotIndex - 9
                if (index < variantsList.size) {
                    val variant = variantsList[index]
                    val entry = HuntPokemonEntry(
                        species = variant.species.showdownId(),
                        form = if (variant.form.name == "Standard") null else variant.form.name,
                        aspects = variant.additionalAspects,
                        chance = 1.0
                    )
                    if (context.clickType == ClickType.RIGHT) {
                        val existing = selectedPokemon.find { it.species == entry.species && it.form == entry.form && it.aspects == entry.aspects }
                        HuntsPokemonEditGui.openGui(player, type, tier, existing ?: entry)
                    } else if (context.clickType == ClickType.LEFT) {
                        if (selectedPokemon.any { it.species == entry.species && it.form == entry.form && it.aspects == entry.aspects }) {
                            selectedPokemon.removeIf { it.species == entry.species && it.form == entry.form && it.aspects == entry.aspects }
                            player.sendMessage(
                                Text.literal("Removed ").styled { it.withColor(Formatting.GRAY) }
                                    .append(Text.literal(variant.species.name).styled { it.withColor(Formatting.WHITE) })
                                    .append(Text.literal(" from $type hunts").styled { it.withColor(Formatting.GRAY) }),
                                false
                            )
                        } else {
                            selectedPokemon.add(entry)
                            player.sendMessage(
                                Text.literal("Added ").styled { it.withColor(Formatting.GRAY) }
                                    .append(Text.literal(variant.species.name).styled { it.withColor(Formatting.WHITE) })
                                    .append(Text.literal(" to $type hunts").styled { it.withColor(Formatting.GRAY) }),
                                false
                            )
                        }
                        HuntsConfig.saveConfig()
                        CustomGui.refreshGui(player, generateSelectionLayout(type, tier, page, sortMethod, searchTerm))
                    }
                }
            }
        }
    }

    private fun cleanupPlayerData(player: ServerPlayerEntity) {
        playerPages.remove(player)
        playerSortMethods.remove(player)
        playerSearchTerms.remove(player)
        playerPreviousSortMethods.remove(player)
        playerTypes.remove(player)
        playerTiers.remove(player)
    }

    private fun createSelectedPokemonItem(tier: String, variant: SpeciesFormVariant, entry: HuntPokemonEntry): ItemStack {
        val aspectsString = variant.additionalAspects.joinToString(" ") { "aspect=$it" }
        val formString = if (variant.form.name != "Standard") " form=${variant.form.name}" else ""
        val properties = PokemonProperties.parse("${variant.species.showdownId()}$formString $aspectsString")
        val pokemon = properties.create()
        val item = PokemonItem.from(pokemon)
        CustomGui.addEnchantmentGlint(item)
        val formDisplay = if (variant.form.name != "Standard") variant.form.name else "Normal"
        val aspectsDisplay = if (variant.additionalAspects.isNotEmpty()) ", ${variant.additionalAspects.joinToString(", ")}" else ""
        val displayName = "${variant.species.name} ($formDisplay$aspectsDisplay)"
        item.setCustomName(Text.literal(displayName).styled { it.withColor(Formatting.WHITE) })
        val lore = mutableListOf<Text>()
        lore.add(
            Text.literal("Chance: ").styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal("%.1f%%".format(entry.chance * 100)).styled { it.withColor(Formatting.AQUA) })
        )
        if (entry.gender != null) {
            lore.add(
                Text.literal("Gender: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal(entry.gender!!.replaceFirstChar { it.titlecase() }).styled { it.withColor(Formatting.LIGHT_PURPLE) })
            )
        }
        if (entry.ivRange != null) {
            lore.add(
                Text.literal("IVs above 20: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal(entry.ivRange!!).styled { it.withColor(Formatting.GOLD) })
            )
        }
        lore.add(Text.literal("Left-click to toggle").styled { it.withColor(Formatting.YELLOW) })
        lore.add(Text.literal("Right-click to edit").styled { it.withColor(Formatting.YELLOW) })
        CustomGui.setItemLore(item, lore)
        return item
    }

    private fun createUnselectedPokemonItem(variant: SpeciesFormVariant): ItemStack {
        val aspectsString = variant.additionalAspects.joinToString(" ") { "aspect=$it" }
        val formString = if (variant.form.name != "Standard") " form=${variant.form.name}" else ""
        val properties = PokemonProperties.parse("${variant.species.showdownId()}$formString $aspectsString")
        val pokemon = properties.create()
        val item = PokemonItem.from(pokemon, tint = Vector4f(0.3f, 0.3f, 0.3f, 1f))
        val formDisplay = if (variant.form.name != "Standard") variant.form.name else "Normal"
        val aspectsDisplay = if (variant.additionalAspects.isNotEmpty()) ", ${variant.additionalAspects.joinToString(", ")}" else ""
        val displayName = "${variant.species.name} ($formDisplay$aspectsDisplay)"
        item.setCustomName(Text.literal(displayName).styled { it.withColor(Formatting.GRAY) })
        CustomGui.setItemLore(item, listOf(
            Text.literal("Left-click to toggle").styled { it.withColor(Formatting.YELLOW) }
        ))
        return item
    }

    private fun getAllVariants(selectedPokemon: List<HuntPokemonEntry>, sortMethod: SortMethod, searchTerm: String): List<SpeciesFormVariant> {
        val speciesList = when (sortMethod) {
            SortMethod.ALPHABETICAL -> PokemonSpecies.species.sortedBy { it.name }
            SortMethod.TYPE -> PokemonSpecies.species.sortedBy { it.primaryType.name }
            SortMethod.SEARCH -> if (searchTerm.isBlank()) PokemonSpecies.species.sortedBy { it.name }
            else PokemonSpecies.species.filter { it.name.lowercase().contains(searchTerm.lowercase()) }.sortedBy { it.name }
            SortMethod.SELECTED -> PokemonSpecies.species.filter { species ->
                selectedPokemon.any { it.species == species.showdownId() }
            }.sortedBy { it.name }
        }
        return speciesList.flatMap { species ->
            val forms = species.forms.ifEmpty { listOf(species.standardForm) }
            forms.flatMap { form ->
                val baseVariants = listOf(
                    SpeciesFormVariant(species, form, emptySet()),
                    SpeciesFormVariant(species, form, setOf("shiny"))
                )
                val additionalAspectSets = getAdditionalAspectSets(species)
                (baseVariants + additionalAspectSets.map { SpeciesFormVariant(species, form, it) }).distinctBy { it.toKey() }
            }
        }.let { variants ->
            if (sortMethod == SortMethod.SELECTED) variants.filter { isVariantSelected(it, selectedPokemon) } else variants
        }
    }

    private fun getVariantsForPage(selectedPokemon: List<HuntPokemonEntry>, page: Int, sortMethod: SortMethod, searchTerm: String, pageSize: Int): List<SpeciesFormVariant> {
        val allVariants = getAllVariants(selectedPokemon, sortMethod, searchTerm)
        val startIndex = page * pageSize
        val endIndex = min(startIndex + pageSize, allVariants.size)
        return if (startIndex < allVariants.size) allVariants.subList(startIndex, endIndex) else emptyList()
    }

    private fun getTotalVariantsCount(selectedPokemon: List<HuntPokemonEntry>, sortMethod: SortMethod, searchTerm: String): Int {
        return getAllVariants(selectedPokemon, sortMethod, searchTerm).size
    }

    private fun isVariantSelected(variant: SpeciesFormVariant, selectedPokemon: List<HuntPokemonEntry>): Boolean {
        val entryForm = if (variant.form.name == "Standard") null else variant.form.name
        return selectedPokemon.any { it.species == variant.species.showdownId() && it.form == entryForm && it.aspects == variant.additionalAspects }
    }

    private fun getAdditionalAspectSets(species: Species): List<Set<String>> {
        val aspectSets = mutableListOf<Set<String>>()
        val speciesSpecificAspects = mutableSetOf<String>()

        species.forms.forEach { form ->
            form.aspects.forEach { speciesSpecificAspects.add(it) }
        }

        val speciesFeatures = SpeciesFeatures.getFeaturesFor(species)
        speciesFeatures.filterIsInstance<ChoiceSpeciesFeatureProvider>().forEach { provider ->
            provider.getAllAspects().forEach { speciesSpecificAspects.add(it) }
        }

        for (aspect in speciesSpecificAspects) {
            aspectSets.add(setOf(aspect))
            aspectSets.add(setOf(aspect, "shiny"))
        }

        return aspectSets.distinct()
    }

    private fun createSortMethodButton(sortMethod: SortMethod, searchTerm: String): ItemStack {
        val sortText = if (sortMethod == SortMethod.SEARCH) {
            Text.literal("Search: ").styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal(searchTerm).styled { it.withColor(Formatting.AQUA) })
        } else {
            Text.literal("Sort: ").styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal(sortMethod.name.lowercase().replaceFirstChar { it.uppercase() }).styled { it.withColor(Formatting.AQUA) })
        }
        val lore = if (sortMethod == SortMethod.SEARCH)
            listOf(
                Text.literal("Current Search: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("\"$searchTerm\"").styled { it.withColor(Formatting.AQUA) }),
                Text.literal("Left-click to cycle sort").styled { it.withColor(Formatting.YELLOW) },
                Text.literal("Right-click to search again").styled { it.withColor(Formatting.YELLOW) }
            )
        else
            listOf(
                Text.literal("Left-click to cycle sort").styled { it.withColor(Formatting.YELLOW) },
                Text.literal("Right-click to search").styled { it.withColor(Formatting.YELLOW) }
            )
        return CustomGui.createPlayerHeadButton("SortMethod", sortText, lore, Textures.SORT_METHOD)
    }

    private fun openSearchGui(player: ServerPlayerEntity, type: String, tier: String, previousSortMethod: SortMethod, currentPage: Int) {
        val cancelButton = CustomGui.createPlayerHeadButton(
            "Cancel",
            Text.literal("Cancel").styled { it.withColor(Formatting.RED) },
            listOf(
                Text.literal("Click to cancel").styled { it.withColor(Formatting.GRAY) }
            ),
            Textures.CANCEL_SEARCH
        )
        val blockedInput = ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
        val placeholderOutput = ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE)

        AnvilGuiManager.openAnvilGui(
            player = player,
            id = "hunts_search_${type}_$tier",
            title = "Search Pokémon",
            initialText = "",
            leftItem = cancelButton,
            rightItem = blockedInput,
            resultItem = placeholderOutput,
            onLeftClick = { _ ->
                player.sendMessage(
                    Text.literal("Search cancelled.").styled { it.withColor(Formatting.GRAY) },
                    false
                )
                player.closeHandledScreen()
                player.server.execute {
                    playerSortMethods[player] = previousSortMethod
                    playerSearchTerms[player] = ""
                    playerPages[player] = currentPage
                    playerTypes[player] = type
                    playerTiers[player] = tier
                    val title = if (type == "global") "Select Pokémon for Global Hunts" else "Select Pokémon for ${type.replaceFirstChar { it.titlecase() }} ${tier.replaceFirstChar { it.titlecase() }}"
                    CustomGui.openGui(
                        player,
                        title,
                        generateSelectionLayout(type, tier, currentPage, previousSortMethod, ""),
                        { context -> handleInteraction(context, player) },
                        { _ -> cleanupPlayerData(player) }
                    )
                }
            },
            onRightClick = null,
            onResultClick = { context ->
                if (context.handler.currentText.isNotBlank()) {
                    val searchTerm = context.handler.currentText.trim()
                    player.sendMessage(
                        Text.literal("Searching for: ").styled { it.withColor(Formatting.GREEN) }
                            .append(Text.literal("'$searchTerm'").styled { it.withColor(Formatting.AQUA) }),
                        false
                    )
                    player.closeHandledScreen()
                    player.server.execute {
                        playerSortMethods[player] = SortMethod.SEARCH
                        playerSearchTerms[player] = searchTerm
                        playerPages[player] = 0
                        playerTypes[player] = type
                        playerTiers[player] = tier
                        val title = if (type == "global") "Select Pokémon for Global Hunts" else "Select Pokémon for ${type.replaceFirstChar { it.titlecase() }} ${tier.replaceFirstChar { it.titlecase() }}"
                        CustomGui.openGui(
                            player,
                            title,
                            generateSelectionLayout(type, tier, 0, SortMethod.SEARCH, searchTerm),
                            { ctx -> handleInteraction(ctx, player) },
                            { _ -> cleanupPlayerData(player) }
                        )
                    }
                } else {
                    player.sendMessage(Text.literal("Please enter a search term.").styled { it.withColor(Formatting.RED) }, false)
                }
            },
            onTextChange = { text ->
                val handler = player.currentScreenHandler as? FullyModularAnvilScreenHandler
                if (text.isNotEmpty()) {
                    handler?.updateSlot(
                        2,
                        CustomGui.createPlayerHeadButton(
                            "Search",
                            Text.literal("Search: ").styled { it.withColor(Formatting.GREEN) }
                                .append(Text.literal(text).styled { it.withColor(Formatting.AQUA) }),
                            listOf(
                                Text.literal("Click to search").styled { it.withColor(Formatting.GRAY) }
                            ),
                            Textures.SORT_METHOD
                        )
                    )
                } else {
                    handler?.updateSlot(2, placeholderOutput)
                }
            },
            onClose = {
                if (player.currentScreenHandler !is FullyModularAnvilScreenHandler) {
                    cleanupPlayerData(player)
                }
            }
        )
        player.server.execute { (player.currentScreenHandler as? FullyModularAnvilScreenHandler)?.clearTextField() }
        player.sendMessage(
            Text.literal("Enter a Pokémon name to search...").styled { it.withColor(Formatting.GRAY) },
            false
        )
    }
}

object HuntsPokemonEditGui {
    private object Slots {
        const val POKEMON_DISPLAY = 4
        const val DECREASE_LARGE = 19
        const val DECREASE_MEDIUM = 20
        const val DECREASE_SMALL = 21
        const val CHANCE_DISPLAY = 22
        const val INCREASE_SMALL = 23
        const val INCREASE_MEDIUM = 24
        const val INCREASE_LARGE = 25
        const val TOGGLE_GENDER = 29
        const val TOGGLE_IV = 33
        const val BACK = 49
    }

    private object Textures {
        const val INCREASE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTU3YTViZGY0MmYxNTIxNzhkMTU0YmIyMjM3ZDlmZDM1NzcyYTdmMzJiY2ZkMzNiZWViOGVkYzQ4MjBiYSJ9fX0="
        const val DECREASE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTZhMDExZTYyNmI3MWNlYWQ5ODQxOTM1MTFlODJlNjVjMTM1OTU2NWYwYTJmY2QxMTg0ODcyZjg5ZDkwOGM2NSJ9fX0="
        const val TOGGLE_GENDER = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTZlNzRlMjlkYTQxZmNmMmZkYzVjMGI2NjVkYzgxMGI5NzY5Y2UxZmI3NTk5MGI0MTg1YzkxMzZiZmRiZWI0In19fQ=="
        const val TOGGLE_IV = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODc1MDkzMmM1OTFhNWZkNmIwNzYxYWQzMjRjYjYyZTA1YTMxNWVkYmEyNjQyYWQ5NmMxMzI5OTMxYjU2MmY3MyJ9fX0="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    fun openGui(player: ServerPlayerEntity, type: String, tier: String, entry: HuntPokemonEntry) {
        val currentList = HuntsConfig.getPokemonList(type, tier)
        if (!currentList.any { it.species == entry.species && it.form == entry.form && it.aspects == entry.aspects }) {
            currentList.add(entry)
            HuntsConfig.saveConfig()
            player.sendMessage(
                Text.literal("Added ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal(entry.species).styled { it.withColor(Formatting.WHITE) })
                    .append(Text.literal(" to $type hunts. Now editing.").styled { it.withColor(Formatting.GRAY) }),
                false
            )
        }

        CustomGui.openGui(
            player,
            "Edit ${if (type == "global") "Global" else tier.replaceFirstChar { it.titlecase() }} Entry",
            generateEditLayout(entry, type, tier),
            { context -> handleEditInteraction(context, player, type, tier, entry) },
            { /* No cleanup needed on close */ }
        )
    }

    private fun generateEditLayout(entry: HuntPokemonEntry, type: String, tier: String): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }
        layout[Slots.POKEMON_DISPLAY] = createPokemonItem(entry)
        layout[Slots.CHANCE_DISPLAY] = ItemStack(Items.PAPER).apply {
            setCustomName(
                Text.literal("Chance: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("%.1f%%".format(entry.chance * 100)).styled { it.withColor(Formatting.AQUA) })
            )
            CustomGui.setItemLore(this, listOf(Text.literal("Base chance for this Pokémon").styled { it.withColor(Formatting.DARK_GRAY) }))
        }
        layout[Slots.DECREASE_LARGE] = createChanceButton("Decrease Large", -1.0, -5.0, entry.chance)
        layout[Slots.DECREASE_MEDIUM] = createChanceButton("Decrease Medium", -0.5, -1.0, entry.chance)
        layout[Slots.DECREASE_SMALL] = createChanceButton("Decrease Small", -0.1, -0.5, entry.chance)
        layout[Slots.INCREASE_SMALL] = createChanceButton("Increase Small", 0.1, 0.5, entry.chance)
        layout[Slots.INCREASE_MEDIUM] = createChanceButton("Increase Medium", 0.5, 1.0, entry.chance)
        layout[Slots.INCREASE_LARGE] = createChanceButton("Increase Large", 1.0, 5.0, entry.chance)

        if (type == "global" || tier in listOf("medium", "hard")) {
            val genderDisplay = entry.gender?.replaceFirstChar { it.titlecase() } ?: "Any"
            val nextGender = when (entry.gender?.lowercase()) {
                "male" -> "Female"
                "female" -> "Any"
                else -> "Male"
            }
            layout[Slots.TOGGLE_GENDER] = CustomGui.createPlayerHeadButton(
                "ToggleGender",
                Text.literal("Gender: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal(genderDisplay).styled { it.withColor(Formatting.LIGHT_PURPLE) }),
                listOf(
                    Text.literal("Click to cycle gender (Any -> Male -> Female)").styled { it.withColor(Formatting.YELLOW) },
                    Text.literal("Next: ").styled { it.withColor(Formatting.GRAY) }
                        .append(Text.literal(nextGender).styled { it.withColor(Formatting.LIGHT_PURPLE) })
                ),
                Textures.TOGGLE_GENDER
            )
        }

        if (type == "global" || tier == "hard") {
            val ivDisplay = entry.ivRange?.let { "At least $it IVs >= 20" } ?: "Any"
            val nextIv = when (entry.ivRange) {
                "1" -> "2"
                "2" -> "3"
                "3" -> "Any"
                else -> "1"
            }
            layout[Slots.TOGGLE_IV] = CustomGui.createPlayerHeadButton(
                "ToggleIV",
                Text.literal("Min Perfect IVs: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal(ivDisplay).styled { it.withColor(Formatting.GOLD) }),
                listOf(
                    Text.literal("Click to cycle minimum perfect IVs (Any -> 1 -> 2 -> 3)").styled { it.withColor(Formatting.YELLOW) },
                    Text.literal("Next: ").styled { it.withColor(Formatting.GRAY) }
                        .append(Text.literal(if (nextIv == "Any") "Any" else "At least $nextIv").styled { it.withColor(Formatting.GOLD) })
                ),
                Textures.TOGGLE_IV
            )
        }

        layout[Slots.BACK] = CustomGui.createPlayerHeadButton(
            "Back",
            Text.literal("Back").styled { it.withColor(Formatting.YELLOW) },
            listOf(
                Text.literal("Return to selection").styled { it.withColor(Formatting.GRAY) }
            ),
            Textures.BACK
        )
        return layout
    }

    private fun handleEditInteraction(context: InteractionContext, player: ServerPlayerEntity, type: String, tier: String, entry: HuntPokemonEntry) {
        val configEntry = HuntsConfig.getPokemonList(type, tier).find {
            it.species == entry.species && it.form == entry.form && it.aspects == entry.aspects
        } ?: run {
            player.sendMessage(Text.literal("Error: Could not find Pokémon entry to edit.").styled { it.withColor(Formatting.RED) }, false)
            HuntsPokemonSelectionGui.openGui(player, type, tier)
            return
        }

        val deltaPercent = when (context.slotIndex) {
            Slots.DECREASE_LARGE -> if (context.clickType == ClickType.LEFT) -1.0 else -5.0
            Slots.DECREASE_MEDIUM -> if (context.clickType == ClickType.LEFT) -0.5 else -1.0
            Slots.DECREASE_SMALL -> if (context.clickType == ClickType.LEFT) -0.1 else -0.5
            Slots.INCREASE_SMALL -> if (context.clickType == ClickType.LEFT) 0.1 else 0.5
            Slots.INCREASE_MEDIUM -> if (context.clickType == ClickType.LEFT) 0.5 else 1.0
            Slots.INCREASE_LARGE -> if (context.clickType == ClickType.LEFT) 1.0 else 5.0
            else -> 0.0
        }

        if (deltaPercent != 0.0) {
            val deltaDecimal = deltaPercent / 100.0
            val currentChanceBd = configEntry.chance.toBigDecimal()
            val deltaBd = deltaDecimal.toBigDecimal()
            val newChance = (currentChanceBd + deltaBd).coerceIn(BigDecimal.ZERO, BigDecimal.ONE).toDouble()
            configEntry.chance = String.format("%.4f", newChance).toDouble()
            HuntsConfig.saveConfig()
            CustomGui.refreshGui(player, generateEditLayout(configEntry, type, tier))
            player.sendMessage(
                Text.literal("Chance set to ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("%.1f%%".format(configEntry.chance * 100)).styled { it.withColor(Formatting.AQUA) }),
                true
            )
            return
        }

        when (context.slotIndex) {
            Slots.TOGGLE_GENDER -> if (type == "global" || tier in listOf("medium", "hard")) {
                configEntry.gender = when (configEntry.gender?.lowercase()) {
                    "male" -> "Female"
                    "female" -> null
                    else -> "Male"
                }
                HuntsConfig.saveConfig()
                CustomGui.refreshGui(player, generateEditLayout(configEntry, type, tier))
                val genderDisplay = configEntry.gender ?: "Any"
                player.sendMessage(
                    Text.literal("Gender set to ").styled { it.withColor(Formatting.GRAY) }
                        .append(Text.literal(genderDisplay).styled { it.withColor(Formatting.LIGHT_PURPLE) }),
                    true
                )
            }
            Slots.TOGGLE_IV -> if (type == "global" || tier == "hard") {
                configEntry.ivRange = when (configEntry.ivRange) {
                    "1" -> "2"
                    "2" -> "3"
                    "3" -> null
                    else -> "1"
                }
                HuntsConfig.saveConfig()
                CustomGui.refreshGui(player, generateEditLayout(configEntry, type, tier))
                val ivDisplay = configEntry.ivRange?.let { "At least $it" } ?: "Any"
                player.sendMessage(
                    Text.literal("Min Perfect IVs set to ").styled { it.withColor(Formatting.GRAY) }
                        .append(Text.literal(ivDisplay).styled { it.withColor(Formatting.GOLD) }),
                    true
                )
            }
            Slots.BACK -> HuntsPokemonSelectionGui.openGui(player, type, tier)
        }
    }

    private fun createPokemonItem(entry: HuntPokemonEntry): ItemStack {
        val propsParts = mutableListOf<String>()
        propsParts.add(entry.species)
        entry.form?.let { propsParts.add("form=$it") }
        entry.aspects.forEach { propsParts.add("aspect=$it") }

        val properties = PokemonProperties.parse(propsParts.joinToString(" "))
        val pokemon = properties.create()
        val item = PokemonItem.from(pokemon)

        val speciesName = PokemonSpecies.getByName(entry.species)?.name ?: entry.species.replaceFirstChar { it.titlecase() }
        val formDisplay = entry.form?.replaceFirstChar { it.titlecase() }
        val aspectsDisplay = entry.aspects.joinToString(", ") { it.replaceFirstChar { it.titlecase() } }

        var displayName = speciesName
        if (formDisplay != null && formDisplay != "Standard") displayName += " ($formDisplay)"
        if (aspectsDisplay.isNotEmpty()) displayName += ", $aspectsDisplay"

        item.setCustomName(Text.literal(displayName).styled { it.withColor(Formatting.WHITE) })

        if (entry.aspects.contains("shiny")) {
            CustomGui.addEnchantmentGlint(item)
        }

        CustomGui.setItemLore(item, listOf(Text.literal("Editing this Pokémon entry").styled { it.withColor(Formatting.DARK_GRAY) }))

        return item
    }

    private fun createChanceButton(name: String, leftDelta: Double, rightDelta: Double, currentChance: Double): ItemStack {
        val textureValue = if (leftDelta > 0) Textures.INCREASE else Textures.DECREASE
        val title = Text.literal(name).styled { it.withColor(if (leftDelta > 0) Formatting.GREEN else Formatting.RED) }
        val lore = listOf(
            Text.literal("Left-click: ").styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal("${if (leftDelta > 0) "+" else ""}${"%.1f".format(leftDelta)}%").styled { it.withColor(if (leftDelta > 0) Formatting.GREEN else Formatting.RED) }),
            Text.literal("Right-click: ").styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal("${if (rightDelta > 0) "+" else ""}${"%.1f".format(rightDelta)}%").styled { it.withColor(if (rightDelta > 0) Formatting.GREEN else Formatting.RED) }),
            Text.literal("Current: ").styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal("%.1f%%".format(currentChance * 100)).styled { it.withColor(Formatting.AQUA) })
        )
        return CustomGui.createPlayerHeadButton(name, title, lore, textureValue)
    }
}

enum class TimerMode {
    COOLDOWN, TIME_LIMIT
}

object HuntsGlobalSettingsGui {
    private val playerTimerModes = ConcurrentHashMap<ServerPlayerEntity, TimerMode>()

    private object Slots {
        const val TOGGLE_MODE = 4
        const val SOLO_EASY_DECREASE_10 = 11
        const val SOLO_EASY_DECREASE_1 = 12
        const val SOLO_EASY_DISPLAY = 13
        const val SOLO_EASY_INCREASE_1 = 14
        const val SOLO_EASY_INCREASE_10 = 15
        const val SOLO_MEDIUM_DECREASE_10 = 20
        const val SOLO_MEDIUM_DECREASE_1 = 21
        const val SOLO_MEDIUM_DISPLAY = 22
        const val SOLO_MEDIUM_INCREASE_1 = 23
        const val SOLO_MEDIUM_INCREASE_10 = 24
        const val SOLO_HARD_DECREASE_10 = 29
        const val SOLO_HARD_DECREASE_1 = 30
        const val SOLO_HARD_DISPLAY = 31
        const val SOLO_HARD_INCREASE_1 = 32
        const val SOLO_HARD_INCREASE_10 = 33
        const val GLOBAL_DECREASE_10 = 38
        const val GLOBAL_DECREASE_1 = 39
        const val GLOBAL_DISPLAY = 40
        const val GLOBAL_INCREASE_1 = 41
        const val GLOBAL_INCREASE_10 = 42
        const val BACK = 49
    }

    private object Textures {
        const val INCREASE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTU3YTViZGY0MmYxNTIxNzhkMTU0YmIyMjM3ZDlmZDM1NzcyYTdmMzJiY2ZkMzNiZWViOGVkYzQ4MjBiYSJ9fX0="
        const val DECREASE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTZhMDExZTYyNmI3MWNlYWQ5ODQxOTM1MTFlODJlNjVjMTM1OTU2NWYwYTJmY2QxMTg0ODcyZjg5ZDkwOGM2NSJ9fX0="
        const val TOGGLE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGI0ODk4YzE0NDI4ZGQyZTQwMTFkOWJlMzc2MGVjNmJhYjUyMWFlNTY1MWY2ZTIwYWQ1MzQxYTBmNWFmY2UyOCJ9fX0="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    fun openGui(player: ServerPlayerEntity) {
        playerTimerModes[player] = TimerMode.COOLDOWN
        CustomGui.openGui(
            player,
            "Hunts Global Settings",
            generateLayout(player),
            { context -> handleInteraction(context, player) },
            { _ -> playerTimerModes.remove(player) }
        )
    }

    private fun generateLayout(player: ServerPlayerEntity): List<ItemStack> {
        val mode = playerTimerModes[player] ?: TimerMode.COOLDOWN
        val layout = MutableList(54) { createFillerPane() }

        layout[Slots.TOGGLE_MODE] = createToggleButton(mode)

        addTimerSection(layout, "Solo Easy", "soloEasy", mode, Slots.SOLO_EASY_DECREASE_10, Slots.SOLO_EASY_DECREASE_1, Slots.SOLO_EASY_DISPLAY, Slots.SOLO_EASY_INCREASE_1, Slots.SOLO_EASY_INCREASE_10)
        addTimerSection(layout, "Solo Medium", "soloMedium", mode, Slots.SOLO_MEDIUM_DECREASE_10, Slots.SOLO_MEDIUM_DECREASE_1, Slots.SOLO_MEDIUM_DISPLAY, Slots.SOLO_MEDIUM_INCREASE_1, Slots.SOLO_MEDIUM_INCREASE_10)
        addTimerSection(layout, "Solo Hard", "soloHard", mode, Slots.SOLO_HARD_DECREASE_10, Slots.SOLO_HARD_DECREASE_1, Slots.SOLO_HARD_DISPLAY, Slots.SOLO_HARD_INCREASE_1, Slots.SOLO_HARD_INCREASE_10)
        addTimerSection(layout, "Global", "global", mode, Slots.GLOBAL_DECREASE_10, Slots.GLOBAL_DECREASE_1, Slots.GLOBAL_DISPLAY, Slots.GLOBAL_INCREASE_1, Slots.GLOBAL_INCREASE_10)

        layout[Slots.BACK] = CustomGui.createPlayerHeadButton(
            textureName = "Back",
            title = Text.literal("Back").styled { it.withColor(Formatting.YELLOW) },
            lore = listOf(
                Text.literal("Return to main menu").styled { it.withColor(Formatting.GRAY) }
            ),
            textureValue = Textures.BACK
        )

        return layout
    }

    private fun createToggleButton(mode: TimerMode): ItemStack {
        val currentModeName = if (mode == TimerMode.COOLDOWN) "Cooldowns" else "Time Limits"
        val nextModeName = if (mode == TimerMode.COOLDOWN) "Time Limits" else "Cooldowns"

        val title = Text.literal("Editing: ").styled { it.withColor(Formatting.GRAY) }
            .append(Text.literal(currentModeName).styled { it.withColor(Formatting.AQUA) })
        val lore = listOf(
            Text.literal("Click to switch to editing ").styled { it.withColor(Formatting.YELLOW) }
                .append(Text.literal(nextModeName).styled { it.withColor(Formatting.GREEN) })
        )
        return CustomGui.createPlayerHeadButton(
            "ToggleMode",
            title,
            lore,
            Textures.TOGGLE
        )
    }

    private fun addTimerSection(
        layout: MutableList<ItemStack>,
        displayPrefix: String,
        configKey: String,
        mode: TimerMode,
        decrease10Slot: Int,
        decrease1Slot: Int,
        displaySlot: Int,
        increase1Slot: Int,
        increase10Slot: Int
    ) {
        val label = if (mode == TimerMode.COOLDOWN) "$displayPrefix Cooldown" else "$displayPrefix Time Limit"
        val currentValue = getTimerValue(configKey, mode)

        layout[decrease10Slot] = createAdjustmentButton("Decrease 10s", -10, Textures.DECREASE, label, currentValue)
        layout[decrease1Slot] = createAdjustmentButton("Decrease 1s", -1, Textures.DECREASE, label, currentValue)

        val displayItem = if (mode == TimerMode.COOLDOWN) Items.CLOCK else Items.COMPASS
        layout[displaySlot] = ItemStack(displayItem).apply {
            setCustomName(
                Text.literal(label).styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal(": ").styled { it.withColor(Formatting.GRAY) })
                    .append(Text.literal("$currentValue seconds").styled { it.withColor(Formatting.AQUA) })
            )
            val itemLore = listOf(
                Text.literal("Currently showing ").styled { it.withColor(Formatting.DARK_GRAY) }
                    .append(Text.literal(if (mode == TimerMode.COOLDOWN) "Cooldown" else "Time Limit").styled { it.withColor(Formatting.DARK_AQUA) })
            )
            CustomGui.setItemLore(this, itemLore)
        }

        layout[increase1Slot] = createAdjustmentButton("Increase 1s", 1, Textures.INCREASE, label, currentValue)
        layout[increase10Slot] = createAdjustmentButton("Increase 10s", 10, Textures.INCREASE, label, currentValue)
    }

    private fun createAdjustmentButton(name: String, delta: Int, textureValue: String, label: String, currentValue: Int): ItemStack {
        val title = Text.literal(name).styled { it.withColor(if (delta > 0) Formatting.GREEN else Formatting.RED) }
        val lore = listOf(
            Text.literal("$label: ").styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal("$currentValue seconds").styled { it.withColor(Formatting.AQUA) }),
            Text.literal("Click to adjust by ").styled { it.withColor(Formatting.YELLOW) }
                .append(Text.literal("$delta seconds").styled { it.withColor(if (delta > 0) Formatting.GREEN else Formatting.RED) })
        )
        return CustomGui.createPlayerHeadButton(name, title, lore, textureValue)
    }

    private fun handleInteraction(context: InteractionContext, player: ServerPlayerEntity) {
        val mode = playerTimerModes[player] ?: TimerMode.COOLDOWN

        when (context.slotIndex) {
            Slots.TOGGLE_MODE -> {
                val newMode = if (mode == TimerMode.COOLDOWN) TimerMode.TIME_LIMIT else TimerMode.COOLDOWN
                playerTimerModes[player] = newMode
                CustomGui.refreshGui(player, generateLayout(player))
                player.sendMessage(
                    Text.literal("Switched to editing ").styled { it.withColor(Formatting.YELLOW) }
                        .append(Text.literal(if (newMode == TimerMode.COOLDOWN) "Cooldowns" else "Time Limits").styled { it.withColor(Formatting.AQUA) }),
                    true
                )
            }
            Slots.SOLO_EASY_DECREASE_10 -> adjustTimer(player, "soloEasy", mode, -10)
            Slots.SOLO_EASY_DECREASE_1 -> adjustTimer(player, "soloEasy", mode, -1)
            Slots.SOLO_EASY_INCREASE_1 -> adjustTimer(player, "soloEasy", mode, 1)
            Slots.SOLO_EASY_INCREASE_10 -> adjustTimer(player, "soloEasy", mode, 10)
            Slots.SOLO_MEDIUM_DECREASE_10 -> adjustTimer(player, "soloMedium", mode, -10)
            Slots.SOLO_MEDIUM_DECREASE_1 -> adjustTimer(player, "soloMedium", mode, -1)
            Slots.SOLO_MEDIUM_INCREASE_1 -> adjustTimer(player, "soloMedium", mode, 1)
            Slots.SOLO_MEDIUM_INCREASE_10 -> adjustTimer(player, "soloMedium", mode, 10)
            Slots.SOLO_HARD_DECREASE_10 -> adjustTimer(player, "soloHard", mode, -10)
            Slots.SOLO_HARD_DECREASE_1 -> adjustTimer(player, "soloHard", mode, -1)
            Slots.SOLO_HARD_INCREASE_1 -> adjustTimer(player, "soloHard", mode, 1)
            Slots.SOLO_HARD_INCREASE_10 -> adjustTimer(player, "soloHard", mode, 10)
            Slots.GLOBAL_DECREASE_10 -> adjustTimer(player, "global", mode, -10)
            Slots.GLOBAL_DECREASE_1 -> adjustTimer(player, "global", mode, -1)
            Slots.GLOBAL_INCREASE_1 -> adjustTimer(player, "global", mode, 1)
            Slots.GLOBAL_INCREASE_10 -> adjustTimer(player, "global", mode, 10)
            Slots.BACK -> HuntsEditorMainGui.openGui(player)
        }
    }

    private fun getTimerValue(configKey: String, mode: TimerMode): Int {
        return when (configKey) {
            "soloEasy" -> if (mode == TimerMode.COOLDOWN) HuntsConfig.config.soloEasyCooldown else HuntsConfig.config.soloEasyTimeLimit
            "soloMedium" -> if (mode == TimerMode.COOLDOWN) HuntsConfig.config.soloMediumCooldown else HuntsConfig.config.soloMediumTimeLimit
            "soloHard" -> if (mode == TimerMode.COOLDOWN) HuntsConfig.config.soloHardCooldown else HuntsConfig.config.soloHardTimeLimit
            "global" -> if (mode == TimerMode.COOLDOWN) HuntsConfig.config.globalCooldown else HuntsConfig.config.globalTimeLimit
            else -> 0
        }
    }

    private fun getTimerFieldName(configKey: String, mode: TimerMode): String {
        return when (configKey) {
            "soloEasy" -> if (mode == TimerMode.COOLDOWN) "soloEasyCooldown" else "soloEasyTimeLimit"
            "soloMedium" -> if (mode == TimerMode.COOLDOWN) "soloMediumCooldown" else "soloMediumTimeLimit"
            "soloHard" -> if (mode == TimerMode.COOLDOWN) "soloHardCooldown" else "soloHardTimeLimit"
            "global" -> if (mode == TimerMode.COOLDOWN) "globalCooldown" else "globalTimeLimit"
            else -> ""
        }
    }

    private fun adjustTimer(player: ServerPlayerEntity, configKey: String, mode: TimerMode, delta: Int) {
        val fieldName = getTimerFieldName(configKey, mode)
        if (fieldName.isEmpty()) return

        val currentValue = getTimerValue(configKey, mode)
        val newValue = (currentValue + delta).coerceAtLeast(0)

        when (fieldName) {
            "soloEasyCooldown" -> HuntsConfig.config.soloEasyCooldown = newValue
            "soloMediumCooldown" -> HuntsConfig.config.soloMediumCooldown = newValue
            "soloHardCooldown" -> HuntsConfig.config.soloHardCooldown = newValue
            "globalCooldown" -> HuntsConfig.config.globalCooldown = newValue
            "soloEasyTimeLimit" -> HuntsConfig.config.soloEasyTimeLimit = newValue
            "soloMediumTimeLimit" -> HuntsConfig.config.soloMediumTimeLimit = newValue
            "soloHardTimeLimit" -> HuntsConfig.config.soloHardTimeLimit = newValue
            "globalTimeLimit" -> HuntsConfig.config.globalTimeLimit = newValue
        }

        HuntsConfig.saveConfig()
        CustomGui.refreshGui(player, generateLayout(player))

        val label = if (mode == TimerMode.COOLDOWN) "$configKey Cooldown" else "$configKey Time Limit"
        player.sendMessage(
            Text.literal("Set ${label.replaceFirstChar { it.titlecase() }} to ").styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal("$newValue seconds").styled { it.withColor(Formatting.AQUA) }),
            true
        )
    }
}

private fun createFillerPane(): ItemStack {
    return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
        setCustomName(Text.literal(" "))
    }
}