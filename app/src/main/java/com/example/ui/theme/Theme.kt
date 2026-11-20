package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
  primary = EmeraldPrimary,
  onPrimary = Color.White,
  primaryContainer = EmeraldContainer,
  onPrimaryContainer = OnEmeraldContainer,
  secondary = SlateDark,
  onSecondary = Color.White,
  secondaryContainer = SlateContainer,
  onSecondaryContainer = SlateDark,
  tertiary = IndigoAccent,
  onTertiary = Color.White,
  tertiaryContainer = IndigoContainer,
  onTertiaryContainer = SlateDark,
  error = CoralError,
  onError = Color.White,
  errorContainer = CoralContainer,
  onErrorContainer = OnCoralContainer,
  background = SlateSurface,
  onBackground = SlateDark,
  surface = Color.White,
  onSurface = SlateDark,
  surfaceVariant = SlateContainer,
  onSurfaceVariant = SlateMuted,
  outline = SlateBorder,
  outlineVariant = SlateContainer
)

private val DarkColors = darkColorScheme(
  primary = DarkEmeraldPrimary,
  onPrimary = OnEmeraldContainer,
  primaryContainer = DarkEmeraldContainer,
  onPrimaryContainer = EmeraldContainer,
  secondary = SlateContainer,
  onSecondary = SlateDark,
  secondaryContainer = DarkSurfaceVariant,
  onSecondaryContainer = SlateContainer,
  tertiary = IndigoContainer,
  onTertiary = SlateDark,
  tertiaryContainer = Color(0xFF312E81),
  onTertiaryContainer = IndigoContainer,
  error = DarkCoralError,
  onError = OnCoralContainer,
  errorContainer = DarkCoralContainer,
  onErrorContainer = CoralContainer,
  background = DarkBackground,
  onBackground = SlateSurface,
  surface = DarkSurface,
  onSurface = SlateSurface,
  surfaceVariant = DarkSurfaceVariant,
  onSurfaceVariant = SlateBorder,
  outline = SlateMuted,
  outlineVariant = DarkSurfaceVariant
)

val BukuKiraShapes = Shapes(
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(22.dp),
  extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BukuKiraTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColors else LightColors,
    typography = BukuKiraTypography,
    shapes = BukuKiraShapes,
    content = content
  )
}
