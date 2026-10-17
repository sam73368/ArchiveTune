/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Indication used on TV (remote control): Material's focus state layer is a faint 10 % overlay that
 * is nearly invisible from a sofa, so the highlighted item got "lost". This one draws a clear
 * rounded highlight and outline on the focused element, and a short flash when it is selected.
 */
class TvFocusIndication(
    private val accent: Color,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        TvFocusIndicationNode(interactionSource, accent)

    override fun equals(other: Any?): Boolean = other is TvFocusIndication && other.accent == accent

    override fun hashCode(): Int = accent.hashCode()
}

private class TvFocusIndicationNode(
    private val interactionSource: InteractionSource,
    private val accent: Color,
) : Modifier.Node(), DrawModifierNode {
    private val focusAlpha = Animatable(0f)
    private val pressAlpha = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            var focusCount = 0
            var pressCount = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> focusCount++
                    is FocusInteraction.Unfocus -> focusCount = (focusCount - 1).coerceAtLeast(0)
                    is PressInteraction.Press -> pressCount++
                    is PressInteraction.Release, is PressInteraction.Cancel ->
                        pressCount = (pressCount - 1).coerceAtLeast(0)
                }
                val focused = focusCount > 0
                val pressed = pressCount > 0
                launch {
                    focusAlpha.animateTo(if (focused) 1f else 0f, tween(if (focused) 120 else 90))
                    invalidateDraw()
                }
                launch {
                    pressAlpha.animateTo(if (pressed) 1f else 0f, tween(if (pressed) 60 else 160))
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val focus = focusAlpha.value
        val press = pressAlpha.value
        if (focus <= 0f && press <= 0f) return
        val radius = CornerRadius(12.dp.toPx())
        val inset = 1.5.dp.toPx()
        val highlight = 0.16f * focus + 0.14f * press
        drawRoundRect(color = Color.White.copy(alpha = highlight.coerceAtMost(0.3f)), cornerRadius = radius)
        if (focus > 0f && size.width > inset * 2 && size.height > inset * 2) {
            drawRoundRect(
                color = accent.copy(alpha = focus),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = radius,
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}
