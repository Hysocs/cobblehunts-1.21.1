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
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryOps
import net.minecraft.text.Text
import net.minecraft.util.JsonHelper
import java.lang.RuntimeException

data class HuntPokemonEntry(
    val species: String,
    val form: String?,
    val aspects: Set<String>,
    var chance: Double = 1.0,
    var gender: String? = null,
    var ivRange: String? = null
)

@JsonAdapter(LootRewardAdapter::class)
sealed class LootReward {
    abstract var chance: Double
}

private val GSON = Gson()

fun serializeItemStack(itemStack: ItemStack, ops: DynamicOps<JsonElement>): String {
    val result = ItemStack.CODEC.encodeStart(ops, itemStack)
    val jsonElement = result.getOrThrow { error -> throw RuntimeException("Failed to serialize ItemStack: $error") }
    return GSON.toJson(jsonElement)
}

fun deserializeItemStack(jsonString: String, ops: DynamicOps<JsonElement>): ItemStack {
    val jsonElement = JsonHelper.deserialize(jsonString)
    val result = ItemStack.CODEC.parse(ops, jsonElement)
    return result.getOrThrow { error -> throw RuntimeException("Failed to deserialize ItemStack: $error") }
}

data class SerializableItemStack(val itemStackString: String) {
    fun toItemStack(ops: DynamicOps<JsonElement>): ItemStack = deserializeItemStack(itemStackString, ops)
    companion object {
        fun fromItemStack(itemStack: ItemStack, ops: DynamicOps<JsonElement>): SerializableItemStack =
            SerializableItemStack(serializeItemStack(itemStack, ops))
    }
}

data class ItemReward(
    val serializableItemStack: SerializableItemStack,
    override var chance: Double
) : LootReward()

data class CommandReward(
    val command: String,
    override var chance: Double,
    var serializableItemStack: SerializableItemStack? = null
) : LootReward()

data class HuntsConfigData(
    override val version: String = "1.0.0",
    override val configId: String = "cobblehunts",
    var globalPokemon: MutableList<HuntPokemonEntry> = mutableListOf(),
    var soloEasyPokemon: MutableList<HuntPokemonEntry> = mutableListOf(),
    var soloMediumPokemon: MutableList<HuntPokemonEntry> = mutableListOf(),
    var soloHardPokemon: MutableList<HuntPokemonEntry> = mutableListOf(),
    var globalLoot: MutableList<LootReward> = mutableListOf(),
    var soloEasyLoot: MutableList<LootReward> = mutableListOf(),
    var soloMediumLoot: MutableList<LootReward> = mutableListOf(),
    var soloHardLoot: MutableList<LootReward> = mutableListOf(),
    var soloEasyCooldown: Int = 120,
    var soloMediumCooldown: Int = 120,
    var soloHardCooldown: Int = 120,
    var globalCooldown: Int = 120,
    var soloEasyTimeLimit: Int = 300,
    var soloMediumTimeLimit: Int = 300,
    var soloHardTimeLimit: Int = 300,
    var globalTimeLimit: Int = 300,
    var globalPoints: Int = 100,
    var soloEasyPoints: Int = 10,
    var soloMediumPoints: Int = 25,
    var soloHardPoints: Int = 40
) : ConfigData

object HuntsConfig {
    // Default Pokémon lists based on assumed rarity from spawn tables
    private val defaultGlobalPokemon = listOf(
        HuntPokemonEntry(species = "mewtwo", form = "Normal", aspects = setOf("shiny"), chance = 0.01),
        HuntPokemonEntry(species = "dragonite", form = "Normal", aspects = emptySet(), chance = 0.05),
        HuntPokemonEntry(species = "snorlax", form = "Normal", aspects = emptySet(), chance = 0.1)
    )

