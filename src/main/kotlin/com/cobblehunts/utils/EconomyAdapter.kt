package com.cobblehunts.utils

import com.cobblehunts.CobbleHunts
import net.impactdev.impactor.api.economy.EconomyService
import net.impactdev.impactor.api.economy.currency.Currency
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.minecraft.server.network.ServerPlayerEntity
import java.math.BigDecimal

object EconomyAdapter {
    private val service by lazy { EconomyService.instance() }
    private val warnedInvalidCurrency = mutableSetOf<String>()

    /** Find the configured Currency or fallback to primary. */
    fun currency(): Currency {
        val config = HuntsConfig.config
        val id = config.rerollCurrency.ifBlank { return service.currencies().primary() }
        val key = Key.key(if (id.contains(':')) id else "impactor:$id")

        val currencyOpt = service.currencies().currency(key)
        if (currencyOpt.isPresent) {
            return currencyOpt.get()
        }

        // Avoid repeated warning calls.
        if (warnedInvalidCurrency.add(id)) {
            CobbleHunts.logger.warn("[CobbleHunts] Currency '$id' not found, defaulting to primary.")
        }
        return service.currencies().primary()
    }

    /** Key string for chat messages. */
    fun currencyId(): String =
        currency().key().value()

    /** Symbol (e.g., "$") for chat messages. */
    fun symbol(): String =
        PlainTextComponentSerializer.plainText().serialize(currency().symbol())

    /**
     * Attempts withdrawal.
     * Returns true if economy is disabled or withdraw succeeds.
     */
    fun charge(player: ServerPlayerEntity, amount: BigDecimal): Boolean {
        if (!HuntsConfig.config.economyEnabled) return true
        val account = service.account(currency(), player.uuid).join()
        return account.withdraw(amount).successful()
    }
}