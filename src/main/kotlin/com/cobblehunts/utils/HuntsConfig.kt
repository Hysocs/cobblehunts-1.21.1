package com.cobblehunts.utils

import com.everlastingutils.config.ConfigData
import com.everlastingutils.config.ConfigManager
import com.everlastingutils.config.ConfigMetadata
import com.everlastingutils.config.WatcherSettings
import com.google.gson.*
import com.google.gson.annotations.JsonAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.mojang.serialization.DynamicOps
import kotlinx.coroutines.runBlocking
import net.minecraft.item.ItemStack
import net.minecraft.util.JsonHelper
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

// --- Data Classes for Configuration ---

data class HuntsConfigData(
    // This version must match CURRENT_VERSION in the object below exactly
    override val version: String = "1.1.4",
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
    var enableLeaderboard: Boolean = true,
    var takeMonOnTurnIn: Boolean = true,
    var autoTurnInOnCapture: Boolean = false,
    var rewardMode: String = "weight",
    var onlyAllowTurnInIfCapturedAfterHuntStarted: Boolean = true,
    var lockGlobalHuntsOnCompletionForAllPlayers: Boolean = true,

    // Permissions
    var permissions: HuntPermissions = HuntPermissions(),

    // Tracking Brush Settings
    var trackingBrush: TrackingBrushSettings = TrackingBrushSettings()
) : ConfigData

data class TrackingBrushSettings(
    var scanRadius: Double = 96.0,
    var scanCooldownSeconds: Int = 30,
    var trailStepDistance: Double = 16.0,
    var trailStartDistance: Double = 6.0,
    var trailTimeoutSeconds: Int = 60,
    var damageOnScan: Int = 5,          // Base cost to scan
    var damageOnFailedScan: Int = 2,    // Extra cost if nothing is found
    var damageOnStep: Int = 1,
    var damageOnFinish: Int = 4
)

data class HuntPermissions(
    var permissionLevel: Int = 2,
    var opLevel: Int = 2,
    var huntsPermission: String = "cobblehunts.hunts",
    var giveBrushPermission: String = "cobblehunts.givebrush",
    var globalHuntPermission: String = "cobblehunts.global",
    var soloEasyHuntPermission: String = "cobblehunts.solo.easy",
    var soloNormalHuntPermission: String = "cobblehunts.solo.normal",
    var soloMediumHuntPermission: String = "cobblehunts.solo.medium",
    var soloHardHuntPermission: String = "cobblehunts.solo.hard"
)

data class HuntPokemonEntry(
    val species: String,
    val form: String?,
    val aspects: Set<String>,
    var chance: Double = 1.0,
    var gender: String? = null,
    var nature: String? = null,
    var ivRange: String? = null
)

data class PokemonPoolsConfig(
    override val version: String = "1.1.1",
    override val configId: String = "cobblehunts",
    var globalPokemon: MutableList<HuntPokemonEntry> = mutableListOf(),
    var soloEasyPokemon: MutableList<HuntPokemonEntry> = mutableListOf(),
    var soloNormalPokemon: MutableList<HuntPokemonEntry> = mutableListOf(),
    var soloMediumPokemon: MutableList<HuntPokemonEntry> = mutableListOf(),
    var soloHardPokemon: MutableList<HuntPokemonEntry> = mutableListOf(),
) : ConfigData

@JsonAdapter(LootRewardAdapter::class)
sealed class LootReward {
    abstract var chance: Double
}
data class ItemReward(
    var serializableItemStack: SerializableItemStack,
    override var chance: Double
) : LootReward()
data class CommandReward(
    var command: String,
    override var chance: Double,
    var serializableItemStack: SerializableItemStack? = null
) : LootReward()

data class SerializableItemStack(val itemStackString: String) {
    fun toItemStack(ops: DynamicOps<JsonElement>): ItemStack = deserializeItemStack(itemStackString, ops)
    companion object {
        fun fromItemStack(itemStack: ItemStack, ops: DynamicOps<JsonElement>): SerializableItemStack =
            SerializableItemStack(serializeItemStack(itemStack, ops))
    }
}

