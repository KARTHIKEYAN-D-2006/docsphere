package com.docsphere.ui.theme

import androidx.compose.ui.graphics.Color

// =========================================================================
//  OFFICIAL GOOGLE FILES (FILES BY GOOGLE) MATERIAL 3 COLOR PALETTE
// =========================================================================

// --- Google Brand Primary Colors ---
val GoogleBlue = Color(0xFF1A73E8)
val GoogleBlueDark = Color(0xFF8AB4F8)
val GoogleBlueContainer = Color(0xFFE8F0FE)
val GoogleBlueContainerDark = Color(0xFF174EA6)

val GoogleRed = Color(0xFFD93025)
val GoogleRedDark = Color(0xFFF28B82)
val GoogleRedContainer = Color(0xFFFCE8E6)
val GoogleRedContainerDark = Color(0xFF8C1D18)

val GoogleYellow = Color(0xFFF9AB00)
val GoogleYellowDark = Color(0xFFFDD663)
val GoogleYellowContainer = Color(0xFFFEF7E0)
val GoogleYellowContainerDark = Color(0xFF544400)

val GoogleGreen = Color(0xFF1E8E3E)
val GoogleGreenDark = Color(0xFF81C995)
val GoogleGreenContainer = Color(0xFFE6F4EA)
val GoogleGreenContainerDark = Color(0xFF137333)

val GooglePurple = Color(0xFF9334E6)
val GooglePurpleDark = Color(0xFFC58AF9)
val GooglePurpleContainer = Color(0xFFF3E8FD)
val GooglePurpleContainerDark = Color(0xFF5B2186)

// --- Google Files Surfaces & Backgrounds ---
val GoogleSurfaceLight = Color(0xFFFFFFFF)
val GoogleSurfaceVariantLight = Color(0xFFF1F3F4)
val GoogleBackgroundLight = Color(0xFFF8F9FA)

val GoogleSurfaceDark = Color(0xFF1F1F1F)
val GoogleSurfaceVariantDark = Color(0xFF28292A)
val GoogleBackgroundDark = Color(0xFF121212)
val GoogleSurfaceElevation = Color(0xFF2D2E30)

// --- Google Typography Colors ---
val GoogleTextPrimary = Color(0xFFE8EAED)
val GoogleTextSecondary = Color(0xFF9AA0A6)
val GoogleTextMuted = Color(0xFF80868B)
val GoogleDivider = Color(0xFF3C4043)

// =========================================================================
//  BACKWARD COMPATIBLE TOKENS (Harmonized with Google Files)
// =========================================================================

val ElectricBlue = GoogleBlue
val ElectricBlueDim = Color(0xFF1565C0)
val ElectricBlueGlow = GoogleBlueDark
val ElectricBlueLight = Color(0xFF8AB4F8)
val NeonCyan = Color(0xFF00D2FF)

val AmberGold = GoogleYellow
val AmberLight = GoogleYellowDark
val AmberDim = Color(0xFFE37400)

val TealEmerald = GoogleGreen
val TealLight = GoogleGreenDark
val TealDim = Color(0xFF137333)

val SuccessGreen = GoogleGreen
val WarningOrange = GoogleYellow
val ErrorRed = GoogleRed

// Surfaces
val NavyDeep = GoogleBackgroundDark
val NavyDark = GoogleSurfaceDark
val NavyMedium = GoogleSurfaceVariantDark
val NavySurface = GoogleSurfaceElevation

val GlassSurface = Color(0x1F3C4043)
val GlassBorder = Color(0x335F6368)
val GlassOverlay = Color(0x1A202124)

// Text
val TextPrimary = GoogleTextPrimary
val TextSecondary = GoogleTextSecondary
val TextMuted = GoogleTextMuted
val TextOnAccent = Color(0xFF000000)

// File Type Colors (Google Workspace standard)
val PdfColor = GoogleRed
val DocColor = GoogleBlue
val ExcelColor = GoogleGreen
val PptColor = GoogleYellow
val ImageColor = GooglePurple
val AudioColor = Color(0xFFFF6D00)
val VideoColor = Color(0xFF00B0FF)
val TextColor = Color(0xFF00BFA5)

// Gradient Endpoints (Clean Google dark surface)
val GradientStart = GoogleBackgroundDark
val GradientMid = GoogleSurfaceDark
val GradientEnd = GoogleBackgroundDark