package xyz.fearr.adfree

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import java.util.concurrent.Executors
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private enum class InstallState { LOADING, INSTALLED, MISSING, ERROR }
private data class InstalledTarget(
    val target: TargetCatalog.Target,
    val state: InstallState = InstallState.LOADING,
    val label: String = target.name(),
    val version: String? = null,
    val code: Long = 0,
) {
    val matches get() = state == InstallState.INSTALLED && target.matches(version, code)
}
private data class DashboardState(
    val framework: ModuleApplication.FrameworkInfo,
    val scope: ScopeController.Snapshot,
    val targets: List<InstalledTarget>,
)

class MainActivity : ComponentActivity() {
    private var dashboard by mutableStateOf<DashboardState?>(null)
    private var refreshGeneration = 0
    private val refreshState = Runnable { loadState() }
    private var updateState by mutableStateOf(UpdateState())
    private val updateWorker = Executors.newSingleThreadExecutor()
    private var logStorageEnabled by mutableStateOf(true)
    private var exportingLogs by mutableStateOf(false)
    private val logDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) {
            exportingLogs = false
        } else {
            exportingLogs = true
            moduleApplication().logs().record("INFO", "app", "Exporting logs", null)
            moduleApplication().logs().export(uri, environmentInfo()) { success ->
                runOnUiThread {
                    if (!isDestroyed && !isFinishing) {
                        exportingLogs = false
                        Toast.makeText(this, if (success) R.string.logs_exported else R.string.logs_export_failed,
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        logStorageEnabled = moduleApplication().logs().isEnabled
        dashboard = DashboardState(
            moduleApplication().frameworkInfo(),
            ScopeController.Snapshot(false, emptySet(), emptySet(), emptyMap(), null),
            TargetCatalog.TARGETS.map { InstalledTarget(it) },
        )
        setContent {
            val colors = if (isSystemInDarkTheme()) {
                darkColorScheme(primary = Color(0xFF91D5B2))
            } else {
                lightColorScheme(primary = Color(0xFF216B55))
            }
            MiuixTheme(colors = colors) {
                Dashboard(
                    state = requireNotNull(dashboard),
                    onRefresh = {
                        moduleApplication().logs().record("INFO", "app", "Refreshing framework state", null)
                        loadState()
                        Toast.makeText(this, R.string.refreshed, Toast.LENGTH_SHORT).show()
                    },
                    onCopy = ::copyEnvironment,
                    onScope = { name, enabled ->
                        moduleApplication().logs().record("INFO", "app", "Scope request: $name enabled=$enabled", null)
                        moduleApplication().scopes.setEnabled(name, enabled)
                    },
                    update = updateState,
                    onCheckUpdate = ::checkUpdate,
                    onOpenRelease = ::openRelease,
                    logStorageEnabled = logStorageEnabled,
                    exportingLogs = exportingLogs,
                    onLogStorage = { enabled ->
                        moduleApplication().logs().setEnabled(enabled)
                        logStorageEnabled = enabled
                    },
                    onExportLogs = ::exportLogs,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        moduleApplication().observe(refreshState)
        loadState()
    }

    override fun onPause() {
        moduleApplication().removeObserver(refreshState)
        refreshGeneration++
        super.onPause()
    }

    private fun moduleApplication() = application as ModuleApplication

    override fun onDestroy() {
        updateWorker.shutdownNow()
        super.onDestroy()
    }

    private fun checkUpdate() {
        if (updateState.status == UpdateStatus.CHECKING) return
        updateState = UpdateState(UpdateStatus.CHECKING)
        moduleApplication().logs().record("INFO", "app", "Checking GitHub updates", null)
        updateWorker.execute {
            val result = try {
                GitHubUpdateChecker.check(BuildConfig.VERSION_NAME)
            } catch (error: Exception) {
                moduleApplication().logs().record("ERROR", "app", "GitHub update check failed", error)
                UpdateState(UpdateStatus.FAILED)
            }
            moduleApplication().logs().record("INFO", "app", "Update result: ${result.status} ${result.version.orEmpty()}", null)
            runOnUiThread {
                if (!isDestroyed && !isFinishing) updateState = result
            }
        }
    }

    private fun openRelease() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse(updateState.releaseUrl ?: GitHubUpdateChecker.RELEASES_URL)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.browser_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportLogs() {
        if (exportingLogs) return
        exportingLogs = true
        try {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            logDocument.launch("AdFree-$timestamp.txt")
        } catch (error: ActivityNotFoundException) {
            exportingLogs = false
            moduleApplication().logs().record("ERROR", "app", "Document picker unavailable", error)
            Toast.makeText(this, R.string.logs_picker_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun loadState() {
        val generation = ++refreshGeneration
        val app = moduleApplication()
        app.worker.execute {
            val next = DashboardState(app.frameworkInfo(), app.scopes.snapshot(),
                TargetCatalog.TARGETS.map(::readTarget))
            runOnUiThread {
                if (generation == refreshGeneration && !isDestroyed) {
                    if (dashboard != next) {
                        moduleApplication().logs().record("INFO", "app",
                            "State: ${next.framework.title()}, scope=${next.scope.packages()}, " +
                                "scopeErrors=${next.scope.errors()}, readError=${next.scope.readError()}, " +
                                "targets=${next.targets.joinToString { "${it.target.packageName()}:${it.version ?: it.state}(${it.code})" }}",
                            null)
                    }
                    dashboard = next
                }
            }
        }
    }

    private fun readTarget(target: TargetCatalog.Target): InstalledTarget {
        return try {
            val info = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(target.packageName(), PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(target.packageName(), 0)
            }
            val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            val label = info.applicationInfo?.let { packageManager.getApplicationLabel(it).toString() } ?: target.name()
            InstalledTarget(target, InstallState.INSTALLED, label, info.versionName, code)
        } catch (_: PackageManager.NameNotFoundException) {
            InstalledTarget(target, InstallState.MISSING)
        } catch (_: RuntimeException) {
            InstalledTarget(target, InstallState.ERROR)
        }
    }

    private fun environmentInfo(): String {
        val state = requireNotNull(dashboard)
        val targetInfo = state.targets.joinToString("\n") {
            "${it.target.packageName()}: ${it.state}, ${it.version ?: "-"} (${it.code}), " +
                "scope=${if (state.scope.available()) state.scope.packages().contains(it.target.packageName()) else "unknown"}"
        }
        return "AdFree ${BuildConfig.VERSION_NAME}\n${BuildConfig.APPLICATION_ID}" +
            "\nAndroid ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}" +
            "\nABI ${Build.SUPPORTED_ABIS.joinToString()}" +
            "\n${state.framework.title()}\n${state.framework.details()}\n$targetInfo"
    }

    private fun copyEnvironment() {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("AdFree", environmentInfo()))
        Toast.makeText(this, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun Dashboard(
    state: DashboardState,
    onRefresh: () -> Unit,
    onCopy: () -> Unit,
    onScope: (String, Boolean) -> Unit,
    update: UpdateState,
    onCheckUpdate: () -> Unit,
    onOpenRelease: () -> Unit,
    logStorageEnabled: Boolean,
    exportingLogs: Boolean,
    onLogStorage: (Boolean) -> Unit,
    onExportLogs: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val homeScroll = rememberScrollState()
    val settingsScroll = rememberScrollState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.app_name),
                actions = {
                    Text(stringResource(R.string.subtitle, BuildConfig.VERSION_NAME),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 13.sp, modifier = Modifier.padding(end = 24.dp))
                },
            )
        },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets),
            horizontalAlignment = Alignment.CenterHorizontally) {
            TabRow(
                tabs = listOf(stringResource(R.string.home_tab), stringResource(R.string.settings_tab)),
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier.widthIn(max = 640.dp).padding(horizontal = 20.dp, vertical = 12.dp),
            )
            Column(Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(if (selectedTab == 0) homeScroll else settingsScroll),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()
                    .padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                    when (selectedTab) {
                        0 -> HomePage(state, onScope)
                        else -> SettingsPage(update, onRefresh, onCopy, onCheckUpdate, onOpenRelease,
                            logStorageEnabled, exportingLogs, onLogStorage, onExportLogs)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePage(state: DashboardState, onScope: (String, Boolean) -> Unit) {
    val healthy = if (isSystemInDarkTheme()) Color(0xFF81DBA0) else Color(0xFF16733B)
    val warning = if (isSystemInDarkTheme()) Color(0xFFFFD181) else Color(0xFF8C5900)
    val danger = if (isSystemInDarkTheme()) Color(0xFFFFA5A5) else Color(0xFFB3261E)
    val dark = isSystemInDarkTheme()
    val frameworkBackground = if (state.framework.connected()) {
        if (dark) Color(0xFF173F32) else Color(0xFF216B55)
    } else {
        if (dark) Color(0xFF542626) else Color(0xFF9F3434)
    }
    val frameworkContent = Color(0xFFF5FFF9)
    SectionHeading(stringResource(R.string.framework_title))
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(24.dp),
        colors = CardDefaults.defaultColors(color = frameworkBackground,
            contentColor = frameworkContent)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Image(painterResource(R.drawable.ic_module), contentDescription = null,
                modifier = Modifier.size(52.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.framework_name), color = frameworkContent,
                    fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                StatusBadge(state.framework.title(), frameworkContent)
            }
        }
        Spacer(Modifier.height(16.dp))
        SelectionContainer {
            Text(state.framework.details(), color = frameworkContent.copy(alpha = 0.88f),
                fontSize = 15.sp, lineHeight = 23.sp)
        }
    }
    SectionHeading(stringResource(R.string.targets_title))
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(24.dp)) {
        state.targets.forEachIndexed { index, target ->
            if (index > 0) Spacer(Modifier.height(28.dp))
            TargetRow(target, state, healthy, warning, danger, onScope)
        }
    }
    state.scope.readError()?.let { Caption(it, Modifier.padding(8.dp), danger) }
}

@Composable
private fun SettingsPage(
    update: UpdateState,
    onRefresh: () -> Unit,
    onCopy: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenRelease: () -> Unit,
    logStorageEnabled: Boolean,
    exportingLogs: Boolean,
    onLogStorage: (Boolean) -> Unit,
    onExportLogs: () -> Unit,
) {
    SectionHeading(stringResource(R.string.logs_title))
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.save_logs), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Caption(stringResource(R.string.save_logs_description))
            }
            Switch(checked = logStorageEnabled, onCheckedChange = onLogStorage,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "保存日志" })
        }
        Spacer(Modifier.height(12.dp))
        Caption(stringResource(R.string.logs_retention))
        Spacer(Modifier.height(12.dp))
        Button(onClick = onExportLogs, enabled = !exportingLogs, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (exportingLogs) R.string.logs_exporting else R.string.export_logs))
        }
    }
    SectionHeading(stringResource(R.string.tools_title))
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.refresh)) }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onCopy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.copy_diagnostics)) }
    }
    SectionHeading(stringResource(R.string.updates_title))
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        val message = when (update.status) {
            UpdateStatus.IDLE -> stringResource(R.string.update_idle, BuildConfig.VERSION_NAME)
            UpdateStatus.CHECKING -> stringResource(R.string.update_checking)
            UpdateStatus.AVAILABLE -> stringResource(R.string.update_available,
                update.version.orEmpty(), BuildConfig.VERSION_NAME)
            UpdateStatus.CURRENT -> stringResource(R.string.update_current, BuildConfig.VERSION_NAME)
            UpdateStatus.NO_RELEASE -> stringResource(R.string.update_no_release)
            UpdateStatus.RATE_LIMITED -> stringResource(R.string.update_rate_limited)
            UpdateStatus.FAILED -> stringResource(R.string.update_failed)
        }
        Caption(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Spacer(Modifier.height(12.dp))
        Button(onClick = onCheckUpdate, enabled = update.status != UpdateStatus.CHECKING,
            modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (update.status == UpdateStatus.CHECKING)
                R.string.update_checking else R.string.check_updates))
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onOpenRelease, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (update.status == UpdateStatus.AVAILABLE)
                R.string.update_download else R.string.open_github))
        }
    }
    Spacer(Modifier.height(28.dp))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Caption(stringResource(R.string.environment, Build.VERSION.RELEASE, Build.VERSION.SDK_INT))
    }
}

