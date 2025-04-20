package com.cobblehunts.utils

import com.cobblehunts.CobbleHunts
import com.cobblehunts.CobbleHunts.MOD_ID
import com.cobblehunts.CobbleHunts.broadcast
import com.cobblehunts.HuntInstance
import com.cobblehunts.gui.TurnInGui
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.Pokemon
import com.everlastingutils.utils.LogDebug
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CatchingTracker {
    // Cache mapping captured Pokémon UUIDs to the time they were captured (in milliseconds)
    private val captureCache = ConcurrentHashMap<UUID, Long>()

    fun registerEvents() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe { event ->
            // Pass both player and pokemon to the handler
            handlePokemonCaptured(event.player, event.pokemon)
        }
    }

    fun handlePokemonCaptured(player: ServerPlayerEntity, pokemon: Pokemon) {
        val capturedUuid = pokemon.uuid
        val captureTime = System.currentTimeMillis()
        captureCache[capturedUuid] = captureTime
        LogDebug.debug("Captured Pokémon stored: UUID = $capturedUuid at time $captureTime", MOD_ID)

        if (HuntsConfig.config.autoTurnInOnCapture) {
            // delegate to the GUI’s “turn‑in” logic
            TurnInGui.autoTurnInOnCapture(player, pokemon)
        } else {
            // just notify the player as before
            checkForActiveHunts(player, pokemon)
        }
    }

    // Remove entries older than one hour (adjust cutoff as needed)
    fun cleanupCache() {
        val cutoff = System.currentTimeMillis() - 3600000L
        captureCache.entries.removeIf { it.value < cutoff }
    }

    // Accessor for checking when a Pokémon was captured
    fun getCaptureTime(uuid: UUID): Long? = captureCache[uuid]


    private fun checkForActiveHunts(player: ServerPlayerEntity, pokemon: Pokemon) {
        val data = CobbleHunts.getPlayerData(player)
        val currentTime = System.currentTimeMillis()

        // Gather all still‐active solo and global hunts
        val activeSolo = data.activePokemon.values.filter { it.endTime == null || currentTime < it.endTime!! }
        val activeGlobal = CobbleHunts.globalHuntStates.filter { it.endTime == null || currentTime < it.endTime!! }

        (activeSolo + activeGlobal).forEach { hunt ->
            val entry = hunt.entry

            // 1) species match
            if (!pokemon.species.name.equals(entry.species, ignoreCase = true)) return@forEach

            // 2) form (if specified)
            entry.form?.let { reqForm ->
                if (!pokemon.form.name.equals(reqForm, ignoreCase = true)) return@forEach
            }

            // 3) aspects
            if (!pokemon.aspects.containsAll(entry.aspects)) return@forEach

            // 4) gender (if specified and not "random")
            hunt.requiredGender?.let { reqGender ->
                if (!pokemon.gender.name.equals(reqGender, ignoreCase = true)) return@forEach
            }

            // 5) nature (if specified and difficulty ≥ medium)
            hunt.requiredNature?.let { reqNature ->
                if (!pokemon.nature.name.path.equals(reqNature, ignoreCase = true)) return@forEach
            }

            // 6) IVs (only on hard; require each listed IV ≥ 20)
            if (hunt.requiredIVs.isNotEmpty()) {
                val bad = hunt.requiredIVs.any { ivName ->
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
                if (bad) return@forEach
            }

            // All requirements satisfied → send message
            val msg = HuntsConfig.config.capturedPokemonMessage
                .replace("%pokemon%", pokemon.species.name)
            CobbleHunts.broadcast(player.server, msg, player)
        }
    }

}
