package com.example.service

import com.example.data.model.ActivityRecord
import com.example.data.model.ExportOrder
import com.example.data.model.PaymentRecord
import com.google.firebase.Timestamp
import java.util.Locale

data class DemoPaymentOperationResult(
    val record: PaymentRecord,
    val activity: ActivityRecord,
    val wasIdempotentReplay: Boolean,
    val updatedLedger: List<PaymentRecord>,
    val statusMessage: String
) {
    val message: String
        get() = statusMessage
}

/**
 * Zero-Additional-Cost Simulated Payment Engine for NiryatFlow AI Demo Mode.
 *
 * STRICT SEPARATION & HONESTY GUARANTEES:
 * 1. Never represents simulated transactions as actual PayPal transactions.
 * 2. Uses distinct statuses: `DEMO_PENDING`, `DEMO_CAPTURED`, `DEMO_FAILED`, `DEMO_CANCELLED`.
 * 3. Always includes the explicit notice: "SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION".
 * 4. Enforces idempotent transaction references so repeated creation requests with the same
 *    order, milestone type, and amount return the existing transaction record.
 * 5. Never writes simulated transactions into Production Firestore `payment_records`.
 */
class DemoPaymentService {

    companion object {
        const val SIMULATED_PAYMENT_NOTICE = "SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION"
        const val SIMULATED_PAYMENT_BANNER = SIMULATED_PAYMENT_NOTICE
        const val STATUS_DEMO_PENDING = "DEMO_PENDING"
        const val STATUS_DEMO_CAPTURED = "DEMO_CAPTURED"
        const val STATUS_DEMO_FAILED = "DEMO_FAILED"
        const val STATUS_DEMO_CANCELLED = "DEMO_CANCELLED"

        fun isDemoCaptured(status: String): Boolean {
            return status.equals(STATUS_DEMO_CAPTURED, ignoreCase = true)
        }

        fun isSettledOrDemoCaptured(status: String): Boolean {
            return status.equals("CAPTURED", ignoreCase = true) ||
                status.equals(STATUS_DEMO_CAPTURED, ignoreCase = true)
        }

        fun isPendingOrDemoPending(status: String): Boolean {
            return status.equals("PENDING_APPROVAL", ignoreCase = true) ||
                status.equals("APPROVED", ignoreCase = true) ||
                status.equals(STATUS_DEMO_PENDING, ignoreCase = true)
        }
    }

    /**
     * Generates a deterministic idempotent transaction reference for a given order, payment type, and amount.
     */
    fun buildIdempotentOrderReference(
        orderNumber: String,
        paymentType: String,
        amount: Double
    ): String {
        val cleanOrder = orderNumber.trim().uppercase(Locale.US).replace(Regex("[^A-Z0-9]"), "")
        val cleanType = paymentType.trim().uppercase(Locale.US).ifBlank { "ADVANCE" }
        val cents = kotlin.math.round(amount * 100.0).toLong()
        return "DEMO-TXN-$cleanOrder-$cleanType-$cents"
    }

