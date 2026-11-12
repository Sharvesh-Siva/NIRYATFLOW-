package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DocumentRecord
import com.example.data.model.ExportOrder
import com.example.data.model.FinancialAssessment
import com.example.data.model.PaymentRecord
import com.example.domain.FinancialCalculationEngine
import com.example.ui.theme.ChampagneAccent
import com.example.ui.theme.CriticalCrimsonBg
import com.example.ui.theme.CriticalCrimsonTone
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkElevatedSurface
import com.example.ui.theme.DarkSecondaryBg
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LightGoldAccent
import com.example.ui.theme.MutedInk
import com.example.ui.theme.OnGoldDarkInk
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
import com.example.ui.viewmodel.NiryatPortfolioState
import kotlin.math.hypot
import kotlin.math.roundToInt

// ============================================================================
// OBJECTIVE 6: CONNECTED TRADE RISK INTELLIGENCE ENGINE
// ============================================================================

data class TradeRiskAlert(
    val id: String,
    val severity: String, // CRITICAL, HIGH, MEDIUM, LOW
    val category: String,
    val affectedOrderId: String,
    val affectedOrderNumber: String,
    val evidence: String,
    val explanation: String,
    val recommendedAction: String,
    val resolutionStatus: String, // OPEN, IN_REVIEW, MONITORING
    val targetRoute: String
)

object TradeRiskEngine {

