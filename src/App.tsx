import React, { useState, useEffect, useMemo } from 'react';
import './styles/fintech.css';
import {
  ExportOrder,
  FinancialAssessment,
  DocumentRecord,
  PaymentRecord,
  ActivityRecord,
  WorkspaceMode,
  WebNavTab,
  TimePeriodCode,
  DocumentStatus,
  AIExtractedOrderDraft,
  StructuredAIRecommendation,
  PayPalSandboxConfigStatus,
  PayPalSandboxTransaction
} from './types/models';
import {
  SAMPLE_WORKSPACE_PROFILE,
  SAMPLE_USER_ID,
  WorkspaceDataset,
  loadSampleWorkspace,
  saveSampleWorkspace,
  resetSampleWorkspace,
  loadProductionWorkspace,
  saveProductionWorkspace
} from './data/sampleWorkspaceSeeder';
import {
  STANDARD_DOCUMENT_TYPES,
  defaultExchangeRateForCurrency,
  calculateGrossOrderValue,
  calculateAssessment,
  isDocumentVerifiedOrApproved,
  isPaymentSettled,
  isPaymentPending,
  formatInrShort,
  buildDigitalTwinState,
  calculatePeriodAnalytics,
  evaluatePortfolioRisks,
  buildShipmentIntelligencePortfolio,
  calculateReceivablesAndCurrencyAnalytics,
  calculateBuyerIntelligence,
  round2
} from './domain/financialEngine';
import {
  SAMPLE_EXTRACTION_TEMPLATES,
  extractPurchaseOrderFromText,
  generateFinancialViabilityAdvisory,
  runLiveOrFallbackNlQuery,
  buildDeterministicAdvisorRecommendations,
  requestLiveOrFallbackPortfolioAdvisory
} from './services/aiIntelligenceService';
import {
  createDemoPayment,
  transitionDemoPayment
} from './services/demoPaymentService';
import {
  syncAuthoritativeOrdersToServer,
  fetchPayPalSandboxConfigStatus,
  createPayPalSandboxOrder,
  fetchPayPalSandboxOrderStatus,
  capturePayPalSandboxOrder,
  cancelPayPalSandboxOrder,
  inspectPayPalRedirectParams,
  clearPayPalRedirectParamsFromUrl
} from './services/paypalSandboxService';
import { BrandLogo, BRAND_TAGLINE } from './components/BrandLogo';
import { NiryatIcon, NiryatIconName } from './components/NiryatIcon';
import { PaymentHubDualModeView } from './components/PaymentHubDualModeView';
import {
  CommandCentreView,
  ShipmentIntelligenceView,
  ReceivablesIntelligenceView,
  BuyerIntelligenceView,
  AiRiskAndAdvisorView,
  ReportingCenterView
} from './components/IntelligencePanels';

const ORDER_STATUSES: ExportOrder['orderStatus'][] = [
  'CREATED',
  'VALIDATED',
  'FINANCIALLY_ASSESSED',
  'DOCS_READY',
  'PAYMENT_INITIATED',
  'COMPLETED',
  'CANCELLED'
];

const DOC_STATUSES_DEMO: DocumentStatus[] = [
  'MISSING',
  'UPLOADED',
  'PENDING_REVIEW',
  'UNDER_REVIEW',
  'APPROVED',
  'VERIFIED',
  'REJECTED',
  'REUPLOAD_REQUIRED',
  'OFFICIALLY_CERTIFIED',
  'NOT_APPLICABLE'
];

const DOC_STATUSES_PRODUCTION: DocumentStatus[] = [
  'MISSING',
  'UPLOADED',
  'PENDING_REVIEW',
  'UNDER_REVIEW',
  'REJECTED',
  'REUPLOAD_REQUIRED',
  'NOT_APPLICABLE'
];

interface ConfirmModalState {
  title: string;
  message: string;
  confirmLabel: string;
  danger?: boolean;
  onConfirm: () => void;
}

