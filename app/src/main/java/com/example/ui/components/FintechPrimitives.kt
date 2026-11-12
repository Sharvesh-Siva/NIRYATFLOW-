package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ChampagneAccent
import com.example.ui.theme.CriticalCrimsonBg
import com.example.ui.theme.CriticalCrimsonTone
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkElevatedSurface
import com.example.ui.theme.DarkSecondaryBg
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LightGoldAccent
import com.example.ui.theme.MutedInk
import com.example.ui.theme.PositiveEmerald
import com.example.ui.theme.PositiveEmeraldBg
import com.example.ui.theme.PrimaryInk
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.SoftChampagneBg
import com.example.ui.theme.SpaceGroteskFamily
import com.example.ui.theme.TradeBlueAccent
import com.example.ui.theme.TradeBlueBg
import com.example.ui.theme.WarmBorder
import com.example.ui.theme.WarningAmberBg
import com.example.ui.theme.WarningAmberTone

enum class FintechTone {
    NEUTRAL,
    ACCENT_GOLD,
    TRADE_BLUE,
    POSITIVE,
    WARNING,
    CRITICAL
}

@Composable
fun WarmSurfaceCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    backgroundColor: Color = DarkCardSurface,
    borderColor: Color = WarmBorder,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        shape = shape,
        color = backgroundColor,
        tonalElevation = 2.dp,
        modifier = modifier
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .then(clickMod)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

