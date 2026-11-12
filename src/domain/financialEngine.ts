import {
  ActivityRecord,
  BuyerAnalyticsItem,
  CurrencyExposureSummary,
  DigitalTwinState,
  DocumentRecord,
  ExportOrder,
  FinancialAssessment,
  LifecycleStageNode,
  LifecycleStageStatus,
  OrderReceivableBreakdown,
  OutstandingAction,
  PaymentRecord,
  PerformanceMetrics,
  PeriodDashboardAnalytics,
  ShipmentIntelligenceItem,
  TimePeriodCode,
  TradeRiskAlert
} from '../types/models';

export const STANDARD_DOCUMENT_TYPES: Array<{ type: string; level: 'REQUIRED' | 'OPTIONAL' }> = [
  { type: 'Commercial Invoice', level: 'REQUIRED' },
  { type: 'Packing List', level: 'REQUIRED' },
  { type: 'Purchase Order / Contract', level: 'REQUIRED' },
  { type: 'Shipping Bill', level: 'REQUIRED' },
  { type: 'Bill of Lading / Airway Bill', level: 'REQUIRED' },
  { type: 'Certificate of Origin', level: 'REQUIRED' },
  { type: 'Insurance Certificate', level: 'OPTIONAL' },
  { type: 'Payment Receipt / Confirmation', level: 'REQUIRED' }
];

export const TIME_PERIOD_DEFINITIONS: Record<
  TimePeriodCode,
  { code: TimePeriodCode; label: string; windowMillis: number | null }
> = {
  '1D': { code: '1D', label: 'Last 24 Hours', windowMillis: 24 * 60 * 60 * 1000 },
  '1W': { code: '1W', label: 'Last 7 Days', windowMillis: 7 * 24 * 60 * 60 * 1000 },
  '1M': { code: '1M', label: 'Last 30 Days', windowMillis: 30 * 24 * 60 * 60 * 1000 },
  '3M': { code: '3M', label: 'Last 90 Days', windowMillis: 90 * 24 * 60 * 60 * 1000 },
  '1Y': { code: '1Y', label: 'Last 12 Months', windowMillis: 365 * 24 * 60 * 60 * 1000 },
  ALL: { code: 'ALL', label: 'All-Time Portfolio', windowMillis: null }
};

export function round2(value: number): number {
  return Math.round(value * 100) / 100;
}

export function defaultExchangeRateForCurrency(currency: string): number {
  switch ((currency || 'USD').toUpperCase()) {
    case 'USD':
      return 84.2;
    case 'EUR':
      return 91.5;
    case 'GBP':
      return 108.4;
    case 'AED':
      return 22.92;
    case 'JPY':
      return 0.56;
    case 'INR':
      return 1.0;
    default:
      return 84.2;
  }
}

export function calculateGrossOrderValue(quantity: number, unitPrice: number): number {
  return round2(Math.max(0, quantity) * Math.max(0, unitPrice));
}

export function calculateAssessment(params: {
  userId: string;
  exportOrderId: string;
  existingId?: string;
  quantity: number;
  unitPrice: number;
  currency: string;
  exchangeRate: number;
  advancePercentage: number;
  paymentFeePercentage: number;
  paymentFixedFee: number;
  procurementCostInr: number;
  logisticsCostInr: number;
  packagingCostInr: number;
  otherCostsInr: number;
  assumptionsNote?: string;
}): FinancialAssessment {
  const safeQty = Math.max(0, params.quantity);
  const safeUnitPrice = Math.max(0, params.unitPrice);
  const safeRate = Math.max(0.01, params.exchangeRate);
  const safeAdvancePct = Math.min(100, Math.max(0, params.advancePercentage));
  const safeFeePct = Math.min(35, Math.max(0, params.paymentFeePercentage));
  const safeFixedFee = Math.max(0, params.paymentFixedFee);

  // 1. Gross Order Value = Quantity * Unit Price
  const grossOrderValue = round2(safeQty * safeUnitPrice);

  // 2. Converted Order Value (INR) = Gross Order Value * Exchange Rate
  const convertedValueInr = round2(grossOrderValue * safeRate);

  // 3. Advance & Outstanding Amounts (in Order Currency)
  const advanceAmount = round2(grossOrderValue * (safeAdvancePct / 100));
  const outstandingAmount = round2(grossOrderValue - advanceAmount);

  // 4. International Payment Fee (INR) = (Gross * Fee%) * ExchangeRate + (FixedFee * ExchangeRate)
  const feeInOrderCurrency = grossOrderValue * (safeFeePct / 100) + safeFixedFee;
  const paymentFeeInr = round2(feeInOrderCurrency * safeRate);

  // 5. Estimated Net Revenue (INR) = Converted Order Value - Payment Fee (INR)
  const estimatedNetRevenueInr = round2(convertedValueInr - paymentFeeInr);

  // 6. Total Operating Costs (INR)
  const procurement = Math.max(0, params.procurementCostInr);
  const logistics = Math.max(0, params.logisticsCostInr);
  const packaging = Math.max(0, params.packagingCostInr);
  const other = Math.max(0, params.otherCostsInr);
  const totalOperatingCostInr = procurement + logistics + packaging + other;

  // 7. Estimated Net Profit (INR) = Estimated Net Revenue - Total Operating Costs
  const estimatedProfitInr = round2(estimatedNetRevenueInr - totalOperatingCostInr);

  // 8. Profit Margin (%) = (Estimated Profit / Converted Order Value) * 100
  const profitMarginPercent =
    convertedValueInr > 0 ? round2((estimatedProfitInr / convertedValueInr) * 100) : 0;

  return {
    id: params.existingId && params.existingId.trim() ? params.existingId : params.exportOrderId,
    userId: params.userId,
    exportOrderId: params.exportOrderId,
    grossOrderValue,
    currency: (params.currency || 'USD').toUpperCase(),
    exchangeRate: safeRate,
    convertedValueInr,
    advancePercentage: safeAdvancePct,
    advanceAmount,
    outstandingAmount,
    paymentFeePercentage: safeFeePct,
    paymentFixedFee: safeFixedFee,
    paymentFeeInr,
    procurementCostInr: round2(procurement),
    logisticsCostInr: round2(logistics),
    packagingCostInr: round2(packaging),
    otherCostsInr: round2(other),
    estimatedNetRevenueInr,
    estimatedProfitInr,
    profitMarginPercent,
    assumptionsNote:
      params.assumptionsNote ||
      'Deterministic calculation based on user-supplied exchange rate and cost structure.',
    updatedAtEpochMs: Date.now()
  };
}

