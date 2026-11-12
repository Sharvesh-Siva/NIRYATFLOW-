package com.example.domain

import com.example.data.model.DigitalTwinState
import com.example.data.model.DocumentRecord
import com.example.data.model.ExportOrder
import com.example.data.model.FinancialAssessment
import com.example.data.model.LifecycleStageNode
import com.example.data.model.LifecycleStageStatus
import com.example.data.model.OutstandingAction
import com.example.data.model.PaymentRecord
import com.example.data.model.PerformanceMetrics
import com.example.data.model.PeriodDashboardAnalytics
import com.example.data.model.TimePeriodFilter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Deterministic Financial Calculation & Digital Twin State Engine.
 * All financial math, currency conversion, fee calculation, profit margin,
 * document readiness, lifecycle progress, and time-period analytics are computed here.
 */
object FinancialCalculationEngine {

    val STANDARD_DOCUMENT_TYPES = listOf(
        "Commercial Invoice" to "REQUIRED",
        "Packing List" to "REQUIRED",
        "Purchase Order / Contract" to "REQUIRED",
        "Shipping Bill" to "REQUIRED",
        "Bill of Lading / Airway Bill" to "REQUIRED",
        "Certificate of Origin" to "REQUIRED",
        "Insurance Certificate" to "OPTIONAL",
        "Payment Receipt / Confirmation" to "REQUIRED"
    )

    fun defaultExchangeRateForCurrency(currency: String): Double {
        return when (currency.uppercase()) {
            "USD" -> 84.20
            "EUR" -> 91.50
            "GBP" -> 108.40
            "AED" -> 22.92
            "JPY" -> 0.56
            "INR" -> 1.00
            else -> 84.20
        }
    }

    fun calculateGrossOrderValue(quantity: Double, unitPrice: Double): Double {
        return round2(quantity.coerceAtLeast(0.0) * unitPrice.coerceAtLeast(0.0))
    }

    fun calculateAssessment(
        userId: String,
        exportOrderId: String,
        existingId: String = "",
        quantity: Double,
        unitPrice: Double,
        currency: String,
        exchangeRate: Double,
        advancePercentage: Double,
        paymentFeePercentage: Double,
        paymentFixedFee: Double,
        procurementCostInr: Double,
        logisticsCostInr: Double,
        packagingCostInr: Double,
        otherCostsInr: Double,
        assumptionsNote: String = "Deterministic calculation based on user-supplied exchange rate and cost structure."
    ): FinancialAssessment {
        val safeQty = quantity.coerceAtLeast(0.0)
        val safeUnitPrice = unitPrice.coerceAtLeast(0.0)
        val safeRate = exchangeRate.coerceAtLeast(0.01)
        val safeAdvancePct = advancePercentage.coerceIn(0.0, 100.0)
        val safeFeePct = paymentFeePercentage.coerceIn(0.0, 35.0)
        val safeFixedFee = paymentFixedFee.coerceAtLeast(0.0)

        // 1. Gross Order Value = Quantity * Unit Price
        val grossOrderValue = round2(safeQty * safeUnitPrice)

        // 2. Converted Order Value (INR) = Gross Order Value * Exchange Rate
        val convertedValueInr = round2(grossOrderValue * safeRate)

        // 3. Advance & Outstanding Amounts (in Order Currency)
        val advanceAmount = round2(grossOrderValue * (safeAdvancePct / 100.0))
        val outstandingAmount = round2(grossOrderValue - advanceAmount)

        // 4. International Payment Fee (INR) = (Gross * Fee%) * ExchangeRate + (FixedFee * ExchangeRate)
        val feeInOrderCurrency = (grossOrderValue * (safeFeePct / 100.0)) + safeFixedFee
        val paymentFeeInr = round2(feeInOrderCurrency * safeRate)

        // 5. Estimated Net Revenue (INR) = Converted Order Value - Payment Fee (INR)
        val estimatedNetRevenueInr = round2(convertedValueInr - paymentFeeInr)

        // 6. Total Operating Costs (INR)
        val totalOperatingCostInr = procurementCostInr.coerceAtLeast(0.0) +
            logisticsCostInr.coerceAtLeast(0.0) +
            packagingCostInr.coerceAtLeast(0.0) +
            otherCostsInr.coerceAtLeast(0.0)

        // 7. Estimated Net Profit (INR) = Estimated Net Revenue - Total Operating Costs
        val estimatedProfitInr = round2(estimatedNetRevenueInr - totalOperatingCostInr)

        // 8. Profit Margin (%) = (Estimated Profit / Converted Order Value) * 100
        val profitMarginPercent = if (convertedValueInr > 0.0) {
            round2((estimatedProfitInr / convertedValueInr) * 100.0)
        } else {
            0.0
        }

        return FinancialAssessment(
            id = existingId.ifBlank { exportOrderId },
            userId = userId,
            exportOrderId = exportOrderId,
            grossOrderValue = grossOrderValue,
            currency = currency.uppercase(),
            exchangeRate = safeRate,
            convertedValueInr = convertedValueInr,
            advancePercentage = safeAdvancePct,
            advanceAmount = advanceAmount,
            outstandingAmount = outstandingAmount,
            paymentFeePercentage = safeFeePct,
            paymentFixedFee = safeFixedFee,
            paymentFeeInr = paymentFeeInr,
            procurementCostInr = round2(procurementCostInr.coerceAtLeast(0.0)),
            logisticsCostInr = round2(logisticsCostInr.coerceAtLeast(0.0)),
            packagingCostInr = round2(packagingCostInr.coerceAtLeast(0.0)),
            otherCostsInr = round2(otherCostsInr.coerceAtLeast(0.0)),
            estimatedNetRevenueInr = estimatedNetRevenueInr,
            estimatedProfitInr = estimatedProfitInr,
            profitMarginPercent = profitMarginPercent,
            assumptionsNote = assumptionsNote
        )
    }

