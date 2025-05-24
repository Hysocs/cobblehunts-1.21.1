package com.cobblehunts.utils

import com.everlastingutils.config.ConfigData
import com.everlastingutils.config.ConfigManager
import com.everlastingutils.config.ConfigMetadata
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.mojang.serialization.DynamicOps
import kotlinx.coroutines.runBlocking
import net.minecraft.item.ItemStack
import net.minecraft.util.JsonHelper
import java.lang.RuntimeException
import java.math.BigDecimal

/**
 * Data class representing a Pokémon entry in a hunt.
 * - species: The Pokémon's species (e.g., "pidgey").
 * - form: The Pokémon's form, if applicable (nullable).
 * - aspects: Special traits like "shiny" (set of strings).
 * - chance: Probability of this entry being selected (default 1.0).
 * - gender: Required gender for Normal+ difficulties (nullable).
 * - nature: Required nature for Medium+ difficulties (nullable).
 * - ivRange: Required IV value for Hard difficulty (nullable).
 */
data class HuntPokemonEntry(
    val species: String,
    val form: String?,
    val aspects: Set<String>,
    var chance: Double = 1.0,
    var gender: String? = null,
    var nature: String? = null,
    var ivRange: String? = null
)

/**
 * Abstract class for loot rewards with a chance of occurring.
 */
@JsonAdapter(LootRewardAdapter::class)
sealed class LootReward {
    abstract var chance: Double
}

private val GSON = Gson()

/** Serializes an ItemStack to a JSON string. */
fun serializeItemStack(itemStack: ItemStack, ops: DynamicOps<JsonElement>): String {
    val result = ItemStack.CODEC.encodeStart(ops, itemStack)
    val jsonElement = result.getOrThrow { error -> throw RuntimeException("Failed to serialize ItemStack: $error") }
    return GSON.toJson(jsonElement)
}

/** Deserializes a JSON string to an ItemStack. */
fun deserializeItemStack(jsonString: String, ops: DynamicOps<JsonElement>): ItemStack {
    val jsonElement = JsonHelper.deserialize(jsonString)
    val result = ItemStack.CODEC.parse(ops, jsonElement)
    return result.getOrThrow { error -> throw RuntimeException("Failed to deserialize ItemStack: $error") }
}

/** Data class for serializing/deserializing ItemStacks. */
data class SerializableItemStack(val itemStackString: String) {
    fun toItemStack(ops: DynamicOps<JsonElement>): ItemStack = deserializeItemStack(itemStackString, ops)
    companion object {
        fun fromItemStack(itemStack: ItemStack, ops: DynamicOps<JsonElement>): SerializableItemStack =
            SerializableItemStack(serializeItemStack(itemStack, ops))
    }
}

/** Loot reward providing an item. */
data class ItemReward(
    var serializableItemStack: SerializableItemStack,
    override var chance: Double
) : LootReward()

/** Loot reward executing a command, optionally with a display item. */
data class CommandReward(
    var command: String,
    override var chance: Double,
    var serializableItemStack: SerializableItemStack? = null
) : LootReward()

/**
 * New data class grouping permission-related configuration.
 * This makes it clear that opLevel and permissionLevel belong to the permissions section.
 */
data class HuntPermissions(
    var permissionLevel: Int = 2,
    var opLevel: Int = 2,
    var huntsPermission: String = "cobblehunts.hunts",
    var globalHuntPermission: String = "cobblehunts.global",
    var soloEasyHuntPermission: String = "cobblehunts.solo.easy",
    var soloNormalHuntPermission: String = "cobblehunts.solo.normal",
    var soloMediumHuntPermission: String = "cobblehunts.solo.medium",
    var soloHardHuntPermission: String = "cobblehunts.solo.hard",
    var rerollPermission: String = "cobblehunts.solo.reroll.use",
    var bypassRerollLimitPermission: String = "cobblehunts.solo.reroll.bypass.limit",
    var bypassRerollCostPermission: String = "cobblehunts.solo.reroll.bypass.cost"
)