    fun evaluatePortfolioRisks(
        orders: List<ExportOrder>,
        assessments: List<FinancialAssessment>,
        documents: List<DocumentRecord>,
        payments: List<PaymentRecord>
    ): List<TradeRiskAlert> {
        if (orders.isEmpty()) return emptyList()

        val alerts = mutableListOf<TradeRiskAlert>()
        val assessmentByOrder = assessments.associateBy { it.exportOrderId }
        val docsByOrder = documents.groupBy { it.exportOrderId }
        val paymentsByOrder = payments.groupBy { it.exportOrderId }

        var totalInr = 0.0
        val currencyTotalsInr = mutableMapOf<String, Double>()

        for (order in orders) {
            val assess = assessmentByOrder[order.id]
            val fx = assess?.exchangeRate ?: FinancialCalculationEngine.defaultExchangeRateForCurrency(order.currency)
            val orderInr = assess?.convertedValueInr ?: (order.orderValue * fx)
            totalInr += orderInr
            currencyTotalsInr[order.currency] = (currencyTotalsInr[order.currency] ?: 0.0) + orderInr

            val orderDocs = docsByOrder[order.id].orEmpty()
            val requiredDocs = orderDocs.filter { it.requirementLevel == "REQUIRED" }
            val missingDocs = requiredDocs.filter { it.status in listOf("MISSING", "REJECTED", "REUPLOAD_REQUIRED") }
            val unverifiedDocs = requiredDocs.filter { it.status in listOf("UPLOADED", "UNDER_REVIEW") }

            // 1. Missing or Rejected Mandatory Documents
            if (missingDocs.isNotEmpty() && order.orderStatus != "COMPLETED") {
                alerts.add(
                    TradeRiskAlert(
                        id = "risk_doc_missing_${order.id}",
                        severity = if (missingDocs.size >= 2) "CRITICAL" else "HIGH",
                        category = "DOCUMENTATION COMPLIANCE",
                        affectedOrderId = order.id,
                        affectedOrderNumber = order.orderNumber,
                        evidence = "${missingDocs.size} of ${requiredDocs.size} required documents missing: ${missingDocs.joinToString { it.documentType }}",
                        explanation = "Customs export clearance and Letter of Credit / CAD presentation cannot proceed without verified mandatory shipping documents.",
                        recommendedAction = "Upload and verify ${missingDocs.first().documentType} in Document Intelligence.",
                        resolutionStatus = "OPEN",
                        targetRoute = "document_centre"
                    )
                )
            } else if (unverifiedDocs.isNotEmpty() && order.orderStatus != "COMPLETED") {
                alerts.add(
                    TradeRiskAlert(
                        id = "risk_doc_review_${order.id}",
                        severity = "MEDIUM",
                        category = "DOCUMENTATION VERIFICATION",
                        affectedOrderId = order.id,
                        affectedOrderNumber = order.orderNumber,
                        evidence = "${unverifiedDocs.size} uploaded document(s) pending compliance review (${unverifiedDocs.joinToString { it.documentType }})",
                        explanation = "Uploaded files are not treated as verified until human compliance sign-off is completed.",
                        recommendedAction = "Complete verification review in Document Intelligence.",
                        resolutionStatus = "IN_REVIEW",
                        targetRoute = "document_centre"
                    )
                )
            }

            // 2. Incomplete Order Commercial / Shipment Fields
            if (order.destinationPort.isBlank() || order.expectedShipmentDate.isBlank() || order.buyerContact.isBlank()) {
                alerts.add(
                    TradeRiskAlert(
                        id = "risk_fields_${order.id}",
                        severity = "HIGH",
                        category = "INCOMPLETE ORDER DATA",
                        affectedOrderId = order.id,
                        affectedOrderNumber = order.orderNumber,
                        evidence = "Missing destinationPort, expectedShipmentDate, or buyerContact on ${order.orderNumber}",
                        explanation = "Incomplete commercial metadata blocks freight booking and customs Shipping Bill filing.",
                        recommendedAction = "Complete missing shipment fields in Export Order Intake.",
                        resolutionStatus = "OPEN",
                        targetRoute = "new_order"
                    )
                )
            }

            // 3. Unverified or Pending Payments
            val orderPayments = paymentsByOrder[order.id].orEmpty()
            val capturedSum = orderPayments.filter { FinancialCalculationEngine.isPaymentSettled(it.paymentStatus) }.sumOf { it.amount }
            val pendingPayments = orderPayments.filter {
                it.paymentStatus in listOf("PENDING_APPROVAL", "DEMO_PENDING", "APPROVED", "FAILED", "DEMO_FAILED", "DEMO_CANCELLED", "REQUIRES_RECONCILIATION")
            }
            if (pendingPayments.isNotEmpty()) {
                val p = pendingPayments.first()
                alerts.add(
                    TradeRiskAlert(
                        id = "risk_pay_pending_${order.id}",
                        severity = if (p.paymentStatus in listOf("FAILED", "DEMO_FAILED", "REQUIRES_RECONCILIATION")) "CRITICAL" else "HIGH",
                        category = "UNVERIFIED SETTLEMENT",
                        affectedOrderId = order.id,
                        affectedOrderNumber = order.orderNumber,
                        evidence = "Payment Ref ${p.paypalOrderId.ifBlank { "Unassigned" }} (${p.currency} ${String.format("%,.2f", p.amount)}) is in state ${p.paymentStatus}",
                        explanation = "Payment initiation does not guarantee settlement until capture is verified.",
                        recommendedAction = "Complete or reconcile payment in Payment Intelligence.",
                        resolutionStatus = "OPEN",
                        targetRoute = "paypal_payments"
                    )
                )
            } else if (capturedSum < order.orderValue && order.orderStatus != "COMPLETED") {
                val outstanding = (order.orderValue - capturedSum).coerceAtLeast(0.0)
                alerts.add(
                    TradeRiskAlert(
                        id = "risk_pay_balance_${order.id}",
                        severity = "MEDIUM",
                        category = "OUTSTANDING RECEIVABLE",
                        affectedOrderId = order.id,
                        affectedOrderNumber = order.orderNumber,
                        evidence = "Outstanding receivable of ${order.currency} ${String.format("%,.2f", outstanding)} (Captured: ${order.currency} ${String.format("%,.2f", capturedSum)})",
                        explanation = "Balance export proceeds remain uncollected prior to final buyer release.",
                        recommendedAction = "Track buyer milestone and initiate balance collection in Payment Intelligence.",
                        resolutionStatus = "MONITORING",
                        targetRoute = "paypal_payments"
                    )
                )
            }

            // 4. Financial Viability & Margin Compression
            if (assess == null) {
                alerts.add(
                    TradeRiskAlert(
                        id = "risk_fin_missing_${order.id}",
                        severity = "HIGH",
                        category = "MISSING FINANCIAL ASSESSMENT",
                        affectedOrderId = order.id,
                        affectedOrderNumber = order.orderNumber,
                        evidence = "No deterministic INR cost & FX assessment saved for ${order.orderNumber}",
                        explanation = "Exporting without verified FX conversion and direct cost modeling exposes the order to margin erosion.",
                        recommendedAction = "Run deterministic INR assessment in Financial Intelligence.",
                        resolutionStatus = "OPEN",
                        targetRoute = "financial_intelligence"
                    )
                )
            } else if (assess.profitMarginPercent < 15.0) {
                alerts.add(
                    TradeRiskAlert(
                        id = "risk_fin_margin_${order.id}",
                        severity = if (assess.profitMarginPercent < 8.0) "CRITICAL" else "MEDIUM",
                        category = "LOW PROFIT MARGIN",
                        affectedOrderId = order.id,
                        affectedOrderNumber = order.orderNumber,
                        evidence = "Net margin is ${assess.profitMarginPercent}% (Net Profit ₹${FinancialCalculationEngine.formatInrShort(assess.estimatedProfitInr)} @ FX ${assess.exchangeRate})",
                        explanation = "Procurement, logistics, and cross-border payment fees are compressing net INR realization.",
                        recommendedAction = "Use the Financial What-If Simulator to test FX hedging or freight optimization.",
                        resolutionStatus = "MONITORING",
                        targetRoute = "financial_intelligence"
                    )
                )
            }
        }

        // 5. Portfolio Currency Concentration Exposure
        if (totalInr > 0.0) {
            val topCurrency = currencyTotalsInr.maxByOrNull { it.value }
            if (topCurrency != null) {
                val sharePct = ((topCurrency.value / totalInr) * 100.0).roundToInt()
                if (sharePct >= 28) {
                    val sampleOrder = orders.first { it.currency == topCurrency.key }
                    alerts.add(
                        TradeRiskAlert(
                            id = "risk_fx_exposure_${topCurrency.key}",
                            severity = "LOW",
                            category = "CURRENCY CONCENTRATION",
                            affectedOrderId = sampleOrder.id,
                            affectedOrderNumber = sampleOrder.orderNumber,
                            evidence = "${topCurrency.key} accounts for $sharePct% (₹${FinancialCalculationEngine.formatInrShort(topCurrency.value)}) of total portfolio value",
                            explanation = "Spot exchange rate volatility in ${topCurrency.key}/INR directly impacts portfolio net settlement.",
                            recommendedAction = "Stress-test ${topCurrency.key}/INR exchange rate sensitivity in Financial Intelligence.",
                            resolutionStatus = "MONITORING",
                            targetRoute = "financial_intelligence"
                        )
                    )
                }
            }
        }

        val severityRank = mapOf("CRITICAL" to 0, "HIGH" to 1, "MEDIUM" to 2, "LOW" to 3)
        return alerts.sortedBy { severityRank[it.severity] ?: 4 }
    }
}