    fun isDocumentVerifiedOrApproved(status: String): Boolean {
        return status == "VERIFIED" || status == "APPROVED" || status == "OFFICIALLY_CERTIFIED"
    }

    fun isPaymentSettled(status: String): Boolean {
        return status == "CAPTURED" || status == "DEMO_CAPTURED"
    }

    fun isPaymentPending(status: String): Boolean {
        return status == "PENDING_APPROVAL" || status == "APPROVED" || status == "DEMO_PENDING"
    }

    fun calculateDocumentReadiness(documents: List<DocumentRecord>): Pair<Double, Pair<Int, Int>> {
        val requiredDocs = documents.filter { it.requirementLevel == "REQUIRED" }
        val totalRequired = requiredDocs.size
        if (totalRequired == 0) return 0.0 to (0 to 0)
        val verifiedCount = requiredDocs.count { isDocumentVerifiedOrApproved(it.status) }
        val pct = round2((verifiedCount.toDouble() / totalRequired.toDouble()) * 100.0)
        return pct to (verifiedCount to totalRequired)
    }

    fun buildDigitalTwinState(
        order: ExportOrder,
        financialAssessment: FinancialAssessment?,
        documents: List<DocumentRecord>,
        payments: List<PaymentRecord>
    ): DigitalTwinState {
        val (docReadinessPct, counts) = calculateDocumentReadiness(documents)
        val (verifiedRequiredDocs, totalRequiredDocs) = counts

        val capturedPayments = payments.filter { isPaymentSettled(it.paymentStatus) }
        val capturedTotal = round2(capturedPayments.sumOf { it.amount })
        val pendingApprovalPayments = payments.filter {
            isPaymentPending(it.paymentStatus)
        }

        val isOrderValidated = order.buyerName.isNotBlank() &&
            order.productName.isNotBlank() &&
            order.quantity > 0.0 &&
            order.unitPrice > 0.0 &&
            order.destinationPort.isNotBlank()

        val isFinanciallyAssessed = financialAssessment != null && financialAssessment.convertedValueInr > 0.0
        val areAllRequiredDocsVerified = totalRequiredDocs > 0 && verifiedRequiredDocs == totalRequiredDocs
        val hasAnyDocUploaded = documents.any { it.status != "MISSING" }
        val hasCapturedAdvanceOrFull = capturedTotal > 0.0
        val isFullyPaid = order.orderValue > 0.0 && capturedTotal >= (order.orderValue * (order.advancePercentage / 100.0)).coerceAtLeast(0.01)

        // Build 8 lifecycle stages
        val stage1 = LifecycleStageNode(
            stageNumber = 1,
            title = "Order Created",
            subtitle = "Export Order ${order.orderNumber} registered (${order.extractionSource})",
            status = LifecycleStageStatus.COMPLETED,
            detailText = "${order.productName} • ${order.quantity} ${order.unit} @ ${order.currency} ${order.unitPrice}",
            targetScreenRoute = "new_order"
        )

        val stage2 = LifecycleStageNode(
            stageNumber = 2,
            title = "Order Details Validated",
            subtitle = if (isOrderValidated) "Buyer, Incoterms (${order.incoterm}), and port verified" else "Missing buyer or shipment fields",
            status = if (isOrderValidated) LifecycleStageStatus.COMPLETED else LifecycleStageStatus.ACTION_REQUIRED,
            detailText = "Destination: ${order.destinationPort.ifBlank { "Pending" }} (${order.buyerCountry})",
            targetScreenRoute = "new_order"
        )

        val stage3 = LifecycleStageNode(
            stageNumber = 3,
            title = "Financial Assessment Completed",
            subtitle = if (isFinanciallyAssessed) {
                "INR ${formatInrShort(financialAssessment!!.convertedValueInr)} • Net Margin ${financialAssessment.profitMarginPercent}%"
            } else {
                "Run deterministic revenue & cost breakdown"
            },
            status = when {
                isFinanciallyAssessed && financialAssessment!!.profitMarginPercent < 5.0 -> LifecycleStageStatus.ACTION_REQUIRED
                isFinanciallyAssessed -> LifecycleStageStatus.COMPLETED
                else -> LifecycleStageStatus.IN_PROGRESS
            },
            detailText = financialAssessment?.let {
                "Est. Profit: INR ${formatInrShort(it.estimatedProfitInr)} @ FX ${it.exchangeRate}"
            } ?: "Awaiting cost structure input",
            targetScreenRoute = "financial_intelligence"
        )

        val stage4 = LifecycleStageNode(
            stageNumber = 4,
            title = "Export Documents Checklist Prepared",
            subtitle = if (totalRequiredDocs > 0) "$totalRequiredDocs mandatory documents configured" else "Initialize export document checklist",
            status = if (totalRequiredDocs > 0) LifecycleStageStatus.COMPLETED else LifecycleStageStatus.IN_PROGRESS,
            detailText = "Total checklist items: ${documents.size}",
            targetScreenRoute = "document_centre"
        )

        val rejectedOrReuploadDocs = documents.count { it.status == "REJECTED" || it.status == "REUPLOAD_REQUIRED" }
        val stage5 = LifecycleStageNode(
            stageNumber = 5,
            title = "Required Documents Verified",
            subtitle = "$verifiedRequiredDocs / $totalRequiredDocs mandatory documents verified (${docReadinessPct.roundToInt()}%)",
            status = when {
                areAllRequiredDocsVerified -> LifecycleStageStatus.COMPLETED
                rejectedOrReuploadDocs > 0 -> LifecycleStageStatus.ACTION_REQUIRED
                hasAnyDocUploaded -> LifecycleStageStatus.IN_PROGRESS
                else -> LifecycleStageStatus.PENDING
            },
            detailText = if (rejectedOrReuploadDocs > 0) "$rejectedOrReuploadDocs document(s) require re-upload" else "Internal pre-shipment verification",
            targetScreenRoute = "document_centre"
        )

        val stage6 = LifecycleStageNode(
            stageNumber = 6,
            title = "International Payment Initiated",
            subtitle = when {
                hasCapturedAdvanceOrFull -> "PayPal Sandbox order initiated & approved"
                pendingApprovalPayments.isNotEmpty() -> "PayPal Order awaiting buyer approval / capture"
                else -> "Create PayPal Sandbox payment order"
            },
            status = when {
                hasCapturedAdvanceOrFull -> LifecycleStageStatus.COMPLETED
                pendingApprovalPayments.isNotEmpty() -> LifecycleStageStatus.IN_PROGRESS
                else -> LifecycleStageStatus.PENDING
            },
            detailText = payments.firstOrNull()?.let { "PayPal Order ID: ${it.paypalOrderId.ifBlank { "Pending" }}" } ?: "No PayPal transaction initiated",
            targetScreenRoute = "paypal_payments"
        )

        val stage7 = LifecycleStageNode(
            stageNumber = 7,
            title = "Payment Verified / Captured",
            subtitle = if (hasCapturedAdvanceOrFull) {
                "Captured ${order.currency} $capturedTotal of ${order.currency} ${order.orderValue}"
            } else {
                "Zero verified captured receipts"
            },
            status = when {
                isFullyPaid -> LifecycleStageStatus.COMPLETED
                hasCapturedAdvanceOrFull -> LifecycleStageStatus.IN_PROGRESS
                pendingApprovalPayments.isNotEmpty() -> LifecycleStageStatus.ACTION_REQUIRED
                else -> LifecycleStageStatus.PENDING
            },
            detailText = capturedPayments.firstOrNull()?.let { "Capture ID: ${it.paypalCaptureId}" } ?: "Awaiting PayPal Sandbox capture",
            targetScreenRoute = "paypal_payments"
        )

        val isOrderComplete = order.orderStatus == "COMPLETED" ||
            (isOrderValidated && isFinanciallyAssessed && areAllRequiredDocsVerified && hasCapturedAdvanceOrFull)

        val stage8 = LifecycleStageNode(
            stageNumber = 8,
            title = "Export Order Ready / Completed",
            subtitle = if (isOrderComplete) "Pre-shipment financial, document & payment checks passed" else "Pending upstream readiness gates",
            status = if (isOrderComplete) LifecycleStageStatus.COMPLETED else LifecycleStageStatus.PENDING,
            detailText = if (isOrderComplete) "Ready for customs dispatch & final settlement" else "Complete remaining action items below",
            targetScreenRoute = "digital_twin"
        )

        val stages = listOf(stage1, stage2, stage3, stage4, stage5, stage6, stage7, stage8)
        val completedCount = stages.count { it.status == LifecycleStageStatus.COMPLETED }
        val overallProgress = ((completedCount.toDouble() / stages.size.toDouble()) * 100.0).roundToInt()

        val activeStage = stages.firstOrNull {
            it.status == LifecycleStageStatus.ACTION_REQUIRED || it.status == LifecycleStageStatus.IN_PROGRESS
        } ?: stages.last()

        // Outstanding actions
        val actions = mutableListOf<OutstandingAction>()
        if (!isFinanciallyAssessed) {
            actions.add(
                OutstandingAction(
                    id = "act_fin_${order.id}",
                    title = "Complete Financial Viability Assessment",
                    description = "Enter procurement, logistics, and FX assumptions to compute INR margin.",
                    severity = "HIGH",
                    targetScreenRoute = "financial_intelligence"
                )
            )
        } else if (financialAssessment!!.profitMarginPercent < 8.0) {
            actions.add(
                OutstandingAction(
                    id = "act_margin_${order.id}",
                    title = "Low Profit Margin Warning (${financialAssessment.profitMarginPercent}%)",
                    description = "Estimated margin is below 8%. Review freight costs or exchange rate buffer.",
                    severity = "HIGH",
                    targetScreenRoute = "financial_intelligence"
                )
            )
        }

        val missingReqDocs = documents.filter { it.requirementLevel == "REQUIRED" && !isDocumentVerifiedOrApproved(it.status) }
        if (missingReqDocs.isNotEmpty()) {
            actions.add(
                OutstandingAction(
                    id = "act_docs_${order.id}",
                    title = "${missingReqDocs.size} Mandatory Export Document(s) Unverified",
                    description = "Pending: ${missingReqDocs.take(3).joinToString { it.documentType }}",
                    severity = if (rejectedOrReuploadDocs > 0) "HIGH" else "MEDIUM",
                    targetScreenRoute = "document_centre"
                )
            )
        }

        if (!hasCapturedAdvanceOrFull) {
            actions.add(
                OutstandingAction(
                    id = "act_pay_${order.id}",
                    title = if (pendingApprovalPayments.isNotEmpty()) "Capture Approved PayPal Order" else "Initiate Advance Payment (${order.advancePercentage.roundToInt()}%)",
                    description = "Expected advance: ${order.currency} ${round2(order.orderValue * (order.advancePercentage / 100.0))}",
                    severity = "MEDIUM",
                    targetScreenRoute = "paypal_payments"
                )
            )
        }

        return DigitalTwinState(
            order = order,
            financialAssessment = financialAssessment,
            documents = documents,
            payments = payments,
            documentReadinessPercent = docReadinessPct,
            verifiedRequiredDocs = verifiedRequiredDocs,
            totalRequiredDocs = totalRequiredDocs,
            capturedPaymentTotal = capturedTotal,
            overallProgressPercent = overallProgress,
            currentStageLabel = activeStage.title,
            stages = stages,
            outstandingActions = actions
        )
    }