data class LootPoolsConfig(
    override val version: String = "1.1.0",
    override val configId: String = "cobblehunts",
    var globalLoot: MutableList<LootReward> = mutableListOf(),
    var soloEasyLoot: MutableList<LootReward> = mutableListOf(),
    var soloNormalLoot: MutableList<LootReward> = mutableListOf(),
    var soloMediumLoot: MutableList<LootReward> = mutableListOf(),
    var soloHardLoot: MutableList<LootReward> = mutableListOf()
) : ConfigData

// --- Main Config Manager Object ---

object HuntsConfig {
    private const val POKEMON_POOLS_FILENAME = "pokemon_pools.jsonc"
    private const val LOOT_POOLS_FILENAME = "loot_pools.jsonc"
    private const val MAIN_CONFIG_ID = "cobblehunts"

    // IMPORTANT: This must match the version in HuntsConfigData exactly
    private const val CURRENT_VERSION = "1.1.4"

    private val configDir: Path = Paths.get("config", MAIN_CONFIG_ID)

    private lateinit var configManager: ConfigManager<HuntsConfigData>
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    val settings: HuntsConfigData
        get() = if (::configManager.isInitialized) configManager.getCurrentConfig() else HuntsConfigData()

    val pokemonPools: PokemonPoolsConfig
        get() = if (::configManager.isInitialized) {
            configManager.getSecondaryConfig(POKEMON_POOLS_FILENAME) ?: createDefaultPokemonPools()
        } else createDefaultPokemonPools()

    val lootPools: LootPoolsConfig
        get() = if (::configManager.isInitialized) {
            configManager.getSecondaryConfig(LOOT_POOLS_FILENAME) ?: createDefaultLootPools()
        } else createDefaultLootPools()

    fun initializeAndLoad() {
        // Run migration *strictly* before initializing config manager
        runMigrationIfNeeded()

        configManager = ConfigManager(
            currentVersion = CURRENT_VERSION,
            defaultConfig = HuntsConfigData(),
            configClass = HuntsConfigData::class,
            metadata = ConfigMetadata(
                headerComments = listOf(
                    "CobbleHunts Main Configuration",
                    "This file contains general settings."
                ),
                watcherSettings = WatcherSettings(enabled = true, autoSaveEnabled = true)
            )
        )

        runBlocking {
            configManager.registerSecondaryConfig(
                fileName = POKEMON_POOLS_FILENAME,
                configClass = PokemonPoolsConfig::class,
                defaultConfig = createDefaultPokemonPools(),
                fileMetadata = ConfigMetadata(
                    headerComments = listOf("CobbleHunts - Pokémon Pools", "Define which Pokémon can appear in hunts here."),
                    watcherSettings = WatcherSettings(enabled = true, autoSaveEnabled = true)
                )
            )
            configManager.registerSecondaryConfig(
                fileName = LOOT_POOLS_FILENAME,
                configClass = LootPoolsConfig::class,
                defaultConfig = createDefaultLootPools(),
                fileMetadata = ConfigMetadata(
                    headerComments = listOf("CobbleHunts - Loot Reward Pools", "Define hunt rewards here."),
                    watcherSettings = WatcherSettings(enabled = true, autoSaveEnabled = true)
                )
            )
        }

        // --- Runtime Validation ---
        if (settings.globalCooldown <= 0) settings.globalCooldown = 120
        if (settings.globalTimeLimit <= 0) settings.globalTimeLimit = 300
        if (settings.activeGlobalHuntsAtOnce <= 0) settings.activeGlobalHuntsAtOnce = 4

        // Ensure brush settings are sane
        if (settings.trackingBrush.scanRadius <= 0) settings.trackingBrush.scanRadius = 48.0
        if (settings.trackingBrush.trailStepDistance <= 0) settings.trackingBrush.trailStepDistance = 16.0
        if (settings.trackingBrush.trailStartDistance <= 0) settings.trackingBrush.trailStartDistance = 6.0
    }

