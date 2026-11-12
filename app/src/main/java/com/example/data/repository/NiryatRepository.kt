package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ActivityRecord
import com.example.data.model.DocumentRecord
import com.example.data.model.ExportOrder
import com.example.data.model.FinancialAssessment
import com.example.data.model.PaymentRecord
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class NiryatRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun hasReviewerClaim(): Boolean {
        val user = auth.currentUser ?: return false
        return try {
            val tokenResult = user.getIdToken(false).await()
            val claims = tokenResult.claims
            claims["role"] == "REVIEWER" || claims["reviewer"] == true
        } catch (_: Exception) {
            false
        }
    }

    // --- OBSERVATION FLOWS ---

    fun observeOrders(userId: String = requireUserId()): Flow<List<ExportOrder>> = flow {
        val path = "export_orders"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ExportOrder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeFinancialAssessments(userId: String = requireUserId()): Flow<List<FinancialAssessment>> = flow {
        val path = "financial_assessments"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FinancialAssessment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeDocumentRecords(userId: String = requireUserId()): Flow<List<DocumentRecord>> = flow {
        val path = "document_records"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DocumentRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePaymentRecords(userId: String = requireUserId()): Flow<List<PaymentRecord>> = flow {
        val path = "payment_records"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PaymentRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeActivityRecords(userId: String = requireUserId()): Flow<List<ActivityRecord>> = flow {
        val path = "activity_records"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ActivityRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    // --- EXPORT ORDERS CRUD ---

    suspend fun createOrder(order: ExportOrder, initializeDefaultChecklist: Boolean = true): Result<String> {
        return try {
            val uid = requireUserId()
            val docRef = if (order.id.isNotBlank()) {
                db.collection("export_orders").document(order.id)
            } else {
                db.collection("export_orders").document()
            }
            val payload = mapOf(
                "userId" to uid,
                "orderNumber" to order.orderNumber,
                "exporterName" to order.exporterName,
                "exporterContact" to order.exporterContact,
                "exporterAddress" to order.exporterAddress,
                "buyerName" to order.buyerName,
                "buyerCountry" to order.buyerCountry,
                "buyerContact" to order.buyerContact,
                "buyerAddress" to order.buyerAddress,
                "productName" to order.productName,
                "productDescription" to order.productDescription,
                "quantity" to order.quantity,
                "unit" to order.unit,
                "unitPrice" to order.unitPrice,
                "orderValue" to order.orderValue,
                "currency" to order.currency,
                "incoterm" to order.incoterm,
                "destinationPort" to order.destinationPort,
                "orderDate" to order.orderDate,
                "expectedShipmentDate" to order.expectedShipmentDate,
                "paymentTerms" to order.paymentTerms,
                "advancePercentage" to order.advancePercentage,
                "orderStatus" to order.orderStatus,
                "extractionSource" to order.extractionSource,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()

            if (initializeDefaultChecklist) {
                initializeDocumentChecklistForOrder(docRef.id)
            }

            logActivity(
                exportOrderId = docRef.id,
                eventType = "ORDER_CREATED",
                title = "Export Order ${order.orderNumber} Created",
                description = "${order.productName} (${order.quantity} ${order.unit}) for ${order.buyerName} (${order.buyerCountry}) • ${order.currency} ${order.orderValue}"
            )
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "export_orders")
            Result.failure(e)
        }
    }

    suspend fun getOrderById(orderId: String): Result<ExportOrder> {
        val docRef = db.collection("export_orders").document(orderId)
        return try {
            val snap = docRef.get().await()
            val order = snap.toObject(
                ExportOrder::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: throw NoSuchElementException("Export order $orderId not found")
            Result.success(order)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun getUserOrders(): Result<List<ExportOrder>> {
        val uid = requireUserId()
        val path = "export_orders"
        return try {
            val snap = db.collection(path).whereEqualTo("userId", uid).get().await()
            val list = snap.toObjects(
                ExportOrder::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun updateOrderStatus(orderId: String, newStatus: String, reason: String = ""): Result<Unit> {
        val docRef = db.collection("export_orders").document(orderId)
        return try {
            requireUserId()
            docRef.update(
                mapOf(
                    "orderStatus" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (reason.isNotBlank()) {
                logActivity(
                    exportOrderId = orderId,
                    eventType = "STATUS_CHANGED",
                    title = "Lifecycle Status Updated to $newStatus",
                    description = reason
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteOrder(orderId: String): Result<Unit> {
        val docRef = db.collection("export_orders").document(orderId)
        return try {
            requireUserId()
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- FINANCIAL ASSESSMENT CRUD ---

    suspend fun saveFinancialAssessment(assessment: FinancialAssessment): Result<String> {
        val uid = requireUserId()
        val docId = assessment.id.ifBlank { assessment.exportOrderId }
        val docRef = db.collection("financial_assessments").document(docId)
        return try {
            val existingSnap = try {
                docRef.get().await()
            } catch (_: Exception) {
                null
            }
            val exists = existingSnap != null && existingSnap.exists()

            if (!exists) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "exportOrderId" to assessment.exportOrderId,
                    "grossOrderValue" to assessment.grossOrderValue,
                    "currency" to assessment.currency,
                    "exchangeRate" to assessment.exchangeRate,
                    "convertedValueInr" to assessment.convertedValueInr,
                    "advancePercentage" to assessment.advancePercentage,
                    "advanceAmount" to assessment.advanceAmount,
                    "outstandingAmount" to assessment.outstandingAmount,
                    "paymentFeePercentage" to assessment.paymentFeePercentage,
                    "paymentFixedFee" to assessment.paymentFixedFee,
                    "paymentFeeInr" to assessment.paymentFeeInr,
                    "procurementCostInr" to assessment.procurementCostInr,
                    "logisticsCostInr" to assessment.logisticsCostInr,
                    "packagingCostInr" to assessment.packagingCostInr,
                    "otherCostsInr" to assessment.otherCostsInr,
                    "estimatedNetRevenueInr" to assessment.estimatedNetRevenueInr,
                    "estimatedProfitInr" to assessment.estimatedProfitInr,
                    "profitMarginPercent" to assessment.profitMarginPercent,
                    "assumptionsNote" to assessment.assumptionsNote,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "grossOrderValue" to assessment.grossOrderValue,
                    "currency" to assessment.currency,
                    "exchangeRate" to assessment.exchangeRate,
                    "convertedValueInr" to assessment.convertedValueInr,
                    "advancePercentage" to assessment.advancePercentage,
                    "advanceAmount" to assessment.advanceAmount,
                    "outstandingAmount" to assessment.outstandingAmount,
                    "paymentFeePercentage" to assessment.paymentFeePercentage,
                    "paymentFixedFee" to assessment.paymentFixedFee,
                    "paymentFeeInr" to assessment.paymentFeeInr,
                    "procurementCostInr" to assessment.procurementCostInr,
                    "logisticsCostInr" to assessment.logisticsCostInr,
                    "packagingCostInr" to assessment.packagingCostInr,
                    "otherCostsInr" to assessment.otherCostsInr,
                    "estimatedNetRevenueInr" to assessment.estimatedNetRevenueInr,
                    "estimatedProfitInr" to assessment.estimatedProfitInr,
                    "profitMarginPercent" to assessment.profitMarginPercent,
                    "assumptionsNote" to assessment.assumptionsNote,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }

            logActivity(
                exportOrderId = assessment.exportOrderId,
                eventType = "FINANCIAL_UPDATED",
                title = "Financial Viability Assessed (${assessment.profitMarginPercent}% Margin)",
                description = "Net Revenue: ₹${assessment.estimatedNetRevenueInr} • Est. Profit: ₹${assessment.estimatedProfitInr} @ FX ${assessment.exchangeRate}"
            )
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    // --- DOCUMENT RECORDS CRUD ---

    suspend fun initializeDocumentChecklistForOrder(exportOrderId: String) {
        val uid = requireUserId()
        val defaultCategories = listOf(
            "Commercial Invoice" to "REQUIRED",
            "Packing List" to "REQUIRED",
            "Shipping Bill" to "REQUIRED",
            "Bill of Lading / Air Waybill" to "REQUIRED",
            "Certificate of Origin" to "REQUIRED",
            "Marine Insurance Certificate" to "OPTIONAL",
            "Phytosanitary / Quality Certificate" to "OPTIONAL"
        )
        defaultCategories.forEachIndexed { index, (docType, reqLevel) ->
            val docId = "${exportOrderId}_doc_${index + 1}"
            val docRef = db.collection("document_records").document(docId)
            val payload = mapOf(
                "userId" to uid,
                "exportOrderId" to exportOrderId,
                "documentType" to docType,
                "requirementLevel" to reqLevel,
                "status" to "MISSING",
                "fileName" to "",
                "fileReference" to "",
                "reviewerNote" to "Awaiting upload",
                "mimeType" to "application/pdf",
                "fileSizeBytes" to 0L,
                "sha256Hash" to "",
                "versionNumber" to 1,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                docRef.set(payload).await()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
            }
        }
    }

    suspend fun updateDocumentRecord(
        docId: String,
        exportOrderId: String,
        documentType: String,
        requirementLevel: String,
        status: String,
        fileName: String,
        fileReference: String,
        reviewerNote: String,
        mimeType: String = "application/pdf",
        fileSizeBytes: Long = 0L,
        sha256Hash: String = "",
        versionNumber: Int = 1
    ): Result<Unit> {
        val docRef = db.collection("document_records").document(docId)
        return try {
            requireUserId()
            // Enforce Part 5 reviewer authorization check before writing restricted states
            val isReviewer = hasReviewerClaim()
            val restrictedStates = setOf("APPROVED", "VERIFIED", "OFFICIALLY_CERTIFIED")
            if (status in restrictedStates && !isReviewer) {
                throw SecurityException(
                    "Security Policy: Ordinary exporter accounts cannot self-verify documents as '$status'. Submit as PENDING_REVIEW for an authorized Reviewer."
                )
            }

            docRef.update(
                mapOf(
                    "documentType" to documentType,
                    "requirementLevel" to requirementLevel,
                    "status" to status,
                    "fileName" to fileName,
                    "fileReference" to fileReference,
                    "reviewerNote" to reviewerNote,
                    "mimeType" to mimeType,
                    "fileSizeBytes" to fileSizeBytes,
                    "sha256Hash" to sha256Hash,
                    "versionNumber" to versionNumber,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            logActivity(
                exportOrderId = exportOrderId,
                eventType = if (status in restrictedStates) "DOC_VERIFIED" else "DOC_UPLOADED",
                title = "$documentType -> $status (v$versionNumber)",
                description = if (fileName.isNotBlank()) "File: $fileName ($reviewerNote)" else reviewerNote
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun addCustomDocumentCategory(
        exportOrderId: String,
        documentType: String,
        requirementLevel: String
    ): Result<String> {
        val uid = requireUserId()
        val docRef = db.collection("document_records").document()
        return try {
            val payload = mapOf(
                "userId" to uid,
                "exportOrderId" to exportOrderId,
                "documentType" to documentType,
                "requirementLevel" to requirementLevel,
                "status" to "MISSING",
                "fileName" to "",
                "fileReference" to "",
                "reviewerNote" to "Added to export checklist",
                "mimeType" to "application/pdf",
                "fileSizeBytes" to 0L,
                "sha256Hash" to "",
                "versionNumber" to 1,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- PAYMENT RECORDS CRUD ---

    suspend fun createPaymentRecord(payment: PaymentRecord): Result<String> {
        val uid = requireUserId()
        val docRef = if (payment.id.isNotBlank()) {
            db.collection("payment_records").document(payment.id)
        } else {
            db.collection("payment_records").document()
        }
        return try {
            if (payment.paymentStatus == "CAPTURED") {
                throw SecurityException("Client cannot directly create a CAPTURED payment record in Firestore.")
            }
            val payload = mapOf(
                "userId" to uid,
                "exportOrderId" to payment.exportOrderId,
                "paypalOrderId" to payment.paypalOrderId,
                "paypalCaptureId" to payment.paypalCaptureId,
                "approvalUrl" to payment.approvalUrl,
                "amount" to payment.amount,
                "currency" to payment.currency,
                "paymentType" to payment.paymentType,
                "paymentStatus" to payment.paymentStatus,
                "verificationDetails" to payment.verificationDetails,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()

            logActivity(
                exportOrderId = payment.exportOrderId,
                eventType = "PAYMENT_CREATED",
                title = "PayPal Sandbox Order Created (${payment.currency} ${payment.amount})",
                description = "PayPal Order ID: ${payment.paypalOrderId} • Status: ${payment.paymentStatus}"
            )
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updatePaymentRecord(
        paymentId: String,
        exportOrderId: String,
        paypalOrderId: String,
        paypalCaptureId: String,
        paymentStatus: String,
        verificationDetails: String
    ): Result<Unit> {
        val docRef = db.collection("payment_records").document(paymentId)
        return try {
            requireUserId()
            // In production, CAPTURED is already written by the Cloud Function capturePayPalSandboxOrder.
            if (paymentStatus == "CAPTURED") {
                logActivity(
                    exportOrderId = exportOrderId,
                    eventType = "PAYMENT_CAPTURED",
                    title = "PayPal Payment Verified by Backend ($paypalCaptureId)",
                    description = verificationDetails
                )
                return Result.success(Unit)
            }

            docRef.update(
                mapOf(
                    "paypalOrderId" to paypalOrderId,
                    "paypalCaptureId" to paypalCaptureId,
                    "paymentStatus" to paymentStatus,
                    "verificationDetails" to verificationDetails,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            logActivity(
                exportOrderId = exportOrderId,
                eventType = "STATUS_CHANGED",
                title = "PayPal Payment Status: $paymentStatus",
                description = verificationDetails
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- ACTIVITY LOGGING ---

    suspend fun logActivity(
        exportOrderId: String,
        eventType: String,
        title: String,
        description: String
    ) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("activity_records").document()
        val payload = mapOf(
            "userId" to uid,
            "exportOrderId" to exportOrderId,
            "eventType" to eventType,
            "title" to title.take(200),
            "description" to description.take(1000),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
        }
    }
}
