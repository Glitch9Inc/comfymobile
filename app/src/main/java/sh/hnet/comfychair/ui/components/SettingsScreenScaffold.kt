package sh.hnet.comfychair.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sh.hnet.comfychair.ui.components.shared.NoOverscrollContainer

/**
 * Reusable scaffold for settings screens.
 * Provides consistent layout with TopAppBar, SettingsMenuDropdown, and scrollable content.
 *
 * @param title The title displayed in the TopAppBar
 * @param onNavigateToGeneration Callback when user navigates to generation screen
 * @param onLogout Callback when user logs out
 * @param scrollable Whether the content is scrollable (default true)
 * @param horizontalPadding Horizontal padding for content (default 16.dp)
 * @param content The composable content to display
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenScaffold(
    title: String,
    onNavigateToGeneration: () -> Unit,
    onLogout: () -> Unit,
    scrollable: Boolean = true,
    horizontalPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    // On the one-page settings screen, sections are stacked inside one scroll:
    // no app bar, no own scroll, just a section header and the content.
    if (LocalSettingsEmbedded.current) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding)) {
            SettingsSectionHeader(title)
            content()
        }
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            windowInsets = WindowInsets(0, 0, 0, 0),
            actions = {
                SettingsMenuDropdown(
                    onGeneration = onNavigateToGeneration,
                    onLogout = onLogout
                )
            }
        )

        NoOverscrollContainer(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = horizontalPadding),
                content = content
            )
        }
    }
}

/** True when a settings screen is shown as a section of the one-page settings screen. */
val LocalSettingsEmbedded = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Section header used on the one-page settings screen. */
@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
        letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp),
        modifier = Modifier.padding(top = 28.dp, bottom = 8.dp)
    )
}
