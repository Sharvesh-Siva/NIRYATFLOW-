package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.ExportOrder
import com.example.data.model.LifecycleStageStatus
import com.example.data.model.PaymentRecord
import com.example.domain.FinancialCalculationEngine
import com.example.ui.components.FintechTone
import com.example.ui.components.LargeMetricDisplay
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.StatusPill
import com.example.ui.components.WarmSurfaceCard
import com.example.ui.components.statusToneFor
import com.example.ui.theme.ChampagneAccent
import com.example.ui.theme.CriticalCrimsonTone
import com.example.ui.theme.IvoryBackground
import com.example.ui.theme.IvorySecondaryBg
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MutedInk
import com.example.ui.theme.PositiveEmerald
import com.example.ui.theme.PositiveEmeraldBg
import com.example.ui.theme.PrimaryInk
import com.example.ui.theme.PureWhiteSurface
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.SoftChampagneBg
import com.example.ui.theme.SpaceGroteskFamily
import com.example.ui.theme.WarmBorder
import com.example.ui.theme.WarningAmberBg
import com.example.ui.theme.WarningAmberTone
import com.example.ui.viewmodel.NiryatPortfolioState
import java.text.SimpleDateFormat
import java.util.Locale

// ============================================================================
// SCREEN 5: PAYPAL PAYMENTS (Warm Minimal International Settlement Flow)
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PayPalPaymentsScreen(
    portfolio: NiryatPortfolioState,
    isPayPalConfigured: Boolean,
    isPaymentBusy: Boolean,
    paypalAuthCheckStatus: String?,
    onVerifyPayPalOAuth: () -> Unit,
    onSelectOrder: (String) -> Unit,
    onCreatePayPalOrder: (ExportOrder, Double, String) -> Unit,
    onCapturePayPalOrder: (PaymentRecord) -> Unit,
    onReconcilePayPalOrder: (PaymentRecord) -> Unit,
    onFailDemoPayment: (PaymentRecord) -> Unit = {},
    onCancelDemoPayment: (PaymentRecord) -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val order = portfolio.orders.firstOrNull { it.id == portfolio.selectedOrderId }
        ?: portfolio.orders.firstOrNull()

    if (order == null) {
        EmptySelectionFallback(title = "PayPal International Payments", onBack = onBack)
        return
    }

    val context = LocalContext.current
    val isDemoMode = portfolio.isSampleWorkspace
    val orderPayments = portfolio.payments.filter { it.exportOrderId == order.id }
    val totalCaptured = orderPayments
        .filter { FinancialCalculationEngine.isPaymentSettled(it.paymentStatus) }
        .sumOf { it.amount }
    val pendingAmount = (order.orderValue - totalCaptured).coerceAtLeast(0.0)
    val collectionPct = if (order.orderValue > 0.0) {
        ((totalCaptured / order.orderValue) * 100.0).toInt().coerceIn(0, 100)
    } else 0

    var selectedPaymentType by rememberSaveable(order.id) { mutableStateOf("ADVANCE") }
    var amountText by rememberSaveable(order.id) {
        mutableStateOf(
            String.format(Locale.US, "%.2f", order.orderValue * (order.advancePercentage / 100.0))
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
                .testTag("paypal_payments_list"),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with Mode Pill
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("paypal_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = PrimaryInk
                            )
                        }
                        Column {
                            Text(
                                text = if (isDemoMode) "ZERO-COST DEMO PAYMENT ENGINE" else "INTERNATIONAL SETTLEMENTS",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChampagneAccent,
                                fontFamily = JetBrainsMonoFamily
                            )
                            Text(
                                text = if (isDemoMode) "Simulated Export Settlement Hub" else "PayPal Cross-Border Hub",
                                style = MaterialTheme.typography.titleLarge,
                                color = PrimaryInk,
                                fontFamily = SpaceGroteskFamily,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    StatusPill(
                        label = if (isDemoMode) "DEMO MODE" else "SANDBOX / TEST ENV",
                        tone = FintechTone.ACCENT_GOLD
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

            // Mandatory Simulated Payment Disclaimer Banner in Demo Mode, or OAuth Status in Production Mode
            item {
                if (isDemoMode) {
                    WarmSurfaceCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("simulated_payment_banner"),
                        backgroundColor = WarningAmberBg,
                        borderColor = ChampagneAccent
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
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = ChampagneAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ChampagneAccent,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("simulated_payment_banner_text")
                                    )
                                    Text(
                                        text = "Demo Mode runs locally via DemoPaymentService (status: DEMO_CAPTURED / DEMO_PENDING / DEMO_FAILED / DEMO_CANCELLED). No Firebase Blaze upgrade, Cloud Functions deployment, or PAYPAL_BACKEND_BASE_URL is required.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PrimaryInk
                                    )
                                }
                            }
                        }
                    }
                } else {
                    WarmSurfaceCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = if (isPayPalConfigured) PureWhiteSurface else WarningAmberBg,
                        borderColor = if (isPayPalConfigured) WarmBorder else WarningAmberTone.copy(alpha = 0.4f)
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
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (isPayPalConfigured) PositiveEmerald else WarningAmberTone,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (isPayPalConfigured) {
                                            "PAYPAL SANDBOX BACKEND CONNECTED (ZERO CLIENT SECRET IN APK)"
                                        } else {
                                            "PAYPAL SANDBOX BACKEND CONFIGURATION PENDING"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isPayPalConfigured) PositiveEmerald else WarningAmberTone,
                                        fontFamily = JetBrainsMonoFamily
                                    )
                                    Text(
                                        text = if (isPayPalConfigured) {
                                            "Authenticated Cloud Function connected to api-m.sandbox.paypal.com (Secret in Firebase Secret Manager)."
                                        } else {
                                            "Production Mode never silently fakes payments. Set PAYPAL_BACKEND_BASE_URL or switch to Sample Business Workspace for Zero-Cost Demo Mode."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PrimaryInk
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = onVerifyPayPalOAuth,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, PrimaryInk),
                                modifier = Modifier.testTag("test_paypal_oauth_inline_button")
                            ) {
                                Text("Verify OAuth", style = MaterialTheme.typography.labelSmall, color = PrimaryInk)
                            }
                        }

                        if (!paypalAuthCheckStatus.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = paypalAuthCheckStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (paypalAuthCheckStatus.startsWith("PASS") || paypalAuthCheckStatus.startsWith("DEMO")) PositiveEmerald else CriticalCrimsonTone,
                                fontFamily = JetBrainsMonoFamily
                            )
                        }
                    }
                }
            }

            // Payment Summary Card + 5-Step Progression Bar
            item {
                WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        LargeMetricDisplay(
                            label = if (isDemoMode) "Simulated Captured Receipts (DEMO_CAPTURED)" else "Verified Captured Receipts",
                            primaryValue = "${order.currency} ${String.format("%,.2f", totalCaptured)}",
                            badgeText = "$collectionPct% Settled",
                            badgeTone = if (collectionPct >= 30) FintechTone.POSITIVE else FintechTone.WARNING,
                            modifier = Modifier.weight(1f)
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "ORDER VALUE",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryInk,
                                fontFamily = JetBrainsMonoFamily
                            )
                            Text(
                                text = "${order.currency} ${String.format("%,.2f", order.orderValue)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = PrimaryInk,
                                fontFamily = JetBrainsMonoFamily
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Pending: ${order.currency} ${String.format("%,.2f", pendingAmount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = WarningAmberTone,
                                fontFamily = JetBrainsMonoFamily
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { (collectionPct / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = PositiveEmerald,
                        trackColor = IvorySecondaryBg
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = WarmBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "5-STEP PAYMENT PROGRESSION",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryInk,
                        fontFamily = JetBrainsMonoFamily
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val latestStatus = orderPayments.firstOrNull()?.paymentStatus
                    val activeStepIdx = when (latestStatus) {
                        null, "NOT_INITIATED" -> -1
                        "PENDING_APPROVAL", "DEMO_PENDING" -> 1
                        "APPROVED" -> 2
                        "CAPTURED", "DEMO_CAPTURED" -> 4
                        "DEMO_FAILED", "DEMO_CANCELLED", "FAILED", "CANCELLED" -> 0
                        else -> 1
                    }
                    val steps = listOf(
                        "1. Created",
                        "2. Pending",
                        "3. Processing",
                        "4. Verified",
                        "5. Settled"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        steps.forEachIndexed { idx, stepLabel ->
                            val isDone = idx <= activeStepIdx
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDone) SoftChampagneBg else IvoryBackground,
                                border = BorderStroke(1.dp, if (isDone) ChampagneAccent else WarmBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stepLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDone) PrimaryInk else MutedInk,
                                    fontWeight = if (isDone) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Create New Payment Card (Simulated in Demo Mode, PayPal Backend in Production Mode)
            item {
                WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (isDemoMode) {
                            "INITIATE SIMULATED PAYMENT (IDEMPOTENT DEMO ENGINE)"
                        } else {
                            "INITIATE PAYPAL SANDBOX CHECKOUT ORDER"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryInk,
                        fontFamily = JetBrainsMonoFamily
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isDemoMode) "Create Simulated Buyer Milestone" else "Create Buyer Milestone Invoice",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryInk
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "ADVANCE" to "Advance (${order.advancePercentage.toInt()}%)",
                            "BALANCE" to "Balance Settlement",
                            "FULL" to "Full Invoice"
                        ).forEach { (typeKey, label) ->
                            FilterChip(
                                selected = selectedPaymentType == typeKey,
                                onClick = {
                                    selectedPaymentType = typeKey
                                    amountText = when (typeKey) {
                                        "ADVANCE" -> String.format(Locale.US, "%.2f", order.orderValue * (order.advancePercentage / 100.0))
                                        "BALANCE" -> String.format(Locale.US, "%.2f", pendingAmount)
                                        else -> String.format(Locale.US, "%.2f", order.orderValue)
                                    }
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryInk,
                                    selectedLabelColor = PureWhiteSurface
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    MinimalFormTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = "Milestone Amount (${order.currency})",
                        keyboardType = KeyboardType.Decimal,
                        isMonospace = true,
                        testTag = "input_paypal_amount"
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull() ?: 0.0
                            if (amt > 0.0) {
                                onCreatePayPalOrder(order, amt, selectedPaymentType)
                            }
                        },
                        enabled = !isPaymentBusy,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryInk,
                            contentColor = PureWhiteSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("create_paypal_order_button")
                    ) {
                        if (isPaymentBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = PureWhiteSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Processing Payment Request...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = null,
                                tint = ChampagneAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isDemoMode) {
                                    "Create Simulated Payment (${order.currency} $amountText)"
                                } else {
                                    "Create PayPal Sandbox Order (${order.currency} $amountText)"
                                }
                            )
                        }
                    }
                }
            }

            // Transaction History
            item {
                SectionHeaderRow(
                    title = if (isDemoMode) {
                        "Simulated Transaction Ledger (${orderPayments.size})"
                    } else {
                        "PayPal Transaction Ledger (${orderPayments.size})"
                    }
                )
            }

            if (orderPayments.isEmpty()) {
                item {
                    WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No payment transactions initiated for ${order.orderNumber} yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryInk
                        )
                    }
                }
            } else {
                items(orderPayments, key = { it.id }) { payment ->
                    val isSettled = FinancialCalculationEngine.isPaymentSettled(payment.paymentStatus)
                    WarmSurfaceCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payment_record_card_${payment.id}")
                    ) {
                        if (isDemoMode || payment.paymentStatus.startsWith("DEMO_")) {
                            Text(
                                text = "SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChampagneAccent,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${payment.paymentType} Milestone",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PrimaryInk
                                )
                                Text(
                                    text = "Transaction Ref: ${payment.paypalOrderId.ifBlank { "NOT_INITIATED" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondaryInk,
                                    fontFamily = JetBrainsMonoFamily
                                )
                            }
                            StatusPill(
                                label = payment.paymentStatus,
                                tone = statusToneFor(payment.paymentStatus)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("AMOUNT", style = MaterialTheme.typography.labelSmall, color = SecondaryInk, fontFamily = JetBrainsMonoFamily)
                                Text(
                                    text = "${payment.currency} ${String.format("%,.2f", payment.amount)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PrimaryInk,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("SETTLEMENT REF", style = MaterialTheme.typography.labelSmall, color = SecondaryInk, fontFamily = JetBrainsMonoFamily)
                                Text(
                                    text = payment.paypalCaptureId.ifBlank { "Awaiting Capture" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PrimaryInk,
                                    fontFamily = JetBrainsMonoFamily
                                )
                            }
                        }

                        if (payment.verificationDetails.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = payment.verificationDetails,
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryInk
                            )
                        }

                        if (!isSettled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = WarmBorder)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!isDemoMode && payment.approvalUrl.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(payment.approvalUrl)))
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, WarmBorder),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = "Open PayPal Checkout",
                                            modifier = Modifier.size(15.dp),
                                            tint = PrimaryInk
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Approve", style = MaterialTheme.typography.labelSmall, color = PrimaryInk)
                                    }
                                }

                                if (isDemoMode) {
                                    OutlinedButton(
                                        onClick = { onFailDemoPayment(payment) },
                                        enabled = !isPaymentBusy,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, CriticalCrimsonTone.copy(alpha = 0.6f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("fail_demo_payment_btn_${payment.id}")
                                    ) {
                                        Text("Fail", style = MaterialTheme.typography.labelSmall, color = CriticalCrimsonTone)
                                    }

                                    OutlinedButton(
                                        onClick = { onCancelDemoPayment(payment) },
                                        enabled = !isPaymentBusy,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, WarmBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("cancel_demo_payment_btn_${payment.id}")
                                    ) {
                                        Text("Cancel", style = MaterialTheme.typography.labelSmall, color = PrimaryInk)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { onReconcilePayPalOrder(payment) },
                                        enabled = !isPaymentBusy,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, WarmBorder),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Reconcile",
                                            modifier = Modifier.size(15.dp),
                                            tint = PrimaryInk
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reconcile", style = MaterialTheme.typography.labelSmall, color = PrimaryInk)
                                    }
                                }

                                Button(
                                    onClick = { onCapturePayPalOrder(payment) },
                                    enabled = !isPaymentBusy && payment.paypalOrderId.isNotBlank(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PositiveEmerald,
                                        contentColor = PureWhiteSurface
                                    ),
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .testTag("capture_paypal_btn_${payment.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "Capture",
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (isDemoMode) "Simulate Capture" else "Capture",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// ============================================================================
// SCREEN 6: EXPORT ORDER DIGITAL TWIN (Warm Minimal Vertical Lifecycle Timeline)
// ============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportOrderDigitalTwinScreen(
    portfolio: NiryatPortfolioState,
    onSelectOrder: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val twin = portfolio.selectedDigitalTwin
    if (twin == null) {
        EmptySelectionFallback(title = "Export Order Digital Twin", onBack = onBack)
        return
    }

    val order = twin.order
    val orderActivities = portfolio.activities.filter { it.exportOrderId == order.id || it.exportOrderId.isBlank() }

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
                .testTag("digital_twin_list"),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            modifier = Modifier.testTag("digital_twin_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = PrimaryInk
                            )
                        }
                        Column {
                            Text(
                                text = "EXPORT ORDER DIGITAL TWIN",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChampagneAccent,
                                fontFamily = JetBrainsMonoFamily
                            )
                            Text(
                                text = "${order.orderNumber} • Live State",
                                style = MaterialTheme.typography.titleLarge,
                                color = PrimaryInk,
                                fontFamily = SpaceGroteskFamily,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    StatusPill(
                        label = "${twin.overallProgressPercent}% Complete",
                        tone = if (twin.overallProgressPercent >= 75) FintechTone.POSITIVE else FintechTone.ACCENT_GOLD
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

            // Top Order Summary & Multi-Module Readiness Bars
            item {
                WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${order.buyerName} • ${order.buyerCountry}",
                                style = MaterialTheme.typography.titleMedium,
                                color = PrimaryInk
                            )
                            Text(
                                text = "${order.productName} (${order.quantity} ${order.unit}) • ${order.incoterm} ${order.destinationPort}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryInk
                            )
                        }
                        Text(
                            text = "${order.currency} ${String.format("%,.2f", order.orderValue)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = PrimaryInk,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = WarmBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    val finMargin = twin.financialAssessment?.profitMarginPercent ?: 0.0
                    ReadinessProgressBarRow(
                        label = "Financial Viability (Margin ${finMargin}%)",
                        score = if (twin.financialAssessment != null) 100 else 35,
                        onClick = { onNavigate("financial_intelligence") }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ReadinessProgressBarRow(
                        label = "Export Document Readiness (${twin.verifiedRequiredDocs}/${twin.totalRequiredDocs} Verified)",
                        score = twin.documentReadinessPercent.toInt(),
                        onClick = { onNavigate("document_centre") }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val payScore = if (order.orderValue > 0.0) {
                        ((twin.capturedPaymentTotal / order.orderValue) * 100.0).toInt().coerceIn(0, 100)
                    } else 0
                    ReadinessProgressBarRow(
                        label = "PayPal Sandbox Settlement (${order.currency} ${twin.capturedPaymentTotal})",
                        score = payScore,
                        onClick = { onNavigate("paypal_payments") }
                    )
                }
            }

            // Outstanding Actions & Blockers Card
            if (twin.outstandingActions.isNotEmpty()) {
                item {
                    WarmSurfaceCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = WarningAmberBg,
                        borderColor = WarningAmberTone.copy(alpha = 0.4f)
                    ) {
                        Text(
                            text = "OUTSTANDING ACTIONS REQUIRED (${twin.outstandingActions.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningAmberTone,
                            fontFamily = JetBrainsMonoFamily
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        twin.outstandingActions.forEach { action ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { onNavigate(action.targetScreenRoute) },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "• ${action.title}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = PrimaryInk
                                    )
                                    Text(
                                        text = action.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SecondaryInk
                                    )
                                }
                                StatusPill(
                                    label = action.severity,
                                    tone = statusToneFor(action.severity)
                                )
                            }
                        }
                    }
                }
            }

            // 8-Stage Vertical Lifecycle Timeline
            item {
                WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "8-STAGE LIFECYCLE TIMELINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryInk,
                        fontFamily = JetBrainsMonoFamily
                    )
                    Text(
                        text = "Synchronized Order Execution State",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryInk
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    twin.stages.forEachIndexed { index, stage ->
                        val nodeColor = when (stage.status) {
                            LifecycleStageStatus.COMPLETED -> PositiveEmerald
                            LifecycleStageStatus.IN_PROGRESS -> ChampagneAccent
                            LifecycleStageStatus.ACTION_REQUIRED -> CriticalCrimsonTone
                            LifecycleStageStatus.PENDING -> WarmBorder
                        }
                        val nodeBg = when (stage.status) {
                            LifecycleStageStatus.COMPLETED -> PositiveEmeraldBg
                            LifecycleStageStatus.IN_PROGRESS -> SoftChampagneBg
                            LifecycleStageStatus.ACTION_REQUIRED -> WarningAmberBg
                            LifecycleStageStatus.PENDING -> IvoryBackground
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(stage.targetScreenRoute) },
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(32.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = nodeBg,
                                    border = BorderStroke(2.dp, nodeColor),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (stage.status == LifecycleStageStatus.COMPLETED) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = PositiveEmerald,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        } else {
                                            Text(
                                                text = "${stage.stageNumber}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PrimaryInk,
                                                fontFamily = JetBrainsMonoFamily
                                            )
                                        }
                                    }
                                }
                                if (index < twin.stages.lastIndex) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(40.dp)
                                            .background(
                                                if (stage.status == LifecycleStageStatus.COMPLETED) PositiveEmerald else WarmBorder
                                            )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(bottom = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stage.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = PrimaryInk,
                                        fontWeight = if (stage.status == LifecycleStageStatus.IN_PROGRESS) FontWeight.Bold else FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stage.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SecondaryInk
                                    )
                                    Text(
                                        text = stage.detailText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MutedInk,
                                        fontFamily = JetBrainsMonoFamily
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))
                                StatusPill(
                                    label = stage.status.name.replace('_', ' '),
                                    tone = statusToneFor(stage.status.name)
                                )
                            }
                        }
                    }
                }
            }

            // Activity History Feed
            item {
                SectionHeaderRow(title = "Audit & Activity Trail (${orderActivities.size})")
            }

            items(orderActivities.take(10), key = { it.id }) { act ->
                val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.US) }
                val timeStr = act.createdAt?.toDate()?.let { dateFormat.format(it) } ?: "Now"
                WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = act.eventType,
                                style = MaterialTheme.typography.labelSmall,
                                color = ChampagneAccent,
                                fontFamily = JetBrainsMonoFamily
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = act.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = PrimaryInk
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = act.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryInk
                            )
                        }
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedInk,
                            fontFamily = JetBrainsMonoFamily
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ReadinessProgressBarRow(
    label: String,
    score: Int,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = PrimaryInk
            )
            Text(
                text = "$score%",
                style = MaterialTheme.typography.labelLarge,
                color = PrimaryInk,
                fontFamily = JetBrainsMonoFamily
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (score / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = when {
                score >= 80 -> PositiveEmerald
                score >= 45 -> ChampagneAccent
                else -> WarningAmberTone
            },
            trackColor = IvorySecondaryBg
        )
    }
}
