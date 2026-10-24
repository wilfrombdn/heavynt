package com.wilfrombdn.heavynt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true; isLenient = true; prettyPrint = false }

fun uid(): String = UUID.randomUUID().toString().take(8)

/** First-run state: library, the preloaded programme (Wed-Sun of week 1, week 2 off) and this week = week 1. */
fun seedState(): AppState {
    val plans = STARTER_PLANS.map { Plan(uid(), it.name, it.items) }
    val days = MutableList<String?>(7 * STARTER_WEEKS) { null }
    STARTER_PLANS.forEachIndexed { i, sp -> days[sp.weekday] = plans[i].id }
    return AppState(
        v = 4,
        exercises = LIBRARY,
        plans = plans.map(::inferSupersets),
        schedule = Schedule(mondayOf(dateKey()), days),
    )
}

/** Library additions shipped in newer versions are merged in without touching user data. */
fun normalize(s: AppState): AppState {
    val have = s.exercises.map { it.id }.toSet()
    val merged = s.exercises + LIBRARY.filter { it.id !in have }
    val cutoff = dateKey(today().minusDays(60))
    val overrides = s.overrides.filterKeys { it >= cutoff }
    val plans = if (s.v < 5) s.plans.map(::inferSupersets) else s.plans
    return s.copy(v = 5, exercises = merged, plans = plans, overrides = overrides)
}

class Storage(private val file: File) {
    fun loadOrNull(): AppState? = try {
        if (file.exists()) normalize(AppJson.decodeFromString<AppState>(file.readText())) else null
    } catch (e: Exception) { null }

    fun save(s: AppState) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(AppJson.encodeToString(s))
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
    }
}

fun storageFor(app: android.content.Context) = Storage(File(app.filesDir, "heavynt-state.json"))

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = storageFor(app)
    private val _state = MutableStateFlow(storage.loadOrNull() ?: seedState().also { storage.save(it) })
    val state: StateFlow<AppState> = _state.asStateFlow()

    /** Apply a change and persist it. */
    fun update(f: (AppState) -> AppState) {
        _state.update(f)
        val snapshot = _state.value
        viewModelScope.launch(Dispatchers.IO) { storage.save(snapshot) }
    }

    fun importBackup(text: String): Result<Unit> = runCatching {
        val s = AppJson.decodeFromString<AppState>(text)
        require(s.exercises.isNotEmpty() || s.logs.isNotEmpty() || s.plans.isNotEmpty()) { "Not a workout backup" }
        update { normalize(s.copy(v = 4)) }
    }

    fun exportBackup(): String = AppJson.encodeToString(_state.value)

    fun eraseAll() { update { seedState() } }

    suspend fun saveNow() = withContext(Dispatchers.IO) { storage.save(_state.value) }
}
