package com.cobblehunts


import com.cobblehunts.gui.HuntsGui
import com.cobblehunts.utils.*
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.Natures
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.pokemon.Pokemon
import com.everlastingutils.colors.KyoriHelper
import com.everlastingutils.command.CommandManager
import com.everlastingutils.scheduling.SchedulerManager
import com.everlastingutils.utils.LogDebug
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.random.Random

object CobbleHunts : ModInitializer {
	private val logger = LoggerFactory.getLogger("cobblehunts")
	const val MOD_ID = "cobblehunts"
	private val playerData = mutableMapOf<UUID, PlayerHuntData>()
	val removedPokemonCache = mutableMapOf<UUID, MutableList<Pokemon>>()
	// Global hunt state variables
	var globalHuntStates: List<HuntInstance> = emptyList()
	val globalCompletedHuntIndices: MutableSet<Int> = mutableSetOf()
	var sharedEndTime: Long? = null
	var globalCooldownEnd: Long = 0

	override fun onInitialize() {
		val loader = FabricLoader.getInstance()
		val utilsContainer = loader.getModContainer("everlastingutils").orElse(null)
		if (utilsContainer == null) {
			logger.error(
				"EverlastingUtils is required but not loaded! " +
						"Please download from https://modrinth.com/mod/e-utils or join our Discord at https://discord.gg/KQyPEye7CT."
			)
			return
		}
		val utilsVersion = utilsContainer.metadata.version.friendlyString
		if (!isVersionSufficient(utilsVersion, "1.0.8")) {
			logger.error(
				"EverlastingUtils version $utilsVersion is too low! You need 1.0.8+." +
						" Please update from https://modrinth.com/mod/e-utils or join our Discord at https://discord.gg/KQyPEye7CT."
			)

			return
		}
		LogDebug.init(MOD_ID, false)
		HuntsConfig.initializeAndLoad()
		updateDebugState()
		HuntsCommands.registerCommands()
		CatchingTracker.registerEvents()

		// Start global hunts immediately and ensure no cooldown on startup
		startGlobalHunt()
		globalCooldownEnd = 0


		ServerLifecycleEvents.SERVER_STARTED.register { server ->
			SchedulerManager.scheduleAtFixedRate("cobblehunts-gui-refresh", server, 0, 1, TimeUnit.SECONDS) {
				HuntsGui.refreshDynamicGuis()
			}
			SchedulerManager.scheduleAtFixedRate("cobblehunts-global-check", server, 0, 1, TimeUnit.SECONDS) {
				checkGlobalHuntState()
			}
		}

		logger.info("CobbleHunts initialized")
	}

	private fun isVersionSufficient(actual: String, required: String): Boolean {
		val actualParts = actual.split(".").mapNotNull { it.toIntOrNull() }
		val requiredParts = required.split(".").mapNotNull { it.toIntOrNull() }
		val length = max(actualParts.size, requiredParts.size)
		for (i in 0 until length) {
			val a = if (i < actualParts.size) actualParts[i] else 0
			val r = if (i < requiredParts.size) requiredParts[i] else 0
			if (a < r) return false
			if (a > r) return true
		}
		return true
	}

	fun updateDebugState() {
		val debugEnabled = HuntsConfig.config.debugEnabled
		LogDebug.setDebugEnabledForMod(MOD_ID, debugEnabled)
		logger.info("Debug logging ${if (debugEnabled) "enabled" else "disabled"} for CobbleHunts")
	}

	internal fun getPlayerData(player: ServerPlayerEntity) =
		playerData.getOrPut(player.uuid) { PlayerHuntData() }

	fun isOnCooldown(player: ServerPlayerEntity, difficulty: String) =
		System.currentTimeMillis() < getPlayerData(player).cooldowns.getOrDefault(difficulty, 0)

	// Generic weighted random selection used for both Pokémon and loot rewards.
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

	// Single function to select a Pokémon entry, filtering out species if needed.
	private fun selectPokemonFromList(
		pokemonList: List<HuntPokemonEntry>,
		usedSpecies: Set<String>? = null
	): HuntPokemonEntry? {
		if (pokemonList.isEmpty()) return null
		val available = usedSpecies?.let { species ->
			pokemonList.filter { it.species.lowercase() !in species }
		} ?: pokemonList
		return if (available.isNotEmpty())
			weightedRandom(available) { it.chance }
		else
			weightedRandom(pokemonList) { it.chance }
	}

