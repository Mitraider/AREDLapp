package com.example.aredlapp.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.aredlapp.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

data class AuthState(
    val isAuthenticated: Boolean = false,
    val userId: String? = null,
    val username: String? = null,
    val globalName: String? = null,
    val discordId: String? = null,
    val discordAvatar: String? = null,
    val accessToken: String? = null,
    val accessExpires: String? = null,
    val refreshToken: String? = null,
    val refreshExpires: String? = null,
    val lastError: String? = null
)

class AredlViewModel(application: Application) : AndroidViewModel(application) {

    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 20000; connectTimeoutMillis = 15000 }
    }

    private val _levels = MutableStateFlow<List<LevelResponse>>(emptyList())
    val levels: StateFlow<List<LevelResponse>> = _levels

    private val _allPlayers = MutableStateFlow<List<LeaderboardResponse>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage
    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages

    val leaderboard: StateFlow<List<LeaderboardResponse>> = combine(_allPlayers, _currentPage, _searchQuery) { all, page, query ->
        if (query.isBlank()) {
            val start = (page - 1) * 100
            if (all.isEmpty()) emptyList() else all.subList(start.coerceIn(0, all.size), (start + 100).coerceIn(0, all.size))
        } else {
            all.filter { (it.user?.global_name ?: "").contains(query, true) || (it.user?.username ?: "").contains(query, true) }.take(50)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _currentLevelVictors = MutableStateFlow<List<LevelRecord>>(emptyList())
    val currentLevelVictors: StateFlow<List<LevelRecord>> = _currentLevelVictors

    private val _favoriteLevels = MutableStateFlow<Set<String>>(emptySet())
    val favoriteLevels: StateFlow<Set<String>> = _favoriteLevels
    private val _todoLevels = MutableStateFlow<Set<String>>(emptySet())
    val todoLevels: StateFlow<Set<String>> = _todoLevels
    private val _completedLevels = MutableStateFlow<Set<String>>(emptySet())
    val completedLevels: StateFlow<Set<String>> = _completedLevels

    private val _availableTags = MutableStateFlow<List<String>>(emptyList())
    val availableTags: StateFlow<List<String>> = _availableTags

    private val _roles = MutableStateFlow<List<RoleResponse>>(emptyList())
    val roles: StateFlow<List<RoleResponse>> = _roles

    private val _selectedLevel = MutableStateFlow<LevelResponse?>(null)
    val selectedLevel: StateFlow<LevelResponse?> = _selectedLevel
    private val _selectedPlayer = MutableStateFlow<LeaderboardResponse?>(null)
    val selectedPlayer: StateFlow<LeaderboardResponse?> = _selectedPlayer
    private val _selectedPlayerProfile = MutableStateFlow<ProfileResponse?>(null)
    val selectedPlayerProfile: StateFlow<ProfileResponse?> = _selectedPlayerProfile
    private val _isAuthenticatedProfileView = MutableStateFlow(false)
    val isAuthenticatedProfileView: StateFlow<Boolean> = _isAuthenticatedProfileView
    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState

    // State for games
    private val _roulettePercent = MutableStateFlow(1)
    val roulettePercent: StateFlow<Int> = _roulettePercent
    private val _rouletteHistory = MutableStateFlow<List<RecordInfo>>(emptyList())
    val rouletteHistory: StateFlow<List<RecordInfo>> = _rouletteHistory
    private val _currentRouletteId = MutableStateFlow<String?>(null)
    val currentRouletteLevel: StateFlow<LevelResponse?> = combine(_levels, _currentRouletteId) { list, id ->
        list.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _alphabetProgress = MutableStateFlow(0)
    val alphabetProgress: StateFlow<Int> = _alphabetProgress
    private val _alphabetHistory = MutableStateFlow<List<String>>(emptyList())
    val alphabetHistory: StateFlow<List<String>> = _alphabetHistory
    private val _currentAlphabetId = MutableStateFlow<String?>(null)
    val currentAlphabetLevel: StateFlow<LevelResponse?> = combine(_levels, _currentAlphabetId) { list, id ->
        list.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _alphabetWon = MutableStateFlow(false)
    val alphabetWon: StateFlow<Boolean> = _alphabetWon
    private val _rouletteWon = MutableStateFlow(false)
    val rouletteWon: StateFlow<Boolean> = _rouletteWon

    private val playerMap = ConcurrentHashMap<String, LeaderboardResponse>()
    private val profileCache = ConcurrentHashMap<String, ProfileResponse>()
    private val playerRanks = ConcurrentHashMap<String, Int>()
    
    private val prefs = application.getSharedPreferences("aredl_v33_bulldozer_mega", Context.MODE_PRIVATE)
    private val authPrefs: SharedPreferences? = createEncryptedAuthPrefs(application)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val authRefreshMutex = Mutex()
    
    private var victorsJob: Job? = null
    private var backgroundFetchJob: Job? = null

    init {
        loadLocalData()
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            loadAllFromCache()
            fetchRoles()
            val lJob = async { fetchLevels() }
            val pJob = async { fetchLeaderboardFirstPage() }
            lJob.await()
            val (pages, shouldFetchRemaining, lastRefreshed) = pJob.await()
            _isLoading.value = false
            if (shouldFetchRemaining && pages > 1) {
                loadRemainingLeaderboardPages(pages, lastRefreshed)
            }
            startBackgroundLevelFetch()
            if (_authState.value.isAuthenticated) {
                refreshAuthenticatedUser()
            }
        }
    }

    private fun loadLocalData() {
        _favoriteLevels.value = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        _todoLevels.value = prefs.getStringSet("todo", emptySet()) ?: emptySet()
        _completedLevels.value = prefs.getStringSet("completed", emptySet()) ?: emptySet()
        _roulettePercent.value = prefs.getInt("roulette_p", 1)
        _alphabetProgress.value = prefs.getInt("alpha_p", 0)
        _alphabetHistory.value = prefs.getStringSet("alphabet_h", emptySet())?.toList() ?: emptyList()
        loadAuthFromCache()
    }

    private fun loadAuthFromCache() {
        val p = authPrefs ?: run {
            _authState.value = AuthState()
            return
        }
        val accessToken = p.getString("auth_access_token", null)
        if (accessToken.isNullOrBlank()) {
            _authState.value = AuthState()
            return
        }
        _authState.value = AuthState(
            isAuthenticated = true,
            userId = p.getString("auth_user_id", null),
            username = p.getString("auth_username", null),
            globalName = p.getString("auth_global_name", null),
            discordId = p.getString("auth_discord_id", null),
            discordAvatar = p.getString("auth_discord_avatar", null),
            accessToken = accessToken,
            accessExpires = p.getString("auth_access_expires", null),
            refreshToken = p.getString("auth_refresh_token", null),
            refreshExpires = p.getString("auth_refresh_expires", null)
        )
    }

    private fun persistAuthState(state: AuthState) {
        val p = authPrefs ?: return
        p.edit().apply {
            if (!state.isAuthenticated || state.accessToken.isNullOrBlank()) {
                remove("auth_user_id")
                remove("auth_username")
                remove("auth_global_name")
                remove("auth_discord_id")
                remove("auth_discord_avatar")
                remove("auth_access_token")
                remove("auth_access_expires")
                remove("auth_refresh_token")
                remove("auth_refresh_expires")
            } else {
                putString("auth_user_id", state.userId)
                putString("auth_username", state.username)
                putString("auth_global_name", state.globalName)
                putString("auth_discord_id", state.discordId)
                putString("auth_discord_avatar", state.discordAvatar)
                putString("auth_access_token", state.accessToken)
                putString("auth_access_expires", state.accessExpires)
                putString("auth_refresh_token", state.refreshToken)
                putString("auth_refresh_expires", state.refreshExpires)
            }
        }.apply()
    }

    private fun createEncryptedAuthPrefs(context: Context): SharedPreferences? {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "aredl_auth_secure",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun loadAllFromCache() {
        try {
            prefs.getString("cached_levels", null)?.let { cachedJson ->
                val cached: List<LevelResponse> = json.decodeFromString(ListSerializer(LevelResponse.serializer()), cachedJson)
                _levels.value = cached
                _availableTags.value = cached.flatMap { it.tags ?: emptyList() }.distinct().sorted()
            }
            prefs.getString("cached_all_players", null)?.let { cachedJson ->
                val cached: List<LeaderboardResponse> = json.decodeFromString(ListSerializer(LeaderboardResponse.serializer()), cachedJson)
                updateLeaderboardData(cached, saveToCache = false)
            }
            prefs.getString("cached_roles", null)?.let { cachedJson ->
                val cached: List<RoleResponse> = json.decodeFromString(ListSerializer(RoleResponse.serializer()), cachedJson)
                _roles.value = cached
            }
        } catch (e: Exception) {}
    }

    private fun updateLeaderboardData(data: List<LeaderboardResponse>, saveToCache: Boolean = true) {
        data.forEach { player ->
            player.user?.id?.let { id ->
                val processed = player.copy(total_points = ensureDivided(player.total_points))
                playerMap[id] = processed
                playerRanks[id] = player.rank ?: 0
            }
        }
        val sortedList = playerMap.values.sortedBy { it.rank ?: Int.MAX_VALUE }
        _allPlayers.value = sortedList
        if (saveToCache) {
            persistLeaderboardCache(sortedList)
        }
    }

    private fun persistLeaderboardCache(players: List<LeaderboardResponse>, lastRefreshed: String? = null) {
        try {
            prefs.edit().apply {
                putString("cached_all_players", json.encodeToString(ListSerializer(LeaderboardResponse.serializer()), players))
                if (!lastRefreshed.isNullOrBlank()) {
                    putString("cached_leaderboard_last_refreshed", lastRefreshed)
                }
            }.apply()
        } catch (e: Exception) {}
    }

    private fun ensureDivided(points: Double?): Double? {
        if (points == null) return null
        return if (points >= 500.0) points / 10.0 else points
    }

    private fun extractCreator(level: LevelResponse): String? {
        return level.global_name?.takeIf { it.isNotBlank() && it != "AREDL" }
            ?: level.creator?.global_name ?: level.creator?.username ?: "AREDL"
    }

    private suspend fun fetchLevels() {
        try {
            val response: HttpResponse = client.get("https://api.aredl.net/api/aredl/levels/")
            val res: List<LevelResponse> = response.body()
            val processed = res.map { it.copy(points = it.points / 10.0, global_name = extractCreator(it)) }
            _levels.value = processed
            saveLevelsToCache(processed)
            withContext(Dispatchers.Main) { pickNewRouletteLevel(); pickNewAlphabetLevel() }
        } catch (e: Exception) {}
    }

    private fun saveLevelsToCache(list: List<LevelResponse>) {
        try { prefs.edit().putString("cached_levels", json.encodeToString(ListSerializer(LevelResponse.serializer()), list)).apply() } catch (e: Exception) {}
    }

    private suspend fun fetchRoles() {
        try {
            val response = client.get("https://api.aredl.net/v2/api/roles")
            val root = Json.parseToJsonElement(response.bodyAsText())
            val rolesList = mutableListOf<RoleResponse>()

            if (root is JsonArray) {
                root.forEach { element ->
                    if (element !is JsonObject) return@forEach

                    val hidden = element["hide"]?.jsonPrimitive?.booleanOrNull ?: false
                    val rawRoleDesc = element["role_desc"]?.jsonPrimitive?.contentOrNull ?: ""
                    val normalizedRoleDesc = rawRoleDesc.lowercase().trim()

                    if (hidden || normalizedRoleDesc.contains("discharge")) return@forEach

                    val roleName = rawRoleDesc
                        .replace('_', ' ')
                        .split(' ')
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { token ->
                            token.lowercase().replaceFirstChar { c -> c.titlecase() }
                        }
                        .ifBlank { "Role" }

                    rolesList.add(
                        RoleResponse(
                            id = element["id"],
                            name = roleName,
                            color = element["color"]?.jsonPrimitive?.contentOrNull,
                            users = element["users"]?.jsonArray?.toList() ?: emptyList(),
                            hide = hidden,
                            privilegeLevel = element["privilege_level"]?.jsonPrimitive?.intOrNull ?: 0
                        )
                    )
                }
            }

            if (rolesList.isNotEmpty()) {
                val sortedRoles = rolesList.sortedByDescending { it.privilegeLevel }
                _roles.value = sortedRoles
                prefs.edit().putString("cached_roles", json.encodeToString(ListSerializer(RoleResponse.serializer()), sortedRoles)).apply()
            }
        } catch (e: Exception) {}
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startBackgroundLevelFetch() {
        backgroundFetchJob?.cancel()
        backgroundFetchJob = viewModelScope.launch(Dispatchers.IO) {
            val priorityLevels = _levels.value.take(500)
            var changed = false
            priorityLevels.asFlow().flatMapMerge(concurrency = 5) { level ->
                flow {
                    if (level.global_name == null || level.global_name == "AREDL") {
                        try {
                            val full: LevelResponse = client.get("https://api.aredl.net/api/aredl/levels/${level.id}/").body()
                            val creator = extractCreator(full)
                            if (creator != null && creator != "AREDL") emit(level.id to creator)
                            delay(100)
                        } catch (e: Exception) {}
                    }
                }
            }.collect { (id, creator) ->
                changed = true
                _levels.update { list -> list.map { if (it.id == id) it.copy(global_name = creator) else it } }
            }
            if (changed) saveLevelsToCache(_levels.value)
        }
    }

    private suspend fun fetchLeaderboardFirstPage(): Triple<Int, Boolean, String?> {
        return try {
            val res: PaginatedLeaderboardResponse = client.get("https://api.aredl.net/api/aredl/leaderboard?page=1").body()
            _totalPages.value = res.pages
            val apiLastRefreshed = res.last_refreshed
            val cachedLastRefreshed = prefs.getString("cached_leaderboard_last_refreshed", null)
            val hasCachedPlayers = _allPlayers.value.isNotEmpty()

            // If nothing changed upstream, keep current cache and avoid a full refetch.
            if (!apiLastRefreshed.isNullOrBlank() && hasCachedPlayers && apiLastRefreshed == cachedLastRefreshed) {
                Triple(res.pages, false, apiLastRefreshed)
            } else {
                playerMap.clear()
                playerRanks.clear()
                updateLeaderboardData(res.data, saveToCache = res.pages <= 1)
                if (res.pages <= 1) {
                    persistLeaderboardCache(_allPlayers.value, apiLastRefreshed)
                }
                Triple(res.pages, true, apiLastRefreshed)
            }
        } catch (e: Exception) { Triple(1, false, null) }
    }

    private fun loadRemainingLeaderboardPages(total: Int, lastRefreshed: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            (2..total).forEach { p ->
                try {
                    val data = client.get("https://api.aredl.net/api/aredl/leaderboard?page=$p").body<PaginatedLeaderboardResponse>().data
                    updateLeaderboardData(data, saveToCache = false)
                } catch (e: Exception) {}
            }
            persistLeaderboardCache(_allPlayers.value, lastRefreshed)
        }
    }

    fun completeDiscordLogin(callbackUrl: String) {
        if (callbackUrl.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = client.get(callbackUrl)
                val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                val userObj = root["user"]?.jsonObject ?: root

                val accessToken = root["access_token"]?.jsonPrimitive?.contentOrNull
                val refreshToken = root["refresh_token"]?.jsonPrimitive?.contentOrNull
                if (accessToken.isNullOrBlank()) {
                    throw IllegalStateException("No access token received from callback")
                }

                val auth = AuthState(
                    isAuthenticated = true,
                    userId = userObj["id"]?.jsonPrimitive?.contentOrNull ?: root["id"]?.jsonPrimitive?.contentOrNull,
                    username = userObj["username"]?.jsonPrimitive?.contentOrNull ?: root["username"]?.jsonPrimitive?.contentOrNull,
                    globalName = userObj["global_name"]?.jsonPrimitive?.contentOrNull ?: root["global_name"]?.jsonPrimitive?.contentOrNull,
                    discordId = userObj["discord_id"]?.jsonPrimitive?.contentOrNull ?: root["discord_id"]?.jsonPrimitive?.contentOrNull,
                    discordAvatar = userObj["discord_avatar"]?.jsonPrimitive?.contentOrNull ?: root["discord_avatar"]?.jsonPrimitive?.contentOrNull,
                    accessToken = accessToken,
                    accessExpires = root["access_expires"]?.jsonPrimitive?.contentOrNull,
                    refreshToken = refreshToken,
                    refreshExpires = root["refresh_expires"]?.jsonPrimitive?.contentOrNull,
                    lastError = null
                )
                _authState.value = auth
                persistAuthState(auth)
                refreshAuthenticatedUser()
            } catch (e: Exception) {
                _authState.value = _authState.value.copy(
                    isAuthenticated = false,
                    lastError = e.message ?: "Login failed"
                )
                persistAuthState(AuthState())
            }
        }
    }

    fun refreshAuthenticatedUser() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = ensureValidAccessToken() ?: return@launch
                val response = client.get("https://api.aredl.net/v2/api/users/@me") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                if (response.status == HttpStatusCode.Unauthorized) {
                    val refreshedToken = refreshAccessToken() ?: throw IllegalStateException("Session expired")
                    val retry = client.get("https://api.aredl.net/v2/api/users/@me") {
                        header(HttpHeaders.Authorization, "Bearer $refreshedToken")
                    }
                    applyAuthenticatedUserResponse(retry.bodyAsText())
                } else {
                    applyAuthenticatedUserResponse(response.bodyAsText())
                }
                syncCompletedFromAuthenticatedRecords()
                selectAuthenticatedPlayer()
            } catch (e: Exception) {
                _authState.value = _authState.value.copy(lastError = e.message ?: "Failed to fetch /users/@me")
            }
        }
    }

    private fun applyAuthenticatedUserResponse(body: String) {
        val root = Json.parseToJsonElement(body).jsonObject
        val userObj = root["user"]?.jsonObject ?: root

        val refreshed = _authState.value.copy(
            isAuthenticated = true,
            userId = userObj["id"]?.jsonPrimitive?.contentOrNull ?: _authState.value.userId,
            username = userObj["username"]?.jsonPrimitive?.contentOrNull ?: _authState.value.username,
            globalName = userObj["global_name"]?.jsonPrimitive?.contentOrNull ?: _authState.value.globalName,
            discordId = userObj["discord_id"]?.jsonPrimitive?.contentOrNull ?: _authState.value.discordId,
            discordAvatar = userObj["discord_avatar"]?.jsonPrimitive?.contentOrNull ?: _authState.value.discordAvatar,
            lastError = null
        )
        _authState.value = refreshed
        persistAuthState(refreshed)
    }

    private suspend fun ensureValidAccessToken(): String? {
        val access = _authState.value.accessToken
        return if (!access.isNullOrBlank()) access else refreshAccessToken()
    }

    private suspend fun refreshAccessToken(): String? = authRefreshMutex.withLock {
        val refresh = _authState.value.refreshToken ?: return@withLock null
        return try {
            val response = client.get("https://api.aredl.net/v2/api/auth/discord/refresh") {
                header(HttpHeaders.Authorization, "Bearer $refresh")
            }
            if (!response.status.isSuccess()) return@withLock null

            val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val newAccess = root["access_token"]?.jsonPrimitive?.contentOrNull ?: return@withLock null
            val newRefresh = root["refresh_token"]?.jsonPrimitive?.contentOrNull ?: _authState.value.refreshToken

            val updated = _authState.value.copy(
                isAuthenticated = true,
                accessToken = newAccess,
                accessExpires = root["access_expires"]?.jsonPrimitive?.contentOrNull ?: _authState.value.accessExpires,
                refreshToken = newRefresh,
                refreshExpires = root["refresh_expires"]?.jsonPrimitive?.contentOrNull ?: _authState.value.refreshExpires,
                lastError = null
            )
            _authState.value = updated
            persistAuthState(updated)
            newAccess
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun syncCompletedFromAuthenticatedRecords() {
        val token = ensureValidAccessToken() ?: return
        try {
            val response = client.get("https://api.aredl.net/v2/api/aredl/records/@me") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (!response.status.isSuccess()) return

            val root = Json.parseToJsonElement(response.bodyAsText())
            val records = when (root) {
                is JsonArray -> root
                is JsonObject -> root["data"]?.jsonArray ?: JsonArray(emptyList())
                else -> JsonArray(emptyList())
            }

            val knownLevels = _levels.value
            if (knownLevels.isEmpty()) return

            val completedIds = mutableSetOf<String>()
            records.forEach { rec ->
                val obj = rec as? JsonObject ?: return@forEach
                val levelObj = obj["level"]?.jsonObject

                val uuid = levelObj?.get("id")?.jsonPrimitive?.contentOrNull
                    ?: obj["id"]?.jsonPrimitive?.contentOrNull
                if (!uuid.isNullOrBlank()) {
                    knownLevels.find { it.id == uuid }?.let { completedIds.add(it.id) }
                }

                val gdId = levelObj?.get("level_id")?.jsonPrimitive?.intOrNull
                    ?: obj["level_id"]?.jsonPrimitive?.intOrNull
                if (gdId != null) {
                    knownLevels.filter { it.level_id == gdId }.forEach { completedIds.add(it.id) }
                }
            }

            _completedLevels.value = completedIds
            prefs.edit().putStringSet("completed", completedIds).apply()
        } catch (_: Exception) {}
    }

    fun selectAuthenticatedPlayer(): Boolean {
        val auth = _authState.value
        val player = _allPlayers.value.firstOrNull { p ->
            val u = p.user
            val byId = !auth.userId.isNullOrBlank() && u?.id == auth.userId
            val byName = !auth.username.isNullOrBlank() && u?.username.equals(auth.username, ignoreCase = true)
            byId || byName
        } ?: return false
        selectPlayer(player, isAuthenticatedProfile = true)
        return true
    }

    fun logoutDiscord() {
        viewModelScope.launch(Dispatchers.IO) {
            val access = _authState.value.accessToken
            if (!access.isNullOrBlank()) {
                try {
                    client.post("https://api.aredl.net/v2/api/auth/logout-all") {
                        header(HttpHeaders.Authorization, "Bearer $access")
                    }
                } catch (e: Exception) {}
            }
            _authState.value = AuthState()
            _completedLevels.value = emptySet()
            prefs.edit().putStringSet("completed", emptySet()).apply()
            persistAuthState(AuthState())
        }
    }

    fun selectLevel(level: LevelResponse) {
        _selectedLevel.value = level
        _currentLevelVictors.value = emptyList()
        val cachedDetail = prefs.getString("detail_${level.id}", null)
        if (cachedDetail != null) {
            try {
                val cached: LevelResponse = json.decodeFromString(LevelResponse.serializer(), cachedDetail)
                _selectedLevel.value = cached
                _currentLevelVictors.value = cached.records ?: emptyList()
            } catch (e: Exception) {}
        }
        victorsJob?.cancel()
        victorsJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val full: LevelResponse = client.get("https://api.aredl.net/api/aredl/levels/${level.id}/").body()
                val processedFull = full.copy(points = full.points / 10.0, global_name = extractCreator(full))
                val processedRecords = full.records?.map { it.copy(points = it.points?.let { p -> ensureDivided(p) } ?: (processedFull.points)) } ?: emptyList()
                val finalLevel = processedFull.copy(records = processedRecords)
                withContext(Dispatchers.Main) {
                    _selectedLevel.value = finalLevel
                    _currentLevelVictors.value = processedRecords
                }
                prefs.edit().putString("detail_${level.id}", json.encodeToString(LevelResponse.serializer(), finalLevel)).apply()
            } catch (e: Exception) {}
        }
    }

    fun selectPlayer(player: LeaderboardResponse, isAuthenticatedProfile: Boolean = false) {
        val playerWithRank = if (player.rank == null || player.rank == 0) player.copy(rank = playerRanks[player.user?.id] ?: 0) else player
        _isAuthenticatedProfileView.value = isAuthenticatedProfile
        _selectedPlayer.value = playerWithRank; _selectedPlayerProfile.value = null
        val username = player.user?.username ?: return
        val cachedProfile = prefs.getString("profile_$username", null)
        if (cachedProfile != null) {
            try {
                val cached: ProfileResponse = json.decodeFromString(ProfileResponse.serializer(), cachedProfile)
                _selectedPlayerProfile.value = cached
                profileCache[username] = cached
            } catch (e: Exception) {}
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = client.get("https://api.aredl.net/api/aredl/profile/$username").body<ProfileResponse>()
                val processed = profile.copy(records = profile.records.map { 
                    it.copy(
                        points = ensureDivided(it.points), 
                        list_points = ensureDivided(it.list_points),
                        level = it.level?.copy(points = ensureDivided(it.level.points) ?: 0.0)
                    ) 
                })
                withContext(Dispatchers.Main) {
                    _selectedPlayerProfile.value = processed
                    if (processed.user != null) _selectedPlayer.value = _selectedPlayer.value?.copy(user = processed.user)
                }
                profileCache[username] = processed; prefs.edit().putString("profile_$username", json.encodeToString(ProfileResponse.serializer(), processed)).apply()
            } catch (e: Exception) {}
        }
    }

    fun searchLeaderboard(query: String) { _searchQuery.value = query }
    fun nextPage() { if (_currentPage.value < _totalPages.value) _currentPage.value++ }
    fun previousPage() { if (_currentPage.value > 1) _currentPage.value-- }
    private fun pickNewRouletteLevel() { val available = _levels.value.filter { it.points > 0 }; if (available.isNotEmpty()) _currentRouletteId.value = available.random().id }
    fun advanceRoulette(percent: Int) { if (_roulettePercent.value >= 100) { _rouletteWon.value = true; return }; val current = currentRouletteLevel.value ?: return; val record = RecordInfo(level = current, achieved_percent = percent); _rouletteHistory.value += record; _roulettePercent.value++; prefs.edit().putInt("roulette_p", _roulettePercent.value).apply(); pickNewRouletteLevel() }
    fun resetRoulette() { _roulettePercent.value = 1; _rouletteHistory.value = emptyList(); _rouletteWon.value = false; prefs.edit().putInt("roulette_p", 1).apply(); pickNewRouletteLevel() }
    private fun pickNewAlphabetLevel() { val char = ('A' + _alphabetProgress.value % 26); val available = _levels.value.filter { it.name.startsWith(char, ignoreCase = true) }; if (available.isNotEmpty()) _currentAlphabetId.value = available.random().id }
    fun advanceAlphabet() { if (_alphabetProgress.value >= 25) { _alphabetWon.value = true; return }; val current = currentAlphabetLevel.value ?: return; _alphabetHistory.value += current.id; _alphabetProgress.value++; prefs.edit().putInt("alpha_p", _alphabetProgress.value).putStringSet("alphabet_h", _alphabetHistory.value.toSet()).apply(); pickNewAlphabetLevel() }
    fun resetAlphabet() { _alphabetProgress.value = 0; _alphabetHistory.value = emptyList(); _alphabetWon.value = false; prefs.edit().putInt("alpha_p", 0).putStringSet("alphabet_h", emptySet()).apply(); pickNewAlphabetLevel() }
    fun toggleFavorite(levelId: String) { val newSet = _favoriteLevels.value.toMutableSet().apply { if (contains(levelId)) remove(levelId) else add(levelId) }; _favoriteLevels.value = newSet; prefs.edit().putStringSet("favorites", newSet).apply() }
    fun toggleTodo(levelId: String) { val newSet = _todoLevels.value.toMutableSet().apply { if (contains(levelId)) remove(levelId) else add(levelId) }; _todoLevels.value = newSet; prefs.edit().putStringSet("todo", newSet).apply() }
    fun toggleCompleted(levelId: String) { val newSet = _completedLevels.value.toMutableSet().apply { if (contains(levelId)) remove(levelId) else add(levelId) }; _completedLevels.value = newSet; prefs.edit().putStringSet("completed", newSet).apply() }
}
