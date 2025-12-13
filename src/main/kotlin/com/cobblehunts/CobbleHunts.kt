package com.cobblehunts

import com.cobblehunts.gui.huntsgui.HuntsGui
import com.cobblehunts.utils.*
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.Pokemon
import com.everlastingutils.colors.KyoriHelper
import com.everlastingutils.command.CommandManager
import com.everlastingutils.scheduling.SchedulerManager
import com.everlastingutils.utils.LogDebug
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import org.slf4j.Logger
import net.fabricmc.loader.api.Version
import net.fabricmc.loader.api.VersionParsingException
import java.util.concurrent.ConcurrentHashMap
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.math.Box


object CobbleHunts : ModInitializer {
    private val logger = LoggerFactory.getLogger("cobblehunts")
    const val MOD_ID = "cobblehunts"

    private val playerData = ConcurrentHashMap<UUID, PlayerHuntData>()
    val removedPokemonCache = mutableMapOf<UUID, MutableList<Pokemon>>()

    var globalHuntStates: List<HuntInstance> = emptyList()
    val globalCompletedHuntIndices: MutableSet<Int> = mutableSetOf()
    var sharedEndTime: Long? = null

    @Volatile
    var globalCooldownEnd: Long = 0

    @Volatile
    private var isSchedulerRunning = false

    private const val MOD_NAME = "CobbleHunts"
    private const val SCHEDULER_ID = "cobblehunts-game-loop"

    override fun onInitialize() {
        val dependenciesMet = checkDependency(
            currentModName = MOD_NAME,
            dependencyModId = "everlastingutils",
            requiredVersionStr = "1.1.2",
            dependencyUrl = "https://modrinth.com/mod/e-utils ",
            logger = logger
        )

        if (!dependenciesMet) return

        LogDebug.init(MOD_ID, false)
        HuntsConfig.initializeAndLoad()
        updateDebugState()
        HuntsCommands.registerCommands()
        CatchingTracker.registerEvents()

        LogDebug.debug("Mod initialized - waiting for server to start", MOD_ID)

        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            LogDebug.debug("Server starting - resetting scheduler", MOD_ID)
            SchedulerManager.onServerStarting(server)
        }

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            LogDebug.debug("Server started - initializing hunts", MOD_ID)

            globalCooldownEnd = 0
            startGlobalHunt()

            TrailManager.init(server)

            LogDebug.debug("Starting schedulers...", MOD_ID)

            val guiRefreshFuture = SchedulerManager.scheduleAtFixedRate(
                "cobblehunts-gui-refresh",
                server,
                0,
                1,
                TimeUnit.SECONDS,
                runAsync = false
            ) {
                // This runs on Main Thread
                HuntsGui.refreshDynamicGuis()
            }

            LogDebug.debug("Starting Central Game Loop", MOD_ID)
            val stateFuture = SchedulerManager.scheduleAtFixedRate(
                SCHEDULER_ID,
                server,
                0,
                1,
                TimeUnit.SECONDS
            ) {
                gameLoop(server)
            }

            isSchedulerRunning = !stateFuture.isCancelled
            LogDebug.debug("GUI scheduler running: ${!guiRefreshFuture.isCancelled}", MOD_ID)
            LogDebug.debug("Game loop scheduler running: $isSchedulerRunning", MOD_ID)

