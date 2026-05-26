package com.example.aredlapp.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
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
import java.time.Instant
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

@RequiresApi(Build.VERSION_CODES.O)
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
    private val _completedLevelGdIds = MutableStateFlow<Set<Int>>(emptySet())
    val completedLevelGdIds: StateFlow<Set<Int>> = _completedLevelGdIds
    private val _submissionInfoByLevel = MutableStateFlow<Map<String, UserSubmissionInfo>>(emptyMap())
    val submissionInfoByLevel: StateFlow<Map<String, UserSubmissionInfo>> = _submissionInfoByLevel
    private val _mySubmissionLevels = MutableStateFlow<List<UserSubmissionLevelItem>>(emptyList())
    val mySubmissionLevels: StateFlow<List<UserSubmissionLevelItem>> = _mySubmissionLevels
    private val _selectedSubmissionDetail = MutableStateFlow<SubmissionDetailUiState?>(null)
    val selectedSubmissionDetail: StateFlow<SubmissionDetailUiState?> = _selectedSubmissionDetail
    private val _submissionsOpen = MutableStateFlow<Boolean?>(null)
    val submissionsOpen: StateFlow<Boolean?> = _submissionsOpen

    private val _availableTags = MutableStateFlow<List<String>>(emptyList())
    val availableTags: StateFlow<List<String>> = _availableTags
    private val _packTiers = MutableStateFlow<List<PackTierResolvedResponse>>(emptyList())
    val packTiers: StateFlow<List<PackTierResolvedResponse>> = _packTiers
    private val _currentLevelPacks = MutableStateFlow<List<LevelPackResponse>>(emptyList())
    val currentLevelPacks: StateFlow<List<LevelPackResponse>> = _currentLevelPacks
    private val _selectedPack = MutableStateFlow<PackResponse?>(null)
    val selectedPack: StateFlow<PackResponse?> = _selectedPack

    private val _roles = MutableStateFlow<List<RoleResponse>>(emptyList())
    val roles: StateFlow<List<RoleResponse>> = _roles

    private val _selectedLevel = MutableStateFlow<LevelResponse?>(null)
    val selectedLevel: StateFlow<LevelResponse?> = _selectedLevel
    private val _selectedPlayer = MutableStateFlow<LeaderboardResponse?>(null)
    val selectedPlayer: StateFlow<LeaderboardResponse?> = _selectedPlayer
    private val _selectedPlayerProfile = MutableStateFlow<ProfileResponse?>(null)
    val selectedPlayerProfile: StateFlow<ProfileResponse?> = _selectedPlayerProfile
    private val _aboutCreatorProfile = MutableStateFlow<ProfileResponse?>(null)
    val aboutCreatorProfile: StateFlow<ProfileResponse?> = _aboutCreatorProfile
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
    
    private val prefs = application.getSharedPreferences("AREDLapp", Context.MODE_PRIVATE)
    private val authPrefs: SharedPreferences = createAuthPrefs(application)
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
            fetchPackTiers()
            fetchAboutCreatorProfile()
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
                refreshAuthenticatedSubmissions()
                refreshAuthenticatedUser()
            }
        }
    }

    private fun loadLocalData() {
        _favoriteLevels.value = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        _todoLevels.value = prefs.getStringSet("todo", emptySet()) ?: emptySet()
        _completedLevels.value = prefs.getStringSet("completed", emptySet()) ?: emptySet()
        _completedLevelGdIds.value = prefs.getStringSet("completed_gd_ids", emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
        _roulettePercent.value = prefs.getInt("roulette_p", 1)
        _alphabetProgress.value = prefs.getInt("alpha_p", 0)
        _alphabetHistory.value = prefs.getStringSet("alphabet_h", emptySet())?.toList() ?: emptyList()
        loadAuthFromCache()
    }

    private fun loadAuthFromCache() {
        val accessToken = authPrefs.getString("auth_access_token", null)
        val refreshToken = authPrefs.getString("auth_refresh_token", null)
        if (accessToken.isNullOrBlank() && refreshToken.isNullOrBlank()) {
            _authState.value = AuthState()
            return
        }
        _authState.value = AuthState(
            isAuthenticated = true,
            userId = authPrefs.getString("auth_user_id", null),
            username = authPrefs.getString("auth_username", null),
            globalName = authPrefs.getString("auth_global_name", null),
            discordId = authPrefs.getString("auth_discord_id", null),
            discordAvatar = authPrefs.getString("auth_discord_avatar", null),
            accessToken = accessToken,
            accessExpires = authPrefs.getString("auth_access_expires", null),
            refreshToken = refreshToken,
            refreshExpires = authPrefs.getString("auth_refresh_expires", null)
        )
    }

    private fun persistAuthState(state: AuthState) {
        authPrefs.edit().apply {
            if (!state.isAuthenticated || (state.accessToken.isNullOrBlank() && state.refreshToken.isNullOrBlank())) {
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

    private fun createAuthPrefs(context: Context): SharedPreferences {
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
            context.getSharedPreferences("aredl_auth_fallback", Context.MODE_PRIVATE)
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
                updateLeaderboardData(cached, saveToCache = false, alreadyNormalized = true)
            }
            prefs.getString("cached_roles", null)?.let { cachedJson ->
                val cached: List<RoleResponse> = json.decodeFromString(ListSerializer(RoleResponse.serializer()), cachedJson)
                _roles.value = cached
            }
            prefs.getString("cached_pack_tiers", null)?.let { cachedJson ->
                val cached: List<PackTierResolvedResponse> = json.decodeFromString(ListSerializer(PackTierResolvedResponse.serializer()), cachedJson)
                _packTiers.value = cached.sortedBy { it.placement }
            }
        } catch (e: Exception) {}
    }

    private fun updateLeaderboardData(
        data: List<LeaderboardResponse>,
        saveToCache: Boolean = true,
        alreadyNormalized: Boolean = false
    ) {
        data.forEach { player ->
            player.user?.id?.let { id ->
                val processed = if (alreadyNormalized) {
                    player
                } else {
                    player.copy(total_points = ensureDivided(player.total_points))
                }
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

    private fun ensureProfileRecordDivided(points: Double?): Double? {
        if (points == null) return null
        return points / 10.0
    }

    private fun normalizeProfile(profile: ProfileResponse): ProfileResponse {
        return profile.copy(
            records = profile.records.map {
                it.copy(
                    points = ensureProfileRecordDivided(it.points),
                    list_points = ensureProfileRecordDivided(it.list_points),
                    level = it.level?.copy(points = ensureProfileRecordDivided(it.level.points) ?: 0.0)
                )
            }
        )
    }

    private fun extractCreator(level: LevelResponse): String? {
        return level.global_name?.takeIf { it.isNotBlank() && it != "AREDL" }
            ?: level.creator?.global_name ?: level.creator?.username ?: "AREDL"
    }

    private suspend fun fetchLevels() {
        try {
            val response: HttpResponse = client.get("https://api.aredl.net/v2/api/aredl/levels")
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
            val response = client.get("https://api.aredl.net/v2/api/users/names")
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

    private suspend fun fetchPackTiers() {
        try {
            val token = ensureValidAccessToken()
            var response = client.get("https://api.aredl.net/v2/api/aredl/pack-tiers") {
                if (!token.isNullOrBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }
            if (!response.status.isSuccess()) {
                response = client.get("https://api.aredl.net/v2/api/aredl/pack-tiers")
            }
            if (!response.status.isSuccess()) return
            val body = response.bodyAsText()
            val root = Json.parseToJsonElement(body)
            val tiers = when (root) {
                is JsonArray -> parsePackTiers(root)
                is JsonObject -> parsePackTiers(root["data"]?.jsonArray ?: JsonArray(emptyList()))
                else -> emptyList()
            }.sortedBy { it.placement }

            _packTiers.value = tiers
            prefs.edit().putString(
                "cached_pack_tiers",
                json.encodeToString(ListSerializer(PackTierResolvedResponse.serializer()), tiers)
            ).apply()
        } catch (_: Exception) {}
    }

    fun refreshPackTiers() {
        viewModelScope.launch(Dispatchers.IO) {
            fetchPackTiers()
        }
    }

    private fun parsePackTiers(array: JsonArray): List<PackTierResolvedResponse> {
        return array.mapNotNull { tierEl ->
            val tierObj = tierEl as? JsonObject ?: return@mapNotNull null
            val packs = tierObj["packs"]?.jsonArray?.mapNotNull { packEl ->
                val packObj = packEl as? JsonObject ?: return@mapNotNull null
                val levels = packObj["levels"]?.jsonArray?.mapNotNull { levelEl ->
                    val levelObj = levelEl as? JsonObject ?: return@mapNotNull null
                    val id = levelObj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    PackLevelResponse(
                        id = id,
                        name = levelObj["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown",
                        level_id = levelObj["level_id"]?.jsonPrimitive?.intOrNull,
                        position = levelObj["position"]?.jsonPrimitive?.intOrNull ?: 0,
                        points = ensureDivided(levelObj["points"]?.jsonPrimitive?.doubleOrNull) ?: 0.0,
                        legacy = levelObj["legacy"]?.jsonPrimitive?.booleanOrNull,
                        completed_by_user = levelObj["completed_by_user"]?.jsonPrimitive?.booleanOrNull
                    )
                } ?: emptyList()

                val packId = packObj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                PackResponse(
                    id = packId,
                    name = packObj["name"]?.jsonPrimitive?.contentOrNull ?: "Pack",
                    points = packObj["points"]?.jsonPrimitive?.intOrNull ?: 0,
                    levels = levels
                )
            } ?: emptyList()

            val tierId = tierObj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            PackTierResolvedResponse(
                id = tierId,
                name = tierObj["name"]?.jsonPrimitive?.contentOrNull ?: "Tier",
                color = tierObj["color"]?.jsonPrimitive?.contentOrNull ?: "#888888",
                placement = tierObj["placement"]?.jsonPrimitive?.intOrNull ?: 0,
                packs = packs
            )
        }
    }

    private fun parseLevelPacks(array: JsonArray): List<LevelPackResponse> {
        return array.mapNotNull { packEl ->
            val packObj = packEl as? JsonObject ?: return@mapNotNull null
            val tierObj = packObj["tier"]?.jsonObject ?: return@mapNotNull null
            val packId = packObj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val tierId = tierObj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null

            LevelPackResponse(
                id = packId,
                name = packObj["name"]?.jsonPrimitive?.contentOrNull ?: "Pack",
                tier = BasePackTierResponse(
                    id = tierId,
                    name = tierObj["name"]?.jsonPrimitive?.contentOrNull ?: "Tier",
                    color = tierObj["color"]?.jsonPrimitive?.contentOrNull ?: "#888888"
                )
            )
        }
    }

    private fun parseLevelVictors(array: JsonArray): List<LevelRecord> {
        return array.mapNotNull { recordEl ->
            val recordObj = recordEl as? JsonObject ?: return@mapNotNull null
            val userObj = recordObj["submitted_by"]?.jsonObject
                ?: recordObj["user"]?.jsonObject
                ?: recordObj["player"]?.jsonObject

            val user = userObj?.let {
                UserInfo(
                    id = it["id"]?.jsonPrimitive?.contentOrNull,
                    username = it["username"]?.jsonPrimitive?.contentOrNull,
                    discord_id = it["discord_id"]?.jsonPrimitive?.contentOrNull,
                    global_name = it["global_name"]?.jsonPrimitive?.contentOrNull,
                    discord_avatar = it["discord_avatar"]?.jsonPrimitive?.contentOrNull,
                    avatar = it["avatar"]?.jsonPrimitive?.contentOrNull
                )
            }

            LevelRecord(
                user = user,
                player = user,
                video = recordObj["video"]?.jsonPrimitive?.contentOrNull,
                video_url = recordObj["video_url"]?.jsonPrimitive?.contentOrNull,
                id = recordObj["id"]?.jsonPrimitive?.contentOrNull,
                level_id = recordObj["level_id"]?.jsonPrimitive?.intOrNull
            )
        }
    }

    private fun parseSubmissionDetail(root: JsonObject): SubmissionDetailResponse? {
        val resolved = root["data"]?.jsonObject ?: root
        val levelObj = resolved["level"]?.jsonObject ?: return null
        val level = LevelResponse(
            id = levelObj["id"]?.jsonPrimitive?.contentOrNull ?: return null,
            level_id = levelObj["level_id"]?.jsonPrimitive?.intOrNull,
            name = levelObj["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown",
            position = levelObj["position"]?.jsonPrimitive?.intOrNull ?: 0,
            points = (levelObj["points"]?.jsonPrimitive?.doubleOrNull ?: 0.0) / 10.0,
            description = levelObj["description"]?.jsonPrimitive?.contentOrNull
        )
        return SubmissionDetailResponse(
            id = resolved["id"]?.jsonPrimitive?.contentOrNull ?: return null,
            level = level,
            rawStatus = resolved["status"]?.jsonPrimitive?.contentOrNull ?: "Pending",
            mobile = resolved["mobile"]?.jsonPrimitive?.booleanOrNull ?: false,
            ldmId = resolved["ldm_id"]?.jsonPrimitive?.intOrNull,
            videoUrl = resolved["video_url"]?.jsonPrimitive?.contentOrNull ?: "",
            rawUrl = resolved["raw_url"]?.jsonPrimitive?.contentOrNull,
            modMenu = resolved["mod_menu"]?.jsonPrimitive?.contentOrNull,
            userNotes = resolved["user_notes"]?.jsonPrimitive?.contentOrNull,
            priority = resolved["priority"]?.jsonPrimitive?.booleanOrNull ?: false,
            locked = resolved["locked"]?.jsonPrimitive?.booleanOrNull ?: false,
            reviewerNotes = resolved["reviewer_notes"]?.jsonPrimitive?.contentOrNull,
            createdAt = resolved["created_at"]?.jsonPrimitive?.contentOrNull,
            updatedAt = resolved["updated_at"]?.jsonPrimitive?.contentOrNull
        )
    }

    private fun parseQueuePosition(root: JsonObject): SubmissionQueuePosition? {
        val resolved = root["data"]?.jsonObject ?: root
        val position = resolved["position"]?.jsonPrimitive?.intOrNull ?: return null
        val priority = resolved["priority"]?.jsonPrimitive?.booleanOrNull ?: false
        return SubmissionQueuePosition(position, priority)
    }

    private fun parseQueueSummary(root: JsonObject): SubmissionQueueSummary? {
        val resolved = root["data"]?.jsonObject ?: root
        return SubmissionQueueSummary(
            regularSubmissionsInQueue = resolved["regular_submissions_in_queue"]?.jsonPrimitive?.intOrNull ?: 0,
            prioritySubmissionsInQueue = resolved["priority_submissions_in_queue"]?.jsonPrimitive?.intOrNull ?: 0,
            underConsiderationSubmissions = resolved["uc_submissions"]?.jsonPrimitive?.intOrNull ?: 0
        )
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
                            val full: LevelResponse = client.get("https://api.aredl.net/v2/api/aredl/levels/${level.id}").body()
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
            val res: PaginatedLeaderboardResponse = client.get("https://api.aredl.net/v2/api/aredl/leaderboard?page=1").body()
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
                    val data = client.get("https://api.aredl.net/v2/api/aredl/leaderboard?page=$p").body<PaginatedLeaderboardResponse>().data
                    updateLeaderboardData(data, saveToCache = false)
                } catch (e: Exception) {}
            }
            persistLeaderboardCache(_allPlayers.value, lastRefreshed)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
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
                refreshAuthenticatedSubmissions()
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

    @RequiresApi(Build.VERSION_CODES.O)
    fun refreshAuthenticatedUser() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = ensureValidAccessToken() ?: return@launch
                val response = client.get("https://api.aredl.net/v2/api/users/@me") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                    val refreshedToken = refreshAccessToken() ?: throw IllegalStateException("Session expired")
                    val retry = client.get("https://api.aredl.net/v2/api/users/@me") {
                        header(HttpHeaders.Authorization, "Bearer $refreshedToken")
                    }
                    applyAuthenticatedUserResponse(retry.bodyAsText())
                } else {
                    applyAuthenticatedUserResponse(response.bodyAsText())
                }
                syncCompletedFromAuthenticatedRecords()
                loadAuthenticatedSubmissions()
                fetchPackTiers()
                selectAuthenticatedPlayer()
            } catch (e: Exception) {
                loadAuthenticatedSubmissions()
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

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun ensureValidAccessToken(): String? {
        val access = _authState.value.accessToken
        val expiresAt = _authState.value.accessExpires
        return if (!access.isNullOrBlank() && !isExpiredOrNearExpiry(expiresAt)) {
            access
        } else {
            refreshAccessToken()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun isExpiredOrNearExpiry(expiresAt: String?): Boolean {
        if (expiresAt.isNullOrBlank()) return true
        return try {
            val expiry = Instant.parse(expiresAt)
            expiry.minusSeconds(60).isBefore(Instant.now())
        } catch (_: Exception) {
            true
        }
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

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun syncCompletedFromAuthenticatedRecords() {
        val token = ensureValidAccessToken() ?: return
        try {
            var response = client.get("https://api.aredl.net/v2/api/aredl/records/@me") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                val refreshedToken = refreshAccessToken() ?: return
                response = client.get("https://api.aredl.net/v2/api/aredl/records/@me") {
                    header(HttpHeaders.Authorization, "Bearer $refreshedToken")
                }
            }
            if (!response.status.isSuccess()) {
                syncCompletedFromAuthenticatedProfile()
                return
            }

            val root = Json.parseToJsonElement(response.bodyAsText())
            val records = when (root) {
                is JsonArray -> root
                is JsonObject -> root["data"]?.jsonArray ?: JsonArray(emptyList())
                else -> JsonArray(emptyList())
            }

            val knownLevels = _levels.value

            val completedIds = mutableSetOf<String>()
            val completedGdIds = mutableSetOf<Int>()
            records.forEach { rec ->
                val obj = rec as? JsonObject ?: return@forEach
                val levelObj = obj["level"]?.jsonObject

                val uuid = levelObj?.get("id")?.jsonPrimitive?.contentOrNull
                    ?: obj["level_id"]?.jsonPrimitive?.contentOrNull
                    ?: obj["aredl_level_id"]?.jsonPrimitive?.contentOrNull
                if (!uuid.isNullOrBlank()) {
                    knownLevels.find { it.id == uuid }?.let { completedIds.add(it.id) }
                    completedIds.add(uuid)
                }

                val gdId = levelObj?.get("level_id")?.jsonPrimitive?.intOrNull
                    ?: obj["level_id"]?.jsonPrimitive?.intOrNull
                    ?: obj["gd_level_id"]?.jsonPrimitive?.intOrNull
                    ?: obj["level_gd_id"]?.jsonPrimitive?.intOrNull
                if (gdId != null) {
                    completedGdIds.add(gdId)
                    knownLevels.filter { it.level_id == gdId }.forEach { completedIds.add(it.id) }
                }
            }

            if (completedIds.isEmpty() && completedGdIds.isEmpty()) {
                syncCompletedFromAuthenticatedProfile()
            } else {
                applyCompletedState(completedIds, completedGdIds)
            }
        } catch (_: Exception) {
            syncCompletedFromAuthenticatedProfile()
        }
    }

    private suspend fun syncCompletedFromAuthenticatedProfile() {
        val username = _authState.value.username?.takeIf { it.isNotBlank() } ?: return
        try {
            val profile = client.get("https://api.aredl.net/v2/api/aredl/profile/$username").body<ProfileResponse>()
            val knownLevels = _levels.value
            val completedIds = mutableSetOf<String>()
            val completedGdIds = mutableSetOf<Int>()

            profile.records.forEach { record ->
                val level = record.level
                val uuid = level?.id
                if (!uuid.isNullOrBlank()) {
                    completedIds.add(uuid)
                    knownLevels.find { it.id == uuid }?.let { completedIds.add(it.id) }
                }

                val gdId = level?.level_id ?: record.level_id
                if (gdId != null) {
                    completedGdIds.add(gdId)
                    knownLevels.filter { it.level_id == gdId }.forEach { completedIds.add(it.id) }
                }
            }

            applyCompletedState(completedIds, completedGdIds)
        } catch (_: Exception) {}
    }

    private fun applyCompletedState(completedIds: Set<String>, completedGdIds: Set<Int>) {
        _completedLevels.value = completedIds
        _completedLevelGdIds.value = completedGdIds
        prefs.edit()
            .putStringSet("completed", completedIds)
            .putStringSet("completed_gd_ids", completedGdIds.map { it.toString() }.toSet())
            .apply()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun loadAuthenticatedSubmissions() {
        val token = ensureValidAccessToken() ?: return
        try {
            var response = client.get("https://api.aredl.net/v2/api/aredl/submissions/@me?per_page=1000") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                val refreshedToken = refreshAccessToken() ?: return
                response = client.get("https://api.aredl.net/v2/api/aredl/submissions/@me?per_page=1000") {
                    header(HttpHeaders.Authorization, "Bearer $refreshedToken")
                }
            }
            if (!response.status.isSuccess()) return

            val body = response.bodyAsText()
            val root = Json.parseToJsonElement(body)
            val submissions = when (root) {
                is JsonArray -> root
                is JsonObject -> root["data"]?.jsonArray ?: JsonArray(emptyList())
                else -> JsonArray(emptyList())
            }

            val latestByLevel = mutableMapOf<String, UserSubmissionInfo>()
            val knownLevels = _levels.value.associateBy { it.id }
            val resolvedItems = mutableListOf<UserSubmissionLevelItem>()
            submissions.forEach { entry ->
                val obj = entry as? JsonObject ?: return@forEach
                val submissionId = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val levelId = obj["level_id"]?.jsonPrimitive?.contentOrNull
                    ?: obj["aredl_level_id"]?.jsonPrimitive?.contentOrNull
                    ?: obj["level"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
                val submission = UserSubmissionInfo(
                    submissionId = submissionId,
                    levelId = levelId ?: "",
                    rawStatus = obj["status"]?.jsonPrimitive?.contentOrNull ?: "Pending",
                    updatedAt = obj["updated_at"]?.jsonPrimitive?.contentOrNull
                        ?: obj["created_at"]?.jsonPrimitive?.contentOrNull
                )

                val knownLevel = levelId?.let { knownLevels[it] }
                val resolvedLevel = knownLevel ?: fetchSubmissionLevel(submissionId, token)

                if (resolvedLevel != null) {
                    val current = latestByLevel[resolvedLevel.id]
                    if (current == null || (submission.updatedAt ?: "") > (current.updatedAt ?: "")) {
                        latestByLevel[resolvedLevel.id] = submission.copy(levelId = resolvedLevel.id)
                    }

                    resolvedLevel.level_id?.toString()?.let { gdLevelId ->
                        val currentByGd = latestByLevel[gdLevelId]
                        if (currentByGd == null || (submission.updatedAt ?: "") > (currentByGd.updatedAt ?: "")) {
                            latestByLevel[gdLevelId] = submission.copy(levelId = resolvedLevel.id)
                        }
                    }

                    resolvedItems += UserSubmissionLevelItem(resolvedLevel, submission.copy(levelId = resolvedLevel.id))
                } else if (!levelId.isNullOrBlank()) {
                    val current = latestByLevel[levelId]
                    if (current == null || (submission.updatedAt ?: "") > (current.updatedAt ?: "")) {
                        latestByLevel[levelId] = submission
                    }
                }
            }

            _submissionInfoByLevel.value = latestByLevel
            _mySubmissionLevels.value = resolvedItems
                .distinctBy { it.submission.submissionId }
                .sortedByDescending { it.submission.updatedAt ?: "" }
        } catch (e: Exception) {
        }
    }

    private suspend fun fetchSubmissionLevel(submissionId: String, token: String): LevelResponse? {
        return try {
            var response = client.get("https://api.aredl.net/v2/api/aredl/submissions/$submissionId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                val refreshedToken = refreshAccessToken() ?: return null
                response = client.get("https://api.aredl.net/v2/api/aredl/submissions/$submissionId") {
                    header(HttpHeaders.Authorization, "Bearer $refreshedToken")
                }
            }
            if (!response.status.isSuccess()) return null

            val parsed = Json.parseToJsonElement(response.bodyAsText())
            val root = when (parsed) {
                is JsonObject -> parsed["data"]?.jsonObject ?: parsed
                else -> return null
            }
            val levelObj = root["level"]?.jsonObject
                ?: root["submission"]?.jsonObject?.get("level")?.jsonObject
                ?: return null
            LevelResponse(
                id = levelObj["id"]?.jsonPrimitive?.contentOrNull ?: return null,
                level_id = levelObj["level_id"]?.jsonPrimitive?.intOrNull,
                name = levelObj["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown",
                position = levelObj["position"]?.jsonPrimitive?.intOrNull ?: 0,
                points = ensureDivided(levelObj["points"]?.jsonPrimitive?.doubleOrNull) ?: 0.0,
                description = levelObj["description"]?.jsonPrimitive?.contentOrNull,
                video = levelObj["video"]?.jsonPrimitive?.contentOrNull,
                thumbnail = levelObj["thumbnail"]?.jsonPrimitive?.contentOrNull
            )
        } catch (_: Exception) {
            null
        }
    }

    fun selectSubmission(submissionId: String) {
        _selectedSubmissionDetail.value = SubmissionDetailUiState(
            mode = SubmissionScreenMode.VIEW,
            detail = null,
            queuePosition = null,
            queueSummary = null,
            submissionsOpen = _submissionsOpen.value,
            isLoading = true
        )
        viewModelScope.launch(Dispatchers.IO) {
            val detail = fetchSubmissionDetail(submissionId)
            val queuePosition = if (detail?.rawStatus == "Pending") fetchSubmissionQueuePosition(submissionId) else null
            val queueSummary = if (detail?.rawStatus == "Pending") fetchSubmissionQueueSummary() else null
            _selectedSubmissionDetail.value = SubmissionDetailUiState(
                mode = SubmissionScreenMode.VIEW,
                detail = detail,
                queuePosition = queuePosition,
                queueSummary = queueSummary,
                submissionsOpen = _submissionsOpen.value,
                isLoading = false
            )
        }
    }

    fun prepareNewSubmission() {
        _selectedSubmissionDetail.value = SubmissionDetailUiState(
            mode = SubmissionScreenMode.CREATE,
            detail = null,
            queuePosition = null,
            queueSummary = null,
            submissionsOpen = _submissionsOpen.value,
            isLoading = true
        )
        viewModelScope.launch(Dispatchers.IO) {
            val open = fetchSubmissionsStatus()
            _submissionsOpen.value = open
            val queueSummary = if (open == true) fetchSubmissionQueueSummary() else null
            _selectedSubmissionDetail.value = SubmissionDetailUiState(
                mode = SubmissionScreenMode.CREATE,
                detail = null,
                queuePosition = null,
                queueSummary = queueSummary,
                submissionsOpen = open,
                isLoading = false
            )
        }
    }

    private suspend fun fetchSubmissionDetail(submissionId: String): SubmissionDetailResponse? {
        val token = ensureValidAccessToken() ?: return null
        return try {
            var response = client.get("https://api.aredl.net/v2/api/aredl/submissions/$submissionId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                val refreshedToken = refreshAccessToken() ?: return null
                response = client.get("https://api.aredl.net/v2/api/aredl/submissions/$submissionId") {
                    header(HttpHeaders.Authorization, "Bearer $refreshedToken")
                }
            }
            if (!response.status.isSuccess()) return null
            val parsed = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            parseSubmissionDetail(parsed)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchSubmissionQueuePosition(submissionId: String): SubmissionQueuePosition? {
        val token = ensureValidAccessToken() ?: return null
        return try {
            var response = client.get("https://api.aredl.net/v2/api/aredl/submissions/$submissionId/queue") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                val refreshedToken = refreshAccessToken() ?: return null
                response = client.get("https://api.aredl.net/v2/api/aredl/submissions/$submissionId/queue") {
                    header(HttpHeaders.Authorization, "Bearer $refreshedToken")
                }
            }
            if (!response.status.isSuccess()) return null
            parseQueuePosition(Json.parseToJsonElement(response.bodyAsText()).jsonObject)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchSubmissionQueueSummary(): SubmissionQueueSummary? {
        return try {
            val response = client.get("https://api.aredl.net/v2/api/aredl/submissions/queue")
            if (!response.status.isSuccess()) return null
            parseQueueSummary(Json.parseToJsonElement(response.bodyAsText()).jsonObject)
        } catch (_: Exception) {
            null
        }
    }

    fun refreshSubmissionsStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            _submissionsOpen.value = fetchSubmissionsStatus()
        }
    }

    private suspend fun fetchSubmissionsStatus(): Boolean? {
        return try {
            val response = client.get("https://api.aredl.net/v2/api/aredl/submissions/status")
            if (!response.status.isSuccess()) return null
            when (val parsed = Json.parseToJsonElement(response.bodyAsText())) {
                is JsonPrimitive -> parsed.booleanOrNull
                is JsonObject -> parsed["enabled"]?.jsonPrimitive?.booleanOrNull
                    ?: parsed["data"]?.jsonPrimitive?.booleanOrNull
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun updateSelectedSubmission(form: SubmissionEditForm): Result<Unit> {
        val current = _selectedSubmissionDetail.value?.detail ?: return Result.failure(IllegalStateException("No submission selected"))
        val token = ensureValidAccessToken() ?: return Result.failure(IllegalStateException("Session expired"))

        val trimmedVideo = form.videoUrl.trim()
        val trimmedModMenu = form.modMenu.trim()
        if (trimmedVideo.isBlank()) return Result.failure(IllegalArgumentException("Completion video is required"))
        if (trimmedModMenu.isBlank()) return Result.failure(IllegalArgumentException("Mod menu is required"))

        val original = current
        val ldmIdValue = form.ldmId.trim().takeIf { it.isNotBlank() }?.toIntOrNull()
            ?: if (form.ldmId.trim().isBlank()) null else return Result.failure(IllegalArgumentException("LDM ID must be a number"))
        val rawUrlValue = form.rawUrl.trim().takeIf { it.isNotBlank() }
        val notesValue = form.userNotes.trim().takeIf { it.isNotBlank() }

        val patchBody = buildJsonObject {
            if (form.mobile != original.mobile) put("mobile", JsonPrimitive(form.mobile))
            if (ldmIdValue != original.ldmId) {
                put("ldm_id", ldmIdValue?.let { JsonPrimitive(it) } ?: JsonNull)
            }
            if (trimmedVideo != original.videoUrl) put("video_url", JsonPrimitive(trimmedVideo))
            if (rawUrlValue != original.rawUrl) {
                put("raw_url", rawUrlValue?.let { JsonPrimitive(it) } ?: JsonNull)
            }
            if (trimmedModMenu != (original.modMenu ?: "")) put("mod_menu", JsonPrimitive(trimmedModMenu))
            if (notesValue != original.userNotes) {
                put("user_notes", notesValue?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        }

        if (patchBody.isEmpty()) return Result.failure(IllegalArgumentException("No changes to save"))

        return try {
            var response = client.patch("https://api.aredl.net/v2/api/aredl/submissions/${current.id}") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(patchBody)
            }
            if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                val refreshedToken = refreshAccessToken() ?: return Result.failure(IllegalStateException("Session expired"))
                response = client.patch("https://api.aredl.net/v2/api/aredl/submissions/${current.id}") {
                    header(HttpHeaders.Authorization, "Bearer $refreshedToken")
                    contentType(ContentType.Application.Json)
                    setBody(patchBody)
                }
            }
            if (!response.status.isSuccess()) {
                return Result.failure(IllegalStateException(response.bodyAsText().ifBlank { "Failed to update submission" }))
            }
            refreshAuthenticatedSubmissions()
            selectSubmission(current.id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun createSubmission(form: SubmissionEditForm): Result<Unit> {
        val token = ensureValidAccessToken() ?: return Result.failure(IllegalStateException("Session expired"))
        val open = fetchSubmissionsStatus()
        _submissionsOpen.value = open
        if (open != true) {
            return Result.failure(IllegalStateException("Submission queue is closed"))
        }

        val trimmedLevelId = form.levelId.trim()
        val trimmedVideo = form.videoUrl.trim()
        val trimmedModMenu = form.modMenu.trim()
        if (trimmedLevelId.isBlank()) return Result.failure(IllegalArgumentException("Level is required"))
        if (trimmedVideo.isBlank()) return Result.failure(IllegalArgumentException("Completion video is required"))
        if (trimmedModMenu.isBlank()) return Result.failure(IllegalArgumentException("Mod menu is required"))
        if (_submissionInfoByLevel.value.containsKey(trimmedLevelId)) {
            return Result.failure(IllegalStateException("You already have a submission for this level"))
        }

        val ldmIdValue = form.ldmId.trim().takeIf { it.isNotBlank() }?.toIntOrNull()
            ?: if (form.ldmId.trim().isBlank()) null else return Result.failure(IllegalArgumentException("LDM ID must be a number"))
        val rawUrlValue = form.rawUrl.trim().takeIf { it.isNotBlank() }
        val notesValue = form.userNotes.trim().takeIf { it.isNotBlank() }

        val postBody = buildJsonObject {
            put("level_id", JsonPrimitive(trimmedLevelId))
            put("mobile", JsonPrimitive(form.mobile))
            put("video_url", JsonPrimitive(trimmedVideo))
            put("mod_menu", JsonPrimitive(trimmedModMenu))
            put("ldm_id", ldmIdValue?.let { JsonPrimitive(it) } ?: JsonNull)
            put("raw_url", rawUrlValue?.let { JsonPrimitive(it) } ?: JsonNull)
            put("user_notes", notesValue?.let { JsonPrimitive(it) } ?: JsonNull)
        }

        return try {
            var response = client.post("https://api.aredl.net/v2/api/aredl/submissions") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(postBody)
            }
            if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                val refreshedToken = refreshAccessToken() ?: return Result.failure(IllegalStateException("Session expired"))
                response = client.post("https://api.aredl.net/v2/api/aredl/submissions") {
                    header(HttpHeaders.Authorization, "Bearer $refreshedToken")
                    contentType(ContentType.Application.Json)
                    setBody(postBody)
                }
            }
            if (!response.status.isSuccess()) {
                return Result.failure(IllegalStateException(response.bodyAsText().ifBlank { "Failed to create submission" }))
            }
            val createdId = extractSubmissionId(response.bodyAsText())
            refreshAuthenticatedSubmissions()
            if (createdId != null) {
                selectSubmission(createdId)
            } else {
                prepareNewSubmission()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractSubmissionId(body: String): String? {
        return try {
            when (val root = Json.parseToJsonElement(body)) {
                is JsonObject -> {
                    val resolved = root["data"]?.jsonObject ?: root
                    resolved["id"]?.jsonPrimitive?.contentOrNull
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun refreshAuthenticatedSubmissions() {
        if (!_authState.value.isAuthenticated) return
        viewModelScope.launch(Dispatchers.IO) {
            loadAuthenticatedSubmissions()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun refreshAuthenticatedCompletions() {
        if (!_authState.value.isAuthenticated) return
        viewModelScope.launch(Dispatchers.IO) {
            syncCompletedFromAuthenticatedRecords()
        }
    }

    fun selectAuthenticatedPlayer(): Boolean {
        val auth = _authState.value
        val player = _allPlayers.value.firstOrNull { p ->
            val u = p.user
            val byId = !auth.userId.isNullOrBlank() && u?.id == auth.userId
            val byName = !auth.username.isNullOrBlank() && u?.username.equals(auth.username, ignoreCase = true)
            byId || byName
        } ?: run {
            val username = auth.username ?: return false
            LeaderboardResponse(
                user = UserInfo(
                    id = auth.userId,
                    username = username,
                    global_name = auth.globalName,
                    discord_id = auth.discordId,
                    discord_avatar = auth.discordAvatar
                ),
                total_points = 0.0,
                rank = 0,
                extremes = 0
            )
        }
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
            _completedLevelGdIds.value = emptySet()
            _submissionInfoByLevel.value = emptyMap()
            _mySubmissionLevels.value = emptyList()
            prefs.edit().putStringSet("completed", emptySet()).putStringSet("completed_gd_ids", emptySet()).apply()
            persistAuthState(AuthState())
        }
    }

    fun selectLevel(level: LevelResponse) {
        _selectedLevel.value = level
        _currentLevelVictors.value = emptyList()
        _currentLevelPacks.value = emptyList()
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
                val full: LevelResponse = client.get("https://api.aredl.net/v2/api/aredl/levels/${level.id}").body()
                val processedFull = full.copy(points = full.points / 10.0, global_name = extractCreator(full))
                val recordsBody = try {
                    client.get("https://api.aredl.net/v2/api/aredl/levels/${level.id}/records").bodyAsText()
                } catch (_: Exception) {
                    null
                }
                val processedRecords = if (!recordsBody.isNullOrBlank()) {
                    val recordsRoot = Json.parseToJsonElement(recordsBody)
                    when (recordsRoot) {
                        is JsonArray -> parseLevelVictors(recordsRoot)
                        is JsonObject -> parseLevelVictors(recordsRoot["data"]?.jsonArray ?: JsonArray(emptyList()))
                        else -> emptyList()
                    }
                } else {
                    full.records?.map { it.copy(points = it.points?.let { p -> ensureDivided(p) } ?: (processedFull.points)) } ?: emptyList()
                }
                val finalLevel = processedFull.copy(records = processedRecords)
                val levelPacks: List<LevelPackResponse> = try {
                    var packsResponse = client.get("https://api.aredl.net/v2/api/aredl/levels/${level.id}/packs")
                    if (!packsResponse.status.isSuccess()) {
                        packsResponse = client.get("https://api.aredl.net/v2/api/aredl/levels/${level.id}/packs")
                    }
                    val packsBody = packsResponse.bodyAsText()
                    val packsRoot = Json.parseToJsonElement(packsBody)
                    when (packsRoot) {
                        is JsonArray -> parseLevelPacks(packsRoot)
                        is JsonObject -> parseLevelPacks(packsRoot["data"]?.jsonArray ?: JsonArray(emptyList()))
                        else -> emptyList()
                    }
                } catch (_: Exception) {
                    emptyList()
                }
                withContext(Dispatchers.Main) {
                    _selectedLevel.value = finalLevel
                    _currentLevelVictors.value = processedRecords
                    _currentLevelPacks.value = levelPacks
                }
                prefs.edit().putString("detail_${level.id}", json.encodeToString(LevelResponse.serializer(), finalLevel)).apply()
            } catch (e: Exception) {}
        }
    }

    fun selectPack(pack: PackResponse) {
        _selectedPack.value = pack
    }

    fun selectPackFromLevelPack(levelPack: LevelPackResponse) {
        _selectedPack.value = _packTiers.value
            .asSequence()
            .flatMap { it.packs.asSequence() }
            .firstOrNull { it.id == levelPack.id || it.name.equals(levelPack.name, ignoreCase = true) }
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
                val profile = client.get("https://api.aredl.net/v2/api/aredl/profile/$username").body<ProfileResponse>()
                val processed = normalizeProfile(profile)
                withContext(Dispatchers.Main) {
                    _selectedPlayerProfile.value = processed
                    if (processed.user != null) _selectedPlayer.value = _selectedPlayer.value?.copy(user = processed.user)
                }
                profileCache[username] = processed; prefs.edit().putString("profile_$username", json.encodeToString(ProfileResponse.serializer(), processed)).apply()
            } catch (e: Exception) {}
        }
    }

    fun fetchAboutCreatorProfile(username: String = "Mitraider08") {
        val cachedProfile = prefs.getString("about_profile_$username", null)
        if (cachedProfile != null) {
            try {
                val cached: ProfileResponse = json.decodeFromString(ProfileResponse.serializer(), cachedProfile)
                _aboutCreatorProfile.value = cached
            } catch (_: Exception) {}
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = client.get("https://api.aredl.net/v2/api/aredl/profile/$username").body<ProfileResponse>()
                val processed = normalizeProfile(profile)
                withContext(Dispatchers.Main) {
                    _aboutCreatorProfile.value = processed
                }
                prefs.edit().putString(
                    "about_profile_$username",
                    json.encodeToString(ProfileResponse.serializer(), processed)
                ).apply()
            } catch (_: Exception) {
                if (_aboutCreatorProfile.value == null) {
                    val fallback = _allPlayers.value.firstOrNull {
                        it.user?.username.equals(username, ignoreCase = true) ||
                            it.user?.global_name.equals(username, ignoreCase = true)
                    }?.user
                    if (fallback != null) {
                        _aboutCreatorProfile.value = ProfileResponse(embeddedUser = fallback)
                    }
                }
            }
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
