package com.threeDLedger.ui.theme

import androidx.compose.ui.graphics.Color

// ── Ultra-Clean High-Contrast Palette for Seniors & Older Users ──────────────
// Light theme: Clean, high-contrast canvas with vibrant emerald, warm eye-catching gold, and crisp slate-black text
val md_theme_light_primary            = Color(0xFF027A55)  // Vibrant rich emerald (high contrast)
val md_theme_light_onPrimary          = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer   = Color(0xFFD1FAE5)  // Bright mint chip container
val md_theme_light_onPrimaryContainer = Color(0xFF003824)

val md_theme_light_secondary          = Color(0xFFD97706)  // Radiant warm gold / amber
val md_theme_light_onSecondary        = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFFEF3C7)  // Soft warm gold badge
val md_theme_light_onSecondaryContainer = Color(0xFF451A03)

val md_theme_light_tertiary           = Color(0xFF0284C7)  // Electric sapphire accent
val md_theme_light_onTertiary         = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer  = Color(0xFFE0F2FE)
val md_theme_light_onTertiaryContainer = Color(0xFF034A6E)

val md_theme_light_error              = Color(0xFFDC2626)  // Vibrant crimson coral
val md_theme_light_errorContainer     = Color(0xFFFEE2E2)
val md_theme_light_onError            = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer   = Color(0xFF7F1D1D)

val md_theme_light_background         = Color(0xFFF8FAFC)  // Crisp slate-white canvas
val md_theme_light_onBackground       = Color(0xFF0F172A)  // Maximum legibility slate-black
val md_theme_light_surface            = Color(0xFFFFFFFF)  // Pure white card canvas
val md_theme_light_onSurface          = Color(0xFF0F172A)  // Deep slate-black (high contrast for numbers & labels)
val md_theme_light_surfaceVariant     = Color(0xFFF1F5F9)  // Clean subtle card separator
val md_theme_light_onSurfaceVariant   = Color(0xFF334155)  // Highly legible dark slate (not pale grey!)
val md_theme_light_outline            = Color(0xFFCBD5E1)  // Crisp defining borders
val md_theme_light_outlineVariant     = Color(0xFFE2E8F0)

// ── Dark theme: Luminous, high-contrast dark canvas with glowing mint & pure white text ──
val md_theme_dark_primary             = Color(0xFF10B981)  // Luminous mint emerald
val md_theme_dark_onPrimary           = Color(0xFF022C22)
val md_theme_dark_primaryContainer    = Color(0xFF064E3B)
val md_theme_dark_onPrimaryContainer  = Color(0xFFA7F3D0)

val md_theme_dark_secondary           = Color(0xFFFBBF24)  // Radiant eye-catching warm gold
val md_theme_dark_onSecondary         = Color(0xFF451A03)
val md_theme_dark_secondaryContainer  = Color(0xFF78350F)
val md_theme_dark_onSecondaryContainer = Color(0xFFFEF3C7)

val md_theme_dark_tertiary            = Color(0xFF38BDF8)  // Sky blue
val md_theme_dark_onTertiary          = Color(0xFF082F49)
val md_theme_dark_tertiaryContainer   = Color(0xFF0369A1)
val md_theme_dark_onTertiaryContainer = Color(0xFFBAE6FD)

val md_theme_dark_error               = Color(0xFFF87171)
val md_theme_dark_errorContainer      = Color(0xFF7F1D1D)
val md_theme_dark_onError             = Color(0xFF450A0A)
val md_theme_dark_onErrorContainer    = Color(0xFFFECACA)

val md_theme_dark_background          = Color(0xFF0F172A)  // Deep slate navy canvas
val md_theme_dark_onBackground        = Color(0xFFFFFFFF)  // Brilliant pure white text
val md_theme_dark_surface             = Color(0xFF1E293B)  // Elevated slate surface
val md_theme_dark_onSurface           = Color(0xFFFFFFFF)  // Pure white for numbers & amounts
val md_theme_dark_surfaceVariant      = Color(0xFF334155)  // Distinct elevated surface
val md_theme_dark_onSurfaceVariant    = Color(0xFFE2E8F0)  // Bright readable off-white
val md_theme_dark_outline             = Color(0xFF475569)  // Crisp borders
val md_theme_dark_outlineVariant      = Color(0xFF334155)

// ── Semantic Color Tokens for Screens ─────────────────────────────────────────
val EmeraldPrimary      = Color(0xFF027A55)
val EmeraldLight        = Color(0xFFD1FAE5)
val EmeraldDark         = Color(0xFF003824)
val EmeraldMedium       = Color(0xFF059669)
val EmeraldSoftBg       = Color(0xFFF0FDF4)

val GoldAccent          = Color(0xFFD97706)
val GoldContainer       = Color(0xFFFEF3C7)
val GoldDark            = Color(0xFF92400E)

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
val KeypadSubmitBg       = Color(0xFF027A55)  // Rich Emerald Submit
val KeypadFocusRing      = Color(0xFF10B981)  // Glowing focus ring