@Composable
private fun TargetRow(
    installed: InstalledTarget,
    state: DashboardState,
    healthy: Color,
    warning: Color,
    danger: Color,
    onScope: (String, Boolean) -> Unit,
) {
    val name = installed.target.packageName()
    val scoped = state.scope.available() && name in state.scope.packages()
    val pending = name in state.scope.pending()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(installed.label, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            SelectionContainer { Caption(name) }
        }
        Switch(
            checked = scoped,
            onCheckedChange = { onScope(name, it) },
            enabled = state.framework.connected() && state.scope.available() &&
                state.scope.pending().isEmpty() &&
                (installed.state == InstallState.INSTALLED || scoped),
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = "${installed.label}作用域" },
        )
    }
    Spacer(Modifier.height(12.dp))
    val status = when (installed.state) {
        InstallState.LOADING -> stringResource(R.string.app_loading)
        InstallState.MISSING -> stringResource(R.string.app_missing)
        InstallState.ERROR -> stringResource(R.string.app_read_error)
        InstallState.INSTALLED -> if (installed.matches) stringResource(R.string.version_matched)
            else stringResource(R.string.version_mismatch)
    }
    val color = when {
        installed.state == InstallState.MISSING || installed.state == InstallState.ERROR -> danger
        installed.matches -> healthy
        else -> warning
    }
    StatusBadge(status, color)
    if (installed.state == InstallState.INSTALLED) {
        Spacer(Modifier.height(8.dp))
        Caption(stringResource(R.string.installed_version, installed.version ?: "?", installed.code))
        if (!installed.matches) {
            Caption(stringResource(R.string.version_warning, installed.target.versionName(),
                installed.target.versionCode()), color = warning)
        }
    }
    Spacer(Modifier.height(8.dp))
    Caption(when {
        pending -> stringResource(R.string.scope_pending)
        !state.scope.available() -> stringResource(R.string.scope_unavailable)
        scoped -> stringResource(R.string.scope_enabled)
        else -> stringResource(R.string.scope_disabled)
    })
    state.scope.errors()[name]?.let { Caption(it, color = danger) }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Badge(containerColor = color.copy(alpha = if (isSystemInDarkTheme()) 0.18f else 0.10f),
        contentColor = color, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(label, color = color, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 12.dp))
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier,
                    color: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary) {
    Text(text, modifier = modifier, fontSize = 13.sp, lineHeight = 20.sp, color = color)
}
