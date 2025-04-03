package com.cobblehunts

import com.cobblehunts.gui.huntsgui.PlayerHuntsGui
import com.cobblehunts.utils.*
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.Natures
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.pokemon.Pokemon
import com.everlastingutils.command.CommandManager
import com.everlastingutils.scheduling.SchedulerManager
import com.everlastingutils.utils.LogDebug
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.network.ServerPlayerEntity
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.random.Random

object CobbleHunts : ModInitializer {
	private val logger = LoggerFactory.getLogger("cobblehunts")
	private const val MOD_ID = "cobblehunts"
	private val playerData = mutableMapOf<UUID, PlayerHuntData>()
	val removedPokemonCache = mutableMapOf<UUID, MutableList<Pokemon>>()
	// Global hunt state variables
	var globalHuntStates: List<HuntInstance> = emptyList()
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

		// Start global hunts immediately and ensure no cooldown on startup
		startGlobalHunt()
		globalCooldownEnd = 0

		ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
			playerData.remove(handler.player.uuid)
		}

		ServerLifecycleEvents.SERVER_STARTED.register { server ->
			SchedulerManager.scheduleAtFixedRate("cobblehunts-gui-refresh", server, 0, 1, TimeUnit.SECONDS) {
				PlayerHuntsGui.refreshDynamicGuis()
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

	fun selectRewardForDifficulty(difficulty: String): LootReward? {
		val lootList = when (difficulty) {
			"easy"   -> HuntsConfig.config.soloEasyLoot
			"normal" -> HuntsConfig.config.soloNormalLoot
			"medium" -> HuntsConfig.config.soloMediumLoot
			"hard"   -> HuntsConfig.config.soloHardLoot
			else     -> return null
		}
		return weightedRandom(lootList) { it.chance }
	}

	fun selectGlobalReward(): LootReward? =
		weightedRandom(HuntsConfig.config.globalLoot) { it.chance }

	// Creates a hunt instance with inline decisions for gender, nature, and IVs.
	private fun createHuntInstance(entry: HuntPokemonEntry, difficulty: String): HuntInstance {
		val props = PokemonProperties.parse("${entry.species}${entry.form?.let { " form=$it" } ?: ""}")
		val pokemon = props.create()
		val possibleGenders = pokemon.form.possibleGenders
		val requiredGender = if (difficulty == "easy") null else entry.gender?.lowercase()?.let { when (it) {
			"male"    -> "MALE"
			"female"  -> "FEMALE"
			"random"  -> if (possibleGenders.isEmpty()) "GENDERLESS"
			else if (possibleGenders.size == 1) possibleGenders.first().name
			else if (Random.nextBoolean()) "MALE" else "FEMALE"
			else      -> null
		} } ?: if (possibleGenders.isEmpty()) "GENDERLESS"
		else if (possibleGenders.size == 1) possibleGenders.first().name
		else if (Random.nextBoolean()) "MALE" else "FEMALE"
		val requiredNature = if (difficulty in listOf("medium", "hard"))
			entry.nature?.takeIf { it.lowercase() != "random" }?.lowercase()
				?: Natures.all().random().name.path.lowercase() else null
		val requiredIVs = if (difficulty == "hard")
			listOf("hp", "attack", "defense", "special_attack", "special_defense", "speed")
				.shuffled().take(entry.ivRange?.toIntOrNull() ?: 2)
		else emptyList()
		return HuntInstance(entry, requiredGender, requiredNature, requiredIVs, selectRewardForDifficulty(difficulty))
	}

	private fun startGlobalHunt() {
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
				HuntInstance(poke, null, null, emptyList(), selectGlobalReward(), endTime)
			} ?: run {
				logger.warn("No Pokémon available for global hunt #$i")
				null
			}
		}
		sharedEndTime = endTime
		playerData.values.forEach { it.completedGlobalHunts.clear() }
	}

	internal fun startGlobalCooldown() {
		globalHuntStates = emptyList()
		sharedEndTime = null
		globalCooldownEnd = System.currentTimeMillis() + HuntsConfig.config.globalCooldown * 1000L
	}

	private fun checkGlobalHuntState() {
		val currentTime = System.currentTimeMillis()
		when {
			sharedEndTime != null && currentTime > sharedEndTime!! -> {
				logger.info("All global hunts have expired, starting cooldown")
				startGlobalCooldown()
			}
			globalHuntStates.isEmpty() && currentTime > globalCooldownEnd -> startGlobalHunt()
		}
	}

	fun refreshPreviewPokemon(player: ServerPlayerEntity) {
		val data = getPlayerData(player)
		listOf("easy", "normal", "medium", "hard").forEach { difficulty ->
			if (!isOnCooldown(player, difficulty)
				&& data.activePokemon[difficulty] == null
				&& data.previewPokemon[difficulty] == null) {
				selectPokemonForDifficulty(difficulty)?.let { pokemon ->
					LogDebug.debug("Generating preview for ${player.name.string} on $difficulty: ${pokemon.species}", MOD_ID)
					setPreviewPokemon(player, difficulty, createHuntInstance(pokemon, difficulty))
				} ?: logger.warn("No Pokémon available for $difficulty preview for ${player.name.string}")
			}
		}
	}

	fun setPreviewPokemon(player: ServerPlayerEntity, difficulty: String, instance: HuntInstance) {
		getPlayerData(player).previewPokemon[difficulty] = instance
	}

	fun getPreviewPokemon(player: ServerPlayerEntity, difficulty: String): HuntInstance? =
		getPlayerData(player).previewPokemon[difficulty]

	fun activateMission(player: ServerPlayerEntity, difficulty: String, instance: HuntInstance) {
		val data = getPlayerData(player)
		data.activePokemon[difficulty] = instance
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


}

data class PlayerHuntData(
	val previewPokemon: MutableMap<String, HuntInstance> = mutableMapOf(),
	val activePokemon: MutableMap<String, HuntInstance> = mutableMapOf(),
	val cooldowns: MutableMap<String, Long> = mutableMapOf(),
	val completedGlobalHunts: MutableSet<Int> = mutableSetOf()
)

data class HuntInstance(
	val entry: HuntPokemonEntry,
	val requiredGender: String?,
	val requiredNature: String?,
	val requiredIVs: List<String>,
	val reward: LootReward?,
	var endTime: Long? = null
)