/**
 * Configuration data for hunts.
 *
 * The non-pool settings are at the top, while all the spawn/loot pools are at the bottom.
 */
data class HuntsConfigData(
    override val version: String = "1.0.7",
    override val configId: String = "cobblehunts",
    var debugEnabled: Boolean = false,
    var activeGlobalHuntsAtOnce: Int = 4,
    var soloEasyCooldown: Int = 120,
    var soloNormalCooldown: Int = 120,
    var soloMediumCooldown: Int = 120,
    var soloHardCooldown: Int = 120,
    var globalCooldown: Int = 120,
    var soloEasyTimeLimit: Int = 300,
    var soloNormalTimeLimit: Int = 300,
    var soloMediumTimeLimit: Int = 300,
    var soloHardTimeLimit: Int = 300,
    var globalTimeLimit: Int = 300,
    var globalPoints: Int = 100,
    var soloEasyPoints: Int = 10,
    var soloNormalPoints: Int = 15,
    var soloMediumPoints: Int = 25,
    var soloHardPoints: Int = 40,
    var globalHuntsEnabled: Boolean = true,
    var soloHuntsEnabled: Boolean = true,
    var soloEasyEnabled: Boolean = true,
    var soloNormalEnabled: Boolean = true,
    var soloMediumEnabled: Boolean = true,
    var soloHardEnabled: Boolean = true,
    var globalHuntCompletionMessage: String = "%player% has completed a global hunt for %pokemon% and received %reward%!",
    var soloHuntCompletionMessage: String = "You received %reward%",
    var capturedPokemonMessage: String = "You caught a %pokemon% that matches an active hunt! Use /hunts to turn it in.",
    var autoAcceptSoloHunts: Boolean = false,
    var permissions: HuntPermissions = HuntPermissions(),
    var enableLeaderboard: Boolean = true,
    var takeMonOnTurnIn: Boolean = true,
    var autoTurnInOnCapture: Boolean = false,
    var rewardMode: String = "weight",
    var onlyAllowTurnInIfCapturedAfterHuntStarted: Boolean = true,
    var lockGlobalHuntsOnCompletionForAllPlayers: Boolean = true,
    var globalPokemon: MutableList<HuntPokemonEntry> = mutableListOf(),
    var soloEasyPokemon: MutableList<HuntPokemonEntry> = mutableListOf(),
    var soloNormalPokemon: MutableList<HuntPokemonEntry> = mutableListOf(),
    var soloMediumPokemon: MutableList<HuntPokemonEntry> = mutableListOf(),
    var soloHardPokemon: MutableList<HuntPokemonEntry> = mutableListOf(),
    var globalLoot: MutableList<LootReward> = mutableListOf(),
    var soloEasyLoot: MutableList<LootReward> = mutableListOf(),
    var soloNormalLoot: MutableList<LootReward> = mutableListOf(),
    var soloMediumLoot: MutableList<LootReward> = mutableListOf(),
    var soloHardLoot: MutableList<LootReward> = mutableListOf(),
    var economyEnabled: Boolean = true,
    var rerollCurrency: String = "pokedollars",
    var rerollCost: Map<String, BigDecimal> = mapOf(
        "easy" to BigDecimal("100.00"),
        "normal" to BigDecimal("200.00"),
        "medium" to BigDecimal("300.00"),
        "hard" to BigDecimal("400.00")
    ),
    var universalRerollLimit: Int = -1,
    var maxRerollsPerDay: Map<String, Int> = mapOf(
        "easy" to 3,
        "normal" to 3,
        "medium" to 3,
        "hard" to 3
    ),
    var allowRerollDuplicates: Boolean = true
) : ConfigData

