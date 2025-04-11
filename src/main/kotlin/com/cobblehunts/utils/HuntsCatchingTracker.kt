package com.cobblehunts.utils

import com.cobblehunts.CobbleHunts.MOD_ID
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.pokemon.Pokemon
import com.everlastingutils.utils.LogDebug
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CatchingTracker {
    // Cache mapping captured Pokémon UUIDs to the time they were captured (in milliseconds)
    private val captureCache = ConcurrentHashMap<UUID, Long>()

    fun registerEvents() {
        // Subscribe to the Pokémon captured event
        CobblemonEvents.POKEMON_CAPTURED.subscribe { event ->
            handlePokemonCaptured(event.pokemon)
        }
    }

    private fun handlePokemonCaptured(pokemon: Pokemon) {
        // Get the Pokémon's UUID and store it with the current time
        val capturedUuid: UUID = pokemon.uuid  // Assumes the Pokémon entity exposes a uuid property
        val captureTime = System.currentTimeMillis()
        captureCache[capturedUuid] = captureTime
        LogDebug.debug("Captured Pokémon stored: UUID = $capturedUuid at time $captureTime", MOD_ID)
    }

    // Remove entries older than one hour (adjust cutoff as needed)
    fun cleanupCache() {
        val cutoff = System.currentTimeMillis() - 3600000L
        captureCache.entries.removeIf { it.value < cutoff }
    }

    // Accessor for checking when a Pokémon was captured
    fun getCaptureTime(uuid: UUID): Long? = captureCache[uuid]
}
