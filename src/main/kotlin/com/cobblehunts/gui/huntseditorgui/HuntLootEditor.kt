package com.cobblehunts.gui.huntseditorgui

import com.cobblehunts.utils.HuntsConfig
import com.cobblehunts.utils.LootReward
import com.cobblehunts.utils.CommandReward
import com.cobblehunts.utils.ItemReward
import com.cobblehunts.utils.SerializableItemStack
import com.everlastingutils.gui.*
import com.google.gson.JsonElement
import com.mojang.serialization.DynamicOps
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.RegistryOps
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ClickType
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

object LootPoolSelectionGui {
    private val playerPages = ConcurrentHashMap<ServerPlayerEntity, Int>()
    private val playerTypes = ConcurrentHashMap<ServerPlayerEntity, String>()
    private val playerTiers = ConcurrentHashMap<ServerPlayerEntity, String>()
    private const val ITEMS_PER_PAGE = 45

    private object Slots {
        const val BACK = 49
        const val PREV_PAGE = 45
        const val NEXT_PAGE = 53
        const val ADD_COMMAND = 50
    }

    object Textures {
        const val PREV_PAGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTMzYWQ1YzIyZGIxNjQzNWRhYWQ2MTU5MGFiYTUxZDkzNzkxNDJkZDU1NmQ2YzQyMmE3MTEwY2EzYWJlYTUwIn19fQ=="
        const val NEXT_PAGE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU0MDNjYzdiYmFjNzM2NzBiZDU0M2Y2YjA5NTViYWU3YjhlOTEyM2Q4M2JkNzYwZjYyMDRjNWFmZDhiZTdlMSJ9fX0="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        const val ADD_COMMAND = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2Q4YWY5ODBmZjYwODg2N2I2YmMzNzM2MTExYzRjMDFiNDFmMWZmNjg5OWEyMWU1OTg1OGJjZTNkNDRkY2Y4NiJ9fX0="
    }

    fun createCancelButton(): ItemStack {
        return CustomGui.createPlayerHeadButton(
            textureName = "Cancel",
            title = Text.literal("Cancel").styled { it.withColor(Formatting.RED) },
            lore = listOf(Text.literal("Click to cancel").styled { it.withColor(Formatting.GRAY) }),
            textureValue = Textures.BACK
        )
    }

