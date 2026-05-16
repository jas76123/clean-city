package com.example.cleancity.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cleancity.composeapp.generated.resources.Res
import cleancity.composeapp.generated.resources.golos_text_medium
import cleancity.composeapp.generated.resources.golos_text_regular
import cleancity.composeapp.generated.resources.golos_text_semibold
import cleancity.composeapp.generated.resources.unbounded_bold
import cleancity.composeapp.generated.resources.unbounded_regular
import cleancity.composeapp.generated.resources.unbounded_semibold
import org.jetbrains.compose.resources.Font

@Composable
fun fontDisplay(): FontFamily = FontFamily(
    Font(Res.font.unbounded_regular, FontWeight.Normal),
    Font(Res.font.unbounded_semibold, FontWeight.SemiBold),
    Font(Res.font.unbounded_bold, FontWeight.Bold),
)

@Composable
fun fontBody(): FontFamily = FontFamily(
    Font(Res.font.golos_text_regular, FontWeight.Normal),
    Font(Res.font.golos_text_medium, FontWeight.Medium),
    Font(Res.font.golos_text_semibold, FontWeight.SemiBold),
)

@Composable
fun cleanCityTypography(): Typography {
    val display = fontDisplay()
    val body = fontBody()
    return Typography(
        displayMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
        headlineMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 31.sp),
        headlineSmall = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
        titleLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        bodyLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.65.sp),
        labelMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp),
        labelSmall = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.55.sp),
    )
}