export function isDocumentVerifiedOrApproved(status: string): boolean {
  return status === 'VERIFIED' || status === 'APPROVED' || status === 'OFFICIALLY_CERTIFIED';
}

export function isPaymentSettled(status: string): boolean {
  return status === 'CAPTURED' || status === 'DEMO_CAPTURED';
}

export function isPaymentPending(status: string): boolean {
  return status === 'PENDING_APPROVAL' || status === 'APPROVED' || status === 'DEMO_PENDING';
}

export function calculateDocumentReadiness(documents: DocumentRecord[]): {
  pct: number;
  verifiedCount: number;
  totalRequired: number;
} {
  const requiredDocs = documents.filter((d) => d.requirementLevel === 'REQUIRED');
  const totalRequired = requiredDocs.length;
  if (totalRequired === 0) {
    return { pct: 0, verifiedCount: 0, totalRequired: 0 };
  }
  const verifiedCount = requiredDocs.filter((d) => isDocumentVerifiedOrApproved(d.status)).length;
  const pct = round2((verifiedCount / totalRequired) * 100);
  return { pct, verifiedCount, totalRequired };
}

export function formatInrShort(amountInr: number): string {
  const absVal = Math.abs(amountInr);
  const sign = amountInr < 0 ? '-' : '';
  if (absVal >= 10_000_000) {
    return `${sign}${(absVal / 10_000_000).toFixed(2)} Cr`;
  }
  if (absVal >= 100_000) {
    return `${sign}${(absVal / 100_000).toFixed(2)} L`;
  }
  if (absVal >= 1_000) {
    return `${sign}${Math.round(absVal).toLocaleString('en-IN')}`;
  }
  return `${sign}${absVal.toFixed(2)}`;
}