    /**
     * Creates a simulated pending payment (`DEMO_PENDING`) or returns the existing record
     * if an active transaction with the same idempotent reference already exists.
     */
    fun createSimulatedPayment(
        userId: String,
        order: ExportOrder,
        amount: Double,
        paymentType: String,
        existingPayments: List<PaymentRecord>,
        nowTimestamp: Timestamp = Timestamp.now()
    ): DemoPaymentOperationResult {
        val safeAmount = kotlin.math.round(amount.coerceAtLeast(0.01) * 100.0) / 100.0
        val cleanCurrency = order.currency.trim().uppercase(Locale.US).ifBlank { "USD" }
        val cleanType = paymentType.trim().uppercase(Locale.US).ifBlank { "ADVANCE" }
        val idempotentRef = buildIdempotentOrderReference(order.orderNumber, cleanType, safeAmount)

        // Check for existing active idempotent transaction (DEMO_PENDING or DEMO_CAPTURED)
        val existingMatch = existingPayments.firstOrNull { rec ->
            rec.exportOrderId == order.id &&
                rec.paypalOrderId == idempotentRef &&
                (rec.paymentStatus == STATUS_DEMO_PENDING || rec.paymentStatus == STATUS_DEMO_CAPTURED)
        }

        if (existingMatch != null) {
            val replayActivity = ActivityRecord(
                id = "demo_act_replay_${System.currentTimeMillis()}",
                userId = userId,
                exportOrderId = order.id,
                eventType = "DEMO_PAYMENT_REPLAY",
                title = "${order.orderNumber} Idempotent Demo Payment Returned",
                description = "$SIMULATED_PAYMENT_NOTICE • Existing $idempotentRef (${existingMatch.paymentStatus}) reused.",
                createdAt = nowTimestamp,
                updatedAt = nowTimestamp
            )
            return DemoPaymentOperationResult(
                record = existingMatch,
                activity = replayActivity,
                wasIdempotentReplay = true,
                updatedLedger = existingPayments,
                statusMessage = "$SIMULATED_PAYMENT_NOTICE • Idempotent replay: existing transaction $idempotentRef (${existingMatch.paymentStatus}) returned."
            )
        }

        val newRecord = PaymentRecord(
            id = "demo_pay_${order.id}_${cleanType.lowercase(Locale.US)}_${kotlin.math.round(safeAmount * 100).toLong()}",
            userId = userId,
            exportOrderId = order.id,
            paypalOrderId = idempotentRef,
            paypalCaptureId = "",
            approvalUrl = "",
            amount = safeAmount,
            currency = cleanCurrency,
            paymentType = cleanType,
            paymentStatus = STATUS_DEMO_PENDING,
            verificationDetails = "$SIMULATED_PAYMENT_NOTICE • Created simulated $cleanType payment ($idempotentRef) for $cleanCurrency ${String.format(Locale.US, "%.2f", safeAmount)}.",
            createdAt = nowTimestamp,
            updatedAt = nowTimestamp
        )

        val newActivity = ActivityRecord(
            id = "demo_act_${System.currentTimeMillis()}",
            userId = userId,
            exportOrderId = order.id,
            eventType = "DEMO_PAYMENT_CREATED",
            title = "${order.orderNumber} Simulated Payment Created ($STATUS_DEMO_PENDING)",
            description = "$SIMULATED_PAYMENT_NOTICE • Ref $idempotentRef for $cleanCurrency $safeAmount.",
            createdAt = nowTimestamp,
            updatedAt = nowTimestamp
        )

        val updatedList = listOf(newRecord) + existingPayments.filterNot { it.id == newRecord.id }
        return DemoPaymentOperationResult(
            record = newRecord,
            activity = newActivity,
            wasIdempotentReplay = false,
            updatedLedger = updatedList,
            statusMessage = "$SIMULATED_PAYMENT_NOTICE • Created simulated payment $idempotentRef ($STATUS_DEMO_PENDING)."
        )
    }

    /**
     * Transitions a simulated payment to `DEMO_CAPTURED`.
     * Idempotent: if already `DEMO_CAPTURED`, returns the record unchanged.
     */
    fun captureSimulatedPayment(
        payment: PaymentRecord,
        nowTimestamp: Timestamp = Timestamp.now(),
        existingPayments: List<PaymentRecord> = emptyList()
    ): DemoPaymentOperationResult {
        if (payment.paymentStatus == STATUS_DEMO_CAPTURED && payment.paypalCaptureId.isNotBlank()) {
            val replayActivity = ActivityRecord(
                id = "demo_act_cap_replay_${System.currentTimeMillis()}",
                userId = payment.userId,
                exportOrderId = payment.exportOrderId,
                eventType = "DEMO_PAYMENT_CAPTURED",
                title = "Idempotent Simulated Capture (${payment.paypalCaptureId})",
                description = "$SIMULATED_PAYMENT_NOTICE • Already settled as $STATUS_DEMO_CAPTURED.",
                createdAt = nowTimestamp,
                updatedAt = nowTimestamp
            )
            return DemoPaymentOperationResult(
                record = payment,
                activity = replayActivity,
                wasIdempotentReplay = true,
                updatedLedger = existingPayments,
                statusMessage = "$SIMULATED_PAYMENT_NOTICE • Idempotent replay: ${payment.paypalCaptureId} is already $STATUS_DEMO_CAPTURED."
            )
        }

        val cents = kotlin.math.round(payment.amount * 100.0).toLong()
        val deterministicCaptureRef = "DEMO-CAP-${payment.currency.uppercase(Locale.US)}-$cents"
        val updated = payment.copy(
            paypalCaptureId = deterministicCaptureRef,
            paymentStatus = STATUS_DEMO_CAPTURED,
            verificationDetails = "$SIMULATED_PAYMENT_NOTICE • Simulated settlement completed ($STATUS_DEMO_CAPTURED) • Ref: $deterministicCaptureRef",
            updatedAt = nowTimestamp
        )

        val capActivity = ActivityRecord(
            id = "demo_act_cap_${System.currentTimeMillis()}",
            userId = payment.userId,
            exportOrderId = payment.exportOrderId,
            eventType = "DEMO_PAYMENT_CAPTURED",
            title = "Simulated Payment Captured ($deterministicCaptureRef)",
            description = "$SIMULATED_PAYMENT_NOTICE • Settled ${payment.currency} ${payment.amount} as $STATUS_DEMO_CAPTURED.",
            createdAt = nowTimestamp,
            updatedAt = nowTimestamp
        )

        val updatedList = existingPayments.map { if (it.id == payment.id) updated else it }
        return DemoPaymentOperationResult(
            record = updated,
            activity = capActivity,
            wasIdempotentReplay = false,
            updatedLedger = updatedList,
            statusMessage = "$SIMULATED_PAYMENT_NOTICE • Marked ${payment.paypalOrderId} as $STATUS_DEMO_CAPTURED ($deterministicCaptureRef)."
        )
    }