    private val defaultSoloEasyPokemon = listOf(
        HuntPokemonEntry(species = "pidgey", form = "Normal", aspects = emptySet(), chance = 0.2),
        HuntPokemonEntry(species = "rattata", form = "Normal", aspects = emptySet(), chance = 0.2),
        HuntPokemonEntry(species = "caterpie", form = "Normal", aspects = emptySet(), chance = 0.2),
        HuntPokemonEntry(species = "weedle", form = "Normal", aspects = emptySet(), chance = 0.2),
        HuntPokemonEntry(species = "zigzagoon", form = "Normal", aspects = emptySet(), chance = 0.2)
    )

    private val defaultSoloMediumPokemon = listOf(
        HuntPokemonEntry(species = "eevee", form = "Normal", aspects = emptySet(), chance = 0.25),
        HuntPokemonEntry(species = "growlithe", form = "Normal", aspects = emptySet(), chance = 0.25),
        HuntPokemonEntry(species = "machop", form = "Normal", aspects = emptySet(), chance = 0.25),
        HuntPokemonEntry(species = "pikachu", form = "Normal", aspects = setOf("shiny"), chance = 0.1)
    )

    private val defaultSoloHardPokemon = listOf(
        HuntPokemonEntry(species = "dragonite", form = "Normal", aspects = emptySet(), chance = 0.2),
        HuntPokemonEntry(species = "snorlax", form = "Normal", aspects = emptySet(), chance = 0.2),
        HuntPokemonEntry(species = "lapras", form = "Normal", aspects = emptySet(), chance = 0.2),
        HuntPokemonEntry(species = "aerodactyl", form = "Normal", aspects = emptySet(), chance = 0.2),
        HuntPokemonEntry(species = "garchomp", form = "Normal", aspects = emptySet(), chance = 0.1)
    )

    // Default Loot lists using CommandRewards
    private val defaultGlobalLoot = listOf<LootReward>(
        CommandReward(
            command = "xp add %player% 200",
            chance = 1.0,
            serializableItemStack = SerializableItemStack(
                itemStackString = "{\"id\":\"minecraft:experience_bottle\",\"count\":1,\"components\":{\"minecraft:custom_name\":\"\\\"200 XP\\\"\"}}"
            )
        )
    )

    private val defaultSoloEasyLoot = listOf<LootReward>(
        CommandReward(
            command = "xp add %player% 10",
            chance = 1.0,
            serializableItemStack = SerializableItemStack(
                itemStackString = "{\"id\":\"minecraft:experience_bottle\",\"count\":1,\"components\":{\"minecraft:custom_name\":\"\\\"10 XP\\\"\"}}"
            )
        )
    )

    private val defaultSoloMediumLoot = listOf<LootReward>(
        CommandReward(
            command = "xp add %player% 50",
            chance = 1.0,
            serializableItemStack = SerializableItemStack(
                itemStackString = "{\"id\":\"minecraft:experience_bottle\",\"count\":1,\"components\":{\"minecraft:custom_name\":\"\\\"50 XP\\\"\"}}"
            )
        )
    )

    private val defaultSoloHardLoot = listOf<LootReward>(
        CommandReward(
            command = "xp add %player% 100",
            chance = 1.0,
            serializableItemStack = SerializableItemStack(
                itemStackString = "{\"id\":\"minecraft:experience_bottle\",\"count\":1,\"components\":{\"minecraft:custom_name\":\"\\\"100 XP\\\"\"}}"
            )
        )
    )

