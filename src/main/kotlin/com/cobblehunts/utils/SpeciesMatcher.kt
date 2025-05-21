package com.cobblehunts.utils

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Pokemon

object SpeciesMatcher {
    private fun normalize(name: String): String {
        return name.lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    /**
     * Checks if the given Pokémon matches the configured species identifier.
     * The configuredSpeciesIdentifier can be a species name (e.g., "Porygon-Z") or a showdownId (e.g., "porygonz").
     */
    fun matches(pokemon: Pokemon, configuredSpeciesIdentifier: String): Boolean {
        val normalizedConfigId = normalize(configuredSpeciesIdentifier)

        // Check against Pokémon's showdownId (already normalized by Cobblemon)
        if (pokemon.species.showdownId() == normalizedConfigId) {
            return true
        }

        // Check against Pokémon's name (after normalizing it)
        val normalizedPokemonName = normalize(pokemon.species.name)
        if (normalizedPokemonName == normalizedConfigId) {
            return true
        }

        return false
    }

    /**
     * Tries to get the canonical "pretty" name for a species,
     * given an identifier that could be a name or a showdownId.
     * Falls back to a title-cased version of the input if no match is found and defaultToConfigured is true.
     */
    fun getPrettyName(identifier: String, defaultToConfigured: Boolean = true): String {
        // Prioritize exact name match (case-insensitive for robustness with user input)
        PokemonSpecies.species.find { it.name.equals(identifier, ignoreCase = true) }?.let { return it.name }

        // Try matching as a showdownId (case-insensitive)
        PokemonSpecies.species.find { it.showdownId().equals(identifier, ignoreCase = true) }?.let { return it.name }

        // Try matching normalized name (e.g. config "Porygon Z" to species name "Porygon-Z")
        val normalizedIdentifier = normalize(identifier)
        PokemonSpecies.species.find { normalize(it.name) == normalizedIdentifier }?.let { return it.name }

        // Fallback
        return if (defaultToConfigured) identifier.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } else identifier
    }

    /**
     * Tries to resolve a species identifier (name or showdownId) to its canonical showdownId.
     * Returns null if no species can be definitively matched.
     * If a non-null return is always desired, consider returning normalized(identifier) as a last resort.
     */
    fun resolveToShowdownId(identifier: String): String? {
        // Try matching as a showdownId first (case-insensitive)
        PokemonSpecies.species.find { it.showdownId().equals(identifier, ignoreCase = true) }?.let { return it.showdownId() }

        // Try matching by exact name (case-insensitive)
        PokemonSpecies.species.find { it.name.equals(identifier, ignoreCase = true) }?.let { return it.showdownId() }

        // Try matching by normalized name to showdownId
        val normalizedIdentifier = normalize(identifier)
        PokemonSpecies.species.find { normalize(it.name) == normalizedIdentifier }?.let { return it.showdownId() }

        // If the normalized identifier itself is a known showdownId (e.g. input was "PorygonZ" and showdownId is "porygonz")
        PokemonSpecies.species.find { it.showdownId() == normalizedIdentifier }?.let { return it.showdownId() }

        return null
    }
}