export function buildDigitalTwinState(
  order: ExportOrder,
  financialAssessment: FinancialAssessment | null,
  documents: DocumentRecord[],
  payments: PaymentRecord[]
): DigitalTwinState {
  const { pct: docReadinessPct, verifiedCount: verifiedRequiredDocs, totalRequired: totalRequiredDocs } =
    calculateDocumentReadiness(documents);

  const capturedPayments = payments.filter((p) => isPaymentSettled(p.paymentStatus));
  const capturedTotal = round2(capturedPayments.reduce((sum, p) => sum + p.amount, 0));
  const pendingApprovalPayments = payments.filter((p) => isPaymentPending(p.paymentStatus));

  const isOrderValidated =
    order.buyerName.trim().length > 0 &&
    order.productName.trim().length > 0 &&
    order.quantity > 0 &&
    order.unitPrice > 0 &&
    order.destinationPort.trim().length > 0;

  const isFinanciallyAssessed =
    financialAssessment !== null && financialAssessment.convertedValueInr > 0;
  const areAllRequiredDocsVerified =
    totalRequiredDocs > 0 && verifiedRequiredDocs === totalRequiredDocs;
  const hasAnyDocUploaded = documents.some((d) => d.status !== 'MISSING');
  const hasCapturedAdvanceOrFull = capturedTotal > 0;
  const isFullyPaid =
    order.orderValue > 0 &&
    capturedTotal >= Math.max(0.01, order.orderValue * (order.advancePercentage / 100));

  const stage1: LifecycleStageNode = {
    stageNumber: 1,
    title: 'Order Created',
    subtitle: `Export Order ${order.orderNumber} registered (${order.extractionSource})`,
    status: 'COMPLETED',
    detailText: `${order.productName} • ${order.quantity} ${order.unit} @ ${order.currency} ${order.unitPrice}`,
    targetScreenRoute: 'orders'
  };

  const stage2: LifecycleStageNode = {
    stageNumber: 2,
    title: 'Order Details Validated',
    subtitle: isOrderValidated
      ? `Buyer, Incoterms (${order.incoterm}), and port verified`
      : 'Missing buyer or shipment fields',
    status: isOrderValidated ? 'COMPLETED' : 'ACTION_REQUIRED',
    detailText: `Destination: ${order.destinationPort || 'Pending'} (${order.buyerCountry})`,
    targetScreenRoute: 'orders'
  };

  let stage3Status: LifecycleStageStatus = 'IN_PROGRESS';
  if (isFinanciallyAssessed && financialAssessment!.profitMarginPercent < 5) {
    stage3Status = 'ACTION_REQUIRED';
  } else if (isFinanciallyAssessed) {
    stage3Status = 'COMPLETED';
  }

  const stage3: LifecycleStageNode = {
    stageNumber: 3,
    title: 'Financial Assessment Completed',
    subtitle: isFinanciallyAssessed
      ? `INR ${formatInrShort(financialAssessment!.convertedValueInr)} • Net Margin ${financialAssessment!.profitMarginPercent}%`
      : 'Run deterministic revenue & cost breakdown',
    status: stage3Status,
    detailText: financialAssessment
      ? `Est. Profit: INR ${formatInrShort(financialAssessment.estimatedProfitInr)} @ FX ${financialAssessment.exchangeRate}`
      : 'Awaiting cost structure input',
    targetScreenRoute: 'financials'
  };

  const stage4: LifecycleStageNode = {
    stageNumber: 4,
    title: 'Export Documents Checklist Prepared',
    subtitle:
      totalRequiredDocs > 0
        ? `${totalRequiredDocs} mandatory documents configured`
        : 'Initialize export document checklist',
    status: totalRequiredDocs > 0 ? 'COMPLETED' : 'IN_PROGRESS',
    detailText: `Total checklist items: ${documents.length}`,
    targetScreenRoute: 'documents'
  };

  const rejectedOrReuploadDocs = documents.filter(
    (d) => d.status === 'REJECTED' || d.status === 'REUPLOAD_REQUIRED'
  ).length;

  let stage5Status: LifecycleStageStatus = 'PENDING';
  if (areAllRequiredDocsVerified) {
    stage5Status = 'COMPLETED';
  } else if (rejectedOrReuploadDocs > 0) {
    stage5Status = 'ACTION_REQUIRED';
  } else if (hasAnyDocUploaded) {
    stage5Status = 'IN_PROGRESS';
  }

  const stage5: LifecycleStageNode = {
    stageNumber: 5,
    title: 'Required Documents Verified',
    subtitle: `${verifiedRequiredDocs} / ${totalRequiredDocs} mandatory documents verified (${Math.round(docReadinessPct)}%)`,
    status: stage5Status,
    detailText:
      rejectedOrReuploadDocs > 0
        ? `${rejectedOrReuploadDocs} document(s) require re-upload`
        : 'Internal pre-shipment verification',
    targetScreenRoute: 'documents'
  };

  let stage6Status: LifecycleStageStatus = 'PENDING';
  if (hasCapturedAdvanceOrFull) {
    stage6Status = 'COMPLETED';
  } else if (pendingApprovalPayments.length > 0) {
    stage6Status = 'IN_PROGRESS';
  }

  const stage6: LifecycleStageNode = {
    stageNumber: 6,
    title: 'International Payment Initiated',
    subtitle: hasCapturedAdvanceOrFull
      ? 'Payment order initiated & approved'
      : pendingApprovalPayments.length > 0
      ? 'Payment order awaiting buyer approval / capture'
      : 'Create payment order',
    status: stage6Status,
    detailText: payments[0]
      ? `Order Ref: ${payments[0].paypalOrderId || 'Pending'}`
      : 'No payment transaction initiated',
    targetScreenRoute: 'payments'
  };

  let stage7Status: LifecycleStageStatus = 'PENDING';
  if (isFullyPaid) {
    stage7Status = 'COMPLETED';
  } else if (hasCapturedAdvanceOrFull) {
    stage7Status = 'IN_PROGRESS';
  } else if (pendingApprovalPayments.length > 0) {
    stage7Status = 'ACTION_REQUIRED';
  }

  const stage7: LifecycleStageNode = {
    stageNumber: 7,
    title: 'Payment Verified / Captured',
    subtitle: hasCapturedAdvanceOrFull
      ? `Captured ${order.currency} ${capturedTotal.toLocaleString()} of ${order.currency} ${order.orderValue.toLocaleString()}`
      : 'Zero verified captured receipts',
    status: stage7Status,
    detailText: capturedPayments[0]
      ? `Capture ID: ${capturedPayments[0].paypalCaptureId}`
      : 'Awaiting settlement capture',
    targetScreenRoute: 'payments'
  };

  const isOrderComplete =
    order.orderStatus === 'COMPLETED' ||
    (isOrderValidated &&
      isFinanciallyAssessed &&
      areAllRequiredDocsVerified &&
      hasCapturedAdvanceOrFull);

  const stage8: LifecycleStageNode = {
    stageNumber: 8,
    title: 'Export Order Ready / Completed',
    subtitle: isOrderComplete
      ? 'Pre-shipment financial, document & payment checks passed'
      : 'Pending upstream readiness gates',
    status: isOrderComplete ? 'COMPLETED' : 'PENDING',
    detailText: isOrderComplete
      ? 'Ready for customs dispatch & final settlement'
      : 'Complete remaining action items below',
    targetScreenRoute: 'digital_twin'
  };

  const stages = [stage1, stage2, stage3, stage4, stage5, stage6, stage7, stage8];
  const completedCount = stages.filter((s) => s.status === 'COMPLETED').length;
  const overallProgress = Math.round((completedCount / stages.length) * 100);

  const activeStage =
    stages.find((s) => s.status === 'ACTION_REQUIRED' || s.status === 'IN_PROGRESS') ||
    stages[stages.length - 1];

  const actions: OutstandingAction[] = [];
  if (!isFinanciallyAssessed) {
    actions.push({
      id: `act_fin_${order.id}`,
      title: 'Complete Financial Viability Assessment',
      description: 'Enter procurement, logistics, and FX assumptions to compute INR margin.',
      severity: 'HIGH',
      targetScreenRoute: 'financials'
    });
  } else if (financialAssessment!.profitMarginPercent < 8) {
    actions.push({
      id: `act_margin_${order.id}`,
      title: `Low Profit Margin Warning (${financialAssessment!.profitMarginPercent}%)`,
      description: 'Estimated margin is below 8%. Review freight costs or exchange rate buffer.',
      severity: 'HIGH',
      targetScreenRoute: 'financials'
    });
  }

  const missingReqDocs = documents.filter(
    (d) => d.requirementLevel === 'REQUIRED' && !isDocumentVerifiedOrApproved(d.status)
  );
  if (missingReqDocs.length > 0) {
    actions.push({
      id: `act_docs_${order.id}`,
      title: `${missingReqDocs.length} Mandatory Export Document(s) Unverified`,
      description: `Pending: ${missingReqDocs
        .slice(0, 3)
        .map((d) => d.documentType)
        .join(', ')}`,
      severity: rejectedOrReuploadDocs > 0 ? 'HIGH' : 'MEDIUM',
      targetScreenRoute: 'documents'
    });
  }

  if (!hasCapturedAdvanceOrFull) {
    actions.push({
      id: `act_pay_${order.id}`,
      title:
        pendingApprovalPayments.length > 0
          ? 'Capture Approved Payment Order'
          : `Initiate Advance Payment (${Math.round(order.advancePercentage)}%)`,
      description: `Expected advance: ${order.currency} ${round2(
        order.orderValue * (order.advancePercentage / 100)
      ).toLocaleString()}`,
      severity: 'MEDIUM',
      targetScreenRoute: 'payments'
    });
  }

  return {
    order,
    financialAssessment,
    documents,
    payments,
    documentReadinessPercent: docReadinessPct,
    verifiedRequiredDocs,
    totalRequiredDocs,
    capturedPaymentTotal: capturedTotal,
    overallProgressPercent: overallProgress,
    currentStageLabel: activeStage.title,
    stages,
    outstandingActions: actions
  };
}