    fun calculatePortfolioMetrics(
        orders: List<ExportOrder>,
        assessments: List<FinancialAssessment>,
        documents: List<DocumentRecord>,
        payments: List<PaymentRecord>
    ): PerformanceMetrics {
        if (orders.isEmpty()) return PerformanceMetrics()

        val assessmentByOrder = assessments.associateBy { it.exportOrderId }
        val completedOrders = orders.count { it.orderStatus == "COMPLETED" }
        val cancelledOrders = orders.count { it.orderStatus == "CANCELLED" }
        val activeOrders = orders.size - completedOrders - cancelledOrders

        var totalValueInr = 0.0
        var totalProfitInr = 0.0
        var marginSum = 0.0
        var marginCount = 0
        val countryMap = mutableMapOf<String, Double>()
        val statusMap = mutableMapOf<String, Int>()

        for (order in orders) {
            statusMap[order.orderStatus] = (statusMap[order.orderStatus] ?: 0) + 1
            val assessment = assessmentByOrder[order.id]
            val orderInr = assessment?.convertedValueInr
                ?: round2(order.orderValue * defaultExchangeRateForCurrency(order.currency))
            totalValueInr += orderInr

            if (assessment != null) {
                totalProfitInr += assessment.estimatedProfitInr
                marginSum += assessment.profitMarginPercent
                marginCount++
            } else {
                val fallbackProfit = round2(orderInr * 0.14)
                totalProfitInr += fallbackProfit
            }

            val countryKey = order.buyerCountry.ifBlank { "Unspecified" }
            countryMap[countryKey] = round2((countryMap[countryKey] ?: 0.0) + orderInr)
        }

        val orderById = orders.associateBy { it.id }
        var capturedInr = 0.0
        for (payment in payments) {
            if (isPaymentSettled(payment.paymentStatus)) {
                val parentOrder = orderById[payment.exportOrderId]
                val fx = assessmentByOrder[payment.exportOrderId]?.exchangeRate
                    ?: defaultExchangeRateForCurrency(payment.currency.ifBlank { parentOrder?.currency ?: "USD" })
                capturedInr += payment.amount * fx
            }
        }
        capturedInr = round2(capturedInr)
        val outstandingInr = round2((totalValueInr - capturedInr).coerceAtLeast(0.0))

        val avgOrderValueInr = round2(totalValueInr / orders.size.toDouble())
        val avgMarginPct = if (marginCount > 0) round2(marginSum / marginCount.toDouble()) else 14.0
        val paymentCompletionRate = if (totalValueInr > 0.0) {
            round2(((capturedInr / totalValueInr) * 100.0).coerceIn(0.0, 100.0))
        } else 0.0

        val (docReadinessRate, _) = calculateDocumentReadiness(documents)

        return PerformanceMetrics(
            totalOrders = orders.size,
            activeOrders = activeOrders,
            completedOrders = completedOrders,
            cancelledOrders = cancelledOrders,
            pendingOrders = activeOrders,
            totalOrderValueInr = round2(totalValueInr),
            capturedPaymentValueInr = capturedInr,
            outstandingPaymentValueInr = outstandingInr,
            totalEstimatedProfitInr = round2(totalProfitInr),
            averageOrderValueInr = avgOrderValueInr,
            averageProfitMarginPercent = avgMarginPct,
            paymentCompletionRatePercent = paymentCompletionRate,
            documentReadinessRatePercent = docReadinessRate,
            statusDistribution = statusMap,
            countryDistribution = countryMap.toList().sortedByDescending { it.second }.toMap()
        )
    }