	fun selectPokemonForDifficulty(difficulty: String): HuntPokemonEntry? = when (difficulty) {
		"easy"   -> weightedRandom(HuntsConfig.config.soloEasyPokemon) { it.chance }
		"normal" -> weightedRandom(HuntsConfig.config.soloNormalPokemon) { it.chance }
		"medium" -> weightedRandom(HuntsConfig.config.soloMediumPokemon) { it.chance }
		"hard"   -> weightedRandom(HuntsConfig.config.soloHardPokemon) { it.chance }
		else     -> throw IllegalArgumentException("Unknown difficulty: $difficulty")
	}

	fun selectGlobalPokemon(): HuntPokemonEntry? =
		weightedRandom(HuntsConfig.config.globalPokemon) { it.chance }

	fun selectRewardForDifficulty(difficulty: String): List<LootReward> {
		val lootList = when (difficulty) {
			"easy"   -> HuntsConfig.config.soloEasyLoot
			"normal" -> HuntsConfig.config.soloNormalLoot
			"medium" -> HuntsConfig.config.soloMediumLoot
			"hard"   -> HuntsConfig.config.soloHardLoot
			else     -> emptyList()
		}
		return selectRewards(lootList)
	}

	fun selectGlobalReward(): List<LootReward> {
		return selectRewards(HuntsConfig.config.globalLoot)
	}

	private fun selectRewards(lootList: List<LootReward>): List<LootReward> {
		if (lootList.isEmpty()) return emptyList()
		return when (HuntsConfig.config.rewardMode) {
			"percentage" -> lootList.filter { Random.nextDouble() < it.chance }
			else -> listOfNotNull(weightedRandom(lootList) { it.chance }) // Default to weight mode, single reward
		}
	}

	// Creates a hunt instance with inline decisions for gender, nature, and IVs.
	private fun createHuntInstance(entry: HuntPokemonEntry, difficulty: String): HuntInstance {
		// only enforce gender if entry.gender != null (and not "random") and difficulty > easy
		val requiredGender = entry.gender
			?.takeIf { it.lowercase() != "random" && difficulty != "easy" }
			?.uppercase()

		// only enforce nature if entry.nature != null (and not "random") and difficulty ≥ medium
		val requiredNature = entry.nature
			?.takeIf { it.lowercase() != "random" && difficulty in listOf("medium", "hard") }
			?.lowercase()

		// only enforce IVs if entry.ivRange != null and difficulty == hard
		val requiredIVs = entry.ivRange
			?.toIntOrNull()
			?.takeIf { difficulty == "hard" }
			?.let { count ->
				listOf("hp", "attack", "defence", "special_attack", "special_defence", "speed")
					.shuffled()
					.take(count)
			} ?: emptyList()

		val selectedRewards = selectRewardForDifficulty(difficulty)
		return HuntInstance(entry, requiredGender, requiredNature, requiredIVs, selectedRewards)
	}


	// Only start global hunts if they are enabled via config.
	private fun startGlobalHunt() {
		if (!HuntsConfig.config.globalHuntsEnabled) {
			globalHuntStates = emptyList()
			return
		}
		globalCompletedHuntIndices.clear()
		val numHunts = HuntsConfig.config.activeGlobalHuntsAtOnce
		val endTime = System.currentTimeMillis() + HuntsConfig.config.globalTimeLimit * 1000L
		val usedSpecies = mutableSetOf<String>()
		globalHuntStates = (0 until numHunts).mapNotNull { i ->
			val pokemon = if (usedSpecies.size < HuntsConfig.config.globalPokemon.size)
				selectPokemonFromList(HuntsConfig.config.globalPokemon, usedSpecies)
			else
				selectGlobalPokemon()
			pokemon?.also { usedSpecies.add(it.species.lowercase()) }?.let { poke ->
				LogDebug.debug("Selected Pokémon for global hunt #$i: ${poke.species}", MOD_ID)
				val selectedRewards = selectGlobalReward()
				HuntInstance(poke, null, null, emptyList(), selectedRewards, endTime).apply {
					startTime = System.currentTimeMillis()
				}
			} ?: run {
				LogDebug.debug("No Pokémon available for global hunt #$i", MOD_ID)
				null
			}
		}
		sharedEndTime = endTime
		playerData.values.forEach { it.completedGlobalHunts.clear() }
	}


	private fun startGlobalCooldown() {
		globalHuntStates = emptyList()
		sharedEndTime = null
		globalCooldownEnd = System.currentTimeMillis() + HuntsConfig.config.globalCooldown * 1000L
	}