    fun createPlaceholderOutput(message: String): ItemStack {
        return ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE).apply {
            setCustomName(Text.literal(message).styled { it.withColor(Formatting.GRAY) })
        }
    }

    fun openGui(player: ServerPlayerEntity, type: String, tier: String) {
        playerTypes[player] = type
        playerTiers[player] = tier
        playerPages[player] = 0
        val registryOps = RegistryOps.of(com.mojang.serialization.JsonOps.INSTANCE, player.server.registryManager)
        CustomGui.openGui(
            player = player,
            title = "Edit ${type.replaceFirstChar { it.titlecase() }} ${tier.replaceFirstChar { it.titlecase() }} Loot Pool",
            layout = generateSelectionLayout(player, type, tier, 0, registryOps),
            onInteract = { context -> handleInteraction(context, player, registryOps) },
            onClose = { _ ->
                cleanupPlayerData(player)
                HuntsConfig.saveConfig()
            }
        )
    }

    private fun generateSelectionLayout(
        player: ServerPlayerEntity,
        type: String,
        tier: String,
        page: Int,
        ops: DynamicOps<JsonElement>
    ): List<ItemStack> {
        val layout = MutableList(54) { if (it >= ITEMS_PER_PAGE) createFillerPane() else ItemStack.EMPTY }
        val lootPool = HuntsConfig.getLootList(type, tier)
        val startIndex = page * ITEMS_PER_PAGE
        val endIndex = min(startIndex + ITEMS_PER_PAGE, lootPool.size)
        for (i in startIndex until endIndex) {
            val slot = i - startIndex
            val reward = lootPool[i]
            layout[slot] = createLootRewardButton(reward, ops)
        }
        if (page > 0) {
            layout[Slots.PREV_PAGE] = CustomGui.createPlayerHeadButton(
                textureName = "Previous",
                title = Text.literal("Previous").styled { it.withColor(Formatting.YELLOW) },
                lore = listOf(Text.literal("Go to previous page").styled { it.withColor(Formatting.GRAY) }),
                textureValue = Textures.PREV_PAGE
            )
        }
        layout[Slots.BACK] = CustomGui.createPlayerHeadButton(
            textureName = "Back",
            title = Text.literal("Back").styled { it.withColor(Formatting.YELLOW) },
            lore = listOf(Text.literal("Return to tier selection").styled { it.withColor(Formatting.GRAY) }),
            textureValue = Textures.BACK
        )
        if (lootPool.size - startIndex >= ITEMS_PER_PAGE) {
            layout[Slots.NEXT_PAGE] = CustomGui.createPlayerHeadButton(
                textureName = "Next",
                title = Text.literal("Next").styled { it.withColor(Formatting.GREEN) },
                lore = listOf(Text.literal("Go to next page").styled { it.withColor(Formatting.GRAY) }),
                textureValue = Textures.NEXT_PAGE
            )
        }

        // --- MODIFIED BUTTON LORE ---
        val addRewardLore = listOf(
            Text.literal("Click to add a command reward").styled { it.withColor(Formatting.YELLOW) },
            Text.literal(""),
            Text.literal("Placeholders: ").styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal("%player%").styled { it.withColor(Formatting.AQUA) }),
            Text.literal("Example: ").styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal("give %player% dirt").styled { it.withColor(Formatting.WHITE) }),
            Text.literal(""),
            Text.literal("――――――――――――――――――").styled { it.withColor(Formatting.DARK_GRAY) },
            Text.literal("To Add An Item Reward:").styled { it.withColor(Formatting.GOLD).withUnderline(true) },
            Text.literal("Drag & drop an item from your").styled { it.withColor(Formatting.YELLOW) },
            Text.literal("inventory into an empty slot.").styled { it.withColor(Formatting.YELLOW) }
        )

        layout[Slots.ADD_COMMAND] = CustomGui.createPlayerHeadButton(
            textureName = "AddReward",
            title = Text.literal("Add a Reward").styled { it.withColor(Formatting.GREEN) },
            lore = addRewardLore,
            textureValue = Textures.ADD_COMMAND
        )
        // --- END OF MODIFICATION ---

        return layout
    }

    private fun handleInteraction(context: InteractionContext, player: ServerPlayerEntity, ops: DynamicOps<JsonElement>) {
        val type = playerTypes[player] ?: return
        val tier = playerTiers[player] ?: return
        val page = playerPages[player] ?: 0
        val lootPool = HuntsConfig.getLootList(type, tier)
        when (context.slotIndex) {
            Slots.ADD_COMMAND -> openCommandInputGui(player, type, tier)
            Slots.PREV_PAGE -> if (page > 0) {
                playerPages[player] = page - 1
                CustomGui.refreshGui(player, generateSelectionLayout(player, type, tier, page - 1, ops))
            }
            Slots.BACK -> {
                HuntsConfig.saveConfig()
                player.closeHandledScreen()
                player.server.execute {
                    if (type == "global") {
                        HuntsEditorMainGui.openGui(player)
                    } else {
                        HuntsTierSelectionGui.openGui(player, type)
                    }
                }
            }
            Slots.NEXT_PAGE -> {
                val totalPages = if (lootPool.size % ITEMS_PER_PAGE == 0) {
                    lootPool.size / ITEMS_PER_PAGE + 1
                } else {
                    (lootPool.size + ITEMS_PER_PAGE) / ITEMS_PER_PAGE
                }
                if (page < totalPages - 1) {
                    playerPages[player] = page + 1
                    CustomGui.refreshGui(player, generateSelectionLayout(player, type, tier, page + 1, ops))
                }
            }
            in 0 until ITEMS_PER_PAGE -> {
                if (!context.clickedStack.isEmpty()) {
                    val index = page * ITEMS_PER_PAGE + context.slotIndex
                    if (index < lootPool.size) {
                        val reward = lootPool[index]
                        when (context.clickType) {
                            // **Reversed: Right-click to edit, Left-click to remove**
                            ClickType.RIGHT -> LootRewardEditGui.openGui(player, type, tier, reward)
                            ClickType.LEFT -> {
                                lootPool.removeAt(index)
                                HuntsConfig.saveConfig()
                                player.server.execute {
                                    CustomGui.refreshGui(player, generateSelectionLayout(player, type, tier, page, ops))
                                }
                                player.sendMessage(Text.literal("Removed reward from $type $tier loot"), false)
                            }
                        }
                    }
                } else {
                    val cursorStack = player.currentScreenHandler.getCursorStack()
                    if (!cursorStack.isEmpty()) {
                        val serializableItemStack = SerializableItemStack.fromItemStack(cursorStack.copy(), ops)
                        val reward = ItemReward(serializableItemStack = serializableItemStack, chance = 1.0)
                        lootPool.add(reward)
                        HuntsConfig.saveConfig()
                        player.currentScreenHandler.setCursorStack(ItemStack.EMPTY)
                        player.server.execute {
                            CustomGui.refreshGui(player, generateSelectionLayout(player, type, tier, page, ops))
                        }
                        player.sendMessage(Text.literal("Added ${cursorStack.item.name.string} to $type $tier loot"), false)
                    }
                }
            }
        }
    }

    private fun createLootRewardButton(reward: LootReward, ops: DynamicOps<JsonElement>): ItemStack {
        return when (reward) {
            is ItemReward -> {
                val item = reward.serializableItemStack?.toItemStack(ops) ?: ItemStack(Items.PAPER)
                val lore = listOf(
                    Text.literal("Chance: ").styled { it.withColor(Formatting.GRAY) }
                        .append(Text.literal("%.1f%%".format(reward.chance * 100)).styled { it.withColor(Formatting.RED) }),
                    Text.literal(""),
                    // **Updated lore to reflect reversed actions**
                    Text.literal("Right-click to edit").styled { it.withColor(Formatting.YELLOW) },
                    Text.literal("Left-click to remove").styled { it.withColor(Formatting.YELLOW) }
                )
                CustomGui.setItemLore(item, lore)
                item
            }
            is CommandReward -> {
                val displayItem = reward.serializableItemStack?.toItemStack(ops)
                    ?: ItemStack(Items.COMMAND_BLOCK).apply { setCustomName(Text.literal("Command Reward").styled { it.withColor(Formatting.GOLD) }) }
                val lore = mutableListOf<Text>()
                lore.add(Text.literal("Command: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("/${reward.command}").styled { it.withColor(Formatting.WHITE) }))
                lore.add(Text.literal("Chance: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("%.1f%%".format(reward.chance * 100)).styled { it.withColor(Formatting.RED) }))
                lore.add(Text.literal(""))
                // **Updated lore to reflect reversed actions**
                lore.add(Text.literal("Right-click to edit").styled { it.withColor(Formatting.YELLOW) })
                lore.add(Text.literal("Left-click to remove").styled { it.withColor(Formatting.YELLOW) })
                CustomGui.setItemLore(displayItem, lore)
                displayItem
            }
        }
    }

    private fun openCommandInputGui(player: ServerPlayerEntity, type: String, tier: String) {
        val cancelButton = createCancelButton()
        val blockedInput = ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
        val placeholderOutput = createPlaceholderOutput("Enter command")
        AnvilGuiManager.openAnvilGui(
            player = player,
            id = "add_command_reward_${type}_$tier",
            title = "Add Command Reward",
            initialText = "",
            leftItem = cancelButton,
            rightItem = blockedInput,
            resultItem = placeholderOutput,
            onLeftClick = { _ ->
                player.sendMessage(Text.literal("§7Adding command reward cancelled."), false)
                player.closeHandledScreen()
                player.server.execute {
                    val registryOps = RegistryOps.of(com.mojang.serialization.JsonOps.INSTANCE, player.server.registryManager)
                    val page = playerPages[player] ?: 0
                    playerTypes[player] = type
                    playerTiers[player] = tier
                    playerPages[player] = page
                    CustomGui.openGui(
                        player,
                        "Edit ${type.replaceFirstChar { it.titlecase() }} ${tier.replaceFirstChar { it.titlecase() }} Loot Pool",
                        generateSelectionLayout(player, type, tier, page, registryOps),
                        { context -> handleInteraction(context, player, registryOps) },
                        { _ ->
                            cleanupPlayerData(player)
                            HuntsConfig.saveConfig()
                        }
                    )
                }
            },
            onRightClick = null,
            onResultClick = { context ->
                val command = context.handler.currentText.trim()
                if (command.isNotEmpty()) {
                    val registryOps = RegistryOps.of(com.mojang.serialization.JsonOps.INSTANCE, player.server.registryManager)
                    val defaultStack = SerializableItemStack.fromItemStack(ItemStack(Items.PAPER), registryOps)
                    val reward = CommandReward(command = command, chance = 1.0, serializableItemStack = defaultStack)
                    val lootPool = HuntsConfig.getLootList(type, tier)
                    lootPool.add(reward)
                    HuntsConfig.saveConfig()
                    player.sendMessage(Text.literal("Added command reward: /$command to $type $tier loot"), false)
                    player.closeHandledScreen()
                    player.server.execute {
                        val ops = RegistryOps.of(com.mojang.serialization.JsonOps.INSTANCE, player.server.registryManager)
                        val page = playerPages[player] ?: 0
                        playerTypes[player] = type
                        playerTiers[player] = tier
                        playerPages[player] = page
                        CustomGui.openGui(
                            player,
                            "Edit ${type.replaceFirstChar { it.titlecase() }} ${tier.replaceFirstChar { it.titlecase() }} Loot Pool",
                            generateSelectionLayout(player, type, tier, page, ops),
                            { context -> handleInteraction(context, player, ops) },
                            { _ ->
                                cleanupPlayerData(player)
                                HuntsConfig.saveConfig()
                            }
                        )
                    }
                }
            },
            onTextChange = { text ->
                val handler = player.currentScreenHandler as? FullyModularAnvilScreenHandler
                if (text.isNotEmpty()) {
                    val addButton = CustomGui.createPlayerHeadButton(
                        textureName = "AddCommand",
                        title = Text.literal("Add Command: ").styled { it.withColor(Formatting.GREEN) }
                            .append(Text.literal("/$text").styled { it.withColor(Formatting.WHITE) }),
                        lore = listOf(Text.literal("Click to add").styled { it.withColor(Formatting.GRAY) }),
                        textureValue = Textures.ADD_COMMAND
                    )
                    handler?.updateSlot(2, addButton)
                } else {
                    handler?.updateSlot(2, placeholderOutput)
                }
            },
            onClose = { HuntsConfig.saveConfig() }
        )
        player.server.execute { (player.currentScreenHandler as? FullyModularAnvilScreenHandler)?.clearTextField() }
        player.sendMessage(Text.literal("Enter a command to add as a reward..."), false)
    }

    private fun cleanupPlayerData(player: ServerPlayerEntity) {
        playerPages.remove(player)
        playerTypes.remove(player)
        playerTiers.remove(player)
    }

    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
    }
}

object LootRewardEditGui {
    private object Slots {
        const val ITEM_DISPLAY = 0
        const val EDIT_TITLE = 1
        const val ADD_LORE = 2
        const val REMOVE_LORE = 3
        const val EDIT_COUNT = 4
        const val ADD_ENCHANT = 5
        const val EDIT_COMMAND = 6
        const val DECREASE_LARGE = 19
        const val DECREASE_MEDIUM = 20
        const val DECREASE_SMALL = 21
        const val CHANCE_DISPLAY = 22
        const val INCREASE_SMALL = 23
        const val INCREASE_MEDIUM = 24
        const val INCREASE_LARGE = 25
        const val BACK = 49
    }

    private object Textures {
        const val INCREASE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTU3YTViZGY0MmYxNTIxNzhkMTU0YmIyMjM3ZDlmZDM1NzcyYTdmMzJiY2ZkMzNiZWViOGVkYzQ4MjBiYSJ9fX0="
        const val DECREASE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTZhMDExZTYyNmI3MWNlYWQ5ODQxOTM1MTFlODJlNjVjMTM1OTU2NWYwYTJmY2QxMTg0ODcyZjg5ZDkwOGM2NSJ9fX0="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        const val EDIT_COUNT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODg5ZWUwYjdmZWY5NTdlZDliNDY0NzU2ZTllNTYxNTQ2OGE5YzQwYzZjMGIxM2Y0NTFmMzNiNDEwMzg5MWVhYiJ9fX0="
        const val ADD_ENCHANT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTU3YTViZGY0MmYxNTIxNzhkMTU0YmIyMjM3ZDlmZDM1NzcyYTdmMzJiY2ZkMzNiZWViOGVkYzQ4MjBiYSJ9fX0="
        const val EDIT_COMMAND = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTU2MTI3OWFhYjM5ZmZmNDljODk2ZDhhNDg0NTQ3NDRlZjNkODdmNTYxMTQ3NDhhYzVlMzI4ZWY3ODA4N2U4In19fQ=="
    }

    fun openGui(player: ServerPlayerEntity, type: String, tier: String, reward: LootReward) {
        val registryOps = RegistryOps.of(com.mojang.serialization.JsonOps.INSTANCE, player.server.registryManager)
        CustomGui.openGui(
            player = player,
            title = "Edit Loot Reward",
            layout = generateEditLayout(reward, registryOps),
            onInteract = { context -> handleEditInteraction(context, player, type, tier, reward, registryOps) },
            onClose = { _ -> HuntsConfig.saveConfig() }
        )
    }

    fun generateEditLayout(reward: LootReward, ops: DynamicOps<JsonElement>): List<ItemStack> {
        val layout = MutableList(54) { createFillerPane() }
        when (reward) {
            is ItemReward -> {
                val item = reward.serializableItemStack?.toItemStack(ops)?.copy() ?: ItemStack(Items.PAPER)
                // **Updated lore for ItemReward in edit GUI**
                val lore = listOf(
                    Text.literal("Drag item to change").styled { it.withColor(Formatting.YELLOW) }
                )
                CustomGui.setItemLore(item, lore)
                layout[Slots.ITEM_DISPLAY] = item
            }
            is CommandReward -> {
                val displayItem = reward.serializableItemStack?.toItemStack(ops)
                    ?: ItemStack(Items.COMMAND_BLOCK).apply {
                        setCustomName(Text.literal("Command Reward").styled { it.withColor(Formatting.GOLD) })
                    }
                // **Updated lore for CommandReward in edit GUI**
                val lore = mutableListOf<Text>()
                lore.add(Text.literal("Command: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("/${reward.command}").styled { it.withColor(Formatting.WHITE) }))
                lore.add(Text.literal("Chance: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("%.1f%%".format(reward.chance * 100)).styled { it.withColor(Formatting.RED) }))
                lore.add(Text.literal(""))
                lore.add(Text.literal("Drag item to change display").styled { it.withColor(Formatting.YELLOW) })
                lore.add(Text.literal("Right-click to reset display").styled { it.withColor(Formatting.YELLOW) })
                CustomGui.setItemLore(displayItem, lore)
                layout[Slots.ITEM_DISPLAY] = displayItem.copy()
                layout[Slots.EDIT_TITLE] = CustomGui.createPlayerHeadButton(
                    textureName = "EditTitle",
                    title = Text.literal("Edit Title").styled { it.withColor(Formatting.GREEN) },
                    lore = listOf(Text.literal("Click to edit display title")),
                    textureValue = Textures.INCREASE
                )
                layout[Slots.ADD_LORE] = CustomGui.createPlayerHeadButton(
                    textureName = "AddLore",
                    title = Text.literal("Add Lore").styled { it.withColor(Formatting.GREEN) },
                    lore = listOf(Text.literal("Click to add a lore line")),
                    textureValue = Textures.INCREASE
                )
                layout[Slots.REMOVE_LORE] = CustomGui.createPlayerHeadButton(
                    textureName = "RemoveLore",
                    title = Text.literal("Remove Lore").styled { it.withColor(Formatting.RED) },
                    lore = listOf(Text.literal("Click to remove a lore line")),
                    textureValue = Textures.DECREASE
                )
                layout[Slots.EDIT_COUNT] = CustomGui.createPlayerHeadButton(
                    textureName = "EditCount",
                    title = Text.literal("Edit Count").styled { it.withColor(Formatting.AQUA) },
                    lore = listOf(Text.literal("Click to edit item count")),
                    textureValue = Textures.EDIT_COUNT
                )
                layout[Slots.ADD_ENCHANT] = CustomGui.createPlayerHeadButton(
                    textureName = "AddEnchant",
                    title = Text.literal("Add Enchant").styled { it.withColor(Formatting.LIGHT_PURPLE) },
                    lore = listOf(
                        Text.literal("Left-click to add an enchantment").styled { it.withColor(Formatting.YELLOW) },
                        Text.literal("Right-click to toggle enchant glint").styled { it.withColor(Formatting.YELLOW) }
                    ),
                    textureValue = Textures.ADD_ENCHANT
                )
                layout[Slots.EDIT_COMMAND] = CustomGui.createPlayerHeadButton(
                    textureName = "EditCommand",
                    title = Text.literal("Edit Command").styled { it.withColor(Formatting.AQUA) },
                    lore = listOf(Text.literal("Click to edit the command").styled { it.withColor(Formatting.YELLOW) }),
                    textureValue = Textures.EDIT_COMMAND
                )
            }
        }
        layout[Slots.CHANCE_DISPLAY] = ItemStack(Items.PAPER).apply {
            setCustomName(
                Text.literal("Chance: ").styled { it.withColor(Formatting.GRAY) }
                    .append(Text.literal("%.1f%%".format(reward.chance * 100)).styled { it.withColor(Formatting.AQUA) })
            )
        }
        layout[Slots.DECREASE_LARGE] = createChanceButton("Decrease Large", -1.0, -5.0, reward.chance)
        layout[Slots.DECREASE_MEDIUM] = createChanceButton("Decrease Medium", -0.5, -1.0, reward.chance)
        layout[Slots.DECREASE_SMALL] = createChanceButton("Decrease Small", -0.1, -0.5, reward.chance)
        layout[Slots.INCREASE_SMALL] = createChanceButton("Increase Small", 0.1, 0.5, reward.chance)
        layout[Slots.INCREASE_MEDIUM] = createChanceButton("Increase Medium", 0.5, 1.0, reward.chance)
        layout[Slots.INCREASE_LARGE] = createChanceButton("Increase Large", 1.0, 5.0, reward.chance)
        layout[Slots.BACK] = CustomGui.createPlayerHeadButton(
            textureName = "Back",
            title = Text.literal("Back").styled { it.withColor(Formatting.YELLOW) },
            lore = listOf(Text.literal("Return to loot pool").styled { it.withColor(Formatting.GRAY) }),
            textureValue = Textures.BACK
        )
        return layout
    }

    private fun handleEditInteraction(
        context: InteractionContext,
        player: ServerPlayerEntity,
        type: String,
        tier: String,
        reward: LootReward,
        ops: DynamicOps<JsonElement>
    ) {
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
            reward.chance = (reward.chance + deltaDecimal).coerceIn(0.0, 1.0)
            HuntsConfig.saveConfig()
            CustomGui.refreshGui(player, generateEditLayout(reward, ops))
            player.sendMessage(Text.literal("Adjusted chance to %.1f%%".format(reward.chance * 100)), false)
            return
        }
        when (context.slotIndex) {
            Slots.ITEM_DISPLAY -> {
                val cursorStack = player.currentScreenHandler.getCursorStack()
                if (!cursorStack.isEmpty()) {
                    // **Handle dragging/placing an item to change for both reward types**
                    val serializable = SerializableItemStack.fromItemStack(cursorStack.copy(), ops)
                    if (reward is ItemReward) {
                        reward.serializableItemStack = serializable
                        player.currentScreenHandler.setCursorStack(ItemStack.EMPTY)
                        HuntsConfig.saveConfig()
                        player.server.execute { CustomGui.refreshGui(player, generateEditLayout(reward, ops)) }
                        player.sendMessage(Text.literal("Changed item reward"), false)
                    } else if (reward is CommandReward) {
                        reward.serializableItemStack = serializable
                        player.currentScreenHandler.setCursorStack(ItemStack.EMPTY)
                        HuntsConfig.saveConfig()
                        player.server.execute { CustomGui.refreshGui(player, generateEditLayout(reward, ops)) }
                        player.sendMessage(Text.literal("Set display item for command reward"), false)
                    }
                } else if (context.clickType == ClickType.RIGHT && reward is CommandReward) {
                    // **Right-click to reset display item for CommandReward**
                    val defaultStack = ItemStack(Items.PAPER)
                    reward.serializableItemStack = SerializableItemStack.fromItemStack(defaultStack, ops)
                    HuntsConfig.saveConfig()
                    player.server.execute { CustomGui.refreshGui(player, generateEditLayout(reward, ops)) }
                    player.sendMessage(Text.literal("Reset display item for command reward"), false)
                }
            }
            Slots.EDIT_TITLE -> if (reward is CommandReward) {
                val currentStack = reward.serializableItemStack?.toItemStack(ops) ?: ItemStack(Items.COMMAND_BLOCK)
                val currentTitle = currentStack.get(DataComponentTypes.CUSTOM_NAME)?.string ?: ""
                AnvilGuiManager.openAnvilGui(
                    player = player,
                    id = "edit_command_title",
                    title = "Edit Display Title",
                    initialText = currentTitle,
                    leftItem = LootPoolSelectionGui.createCancelButton(),
                    rightItem = ItemStack(Items.PAPER),
                    resultItem = LootPoolSelectionGui.createPlaceholderOutput("Enter title"),
                    onLeftClick = { _ -> player.closeHandledScreen() },
                    onRightClick = null,
                    onResultClick = { ctx ->
                        val newTitle = ctx.handler.currentText.trim()
                        if (newTitle.isNotEmpty()) {
                            val itemStack = reward.serializableItemStack?.toItemStack(ops) ?: ItemStack(Items.COMMAND_BLOCK)
                            itemStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(newTitle))
                            reward.serializableItemStack = SerializableItemStack.fromItemStack(itemStack, ops)
                            HuntsConfig.saveConfig()
                            player.sendMessage(Text.literal("Title updated to: $newTitle"), false)
                            player.closeHandledScreen()
                            player.server.execute { openGui(player, type, tier, reward) }
                        }
                    },
                    onTextChange = { text ->
                        val handler = player.currentScreenHandler as? FullyModularAnvilScreenHandler
                        if (text.isNotEmpty()) {
                            val setTitleButton = CustomGui.createPlayerHeadButton(
                                textureName = "SetTitle",
                                title = Text.literal("Set Title: ").styled { it.withColor(Formatting.GREEN) }
                                    .append(Text.literal(text).styled { it.withColor(Formatting.WHITE) }),
                                lore = listOf(Text.literal("Click to set title").styled { it.withColor(Formatting.GRAY) }),
                                textureValue = Textures.INCREASE
                            )
                            handler?.updateSlot(2, setTitleButton)
                        }
                    },
                    onClose = { HuntsConfig.saveConfig() }
                )
            }
            Slots.EDIT_COUNT -> if (reward is CommandReward) {
                val currentStack = reward.serializableItemStack?.toItemStack(ops) ?: ItemStack(Items.COMMAND_BLOCK)
                val currentCount = currentStack.count
                AnvilGuiManager.openAnvilGui(
                    player = player,
                    id = "edit_command_count",
                    title = "Edit Item Count",
                    initialText = currentCount.toString(),
                    leftItem = LootPoolSelectionGui.createCancelButton(),
                    rightItem = ItemStack(Items.PAPER),
                    resultItem = LootPoolSelectionGui.createPlaceholderOutput("Enter new count"),
                    onLeftClick = { _ -> player.closeHandledScreen() },
                    onRightClick = null,
                    onResultClick = { ctx ->
                        val newCountStr = ctx.handler.currentText.trim()
                        val newCount = newCountStr.toIntOrNull()
                        if (newCount != null && newCount > 0) {
                            currentStack.count = newCount
                            reward.serializableItemStack = SerializableItemStack.fromItemStack(currentStack, ops)
                            HuntsConfig.saveConfig()
                            player.sendMessage(Text.literal("Count updated to: $newCount"), false)
                            player.closeHandledScreen()
                            player.server.execute { openGui(player, type, tier, reward) }
                        } else {
                            player.sendMessage(Text.literal("Invalid count"), false)
                        }
                    },
                    onTextChange = { text ->
                        val handler = player.currentScreenHandler as? FullyModularAnvilScreenHandler
                        if (text.isNotEmpty()) {
                            val setCountButton = CustomGui.createPlayerHeadButton(
                                textureName = "SetCount",
                                title = Text.literal("Set Count: ").styled { it.withColor(Formatting.GREEN) }
                                    .append(Text.literal(text).styled { it.withColor(Formatting.WHITE) }),
                                lore = listOf(Text.literal("Click to set count").styled { it.withColor(Formatting.GRAY) }),
                                textureValue = Textures.INCREASE
                            )
                            handler?.updateSlot(2, setCountButton)
                        }
                    },
                    onClose = { HuntsConfig.saveConfig() }
                )
            }
            Slots.ADD_ENCHANT -> if (reward is CommandReward) {
                if (context.clickType == ClickType.LEFT) {
                    AnvilGuiManager.openAnvilGui(
                        player = player,
                        id = "add_command_enchant",
                        title = "Add Enchantment",
                        initialText = "",
                        leftItem = LootPoolSelectionGui.createCancelButton(),
                        rightItem = ItemStack(Items.PAPER),
                        resultItem = LootPoolSelectionGui.createPlaceholderOutput("Enter enchantment data"),
                        onLeftClick = { _ -> player.closeHandledScreen() },
                        onRightClick = null,
                        onResultClick = { ctx ->
                            val enchantInput = ctx.handler.currentText.trim()
                            val parts = enchantInput.split(",")
                            if (parts.size == 2) {
                                try {
                                    val enchantId = parts[0].trim()
                                    val level = parts[1].trim().toInt()
                                    val identifier = Identifier.tryParse(enchantId)
                                    if (identifier == null) {
                                        player.sendMessage(Text.literal("Invalid enchantment id: $enchantId"), false)
                                        return@openAnvilGui
                                    }
                                    val enchantmentLookup = player.server.registryManager.get(RegistryKeys.ENCHANTMENT)
                                    val enchantmentEntryOpt = enchantmentLookup.getEntry(identifier)
                                    if (!enchantmentEntryOpt.isPresent) {
                                        player.sendMessage(Text.literal("Enchantment not found: $enchantId"), false)
                                        return@openAnvilGui
                                    }
                                    val enchantmentEntry = enchantmentEntryOpt.get()
                                    val itemStack = reward.serializableItemStack?.toItemStack(ops) ?: ItemStack(Items.COMMAND_BLOCK)
                                    itemStack.addEnchantment(enchantmentEntry, level)
                                    reward.serializableItemStack = SerializableItemStack.fromItemStack(itemStack, ops)
                                    HuntsConfig.saveConfig()
                                    player.sendMessage(Text.literal("Enchantment added: $enchantId level $level"), false)
                                    player.closeHandledScreen()
                                    player.server.execute { openGui(player, type, tier, reward) }
                                } catch (e: Exception) {
                                    player.sendMessage(Text.literal("Invalid enchant input"), false)
                                }
                            } else {
                                player.sendMessage(Text.literal("Input format must be: enchantmentId,level! Example: minecraft:sharpness,5"), false)
                            }
                        },
                        onTextChange = { text ->
                            val handler = player.currentScreenHandler as? FullyModularAnvilScreenHandler
                            if (text.isNotEmpty()) {
                                val enchantButton = CustomGui.createPlayerHeadButton(
                                    textureName = "AddEnchant",
                                    title = Text.literal("Add Enchant: ").styled { it.withColor(Formatting.LIGHT_PURPLE) }
                                        .append(Text.literal(text).styled { it.withColor(Formatting.WHITE) }),
                                    lore = listOf(Text.literal("Format: enchantmentId,level\nExample: minecraft:sharpness,5").styled { it.withColor(Formatting.GRAY) }),
                                    textureValue = Textures.ADD_ENCHANT
                                )
                                handler?.updateSlot(2, enchantButton)
                            }
                        },
                        onClose = { HuntsConfig.saveConfig() }
                    )
                } else if (context.clickType == ClickType.RIGHT) {
                    val itemStack = reward.serializableItemStack?.toItemStack(ops) ?: return
                    val currentGlint = (itemStack.get(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE) as? Boolean) ?: false
                    itemStack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, !currentGlint)
                    if (!currentGlint) {
                        player.sendMessage(Text.literal("Enchantment glint toggled on"), false)
                    } else {
                        player.sendMessage(Text.literal("Enchantment glint toggled off"), false)
                    }
                    reward.serializableItemStack = SerializableItemStack.fromItemStack(itemStack, ops)
                    HuntsConfig.saveConfig()
                    player.server.execute { CustomGui.refreshGui(player, generateEditLayout(reward, ops)) }
                }
            }
            Slots.ADD_LORE -> if (reward is CommandReward) {
                AnvilGuiManager.openAnvilGui(
                    player = player,
                    id = "add_command_lore",
                    title = "Add Lore Line",
                    initialText = "",
                    leftItem = LootPoolSelectionGui.createCancelButton(),
                    rightItem = ItemStack(Items.PAPER),
                    resultItem = LootPoolSelectionGui.createPlaceholderOutput("Enter lore line"),
                    onLeftClick = { _ -> player.closeHandledScreen() },
                    onRightClick = null,
                    onResultClick = { ctx ->
                        val newLoreLine = ctx.handler.currentText.trim()
                        if (newLoreLine.isNotEmpty()) {
                            val itemStack = reward.serializableItemStack?.toItemStack(ops) ?: ItemStack(Items.COMMAND_BLOCK)
                            val currentLore = itemStack.getOrDefault(DataComponentTypes.LORE, LoreComponent(emptyList())).lines()
                            val newLore = currentLore + Text.literal(newLoreLine)
                            itemStack.set(DataComponentTypes.LORE, LoreComponent(newLore))
                            reward.serializableItemStack = SerializableItemStack.fromItemStack(itemStack, ops)
                            HuntsConfig.saveConfig()
                            player.sendMessage(Text.literal("Added lore line: $newLoreLine"), false)
                            player.closeHandledScreen()
                            player.server.execute { openGui(player, type, tier, reward) }
                        }
                    },
                    onTextChange = { text ->
                        val handler = player.currentScreenHandler as? FullyModularAnvilScreenHandler
                        if (text.isNotEmpty()) {
                            val addLoreButton = CustomGui.createPlayerHeadButton(
                                textureName = "AddLore",
                                title = Text.literal("Add Lore: ").styled { it.withColor(Formatting.GREEN) }
                                    .append(Text.literal(text).styled { it.withColor(Formatting.WHITE) }),
                                lore = listOf(Text.literal("Click to add lore line").styled { it.withColor(Formatting.GRAY) }),
                                textureValue = Textures.INCREASE
                            )
                            handler?.updateSlot(2, addLoreButton)
                        }
                    },
                    onClose = { HuntsConfig.saveConfig() }
                )
            }
            Slots.REMOVE_LORE -> if (reward is CommandReward) {
                val itemStack = reward.serializableItemStack?.toItemStack(ops) ?: ItemStack(Items.COMMAND_BLOCK)
                val currentLore = itemStack.getOrDefault(DataComponentTypes.LORE, LoreComponent(emptyList())).lines()
                if (currentLore.isNotEmpty()) {
                    AnvilGuiManager.openAnvilGui(
                        player = player,
                        id = "remove_command_lore",
                        title = "Remove Lore Line",
                        initialText = "",
                        leftItem = LootPoolSelectionGui.createCancelButton(),
                        rightItem = ItemStack(Items.PAPER),
                        resultItem = LootPoolSelectionGui.createPlaceholderOutput("Enter lore index to remove"),
                        onLeftClick = { _ -> player.closeHandledScreen() },
                        onRightClick = null,
                        onResultClick = { ctx ->
                            val indexStr = ctx.handler.currentText.trim()
                            val index = indexStr.toIntOrNull()
                            if (index != null && index in currentLore.indices) {
                                val newLore = currentLore.toMutableList().apply { removeAt(index) }
                                itemStack.set(DataComponentTypes.LORE, LoreComponent(newLore))
                                reward.serializableItemStack = SerializableItemStack.fromItemStack(itemStack, ops)
                                HuntsConfig.saveConfig()
                                player.sendMessage(Text.literal("Removed lore line at index $index"), false)
                                player.closeHandledScreen()
                                player.server.execute { openGui(player, type, tier, reward) }
                            } else {
                                player.sendMessage(Text.literal("Invalid lore index"), false)
                            }
                        },
                        onTextChange = { text ->
                            val handler = player.currentScreenHandler as? FullyModularAnvilScreenHandler
                            val index = text.toIntOrNull()
                            if (index != null && index in currentLore.indices) {
                                val removeLoreButton = CustomGui.createPlayerHeadButton(
                                    textureName = "RemoveLore",
                                    title = Text.literal("Remove Lore Line ").styled { it.withColor(Formatting.RED) }
                                        .append(Text.literal("$index").styled { it.withColor(Formatting.WHITE) }),
                                    lore = listOf(Text.literal("Click to remove").styled { it.withColor(Formatting.GRAY) }),
                                    textureValue = Textures.DECREASE
                                )
                                handler?.updateSlot(2, removeLoreButton)
                            }
                        },
                        onClose = { HuntsConfig.saveConfig() }
                    )
                } else {
                    player.sendMessage(Text.literal("No lore lines to remove"), false)
                }
            }
            Slots.EDIT_COMMAND -> if (reward is CommandReward) {
                AnvilGuiManager.openAnvilGui(
                    player = player,
                    id = "edit_command_text",
                    title = "Edit Command",
                    initialText = reward.command,
                    leftItem = LootPoolSelectionGui.createCancelButton(),
                    rightItem = ItemStack(Items.PAPER),
                    resultItem = LootPoolSelectionGui.createPlaceholderOutput("Enter new command"),
                    onLeftClick = { _ -> player.closeHandledScreen() },
                    onRightClick = null,
                    onResultClick = { ctx ->
                        val newCommand = ctx.handler.currentText.trim()
                        if (newCommand.isNotEmpty()) {
                            reward.command = newCommand
                            HuntsConfig.saveConfig()
                            player.sendMessage(Text.literal("Command updated to: /$newCommand"), false)
                            player.closeHandledScreen()
                            player.server.execute { openGui(player, type, tier, reward) }
                        } else {
                            player.sendMessage(Text.literal("Command cannot be empty"), false)
                        }
                    },
                    onTextChange = { text ->
                        val handler = player.currentScreenHandler as? FullyModularAnvilScreenHandler
                        if (text.isNotEmpty()) {
                            val setCommandButton = CustomGui.createPlayerHeadButton(
                                textureName = "SetCommand",
                                title = Text.literal("Set Command: ").styled { it.withColor(Formatting.AQUA) }
                                    .append(Text.literal("/$text").styled { it.withColor(Formatting.WHITE) }),
                                lore = listOf(Text.literal("Click to set command").styled { it.withColor(Formatting.GRAY) }),
                                textureValue = Textures.EDIT_COMMAND
                            )
                            handler?.updateSlot(2, setCommandButton)
                        }
                    },
                    onClose = { HuntsConfig.saveConfig() }
                )
            }
            Slots.BACK -> {
                HuntsConfig.saveConfig()
                LootPoolSelectionGui.openGui(player, type, tier)
            }
        }
    }

    private fun createChanceButton(name: String, leftDelta: Double, rightDelta: Double, currentChance: Double): ItemStack {
        val textureValue = if (leftDelta > 0) Textures.INCREASE else Textures.DECREASE
        val title = Text.literal(name).styled { it.withColor(if (leftDelta > 0) Formatting.GREEN else Formatting.RED) }
        val lore = listOf(
            Text.literal("Left-click: ").styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal("${if (leftDelta > 0) "+" else ""}${"%.1f".format(leftDelta)}%").styled { it.withColor(if (leftDelta > 0) Formatting.GREEN else Formatting.RED) }),
            Text.literal("Right-click: ").styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal("${if (rightDelta > 0) "+" else ""}${"%.1f".format(rightDelta)}%").styled { it.withColor(if (rightDelta > 0) Formatting.GREEN else Formatting.RED) }),
            Text.literal(""),
            Text.literal("Current Chance: ").styled { it.withColor(Formatting.GRAY) }
                .append(Text.literal("%.1f%%".format(currentChance * 100)).styled { it.withColor(Formatting.AQUA) })
        )
        return CustomGui.createPlayerHeadButton(textureName = name, title = title, lore = lore, textureValue = textureValue)
    }

    private fun createFillerPane(): ItemStack {
        return ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal("")) }
    }
}