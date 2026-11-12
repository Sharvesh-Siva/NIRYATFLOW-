package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class ExportOrder(
    @DocumentId val id: String = "",
    val userId: String = "",
    val orderNumber: String = "",
    val exporterName: String = "",
    val exporterContact: String = "",
    val exporterAddress: String = "",
    val buyerName: String = "",
    val buyerCountry: String = "",
    val buyerContact: String = "",
    val buyerAddress: String = "",
    val productName: String = "",
    val productDescription: String = "",
    val quantity: Double = 0.0,
    val unit: String = "KG",
    val unitPrice: Double = 0.0,
    val orderValue: Double = 0.0,
    val currency: String = "USD",
    val incoterm: String = "FOB",
    val destinationPort: String = "",
    val orderDate: String = "",
    val expectedShipmentDate: String = "",
    val paymentTerms: String = "30% Advance, 70% CAD",
    val advancePercentage: Double = 30.0,
    val orderStatus: String = "CREATED", // CREATED, VALIDATED, FINANCIALLY_ASSESSED, DOCS_READY, PAYMENT_INITIATED, COMPLETED, CANCELLED
    val extractionSource: String = "MANUAL", // MANUAL, AI_EXTRACTED_CONFIRMED
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FinancialAssessment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val exportOrderId: String = "",
    val grossOrderValue: Double = 0.0,
    val currency: String = "USD",
    val exchangeRate: Double = 84.20,
    val convertedValueInr: Double = 0.0,
    val advancePercentage: Double = 30.0,
    val advanceAmount: Double = 0.0,
    val outstandingAmount: Double = 0.0,
    val paymentFeePercentage: Double = 4.4,
    val paymentFixedFee: Double = 0.30,
    val paymentFeeInr: Double = 0.0,
    val procurementCostInr: Double = 0.0,
    val logisticsCostInr: Double = 0.0,
    val packagingCostInr: Double = 0.0,
    val otherCostsInr: Double = 0.0,
    val estimatedNetRevenueInr: Double = 0.0,
    val estimatedProfitInr: Double = 0.0,
    val profitMarginPercent: Double = 0.0,
    val assumptionsNote: String = "Deterministic calculation based on user-supplied exchange rate and cost structure.",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DocumentRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val exportOrderId: String = "",
    val documentType: String = "",
    val requirementLevel: String = "REQUIRED", // REQUIRED, OPTIONAL, NOT_APPLICABLE
    val status: String = "MISSING", // MISSING, UPLOADED, PENDING_REVIEW, UNDER_REVIEW, APPROVED, VERIFIED, REJECTED, REUPLOAD_REQUIRED, OFFICIALLY_CERTIFIED
    val fileName: String = "",
    val fileReference: String = "",
    val reviewerNote: String = "",
    val fileMimeType: String = "application/pdf",
    val fileSizeBytes: Long = 0L,
    val sha256Checksum: String = "",
    val versionNumber: Int = 1,
    val reviewerRole: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PaymentRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val exportOrderId: String = "",
    val paypalOrderId: String = "",
    val paypalCaptureId: String = "",
    val approvalUrl: String = "",
    val amount: Double = 0.0,
    val currency: String = "USD",
    val paymentType: String = "ADVANCE", // ADVANCE, BALANCE, FULL
    val paymentStatus: String = "NOT_INITIATED", // NOT_INITIATED, PENDING_APPROVAL, APPROVED, CAPTURED, FAILED, CANCELLED, REQUIRES_RECONCILIATION
    val verificationDetails: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ActivityRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val exportOrderId: String = "",
    val eventType: String = "ORDER_CREATED",
    val title: String = "",
    val description: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class AIExtractedOrderDraft(
    val exporterName: String = "",
    val exporterContact: String = "",
    val exporterAddress: String = "",
    val buyerName: String = "",
    val buyerCountry: String = "",
    val buyerContact: String = "",
    val buyerAddress: String = "",
    val productName: String = "",
    val productDescription: String = "",
    val quantity: Double = 0.0,
    val unit: String = "KG",
    val unitPrice: Double = 0.0,
    val currency: String = "USD",
    val incoterm: String = "FOB",
    val destinationPort: String = "",
    val orderDate: String = "",
    val expectedShipmentDate: String = "",
    val paymentTerms: String = "30% Advance, 70% CAD",
    val advancePercentage: Double = 30.0,
    val confidenceScore: Int = 90,
    val missingFields: List<String> = emptyList(),
    val uncertainFields: List<String> = emptyList(),
    val conflictsDetected: List<String> = emptyList(),
    val aiExtractionNotes: String = ""
)

enum class LifecycleStageStatus {
    COMPLETED,
    IN_PROGRESS,
    ACTION_REQUIRED,
    PENDING
}

data class LifecycleStageNode(
    val stageNumber: Int,
    val title: String,
    val subtitle: String,
    val status: LifecycleStageStatus,
    val detailText: String,
    val targetScreenRoute: String
)

data class OutstandingAction(
    val id: String,
    val title: String,
    val description: String,
    val severity: String, // HIGH, MEDIUM, INFO
    val targetScreenRoute: String
)

data class DigitalTwinState(
    val order: ExportOrder,
    val financialAssessment: FinancialAssessment?,
    val documents: List<DocumentRecord>,
    val payments: List<PaymentRecord>,
    val documentReadinessPercent: Double,
    val verifiedRequiredDocs: Int,
    val totalRequiredDocs: Int,
    val capturedPaymentTotal: Double,
    val overallProgressPercent: Int,
    val currentStageLabel: String,
    val stages: List<LifecycleStageNode>,
    val outstandingActions: List<OutstandingAction>
)

data class PerformanceMetrics(
    val totalOrders: Int = 0,
    val activeOrders: Int = 0,
    val completedOrders: Int = 0,
    val cancelledOrders: Int = 0,
    val pendingOrders: Int = 0,
    val totalOrderValueInr: Double = 0.0,
    val capturedPaymentValueInr: Double = 0.0,
    val outstandingPaymentValueInr: Double = 0.0,
    val totalEstimatedProfitInr: Double = 0.0,
    val averageOrderValueInr: Double = 0.0,
    val averageProfitMarginPercent: Double = 0.0,
    val paymentCompletionRatePercent: Double = 0.0,
    val documentReadinessRatePercent: Double = 0.0,
    val statusDistribution: Map<String, Int> = emptyMap(),
    val countryDistribution: Map<String, Double> = emptyMap()
)

enum class TimePeriodFilter(val code: String, val label: String, val windowMillis: Long?) {
    ONE_DAY("1D", "Last 24 Hours", 24L * 60 * 60 * 1000L),
    ONE_WEEK("1W", "Last 7 Days", 7L * 24 * 60 * 60 * 1000L),
    ONE_MONTH("1M", "Last 30 Days", 30L * 24 * 60 * 60 * 1000L),
    THREE_MONTHS("3M", "Last 90 Days", 90L * 24 * 60 * 60 * 1000L),
    ONE_YEAR("1Y", "Last 12 Months", 365L * 24 * 60 * 60 * 1000L),
    ALL("ALL", "All-Time Portfolio", null);

    companion object {
        fun fromCode(code: String): TimePeriodFilter =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ONE_MONTH
    }
}

data class PeriodDashboardAnalytics(
    val period: TimePeriodFilter,
    val metrics: PerformanceMetrics,
    val filteredOrders: List<ExportOrder>,
    val filteredPayments: List<PaymentRecord>,
    val previousPeriodOrderValueInr: Double = 0.0,
    val orderValueGrowthPercent: Double? = null,
    val previousPeriodCapturedInr: Double = 0.0,
    val capturedRevenueGrowthPercent: Double? = null,
    val chartBucketLabels: List<String> = emptyList(),
    val chartOrderIntakeInr: List<Double> = emptyList(),
    val chartCapturedPaymentsInr: List<Double> = emptyList(),
    val periodSummaryText: String = ""
)
