package com.example.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AIExtractedOrderDraft
import com.example.data.model.ActivityRecord
import com.example.data.model.DigitalTwinState
import com.example.data.model.DocumentRecord
import com.example.data.model.ExportOrder
import com.example.data.model.FinancialAssessment
import com.example.data.model.PaymentRecord
import com.example.data.model.PerformanceMetrics
import com.example.data.repository.NiryatRepository
import com.example.data.sample.BusinessWorkspaceProfile
import com.example.data.sample.SampleBusinessWorkspaceSeeder
import com.example.data.sample.SampleWorkspaceLocalStore
import com.example.data.sample.SampleWorkspaceSeedBundle
import com.example.data.sample.WorkspaceMode
import com.example.domain.FinancialCalculationEngine
import com.example.service.DemoPaymentService
import com.example.service.GeminiIntelligenceService
import com.example.service.PayPalSandboxService
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class AiTaskState<out T> {
    data object Idle : AiTaskState<Nothing>()
    data object Loading : AiTaskState<Nothing>()
    data class Success<T>(val data: T) : AiTaskState<T>()
    data class Error(val message: String) : AiTaskState<Nothing>()
}

data class NiryatPortfolioState(
    val workspaceMode: WorkspaceMode = WorkspaceMode.PRODUCTION,
    val businessProfile: BusinessWorkspaceProfile = SampleBusinessWorkspaceSeeder.defaultProductionProfile(""),
    val orders: List<ExportOrder> = emptyList(),
    val assessments: List<FinancialAssessment> = emptyList(),
    val documents: List<DocumentRecord> = emptyList(),
    val payments: List<PaymentRecord> = emptyList(),
    val activities: List<ActivityRecord> = emptyList(),
    val selectedOrderId: String? = null,
    val selectedDigitalTwin: DigitalTwinState? = null,
    val allDigitalTwins: List<DigitalTwinState> = emptyList(),
    val performanceMetrics: PerformanceMetrics = PerformanceMetrics(),
    val isLoading: Boolean = true
) {
    val isSampleWorkspace: Boolean
        get() = workspaceMode == WorkspaceMode.SAMPLE_BUSINESS

    val isDemoMode: Boolean
        get() = workspaceMode == WorkspaceMode.SAMPLE_BUSINESS
}

