package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val EnterpriseDarkFintechScheme = darkColorScheme(
    primary = ChampagneAccent,
    onPrimary = OnGoldDarkInk,
    primaryContainer = SoftChampagneBg,
    onPrimaryContainer = LightGoldAccent,
    secondary = TradeBlueAccent,
    onSecondary = OnGoldDarkInk,
    secondaryContainer = TradeBlueBg,
    onSecondaryContainer = TradeBlueAccent,
    tertiary = PositiveEmerald,
    onTertiary = OnGoldDarkInk,
    tertiaryContainer = PositiveEmeraldBg,
    onTertiaryContainer = PositiveEmerald,
    background = DarkPrimaryBg,
    onBackground = PrimaryInk,
    surface = DarkCardSurface,
    onSurface = PrimaryInk,
    surfaceVariant = DarkElevatedSurface,
    onSurfaceVariant = SecondaryInk,
    error = CriticalCrimsonTone,
    onError = OnGoldDarkInk,
    errorContainer = CriticalCrimsonBg,
    onErrorContainer = CriticalCrimsonTone,
    outline = WarmBorder,
    outlineVariant = WarmBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            activity?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = EnterpriseDarkFintechScheme,
        typography = Typography,
        content = content
    )
}
