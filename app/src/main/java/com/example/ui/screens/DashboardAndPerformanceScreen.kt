package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ExportOrder
import com.example.data.sample.WorkspaceMode
import com.example.domain.FinancialCalculationEngine
import com.example.ui.components.EnterpriseVisualizationsSuite
import com.example.ui.components.FintechTone
import com.example.ui.components.InteractiveTradeRouteWorldMap
import com.example.ui.components.LargeMetricDisplay
import com.example.ui.components.MinimalFintechChart
import com.example.ui.components.PeriodSelectorBar
import com.example.ui.components.RulerTickVisual
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.StatusPill
import com.example.ui.components.TradeRiskEngine
import com.example.ui.components.TradeRiskIntelligencePanel
import com.example.ui.components.WarmSurfaceCard
import com.example.ui.components.statusToneFor
import com.example.ui.theme.ChampagneAccent
import com.example.ui.theme.CriticalCrimsonTone
import com.example.ui.theme.IvoryBackground
import com.example.ui.theme.IvorySecondaryBg
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LightGoldAccent
import com.example.ui.theme.MutedInk
import com.example.ui.theme.OnGoldDarkInk
import com.example.ui.theme.PositiveEmerald
import com.example.ui.theme.PrimaryInk
import com.example.ui.theme.PureWhiteSurface
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.SoftChampagneBg
import com.example.ui.theme.SpaceGroteskFamily
import com.example.ui.theme.WarmBorder
import com.example.ui.theme.WarningAmberTone
import com.example.ui.viewmodel.AiTaskState
import com.example.ui.viewmodel.NiryatPortfolioState
import java.text.SimpleDateFormat
import java.util.Locale

private val SAMPLE_NL_QUERIES = listOf(
    "Which of my active export orders have incomplete documentation?",
    "Which orders currently have the lowest estimated profit margins?",
    "Show all payments awaiting capture.",
    "Which destination countries generated the highest revenue this quarter?"
)

private val BUSINESS_REPORT_TYPES = listOf(
    "Export Order Report",
    "Financial Performance Report",
    "Outstanding Payment Report",
    "Document Readiness Report",
    "Export Destination Report",
    "Order Completion Report"
)

