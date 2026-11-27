package com.threeDLedger.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme

// ── Elder-Friendly High-Contrast Palette ───────────────────────────────────
// LIGHT: Warm parchment canvas · deep ink text · vibrant emerald · rich gold
// Philosophy: zero eye-strain glare, max readability at arm's length
val md_theme_light_primary            = Color(0xFF065F46)  // Deep forest emerald — maximum WCAG AA contrast
val md_theme_light_onPrimary          = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer   = Color(0xFFBBF7D0)  // Soft mint chip (clear but not blinding)
val md_theme_light_onPrimaryContainer = Color(0xFF002B1B)

val md_theme_light_secondary          = Color(0xFFB45309)  // Rich deep amber — warm gold ink
val md_theme_light_onSecondary        = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFFDE68A)  // Honey-warm badge container
val md_theme_light_onSecondaryContainer = Color(0xFF3B1A00)

val md_theme_light_tertiary           = Color(0xFF1D4ED8)  // Bold sapphire blue accent
val md_theme_light_onTertiary         = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer  = Color(0xFFDBEAFE)
val md_theme_light_onTertiaryContainer = Color(0xFF1E3A8A)

val md_theme_light_error              = Color(0xFFB91C1C)  // Deep crimson — legible without harshness
val md_theme_light_errorContainer     = Color(0xFFFEE2E2)
val md_theme_light_onError            = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer   = Color(0xFF7F1D1D)

// Canvas: warm parchment — far less glare than pure white for older eyes
val md_theme_light_background         = Color(0xFFF5EDD8)  // Rich warm parchment (newspaper-toned)
val md_theme_light_onBackground       = Color(0xFF0D1117)  // Near-black ink for maximum readability
val md_theme_light_surface            = Color(0xFFFFFBF0)  // Cream-white card face
val md_theme_light_onSurface          = Color(0xFF0D1117)  // Near-black ink
val md_theme_light_surfaceVariant     = Color(0xFFEAE0C8)  // Warm separator (clearly distinct on parchment)
val md_theme_light_onSurfaceVariant   = Color(0xFF1E293B)  // Dark readable slate (never pale!)
val md_theme_light_outline            = Color(0xFFB8A98A)  // Warm tan border — visible but soft
val md_theme_light_outlineVariant     = Color(0xFFD4C9AE)

// ── DARK: Midnight ink · glowing emerald · radiant gold ───────────────────
val md_theme_dark_primary             = Color(0xFF34D399)  // Bright glowing mint — max contrast on dark
val md_theme_dark_onPrimary           = Color(0xFF022C22)
val md_theme_dark_primaryContainer    = Color(0xFF064E3B)
val md_theme_dark_onPrimaryContainer  = Color(0xFFBBF7D0)

val md_theme_dark_secondary           = Color(0xFFFCD34D)  // Bright warm gold
val md_theme_dark_onSecondary         = Color(0xFF451A03)
val md_theme_dark_secondaryContainer  = Color(0xFF78350F)
val md_theme_dark_onSecondaryContainer = Color(0xFFFEF3C7)

val md_theme_dark_tertiary            = Color(0xFF60A5FA)  // Bright sky blue
val md_theme_dark_onTertiary          = Color(0xFF082F49)
val md_theme_dark_tertiaryContainer   = Color(0xFF1D4ED8)
val md_theme_dark_onTertiaryContainer = Color(0xFFDBEAFE)

val md_theme_dark_error               = Color(0xFFFCA5A5)
val md_theme_dark_errorContainer      = Color(0xFF7F1D1D)
val md_theme_dark_onError             = Color(0xFF450A0A)
val md_theme_dark_onErrorContainer    = Color(0xFFFECACA)

val md_theme_dark_background          = Color(0xFF080F1A)  // Deep midnight — no blue-grey shimmer
val md_theme_dark_onBackground        = Color(0xFFF1F5F9)  // Bright off-white (less harsh than pure white)
val md_theme_dark_surface             = Color(0xFF111827)  // Rich dark card
val md_theme_dark_onSurface           = Color(0xFFF1F5F9)  // Bright off-white numbers
val md_theme_dark_surfaceVariant      = Color(0xFF1E2D40)  // Elevated dark surface
val md_theme_dark_onSurfaceVariant    = Color(0xFFCBD5E1)  // Warm readable light grey
val md_theme_dark_outline             = Color(0xFF374151)  // Crisp dark borders
val md_theme_dark_outlineVariant      = Color(0xFF1F2937)

// ── Semantic Color Tokens ──────────────────────────────────────────────────
val EmeraldPrimary      = Color(0xFF065F46)   // Deep forest emerald
val EmeraldLight        = Color(0xFFBBF7D0)   // Mint chip background
val EmeraldDark         = Color(0xFF022C22)
val EmeraldMedium       = Color(0xFF047857)
val EmeraldSoftBg       = Color(0xFFF0FDF4)

val GoldAccent          = Color(0xFFB45309)   // Rich amber gold ink
val GoldContainer       = Color(0xFFFDE68A)   // Honey badge container
val GoldDark            = Color(0xFF78350F)

// Payout / Winner Semantic Chips
val WinExactRed         = Color(0xFFDC2626)  // ပေါက်သီး (Direct Match)
val WinExactBg          = Color(0xFFFEE2E2)
val WinPermGold         = Color(0xFFD97706)  // ပတ်လည် (Permutation)
val WinPermBg           = Color(0xFFFEF3C7)
val WinNearBlue         = Color(0xFF0284C7)  // ကပ်သီး (Near Number)
val WinNearBg           = Color(0xFFE0F2FE)

