package com.cobblehunts.utils



import com.everlastingutils.utils.logDebug
import com.google.gson.Gson
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
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap

object LeaderboardManager {
    private val data = mutableMapOf<String, Int>()
    private val mojangProfileCache = ConcurrentHashMap<String, GameProfile>()
    private val mojangProfileExecutor = Executors.newSingleThreadExecutor()

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

    fun fetchMojangProfile(playerName: String): CompletableFuture<GameProfile?> {
        // Check cache first on the main thread
        mojangProfileCache[playerName]?.let {
            logDebug("DEBUG: Returning cached Mojang profile for '$playerName'", "cobblehunts")
            return CompletableFuture.completedFuture(it)
        }
        // Run blocking operations asynchronously using a single-thread executor so only one is processed at a time.
        return CompletableFuture.supplyAsync({
            try {
                // Step 1: Get the Mojang ID for the player
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

                    // Step 2: Get the session profile to retrieve texture data
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
                            // Convert the id to a proper UUID (inserting dashes)
                            val uuidString = id.replaceFirst(
                                Regex("([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})"),
                                "$1-$2-$3-$4-$5"
                            )
                            val uuid = java.util.UUID.fromString(uuidString)
                            val profile = GameProfile(uuid, playerName)
                            profile.properties.put("textures", Property("textures", textureValue, signature))
                            // Cache the profile
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
            return@supplyAsync null
        }, mojangProfileExecutor)
    }

}
