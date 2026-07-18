package de.dvld.heartthrum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource

val Pink = Color(0xFFE0326E)
val Purple = Color(0xFFA02497)

private val LightColors = lightColorScheme(
    primary = Pink,
    secondary = Purple,
    tertiary = Color(0xFF14B8A6)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF7AA8),
    secondary = Color(0xFFE54AC2),
    tertiary = Color(0xFF2DD4BF)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
                Root()
            }
        }
    }
}

@Composable
fun Root() {
    val prefs = Prefs.current()
    var onboarded by rememberSaveable { mutableStateOf(prefs.getBoolean("onboarded", false)) }
    if (!onboarded) {
        OnboardingScreen {
            prefs.edit().putBoolean("onboarded", true).apply()
            onboarded = true
        }
        return
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Favorite, null) },
                    label = { Text(stringResource(R.string.tab_measure)) })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.ShowChart, null) },
                    label = { Text(stringResource(R.string.tab_history)) })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Air, null) },
                    label = { Text(stringResource(R.string.tab_breathe)) })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 },
                    icon = { Icon(Icons.Filled.Settings, null) },
                    label = { Text(stringResource(R.string.tab_settings)) })
            }
        }
    ) { pad ->
        val m = Modifier.padding(pad)
        when (tab) {
            0 -> MeasureScreen(m)
            1 -> HistoryScreen(m)
            2 -> BreatheScreen(m)
            else -> SettingsScreen(m)
        }
    }
}