    // Function to create default configuration with populated lists
    private fun createDefaultConfig(): HuntsConfigData {
        return HuntsConfigData(
            globalPokemon = defaultGlobalPokemon.toMutableList(),
            soloEasyPokemon = defaultSoloEasyPokemon.toMutableList(),
            soloMediumPokemon = defaultSoloMediumPokemon.toMutableList(),
            soloHardPokemon = defaultSoloHardPokemon.toMutableList(),
            globalLoot = defaultGlobalLoot.toMutableList(),
            soloEasyLoot = defaultSoloEasyLoot.toMutableList(),
            soloMediumLoot = defaultSoloMediumLoot.toMutableList(),
            soloHardLoot = defaultSoloHardLoot.toMutableList(),
            soloEasyCooldown = 120,
            soloMediumCooldown = 120,
            soloHardCooldown = 120,
            globalCooldown = 120,
            soloEasyTimeLimit = 300,
            soloMediumTimeLimit = 300,
            soloHardTimeLimit = 300,
            globalTimeLimit = 300,
            globalPoints = 100,
            soloEasyPoints = 10,
            soloMediumPoints = 25,
            soloHardPoints = 40
        )
    }

    private val configManager = ConfigManager(
        currentVersion = "1.0.0",
        defaultConfig = createDefaultConfig(),
        configClass = HuntsConfigData::class,
        metadata = ConfigMetadata(
            headerComments = listOf(
                "CobbleHunts Configuration",
                "Stores Pokémon and loot pools for Global and Solo hunts."
            ),
            sectionComments = mapOf(
                "globalPokemon" to "Pokémon for Global hunts with spawn chances, genders, and IV ranges.",
                "soloEasyPokemon" to "Pokémon for Solo Easy tier with spawn chances.",
                "soloMediumPokemon" to "Pokémon for Solo Medium tier with spawn chances and genders.",
                "soloHardPokemon" to "Pokémon for Solo Hard tier with spawn chances, genders, and IV ranges.",
                "globalLoot" to "Loot rewards for Global hunts.",
                "soloEasyLoot" to "Loot rewards for Solo Easy tier.",
                "soloMediumLoot" to "Loot rewards for Solo Medium tier.",
                "soloHardLoot" to "Loot rewards for Solo Hard tier.",
                "soloEasyCooldown" to "Cooldown timer for Solo Easy hunts in seconds.",
                "soloMediumCooldown" to "Cooldown timer for Solo Medium hunts in seconds.",
                "soloHardCooldown" to "Cooldown timer for Solo Hard hunts in seconds.",
                "globalCooldown" to "Cooldown timer for Global hunts in seconds.",
                "soloEasyTimeLimit" to "Time limit for Solo Easy hunts in seconds.",
                "soloMediumTimeLimit" to "Time limit for Solo Medium hunts in seconds.",
                "soloHardTimeLimit" to "Time limit for Solo Hard hunts in seconds.",
                "globalTimeLimit" to "Time limit for Global hunts in seconds.",
                "globalPoints" to "Points awarded for completing a global hunt.",
                "soloEasyPoints" to "Points awarded for completing a solo easy hunt.",
                "soloMediumPoints" to "Points awarded for completing a solo medium hunt.",
                "soloHardPoints" to "Points awarded for completing a solo hard hunt."
            )
        )
    )
    val config: HuntsConfigData get() = configManager.getCurrentConfig()
    fun initializeAndLoad() { runBlocking { configManager.reloadConfig() } }
    fun saveConfig() { runBlocking { configManager.saveConfig(config) } }
    fun reloadBlocking() { runBlocking { configManager.reloadConfig() } }
    fun getPokemonList(type: String, tier: String): MutableList<HuntPokemonEntry> {
        return when (type) {
            "global" -> config.globalPokemon
            "solo" -> when (tier) {
                "easy" -> config.soloEasyPokemon
                "medium" -> config.soloMediumPokemon
                "hard" -> config.soloHardPokemon
                else -> mutableListOf()
            }
            else -> mutableListOf()
        }
    }
    fun getLootList(type: String, tier: String): MutableList<LootReward> {
        return when (type) {
            "global" -> config.globalLoot
            "solo" -> when (tier) {
                "easy" -> config.soloEasyLoot
                "medium" -> config.soloMediumLoot
                "hard" -> config.soloHardLoot
                else -> mutableListOf()
            }
            else -> mutableListOf()
        }
    }
}

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