    /**
     * Resolves an order's effective timestamp in epoch milliseconds using `createdAt`,
     * or falls back to parsing `orderDate` (`yyyy-MM-dd`), or defaults to `fallbackMillis`.
     */
    fun resolveOrderTimestampMillis(order: ExportOrder, fallbackMillis: Long = System.currentTimeMillis()): Long {
        order.createdAt?.toDate()?.time?.let { return it }
        if (order.orderDate.isNotBlank()) {
            runCatching {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                sdf.parse(order.orderDate)?.time?.let { return it }
            }
        }
        return fallbackMillis
    }

    /**
     * Resolves a payment record's effective timestamp in epoch milliseconds using `updatedAt` or `createdAt`.
     */
    fun resolvePaymentTimestampMillis(payment: PaymentRecord, fallbackMillis: Long = System.currentTimeMillis()): Long {
        payment.updatedAt?.toDate()?.time?.let { return it }
        payment.createdAt?.toDate()?.time?.let { return it }
        return fallbackMillis
    }

    /**
     * Live Time-Period Dashboard Calculation Engine (`1D`, `1W`, `1M`, `3M`, `1Y`, `ALL`).
     *
     * Rules enforced:
     * 1. Order intake metrics (`totalOrderValueInr`, `totalOrders`, `activeOrders`, `completedOrders`, `totalEstimatedProfitInr`)
     *    are filtered strictly by `order` creation/intake timestamp within the active period window.
     * 2. Captured revenue (`capturedPaymentValueInr`) is filtered strictly by `CAPTURED` payments whose
     *    capture/update timestamp falls within the active period window. An order's full value is NEVER
     *    counted as received revenue merely because the order exists.
     * 3. Previous-period comparison computes the exact preceding window of equal duration (`[now - 2*W, now - W)`)
     *    and handles zero denominators safely without `NaN` or `Infinity`.
     * 4. Time-series chart buckets dynamically adapt to the selected time period:
     *    - `1D`: 6 four-hour intraday buckets
     *    - `1W`: 7 daily buckets
     *    - `1M`: 4 weekly buckets
     *    - `3M`: 3 monthly buckets
     *    - `1Y`: 6 bi-monthly buckets
     *    - `ALL`: per-order / portfolio chronological progression
     */
    fun calculatePeriodAnalytics(
        orders: List<ExportOrder>,
        assessments: List<FinancialAssessment>,
        documents: List<DocumentRecord>,
        payments: List<PaymentRecord>,
        periodCode: String,
        nowMillis: Long = System.currentTimeMillis()
    ): PeriodDashboardAnalytics {
        val period = TimePeriodFilter.fromCode(periodCode)
        val assessmentByOrder = assessments.associateBy { it.exportOrderId }
        val orderById = orders.associateBy { it.id }

        fun orderInrValue(order: ExportOrder): Double {
            return assessmentByOrder[order.id]?.convertedValueInr
                ?: round2(order.orderValue * defaultExchangeRateForCurrency(order.currency))
        }

        fun paymentInrValue(payment: PaymentRecord): Double {
            val parentOrder = orderById[payment.exportOrderId]
            val fx = assessmentByOrder[payment.exportOrderId]?.exchangeRate
                ?: defaultExchangeRateForCurrency(payment.currency.ifBlank { parentOrder?.currency ?: "USD" })
            return round2(payment.amount * fx)
        }

        val windowMillis = period.windowMillis
        val windowStartMillis = if (windowMillis != null) nowMillis - windowMillis else Long.MIN_VALUE
        val prevWindowStartMillis = if (windowMillis != null) nowMillis - (windowMillis * 2L) else Long.MIN_VALUE

        val filteredOrders = if (windowMillis == null) {
            orders
        } else {
            orders.filter { ord ->
                val ts = resolveOrderTimestampMillis(ord, nowMillis)
                ts in windowStartMillis..nowMillis
            }
        }

        val filteredOrderIds = filteredOrders.map { it.id }.toSet()
        val filteredDocuments = if (windowMillis == null) {
            documents
        } else {
            documents.filter { it.exportOrderId in filteredOrderIds }
        }

        val filteredPayments = if (windowMillis == null) {
            payments
        } else {
            payments.filter { pay ->
                val ts = resolvePaymentTimestampMillis(pay, nowMillis)
                ts in windowStartMillis..nowMillis
            }
        }

        // Compute base metrics for filtered orders, and override capturedPaymentValueInr with period-captured payments
        val baseMetrics = calculatePortfolioMetrics(
            orders = filteredOrders,
            assessments = assessments,
            documents = filteredDocuments,
            payments = emptyList()
        )

        val periodCapturedInr = round2(
            filteredPayments
                .filter { isPaymentSettled(it.paymentStatus) }
                .sumOf { paymentInrValue(it) }
        )

        // Outstanding receivable value across the filtered orders
        val capturedAgainstFilteredOrdersInr = round2(
            payments
                .filter { isPaymentSettled(it.paymentStatus) && it.exportOrderId in filteredOrderIds }
                .sumOf { paymentInrValue(it) }
        )
        val periodOutstandingInr = round2(
            (baseMetrics.totalOrderValueInr - capturedAgainstFilteredOrdersInr).coerceAtLeast(0.0)
        )
        val periodPaymentCompletionPct = if (baseMetrics.totalOrderValueInr > 0.0) {
            round2(((capturedAgainstFilteredOrdersInr / baseMetrics.totalOrderValueInr) * 100.0).coerceIn(0.0, 100.0))
        } else 0.0

        val periodMetrics = baseMetrics.copy(
            capturedPaymentValueInr = periodCapturedInr,
            outstandingPaymentValueInr = periodOutstandingInr,
            paymentCompletionRatePercent = periodPaymentCompletionPct
        )

        // Previous period comparison
        var prevOrderValueInr = 0.0
        var prevCapturedInr = 0.0
        if (windowMillis != null) {
            for (ord in orders) {
                val ts = resolveOrderTimestampMillis(ord, nowMillis)
                if (ts in prevWindowStartMillis until windowStartMillis) {
                    prevOrderValueInr += orderInrValue(ord)
                }
            }
            for (pay in payments) {
                if (isPaymentSettled(pay.paymentStatus)) {
                    val ts = resolvePaymentTimestampMillis(pay, nowMillis)
                    if (ts in prevWindowStartMillis until windowStartMillis) {
                        prevCapturedInr += paymentInrValue(pay)
                    }
                }
            }
        }
        prevOrderValueInr = round2(prevOrderValueInr)
        prevCapturedInr = round2(prevCapturedInr)

        val orderGrowthPct: Double? = if (windowMillis != null && prevOrderValueInr > 0.0) {
            round2(((periodMetrics.totalOrderValueInr - prevOrderValueInr) / prevOrderValueInr) * 100.0)
        } else null

        val capturedGrowthPct: Double? = if (windowMillis != null && prevCapturedInr > 0.0) {
            round2(((periodCapturedInr - prevCapturedInr) / prevCapturedInr) * 100.0)
        } else null

        // Build time-series buckets for the selected period
        val bucketCount = when (period) {
            TimePeriodFilter.ONE_DAY -> 6
            TimePeriodFilter.ONE_WEEK -> 7
            TimePeriodFilter.ONE_MONTH -> 4
            TimePeriodFilter.THREE_MONTHS -> 3
            TimePeriodFilter.ONE_YEAR -> 6
            TimePeriodFilter.ALL -> filteredOrders.size.coerceIn(4, 6)
        }

        val labels = mutableListOf<String>()
        val intakeSeries = mutableListOf<Double>()
        val capturedSeries = mutableListOf<Double>()

        if (windowMillis == null) {
            // ALL-TIME: group chronologically across orders
            val sortedOrders = filteredOrders.sortedBy { resolveOrderTimestampMillis(it, nowMillis) }
            if (sortedOrders.isEmpty()) {
                listOf("Q1", "Q2", "Q3", "Q4").forEach { lbl ->
                    labels.add(lbl)
                    intakeSeries.add(0.0)
                    capturedSeries.add(0.0)
                }
            } else {
                sortedOrders.takeLast(6).forEach { ord ->
                    labels.add(ord.orderNumber.removePrefix("ORDER-").ifBlank { ord.orderNumber })
                    intakeSeries.add(orderInrValue(ord))
                    val ordCap = payments
                        .filter { it.exportOrderId == ord.id && isPaymentSettled(it.paymentStatus) }
                        .sumOf { paymentInrValue(it) }
                    capturedSeries.add(round2(ordCap))
                }
            }
        } else {
            val bucketSpan = windowMillis / bucketCount.toLong()
            val dayFmt = SimpleDateFormat("dd MMM", Locale.US)
            val monthFmt = SimpleDateFormat("MMM", Locale.US)

            for (i in 0 until bucketCount) {
                val bStart = windowStartMillis + (i * bucketSpan)
                val bEnd = if (i == bucketCount - 1) nowMillis + 1000L else windowStartMillis + ((i + 1) * bucketSpan)

                val bucketLabel = when (period) {
                    TimePeriodFilter.ONE_DAY -> {
                        val hoursAgo = ((bucketCount - 1 - i) * 4)
                        if (hoursAgo == 0) "Now" else "-${hoursAgo}h"
                    }
                    TimePeriodFilter.ONE_WEEK -> {
                        if (i == bucketCount - 1) "Today" else dayFmt.format(Date(bStart))
                    }
                    TimePeriodFilter.ONE_MONTH -> "Wk ${i + 1}"
                    TimePeriodFilter.THREE_MONTHS -> monthFmt.format(Date(bEnd - 1000L))
                    TimePeriodFilter.ONE_YEAR -> monthFmt.format(Date(bEnd - 1000L))
                    TimePeriodFilter.ALL -> "P${i + 1}"
                }

                val bucketIntake = orders
                    .filter { resolveOrderTimestampMillis(it, nowMillis) in bStart until bEnd }
                    .sumOf { orderInrValue(it) }

                val bucketCaptured = payments
                    .filter {
                        isPaymentSettled(it.paymentStatus) &&
                            resolvePaymentTimestampMillis(it, nowMillis) in bStart until bEnd
                    }
                    .sumOf { paymentInrValue(it) }

                labels.add(bucketLabel)
                intakeSeries.add(round2(bucketIntake))
                capturedSeries.add(round2(bucketCaptured))
            }
        }

        val summaryText = "${period.label}: ${filteredOrders.size} order(s) • Intake ₹${formatInrShort(periodMetrics.totalOrderValueInr)} • Captured ₹${formatInrShort(periodCapturedInr)}"

        return PeriodDashboardAnalytics(
            period = period,
            metrics = periodMetrics,
            filteredOrders = filteredOrders,
            filteredPayments = filteredPayments,
            previousPeriodOrderValueInr = prevOrderValueInr,
            orderValueGrowthPercent = orderGrowthPct,
            previousPeriodCapturedInr = prevCapturedInr,
            capturedRevenueGrowthPercent = capturedGrowthPct,
            chartBucketLabels = labels,
            chartOrderIntakeInr = intakeSeries,
            chartCapturedPaymentsInr = capturedSeries,
            periodSummaryText = summaryText
        )
    }

    fun formatInrShort(amountInr: Double): String {
        val absVal = kotlin.math.abs(amountInr)
        val sign = if (amountInr < 0) "-" else ""
        return when {
            absVal >= 10_000_000.0 -> String.format(Locale.US, "%s%.2f Cr", sign, absVal / 10_000_000.0)
            absVal >= 100_000.0 -> String.format(Locale.US, "%s%.2f L", sign, absVal / 100_000.0)
            absVal >= 1_000.0 -> String.format(Locale.US, "%s%,.0f", sign, absVal)
            else -> String.format(Locale.US, "%s%.2f", sign, absVal)
        }
    }

    private fun round2(value: Double): Double {
        return kotlin.math.round(value * 100.0) / 100.0
    }
}
