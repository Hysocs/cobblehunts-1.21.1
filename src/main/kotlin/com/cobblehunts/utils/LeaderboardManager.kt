package com.cobblehunts.utils

import com.everlastingutils.utils.logDebug
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

object LeaderboardManager {
    // Define a data class for player data with named fields
    data class PlayerData(val points: Int, val texture: String?)

    // Data now stores PlayerData objects
    private val data = mutableMapOf<String, PlayerData>()
    private val mojangProfileCache = ConcurrentHashMap<String, GameProfile>()
    private val mojangProfileExecutor = Executors.newSingleThreadExecutor()
    private val gson = GsonBuilder().setPrettyPrinting().create() // Gson instance with pretty printing

    init {
        loadData()
    }

    private fun loadData() {
        val file = File("config/cobblehunts/leaderboard.json")
        if (file.exists()) {
            val json = file.readText()
            try {
                // Try to load as the new format
                val newType = object : TypeToken<Map<String, PlayerData>>() {}.type
                val map: Map<String, PlayerData> = gson.fromJson(json, newType)
                data.putAll(map)
            } catch (e: Exception) {
                // If new format fails, attempt to load and migrate from old format
                try {
                    val rawType = object : TypeToken<Map<String, Any>>() {}.type
                    val rawMap: Map<String, Any> = gson.fromJson(json, rawType)
                    var migrated = false
                    for ((playerName, value) in rawMap) {
                        when (value) {
                            is Number -> {
                                // Old format with just points
                                data[playerName] = PlayerData(value.toInt(), null)
                                migrated = true
                            }
                            is Map<*, *> -> {
                                // New format with PlayerData
                                val points = (value["points"] as? Number)?.toInt() ?: 0
                                val texture = value["texture"] as? String
                                data[playerName] = PlayerData(points, texture)
                            }
                            else -> {
                                logDebug("DEBUG: Invalid data for player '$playerName'", "cobblehunts")
                            }
                        }
                    }
                    if (migrated) {
                        saveData() // Save in new format after migration
                        logDebug("DEBUG: Migrated old leaderboard data to new format", "cobblehunts")
                    }
                } catch (e2: Exception) {
                    logDebug("DEBUG: Failed to load leaderboard data: ${e2.message}", "cobblehunts")
                }
            }
        }
    }

    private fun saveData() {
        val file = File("config/cobblehunts/leaderboard.json")
        file.parentFile.mkdirs()
        val json = gson.toJson(data) // Pretty-printed JSON
        file.writeText(json)
    }

    fun addPoints(playerName: String, points: Int) {
        val playerData = data.getOrDefault(playerName, PlayerData(0, null))
        data[playerName] = playerData.copy(points = playerData.points + points)
        saveData()
    }

    fun getTopPlayers(limit: Int): List<Triple<String, Int, String?>> {
        return data.entries.sortedByDescending { it.value.points }
            .take(limit)
            .map { Triple(it.key, it.value.points, it.value.texture) }
    }

    fun getAllPlayerNames(): List<String> {
        return data.keys.toList()
    }

    fun updatePlayerTexture(playerName: String, server: net.minecraft.server.MinecraftServer): CompletableFuture<String?> {
        return CompletableFuture.supplyAsync {
            val playerData = data[playerName]
            if (playerData?.texture != null) {
                // Texture is already stored in JSON, use it and do not update
                logDebug("DEBUG: Using stored texture for '$playerName' from JSON", "cobblehunts")
                playerData.texture
            } else {
                // No texture in JSON, proceed to fetch
                server.playerManager.getPlayer(playerName)?.let { onlinePlayer ->
                    // Player is online, fetch texture from their profile
                    onlinePlayer.gameProfile.properties["textures"]?.firstOrNull()?.value.also { texture ->
                        if (texture != null) {
                            // Store the fetched texture in JSON since it wasn't there
                            val updatedData = (playerData ?: PlayerData(0, null)).copy(texture = texture)
                            data[playerName] = updatedData
                            saveData()
                            logDebug("DEBUG: Fetched and stored texture for online player '$playerName'", "cobblehunts")
                        }
                    }
                } ?: run {
                    // Player is offline, fetch from Mojang API
                    fetchMojangProfile(playerName).join()?.properties?.get("textures")?.firstOrNull()?.value.also { texture ->
                        if (texture != null) {
                            // Store the fetched texture in JSON since it wasn't there
                            val updatedData = (playerData ?: PlayerData(0, null)).copy(texture = texture)
                            data[playerName] = updatedData
                            saveData()
                            logDebug("DEBUG: Fetched and stored texture for '$playerName' from Mojang API", "cobblehunts")
                        }
                    }
                }
            }
        }
    }

    fun fetchMojangProfile(playerName: String): CompletableFuture<GameProfile?> {
        mojangProfileCache[playerName]?.let {
            logDebug("DEBUG: Returning cached Mojang profile for '$playerName'", "cobblehunts")
            return CompletableFuture.completedFuture(it)
        }
        return CompletableFuture.supplyAsync({
            try {
                val profileUrl = URL("https://api.mojang.com/users/profiles/minecraft/$playerName")
                val connection = profileUrl.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonElement = Json.parseToJsonElement(response)
                    val jsonObject = jsonElement.jsonObject
                    if (!jsonObject.containsKey("id")) {
                        logDebug("DEBUG: Mojang API did not return an id for '$playerName'", "cobblehunts")
                        return@supplyAsync null
                    }
                    val id = jsonObject["id"]?.jsonPrimitive?.content ?: return@supplyAsync null
                    val sessionUrl = URL("https://sessionserver.mojang.com/session/minecraft/profile/$id?unsigned=false")
                    val sessionConn = sessionUrl.openConnection() as HttpURLConnection
                    sessionConn.requestMethod = "GET"
                    sessionConn.connect()
                    if (sessionConn.responseCode == HttpURLConnection.HTTP_OK) {
                        val sessionResponse = sessionConn.inputStream.bufferedReader().use { it.readText() }
                        val sessionJsonElement = Json.parseToJsonElement(sessionResponse)
                        val sessionJsonObject = sessionJsonElement.jsonObject
                        val properties = sessionJsonObject["properties"]?.jsonArray
                        if (properties != null && properties.size > 0) {
                            val textureProperty = properties[0].jsonObject
                            val textureValue = textureProperty["value"]?.jsonPrimitive?.content
                            val signature = textureProperty["signature"]?.jsonPrimitive?.content
                            if (textureValue == null || signature == null) {
                                logDebug("DEBUG: Texture or signature missing for '$playerName'", "cobblehunts")
                                return@supplyAsync null
                            }
                            val uuidString = id.replaceFirst(
                                Regex("([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})"),
                                "$1-$2-$3-$4-$5"
                            )
                            val uuid = java.util.UUID.fromString(uuidString)
                            val profile = GameProfile(uuid, playerName)
                            profile.properties.put("textures", Property("textures", textureValue, signature))
                            mojangProfileCache[playerName] = profile
                            logDebug("DEBUG: Fetched and cached Mojang profile for '$playerName': $profile", "cobblehunts")
                            return@supplyAsync profile
                        }
                    } else {
                        logDebug("DEBUG: Session server returned code ${sessionConn.responseCode} for '$playerName'", "cobblehunts")
                    }
                } else {
                    logDebug("DEBUG: Mojang API returned code ${connection.responseCode} for '$playerName'", "cobblehunts")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }, mojangProfileExecutor)
    }
}