    /**
     * Transitions a simulated payment to `DEMO_FAILED`.
     */
    fun failSimulatedPayment(
        payment: PaymentRecord,
        reason: String = "Simulated buyer bank decline (Demo Test)",
        nowTimestamp: Timestamp = Timestamp.now(),
        existingPayments: List<PaymentRecord> = emptyList()
    ): DemoPaymentOperationResult {
        val updated = payment.copy(
            paypalCaptureId = "",
            paymentStatus = STATUS_DEMO_FAILED,
            verificationDetails = "$SIMULATED_PAYMENT_NOTICE • Simulated failure ($STATUS_DEMO_FAILED): $reason",
            updatedAt = nowTimestamp
        )
        val failActivity = ActivityRecord(
            id = "demo_act_fail_${System.currentTimeMillis()}",
            userId = payment.userId,
            exportOrderId = payment.exportOrderId,
            eventType = "DEMO_PAYMENT_FAILED",
            title = "Simulated Payment Failed (${payment.paypalOrderId})",
            description = "$SIMULATED_PAYMENT_NOTICE • $reason",
            createdAt = nowTimestamp,
            updatedAt = nowTimestamp
        )
        val updatedList = existingPayments.map { if (it.id == payment.id) updated else it }
        return DemoPaymentOperationResult(
            record = updated,
            activity = failActivity,
            wasIdempotentReplay = false,
            updatedLedger = updatedList,
            statusMessage = "$SIMULATED_PAYMENT_NOTICE • Marked ${payment.paypalOrderId} as $STATUS_DEMO_FAILED."
        )
    }

    /**
     * Transitions a simulated payment to `DEMO_CANCELLED`.
     */
    fun cancelSimulatedPayment(
        payment: PaymentRecord,
        nowTimestamp: Timestamp = Timestamp.now(),
        existingPayments: List<PaymentRecord> = emptyList()
    ): DemoPaymentOperationResult {
        val updated = payment.copy(
            paypalCaptureId = "",
            paymentStatus = STATUS_DEMO_CANCELLED,
            verificationDetails = "$SIMULATED_PAYMENT_NOTICE • Simulated payment cancelled ($STATUS_DEMO_CANCELLED).",
            updatedAt = nowTimestamp
        )
        val cancelActivity = ActivityRecord(
            id = "demo_act_cancel_${System.currentTimeMillis()}",
            userId = payment.userId,
            exportOrderId = payment.exportOrderId,
            eventType = "DEMO_PAYMENT_CANCELLED",
            title = "Simulated Payment Cancelled (${payment.paypalOrderId})",
            description = "$SIMULATED_PAYMENT_NOTICE • Marked as $STATUS_DEMO_CANCELLED.",
            createdAt = nowTimestamp,
            updatedAt = nowTimestamp
        )
        val updatedList = existingPayments.map { if (it.id == payment.id) updated else it }
        return DemoPaymentOperationResult(
            record = updated,
            activity = cancelActivity,
            wasIdempotentReplay = false,
            updatedLedger = updatedList,
            statusMessage = "$SIMULATED_PAYMENT_NOTICE • Marked ${payment.paypalOrderId} as $STATUS_DEMO_CANCELLED."
        )
    }

    /**
     * Returns chronological transaction history for a specific export order.
     */
    fun getTransactionHistoryForOrder(
        allPayments: List<PaymentRecord>,
        exportOrderId: String
    ): List<PaymentRecord> {
        return allPayments
            .filter { it.exportOrderId == exportOrderId }
            .sortedByDescending { it.updatedAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L }
    }
}
