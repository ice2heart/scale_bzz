package ru.scalejournal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.scalejournal.ble.ScaleClient
import ru.scalejournal.data.AppDatabase
import ru.scalejournal.data.Measurement
import ru.scalejournal.data.UserProfile
import ru.scalejournal.ui.AppScreen
import ru.scalejournal.ui.AppTheme
import ru.scalejournal.util.AppLog
import ru.scalejournal.util.GitHubLogUploader
import ru.scalejournal.util.LogConfig
import ru.scalejournal.util.UpdateChecker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val db by lazy { AppDatabase.get(this) }
    private val prefs by lazy { getSharedPreferences("log", MODE_PRIVATE) }

    private val status = mutableStateOf("Запуск…")
    private val measurements = mutableStateListOf<Measurement>()
    private val profile = mutableStateOf<UserProfile?>(null)
    private val updateMessage = mutableStateOf("")
    private val updateAvailable = mutableStateOf(false)
    private var updateInfo: UpdateChecker.Info? = null
    private var client: ScaleClient? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        AppLog.log("PERM", "результат запроса разрешений: $granted")
        if (granted.values.all { it }) {
            startSession()
        } else {
            status.value = "Нужны разрешения Bluetooth"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.init(this)
        AppLog.log(
            "APP",
            "запуск: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
        )
        enableEdgeToEdge()
        lifecycleScope.launch {
            db.measurementDao().observeAll().collect { list ->
                measurements.clear()
                measurements.addAll(list)
            }
        }
        lifecycleScope.launch {
            db.profileDao().observe().collect { profile.value = it }
        }
        setContent {
            AppTheme {
                AppScreen(
                    status = status.value,
                    measurements = measurements,
                    profile = profile.value,
                    updateMessage = updateMessage.value,
                    updateAvailable = updateAvailable.value,
                    storedLogToken = prefs.getString("gh_token", "") ?: "",
                    onMeasure = { ensurePermissionsThen { startSession() } },
                    onAdd = { weight -> insert(weight, null, "manual") },
                    onDelete = { m ->
                        AppLog.log("DB", "удаление записи id=${m.id}")
                        lifecycleScope.launch { db.measurementDao().delete(m) }
                    },
                    onSaveProfile = { heightCm, birthMillis, isMale ->
                        saveProfile(heightCm, birthMillis, isMale)
                    },
                    onSendLog = { token, onResult ->
                        val resolved = token.takeIf { it.isNotBlank() }?.also {
                            prefs.edit().putString("gh_token", it).apply()
                        } ?: effectiveToken()
                        doUploadLog(resolved, onResult)
                    },
                    onCheckUpdate = { checkUpdate() },
                    onDownloadUpdate = { downloadUpdate() },
                )
            }
        }
        AppLog.log("APP", "автостарт поиска весов")
        ensurePermissionsThen { startSession() }
    }

    override fun onStart() {
        super.onStart()
        AppLog.log("APP", "onStart")
        if (client == null && hasPermissions()) {
            AppLog.log("APP", "автоперезапуск поиска весов")
            startSession()
        }
    }

    override fun onStop() {
        super.onStop()
        AppLog.log("APP", "onStop")
    }

    private fun neededPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun hasPermissions(): Boolean =
        neededPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun ensurePermissionsThen(action: () -> Unit) {
        val missing = neededPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            AppLog.log("PERM", "запрос разрешений: $missing")
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startSession() {
        client?.stop()
        val newClient = ScaleClient(this, object : ScaleClient.Listener {
            override fun onStatus(text: String) {
                runOnUiThread { status.value = text }
            }

            override fun onMeasured(weightKg: Double, impedanceOhm: Double?, source: String) {
                runOnUiThread { insert(weightKg, impedanceOhm, source) }
            }

            override fun onSessionEnded(c: ScaleClient, connectionAttempted: Boolean) {
                runOnUiThread {
                    if (client === c) client = null
                    if (connectionAttempted) autoSendLog()
                }
            }
        })
        client = newClient
        newClient.start()
    }

    private fun insert(weight: Double, impedance: Double?, source: String) {
        AppLog.log("DB", "сохранение записи: weight=$weight imp=$impedance source=$source")
        lifecycleScope.launch {
            db.measurementDao().insert(
                Measurement(
                    timeMillis = System.currentTimeMillis(),
                    weightKg = weight,
                    impedanceOhm = impedance,
                    source = source,
                ),
            )
        }
        if (source == "manual") status.value = "Запись добавлена"
    }

    private fun saveProfile(heightCm: Int, birthMillis: Long, isMale: Boolean) {
        AppLog.log("DB", "сохранение профиля: height=$heightCm birth=$birthMillis male=$isMale")
        lifecycleScope.launch {
            db.profileDao().upsert(
                UserProfile(id = 1, heightCm = heightCm, birthMillis = birthMillis, isMale = isMale),
            )
        }
        status.value = "Профиль сохранён"
    }

    private fun effectiveToken(): String =
        prefs.getString("gh_token", null)?.takeIf { it.isNotBlank() } ?: ""

    private fun autoSendLog() {
        val token = effectiveToken()
        if (token.isBlank()) {
            AppLog.log("LOG", "автоотправка пропущена: токен GitHub не задан (Профиль → Отладка)")
            return
        }
        doUploadLog(token) { message ->
            if (message.startsWith("Ошибка")) {
                AppLog.log("LOG", "ошибка автоотправки лога: $message")
            } else {
                AppLog.log("LOG", "автоотправка лога: $message")
            }
        }
    }

    private fun doUploadLog(token: String, onResult: (String) -> Unit) {
        if (token.isBlank()) {
            onResult("Ошибка: введите GitHub-токен с правом Contents: Read and write")
            return
        }
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
        val meta = buildString {
            appendLine("app: ScaleJournal $version")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("sent: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        }
        val content = AppLog.dump(meta)
        val fileName = "log-$ts.txt"
        AppLog.log("LOG", "отправка лога в oblvsp/scale/logs/$fileName (${content.length} символов)")
        GitHubLogUploader.upload(token, fileName, content) { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = {
                        AppLog.log("LOG", "лог отправлен: $it")
                        onResult(it)
                    },
                    onFailure = { e ->
                        AppLog.log("LOG", "ошибка отправки лога: $e")
                        onResult("Ошибка: ${e.message}")
                    },
                )
            }
        }
    }

    private fun checkUpdate() {
        updateMessage.value = "Проверка обновлений…"
        UpdateChecker.latest(effectiveToken()) { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { info ->
                        if (info == null) {
                            updateMessage.value = "Релизы не найдены в ${LogConfig.REPO_OWNER}/${LogConfig.REPO_NAME}"
                            updateAvailable.value = false
                        } else {
                            val current = try {
                                packageManager.getPackageInfo(packageName, 0).versionName
                            } catch (_: Exception) {
                                "?"
                            }
                            val remote = info.tag.removePrefix("v")
                            if (!isNewerVersion(remote, current)) {
                                updateMessage.value = "Установлена последняя версия ($current)"
                                updateAvailable.value = false
                            } else {
                                updateInfo = info
                                updateAvailable.value = true
                                updateMessage.value =
                                    "Доступно обновление ${info.tag} (установлена $current)"
                            }
                        }
                    },
                    onFailure = { e ->
                        updateMessage.value = "Ошибка проверки: ${e.message}"
                        updateAvailable.value = false
                    },
                )
            }
        }
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split('.')
            .map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        // Равные числа: пре-релиз (1.4.3-rc1) старше обычного релиза (1.4.3)
        return current.contains('-') && !remote.contains('-')
    }

    private fun downloadUpdate() {
        val info = updateInfo ?: return
        updateMessage.value = "Скачивание…"
        UpdateChecker.download(effectiveToken(), info.apkUrl, info.sizeBytes, File(File(cacheDir, "updates"), "update.apk")) { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { file ->
                        updateMessage.value = "Файл скачан — подтвердите установку"
                        AppLog.log("UPDATE", "APK скачан (${file.length()} байт), запуск установщика")
                        installUpdate(file)
                    },
                    onFailure = { e ->
                        updateMessage.value = "Ошибка скачивания: ${e.message}"
                        AppLog.log("UPDATE", "ошибка скачивания: $e")
                    },
                )
            }
        }
    }

    private fun installUpdate(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}