val CardBorderSubtle    = Color(0xFFCBD5E1)
val CardBorderDark      = Color(0xFF334155)

// GitHub Update Callout Semantic Colors
val UpdateBannerBg      = Color(0xFFFEF3C7)
val UpdateBannerBorder  = Color(0xFFF59E0B)
val UpdateBannerText    = Color(0xFF92400E)
val UpdateBannerAccent  = Color(0xFFD97706)

// ── Tactile Pro Keypad & Physical Button Color Tokens ─────────────────────────
val KeypadDigitBgLight   = Color(0xFFFFFFFF)  // Crisp physical key face
val KeypadDigitBgDark    = Color(0xFF1E293B)
val KeypadDigitText      = Color(0xFF0F172A)  // Deep obsidian slate
val KeypadBorderLight    = Color(0xFFCBD5E1)  // 3D edge bevel
val KeypadShadowLight    = Color(0xFF94A3B8)  // Bottom key drop bevel

val KeypadActionEmerald  = Color(0xFF059669)  // R (Permutation) key
val KeypadActionTeal     = Color(0xFF0D9488)  // Triples (ထွိုင်) key
val KeypadBackspaceRed   = Color(0xFFDC2626)  // Backspace key
val KeypadClearAmber     = Color(0xFFD97706)  // ရှင်း Clear key
val KeypadSubmitGold     = Color(0xFFEAB308)  // OK / Enter key
val KeypadSubmitBg       = Color(0xFF047857)  // Bright Emerald Submit
val KeypadFocusRing      = Color(0xFF10B981)  // Glowing focus ring

// ── Dark-Aware Brand Accent Helpers (Elder Legibility) ───────────────────
@Composable
fun themeEmerald(): Color = if (isSystemInDarkTheme()) Color(0xFF34D399) else Color(0xFF065F46)

@Composable
fun themeGold(): Color = if (isSystemInDarkTheme()) Color(0xFFFCD34D) else Color(0xFFB45309)

@Composable
fun themeGoldDeep(): Color = if (isSystemInDarkTheme()) Color(0xFFFCD34D) else Color(0xFF78350F)

@Composable
fun themeWinRed(): Color = if (isSystemInDarkTheme()) Color(0xFFFCA5A5) else Color(0xFFB91C1C)

// ── Gradient & Surface Tokens ─────────────────────────────────────────────
val PrimaryGradientLight = listOf(Color(0xFF065F46), Color(0xFF047857), Color(0xFF059669))
val PrimaryGradientDark  = listOf(Color(0xFF059669), Color(0xFF10B981), Color(0xFF34D399))
val GoldGradientLight    = listOf(Color(0xFF92400E), Color(0xFFB45309), Color(0xFFD97706))
val GoldGradientDark     = listOf(Color(0xFFD97706), Color(0xFFF59E0B), Color(0xFFFCD34D))

@Composable
fun themePrimaryGradient(): List<Color> =
    if (isSystemInDarkTheme()) PrimaryGradientDark else PrimaryGradientLight

@Composable
fun themeGoldGradient(): List<Color> =
    if (isSystemInDarkTheme()) GoldGradientDark else GoldGradientLight

@Composable
fun themeSurfaceCardGradient(): List<Color> =
    if (isSystemInDarkTheme()) {
        listOf(Color(0xFF111827), Color(0xFF0D1520))
    } else {
        listOf(Color(0xFFFFFBF0), Color(0xFFF5EDD8))
    }

@Composable
fun themeTableRowOdd(): Color =
    if (isSystemInDarkTheme()) Color(0xFF111827) else Color(0xFFFFFBF0)

@Composable
fun themeTableRowEven(): Color =
    if (isSystemInDarkTheme()) Color(0xFF0D1520) else Color(0xFFEEE5D0)

// Dominant button border — deep emerald in light mode, glowing mint in dark
@Composable
fun themeButtonBorder(): Color = if (isSystemInDarkTheme()) Color(0xFF34D399) else Color(0xFF065F46)

// Dominant table-cell border — clearly visible warm-tan separators for data tables
@Composable
fun themeTableBorder(): Color = if (isSystemInDarkTheme()) Color(0xFF374151) else Color(0xFFA89470)

// Dark-aware container/border tints for banners, badges & callouts.
// Light mode keeps the soft pastel containers; dark mode swaps to the deep
// Material containers so white-on-dark text/accents stay fully legible.
// Parchment-tuned container & border helpers
@Composable
fun themeEmeraldContainer(): Color = if (isSystemInDarkTheme()) Color(0xFF064E3B) else Color(0xFFECFDF5)

@Composable
fun themeAmberContainer(): Color = if (isSystemInDarkTheme()) Color(0xFF78350F) else Color(0xFFFEF3C7)

@Composable
fun themeCreamContainer(): Color = if (isSystemInDarkTheme()) Color(0xFF422006) else Color(0xFFFFFBEB)

@Composable
fun themeEmeraldBorder(): Color = if (isSystemInDarkTheme()) Color(0xFF34D399) else Color(0xFF4ADE80)

@Composable
fun themeAmberBorder(): Color = if (isSystemInDarkTheme()) Color(0xFFFCD34D) else Color(0xFFD97706)

// Surface card gradient — parchment cream in light, deep ink in dark
@Composable
fun themeSurfaceCardGradientFn(): List<Color> =
    if (isSystemInDarkTheme()) listOf(Color(0xFF111827), Color(0xFF0D1520))
    else listOf(Color(0xFFFFFBF0), Color(0xFFF5EDD8))


