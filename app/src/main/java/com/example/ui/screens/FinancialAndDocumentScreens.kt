package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.DocumentRecord
import com.example.data.model.ExportOrder
import com.example.data.model.FinancialAssessment
import com.example.domain.FinancialCalculationEngine
import com.example.ui.components.FintechTone
import com.example.ui.components.LargeMetricDisplay
import com.example.ui.components.MinimalFintechChart
import com.example.ui.components.RulerTickVisual
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.StatusPill
import com.example.ui.components.WarmSurfaceCard
import com.example.ui.components.statusToneFor
import com.example.ui.theme.ChampagneAccent
import com.example.ui.theme.CriticalCrimsonTone
import com.example.ui.theme.IvoryBackground
import com.example.ui.theme.IvorySecondaryBg
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LightGoldAccent
import com.example.ui.theme.OnGoldDarkInk
import com.example.ui.theme.PositiveEmerald
import com.example.ui.theme.PrimaryInk
import com.example.ui.theme.PureWhiteSurface
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.SoftChampagneBg
import com.example.ui.theme.SpaceGroteskFamily
import com.example.ui.theme.TradeBlueAccent
import com.example.ui.theme.WarmBorder
import com.example.ui.theme.WarningAmberTone
import com.example.ui.viewmodel.AiTaskState
import com.example.ui.viewmodel.NiryatPortfolioState