// ============================================================================
// OBJECTIVE 3 & 5: INTERACTIVE GLOBAL TRADE ROUTE WORLD MAP (VISUALIZATION 10)
// ============================================================================

private data class DestinationGeoPoint(
    val country: String,
    val xFraction: Float,
    val yFraction: Float,
    val regionCode: String
)

private val KNOWN_GEO_POINTS = listOf(
    DestinationGeoPoint("Germany", 0.46f, 0.26f, "EU • Hamburg"),
    DestinationGeoPoint("USA", 0.17f, 0.34f, "NA • Detroit/NY"),
    DestinationGeoPoint("United States", 0.17f, 0.34f, "NA • Detroit/NY"),
    DestinationGeoPoint("UAE", 0.57f, 0.46f, "MENA • Jebel Ali"),
    DestinationGeoPoint("United Arab Emirates", 0.57f, 0.46f, "MENA • Jebel Ali"),
    DestinationGeoPoint("Singapore", 0.77f, 0.62f, "APAC • Singapore"),
    DestinationGeoPoint("UK", 0.42f, 0.23f, "UK • Felixstowe"),
    DestinationGeoPoint("United Kingdom", 0.42f, 0.23f, "UK • Felixstowe"),
    DestinationGeoPoint("Japan", 0.87f, 0.35f, "APAC • Tokyo")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractiveTradeRouteWorldMap(
    portfolio: NiryatPortfolioState,
    onSelectOrderAndDrillDown: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val orders = portfolio.orders
    val assessmentByOrder = remember(portfolio.assessments) {
        portfolio.assessments.associateBy { it.exportOrderId }
    }

    val ordersByCountry = remember(orders) {
        orders.groupBy { it.buyerCountry.ifBlank { "International" } }
    }
    val countries = remember(ordersByCountry) { ordersByCountry.keys.toList() }
    var selectedCountry by rememberSaveable(countries) {
        mutableStateOf(countries.firstOrNull().orEmpty())
    }

    val infiniteTransition = rememberInfiniteTransition(label = "world_map_corridors")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "corridor_packet"
    )

    WarmSurfaceCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("interactive_trade_route_map")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = ChampagneAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Global Export Corridors",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryInk
                    )
                    Text(
                        text = "Origin: Chennai / India Hub → ${countries.size} Destination Markets",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = SecondaryInk
                    )
                }
            }
            StatusPill(
                label = "${orders.size} Active Routes",
                tone = FintechTone.TRADE_BLUE
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Interactive World Map Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(195.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSecondaryBg)
                .border(1.dp, WarmBorder, RoundedCornerShape(14.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(195.dp)
                    .pointerInput(countries) {
                        detectTapGestures { tapOffset ->
                            val w = size.width
                            val h = size.height
                            var closestCountry: String? = null
                            var minDistance = Float.MAX_VALUE
                            countries.forEachIndexed { idx, country ->
                                val geo = KNOWN_GEO_POINTS.firstOrNull {
                                    it.country.equals(country, ignoreCase = true)
                                }
                                val nx = (geo?.xFraction ?: (0.20f + (idx * 0.14f) % 0.70f)) * w
                                val ny = (geo?.yFraction ?: (0.28f + (idx * 0.13f) % 0.45f)) * h
                                val dist = hypot(tapOffset.x - nx, tapOffset.y - ny)
                                if (dist < minDistance) {
                                    minDistance = dist
                                    closestCountry = country
                                }
                            }
                            if (closestCountry != null && minDistance < 120f) {
                                selectedCountry = closestCountry!!
                            }
                        }
                    }
            ) {
                val w = size.width
                val h = size.height

                // Subtle lat/lon graticule grid
                for (i in 1..4) {
                    val y = h * (i / 5f)
                    drawLine(
                        color = WarmBorder.copy(alpha = 0.55f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                }
                for (j in 1..5) {
                    val x = w * (j / 6f)
                    drawLine(
                        color = WarmBorder.copy(alpha = 0.45f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1f
                    )
                }

                // Simplified Continental Silhouettes for spatial context
                val continentColor = DarkElevatedSurface.copy(alpha = 0.85f)
                // North America
                drawRoundRect(
                    color = continentColor,
                    topLeft = Offset(w * 0.08f, h * 0.18f),
                    size = Size(w * 0.18f, h * 0.30f),
                    cornerRadius = CornerRadius(18f, 18f)
                )
                // Europe & UK
                drawRoundRect(
                    color = continentColor,
                    topLeft = Offset(w * 0.38f, h * 0.15f),
                    size = Size(w * 0.16f, h * 0.22f),
                    cornerRadius = CornerRadius(14f, 14f)
                )
                // MENA / Africa
                drawRoundRect(
                    color = continentColor,
                    topLeft = Offset(w * 0.44f, h * 0.40f),
                    size = Size(w * 0.16f, h * 0.34f),
                    cornerRadius = CornerRadius(16f, 16f)
                )
                // India & South/East Asia
                drawRoundRect(
                    color = continentColor,
                    topLeft = Offset(w * 0.62f, h * 0.24f),
                    size = Size(w * 0.28f, h * 0.44f),
                    cornerRadius = CornerRadius(20f, 20f)
                )

                val indiaOrigin = Offset(w * 0.68f, h * 0.52f)

                countries.forEachIndexed { idx, country ->
                    val geo = KNOWN_GEO_POINTS.firstOrNull {
                        it.country.equals(country, ignoreCase = true)
                    }
                    val destX = (geo?.xFraction ?: (0.20f + (idx * 0.14f) % 0.70f)) * w
                    val destY = (geo?.yFraction ?: (0.28f + (idx * 0.13f) % 0.45f)) * h
                    val destOffset = Offset(destX, destY)

                    val countryOrders = ordersByCountry[country].orEmpty()
                    val hasActiveOrCompletedShipment = countryOrders.any {
                        it.orderStatus in listOf("COMPLETED", "SHIPMENT_IN_PROGRESS", "PAYMENT_COMPLETED", "READY_FOR_PAYMENT")
                    }
                    val isSelected = country == selectedCountry

                    val control = Offset(
                        x = (indiaOrigin.x + destOffset.x) / 2f,
                        y = minOf(indiaOrigin.y, destOffset.y) - (h * 0.18f)
                    )
                    val routePath = Path().apply {
                        moveTo(indiaOrigin.x, indiaOrigin.y)
                        quadraticTo(control.x, control.y, destOffset.x, destOffset.y)
                    }

                    val corridorColor = when {
                        isSelected -> LightGoldAccent
                        hasActiveOrCompletedShipment -> PositiveEmerald
                        else -> TradeBlueAccent
                    }

                    // Distinguish verified shipment corridors (solid) from planned routes (dashed)
                    drawPath(
                        path = routePath,
                        color = corridorColor.copy(alpha = if (isSelected) 0.95f else 0.60f),
                        style = Stroke(
                            width = if (isSelected) 3.4f else 2.2f,
                            cap = StrokeCap.Round,
                            pathEffect = if (hasActiveOrCompletedShipment) null else PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                        )
                    )

                    // Animated trade flow indicator along verified corridors
                    val t = (animProgress + idx * 0.17f) % 1f
                    val omt = 1f - t
                    val px = omt * omt * indiaOrigin.x + 2f * omt * t * control.x + t * t * destOffset.x
                    val py = omt * omt * indiaOrigin.y + 2f * omt * t * control.y + t * t * destOffset.y
                    drawCircle(
                        color = corridorColor,
                        radius = if (isSelected) 5.5f else 4f,
                        center = Offset(px, py)
                    )

                    // Destination node
                    if (isSelected) {
                        drawCircle(
                            color = LightGoldAccent.copy(alpha = 0.25f),
                            radius = 15f,
                            center = destOffset
                        )
                    }
                    drawCircle(
                        color = corridorColor,
                        radius = if (isSelected) 7f else 5.5f,
                        center = destOffset
                    )
                }

                // India Origin Hub Node (Chennai / India)
                drawCircle(
                    color = ChampagneAccent.copy(alpha = 0.28f),
                    radius = 16f,
                    center = indiaOrigin
                )
                drawCircle(
                    color = ChampagneAccent,
                    radius = 7.5f,
                    center = indiaOrigin
                )
            }

            // Legend Overlay
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkCardSurface.copy(alpha = 0.90f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(color = ChampagneAccent, label = "India Origin")
                LegendDot(color = PositiveEmerald, label = "Verified Corridor")
                LegendDot(color = TradeBlueAccent, label = "Planned Corridor")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Interactive Destination Selector Pills
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            countries.forEach { country ->
                val isSelected = country == selectedCountry
                val count = ordersByCountry[country]?.size ?: 0
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCountry = country },
                    label = {
                        Text(
                            text = "$country ($count)",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SoftChampagneBg,
                        selectedLabelColor = LightGoldAccent,
                        containerColor = DarkElevatedSurface,
                        labelColor = SecondaryInk
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = WarmBorder,
                        selectedBorderColor = ChampagneAccent
                    )
                )
            }
        }

        // Selected Destination Corridor Tooltip & Drill-Down Card
        val selectedOrders = ordersByCountry[selectedCountry].orEmpty()
        if (selectedOrders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            val leadOrder = selectedOrders.first()
            val leadAssess = assessmentByOrder[leadOrder.id]
            val fx = leadAssess?.exchangeRate ?: FinancialCalculationEngine.defaultExchangeRateForCurrency(leadOrder.currency)
            val totalCountryInr = selectedOrders.sumOf { ord ->
                val a = assessmentByOrder[ord.id]
                a?.convertedValueInr ?: (ord.orderValue * (a?.exchangeRate ?: FinancialCalculationEngine.defaultExchangeRateForCurrency(ord.currency)))
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarkElevatedSurface,
                border = BorderStroke(1.dp, ChampagneAccent.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CORRIDOR: INDIA → ${selectedCountry.uppercase()} (${leadOrder.destinationPort})",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                color = LightGoldAccent,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${leadOrder.orderNumber} • ${leadOrder.productName}",
                                style = MaterialTheme.typography.titleMedium,
                                color = PrimaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        StatusPill(
                            label = leadOrder.orderStatus.replace('_', ' '),
                            tone = statusToneFor(leadOrder.orderStatus)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Buyer: ${leadOrder.buyerName} • Value: ${leadOrder.currency} ${String.format("%,.0f", leadOrder.orderValue)} (₹${FinancialCalculationEngine.formatInrShort(totalCountryInr)} @ FX $fx) • Incoterm: ${leadOrder.incoterm}",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = SecondaryInk
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Note: Planned/milestone route derived from order records (not live GPS vessel tracking).",
                            fontSize = 10.sp,
                            color = MutedInk,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onSelectOrderAndDrillDown(leadOrder.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ChampagneAccent,
                                contentColor = OnGoldDarkInk
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("map_drill_down_button")
                        ) {
                            Text("Inspect Twin", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 9.sp,
            color = SecondaryInk
        )
    }
}

// ============================================================================
// OBJECTIVE 5: ENTERPRISE INTERACTIVE VISUALIZATIONS SUITE (CHARTS 1–9)
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnterpriseVisualizationsSuite(
    portfolio: NiryatPortfolioState,
    selectedPeriod: String,
    onSelectOrderAndNavigate: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val chartTabs = listOf(
        "1. Revenue vs Cost",
        "2. Lifecycle Funnel",
        "3. Currency Exposure",
        "4. Doc Readiness",
        "5. Payment Breakdown"
    )
    var activeChartTab by rememberSaveable { mutableIntStateOf(0) }

    val orders = portfolio.orders
    val assessmentByOrder = remember(portfolio.assessments) {
        portfolio.assessments.associateBy { it.exportOrderId }
    }
    val docsByOrder = remember(portfolio.documents) {
        portfolio.documents.groupBy { it.exportOrderId }
    }
    val paymentsByOrder = remember(portfolio.payments) {
        portfolio.payments.groupBy { it.exportOrderId }
    }

    WarmSurfaceCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("enterprise_visualizations_suite")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    tint = ChampagneAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Interactive Trade Analytics Suite",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryInk
                    )
                    Text(
                        text = "Base Currency: ${portfolio.businessProfile.reportingCurrency} • Horizon: $selectedPeriod • Tap bars to drill down",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = SecondaryInk
                    )
                }
            }
            StatusPill(
                label = "Deterministic",
                tone = FintechTone.POSITIVE
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            chartTabs.forEachIndexed { index, title ->
                val selected = activeChartTab == index
                FilterChip(
                    selected = selected,
                    onClick = { activeChartTab = index },
                    label = {
                        Text(
                            text = title,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SoftChampagneBg,
                        selectedLabelColor = LightGoldAccent,
                        containerColor = DarkElevatedSurface,
                        labelColor = SecondaryInk
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = WarmBorder,
                        selectedBorderColor = ChampagneAccent
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (activeChartTab) {
            // Chart Tab 0: Revenue vs Expenditure & Profit Margin per Order
            0 -> {
                Text(
                    text = "REVENUE VS. DIRECT EXPENDITURE & NET PROFIT (INR)",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = LightGoldAccent
                )
                Spacer(modifier = Modifier.height(8.dp))
                orders.forEach { order ->
                    val assess = assessmentByOrder[order.id]
                    val fx = assess?.exchangeRate ?: FinancialCalculationEngine.defaultExchangeRateForCurrency(order.currency)
                    val grossInr = assess?.convertedValueInr ?: (order.orderValue * fx)
                    val totalCostsInr = if (assess != null) {
                        assess.procurementCostInr + assess.logisticsCostInr + assess.packagingCostInr + assess.otherCostsInr + assess.paymentFeeInr
                    } else {
                        grossInr * 0.78
                    }
                    val netProfitInr = assess?.estimatedProfitInr ?: (grossInr - totalCostsInr)
                    val marginPct = assess?.profitMarginPercent ?: 22.0
                    val costRatio = if (grossInr > 0) (totalCostsInr / grossInr).toFloat().coerceIn(0.1f, 1f) else 0.7f
                    val profitRatio = if (grossInr > 0) (netProfitInr / grossInr).toFloat().coerceIn(0.05f, 1f) else 0.2f

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectOrderAndNavigate(order.id, "financial_intelligence") }
                            .padding(vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${order.orderNumber} • ${order.buyerCountry} (${order.currency})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryInk
                            )
                            Text(
                                text = "Gross ₹${FinancialCalculationEngine.formatInrShort(grossInr)} | Profit ₹${FinancialCalculationEngine.formatInrShort(netProfitInr)} (${marginPct}%)",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                color = PositiveEmerald
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        // Dual Stacked Horizontal Comparison Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(DarkElevatedSurface)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(costRatio)
                                    .height(8.dp)
                                    .background(TradeBlueAccent)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(profitRatio.coerceAtMost(1f))
                                    .height(8.dp)
                                    .background(PositiveEmerald)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendDot(color = TradeBlueAccent, label = "Expenditure + Fees")
                    LegendDot(color = PositiveEmerald, label = "Net Realized Profit")
                }
            }

            // Chart Tab 1: Order Lifecycle Funnel
            1 -> {
                Text(
                    text = "EXPORT ORDER LIFECYCLE FUNNEL (8-STAGE DIGITAL TWIN)",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = LightGoldAccent
                )
                Spacer(modifier = Modifier.height(8.dp))
                val total = orders.size.coerceAtLeast(1)
                val assessedCount = orders.count { assessmentByOrder.containsKey(it.id) }
                val docsVerifiedCount = orders.count { ord ->
                    val d = docsByOrder[ord.id].orEmpty()
                    val (pct, _) = FinancialCalculationEngine.calculateDocumentReadiness(d)
                    pct >= 100.0
                }
                val paymentInitiatedCount = orders.count { ord ->
                    paymentsByOrder[ord.id].orEmpty().isNotEmpty()
                }
                val completedCount = orders.count { it.orderStatus == "COMPLETED" }

                val funnelStages = listOf(
                    Triple("1. Orders Created & Validated", orders.size, TradeBlueAccent),
                    Triple("2. Financially Assessed (INR)", assessedCount, ChampagneAccent),
                    Triple("3. Payment Initiated (PayPal)", paymentInitiatedCount, LightGoldAccent),
                    Triple("4. 100% Mandatory Docs Verified", docsVerifiedCount, PositiveEmerald),
                    Triple("5. Fully Settled & Completed", completedCount, PositiveEmerald)
                )
                funnelStages.forEach { (stageName, count, color) ->
                    val frac = (count.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stageName, style = MaterialTheme.typography.bodySmall, color = PrimaryInk)
                            Text(
                                text = "$count / ${orders.size} (${(frac * 100).roundToInt()}%)",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = color
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { frac },
                            color = color,
                            trackColor = DarkElevatedSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }

            // Chart Tab 2: Currency Exposure Breakdown
            2 -> {
                Text(
                    text = "MULTI-CURRENCY PORTFOLIO EXPOSURE & INR EQUIVALENT",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = LightGoldAccent
                )
                Spacer(modifier = Modifier.height(8.dp))
                val totalPortfolioInr = portfolio.performanceMetrics.totalOrderValueInr.coerceAtLeast(1.0)
                val groupedByCurrency = orders.groupBy { it.currency }

                groupedByCurrency.forEach { (curr, currOrders) ->
                    val foreignSum = currOrders.sumOf { it.orderValue }
                    val fx = currOrders.firstNotNullOfOrNull { assessmentByOrder[it.id]?.exchangeRate }
                        ?: FinancialCalculationEngine.defaultExchangeRateForCurrency(curr)
                    val inrSum = currOrders.sumOf { ord ->
                        assessmentByOrder[ord.id]?.convertedValueInr ?: (ord.orderValue * fx)
                    }
                    val sharePct = ((inrSum / totalPortfolioInr) * 100.0).roundToInt()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onSelectOrderAndNavigate(currOrders.first().id, "financial_intelligence")
                            }
                            .padding(vertical = 5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$curr • ${currOrders.size} order(s) @ FX ₹$fx",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryInk
                            )
                            Text(
                                text = "$curr ${String.format("%,.0f", foreignSum)} → ₹${FinancialCalculationEngine.formatInrShort(inrSum)} ($sharePct%)",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = ChampagneAccent
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (sharePct / 100f).coerceIn(0.05f, 1f) },
                            color = ChampagneAccent,
                            trackColor = DarkElevatedSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }

            // Chart Tab 3: Documentation Readiness Visualization
            3 -> {
                Text(
                    text = "ORDER-BY-ORDER MANDATORY DOCUMENTATION READINESS",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = LightGoldAccent
                )
                Spacer(modifier = Modifier.height(8.dp))
                orders.forEach { order ->
                    val orderDocs = docsByOrder[order.id].orEmpty()
                    val (pct, counts) = FinancialCalculationEngine.calculateDocumentReadiness(orderDocs)
                    val (verified, totalReq) = counts
                    val barColor = when {
                        pct >= 100.0 -> PositiveEmerald
                        pct >= 60.0 -> WarningAmberTone
                        else -> CriticalCrimsonTone
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectOrderAndNavigate(order.id, "document_centre") }
                            .padding(vertical = 5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${order.orderNumber} • ${order.productName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = PrimaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "$verified/$totalReq Verified (${pct.toInt()}%)",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = barColor
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
                            color = barColor,
                            trackColor = DarkElevatedSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Compliance Note: Internal checklist readiness tracks operational preparation and does not constitute official ICEGATE/customs regulatory clearance.",
                    fontSize = 10.sp,
                    color = MutedInk
                )
            }

            // Chart Tab 4: Payment Status Breakdown
            else -> {
                val metrics = portfolio.performanceMetrics
                val capturedInr = metrics.capturedPaymentValueInr
                val outstandingInr = metrics.outstandingPaymentValueInr
                val totalInr = metrics.totalOrderValueInr.coerceAtLeast(1.0)
                val capturedRatio = (capturedInr / totalInr).toFloat().coerceIn(0f, 1f)

                Text(
                    text = "CROSS-BORDER PAYMENT COLLECTION & SETTLEMENT STATUS",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = LightGoldAccent
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Captured (Verified)", style = MaterialTheme.typography.bodySmall, color = SecondaryInk)
                        Text(
                            text = "₹${FinancialCalculationEngine.formatInrShort(capturedInr)}",
                            style = MaterialTheme.typography.titleLarge,
                            color = PositiveEmerald
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Outstanding / Pending", style = MaterialTheme.typography.bodySmall, color = SecondaryInk)
                        Text(
                            text = "₹${FinancialCalculationEngine.formatInrShort(outstandingInr)}",
                            style = MaterialTheme.typography.titleLarge,
                            color = WarningAmberTone
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { capturedRatio },
                    color = PositiveEmerald,
                    trackColor = WarningAmberTone.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                )
                Spacer(modifier = Modifier.height(10.dp))
                portfolio.payments.take(6).forEach { pay ->
                    val ord = orders.firstOrNull { it.id == pay.exportOrderId }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (ord != null) onSelectOrderAndNavigate(ord.id, "paypal_payments")
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${ord?.orderNumber ?: pay.exportOrderId} • ${pay.currency} ${String.format("%,.0f", pay.amount)} (${pay.paymentType})",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = PrimaryInk
                        )
                        StatusPill(
                            label = pay.paymentStatus,
                            tone = statusToneFor(pay.paymentStatus)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// OBJECTIVE 6: CONNECTED TRADE RISK INTELLIGENCE PANEL UI
// ============================================================================

@Composable
fun TradeRiskIntelligencePanel(
    alerts: List<TradeRiskAlert>,
    onInspectAlert: (TradeRiskAlert) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAllAlerts by rememberSaveable { mutableStateOf(false) }
    val criticalOrHighCount = alerts.count { it.severity in listOf("CRITICAL", "HIGH") }
    val visibleAlerts = if (showAllAlerts) alerts else alerts.take(4)

    WarmSurfaceCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trade_risk_intelligence_panel")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (criticalOrHighCount > 0) WarningAmberTone else PositiveEmerald,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Trade Risk Intelligence Engine",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryInk
                    )
                    Text(
                        text = "${alerts.size} Active Alerts • $criticalOrHighCount High/Critical Priority",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = SecondaryInk
                    )
                }
            }
            if (alerts.size > 4) {
                Text(
                    text = if (showAllAlerts) "Collapse" else "View All (${alerts.size})",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = LightGoldAccent,
                    modifier = Modifier.clickable { showAllAlerts = !showAllAlerts }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (alerts.isEmpty()) {
            Text(
                text = "All active export orders have verified documentation, positive margins, and reconciled payment milestones.",
                style = MaterialTheme.typography.bodyMedium,
                color = PositiveEmerald
            )
        } else {
            visibleAlerts.forEachIndexed { idx, alert ->
                if (idx > 0) {
                    HorizontalDivider(
                        color = WarmBorder,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onInspectAlert(alert) }
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = when (alert.severity) {
                                    "CRITICAL" -> CriticalCrimsonTone
                                    "HIGH" -> WarningAmberTone
                                    else -> TradeBlueAccent
                                },
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${alert.affectedOrderNumber} • ${alert.category}",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryInk
                            )
                        }
                        StatusPill(
                            label = "${alert.severity} • ${alert.resolutionStatus}",
                            tone = statusToneFor(alert.severity)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Evidence: ${alert.evidence}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryInk
                    )
                    Text(
                        text = alert.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryInk
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Recommended Action: ${alert.recommendedAction} →",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = LightGoldAccent
                    )
                }
            }
        }
    }
}