/**
 * Screen 1 — Redesigned Minimalist Export Command Centre
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    portfolio: NiryatPortfolioState,
    userEmail: String = "",
    aiInsightsState: AiTaskState<String>,
    nlQueryState: AiTaskState<String> = AiTaskState.Idle,
    onSelectOrder: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onRequestAiInsights: () -> Unit,
    onAskNaturalLanguageQuery: (String) -> Unit = {},
    onSwitchWorkspace: (WorkspaceMode) -> Unit = {},
    onResetSampleWorkspace: () -> Unit = {}
) {
    var selectedPeriod by rememberSaveable { mutableStateOf("1M") }
    var chartSeriesMode by rememberSaveable { mutableStateOf("INTAKE") } // INTAKE or CAPTURED
    var customQueryText by rememberSaveable { mutableStateOf("") }
    val periodAnalytics = remember(
        portfolio.orders,
        portfolio.assessments,
        portfolio.documents,
        portfolio.payments,
        selectedPeriod
    ) {
        FinancialCalculationEngine.calculatePeriodAnalytics(
            orders = portfolio.orders,
            assessments = portfolio.assessments,
            documents = portfolio.documents,
            payments = portfolio.payments,
            periodCode = selectedPeriod
        )
    }
    val metrics = periodAnalytics.metrics
    val isSample = portfolio.workspaceMode == WorkspaceMode.SAMPLE_BUSINESS
    val riskAlerts = remember(portfolio.orders, portfolio.assessments, portfolio.documents, portfolio.payments) {
        TradeRiskEngine.evaluatePortfolioRisks(
            orders = portfolio.orders,
            assessments = portfolio.assessments,
            documents = portfolio.documents,
            payments = portfolio.payments
        )
    }
    val exporterFirstName = remember(userEmail, isSample) {
        if (isSample) {
            "Chennai Operations Desk"
        } else {
            userEmail.substringBefore("@").replaceFirstChar { it.uppercase() }.ifBlank { "Exporter" }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header: Wordmark, Business Workspace Identity, Notification & Profile Access
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSwitchWorkspace(WorkspaceMode.SAMPLE_BUSINESS) }
                        .testTag("dashboard_emblem_sample_entry")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SoftChampagneBg)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "NiryatFlow AI Emblem",
                            tint = ChampagneAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "NiryatFlow AI",
                            style = MaterialTheme.typography.titleMedium,
                            color = PrimaryInk
                        )
                        Text(
                            text = "${portfolio.businessProfile.businessName} • $exporterFirstName",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val actionCount = portfolio.selectedDigitalTwin?.outstandingActions?.size ?: 0
                    Surface(
                        shape = CircleShape,
                        color = PureWhiteSurface,
                        modifier = Modifier
                            .size(38.dp)
                            .border(1.dp, WarmBorder, CircleShape)
                            .clickable { onNavigate("digital_twin") }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.NotificationsNone,
                                contentDescription = "Notifications",
                                tint = PrimaryInk,
                                modifier = Modifier.size(19.dp)
                            )
                            if (actionCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(7.dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(CriticalCrimsonTone)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = IvorySecondaryBg,
                        modifier = Modifier
                            .size(38.dp)
                            .border(1.dp, WarmBorder, RoundedCornerShape(12.dp))
                            .clickable { onNavigate("more") }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PersonOutline,
                                contentDescription = "Profile",
                                tint = PrimaryInk,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dual Workspace Banner (Production vs Sample Business Workspace)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isSample) SoftChampagneBg else PureWhiteSurface,
                border = BorderStroke(1.dp, if (isSample) ChampagneAccent else WarmBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isSample) {
                                "SAMPLE WORKSPACE • ISOLATED FICTIONAL DATA"
                            } else {
                                "PRODUCTION WORKSPACE • CLOUD FIRESTORE"
                            },
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSample) ChampagneAccent else PositiveEmerald
                        )
                        Text(
                            text = "${portfolio.businessProfile.businessName} (Role: ${portfolio.businessProfile.role} • Base: ${portfolio.businessProfile.reportingCurrency})",
                            style = MaterialTheme.typography.bodySmall,
                            color = PrimaryInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isSample) {
                            OutlinedButton(
                                onClick = onResetSampleWorkspace,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, WarmBorder),
                                modifier = Modifier.testTag("dashboard_reset_sample_btn")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset Sample", modifier = Modifier.size(14.dp), tint = PrimaryInk)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset", fontSize = 11.sp, color = PrimaryInk)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                val nextMode = if (isSample) WorkspaceMode.PRODUCTION else WorkspaceMode.SAMPLE_BUSINESS
                                onSwitchWorkspace(nextMode)
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, PrimaryInk),
                            modifier = Modifier.testTag("switch_workspace_btn")
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp), tint = PrimaryInk)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isSample) "Production" else "Sample MSME",
                                fontSize = 11.sp,
                                color = PrimaryInk
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            PeriodSelectorBar(
                selectedPeriod = selectedPeriod,
                onSelectPeriod = { selectedPeriod = it }
            )
        }

        // Main Section: Prominent Portfolio Summary (Typographic Hierarchy)
        item {
            WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Export Portfolio Value ($selectedPeriod • ${portfolio.businessProfile.reportingCurrency})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryInk
                        )
                        Text(
                            text = periodAnalytics.periodSummaryText,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = ChampagneAccent,
                            modifier = Modifier.testTag("dashboard_period_summary_text")
                        )
                    }
                    StatusPill(
                        label = "${metrics.totalOrders} Orders • ${metrics.activeOrders} Active",
                        tone = FintechTone.ACCENT_GOLD
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "₹${FinancialCalculationEngine.formatInrShort(metrics.totalOrderValueInr)}",
                        fontFamily = SpaceGroteskFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp,
                        lineHeight = 40.sp,
                        color = PrimaryInk,
                        modifier = Modifier.testTag("dashboard_kpi_total_order_value")
                    )
                    StatusPill(
                        label = "Avg Margin ${metrics.averageProfitMarginPercent}%",
                        tone = if (metrics.averageProfitMarginPercent >= 12.0) FintechTone.POSITIVE else FintechTone.WARNING,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                val growthBadgeText = periodAnalytics.orderValueGrowthPercent?.let { pct ->
                    val sign = if (pct >= 0.0) "+" else ""
                    "vs Prev $selectedPeriod: $sign${pct}% (₹${FinancialCalculationEngine.formatInrShort(periodAnalytics.previousPeriodOrderValueInr)})"
                } ?: if (selectedPeriod == "ALL") {
                    "All-Time Cumulative Export Portfolio"
                } else {
                    "vs Prev $selectedPeriod: New Period Activity (Prev ₹${FinancialCalculationEngine.formatInrShort(periodAnalytics.previousPeriodOrderValueInr)})"
                }
                Text(
                    text = growthBadgeText,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = SecondaryInk,
                    modifier = Modifier.testTag("dashboard_period_growth_badge")
                )

                Spacer(modifier = Modifier.height(12.dp))
                RulerTickVisual(
                    progressFraction = (metrics.paymentCompletionRatePercent / 100.0).toFloat()
                )
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = WarmBorder)
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CompactFinancialColumn(
                        label = "Collected ($selectedPeriod)",
                        value = "₹${FinancialCalculationEngine.formatInrShort(metrics.capturedPaymentValueInr)}",
                        subtext = "${metrics.paymentCompletionRatePercent}% captured",
                        valueColor = PositiveEmerald
                    )
                    CompactFinancialColumn(
                        label = "Outstanding",
                        value = "₹${FinancialCalculationEngine.formatInrShort(metrics.outstandingPaymentValueInr)}",
                        subtext = "Receivables",
                        valueColor = WarningAmberTone
                    )
                    CompactFinancialColumn(
                        label = "Est. Profit ($selectedPeriod)",
                        value = "₹${FinancialCalculationEngine.formatInrShort(metrics.totalEstimatedProfitInr)}",
                        subtext = "Net INR",
                        valueColor = PrimaryInk
                    )
                }
            }
        }

        // Visualisation 1: Live Period-Aggregated Revenue and Collections Trend
        item {
            WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeaderRow(
                    title = "Revenue & Collections Trend ($selectedPeriod)",
                    trailingText = "Doc Readiness: ${metrics.documentReadinessRatePercent}%"
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = chartSeriesMode == "INTAKE",
                        onClick = { chartSeriesMode = "INTAKE" },
                        label = {
                            Text(
                                "Order Intake (₹${FinancialCalculationEngine.formatInrShort(metrics.totalOrderValueInr)})",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp
                            )
                        },
                        modifier = Modifier.testTag("chart_mode_intake")
                    )
                    FilterChip(
                        selected = chartSeriesMode == "CAPTURED",
                        onClick = { chartSeriesMode = "CAPTURED" },
                        label = {
                            Text(
                                "Captured Receipts (₹${FinancialCalculationEngine.formatInrShort(metrics.capturedPaymentValueInr)})",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp
                            )
                        },
                        modifier = Modifier.testTag("chart_mode_captured")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val activeSeries = if (chartSeriesMode == "CAPTURED") {
                    periodAnalytics.chartCapturedPaymentsInr
                } else {
                    periodAnalytics.chartOrderIntakeInr
                }

                MinimalFintechChart(
                    values = if (activeSeries.isEmpty()) listOf(0.0, 0.0, 0.0, 0.0) else activeSeries,
                    labels = if (periodAnalytics.chartBucketLabels.isEmpty()) listOf("P1", "P2", "P3", "P4") else periodAnalytics.chartBucketLabels
                )
            }
        }

        // Visualisation 2: Export Destination Breakdown & Order Lifecycle Distribution
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WarmSurfaceCard(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Destinations",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryInk
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (metrics.countryDistribution.isEmpty()) {
                        Text(
                            text = "No destination records yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedInk
                        )
                    } else {
                        metrics.countryDistribution.entries.take(4).forEach { (country, inrVal) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = country,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SecondaryInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "₹${FinancialCalculationEngine.formatInrShort(inrVal)}",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = PrimaryInk
                                )
                            }
                        }
                    }
                }

                WarmSurfaceCard(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lifecycle Split",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryInk
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LifecycleMiniRow("Active", metrics.activeOrders, ChampagneAccent)
                    LifecycleMiniRow("Completed", metrics.completedOrders, PositiveEmerald)
                    LifecycleMiniRow("Risk Alerts", riskAlerts.size, WarningAmberTone)
                }
            }
        }

        // Objective 3 & 5: Interactive Global Trade Route Map
        item {
            InteractiveTradeRouteWorldMap(
                portfolio = portfolio,
                onSelectOrderAndDrillDown = { orderId ->
                    onSelectOrder(orderId)
                    onNavigate("digital_twin")
                }
            )
        }

        // Objective 5: Interactive Trade Analytics Suite (Charts 1-9)
        item {
            EnterpriseVisualizationsSuite(
                portfolio = portfolio,
                selectedPeriod = selectedPeriod,
                onSelectOrderAndNavigate = { orderId, targetRoute ->
                    onSelectOrder(orderId)
                    onNavigate(targetRoute)
                }
            )
        }

        // Objective 6: Connected Trade Risk Intelligence Engine Panel
        item {
            TradeRiskIntelligencePanel(
                alerts = riskAlerts,
                onInspectAlert = { alert ->
                    onSelectOrder(alert.affectedOrderId)
                    onNavigate(alert.targetRoute)
                }
            )
        }

        // Natural-Language Business Query Assistant (Section 9)
        item {
            WarmSurfaceCard(
                backgroundColor = PureWhiteSurface,
                borderColor = ChampagneAccent.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = ChampagneAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Natural-Language Business Query",
                                style = MaterialTheme.typography.titleMedium,
                                color = PrimaryInk
                            )
                            Text(
                                text = "Grounded in authorized ${if (isSample) "Sample" else "Production"} records",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryInk
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onRequestAiInsights,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("generate_portfolio_insights_btn")
                    ) {
                        Text("Portfolio Brief", color = PrimaryInk, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SAMPLE_NL_QUERIES.forEachIndexed { idx, presetQuery ->
                        FilterChip(
                            selected = customQueryText == presetQuery,
                            onClick = {
                                customQueryText = presetQuery
                                onAskNaturalLanguageQuery(presetQuery)
                            },
                            label = {
                                Text(
                                    text = presetQuery,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag("preset_nl_query_$idx")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MinimalFormTextField(
                        value = customQueryText,
                        onValueChange = { customQueryText = it },
                        label = "Ask Gemini about orders, margins, docs, or payments...",
                        modifier = Modifier.weight(1f),
                        testTag = "nl_query_input"
                    )
                    Button(
                        onClick = { onAskNaturalLanguageQuery(customQueryText) },
                        enabled = customQueryText.isNotBlank() && nlQueryState !is AiTaskState.Loading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ChampagneAccent,
                            contentColor = OnGoldDarkInk
                        ),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("Ask", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (nlQueryState !is AiTaskState.Idle) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SoftChampagneBg.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(12.dp)) {
                            when (nlQueryState) {
                                is AiTaskState.Loading -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            color = PrimaryInk,
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Querying authorized workspace records...", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                is AiTaskState.Success -> {
                                    Text(
                                        text = nlQueryState.data,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PrimaryInk
                                    )
                                }
                                is AiTaskState.Error -> {
                                    Text(
                                        text = nlQueryState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CriticalCrimsonTone
                                    )
                                }
                                else -> {}
                            }
                        }
                    }
                }

                if (aiInsightsState is AiTaskState.Success) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = aiInsightsState.data,
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryInk
                    )
                }
            }
        }

        // Recent Orders Section
        item {
            SectionHeaderRow(
                title = "Active Export Orders (${portfolio.orders.size})",
                trailingText = "+ New Order",
                onTrailingClick = { onNavigate("new_order") }
            )
        }

        if (portfolio.orders.isEmpty() && !portfolio.isLoading) {
            item {
                WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "No Export Orders in Production Workspace",
                            style = MaterialTheme.typography.titleMedium,
                            color = PrimaryInk
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Create an export order below, or tap 'Sample MSME' above to explore Tamil Nadu Global Exports Pvt. Ltd.'s 6 preloaded orders.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryInk
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onNavigate("new_order") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryInk,
                                    contentColor = PureWhiteSurface
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.testTag("dashboard_new_order_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Order")
                            }
                            OutlinedButton(
                                onClick = { onSwitchWorkspace(WorkspaceMode.SAMPLE_BUSINESS) },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, ChampagneAccent)
                            ) {
                                Text("Explore Sample Business", color = PrimaryInk)
                            }
                        }
                    }
                }
            }
        } else {
            items(portfolio.orders.take(6), key = { it.id }) { order ->
                val isSelected = order.id == portfolio.selectedOrderId
                OrderSummaryListItem(
                    order = order,
                    isSelected = isSelected,
                    onClick = {
                        onSelectOrder(order.id)
                        onNavigate("digital_twin")
                    },
                    onOpenPayPal = {
                        onSelectOrder(order.id)
                        onNavigate("paypal_payments")
                    }
                )
            }
        }

        // Recent Activity Stream
        if (portfolio.activities.isNotEmpty()) {
            item {
                SectionHeaderRow(title = "Recent Operational Activity")
            }
            items(portfolio.activities.take(5), key = { it.id }) { act ->
                val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.US) }
                val timeStr = act.createdAt?.toDate()?.let { dateFormat.format(it) } ?: "Now"
                WarmSurfaceCard(
                    cornerRadius = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = act.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 14.sp,
                                color = PrimaryInk
                            )
                            Text(
                                text = act.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SecondaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = timeStr,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = MutedInk
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
}

@Composable
private fun CompactFinancialColumn(
    label: String,
    value: String,
    subtext: String,
    valueColor: androidx.compose.ui.graphics.Color
) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = SecondaryInk)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontFamily = SpaceGroteskFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = valueColor
        )
        Text(text = subtext, fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = MutedInk)
    }
}

@Composable
private fun LifecycleMiniRow(
    label: String,
    count: Int,
    dotColor: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = SecondaryInk)
        }
        Text(
            text = "$count",
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = PrimaryInk
        )
    }
}

@Composable
fun OrderSummaryListItem(
    order: ExportOrder,
    isSelected: Boolean,
    onClick: () -> Unit,
    onOpenPayPal: () -> Unit
) {
    WarmSurfaceCard(
        borderColor = if (isSelected) ChampagneAccent else WarmBorder,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = order.orderNumber,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = PrimaryInk
            )
            StatusPill(
                label = order.orderStatus.replace('_', ' '),
                tone = statusToneFor(order.orderStatus)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${order.productName} • ${order.buyerName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = PrimaryInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${order.buyerCountry} (${order.incoterm} ${order.destinationPort})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${order.currency} ${String.format("%,.0f", order.orderValue)}",
                    fontFamily = SpaceGroteskFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PrimaryInk
                )
                Text(
                    text = "PayPal / Twin →",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = ChampagneAccent,
                    modifier = Modifier.clickable { onOpenPayPal() }
                )
            }
        }
    }
}

/**
 * Orders Hub Screen (Destination 2 in the 5-tab AppBottomNavigation bar).
 * Includes search & status filtering across all orders.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrdersHubScreen(
    portfolio: NiryatPortfolioState,
    onSelectOrder: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onUpdateOrderStatus: (ExportOrder, String) -> Unit = { _, _ -> },
    onDeleteOrder: (ExportOrder) -> Unit = {}
) {
    BackHandler { onNavigate("dashboard") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf("ALL") }
    var sortOption by rememberSaveable { mutableStateOf("VALUE_DESC") }

    val assessmentMap = remember(portfolio.assessments) {
        portfolio.assessments.associateBy { it.exportOrderId }
    }

    val filteredOrders = remember(portfolio.orders, assessmentMap, searchQuery, statusFilter, sortOption) {
        val base = portfolio.orders.filter { ord ->
            val matchesQuery = searchQuery.isBlank() ||
                ord.orderNumber.contains(searchQuery, ignoreCase = true) ||
                ord.buyerName.contains(searchQuery, ignoreCase = true) ||
                ord.buyerCountry.contains(searchQuery, ignoreCase = true) ||
                ord.productName.contains(searchQuery, ignoreCase = true) ||
                ord.currency.contains(searchQuery, ignoreCase = true)
            val matchesStatus = statusFilter == "ALL" || ord.orderStatus.contains(statusFilter, ignoreCase = true)
            matchesQuery && matchesStatus
        }
        when (sortOption) {
            "VALUE_DESC" -> base.sortedByDescending { ord ->
                val fx = assessmentMap[ord.id]?.exchangeRate ?: FinancialCalculationEngine.defaultExchangeRateForCurrency(ord.currency)
                ord.orderValue * fx
            }
            "MARGIN_DESC" -> base.sortedByDescending { ord ->
                assessmentMap[ord.id]?.profitMarginPercent ?: 0.0
            }
            "SHIP_DATE" -> base.sortedBy { it.expectedShipmentDate }
            else -> base.sortedBy { it.orderNumber }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Export Orders (${filteredOrders.size})",
                        style = MaterialTheme.typography.headlineMedium,
                        color = PrimaryInk
                    )
                    Text(
                        text = "${portfolio.businessProfile.businessName} • Search, sort, filter & open Digital Twin",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryInk
                    )
                }
                Button(
                    onClick = { onNavigate("new_order") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChampagneAccent,
                        contentColor = OnGoldDarkInk
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("orders_hub_new_order_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Order", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Search, Status Filter & Advanced Sorting Bar
        item {
            WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                MinimalFormTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "Search by Order #, Buyer, Country, Currency, or Product...",
                    testTag = "orders_search_field"
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ALL", "SHIPMENT", "PAYMENT", "DOCUMENTATION", "COMPLETED", "PROCESSING").forEach { st ->
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = { statusFilter = st },
                            label = { Text(st, style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMonoFamily) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SoftChampagneBg,
                                selectedLabelColor = LightGoldAccent
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "SORT BY:",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = SecondaryInk
                    )
                    listOf(
                        "VALUE_DESC" to "INR Value ↓",
                        "MARGIN_DESC" to "Margin % ↓",
                        "ORDER_NUM" to "Order #",
                        "SHIP_DATE" to "Ship Date"
                    ).forEach { (key, lbl) ->
                        FilterChip(
                            selected = sortOption == key,
                            onClick = { sortOption = key },
                            label = { Text(lbl, fontSize = 10.sp, fontFamily = JetBrainsMonoFamily) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SoftChampagneBg,
                                selectedLabelColor = LightGoldAccent
                            )
                        )
                    }
                }
            }
        }

        // Contextual shortcuts for the currently selected order
        portfolio.selectedDigitalTwin?.let { twin ->
            item {
                WarmSurfaceCard(
                    backgroundColor = SoftChampagneBg.copy(alpha = 0.75f),
                    borderColor = ChampagneAccent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ACTIVE ORDER • ${twin.order.orderNumber}",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = LightGoldAccent
                            )
                            Text(
                                text = "${twin.order.productName} → ${twin.order.buyerName}",
                                style = MaterialTheme.typography.titleMedium,
                                color = PrimaryInk
                            )
                        }
                        StatusPill(
                            label = "${twin.overallProgressPercent}% Twin",
                            tone = FintechTone.POSITIVE
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onNavigate("digital_twin") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ChampagneAccent,
                                contentColor = OnGoldDarkInk
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_digital_twin_btn")
                        ) {
                            Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Digital Twin", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { onNavigate("paypal_payments") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_paypal_payments_btn")
                        ) {
                            Icon(Icons.Default.Payment, contentDescription = null, tint = PrimaryInk, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PayPal Sandbox", color = PrimaryInk, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "UPDATE SHIPMENT / LIFECYCLE STATUS (${twin.order.orderNumber}):",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = SecondaryInk
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "ORDER_PROCESSING",
                            "DOCUMENTATION_PENDING",
                            "DOCS_READY",
                            "AWAITING_PAYMENT",
                            "SHIPMENT_IN_TRANSIT",
                            "COMPLETED"
                        ).forEach { st ->
                            FilterChip(
                                selected = twin.order.orderStatus == st,
                                onClick = { onUpdateOrderStatus(twin.order, st) },
                                label = { Text(st.replace('_', ' '), fontSize = 10.sp, fontFamily = JetBrainsMonoFamily) },
                                modifier = Modifier.testTag("order_status_chip_$st")
                            )
                        }
                    }
                }
            }
        }

        if (filteredOrders.isEmpty()) {
            item {
                WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No matching export orders found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryInk
                    )
                }
            }
        } else {
            items(filteredOrders, key = { it.id }) { order ->
                OrderSummaryListItem(
                    order = order,
                    isSelected = order.id == portfolio.selectedOrderId,
                    onClick = {
                        onSelectOrder(order.id)
                        onNavigate("digital_twin")
                    },
                    onOpenPayPal = {
                        onSelectOrder(order.id)
                        onNavigate("paypal_payments")
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
}

/**
 * Profile / More Screen (Destination 5 in the 5-tab AppBottomNavigation bar).
 * Houses:
 * - Workspace Switcher (Production vs Sample Business Workspace: Tamil Nadu Global Exports Pvt. Ltd.) & Reset
 * - Business Profile Onboarding & Multi-Tenant Role Settings
 * - Downloadable Business Reports (6 CSV/Shareable reports)
 * - Module 5 Export Performance Intelligence & PayPal OAuth 2.0 Diagnostics
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileAndMoreScreen(
    portfolio: NiryatPortfolioState,
    userEmail: String,
    isPayPalConfigured: Boolean,
    paypalAuthCheckStatus: String?,
    aiInsightsState: AiTaskState<String>,
    onVerifyPayPalOAuth: () -> Unit,
    onRequestAiInsights: () -> Unit,
    onSwitchWorkspace: (WorkspaceMode) -> Unit = {},
    onResetSampleWorkspace: () -> Unit = {},
    onUpdateBusinessProfile: (String, String, String, String, String, String) -> Unit = { _, _, _, _, _, _ -> },
    onExportReportCsv: (String) -> String = { "" },
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit
) {
    BackHandler { onNavigate("dashboard") }
    val context = LocalContext.current
    val metrics = portfolio.performanceMetrics
    val profile = portfolio.businessProfile
    val isSample = portfolio.workspaceMode == WorkspaceMode.SAMPLE_BUSINESS

    var editBizName by rememberSaveable(profile.businessId) { mutableStateOf(profile.businessName) }
    var editBizType by rememberSaveable(profile.businessId) { mutableStateOf(profile.businessType) }
    var editBaseLocation by rememberSaveable(profile.businessId) { mutableStateOf(profile.primaryBase) }
    var editContactEmail by rememberSaveable(profile.businessId) { mutableStateOf(profile.contactEmail) }
    var editCategories by rememberSaveable(profile.businessId) { mutableStateOf(profile.primaryCategories) }
    var editCurrency by rememberSaveable(profile.businessId) { mutableStateOf(profile.reportingCurrency) }
    var showProfileEditor by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Workspace, Reports & Intelligence",
                style = MaterialTheme.typography.headlineMedium,
                color = PrimaryInk
            )
            Text(
                text = "${profile.businessName} • Role: ${profile.role}",
                style = MaterialTheme.typography.bodyMedium,
                color = SecondaryInk
            )
        }

        // Workspace Switcher & Sample Business Workspace Reset Card (Sections 2, 10 & 11)
        item {
            WarmSurfaceCard(
                backgroundColor = if (isSample) SoftChampagneBg else PureWhiteSurface,
                borderColor = ChampagneAccent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isSample) "SAMPLE BUSINESS WORKSPACE ACTIVE" else "PRODUCTION WORKSPACE ACTIVE",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ChampagneAccent
                        )
                        Text(
                            text = if (isSample) {
                                "Tamil Nadu Global Exports Pvt. Ltd. (Chennai, TN) — 6 Preloaded Multi-Currency Orders"
                            } else {
                                "Authenticated Cloud Firestore Workspace (${userEmail.ifBlank { "Exporter" }})"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = PrimaryInk
                        )
                    }
                    StatusPill(
                        label = if (isSample) "ISOLATED SAMPLE" else "LIVE CLOUD",
                        tone = if (isSample) FintechTone.ACCENT_GOLD else FintechTone.POSITIVE
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onSwitchWorkspace(
                                if (isSample) WorkspaceMode.PRODUCTION else WorkspaceMode.SAMPLE_BUSINESS
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryInk,
                            contentColor = PureWhiteSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("more_switch_workspace_btn")
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSample) "Switch to Production" else "Explore Sample Business",
                            fontSize = 12.sp
                        )
                    }

                    if (isSample) {
                        OutlinedButton(
                            onClick = onResetSampleWorkspace,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PrimaryInk),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("more_reset_sample_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = PrimaryInk, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Sample Data", color = PrimaryInk, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Business Onboarding & Multi-Tenant Profile Card (Sections 5 & 6)
        item {
            WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Business, contentDescription = null, tint = ChampagneAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Business Profile & Onboarding",
                                style = MaterialTheme.typography.titleMedium,
                                color = PrimaryInk
                            )
                            Text(
                                text = "${profile.businessType} • ${profile.primaryBase}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryInk
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { showProfileEditor = !showProfileEditor },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (showProfileEditor) "Hide" else "Edit Profile", color = PrimaryInk, fontSize = 11.sp)
                    }
                }

                if (showProfileEditor) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = WarmBorder)
                    Spacer(modifier = Modifier.height(12.dp))
                    MinimalFormTextField(
                        value = editBizName,
                        onValueChange = { editBizName = it },
                        label = "Business Legal Name"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MinimalFormTextField(
                        value = editBizType,
                        onValueChange = { editBizType = it },
                        label = "Business Classification / Type"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MinimalFormTextField(
                        value = editBaseLocation,
                        onValueChange = { editBaseLocation = it },
                        label = "Primary Operations Base / Address"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MinimalFormTextField(
                        value = editCategories,
                        onValueChange = { editCategories = it },
                        label = "Primary Export Categories"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onUpdateBusinessProfile(
                                editBizName,
                                editBizType,
                                editBaseLocation,
                                editContactEmail,
                                editCategories,
                                editCurrency
                            )
                            showProfileEditor = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryInk,
                            contentColor = PureWhiteSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Business Profile")
                    }
                }
            }
        }

        // Downloadable Business Reports (Section 14)
        item {
            WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeaderRow(
                    title = "Downloadable Business Reports (CSV)",
                    trailingText = "Base: ${profile.reportingCurrency}"
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Generated directly from authorized ${if (isSample) "Sample Workspace" else "Production"} records with generation timestamp and assumptions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryInk
                )
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BUSINESS_REPORT_TYPES.forEach { reportTitle ->
                        OutlinedButton(
                            onClick = {
                                val csvContent = onExportReportCsv(reportTitle)
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_SUBJECT, "NiryatFlow AI — $reportTitle")
                                    putExtra(Intent.EXTRA_TEXT, csvContent)
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(sendIntent, "Export $reportTitle"))
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, WarmBorder),
                            modifier = Modifier.testTag("export_report_${reportTitle.lowercase().replace(' ', '_')}")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = PrimaryInk)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(reportTitle, fontSize = 11.sp, color = PrimaryInk)
                        }
                    }
                }
            }
        }

        // Quick Module Access (Digital Twin, PayPal Payments, New Order)
        item {
            WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeaderRow(title = "All Connected Modules")
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onNavigate("digital_twin") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Digital Twin", color = PrimaryInk, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onNavigate("paypal_payments") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("PayPal Sandbox", color = PrimaryInk, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onNavigate("new_order") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("New Order", color = PrimaryInk, fontSize = 12.sp)
                    }
                }
            }
        }

        // PayPal Sandbox Environment & Demo Mode Status Card
        item {
            WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isSample) {
                                "Zero-Cost Demo Payment Engine"
                            } else {
                                "PayPal Sandbox Backend Check"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = PrimaryInk
                        )
                        Text(
                            text = if (isSample) {
                                "SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION"
                            } else {
                                "Authenticated Cloud Function • Zero Client Secret in APK"
                            },
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = if (isSample) ChampagneAccent else SecondaryInk
                        )
                    }
                    StatusPill(
                        label = when {
                            isSample -> "DEMO MODE ACTIVE"
                            isPayPalConfigured -> "BACKEND CONFIGURED"
                            else -> "BACKEND OPTIONAL"
                        },
                        tone = when {
                            isSample -> FintechTone.ACCENT_GOLD
                            isPayPalConfigured -> FintechTone.POSITIVE
                            else -> FintechTone.WARNING
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = paypalAuthCheckStatus
                        ?: if (isSample) {
                            "Sample Business Workspace runs DemoPaymentService locally (DEMO_PENDING, DEMO_CAPTURED, DEMO_FAILED, DEMO_CANCELLED) with idempotent references. No PAYPAL_BACKEND_BASE_URL, PayPal Client Secret, or Firebase Blaze upgrade is required."
                        } else if (isPayPalConfigured) {
                            "Backend Cloud Function endpoint detected via BuildConfig (Zero Client Secret in APK). Tap 'Verify Payment Mode / Backend' to validate."
                        } else {
                            "Production Mode never silently fakes payments. Configure PAYPAL_BACKEND_BASE_URL for live Sandbox calls, or switch to Sample Business Workspace for Zero-Cost Demo Mode."
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (paypalAuthCheckStatus?.startsWith("PASS") == true || paypalAuthCheckStatus?.startsWith("DEMO") == true) {
                        PositiveEmerald
                    } else {
                        SecondaryInk
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onVerifyPayPalOAuth,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryInk,
                        contentColor = PureWhiteSurface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("verify_paypal_oauth_btn")
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isSample) {
                            "Verify Zero-Cost Demo Payment Engine"
                        } else {
                            "Verify PayPal Sandbox Backend"
                        }
                    )
                }
            }
        }

        // Module 5: Export Performance Intelligence Summary
        item {
            WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeaderRow(title = "Export Performance Intelligence (Module 5)")
                Spacer(modifier = Modifier.height(12.dp))
                LargeMetricDisplay(
                    label = "Average Order Value",
                    primaryValue = "₹${FinancialCalculationEngine.formatInrShort(metrics.averageOrderValueInr)}",
                    badgeText = "${metrics.averageProfitMarginPercent}% Avg Margin",
                    badgeTone = FintechTone.POSITIVE
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Payment Completion Rate (${metrics.paymentCompletionRatePercent}%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryInk
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (metrics.paymentCompletionRatePercent / 100.0).toFloat().coerceIn(0f, 1f) },
                    color = PositiveEmerald,
                    trackColor = IvorySecondaryBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Document Readiness Rate (${metrics.documentReadinessRatePercent}%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryInk
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (metrics.documentReadinessRatePercent / 100.0).toFloat().coerceIn(0f, 1f) },
                    color = ChampagneAccent,
                    trackColor = IvorySecondaryBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }

        // Account & Session Card
        item {
            WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isSample) "Sample Workspace Session" else "Authenticated Exporter",
                            style = MaterialTheme.typography.labelMedium,
                            color = SecondaryInk
                        )
                        Text(
                            text = userEmail.ifBlank { "Sample Business Evaluator (Chennai)" },
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 13.sp,
                            color = PrimaryInk
                        )
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Sign out",
                            tint = CriticalCrimsonTone,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isSample && userEmail.isBlank()) "Exit Sample" else "Sign Out", color = CriticalCrimsonTone)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
}
