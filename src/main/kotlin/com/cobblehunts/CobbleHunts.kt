package com.cobblehunts

import com.cobblehunts.gui.HuntsEditorMainGui
import com.cobblehunts.gui.PlayerHuntsGui
import com.cobblehunts.utils.*
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.Natures
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.pokemon.Pokemon
import com.everlastingutils.command.CommandManager
import com.everlastingutils.scheduling.SchedulerManager
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object CobbleHunts : ModInitializer {
	private val logger = LoggerFactory.getLogger("cobblehunts")
	private val cmdManager = CommandManager("hunts", defaultPermissionLevel = 2, defaultOpLevel = 2)
	private val playerData = mutableMapOf<UUID, PlayerHuntData>()
	private var guiRefreshScheduled = false

	data class GlobalHuntState(
		val instance: HuntInstance,
		var isCompleted: Boolean = false
	)

	var globalHuntState: GlobalHuntState? = null
	var globalCooldownEnd: Long = 0
	private var lastCheckTime: Long = System.currentTimeMillis()

	override fun onInitialize() {
		HuntsConfig.initializeAndLoad()
		registerCommands()
		startGlobalHunt()
		ServerLifecycleEvents.SERVER_STARTED.register { server ->
		}

		ServerTickEvents.END_SERVER_TICK.register { server ->
			onServerTick()
			if (!guiRefreshScheduled) {
				SchedulerManager.scheduleAtFixedRate("cobblehunts-gui-refresh", server, 0, 1, TimeUnit.SECONDS) {
					PlayerHuntsGui.refreshDynamicGuis()
				}
				guiRefreshScheduled = true
			}
		}
		logger.info("CobbleHunts initialized")
	}

	private fun registerCommands() {
		cmdManager.command("hunts", permission = "hunts.use") {
			executes { ctx ->
				val player = ctx.source.player as? ServerPlayerEntity
				if (player != null) {
					PlayerHuntsGui.openMainGui(player)
				} else {
					ctx.source.sendError(Text.literal("This command can only be used by players."))
				}
				1
			}
			subcommand("editor", permission = "hunts.editor") {
				executes { ctx ->
					val player = ctx.source.player as? ServerPlayerEntity
					if (player != null) {
						HuntsEditorMainGui.openGui(player)
					} else {
						ctx.source.sendError(Text.literal("This command can only be used by players."))
					}
					1
				}
			}
			subcommand("reload", permission = "hunts.reload") {
				executes { ctx ->
					HuntsConfig.reloadBlocking()
					HuntsConfig.saveConfig()
					ctx.source.sendMessage(Text.literal("CobbleHunts config reloaded."))
					1
				}
			}
		}
		cmdManager.register()
	}

	internal fun getPlayerData(player: ServerPlayerEntity): PlayerHuntData {
		return playerData.getOrPut(player.uuid) { PlayerHuntData() }
	}

	fun isOnCooldown(player: ServerPlayerEntity, difficulty: String): Boolean {
		val data = getPlayerData(player)
		val cooldownEnd = data.cooldowns[difficulty] ?: 0
		return System.currentTimeMillis() < cooldownEnd
	}

	private fun selectPokemonFromList(pokemonList: List<HuntPokemonEntry>): HuntPokemonEntry? {
		if (pokemonList.isEmpty()) return null
		val totalChance = pokemonList.sumOf { it.chance }
		if (totalChance <= 0) return pokemonList.random()
		var random = Random.nextDouble() * totalChance
		for (entry in pokemonList) {
			random -= entry.chance
			if (random <= 0) return entry
		}
		return pokemonList.last()
	}

	fun selectPokemonForDifficulty(difficulty: String): HuntPokemonEntry? {
		val pokemonList = when (difficulty) {
			"easy" -> HuntsConfig.config.soloEasyPokemon
			"normal" -> HuntsConfig.config.soloNormalPokemon
			"medium" -> HuntsConfig.config.soloMediumPokemon
			"hard" -> HuntsConfig.config.soloHardPokemon
			else -> throw IllegalArgumentException("Unknown difficulty: $difficulty")
		}
		return selectPokemonFromList(pokemonList)
	}

	fun selectGlobalPokemon(): HuntPokemonEntry? {
		return selectPokemonFromList(HuntsConfig.config.globalPokemon)
	}

	fun selectRewardForDifficulty(difficulty: String): LootReward? {
		val lootList = when (difficulty) {
			"easy" -> HuntsConfig.config.soloEasyLoot
			"normal" -> HuntsConfig.config.soloNormalLoot
			"medium" -> HuntsConfig.config.soloMediumLoot
			"hard" -> HuntsConfig.config.soloHardLoot
			else -> return null
		}
		if (lootList.isEmpty()) return null
		val totalChance = lootList.sumOf { it.chance }
		if (totalChance <= 0) return lootList.random()
		var random = Random.nextDouble() * totalChance
		for (reward in lootList) {
			random -= reward.chance
			if (random <= 0) return reward
		}
		return lootList.last()
	}

	fun selectGlobalReward(): LootReward? {
		val lootList = HuntsConfig.config.globalLoot
		if (lootList.isEmpty()) return null
		val totalChance = lootList.sumOf { it.chance }
		if (totalChance <= 0) return lootList.random()
		var random = Random.nextDouble() * totalChance
		for (reward in lootList) {
			random -= reward.chance
			if (random <= 0) return reward
		}
		return lootList.last()
	}

	private fun createHuntInstance(entry: HuntPokemonEntry, difficulty: String): HuntInstance {
		val pokemonProperties = PokemonProperties.parse("${entry.species}${if (entry.form != null) " form=${entry.form}" else ""}")
		val pokemon = pokemonProperties.create()
		val possibleGenders = pokemon.form.possibleGenders

		// Determine requiredGender
		val requiredGender = when (difficulty) {
			"easy" -> null
			"normal", "medium", "hard" -> {
				when {
					entry.gender != null -> {
						when (entry.gender!!.lowercase()) {
							"male" -> "MALE"
							"female" -> "FEMALE"
							"random" -> {
								when {
									possibleGenders.isEmpty() -> "GENDERLESS"
									possibleGenders.size == 1 -> possibleGenders.first().name
									else -> if (Random.nextBoolean()) "MALE" else "FEMALE"
								}
							}
							else -> null
						}
					}
					else -> {
						when {
							possibleGenders.isEmpty() -> "GENDERLESS"
							possibleGenders.size == 1 -> possibleGenders.first().name
							else -> if (Random.nextBoolean()) "MALE" else "FEMALE"
						}
					}
				}
			}
			else -> null
		}

		// Determine requiredNature
		val requiredNature = when (difficulty) {
			"easy", "normal" -> null
			"medium", "hard" -> {
				if (entry.nature == null || entry.nature!!.lowercase() == "random") {
					Natures.all().random().name.path.lowercase()
				} else {
					entry.nature!!.lowercase()
				}
			}
			else -> null
		}

		// Determine requiredIVs
		val allIVs = listOf("hp", "attack", "defense", "special_attack", "special_defense", "speed")
		val requiredIVs = when (difficulty) {
			"hard" -> {
				if (entry.ivRange != null) {
					allIVs.shuffled().take(entry.ivRange!!.toIntOrNull() ?: 2)
				} else {
					allIVs.shuffled().take(2)
				}
			}
			else -> emptyList()
		}

		val reward = selectRewardForDifficulty(difficulty)
		return HuntInstance(entry, requiredGender, requiredNature, requiredIVs, reward)
	}

	private fun startGlobalHunt() {
		val pokemon = selectGlobalPokemon()
		if (pokemon != null) {
			val reward = selectGlobalReward()
			val instance = HuntInstance(
				entry = pokemon,
				requiredGender = null,
				requiredNature = null, // Global hunts do not specify a nature
				requiredIVs = emptyList(),
				reward = reward,
				endTime = System.currentTimeMillis() + (HuntsConfig.config.globalTimeLimit * 1000L)
			)
			globalHuntState = GlobalHuntState(instance)
		} else {
			logger.warn("No Pokémon available for global hunt")
		}
	}

	internal fun startGlobalCooldown() {
		globalHuntState = null
		val cooldown = HuntsConfig.config.globalCooldown
		globalCooldownEnd = System.currentTimeMillis() + (cooldown * 1000L)
	}

	fun onServerTick() {
		val currentTime = System.currentTimeMillis()
		if (currentTime - lastCheckTime >= 60000) {
			lastCheckTime = currentTime
			if (globalHuntState != null) {
				if (!globalHuntState!!.isCompleted && currentTime > globalHuntState!!.instance.endTime!!) {
					logger.info("Global hunt timed out")
					startGlobalCooldown()
				}
			} else if (currentTime > globalCooldownEnd) {
				startGlobalHunt()
			}
		}
	}

	fun refreshPreviewPokemon(player: ServerPlayerEntity) {
		val difficulties = listOf("easy", "normal", "medium", "hard")
		difficulties.forEach { difficulty ->
			val data = getPlayerData(player)
			if (!isOnCooldown(player, difficulty) &&
				data.activePokemon[difficulty] == null &&
				data.previewPokemon[difficulty] == null) {
				val pokemon = selectPokemonForDifficulty(difficulty)
				if (pokemon != null) {
					val instance = createHuntInstance(pokemon, difficulty)
					setPreviewPokemon(player, difficulty, instance)
				} else {
					logger.warn("No Pokémon available for $difficulty, skipping preview for player ${player.name.string}")
				}
			}
		}
	}

	fun setPreviewPokemon(player: ServerPlayerEntity, difficulty: String, instance: HuntInstance) {
		val data = getPlayerData(player)
		data.previewPokemon[difficulty] = instance
	}

	fun getPreviewPokemon(player: ServerPlayerEntity, difficulty: String): HuntInstance? {
		val data = getPlayerData(player)
		return data.previewPokemon[difficulty]
	}

	fun activateMission(player: ServerPlayerEntity, difficulty: String, instance: HuntInstance) {
		val data = getPlayerData(player)
		data.activePokemon[difficulty] = instance
		val timeLimit = when (difficulty) {
			"easy" -> HuntsConfig.config.soloEasyTimeLimit
			"normal" -> HuntsConfig.config.soloNormalTimeLimit
			"medium" -> HuntsConfig.config.soloMediumTimeLimit
			"hard" -> HuntsConfig.config.soloHardTimeLimit
			else -> 0
		}
		if (timeLimit > 0) {
			instance.endTime = System.currentTimeMillis() + (timeLimit * 1000L)
		} else {
			instance.endTime = null
		}
		data.previewPokemon.remove(difficulty)
	}

	fun getGlobalPokemon(): HuntPokemonEntry? {
		return globalHuntState?.instance?.entry
	}

	fun getPlayerParty(player: ServerPlayerEntity): List<Pokemon> {
		return Cobblemon.storage.getParty(player).toList()
	}
}

data class PlayerHuntData(
	val previewPokemon: MutableMap<String, HuntInstance> = mutableMapOf(),
	val activePokemon: MutableMap<String, HuntInstance> = mutableMapOf(),
	val cooldowns: MutableMap<String, Long> = mutableMapOf()
)

data class HuntInstance(
	val entry: HuntPokemonEntry,
	val requiredGender: String?,
	val requiredNature: String?, // Added field for nature
	val requiredIVs: List<String>,
	val reward: LootReward?,
	var endTime: Long? = null
)