export function calculatePortfolioMetrics(
  orders: ExportOrder[],
  assessments: FinancialAssessment[],
  documents: DocumentRecord[],
  payments: PaymentRecord[]
): PerformanceMetrics {
  if (orders.length === 0) {
    return {
      totalOrders: 0,
      activeOrders: 0,
      completedOrders: 0,
      cancelledOrders: 0,
      pendingOrders: 0,
      totalOrderValueInr: 0,
      capturedPaymentValueInr: 0,
      outstandingPaymentValueInr: 0,
      totalEstimatedProfitInr: 0,
      averageOrderValueInr: 0,
      averageProfitMarginPercent: 0,
      paymentCompletionRatePercent: 0,
      documentReadinessRatePercent: 0,
      statusDistribution: {},
      countryDistribution: {}
    };
  }

  const assessmentByOrder = new Map(assessments.map((a) => [a.exportOrderId, a]));
  const completedOrders = orders.filter((o) => o.orderStatus === 'COMPLETED').length;
  const cancelledOrders = orders.filter((o) => o.orderStatus === 'CANCELLED').length;
  const activeOrders = orders.length - completedOrders - cancelledOrders;

  let totalValueInr = 0;
  let totalProfitInr = 0;
  let marginSum = 0;
  let marginCount = 0;
  const countryMap: Record<string, number> = {};
  const statusMap: Record<string, number> = {};

  for (const order of orders) {
    statusMap[order.orderStatus] = (statusMap[order.orderStatus] || 0) + 1;
    const assessment = assessmentByOrder.get(order.id);
    const orderInr =
      assessment?.convertedValueInr ??
      round2(order.orderValue * defaultExchangeRateForCurrency(order.currency));
    totalValueInr += orderInr;

    if (assessment) {
      totalProfitInr += assessment.estimatedProfitInr;
      marginSum += assessment.profitMarginPercent;
      marginCount++;
    } else {
      totalProfitInr += round2(orderInr * 0.14);
    }

    const countryKey = order.buyerCountry.trim() || 'Unspecified';
    countryMap[countryKey] = round2((countryMap[countryKey] || 0) + orderInr);
  }

  const orderById = new Map(orders.map((o) => [o.id, o]));
  let capturedInr = 0;
  for (const payment of payments) {
    if (isPaymentSettled(payment.paymentStatus)) {
      const parentOrder = orderById.get(payment.exportOrderId);
      const fx =
        assessmentByOrder.get(payment.exportOrderId)?.exchangeRate ??
        defaultExchangeRateForCurrency(payment.currency || parentOrder?.currency || 'USD');
      capturedInr += payment.amount * fx;
    }
  }
  capturedInr = round2(capturedInr);
  const outstandingInr = round2(Math.max(0, totalValueInr - capturedInr));
  const avgOrderValueInr = round2(totalValueInr / orders.length);
  const avgMarginPct = marginCount > 0 ? round2(marginSum / marginCount) : 14.0;
  const paymentCompletionRate =
    totalValueInr > 0
      ? round2(Math.min(100, Math.max(0, (capturedInr / totalValueInr) * 100)))
      : 0;

  const { pct: docReadinessRate } = calculateDocumentReadiness(documents);

  const sortedCountries = Object.entries(countryMap)
    .sort((a, b) => b[1] - a[1])
    .reduce<Record<string, number>>((acc, [k, v]) => {
      acc[k] = v;
      return acc;
    }, {});

  return {
    totalOrders: orders.length,
    activeOrders,
    completedOrders,
    cancelledOrders,
    pendingOrders: activeOrders,
    totalOrderValueInr: round2(totalValueInr),
    capturedPaymentValueInr: capturedInr,
    outstandingPaymentValueInr: outstandingInr,
    totalEstimatedProfitInr: round2(totalProfitInr),
    averageOrderValueInr: avgOrderValueInr,
    averageProfitMarginPercent: avgMarginPct,
    paymentCompletionRatePercent: paymentCompletionRate,
    documentReadinessRatePercent: docReadinessRate,
    statusDistribution: statusMap,
    countryDistribution: sortedCountries
  };
}

export function resolveOrderTimestampMillis(order: ExportOrder, fallbackMillis = Date.now()): number {
  if (order.createdAtEpochMs && order.createdAtEpochMs > 0) {
    return order.createdAtEpochMs;
  }
  if (order.orderDate) {
    const parsed = Date.parse(order.orderDate);
    if (!Number.isNaN(parsed)) {
      return parsed;
    }
  }
  return fallbackMillis;
}

export function resolvePaymentTimestampMillis(
  payment: PaymentRecord,
  fallbackMillis = Date.now()
): number {
  if (payment.updatedAtEpochMs && payment.updatedAtEpochMs > 0) {
    return payment.updatedAtEpochMs;
  }
  if (payment.createdAtEpochMs && payment.createdAtEpochMs > 0) {
    return payment.createdAtEpochMs;
  }
  return fallbackMillis;
}