class NiryatViewModel(
    private val repository: NiryatRepository?,
    private val currentUserId: String,
    initialMode: WorkspaceMode = if (repository == null) WorkspaceMode.SAMPLE_BUSINESS else WorkspaceMode.PRODUCTION,
    private val sampleLocalStore: SampleWorkspaceLocalStore? = null
) : ViewModel() {

    private val geminiService = GeminiIntelligenceService()
    private val payPalService = PayPalSandboxService()
    private val demoPaymentService = DemoPaymentService()

    private val _workspaceMode = MutableStateFlow(initialMode)
    val workspaceMode: StateFlow<WorkspaceMode> = _workspaceMode.asStateFlow()

    private val _productionProfile = MutableStateFlow(
        SampleBusinessWorkspaceSeeder.defaultProductionProfile("")
    )

    private val _sampleBundle = MutableStateFlow<SampleWorkspaceSeedBundle>(
        sampleLocalStore?.loadOrSeed() ?: SampleBusinessWorkspaceSeeder.createSeedBundle()
    )

    private val _selectedOrderId = MutableStateFlow<String?>(null)
    val selectedOrderId: StateFlow<String?> = _selectedOrderId.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _extractionState = MutableStateFlow<AiTaskState<AIExtractedOrderDraft>>(AiTaskState.Idle)
    val extractionState: StateFlow<AiTaskState<AIExtractedOrderDraft>> = _extractionState.asStateFlow()

    private val _financialAiExplanation = MutableStateFlow<AiTaskState<String>>(AiTaskState.Idle)
    val financialAiExplanation: StateFlow<AiTaskState<String>> = _financialAiExplanation.asStateFlow()

    private val _portfolioAiInsights = MutableStateFlow<AiTaskState<String>>(AiTaskState.Idle)
    val portfolioAiInsights: StateFlow<AiTaskState<String>> = _portfolioAiInsights.asStateFlow()

    private val _nlQueryState = MutableStateFlow<AiTaskState<String>>(AiTaskState.Idle)
    val nlQueryState: StateFlow<AiTaskState<String>> = _nlQueryState.asStateFlow()

    private val _paymentBusy = MutableStateFlow(false)
    val paymentBusy: StateFlow<Boolean> = _paymentBusy.asStateFlow()

    private val _orderSubmitting = MutableStateFlow(false)
    val orderSubmitting: StateFlow<Boolean> = _orderSubmitting.asStateFlow()

    private val _paypalAuthCheckState = MutableStateFlow<String?>(null)
    val paypalAuthCheckState: StateFlow<String?> = _paypalAuthCheckState.asStateFlow()

    val isPayPalConfigured: Boolean
        get() = payPalService.isConfigured()

    private fun mutateSampleBundle(transform: (SampleWorkspaceSeedBundle) -> SampleWorkspaceSeedBundle) {
        val updated = transform(_sampleBundle.value)
        _sampleBundle.value = updated
        sampleLocalStore?.saveBundle(updated)
    }

    private val prodOrdersFlow: Flow<List<ExportOrder>> =
        repository?.observeOrders(currentUserId) ?: flowOf(emptyList())
    private val prodAssessmentsFlow: Flow<List<FinancialAssessment>> =
        repository?.observeFinancialAssessments(currentUserId) ?: flowOf(emptyList())
    private val prodDocumentsFlow: Flow<List<DocumentRecord>> =
        repository?.observeDocumentRecords(currentUserId) ?: flowOf(emptyList())
    private val prodPaymentsFlow: Flow<List<PaymentRecord>> =
        repository?.observePaymentRecords(currentUserId) ?: flowOf(emptyList())
    private val prodActivitiesFlow: Flow<List<ActivityRecord>> =
        repository?.observeActivityRecords(currentUserId) ?: flowOf(emptyList())

    private val rawProductionStream: Flow<SampleWorkspaceSeedBundle> = combine(
        prodOrdersFlow,
        prodAssessmentsFlow,
        prodDocumentsFlow,
        prodPaymentsFlow,
        prodActivitiesFlow
    ) { orders, assessments, documents, payments, activities ->
        SampleWorkspaceSeedBundle(
            profile = _productionProfile.value,
            orders = orders,
            assessments = assessments,
            documents = documents,
            payments = payments,
            activities = activities
        )
    }

    val portfolioState: StateFlow<NiryatPortfolioState> = combine(
        _workspaceMode,
        rawProductionStream,
        _sampleBundle,
        _selectedOrderId,
        _productionProfile
    ) { mode, prodBundle, sampleBundle, selectedId, prodProfile ->
        val activeBundle = if (mode == WorkspaceMode.SAMPLE_BUSINESS) {
            sampleBundle
        } else {
            prodBundle.copy(profile = prodProfile)
        }

        val orders = activeBundle.orders
        val assessments = activeBundle.assessments
        val documents = activeBundle.documents
        val payments = activeBundle.payments
        val activities = activeBundle.activities

        val effectiveSelectedId = when {
            selectedId != null && orders.any { it.id == selectedId } -> selectedId
            else -> orders.firstOrNull()?.id
        }

        val assessmentByOrder = assessments.associateBy { it.exportOrderId }
        val docsByOrder = documents.groupBy { it.exportOrderId }
        val paymentsByOrder = payments.groupBy { it.exportOrderId }

        val allTwins = orders.map { order ->
            FinancialCalculationEngine.buildDigitalTwinState(
                order = order,
                financialAssessment = assessmentByOrder[order.id],
                documents = docsByOrder[order.id].orEmpty(),
                payments = paymentsByOrder[order.id].orEmpty()
            )
        }

        val selectedTwin = allTwins.firstOrNull { it.order.id == effectiveSelectedId }
        val metrics = FinancialCalculationEngine.calculatePortfolioMetrics(
            orders = orders,
            assessments = assessments,
            documents = documents,
            payments = payments
        )

        NiryatPortfolioState(
            workspaceMode = mode,
            businessProfile = activeBundle.profile,
            orders = orders,
            assessments = assessments,
            documents = documents,
            payments = payments,
            activities = activities,
            selectedOrderId = effectiveSelectedId,
            selectedDigitalTwin = selectedTwin,
            allDigitalTwins = allTwins,
            performanceMetrics = metrics,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NiryatPortfolioState()
    )

    fun switchWorkspaceMode(targetMode: WorkspaceMode) {
        if (_workspaceMode.value == targetMode) return
        _workspaceMode.value = targetMode
        _selectedOrderId.value = null
        _financialAiExplanation.value = AiTaskState.Idle
        _portfolioAiInsights.value = AiTaskState.Idle
        _nlQueryState.value = AiTaskState.Idle
        _statusBanner.value = if (targetMode == WorkspaceMode.SAMPLE_BUSINESS) {
            "Switched to Sample Business Workspace: Tamil Nadu Global Exports Pvt. Ltd. (Persisted Locally)"
        } else {
            "Switched to Production Export Workspace"
        }
    }

    fun resetSampleWorkspace() {
        val clean = sampleLocalStore?.resetToCleanSeed() ?: SampleBusinessWorkspaceSeeder.createSeedBundle()
        _sampleBundle.value = clean
        _selectedOrderId.value = clean.orders.firstOrNull()?.id
        _financialAiExplanation.value = AiTaskState.Idle
        _portfolioAiInsights.value = AiTaskState.Idle
        _nlQueryState.value = AiTaskState.Idle
        _statusBanner.value = "Sample Business Workspace reset to clean 6-order Tamil Nadu Global Exports Pvt. Ltd. seed state."
    }

    fun updateBusinessProfile(
        businessName: String,
        businessType: String,
        primaryBase: String,
        contactEmail: String,
        productCategories: String,
        reportingCurrency: String
    ) {
        if (_workspaceMode.value == WorkspaceMode.SAMPLE_BUSINESS) {
            val updated = _sampleBundle.value.profile.copy(
                businessName = businessName.trim().ifBlank { _sampleBundle.value.profile.businessName },
                businessType = businessType.trim().ifBlank { _sampleBundle.value.profile.businessType },
                primaryBase = primaryBase.trim().ifBlank { _sampleBundle.value.profile.primaryBase },
                contactEmail = contactEmail.trim().ifBlank { _sampleBundle.value.profile.contactEmail },
                productCategories = productCategories.trim().ifBlank { _sampleBundle.value.profile.productCategories },
                reportingCurrency = reportingCurrency.trim().ifBlank { "INR" }
            )
            mutateSampleBundle { it.copy(profile = updated) }
            _statusBanner.value = "Updated Sample Business Profile (${updated.businessName})."
        } else {
            _productionProfile.value = _productionProfile.value.copy(
                businessName = businessName.trim().ifBlank { "Production Export Workspace" },
                businessType = businessType.trim().ifBlank { "MSME Exporter" },
                primaryBase = primaryBase.trim().ifBlank { "India" },
                contactEmail = contactEmail.trim(),
                productCategories = productCategories.trim(),
                reportingCurrency = reportingCurrency.trim().ifBlank { "INR" }
            )
            _statusBanner.value = "Updated Production Business Workspace Profile."
        }
    }

    fun selectOrder(orderId: String) {
        _selectedOrderId.value = orderId
        _financialAiExplanation.value = AiTaskState.Idle
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun generateOrderNumber(): String {
        val currentOrders = portfolioState.value.orders
        return if (_workspaceMode.value == WorkspaceMode.SAMPLE_BUSINESS) {
            val nextIdx = (currentOrders.size + 1).coerceAtLeast(7)
            String.format(Locale.US, "ORDER-%03d", nextIdx)
        } else {
            val stamp = SimpleDateFormat("yyMMdd-HHmm", Locale.US).format(Date())
            "EXP-$stamp"
        }
    }

    // ------------------------------------------------------------------------
    // MODULE 1: AI-ASSISTED ORDER EXTRACTION & ORDER CREATION / EDITING / STATUS
    // ------------------------------------------------------------------------

    fun runAiOrderExtraction(rawText: String, bitmap: Bitmap?) {
        if (rawText.isBlank() && bitmap == null) {
            _extractionState.value = AiTaskState.Error("Provide trade inquiry text or attach an image to extract order fields.")
            return
        }
        viewModelScope.launch {
            _extractionState.value = AiTaskState.Loading
            val result = geminiService.extractOrderFromTextOrImage(rawText, bitmap)
            result.fold(
                onSuccess = { draft ->
                    _extractionState.value = AiTaskState.Success(draft)
                    _statusBanner.value = "AI Order Extraction completed (${draft.confidenceScore}% confidence). Review and confirm fields before saving."
                },
                onFailure = { err ->
                    _extractionState.value = AiTaskState.Error(
                        "AI Extraction failed: ${err.localizedMessage ?: "Check network or Gemini configuration."}"
                    )
                }
            )
        }
    }

    fun resetExtractionDraft() {
        _extractionState.value = AiTaskState.Idle
    }

    fun createExportOrder(
        orderNumber: String,
        exporterName: String,
        exporterContact: String,
        exporterAddress: String,
        buyerName: String,
        buyerCountry: String,
        buyerContact: String,
        buyerAddress: String,
        productName: String,
        productDescription: String,
        quantity: Double,
        unit: String,
        unitPrice: Double,
        currency: String,
        incoterm: String,
        destinationPort: String,
        orderDate: String,
        expectedShipmentDate: String,
        paymentTerms: String,
        advancePercentage: Double,
        extractionSource: String,
        onCreated: (String) -> Unit = {}
    ) {
        if (_orderSubmitting.value) return
        viewModelScope.launch {
            _orderSubmitting.value = true
            try {
                val safeQty = quantity.coerceAtLeast(0.01)
                val safePrice = unitPrice.coerceAtLeast(0.0)
                val computedOrderValue = FinancialCalculationEngine.calculateGrossOrderValue(safeQty, safePrice)
                val finalOrderNo = orderNumber.ifBlank { generateOrderNumber() }
                val nowTs = Timestamp.now()

                if (_workspaceMode.value == WorkspaceMode.SAMPLE_BUSINESS || repository == null) {
                    val sampleOrderId = "sample_ord_${System.currentTimeMillis()}"
                    val newOrder = ExportOrder(
                        id = sampleOrderId,
                        userId = SampleBusinessWorkspaceSeeder.SAMPLE_USER_ID,
                        orderNumber = finalOrderNo,
                        exporterName = exporterName.trim().ifBlank { _sampleBundle.value.profile.businessName },
                        exporterContact = exporterContact.trim(),
                        exporterAddress = exporterAddress.trim(),
                        buyerName = buyerName.trim(),
                        buyerCountry = buyerCountry.trim(),
                        buyerContact = buyerContact.trim(),
                        buyerAddress = buyerAddress.trim(),
                        productName = productName.trim(),
                        productDescription = productDescription.trim(),
                        quantity = safeQty,
                        unit = unit.trim().ifBlank { "KG" },
                        unitPrice = safePrice,
                        orderValue = computedOrderValue,
                        currency = currency.trim().uppercase().ifBlank { "USD" },
                        incoterm = incoterm.trim().uppercase().ifBlank { "FOB" },
                        destinationPort = destinationPort.trim(),
                        orderDate = orderDate.trim(),
                        expectedShipmentDate = expectedShipmentDate.trim(),
                        paymentTerms = paymentTerms.trim(),
                        advancePercentage = advancePercentage.coerceIn(0.0, 100.0),
                        orderStatus = "ORDER_PROCESSING",
                        extractionSource = extractionSource,
                        createdAt = nowTs,
                        updatedAt = nowTs
                    )

                    val defaultFx = FinancialCalculationEngine.defaultExchangeRateForCurrency(newOrder.currency)
                    val grossInr = computedOrderValue * defaultFx
                    val newAssessment = FinancialCalculationEngine.calculateAssessment(
                        userId = SampleBusinessWorkspaceSeeder.SAMPLE_USER_ID,
                        exportOrderId = sampleOrderId,
                        existingId = "sample_fin_$sampleOrderId",
                        quantity = safeQty,
                        unitPrice = safePrice,
                        currency = newOrder.currency,
                        exchangeRate = defaultFx,
                        advancePercentage = newOrder.advancePercentage,
                        paymentFeePercentage = 3.2,
                        paymentFixedFee = 15.0,
                        procurementCostInr = grossInr * 0.56,
                        logisticsCostInr = grossInr * 0.08,
                        packagingCostInr = grossInr * 0.03,
                        otherCostsInr = grossInr * 0.02,
                        assumptionsNote = "Sample Workspace baseline calculation (persisted locally)."
                    ).copy(createdAt = nowTs, updatedAt = nowTs)

                    val newDocs = listOf(
                        "Commercial Invoice",
                        "Packing List",
                        "Bill of Lading / Airway Bill",
                        "Certificate of Origin",
                        "Shipping Bill"
                    ).mapIndexed { idx, docTitle ->
                        DocumentRecord(
                            id = "sample_doc_${sampleOrderId}_${idx + 1}",
                            userId = SampleBusinessWorkspaceSeeder.SAMPLE_USER_ID,
                            exportOrderId = sampleOrderId,
                            documentType = docTitle,
                            requirementLevel = "REQUIRED",
                            status = "MISSING",
                            reviewerNote = "Initialized for ${newOrder.orderNumber}",
                            versionNumber = 1,
                            createdAt = nowTs,
                            updatedAt = nowTs
                        )
                    }

                    val newActivity = ActivityRecord(
                        id = "sample_act_${System.currentTimeMillis()}",
                        userId = SampleBusinessWorkspaceSeeder.SAMPLE_USER_ID,
                        exportOrderId = sampleOrderId,
                        eventType = "ORDER_CREATED",
                        title = "Sample Order ${newOrder.orderNumber} Created",
                        description = "${newOrder.productName} for ${newOrder.buyerName} (${newOrder.currency} ${newOrder.orderValue})",
                        createdAt = nowTs,
                        updatedAt = nowTs
                    )

                    mutateSampleBundle { cur ->
                        cur.copy(
                            orders = listOf(newOrder) + cur.orders,
                            assessments = listOf(newAssessment) + cur.assessments,
                            documents = newDocs + cur.documents,
                            activities = listOf(newActivity) + cur.activities
                        )
                    }
                    _selectedOrderId.value = sampleOrderId
                    _extractionState.value = AiTaskState.Idle
                    _statusBanner.value = "Sample Order ${newOrder.orderNumber} created and persisted in Sample Business Workspace."
                    onCreated(sampleOrderId)
                    return@launch
                }

                val order = ExportOrder(
                    userId = currentUserId,
                    orderNumber = finalOrderNo,
                    exporterName = exporterName.trim(),
                    exporterContact = exporterContact.trim(),
                    exporterAddress = exporterAddress.trim(),
                    buyerName = buyerName.trim(),
                    buyerCountry = buyerCountry.trim(),
                    buyerContact = buyerContact.trim(),
                    buyerAddress = buyerAddress.trim(),
                    productName = productName.trim(),
                    productDescription = productDescription.trim(),
                    quantity = safeQty,
                    unit = unit.trim().ifBlank { "KG" },
                    unitPrice = safePrice,
                    orderValue = computedOrderValue,
                    currency = currency.trim().uppercase().ifBlank { "USD" },
                    incoterm = incoterm.trim().uppercase().ifBlank { "FOB" },
                    destinationPort = destinationPort.trim(),
                    orderDate = orderDate.trim(),
                    expectedShipmentDate = expectedShipmentDate.trim(),
                    paymentTerms = paymentTerms.trim(),
                    advancePercentage = advancePercentage.coerceIn(0.0, 100.0),
                    orderStatus = "VALIDATED",
                    extractionSource = extractionSource
                )

                val createdId = repository.createOrder(order, initializeDefaultChecklist = true).getOrThrow()
                val defaultFx = FinancialCalculationEngine.defaultExchangeRateForCurrency(order.currency)
                val grossInr = computedOrderValue * defaultFx
                val initialAssessment = FinancialCalculationEngine.calculateAssessment(
                    userId = currentUserId,
                    exportOrderId = createdId,
                    existingId = createdId,
                    quantity = safeQty,
                    unitPrice = safePrice,
                    currency = order.currency,
                    exchangeRate = defaultFx,
                    advancePercentage = order.advancePercentage,
                    paymentFeePercentage = 4.4,
                    paymentFixedFee = 0.30,
                    procurementCostInr = grossInr * 0.58,
                    logisticsCostInr = grossInr * 0.09,
                    packagingCostInr = grossInr * 0.03,
                    otherCostsInr = grossInr * 0.02,
                    assumptionsNote = "Initial baseline calculation. Adjust cost structure in Financial Intelligence."
                )
                repository.saveFinancialAssessment(initialAssessment)

                _selectedOrderId.value = createdId
                _extractionState.value = AiTaskState.Idle
                _statusBanner.value = "Export Order ${order.orderNumber} created and synchronized with Digital Twin."
                onCreated(createdId)
            } catch (e: Exception) {
                _statusBanner.value = "Failed to save Export Order: ${e.localizedMessage}"
            } finally {
                _orderSubmitting.value = false
            }
        }
    }

    /**
     * Updates an existing order's shipment status or core details in either Sample or Production workspace.
     */
    fun updateOrderStatus(order: ExportOrder, newStatus: String) {
        viewModelScope.launch {
            val nowTs = Timestamp.now()
            val updatedOrder = order.copy(orderStatus = newStatus, updatedAt = nowTs)
            if (_workspaceMode.value == WorkspaceMode.SAMPLE_BUSINESS || repository == null) {
                val act = ActivityRecord(
                    id = "sample_act_${System.currentTimeMillis()}",
                    userId = SampleBusinessWorkspaceSeeder.SAMPLE_USER_ID,
                    exportOrderId = order.id,
                    eventType = "STATUS_UPDATED",
                    title = "${order.orderNumber} Status → ${newStatus.replace('_', ' ')}",
                    description = "Updated shipment/order status for ${order.buyerName} (${order.destinationPort}).",
                    createdAt = nowTs,
                    updatedAt = nowTs
                )
                mutateSampleBundle { cur ->
                    cur.copy(
                        orders = cur.orders.map { if (it.id == order.id) updatedOrder else it },
                        activities = listOf(act) + cur.activities
                    )
                }
                _statusBanner.value = "${order.orderNumber} status updated to ${newStatus.replace('_', ' ')} (Saved to Sample Workspace)."
            } else {
                val res = repository.updateOrderStatus(
                    orderId = order.id,
                    newStatus = newStatus,
                    reason = "Updated from Orders Hub"
                )
                res.fold(
                    onSuccess = { _statusBanner.value = "${order.orderNumber} status updated to ${newStatus.replace('_', ' ')}." },
                    onFailure = { e -> _statusBanner.value = "Failed to update order status: ${e.localizedMessage}" }
                )
            }
        }
    }

    /**
     * Deletes an order from the Sample Business Workspace (or marks CANCELLED in Production) and persists the change.
     */
    fun deleteExportOrder(order: ExportOrder) {
        viewModelScope.launch {
            if (_workspaceMode.value == WorkspaceMode.SAMPLE_BUSINESS || repository == null) {
                mutateSampleBundle { cur ->
                    val remainingOrders = cur.orders.filterNot { it.id == order.id }
                    cur.copy(
                        orders = remainingOrders,
                        assessments = cur.assessments.filterNot { it.exportOrderId == order.id },
                        documents = cur.documents.filterNot { it.exportOrderId == order.id },
                        payments = cur.payments.filterNot { it.exportOrderId == order.id }
                    )
                }
                if (_selectedOrderId.value == order.id) {
                    _selectedOrderId.value = _sampleBundle.value.orders.firstOrNull()?.id
                }
                _statusBanner.value = "Deleted ${order.orderNumber} from Sample Business Workspace."
            } else {
                repository.updateOrderStatus(order.id, "CANCELLED", "Cancelled by user")
                _statusBanner.value = "Marked ${order.orderNumber} as CANCELLED."
            }
        }
    }

    // ------------------------------------------------------------------------
    // MODULE 2: EXPORT FINANCIAL INTELLIGENCE ENGINE
    // ------------------------------------------------------------------------

    fun saveFinancialAssessment(
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
    ) {
        viewModelScope.launch {
            try {
                val existing = portfolioState.value.assessments.firstOrNull { it.exportOrderId == order.id }
                val nowTs = Timestamp.now()
                val calculated = FinancialCalculationEngine.calculateAssessment(
                    userId = if (_workspaceMode.value == WorkspaceMode.SAMPLE_BUSINESS) {
                        SampleBusinessWorkspaceSeeder.SAMPLE_USER_ID
                    } else {
                        currentUserId
                    },
                    exportOrderId = order.id,
                    existingId = existing?.id ?: order.id,
                    quantity = order.quantity,
                    unitPrice = order.unitPrice,
                    currency = order.currency,
                    exchangeRate = exchangeRate,
                    advancePercentage = advancePercentage,
                    paymentFeePercentage = paymentFeePercentage,
                    paymentFixedFee = paymentFixedFee,
                    procurementCostInr = procurementCostInr,
                    logisticsCostInr = logisticsCostInr,
                    packagingCostInr = packagingCostInr,
                    otherCostsInr = otherCostsInr,
                    assumptionsNote = assumptionsNote
                ).copy(
                    createdAt = existing?.createdAt ?: nowTs,
                    updatedAt = nowTs
                )

                if (_workspaceMode.value == WorkspaceMode.SAMPLE_BUSINESS || repository == null) {
                    val act = ActivityRecord(
                        id = "sample_act_${System.currentTimeMillis()}",
                        userId = SampleBusinessWorkspaceSeeder.SAMPLE_USER_ID,
                        exportOrderId = order.id,
                        eventType = "FINANCIAL_ASSESSED",
                        title = "${order.orderNumber} Financial Assessment Updated",
                        description = "Recalculated net profit ₹${FinancialCalculationEngine.formatInrShort(calculated.estimatedProfitInr)} (${calculated.profitMarginPercent}% margin @ FX ₹${calculated.exchangeRate}).",
                        createdAt = nowTs,
                        updatedAt = nowTs
                    )
                    mutateSampleBundle { cur ->
                        val updatedList = cur.assessments.filterNot { it.exportOrderId == order.id } + calculated
                        cur.copy(
                            assessments = updatedList,
                            activities = listOf(act) + cur.activities
                        )
                    }
                    _statusBanner.value = "Sample Financial Assessment saved: Est. Profit ₹${FinancialCalculationEngine.formatInrShort(calculated.estimatedProfitInr)} (${calculated.profitMarginPercent}% margin)."
                    return@launch
                }

                repository.saveFinancialAssessment(calculated)
                if (order.orderStatus == "CREATED" || order.orderStatus == "VALIDATED") {
                    repository.updateOrderStatus(order.id, "FINANCIALLY_ASSESSED", "Financial assessment saved")
                }
                _statusBanner.value = "Financial Assessment saved: Est. Profit INR ${FinancialCalculationEngine.formatInrShort(calculated.estimatedProfitInr)} (${calculated.profitMarginPercent}% margin)."
            } catch (e: Exception) {
                _statusBanner.value = "Failed to save Financial Assessment: ${e.localizedMessage}"
            }
        }
    }

    fun requestFinancialExplanation(order: ExportOrder, assessment: FinancialAssessment) {
        viewModelScope.launch {
            _financialAiExplanation.value = AiTaskState.Loading
            val res = geminiService.explainFinancialViability(order, assessment)
            res.fold(
                onSuccess = { text -> _financialAiExplanation.value = AiTaskState.Success(text) },
                onFailure = { err -> _financialAiExplanation.value = AiTaskState.Error("Gemini Advisory Error: ${err.localizedMessage}") }
            )
        }
    }

    // ------------------------------------------------------------------------
    // MODULE 3: EXPORT DOCUMENT MANAGEMENT CENTRE (WITH VERSIONING & ROLE GUARD)
    // ------------------------------------------------------------------------

    fun initializeChecklistIfEmpty(orderId: String) {
        if (_workspaceMode.value == WorkspaceMode.SAMPLE_BUSINESS || repository == null) {
            val cur = _sampleBundle.value
            if (cur.documents.any { it.exportOrderId == orderId }) return
            val nowTs = Timestamp.now()
            val seededDocs = listOf(
                "Commercial Invoice",
                "Packing List",
                "Bill of Lading / Airway Bill",
                "Certificate of Origin",
                "Shipping Bill"
            ).mapIndexed { idx, title ->
                DocumentRecord(
                    id = "sample_doc_${orderId}_${idx + 1}",
                    userId = SampleBusinessWorkspaceSeeder.SAMPLE_USER_ID,
                    exportOrderId = orderId,
                    documentType = title,
                    requirementLevel = "REQUIRED",
                    status = "MISSING",
                    versionNumber = 1,
                    createdAt = nowTs,
                    updatedAt = nowTs
                )
            }
            mutateSampleBundle { it.copy(documents = it.documents + seededDocs) }
            return
        }
        viewModelScope.launch {
            try {
                val existingDocs = portfolioState.value.documents.filter { it.exportOrderId == orderId }
                if (existingDocs.isEmpty()) {
                    repository.initializeDocumentChecklistForOrder(orderId)
                    _statusBanner.value = "Initialized standard export documents."
                }
            } catch (e: Exception) {
                _statusBanner.value = "Failed to initialize document checklist: ${e.localizedMessage}"
            }
        }
    }

    fun updateDocumentItem(
        doc: DocumentRecord,
        newRequirementLevel: String,
        newStatus: String,
        newFileName: String,
        newFileReference: String,
        newReviewerNote: String,
        newFileMimeType: String = doc.fileMimeType,
        newFileSizeBytes: Long = doc.fileSizeBytes,
        newSha256Checksum: String = doc.sha256Checksum
    ) {
        viewModelScope.launch {
            try {
                val nowTs = Timestamp.now()
                val isFileChanged = newFileName.isNotBlank() && newFileName != doc.fileName
                val nextVersion = if (isFileChanged) (doc.versionNumber + 1) else doc.versionNumber

                if (_workspaceMode.value == WorkspaceMode.SAMPLE_BUSINESS || repository == null) {
                    val updated = doc.copy(
                        requirementLevel = newRequirementLevel,
                        status = newStatus,
                        fileName = newFileName.trim(),
                        fileReference = newFileReference.trim(),
                        reviewerNote = newReviewerNote.trim(),
                        fileMimeType = newFileMimeType,
                        fileSizeBytes = newFileSizeBytes,
                        sha256Checksum = newSha256Checksum,
                        versionNumber = nextVersion,
                        reviewerRole = if (FinancialCalculationEngine.isDocumentVerifiedOrApproved(newStatus)) "SAMPLE_COMPLIANCE_REVIEWER" else doc.reviewerRole,
                        updatedAt = nowTs
                    )
                    val act = ActivityRecord(
                        id = "sample_act_${System.currentTimeMillis()}",
                        userId = SampleBusinessWorkspaceSeeder.SAMPLE_USER_ID,
                        exportOrderId = doc.exportOrderId,
                        eventType = "DOC_UPDATED",
                        title = "${doc.documentType} (v$nextVersion) → ${newStatus.replace('_', ' ')}",
                        description = newReviewerNote.ifBlank { "Updated compliance document status in Sample Workspace." },
                        createdAt = nowTs,
                        updatedAt = nowTs
                    )
                    mutateSampleBundle { cur ->
                        val newDocs = cur.documents.map { if (it.id == doc.id) updated else it }
                        cur.copy(
                            documents = newDocs,
                            activities = listOf(act) + cur.activities
                        )
                    }
                    _statusBanner.value = "Sample Document '${doc.documentType}' (v$nextVersion) updated to ${newStatus.replace('_', ' ')}."
                    return@launch
                }

                // Production Workspace Security Guard:
                // Ordinary exporter clients cannot self-certify documents as VERIFIED/APPROVED/OFFICIALLY_CERTIFIED.
                val requestedReviewerOnlyState = newStatus in listOf("VERIFIED", "APPROVED", "OFFICIALLY_CERTIFIED")
                val safeProductionStatus = if (requestedReviewerOnlyState) "UNDER_REVIEW" else newStatus
                val safeNote = if (requestedReviewerOnlyState) {
                    "Submitted for Compliance Reviewer approval (Production self-verification blocked by security policy). $newReviewerNote".trim()
                } else {
                    newReviewerNote.trim()
                }

                repository.updateDocumentRecord(
                    docId = doc.id,
                    exportOrderId = doc.exportOrderId,
                    documentType = doc.documentType,
                    requirementLevel = newRequirementLevel,
                    status = safeProductionStatus,
                    fileName = newFileName.trim(),
                    fileReference = newFileReference.trim(),
                    reviewerNote = safeNote
                )
                _statusBanner.value = if (requestedReviewerOnlyState) {
                    "'${doc.documentType}' submitted as UNDER REVIEW (Compliance Reviewer role required for VERIFIED state in Production)."
                } else {
                    "Updated '${doc.documentType}' (v$nextVersion) to ${safeProductionStatus.replace('_', ' ')}."
                }
            } catch (e: Exception) {
                _statusBanner.value = "Failed to update document: ${e.localizedMessage}"
            }
        }
    }

    fun addCustomDocument(orderId: String, customDocType: String, requirementLevel: String) {
        if (customDocType.isBlank()) return
        val nowTs = Timestamp.now()
        if (_workspaceMode.value == WorkspaceMode.SAMPLE_BUSINESS || repository == null) {
            val newDoc = DocumentRecord(
                id = "sample_doc_custom_${System.currentTimeMillis()}",
                userId = SampleBusinessWorkspaceSeeder.SAMPLE_USER_ID,
                exportOrderId = orderId,
                documentType = customDocType.trim(),
                requirementLevel = requirementLevel,
                status = "MISSING",
                versionNumber = 1,
                createdAt = nowTs,
                updatedAt = nowTs
            )
            mutateSampleBundle { cur ->
                cur.copy(documents = cur.documents + newDoc)
            }
            _statusBanner.value = "Added custom document '${customDocType.trim()}' to Sample Order."
            return
        }
        viewModelScope.launch {
            try {
                repository.addCustomDocumentCategory(
                    exportOrderId = orderId,
                    documentType = customDocType.trim(),
                    requirementLevel = requirementLevel
                )
                _statusBanner.value = "Added custom document '${customDocType.trim()}'."
            } catch (e: Exception) {
                _statusBanner.value = "Failed to add custom document: ${e.localizedMessage}"
            }
        }
    }

    // ------------------------------------------------------------------------
    // MODULE 4: PAYPAL SANDBOX INTERNATIONAL PAYMENT INTEGRATION (BACKEND-VERIFIED)
    // ------------------------------------------------------------------------

    fun verifyPayPalSandboxConnection() {
        viewModelScope.launch {
            if (_workspaceMode.value == WorkspaceMode.SAMPLE_BUSINESS) {
                val msg = "DEMO MODE ACTIVE: Using DemoPaymentService (${DemoPaymentService.SIMULATED_PAYMENT_BANNER}). No PAYPAL_BACKEND_BASE_URL or Blaze plan required."
                _paypalAuthCheckState.value = "DEMO MODE: $msg"
                _statusBanner.value = msg
                return@launch
            }
            _paypalAuthCheckState.value = "Verifying authenticated PayPal Sandbox Backend Cloud Function..."
            val result = payPalService.verifyOAuthConnection()
            result.fold(
                onSuccess = { msg ->
                    _paypalAuthCheckState.value = "PASS: $msg"
                    _statusBanner.value = msg
                },
                onFailure = { err ->
                    _paypalAuthCheckState.value = "FAIL: ${err.localizedMessage}"
                    _statusBanner.value = "PayPal Backend Verification: ${err.localizedMessage}"
                }
            )
        }
    }

    fun createPayPalSandboxOrder(
        order: ExportOrder,
        amount: Double,
        paymentType: String
    ) {
        if (_paymentBusy.value) return
        viewModelScope.launch {
            _paymentBusy.value = true
            try {
                if (_workspaceMode.value == WorkspaceMode.SAMPLE_BUSINESS) {
                    val outcome = demoPaymentService.createSimulatedPayment(
                        userId = SampleBusinessWorkspaceSeeder.SAMPLE_USER_ID,
                        order = order,
                        amount = amount,
                        paymentType = paymentType,
                        existingPayments = _sampleBundle.value.payments
                    )
                    if (!outcome.wasIdempotentReplay) {
                        mutateSampleBundle { cur ->
                            cur.copy(
                                payments = listOf(outcome.record) + cur.payments,
                                activities = listOf(outcome.activity) + cur.activities
                            )
                        }
                    }
                    _statusBanner.value = outcome.message
                    return@launch
                }

                // Production Workspace: Never silently switch Production users into simulated payment mode
                if (repository == null) {
                    _statusBanner.value = "Production payments require an authenticated Firebase session. Sign in or switch to Sample Business Workspace for Demo Mode."
                    return@launch
                }

                val apiResult = payPalService.createSandboxOrder(
                    orderNumber = order.orderNumber,
                    currency = order.currency,
                    amount = amount,
                    paymentType = paymentType,
                    exportOrderId = order.id
                )

                apiResult.fold(
                    onSuccess = { created ->
                        val paymentRecord = PaymentRecord(
                            id = "pay_${created.paypalOrderId}",
                            userId = currentUserId,
                            exportOrderId = order.id,
                            paypalOrderId = created.paypalOrderId,
                            paypalCaptureId = "",
                            approvalUrl = created.approvalUrl,
                            amount = amount,
                            currency = order.currency,
                            paymentType = paymentType,
                            paymentStatus = "PENDING_APPROVAL",
                            verificationDetails = created.verificationSummary
                        )
                        repository.createPaymentRecord(paymentRecord)
                        _statusBanner.value = "PayPal Sandbox Order (${created.paypalOrderId}) created via Backend. Approve in browser, then tap Capture."
                    },
                    onFailure = { err ->
                        _statusBanner.value = "PayPal Backend Order Creation Failed: ${err.localizedMessage}"
                    }
                )
            } finally {
                _paymentBusy.value = false
            }
        }
    }

    fun capturePayPalSandboxOrder(payment: PaymentRecord) {
        if (_paymentBusy.value || payment.paypalOrderId.isBlank()) return
        viewModelScope.launch {
            _paymentBusy.value = true
            try {
                val nowTs = Timestamp.now()
                if (_workspaceMode.value == WorkspaceMode.SAMPLE_BUSINESS) {
                    val outcome = demoPaymentService.captureSimulatedPayment(payment, nowTs)
                    mutateSampleBundle { cur ->
                        val updatedOrders = cur.orders.map { ord ->
                            if (ord.id == payment.exportOrderId && ord.orderStatus == "AWAITING_PAYMENT") {
                                ord.copy(orderStatus = "SHIPMENT_IN_TRANSIT", updatedAt = nowTs)
                            } else ord
                        }
                        cur.copy(
                            orders = updatedOrders,
                            payments = cur.payments.map { if (it.id == payment.id) outcome.record else it },
                            activities = listOf(outcome.activity) + cur.activities
                        )
                    }
                    _statusBanner.value = outcome.message
                    return@launch
                }

                if (repository == null) {
                    _statusBanner.value = "Production payment capture requires an authenticated Firebase session."
                    return@launch
                }

                // Production Workspace: Backend Cloud Function executes PayPal capture and writes CAPTURED status via Admin SDK
                val capResult = payPalService.captureSandboxOrder(
                    paypalOrderId = payment.paypalOrderId,
                    paymentRecordId = payment.id
                )
                capResult.fold(
                    onSuccess = { captured ->
                        _statusBanner.value = if (captured.status == "CAPTURED") {
                            "Backend Verified & Captured Payment! Capture ID: ${captured.captureId}"
                        } else {
                            "PayPal Order Status: ${captured.status}. ${captured.verificationSummary}"
                        }
                    },
                    onFailure = { err ->
                        _statusBanner.value = "Backend Capture / Verification notice: ${err.localizedMessage}"
                    }
                )
            } finally {
                _paymentBusy.value = false
            }
        }
    }

    fun failDemoPayment(payment: PaymentRecord, reason: String = "Simulated bank decline in Demo Mode") {
        if (_workspaceMode.value != WorkspaceMode.SAMPLE_BUSINESS) {
            _statusBanner.value = "Simulated payment failure is only available in Demo Mode (Sample Business Workspace)."
            return
        }
        val outcome = demoPaymentService.failSimulatedPayment(payment, reason)
        mutateSampleBundle { cur ->
            cur.copy(
                payments = cur.payments.map { if (it.id == payment.id) outcome.record else it },
                activities = listOf(outcome.activity) + cur.activities
            )
        }
        _statusBanner.value = outcome.message
    }

    fun cancelDemoPayment(payment: PaymentRecord) {
        if (_workspaceMode.value != WorkspaceMode.SAMPLE_BUSINESS) {
            _statusBanner.value = "Simulated payment cancellation is only available in Demo Mode (Sample Business Workspace)."
            return
        }
        val outcome = demoPaymentService.cancelSimulatedPayment(payment)
        mutateSampleBundle { cur ->
            cur.copy(
                payments = cur.payments.map { if (it.id == payment.id) outcome.record else it },
                activities = listOf(outcome.activity) + cur.activities
            )
        }
        _statusBanner.value = outcome.message
    }

    fun reconcilePayPalOrderStatus(payment: PaymentRecord) {
        if (_paymentBusy.value || payment.paypalOrderId.isBlank()) return
        viewModelScope.launch {
            _paymentBusy.value = true
            try {
                val nowTs = Timestamp.now()
                if (_workspaceMode.value == WorkspaceMode.SAMPLE_BUSINESS) {
                    val updatedPay = payment.copy(
                        verificationDetails = "${DemoPaymentService.SIMULATED_PAYMENT_BANNER} • Reconciled against local DemoPaymentService ledger (Status: ${payment.paymentStatus}).",
                        updatedAt = nowTs
                    )
                    mutateSampleBundle { cur ->
                        cur.copy(payments = cur.payments.map { if (it.id == payment.id) updatedPay else it })
                    }
                    _statusBanner.value = "Demo Ledger Reconciled (${payment.paypalOrderId}): ${payment.paymentStatus}"
                    return@launch
                }

                if (repository == null) {
                    _statusBanner.value = "Production reconciliation requires an authenticated session."
                    return@launch
                }

                val statusRes = payPalService.getOrderDetailsStatus(payment.paypalOrderId)
                statusRes.fold(
                    onSuccess = { info ->
                        _statusBanner.value = "Backend Reconciled PayPal Order ${payment.paypalOrderId}: ${info.status}"
                    },
                    onFailure = { err ->
                        _statusBanner.value = "PayPal status check failed: ${err.localizedMessage}"
                    }
                )
            } finally {
                _paymentBusy.value = false
            }
        }
    }

    // ------------------------------------------------------------------------
    // MODULE 5 & SECTION 9: AI INSIGHTS, NATURAL LANGUAGE QUERY & CSV REPORTS
    // ------------------------------------------------------------------------

    fun requestPortfolioInsights() {
        val current = portfolioState.value
        viewModelScope.launch {
            _portfolioAiInsights.value = AiTaskState.Loading
            val res = geminiService.generatePortfolioInsights(
                metrics = current.performanceMetrics,
                orders = current.orders
            )
            res.fold(
                onSuccess = { text -> _portfolioAiInsights.value = AiTaskState.Success(text) },
                onFailure = { err -> _portfolioAiInsights.value = AiTaskState.Error("Portfolio AI Error: ${err.localizedMessage}") }
            )
        }
    }

    fun askNaturalLanguageBusinessQuery(query: String) {
        if (query.isBlank()) return
        val current = portfolioState.value
        viewModelScope.launch {
            _nlQueryState.value = AiTaskState.Loading
            val res = geminiService.answerNaturalLanguageBusinessQuery(
                query = query,
                businessName = current.businessProfile.businessName,
                isSampleWorkspace = current.isSampleWorkspace,
                metrics = current.performanceMetrics,
                orders = current.orders,
                assessments = current.assessments,
                documents = current.documents,
                payments = current.payments
            )
            res.fold(
                onSuccess = { answer -> _nlQueryState.value = AiTaskState.Success(answer) },
                onFailure = { err -> _nlQueryState.value = AiTaskState.Error("Query Error: ${err.localizedMessage}") }
            )
        }
    }

    fun generateBusinessReportCsv(reportType: String): String {
        val s = portfolioState.value
        val sb = StringBuilder()
        sb.appendLine("# NIRYATFLOW AI - EXPORT INTELLIGENCE REPORT")
        sb.appendLine("# Workspace: ${s.businessProfile.businessName} (${if (s.isSampleWorkspace) "SAMPLE BUSINESS WORKSPACE" else "PRODUCTION WORKSPACE"})")
        sb.appendLine("# Report Type: $reportType")
        sb.appendLine("# Generated At: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        sb.appendLine()

        when (reportType) {
            "EXPORT_ORDERS_SUMMARY", "Export Order Report", "Order Completion Report" -> {
                sb.appendLine("OrderNumber,BuyerName,Country,Product,Quantity,Unit,Currency,OrderValue,Incoterm,DestinationPort,Status")
                s.orders.forEach { o ->
                    sb.appendLine("${o.orderNumber},\"${o.buyerName}\",${o.buyerCountry},\"${o.productName}\",${o.quantity},${o.unit},${o.currency},${o.orderValue},${o.incoterm},\"${o.destinationPort}\",${o.orderStatus}")
                }
            }
            "FINANCIAL_ASSESSMENT_REPORT", "Financial Performance Report" -> {
                sb.appendLine("OrderID,Currency,GrossValue,ExchangeRate,ConvertedINR,ProcurementINR,LogisticsINR,PaymentFeeINR,EstimatedNetProfitINR,ProfitMarginPercent")
                s.assessments.forEach { a ->
                    sb.appendLine("${a.exportOrderId},${a.currency},${a.grossOrderValue},${a.exchangeRate},${a.convertedValueInr},${a.procurementCostInr},${a.logisticsCostInr},${a.paymentFeeInr},${a.estimatedProfitInr},${a.profitMarginPercent}%")
                }
            }
            "DOCUMENT_READINESS_REPORT", "Document Readiness Report" -> {
                sb.appendLine("OrderID,DocumentType,RequirementLevel,Status,Version,SHA256,FileName,ReviewerNote")
                s.documents.forEach { d ->
                    sb.appendLine("${d.exportOrderId},\"${d.documentType}\",${d.requirementLevel},${d.status},v${d.versionNumber},${d.sha256Checksum},\"${d.fileName}\",\"${d.reviewerNote}\"")
                }
            }
            "PAYMENT_STATUS_REPORT", "Outstanding Payment Report" -> {
                sb.appendLine("OrderID,TransactionRef,SettlementRef,Currency,Amount,PaymentType,Status,VerificationDetails")
                s.payments.forEach { p ->
                    sb.appendLine("${p.exportOrderId},${p.paypalOrderId},${p.paypalCaptureId},${p.currency},${p.amount},${p.paymentType},${p.paymentStatus},\"${p.verificationDetails}\"")
                }
            }
            "Export Destination Report" -> {
                sb.appendLine("Country,TotalRevenueINR")
                s.performanceMetrics.countryDistribution.forEach { (country, inr) ->
                    sb.appendLine("\"$country\",$inr")
                }
            }
            else -> {
                sb.appendLine("Metric,Value")
                sb.appendLine("Total Orders,${s.performanceMetrics.totalOrders}")
                sb.appendLine("Active Orders,${s.performanceMetrics.activeOrders}")
                sb.appendLine("Completed Orders,${s.performanceMetrics.completedOrders}")
                sb.appendLine("Total Order Value (INR),${s.performanceMetrics.totalOrderValueInr}")
                sb.appendLine("Captured Payments (INR),${s.performanceMetrics.capturedPaymentValueInr}")
                sb.appendLine("Outstanding Receivables (INR),${s.performanceMetrics.outstandingPaymentValueInr}")
                sb.appendLine("Total Estimated Profit (INR),${s.performanceMetrics.totalEstimatedProfitInr}")
                sb.appendLine("Average Profit Margin (%),${s.performanceMetrics.averageProfitMarginPercent}%")
                sb.appendLine("Document Readiness Rate (%),${s.performanceMetrics.documentReadinessRatePercent}%")
                sb.appendLine("Payment Completion Rate (%),${s.performanceMetrics.paymentCompletionRatePercent}%")
            }
        }
        return sb.toString()
    }
}
