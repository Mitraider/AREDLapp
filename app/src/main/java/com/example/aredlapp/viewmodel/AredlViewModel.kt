package com.example.aredlapp.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

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
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    
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
            val pages = pJob.await()
            _isLoading.value = false
            if (pages > 1) loadRemainingLeaderboardPages(pages)
            startBackgroundLevelFetch()
        }
    }

    private fun loadLocalData() {
        _favoriteLevels.value = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        _todoLevels.value = prefs.getStringSet("todo", emptySet()) ?: emptySet()
        _completedLevels.value = prefs.getStringSet("completed", emptySet()) ?: emptySet()
        _roulettePercent.value = prefs.getInt("roulette_p", 1)
        _alphabetProgress.value = prefs.getInt("alpha_p", 0)
        _alphabetHistory.value = prefs.getStringSet("alphabet_h", emptySet())?.toList() ?: emptyList()
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
            try {
                prefs.edit().putString("cached_all_players", json.encodeToString(ListSerializer(LeaderboardResponse.serializer()), sortedList)).apply()
            } catch (e: Exception) {}
        }
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
            val response = client.get("https://api.aredl.net/api/roles")
            val root = Json.parseToJsonElement(response.bodyAsText())
            val rolesList = mutableListOf<RoleResponse>()

            if (root is JsonObject) {
                for ((roleName, element) in root) {
                    var roleColor: String? = null
                    var usersList = emptyList<JsonElement>()

                    if (element is JsonObject) {
                        roleColor = element["color"]?.jsonPrimitive?.contentOrNull
                        usersList = element["users"]?.jsonArray?.toList() ?: emptyList()
                    } else if (element is JsonArray) {
                        usersList = element.toList()
                    }

                    rolesList.add(RoleResponse(
                        name = roleName,
                        color = roleColor,
                        users = usersList
                    ))
                }
            }
            
            if (rolesList.isNotEmpty()) {
                _roles.value = rolesList
                prefs.edit().putString("cached_roles", json.encodeToString(ListSerializer(RoleResponse.serializer()), rolesList)).apply()
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

    private suspend fun fetchLeaderboardFirstPage(): Int {
        return try {
            val res: PaginatedLeaderboardResponse = client.get("https://api.aredl.net/api/aredl/leaderboard?page=1").body()
            _totalPages.value = res.pages
            updateLeaderboardData(res.data)
            res.pages
        } catch (e: Exception) { 1 }
    }

    private fun loadRemainingLeaderboardPages(total: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            (2..total.coerceAtMost(30)).forEach { p ->
                try {
                    val data = client.get("https://api.aredl.net/api/aredl/leaderboard?page=$p").body<PaginatedLeaderboardResponse>().data
                    updateLeaderboardData(data)
                } catch (e: Exception) {}
            }
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

    fun selectPlayer(player: LeaderboardResponse) {
        val playerWithRank = if (player.rank == null || player.rank == 0) player.copy(rank = playerRanks[player.user?.id] ?: 0) else player
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