            if (!isSchedulerRunning) {
                logger.error("CRITICAL: Game loop scheduler failed to start!")
            }
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            LogDebug.debug("Server stopping - shutting down schedulers", MOD_ID)
            isSchedulerRunning = false
            SchedulerManager.shutdown("cobblehunts-gui-refresh")
            SchedulerManager.shutdown(SCHEDULER_ID)
            LogDebug.debug("Shutdown complete", MOD_ID)
        }

        LogDebug.debug("Lifecycle events registered", MOD_ID)
    }



    private fun gameLoop(server: MinecraftServer) {
        if (!isSchedulerRunning) {
            LogDebug.debug("WARNING: Scheduler is not running!", MOD_ID)
            return
        }

        val currentTime = System.currentTimeMillis()

        if (currentTime % 30000 < 1000) {
            LogDebug.debug("Game loop running at $currentTime", MOD_ID)
        }

        processGlobalHuntLogic(currentTime)

        server.playerManager.playerList.forEach { player ->
            processSoloHuntLogic(player, currentTime)
        }
    }

    private fun processGlobalHuntLogic(currentTime: Long) {
        if (!HuntsConfig.settings.globalHuntsEnabled) {
            LogDebug.debug("Global hunts disabled in config", MOD_ID)
            return
        }

        if (currentTime % 10000 < 1000) {
            LogDebug.debug("Global state check: hunts=${globalHuntStates.size}, cooldownEnd=$globalCooldownEnd, sharedEndTime=$sharedEndTime", MOD_ID)
        }

        if (globalHuntStates.isNotEmpty()) {
            val firstEndTime = globalHuntStates.first().endTime ?: 0
            val huntsExpired = currentTime > firstEndTime
            val allCompleted = globalHuntStates.indices.all { globalCompletedHuntIndices.contains(it) }

            LogDebug.debug("Active hunts check: huntsExpired=$huntsExpired, allCompleted=$allCompleted, currentTime=$currentTime, endTime=$firstEndTime", MOD_ID)

            if (huntsExpired || allCompleted) {
                LogDebug.debug("Ending Global Hunts (Expired: $huntsExpired, AllDone: $allCompleted)", MOD_ID)
                startGlobalCooldown()
            } else {
                LogDebug.debug("Global hunts still active, time left: ${(firstEndTime - currentTime) / 1000}s", MOD_ID)
            }
        }
        else if (globalCooldownEnd > 0) {
            LogDebug.debug("In cooldown: end=$globalCooldownEnd, current=$currentTime, remaining=${(globalCooldownEnd - currentTime) / 1000}s", MOD_ID)

            if (currentTime > globalCooldownEnd) {
                LogDebug.debug("Cooldown finished, starting new global hunts", MOD_ID)
                startGlobalHunt()
            }
        }
        else {
            LogDebug.debug("No active hunts and no cooldown - starting new global hunt", MOD_ID)
            startGlobalHunt()
        }
    }

    private fun processSoloHuntLogic(player: ServerPlayerEntity, currentTime: Long) {
        if (!HuntsConfig.settings.soloHuntsEnabled) return
        val data = getPlayerData(player)
        val difficulties = listOf("easy", "normal", "medium", "hard")

        difficulties.forEach { diff ->
            val activeHunt = data.activePokemon[diff]
            val cooldownEnd = data.cooldowns.getOrDefault(diff, 0L)
            val isStillOnCooldown = currentTime < cooldownEnd

            if (activeHunt != null) {
                if (activeHunt.endTime != null && currentTime >= activeHunt.endTime!!) {
                    data.activePokemon.remove(diff)
                    val cdDuration = getCooldownForDifficulty(diff)
                    data.cooldowns[diff] = currentTime + (cdDuration * 1000L)
                    LogDebug.debug("Solo $diff hunt expired for ${player.name.string}. Cooldown set for ${cdDuration}s.", MOD_ID)
                }
            } else if (isStillOnCooldown) {
            } else {
                if (HuntsConfig.settings.autoAcceptSoloHunts) {
                    attemptStartSoloHunt(player, diff)
                }
            }
        }
    }

    private fun getCooldownForDifficulty(difficulty: String): Int {
        return when (difficulty) {
            "easy" -> HuntsConfig.settings.soloEasyCooldown
            "normal" -> HuntsConfig.settings.soloNormalCooldown
            "medium" -> HuntsConfig.settings.soloMediumCooldown
            "hard" -> HuntsConfig.settings.soloHardCooldown
            else -> 60
        }
    }

    private fun attemptStartSoloHunt(player: ServerPlayerEntity, difficulty: String) {
        val pokemon = selectPokemonForDifficulty(difficulty) ?: return
        val instance = createHuntInstance(pokemon, difficulty)
        activateMission(player, difficulty, instance)
    }

    sealed class SoloHuntDisplayState {
        data class Active(val instance: HuntInstance, val remainingSeconds: Long) : SoloHuntDisplayState()
        data class Cooldown(val remainingSeconds: Long) : SoloHuntDisplayState()
        data class Ready(val preview: HuntInstance?) : SoloHuntDisplayState()
        data object Locked : SoloHuntDisplayState()
    }

    fun getSoloHuntDisplayState(player: ServerPlayerEntity, difficulty: String): SoloHuntDisplayState {
        if (!hasHuntPermission(player, difficulty)) return SoloHuntDisplayState.Locked

        val data = getPlayerData(player)
        val currentTime = System.currentTimeMillis()

        data.activePokemon[difficulty]?.let { instance ->
            val remaining = ((instance.endTime ?: currentTime) - currentTime) / 1000
            return SoloHuntDisplayState.Active(instance, remaining.coerceAtLeast(0))
        }

        val cdEnd = data.cooldowns.getOrDefault(difficulty, 0L)
        if (cdEnd > currentTime) {
            val remaining = (cdEnd - currentTime) / 1000
            return SoloHuntDisplayState.Cooldown(remaining.coerceAtLeast(0))
        }

        return SoloHuntDisplayState.Ready(data.previewPokemon[difficulty])
    }

    sealed class GlobalHuntDisplayState {
        data class Active(val remainingSeconds: Long, val hunts: List<HuntInstance>) : GlobalHuntDisplayState()
        data class Cooldown(val remainingSeconds: Long) : GlobalHuntDisplayState()
        data object StartingSoon : GlobalHuntDisplayState()
    }

    fun getGlobalHuntDisplayState(): GlobalHuntDisplayState {
        val currentTime = System.currentTimeMillis()

        if (globalHuntStates.isNotEmpty()) {
            val end = globalHuntStates.first().endTime ?: 0L
            if (end > currentTime) {
                val remaining = (end - currentTime) / 1000
                return GlobalHuntDisplayState.Active(remaining.coerceAtLeast(0), globalHuntStates)
            }
        }

        if (globalCooldownEnd > currentTime) {
            val remaining = (globalCooldownEnd - currentTime) / 1000
            return GlobalHuntDisplayState.Cooldown(remaining.coerceAtLeast(0))
        }

        return GlobalHuntDisplayState.StartingSoon
    }

    private fun startGlobalHunt() {
        LogDebug.debug("===== startGlobalHunt() called =====", MOD_ID)
        globalCooldownEnd = 0
        globalCompletedHuntIndices.clear()
        playerData.values.forEach { it.completedGlobalHunts.clear() }

        if (!HuntsConfig.settings.globalHuntsEnabled) {
            LogDebug.debug("Global hunts disabled in config", MOD_ID)
            globalHuntStates = emptyList()
            return
        }

        val numHunts = HuntsConfig.settings.activeGlobalHuntsAtOnce
        val endTime = System.currentTimeMillis() + HuntsConfig.settings.globalTimeLimit * 1000L
        val usedSpecies = mutableSetOf<String>()

        LogDebug.debug("Creating $numHunts global hunts for ${HuntsConfig.settings.globalTimeLimit} seconds", MOD_ID)
        LogDebug.debug("Global pokemon pool size: ${HuntsConfig.pokemonPools.globalPokemon.size}", MOD_ID)

        if (HuntsConfig.pokemonPools.globalPokemon.isEmpty()) {
            LogDebug.debug("ERROR: Global pokemon pool is empty!", MOD_ID)
            globalHuntStates = emptyList()
            return
        }

        globalHuntStates = (0 until numHunts).mapNotNull { i ->
            val pokemon = if (usedSpecies.size < HuntsConfig.pokemonPools.globalPokemon.size) {
                selectPokemonFromList(HuntsConfig.pokemonPools.globalPokemon, usedSpecies)
            } else {
                selectGlobalPokemon()
            }

            pokemon?.let { poke ->
                usedSpecies.add(poke.species.lowercase())
                val instance = createGlobalHuntInstance(poke)
                instance.startTime = System.currentTimeMillis()
                instance.endTime = endTime
                LogDebug.debug("Created hunt $i: ${poke.species} (ends at $endTime)", MOD_ID)
                instance
            }
        }
        sharedEndTime = endTime

        LogDebug.debug("Created ${globalHuntStates.size} global hunts", MOD_ID)
        LogDebug.debug("===== startGlobalHunt() finished =====", MOD_ID)
    }

    private fun startGlobalCooldown() {
        LogDebug.debug("===== startGlobalCooldown() called =====", MOD_ID)
        globalHuntStates = emptyList()
        sharedEndTime = null
        globalCompletedHuntIndices.clear()
        globalCooldownEnd = System.currentTimeMillis() + HuntsConfig.settings.globalCooldown * 1000L
        LogDebug.debug("Cooldown set for ${HuntsConfig.settings.globalCooldown} seconds", MOD_ID)
        LogDebug.debug("Cooldown ends at: $globalCooldownEnd", MOD_ID)
        LogDebug.debug("===== startGlobalCooldown() finished =====", MOD_ID)
    }

    fun refreshPreviewPokemon(player: ServerPlayerEntity) {
        if (!HuntsConfig.settings.soloHuntsEnabled) return
        val data = getPlayerData(player)

        listOf("easy", "normal", "medium", "hard").forEach { difficulty ->
            if (data.activePokemon[difficulty] == null && !isOnCooldown(player, difficulty)) {
                if (data.previewPokemon[difficulty] == null) {
                    selectPokemonForDifficulty(difficulty)?.let { pokemon ->
                        val instance = createHuntInstance(pokemon, difficulty)
                        setPreviewPokemon(player, difficulty, instance)
                    }
                }
            }
        }
    }

    fun getPlayerData(player: ServerPlayerEntity) =
        playerData.getOrPut(player.uuid) { PlayerHuntData() }

    fun isOnCooldown(player: ServerPlayerEntity, difficulty: String): Boolean {
        return System.currentTimeMillis() < getPlayerData(player).cooldowns.getOrDefault(difficulty, 0)
    }

    fun activateMission(player: ServerPlayerEntity, difficulty: String, instance: HuntInstance) {
        val data = getPlayerData(player)
        data.activePokemon[difficulty] = instance
        instance.startTime = System.currentTimeMillis()

        val limit = when (difficulty) {
            "easy"   -> HuntsConfig.settings.soloEasyTimeLimit
            "normal" -> HuntsConfig.settings.soloNormalTimeLimit
            "medium" -> HuntsConfig.settings.soloMediumTimeLimit
            "hard"   -> HuntsConfig.settings.soloHardTimeLimit
            else     -> 300
        }

        instance.endTime = System.currentTimeMillis() + limit * 1000L
        data.previewPokemon.remove(difficulty)
        LogDebug.debug("Activated $difficulty mission for ${player.name.string}: ${instance.entry.species}", MOD_ID)
    }

    private fun checkDependency(
        currentModName: String,
        dependencyModId: String,
        requiredVersionStr: String,
        dependencyUrl: String,
        logger: Logger
    ): Boolean {
        // Attempt to get the dependency's container from the Fabric Loader
        val modContainerOpt = FabricLoader.getInstance().getModContainer(dependencyModId)

        // Check if the dependency is missing entirely
        if (modContainerOpt.isEmpty) {
            logger.error("************************************************************")
            logger.error("* FATAL: $currentModName requires the mod '$dependencyModId', but it is missing.")
            logger.error("* Please install '$dependencyModId' version $requiredVersionStr or newer.")
            logger.error("* You can download it from: $dependencyUrl")
            logger.error("************************************************************")
            return false
        }

        // Get the installed version of the dependency
        val installedVersion = modContainerOpt.get().metadata.version

        try {
            // Parse the required version string into a Version object
            val requiredVersion = Version.parse(requiredVersionStr)

            // Compare the installed version with the required version.
            // A result less than 0 means the installed version is older.
            if (installedVersion.compareTo(requiredVersion) < 0) {
                logger.error("************************************************************")
                logger.error("* FATAL: Your version of '$dependencyModId' ($installedVersion) is too old.")
                logger.error("* $currentModName requires version $requiredVersionStr or newer.")
                logger.error("* Please update '$dependencyModId' to prevent issues.")
                logger.error("* You can download it from: $dependencyUrl")
                logger.error("************************************************************")
                return false
            }

            // If the check passes, log a success message
            logger.info("Found compatible version of '$dependencyModId': $installedVersion")
            return true

        } catch (e: VersionParsingException) {
            // This catch block handles errors in your own code (e.g., a typo in the version string)
            logger.error("Could not parse required version string '$requiredVersionStr' for '$dependencyModId'. This is a bug in $currentModName.", e)
            return false
        }
    }

    fun updateDebugState() {
        val debugEnabled = HuntsConfig.settings.debugEnabled
        LogDebug.setDebugEnabledForMod(MOD_ID, debugEnabled)
    }

    private inline fun <T> weightedRandom(list: List<T>, weightSelector: (T) -> Double): T? {
        if (list.isEmpty()) return null
        val total = list.sumOf(weightSelector)
        if (total <= 0) return list.random()
        var randomValue = Random.nextDouble() * total
        for (item in list) {
            randomValue -= weightSelector(item)
            if (randomValue <= 0) return item
        }
        return list.last()
    }

    private fun selectPokemonFromList(pokemonList: List<HuntPokemonEntry>, usedSpecies: Set<String>? = null): HuntPokemonEntry? {
        if (pokemonList.isEmpty()) return null
        val available = usedSpecies?.let { species -> pokemonList.filter { it.species.lowercase() !in species } } ?: pokemonList
        return if (available.isNotEmpty()) weightedRandom(available) { it.chance } else weightedRandom(pokemonList) { it.chance }
    }

    fun selectPokemonForDifficulty(difficulty: String): HuntPokemonEntry? = when (difficulty) {
        "easy"   -> weightedRandom(HuntsConfig.pokemonPools.soloEasyPokemon) { it.chance }
        "normal" -> weightedRandom(HuntsConfig.pokemonPools.soloNormalPokemon) { it.chance }
        "medium" -> weightedRandom(HuntsConfig.pokemonPools.soloMediumPokemon) { it.chance }
        "hard"   -> weightedRandom(HuntsConfig.pokemonPools.soloHardPokemon) { it.chance }
        else     -> null
    }

    fun selectGlobalPokemon(): HuntPokemonEntry? = weightedRandom(HuntsConfig.pokemonPools.globalPokemon) { it.chance }

    fun selectRewardForDifficulty(difficulty: String): List<LootReward> {
        val lootList = when (difficulty) {
            "easy"   -> HuntsConfig.lootPools.soloEasyLoot
            "normal" -> HuntsConfig.lootPools.soloNormalLoot
            "medium" -> HuntsConfig.lootPools.soloMediumLoot
            "hard"   -> HuntsConfig.lootPools.soloHardLoot
            else     -> emptyList()
        }
        return selectRewards(lootList)
    }

    fun selectGlobalReward(): List<LootReward> = selectRewards(HuntsConfig.lootPools.globalLoot)

    private fun selectRewards(lootList: List<LootReward>): List<LootReward> {
        if (lootList.isEmpty()) return emptyList()
        return when (HuntsConfig.settings.rewardMode) {
            "percentage" -> lootList.filter { Random.nextDouble() < it.chance }
            else -> listOfNotNull(weightedRandom(lootList) { it.chance })
        }
    }

    private fun createHuntInstance(entry: HuntPokemonEntry, difficulty: String): HuntInstance {
        val requiredGender = entry.gender?.takeIf { it.lowercase() != "random" && difficulty != "easy" }?.uppercase()
        val requiredNature = entry.nature?.takeIf { it.lowercase() != "random" && difficulty in listOf("medium", "hard") }?.lowercase()
        val requiredIVs = entry.ivRange?.toIntOrNull()?.takeIf { difficulty == "hard" }?.let { count ->
            listOf("hp", "attack", "defence", "special_attack", "special_defence", "speed").shuffled().take(count)
        } ?: emptyList()
        return HuntInstance(entry, requiredGender, requiredNature, requiredIVs, selectRewardForDifficulty(difficulty))
    }

    private fun createGlobalHuntInstance(entry: HuntPokemonEntry): HuntInstance {
        val requiredGender = entry.gender?.takeIf { it.lowercase() != "random" }?.uppercase()
        val requiredNature = entry.nature?.takeIf { it.lowercase() != "random" }?.lowercase()
        val requiredIVs = entry.ivRange?.toIntOrNull()?.takeIf { it > 0 }?.let { count ->
            listOf("hp", "attack", "defence", "special_attack", "special_defence", "speed").shuffled().take(count)
        } ?: emptyList()
        return HuntInstance(entry, requiredGender, requiredNature, requiredIVs, selectGlobalReward())
    }

    fun setPreviewPokemon(player: ServerPlayerEntity, difficulty: String, instance: HuntInstance) {
        getPlayerData(player).previewPokemon[difficulty] = instance
    }
    fun getPreviewPokemon(player: ServerPlayerEntity, difficulty: String): HuntInstance? = getPlayerData(player).previewPokemon[difficulty]

    fun getPlayerParty(player: ServerPlayerEntity): List<Pokemon?> {
        val party = Cobblemon.storage.getParty(player)
        return (0 until 6).map { index -> party.get(index) }
    }
    fun restartHunts() {
        LogDebug.debug("Restarting all hunts", MOD_ID)
        playerData.values.forEach { data ->
            data.previewPokemon.clear()
            data.activePokemon.clear()
            data.completedGlobalHunts.clear()
        }
        startGlobalHunt()
    }

    fun hasHuntPermission(player: ServerPlayerEntity, tier: String): Boolean {
        val permissionNode = when (tier.lowercase()) {
            "global" -> HuntsConfig.settings.permissions.globalHuntPermission
            "easy" -> HuntsConfig.settings.permissions.soloEasyHuntPermission
            "normal" -> HuntsConfig.settings.permissions.soloNormalHuntPermission
            "medium" -> HuntsConfig.settings.permissions.soloMediumHuntPermission
            "hard" -> HuntsConfig.settings.permissions.soloHardHuntPermission
            else -> ""
        }
        val source = player.server.commandSource.withEntity(player).withPosition(player.pos)
        return CommandManager.hasPermissionOrOp(source, permissionNode, HuntsConfig.settings.permissions.permissionLevel, HuntsConfig.settings.permissions.opLevel)
    }

    fun broadcast(server: MinecraftServer, message: String, player: ServerPlayerEntity? = null) {
        val registryWrapper = server.registryManager
        val formatted = KyoriHelper.parseToMinecraft(message, registryWrapper)
        if (player != null) player.sendMessage(formatted)
        else server.playerManager.playerList.forEach { p -> p.sendMessage(formatted) }
    }
}

data class PlayerHuntData(
    val previewPokemon: MutableMap<String, HuntInstance> = mutableMapOf(),
    val activePokemon: MutableMap<String, HuntInstance> = mutableMapOf(),
    val cooldowns: MutableMap<String, Long> = mutableMapOf(),
    val completedGlobalHunts: MutableSet<Int> = mutableSetOf(),
    val usedPokemon: MutableSet<UUID> = mutableSetOf()
)

data class HuntInstance(
    val entry: HuntPokemonEntry,
    val requiredGender: String?,
    val requiredNature: String?,
    val requiredIVs: List<String>,
    val rewards: List<LootReward>,
    var endTime: Long? = null,
    var startTime: Long? = null
)