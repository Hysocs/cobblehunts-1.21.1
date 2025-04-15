package com.cobblehunts.utils

import com.cobblehunts.CobbleHunts
import com.cobblehunts.CobbleHunts.MOD_ID
import com.cobblehunts.CobbleHunts.broadcast
import com.cobblehunts.HuntInstance
import com.cobblemon.mod.common.api.events.CobblemonEvents
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

    private fun handlePokemonCaptured(player: ServerPlayerEntity, pokemon: Pokemon) {
        val capturedUuid: UUID = pokemon.uuid
        val captureTime = System.currentTimeMillis()
        captureCache[capturedUuid] = captureTime
        LogDebug.debug("Captured Pokémon stored: UUID = $capturedUuid at time $captureTime", MOD_ID)

        // Check for active hunts and notify the player
        checkForActiveHunts(player, pokemon)
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

        val activeSoloHunts = data.activePokemon.values.filter { hunt ->
            hunt.endTime == null || currentTime < hunt.endTime!!
        }

        val activeGlobalHunts = CobbleHunts.globalHuntStates.filter { hunt ->
            hunt.endTime == null || currentTime < hunt.endTime!!
        }

        val matchingHunts = mutableListOf<HuntInstance>()

        for (hunt in activeSoloHunts) {
            if (hunt.entry.species.equals(pokemon.species.name, ignoreCase = true)) {
                matchingHunts.add(hunt)
            }
        }

        for (hunt in activeGlobalHunts) {
            if (hunt.entry.species.equals(pokemon.species.name, ignoreCase = true)) {
                matchingHunts.add(hunt)
            }
        }

        if (matchingHunts.isNotEmpty()) {
            val messageTemplate = HuntsConfig.config.capturedPokemonMessage
            val message = messageTemplate.replace("%pokemon%", pokemon.species.name)
            broadcast(player.server, message, player) // Send to individual player
        }
    }
}
