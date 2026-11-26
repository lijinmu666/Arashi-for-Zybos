package com.lijinmu.arashi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.lijinmu.arashi.R
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles

private val InterFontFamily = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
    Font(R.font.inter_700, FontWeight.Bold),
)

@Composable
internal fun arashiTextStyles(useInter: Boolean): TextStyles {
    val defaults = MiuixTheme.textStyles
    return remember(defaults, useInter) {
        val family = if (useInter) InterFontFamily else FontFamily.Default
        defaults.copy(
            main = defaults.main.copy(fontFamily = family),
            paragraph = defaults.paragraph.copy(fontFamily = family),
            body1 = defaults.body1.copy(fontFamily = family),
            body2 = defaults.body2.copy(fontFamily = family),
            button = defaults.button.copy(fontFamily = family),
            footnote1 = defaults.footnote1.copy(fontFamily = family),
            footnote2 = defaults.footnote2.copy(fontFamily = family),
            headline1 = defaults.headline1.copy(fontFamily = family),
            headline2 = defaults.headline2.copy(fontFamily = family),
            subtitle = defaults.subtitle.copy(fontFamily = family),
            title1 = defaults.title1.copy(fontFamily = family),
            title2 = defaults.title2.copy(fontFamily = family),
            title3 = defaults.title3.copy(fontFamily = family),
            title4 = defaults.title4.copy(fontFamily = family),
        )
    }
}