export function calculatePeriodAnalytics(
  orders: ExportOrder[],
  assessments: FinancialAssessment[],
  documents: DocumentRecord[],
  payments: PaymentRecord[],
  periodCode: TimePeriodCode,
  nowMillis = Date.now()
): PeriodDashboardAnalytics {
  const period = TIME_PERIOD_DEFINITIONS[periodCode] || TIME_PERIOD_DEFINITIONS['1M'];
  const assessmentByOrder = new Map(assessments.map((a) => [a.exportOrderId, a]));
  const orderById = new Map(orders.map((o) => [o.id, o]));

  const orderInrValue = (order: ExportOrder): number =>
    assessmentByOrder.get(order.id)?.convertedValueInr ??
    round2(order.orderValue * defaultExchangeRateForCurrency(order.currency));

  const paymentInrValue = (payment: PaymentRecord): number => {
    const parentOrder = orderById.get(payment.exportOrderId);
    const fx =
      assessmentByOrder.get(payment.exportOrderId)?.exchangeRate ??
      defaultExchangeRateForCurrency(payment.currency || parentOrder?.currency || 'USD');
    return round2(payment.amount * fx);
  };

  const windowMillis = period.windowMillis;
  const windowStartMillis = windowMillis !== null ? nowMillis - windowMillis : Number.MIN_SAFE_INTEGER;
  const prevWindowStartMillis =
    windowMillis !== null ? nowMillis - windowMillis * 2 : Number.MIN_SAFE_INTEGER;

  const filteredOrders =
    windowMillis === null
      ? orders
      : orders.filter((ord) => {
          const ts = resolveOrderTimestampMillis(ord, nowMillis);
          return ts >= windowStartMillis && ts <= nowMillis;
        });

  const filteredOrderIds = new Set(filteredOrders.map((o) => o.id));
  const filteredDocuments =
    windowMillis === null
      ? documents
      : documents.filter((d) => filteredOrderIds.has(d.exportOrderId));

  const filteredPayments =
    windowMillis === null
      ? payments
      : payments.filter((pay) => {
          const ts = resolvePaymentTimestampMillis(pay, nowMillis);
          return ts >= windowStartMillis && ts <= nowMillis;
        });

  const baseMetrics = calculatePortfolioMetrics(filteredOrders, assessments, filteredDocuments, []);

  const periodCapturedInr = round2(
    filteredPayments
      .filter((p) => isPaymentSettled(p.paymentStatus))
      .reduce((sum, p) => sum + paymentInrValue(p), 0)
  );

  const capturedAgainstFilteredOrdersInr = round2(
    payments
      .filter((p) => isPaymentSettled(p.paymentStatus) && filteredOrderIds.has(p.exportOrderId))
      .reduce((sum, p) => sum + paymentInrValue(p), 0)
  );

  const periodOutstandingInr = round2(
    Math.max(0, baseMetrics.totalOrderValueInr - capturedAgainstFilteredOrdersInr)
  );
  const periodPaymentCompletionPct =
    baseMetrics.totalOrderValueInr > 0
      ? round2(
          Math.min(
            100,
            Math.max(0, (capturedAgainstFilteredOrdersInr / baseMetrics.totalOrderValueInr) * 100)
          )
        )
      : 0;

  const periodMetrics: PerformanceMetrics = {
    ...baseMetrics,
    capturedPaymentValueInr: periodCapturedInr,
    outstandingPaymentValueInr: periodOutstandingInr,
    paymentCompletionRatePercent: periodPaymentCompletionPct
  };

  let prevOrderValueInr = 0;
  let prevCapturedInr = 0;
  if (windowMillis !== null) {
    for (const ord of orders) {
      const ts = resolveOrderTimestampMillis(ord, nowMillis);
      if (ts >= prevWindowStartMillis && ts < windowStartMillis) {
        prevOrderValueInr += orderInrValue(ord);
      }
    }
    for (const pay of payments) {
      if (isPaymentSettled(pay.paymentStatus)) {
        const ts = resolvePaymentTimestampMillis(pay, nowMillis);
        if (ts >= prevWindowStartMillis && ts < windowStartMillis) {
          prevCapturedInr += paymentInrValue(pay);
        }
      }
    }
  }
  prevOrderValueInr = round2(prevOrderValueInr);
  prevCapturedInr = round2(prevCapturedInr);

  const orderValueGrowthPercent =
    windowMillis !== null && prevOrderValueInr > 0
      ? round2(((periodMetrics.totalOrderValueInr - prevOrderValueInr) / prevOrderValueInr) * 100)
      : null;

  const capturedRevenueGrowthPercent =
    windowMillis !== null && prevCapturedInr > 0
      ? round2(((periodCapturedInr - prevCapturedInr) / prevCapturedInr) * 100)
      : null;

  const bucketCount =
    periodCode === '1D'
      ? 6
      : periodCode === '1W'
      ? 7
      : periodCode === '1M'
      ? 4
      : periodCode === '3M'
      ? 3
      : periodCode === '1Y'
      ? 6
      : Math.min(6, Math.max(4, filteredOrders.length));

  const labels: string[] = [];
  const intakeSeries: number[] = [];
  const capturedSeries: number[] = [];

  if (windowMillis === null) {
    const sortedOrders = [...filteredOrders].sort(
      (a, b) => resolveOrderTimestampMillis(a, nowMillis) - resolveOrderTimestampMillis(b, nowMillis)
    );
    if (sortedOrders.length === 0) {
      ['Q1', 'Q2', 'Q3', 'Q4'].forEach((lbl) => {
        labels.push(lbl);
        intakeSeries.push(0);
        capturedSeries.push(0);
      });
    } else {
      sortedOrders.slice(-6).forEach((ord) => {
        labels.push(ord.orderNumber.replace(/^ORDER-/, '') || ord.orderNumber);
        intakeSeries.push(orderInrValue(ord));
        const ordCap = payments
          .filter((p) => p.exportOrderId === ord.id && isPaymentSettled(p.paymentStatus))
          .reduce((sum, p) => sum + paymentInrValue(p), 0);
        capturedSeries.push(round2(ordCap));
      });
    }
  } else {
    const bucketSpan = Math.floor(windowMillis / bucketCount);
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

    for (let i = 0; i < bucketCount; i++) {
      const bStart = windowStartMillis + i * bucketSpan;
      const bEnd = i === bucketCount - 1 ? nowMillis + 1000 : windowStartMillis + (i + 1) * bucketSpan;

      let bucketLabel = `P${i + 1}`;
      if (periodCode === '1D') {
        const hoursAgo = (bucketCount - 1 - i) * 4;
        bucketLabel = hoursAgo === 0 ? 'Now' : `-${hoursAgo}h`;
      } else if (periodCode === '1W') {
        if (i === bucketCount - 1) {
          bucketLabel = 'Today';
        } else {
          const d = new Date(bStart);
          bucketLabel = `${String(d.getDate()).padStart(2, '0')} ${months[d.getMonth()]}`;
        }
      } else if (periodCode === '1M') {
        bucketLabel = `Wk ${i + 1}`;
      } else {
        const d = new Date(bEnd - 1000);
        bucketLabel = months[d.getMonth()];
      }

      const bucketIntake = orders
        .filter((o) => {
          const ts = resolveOrderTimestampMillis(o, nowMillis);
          return ts >= bStart && ts < bEnd;
        })
        .reduce((sum, o) => sum + orderInrValue(o), 0);

      const bucketCaptured = payments
        .filter((p) => {
          if (!isPaymentSettled(p.paymentStatus)) return false;
          const ts = resolvePaymentTimestampMillis(p, nowMillis);
          return ts >= bStart && ts < bEnd;
        })
        .reduce((sum, p) => sum + paymentInrValue(p), 0);

      labels.push(bucketLabel);
      intakeSeries.push(round2(bucketIntake));
      capturedSeries.push(round2(bucketCaptured));
    }
  }

  const periodSummaryText = `${period.label}: ${filteredOrders.length} order(s) • Intake ₹${formatInrShort(
    periodMetrics.totalOrderValueInr
  )} • Captured ₹${formatInrShort(periodCapturedInr)}`;

  return {
    periodCode,
    periodLabel: period.label,
    metrics: periodMetrics,
    filteredOrders,
    filteredPayments,
    previousPeriodOrderValueInr: prevOrderValueInr,
    orderValueGrowthPercent,
    previousPeriodCapturedInr: prevCapturedInr,
    capturedRevenueGrowthPercent,
    chartBucketLabels: labels,
    chartOrderIntakeInr: intakeSeries,
    chartCapturedPaymentsInr: capturedSeries,
    periodSummaryText
  };
}