    fun saveConfig() {
        if (!::configManager.isInitialized) return
        runBlocking {
            configManager.saveConfig(settings)

            try {
                val pokemonPoolFile = configDir.resolve(POKEMON_POOLS_FILENAME)
                Files.createDirectories(pokemonPoolFile.parent)
                pokemonPoolFile.writeText(gson.toJson(pokemonPools))

                val lootPoolFile = configDir.resolve(LOOT_POOLS_FILENAME)
                Files.createDirectories(lootPoolFile.parent)
                lootPoolFile.writeText(gson.toJson(lootPools))
            } catch (e: Exception) {
                System.err.println("[CobbleHunts] CRITICAL: Failed to save pool configuration files. Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun reloadBlocking() {
        runBlocking { configManager.reloadConfig() }
    }

    // --- (getPokemonList, getLootList remain unchanged) ---
    fun getPokemonList(type: String, tier: String): MutableList<HuntPokemonEntry> {
        return when (type) {
            "global" -> pokemonPools.globalPokemon
            "solo" -> when (tier) {
                "easy" -> pokemonPools.soloEasyPokemon
                "normal" -> pokemonPools.soloNormalPokemon
                "medium" -> pokemonPools.soloMediumPokemon
                "hard" -> pokemonPools.soloHardPokemon
                else -> mutableListOf()
            }
            else -> mutableListOf()
        }
    }
    fun getLootList(type: String, tier: String): MutableList<LootReward> {
        return when (type) {
            "global" -> lootPools.globalLoot
            "solo" -> when (tier) {
                "easy" -> lootPools.soloEasyLoot
                "normal" -> lootPools.soloNormalLoot
                "medium" -> lootPools.soloMediumLoot
                "hard" -> lootPools.soloHardLoot
                else -> mutableListOf()
            }
            else -> mutableListOf()
        }
    }

    private fun runMigrationIfNeeded() {
        val oldConfigFile = Paths.get("config", MAIN_CONFIG_ID, "config.jsonc")
        if (!oldConfigFile.exists()) return

        try {
            val content = oldConfigFile.readText()
            val reader = JsonReader(StringReader(content))
            reader.isLenient = true
            val jsonObject = JsonParser.parseReader(reader).asJsonObject

            var needsSave = false

            // 1. CHECK FOR OLD POOL DATA
            if (jsonObject.has("globalPokemon")) {
                println("[CobbleHunts] Found legacy Pokemon pools in main config. Migrating to $POKEMON_POOLS_FILENAME...")

                val backupFile = oldConfigFile.resolveSibling(oldConfigFile.fileName.toString() + ".backup_pools")
                Files.copy(oldConfigFile, backupFile, StandardCopyOption.REPLACE_EXISTING)

                val newPokemonPools = PokemonPoolsConfig(
                    globalPokemon = extractList(jsonObject, "globalPokemon"),
                    soloEasyPokemon = extractList(jsonObject, "soloEasyPokemon"),
                    soloNormalPokemon = extractList(jsonObject, "soloNormalPokemon"),
                    soloMediumPokemon = extractList(jsonObject, "soloMediumPokemon"),
                    soloHardPokemon = extractList(jsonObject, "soloHardPokemon")
                )
                val newLootPools = LootPoolsConfig(
                    globalLoot = extractList(jsonObject, "globalLoot"),
                    soloEasyLoot = extractList(jsonObject, "soloEasyLoot"),
                    soloNormalLoot = extractList(jsonObject, "soloNormalLoot"),
                    soloMediumLoot = extractList(jsonObject, "soloMediumLoot"),
                    soloHardLoot = extractList(jsonObject, "soloHardLoot")
                )

                val pokemonPoolFile = oldConfigFile.parent.resolve(POKEMON_POOLS_FILENAME)
                pokemonPoolFile.writeText(gson.toJson(newPokemonPools))

                val lootPoolFile = oldConfigFile.parent.resolve(LOOT_POOLS_FILENAME)
                lootPoolFile.writeText(gson.toJson(newLootPools))

                listOf(
                    "globalPokemon", "soloEasyPokemon", "soloNormalPokemon", "soloMediumPokemon", "soloHardPokemon",
                    "globalLoot", "soloEasyLoot", "soloNormalLoot", "soloMediumLoot", "soloHardLoot"
                ).forEach { jsonObject.remove(it) }

                needsSave = true
            }

            // 2. CHECK FOR OLD SCANNER SETTINGS
            val brushObj = if (jsonObject.has("trackingBrush")) jsonObject.getAsJsonObject("trackingBrush") else JsonObject()
            var brushUpdated = false

            // Migrate root properties (v1.0 style)
            if (jsonObject.has("scannerDamageOnScan")) {
                brushObj.addProperty("damageOnScan", jsonObject.get("scannerDamageOnScan").asInt)
                jsonObject.remove("scannerDamageOnScan")
                brushUpdated = true
            }
            if (jsonObject.has("scannerDamageOnTrailStep")) {
                brushObj.addProperty("damageOnStep", jsonObject.get("scannerDamageOnTrailStep").asInt)
                jsonObject.remove("scannerDamageOnTrailStep")
                brushUpdated = true
            }
            if (jsonObject.has("scannerDamageOnTrailFinish")) {
                brushObj.addProperty("damageOnFinish", jsonObject.get("scannerDamageOnTrailFinish").asInt)
                jsonObject.remove("scannerDamageOnTrailFinish")
                brushUpdated = true
            }

            // Migrate old "scanner" object (v1.1.2 style)
            if (jsonObject.has("scanner")) {
                val oldScanner = jsonObject.getAsJsonObject("scanner")
                if (oldScanner.has("damageOnScan")) brushObj.addProperty("damageOnScan", oldScanner.get("damageOnScan").asInt)
                if (oldScanner.has("damageOnStep")) brushObj.addProperty("damageOnStep", oldScanner.get("damageOnStep").asInt)
                if (oldScanner.has("damageOnFinish")) brushObj.addProperty("damageOnFinish", oldScanner.get("damageOnFinish").asInt)
                if (oldScanner.has("scanRadius")) brushObj.addProperty("scanRadius", oldScanner.get("scanRadius").asDouble)

                jsonObject.remove("scanner")
                brushUpdated = true
            }

            // Migrate Permission name
            if (jsonObject.has("permissions")) {
                val perms = jsonObject.getAsJsonObject("permissions")
                if (perms.has("scannerPermission")) {
                    perms.addProperty("giveBrushPermission", perms.get("scannerPermission").asString)
                    perms.remove("scannerPermission")
                    needsSave = true
                }
            }

            if (brushUpdated) {
                println("[CobbleHunts] Migrating scanner settings to trackingBrush group...")
                jsonObject.add("trackingBrush", brushObj)
                needsSave = true
            }

            // 3. FINAL SAVE
            if (needsSave) {
                jsonObject.addProperty("version", CURRENT_VERSION)
                oldConfigFile.writeText(gson.toJson(jsonObject))
                println("[CobbleHunts] Structural migration successful. Version updated to $CURRENT_VERSION.")
            }

        } catch (e: Exception) {
            System.err.println("[CobbleHunts] CRITICAL ERROR during config migration. Error: ${e.message}")
            e.printStackTrace()
        }
    }

    private inline fun <reified T> extractList(json: JsonObject, key: String): MutableList<T> {
        if (!json.has(key)) return mutableListOf()
        val typeToken = object : com.google.gson.reflect.TypeToken<MutableList<T>>() {}.type
        return gson.fromJson(json.get(key), typeToken)
    }

    private fun createDefaultPokemonPools() = PokemonPoolsConfig(
        globalPokemon = defaultGlobalPokemon.toMutableList(),
        soloEasyPokemon = defaultSoloEasyPokemon.toMutableList(),
        soloNormalPokemon = defaultSoloNormalPokemon.toMutableList(),
        soloMediumPokemon = defaultSoloMediumPokemon.toMutableList(),
        soloHardPokemon = defaultSoloHardPokemon.toMutableList()
    )
    private fun createDefaultLootPools() = LootPoolsConfig(
        globalLoot = defaultGlobalLoot.toMutableList(),
        soloEasyLoot = defaultSoloEasyLoot.toMutableList(),
        soloNormalLoot = defaultSoloNormalLoot.toMutableList(),
        soloMediumLoot = defaultSoloMediumLoot.toMutableList(),
        soloHardLoot = defaultSoloHardLoot.toMutableList()
    )

    // --- Defaults (unchanged) ---
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
    private val defaultGlobalLoot = listOf<LootReward>(CommandReward(command = "eco deposit 150 dollars %player%", chance = 1.0, serializableItemStack = SerializableItemStack("{\"id\":\"minecraft:paper\",\"count\":1,\"components\":{\"minecraft:custom_name\":\"\\\"§aMoney Reward: $150\\\"\",\"minecraft:lore\":[\"\\\"You will receive 50 money.\\\"\"]}}")))
    private val defaultSoloEasyLoot = listOf<LootReward>(CommandReward(command = "eco deposit 10 dollars %player%", chance = 1.0, serializableItemStack = SerializableItemStack("{\"id\":\"minecraft:paper\",\"count\":1,\"components\":{\"minecraft:custom_name\":\"\\\"§aMoney Reward: $10\\\"\",\"minecraft:lore\":[\"\\\"You will receive 10 money.\\\"\"]}}")))
    private val defaultSoloNormalLoot = listOf<LootReward>(CommandReward(command = "eco deposit 15 dollars %player%", chance = 1.0, serializableItemStack = SerializableItemStack("{\"id\":\"minecraft:paper\",\"count\":1,\"components\":{\"minecraft:custom_name\":\"\\\"§aMoney Reward: $15\\\"\",\"minecraft:lore\":[\"\\\"You will receive 15 money.\\\"\"]}}")))
    private val defaultSoloMediumLoot = listOf<LootReward>(CommandReward(command = "eco deposit 25 dollars %player%", chance = 1.0, serializableItemStack = SerializableItemStack("{\"id\":\"minecraft:paper\",\"count\":1,\"components\":{\"minecraft:custom_name\":\"\\\"§aMoney Reward: $25\\\"\",\"minecraft:lore\":[\"\\\"You will receive 25 money.\\\"\"]}}")))
    private val defaultSoloHardLoot = listOf<LootReward>(CommandReward(command = "eco deposit 50 dollars %player%", chance = 1.0, serializableItemStack = SerializableItemStack("{\"id\":\"minecraft:paper\",\"count\":1,\"components\":{\"minecraft:custom_name\":\"\\\"§aMoney Reward: $50\\\"\",\"minecraft:lore\":[\"\\\"You will receive 50 money.\\\"\"]}}")))
}

// --- Utility Functions & Classes ---
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
class LootRewardAdapter : TypeAdapter<LootReward>() {
    override fun write(out: JsonWriter, value: LootReward?) {
        if (value == null) {
            out.nullValue()
            return
        }
        out.beginObject()
        when (value) {
            is ItemReward -> {
                out.name("type").value("item")
                out.name("serializableItemStack")
                gson.toJson(value.serializableItemStack, SerializableItemStack::class.java, out)
                out.name("chance").value(value.chance)
            }
            is CommandReward -> {
                out.name("type").value("command")
                out.name("command").value(value.command)
                out.name("chance").value(value.chance)
                if (value.serializableItemStack != null) {
                    out.name("serializableItemStack")
                    gson.toJson(value.serializableItemStack, SerializableItemStack::class.java, out)
                }
            }
        }
        out.endObject()
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