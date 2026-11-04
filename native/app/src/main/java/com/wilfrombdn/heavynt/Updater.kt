package com.wilfrombdn.heavynt

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * In-app updates, the Tellem way. Heavyn't asks GitHub for the newest release of wilfrombdn/heavynt (what
 * `scripts/release-native.ps1` publishes), downloads its .apk in the background, and hands it to Android's
 * installer. Android refuses anything not signed with the same key as the installed app, so a tampered file can't install.
 */
object Updater {
    private const val API = "https://api.github.com/repos/wilfrombdn/heavynt/releases/latest"
    private const val CHECK_EVERY_MS = 6 * 3600_000L

    class Update(val version: String, val notes: List<String>, val url: String, val sha256: String?, val size: Long)

    sealed class State {
        object Idle : State()
        object Checking : State()
        object UpToDate : State()
        class Downloading(val update: Update, val progress: Float) : State()
        class Ready(val update: Update, val file: File) : State()
        class Failed(val message: String) : State()
    }

    val state = MutableStateFlow<State>(State.Idle)
    /** Set when Android reports the install didn't go through. */
    val installError = MutableStateFlow<String?>(null)

    fun currentName(ctx: Context): String = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"

    /** "3.20.1" is newer than "3.20.0"; anything non-numeric counts as 0. */
    fun newer(a: String, b: String): Boolean {
        val x = a.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Heavynt-updater"); setRequestProperty("Accept", "application/vnd.github+json")
    }

    /** The newer release, or null when this is the latest. Off the main thread. */
    fun check(ctx: Context): Update? {
        val c = open(API)
        if (c.responseCode != 200) throw IOException("GitHub said HTTP ${c.responseCode}")
        val o = JSONObject(c.inputStream.use { it.readBytes().decodeToString() })
        val tag = o.getString("tag_name")
        if (!newer(tag, currentName(ctx))) return null
        val assets = o.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) {
                val notes = o.optString("body").lines().map { it.trim().trimStart('-', '*', ' ') }.filter { it.isNotEmpty() }.take(6)
                val digest = a.optString("digest").removePrefix("sha256:").lowercase().takeIf { it.length == 64 }
                return Update(tag.removePrefix("v"), notes, a.getString("browser_download_url"), digest, a.optLong("size"))
            }
        }
        return null
    }

    private fun dir(ctx: Context) = File(ctx.cacheDir, "update")

    fun clear(ctx: Context) { dir(ctx).deleteRecursively() }

    /** Downloads (and checks) the APK. Off the main thread. */
    fun download(ctx: Context, u: Update, progress: (Float) -> Unit): File {
        val d = dir(ctx).apply { mkdirs() }
        val f = File(d, "heavynt-${u.version}.apk")
        d.listFiles()?.filter { it != f }?.forEach { it.deleteRecursively() }
        if (f.exists() && verify(f, u)) return f
        val part = File(d, f.name + ".part").apply { delete() }
        val c = open(u.url)
        if (c.responseCode != 200) throw IOException("Download failed (HTTP ${c.responseCode})")
        val total = c.contentLengthLong.takeIf { it > 0 } ?: u.size
        c.inputStream.use { inp -> part.outputStream().use { out ->
            val buf = ByteArray(64 * 1024); var got = 0L
            while (true) {
                val n = inp.read(buf); if (n < 0) break
                out.write(buf, 0, n); got += n
                if (total > 0) progress(got.toFloat() / total)
            }
        } }
        if (!part.renameTo(f)) throw IOException("Couldn't save the download")
        if (!verify(f, u)) { f.delete(); throw IOException("The download was damaged") }
        return f
    }

    private fun verify(f: File, u: Update): Boolean {
        if (f.length() < 100_000) return false
        val want = u.sha256 ?: return true   // no checksum published: Android's signature check is the guard
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s -> val b = ByteArray(64 * 1024); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) } == want
    }

    /** Check, and when there's something new, download it. [force] ignores the "checked recently" pause. */
    fun run(ctx: Context, force: Boolean) {
        val app = ctx.applicationContext
        val prefs = app.getSharedPreferences("updater", Context.MODE_PRIVATE)
        if (!force && System.currentTimeMillis() - prefs.getLong("last", 0) < CHECK_EVERY_MS) return
        if (state.value is State.Checking || state.value is State.Downloading) return
        state.value = State.Checking
        try {
            val u = check(app)
            prefs.edit().putLong("last", System.currentTimeMillis()).apply()
            if (u == null) { clear(app); state.value = State.UpToDate; return }
            state.value = State.Downloading(u, 0f)
            var lastPct = -1
            val f = download(app, u) { p -> val pct = (p * 100).toInt(); if (pct != lastPct) { lastPct = pct; state.value = State.Downloading(u, p) } }
            state.value = State.Ready(u, f)
        } catch (e: Exception) {
            state.value = State.Failed(e.message ?: "Couldn't check for updates")
        }
    }

    const val ACTION_STATUS = "com.wilfrombdn.heavynt.UPDATE_STATUS"

    /** Hands the APK to Android's installer; Android closes the app while it replaces it. */
    fun install(ctx: Context, apk: File) {
        installError.value = null
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            // Android 12+: no extra confirmation once Heavyn't has installed an update before
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            s.openWrite("heavynt.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; s.fsync(out) }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val status = PendingIntent.getBroadcast(ctx, 7, Intent(ctx, UpdateReceiver::class.java).setAction(ACTION_STATUS).setPackage(ctx.packageName), flags)
            s.commit(status.intentSender)
        }
    }
}

/** Installer status (asks for confirmation when Android needs it), and reopening the app after the update. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        when (i.action) {
            Updater.ACTION_STATUS -> when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION") val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { ctx.startActivity(it) } }
                }
                PackageInstaller.STATUS_SUCCESS -> {}
                else -> Updater.installError.value = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "The update didn't install"
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Updater.clear(ctx)
                runCatching { ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }
}