object HuntsConfig {
    /**
     * Global Pokémon pool is now set to be the same as the solo easy pool.
     * All Pokémon pools have been trimmed to 10 entries.
     */
    private val defaultSoloEasyPokemon = listOf(
        HuntPokemonEntry(species = "pidgey", form = "Normal", aspects = emptySet(), chance = 1.0),
        HuntPokemonEntry(species = "rattata", form = "Normal", aspects = emptySet(), chance = 1.0),
        HuntPokemonEntry(species = "caterpie", form = "Normal", aspects = emptySet(), chance = 1.0),
        HuntPokemonEntry(species = "weedle", form = "Normal", aspects = emptySet(), chance = 1.0),
        HuntPokemonEntry(species = "zigzagoon", form = "Normal", aspects = emptySet(), chance = 1.0),
        HuntPokemonEntry(species = "bidoof", form = "Normal", aspects = emptySet(), chance = 1.0),
        HuntPokemonEntry(species = "patrat", form = "Normal", aspects = emptySet(), chance = 1.0),
        HuntPokemonEntry(species = "lillipup", form = "Normal", aspects = emptySet(), chance = 1.0),
        HuntPokemonEntry(species = "pidove", form = "Normal", aspects = emptySet(), chance = 1.0),
        HuntPokemonEntry(species = "sentret", form = "Normal", aspects = emptySet(), chance = 1.0)
    )
    // Global pool is now identical to the solo easy pool.
    private val defaultGlobalPokemon = defaultSoloEasyPokemon

    private val defaultSoloNormalPokemon = listOf(
        HuntPokemonEntry(species = "eevee", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "male"),
        HuntPokemonEntry(species = "pikachu", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "female"),
        HuntPokemonEntry(species = "buneary", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "male"),
        HuntPokemonEntry(species = "shinx", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "female"),
        HuntPokemonEntry(species = "staravia", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "male"),
        HuntPokemonEntry(species = "bibarel", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "female"),
        HuntPokemonEntry(species = "watchog", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "male"),
        HuntPokemonEntry(species = "herdier", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "female"),
        HuntPokemonEntry(species = "tranquill", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "male"),
        HuntPokemonEntry(species = "furret", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "female")
    )

    private val defaultSoloMediumPokemon = listOf(
        HuntPokemonEntry(species = "growlithe", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "male", nature = "Adamant"),
        HuntPokemonEntry(species = "machop", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "female", nature = "Brave"),
        HuntPokemonEntry(species = "vulpix", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "male", nature = "Timid"),
        HuntPokemonEntry(species = "sandshrew", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "female", nature = "Impish"),
        HuntPokemonEntry(species = "geodude", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "male", nature = "Relaxed"),
        HuntPokemonEntry(species = "ponyta", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "female", nature = "Jolly"),
        HuntPokemonEntry(species = "doduo", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "male", nature = "Adamant"),
        HuntPokemonEntry(species = "seel", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "female", nature = "Calm"),
        HuntPokemonEntry(species = "grimer", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "male", nature = "Bold"),
        HuntPokemonEntry(species = "shellder", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "female", nature = "Naughty")
    )

    private val defaultSoloHardPokemon = listOf(
        HuntPokemonEntry(species = "dragonite", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "male", nature = "Adamant", ivRange = "2"),
        HuntPokemonEntry(species = "snorlax", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "female", nature = "Relaxed", ivRange = "2"),
        HuntPokemonEntry(species = "lapras", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "male", nature = "Modest", ivRange = "2"),
        HuntPokemonEntry(species = "aerodactyl", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "female", nature = "Jolly", ivRange = "2"),
        HuntPokemonEntry(species = "garchomp", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "male", nature = "Jolly", ivRange = "2"),
        HuntPokemonEntry(species = "milotic", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "female", nature = "Bold", ivRange = "2"),
        HuntPokemonEntry(species = "tyranitar", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "male", nature = "Adamant", ivRange = "2"),
        HuntPokemonEntry(species = "salamence", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "female", nature = "Naive", ivRange = "2"),
        HuntPokemonEntry(species = "metagross", form = "Normal", aspects = emptySet(), chance = 1.0, gender = null, nature = "Adamant", ivRange = "2"),
        HuntPokemonEntry(species = "lucario", form = "Normal", aspects = emptySet(), chance = 1.0, gender = "male", nature = "Timid", ivRange = "2")
    )

