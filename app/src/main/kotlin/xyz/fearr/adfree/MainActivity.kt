package xyz.fearr.adfree

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class MainActivity : ComponentActivity() {
    private var frameworkInfo by mutableStateOf<ModuleApplication.FrameworkInfo?>(null)
    private val updateStatus = Runnable { frameworkInfo = moduleApplication().frameworkInfo() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateStatus.run()
        setContent {
            val dark = isSystemInDarkTheme()
            val colors = if (dark) {
                darkColorScheme(primary = Color(0xFF91D5B2))
            } else {
                lightColorScheme(primary = Color(0xFF216B55))
            }
            MiuixTheme(colors = colors) {
                Dashboard(
                    info = requireNotNull(frameworkInfo),
                    onRefresh = {
                        updateStatus.run()
                        Toast.makeText(this, R.string.refreshed, Toast.LENGTH_SHORT).show()
                    },
                    onCopy = ::copyEnvironment,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        moduleApplication().observe(updateStatus)
        updateStatus.run()
    }

    override fun onPause() {
        moduleApplication().removeObserver(updateStatus)
        super.onPause()
    }

    private fun moduleApplication() = application as ModuleApplication

    private fun copyEnvironment() {
        val info = "AdFree ${BuildConfig.VERSION_NAME}\n${BuildConfig.APPLICATION_ID}" +
            "\nAndroid ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}" +
            "\nABI ${Build.SUPPORTED_ABIS.joinToString()}" +
            "\n${moduleApplication().frameworkStatus()}\n${getString(R.string.framework_hint)}"
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("AdFree", info))
        Toast.makeText(this, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun Dashboard(
    info: ModuleApplication.FrameworkInfo,
    onRefresh: () -> Unit,
    onCopy: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.headline),
                actions = {
                    Text(
                        text = stringResource(R.string.subtitle, BuildConfig.VERSION_NAME),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(end = 24.dp),
                    )
                },
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                    .padding(horizontal = 20.dp).padding(bottom = 32.dp),
            ) {
                SectionHeading(stringResource(R.string.framework_title))
                Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(24.dp)) {
                    Text(info.title(), fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    SelectionContainer {
                        Text(info.details(), color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 15.sp, lineHeight = 23.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Caption(stringResource(R.string.framework_hint))
                }
                SectionHeading(stringResource(R.string.targets_title))
                Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(24.dp)) {
                    Caption(stringResource(R.string.targets_caption))
                    Spacer(Modifier.height(20.dp))
                    AppRow(stringResource(R.string.xbud_name), stringResource(R.string.xbud_detail),
                        stringResource(R.string.xbud_version))
                    Spacer(Modifier.height(24.dp))
                    AppRow(stringResource(R.string.fiveplay_name), stringResource(R.string.fiveplay_detail),
                        stringResource(R.string.fiveplay_version))
                }
                Caption(stringResource(R.string.scope_hint), Modifier.padding(horizontal = 8.dp, vertical = 12.dp))
                SectionHeading(stringResource(R.string.tools_title))
                Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                    Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.refresh))
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.copy_diagnostics))
                    }
                }
                Spacer(Modifier.height(28.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Caption(stringResource(R.string.environment, Build.VERSION.RELEASE, Build.VERSION.SDK_INT))
                    Spacer(Modifier.height(6.dp))
                    Caption(stringResource(R.string.footer))
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 12.dp))
}

@Composable
private fun AppRow(name: String, packageName: String, version: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            SelectionContainer { Caption(packageName) }
        }
        Text(version, fontSize = 13.sp, color = MiuixTheme.colorScheme.primary)
    }
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontSize = 13.sp, lineHeight = 20.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}
