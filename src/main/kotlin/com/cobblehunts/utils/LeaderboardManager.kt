package com.cobblehunts.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object LeaderboardManager {
    private val data = mutableMapOf<String, Int>()

    init {
        loadData()
    }

    private fun loadData() {
        val file = File("config/cobblehunts/leaderboard.json")
        if (file.exists()) {
            val json = file.readText()
            val type = object : TypeToken<Map<String, Int>>() {}.type
            val map: Map<String, Int> = Gson().fromJson(json, type)
            data.putAll(map)
        }
    }

    private fun saveData() {
        val file = File("config/cobblehunts/leaderboard.json")
        file.parentFile.mkdirs()
        val json = Gson().toJson(data)
        file.writeText(json)
    }

    fun addPoints(playerName: String, points: Int) {
        val current = data.getOrDefault(playerName, 0)
        data[playerName] = current + points
        saveData()
    }

    fun getTopPlayers(limit: Int): List<Pair<String, Int>> {
        return data.entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }
    }
    fun getAllPlayerNames(): List<String> {
        return data.keys.toList()
    }

}