@Composable
fun StatusPill(
    label: String,
    tone: FintechTone,
    modifier: Modifier = Modifier
) {
    val (bg, fg, borderCol) = when (tone) {
        FintechTone.POSITIVE -> Triple(PositiveEmeraldBg, PositiveEmerald, PositiveEmerald.copy(alpha = 0.35f))
        FintechTone.ACCENT_GOLD -> Triple(SoftChampagneBg, LightGoldAccent, ChampagneAccent.copy(alpha = 0.45f))
        FintechTone.TRADE_BLUE -> Triple(TradeBlueBg, TradeBlueAccent, TradeBlueAccent.copy(alpha = 0.35f))
        FintechTone.WARNING -> Triple(WarningAmberBg, WarningAmberTone, WarningAmberTone.copy(alpha = 0.35f))
        FintechTone.CRITICAL -> Triple(CriticalCrimsonBg, CriticalCrimsonTone, CriticalCrimsonTone.copy(alpha = 0.40f))
        FintechTone.NEUTRAL -> Triple(DarkElevatedSurface, SecondaryInk, WarmBorder)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun statusToneFor(status: String): FintechTone {
    return when (status.uppercase()) {
        "COMPLETED", "VERIFIED", "CAPTURED", "DEMO_CAPTURED", "VALIDATED", "PASS", "RESOLVED" -> FintechTone.POSITIVE
        "SHIPMENT_IN_PROGRESS", "PROCESSING", "FINANCIALLY_ASSESSED", "DOCS_READY" -> FintechTone.TRADE_BLUE
        "IN_PROGRESS", "UPLOADED", "UNDER_REVIEW", "APPROVED", "PAYMENT_INITIATED", "CREATED" -> FintechTone.ACCENT_GOLD
        "PENDING", "PENDING_APPROVAL", "DEMO_PENDING", "MISSING", "OPTIONAL", "MEDIUM", "LOW" -> FintechTone.WARNING
        "FAILED", "DEMO_FAILED", "REJECTED", "REUPLOAD_REQUIRED", "CANCELLED", "DEMO_CANCELLED", "REQUIRES_RECONCILIATION", "HIGH", "CRITICAL", "FAIL" -> FintechTone.CRITICAL
        else -> FintechTone.NEUTRAL
    }
}

@Composable
fun PeriodSelectorBar(
    periods: List<String> = listOf("1D", "1W", "1M", "3M", "1Y", "ALL"),
    selectedPeriod: String,
    onSelectPeriod: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSecondaryBg)
            .border(1.dp, WarmBorder, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        periods.forEach { period ->
            val selected = period == selectedPeriod
            val bg by animateColorAsState(
                targetValue = if (selected) DarkElevatedSurface else Color.Transparent,
                animationSpec = tween(200),
                label = "period_bg"
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .then(
                        if (selected) Modifier.border(1.dp, ChampagneAccent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        else Modifier
                    )
                    .clickable { onSelectPeriod(period) }
                    .padding(vertical = 7.dp)
                    .testTag("period_pill_$period")
            ) {
                Text(
                    text = period,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) LightGoldAccent else SecondaryInk
                )
            }
        }
    }
}

/**
 * Precision Ruler-Tick Indicator for progress, margin, and readiness metrics.
 */
@Composable
fun RulerTickVisual(
    progressFraction: Float,
    modifier: Modifier = Modifier,
    tickCount: Int = 29,
    activeColor: Color = LightGoldAccent,
    accentColor: Color = ChampagneAccent
) {
    val clamped by animateFloatAsState(
        targetValue = progressFraction.coerceIn(0f, 1f),
        animationSpec = tween(350),
        label = "ruler_progress"
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
    ) {
        val spacing = size.width / (tickCount + 1)
        val activeIndex = (clamped * (tickCount - 1)).toInt()
        for (i in 0 until tickCount) {
            val x = spacing * (i + 1)
            val isCenterOrActive = i == activeIndex
            val isMajor = i % 5 == 0
            val tickHeight = when {
                isCenterOrActive -> size.height * 0.92f
                isMajor -> size.height * 0.62f
                else -> size.height * 0.38f
            }
            val topY = (size.height - tickHeight) / 2f
            drawLine(
                color = when {
                    isCenterOrActive -> activeColor
                    i < activeIndex -> accentColor
                    else -> WarmBorder
                },
                start = Offset(x, topY),
                end = Offset(x, topY + tickHeight),
                strokeWidth = if (isCenterOrActive) 4f else 2f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Interactive Enterprise Line + Bar Chart with tap-to-inspect tooltip support.
 */
@Composable
fun MinimalFintechChart(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    lineColor: Color = ChampagneAccent,
    highlightColor: Color = PositiveEmerald,
    unitPrefix: String = "₹",
    onPointSelected: ((Int) -> Unit)? = null
) {
    val safeValues = if (values.isEmpty()) listOf(0.0, 0.0, 0.0, 0.0, 0.0) else values
    val maxVal = (safeValues.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    val defaultPeakIdx = safeValues.indices.maxByOrNull { safeValues[it] } ?: 0
    var selectedIdx by remember(safeValues) { mutableIntStateOf(defaultPeakIdx) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Interactive Tooltip Header
        val activeIdx = selectedIdx.coerceIn(0, safeValues.lastIndex)
        val activeLabel = labels.getOrNull(activeIdx) ?: "Item ${activeIdx + 1}"
        val activeVal = safeValues.getOrElse(activeIdx) { 0.0 }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SELECTED: $activeLabel",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = SecondaryInk
            )
            Text(
                text = "$unitPrefix${String.format("%,.0f", activeVal)}",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = LightGoldAccent
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp)
                .pointerInput(safeValues) {
                    detectTapGestures { offset ->
                        val count = safeValues.size.coerceAtLeast(1)
                        val slot = size.width / count.toFloat()
                        val tapped = (offset.x / slot).toInt().coerceIn(0, count - 1)
                        selectedIdx = tapped
                        onPointSelected?.invoke(tapped)
                    }
                }
        ) {
            val count = safeValues.size
            val slotWidth = size.width / count.toFloat()
            val barWidth = (slotWidth * 0.52f).coerceAtMost(40f)

            val path = Path()
            safeValues.forEachIndexed { idx, v ->
                val ratio = (v / maxVal).toFloat().coerceIn(0.08f, 1f)
                val barH = size.height * ratio * 0.85f
                val cx = slotWidth * idx + slotWidth / 2f
                val topY = size.height - barH

                val isSelected = idx == activeIdx
                drawRoundRect(
                    color = if (isSelected) SoftChampagneBg else DarkElevatedSurface,
                    topLeft = Offset(cx - barWidth / 2f, topY),
                    size = Size(barWidth, barH),
                    cornerRadius = CornerRadius(8f, 8f)
                )

                val lineY = size.height - (size.height * ratio * 0.9f)
                if (idx == 0) {
                    path.moveTo(cx, lineY)
                } else {
                    val prevRatio = (safeValues[idx - 1] / maxVal).toFloat().coerceIn(0.08f, 1f)
                    val prevCx = slotWidth * (idx - 1) + slotWidth / 2f
                    val prevY = size.height - (size.height * prevRatio * 0.9f)
                    val midX = (prevCx + cx) / 2f
                    path.cubicTo(midX, prevY, midX, lineY, cx, lineY)
                }

                if (isSelected) {
                    drawLine(
                        color = ChampagneAccent.copy(alpha = 0.5f),
                        start = Offset(cx, 0f),
                        end = Offset(cx, size.height),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                    drawCircle(
                        color = highlightColor,
                        radius = 6f,
                        center = Offset(cx, lineY)
                    )
                } else {
                    drawCircle(
                        color = lineColor.copy(alpha = 0.7f),
                        radius = 3.5f,
                        center = Offset(cx, lineY)
                    )
                }
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3.2f, cap = StrokeCap.Round)
            )
        }

        if (labels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                labels.take(6).forEachIndexed { idx, lbl ->
                    Text(
                        text = lbl,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        fontWeight = if (idx == activeIdx) FontWeight.Bold else FontWeight.Normal,
                        color = if (idx == activeIdx) LightGoldAccent else MutedInk,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

data class PrimaryNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Required `AppBottomNavigation` component built with Material 3 `NavigationBar` and `NavigationBarItem`
 * implementing the five-destination mobile layout ("Home", "Orders", "Finance", "Documents", "More")
 * with navigation state management and mobile safe-area inset handling (`WindowInsets.navigationBars`).
 */
@Composable
fun AppBottomNavigation(
    tabs: List<PrimaryNavTab>,
    activeTabRoute: String,
    onSelectTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSecondaryBg)
            .testTag("app_bottom_navigation")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(WarmBorder)
        )
        NavigationBar(
            containerColor = DarkSecondaryBg,
            contentColor = PrimaryInk,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets.navigationBars,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEach { tab ->
                val isSelected = activeTabRoute == tab.route
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelectTab(tab.route) },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = LightGoldAccent,
                        selectedTextColor = PrimaryInk,
                        indicatorColor = SoftChampagneBg,
                        unselectedIconColor = SecondaryInk,
                        unselectedTextColor = SecondaryInk
                    ),
                    modifier = Modifier.testTag("nav_${tab.route}")
                )
            }
        }
    }
}

@Composable
fun FloatingMinimalBottomBar(
    tabs: List<PrimaryNavTab>,
    activeTabRoute: String,
    onSelectTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AppBottomNavigation(
        tabs = tabs,
        activeTabRoute = activeTabRoute,
        onSelectTab = onSelectTab,
        modifier = modifier
    )
}

@Composable
fun SectionHeaderRow(
    title: String,
    trailingText: String? = null,
    onTrailingClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(ChampagneAccent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = PrimaryInk
            )
        }
        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelLarge,
                color = if (onTrailingClick != null) LightGoldAccent else SecondaryInk,
                modifier = if (onTrailingClick != null) Modifier.clickable { onTrailingClick() } else Modifier
            )
        }
    }
}

@Composable
fun LargeMetricDisplay(
    label: String,
    primaryValue: String,
    unitOrCurrency: String = "",
    badgeText: String? = null,
    badgeTone: FintechTone = FintechTone.POSITIVE,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryInk
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = primaryValue,
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                color = PrimaryInk
            )
            if (unitOrCurrency.isNotBlank()) {
                Text(
                    text = unitOrCurrency,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.sp,
                    color = ChampagneAccent,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            if (badgeText != null) {
                StatusPill(
                    label = badgeText,
                    tone = badgeTone,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}