    /** Global loot rewards. */
    private val defaultGlobalLoot = listOf<LootReward>(
        CommandReward(
            command = "eco deposit 150 dollars %player%",
            chance = 1.0,
            serializableItemStack = SerializableItemStack(
                itemStackString = "{\"id\":\"minecraft:paper\",\"count\":1,\"components\":{\"minecraft:custom_name\":\"\\\"§aMoney Reward: $150\\\"\",\"minecraft:lore\":[\"\\\"You will receive 50 money.\\\"\"]}}"
            )
        )
    )

    /** Easy difficulty loot rewards. */
    private val defaultSoloEasyLoot = listOf<LootReward>(
        CommandReward(
            command = "eco deposit 10 dollars %player%",
            chance = 1.0,
            serializableItemStack = SerializableItemStack(
                itemStackString = "{\"id\":\"minecraft:paper\",\"count\":1,\"components\":{\"minecraft:custom_name\":\"\\\"§aMoney Reward: $10\\\"\",\"minecraft:lore\":[\"\\\"You will receive 10 money.\\\"\"]}}"
            )
        )
    )

    /** Normal difficulty loot rewards. */
    private val defaultSoloNormalLoot = listOf<LootReward>(
        CommandReward(
            command = "eco deposit 15 dollars %player%",
            chance = 1.0,
            serializableItemStack = SerializableItemStack(
                itemStackString = "{\"id\":\"minecraft:paper\",\"count\":1,\"components\":{\"minecraft:custom_name\":\"\\\"§aMoney Reward: $15\\\"\",\"minecraft:lore\":[\"\\\"You will receive 15 money.\\\"\"]}}"
            )
        )
    )

    /** Medium difficulty loot rewards. */
    private val defaultSoloMediumLoot = listOf<LootReward>(
        CommandReward(
            command = "eco deposit 25 dollars %player%",
            chance = 1.0,
            serializableItemStack = SerializableItemStack(
                itemStackString = "{\"id\":\"minecraft:paper\",\"count\":1,\"components\":{\"minecraft:custom_name\":\"\\\"§aMoney Reward: $25\\\"\",\"minecraft:lore\":[\"\\\"You will receive 25 money.\\\"\"]}}"
            )
        )
    )

    /** Hard difficulty loot rewards. */
    private val defaultSoloHardLoot = listOf<LootReward>(
        CommandReward(
            command = "eco deposit 50 dollars %player%",
            chance = 1.0,
            serializableItemStack = SerializableItemStack(
                itemStackString = "{\"id\":\"minecraft:paper\",\"count\":1,\"components\":{\"minecraft:custom_name\":\"\\\"§aMoney Reward: $50\\\"\",\"minecraft:lore\":[\"\\\"You will receive 50 money.\\\"\"]}}"
            )
        )
    )