export function App() {
  const [workspaceMode, setWorkspaceMode] = useState<WorkspaceMode>('SAMPLE_BUSINESS');
  const [prodUserEmail] = useState<string>('exporter@production-trade.in');
  const [dataset, setDataset] = useState<WorkspaceDataset>(() => loadSampleWorkspace());
  const [activeTab, setActiveTab] = useState<WebNavTab>('dashboard');
  const [timePeriod, setTimePeriod] = useState<TimePeriodCode>('ALL');

  const [selectedOrderId, setSelectedOrderId] = useState<string>('ORDER-001');
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [confirmModal, setConfirmModal] = useState<ConfirmModalState | null>(null);
  const [serverGeminiConfigured, setServerGeminiConfigured] = useState<boolean>(false);

  // Genuine PayPal Sandbox state (independent from DemoPaymentService)
  const [paypalConfig, setPaypalConfig] = useState<PayPalSandboxConfigStatus | null>(null);
  const [paypalTransactions, setPaypalTransactions] = useState<PayPalSandboxTransaction[]>([]);
  const [paypalBusyOrderId, setPaypalBusyOrderId] = useState<string | null>(null);
  const [paypalErrorBanner, setPaypalErrorBanner] = useState<string | null>(null);

  // Order Registry & AI PO Extraction state
  const [orderSearch, setOrderSearch] = useState<string>('');
  const [orderStatusFilter, setOrderStatusFilter] = useState<string>('ALL');
  const [poInputText, setPoInputText] = useState<string>(
    SAMPLE_EXTRACTION_TEMPLATES[0]?.rawText || ''
  );
  const [poExtracting, setPoExtracting] = useState<boolean>(false);
  const [poDraft, setPoDraft] = useState<AIExtractedOrderDraft | null>(null);

  const [orderForm, setOrderForm] = useState({
    orderNumber: '',
    buyerName: '',
    buyerCountry: '',
    buyerContact: '',
    buyerAddress: '',
    productName: '',
    productDescription: '',
    quantity: 1000,
    unit: 'KG',
    unitPrice: 10,
    currency: 'USD',
    incoterm: 'CIF',
    destinationPort: 'Port of Hamburg, Germany',
    orderDate: '2026-04-12',
    expectedShipmentDate: '2026-05-15',
    paymentTerms: '30% Advance, 70% CAD',
    advancePercentage: 30
  });

  // What-If Simulator State (isolated from saved records)
  const [whatIfFxDeltaPct, setWhatIfFxDeltaPct] = useState<number>(0);
  const [whatIfFreightDeltaPct, setWhatIfFreightDeltaPct] = useState<number>(0);
  const [whatIfProcurementDeltaPct, setWhatIfProcurementDeltaPct] = useState<number>(0);
  const [showFormulaAudit, setShowFormulaAudit] = useState<boolean>(false);

  // Document filter & upload state
  const [docStatusFilter, setDocStatusFilter] = useState<string>('ALL');
  const [uploadingDocId, setUploadingDocId] = useState<string | null>(null);

  // Payment creation state
  const [newPaymentAmountForeign, setNewPaymentAmountForeign] = useState<number>(15000);
  const [newPaymentType, setNewPaymentType] = useState<'ADVANCE' | 'BALANCE' | 'FULL'>('ADVANCE');

  // NL Query & AI Advisor state
  const [nlQueryInput, setNlQueryInput] = useState<string>(
    'Which orders have missing or rejected documents?'
  );
  const [nlQueryLoading, setNlQueryLoading] = useState<boolean>(false);
  const [nlQueryResult, setNlQueryResult] = useState<{
    answerTitle: string;
    answerBullets: string[];
    citedOrders: string[];
    engineLabel: string;
  } | null>(null);
  const [advisorLoading, setAdvisorLoading] = useState<boolean>(false);
  const [liveAdvisoryOverride, setLiveAdvisoryOverride] = useState<{
    executiveSummary: string;
    recommendations: StructuredAIRecommendation[];
    engineLabel: string;
  } | null>(null);

  const refreshPayPalSandboxState = async (mode: WorkspaceMode) => {
    try {
      const cfg = await fetchPayPalSandboxConfigStatus(mode);
      setPaypalConfig(cfg);
      setPaypalTransactions(cfg.transactions || []);
    } catch {
      // keep existing state if offline
    }
  };

  useEffect(() => {
    fetch('/api/health')
      .then((r) => (r.ok ? r.json() : null))
      .then((d) => {
        if (d && typeof d.aiConfigured === 'boolean') {
          setServerGeminiConfigured(d.aiConfigured);
        }
      })
      .catch(() => {
        setServerGeminiConfigured(false);
      });
  }, []);

  useEffect(() => {
    const loaded =
      workspaceMode === 'SAMPLE_BUSINESS' ? loadSampleWorkspace() : loadProductionWorkspace();
    setDataset(loaded);
    if (loaded.orders.length > 0) {
      setSelectedOrderId(loaded.orders[0].id);
    } else {
      setSelectedOrderId('');
    }
    syncAuthoritativeOrdersToServer(loaded.orders);
    refreshPayPalSandboxState(workspaceMode);
  }, [workspaceMode]);

  // Handle buyer return or cancellation from PayPal Sandbox approval redirect
  useEffect(() => {
    const redirectInfo = inspectPayPalRedirectParams();
    if (!redirectInfo.type || !redirectInfo.paypalOrderId) return;

    setActiveTab('payments');
    if (redirectInfo.niryatOrderId) {
      setSelectedOrderId(redirectInfo.niryatOrderId);
    }
    clearPayPalRedirectParamsFromUrl();

    if (redirectInfo.type === 'RETURN_APPROVED') {
      // CRITICAL SECURITY RULE: Returning from redirect never marks payment completed; it verifies order-status
      fetchPayPalSandboxOrderStatus({
        paypalOrderId: redirectInfo.paypalOrderId,
        orderId: redirectInfo.niryatOrderId || undefined
      })
        .then((res) => {
          setPaypalErrorBanner(null);
          refreshPayPalSandboxState(workspaceMode);
          setToastMessage(
            `PayPal Sandbox Order ${res.paypalOrderId} status verified as ${res.status}. Click "3. Capture Order" to execute authorized settlement.`
          );
        })
        .catch((err: any) => {
          setPaypalErrorBanner(err?.message || 'Unable to verify PayPal Sandbox return status.');
        });
    } else if (redirectInfo.type === 'CANCELLED') {
      cancelPayPalSandboxOrder({
        paypalOrderId: redirectInfo.paypalOrderId,
        orderId: redirectInfo.niryatOrderId || undefined
      })
        .then(() => {
          refreshPayPalSandboxState(workspaceMode);
          setToastMessage(
            `Buyer cancelled PayPal Sandbox approval for Order ${redirectInfo.paypalOrderId}.`
          );
        })
        .catch((err: any) => {
          setPaypalErrorBanner(err?.message || 'Unable to record PayPal Sandbox cancellation.');
        });
    }
  }, [workspaceMode]);

  const persistDataset = (next: WorkspaceDataset) => {
    setDataset(next);
    if (workspaceMode === 'SAMPLE_BUSINESS') {
      saveSampleWorkspace(next);
    } else {
      saveProductionWorkspace(next);
    }
  };

  const triggerToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 4200);
  };

  const isDemoMode = workspaceMode === 'SAMPLE_BUSINESS';

  const selectedOrder: ExportOrder | undefined = useMemo(
    () => dataset.orders.find((o) => o.id === selectedOrderId) || dataset.orders[0],
    [dataset.orders, selectedOrderId]
  );

  const selectedAssessment: FinancialAssessment | undefined = useMemo(() => {
    if (!selectedOrder) return undefined;
    const existing = dataset.assessments.find((a) => a.exportOrderId === selectedOrder.id);
    if (existing) return existing;
    return calculateAssessment({
      userId: selectedOrder.userId,
      exportOrderId: selectedOrder.id,
      quantity: selectedOrder.quantity,
      unitPrice: selectedOrder.unitPrice,
      currency: selectedOrder.currency,
      exchangeRate: defaultExchangeRateForCurrency(selectedOrder.currency),
      advancePercentage: selectedOrder.advancePercentage,
      paymentFeePercentage: 2.5,
      paymentFixedFee: 15,
      procurementCostInr: round2(
        selectedOrder.orderValue * defaultExchangeRateForCurrency(selectedOrder.currency) * 0.58
      ),
      logisticsCostInr: round2(
        selectedOrder.orderValue * defaultExchangeRateForCurrency(selectedOrder.currency) * 0.08
      ),
      packagingCostInr: round2(
        selectedOrder.orderValue * defaultExchangeRateForCurrency(selectedOrder.currency) * 0.03
      ),
      otherCostsInr: round2(
        selectedOrder.orderValue * defaultExchangeRateForCurrency(selectedOrder.currency) * 0.02
      )
    });
  }, [selectedOrder, dataset.assessments]);

  const selectedOrderDocs: DocumentRecord[] = useMemo(() => {
    if (!selectedOrder) return [];
    return dataset.documents.filter((d) => d.exportOrderId === selectedOrder.id);
  }, [selectedOrder, dataset.documents]);

  const selectedOrderPayments: PaymentRecord[] = useMemo(() => {
    if (!selectedOrder) return [];
    return dataset.payments.filter((p) => p.exportOrderId === selectedOrder.id);
  }, [selectedOrder, dataset.payments]);

  // Period Analytics for Executive Dashboard (1D, 1W, 1M, 3M, 1Y, ALL)
  const periodAnalytics = useMemo(
    () =>
      calculatePeriodAnalytics(
        dataset.orders,
        dataset.assessments,
        dataset.documents,
        dataset.payments,
        timePeriod
      ),
    [dataset.orders, dataset.assessments, dataset.documents, dataset.payments, timePeriod]
  );

  // Portfolio-wide risk alerts
  const allRisks = useMemo(
    () =>
      evaluatePortfolioRisks(
        dataset.orders,
        dataset.assessments,
        dataset.documents,
        dataset.payments
      ),
    [dataset.orders, dataset.assessments, dataset.documents, dataset.payments]
  );

  // Shipment Intelligence Portfolio
  const shipmentIntelItems = useMemo(
    () =>
      buildShipmentIntelligencePortfolio(
        dataset.orders,
        dataset.assessments,
        dataset.documents,
        dataset.payments
      ),
    [dataset.orders, dataset.assessments, dataset.documents, dataset.payments]
  );

  // Receivables & Currency Analytics
  const receivablesAnalytics = useMemo(
    () =>
      calculateReceivablesAndCurrencyAnalytics(
        dataset.orders,
        dataset.assessments,
        dataset.documents,
        dataset.payments
      ),
    [dataset.orders, dataset.assessments, dataset.documents, dataset.payments]
  );

  // Buyer Intelligence
  const buyerAnalytics = useMemo(
    () =>
      calculateBuyerIntelligence(
        dataset.orders,
        dataset.assessments,
        dataset.documents,
        dataset.payments,
        dataset.activities
      ),
    [dataset.orders, dataset.assessments, dataset.documents, dataset.payments, dataset.activities]
  );

  // Deterministic AI Advisor Recommendations
  const deterministicAdvisor = useMemo(
    () =>
      buildDeterministicAdvisorRecommendations({
        orders: dataset.orders,
        assessments: dataset.assessments,
        documents: dataset.documents,
        payments: dataset.payments
      }),
    [dataset.orders, dataset.assessments, dataset.documents, dataset.payments]
  );

  const activeAdvisor = liveAdvisoryOverride || deterministicAdvisor;

  // Selected Order Digital Twin
  const selectedTwin = useMemo(() => {
    if (!selectedOrder) return null;
    return buildDigitalTwinState(
      selectedOrder,
      selectedAssessment || null,
      selectedOrderDocs,
      selectedOrderPayments
    );
  }, [selectedOrder, selectedAssessment, selectedOrderDocs, selectedOrderPayments]);

  // Isolated What-If Simulation for selected order
  const whatIfAssessment = useMemo(() => {
    if (!selectedOrder || !selectedAssessment) return null;
    return calculateAssessment({
      userId: selectedOrder.userId,
      exportOrderId: selectedOrder.id,
      existingId: selectedAssessment.id,
      quantity: selectedOrder.quantity,
      unitPrice: selectedOrder.unitPrice,
      currency: selectedOrder.currency,
      exchangeRate: round2(selectedAssessment.exchangeRate * (1 + whatIfFxDeltaPct / 100)),
      advancePercentage: selectedAssessment.advancePercentage,
      paymentFeePercentage: selectedAssessment.paymentFeePercentage,
      paymentFixedFee: selectedAssessment.paymentFixedFee,
      procurementCostInr: round2(
        selectedAssessment.procurementCostInr * (1 + whatIfProcurementDeltaPct / 100)
      ),
      logisticsCostInr: round2(
        selectedAssessment.logisticsCostInr * (1 + whatIfFreightDeltaPct / 100)
      ),
      packagingCostInr: selectedAssessment.packagingCostInr,
      otherCostsInr: selectedAssessment.otherCostsInr,
      assumptionsNote: 'Isolated What-If Scenario (Does not mutate saved financial record)'
    });
  }, [
    selectedOrder,
    selectedAssessment,
    whatIfFxDeltaPct,
    whatIfFreightDeltaPct,
    whatIfProcurementDeltaPct
  ]);

  const selectedViabilityAdvisory = useMemo(() => {
    if (!selectedOrder || !selectedAssessment) return null;
    return generateFinancialViabilityAdvisory(selectedOrder, selectedAssessment);
  }, [selectedOrder, selectedAssessment]);

  const navigateToOrderModule = (tab: WebNavTab, orderId?: string) => {
    if (orderId) {
      setSelectedOrderId(orderId);
    }
    setActiveTab(tab);
  };

  // --- Handlers ---
  const handleRunPoExtraction = async () => {
    if (!poInputText.trim()) return;
    setPoExtracting(true);
    try {
      const draft = await extractPurchaseOrderFromText(poInputText);
      setPoDraft(draft);
      const nextNum = `ORDER-00${dataset.orders.length + 1}`;
      setOrderForm({
        orderNumber: nextNum,
        buyerName: draft.buyerName,
        buyerCountry: draft.buyerCountry,
        buyerContact: draft.buyerContact,
        buyerAddress: draft.buyerAddress,
        productName: draft.productName,
        productDescription: draft.productDescription,
        quantity: draft.quantity,
        unit: draft.unit,
        unitPrice: draft.unitPrice,
        currency: draft.currency,
        incoterm: draft.incoterm,
        destinationPort: draft.destinationPort,
        orderDate: draft.orderDate,
        expectedShipmentDate: draft.expectedShipmentDate,
        paymentTerms: draft.paymentTerms,
        advancePercentage: draft.advancePercentage
      });
      triggerToast(`Extracted PO fields via ${draft.extractionEngineLabel}. Confirm below to save.`);
    } finally {
      setPoExtracting(false);
    }
  };

  const handleSaveConfirmedOrder = (e: React.FormEvent) => {
    e.preventDefault();
    if (!orderForm.buyerName.trim() || !orderForm.productName.trim()) {
      triggerToast('Buyer Name and Product Name are required.');
      return;
    }
    if (orderForm.quantity <= 0 || orderForm.unitPrice <= 0) {
      triggerToast('Quantity and Unit Price must be greater than zero.');
      return;
    }

    const now = Date.now();
    const orderId =
      orderForm.orderNumber.trim() || `ORDER-00${dataset.orders.length + 1}`;
    const grossVal = calculateGrossOrderValue(orderForm.quantity, orderForm.unitPrice);
    const fx = defaultExchangeRateForCurrency(orderForm.currency);

    const newOrder: ExportOrder = {
      id: orderId,
      userId: isDemoMode ? SAMPLE_USER_ID : prodUserEmail,
      orderNumber: orderId,
      exporterName: SAMPLE_WORKSPACE_PROFILE.companyName,
      exporterContact: 'exports@tnglobalexports.in | +91 44 2250 4890',
      exporterAddress: SAMPLE_WORKSPACE_PROFILE.headquarters,
      buyerName: orderForm.buyerName.trim(),
      buyerCountry: orderForm.buyerCountry.trim() || 'Germany',
      buyerContact: orderForm.buyerContact.trim(),
      buyerAddress: orderForm.buyerAddress.trim(),
      productName: orderForm.productName.trim(),
      productDescription: orderForm.productDescription.trim() || orderForm.productName.trim(),
      quantity: Number(orderForm.quantity),
      unit: orderForm.unit.trim() || 'KG',
      unitPrice: Number(orderForm.unitPrice),
      orderValue: grossVal,
      currency: orderForm.currency,
      incoterm: orderForm.incoterm,
      destinationPort: orderForm.destinationPort,
      orderDate: orderForm.orderDate,
      expectedShipmentDate: orderForm.expectedShipmentDate,
      paymentTerms: orderForm.paymentTerms,
      advancePercentage: Number(orderForm.advancePercentage),
      orderStatus: 'FINANCIALLY_ASSESSED',
      extractionSource: poDraft ? 'AI_EXTRACTED_CONFIRMED' : 'MANUAL',
      createdAtEpochMs: now,
      updatedAtEpochMs: now
    };

    const newAssessment = calculateAssessment({
      userId: newOrder.userId,
      exportOrderId: orderId,
      existingId: `FA-${orderId}`,
      quantity: newOrder.quantity,
      unitPrice: newOrder.unitPrice,
      currency: newOrder.currency,
      exchangeRate: fx,
      advancePercentage: newOrder.advancePercentage,
      paymentFeePercentage: 2.5,
      paymentFixedFee: 15,
      procurementCostInr: round2(grossVal * fx * 0.58),
      logisticsCostInr: round2(grossVal * fx * 0.08),
      packagingCostInr: round2(grossVal * fx * 0.03),
      otherCostsInr: round2(grossVal * fx * 0.02)
    });

    const newDocs: DocumentRecord[] = STANDARD_DOCUMENT_TYPES.map((dt, idx) => ({
      id: `DOC-${orderId}-${idx + 1}`,
      userId: newOrder.userId,
      exportOrderId: orderId,
      documentType: dt.type,
      requirementLevel: dt.level,
      status: 'MISSING',
      fileName: '',
      fileReference: '',
      reviewerNote: 'Generated upon order confirmation. Awaiting file upload.',
      fileMimeType: '',
      fileSizeBytes: 0,
      sha256Checksum: '',
      versionNumber: 1,
      reviewerRole: 'PENDING',
      createdAtEpochMs: now,
      updatedAtEpochMs: now
    }));

    const newActivity: ActivityRecord = {
      id: `ACT-${now}`,
      userId: newOrder.userId,
      exportOrderId: orderId,
      eventType: 'ORDER_CONFIRMED',
      title: `Export Order ${orderId} Confirmed`,
      description: `${newOrder.buyerName} (${newOrder.currency} ${newOrder.orderValue.toLocaleString()}) added to ledger with 8 compliance document slots.`,
      createdAtEpochMs: now,
      updatedAtEpochMs: now
    };

    persistDataset({
      orders: [newOrder, ...dataset.orders],
      assessments: [newAssessment, ...dataset.assessments],
      documents: [...newDocs, ...dataset.documents],
      payments: dataset.payments,
      activities: [newActivity, ...dataset.activities]
    });
    setSelectedOrderId(orderId);
    setPoDraft(null);
    triggerToast(`Export Order ${orderId} saved and financial assessment calculated.`);
  };

  const handleUpdateOrderStatus = (
    orderId: string,
    nextStatus: ExportOrder['orderStatus']
  ) => {
    const now = Date.now();
    const updatedOrders = dataset.orders.map((o) =>
      o.id === orderId ? { ...o, orderStatus: nextStatus, updatedAtEpochMs: now } : o
    );
    const act: ActivityRecord = {
      id: `ACT-${now}`,
      userId: isDemoMode ? SAMPLE_USER_ID : prodUserEmail,
      exportOrderId: orderId,
      eventType: 'ORDER_STATUS_UPDATED',
      title: `${orderId} Status -> ${nextStatus}`,
      description: `Consignment workflow status transitioned to ${nextStatus}.`,
      createdAtEpochMs: now,
      updatedAtEpochMs: now
    };
    persistDataset({
      ...dataset,
      orders: updatedOrders,
      activities: [act, ...dataset.activities]
    });
    triggerToast(`Updated ${orderId} status to ${nextStatus}`);
  };

  const handleDeleteOrder = (orderId: string) => {
    setConfirmModal({
      title: `Delete Export Order ${orderId}?`,
      message: `Are you sure you want to delete ${orderId} and its linked financial, document, and payment records from the current ${
        isDemoMode ? 'Sample Business' : 'Production'
      } Workspace?`,
      confirmLabel: 'Delete Order',
      danger: true,
      onConfirm: () => {
        const nextOrders = dataset.orders.filter((o) => o.id !== orderId);
        persistDataset({
          orders: nextOrders,
          assessments: dataset.assessments.filter((a) => a.exportOrderId !== orderId),
          documents: dataset.documents.filter((d) => d.exportOrderId !== orderId),
          payments: dataset.payments.filter((p) => p.exportOrderId !== orderId),
          activities: dataset.activities
        });
        if (selectedOrderId === orderId) {
          setSelectedOrderId(nextOrders[0]?.id || '');
        }
        setConfirmModal(null);
        triggerToast(`Deleted ${orderId}`);
      }
    });
  };

  const handleUpdateFinancialField = (
    field:
      | 'exchangeRate'
      | 'procurementCostInr'
      | 'logisticsCostInr'
      | 'packagingCostInr'
      | 'otherCostsInr'
      | 'paymentFeePercentage',
    value: number
  ) => {
    if (!selectedOrder || !selectedAssessment) return;
    const nextParams = {
      userId: selectedOrder.userId,
      exportOrderId: selectedOrder.id,
      existingId: selectedAssessment.id,
      quantity: selectedOrder.quantity,
      unitPrice: selectedOrder.unitPrice,
      currency: selectedOrder.currency,
      exchangeRate:
        field === 'exchangeRate' ? Math.max(0.01, value) : selectedAssessment.exchangeRate,
      advancePercentage: selectedAssessment.advancePercentage,
      paymentFeePercentage:
        field === 'paymentFeePercentage'
          ? Math.max(0, value)
          : selectedAssessment.paymentFeePercentage,
      paymentFixedFee: selectedAssessment.paymentFixedFee,
      procurementCostInr:
        field === 'procurementCostInr'
          ? Math.max(0, value)
          : selectedAssessment.procurementCostInr,
      logisticsCostInr:
        field === 'logisticsCostInr'
          ? Math.max(0, value)
          : selectedAssessment.logisticsCostInr,
      packagingCostInr:
        field === 'packagingCostInr'
          ? Math.max(0, value)
          : selectedAssessment.packagingCostInr,
      otherCostsInr:
        field === 'otherCostsInr' ? Math.max(0, value) : selectedAssessment.otherCostsInr,
      assumptionsNote: selectedAssessment.assumptionsNote
    };
    const recalculated = calculateAssessment(nextParams);
    const nextAssessments = dataset.assessments.some((a) => a.exportOrderId === selectedOrder.id)
      ? dataset.assessments.map((a) =>
          a.exportOrderId === selectedOrder.id ? recalculated : a
        )
      : [...dataset.assessments, recalculated];

    persistDataset({
      ...dataset,
      assessments: nextAssessments
    });
  };

  const handleDocumentFileUpload = async (doc: DocumentRecord, file: File | undefined) => {
    if (!file) return;
    const allowedTypes = ['application/pdf', 'image/png', 'image/jpeg', 'image/jpg'];
    if (!allowedTypes.includes(file.type)) {
      triggerToast('Rejected: Only PDF, PNG, and JPG trade documents are allowed.');
      return;
    }
    if (file.size > 15 * 1024 * 1024) {
      triggerToast('Rejected: File size exceeds 15MB maximum limit.');
      return;
    }

    setUploadingDocId(doc.id);
    try {
      const arrayBuffer = await file.arrayBuffer();
      const hashBuffer = await crypto.subtle.digest('SHA-256', arrayBuffer);
      const hashArray = Array.from(new Uint8Array(hashBuffer));
      const sha256 = hashArray.map((b) => b.toString(16).padStart(2, '0')).join('');

      const now = Date.now();
      const updatedDoc: DocumentRecord = {
        ...doc,
        fileName: file.name,
        fileMimeType: file.type,
        fileSizeBytes: file.size,
        sha256Checksum: sha256,
        status: 'PENDING_REVIEW',
        versionNumber: doc.fileName ? doc.versionNumber + 1 : doc.versionNumber,
        reviewerNote: `Uploaded ${file.name} (${(file.size / 1024).toFixed(
          1
        )} KB). SHA-256 computed locally via Web Crypto API.`,
        updatedAtEpochMs: now
      };

      const act: ActivityRecord = {
        id: `ACT-DOC-${now}`,
        userId: doc.userId,
        exportOrderId: doc.exportOrderId,
        eventType: 'DOCUMENT_UPLOADED_SHA256',
        title: `${doc.documentType} Uploaded (v${updatedDoc.versionNumber})`,
        description: `SHA-256 checksum ${sha256.slice(0, 16)}... computed for ${file.name}.`,
        createdAtEpochMs: now,
        updatedAtEpochMs: now
      };

      persistDataset({
        ...dataset,
        documents: dataset.documents.map((d) => (d.id === doc.id ? updatedDoc : d)),
        activities: [act, ...dataset.activities]
      });
      triggerToast(`Computed SHA-256 checksum for ${file.name}`);
    } finally {
      setUploadingDocId(null);
    }
  };

  const handleUpdateDocStatus = (docId: string, nextStatus: DocumentStatus) => {
    const target = dataset.documents.find((d) => d.id === docId);
    if (!target) return;
    const updated: DocumentRecord = {
      ...target,
      status: nextStatus,
      updatedAtEpochMs: Date.now()
    };
    persistDataset({
      ...dataset,
      documents: dataset.documents.map((d) => (d.id === docId ? updated : d))
    });
    triggerToast(`${target.documentType} status updated to ${nextStatus}`);
  };

  const handleCreateDemoPayment = () => {
    if (!selectedOrder) return;
    const res = createDemoPayment({
      order: selectedOrder,
      amount: newPaymentAmountForeign,
      currency: selectedOrder.currency,
      paymentType: newPaymentType,
      existingPayments: dataset.payments,
      userId: selectedOrder.userId
    });

    const nextPayments = res.isDuplicatePrevented
      ? dataset.payments
      : [res.payment, ...dataset.payments];

    persistDataset({
      ...dataset,
      payments: nextPayments,
      activities: [res.activity, ...dataset.activities]
    });
    triggerToast(res.message);
  };

  const handleTransitionDemoPayment = (
    payment: PaymentRecord,
    outcome: 'DEMO_CAPTURED' | 'DEMO_FAILED' | 'DEMO_CANCELLED'
  ) => {
    const res = transitionDemoPayment({
      payment,
      orderNumber: payment.exportOrderId,
      outcome,
      userId: payment.userId
    });

    persistDataset({
      ...dataset,
      payments: dataset.payments.map((p) =>
        p.id === payment.id ? res.updatedPayment : p
      ),
      activities: [res.activity, ...dataset.activities]
    });
    triggerToast(res.message);
  };

  const handleCreatePayPalSandboxOrder = async (
    paymentType: 'ADVANCE' | 'BALANCE' | 'FULL'
  ) => {
    if (!selectedOrder) return;
    setPaypalBusyOrderId('CREATE');
    setPaypalErrorBanner(null);
    try {
      await syncAuthoritativeOrdersToServer(dataset.orders);
      const res = await createPayPalSandboxOrder({
        orderId: selectedOrder.id,
        paymentType,
        workspaceMode
      });
      await refreshPayPalSandboxState(workspaceMode);
      const act: ActivityRecord = {
        id: `ACT-PP-CREATE-${Date.now()}`,
        userId: selectedOrder.userId,
        exportOrderId: selectedOrder.id,
        eventType: 'PAYPAL_SANDBOX_ORDER_CREATED',
        title: `PayPal Sandbox Order Created (${res.paypalOrderId})`,
        description: `Created PayPal Orders v2 order ${res.paypalOrderId} for ${selectedOrder.orderNumber} (${res.currency} ${res.amount.toLocaleString()}). Status: ${res.status}.`,
        createdAtEpochMs: Date.now(),
        updatedAtEpochMs: Date.now()
      };
      persistDataset({
        ...dataset,
        activities: [act, ...dataset.activities]
      });
      triggerToast(`Created PayPal Sandbox Order ${res.paypalOrderId} (${res.status}).`);
    } catch (err: any) {
      const msg = err?.message || 'Unable to create PayPal Sandbox order.';
      setPaypalErrorBanner(msg);
      triggerToast(msg);
    } finally {
      setPaypalBusyOrderId(null);
    }
  };

  const handleCheckPayPalSandboxStatus = async (paypalOrderId: string, orderId: string) => {
    setPaypalBusyOrderId(paypalOrderId);
    setPaypalErrorBanner(null);
    try {
      const res = await fetchPayPalSandboxOrderStatus({ paypalOrderId, orderId });
      await refreshPayPalSandboxState(workspaceMode);
      triggerToast(
        `Verified PayPal Sandbox Order ${res.paypalOrderId}: status is ${res.status} (${res.rawPayPalStatus}).`
      );
    } catch (err: any) {
      const msg = err?.message || 'Failed to check PayPal Sandbox order status.';
      setPaypalErrorBanner(msg);
      triggerToast(msg);
    } finally {
      setPaypalBusyOrderId(null);
    }
  };

  const handleCapturePayPalSandboxOrder = async (paypalOrderId: string, orderId: string) => {
    setPaypalBusyOrderId(paypalOrderId);
    setPaypalErrorBanner(null);
    try {
      const res = await capturePayPalSandboxOrder({ paypalOrderId, orderId });
      await refreshPayPalSandboxState(workspaceMode);
      const act: ActivityRecord = {
        id: `ACT-PP-CAPTURE-${Date.now()}`,
        userId: isDemoMode ? SAMPLE_USER_ID : prodUserEmail,
        exportOrderId: orderId,
        eventType: 'PAYPAL_SANDBOX_CAPTURED',
        title: `PayPal Sandbox Payment Captured (${res.paypalCaptureId})`,
        description: `PayPal Order ${res.paypalOrderId} captured via /v2/checkout/orders/${res.paypalOrderId}/capture. Capture ID: ${res.paypalCaptureId} (${res.currency} ${res.amount.toLocaleString()}).`,
        createdAtEpochMs: Date.now(),
        updatedAtEpochMs: Date.now()
      };
      persistDataset({
        ...dataset,
        activities: [act, ...dataset.activities]
      });
      triggerToast(
        `Captured PayPal Sandbox Order ${res.paypalOrderId} (Capture ID: ${res.paypalCaptureId}).`
      );
    } catch (err: any) {
      const msg = err?.message || 'PayPal Sandbox capture failed.';
      setPaypalErrorBanner(msg);
      await refreshPayPalSandboxState(workspaceMode);
      triggerToast(msg);
    } finally {
      setPaypalBusyOrderId(null);
    }
  };

  const handleCancelPayPalSandboxOrder = async (paypalOrderId: string, orderId: string) => {
    setPaypalBusyOrderId(paypalOrderId);
    setPaypalErrorBanner(null);
    try {
      await cancelPayPalSandboxOrder({ paypalOrderId, orderId });
      await refreshPayPalSandboxState(workspaceMode);
      triggerToast(`Marked PayPal Sandbox Order ${paypalOrderId} as CANCELLED.`);
    } catch (err: any) {
      const msg = err?.message || 'Unable to cancel PayPal Sandbox order.';
      setPaypalErrorBanner(msg);
      triggerToast(msg);
    } finally {
      setPaypalBusyOrderId(null);
    }
  };

  const handleExecuteNlQuery = async () => {
    if (!nlQueryInput.trim()) return;
    setNlQueryLoading(true);
    try {
      const res = await runLiveOrFallbackNlQuery({
        query: nlQueryInput,
        orders: dataset.orders,
        assessments: dataset.assessments,
        documents: dataset.documents,
        payments: dataset.payments
      });
      setNlQueryResult({
        answerTitle: res.answerTitle,
        answerBullets: res.answerBullets,
        citedOrders: res.citedOrders,
        engineLabel: res.engineLabel
      });
    } finally {
      setNlQueryLoading(false);
    }
  };

  const handleRunPortfolioAdvisory = async () => {
    setAdvisorLoading(true);
    try {
      const res = await requestLiveOrFallbackPortfolioAdvisory({
        orders: dataset.orders,
        assessments: dataset.assessments,
        documents: dataset.documents,
        payments: dataset.payments
      });
      setLiveAdvisoryOverride(res);
      triggerToast(`Portfolio AI synthesis complete (${res.engineLabel})`);
    } finally {
      setAdvisorLoading(false);
    }
  };

  const handleResetDemoWorkspace = () => {
    setConfirmModal({
      title: 'Reset Sample Business Workspace?',
      message:
        'This will restore all 6 sample export orders (ORDER-001 through ORDER-006), financial assessments, 48 document records, and payment records to their authoritative initial values.',
      confirmLabel: 'Restore Authoritative Sample Data',
      danger: false,
      onConfirm: () => {
        const fresh = resetSampleWorkspace();
        setDataset(fresh);
        setSelectedOrderId('ORDER-001');
        setConfirmModal(null);
        triggerToast('Sample Business Workspace restored to authoritative defaults.');
      }
    });
  };

  const filteredOrders = useMemo(() => {
    return dataset.orders.filter((o) => {
      const matchesStatus =
        orderStatusFilter === 'ALL' || o.orderStatus === orderStatusFilter;
      const q = orderSearch.toLowerCase().trim();
      const matchesSearch =
        !q ||
        o.orderNumber.toLowerCase().includes(q) ||
        o.buyerName.toLowerCase().includes(q) ||
        o.buyerCountry.toLowerCase().includes(q) ||
        o.productName.toLowerCase().includes(q) ||
        o.destinationPort.toLowerCase().includes(q);
      return matchesStatus && matchesSearch;
    });
  }, [dataset.orders, orderStatusFilter, orderSearch]);

  const commandCentreData = useMemo(() => {
    const pendingPayments = dataset.payments.filter((p) =>
      isPaymentPending(p.paymentStatus)
    );
    const paymentExceptions = dataset.payments.filter(
      (p) =>
        p.paymentStatus === 'FAILED' ||
        p.paymentStatus === 'DEMO_FAILED' ||
        p.paymentStatus === 'DEMO_CANCELLED'
    );
    const incompleteDocs = dataset.documents.filter(
      (d) =>
        d.requirementLevel === 'REQUIRED' && !isDocumentVerifiedOrApproved(d.status)
    );
    const attentionOrders = shipmentIntelItems.filter(
      (s) => s.shipmentRiskStatus !== 'READY_OR_DISPATCHED'
    );

    return {
      pendingPayments,
      paymentExceptions,
      incompleteDocs,
      attentionOrders
    };
  }, [dataset.payments, dataset.documents, shipmentIntelItems]);

  const navItems: {
    id: WebNavTab;
    label: string;
    icon: NiryatIconName;
    section: 'COMMAND' | 'OPERATIONS' | 'FINANCE' | 'INTELLIGENCE' | 'GOVERNANCE';
    badge?: string | number;
  }[] = [
    {
      id: 'dashboard',
      label: 'Executive Dashboard',
      icon: 'layout-dashboard',
      section: 'COMMAND',
      badge: timePeriod
    },
    {
      id: 'command_centre',
      label: 'Operational Command',
      icon: 'panels-top-left',
      section: 'COMMAND',
      badge: allRisks.filter((r) => r.severity === 'CRITICAL').length
    },
    {
      id: 'orders',
      label: 'Export Orders & AI PO',
      icon: 'package',
      section: 'OPERATIONS',
      badge: dataset.orders.length
    },
    {
      id: 'shipment_intel',
      label: 'Shipment Intelligence',
      icon: 'ship',
      section: 'OPERATIONS',
      badge: shipmentIntelItems.filter((i) => i.shipmentRiskStatus === 'AT_RISK_BLOCKED').length
    },
    {
      id: 'digital_twin',
      label: '8-Stage Digital Twin',
      icon: 'workflow',
      section: 'OPERATIONS',
      badge: '8 GATES'
    },
    {
      id: 'documents',
      label: 'Document Centre (SHA-256)',
      icon: 'file-check',
      section: 'OPERATIONS',
      badge: `${periodAnalytics.metrics.documentReadinessRatePercent}%`
    },
    {
      id: 'financials',
      label: 'Financial Intelligence',
      icon: 'chart-no-axes-combined',
      section: 'FINANCE',
      badge: 'INR'
    },
    {
      id: 'receivables',
      label: 'Receivables & Cash Flow',
      icon: 'wallet',
      section: 'FINANCE',
      badge: `₹${formatInrShort(receivablesAnalytics.uncollectedExposureInr)}`
    },
    {
      id: 'payments',
      label: 'Payment Hub',
      icon: 'credit-card',
      section: 'FINANCE',
      badge: dataset.payments.length + paypalTransactions.length
    },
    {
      id: 'buyers',
      label: 'Buyer Intelligence',
      icon: 'users',
      section: 'INTELLIGENCE',
      badge: buyerAnalytics.length
    },
    {
      id: 'ai_risk',
      label: 'Trade Risk & AI Advisor',
      icon: 'brain-circuit',
      section: 'INTELLIGENCE',
      badge: activeAdvisor.recommendations.length
    },
    {
      id: 'reports',
      label: 'Reporting & Exports',
      icon: 'file-spreadsheet',
      section: 'GOVERNANCE',
      badge: 'CSV/PDF'
    },
    {
      id: 'workspace',
      label: 'Workspace & Governance',
      icon: 'building-2',
      section: 'GOVERNANCE',
      badge: isDemoMode ? 'DEMO' : 'PROD'
    }
  ];

  return (
    <div className={`app-shell ${sidebarCollapsed ? 'sidebar-collapsed' : ''}`}>
      <aside className="sidebar" aria-label="Primary Enterprise Navigation">
        <div>
          <div className="brand-header">
            <BrandLogo variant="sidebar" collapsed={sidebarCollapsed} size={36} />
            <button
              type="button"
              className="sidebar-collapse-btn"
              onClick={() => setSidebarCollapsed((c) => !c)}
              aria-label={sidebarCollapsed ? 'Expand Sidebar' : 'Collapse Sidebar'}
              title={sidebarCollapsed ? 'Expand Navigation' : 'Collapse Navigation'}
            >
              <NiryatIcon
                name={sidebarCollapsed ? 'chevron-right' : 'chevron-left'}
                size="compact"
              />
            </button>
          </div>

          {(['COMMAND', 'OPERATIONS', 'FINANCE', 'INTELLIGENCE', 'GOVERNANCE'] as const).map(
            (section) => (
              <div key={section}>
                {!sidebarCollapsed && <div className="nav-section-label">{section}</div>}
                <nav className="nav-list">
                  {navItems
                    .filter((item) => item.section === section)
                    .map((item) => (
                      <button
                        key={item.id}
                        type="button"
                        onClick={() => setActiveTab(item.id)}
                        className={`nav-item ${activeTab === item.id ? 'active' : ''}`}
                        title={sidebarCollapsed ? item.label : undefined}
                        aria-label={item.label}
                      >
                        <span className="nav-item-left">
                          <NiryatIcon
                            name={item.icon}
                            size="nav"
                            active={activeTab === item.id}
                          />
                          {!sidebarCollapsed && (
                            <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                              {item.label}
                            </span>
                          )}
                        </span>
                        {!sidebarCollapsed && item.badge !== undefined && (
                          <span className="nav-badge">{item.badge}</span>
                        )}
                      </button>
                    ))}
                </nav>
              </div>
            )
          )}
        </div>

        {!sidebarCollapsed && (
          <div
            style={{
              marginTop: '1.15rem',
              padding: '0.85rem',
              borderRadius: 'var(--radius-md)',
              backgroundColor: 'rgba(255, 255, 255, 0.06)',
              border: '1px solid rgba(245, 242, 234, 0.14)',
              fontSize: '0.73rem',
              color: '#F5F2EA'
            }}
          >
            <div style={{ fontWeight: 700, marginBottom: '0.2rem' }}>
              {SAMPLE_WORKSPACE_PROFILE.companyName}
            </div>
            <div className="mono-code" style={{ opacity: 0.78, fontSize: '0.68rem' }}>
              {SAMPLE_WORKSPACE_PROFILE.iecNumber}
            </div>
            <div className="mono-code" style={{ opacity: 0.78, fontSize: '0.68rem' }}>
              GSTIN: {SAMPLE_WORKSPACE_PROFILE.gstin}
            </div>
            <div
              className="mono-code"
              style={{ opacity: 0.65, fontSize: '0.66rem', marginTop: '0.15rem' }}
            >
              AD: {SAMPLE_WORKSPACE_PROFILE.adCode}
            </div>
            <div
              style={{
                fontSize: '0.66rem',
                color: 'var(--gold-primary)',
                marginTop: '0.45rem',
                fontStyle: 'italic'
              }}
            >
              {BRAND_TAGLINE}
            </div>
          </div>
        )}
      </aside>

      <div className="main-area">
        <div className="mobile-nav-bar" role="navigation" aria-label="Mobile Navigation">
          <BrandLogo variant="square" size={28} />
          {navItems.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => setActiveTab(item.id)}
              className={`btn btn-xs ${activeTab === item.id ? 'btn-gold' : 'btn-outline'}`}
            >
              <NiryatIcon name={item.icon} size="compact" />
              {item.label}
            </button>
          ))}
        </div>

        <header className="top-bar">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.85rem', flexWrap: 'wrap' }}>
            <span className={`badge ${isDemoMode ? 'badge-amber' : 'badge-emerald'}`}>
              <NiryatIcon name="building-2" size="compact" />
              {isDemoMode ? 'SAMPLE BUSINESS WORKSPACE' : 'PRODUCTION WORKSPACE'}
            </span>

            <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem' }}>
              <label
                htmlFor="active-order-select"
                style={{ fontSize: '0.76rem', color: 'var(--text-secondary)', fontWeight: 600 }}
              >
                Active Consignment:
              </label>
              <select
                id="active-order-select"
                className="select-field"
                style={{ width: 'auto', padding: '0.32rem 0.65rem', fontSize: '0.78rem' }}
                value={selectedOrder?.id || ''}
                onChange={(e) => setSelectedOrderId(e.target.value)}
              >
                {dataset.orders.length === 0 && <option value="">No Orders in Workspace</option>}
                {dataset.orders.map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.orderNumber} — {o.buyerName} ({o.currency} {o.orderValue.toLocaleString()}) [
                    {o.orderStatus}]
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', flexWrap: 'wrap' }}>
            <span
              className={`badge ${
                paypalConfig?.configured ? 'badge-emerald' : 'badge-gold'
              }`}
              title="PayPal Sandbox Orders v2 REST API status on server"
            >
              <NiryatIcon name="credit-card" size="compact" />
              {paypalConfig?.configured ? 'PayPal Sandbox: Live' : 'PayPal Sandbox: Ready'}
            </span>
            <span
              className={`badge ${serverGeminiConfigured ? 'badge-emerald' : 'badge-indigo'}`}
              title="Gemini API keys are managed strictly on the server via /api/ai/* and never exposed in browser code."
            >
              <NiryatIcon name="sparkles" size="compact" />
              {serverGeminiConfigured
                ? 'AI Engine: Server Gemini Active'
                : 'AI Engine: Deterministic Fallback Ready'}
            </span>
            <button
              type="button"
              className="btn btn-outline btn-xs"
              onClick={() =>
                setWorkspaceMode((prev) =>
                  prev === 'SAMPLE_BUSINESS' ? 'AUTHENTICATED_USER' : 'SAMPLE_BUSINESS'
                )
              }
            >
              Switch to {isDemoMode ? 'Production' : 'Sample'} Mode
            </button>
            {isDemoMode && (
              <button
                type="button"
                className="btn btn-outline btn-xs"
                onClick={handleResetDemoWorkspace}
              >
                <NiryatIcon name="refresh-cw" size="compact" /> Reset Sample Data
              </button>
            )}
          </div>
        </header>

        {toastMessage && (
          <div
            role="status"
            style={{
              position: 'fixed',
              bottom: '1.5rem',
              right: '1.5rem',
              backgroundColor: '#141413',
              color: '#FFFFFF',
              padding: '0.8rem 1.2rem',
              borderRadius: 'var(--radius-md)',
              boxShadow: 'var(--shadow-lg)',
              zIndex: 100,
              fontSize: '0.84rem',
              borderLeft: '4px solid var(--gold-primary)'
            }}
          >
            {toastMessage}
          </div>
        )}

        {confirmModal && (
          <div className="modal-backdrop" role="dialog" aria-modal="true">
            <div className="modal-card">
              <h3 className="heading-font" style={{ fontSize: '1.1rem', marginBottom: '0.5rem' }}>
                {confirmModal.title}
              </h3>
              <p
                style={{
                  fontSize: '0.86rem',
                  color: 'var(--text-secondary)',
                  marginBottom: '1.25rem'
                }}
              >
                {confirmModal.message}
              </p>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.65rem' }}>
                <button
                  type="button"
                  className="btn btn-outline"
                  onClick={() => setConfirmModal(null)}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  className={`btn ${confirmModal.danger ? 'btn-danger' : 'btn-primary'}`}
                  onClick={confirmModal.onConfirm}
                >
                  {confirmModal.confirmLabel}
                </button>
              </div>
            </div>
          </div>
        )}

        <main className="page-content">
          <div className={`governance-banner ${isDemoMode ? 'demo' : 'production'}`}>
            <div>
              <strong>
                {isDemoMode
                  ? SAMPLE_WORKSPACE_PROFILE.workspaceBannerText
                  : `PRODUCTION WORKSPACE (${prodUserEmail}) — Isolated Live Records`}
              </strong>
            </div>
            <div style={{ display: 'flex', gap: '0.45rem' }}>
              <button
                type="button"
                className="btn btn-outline btn-xs"
                onClick={() => setActiveTab('reports')}
              >
                Export CSV / PDF Reports
              </button>
              <button
                type="button"
                className="btn btn-outline btn-xs"
                onClick={() => setActiveTab('command_centre')}
              >
                Operational Command Centre
              </button>
            </div>
          </div>

          {/* ========================================================= */}
          {/* TAB 1: ADVANCED EXECUTIVE DASHBOARD (PART 3)              */}
          {/* ========================================================= */}
          {activeTab === 'dashboard' && (
            <div>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'flex-end',
                  marginBottom: '1.25rem',
                  flexWrap: 'wrap',
                  gap: '1rem'
                }}
              >
                <div>
                  <h1 style={{ fontSize: '1.6rem', fontWeight: 700 }}>
                    Executive Trade &amp; Treasury Dashboard
                  </h1>
                  <p style={{ color: 'var(--text-secondary)', fontSize: '0.86rem' }}>
                    {periodAnalytics.periodSummaryText}
                  </p>
                </div>

                <div>
                  <div
                    style={{
                      fontSize: '0.68rem',
                      color: 'var(--text-muted)',
                      marginBottom: '0.25rem',
                      textAlign: 'right',
                      fontWeight: 600
                    }}
                  >
                    PORTFOLIO HORIZON FILTER
                  </div>
                  <div className="segmented-bar" role="group" aria-label="Time Range Filter">
                    {(['1D', '1W', '1M', '3M', '1Y', 'ALL'] as TimePeriodCode[]).map((tf) => (
                      <button
                        key={tf}
                        type="button"
                        className={`segment-btn ${timePeriod === tf ? 'active' : ''}`}
                        onClick={() => setTimePeriod(tf)}
                      >
                        {tf}
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              {/* SECTION A: 7 EXECUTIVE KPI CARDS */}
              <div className="grid-7" style={{ marginBottom: '1.25rem' }}>
                <div
                  className="kpi-card"
                  style={{ borderColor: 'var(--gold-border)', backgroundColor: '#FFFDF9' }}
                >
                  <div className="kpi-label">1. Total Export Value</div>
                  <div className="kpi-value">
                    ₹{formatInrShort(periodAnalytics.metrics.totalOrderValueInr)}
                  </div>
                  <div className="kpi-sub">
                    Est. Profit:{' '}
                    <strong style={{ color: 'var(--emerald-primary)' }}>
                      ₹{formatInrShort(periodAnalytics.metrics.totalEstimatedProfitInr)}
                    </strong>{' '}
                    ({periodAnalytics.metrics.averageProfitMarginPercent}%)
                  </div>
                </div>

                <div className="kpi-card">
                  <div className="kpi-label">2. Active Export Orders</div>
                  <div className="kpi-value">{periodAnalytics.metrics.activeOrders}</div>
                  <div className="kpi-sub">
                    Out of {periodAnalytics.metrics.totalOrders} orders in {timePeriod}
                  </div>
                </div>

                <div className="kpi-card">
                  <div className="kpi-label">3. Completed Orders</div>
                  <div className="kpi-value" style={{ color: 'var(--emerald-primary)' }}>
                    {periodAnalytics.metrics.completedOrders}
                  </div>
                  <div className="kpi-sub">
                    Cancelled: {periodAnalytics.metrics.cancelledOrders}
                  </div>
                </div>

                <div className="kpi-card">
                  <div className="kpi-label">4. Outstanding Receivables</div>
                  <div className="kpi-value" style={{ color: 'var(--amber-primary)' }}>
                    ₹{formatInrShort(periodAnalytics.metrics.outstandingPaymentValueInr)}
                  </div>
                  <div className="kpi-sub">Uncollected invoice exposure</div>
                </div>

                <div className="kpi-card">
                  <div className="kpi-label">5. Collection Status</div>
                  <div className="kpi-value" style={{ color: 'var(--emerald-primary)' }}>
                    {periodAnalytics.metrics.paymentCompletionRatePercent}%
                  </div>
                  <div className="kpi-sub">
                    Settled: ₹{formatInrShort(periodAnalytics.metrics.capturedPaymentValueInr)}
                  </div>
                </div>

                <div className="kpi-card">
                  <div className="kpi-label">6. Financial Exposure</div>
                  <div className="kpi-value" style={{ color: 'var(--crimson-primary)' }}>
                    ₹{formatInrShort(receivablesAnalytics.uncollectedExposureInr)}
                  </div>
                  <div className="kpi-sub">
                    Pending Approval: ₹{formatInrShort(receivablesAnalytics.pendingApprovalInr)}
                  </div>
                </div>

                <div className="kpi-card">
                  <div className="kpi-label">7. Orders Requiring Attention</div>
                  <div
                    className="kpi-value"
                    style={{
                      color:
                        commandCentreData.attentionOrders.length > 0
                          ? 'var(--crimson-primary)'
                          : 'var(--emerald-primary)'
                    }}
                  >
                    {commandCentreData.attentionOrders.length}
                  </div>
                  <div className="kpi-sub">Pre-shipment doc or advance gates</div>
                </div>
              </div>

              {/* SECTION D: ANALYTICAL PERIOD COMPARISONS */}
              <div className="surface-card" style={{ marginBottom: '1.25rem' }}>
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    marginBottom: '0.85rem',
                    flexWrap: 'wrap',
                    gap: '0.5rem'
                  }}
                >
                  <div>
                    <h3 style={{ fontSize: '0.98rem', fontWeight: 700 }}>
                      Analytical Period Comparison ({periodAnalytics.periodLabel} vs Previous Window)
                    </h3>
                    <p style={{ fontSize: '0.76rem', color: 'var(--text-secondary)' }}>
                      Derived strictly from actual order and payment timestamps without fabricating historical data.
                    </p>
                  </div>
                  <span className="badge badge-gold">{timePeriod} Analytical Delta</span>
                </div>

                <div className="grid-4">
                  <div
                    style={{
                      padding: '0.8rem',
                      backgroundColor: 'var(--bg-subtle)',
                      borderRadius: 'var(--radius-sm)'
                    }}
                  >
                    <div className="kpi-label">Current vs Previous Order Value</div>
                    <div
                      className="mono-metric"
                      style={{ fontSize: '1.02rem', fontWeight: 700, marginTop: '0.25rem' }}
                    >
                      ₹{formatInrShort(periodAnalytics.metrics.totalOrderValueInr)} vs{' '}
                      {timePeriod === 'ALL'
                        ? 'N/A (All-Time)'
                        : `₹${formatInrShort(periodAnalytics.previousPeriodOrderValueInr)}`}
                    </div>
                    <div
                      style={{
                        fontSize: '0.73rem',
                        color: 'var(--text-muted)',
                        marginTop: '0.2rem'
                      }}
                    >
                      Actual ledger window comparison
                    </div>
                  </div>

                  <div
                    style={{
                      padding: '0.8rem',
                      backgroundColor: 'var(--bg-subtle)',
                      borderRadius: 'var(--radius-sm)'
                    }}
                  >
                    <div className="kpi-label">Order Value Change (%)</div>
                    <div
                      className="mono-metric"
                      style={{
                        fontSize: '1.08rem',
                        fontWeight: 700,
                        marginTop: '0.25rem',
                        color:
                          periodAnalytics.orderValueGrowthPercent !== null &&
                          periodAnalytics.orderValueGrowthPercent >= 0
                            ? 'var(--emerald-primary)'
                            : 'var(--text-primary)'
                      }}
                    >
                      {periodAnalytics.orderValueGrowthPercent !== null
                        ? `${periodAnalytics.orderValueGrowthPercent >= 0 ? '+' : ''}${
                            periodAnalytics.orderValueGrowthPercent
                          }%`
                        : 'Insufficient data'}
                    </div>
                    <div
                      style={{
                        fontSize: '0.73rem',
                        color: 'var(--text-muted)',
                        marginTop: '0.2rem'
                      }}
                    >
                      {timePeriod === 'ALL'
                        ? 'Select 1D–1Y window for prior-window delta'
                        : 'Compared against immediately preceding window'}
                    </div>
                  </div>

                  <div
                    style={{
                      padding: '0.8rem',
                      backgroundColor: 'var(--bg-subtle)',
                      borderRadius: 'var(--radius-sm)'
                    }}
                  >
                    <div className="kpi-label">Collection Performance Change</div>
                    <div
                      className="mono-metric"
                      style={{ fontSize: '1.08rem', fontWeight: 700, marginTop: '0.25rem' }}
                    >
                      {periodAnalytics.capturedRevenueGrowthPercent !== null
                        ? `${periodAnalytics.capturedRevenueGrowthPercent >= 0 ? '+' : ''}${
                            periodAnalytics.capturedRevenueGrowthPercent
                          }%`
                        : 'Insufficient data'}
                    </div>
                    <div
                      style={{
                        fontSize: '0.73rem',
                        color: 'var(--text-muted)',
                        marginTop: '0.2rem'
                      }}
                    >
                      Prev window captured: ₹
                      {formatInrShort(periodAnalytics.previousPeriodCapturedInr)}
                    </div>
                  </div>

                  <div
                    style={{
                      padding: '0.8rem',
                      backgroundColor: 'var(--bg-subtle)',
                      borderRadius: 'var(--radius-sm)'
                    }}
                  >
                    <div className="kpi-label">Operational Risk Profile</div>
                    <div
                      className="mono-metric"
                      style={{ fontSize: '1.02rem', fontWeight: 700, marginTop: '0.25rem' }}
                    >
                      {allRisks.filter((r) => r.severity === 'CRITICAL').length} Critical /{' '}
                      {allRisks.filter((r) => r.severity === 'WARNING').length} Warning
                    </div>
                    <div
                      style={{
                        fontSize: '0.73rem',
                        color: 'var(--text-muted)',
                        marginTop: '0.2rem'
                      }}
                    >
                      Doc Readiness: {periodAnalytics.metrics.documentReadinessRatePercent}%
                    </div>
                  </div>
                </div>
              </div>

              {/* SECTION B: FINANCIAL VISUALIZATIONS */}
              <div className="grid-2" style={{ marginBottom: '1.25rem' }}>
                <div className="surface-card">
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      marginBottom: '0.9rem'
                    }}
                  >
                    <div>
                      <h3 style={{ fontSize: '0.98rem', fontWeight: 700 }}>
                        Export Order Value Trend vs Settled Payments ({timePeriod})
                      </h3>
                      <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                        Order intake vs payment settlements across actual ledger buckets
                      </p>
                    </div>
                    <span className="badge badge-emerald">Deterministic Engine</span>
                  </div>

                  <svg
                    width="100%"
                    height="220"
                    viewBox="0 0 600 220"
                    role="img"
                    aria-label="Export Order Value Trend Chart"
                  >
                    <line x1="40" y1="180" x2="580" y2="180" stroke="#D4CFC7" strokeWidth="1" />
                    {periodAnalytics.chartBucketLabels.map((lbl, idx) => {
                      const maxVal = Math.max(
                        ...periodAnalytics.chartOrderIntakeInr,
                        ...periodAnalytics.chartCapturedPaymentsInr,
                        1
                      );
                      const groupW =
                        520 / Math.max(periodAnalytics.chartBucketLabels.length, 1);
                      const xBase = 55 + idx * groupW;
                      const intakeVal = periodAnalytics.chartOrderIntakeInr[idx] || 0;
                      const capVal = periodAnalytics.chartCapturedPaymentsInr[idx] || 0;
                      const intakeH = Math.max(3, (intakeVal / maxVal) * 140);
                      const capH = Math.max(3, (capVal / maxVal) * 140);

                      return (
                        <g key={`${lbl}-${idx}`}>
                          <rect
                            x={xBase}
                            y={180 - intakeH}
                            width="20"
                            height={intakeH}
                            fill="#C69C3A"
                            rx="3"
                          />
                          <rect
                            x={xBase + 24}
                            y={180 - capH}
                            width="20"
                            height={capH}
                            fill="#1E7E5A"
                            rx="3"
                          />
                          <text
                            x={xBase + 22}
                            y="198"
                            textAnchor="middle"
                            fontSize="10"
                            fill="#57534E"
                            fontFamily="JetBrains Mono"
                          >
                            {lbl}
                          </text>
                        </g>
                      );
                    })}
                  </svg>
                  <div
                    style={{
                      display: 'flex',
                      gap: '1.2rem',
                      fontSize: '0.74rem',
                      marginTop: '0.4rem',
                      justifyContent: 'center'
                    }}
                  >
                    <span>
                      <strong style={{ color: '#C69C3A' }}>■</strong> Export Order Intake (INR)
                    </span>
                    <span>
                      <strong style={{ color: '#1E7E5A' }}>■</strong> Settled / Demo Captured (INR)
                    </span>
                  </div>
                </div>

                <div className="surface-card">
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      marginBottom: '0.9rem'
                    }}
                  >
                    <div>
                      <h3 style={{ fontSize: '0.98rem', fontWeight: 700 }}>
                        Payment Collection Breakdown &amp; Financial Exposure Overview
                      </h3>
                      <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                        Separates live captured funds, simulated demo captures, and uncollected exposure
                      </p>
                    </div>
                    <button
                      type="button"
                      className="btn btn-outline btn-xs"
                      onClick={() => setActiveTab('receivables')}
                    >
                      Open Receivables Hub
                    </button>
                  </div>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                    {[
                      {
                        label: 'Actual Live Captured Funds (CAPTURED)',
                        amount: receivablesAnalytics.liveCapturedInr,
                        color: 'var(--emerald-primary)'
                      },
                      {
                        label: 'Simulated Demo Captures (DEMO_CAPTURED)',
                        amount: receivablesAnalytics.simulatedCapturedInr,
                        color: 'var(--indigo-primary)'
                      },
                      {
                        label: 'Pending Approval / In-Flight Remittances',
                        amount: receivablesAnalytics.pendingApprovalInr,
                        color: 'var(--amber-primary)'
                      },
                      {
                        label: 'Uncollected Receivable Exposure',
                        amount: receivablesAnalytics.uncollectedExposureInr,
                        color: 'var(--crimson-primary)'
                      }
                    ].map((row) => {
                      const total = receivablesAnalytics.portfolioMetrics.totalOrderValueInr;
                      const pct =
                        total > 0 ? Math.min(100, Math.round((row.amount / total) * 100)) : 0;
                      return (
                        <div key={row.label}>
                          <div
                            style={{
                              display: 'flex',
                              justifyContent: 'space-between',
                              fontSize: '0.78rem',
                              marginBottom: '0.22rem'
                            }}
                          >
                            <span>{row.label}</span>
                            <span className="mono-metric" style={{ fontWeight: 700 }}>
                              ₹{formatInrShort(row.amount)} ({pct}%)
                            </span>
                          </div>
                          <div className="progress-track">
                            <div
                              style={{
                                width: `${pct}%`,
                                height: '100%',
                                backgroundColor: row.color,
                                borderRadius: '4px'
                              }}
                            />
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              </div>

              {/* SECTION C: OPERATIONAL VISUALIZATIONS */}
              <div className="grid-3" style={{ marginBottom: '1.25rem' }}>
                <div className="surface-card">
                  <h3 style={{ fontSize: '0.95rem', fontWeight: 700, marginBottom: '0.75rem' }}>
                    Order Pipeline &amp; Status Distribution
                  </h3>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.55rem' }}>
                    {Object.entries(periodAnalytics.metrics.statusDistribution).map(
                      ([status, count]) => {
                        const pct =
                          periodAnalytics.metrics.totalOrders > 0
                            ? Math.round((count / periodAnalytics.metrics.totalOrders) * 100)
                            : 0;
                        return (
                          <div key={status}>
                            <div
                              style={{
                                display: 'flex',
                                justifyContent: 'space-between',
                                fontSize: '0.78rem',
                                marginBottom: '0.2rem'
                              }}
                            >
                              <span style={{ fontWeight: 600 }}>{status}</span>
                              <span className="mono-metric">
                                {count} order{count > 1 ? 's' : ''} ({pct}%)
                              </span>
                            </div>
                            <div className="progress-track">
                              <div className="progress-fill" style={{ width: `${pct}%` }} />
                            </div>
                          </div>
                        );
                      }
                    )}
                  </div>
                </div>

                <div className="surface-card">
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      marginBottom: '0.75rem'
                    }}
                  >
                    <h3 style={{ fontSize: '0.95rem', fontWeight: 700 }}>
                      Shipment Readiness &amp; Document Verification
                    </h3>
                    <span
                      className="mono-metric"
                      style={{
                        fontSize: '0.82rem',
                        fontWeight: 700,
                        color: 'var(--emerald-primary)'
                      }}
                    >
                      {periodAnalytics.metrics.documentReadinessRatePercent}% Ready
                    </span>
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.55rem' }}>
                    {shipmentIntelItems.map((item) => (
                      <div
                        key={item.order.id}
                        style={{
                          padding: '0.45rem 0.6rem',
                          borderRadius: 'var(--radius-sm)',
                          backgroundColor: 'var(--bg-subtle)',
                          cursor: 'pointer'
                        }}
                        onClick={() => navigateToOrderModule('shipment_intel', item.order.id)}
                      >
                        <div
                          style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            fontSize: '0.76rem',
                            marginBottom: '0.2rem'
                          }}
                        >
                          <span>
                            <strong>{item.order.orderNumber}</strong> — {item.order.buyerName}
                          </span>
                          <span className="mono-metric" style={{ fontWeight: 700 }}>
                            {item.twin.overallProgressPercent}%
                          </span>
                        </div>
                        <div className="progress-track">
                          <div
                            className="progress-fill"
                            style={{ width: `${item.twin.overallProgressPercent}%` }}
                          />
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="surface-card">
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      marginBottom: '0.75rem'
                    }}
                  >
                    <h3 style={{ fontSize: '0.95rem', fontWeight: 700 }}>
                      Operational Risk Distribution
                    </h3>
                    <button
                      type="button"
                      className="btn btn-outline btn-xs"
                      onClick={() => setActiveTab('ai_risk')}
                    >
                      Inspect Risks
                    </button>
                  </div>
                  <div className="grid-2" style={{ gap: '0.65rem' }}>
                    <div
                      style={{
                        padding: '0.75rem',
                        borderRadius: 'var(--radius-sm)',
                        backgroundColor: 'var(--crimson-subtle)',
                        border: '1px solid var(--crimson-border)'
                      }}
                    >
                      <div className="kpi-label" style={{ color: 'var(--crimson-primary)' }}>
                        CRITICAL ALERTS
                      </div>
                      <div
                        className="mono-metric"
                        style={{
                          fontSize: '1.4rem',
                          fontWeight: 700,
                          color: 'var(--crimson-primary)'
                        }}
                      >
                        {allRisks.filter((r) => r.severity === 'CRITICAL').length}
                      </div>
                    </div>
                    <div
                      style={{
                        padding: '0.75rem',
                        borderRadius: 'var(--radius-sm)',
                        backgroundColor: 'var(--amber-subtle)',
                        border: '1px solid var(--amber-border)'
                      }}
                    >
                      <div className="kpi-label" style={{ color: 'var(--amber-primary)' }}>
                        WARNING ALERTS
                      </div>
                      <div
                        className="mono-metric"
                        style={{
                          fontSize: '1.4rem',
                          fontWeight: 700,
                          color: 'var(--amber-primary)'
                        }}
                      >
                        {allRisks.filter((r) => r.severity === 'WARNING').length}
                      </div>
                    </div>
                    <div
                      style={{
                        padding: '0.75rem',
                        borderRadius: 'var(--radius-sm)',
                        backgroundColor: 'var(--gold-subtle)',
                        border: '1px solid var(--gold-border)'
                      }}
                    >
                      <div className="kpi-label" style={{ color: 'var(--gold-dark)' }}>
                        ADVISORY ALERTS
                      </div>
                      <div
                        className="mono-metric"
                        style={{ fontSize: '1.4rem', fontWeight: 700, color: 'var(--gold-dark)' }}
                      >
                        {allRisks.filter((r) => r.severity === 'ADVISORY').length}
                      </div>
                    </div>
                    <div
                      style={{
                        padding: '0.75rem',
                        borderRadius: 'var(--radius-sm)',
                        backgroundColor: 'var(--emerald-subtle)',
                        border: '1px solid var(--emerald-border)'
                      }}
                    >
                      <div className="kpi-label" style={{ color: 'var(--emerald-primary)' }}>
                        TOTAL ALERTS
                      </div>
                      <div
                        className="mono-metric"
                        style={{
                          fontSize: '1.4rem',
                          fontWeight: 700,
                          color: 'var(--emerald-primary)'
                        }}
                      >
                        {allRisks.length}
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* Active Consignment Portfolio Ledger Table */}
              <div className="surface-card">
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    marginBottom: '0.9rem',
                    flexWrap: 'wrap',
                    gap: '0.5rem'
                  }}
                >
                  <h3 style={{ fontSize: '1rem', fontWeight: 700 }}>
                    Active Consignment Ledger ({periodAnalytics.filteredOrders.length} Orders in{' '}
                    {timePeriod})
                  </h3>
                  <button
                    type="button"
                    className="btn btn-primary btn-xs"
                    onClick={() => setActiveTab('orders')}
                  >
                    + New Export Order / AI PO Parse
                  </button>
                </div>

                <div className="table-scroll">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Order ID</th>
                        <th>Buyer &amp; Destination</th>
                        <th>Commodity</th>
                        <th>Foreign Value</th>
                        <th>Converted INR</th>
                        <th>Est. Profit (INR)</th>
                        <th>Margin</th>
                        <th>Status</th>
                        <th>Inspect</th>
                      </tr>
                    </thead>
                    <tbody>
                      {periodAnalytics.filteredOrders.map((order) => {
                        const fin = dataset.assessments.find(
                          (f) => f.exportOrderId === order.id
                        );
                        const fx =
                          fin?.exchangeRate ?? defaultExchangeRateForCurrency(order.currency);
                        const inrVal = fin?.convertedValueInr ?? round2(order.orderValue * fx);
                        const profitInr = fin?.estimatedProfitInr ?? round2(inrVal * 0.14);
                        const marginPct = fin?.profitMarginPercent ?? 14;

                        return (
                          <tr
                            key={order.id}
                            className={selectedOrder?.id === order.id ? 'selected-row' : ''}
                          >
                            <td className="mono-code" style={{ fontWeight: 700 }}>
                              {order.orderNumber}
                            </td>
                            <td>
                              <div style={{ fontWeight: 600 }}>{order.buyerName}</div>
                              <div style={{ fontSize: '0.74rem', color: 'var(--text-muted)' }}>
                                {order.buyerCountry} • {order.incoterm} • {order.destinationPort}
                              </div>
                            </td>
                            <td>
                              <div>{order.productName}</div>
                              <div
                                className="mono-code"
                                style={{ color: 'var(--text-muted)', fontSize: '0.72rem' }}
                              >
                                {order.quantity.toLocaleString()} {order.unit} @ {order.currency}{' '}
                                {order.unitPrice}
                              </div>
                            </td>
                            <td className="mono-metric">
                              {order.currency} {order.orderValue.toLocaleString()}
                              <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>
                                @ ₹{fx}
                              </div>
                            </td>
                            <td className="mono-metric" style={{ fontWeight: 600 }}>
                              ₹{formatInrShort(inrVal)}
                            </td>
                            <td
                              className="mono-metric"
                              style={{ color: 'var(--emerald-primary)', fontWeight: 600 }}
                            >
                              ₹{formatInrShort(profitInr)}
                            </td>
                            <td>
                              <span
                                className={`badge ${
                                  marginPct >= 18
                                    ? 'badge-emerald'
                                    : marginPct >= 12
                                    ? 'badge-gold'
                                    : 'badge-crimson'
                                }`}
                              >
                                {marginPct}%
                              </span>
                            </td>
                            <td>
                              <span
                                className={`badge ${
                                  order.orderStatus === 'COMPLETED'
                                    ? 'badge-emerald'
                                    : order.orderStatus === 'CANCELLED'
                                    ? 'badge-crimson'
                                    : 'badge-amber'
                                }`}
                              >
                                {order.orderStatus}
                              </span>
                            </td>
                            <td>
                              <div style={{ display: 'flex', gap: '0.35rem' }}>
                                <button
                                  type="button"
                                  className="btn btn-outline btn-xs"
                                  onClick={() => navigateToOrderModule('financials', order.id)}
                                >
                                  Financials
                                </button>
                                <button
                                  type="button"
                                  className="btn btn-outline btn-xs"
                                  onClick={() => navigateToOrderModule('digital_twin', order.id)}
                                >
                                  Twin
                                </button>
                              </div>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: OPERATIONAL COMMAND CENTRE */}
          {activeTab === 'command_centre' && (
            <CommandCentreView
              pendingPayments={commandCentreData.pendingPayments}
              paymentExceptions={commandCentreData.paymentExceptions}
              incompleteDocs={commandCentreData.incompleteDocs}
              attentionOrders={commandCentreData.attentionOrders}
              riskAlerts={allRisks}
              activities={dataset.activities}
              onNavigate={navigateToOrderModule}
            />
          )}

          {/* TAB 3: EXPORT ORDERS & AI PO EXTRACTION */}
          {activeTab === 'orders' && (
            <div>
              <div style={{ marginBottom: '1.25rem' }}>
                <h1 style={{ fontSize: '1.6rem', fontWeight: 700 }}>
                  Export Order Management &amp; AI Purchase Order Extraction
                </h1>
                <p style={{ color: 'var(--text-secondary)', fontSize: '0.86rem' }}>
                  Extract structured export consignments from unstructured buyer Purchase Orders using Server-Side Gemini AI (with automatic deterministic parser fallback) and mandatory human-in-the-loop review.
                </p>
              </div>

              <div className="grid-2" style={{ marginBottom: '1.25rem' }}>
                <div className="surface-card gold-accent">
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      marginBottom: '0.75rem',
                      flexWrap: 'wrap',
                      gap: '0.5rem'
                    }}
                  >
                    <h3 style={{ fontSize: '1rem', fontWeight: 700 }}>
                      1. Unstructured Buyer PO / Email Extraction
                    </h3>
                    <div style={{ display: 'flex', gap: '0.35rem', flexWrap: 'wrap' }}>
                      {SAMPLE_EXTRACTION_TEMPLATES.map((tpl) => (
                        <button
                          key={tpl.id}
                          type="button"
                          className="btn btn-outline btn-xs"
                          onClick={() => setPoInputText(tpl.rawText)}
                        >
                          {tpl.corridor}
                        </button>
                      ))}
                    </div>
                  </div>

                  <label htmlFor="po-raw-textarea" className="field-label">
                    Paste Unstructured Purchase Order Text, Telex, or Buyer Email
                  </label>
                  <textarea
                    id="po-raw-textarea"
                    rows={7}
                    className="textarea-field mono-code"
                    value={poInputText}
                    onChange={(e) => setPoInputText(e.target.value)}
                  />

                  <div
                    style={{
                      marginTop: '0.75rem',
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      flexWrap: 'wrap',
                      gap: '0.5rem'
                    }}
                  >
                    <span style={{ fontSize: '0.74rem', color: 'var(--text-muted)' }}>
                      {serverGeminiConfigured
                        ? 'Uses Server-Side /api/ai/extract-po (Gemini)'
                        : 'Uses Deterministic Local Parser Fallback (Zero Client Secrets)'}
                    </span>
                    <button
                      type="button"
                      className="btn btn-gold"
                      disabled={poExtracting}
                      onClick={handleRunPoExtraction}
                    >
                      {poExtracting ? 'Extracting Fields...' : 'Extract PO Fields with AI'}
                    </button>
                  </div>

                  {poDraft && (
                    <div
                      style={{
                        marginTop: '1rem',
                        padding: '0.85rem',
                        borderRadius: 'var(--radius-md)',
                        backgroundColor: 'var(--bg-surface)',
                        border: '1px solid var(--border-strong)',
                        fontSize: '0.8rem'
                      }}
                    >
                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          marginBottom: '0.5rem',
                          flexWrap: 'wrap',
                          gap: '0.4rem'
                        }}
                      >
                        <strong>{poDraft.extractionEngineLabel}</strong>
                        <span className="badge badge-emerald">
                          Confidence: {poDraft.confidenceScore}%
                        </span>
                      </div>
                      <p style={{ color: 'var(--text-secondary)', marginBottom: '0.35rem' }}>
                        {poDraft.aiExtractionNotes}
                      </p>
                      {poDraft.missingFields.length > 0 && (
                        <div style={{ color: 'var(--crimson-primary)' }}>
                          <strong>Missing Fields:</strong> {poDraft.missingFields.join(', ')}
                        </div>
                      )}
                      {poDraft.uncertainFields.length > 0 && (
                        <div style={{ color: 'var(--amber-primary)' }}>
                          <strong>Uncertain Fields:</strong> {poDraft.uncertainFields.join(', ')}
                        </div>
                      )}
                    </div>
                  )}
                </div>

                <form className="surface-card" onSubmit={handleSaveConfirmedOrder}>
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      marginBottom: '0.75rem'
                    }}
                  >
                    <h3 style={{ fontSize: '1rem', fontWeight: 700 }}>
                      2. Human-in-the-Loop Order Confirmation
                    </h3>
                    <span className="badge badge-emerald">Mandatory Review</span>
                  </div>

                  <div className="grid-2" style={{ gap: '0.7rem' }}>
                    <div>
                      <label className="field-label">Order Number</label>
                      <input
                        className="input-field mono-code"
                        value={orderForm.orderNumber}
                        placeholder="e.g. ORDER-007"
                        onChange={(e) =>
                          setOrderForm({ ...orderForm, orderNumber: e.target.value })
                        }
                      />
                    </div>
                    <div>
                      <label className="field-label">Buyer Organization *</label>
                      <input
                        className="input-field"
                        required
                        value={orderForm.buyerName}
                        onChange={(e) =>
                          setOrderForm({ ...orderForm, buyerName: e.target.value })
                        }
                      />
                    </div>
                    <div>
                      <label className="field-label">Destination Country</label>
                      <input
                        className="input-field"
                        value={orderForm.buyerCountry}
                        onChange={(e) =>
                          setOrderForm({ ...orderForm, buyerCountry: e.target.value })
                        }
                      />
                    </div>
                    <div>
                      <label className="field-label">Destination Port</label>
                      <input
                        className="input-field"
                        value={orderForm.destinationPort}
                        onChange={(e) =>
                          setOrderForm({ ...orderForm, destinationPort: e.target.value })
                        }
                      />
                    </div>
                    <div style={{ gridColumn: 'span 2' }}>
                      <label className="field-label">Product Name &amp; HS Code *</label>
                      <input
                        className="input-field"
                        required
                        value={orderForm.productName}
                        onChange={(e) =>
                          setOrderForm({ ...orderForm, productName: e.target.value })
                        }
                      />
                    </div>
                    <div>
                      <label className="field-label">Quantity *</label>
                      <input
                        type="number"
                        className="input-field mono-metric"
                        required
                        min={1}
                        value={orderForm.quantity}
                        onChange={(e) =>
                          setOrderForm({ ...orderForm, quantity: Number(e.target.value) })
                        }
                      />
                    </div>
                    <div>
                      <label className="field-label">Unit Price (Foreign) *</label>
                      <input
                        type="number"
                        step="0.01"
                        className="input-field mono-metric"
                        required
                        min={0.01}
                        value={orderForm.unitPrice}
                        onChange={(e) =>
                          setOrderForm({ ...orderForm, unitPrice: Number(e.target.value) })
                        }
                      />
                    </div>
                    <div>
                      <label className="field-label">Currency</label>
                      <select
                        className="select-field"
                        value={orderForm.currency}
                        onChange={(e) =>
                          setOrderForm({ ...orderForm, currency: e.target.value })
                        }
                      >
                        {['EUR', 'USD', 'AED', 'GBP', 'JPY'].map((c) => (
                          <option key={c} value={c}>
                            {c}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="field-label">Incoterm</label>
                      <input
                        className="input-field"
                        value={orderForm.incoterm}
                        onChange={(e) =>
                          setOrderForm({ ...orderForm, incoterm: e.target.value })
                        }
                      />
                    </div>
                    <div>
                      <label className="field-label">Expected Shipment Date</label>
                      <input
                        type="date"
                        className="input-field"
                        value={orderForm.expectedShipmentDate}
                        onChange={(e) =>
                          setOrderForm({
                            ...orderForm,
                            expectedShipmentDate: e.target.value
                          })
                        }
                      />
                    </div>
                    <div>
                      <label className="field-label">Advance Percentage (%)</label>
                      <input
                        type="number"
                        className="input-field mono-metric"
                        min={0}
                        max={100}
                        value={orderForm.advancePercentage}
                        onChange={(e) =>
                          setOrderForm({
                            ...orderForm,
                            advancePercentage: Number(e.target.value)
                          })
                        }
                      />
                    </div>
                  </div>

                  <div
                    style={{
                      marginTop: '0.9rem',
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center'
                    }}
                  >
                    <div className="mono-metric" style={{ fontSize: '0.82rem' }}>
                      Contract Value:{' '}
                      <strong>
                        {orderForm.currency}{' '}
                        {calculateGrossOrderValue(
                          orderForm.quantity,
                          orderForm.unitPrice
                        ).toLocaleString()}
                      </strong>
                    </div>
                    <button type="submit" className="btn btn-primary">
                      Confirm &amp; Save Export Order
                    </button>
                  </div>
                </form>
              </div>

              <div className="surface-card">
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    marginBottom: '0.9rem',
                    flexWrap: 'wrap',
                    gap: '0.75rem'
                  }}
                >
                  <h3 style={{ fontSize: '1rem', fontWeight: 700 }}>
                    Export Order Registry ({filteredOrders.length} of {dataset.orders.length})
                  </h3>
                  <div style={{ display: 'flex', gap: '0.6rem', flexWrap: 'wrap' }}>
                    <input
                      className="input-field"
                      style={{ width: '240px' }}
                      placeholder="Search ID, buyer, country, port..."
                      value={orderSearch}
                      onChange={(e) => setOrderSearch(e.target.value)}
                    />
                    <select
                      className="select-field"
                      style={{ width: '185px' }}
                      value={orderStatusFilter}
                      onChange={(e) => setOrderStatusFilter(e.target.value)}
                    >
                      <option value="ALL">All Statuses</option>
                      {ORDER_STATUSES.map((s) => (
                        <option key={s} value={s}>
                          {s}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                <div className="table-scroll">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Order ID</th>
                        <th>Buyer / Country</th>
                        <th>Product</th>
                        <th>Contract Value</th>
                        <th>Shipment Date</th>
                        <th>Workflow Status</th>
                        <th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredOrders.map((order) => (
                        <tr key={order.id}>
                          <td className="mono-code" style={{ fontWeight: 700 }}>
                            {order.orderNumber}
                          </td>
                          <td>
                            <div style={{ fontWeight: 600 }}>{order.buyerName}</div>
                            <div style={{ fontSize: '0.74rem', color: 'var(--text-muted)' }}>
                              {order.buyerCountry} • {order.destinationPort}
                            </div>
                          </td>
                          <td>
                            <div>{order.productName}</div>
                            <div
                              className="mono-code"
                              style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}
                            >
                              {order.quantity.toLocaleString()} {order.unit} × {order.currency}{' '}
                              {order.unitPrice}
                            </div>
                          </td>
                          <td className="mono-metric" style={{ fontWeight: 600 }}>
                            {order.currency} {order.orderValue.toLocaleString()}
                          </td>
                          <td className="mono-code">{order.expectedShipmentDate}</td>
                          <td>
                            <select
                              aria-label={`Status for ${order.orderNumber}`}
                              className="select-field"
                              style={{
                                padding: '0.28rem 0.5rem',
                                fontSize: '0.75rem',
                                width: '165px'
                              }}
                              value={order.orderStatus}
                              onChange={(e) =>
                                handleUpdateOrderStatus(
                                  order.id,
                                  e.target.value as ExportOrder['orderStatus']
                                )
                              }
                            >
                              {ORDER_STATUSES.map((st) => (
                                <option key={st} value={st}>
                                  {st}
                                </option>
                              ))}
                            </select>
                          </td>
                          <td>
                            <div style={{ display: 'flex', gap: '0.35rem' }}>
                              <button
                                type="button"
                                className="btn btn-outline btn-xs"
                                onClick={() => navigateToOrderModule('digital_twin', order.id)}
                              >
                                Twin
                              </button>
                              <button
                                type="button"
                                className="btn btn-danger btn-xs"
                                onClick={() => handleDeleteOrder(order.id)}
                              >
                                Delete
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {/* TAB 4: SHIPMENT INTELLIGENCE */}
          {activeTab === 'shipment_intel' && (
            <ShipmentIntelligenceView
              items={shipmentIntelItems}
              onNavigate={navigateToOrderModule}
            />
          )}

          {/* TAB 5: 8-STAGE DIGITAL TWIN */}
          {activeTab === 'digital_twin' && selectedOrder && selectedTwin && (
            <div>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  marginBottom: '1.25rem',
                  flexWrap: 'wrap',
                  gap: '0.75rem'
                }}
              >
                <div>
                  <h1 style={{ fontSize: '1.6rem', fontWeight: 700 }}>
                    8-Stage Export Order Digital Twin — {selectedOrder.orderNumber}
                  </h1>
                  <p style={{ color: 'var(--text-secondary)', fontSize: '0.86rem' }}>
                    {selectedOrder.buyerName} ({selectedOrder.buyerCountry}) •{' '}
                    {selectedOrder.productName} • {selectedOrder.currency}{' '}
                    {selectedOrder.orderValue.toLocaleString()}
                  </p>
                </div>
                <span className="badge badge-gold">
                  Progress: {selectedTwin.overallProgressPercent}% ({selectedTwin.currentStageLabel})
                </span>
              </div>

              <div className="grid-4">
                {selectedTwin.stages.map((stage) => (
                  <div
                    key={stage.stageNumber}
                    className={`surface-card ${
                      stage.status === 'ACTION_REQUIRED'
                        ? 'crimson-accent'
                        : stage.status === 'COMPLETED'
                        ? 'emerald-accent'
                        : stage.status === 'IN_PROGRESS'
                        ? 'gold-accent'
                        : ''
                    }`}
                  >
                    <div
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        marginBottom: '0.5rem'
                      }}
                    >
                      <span className="mono-code" style={{ fontWeight: 700 }}>
                        STAGE 0{stage.stageNumber}
                      </span>
                      <span
                        className={`badge ${
                          stage.status === 'COMPLETED'
                            ? 'badge-emerald'
                            : stage.status === 'ACTION_REQUIRED'
                            ? 'badge-crimson'
                            : stage.status === 'IN_PROGRESS'
                            ? 'badge-gold'
                            : 'badge-neutral'
                        }`}
                      >
                        {stage.status}
                      </span>
                    </div>
                    <h3 style={{ fontSize: '0.96rem', fontWeight: 700, marginBottom: '0.25rem' }}>
                      {stage.title}
                    </h3>
                    <p
                      style={{
                        fontSize: '0.77rem',
                        color: 'var(--text-secondary)',
                        marginBottom: '0.45rem'
                      }}
                    >
                      {stage.subtitle}
                    </p>
                    <div
                      style={{
                        padding: '0.45rem 0.6rem',
                        borderRadius: 'var(--radius-sm)',
                        backgroundColor: 'var(--bg-subtle)',
                        fontSize: '0.73rem'
                      }}
                    >
                      {stage.detailText}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* TAB 6: FINANCIAL INTELLIGENCE & WHAT-IF SIMULATOR */}
          {activeTab === 'financials' &&
            selectedOrder &&
            selectedAssessment &&
            whatIfAssessment && (
              <div>
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    marginBottom: '1.25rem',
                    flexWrap: 'wrap',
                    gap: '0.75rem'
                  }}
                >
                  <div>
                    <h1 style={{ fontSize: '1.6rem', fontWeight: 700 }}>
                      Financial Intelligence &amp; What-If Simulator ({selectedOrder.orderNumber})
                    </h1>
                    <p style={{ color: 'var(--text-secondary)', fontSize: '0.86rem' }}>
                      Deterministic INR conversion, gateway fee deduction, operating cost breakdown, and isolated stress-testing.
                    </p>
                  </div>
                  <button
                    type="button"
                    className="btn btn-outline btn-xs"
                    onClick={() => setShowFormulaAudit((prev) => !prev)}
                  >
                    {showFormulaAudit
                      ? 'Hide Formula Audit Trail'
                      : 'Show 8-Step Formula Audit Trail'}
                  </button>
                </div>

                <div className="grid-4" style={{ marginBottom: '1.25rem' }}>
                  <div className="kpi-card">
                    <div className="kpi-label">Converted Order Value (INR)</div>
                    <div className="kpi-value">
                      ₹{formatInrShort(selectedAssessment.convertedValueInr)}
                    </div>
                    <div className="kpi-sub">
                      {selectedOrder.currency} {selectedAssessment.grossOrderValue.toLocaleString()}{' '}
                      @ ₹{selectedAssessment.exchangeRate}
                    </div>
                  </div>
                  <div className="kpi-card">
                    <div className="kpi-label">Estimated Net Revenue</div>
                    <div className="kpi-value">
                      ₹{formatInrShort(selectedAssessment.estimatedNetRevenueInr)}
                    </div>
                    <div className="kpi-sub">
                      Gateway Fee: ₹{formatInrShort(selectedAssessment.paymentFeeInr)} (
                      {selectedAssessment.paymentFeePercentage}%)
                    </div>
                  </div>
                  <div className="kpi-card">
                    <div className="kpi-label">Deterministic Net Profit</div>
                    <div className="kpi-value" style={{ color: 'var(--emerald-primary)' }}>
                      ₹{formatInrShort(selectedAssessment.estimatedProfitInr)}
                    </div>
                    <div className="kpi-sub">
                      Margin: {selectedAssessment.profitMarginPercent}%
                    </div>
                  </div>
                  <div className="kpi-card">
                    <div className="kpi-label">Simulated What-If Margin</div>
                    <div
                      className="kpi-value"
                      style={{
                        color:
                          whatIfAssessment.profitMarginPercent >= 12
                            ? 'var(--emerald-primary)'
                            : 'var(--crimson-primary)'
                      }}
                    >
                      {whatIfAssessment.profitMarginPercent}%
                    </div>
                    <div className="kpi-sub">
                      Simulated Profit: ₹{formatInrShort(whatIfAssessment.estimatedProfitInr)}
                    </div>
                  </div>
                </div>

                {showFormulaAudit && (
                  <div className="surface-card gold-accent" style={{ marginBottom: '1.25rem' }}>
                    <h3 style={{ fontSize: '0.96rem', fontWeight: 700, marginBottom: '0.6rem' }}>
                      Deterministic 8-Step Formula Audit Trail ({selectedAssessment.id})
                    </h3>
                    <div
                      style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem' }}
                      className="mono-code"
                    >
                      <div>
                        1. Gross Order Value = {selectedOrder.quantity} × {selectedOrder.unitPrice}{' '}
                        = {selectedOrder.currency} {selectedAssessment.grossOrderValue}
                      </div>
                      <div>
                        2. Converted INR Value = {selectedAssessment.grossOrderValue} × ₹
                        {selectedAssessment.exchangeRate} = ₹{selectedAssessment.convertedValueInr}
                      </div>
                      <div>
                        3. Advance ({selectedAssessment.advancePercentage}%) ={' '}
                        {selectedOrder.currency} {selectedAssessment.advanceAmount} | Balance ={' '}
                        {selectedOrder.currency} {selectedAssessment.outstandingAmount}
                      </div>
                      <div>
                        4. Gateway Fee ({selectedAssessment.paymentFeePercentage}% +{' '}
                        {selectedAssessment.paymentFixedFee}) = ₹{selectedAssessment.paymentFeeInr}
                      </div>
                      <div>
                        5. Estimated Net Revenue = ₹{selectedAssessment.convertedValueInr} - ₹
                        {selectedAssessment.paymentFeeInr} = ₹
                        {selectedAssessment.estimatedNetRevenueInr}
                      </div>
                      <div>
                        6. Operating Costs = Procurement ₹{selectedAssessment.procurementCostInr} +
                        Logistics ₹{selectedAssessment.logisticsCostInr} + Packaging ₹
                        {selectedAssessment.packagingCostInr} + Other ₹
                        {selectedAssessment.otherCostsInr}
                      </div>
                      <div>
                        7. Estimated Net Profit = ₹{selectedAssessment.estimatedProfitInr}
                      </div>
                      <div>
                        8. Net Profit Margin = ({selectedAssessment.estimatedProfitInr} /{' '}
                        {selectedAssessment.convertedValueInr}) × 100 ={' '}
                        {selectedAssessment.profitMarginPercent}%
                      </div>
                    </div>
                  </div>
                )}

                <div className="grid-2">
                  <div className="surface-card">
                    <h3 style={{ fontSize: '0.98rem', fontWeight: 700, marginBottom: '0.75rem' }}>
                      Operating Cost &amp; FX Parameters (Authoritative Ledger)
                    </h3>
                    <div className="grid-2" style={{ gap: '0.65rem' }}>
                      {(
                        [
                          ['exchangeRate', 'Exchange Rate (to INR)'],
                          ['paymentFeePercentage', 'Gateway Fee (%)'],
                          ['procurementCostInr', 'Procurement Cost (INR)'],
                          ['logisticsCostInr', 'Ocean / Air Logistics (INR)'],
                          ['packagingCostInr', 'Export Packaging (INR)'],
                          ['otherCostsInr', 'Compliance & Other Costs (INR)']
                        ] as const
                      ).map(([key, label]) => (
                        <div key={key}>
                          <label className="field-label">{label}</label>
                          <input
                            type="number"
                            step="0.01"
                            className="input-field mono-metric"
                            value={selectedAssessment[key]}
                            onChange={(e) =>
                              handleUpdateFinancialField(key, Number(e.target.value))
                            }
                          />
                        </div>
                      ))}
                    </div>
                    <p
                      style={{
                        fontSize: '0.74rem',
                        color: 'var(--text-muted)',
                        marginTop: '0.75rem'
                      }}
                    >
                      Assumptions Note: {selectedAssessment.assumptionsNote}
                    </p>
                  </div>

                  <div className="surface-card">
                    <div
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        marginBottom: '0.75rem'
                      }}
                    >
                      <h3 style={{ fontSize: '0.98rem', fontWeight: 700 }}>
                        Isolated What-If Stress Simulator
                      </h3>
                      <button
                        type="button"
                        className="btn btn-outline btn-xs"
                        onClick={() => {
                          setWhatIfFxDeltaPct(0);
                          setWhatIfFreightDeltaPct(0);
                          setWhatIfProcurementDeltaPct(0);
                        }}
                      >
                        Reset Sliders
                      </button>
                    </div>
                    <p
                      style={{
                        fontSize: '0.76rem',
                        color: 'var(--text-secondary)',
                        marginBottom: '0.9rem'
                      }}
                    >
                      Scenario simulations are strictly isolated and never mutate your saved order or financial assessment records.
                    </p>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.9rem' }}>
                      <div>
                        <div
                          style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            fontSize: '0.8rem',
                            fontWeight: 600
                          }}
                        >
                          <span>FX Spot Rate Shock (%)</span>
                          <span className="mono-metric">{whatIfFxDeltaPct}%</span>
                        </div>
                        <input
                          type="range"
                          min={-15}
                          max={15}
                          step={0.5}
                          style={{ width: '100%' }}
                          value={whatIfFxDeltaPct}
                          onChange={(e) => setWhatIfFxDeltaPct(Number(e.target.value))}
                        />
                      </div>

                      <div>
                        <div
                          style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            fontSize: '0.8rem',
                            fontWeight: 600
                          }}
                        >
                          <span>Ocean / Air Freight Surge (%)</span>
                          <span className="mono-metric">{whatIfFreightDeltaPct}%</span>
                        </div>
                        <input
                          type="range"
                          min={-20}
                          max={100}
                          step={5}
                          style={{ width: '100%' }}
                          value={whatIfFreightDeltaPct}
                          onChange={(e) => setWhatIfFreightDeltaPct(Number(e.target.value))}
                        />
                      </div>

                      <div>
                        <div
                          style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            fontSize: '0.8rem',
                            fontWeight: 600
                          }}
                        >
                          <span>Procurement Cost Delta (%)</span>
                          <span className="mono-metric">{whatIfProcurementDeltaPct}%</span>
                        </div>
                        <input
                          type="range"
                          min={-15}
                          max={30}
                          step={1}
                          style={{ width: '100%' }}
                          value={whatIfProcurementDeltaPct}
                          onChange={(e) => setWhatIfProcurementDeltaPct(Number(e.target.value))}
                        />
                      </div>

                      {selectedViabilityAdvisory && (
                        <div
                          style={{
                            padding: '0.8rem',
                            borderRadius: 'var(--radius-sm)',
                            border: '1px solid var(--gold-border)',
                            backgroundColor: 'var(--gold-subtle)',
                            fontSize: '0.78rem'
                          }}
                        >
                          <strong>
                            {selectedViabilityAdvisory.modeLabel} (
                            {selectedViabilityAdvisory.riskRating}):
                          </strong>
                          <p style={{ marginTop: '0.25rem', color: 'var(--text-secondary)' }}>
                            {selectedViabilityAdvisory.headline}
                          </p>
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            )}

          {/* TAB 7: RECEIVABLES & CASH FLOW INTELLIGENCE */}
          {activeTab === 'receivables' && (
            <ReceivablesIntelligenceView analytics={receivablesAnalytics} />
          )}

          {/* TAB 8: BUYER INTELLIGENCE */}
          {activeTab === 'buyers' && <BuyerIntelligenceView buyers={buyerAnalytics} />}

          {/* TAB 9: DOCUMENT CENTRE & SHA-256 VERIFICATION */}
          {activeTab === 'documents' && selectedOrder && (
            <div>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  marginBottom: '1rem',
                  flexWrap: 'wrap',
                  gap: '0.75rem'
                }}
              >
                <div>
                  <h1 style={{ fontSize: '1.6rem', fontWeight: 700 }}>
                    Document Centre &amp; SHA-256 Cryptographic Verification (
                    {selectedOrder.orderNumber})
                  </h1>
                  <p style={{ color: 'var(--text-secondary)', fontSize: '0.86rem' }}>
                    Upload PDF/PNG/JPG compliance files (max 15MB) to compute real Web Crypto API SHA-256 checksums.
                  </p>
                </div>
                <select
                  className="select-field"
                  style={{ width: '190px' }}
                  value={docStatusFilter}
                  onChange={(e) => setDocStatusFilter(e.target.value)}
                >
                  <option value="ALL">All Document Statuses</option>
                  {DOC_STATUSES_DEMO.map((st) => (
                    <option key={st} value={st}>
                      {st}
                    </option>
                  ))}
                </select>
              </div>

              <div className="governance-banner simulated">
                <div>
                  <strong>LOCAL BROWSER STORAGE &amp; CRYPTOGRAPHIC INTEGRITY:</strong> Uploaded files are hashed in-browser using <code>crypto.subtle.digest(&apos;SHA-256&apos;)</code> and metadata is stored in browser <code>localStorage</code>.{' '}
                  {isDemoMode
                    ? 'Sample mode allows testing full verification states.'
                    : 'Production mode enforces dual-control: self-certification to VERIFIED / OFFICIALLY_CERTIFIED is disabled.'}
                </div>
                <span className="badge badge-emerald">Web Crypto SHA-256</span>
              </div>

              <div className="grid-2">
                {selectedOrderDocs
                  .filter((d) => docStatusFilter === 'ALL' || d.status === docStatusFilter)
                  .map((doc) => (
                    <div key={doc.id} className="surface-card">
                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'flex-start',
                          marginBottom: '0.5rem'
                        }}
                      >
                        <div>
                          <h3 style={{ fontSize: '0.96rem', fontWeight: 700 }}>
                            {doc.documentType}
                          </h3>
                          <div
                            className="mono-code"
                            style={{ color: 'var(--text-muted)', fontSize: '0.72rem' }}
                          >
                            ID: {doc.id} • {doc.requirementLevel} • v{doc.versionNumber}
                          </div>
                        </div>
                        <span
                          className={`badge ${
                            isDocumentVerifiedOrApproved(doc.status)
                              ? 'badge-emerald'
                              : doc.status === 'MISSING' ||
                                doc.status === 'REJECTED' ||
                                doc.status === 'REUPLOAD_REQUIRED'
                              ? 'badge-crimson'
                              : 'badge-amber'
                          }`}
                        >
                          {doc.status}
                        </span>
                      </div>

                      <p
                        style={{
                          fontSize: '0.78rem',
                          color: 'var(--text-secondary)',
                          marginBottom: '0.6rem'
                        }}
                      >
                        {doc.reviewerNote}
                      </p>

                      {doc.sha256Checksum && (
                        <div
                          className="mono-code"
                          style={{
                            padding: '0.45rem 0.6rem',
                            backgroundColor: 'var(--bg-subtle)',
                            borderRadius: 'var(--radius-sm)',
                            fontSize: '0.69rem',
                            wordBreak: 'break-all',
                            marginBottom: '0.65rem'
                          }}
                        >
                          <strong>SHA-256:</strong> {doc.sha256Checksum}
                        </div>
                      )}

                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          gap: '0.6rem',
                          flexWrap: 'wrap'
                        }}
                      >
                        <label className="btn btn-outline btn-xs" style={{ cursor: 'pointer' }}>
                          {uploadingDocId === doc.id
                            ? 'Computing SHA-256...'
                            : 'Upload PDF/Image (<=15MB)'}
                          <input
                            type="file"
                            accept=".pdf,.png,.jpg,.jpeg"
                            style={{ display: 'none' }}
                            onChange={(e) => handleDocumentFileUpload(doc, e.target.files?.[0])}
                          />
                        </label>

                        <select
                          aria-label={`Document status for ${doc.documentType}`}
                          className="select-field"
                          style={{
                            width: '175px',
                            padding: '0.28rem 0.5rem',
                            fontSize: '0.75rem'
                          }}
                          value={doc.status}
                          onChange={(e) =>
                            handleUpdateDocStatus(doc.id, e.target.value as DocumentStatus)
                          }
                        >
                          {(isDemoMode ? DOC_STATUSES_DEMO : DOC_STATUSES_PRODUCTION).map((st) => (
                            <option key={st} value={st}>
                              {st}
                            </option>
                          ))}
                        </select>
                      </div>
                    </div>
                  ))}
              </div>
            </div>
          )}

          {/* TAB 10: DUAL-MODE PAYMENT HUB (DEMO + GENUINE PAYPAL SANDBOX) */}
          {activeTab === 'payments' && selectedOrder && (
            <PaymentHubDualModeView
              selectedOrder={selectedOrder}
              workspaceMode={workspaceMode}
              demoPayments={dataset.payments}
              newPaymentType={newPaymentType}
              setNewPaymentType={setNewPaymentType}
              newPaymentAmountForeign={newPaymentAmountForeign}
              setNewPaymentAmountForeign={setNewPaymentAmountForeign}
              onCreateDemoPayment={handleCreateDemoPayment}
              onTransitionDemoPayment={handleTransitionDemoPayment}
              paypalConfig={paypalConfig}
              paypalTransactions={paypalTransactions}
              paypalBusyOrderId={paypalBusyOrderId}
              paypalErrorBanner={paypalErrorBanner}
              onRefreshPayPalConfig={() => refreshPayPalSandboxState(workspaceMode)}
              onCreatePayPalOrder={handleCreatePayPalSandboxOrder}
              onCheckPayPalOrderStatus={handleCheckPayPalSandboxStatus}
              onCapturePayPalOrder={handleCapturePayPalSandboxOrder}
              onCancelPayPalOrder={handleCancelPayPalSandboxOrder}
            />
          )}

          {/* TAB 11: TRADE RISK & AUTONOMOUS AI ADVISOR */}
          {activeTab === 'ai_risk' && (
            <AiRiskAndAdvisorView
              nlQueryInput={nlQueryInput}
              setNlQueryInput={setNlQueryInput}
              nlQueryLoading={nlQueryLoading}
              onExecuteNlQuery={handleExecuteNlQuery}
              nlQueryResult={nlQueryResult}
              advisorLoading={advisorLoading}
              onRunPortfolioAdvisory={handleRunPortfolioAdvisory}
              executiveSummary={activeAdvisor.executiveSummary}
              advisorEngineLabel={activeAdvisor.engineLabel}
              structuredRecommendations={activeAdvisor.recommendations}
              onNavigate={navigateToOrderModule}
            />
          )}

          {/* TAB 12: REPORTING & EXPORTS */}
          {activeTab === 'reports' && (
            <ReportingCenterView
              workspaceMode={workspaceMode}
              prodUserEmail={prodUserEmail}
              orders={dataset.orders}
              assessments={dataset.assessments}
              documents={dataset.documents}
              payments={dataset.payments}
              risks={allRisks}
              activities={dataset.activities}
              onToast={triggerToast}
            />
          )}

          {/* TAB 13: WORKSPACE, BRAND IDENTITY & SECURITY GOVERNANCE */}
          {activeTab === 'workspace' && (
            <div>
              <div style={{ marginBottom: '1.25rem' }}>
                <h1 style={{ fontSize: '1.6rem', fontWeight: 700 }}>
                  Workspace Separation, Brand Identity &amp; Security Architecture
                </h1>
                <p style={{ color: 'var(--text-secondary)', fontSize: '0.86rem' }}>
                  {BRAND_TAGLINE} — Strict separation between Sample Business Workspace and Production Workspace, server-side secret isolation, custom geometric SVG brand system, and immutable audit logging.
                </p>
              </div>

              <div className="grid-2" style={{ marginBottom: '1.25rem' }}>
                <div className="surface-card">
                  <h3 style={{ fontSize: '0.98rem', fontWeight: 700, marginBottom: '0.65rem' }}>
                    Exporter Corporate Profile (Authoritative Record)
                  </h3>
                  <div
                    style={{
                      fontSize: '0.83rem',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '0.4rem'
                    }}
                  >
                    <div>
                      <strong>Company Name:</strong> {SAMPLE_WORKSPACE_PROFILE.companyName}
                    </div>
                    <div>
                      <strong>DGFT IEC Code:</strong>{' '}
                      <span className="mono-code">{SAMPLE_WORKSPACE_PROFILE.iecNumber}</span>
                    </div>
                    <div>
                      <strong>GSTIN:</strong>{' '}
                      <span className="mono-code">{SAMPLE_WORKSPACE_PROFILE.gstin}</span>
                    </div>
                    <div>
                      <strong>Authorized Dealer (AD) Code:</strong>{' '}
                      <span className="mono-code">{SAMPLE_WORKSPACE_PROFILE.adCode}</span>
                    </div>
                    <div>
                      <strong>Headquarters:</strong> {SAMPLE_WORKSPACE_PROFILE.headquarters}
                    </div>
                    <div>
                      <strong>Primary Settlement Bank:</strong>{' '}
                      {SAMPLE_WORKSPACE_PROFILE.primaryBank}
                    </div>
                  </div>
                </div>

                <div className="surface-card">
                  <h3 style={{ fontSize: '0.98rem', fontWeight: 700, marginBottom: '0.65rem' }}>
                    Security &amp; Live vs Simulated Capability Matrix
                  </h3>
                  <div
                    style={{
                      fontSize: '0.8rem',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '0.45rem'
                    }}
                  >
                    <div>
                      <span className="badge badge-emerald" style={{ marginRight: '0.4rem' }}>
                        SERVER-SIDE
                      </span>
                      <strong>PayPal Orders v2 Proxy (/api/paypal/*):</strong> OAuth2 access tokens and Client Secret stay strictly in server memory; zero browser exposure.
                    </div>
                    <div>
                      <span className="badge badge-emerald" style={{ marginRight: '0.4rem' }}>
                        SERVER-SIDE
                      </span>
                      <strong>Gemini AI Proxy (/api/ai/*):</strong> API key stays strictly in server memory; zero client key exposure.
                    </div>
                    <div>
                      <span className="badge badge-emerald" style={{ marginRight: '0.4rem' }}>
                        REAL ENGINE
                      </span>
                      <strong>Financial &amp; Risk Engine:</strong> 100% deterministic INR conversion, gateway fee, operating cost, and What-If math.
                    </div>
                    <div>
                      <span className="badge badge-emerald" style={{ marginRight: '0.4rem' }}>
                        WEB CRYPTO
                      </span>
                      <strong>SHA-256 Document Verification:</strong> Real cryptographic digest computed locally via browser Web Crypto API.
                    </div>
                    <div>
                      <span className="badge badge-amber" style={{ marginRight: '0.4rem' }}>
                        SIMULATED
                      </span>
                      <strong>DemoPaymentService:</strong> Explicit sandbox state machine for instant offline testing alongside genuine PayPal Sandbox mode.
                    </div>
                  </div>
                </div>
              </div>

              {/* Part 5.3 — All 7 Required Brand Logo System Variants */}
              <div className="surface-card" style={{ marginBottom: '1.25rem' }}>
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    marginBottom: '0.85rem',
                    flexWrap: 'wrap',
                    gap: '0.5rem'
                  }}
                >
                  <div>
                    <h3 style={{ fontSize: '0.98rem', fontWeight: 700 }}>
                      NiryatFlow AI Geometric SVG Brand Identity System (All 7 Variants)
                    </h3>
                    <p style={{ fontSize: '0.76rem', color: 'var(--text-secondary)' }}>
                      Tagline: <strong>{BRAND_TAGLINE}</strong> • Abstract letter &lsquo;N&rsquo; trade route path + forward progress vector + #D2AE6D champagne gold financial signal node.
                    </p>
                  </div>
                  <span className="badge badge-gold">Scalable Inline SVG</span>
                </div>

                <div className="grid-4">
                  <div
                    style={{
                      padding: '0.85rem',
                      borderRadius: 'var(--radius-sm)',
                      border: '1px solid var(--border-subtle)',
                      backgroundColor: '#FFFFFF'
                    }}
                  >
                    <div className="kpi-label" style={{ marginBottom: '0.45rem' }}>
                      1. Primary Horizontal &amp; 6. Light Surface
                    </div>
                    <BrandLogo variant="light" size={38} showTagline />
                  </div>

                  <div
                    style={{
                      padding: '0.85rem',
                      borderRadius: 'var(--radius-sm)',
                      border: '1px solid var(--primary-teal)',
                      backgroundColor: 'var(--primary-teal)'
                    }}
                  >
                    <div
                      className="kpi-label"
                      style={{ marginBottom: '0.45rem', color: 'rgba(245,242,234,0.7)' }}
                    >
                      3. Sidebar &amp; 7. Dark Surface
                    </div>
                    <BrandLogo variant="dark" size={38} showTagline />
                  </div>

                  <div
                    style={{
                      padding: '0.85rem',
                      borderRadius: 'var(--radius-sm)',
                      border: '1px solid var(--border-subtle)',
                      backgroundColor: 'var(--bg-elevated)',
                      display: 'flex',
                      flexDirection: 'column',
                      justifyContent: 'space-between'
                    }}
                  >
                    <div className="kpi-label" style={{ marginBottom: '0.45rem' }}>
                      2. Compact Square &amp; 4. Favicon SVG
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.85rem' }}>
                      <BrandLogo variant="square" size={40} />
                      <BrandLogo variant="square" size={24} />
                      <span className="mono-code" style={{ fontSize: '0.72rem', color: 'var(--text-secondary)' }}>
                        40×40 &amp; 24×24 crisp scaling
                      </span>
                    </div>
                  </div>

                  <div
                    style={{
                      padding: '0.85rem',
                      borderRadius: 'var(--radius-sm)',
                      border: '1px solid var(--border-subtle)',
                      backgroundColor: '#FFFFFF'
                    }}
                  >
                    <div className="kpi-label" style={{ marginBottom: '0.45rem' }}>
                      5. Monochrome Institutional Mark
                    </div>
                    <BrandLogo variant="monochrome" size={38} showTagline />
                  </div>
                </div>
              </div>

              <div className="surface-card">
                <h3 style={{ fontSize: '0.98rem', fontWeight: 700, marginBottom: '0.75rem' }}>
                  Immutable Activity &amp; Audit Log ({dataset.activities.length} Events)
                </h3>
                <div className="table-scroll">
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Event ID</th>
                        <th>Order ID</th>
                        <th>Event Type</th>
                        <th>Title</th>
                        <th>Description</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dataset.activities.map((act) => (
                        <tr key={act.id}>
                          <td className="mono-code">{act.id}</td>
                          <td className="mono-code">{act.exportOrderId}</td>
                          <td>
                            <span className="badge badge-gold">{act.eventType}</span>
                          </td>
                          <td style={{ fontWeight: 600 }}>{act.title}</td>
                          <td>{act.description}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}

export default App;
