package com.cobblehunts.utils

import com.cobblehunts.CobbleHunts
import com.cobblehunts.CobbleHunts.MOD_ID
import com.cobblehunts.gui.TurnInGui
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.storage.pc.POKEMON_PER_BOX
import com.cobblemon.mod.common.pokemon.Pokemon
import com.everlastingutils.utils.LogDebug
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CatchingTracker {
    private val captureCache = ConcurrentHashMap<UUID, Long>()

    fun registerEvents() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe { event ->
            handlePokemonCaptured(event.player, event.pokemon)
        }
    }

    fun handlePokemonCaptured(player: ServerPlayerEntity, pokemon: Pokemon) {
        val capturedUuid = pokemon.uuid
        val captureTime = System.currentTimeMillis()
        captureCache[capturedUuid] = captureTime
        LogDebug.debug("Captured Pokémon stored: UUID = $capturedUuid at time $captureTime", MOD_ID)

        if (HuntsConfig.settings.autoTurnInOnCapture) {
            autoTurnInOnCapture(player, pokemon)
        } else {
            checkForActiveHunts(player, pokemon)
        }
    }

    fun cleanupCache() {
        val cutoff = System.currentTimeMillis() - 3600000L
        captureCache.entries.removeIf { it.value < cutoff }
    }

    fun getCaptureTime(uuid: UUID): Long? = captureCache[uuid]

    private fun autoTurnInOnCapture(player: ServerPlayerEntity, pokemon: Pokemon) {
        var selection: HuntSelection? = null

        val party = Cobblemon.storage.getParty(player)
        val partySlot = party.indexOf(pokemon)

        if (partySlot != -1) {
            selection = PartySelection(partySlot, pokemon.uuid)
        } else {
            // Not in party, check PC
            val pc = Cobblemon.storage.getPC(player)
            val boxSize = POKEMON_PER_BOX

            for ((boxIndex, box) in pc.boxes.withIndex()) {
                for (pcSlot in 0 until boxSize) {
                    val pcPokemon = box.get(pcSlot)
                    if (pcPokemon != null && pcPokemon.uuid == pokemon.uuid) {
                        selection = PcSelection(boxIndex, pcSlot, pokemon.uuid)
                        break
                    }
                }
                if (selection != null) break
            }
        }

        if (selection == null) {
            LogDebug.debug("Captured pokemon ${pokemon.uuid} not found in Party or PC, skipping auto-turn-in", MOD_ID)
            return
        }

        if (HuntsConfig.settings.soloHuntsEnabled) {
            for (difficulty in listOf("easy", "normal", "medium", "hard")) {
                val tierEnabled = when (difficulty) {
                    "easy" -> HuntsConfig.settings.soloEasyEnabled
                    "normal" -> HuntsConfig.settings.soloNormalEnabled
                    "medium" -> HuntsConfig.settings.soloMediumEnabled
                    "hard" -> HuntsConfig.settings.soloHardEnabled
                    else -> false
                }
                if (!tierEnabled) continue

                val hunt = CobbleHunts.getPlayerData(player).activePokemon[difficulty] ?: continue

                if (!SpeciesMatcher.matches(pokemon, hunt.entry.species)) continue

                TurnInGui.handleConfirmTurnIn(player, selection, difficulty, null, openGui = false)
                return
            }
        }

        if (HuntsConfig.settings.globalHuntsEnabled) {
            val data = CobbleHunts.getPlayerData(player)
            CobbleHunts.globalHuntStates.withIndex().forEach { (index, hunt) ->
                if (!SpeciesMatcher.matches(pokemon, hunt.entry.species)) return@forEach

                val isCompleted = if (HuntsConfig.settings.lockGlobalHuntsOnCompletionForAllPlayers) {
                    CobbleHunts.globalCompletedHuntIndices.contains(index)
                } else {
                    data.completedGlobalHunts.contains(index)
                }
                if (isCompleted) return@forEach

                TurnInGui.handleConfirmTurnIn(player, selection, "global", index, openGui = false)
                return
            }
        }
    }

    private fun checkForActiveHunts(player: ServerPlayerEntity, pokemon: Pokemon) {
        val data = CobbleHunts.getPlayerData(player)
        val currentTime = System.currentTimeMillis()

        // Get all potentially active hunts for this player
        val activeSolo = data.activePokemon.values.filter { it.endTime == null || currentTime < it.endTime!! }
        val activeGlobal = CobbleHunts.globalHuntStates.filterIndexed { index, hunt ->
            val isCompleted = if (HuntsConfig.settings.lockGlobalHuntsOnCompletionForAllPlayers) {
                CobbleHunts.globalCompletedHuntIndices.contains(index)
            } else {
                data.completedGlobalHunts.contains(index)
            }
            // A global hunt is active for the player if it hasn't expired and they haven't completed it
            (hunt.endTime == null || currentTime < hunt.endTime!!) && !isCompleted
        }

        val allPotentiallyActiveHunts = activeSolo + activeGlobal

        // Filter this list to find hunts that the captured Pokémon actually qualifies for
        val matchingHunts = allPotentiallyActiveHunts.filter { hunt ->
            val entry = hunt.entry

            if (!pokemon.species.name.equals(entry.species, ignoreCase = true)) {
                return@filter false
            }

            entry.form?.let { reqForm ->
                if (!pokemon.form.name.equals(reqForm, ignoreCase = true)) {
                    return@filter false
                }
            }

            if (!pokemon.aspects.containsAll(entry.aspects)) {
                return@filter false
            }

            hunt.requiredGender?.let { reqGender ->
                if (!pokemon.gender.name.equals(reqGender, ignoreCase = true)) {
                    return@filter false
                }
            }

            hunt.requiredNature?.let { reqNature ->
                if (!pokemon.nature.name.path.equals(reqNature, ignoreCase = true)) {
                    return@filter false
                }
            }

            if (hunt.requiredIVs.isNotEmpty()) {
                val hasBadIv = hunt.requiredIVs.any { ivName ->
                    val stat = when (ivName) {
                        "hp"             -> Stats.HP
                        "attack"         -> Stats.ATTACK
                        "defence"        -> Stats.DEFENCE
                        "special_attack" -> Stats.SPECIAL_ATTACK
                        "special_defence"-> Stats.SPECIAL_DEFENCE
                        "speed"          -> Stats.SPEED
                        else             -> null
                    }
                    stat != null && (pokemon.ivs[stat] ?: 0) < 20
                }
                if (hasBadIv) {
                    return@filter false
                }
            }

            // If all checks passed, it's a match
            true
        }

        val matchCount = matchingHunts.size
        if (matchCount > 0) {
            val message = if (matchCount == 1) {
                // For a single match, use the existing configurable message
                HuntsConfig.settings.capturedPokemonMessage
                    .replace("%pokemon%", pokemon.species.name)
            } else {
                // For multiple matches, use a new, more informative message
                "You caught a ${pokemon.species.name} that can be used for $matchCount active hunts!"
            }
            CobbleHunts.broadcast(player.server, message, player)
        }
    }
}