export function evaluatePortfolioRisks(
  orders: ExportOrder[],
  assessments: FinancialAssessment[],
  documents: DocumentRecord[],
  payments: PaymentRecord[]
): TradeRiskAlert[] {
  const alerts: TradeRiskAlert[] = [];
  const assessmentMap = new Map(assessments.map((a) => [a.exportOrderId, a]));

  for (const order of orders) {
    const orderDocs = documents.filter((d) => d.exportOrderId === order.id);
    const orderPayments = payments.filter((p) => p.exportOrderId === order.id);
    const assessment = assessmentMap.get(order.id);

    const rejectedDocs = orderDocs.filter(
      (d) => d.status === 'REJECTED' || d.status === 'REUPLOAD_REQUIRED'
    );
    if (rejectedDocs.length > 0) {
      alerts.push({
        id: `risk_doc_rej_${order.id}`,
        orderId: order.id,
        orderNumber: order.orderNumber,
        category: 'DOCUMENTATION',
        severity: 'CRITICAL',
        title: `${rejectedDocs.length} Export Document(s) Flagged for Re-upload`,
        evidence: `${order.orderNumber} (${order.buyerName}): ${rejectedDocs
          .map((d) => `${d.documentType} [${d.status}]`)
          .join(', ')}`,
        recommendedAction:
          'Upload revised PDF/Image with matching invoice/HS code details and verify SHA-256 hash.',
        targetScreenRoute: 'documents'
      });
    }

    const missingRequired = orderDocs.filter(
      (d) => d.requirementLevel === 'REQUIRED' && !isDocumentVerifiedOrApproved(d.status)
    );
    if (missingRequired.length > 0 && rejectedDocs.length === 0) {
      alerts.push({
        id: `risk_doc_pend_${order.id}`,
        orderId: order.id,
        orderNumber: order.orderNumber,
        category: 'DOCUMENTATION',
        severity: 'WARNING',
        title: `${missingRequired.length} Mandatory Document(s) Pending Verification`,
        evidence: `${order.orderNumber} to ${order.destinationPort}: ${missingRequired
          .map((d) => d.documentType)
          .join(', ')}`,
        recommendedAction:
          'Complete internal pre-shipment verification before customs gate-in.',
        targetScreenRoute: 'documents'
      });
    }

    const capturedAmount = orderPayments
      .filter((p) => isPaymentSettled(p.paymentStatus))
      .reduce((sum, p) => sum + p.amount, 0);
    const expectedAdvance = round2(order.orderValue * (order.advancePercentage / 100));

    if (capturedAmount < expectedAdvance) {
      alerts.push({
        id: `risk_pay_${order.id}`,
        orderId: order.id,
        orderNumber: order.orderNumber,
        category: 'SETTLEMENT',
        severity: capturedAmount === 0 ? 'CRITICAL' : 'WARNING',
        title:
          capturedAmount === 0
            ? `Uncollected Advance Receivable (${order.currency} ${expectedAdvance.toLocaleString()})`
            : `Partial Advance Settlement (${order.currency} ${capturedAmount.toLocaleString()} / ${expectedAdvance.toLocaleString()})`,
        evidence: `${order.orderNumber} (${order.paymentTerms}) has ${order.currency} ${round2(
          order.orderValue - capturedAmount
        ).toLocaleString()} total uncollected exposure.`,
        recommendedAction:
          'Capture pending advance payment or issue payment reminder before Bill of Lading release.',
        targetScreenRoute: 'payments'
      });
    }

    if (assessment && assessment.profitMarginPercent < 12) {
      alerts.push({
        id: `risk_margin_${order.id}`,
        orderId: order.id,
        orderNumber: order.orderNumber,
        category: 'MARGIN',
        severity: assessment.profitMarginPercent < 8 ? 'CRITICAL' : 'ADVISORY',
        title: `Thin Net Export Margin (${assessment.profitMarginPercent}%)`,
        evidence: `${order.orderNumber}: Net profit ₹${formatInrShort(
          assessment.estimatedProfitInr
        )} on converted value ₹${formatInrShort(assessment.convertedValueInr)} @ FX ${
          assessment.exchangeRate
        }.`,
        recommendedAction:
          'Run What-If Simulator to test forward FX hedging or freight consolidation.',
        targetScreenRoute: 'financials'
      });
    }
  }

  return alerts;
}

export function buildShipmentIntelligencePortfolio(
  orders: ExportOrder[],
  assessments: FinancialAssessment[],
  documents: DocumentRecord[],
  payments: PaymentRecord[]
): ShipmentIntelligenceItem[] {
  const assessmentMap = new Map(assessments.map((a) => [a.exportOrderId, a]));

  return orders.map((order) => {
    const assessment = assessmentMap.get(order.id) || null;
    const orderDocs = documents.filter((d) => d.exportOrderId === order.id);
    const orderPayments = payments.filter((p) => p.exportOrderId === order.id);
    const twin = buildDigitalTwinState(order, assessment, orderDocs, orderPayments);

    const convertedValueInr =
      assessment?.convertedValueInr ??
      round2(order.orderValue * defaultExchangeRateForCurrency(order.currency));

    const pendingDocs = orderDocs.filter(
      (d) => d.requirementLevel === 'REQUIRED' && !isDocumentVerifiedOrApproved(d.status)
    );
    const flaggedDocs = orderDocs.filter(
      (d) => d.status === 'REJECTED' || d.status === 'REUPLOAD_REQUIRED'
    );

    let daysToExpectedShipment: number | null = null;
    if (order.expectedShipmentDate && order.expectedShipmentDate.trim().length > 0) {
      const parsedShipmentMs = Date.parse(order.expectedShipmentDate);
      const refOrderDateMs = order.orderDate ? Date.parse(order.orderDate) : NaN;
      if (!Number.isNaN(parsedShipmentMs) && !Number.isNaN(refOrderDateMs)) {
        daysToExpectedShipment = Math.round((parsedShipmentMs - refOrderDateMs) / (24 * 60 * 60 * 1000));
      }
    }

    const logisticsExceptions: string[] = [];
    if (flaggedDocs.length > 0) {
      flaggedDocs.forEach((doc) => {
        logisticsExceptions.push(
          `${doc.documentType} [${doc.status}]: ${doc.reviewerNote || 'Requires compliance re-upload'}`
        );
      });
    }
    const blDoc = orderDocs.find((d) => d.documentType.toLowerCase().includes('bill of lading'));
    if (blDoc && !isDocumentVerifiedOrApproved(blDoc.status) && order.orderStatus !== 'COMPLETED') {
      logisticsExceptions.push(
        `Bill of Lading / Airway Bill is [${blDoc.status}]${
          blDoc.reviewerNote ? ` — ${blDoc.reviewerNote}` : ''
        }`
      );
    }
    const sbDoc = orderDocs.find((d) => d.documentType.toLowerCase().includes('shipping bill'));
    if (sbDoc && !isDocumentVerifiedOrApproved(sbDoc.status) && order.orderStatus !== 'COMPLETED') {
      logisticsExceptions.push(
        `ICEGATE Shipping Bill is [${sbDoc.status}]${
          sbDoc.reviewerNote ? ` — ${sbDoc.reviewerNote}` : ''
        }`
      );
    }
    if (twin.capturedPaymentTotal === 0 && order.advancePercentage > 0 && order.orderStatus !== 'COMPLETED') {
      logisticsExceptions.push(
        `Pre-shipment advance (${order.advancePercentage}% = ${order.currency} ${round2(
          order.orderValue * (order.advancePercentage / 100)
        ).toLocaleString()}) not yet captured.`
      );
    }

    let shipmentRiskStatus: ShipmentIntelligenceItem['shipmentRiskStatus'] = 'ON_SCHEDULE';
    let shipmentRiskReason = 'Pre-shipment workflow gates progressing within contract parameters.';

    if (order.orderStatus === 'COMPLETED' || twin.overallProgressPercent === 100) {
      shipmentRiskStatus = 'READY_OR_DISPATCHED';
      shipmentRiskReason = 'All 8 Digital Twin readiness gates completed; customs & B/L verified.';
    } else if (flaggedDocs.length > 0 || twin.stages.some((s) => s.status === 'ACTION_REQUIRED')) {
      shipmentRiskStatus = 'AT_RISK_BLOCKED';
      shipmentRiskReason =
        flaggedDocs.length > 0
          ? `${flaggedDocs.length} mandatory document(s) flagged for re-upload before customs gate-in.`
          : `Action required at Digital Twin gate: ${twin.currentStageLabel}.`;
    } else if (pendingDocs.length > 0) {
      shipmentRiskStatus = 'APPROACHING_GATE';
      shipmentRiskReason = `${pendingDocs.length} mandatory document(s) awaiting pre-shipment verification (${twin.documentReadinessPercent}% ready).`;
    }

    return {
      order,
      twin,
      convertedValueInr,
      daysToExpectedShipment,
      shipmentRiskStatus,
      shipmentRiskReason,
      pendingDocs,
      flaggedDocs,
      logisticsExceptions
    };
  });
}