	private fun checkGlobalHuntState() {
		val currentTime = System.currentTimeMillis()
		if (globalHuntStates.isNotEmpty()) {
			// For every global hunt, if its own end time has passed and it hasn't been marked complete, mark it.
			globalHuntStates.withIndex().forEach { (i, hunt) ->
				if (hunt.endTime != null && currentTime > hunt.endTime!!) {
					globalCompletedHuntIndices.add(i)
				}
			}
			// If every hunt (regardless of number) is either completed or expired, start the global cooldown.
			if (globalHuntStates.indices.all { i -> globalCompletedHuntIndices.contains(i) }) {
				LogDebug.debug("All global hunts have expired or been completed; starting cooldown", MOD_ID)
				startGlobalCooldown()
			}
		} else if (currentTime > globalCooldownEnd) {
			// If there are no global hunts and the cooldown is over, start new hunts.
			startGlobalHunt()
		}
	}


	// Only process solo hunt preview logic if solo hunts are enabled.
	fun refreshPreviewPokemon(player: ServerPlayerEntity) {
		if (!HuntsConfig.config.soloHuntsEnabled) return
		val data = getPlayerData(player)
		listOf("easy", "normal", "medium", "hard").forEach { difficulty ->
			if (!isOnCooldown(player, difficulty) && data.activePokemon[difficulty] == null) {
				selectPokemonForDifficulty(difficulty)?.let { pokemon ->
					val instance = createHuntInstance(pokemon, difficulty)
					if (HuntsConfig.config.autoAcceptSoloHunts) {
						activateMission(player, difficulty, instance)
						LogDebug.debug("Auto-activated $difficulty hunt for ${player.name.string}: ${pokemon.species}", MOD_ID)
					} else if (data.previewPokemon[difficulty] == null) {
						setPreviewPokemon(player, difficulty, instance)
						LogDebug.debug("Generated preview for ${player.name.string} on $difficulty: ${pokemon.species}", MOD_ID)
					}
				} ?: LogDebug.debug("No Pokémon available for $difficulty hunt for ${player.name.string}", MOD_ID)
			}
		}
	}


	fun setPreviewPokemon(player: ServerPlayerEntity, difficulty: String, instance: HuntInstance) {
		getPlayerData(player).previewPokemon[difficulty] = instance
	}

	fun getPreviewPokemon(player: ServerPlayerEntity, difficulty: String): HuntInstance? =
		getPlayerData(player).previewPokemon[difficulty]

	// Only activate solo missions if solo hunts are enabled.
	fun activateMission(player: ServerPlayerEntity, difficulty: String, instance: HuntInstance) {
		if (!HuntsConfig.config.soloHuntsEnabled) return
		val data = getPlayerData(player)
		data.activePokemon[difficulty] = instance
		// Record the hunt start time (or always record if you prefer)
		instance.startTime = System.currentTimeMillis()
		instance.endTime = when (difficulty) {
			"easy"   -> HuntsConfig.config.soloEasyTimeLimit
			"normal" -> HuntsConfig.config.soloNormalTimeLimit
			"medium" -> HuntsConfig.config.soloMediumTimeLimit
			"hard"   -> HuntsConfig.config.soloHardTimeLimit
			else     -> 0
		}.takeIf { it > 0 }?.let { System.currentTimeMillis() + it * 1000L }
		data.previewPokemon.remove(difficulty)
	}



	fun getPlayerParty(player: ServerPlayerEntity): List<Pokemon> =
		Cobblemon.storage.getParty(player).toList()

	fun restartHunts() {
		// Clear solo hunts for all players
		playerData.values.forEach { data ->
			data.previewPokemon.clear()
			data.activePokemon.clear()
			data.completedGlobalHunts.clear()
		}
		// Reset global cooldown and start new global hunts
		globalCooldownEnd = 0
		startGlobalHunt()
	}
	fun hasHuntPermission(player: ServerPlayerEntity, tier: String): Boolean {
		val permissionNode = when (tier.lowercase()) {
			"global" -> HuntsConfig.config.permissions.globalHuntPermission
			"easy" -> HuntsConfig.config.permissions.soloEasyHuntPermission
			"normal" -> HuntsConfig.config.permissions.soloNormalHuntPermission
			"medium" -> HuntsConfig.config.permissions.soloMediumHuntPermission
			"hard" -> HuntsConfig.config.permissions.soloHardHuntPermission
			else -> ""
		}
		val source = player.server.commandSource.withEntity(player).withPosition(player.pos)
		return CommandManager.hasPermissionOrOp(
			source,
			permissionNode,
			HuntsConfig.config.permissions.permissionLevel,
			HuntsConfig.config.permissions.opLevel
		)
	}
	fun broadcast(server: MinecraftServer, message: String, player: ServerPlayerEntity? = null) {
		val registryWrapper = server.registryManager
		val formatted = KyoriHelper.parseToMinecraft(message, registryWrapper)
		if (player != null) {
			player.sendMessage(formatted)
		} else {
			server.playerManager.playerList.forEach { p ->
				p.sendMessage(formatted)
			}
		}
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


