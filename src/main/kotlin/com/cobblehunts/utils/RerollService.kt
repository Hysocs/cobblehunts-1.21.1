package com.cobblehunts.utils

import com.cobblehunts.CobbleHunts
import com.cobblehunts.PlayerHuntData
import com.everlastingutils.command.CommandManager
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.math.BigDecimal

object RerollService {
    fun tryRerollPreview(player: ServerPlayerEntity, huntType: String): Boolean {
        val config = HuntsConfig.config
        val perms = config.permissions
        val source = player.server.commandSource.withEntity(player).withPosition(player.pos)

        if (!CommandManager.hasPermissionOrOp(source, perms.rerollPermission, perms.permissionLevel, perms.opLevel)) {
            player.sendMessage(Text.literal("You do not have permission to reroll hunts")
                .styled { it.withColor(Formatting.RED) }, false)
            return false
        }

        val difficulty = when (huntType) {
            "soloeasy" -> "easy"
            "solonormal" -> "normal"
            "solomedium" -> "medium"
            "solohard" -> "hard"
            else -> {
                player.sendMessage(Text.literal("Invalid hunt type: $huntType"), false)
                return false
            }
        }

        val data = CobbleHunts.getPlayerData(player)
        val rules = data.rerollRules(difficulty)

        val canBypassLimit = CommandManager.hasPermissionOrOp(source, perms.bypassRerollLimitPermission, perms.permissionLevel, perms.opLevel)
        val canBypassCost = CommandManager.hasPermissionOrOp(source, perms.bypassRerollCostPermission, perms.permissionLevel, perms.opLevel)

        if (!canBypassLimit && !rules.unlimited && rules.used >= rules.limit) {
            player.sendMessage(Text.literal("You have used all ${rules.limit} rerolls today for $difficulty mission.")
                .styled { it.withColor(Formatting.RED) }, false)
            return false
        }

        if (config.economyEnabled && !canBypassCost) {
            if (!EconomyAdapter.charge(player, rules.cost)) {
                player.sendMessage(Text.literal("You need ${EconomyAdapter.symbol()}${rules.cost} ${EconomyAdapter.currencyId()} to reroll.")
                    .styled { it.withColor(Formatting.RED) }, false)
                return false
            }
        }

        data.activePokemon.remove(difficulty)

        val previous = data.previewPokemon[difficulty]?.entry?.species
        val entry = CobbleHunts.selectPokemonForDifficulty(difficulty)?.let { first ->
            if (config.allowRerollDuplicates || previous == null) first
            else run {
                repeat(5) {
                    val candidate = CobbleHunts.selectPokemonForDifficulty(difficulty) ?: return@repeat
                    if (!candidate.species.equals(previous, ignoreCase = true)) return@run candidate
                }
                first
            }
        } ?: run {
            player.sendMessage(
                Text.literal("No $difficulty hunt available right now.")
                    .styled { it.withColor(Formatting.RED) }, false)
            return false
        }

        val preview = CobbleHunts.createHuntInstance(entry, difficulty)
        data.previewPokemon[difficulty] = preview

        if (!canBypassLimit && !rules.unlimited) {
            if (config.universalRerollLimit >= 0) data.globalRerollsToday = rules.used + 1
            else data.rerollsToday[difficulty] = rules.used + 1
        }

        val speciesLabel = entry.species.replaceFirstChar { it.uppercaseChar() }
        player.sendMessage(
            Text.literal("Rerolled $difficulty mission to $speciesLabel.")
            .styled { it.withColor(Formatting.GREEN) }, false)
        return true
    }
}

/** Holds all reroll repeated data for a given difficulty. */
data class RerollRules(
    val cost: BigDecimal,
    val limit: Int,
    val used: Int,
    val unlimited: Boolean
)

/**
 * Extension to [PlayerHuntData] so that all code can call the same logic without duplicate code.
 */
fun PlayerHuntData.rerollRules(difficulty: String, config: HuntsConfigData = HuntsConfig.config): RerollRules {
    resetRerollsIfNeeded()
    val cost = config.rerollCost[difficulty] ?: BigDecimal.ZERO
    val perLimit = config.maxRerollsPerDay[difficulty] ?: 0
    val universal = config.universalRerollLimit
    val limit = if (universal >= 0) universal else perLimit
    val used = if (universal >= 0) globalRerollsToday else rerollsToday.getOrDefault(difficulty, 0)

    val unlimited = (limit == 0)
    return RerollRules(cost, limit, used, unlimited)
}