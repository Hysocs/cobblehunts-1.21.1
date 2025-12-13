package com.cobblehunts.gui.huntseditorgui

import com.cobblehunts.utils.HuntsConfig
import com.cobblehunts.utils.HuntPokemonEntry
import com.cobblehunts.utils.SpeciesMatcher
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
import java.util.concurrent.CompletableFuture
import com.everlastingutils.gui.CustomScreenHandler
import net.minecraft.screen.AnvilScreenHandler
import kotlin.math.min

enum class SortMethod {
    ALPHABETICAL, TYPE, SELECTED, SEARCH
}

enum class Difficulty(val displayName: String, val texture: String, val color: Formatting) {
    EASY("Easy", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2MxYjJmNTkyY2ZjOGQzNzJkY2Y1ZmQ0NGVlZDY5ZGRkYzY0NjAxZDc4NDZkNzI2MTlmNzA1MTFkODA0M2E4OSJ9fX0=", Formatting.GREEN),
    NORMAL("Normal", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGViZjZiZTgyZTgyM2U1ODJmZjhmNDVlMDFjZjUxNzUxNzM0ZDlhNzc2Y2ZmNTE0ZTg4ZWEyNmQ0OTczYmJmMCJ9fX0=", Formatting.BLUE),
    MEDIUM("Medium", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU0ZWJlZmVmYThjYjNjNTg2MGFjODQxMjY1OWUxMjNiYTE1NGQ0YjcwOTZjM2IxMjNjMGQxZmNhNjNjOTc5OSJ9fX0=", Formatting.GOLD),
    HARD("Hard", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjU0NGIwOWQ1NTljZWJiNDA2YjcyMjcwMGYzYmQzN2NiMTZkMjhkMmY2ODE0YjU4M2M2NDI3MDA4NGJjNjhiMyJ9fX0=", Formatting.RED)
}

object GuiHelpers {
    fun createFillerPane(): ItemStack = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply {
        setCustomName(Text.literal(""))
    }

    fun createPlayerHeadButton(name: String, title: Text, lore: List<Text>, texture: String): ItemStack =
        CustomGui.createPlayerHeadButton(name, title, lore, texture)

    fun createBackButton(actionText: String = "Return to previous menu"): ItemStack =
        createPlayerHeadButton(
            "Back",
            Text.literal("Back").styled { it.withColor(Formatting.YELLOW) },
            listOf(Text.literal(actionText).styled { it.withColor(Formatting.GRAY).withItalic(false) }),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        )

    fun createDifficultyButton(difficulty: Difficulty): ItemStack =
        createPlayerHeadButton(
            difficulty.displayName,
            Text.literal("${difficulty.displayName} Tier").styled { it.withColor(difficulty.color) },
            listOf(
                Text.literal("Left-click: ").styled { it.withColor(Formatting.GRAY).withItalic(false) }
                    .append(Text.literal("Edit Pokémon").styled { it.withColor(Formatting.YELLOW).withItalic(false) }),
                Text.literal("Right-click: ").styled { it.withColor(Formatting.GRAY).withItalic(false) }
                    .append(Text.literal("Edit Loot Pool").styled { it.withColor(Formatting.YELLOW).withItalic(false) })
            ),
            difficulty.texture
        )
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
        CustomGui.openGui(player, "Hunts Editor", generateMainLayout(), { context -> handleMainInteraction(context, player) }, {})
    }

    private fun generateMainLayout(): List<ItemStack> {
        val layout = MutableList(54) { GuiHelpers.createFillerPane() }
        layout[Slots.GLOBAL] = GuiHelpers.createPlayerHeadButton(
            "GlobalHunts",
            Text.literal("Global Hunts").styled { it.withColor(Formatting.GREEN) },
            listOf(
                Text.literal("Left-click: ").styled { it.withColor(Formatting.GRAY).withItalic(false) }
                    .append(Text.literal("Edit Pokémon").styled { it.withColor(Formatting.YELLOW).withItalic(false) }),
                Text.literal("Right-click: ").styled { it.withColor(Formatting.GRAY).withItalic(false) }
                    .append(Text.literal("Edit Loot Pool").styled { it.withColor(Formatting.YELLOW).withItalic(false) })
            ),
            Textures.GLOBAL
        )
        layout[Slots.SOLO] = GuiHelpers.createPlayerHeadButton(
            "SoloHunts",
            Text.literal("Solo Hunts").styled { it.withColor(Formatting.AQUA) },
            listOf(Text.literal("Click to edit tiers").styled { it.withColor(Formatting.YELLOW).withItalic(false) }),
            Textures.SOLO
        )
        layout[Slots.SETTINGS] = GuiHelpers.createPlayerHeadButton(
            "Settings",
            Text.literal("Hunts Global Settings").styled { it.withColor(Formatting.YELLOW) },
            listOf(
                Text.literal("Click to edit global timers")
                    .styled { it.withColor(Formatting.YELLOW).withItalic(false) }),
            Textures.SETTINGS
        )
        return layout
    }

    private fun handleMainInteraction(context: InteractionContext, player: ServerPlayerEntity) {
        when (context.slotIndex) {
            Slots.GLOBAL -> if (context.clickType == ClickType.LEFT) HuntsPokemonSelectionGui.openGui(
                player,
                "global",
                "global"
            )
            else if (context.clickType == ClickType.RIGHT) LootPoolSelectionGui.openGui(player, "global", "global")
            Slots.SOLO -> HuntsTierSelectionGui.openGui(player, "solo")
            Slots.SETTINGS -> HuntsGlobalSettingsGui.openGui(player)
        }
    }
}

object HuntsTierSelectionGui {
    private object Slots {
        const val EASY = 20
        const val NORMAL = 21
        const val MEDIUM = 23
        const val HARD = 24
        const val BACK = 49
    }

    fun openGui(player: ServerPlayerEntity, type: String) {
        CustomGui.openGui(
            player,
            "Select Tier for ${type.replaceFirstChar { it.titlecase() }} Hunts",
            generateTierLayout(),
            { context -> handleTierInteraction(context, player, type) },
            {}
        )
    }

    private fun generateTierLayout(): List<ItemStack> {
        val layout = MutableList(54) { GuiHelpers.createFillerPane() }
        layout[Slots.EASY] = GuiHelpers.createDifficultyButton(Difficulty.EASY)
        layout[Slots.NORMAL] = GuiHelpers.createDifficultyButton(Difficulty.NORMAL)
        layout[Slots.MEDIUM] = GuiHelpers.createDifficultyButton(Difficulty.MEDIUM)
        layout[Slots.HARD] = GuiHelpers.createDifficultyButton(Difficulty.HARD)
        layout[Slots.BACK] = GuiHelpers.createBackButton("Return to main menu")
        return layout
    }

    private fun handleTierInteraction(context: InteractionContext, player: ServerPlayerEntity, type: String) {
        when (context.slotIndex) {
            in listOf(Slots.EASY, Slots.NORMAL, Slots.MEDIUM, Slots.HARD) -> {
                val difficulty = when (context.slotIndex) {
                    Slots.EASY -> Difficulty.EASY
                    Slots.NORMAL -> Difficulty.NORMAL
                    Slots.MEDIUM -> Difficulty.MEDIUM
                    Slots.HARD -> Difficulty.HARD
                    else -> return
                }
                if (context.clickType == ClickType.LEFT) HuntsPokemonSelectionGui.openGui(
                    player,
                    type,
                    difficulty.name.lowercase()
                )
                else if (context.clickType == ClickType.RIGHT) LootPoolSelectionGui.openGui(
                    player,
                    type,
                    difficulty.name.lowercase()
                )
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
    private val playerLastRefreshId = ConcurrentHashMap<ServerPlayerEntity, Long>()
    @Volatile private var currentRefreshId: Long = 0L


    private object Slots {
        const val BACK = 49
        const val PREV_PAGE = 45
        const val NEXT_PAGE = 53
        const val SORT_METHOD = 4
    }

    private object Textures {
        const val PREV_PAGE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTMzYWQ1YzIyZGIxNjQzNWRhYWQ2MTU5MGFiYTUxZDkzNzkxNDJkZDU1NmQ2YzQyMmE3MTEwY2EzYWJlYTUwIn19fQ=="
        const val NEXT_PAGE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU0MDNjYzdiYmFjNzM2NzBiZDU0M2Y2YjA5NTViYWU3YjhlOTEyM2Q4M2JkNzYwZjYyMDRjNWFmZDhiZTdlMSJ9fX0="
        const val SORT_METHOD =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDExZTk5N2QwZjE4YjZhMjk1YTRiZjBiZDdhYWZjZjE2ZWRhZDgzMjEwN2QyZmYyNzFjNzgxZDU2ZGQ5MWE3MyJ9fX0="
        const val CANCEL_SEARCH =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    data class SpeciesFormVariant(val species: Species, val form: FormData, val additionalAspects: Set<String>) {
        fun toKey(): String = "${species.showdownId()}_${
            if (form.name.equals("Standard", ignoreCase = true)) "normal" else form.name.lowercase()
        }_${additionalAspects.map { it.lowercase() }.sorted().joinToString(",")}"
    }

    private val loadingItem: ItemStack by lazy {
        ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("Loading...").formatted(Formatting.YELLOW)) }
    }

    fun openGui(player: ServerPlayerEntity, type: String, tier: String) {
        playerTypes[player] = type
        playerTiers[player] = tier
        playerPages.putIfAbsent(player, 0)
        playerSortMethods.putIfAbsent(player, SortMethod.ALPHABETICAL)
        playerSearchTerms.putIfAbsent(player, "")

        val title = if (type == "global") "Select Pokémon for Global Hunts"
        else "Select Pokémon for ${type.replaceFirstChar { it.titlecase() }} ${tier.replaceFirstChar { it.titlecase() }}"

        val initialLayout = MutableList(54) { GuiHelpers.createFillerPane() }
        for (i in 9..44) {
            initialLayout[i] = loadingItem
        }
        initialLayout[Slots.SORT_METHOD] = createSortMethodButton(playerSortMethods[player]!!, playerSearchTerms[player]!!)
        initialLayout[Slots.BACK] = GuiHelpers.createBackButton()
        val greyedOutNavButton = GuiHelpers.createPlayerHeadButton("...", Text.literal("").styled{it.withColor(Formatting.DARK_GRAY)}, emptyList(), Textures.PREV_PAGE)
        initialLayout[Slots.PREV_PAGE] = greyedOutNavButton
        initialLayout[Slots.NEXT_PAGE] = greyedOutNavButton


        CustomGui.openGui(
            player,
            title,
            initialLayout,
            { ctx -> handleInteraction(ctx, player) },
            { cleanupPlayerData(player) }
        )
        refreshGuiAsync(player, type, tier, playerPages[player]!!, playerSortMethods[player]!!, playerSearchTerms[player]!!)
    }

    private fun refreshGuiAsync(player: ServerPlayerEntity, type: String, tier: String, page: Int, sortMethod: SortMethod, searchTerm: String) {
        val refreshId = synchronized(this) { ++currentRefreshId }
        playerLastRefreshId[player] = refreshId

        CompletableFuture.supplyAsync {
            generateSelectionLayout(type, tier, page, sortMethod, searchTerm)
        }.thenAcceptAsync({ finalLayout ->
            if (player.isRemoved || player.currentScreenHandler !is CustomScreenHandler) {
                return@thenAcceptAsync
            }
            if (playerLastRefreshId[player] != refreshId) {
                return@thenAcceptAsync
            }

            if (playerPages[player] == page && playerSortMethods[player] == sortMethod &&
                playerSearchTerms[player] == searchTerm && playerTypes[player] == type && playerTiers[player] == tier) {
                CustomGui.refreshGui(player, finalLayout)
            }
        }, player.server)
    }


    private fun generateSelectionLayout(
        type: String,
        tier: String,
        page: Int,
        sortMethod: SortMethod,
        searchTerm: String
    ): List<ItemStack> {
        val layout = MutableList(54) { GuiHelpers.createFillerPane() }
        layout[Slots.SORT_METHOD] = createSortMethodButton(sortMethod, searchTerm)
        val monsPerPage = 36
        val selectedPokemonFromConfig = HuntsConfig.getPokemonList(type, tier)

        val selectedPokemonMap = selectedPokemonFromConfig.mapNotNull { configEntry ->
            val resolvedId = SpeciesMatcher.resolveToShowdownId(configEntry.species)
            if (resolvedId != null) {
                val key = Triple(resolvedId, configEntry.form, configEntry.aspects)
                key to configEntry
            } else {
                null
            }
        }.toMap()

        val variantsList = getVariantsForPage(selectedPokemonFromConfig, page, sortMethod, searchTerm, monsPerPage)

        for (i in variantsList.indices) {
            val slot = i + 9
            val variant = variantsList[i]
            val variantShowdownId = variant.species.showdownId()
            val variantFormName = if (variant.form.name.equals("Standard", ignoreCase = true)) null else variant.form.name
            val variantAspects = variant.additionalAspects

            val lookupKey = Triple(variantShowdownId, variantFormName, variantAspects)
            val entry: HuntPokemonEntry? = selectedPokemonMap[lookupKey]

            layout[slot] =
                if (entry != null) createSelectedPokemonItem(tier, variant, entry) else createUnselectedPokemonItem(variant)
        }

        val totalVariants = getTotalVariantsCount(selectedPokemonFromConfig, sortMethod, searchTerm)
        if (page > 0) layout[Slots.PREV_PAGE] = GuiHelpers.createPlayerHeadButton(
            "Previous",
            Text.literal("Previous").styled { it.withColor(Formatting.YELLOW) },
            listOf(Text.literal("Go to previous page").styled { it.withColor(Formatting.GRAY).withItalic(false) }),
            Textures.PREV_PAGE
        ) else {
            layout[Slots.PREV_PAGE] = GuiHelpers.createFillerPane()
        }

        layout[Slots.BACK] = GuiHelpers.createBackButton()

        if ((page + 1) * monsPerPage < totalVariants) layout[Slots.NEXT_PAGE] = GuiHelpers.createPlayerHeadButton(
            "Next",
            Text.literal("Next").styled { it.withColor(Formatting.GREEN) },
            listOf(Text.literal("Go to next page").styled { it.withColor(Formatting.GRAY).withItalic(false) }),
            Textures.NEXT_PAGE
        ) else {
            layout[Slots.NEXT_PAGE] = GuiHelpers.createFillerPane()
        }
        return layout
    }

    private fun handleInteraction(context: InteractionContext, player: ServerPlayerEntity) {
        val type = playerTypes[player] ?: return
        val tier = playerTiers[player] ?: return
        var currentPage = playerPages[player] ?: 0
        val currentSortMethod = playerSortMethods[player] ?: SortMethod.ALPHABETICAL
        val currentSearchTerm = playerSearchTerms[player] ?: ""

        when (context.slotIndex) {
            Slots.PREV_PAGE -> if (currentPage > 0) {
                currentPage--
                playerPages[player] = currentPage
                refreshGuiAsync(player, type, tier, currentPage, currentSortMethod, currentSearchTerm)
            }

            Slots.BACK -> {
                if (type == "global") HuntsEditorMainGui.openGui(player)
                else HuntsTierSelectionGui.openGui(player, type)
            }

            Slots.SORT_METHOD -> when (context.clickType) {
                ClickType.LEFT -> {
                    val newSortMethod = when (currentSortMethod) {
                        SortMethod.ALPHABETICAL -> SortMethod.TYPE
                        SortMethod.TYPE -> SortMethod.SELECTED
                        SortMethod.SELECTED -> SortMethod.ALPHABETICAL
                        SortMethod.SEARCH -> SortMethod.ALPHABETICAL
                    }
                    playerSortMethods[player] = newSortMethod
                    if (newSortMethod != SortMethod.SEARCH) playerSearchTerms[player] = ""
                    playerPages[player] = 0
                    refreshGuiAsync(player, type, tier, 0, newSortMethod, playerSearchTerms[player]!!)
                }
                ClickType.RIGHT -> {
                    playerPreviousSortMethods[player] = currentSortMethod
                    openSearchGui(player, type, tier)
                }
            }

            Slots.NEXT_PAGE -> {
                val totalVariants = getTotalVariantsCount(HuntsConfig.getPokemonList(type, tier), currentSortMethod, currentSearchTerm)
                if ((currentPage + 1) * 36 < totalVariants) {
                    currentPage++
                    playerPages[player] = currentPage
                    refreshGuiAsync(player, type, tier, currentPage, currentSortMethod, currentSearchTerm)
                }
            }

            in 9..44 -> {
                val selectedPokemonConfigList = HuntsConfig.getPokemonList(type, tier)
                val variantsDisplayed = getVariantsForPage(selectedPokemonConfigList, currentPage, currentSortMethod, currentSearchTerm, 36)
                val index = context.slotIndex - 9

                if (index < variantsDisplayed.size) {
                    val clickedVariant = variantsDisplayed[index]
                    val clickedPokemonPrettyName = clickedVariant.species.name
                    val clickedPokemonShowdownId = clickedVariant.species.showdownId()
                    val formNameForConfig = if (clickedVariant.form.name.equals("Standard", ignoreCase = true)) null else clickedVariant.form.name

                    val entryForOperations = HuntPokemonEntry(
                        species = clickedPokemonPrettyName,
                        form = formNameForConfig,
                        aspects = clickedVariant.additionalAspects,
                        chance = 1.0
                    )

                    if (context.clickType == ClickType.RIGHT) {
                        val existingConfigEntry = selectedPokemonConfigList.find { configEntry ->
                            val resolvedConfigSpeciesId = SpeciesMatcher.resolveToShowdownId(configEntry.species)
                            resolvedConfigSpeciesId != null &&
                                    resolvedConfigSpeciesId == clickedPokemonShowdownId &&
                                    configEntry.form == formNameForConfig &&
                                    configEntry.aspects == clickedVariant.additionalAspects
                        }
                        HuntsPokemonEditGui.openGui(player, type, tier, existingConfigEntry ?: entryForOperations)
                    } else if (context.clickType == ClickType.LEFT) {
                        val removed = selectedPokemonConfigList.removeIf { configEntry ->
                            val resolvedConfigSpeciesId = SpeciesMatcher.resolveToShowdownId(configEntry.species)
                            resolvedConfigSpeciesId != null &&
                                    resolvedConfigSpeciesId == clickedPokemonShowdownId &&
                                    configEntry.form == formNameForConfig &&
                                    configEntry.aspects == clickedVariant.additionalAspects
                        }

                        if (removed) {
                            player.sendMessage(
                                Text.literal("Removed ").styled { it.withColor(Formatting.GRAY) }
                                    .append(Text.literal(clickedPokemonPrettyName).styled { it.withColor(Formatting.WHITE) })
                                    .append(Text.literal(" from $type hunts").styled { it.withColor(Formatting.GRAY) }),
                                false
                            )
                        } else {
                            selectedPokemonConfigList.add(entryForOperations)
                            player.sendMessage(
                                Text.literal("Added ").styled { it.withColor(Formatting.GRAY) }
                                    .append(Text.literal(clickedPokemonPrettyName).styled { it.withColor(Formatting.WHITE) })
                                    .append(Text.literal(" to $type hunts").styled { it.withColor(Formatting.GRAY) }),
                                false
                            )
                        }
                        HuntsConfig.saveConfig()
                        refreshGuiAsync(player, type, tier, currentPage, currentSortMethod, currentSearchTerm)
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
        playerLastRefreshId.remove(player)
    }

    private fun createSelectedPokemonItem(
        tier: String,
        variant: SpeciesFormVariant,
        entry: HuntPokemonEntry
    ): ItemStack {
        val aspectsString = variant.additionalAspects.joinToString(" ") { "aspect=$it" }
        val formString = if (!variant.form.name.equals("Standard", ignoreCase = true)) " form=${variant.form.name}" else ""
        val properties = PokemonProperties.parse("${variant.species.showdownId()}$formString $aspectsString".trim())
        val pokemon = properties.create()
        val item = PokemonItem.from(pokemon)
        CustomGui.addEnchantmentGlint(item)

        val speciesDisplayName = variant.species.name
        val originalFormName = variant.form.name
        val formDisplayValue = if (originalFormName.equals("Standard", ignoreCase = true)) "Normal" else originalFormName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        val aspectsToDisplay = variant.additionalAspects.map {
            it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
        }.sorted()
        val aspectsDisplayValue = if (aspectsToDisplay.isNotEmpty()) aspectsToDisplay.joinToString(", ") else ""

        val nameDetailsCollector = mutableListOf<String>()
        nameDetailsCollector.add(formDisplayValue)
        if (aspectsDisplayValue.isNotEmpty()) {
            nameDetailsCollector.add(aspectsDisplayValue)
        }

        val effectiveDetails = nameDetailsCollector.filter { it.isNotBlank() }

        val finalDisplayName = if (effectiveDetails.isNotEmpty()) {
            "$speciesDisplayName (${effectiveDetails.joinToString(", ")})"
        } else {
            speciesDisplayName
        }

        item.setCustomName(Text.literal(finalDisplayName).styled { it.withColor(Formatting.WHITE) })

        val lore = mutableListOf<Text>()
        lore.add(Text.literal("Chance: ").styled { it.withColor(Formatting.GRAY).withItalic(false) }
            .append(
                Text.literal("%.1f%%".format(entry.chance * 100))
                    .styled { it.withColor(Formatting.AQUA).withItalic(false) }))
        if (tier != "easy" && entry.gender != null) lore.add(
            Text.literal("Gender: ").styled { it.withColor(Formatting.GRAY).withItalic(false) }
                .append(
                    Text.literal(entry.gender!!.replaceFirstChar { it.titlecase() })
                        .styled { it.withColor(Formatting.LIGHT_PURPLE).withItalic(false) })
        )
        if (tier in listOf("medium", "hard") && entry.nature != null) lore.add(
            Text.literal("Nature: ").styled { it.withColor(Formatting.GRAY).withItalic(false) }
                .append(
                    Text.literal(entry.nature!!.replaceFirstChar { it.titlecase() })
                        .styled { it.withColor(Formatting.GREEN).withItalic(false) })
        )
        if (tier == "hard" && entry.ivRange != null) lore.add(
            Text.literal("IVs above 20: ").styled { it.withColor(Formatting.GRAY).withItalic(false) }
                .append(Text.literal(entry.ivRange!!).styled { it.withColor(Formatting.GOLD).withItalic(false) })
        )
        lore.add(Text.literal("Left-click to toggle").styled { it.withColor(Formatting.YELLOW).withItalic(false) })
        lore.add(Text.literal("Right-click to edit").styled { it.withColor(Formatting.YELLOW).withItalic(false) })
        CustomGui.setItemLore(item, lore)
        return item
    }

    private fun createUnselectedPokemonItem(variant: SpeciesFormVariant): ItemStack {
        val aspectsString = variant.additionalAspects.joinToString(" ") { "aspect=$it" }
        val formString = if (!variant.form.name.equals("Standard", ignoreCase = true)) " form=${variant.form.name}" else ""
        val properties = PokemonProperties.parse("${variant.species.showdownId()}$formString $aspectsString".trim())
        val pokemon = properties.create()
        val item = PokemonItem.from(pokemon, tint = Vector4f(0.3f, 0.3f, 0.3f, 1f))

        val speciesDisplayName = variant.species.name
        val originalFormName = variant.form.name
        val formDisplayValue = if (originalFormName.equals("Standard", ignoreCase = true)) "Normal" else originalFormName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        val aspectsToDisplay = variant.additionalAspects.map {
            it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
        }.sorted()
        val aspectsDisplayValue = if (aspectsToDisplay.isNotEmpty()) aspectsToDisplay.joinToString(", ") else ""

        val nameDetailsCollector = mutableListOf<String>()
        nameDetailsCollector.add(formDisplayValue)
        if (aspectsDisplayValue.isNotEmpty()) {
            nameDetailsCollector.add(aspectsDisplayValue)
        }

        val effectiveDetails = nameDetailsCollector.filter { it.isNotBlank() }

        val finalDisplayName = if (effectiveDetails.isNotEmpty()) {
            "$speciesDisplayName (${effectiveDetails.joinToString(", ")})"
        } else {
            speciesDisplayName
        }
        item.setCustomName(Text.literal(finalDisplayName).styled { it.withColor(Formatting.GRAY) })

        CustomGui.setItemLore(
            item,
            listOf(Text.literal("Left-click to toggle").styled { it.withColor(Formatting.YELLOW).withItalic(false) })
        )
        return item
    }

    private fun getAllVariants(
        selectedPokemon: List<HuntPokemonEntry>,
        sortMethod: SortMethod,
        searchTerm: String
    ): List<SpeciesFormVariant> {
        val speciesListSource = PokemonSpecies.species

        val pairResult: Pair<List<Species>, Set<Triple<String, String?, Set<String>>>?> =
            when (sortMethod) {
                SortMethod.ALPHABETICAL -> speciesListSource.sortedBy { it.name } to null
                SortMethod.TYPE -> speciesListSource.sortedBy { it.primaryType.name } to null
                SortMethod.SEARCH -> {
                    val list = if (searchTerm.isBlank()) {
                        speciesListSource.sortedBy { it.name }
                    } else {
                        speciesListSource.filter {
                            it.name.lowercase().contains(searchTerm.lowercase()) || it.showdownId().contains(searchTerm.lowercase())
                        }.sortedBy { it.name }
                    }
                    list to null
                }
                SortMethod.SELECTED -> {
                    val selectedShowdownIdsFromConfig = selectedPokemon.mapNotNull { configEntry ->
                        SpeciesMatcher.resolveToShowdownId(configEntry.species)
                    }.toSet()

                    val list = speciesListSource.filter { species ->
                        selectedShowdownIdsFromConfig.contains(species.showdownId())
                    }.sortedBy { it.name }

                    val fullSelectedCache = selectedPokemon.mapNotNull { configEntry ->
                        val resolvedId = SpeciesMatcher.resolveToShowdownId(configEntry.species)
                        if (resolvedId != null) {
                            Triple(resolvedId, configEntry.form, configEntry.aspects)
                        } else {
                            null
                        }
                    }.toSet()
                    list to fullSelectedCache
                }
            }

        val (filteredSpeciesList, selectedCacheForFilter) = pairResult

        return filteredSpeciesList.flatMap { species ->
            val forms = species.forms.ifEmpty { listOf(species.standardForm) }
            forms.flatMap { form ->
                val baseVariants = listOf(
                    SpeciesFormVariant(species, form, emptySet()),
                    SpeciesFormVariant(species, form, setOf("shiny"))
                )
                val additionalAspectSets = getAdditionalAspectSets(species)
                (baseVariants + additionalAspectSets.map {
                    SpeciesFormVariant(
                        species,
                        form,
                        it
                    )
                }).distinctBy { it.toKey() }
            }
        }.let { variants ->
            if (sortMethod == SortMethod.SELECTED && selectedCacheForFilter != null) {
                variants.filter { variant -> isVariantSelected(variant, selectedCacheForFilter) }
            } else variants
        }
    }

    private fun getVariantsForPage(
        selectedPokemon: List<HuntPokemonEntry>,
        page: Int,
        sortMethod: SortMethod,
        searchTerm: String,
        pageSize: Int
    ): List<SpeciesFormVariant> {
        val allVariants = getAllVariants(selectedPokemon, sortMethod, searchTerm)
        val startIndex = page * pageSize
        val endIndex = min(startIndex + pageSize, allVariants.size)
        return if (startIndex < allVariants.size) allVariants.subList(startIndex, endIndex) else emptyList()
    }

    private fun getTotalVariantsCount(
        selectedPokemon: List<HuntPokemonEntry>,
        sortMethod: SortMethod,
        searchTerm: String
    ): Int = getAllVariants(selectedPokemon, sortMethod, searchTerm).size

    private fun isVariantSelected(variant: SpeciesFormVariant, selectedCache: Set<Triple<String, String?, Set<String>>>): Boolean {
        val entryForm = if (variant.form.name.equals("Standard", ignoreCase = true)) null else variant.form.name
        val variantShowdownId = variant.species.showdownId()
        val keyToTest = Triple(variantShowdownId, entryForm, variant.additionalAspects)
        return selectedCache.contains(keyToTest)
    }

    private fun getAdditionalAspectSets(species: Species): List<Set<String>> {
        val aspectSets = mutableListOf<Set<String>>()
        val speciesSpecificAspects = mutableSetOf<String>()
        species.forms.forEach { form -> form.aspects.forEach { speciesSpecificAspects.add(it) } }
        SpeciesFeatures.getFeaturesFor(species).filterIsInstance<ChoiceSpeciesFeatureProvider>()
            .forEach { provider -> provider.getAllAspects().forEach { speciesSpecificAspects.add(it) } }
        for (aspect in speciesSpecificAspects) {
            aspectSets.add(setOf(aspect));
            aspectSets.add(setOf(aspect, "shiny"))
        }
        return aspectSets.distinct()
    }

    private fun createSortMethodButton(sortMethod: SortMethod, searchTerm: String): ItemStack {
        val sortText = if (sortMethod == SortMethod.SEARCH && searchTerm.isNotBlank()) Text.literal("Search: ")
            .styled { it.withColor(Formatting.GRAY).withItalic(false) }
            .append(Text.literal(searchTerm).styled { it.withColor(Formatting.AQUA).withItalic(false) })
        else Text.literal("Sort: ").styled { it.withColor(Formatting.GRAY).withItalic(false) }
            .append(
                Text.literal(sortMethod.name.lowercase().replaceFirstChar { it.uppercase() })
                    .styled { it.withColor(Formatting.AQUA).withItalic(false) })
        val lore = if (sortMethod == SortMethod.SEARCH && searchTerm.isNotBlank()) listOf(
            Text.literal("Current Search: ").styled { it.withColor(Formatting.GRAY).withItalic(false) }
                .append(Text.literal("\"$searchTerm\"").styled { it.withColor(Formatting.AQUA).withItalic(false) }),
            Text.literal("Left-click to cycle sort").styled { it.withColor(Formatting.YELLOW).withItalic(false) },
            Text.literal("Right-click to search again").styled { it.withColor(Formatting.YELLOW).withItalic(false) }
        ) else listOf(
            Text.literal("Left-click to cycle sort").styled { it.withColor(Formatting.YELLOW).withItalic(false) },
            Text.literal("Right-click to search").styled { it.withColor(Formatting.YELLOW).withItalic(false) }
        )
        return GuiHelpers.createPlayerHeadButton("SortMethod", sortText, lore, Textures.SORT_METHOD)
    }

    private fun openSearchGui(
        player: ServerPlayerEntity,
        type: String,
        tier: String
    ) {
        val previousSortMethodOnClick = playerPreviousSortMethods[player] ?: SortMethod.ALPHABETICAL
        val currentPageOnClick = playerPages[player] ?: 0


        val cancelButton = GuiHelpers.createPlayerHeadButton(
            "Cancel",
            Text.literal("Cancel").styled { it.withColor(Formatting.RED) },
            listOf(Text.literal("Click to cancel").styled { it.withColor(Formatting.GRAY).withItalic(false) }),
            Textures.CANCEL_SEARCH
        )
        val blockedInput = ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE).apply{setCustomName(Text.literal(""))}
        val placeholderOutput = ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE).apply{setCustomName(Text.literal(""))}

        AnvilGuiManager.openAnvilGui(
            player = player,
            id = "hunts_search_${type}_$tier",
            title = "Search Pokémon",
            initialText = "",
            leftItem = cancelButton,
            rightItem = blockedInput,
            resultItem = placeholderOutput,

            onLeftClick = { _ ->
                player.closeHandledScreen()
                playerSortMethods[player] = previousSortMethodOnClick
                playerSearchTerms[player] = ""
                playerPages[player] = currentPageOnClick
                openGui(player, type, tier)
            },

            onResultClick = { ctx ->
                val txt = ctx.handler.currentText.trim()
                player.closeHandledScreen()
                if (txt.isNotBlank()) {
                    playerSortMethods[player] = SortMethod.SEARCH
                    playerSearchTerms[player] = txt
                    playerPages[player] = 0
                } else {
                    playerSortMethods[player] = previousSortMethodOnClick
                    playerSearchTerms[player] = ""
                    playerPages[player] = currentPageOnClick
                }
                openGui(player, type, tier)
            },

            onTextChange = { text ->
                val handler = player.currentScreenHandler as? FullyModularAnvilScreenHandler
                if (text.isNotEmpty()) {
                    handler?.updateSlot(
                        2,
                        GuiHelpers.createPlayerHeadButton(
                            "Search",
                            Text.literal("Search: ").styled { it.withColor(Formatting.GREEN) }
                                .append(Text.literal(text).styled { it.withColor(Formatting.AQUA) }),
                            listOf(
                                Text.literal("Click to search")
                                    .styled { it.withColor(Formatting.GRAY).withItalic(false) }),
                            Textures.SORT_METHOD
                        )
                    )
                } else {
                    handler?.updateSlot(2, placeholderOutput)
                }
            },
            onClose = {
                val currentSort = playerSortMethods[player]
                val currentSearch = playerSearchTerms[player]
                val wasSearchButBlanked = currentSort == SortMethod.SEARCH && currentSearch?.isBlank() == true

                if(player.currentScreenHandler !is AnvilScreenHandler){
                    if (wasSearchButBlanked) {
                        playerSortMethods[player] = playerPreviousSortMethods[player] ?: SortMethod.ALPHABETICAL
                        playerSearchTerms[player] = ""
                        playerPages[player] = playerPages[player] ?: 0
                    }
                    openGui(player, type, tier)
                }
            }
        )

        player.server.execute {
            (player.currentScreenHandler as? FullyModularAnvilScreenHandler)?.clearTextField()
        }
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
        const val TOGGLE_NATURE = 31
        const val TOGGLE_IV = 33
        const val BACK = 49
    }

    private object Textures {
        const val INCREASE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTU3YTViZGY0MmYxNTIxNzhkMTU0YmIyMjM3ZDlmZDM1NzcyYTdmMzJiY2ZkMzNiZWViOGVkYzQ4MjBiYSJ9fX0="
        const val DECREASE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTZhMDExZTYyNmI3MWNlYWQ5ODQxOTM1MTFlODJlNjVjMTM1OTU2NWYwYTJmY2QxMTg0ODcyZjg5ZDkwOGM2NSJ9fX0="
        const val TOGGLE_GENDER = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTZlNzRlMjlkYTQxZmNmMmZkYzVjMGI2NjVkYzgxMGI5NzY5Y2UxZmI3NTk5MGI0MTg1YzkxMzZiZmRiZWI0In19fQ=="
        const val TOGGLE_NATURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzQyZmUyNWMxNGMzODVjMjYxNjZiNzExYmY2ZWM3NDQyNmEwNjFhM2I2YTYzMjQzZGU4ZTE5NWEwYjMwNTVjZSJ9fX0="
        const val TOGGLE_IV = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODc1MDkzMmM1OTFhNWZkNmIwNzYxYWQzMjRjYjYyZTA1YTMxNWVkYmEyNjQyYWQ5NmMxMzI5OTMxYjU2MmY3MyJ9fX0="
    }

    private val natures = listOf(
        "Adamant", "Bashful", "Bold", "Brave", "Calm", "Careful", "Docile", "Gentle", "Hardy", "Hasty",
        "Impish", "Jolly", "Lax", "Lonely", "Mild", "Modest", "Naive", "Naughty", "Quiet", "Quirky",
        "Rash", "Relaxed", "Sassy", "Serious", "Timid"
    )

    fun openGui(player: ServerPlayerEntity, type: String, tier: String, entry: HuntPokemonEntry) {
        val currentList = HuntsConfig.getPokemonList(type, tier)
        val entryShowdownId = SpeciesMatcher.resolveToShowdownId(entry.species)

        if (entryShowdownId == null) {
            player.sendMessage(Text.literal("Error: Invalid Pokémon species in entry '${entry.species}'.").styled { it.withColor(Formatting.RED) }, false)
            return
        }

        val configContainsEntry = currentList.any { configEntry ->
            val configEntryShowdownId = SpeciesMatcher.resolveToShowdownId(configEntry.species)
            configEntryShowdownId != null &&
                    configEntryShowdownId == entryShowdownId &&
                    configEntry.form == entry.form &&
                    configEntry.aspects == entry.aspects
        }

        if (!configContainsEntry) {
            currentList.add(entry)
            HuntsConfig.saveConfig()
            player.sendMessage(
                Text.literal("Added ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal(SpeciesMatcher.getPrettyName(entry.species)).styled { it.withColor(Formatting.WHITE) })
                    .append(Text.literal(" to $type hunts. Now editing.").styled { it.withColor(Formatting.GRAY) }), false
            )
        }

        val entryToEdit = currentList.find { configEntry ->
            val configEntryShowdownId = SpeciesMatcher.resolveToShowdownId(configEntry.species)
            configEntryShowdownId != null &&
                    configEntryShowdownId == entryShowdownId &&
                    configEntry.form == entry.form &&
                    configEntry.aspects == entry.aspects
        } ?: entry

        CustomGui.openGui(
            player,
            "Edit ${if (type == "global") "Global" else tier.replaceFirstChar { it.titlecase() }} Entry",
            generateEditLayout(entryToEdit, type, tier),
            { context -> handleEditInteraction(context, player, type, tier, entryToEdit) },
            {}
        )
    }

    private fun generateEditLayout(entry: HuntPokemonEntry, type: String, tier: String): List<ItemStack> {
        val layout = MutableList(54) { GuiHelpers.createFillerPane() }
        layout[Slots.POKEMON_DISPLAY] = createPokemonItem(entry)
        layout[Slots.CHANCE_DISPLAY] = ItemStack(Items.PAPER).apply {
            setCustomName(
                Text.literal("Chance: ").styled { it.withColor(Formatting.GRAY).withItalic(false) }
                    .append(Text.literal("%.1f%%".format(entry.chance * 100)).styled { it.withColor(Formatting.AQUA).withItalic(false) })
            )
            CustomGui.setItemLore(
                this,
                listOf(Text.literal("Base chance for this Pokémon").styled { it.withColor(Formatting.DARK_GRAY).withItalic(false) })
            )
        }
        layout[Slots.DECREASE_LARGE] = createChanceButton("Decrease Large", -1.0, -5.0, entry.chance)
        layout[Slots.DECREASE_MEDIUM] = createChanceButton("Decrease Medium", -0.5, -1.0, entry.chance)
        layout[Slots.DECREASE_SMALL] = createChanceButton("Decrease Small", -0.1, -0.5, entry.chance)
        layout[Slots.INCREASE_SMALL] = createChanceButton("Increase Small", 0.1, 0.5, entry.chance)
        layout[Slots.INCREASE_MEDIUM] = createChanceButton("Increase Medium", 0.5, 1.0, entry.chance)
        layout[Slots.INCREASE_LARGE] = createChanceButton("Increase Large", 1.0, 5.0, entry.chance)

        if (type == "global" || tier != "easy") {
            val genderDisplay = entry.gender?.replaceFirstChar { it.titlecase() } ?: "Any"
            layout[Slots.TOGGLE_GENDER] = GuiHelpers.createPlayerHeadButton(
                "ToggleGender",
                Text.literal("Gender: ").styled { it.withColor(Formatting.GRAY).withItalic(false) }
                    .append(
                        Text.literal(genderDisplay).styled { it.withColor(Formatting.LIGHT_PURPLE).withItalic(false) }),
                listOf(
                    Text.literal("Click to cycle gender (Any -> Male -> Female)")
                        .styled { it.withColor(Formatting.YELLOW).withItalic(false) }
                ),
                Textures.TOGGLE_GENDER
            )
        }

        if (type == "global" || tier in listOf("medium", "hard")) {
            val natureDisplay = entry.nature?.replaceFirstChar { it.titlecase() } ?: "Any"
            layout[Slots.TOGGLE_NATURE] = GuiHelpers.createPlayerHeadButton(
                "ToggleNature",
                Text.literal("Nature: ").styled { it.withColor(Formatting.GRAY).withItalic(false) }
                    .append(Text.literal(natureDisplay).styled { it.withColor(Formatting.GREEN).withItalic(false) }),
                listOf(
                    Text.literal("Click to cycle nature").styled { it.withColor(Formatting.YELLOW).withItalic(false) }
                ),
                Textures.TOGGLE_NATURE
            )
        }

        if (type == "global" || tier == "hard") {
            val ivDisplay = entry.ivRange?.let { "At least $it IVs >= 20" } ?: "Any"
            layout[Slots.TOGGLE_IV] = GuiHelpers.createPlayerHeadButton(
                "ToggleIV",
                Text.literal("Min Perfect IVs: ").styled { it.withColor(Formatting.GRAY).withItalic(false) }
                    .append(Text.literal(ivDisplay).styled { it.withColor(Formatting.GOLD).withItalic(false) }),
                listOf(
                    Text.literal("Click to cycle minimum perfect IVs (Any -> 1 -> 2 -> 3)")
                        .styled { it.withColor(Formatting.YELLOW).withItalic(false) }
                ),
                Textures.TOGGLE_IV
            )
        }

        layout[Slots.BACK] = GuiHelpers.createBackButton("Return to selection")
        return layout
    }

    private fun handleEditInteraction(context: InteractionContext, player: ServerPlayerEntity, type: String, tier: String, guiStateEntry: HuntPokemonEntry) {
        val entryShowdownIdFromGuiState = SpeciesMatcher.resolveToShowdownId(guiStateEntry.species)
        if (entryShowdownIdFromGuiState == null) {
            player.sendMessage(Text.literal("Error: Invalid Pokémon species '${guiStateEntry.species}' during edit operation.").styled { it.withColor(Formatting.RED) }, false)
            HuntsPokemonSelectionGui.openGui(player, type, tier)
            return
        }

        val configEntryToUpdate = HuntsConfig.getPokemonList(type, tier).find { cfgEntry ->
            val cfgEntryShowdownId = SpeciesMatcher.resolveToShowdownId(cfgEntry.species)
            cfgEntryShowdownId != null &&
                    cfgEntryShowdownId == entryShowdownIdFromGuiState &&
                    cfgEntry.form == guiStateEntry.form &&
                    cfgEntry.aspects == guiStateEntry.aspects
        } ?: run {
            player.sendMessage(Text.literal("Error: Could not find Pokémon entry in config to update. It might have been modified or removed.").styled { it.withColor(Formatting.RED) }, false)
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
            val currentChanceBd = configEntryToUpdate.chance.toBigDecimal()
            val deltaBd = deltaDecimal.toBigDecimal()
            val newChance = (currentChanceBd + deltaBd).coerceIn(BigDecimal.ZERO, BigDecimal("1.0")).toDouble()
            configEntryToUpdate.chance = String.format("%.4f", newChance).toDouble()
            HuntsConfig.saveConfig()
            CustomGui.refreshGui(player, generateEditLayout(configEntryToUpdate, type, tier))
            player.sendMessage(
                Text.literal("Chance set to ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("%.1f%%".format(configEntryToUpdate.chance * 100)).styled { it.withColor(Formatting.AQUA) }), true
            )
            return
        }

        when (context.slotIndex) {
            Slots.TOGGLE_GENDER -> if (type == "global" || tier != "easy") {
                configEntryToUpdate.gender = when (configEntryToUpdate.gender?.lowercase()) { "male" -> "Female"; "female" -> null; else -> "Male" }
                HuntsConfig.saveConfig()
                CustomGui.refreshGui(player, generateEditLayout(configEntryToUpdate, type, tier))
                val genderDisplay = configEntryToUpdate.gender?.replaceFirstChar { it.titlecase() } ?: "Any"
                player.sendMessage(
                    Text.literal("Gender set to ").styled { it.withColor(Formatting.GRAY) }
                        .append(Text.literal(genderDisplay).styled { it.withColor(Formatting.LIGHT_PURPLE) }), true
                )
            }
            Slots.TOGGLE_NATURE -> if (type == "global" || tier in listOf("medium", "hard")) {
                configEntryToUpdate.nature = getNextNature(configEntryToUpdate.nature)
                HuntsConfig.saveConfig()
                CustomGui.refreshGui(player, generateEditLayout(configEntryToUpdate, type, tier))
                val natureDisplay = configEntryToUpdate.nature?.replaceFirstChar { it.titlecase() } ?: "Any"
                player.sendMessage(
                    Text.literal("Nature set to ").styled { it.withColor(Formatting.GRAY) }
                        .append(Text.literal(natureDisplay).styled { it.withColor(Formatting.GREEN) }), true
                )
            }
            Slots.TOGGLE_IV -> if (type == "global" || tier == "hard") {
                configEntryToUpdate.ivRange = when (configEntryToUpdate.ivRange) { "1" -> "2"; "2" -> "3"; "3" -> null; else -> "1" }
                HuntsConfig.saveConfig()
                CustomGui.refreshGui(player, generateEditLayout(configEntryToUpdate, type, tier))
                val ivDisplay = configEntryToUpdate.ivRange?.let { "At least $it" } ?: "Any"
                player.sendMessage(
                    Text.literal("Min Perfect IVs set to ").styled { it.withColor(Formatting.GRAY) }
                        .append(Text.literal(ivDisplay).styled { it.withColor(Formatting.GOLD) }), true
                )
            }
            Slots.BACK -> HuntsPokemonSelectionGui.openGui(player, type, tier)
        }
    }

    private fun createPokemonItem(entry: HuntPokemonEntry): ItemStack {
        val resolvedShowdownId = SpeciesMatcher.resolveToShowdownId(entry.species) ?: entry.species
        val formString = entry.form?.let { " form=$it" } ?: ""
        val aspectsString = entry.aspects.joinToString(" ") { aspect -> "aspect=$aspect" }
        val propertyString = "$resolvedShowdownId$formString $aspectsString".trim()

        val properties = PokemonProperties.parse(propertyString)
        val pokemon = properties.create()
        val item = PokemonItem.from(pokemon)

        val speciesForDisplay = SpeciesMatcher.getPrettyName(entry.species, defaultToConfigured = true)
        val formDisplayValue = entry.form?.takeIf { it.isNotBlank() }?.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() } ?: "Normal"

        val aspectsToDisplay = entry.aspects.map {
            it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
        }.sorted()
        val aspectsDisplayValue = if (aspectsToDisplay.isNotEmpty()) aspectsToDisplay.joinToString(", ") else ""

        val nameDetailsCollector = mutableListOf<String>()
        nameDetailsCollector.add(formDisplayValue)
        if (aspectsDisplayValue.isNotEmpty()) {
            nameDetailsCollector.add(aspectsDisplayValue)
        }

        val effectiveDetails = nameDetailsCollector.filter { it.isNotBlank() }

        val finalDisplayNameString = if (effectiveDetails.isNotEmpty()) {
            "$speciesForDisplay (${effectiveDetails.joinToString(", ")})"
        } else {
            speciesForDisplay
        }

        item.setCustomName(Text.literal(finalDisplayNameString).styled { it.withColor(Formatting.WHITE) })
        if (entry.aspects.contains("shiny")) {
            CustomGui.addEnchantmentGlint(item)
        }
        CustomGui.setItemLore(
            item,
            listOf(Text.literal("Editing this Pokémon entry").styled { it.withColor(Formatting.DARK_GRAY).withItalic(false) })
        )
        return item
    }

    private fun createChanceButton(name: String, leftDelta: Double, rightDelta: Double, currentChance: Double): ItemStack {
        val textureValue = if (leftDelta > 0) Textures.INCREASE else Textures.DECREASE
        val title = Text.literal(name).styled { it.withColor(if (leftDelta > 0) Formatting.GREEN else Formatting.RED) }
        val lore = listOf(
            Text.literal("Left-click: ").styled { it.withColor(Formatting.GRAY).withItalic(false) }
                .append(Text.literal("${if (leftDelta > 0) "+" else ""}${"%.1f".format(leftDelta)}%").styled { it.withColor(if (leftDelta > 0) Formatting.GREEN else Formatting.RED).withItalic(false) }),
            Text.literal("Right-click: ").styled { it.withColor(Formatting.GRAY).withItalic(false) }
                .append(Text.literal("${if (rightDelta > 0) "+" else ""}${"%.1f".format(rightDelta)}%").styled { it.withColor(if (rightDelta > 0) Formatting.GREEN else Formatting.RED).withItalic(false) }),
            Text.literal("Current: ").styled { it.withColor(Formatting.GRAY).withItalic(false) }
                .append(Text.literal("%.1f%%".format(currentChance * 100)).styled { it.withColor(Formatting.AQUA).withItalic(false) })
        )
        return GuiHelpers.createPlayerHeadButton(name, title, lore, textureValue)
    }

    private fun getNextNature(currentNature: String?): String? {
        val natureList = listOf(null, "Random") + natures
        val normalizedCurrentNature = currentNature?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val currentIndex = natureList.indexOf(normalizedCurrentNature)
        val nextIndex = (currentIndex + 1) % natureList.size
        return natureList[nextIndex]
    }
}

enum class TimerMode { COOLDOWN, TIME_LIMIT }

object HuntsGlobalSettingsGui {
    private val playerTimerModes = ConcurrentHashMap<ServerPlayerEntity, TimerMode>()

    private object Slots {
        const val TOGGLE_MODE = 4
        const val BACK = 49
        const val SOLO_EASY_DECREASE = 10
        const val SOLO_EASY_DISPLAY = 11
        const val SOLO_EASY_INCREASE = 12
        const val SOLO_HARD_DECREASE = 14
        const val SOLO_HARD_DISPLAY = 15
        const val SOLO_HARD_INCREASE = 16
        const val SOLO_NORMAL_DECREASE = 19
        const val SOLO_NORMAL_DISPLAY = 20
        const val SOLO_NORMAL_INCREASE = 21
        const val SOLO_MEDIUM_DECREASE = 23
        const val SOLO_MEDIUM_DISPLAY = 24
        const val SOLO_MEDIUM_INCREASE = 25
        const val GLOBAL_DECREASE = 28
        const val GLOBAL_DISPLAY = 29
        const val GLOBAL_INCREASE = 30
        const val ACTIVE_GLOBAL_DECREASE = 32
        const val ACTIVE_GLOBAL_DISPLAY = 33
        const val ACTIVE_GLOBAL_INCREASE = 34
    }

    private object Textures {
        const val INCREASE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTU3YTViZGY0MmYxNTIxNzhkMTU0YmIyMjM3ZDlmZDM1NzcyYTdmMzJiY2ZkMzNiZWViOGVkYzQ4MjBiYSJ9fX0="
        const val DECREASE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTZhMDExZTYyNmI3MWNlYWQ5ODQxOTM1MTFlODJlNjVjMTM1OTU2NWYwYTJmY2QxMTg0ODcyZjg5ZDkwOGM2NSJ9fX0="
        const val TOGGLE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGI0ODk4YzE0NDI4ZGQyZTQwMTFkOWJlMzc2MGVjNmJhYjUyMWFlNTY1MWY2ZTIwYWQ1MzQxYTBmNWFmY2UyOCJ9fX0="
    }

    fun openGui(player: ServerPlayerEntity) {
        playerTimerModes.putIfAbsent(player, TimerMode.COOLDOWN)
        CustomGui.openGui(player, "Hunts Global Settings", generateLayout(player), { context -> handleInteraction(context, player) }, { _ -> playerTimerModes.remove(player) })
    }

    private fun generateLayout(player: ServerPlayerEntity): List<ItemStack> {
        val mode = playerTimerModes[player] ?: TimerMode.COOLDOWN
        val layout = MutableList(54) { GuiHelpers.createFillerPane() }

        for (row in 1..4) {
            val slot = row * 9 + 4
            layout[slot] = ItemStack(Items.WHITE_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
        }

        addTimerSection(layout, "Solo Easy", "soloEasy", mode, Slots.SOLO_EASY_DECREASE, Slots.SOLO_EASY_DISPLAY, Slots.SOLO_EASY_INCREASE)
        addTimerSection(layout, "Solo Hard", "soloHard", mode, Slots.SOLO_HARD_DECREASE, Slots.SOLO_HARD_DISPLAY, Slots.SOLO_HARD_INCREASE)
        addTimerSection(layout, "Solo Normal", "soloNormal", mode, Slots.SOLO_NORMAL_DECREASE, Slots.SOLO_NORMAL_DISPLAY, Slots.SOLO_NORMAL_INCREASE)
        addTimerSection(layout, "Solo Medium", "soloMedium", mode, Slots.SOLO_MEDIUM_DECREASE, Slots.SOLO_MEDIUM_DISPLAY, Slots.SOLO_MEDIUM_INCREASE)
        addTimerSection(layout, "Global", "global", mode, Slots.GLOBAL_DECREASE, Slots.GLOBAL_DISPLAY, Slots.GLOBAL_INCREASE)
        addActiveGlobalHuntsSection(layout, Slots.ACTIVE_GLOBAL_DECREASE, Slots.ACTIVE_GLOBAL_DISPLAY, Slots.ACTIVE_GLOBAL_INCREASE)

        layout[Slots.TOGGLE_MODE] = createToggleButton(mode)
        layout[Slots.BACK] = GuiHelpers.createBackButton("Return to main menu")

        return layout
    }

    private fun createToggleButton(mode: TimerMode): ItemStack {
        val currentModeName = if (mode == TimerMode.COOLDOWN) "Cooldowns" else "Time Limits"
        val nextModeName = if (mode == TimerMode.COOLDOWN) "Time Limits" else "Cooldowns"
        return GuiHelpers.createPlayerHeadButton(
            "ToggleMode",
            Text.literal("Editing: ").styled { it.withColor(Formatting.GRAY).withItalic(false) }
                .append(Text.literal(currentModeName).styled { it.withColor(Formatting.AQUA).withItalic(false) }),
            listOf(
                Text.literal("Click to switch to editing ").styled { it.withColor(Formatting.YELLOW).withItalic(false) }
                    .append(Text.literal(nextModeName).styled { it.withColor(Formatting.GREEN).withItalic(false) })
            ),
            Textures.TOGGLE
        )
    }

    private fun addTimerSection(
        layout: MutableList<ItemStack>,
        displayPrefix: String,
        configKey: String,
        mode: TimerMode,
        decreaseSlot: Int,
        displaySlot: Int,
        increaseSlot: Int
    ) {
        val label = if (mode == TimerMode.COOLDOWN) "$displayPrefix Cooldown" else "$displayPrefix Time Limit"
        val currentValue = getTimerValue(configKey, mode)
        layout[decreaseSlot] = createAdjustmentButton(
            "Decrease",
            -1,
            if (configKey.startsWith("solo")) -10 else -60,
            Textures.DECREASE,
            label,
            currentValue,
            isTimer = true
        )
        layout[displaySlot] = ItemStack(if (mode == TimerMode.COOLDOWN) Items.CLOCK else Items.COMPASS).apply {
            setCustomName(
                Text.literal(label).styled { it.withColor(Formatting.GRAY).withItalic(false) }
                    .append(Text.literal(": ").styled { it.withColor(Formatting.GRAY).withItalic(false) })
                    .append(Text.literal("$currentValue seconds").styled { it.withColor(Formatting.AQUA).withItalic(false) })
            )
            CustomGui.setItemLore(
                this,
                listOf(
                    Text.literal("Currently showing ").styled { it.withColor(Formatting.DARK_GRAY).withItalic(false) }
                        .append(Text.literal(if (mode == TimerMode.COOLDOWN) "Cooldown" else "Time Limit").styled { it.withColor(Formatting.DARK_AQUA).withItalic(false) })
                )
            )
        }
        layout[increaseSlot] = createAdjustmentButton(
            "Increase",
            1,
            if (configKey.startsWith("solo")) 10 else 60,
            Textures.INCREASE,
            label,
            currentValue,
            isTimer = true
        )
    }

    private fun addActiveGlobalHuntsSection(layout: MutableList<ItemStack>, decreaseSlot: Int, displaySlot: Int, increaseSlot: Int) {
        val label = "Active Global Hunts"
        val currentValue = HuntsConfig.settings.activeGlobalHuntsAtOnce
        layout[decreaseSlot] = createAdjustmentButton(
            "Decrease", -1, -5, Textures.DECREASE, label, currentValue, isTimer = false, minValue = 1, maxValue = 7
        )
        layout[displaySlot] = ItemStack(Items.WRITABLE_BOOK).apply {
            setCustomName(
                Text.literal("$label: ").styled { it.withColor(Formatting.GRAY).withItalic(false) }
                    .append(Text.literal("$currentValue").styled { it.withColor(Formatting.AQUA).withItalic(false) })
            )
            val lore = mutableListOf<Text>()
            lore.add(Text.literal("Number of active global hunts at once").styled { it.withColor(Formatting.DARK_GRAY).withItalic(false) })
            if (currentValue == 1) {
                lore.add(Text.literal("Minimum value").styled { it.withColor(Formatting.RED).withItalic(false) })
            } else if (currentValue == 7) {
                lore.add(Text.literal("Maximum value").styled { it.withColor(Formatting.RED).withItalic(false) })
            }
            CustomGui.setItemLore(this, lore)
        }
        layout[increaseSlot] = createAdjustmentButton(
            "Increase", 1, 5, Textures.INCREASE, label, currentValue, isTimer = false, minValue = 1, maxValue = 7
        )
    }

    private fun createAdjustmentButton(
        name: String,
        leftDelta: Int,
        rightDelta: Int,
        textureValue: String,
        label: String,
        currentValue: Int,
        isTimer: Boolean,
        minValue: Int? = null,
        maxValue: Int? = null
    ): ItemStack {
        val unit = if (isTimer) " seconds" else ""
        val lore = mutableListOf<Text>()
        lore.add(Text.literal("$label: ").styled { it.withColor(Formatting.GRAY).withItalic(false) }
            .append(Text.literal("$currentValue$unit").styled { it.withColor(Formatting.AQUA).withItalic(false) }))

        val canDecreaseLeft = minValue == null || currentValue + leftDelta >= minValue
        val canDecreaseRight = minValue == null || currentValue + rightDelta >= minValue
        val canIncreaseLeft = maxValue == null || currentValue + leftDelta <= maxValue
        val canIncreaseRight = maxValue == null || currentValue + rightDelta <= maxValue

        if (leftDelta != 0) {
            val leftClickText = Text.literal("Left-click: ").styled { it.withColor(Formatting.YELLOW).withItalic(false) }
                .append(Text.literal("${if (leftDelta > 0) "+" else ""}$leftDelta$unit")
                    .styled { style ->
                        val color = if (leftDelta > 0) Formatting.GREEN else Formatting.RED
                        val finalColor = if ((leftDelta < 0 && !canDecreaseLeft) || (leftDelta > 0 && !canIncreaseLeft)) Formatting.DARK_GRAY else color
                        style.withColor(finalColor).withItalic(false)
                    })
            lore.add(leftClickText)
        }

        if (rightDelta != 0) {
            val rightClickText = Text.literal("Right-click: ").styled { it.withColor(Formatting.YELLOW).withItalic(false) }
                .append(Text.literal("${if (rightDelta > 0) "+" else ""}$rightDelta$unit")
                    .styled { style ->
                        val color = if (rightDelta > 0) Formatting.GREEN else Formatting.RED
                        val finalColor = if ((rightDelta < 0 && !canDecreaseRight) || (rightDelta > 0 && !canIncreaseRight)) Formatting.DARK_GRAY else color
                        style.withColor(finalColor).withItalic(false)
                    })
            lore.add(rightClickText)
        }


        if (minValue != null && currentValue <= minValue && (leftDelta < 0 || rightDelta < 0) ) {
            if ( (leftDelta < 0 && !canDecreaseLeft) || (rightDelta < 0 && !canDecreaseRight))
                lore.add(Text.literal("Minimum value reached").styled { it.withColor(Formatting.RED).withItalic(false) })
        }
        if (maxValue != null && currentValue >= maxValue && (leftDelta > 0 || rightDelta > 0)) {
            if( (leftDelta > 0 && !canIncreaseLeft) || (rightDelta > 0 && !canIncreaseRight) )
                lore.add(Text.literal("Maximum value reached").styled { it.withColor(Formatting.RED).withItalic(false) })
        }

        return GuiHelpers.createPlayerHeadButton(
            name,
            Text.literal(name).styled { it.withColor(if (leftDelta > 0) Formatting.GREEN else Formatting.RED) },
            lore,
            textureValue
        )
    }

    private fun handleInteraction(context: InteractionContext, player: ServerPlayerEntity) {
        val mode = playerTimerModes[player] ?: TimerMode.COOLDOWN
        val clickType = context.clickType

        val deltaMap = mapOf(
            Slots.SOLO_EASY_DECREASE to Pair(-1, -10), Slots.SOLO_EASY_INCREASE to Pair(1, 10),
            Slots.SOLO_NORMAL_DECREASE to Pair(-1, -10), Slots.SOLO_NORMAL_INCREASE to Pair(1, 10),
            Slots.SOLO_MEDIUM_DECREASE to Pair(-1, -10), Slots.SOLO_MEDIUM_INCREASE to Pair(1, 10),
            Slots.SOLO_HARD_DECREASE to Pair(-1, -10), Slots.SOLO_HARD_INCREASE to Pair(1, 10),
            Slots.GLOBAL_DECREASE to Pair(-10, -60), Slots.GLOBAL_INCREASE to Pair(10, 60),
            Slots.ACTIVE_GLOBAL_DECREASE to Pair(-1, -5), Slots.ACTIVE_GLOBAL_INCREASE to Pair(1, 5)
        )

        val configKeyMap = mapOf(
            Slots.SOLO_EASY_DECREASE to "soloEasy", Slots.SOLO_EASY_INCREASE to "soloEasy",
            Slots.SOLO_NORMAL_DECREASE to "soloNormal", Slots.SOLO_NORMAL_INCREASE to "soloNormal",
            Slots.SOLO_MEDIUM_DECREASE to "soloMedium", Slots.SOLO_MEDIUM_INCREASE to "soloMedium",
            Slots.SOLO_HARD_DECREASE to "soloHard", Slots.SOLO_HARD_INCREASE to "soloHard",
            Slots.GLOBAL_DECREASE to "global", Slots.GLOBAL_INCREASE to "global"
        )

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
            in deltaMap.keys -> {
                val deltas = deltaMap[context.slotIndex]!!
                val delta = if (clickType == ClickType.LEFT) deltas.first else if (clickType == ClickType.RIGHT) deltas.second else 0
                if (delta != 0) {
                    if (configKeyMap.containsKey(context.slotIndex)) {
                        adjustTimer(player, configKeyMap[context.slotIndex]!!, mode, delta)
                    } else {
                        adjustActiveGlobalHunts(player, delta)
                    }
                }
            }
            Slots.BACK -> HuntsEditorMainGui.openGui(player)
        }
    }

    private fun getTimerValue(configKey: String, mode: TimerMode): Int = when (configKey) {
        "soloEasy" -> if (mode == TimerMode.COOLDOWN) HuntsConfig.settings.soloEasyCooldown else HuntsConfig.settings.soloEasyTimeLimit
        "soloNormal" -> if (mode == TimerMode.COOLDOWN) HuntsConfig.settings.soloNormalCooldown else HuntsConfig.settings.soloNormalTimeLimit
        "soloMedium" -> if (mode == TimerMode.COOLDOWN) HuntsConfig.settings.soloMediumCooldown else HuntsConfig.settings.soloMediumTimeLimit
        "soloHard" -> if (mode == TimerMode.COOLDOWN) HuntsConfig.settings.soloHardCooldown else HuntsConfig.settings.soloHardTimeLimit
        "global" -> if (mode == TimerMode.COOLDOWN) HuntsConfig.settings.globalCooldown else HuntsConfig.settings.globalTimeLimit
        else -> 0
    }

    private fun adjustTimer(player: ServerPlayerEntity, configKey: String, mode: TimerMode, delta: Int) {
        val fieldName = when (configKey) {
            "soloEasy" -> if (mode == TimerMode.COOLDOWN) "soloEasyCooldown" else "soloEasyTimeLimit"
            "soloNormal" -> if (mode == TimerMode.COOLDOWN) "soloNormalCooldown" else "soloNormalTimeLimit"
            "soloMedium" -> if (mode == TimerMode.COOLDOWN) "soloMediumCooldown" else "soloMediumTimeLimit"
            "soloHard" -> if (mode == TimerMode.COOLDOWN) "soloHardCooldown" else "soloHardTimeLimit"
            "global" -> if (mode == TimerMode.COOLDOWN) "globalCooldown" else "globalTimeLimit"
            else -> return
        }
        val currentValue = getTimerValue(configKey, mode)
        val newValue = (currentValue + delta).coerceAtLeast(0)

        if (currentValue == newValue && delta < 0 ) {
            player.sendMessage(Text.literal("Cannot set below 0 seconds.").styled{it.withColor(Formatting.RED)}, true)
            return
        }

        when (fieldName) {
            "soloEasyCooldown" -> HuntsConfig.settings.soloEasyCooldown = newValue
            "soloNormalCooldown" -> HuntsConfig.settings.soloNormalCooldown = newValue
            "soloMediumCooldown" -> HuntsConfig.settings.soloMediumCooldown = newValue
            "soloHardCooldown" -> HuntsConfig.settings.soloHardCooldown = newValue
            "globalCooldown" -> HuntsConfig.settings.globalCooldown = newValue
            "soloEasyTimeLimit" -> HuntsConfig.settings.soloEasyTimeLimit = newValue
            "soloNormalTimeLimit" -> HuntsConfig.settings.soloNormalTimeLimit = newValue
            "soloMediumTimeLimit" -> HuntsConfig.settings.soloMediumTimeLimit = newValue
            "soloHardTimeLimit" -> HuntsConfig.settings.soloHardTimeLimit = newValue
            "globalTimeLimit" -> HuntsConfig.settings.globalTimeLimit = newValue
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

    private fun adjustActiveGlobalHunts(player: ServerPlayerEntity, delta: Int) {
        val currentValue = HuntsConfig.settings.activeGlobalHuntsAtOnce
        val newValue = (currentValue + delta).coerceIn(1, 7)

        if (currentValue == newValue && delta != 0) {
            if (newValue == 1 && delta < 0) player.sendMessage(Text.literal("Cannot set below 1.").styled{it.withColor(Formatting.RED)}, true)
            if (newValue == 7 && delta > 0) player.sendMessage(Text.literal("Cannot set above 7.").styled{it.withColor(Formatting.RED)}, true)
            return
        }

        HuntsConfig.settings.activeGlobalHuntsAtOnce = newValue
        HuntsConfig.saveConfig()
        CustomGui.refreshGui(player, generateLayout(player))
        player.sendMessage(
            Text.literal("Set Active Global Hunts At Once to ").styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal("$newValue").styled { it.withColor(Formatting.AQUA) }),
            true
        )
    }
}