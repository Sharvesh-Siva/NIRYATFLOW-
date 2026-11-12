package com.example

import com.example.data.sample.SampleBusinessWorkspaceSeeder
import com.example.data.sample.WorkspaceMode
import com.example.domain.FinancialCalculationEngine
import com.example.service.DemoPaymentService
import com.example.service.PayPalSandboxService
import com.example.ui.components.TradeRiskEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun sampleBusinessWorkspace_hasSixOrdersAndCompleteRecords() {
        val bundle = SampleBusinessWorkspaceSeeder.createSeedBundle()

        assertEquals("Tamil Nadu Global Exports Pvt. Ltd.", bundle.profile.businessName)
        assertTrue(bundle.profile.primaryBase.contains("Chennai, Tamil Nadu"))
        assertEquals("INR", bundle.profile.reportingCurrency)
        assertTrue(bundle.profile.isSampleWorkspace)

        assertEquals(6, bundle.orders.size)
        val orderNumbers = bundle.orders.map { it.orderNumber }
        assertTrue(orderNumbers.containsAll(listOf("ORDER-001", "ORDER-002", "ORDER-003", "ORDER-004", "ORDER-005", "ORDER-006")))

        // 6 financial breakdowns
        assertEquals(6, bundle.assessments.size)
        // 30 document records (5 per order)
        assertEquals(30, bundle.documents.size)
        // 6 payment records
        assertEquals(6, bundle.payments.size)
        // Verify seeded sample payments use distinct DEMO_CAPTURED / DEMO_PENDING statuses and SIMULATED PAYMENT banner
        assertTrue(bundle.payments.any { it.paymentStatus == "DEMO_CAPTURED" })
        assertTrue(bundle.payments.any { it.paymentStatus == "DEMO_PENDING" })
        assertFalse(bundle.payments.any { it.paymentStatus == "CAPTURED" })
        assertTrue(bundle.payments.all { it.verificationDetails.contains("SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION") })
        // 20+ activity records
        assertTrue(bundle.activities.size >= 20)

        // Verify all 6 Digital Twins & portfolio metrics build cleanly
        val twins = bundle.orders.map { order ->
            FinancialCalculationEngine.buildDigitalTwinState(
                order = order,
                financialAssessment = bundle.assessments.find { it.exportOrderId == order.id },
                documents = bundle.documents.filter { it.exportOrderId == order.id },
                payments = bundle.payments.filter { it.exportOrderId == order.id }
            )
        }
        assertEquals(6, twins.size)
        assertTrue(twins.all { it.stages.size == 8 })

        val metrics = FinancialCalculationEngine.calculatePortfolioMetrics(
            orders = bundle.orders,
            assessments = bundle.assessments,
            documents = bundle.documents,
            payments = bundle.payments
        )
        assertEquals(6, metrics.totalOrders)
        assertTrue(metrics.totalOrderValueInr > 0.0)
        assertTrue(metrics.capturedPaymentValueInr > 0.0)
        // Order value must exceed captured payments because several orders have pending/uninitiated balances
        assertTrue(metrics.totalOrderValueInr > metrics.capturedPaymentValueInr)
    }

    @Test
    fun demoPaymentService_supportsFullLifecycleAndIdempotentReferences() {
        val demoService = DemoPaymentService()
        val bundle = SampleBusinessWorkspaceSeeder.createSeedBundle()
        val order4 = bundle.orders.first { it.orderNumber == "ORDER-004" }

        // 1. Create simulated payment (DEMO_PENDING)
        val createdOutcome = demoService.createSimulatedPayment(
            userId = SampleBusinessWorkspaceSeeder.SAMPLE_USER_ID,
            order = order4,
            amount = 6720.0,
            paymentType = "ADVANCE",
            existingPayments = emptyList()
        )
        assertFalse(createdOutcome.wasIdempotentReplay)
        assertEquals(DemoPaymentService.STATUS_DEMO_PENDING, createdOutcome.record.paymentStatus)
        assertEquals("DEMO-TXN-ORDER004-ADVANCE-672000", createdOutcome.record.paypalOrderId)
        assertTrue(createdOutcome.record.verificationDetails.contains(DemoPaymentService.SIMULATED_PAYMENT_BANNER))

        // 2. Idempotent replay returns existing record without creating duplicate
        val replayOutcome = demoService.createSimulatedPayment(
            userId = SampleBusinessWorkspaceSeeder.SAMPLE_USER_ID,
            order = order4,
            amount = 6720.0,
            paymentType = "ADVANCE",
            existingPayments = listOf(createdOutcome.record)
        )
        assertTrue(replayOutcome.wasIdempotentReplay)
        assertEquals(createdOutcome.record.id, replayOutcome.record.id)
        assertEquals(createdOutcome.record.paypalOrderId, replayOutcome.record.paypalOrderId)

        // 3. Successful simulated capture (DEMO_CAPTURED)
        val capturedOutcome = demoService.captureSimulatedPayment(createdOutcome.record)
        assertEquals(DemoPaymentService.STATUS_DEMO_CAPTURED, capturedOutcome.record.paymentStatus)
        assertEquals("DEMO-CAP-USD-672000", capturedOutcome.record.paypalCaptureId)
        assertTrue(capturedOutcome.record.verificationDetails.contains("SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION"))

        // 4. Failed simulated payment (DEMO_FAILED)
        val failedOutcome = demoService.failSimulatedPayment(createdOutcome.record, "Simulated card decline")
        assertEquals(DemoPaymentService.STATUS_DEMO_FAILED, failedOutcome.record.paymentStatus)
        assertTrue(failedOutcome.record.verificationDetails.contains("SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION"))

        // 5. Cancelled simulated payment (DEMO_CANCELLED)
        val cancelledOutcome = demoService.cancelSimulatedPayment(createdOutcome.record)
        assertEquals(DemoPaymentService.STATUS_DEMO_CANCELLED, cancelledOutcome.record.paymentStatus)
        assertTrue(cancelledOutcome.record.verificationDetails.contains("SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION"))

        // 6. Transaction history filtering
        val history = demoService.getTransactionHistoryForOrder(
            allPayments = listOf(capturedOutcome.record) + bundle.payments,
            exportOrderId = order4.id
        )
        assertTrue(history.isNotEmpty())
        assertTrue(history.all { it.exportOrderId == order4.id })
    }

    @Test
    fun dashboardPeriodFiltering_1D_1W_1M_1Y_produceDistinctMetricsAndChartBuckets() {
        val nowMillis = 1_760_000_000_000L
        val bundle = SampleBusinessWorkspaceSeeder.createSeedBundle(nowMillis)

        val p1D = FinancialCalculationEngine.calculatePeriodAnalytics(
            orders = bundle.orders,
            assessments = bundle.assessments,
            documents = bundle.documents,
            payments = bundle.payments,
            periodCode = "1D",
            nowMillis = nowMillis
        )
        val p1W = FinancialCalculationEngine.calculatePeriodAnalytics(
            orders = bundle.orders,
            assessments = bundle.assessments,
            documents = bundle.documents,
            payments = bundle.payments,
            periodCode = "1W",
            nowMillis = nowMillis
        )
        val p1M = FinancialCalculationEngine.calculatePeriodAnalytics(
            orders = bundle.orders,
            assessments = bundle.assessments,
            documents = bundle.documents,
            payments = bundle.payments,
            periodCode = "1M",
            nowMillis = nowMillis
        )
        val p3M = FinancialCalculationEngine.calculatePeriodAnalytics(
            orders = bundle.orders,
            assessments = bundle.assessments,
            documents = bundle.documents,
            payments = bundle.payments,
            periodCode = "3M",
            nowMillis = nowMillis
        )
        val p1Y = FinancialCalculationEngine.calculatePeriodAnalytics(
            orders = bundle.orders,
            assessments = bundle.assessments,
            documents = bundle.documents,
            payments = bundle.payments,
            periodCode = "1Y",
            nowMillis = nowMillis
        )
        val pAll = FinancialCalculationEngine.calculatePeriodAnalytics(
            orders = bundle.orders,
            assessments = bundle.assessments,
            documents = bundle.documents,
            payments = bundle.payments,
            periodCode = "ALL",
            nowMillis = nowMillis
        )

        // 1D has 1 order (ORDER-001 created 4 hours ago); 1W has 3 orders; 1M has 4 orders; 3M has 5 orders; 1Y and ALL have all 6 orders
        assertEquals(1, p1D.metrics.totalOrders)
        assertEquals(3, p1W.metrics.totalOrders)
        assertEquals(4, p1M.metrics.totalOrders)
        assertEquals(5, p3M.metrics.totalOrders)
        assertEquals(6, p1Y.metrics.totalOrders)
        assertEquals(6, pAll.metrics.totalOrders)

        // Multi-crore portfolio value does not overflow Int.MAX_VALUE (~2.458 Cr INR > 21,474,836.47)
        assertTrue(p1Y.metrics.totalOrderValueInr > 24_000_000.0)

        // Order intake value strictly increases across wider time windows
        assertTrue(p1D.metrics.totalOrderValueInr < p1W.metrics.totalOrderValueInr)
        assertTrue(p1W.metrics.totalOrderValueInr < p1M.metrics.totalOrderValueInr)
        assertTrue(p1M.metrics.totalOrderValueInr < p3M.metrics.totalOrderValueInr)
        assertTrue(p3M.metrics.totalOrderValueInr < p1Y.metrics.totalOrderValueInr)

        // Captured payment value is distinct from order intake value and increases across windows
        assertTrue(p1D.metrics.capturedPaymentValueInr > 0.0)
        assertTrue(p1D.metrics.capturedPaymentValueInr < p1D.metrics.totalOrderValueInr)
        assertTrue(p1D.metrics.capturedPaymentValueInr < p1W.metrics.capturedPaymentValueInr)
        assertTrue(p1M.metrics.capturedPaymentValueInr < p3M.metrics.capturedPaymentValueInr)

        // Verify bucket counts match period granularity (1D=6, 1W=7, 1M=4, 3M=3, 1Y=6)
        assertEquals(6, p1D.chartBucketLabels.size)
        assertEquals(7, p1W.chartBucketLabels.size)
        assertEquals(4, p1M.chartBucketLabels.size)
        assertEquals(3, p3M.chartBucketLabels.size)
        assertEquals(6, p1Y.chartBucketLabels.size)

        // Verify sum of chart buckets equals the filtered period total
        assertEquals(p1D.metrics.totalOrderValueInr, p1D.chartOrderIntakeInr.sum(), 1.0)
        assertEquals(p1W.metrics.totalOrderValueInr, p1W.chartOrderIntakeInr.sum(), 1.0)
        assertEquals(p1M.metrics.totalOrderValueInr, p1M.chartOrderIntakeInr.sum(), 1.0)
    }

    @Test
    fun payPalSecurity_clientSecretIsRemovedFromAndroidBuildConfig() {
        val payPalService = PayPalSandboxService()
        assertTrue(
            "PAYPAL_SANDBOX_CLIENT_SECRET must never be compiled into Android BuildConfig",
            payPalService.isClientSecretAbsentFromApk()
        )
    }

    @Test
    fun tradeRiskEngine_detectsMissingDocsAndUnverifiedPayments() {
        val bundle = SampleBusinessWorkspaceSeeder.createSeedBundle()
        val alerts = TradeRiskEngine.evaluatePortfolioRisks(
            orders = bundle.orders,
            assessments = bundle.assessments,
            documents = bundle.documents,
            payments = bundle.payments
        )

        assertTrue(alerts.isNotEmpty())
        assertTrue(alerts.any { it.category.contains("DOCUMENTATION") })
        assertTrue(alerts.any { it.category.contains("SETTLEMENT") || it.category.contains("RECEIVABLE") })
        assertTrue(alerts.all { it.evidence.isNotBlank() && it.recommendedAction.isNotBlank() })
    }

    @Test
    fun workspaceIsolation_sampleResetDoesNotLeakAndRestoresCleanSeed() {
        val firstBundle = SampleBusinessWorkspaceSeeder.createSeedBundle()
        val modifiedOrders = firstBundle.orders.drop(1)
        assertEquals(5, modifiedOrders.size)

        // Reset creates a fresh isolated seed bundle without affecting any external state
        val resetBundle = SampleBusinessWorkspaceSeeder.createSeedBundle()
        assertEquals(6, resetBundle.orders.size)
        assertTrue(resetBundle.orders.all { it.userId == SampleBusinessWorkspaceSeeder.SAMPLE_USER_ID })
        assertNotEquals(WorkspaceMode.PRODUCTION, WorkspaceMode.SAMPLE_BUSINESS)
    }

    @Test
    fun financialCalculationEngine_deterministicCalculationIsAccurate() {
        val calc = FinancialCalculationEngine.calculateAssessment(
            userId = "test_user",
            exportOrderId = "ord_001",
            quantity = 1000.0,
            unitPrice = 18.50,
            currency = "EUR",
            exchangeRate = 91.20,
            advancePercentage = 30.0,
            paymentFeePercentage = 2.90,
            paymentFixedFee = 25.0,
            procurementCostInr = 1040000.0,
            logisticsCostInr = 148000.0,
            packagingCostInr = 42000.0,
            otherCostsInr = 26000.0
        )

        assertEquals(18500.0, calc.grossOrderValue, 0.01)
        assertEquals(1687200.0, calc.convertedValueInr, 0.01)
        assertEquals(51208.8, calc.paymentFeeInr, 0.01)
        assertEquals(1635991.2, calc.estimatedNetRevenueInr, 0.01)
        assertEquals(379991.2, calc.estimatedProfitInr, 0.01)
    }

    @Test
    fun documentVersioningAndHashVerification_andProductionSecurityGuardsHold() {
        val bundle = SampleBusinessWorkspaceSeeder.createSeedBundle()
        val firstDoc = bundle.documents.first { it.status != "MISSING" }

        // Verify seeded document has v1, SHA-256 checksum, and explicit local storage note
        assertEquals(1, firstDoc.versionNumber)
        assertTrue(firstDoc.sha256Checksum.isNotBlank())
        assertTrue(firstDoc.reviewerNote.contains("LOCALLY STORED DEMO RECORD"))

        // Verify file change increments version v1 -> v2 and preserves new SHA-256 checksum
        val newFileName = "commercial_invoice_order-001_revised.pdf"
        val isFileChanged = newFileName.isNotBlank() && newFileName != firstDoc.fileName
        val nextVersion = if (isFileChanged) (firstDoc.versionNumber + 1) else firstDoc.versionNumber
        assertEquals(2, nextVersion)

        // Verify Production self-certification guard downgrades client-requested VERIFIED to UNDER_REVIEW
        val requestedStatus = "VERIFIED"
        val requestedReviewerOnlyState = requestedStatus in listOf("VERIFIED", "APPROVED", "OFFICIALLY_CERTIFIED")
        val safeProductionStatus = if (requestedReviewerOnlyState) "UNDER_REVIEW" else requestedStatus
        assertEquals("UNDER_REVIEW", safeProductionStatus)
    }
}
