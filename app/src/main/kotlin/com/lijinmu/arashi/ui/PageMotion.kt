// Motion parameters adapted from compose-miuix-ui/miuix (Apache-2.0).
// response = 0.8, damping = 0.95; matches InstallerX Revived's MiuixDefault.
package com.lijinmu.arashi.ui

import androidx.compose.animation.core.Easing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

internal object PageMotion {
    const val DurationMillis = 500
    private val omega = 2.0 * PI / 0.8
    private val decay = -0.95 * omega
    private val frequency = omega * sqrt(1.0 - 0.95 * 0.95)

    val Easing: Easing = Easing { fraction ->
        when {
            fraction <= 0f -> 0f
            fraction >= 1f -> 1f
            else -> {
                val t = fraction.toDouble()
                (1.0 + exp(decay * t) *
                    (-cos(frequency * t) + decay / frequency * sin(frequency * t))).toFloat()
            }
        }
    }
}