export function calculateReceivablesAndCurrencyAnalytics(
  orders: ExportOrder[],
  assessments: FinancialAssessment[],
  documents: DocumentRecord[],
  payments: PaymentRecord[]
): {
  portfolioMetrics: PerformanceMetrics;
  liveCapturedInr: number;
  simulatedCapturedInr: number;
  totalSettledOrSimulatedInr: number;
  pendingApprovalInr: number;
  uncollectedExposureInr: number;
  orderBreakdowns: OrderReceivableBreakdown[];
  currencyExposures: CurrencyExposureSummary[];
} {
  const portfolioMetrics = calculatePortfolioMetrics(orders, assessments, documents, payments);
  const assessmentMap = new Map(assessments.map((a) => [a.exportOrderId, a]));

  let liveCapturedInr = 0;
  let simulatedCapturedInr = 0;
  let pendingApprovalInr = 0;

  const currencyBucket = new Map<
    string,
    {
      currency: string;
      exchangeRate: number;
      orderCount: number;
      totalForeignAmount: number;
      totalInrEquivalent: number;
      settledOrSimulatedInr: number;
      uncollectedExposureInr: number;
    }
  >();

  const orderBreakdowns: OrderReceivableBreakdown[] = orders.map((order) => {
    const assessment = assessmentMap.get(order.id);
    const fx = assessment?.exchangeRate ?? defaultExchangeRateForCurrency(order.currency);
    const convertedOrderValueInr =
      assessment?.convertedValueInr ?? round2(order.orderValue * fx);
    const expectedAdvanceForeign = round2(order.orderValue * (order.advancePercentage / 100));
    const expectedAdvanceInr = round2(expectedAdvanceForeign * fx);

    const orderPayments = payments.filter((p) => p.exportOrderId === order.id);

    const liveCapturedForeign = round2(
      orderPayments
        .filter((p) => p.paymentStatus === 'CAPTURED')
        .reduce((sum, p) => sum + p.amount, 0)
    );
    const ordLiveCapturedInr = round2(liveCapturedForeign * fx);

    const simulatedCapturedForeign = round2(
      orderPayments
        .filter((p) => p.paymentStatus === 'DEMO_CAPTURED')
        .reduce((sum, p) => sum + p.amount, 0)
    );
    const ordSimulatedCapturedInr = round2(simulatedCapturedForeign * fx);

    const totalSettledOrSimulatedForeign = round2(
      liveCapturedForeign + simulatedCapturedForeign
    );
    const totalSettledOrSimulatedInr = round2(ordLiveCapturedInr + ordSimulatedCapturedInr);

    const pendingApprovalForeign = round2(
      orderPayments
        .filter((p) => isPaymentPending(p.paymentStatus))
        .reduce((sum, p) => sum + p.amount, 0)
    );
    const ordPendingApprovalInr = round2(pendingApprovalForeign * fx);

    const uncollectedExposureForeign = round2(
      Math.max(0, order.orderValue - totalSettledOrSimulatedForeign)
    );
    const uncollectedExposureInr = round2(
      Math.max(0, convertedOrderValueInr - totalSettledOrSimulatedInr)
    );

    liveCapturedInr += ordLiveCapturedInr;
    simulatedCapturedInr += ordSimulatedCapturedInr;
    pendingApprovalInr += ordPendingApprovalInr;

    const curr = order.currency.toUpperCase();
    const existingCurr = currencyBucket.get(curr) || {
      currency: curr,
      exchangeRate: fx,
      orderCount: 0,
      totalForeignAmount: 0,
      totalInrEquivalent: 0,
      settledOrSimulatedInr: 0,
      uncollectedExposureInr: 0
    };
    existingCurr.orderCount += 1;
    existingCurr.totalForeignAmount = round2(existingCurr.totalForeignAmount + order.orderValue);
    existingCurr.totalInrEquivalent = round2(
      existingCurr.totalInrEquivalent + convertedOrderValueInr
    );
    existingCurr.settledOrSimulatedInr = round2(
      existingCurr.settledOrSimulatedInr + totalSettledOrSimulatedInr
    );
    existingCurr.uncollectedExposureInr = round2(
      existingCurr.uncollectedExposureInr + uncollectedExposureInr
    );
    currencyBucket.set(curr, existingCurr);

    let dueDateStatusLabel = 'Insufficient data (No formal credit due date in record)';
    let agingCategoryLabel = 'Milestone-Based (Advance / CAD / L/C)';

    if (uncollectedExposureForeign <= 0.01) {
      dueDateStatusLabel = `100% Settled (${
        simulatedCapturedForeign > 0 ? 'Simulated Demo Capture' : 'Captured'
      })`;
      agingCategoryLabel = 'CLOSED / FULLY SETTLED';
    } else if (order.expectedShipmentDate) {
      dueDateStatusLabel = `Expected Shipment Gate: ${order.expectedShipmentDate} • Terms: ${order.paymentTerms}`;
      if (totalSettledOrSimulatedForeign < expectedAdvanceForeign) {
        agingCategoryLabel =
          pendingApprovalForeign > 0
            ? 'PRE-SHIPMENT ADVANCE AWAITING CAPTURE'
            : 'PRE-SHIPMENT ADVANCE UNINITIATED';
      } else {
        agingCategoryLabel = 'POST-SHIPMENT CAD / L/C BALANCE (NO DUE DATE OVERDUE INFERRED)';
      }
    }

    return {
      order,
      currency: curr,
      exchangeRate: fx,
      grossOrderValueForeign: order.orderValue,
      convertedOrderValueInr,
      expectedAdvanceForeign,
      expectedAdvanceInr,
      liveCapturedForeign,
      liveCapturedInr: ordLiveCapturedInr,
      simulatedCapturedForeign,
      simulatedCapturedInr: ordSimulatedCapturedInr,
      totalSettledOrSimulatedForeign,
      totalSettledOrSimulatedInr,
      pendingApprovalForeign,
      pendingApprovalInr: ordPendingApprovalInr,
      uncollectedExposureForeign,
      uncollectedExposureInr,
      dueDateStatusLabel,
      agingCategoryLabel
    };
  });

  const totalPortfolioInr = portfolioMetrics.totalOrderValueInr;
  const currencyExposures: CurrencyExposureSummary[] = Array.from(currencyBucket.values())
    .sort((a, b) => b.totalInrEquivalent - a.totalInrEquivalent)
    .map((item) => ({
      ...item,
      portfolioSharePercent:
        totalPortfolioInr > 0 ? round2((item.totalInrEquivalent / totalPortfolioInr) * 100) : 0
    }));

  return {
    portfolioMetrics,
    liveCapturedInr: round2(liveCapturedInr),
    simulatedCapturedInr: round2(simulatedCapturedInr),
    totalSettledOrSimulatedInr: round2(liveCapturedInr + simulatedCapturedInr),
    pendingApprovalInr: round2(pendingApprovalInr),
    uncollectedExposureInr: portfolioMetrics.outstandingPaymentValueInr,
    orderBreakdowns,
    currencyExposures
  };
}