    /** Creates the default configuration with populated lists and settings. */
    private fun createDefaultConfig(): HuntsConfigData {
        return HuntsConfigData(
            globalPokemon = defaultGlobalPokemon.toMutableList(),
            soloEasyPokemon = defaultSoloEasyPokemon.toMutableList(),
            soloNormalPokemon = defaultSoloNormalPokemon.toMutableList(),
            soloMediumPokemon = defaultSoloMediumPokemon.toMutableList(),
            soloHardPokemon = defaultSoloHardPokemon.toMutableList(),
            globalLoot = defaultGlobalLoot.toMutableList(),
            soloEasyLoot = defaultSoloEasyLoot.toMutableList(),
            soloNormalLoot = defaultSoloNormalLoot.toMutableList(),
            soloMediumLoot = defaultSoloMediumLoot.toMutableList(),
            soloHardLoot = defaultSoloHardLoot.toMutableList(),
            soloEasyCooldown = 120,
            soloNormalCooldown = 120,
            soloMediumCooldown = 120,
            soloHardCooldown = 120,
            globalCooldown = 120,
            soloEasyTimeLimit = 300,
            soloNormalTimeLimit = 300,
            soloMediumTimeLimit = 300,
            soloHardTimeLimit = 300,
            globalTimeLimit = 300,
            globalPoints = 100,
            soloEasyPoints = 10,
            soloNormalPoints = 15,
            soloMediumPoints = 25,
            soloHardPoints = 40,
            enableLeaderboard = true,
            soloHardEnabled = true,
            autoAcceptSoloHunts = false,
            globalHuntCompletionMessage = "%player% has completed a global hunt for %pokemon% and received %reward%!",
            soloHuntCompletionMessage = "You received %reward%",
            capturedPokemonMessage = "You caught a %pokemon% that matches an active hunt! Use /hunts to turn it in.",
        )
    }
    private val configManager = ConfigManager(
        currentVersion = "1.0.7",
        defaultConfig = createDefaultConfig(),
        configClass = HuntsConfigData::class,
        metadata = ConfigMetadata(
            headerComments = listOf(
                "CobbleHunts Configuration",
                "Stores Pokémon and loot pools for Global and Solo hunts."
            ),
            sectionComments = mapOf(
                "debugEnabled" to "Enable debug logging for CobbleHunts. Set to true to see detailed logs.",
                "activeGlobalHuntsAtOnce" to "Number of global hunts active at once (Max: 28).",
                "soloEasyCooldown" to "Cooldown timer for Solo Easy hunts in seconds.",
                "soloNormalCooldown" to "Cooldown timer for Solo Normal hunts in seconds.",
                "soloMediumCooldown" to "Cooldown timer for Solo Medium hunts in seconds.",
                "soloHardCooldown" to "Cooldown timer for Solo Hard hunts in seconds.",
                "globalCooldown" to "Cooldown timer for Global hunts in seconds.",
                "soloEasyTimeLimit" to "Time limit for Solo Easy hunts in seconds.",
                "soloNormalTimeLimit" to "Time limit for Solo Normal hunts in seconds.",
                "soloMediumTimeLimit" to "Time limit for Solo Medium hunts in seconds.",
                "soloHardTimeLimit" to "Time limit for Solo Hard hunts in seconds.",
                "globalTimeLimit" to "Time limit for Global hunts in seconds.",
                "globalPoints" to "Points awarded for completing a global hunt.",
                "soloEasyPoints" to "Points awarded for completing a solo easy hunt.",
                "soloNormalPoints" to "Points awarded for completing a solo normal hunt.",
                "soloMediumPoints" to "Points awarded for completing a solo medium hunt.",
                "soloHardPoints" to "Points awarded for completing a solo hard hunt.",
                "permissions" to "Permission settings for hunts. Note: 'permissionLevel' and 'opLevel' are part of permissions.",
                "enableLeaderboard" to "Enable or disable the leaderboard feature. If false, the leaderboard button will not be shown in the GUI.",
                "huntingBrushItem" to "Serialized item string for the Hunting Brush.",
                "onlyAllowTurnInIfCapturedAfterHuntStarted" to "Only allows you to turn in mons captured after starting a hunt",
                "lockGlobalHuntsOnCompletionForAllPlayers" to "If true, once a global hunt is completed by any player, it is locked for all players until the next round.",
                "globalHuntCompletionMessage" to "Message broadcasted when a player completes a global hunt. Available placeholders: %player% (player's name), %pokemon% (Pokémon species), %reward% (reward description)",
                "capturedPokemonMessage" to "Message sent to a player when they catch a Pokémon matching an active hunt. Use %pokemon% for the Pokémon's name.",
                "soloHuntCompletionMessage" to "Message sent to a player when they complete a solo hunt. Use %reward% for the reward description.",
                "autoAcceptSoloHunts" to "If true, solo hunts will automatically activate when available, without needing player interaction.",
                "rewardMode" to "Mode for reward selection: 'weight' (default) or 'percentage'. In 'weight' mode, rewards are selected based on their chance relative to the total. In 'percentage' mode, rewards with chance >= 1.0 are selected uniformly at random among them; otherwise, selection is proportional to their chances.",
            )
        )
    )

