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
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class AredlViewModel(application: Application) : AndroidViewModel(application) {

    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 20000; connectTimeoutMillis = 15000 }
    }

    private val _levels = MutableStateFlow<List<LevelResponse>>(emptyList())
    val levels: StateFlow<List<LevelResponse>> = _levels

    private val _allPlayers = MutableStateFlow<List<LeaderboardResponse>>(emptyList())
    val allPlayers: StateFlow<List<LeaderboardResponse>> = _allPlayers
    
    private val _leaderboard = MutableStateFlow<List<LeaderboardResponse>>(emptyList())
    val leaderboard: StateFlow<List<LeaderboardResponse>> = _leaderboard

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage

    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages

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

    private val _selectedLevel = MutableStateFlow<LevelResponse?>(null)
    val selectedLevel: StateFlow<LevelResponse?> = _selectedLevel
    private val _selectedPlayer = MutableStateFlow<LeaderboardResponse?>(null)
    val selectedPlayer: StateFlow<LeaderboardResponse?> = _selectedPlayer
    private val _selectedPlayerProfile = MutableStateFlow<ProfileResponse?>(null)
    val selectedPlayerProfile: StateFlow<ProfileResponse?> = _selectedPlayerProfile

    private val playerMap = ConcurrentHashMap<String, LeaderboardResponse>()
    private val profileCache = ConcurrentHashMap<String, ProfileResponse>()
    private val playerRanks = ConcurrentHashMap<String, Int>()
    private val prefs = application.getSharedPreferences("aredl_app_final_fixed", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    
    private var searchJob: Job? = null
    private var victorsJob: Job? = null
    private var enrichmentJob: Job? = null
    private var levelEnrichmentJob: Job? = null
    private var backgroundFetchJob: Job? = null

    init {
        loadLocalData()
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            loadLevelsFromCache()
            loadLeaderboardFromCache()
            val levelsJob = async { fetchLevels() }
            val firstPageJob = async { fetchLeaderboardFirstPage() }
            levelsJob.await()
            val pages = firstPageJob.await()
            _isLoading.value = false
            if (pages > 1) { loadRemainingLeaderboardPages(pages) }
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
        _rouletteHistory.value = (prefs.getStringSet("roulette_h_v2", emptySet()) ?: emptySet()).mapNotNull { 
            try { 
                val p = it.split("|")
                val rawPoints = if (p.size > 2) p[2].toDoubleOrNull() ?: 0.0 else 0.0
                val finalPoints = if (rawPoints > 500) rawPoints / 10.0 else rawPoints
                RecordInfo(id = p[0], achieved_percent = p[1].toInt(), points = finalPoints) 
            } catch(e: Exception) { null }
        }
    }

    private fun loadLevelsFromCache() {
        try {
            val cachedJson = prefs.getString("cached_levels", null)
            if (cachedJson != null) {
                val cached: List<LevelResponse> = json.decodeFromString(ListSerializer(LevelResponse.serializer()), cachedJson)
                _levels.value = cached
                _availableTags.value = cached.flatMap { it.tags ?: emptyList() }.distinct().sorted()
            }
        } catch (e: Exception) {}
    }

    private fun loadLeaderboardFromCache() {
        try {
            val cachedJson = prefs.getString("cached_leaderboard", null)
            if (cachedJson != null) {
                val cached: List<LeaderboardResponse> = json.decodeFromString(ListSerializer(LeaderboardResponse.serializer()), cachedJson)
                updateLeaderboardData(cached)
            }
        } catch (e: Exception) {}
    }

    private fun saveLevelsToCache(levels: List<LevelResponse>, lastModified: String?) {
        try {
            val serialized = json.encodeToString(ListSerializer(LevelResponse.serializer()), levels)
            prefs.edit().apply {
                putString("cached_levels", serialized)
                if (lastModified != null) putString("levels_last_modified", lastModified)
            }.apply()
        } catch (e: Exception) {}
    }

    private fun saveLeaderboardToCache(data: List<LeaderboardResponse>) {
        try {
            val serialized = json.encodeToString(ListSerializer(LeaderboardResponse.serializer()), data)
            prefs.edit().putString("cached_leaderboard", serialized).apply()
        } catch (e: Exception) {}
    }

    private fun extractCreator(level: LevelResponse): String? {
        val name = level.global_name?.takeIf { it.isNotBlank() && it != "AREDL" }
            ?: level.creator?.global_name?.takeIf { it.isNotBlank() && it != "AREDL" }
            ?: level.creator?.username?.takeIf { it.isNotBlank() && it != "AREDL" }
            ?: level.publisher?.global_name?.takeIf { it.isNotBlank() && it != "AREDL" }
            ?: level.publisher?.username?.takeIf { it.isNotBlank() && it != "AREDL" }
        
        if (name == null || name == "AREDL") {
            val userId = level.creator?.id ?: level.publisher?.id
            if (userId != null) {
                playerMap[userId]?.user?.let { return it.global_name ?: it.username }
            }
        }
        return name
    }

    private suspend fun fetchLevels() {
        try {
            val lastModified = prefs.getString("levels_last_modified", null)
            val response: HttpResponse = client.get("https://api.aredl.net/v2/api/aredl/levels/") {
                if (lastModified != null) header(HttpHeaders.IfModifiedSince, lastModified)
            }
            
            if (response.status == HttpStatusCode.NotModified) {
                withContext(Dispatchers.Main) { pickNewRouletteLevel(); pickNewAlphabetLevel() }
                return
            }

            val res: List<LevelResponse> = response.body()
            val processed = res.map { 
                it.copy(
                    points = it.points / 10.0,
                    global_name = extractCreator(it) 
                ) 
            }
            _levels.value = processed
            _availableTags.value = processed.flatMap { it.tags ?: emptyList() }.distinct().sorted()
            
            val newLastModified = response.headers[HttpHeaders.LastModified]
            saveLevelsToCache(processed, newLastModified)
            
            withContext(Dispatchers.Main) { pickNewRouletteLevel(); pickNewAlphabetLevel() }
        } catch (e: Exception) {}
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startBackgroundLevelFetch() {
        backgroundFetchJob?.cancel()
        backgroundFetchJob = viewModelScope.launch(Dispatchers.IO) {
            val priorityLevels = _levels.value.take(500)
            priorityLevels.asFlow().flatMapMerge(concurrency = 5) { level ->
                flow {
                    if (level.global_name == null || level.global_name == "AREDL") {
                        try {
                            val full: LevelResponse = client.get("https://api.aredl.net/v2/api/aredl/levels/${level.id}/").body()
                            val creator = extractCreator(full)
                            if (creator != null && creator != "AREDL") {
                                emit(level.id to creator)
                            }
                            delay(100)
                        } catch (e: Exception) {}
                    }
                }
            }.collect { (id, creator) ->
                _levels.update { list -> 
                    val newList = list.map { if (it.id == id) it.copy(global_name = creator) else it }
                    if (id == priorityLevels.lastOrNull()?.id) saveLevelsToCache(newList, null)
                    newList
                }
            }
        }
    }

    private suspend fun fetchLeaderboardFirstPage(): Int {
        return try {
            val res: PaginatedLeaderboardResponse = client.get("https://api.aredl.net/v2/api/aredl/leaderboard?page=1").body()
            _totalPages.value = res.pages
            updateLeaderboardData(res.data)
            saveLeaderboardToCache(res.data)
            res.pages
        } catch (e: Exception) { 1 }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadRemainingLeaderboardPages(total: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            (2..total).asFlow().flatMapMerge(concurrency = 8) { p ->
                flow {
                    try {
                        val data = client.get("https://api.aredl.net/v2/api/aredl/leaderboard?page=$p").body<PaginatedLeaderboardResponse>().data
                        emit(data)
                    } catch (e: Exception) {}
                }
            }.collect { updateLeaderboardData(it) }
        }
    }

    private fun updateLeaderboardData(data: List<LeaderboardResponse>) {
        data.forEach { player ->
            player.user?.id?.let { id ->
                val processed = player.copy(total_points = (player.total_points ?: 0.0) / 10.0)
                playerMap[id] = processed
                playerRanks[id] = player.rank ?: 0
            }
        }
        _allPlayers.value = playerMap.values.sortedBy { it.rank ?: Int.MAX_VALUE }
        setPage(_currentPage.value)
        enrichProfiles(data)
        triggerLevelEnrichment()
    }

    private fun triggerLevelEnrichment() {
        levelEnrichmentJob?.cancel()
        levelEnrichmentJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300)
            _levels.update { currentLevels ->
                currentLevels.map { level ->
                    if (level.global_name == null || level.global_name == "AREDL") {
                        val betterName = extractCreator(level)
                        if (betterName != null && betterName != "AREDL") {
                            level.copy(global_name = betterName)
                        } else level
                    } else level
                }
            }
            
            _selectedPlayerProfile.value?.let { profile ->
                val mainLevels = _levels.value
                val enrichedRecords = profile.records.map { record ->
                    val matchingLevel = mainLevels.find { it.id == record.id || it.level_id == record.level_id || it.id == record.level?.id }
                    if (matchingLevel != null) {
                        val mainCreator = extractCreator(matchingLevel)
                        if (mainCreator != null && mainCreator != "AREDL") {
                            record.copy(level = matchingLevel.copy(global_name = mainCreator))
                        } else record
                    } else record
                }
                _selectedPlayerProfile.value = profile.copy(records = enrichedRecords)
            }
        }
    }

    private fun enrichProfiles(players: List<LeaderboardResponse>) {
        enrichmentJob?.cancel()
        enrichmentJob = viewModelScope.launch(Dispatchers.IO) {
            players.chunked(10).forEach { chunk ->
                chunk.map { player ->
                    async {
                        val username = player.user?.username ?: return@async
                        if (player.user?.discord_id != null && player.user?.discord_avatar != null && player.user?.avatar != null) return@async
                        try {
                            val profile = profileCache[username] ?: client.get("https://api.aredl.net/v2/api/aredl/profile/$username").body<ProfileResponse>().also { profileCache[username] = it }
                            player.user?.id?.let { id ->
                                val current = playerMap[id] ?: return@async
                                val enriched = current.copy(user = current.user?.copy(
                                    discord_id = profile.user?.discord_id ?: current.user?.discord_id,
                                    discord_avatar = profile.user?.discord_avatar ?: current.user?.discord_avatar,
                                    avatar = profile.user?.avatar ?: current.user?.avatar
                                ))
                                playerMap[id] = enriched
                            }
                        } catch (e: Exception) {}
                    }
                }.awaitAll()
                _allPlayers.value = playerMap.values.sortedBy { it.rank ?: Int.MAX_VALUE }
                delay(200) 
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun selectLevel(level: LevelResponse) {
        _selectedLevel.value = level
        _currentLevelVictors.value = emptyList()
        victorsJob?.cancel()
        victorsJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val full: LevelResponse = client.get("https://api.aredl.net/v2/api/aredl/levels/${level.id}/").body()
                val aredlId = full.id
                val gdId = full.level_id
                val rawLevelPoints = full.points 
                val foundVictors = mutableListOf<LevelRecord>()
                var stopSearching = false

                val processedFull = full.copy(
                    points = full.points / 10.0,
                    global_name = extractCreator(full)
                )

                withContext(Dispatchers.Main) {
                    _selectedLevel.value = processedFull
                    _levels.update { list -> list.map { if (it.id == level.id) processedFull else it } }
                }

                if (!full.records.isNullOrEmpty()) {
                    foundVictors.addAll(full.records)
                    withContext(Dispatchers.Main) { _currentLevelVictors.value = foundVictors.toList() }
                }

                for (page in 1..50) {
                    if (stopSearching || !coroutineContext.isActive) break
                    val lb = try { client.get("https://api.aredl.net/v2/api/aredl/leaderboard?page=$page").body<PaginatedLeaderboardResponse>() } catch(e: Exception) { break }
                    if (lb.data.isEmpty()) break
                    
                    lb.data.asFlow().flatMapMerge(concurrency = 30) { player ->
                        flow {
                            if (stopSearching || !currentCoroutineContext().isActive) return@flow
                            val pt = player.total_points ?: 0.0
                            if (page > 1 && pt < rawLevelPoints) { stopSearching = true; return@flow }
                            val user = player.user ?: return@flow
                            try {
                                val username = user.username ?: return@flow
                                val profile = profileCache[username] ?: try { client.get("https://api.aredl.net/v2/api/aredl/profile/$username").body<ProfileResponse>().also { profileCache[username] = it } } catch(e:Exception){null}
                                val match = profile?.records?.find { r -> (gdId != null && r.level_id == gdId) || r.id == aredlId || r.level?.id == aredlId }
                                if (match != null) {
                                    emit(LevelRecord(
                                        user = user.copy(discord_id = profile?.user?.discord_id ?: user.discord_id, discord_avatar = profile?.user?.discord_avatar ?: user.discord_avatar, avatar = profile?.user?.avatar ?: user.avatar),
                                        video_url = match.video_url ?: match.video
                                    ))
                                }
                            } catch (e: Exception) {}
                        }
                    }.collect { victor ->
                        if (foundVictors.none { it.user?.id == victor.user?.id }) {
                            foundVictors.add(victor)
                            withContext(Dispatchers.Main) {
                                _currentLevelVictors.value = foundVictors.sortedBy { playerRanks[it.user?.id] ?: Int.MAX_VALUE }.toList()
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
        }
    }

    fun selectPlayer(player: LeaderboardResponse) {
        _selectedPlayer.value = player; _selectedPlayerProfile.value = null
        val username = player.user?.username ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = profileCache[username] ?: client.get("https://api.aredl.net/v2/api/aredl/profile/$username").body<ProfileResponse>().also { profileCache[username] = it }
                val mainLevels = _levels.value
                val enrichedRecords = profile.records.map { record ->
                    val matchingLevel = mainLevels.find { it.id == record.id || it.level_id == record.level_id || it.id == record.level?.id }
                    val levelFromRecord = record.level
                    val recordCreator = levelFromRecord?.let { extractCreator(it) }
                    
                    if (matchingLevel != null) {
                        val mainCreator = extractCreator(matchingLevel)
                        val finalCreator = if (mainCreator != null && mainCreator != "AREDL") mainCreator else recordCreator
                        record.copy(level = matchingLevel.copy(global_name = finalCreator))
                    } else if (levelFromRecord != null) {
                        record.copy(level = levelFromRecord.copy(global_name = recordCreator, points = (levelFromRecord.points ?: 0.0) / 10.0))
                    } else record
                }
                _selectedPlayerProfile.value = profile.copy(records = enrichedRecords)
            } catch (e: Exception) {}
        }
    }

    fun setPage(p: Int) { 
        _currentPage.value = p
        val all = _allPlayers.value
        val s = (p - 1) * 100
        if (all.isNotEmpty() && s >= 0 && s < all.size) {
            _leaderboard.value = all.subList(s, minOf(s + 100, all.size))
        }
    }

    fun nextPage() { if (_currentPage.value < _totalPages.value) setPage(_currentPage.value + 1) }
    fun previousPage() { if (_currentPage.value > 1) setPage(_currentPage.value - 1) }

    fun searchLeaderboard(q: String) { 
        searchJob?.cancel()
        searchJob = viewModelScope.launch { 
            delay(150)
            if (q.isBlank()) setPage(_currentPage.value) 
            else _leaderboard.value = _allPlayers.value.filter { (it.user?.global_name ?: "").contains(q, true) || (it.user?.username ?: "").contains(q, true) }.take(50)
        }
    }

    fun pickNewAlphabetLevel() { 
        _levels.value.filter { it.name.startsWith(('A' + _alphabetProgress.value).toString(), true) }.let { 
            if (it.isNotEmpty()) {
                val chosen = it.random()
                _currentAlphabetId.value = chosen.id 
                ensureLevelCreatorFetched(chosen.id)
            }
        } 
    }
    
    fun pickNewRouletteLevel() { 
        val h = _rouletteHistory.value.mapNotNull { it.id }.toSet()
        _levels.value.filter { !h.contains(it.id) }.let { 
            if (it.isNotEmpty()) {
                val chosen = it.random()
                _currentRouletteId.value = chosen.id 
                ensureLevelCreatorFetched(chosen.id)
            }
        } 
    }

    private fun ensureLevelCreatorFetched(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _levels.value.find { it.id == id }
            if (current == null || current.global_name == null || current.global_name == "AREDL") {
                try {
                    val full: LevelResponse = client.get("https://api.aredl.net/v2/api/aredl/levels/$id/").body()
                    val creator = extractCreator(full)
                    if (creator != null && creator != "AREDL") {
                        _levels.update { list -> list.map { if (it.id == id) it.copy(global_name = creator) else it } }
                    }
                } catch (e: Exception) {}
            }
        }
    }
    
    fun advanceRoulette(a: Int) { 
        val lvl = currentRouletteLevel.value ?: return
        val record = RecordInfo(id = lvl.id, level_id = lvl.level_id, name = lvl.name, position = lvl.position, points = lvl.points, achieved_percent = a, level = lvl)
        _rouletteHistory.update { listOf(record) + it }
        if (a >= 100) _rouletteWon.value = true else { _roulettePercent.value = a + 1; pickNewRouletteLevel() }
        saveRoulette() 
    }
    
    private fun saveRoulette() { 
        prefs.edit()
            .putInt("roulette_p", _roulettePercent.value)
            .putStringSet("roulette_h_v2", _rouletteHistory.value.map { "${it.id}|${it.achieved_percent}|${it.points ?: 0.0}" }.toSet())
            .apply() 
    }
    
    fun resetRoulette() { _rouletteWon.value = false; _roulettePercent.value = 1; _rouletteHistory.value = emptyList(); pickNewRouletteLevel(); saveRoulette() }
    
    fun advanceAlphabet() { 
        val id = _currentAlphabetId.value ?: return
        _alphabetHistory.update { (it + id).distinct() }
        if (_alphabetProgress.value >= 25) _alphabetWon.value = true else { _alphabetProgress.value += 1; pickNewAlphabetLevel() }
        prefs.edit().putInt("alpha_p", _alphabetProgress.value).putStringSet("alphabet_h", _alphabetHistory.value.toSet()).apply() 
    }

    fun resetAlphabet() { _alphabetWon.value = false; _alphabetProgress.value = 0; _alphabetHistory.value = emptyList(); pickNewAlphabetLevel(); prefs.edit().putInt("alpha_p", 0).putStringSet("alphabet_h", emptySet()).apply() }

    fun toggleFavorite(id: String) { _favoriteLevels.update { val n = it.toMutableSet().apply { if (contains(id)) remove(id) else add(id) }; prefs.edit().putStringSet("favorites", n).apply(); n } }
    fun toggleTodo(id: String) { _todoLevels.update { val n = it.toMutableSet().apply { if (contains(id)) remove(id) else add(id) }; prefs.edit().putStringSet("todo", n).apply(); n } }
    fun toggleCompleted(id: String) { _completedLevels.update { val n = it.toMutableSet().apply { if (contains(id)) remove(id) else add(id) }; prefs.edit().putStringSet("completed", n).apply(); n } }
}