export function calculateBuyerIntelligence(
  orders: ExportOrder[],
  assessments: FinancialAssessment[],
  documents: DocumentRecord[],
  payments: PaymentRecord[],
  activities: ActivityRecord[]
): BuyerAnalyticsItem[] {
  const assessmentMap = new Map(assessments.map((a) => [a.exportOrderId, a]));
  const allRisks = evaluatePortfolioRisks(orders, assessments, documents, payments);
  const portfolioMetrics = calculatePortfolioMetrics(orders, assessments, documents, payments);
  const totalPortfolioInr = portfolioMetrics.totalOrderValueInr;

  const groupedByBuyer = new Map<string, ExportOrder[]>();
  for (const order of orders) {
    const key = order.buyerName.trim() || 'Unspecified Buyer';
    const list = groupedByBuyer.get(key) || [];
    list.push(order);
    groupedByBuyer.set(key, list);
  }

  const items: BuyerAnalyticsItem[] = [];

  for (const [buyerName, buyerOrders] of groupedByBuyer.entries()) {
    const first = buyerOrders[0];
    const orderIdSet = new Set(buyerOrders.map((o) => o.id));
    const orderNumberSet = new Set(buyerOrders.map((o) => o.orderNumber));

    let totalValueInr = 0;
    let settledOrSimulatedInr = 0;
    let pendingApprovalInr = 0;
    let marginSum = 0;
    let marginCount = 0;

    for (const ord of buyerOrders) {
      const assess = assessmentMap.get(ord.id);
      const fx = assess?.exchangeRate ?? defaultExchangeRateForCurrency(ord.currency);
      const ordInr = assess?.convertedValueInr ?? round2(ord.orderValue * fx);
      totalValueInr += ordInr;

      if (assess) {
        marginSum += assess.profitMarginPercent;
        marginCount += 1;
      }

      const ordPays = payments.filter((p) => p.exportOrderId === ord.id);
      for (const p of ordPays) {
        if (isPaymentSettled(p.paymentStatus)) {
          settledOrSimulatedInr += p.amount * fx;
        } else if (isPaymentPending(p.paymentStatus)) {
          pendingApprovalInr += p.amount * fx;
        }
      }
    }

    totalValueInr = round2(totalValueInr);
    settledOrSimulatedInr = round2(settledOrSimulatedInr);
    pendingApprovalInr = round2(pendingApprovalInr);
    const uncollectedExposureInr = round2(Math.max(0, totalValueInr - settledOrSimulatedInr));
    const collectionRatePct =
      totalValueInr > 0
        ? round2(Math.min(100, Math.max(0, (settledOrSimulatedInr / totalValueInr) * 100)))
        : 0;

    const buyerDocs = documents.filter((d) => orderIdSet.has(d.exportOrderId));
    const {
      pct: docReadinessPct,
      verifiedCount: verifiedDocsCount,
      totalRequired: totalRequiredDocsCount
    } = calculateDocumentReadiness(buyerDocs);

    const buyerRisks = allRisks.filter(
      (r) => orderIdSet.has(r.orderId) || orderNumberSet.has(r.orderNumber)
    );
    const buyerActivities = activities.filter(
      (a) => orderIdSet.has(a.exportOrderId) || orderNumberSet.has(a.exportOrderId)
    );

    const ports = Array.from(
      new Set(buyerOrders.map((o) => o.destinationPort.trim()).filter(Boolean))
    );

    items.push({
      buyerName,
      buyerCountry: first.buyerCountry,
      buyerContact: first.buyerContact || 'Insufficient data',
      buyerAddress: first.buyerAddress || 'Insufficient data',
      destinationPorts: ports,
      orders: buyerOrders,
      orderCount: buyerOrders.length,
      totalValueInr,
      portfolioConcentrationPct:
        totalPortfolioInr > 0 ? round2((totalValueInr / totalPortfolioInr) * 100) : 0,
      settledOrSimulatedInr,
      pendingApprovalInr,
      uncollectedExposureInr,
      collectionRatePct,
      averageMarginPct: marginCount > 0 ? round2(marginSum / marginCount) : null,
      verifiedDocsCount,
      totalRequiredDocsCount,
      docReadinessPct,
      riskAlerts: buyerRisks,
      activities: buyerActivities,
      externalCreditRatingStatus: 'Insufficient data (External ECGC / D&B credit bureau not connected)',
      historicalDefaultHistoryStatus:
        'Insufficient data (Only in-workspace export order ledger available)'
    });
  }

  return items.sort((a, b) => b.totalValueInr - a.totalValueInr);
}