    /** Current configuration instance. */
    val config: HuntsConfigData get() = configManager.getCurrentConfig()

    /** Initializes and loads the config. */
    fun initializeAndLoad() { runBlocking { configManager.reloadConfig() }
                              saveConfig()  }

    /** Saves the current config. */
    fun saveConfig() { runBlocking { configManager.saveConfig(config) } }

    /** Reloads the config synchronously. */
    fun reloadBlocking() { runBlocking { configManager.reloadConfig() } }

    /** Retrieves the Pokémon list for a given type and tier. */
    fun getPokemonList(type: String, tier: String): MutableList<HuntPokemonEntry> {
        return when (type) {
            "global" -> config.globalPokemon
            "solo" -> when (tier) {
                "easy" -> config.soloEasyPokemon
                "normal" -> config.soloNormalPokemon
                "medium" -> config.soloMediumPokemon
                "hard" -> config.soloHardPokemon
                else -> mutableListOf()
            }
            else -> mutableListOf()
        }
    }

    /** Retrieves the loot list for a given type and tier. */
    fun getLootList(type: String, tier: String): MutableList<LootReward> {
        return when (type) {
            "global" -> config.globalLoot
            "solo" -> when (tier) {
                "easy" -> config.soloEasyLoot
                "normal" -> config.soloNormalLoot
                "medium" -> config.soloMediumLoot
                "hard" -> config.soloHardLoot
                else -> mutableListOf()
            }
            else -> mutableListOf()
        }
    }
}

/** JSON adapter for serializing/deserializing LootReward subclasses. */
class LootRewardAdapter : TypeAdapter<LootReward>() {
    override fun write(out: JsonWriter, value: LootReward?) {
        if (value == null) {
            out.nullValue()
            return
        }
        when (value) {
            is ItemReward -> {
                out.beginObject()
                out.name("type").value("item")
                out.name("serializableItemStack")
                gson.toJson(value.serializableItemStack, SerializableItemStack::class.java, out)
                out.name("chance").value(value.chance)
                out.endObject()
            }
            is CommandReward -> {
                out.beginObject()
                out.name("type").value("command")
                out.name("command").value(value.command)
                out.name("chance").value(value.chance)
                if (value.serializableItemStack != null) {
                    out.name("serializableItemStack")
                    gson.toJson(value.serializableItemStack, SerializableItemStack::class.java, out)
                }
                out.endObject()
            }
        }
    }

    override fun read(`in`: JsonReader): LootReward? {
        `in`.beginObject()
        var type: String? = null
        var serializableItemStack: SerializableItemStack? = null
        var command: String? = null
        var chance: Double? = null
        while (`in`.hasNext()) {
            when (`in`.nextName()) {
                "type" -> type = `in`.nextString()
                "serializableItemStack" -> serializableItemStack = gson.fromJson(`in`, SerializableItemStack::class.java)
                "command" -> command = `in`.nextString()
                "chance" -> chance = `in`.nextDouble()
                "commandItemStackString" -> serializableItemStack = SerializableItemStack(`in`.nextString())
                "displayItem" -> `in`.skipValue()
            }
        }
        `in`.endObject()
        return when (type) {
            "item" -> ItemReward(serializableItemStack!!, chance!!)
            "command" -> CommandReward(command!!, chance!!, serializableItemStack)
            else -> throw IllegalArgumentException("Unknown LootReward type: $type")
        }
    }

    companion object {
        private val gson = Gson()
    }
}