// ============================================================================
// SCREEN 3: FINANCIAL INTELLIGENCE (Warm Minimal Fintech Redesign)
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FinancialIntelligenceScreen(
    portfolio: NiryatPortfolioState,
    aiExplanationState: AiTaskState<String>,
    onSelectOrder: (String) -> Unit,
    onSaveAssessment: (
        order: ExportOrder,
        exchangeRate: Double,
        advancePercentage: Double,
        paymentFeePercentage: Double,
        paymentFixedFee: Double,
        procurementCostInr: Double,
        logisticsCostInr: Double,
        packagingCostInr: Double,
        otherCostsInr: Double,
        assumptionsNote: String
    ) -> Unit,
    onRequestAiExplanation: (ExportOrder, FinancialAssessment) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val order = portfolio.orders.firstOrNull { it.id == portfolio.selectedOrderId }
        ?: portfolio.orders.firstOrNull()

    if (order == null) {
        EmptySelectionFallback(title = "Financial Intelligence", onBack = onBack)
        return
    }

    val existing = portfolio.assessments.firstOrNull { it.exportOrderId == order.id }
    val defaultFx = existing?.exchangeRate ?: FinancialCalculationEngine.defaultExchangeRateForCurrency(order.currency)
    val defaultGrossInr = order.orderValue * defaultFx

    var fxRateSlider by rememberSaveable(order.id) { mutableFloatStateOf(defaultFx.toFloat()) }
    var advanceSlider by rememberSaveable(order.id) {
        mutableFloatStateOf((existing?.advancePercentage ?: order.advancePercentage).toFloat())
    }
    var paymentFeePctText by rememberSaveable(order.id) {
        mutableStateOf((existing?.paymentFeePercentage ?: 3.4).toString())
    }
    var procurementInrText by rememberSaveable(order.id) {
        mutableStateOf((existing?.procurementCostInr ?: (defaultGrossInr * 0.56)).toInt().toString())
    }
    var logisticsInrText by rememberSaveable(order.id) {
        mutableStateOf((existing?.logisticsCostInr ?: (defaultGrossInr * 0.09)).toInt().toString())
    }
    var packagingInrText by rememberSaveable(order.id) {
        mutableStateOf((existing?.packagingCostInr ?: (defaultGrossInr * 0.03)).toInt().toString())
    }
    var otherCostsInrText by rememberSaveable(order.id) {
        mutableStateOf((existing?.otherCostsInr ?: (defaultGrossInr * 0.02)).toInt().toString())
    }
    var showFormulas by rememberSaveable { mutableStateOf(false) }

    val liveAssessment = remember(
        order,
        fxRateSlider,
        advanceSlider,
        paymentFeePctText,
        procurementInrText,
        logisticsInrText,
        packagingInrText,
        otherCostsInrText
    ) {
        FinancialCalculationEngine.calculateAssessment(
            userId = order.userId,
            exportOrderId = order.id,
            existingId = existing?.id ?: order.id,
            quantity = order.quantity,
            unitPrice = order.unitPrice,
            currency = order.currency,
            exchangeRate = fxRateSlider.toDouble(),
            advancePercentage = advanceSlider.toDouble(),
            paymentFeePercentage = paymentFeePctText.toDoubleOrNull() ?: 3.4,
            paymentFixedFee = 0.30,
            procurementCostInr = procurementInrText.toDoubleOrNull() ?: 0.0,
            logisticsCostInr = logisticsInrText.toDoubleOrNull() ?: 0.0,
            packagingCostInr = packagingInrText.toDoubleOrNull() ?: 0.0,
            otherCostsInr = otherCostsInrText.toDoubleOrNull() ?: 0.0
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IvoryBackground),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp)
                .testTag("financial_intelligence_list"),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Minimal Header + Order Switcher
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("financial_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = PrimaryInk
                            )
                        }
                        Column {
                            Text(
                                text = "FINANCIAL INTELLIGENCE",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChampagneAccent,
                                fontFamily = JetBrainsMonoFamily
                            )
                            Text(
                                text = "${order.orderNumber} • ${order.buyerName}",
                                style = MaterialTheme.typography.titleLarge,
                                color = PrimaryInk,
                                fontFamily = SpaceGroteskFamily,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    val marginTone = when {
                        liveAssessment.profitMarginPercent >= 14.0 -> FintechTone.POSITIVE
                        liveAssessment.profitMarginPercent >= 6.0 -> FintechTone.WARNING
                        else -> FintechTone.CRITICAL
                    }
                    StatusPill(
                        label = "Margin ${liveAssessment.profitMarginPercent}%",
                        tone = marginTone
                    )
                }

                if (portfolio.orders.size > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        portfolio.orders.forEach { itemOrder ->
                            FilterChip(
                                selected = itemOrder.id == order.id,
                                onClick = { onSelectOrder(itemOrder.id) },
                                label = {
                                    Text(
                                        text = "${itemOrder.orderNumber} (${itemOrder.currency})",
                                        fontFamily = JetBrainsMonoFamily,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryInk,
                                    selectedLabelColor = PureWhiteSurface
                                )
                            )
                        }
                    }
                }
            }

            // Primary Hero Financial Card
            item {
                WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    LargeMetricDisplay(
                        label = "Estimated Net Export Profit (INR)",
                        primaryValue = "₹${FinancialCalculationEngine.formatInrShort(liveAssessment.estimatedProfitInr)}",
                        badgeText = "${liveAssessment.profitMarginPercent}% Net Margin",
                        badgeTone = if (liveAssessment.profitMarginPercent >= 12.0) FintechTone.POSITIVE else FintechTone.WARNING
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = WarmBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FinancialStatBox(
                            label = "GROSS VALUE",
                            value = "₹${FinancialCalculationEngine.formatInrShort(liveAssessment.convertedValueInr)}",
                            sub = "${order.currency} ${order.orderValue}",
                            modifier = Modifier.weight(1f)
                        )
                        FinancialStatBox(
                            label = "ADVANCE (${liveAssessment.advancePercentage.toInt()}%)",
                            value = "${order.currency} ${liveAssessment.advanceAmount}",
                            sub = "Upfront Collection",
                            modifier = Modifier.weight(1f)
                        )
                        FinancialStatBox(
                            label = "OUTSTANDING",
                            value = "${order.currency} ${liveAssessment.outstandingAmount}",
                            sub = "Receivable Exposure",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "REVENUE VS COST COMPOSITION (INR)",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryInk,
                        fontFamily = JetBrainsMonoFamily
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    MinimalFintechChart(
                        values = listOf(
                            liveAssessment.convertedValueInr,
                            liveAssessment.procurementCostInr,
                            liveAssessment.logisticsCostInr + liveAssessment.packagingCostInr + liveAssessment.otherCostsInr,
                            liveAssessment.paymentFeeInr,
                            liveAssessment.estimatedProfitInr.coerceAtLeast(0.0)
                        ),
                        labels = listOf("Gross INR", "Procure", "Logistics", "Pay Fee", "Net Profit")
                    )
                }
            }

            // Ruler-Tick FX & Cost Assumptions Simulator
            item {
                WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    SectionHeaderRow(
                        title = "FX & Cost Structure Simulator",
                        trailingText = "Save Assessment",
                        onTrailingClick = {
                            onSaveAssessment(
                                order,
                                fxRateSlider.toDouble(),
                                advanceSlider.toDouble(),
                                paymentFeePctText.toDoubleOrNull() ?: 3.4,
                                0.30,
                                procurementInrText.toDoubleOrNull() ?: 0.0,
                                logisticsInrText.toDoubleOrNull() ?: 0.0,
                                packagingInrText.toDoubleOrNull() ?: 0.0,
                                otherCostsInrText.toDoubleOrNull() ?: 0.0,
                                "Deterministic assessment saved from Financial Intelligence"
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Exchange Rate: 1 ${order.currency} = ₹${String.format("%.2f", fxRateSlider)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = PrimaryInk,
                            fontFamily = JetBrainsMonoFamily
                        )
                        Text(
                            text = "Advance: ${advanceSlider.toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = ChampagneAccent,
                            fontFamily = JetBrainsMonoFamily
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    RulerTickVisual(
                        progressFraction = ((fxRateSlider - 65f) / 50f).coerceIn(0.08f, 0.92f)
                    )
                    Slider(
                        value = fxRateSlider,
                        onValueChange = { fxRateSlider = it },
                        valueRange = 65f..115f,
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryInk,
                            activeTrackColor = ChampagneAccent,
                            inactiveTrackColor = IvorySecondaryBg
                        ),
                        modifier = Modifier.testTag("slider_fx_rate")
                    )

                    Text(
                        text = "Advance Deposit Percentage (${advanceSlider.toInt()}%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryInk
                    )
                    Slider(
                        value = advanceSlider,
                        onValueChange = { advanceSlider = it },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryInk,
                            activeTrackColor = PositiveEmerald,
                            inactiveTrackColor = IvorySecondaryBg
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MinimalFormTextField(
                            value = procurementInrText,
                            onValueChange = { procurementInrText = it },
                            label = "Procurement (INR)",
                            keyboardType = KeyboardType.Number,
                            isMonospace = true,
                            modifier = Modifier.weight(1f)
                        )
                        MinimalFormTextField(
                            value = logisticsInrText,
                            onValueChange = { logisticsInrText = it },
                            label = "Logistics (INR)",
                            keyboardType = KeyboardType.Number,
                            isMonospace = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MinimalFormTextField(
                            value = packagingInrText,
                            onValueChange = { packagingInrText = it },
                            label = "Packaging (INR)",
                            keyboardType = KeyboardType.Number,
                            isMonospace = true,
                            modifier = Modifier.weight(1f)
                        )
                        MinimalFormTextField(
                            value = paymentFeePctText,
                            onValueChange = { paymentFeePctText = it },
                            label = "Gateway Fee %",
                            keyboardType = KeyboardType.Decimal,
                            isMonospace = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onSaveAssessment(
                                order,
                                fxRateSlider.toDouble(),
                                advanceSlider.toDouble(),
                                paymentFeePctText.toDoubleOrNull() ?: 3.4,
                                0.30,
                                procurementInrText.toDoubleOrNull() ?: 0.0,
                                logisticsInrText.toDoubleOrNull() ?: 0.0,
                                packagingInrText.toDoubleOrNull() ?: 0.0,
                                otherCostsInrText.toDoubleOrNull() ?: 0.0,
                                "Deterministic assessment saved from Financial Intelligence"
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ChampagneAccent,
                            contentColor = OnGoldDarkInk
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("commit_financial_recalc_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = null,
                            tint = OnGoldDarkInk,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Deterministic Financial Assessment", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Objective 6: Hypothetical Financial What-If Scenario Simulator (Separated from Saved Record)
            item {
                var whatIfFxShiftPct by rememberSaveable(order.id) { mutableFloatStateOf(0f) }
                var whatIfLogisticsShiftPct by rememberSaveable(order.id) { mutableFloatStateOf(0f) }

                val baseRecord = existing ?: liveAssessment
                val simulatedFx = (baseRecord.exchangeRate * (1.0 + whatIfFxShiftPct / 100.0)).coerceAtLeast(0.01)
                val simulatedLogistics = (baseRecord.logisticsCostInr * (1.0 + whatIfLogisticsShiftPct / 100.0)).coerceAtLeast(0.0)
                val hypotheticalAssessment = remember(baseRecord, simulatedFx, simulatedLogistics) {
                    FinancialCalculationEngine.calculateAssessment(
                        userId = order.userId,
                        exportOrderId = order.id,
                        existingId = "hypothetical_${order.id}",
                        quantity = order.quantity,
                        unitPrice = order.unitPrice,
                        currency = order.currency,
                        exchangeRate = simulatedFx,
                        advancePercentage = baseRecord.advancePercentage,
                        paymentFeePercentage = baseRecord.paymentFeePercentage,
                        paymentFixedFee = baseRecord.paymentFixedFee,
                        procurementCostInr = baseRecord.procurementCostInr,
                        logisticsCostInr = simulatedLogistics,
                        packagingCostInr = baseRecord.packagingCostInr,
                        otherCostsInr = baseRecord.otherCostsInr,
                        assumptionsNote = "Hypothetical What-If Stress Scenario (Does not overwrite saved record)"
                    )
                }
                val profitDeltaInr = hypotheticalAssessment.estimatedProfitInr - baseRecord.estimatedProfitInr

                WarmSurfaceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("what_if_simulator_card"),
                    borderColor = TradeBlueAccent.copy(alpha = 0.55f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "HYPOTHETICAL WHAT-IF SCENARIO SIMULATOR",
                                style = MaterialTheme.typography.labelSmall,
                                color = TradeBlueAccent,
                                fontFamily = JetBrainsMonoFamily
                            )
                            Text(
                                text = "FX & Freight Stress Test (Isolated from Saved Record)",
                                style = MaterialTheme.typography.titleMedium,
                                color = PrimaryInk
                            )
                        }
                        StatusPill(
                            label = "SANDBOX SIMULATION",
                            tone = FintechTone.TRADE_BLUE
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "FX Shock (${order.currency}/INR): ${if (whatIfFxShiftPct >= 0) "+" else ""}${String.format("%.1f", whatIfFxShiftPct)}% → Simulated FX ₹${String.format("%.2f", simulatedFx)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFamily,
                        color = PrimaryInk
                    )
                    Slider(
                        value = whatIfFxShiftPct,
                        onValueChange = { whatIfFxShiftPct = it },
                        valueRange = -10f..10f,
                        colors = SliderDefaults.colors(
                            thumbColor = TradeBlueAccent,
                            activeTrackColor = TradeBlueAccent,
                            inactiveTrackColor = WarmBorder
                        )
                    )

                    Text(
                        text = "Freight & Logistics Surge: ${if (whatIfLogisticsShiftPct >= 0) "+" else ""}${String.format("%.1f", whatIfLogisticsShiftPct)}% → Simulated ₹${FinancialCalculationEngine.formatInrShort(simulatedLogistics)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFamily,
                        color = PrimaryInk
                    )
                    Slider(
                        value = whatIfLogisticsShiftPct,
                        onValueChange = { whatIfLogisticsShiftPct = it },
                        valueRange = -20f..30f,
                        colors = SliderDefaults.colors(
                            thumbColor = ChampagneAccent,
                            activeTrackColor = ChampagneAccent,
                            inactiveTrackColor = WarmBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FinancialStatBox(
                            label = "SAVED AUTHORITATIVE",
                            value = "₹${FinancialCalculationEngine.formatInrShort(baseRecord.estimatedProfitInr)}",
                            sub = "Margin ${baseRecord.profitMarginPercent}% @ FX ₹${baseRecord.exchangeRate}",
                            modifier = Modifier.weight(1f)
                        )
                        FinancialStatBox(
                            label = "HYPOTHETICAL SCENARIO",
                            value = "₹${FinancialCalculationEngine.formatInrShort(hypotheticalAssessment.estimatedProfitInr)}",
                            sub = "Margin ${hypotheticalAssessment.profitMarginPercent}% (${if (profitDeltaInr >= 0) "+" else ""}₹${FinancialCalculationEngine.formatInrShort(profitDeltaInr)})",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Expandable Deterministic Formula Breakdown
            item {
                WarmSurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showFormulas = !showFormulas }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "DETERMINISTIC FORMULA AUDIT",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryInk,
                                fontFamily = JetBrainsMonoFamily
                            )
                            Text(
                                text = "Transparent INR Revenue, Fee & Margin Math",
                                style = MaterialTheme.typography.titleMedium,
                                color = PrimaryInk
                            )
                        }
                        Icon(
                            imageVector = if (showFormulas) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = SecondaryInk
                        )
                    }

                    AnimatedVisibility(visible = showFormulas) {
                        Column(
                            modifier = Modifier.padding(top = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalDivider(color = WarmBorder)
                            FormulaAuditLine(
                                "Gross Order Value",
                                "${order.quantity} × ${order.unitPrice}",
                                "${order.currency} ${liveAssessment.grossOrderValue}"
                            )
                            FormulaAuditLine(
                                "Converted Value (INR)",
                                "Gross × ₹${liveAssessment.exchangeRate}",
                                "₹${FinancialCalculationEngine.formatInrShort(liveAssessment.convertedValueInr)}"
                            )
                            FormulaAuditLine(
                                "Cross-Border Payment Fee",
                                "${liveAssessment.paymentFeePercentage}% + ${liveAssessment.paymentFixedFee}",
                                "- ₹${FinancialCalculationEngine.formatInrShort(liveAssessment.paymentFeeInr)}"
                            )
                            FormulaAuditLine(
                                "Estimated Net Revenue",
                                "Converted INR − Gateway Fee",
                                "₹${FinancialCalculationEngine.formatInrShort(liveAssessment.estimatedNetRevenueInr)}"
                            )
                            FormulaAuditLine(
                                "Estimated Net Profit",
                                "Net Revenue − Operating Costs",
                                "₹${FinancialCalculationEngine.formatInrShort(liveAssessment.estimatedProfitInr)} (${liveAssessment.profitMarginPercent}%)"
                            )
                        }
                    }
                }
            }

            // Contextual Gemini Financial Viability Explanation
            item {
                WarmSurfaceCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = SoftChampagneBg.copy(alpha = 0.55f),
                    borderColor = ChampagneAccent.copy(alpha = 0.5f)
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
                                    text = "GEMINI TRADE FINANCE ANALYST",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ChampagneAccent,
                                    fontFamily = JetBrainsMonoFamily
                                )
                                Text(
                                    text = "Contextual Viability & Risk Memo",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PrimaryInk
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { onRequestAiExplanation(order, liveAssessment) },
                            enabled = aiExplanationState !is AiTaskState.Loading,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, PrimaryInk),
                            modifier = Modifier.testTag("generate_financial_advisory_button")
                        ) {
                            if (aiExplanationState is AiTaskState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = PrimaryInk
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Analyzing...", style = MaterialTheme.typography.labelSmall, color = PrimaryInk)
                            } else {
                                Text("Explain Viability", style = MaterialTheme.typography.labelSmall, color = PrimaryInk)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    when (aiExplanationState) {
                        is AiTaskState.Idle -> {
                            Text(
                                text = "Tap 'Explain Viability' to generate an executive trade-finance interpretation grounded strictly in the deterministic numbers above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryInk
                            )
                        }
                        is AiTaskState.Loading -> {
                            Text(
                                text = "Generating contextual financial explanation...",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryInk
                            )
                        }
                        is AiTaskState.Success -> {
                            Text(
                                text = aiExplanationState.data,
                                style = MaterialTheme.typography.bodySmall,
                                color = PrimaryInk
                            )
                        }
                        is AiTaskState.Error -> {
                            Text(
                                text = aiExplanationState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = CriticalCrimsonTone
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun FinancialStatBox(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = IvoryBackground,
        border = BorderStroke(1.dp, WarmBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = SecondaryInk,
                fontFamily = JetBrainsMonoFamily
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = PrimaryInk,
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryInk
            )
        }
    }
}

@Composable
private fun FormulaAuditLine(label: String, formula: String, result: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = PrimaryInk)
            Text(text = formula, style = MaterialTheme.typography.bodySmall, color = SecondaryInk, fontFamily = JetBrainsMonoFamily)
        }
        Text(
            text = result,
            style = MaterialTheme.typography.labelLarge,
            color = PrimaryInk,
            fontFamily = JetBrainsMonoFamily
        )
    }
}

// ============================================================================
// SCREEN 4: DOCUMENT CENTRE (Warm Minimal Checklist + Expandable Detail Cards)
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentCentreScreen(
    portfolio: NiryatPortfolioState,
    onSelectOrder: (String) -> Unit,
    onInitializeChecklist: (String) -> Unit,
    onUpdateDocument: (
        doc: DocumentRecord,
        newRequirementLevel: String,
        newStatus: String,
        newFileName: String,
        newFileReference: String,
        newReviewerNote: String
    ) -> Unit,
    onAddCustomDocument: (String, String, String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val order = portfolio.orders.firstOrNull { it.id == portfolio.selectedOrderId }
        ?: portfolio.orders.firstOrNull()

    if (order == null) {
        EmptySelectionFallback(title = "Document Centre", onBack = onBack)
        return
    }

    val context = LocalContext.current
    val orderDocs = portfolio.documents.filter { it.exportOrderId == order.id }
    val (readinessPct, docCounts) = FinancialCalculationEngine.calculateDocumentReadiness(orderDocs)
    val (verifiedReqCount, totalReqCount) = docCounts

    var activeUploadDoc by remember { mutableStateOf<DocumentRecord?>(null) }
    var previewDialogDoc by remember { mutableStateOf<DocumentRecord?>(null) }
    var expandedDocId by rememberSaveable(order.id) { mutableStateOf<String?>(orderDocs.firstOrNull()?.id) }
    var customDocName by rememberSaveable { mutableStateOf("") }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val targetDoc = activeUploadDoc
        if (uri != null && targetDoc != null) {
            var displayName = "${targetDoc.documentType.lowercase().replace(' ', '_')}_${order.orderNumber}.pdf"
            var sizeBytes = 0L
            val mimeType = context.contentResolver.getType(uri) ?: "application/pdf"
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }

            // Copy file to app-local export_docs storage and compute SHA-256 checksum
            var persistedRef = uri.toString()
            var sha256Hex = ""
            runCatching {
                val docsDir = java.io.File(context.filesDir, "export_docs/${order.orderNumber}")
                docsDir.mkdirs()
                val safeFileName = "v${targetDoc.versionNumber + 1}_${displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
                val destFile = java.io.File(docsDir, safeFileName)
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        var totalRead = 0L
                        while (input.read(buffer).also { read = it } != -1) {
                            totalRead += read
                            if (totalRead <= 15L * 1024L * 1024L) {
                                output.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                            }
                        }
                        if (sizeBytes <= 0L) sizeBytes = totalRead
                    }
                }
                persistedRef = destFile.absolutePath
                sha256Hex = digest.digest().joinToString("") { "%02x".format(it) }
            }

            val sizeKb = (sizeBytes / 1024L).coerceAtLeast(1L)
            onUpdateDocument(
                targetDoc,
                targetDoc.requirementLevel,
                "UPLOADED",
                displayName,
                persistedRef,
                "Uploaded v${targetDoc.versionNumber + 1} ($mimeType • ${sizeKb}KB • SHA256:${sha256Hex.take(12)})"
            )
        }
    }

    val currentPreviewDoc = previewDialogDoc
    if (currentPreviewDoc != null) {
        AlertDialog(
            onDismissRequest = { previewDialogDoc = null },
            containerColor = PureWhiteSurface,
            title = {
                Text(
                    text = currentPreviewDoc.documentType,
                    style = MaterialTheme.typography.titleMedium,
                    color = PrimaryInk
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Order Ref: ${order.orderNumber} • ${order.buyerName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = ChampagneAccent,
                        fontFamily = JetBrainsMonoFamily
                    )
                    HorizontalDivider(color = WarmBorder)
                    Text(
                        text = "File Name: ${currentPreviewDoc.fileName.ifBlank { "No file attached yet" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryInk,
                        fontFamily = JetBrainsMonoFamily
                    )
                    Text(
                        text = "Requirement: ${currentPreviewDoc.requirementLevel} • Status: ${currentPreviewDoc.status} • Version: v${currentPreviewDoc.versionNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryInk,
                        fontFamily = JetBrainsMonoFamily
                    )
                    if (currentPreviewDoc.sha256Checksum.isNotBlank()) {
                        Text(
                            text = "SHA-256: ${currentPreviewDoc.sha256Checksum.take(24)}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryInk,
                            fontFamily = JetBrainsMonoFamily
                        )
                    }
                    if (currentPreviewDoc.reviewerNote.isNotBlank()) {
                        Text(
                            text = "Reviewer Notes: ${currentPreviewDoc.reviewerNote}",
                            style = MaterialTheme.typography.bodySmall,
                            color = PrimaryInk
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { previewDialogDoc = null }) {
                    Text("Close Preview", color = PrimaryInk)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IvoryBackground),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp)
                .testTag("document_centre_list"),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("document_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = PrimaryInk
                            )
                        }
                        Column {
                            Text(
                                text = "EXPORT DOCUMENT CENTRE",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChampagneAccent,
                                fontFamily = JetBrainsMonoFamily
                            )
                            Text(
                                text = "${order.orderNumber} Checklist",
                                style = MaterialTheme.typography.titleLarge,
                                color = PrimaryInk,
                                fontFamily = SpaceGroteskFamily,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    StatusPill(
                        label = "${readinessPct.toInt()}% Verified",
                        tone = if (readinessPct >= 80.0) FintechTone.POSITIVE else FintechTone.WARNING
                    )
                }

                if (portfolio.orders.size > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        portfolio.orders.forEach { itemOrder ->
                            FilterChip(
                                selected = itemOrder.id == order.id,
                                onClick = { onSelectOrder(itemOrder.id) },
                                label = {
                                    Text(
                                        text = itemOrder.orderNumber,
                                        fontFamily = JetBrainsMonoFamily,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryInk,
                                    selectedLabelColor = PureWhiteSurface
                                )
                            )
                        }
                    }
                }
            }

            // Readiness Progress Card
            item {
                WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MANDATORY COMPLIANCE READINESS",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryInk,
                                fontFamily = JetBrainsMonoFamily
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$verifiedReqCount of $totalReqCount Required Documents Verified",
                                style = MaterialTheme.typography.titleMedium,
                                color = PrimaryInk,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "LOCALLY STORED ON DEVICE — NOT UPLOADED TO CLOUD OR OFFICIALLY CERTIFIED",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChampagneAccent,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("local_document_storage_notice")
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Files are stored in app-local storage (filesDir/export_docs) with SHA-256 checksums and version history (v1, v2...). Uploaded files are not counted as verified until reviewed, and checklist readiness does not constitute official customs, ICEGATE, or DGFT regulatory approval.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryInk
                            )
                        }
                        Text(
                            text = "${readinessPct.toInt()}%",
                            style = MaterialTheme.typography.headlineMedium,
                            color = PrimaryInk,
                            fontFamily = SpaceGroteskFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { (readinessPct / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (readinessPct >= 80.0) PositiveEmerald else ChampagneAccent,
                        trackColor = IvorySecondaryBg
                    )

                    if (orderDocs.isEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onInitializeChecklist(order.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryInk,
                                contentColor = PureWhiteSurface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Initialize Standard 8-Document Checklist")
                        }
                    }
                }
            }

            // Expandable Document Checklist Cards
            items(orderDocs, key = { it.id }) { doc ->
                val isExpanded = expandedDocId == doc.id

                WarmSurfaceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("doc_card_${doc.documentType}"),
                    onClick = { expandedDocId = if (isExpanded) null else doc.id }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = IvoryBackground,
                                border = BorderStroke(1.dp, WarmBorder),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (doc.status == "VERIFIED") Icons.Default.CheckCircle else Icons.Default.Description,
                                        contentDescription = null,
                                        tint = if (doc.status == "VERIFIED") PositiveEmerald else ChampagneAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "${doc.documentType} (v${doc.versionNumber})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PrimaryInk
                                )
                                Text(
                                    text = "${doc.requirementLevel} • ${doc.fileName.ifBlank { "Awaiting attachment" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryInk,
                                    fontFamily = JetBrainsMonoFamily
                                )
                            }
                        }

                        StatusPill(
                            label = doc.status.replace('_', ' '),
                            tone = statusToneFor(doc.status)
                        )
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            modifier = Modifier.padding(top = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(color = WarmBorder)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        activeUploadDoc = doc
                                        openDocumentLauncher.launch(arrayOf("application/pdf", "image/*"))
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PrimaryInk,
                                        contentColor = PureWhiteSurface
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("upload_doc_btn_${doc.documentType}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = "Upload Document",
                                        modifier = Modifier.size(16.dp),
                                        tint = ChampagneAccent
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Select File", style = MaterialTheme.typography.labelMedium)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val generatedName = "${doc.documentType.lowercase().replace(' ', '_')}_${order.orderNumber}.pdf"
                                        onUpdateDocument(
                                            doc,
                                            doc.requirementLevel,
                                            "UPLOADED",
                                            generatedName,
                                            "content://niryatflow.local/documents/$generatedName",
                                            "Generated export compliance draft"
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, WarmBorder),
                                    modifier = Modifier.testTag("quick_attach_btn_${doc.documentType}")
                                ) {
                                    Text("Attach Draft PDF", style = MaterialTheme.typography.labelMedium, color = PrimaryInk)
                                }

                                if (doc.fileName.isNotBlank()) {
                                    IconButton(onClick = { previewDialogDoc = doc }) {
                                        Icon(
                                            imageVector = Icons.Default.Visibility,
                                            contentDescription = "Preview Document",
                                            tint = PrimaryInk
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "UPDATE VERIFICATION STATUS",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryInk,
                                fontFamily = JetBrainsMonoFamily
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "MISSING",
                                    "UPLOADED",
                                    "UNDER_REVIEW",
                                    "VERIFIED",
                                    "REJECTED",
                                    "REUPLOAD_REQUIRED"
                                ).forEach { st ->
                                    FilterChip(
                                        selected = doc.status == st,
                                        onClick = {
                                            onUpdateDocument(
                                                doc,
                                                doc.requirementLevel,
                                                st,
                                                doc.fileName.ifBlank { "${doc.documentType.lowercase().replace(' ', '_')}.pdf" },
                                                doc.fileReference.ifBlank { "local://verified" },
                                                "Updated status to $st"
                                            )
                                        },
                                        label = { Text(st.replace('_', ' '), style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PrimaryInk,
                                            selectedLabelColor = PureWhiteSurface
                                        ),
                                        modifier = Modifier.testTag("status_chip_${doc.documentType}_$st")
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("REQUIRED", "OPTIONAL", "NOT_APPLICABLE").forEach { reqLevel ->
                                    FilterChip(
                                        selected = doc.requirementLevel == reqLevel,
                                        onClick = {
                                            onUpdateDocument(
                                                doc,
                                                reqLevel,
                                                doc.status,
                                                doc.fileName,
                                                doc.fileReference,
                                                doc.reviewerNote
                                            )
                                        },
                                        label = { Text(reqLevel.replace('_', ' '), style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Add Custom Document Row
            item {
                WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "ADD CUSTOM EXPORT DOCUMENT",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryInk,
                        fontFamily = JetBrainsMonoFamily
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MinimalFormTextField(
                            value = customDocName,
                            onValueChange = { customDocName = it },
                            label = "Custom Document Title (e.g. Phytosanitary Certificate)",
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (customDocName.isNotBlank()) {
                                    onAddCustomDocument(order.id, customDocName, "REQUIRED")
                                    customDocName = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryInk,
                                contentColor = PureWhiteSurface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun EmptySelectionFallback(title: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IvoryBackground)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = PrimaryInk,
            fontFamily = SpaceGroteskFamily
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "No active export order selected. Create or select an export order from the dashboard.",
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryInk
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryInk, contentColor = PureWhiteSurface)
        ) {
            Text("Back to Home")
        }
    }
}
