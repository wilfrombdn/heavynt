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

/** First-run state: just the exercise library. No plans or schedule; the user builds their own. */
fun seedState(): AppState {
    return AppState(
        v = 9,
        exercises = LIBRARY,
        plans = STARTER_DAYS,
        schedule = starterSchedule(),
        planName = STARTER_NAME,
    )
}

/** Library additions shipped in newer versions are merged in without touching user data. */
fun normalize(s: AppState): AppState {
    val have = s.exercises.map { it.id }.toSet()
    // v7: finer muscle map and research-based benchmarks, so built-in exercises take the library's muscles and benchmarks.
    val lib = LIBRARY.associateBy { it.id }
    val retagged = if (s.v < 7) s.exercises.map { e ->
        lib[e.id]?.takeIf { !e.custom }?.let { e.copy(primary = it.primary, secondary = it.secondary, std = it.std, stdF = it.stdF) } ?: e
    } else s.exercises
    val merged = retagged + LIBRARY.filter { it.id !in have }
    val cutoff = dateKey(today().minusDays(60))
    val overrides = s.overrides.filterKeys { it >= cutoff }
    val intake = s.intake.filterKeys { it >= cutoff }
    val plans = when { s.v < 5 -> s.plans.map(::inferSupersets); s.v < 9 -> fixStarterDays(s.plans); else -> s.plans }
    val saved = if (s.v < 9) s.saved.map { it.copy(plans = fixStarterDays(it.plans)) } else s.saved
    // Weights saved before the monthly weigh-in existed count as current, so nobody is asked straight after updating.
    val settings = if (s.settings.bodyweight > 0 && s.settings.bodyweightSetAt.isEmpty()) s.settings.copy(bodyweightSetAt = dateKey()) else s.settings
    val out = withStarter(s.copy(exercises = merged, plans = plans, saved = saved, overrides = overrides, settings = settings, intake = intake)).copy(v = 9)
    // a demo left on from an older version: show the current demo (the real data stays set aside)
    return if (out.demoBackup != null && out.demoVersion < DEMO_VERSION) demoState(out).copy(demoBackup = out.demoBackup) else out
}

/** v8: installs from before the starter plan get it once: as the current plan if they have no workout days, else in the saved plans. */
private fun withStarter(s: AppState): AppState = when {
    s.v >= 8 || s.planName == STARTER_NAME || s.saved.any { it.name == STARTER_NAME } -> s
    s.plans.isEmpty() -> s.copy(plans = STARTER_DAYS, schedule = starterSchedule(), planName = STARTER_NAME)
    else -> s.copy(saved = s.saved + SavedPlan(uid(), STARTER_NAME, STARTER_DAYS, starterSchedule(), nowIso()))
}

class Storage(private val file: File) {
    fun loadOrNull(): AppState? = try {
        if (file.exists()) normalize(AppJson.decodeFromString<AppState>(file.readText())) else null
    } catch (e: Exception) { null }

    @Synchronized
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

    /** Developer demo: set the real data aside and show a filled-in example. */
    fun enterDemo() { update { s -> if (s.demoBackup != null) s else demoState(s).copy(demoBackup = AppJson.encodeToString(s)) } }

    fun exitDemo() { update { s -> s.demoBackup?.let { normalize(AppJson.decodeFromString<AppState>(it)) } ?: s } }

    suspend fun saveNow() = withContext(Dispatchers.IO) { storage.save(_state.value) }
}
