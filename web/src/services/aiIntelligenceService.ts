import {
  AIExtractedOrderDraft,
  DocumentRecord,
  ExportOrder,
  FinancialAssessment,
  PaymentRecord,
  StructuredAIRecommendation
} from '../types/models';
import {
  buildShipmentIntelligencePortfolio,
  calculateBuyerIntelligence,
  calculatePortfolioMetrics,
  calculateReceivablesAndCurrencyAnalytics,
  evaluatePortfolioRisks,
  formatInrShort,
  isDocumentVerifiedOrApproved,
  isPaymentSettled,
  round2
} from '../domain/financialEngine';

export const SAMPLE_EXTRACTION_TEMPLATES: Array<{
  id: string;
  label: string;
  corridor: string;
  rawText: string;
}> = [
  {
    id: 'tpl_germany_turmeric',
    label: 'Germany CIF • Organic Turmeric (EUR 49,200)',
    corridor: 'Chennai → Hamburg (EUR)',
    rawText: `PURCHASE ORDER: PO-DE-2026-8841
EXPORTER: Tamil Nadu Global Exports Pvt. Ltd., Guindy Industrial Estate, Chennai 600032
EXPORTER CONTACT: exports@tnglobalexports.in | +91 44 2250 4890
BUYER: Hanseatic Spice & Botanicals GmbH
BUYER COUNTRY: Germany
BUYER ADDRESS: Am Sandtorkai 48, HafenCity, 20457 Hamburg, Germany
BUYER CONTACT: procurement@hanseatic-spice.de
PRODUCT: Organic Erode Turmeric Finger & Alleppey Curcumin Powder (HS 091030)
DESCRIPTION: EU Organic Certified High-Curcumin (5.2%) Erode Turmeric in 25kg Kraft Bags
QUANTITY: 12000
UNIT: KG
UNIT PRICE: 4.10
CURRENCY: EUR
INCOTERM: CIF
DESTINATION PORT: Port of Hamburg, Germany
ORDER DATE: 2026-04-12
SHIPMENT DATE: 2026-05-04
PAYMENT TERMS: 40% Advance, 60% CAD against Original B/L
ADVANCE PERCENTAGE: 40`
  },
  {
    id: 'tpl_usa_apparel',
    label: 'USA FOB • GOTS Cotton Apparel (USD 64,600)',
    corridor: 'Tirupur → Long Beach (USD)',
    rawText: `PURCHASE ORDER: PO-US-LB-9920
EXPORTER: Tamil Nadu Global Exports Pvt. Ltd., Tirupur Knitwear Cluster, TN
EXPORTER CONTACT: apparel@tnglobalexports.in
BUYER: Pacific Coast Sustainable Apparel Inc.
BUYER COUNTRY: United States
BUYER ADDRESS: 1450 OceanGate Blvd, Long Beach, CA 90802, USA
BUYER CONTACT: sourcing@pacificcoastapparel.com
PRODUCT: 100% GOTS Organic Combed Cotton Interlock Kids Rompers (HS 611120)
DESCRIPTION: 180 GSM Bio-Washed Organic Cotton Knitted Apparel, Azo-Free Reactive Dyed
QUANTITY: 9500
UNIT: PCS
UNIT PRICE: 6.80
CURRENCY: USD
INCOTERM: FOB
DESTINATION PORT: Port of Long Beach, USA
ORDER DATE: 2026-04-12
SHIPMENT DATE: 2026-05-10
PAYMENT TERMS: 50% Advance, 50% CAD
ADVANCE PERCENTAGE: 50`
  },
  {
    id: 'tpl_japan_valves',
    label: 'Japan CIF • CNC SS316L Valves (JPY 6,150,000)',
    corridor: 'Coimbatore → Yokohama (JPY)',
    rawText: `PURCHASE ORDER: PO-JP-OSA-4410
EXPORTER: Tamil Nadu Global Exports Pvt. Ltd., Coimbatore Precision CNC Cluster
EXPORTER CONTACT: engineering@tnglobalexports.in
BUYER: Kansai Industrial Fluid Controls Co., Ltd.
BUYER COUNTRY: Japan
BUYER ADDRESS: 2-3-18 Nakanoshima, Kita-ku, Osaka 530-0005, Japan
BUYER CONTACT: global-qa@kansaifluid.co.jp
PRODUCT: CNC-Machined SS316L High-Pressure Cryogenic Valve Bodies (HS 848190)
DESCRIPTION: JIS B2001 Stainless Steel 316L Precision Machined Valve Castings with 3.1 MTC
QUANTITY: 1500
UNIT: PCS
UNIT PRICE: 4100
CURRENCY: JPY
INCOTERM: CIF
DESTINATION PORT: Port of Yokohama, Japan
ORDER DATE: 2026-04-12
SHIPMENT DATE: 2026-05-18
PAYMENT TERMS: 30% Advance, 70% Sight L/C
ADVANCE PERCENTAGE: 30`
  }
];

function extractField(text: string, keys: string[]): string {
  for (const key of keys) {
    const regex = new RegExp(`^\\s*${key}\\s*[:=-]\\s*(.+)$`, 'im');
    const match = text.match(regex);
    if (match && match[1]) {
      return match[1].trim();
    }
  }
  return '';
}

/**
 * Calls server-side Gemini endpoint (/api/ai/extract-po) first.
 * Never exposes or accepts API keys in client code or URLs (Part 11A).
 * Falls back cleanly to deterministic parser and explicitly labels the engine used.
 */
export async function extractPurchaseOrderFromText(
  rawText: string
): Promise<AIExtractedOrderDraft> {
  const trimmed = rawText.trim();
  if (!trimmed) {
    throw new Error('Please paste a Purchase Order or select a sample trade document first.');
  }

  try {
    const response = await fetch('/api/ai/extract-po', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ rawText: trimmed })
    });

    if (response.ok) {
      const payload = await response.json();
      if (payload?.liveGemini && payload?.draft) {
        const parsed = payload.draft;
        return {
          exporterName: parsed.exporterName || 'Tamil Nadu Global Exports Pvt. Ltd.',
          exporterContact: parsed.exporterContact || 'exports@tnglobalexports.in',
          exporterAddress:
            parsed.exporterAddress || 'Guindy Industrial Estate, Chennai, Tamil Nadu 600032, India',
          buyerName: parsed.buyerName || '',
          buyerCountry: parsed.buyerCountry || '',
          buyerContact: parsed.buyerContact || '',
          buyerAddress: parsed.buyerAddress || '',
          productName: parsed.productName || '',
          productDescription: parsed.productDescription || '',
          quantity: Number(parsed.quantity) || 0,
          unit: parsed.unit || 'KG',
          unitPrice: Number(parsed.unitPrice) || 0,
          currency: (parsed.currency || 'USD').toUpperCase(),
          incoterm: (parsed.incoterm || 'FOB').toUpperCase(),
          destinationPort: parsed.destinationPort || '',
          orderDate: parsed.orderDate || new Date().toISOString().slice(0, 10),
          expectedShipmentDate: parsed.expectedShipmentDate || '',
          paymentTerms: parsed.paymentTerms || '30% Advance, 70% CAD',
          advancePercentage: Number(parsed.advancePercentage) || 30,
          confidenceScore: Number(parsed.confidenceScore) || 95,
          missingFields: Array.isArray(parsed.missingFields) ? parsed.missingFields : [],
          uncertainFields: Array.isArray(parsed.uncertainFields) ? parsed.uncertainFields : [],
          conflictsDetected: Array.isArray(parsed.conflictsDetected)
            ? parsed.conflictsDetected
            : [],
          aiExtractionNotes:
            parsed.aiExtractionNotes ||
            'Extracted via Server-Side Gemini 3.8 Flash structured JSON schema. Explicit human confirmation required to create order.',
          extractionEngineLabel: 'Live Gemini 3.8 Flash (Server-Side API)'
        };
      }
    }
  } catch {
    // Cleanly fall through to deterministic local parser
  }

  // Deterministic Local Structured Parser (Zero-Cost Demo Mode Fallback)
  const exporterName =
    extractField(trimmed, ['EXPORTER', 'EXPORTER NAME', 'SELLER']) ||
    'Tamil Nadu Global Exports Pvt. Ltd.';
  const exporterContact =
    extractField(trimmed, ['EXPORTER CONTACT', 'SELLER CONTACT']) ||
    'exports@tnglobalexports.in | +91 44 2250 4890';
  const exporterAddress =
    extractField(trimmed, ['EXPORTER ADDRESS', 'SELLER ADDRESS']) ||
    'Guindy Industrial Estate, Chennai, Tamil Nadu 600032, India';

  const buyerName = extractField(trimmed, ['BUYER', 'BUYER NAME', 'CONSIGNEE', 'IMPORTER']);
  const buyerCountry = extractField(trimmed, ['BUYER COUNTRY', 'COUNTRY', 'DESTINATION COUNTRY']);
  const buyerContact = extractField(trimmed, ['BUYER CONTACT', 'CONSIGNEE CONTACT']);
  const buyerAddress = extractField(trimmed, ['BUYER ADDRESS', 'CONSIGNEE ADDRESS']);
  const productName = extractField(trimmed, ['PRODUCT', 'PRODUCT NAME', 'COMMODITY', 'ITEM']);
  const productDescription =
    extractField(trimmed, ['DESCRIPTION', 'PRODUCT DESCRIPTION', 'SPECIFICATION']) || productName;

  const qtyStr = extractField(trimmed, ['QUANTITY', 'QTY']).replace(/,/g, '');
  const quantity = parseFloat(qtyStr) || 1000;
  const unit = (extractField(trimmed, ['UNIT', 'UOM']) || 'KG').toUpperCase();

  const priceStr = extractField(trimmed, ['UNIT PRICE', 'PRICE', 'RATE']).replace(/[^0-9.]/g, '');
  const unitPrice = parseFloat(priceStr) || 10;

  const currency = (extractField(trimmed, ['CURRENCY', 'CURR']) || 'USD').toUpperCase();
  const incoterm = (
    extractField(trimmed, ['INCOTERM', 'INCOTERMS', 'TERMS OF DELIVERY']) || 'CIF'
  ).toUpperCase();
  const destinationPort = extractField(trimmed, [
    'DESTINATION PORT',
    'PORT OF DISCHARGE',
    'PORT'
  ]);
  const orderDate =
    extractField(trimmed, ['ORDER DATE', 'PO DATE', 'DATE']) ||
    new Date().toISOString().slice(0, 10);
  const expectedShipmentDate =
    extractField(trimmed, ['SHIPMENT DATE', 'EXPECTED SHIPMENT DATE', 'DELIVERY DATE']) ||
    '2026-05-15';
  const paymentTerms =
    extractField(trimmed, ['PAYMENT TERMS', 'SETTLEMENT TERMS']) || '30% Advance, 70% CAD';
  const advStr = extractField(trimmed, ['ADVANCE PERCENTAGE', 'ADVANCE %', 'ADVANCE']).replace(
    /[^0-9.]/g,
    ''
  );
  const advancePercentage = parseFloat(advStr) || 30;

  const missingFields: string[] = [];
  if (!buyerName) missingFields.push('buyerName');
  if (!buyerCountry) missingFields.push('buyerCountry');
  if (!productName) missingFields.push('productName');
  if (!destinationPort) missingFields.push('destinationPort');

  const uncertainFields: string[] = [];
  if (!priceStr) uncertainFields.push('unitPrice (defaulted to 10.0)');
  if (!qtyStr) uncertainFields.push('quantity (defaulted to 1000)');

  const confidenceScore = Math.max(
    68,
    98 - missingFields.length * 8 - uncertainFields.length * 5
  );

  return {
    exporterName,
    exporterContact,
    exporterAddress,
    buyerName: buyerName || 'Global Trade Partner GmbH',
    buyerCountry: buyerCountry || 'Germany',
    buyerContact: buyerContact || 'procurement@globaltradepartner.de',
    buyerAddress: buyerAddress || 'HafenCity Trade Center, Hamburg',
    productName: productName || 'Export Consignment (Extracted)',
    productDescription: productDescription || 'Standard export specification per buyer PO',
    quantity,
    unit,
    unitPrice,
    currency,
    incoterm,
    destinationPort: destinationPort || 'Port of Hamburg',
    orderDate,
    expectedShipmentDate,
    paymentTerms,
    advancePercentage,
    confidenceScore,
    missingFields,
    uncertainFields,
    conflictsDetected: [],
    aiExtractionNotes:
      'Parsed via NiryatFlow Deterministic PO Extraction Engine (Fallback — Live Gemini Not Active). Review fields below and click Confirm to create the Export Order.',
    extractionEngineLabel: 'Deterministic Local PO Parser (Fallback Mode)'
  };
}

export function generateFinancialViabilityAdvisory(
  order: ExportOrder,
  assessment: FinancialAssessment
): {
  headline: string;
  riskRating: 'STRONG' | 'MODERATE' | 'CRITICAL';
  recommendations: string[];
  modeLabel: string;
} {
  const margin = assessment.profitMarginPercent;
  const totalCostInr =
    assessment.procurementCostInr +
    assessment.logisticsCostInr +
    assessment.packagingCostInr +
    assessment.otherCostsInr;
  const logisticsSharePct =
    assessment.convertedValueInr > 0
      ? round2((assessment.logisticsCostInr / assessment.convertedValueInr) * 100)
      : 0;
  const feeSharePct =
    assessment.convertedValueInr > 0
      ? round2((assessment.paymentFeeInr / assessment.convertedValueInr) * 100)
      : 0;

  const riskRating = margin >= 18 ? 'STRONG' : margin >= 10 ? 'MODERATE' : 'CRITICAL';

  const headline =
    riskRating === 'STRONG'
      ? `${order.orderNumber} achieves a healthy ${margin}% net export margin (₹${formatInrShort(
          assessment.estimatedProfitInr
        )} net profit on ₹${formatInrShort(assessment.convertedValueInr)} converted value).`
      : riskRating === 'MODERATE'
      ? `${order.orderNumber} delivers a moderate ${margin}% net margin (₹${formatInrShort(
          assessment.estimatedProfitInr
        )}). Sensitive to ${order.currency}/INR spot fluctuations below ${round2(
          assessment.exchangeRate * 0.97
        )}.`
      : `CRITICAL MARGIN ALERT: ${order.orderNumber} nets only ${margin}% (₹${formatInrShort(
          assessment.estimatedProfitInr
        )}). Immediate cost or FX renegotiation required before vessel booking.`;

  const recommendations: string[] = [
    `FX Protection: Lock 60% of ${order.currency} ${assessment.grossOrderValue.toLocaleString()} via an Authorized Dealer (AD Category-I) forward contract at ≥ ₹${assessment.exchangeRate} to protect ₹${formatInrShort(
      assessment.estimatedProfitInr
    )} net profit.`,
    `Payment Corridor Optimization: International payment fees consume ₹${formatInrShort(
      assessment.paymentFeeInr
    )} (${feeSharePct}% of gross value). Collect the ${assessment.advancePercentage}% advance (${
      order.currency
    } ${assessment.advanceAmount.toLocaleString()}) immediately and settle the ${round2(
      100 - assessment.advancePercentage
    )}% balance via e-FIRC wire/CAD to save ~1.8% in gateway fees.`,
    `Freight & Operating Structure: Total operating cost is ₹${formatInrShort(
      totalCostInr
    )}, with logistics at ₹${formatInrShort(
      assessment.logisticsCostInr
    )} (${logisticsSharePct}% of order value under ${order.incoterm} ${order.destinationPort}). Claim RoDTEP / Duty Drawback on the ICEGATE Shipping Bill to add ~1.9%–2.8% post-shipment incentive.`
  ];

  return {
    headline,
    riskRating,
    recommendations,
    modeLabel: 'Deterministic Export Finance Analyst (Grounded in Order Math)'
  };
}

export function answerWorkspaceBusinessQuery(params: {
  query: string;
  orders: ExportOrder[];
  assessments: FinancialAssessment[];
  documents: DocumentRecord[];
  payments: PaymentRecord[];
}): {
  answerTitle: string;
  answerBullets: string[];
  citedOrders: string[];
  engineLabel: string;
} {
  const q = params.query.trim().toLowerCase();
  const assessmentMap = new Map(params.assessments.map((a) => [a.exportOrderId, a]));
  const risks = evaluatePortfolioRisks(
    params.orders,
    params.assessments,
    params.documents,
    params.payments
  );

  if (
    q.includes('document') ||
    q.includes('missing') ||
    q.includes('reupload') ||
    q.includes('re-upload') ||
    q.includes('compliance')
  ) {
    const flagged = params.documents.filter(
      (d) => d.requirementLevel === 'REQUIRED' && !isDocumentVerifiedOrApproved(d.status)
    );
    const cited = Array.from(new Set(flagged.map((d) => d.exportOrderId)));
    return {
      answerTitle: `Found ${flagged.length} unverified mandatory export document(s) across ${cited.length} order(s):`,
      answerBullets:
        flagged.length === 0
          ? ['All mandatory export documents across the active portfolio are verified.']
          : flagged.map(
              (d) =>
                `${d.exportOrderId} (${d.id}) — ${d.documentType}: Status [${d.status}] ${
                  d.reviewerNote ? `(${d.reviewerNote})` : ''
                }`
            ),
      citedOrders: cited,
      engineLabel: 'Deterministic Grounded Query Engine (Fallback — Local Workspace Records)'
    };
  }

  if (
    q.includes('shipment') ||
    q.includes('readiness') ||
    q.includes('delay') ||
    q.includes('logistics') ||
    q.includes('port')
  ) {
    const shipments = buildShipmentIntelligencePortfolio(
      params.orders,
      params.assessments,
      params.documents,
      params.payments
    );
    const blockedOrApproaching = shipments.filter(
      (s) => s.shipmentRiskStatus !== 'READY_OR_DISPATCHED'
    );
    return {
      answerTitle: `Shipment Readiness & Logistics Status (${blockedOrApproaching.length} Active Pre-Shipment Consignments):`,
      answerBullets: shipments.map(
        (s) =>
          `${s.order.orderNumber} → ${s.order.destinationPort} (Expected: ${s.order.expectedShipmentDate}): [${s.shipmentRiskStatus}] • Digital Twin ${s.twin.overallProgressPercent}% (${s.twin.currentStageLabel}) • ${s.shipmentRiskReason}`
      ),
      citedOrders: shipments.map((s) => s.order.orderNumber),
      engineLabel: 'Deterministic Grounded Query Engine (Fallback — Local Workspace Records)'
    };
  }

  if (q.includes('buyer') || q.includes('concentration') || q.includes('customer')) {
    const buyers = calculateBuyerIntelligence(
      params.orders,
      params.assessments,
      params.documents,
      params.payments,
      []
    );
    return {
      answerTitle: `Buyer Concentration & Exposure Breakdown (${buyers.length} International Buyers):`,
      answerBullets: buyers.map(
        (b) =>
          `${b.buyerName} (${b.buyerCountry}): ${b.orderCount} order(s) • Total Value ₹${formatInrShort(
            b.totalValueInr
          )} (${b.portfolioConcentrationPct}% of portfolio) • Settled/Simulated ₹${formatInrShort(
            b.settledOrSimulatedInr
          )} • Uncollected Exposure ₹${formatInrShort(b.uncollectedExposureInr)}`
      ),
      citedOrders: params.orders.map((o) => o.orderNumber),
      engineLabel: 'Deterministic Grounded Query Engine (Fallback — Local Workspace Records)'
    };
  }

  if (
    q.includes('payment') ||
    q.includes('unpaid') ||
    q.includes('advance') ||
    q.includes('receivable') ||
    q.includes('capture') ||
    q.includes('cash')
  ) {
    const pendingPayments = params.payments.filter((p) => !isPaymentSettled(p.paymentStatus));
    const ordersWithoutAdvance = params.orders.filter((o) => {
      const cap = params.payments
        .filter((p) => p.exportOrderId === o.id && isPaymentSettled(p.paymentStatus))
        .reduce((s, p) => s + p.amount, 0);
      return cap === 0;
    });
    const cited = Array.from(
      new Set([
        ...pendingPayments.map((p) => p.exportOrderId),
        ...ordersWithoutAdvance.map((o) => o.id)
      ])
    );

    const bullets: string[] = [];
    for (const p of pendingPayments) {
      bullets.push(
        `${p.exportOrderId} (${p.id}): ${p.paymentType} of ${p.currency} ${p.amount.toLocaleString()} is in [${
          p.paymentStatus
        }] (Ref: ${p.paypalOrderId} — Simulated Demo Mode).`
      );
    }
    for (const o of ordersWithoutAdvance) {
      if (!pendingPayments.some((p) => p.exportOrderId === o.id)) {
        bullets.push(
          `${o.orderNumber} (${o.buyerName}, ${o.buyerCountry}): Zero captured receipts against ${
            o.currency
          } ${o.orderValue.toLocaleString()} (${o.advancePercentage}% advance expected = ${
            o.currency
          } ${round2(o.orderValue * (o.advancePercentage / 100)).toLocaleString()}).`
        );
      }
    }

    return {
      answerTitle: `Receivable & Settlement Exposure Analysis (${cited.length} order(s) require action):`,
      answerBullets:
        bullets.length > 0
          ? bullets
          : ['All export orders have settled their required advance/full payment milestones.'],
      citedOrders: cited,
      engineLabel: 'Deterministic Grounded Query Engine (Fallback — Local Workspace Records)'
    };
  }

  if (q.includes('margin') || q.includes('profit') || q.includes('highest') || q.includes('financial')) {
    const ranked = [...params.orders]
      .map((o) => ({
        order: o,
        assessment: assessmentMap.get(o.id)
      }))
      .sort(
        (a, b) =>
          (b.assessment?.profitMarginPercent ?? 0) - (a.assessment?.profitMarginPercent ?? 0)
      );

    return {
      answerTitle: 'Portfolio Profitability & Margin Ranking (Deterministic INR Math):',
      answerBullets: ranked.map(({ order, assessment }) =>
        assessment
          ? `${order.orderNumber} (${order.buyerCountry} • ${order.currency} ${order.orderValue.toLocaleString()}): Net Margin ${
              assessment.profitMarginPercent
            }% • Est. Profit ₹${formatInrShort(
              assessment.estimatedProfitInr
            )} on Converted Value ₹${formatInrShort(assessment.convertedValueInr)} @ FX ${
              assessment.exchangeRate
            }`
          : `${order.orderNumber}: Financial assessment not yet saved.`
      ),
      citedOrders: ranked.map((r) => r.order.orderNumber),
      engineLabel: 'Deterministic Grounded Query Engine (Fallback — Local Workspace Records)'
    };
  }

  const criticalRisks = risks.filter((r) => r.severity === 'CRITICAL');
  return {
    answerTitle: `Executive Portfolio Operations Brief (${params.orders.length} Orders, ${criticalRisks.length} Critical Action Items):`,
    answerBullets: [
      ...risks.slice(0, 5).map((r) => `[${r.severity} • ${r.category}] ${r.title} — ${r.evidence}`),
      `Tip: Ask about "missing documents", "unpaid advances", "shipment readiness", "buyer concentration", or "highest profit margin orders" for targeted drill-downs.`
    ],
    citedOrders: Array.from(new Set(risks.map((r) => r.orderNumber))),
    engineLabel: 'Deterministic Grounded Query Engine (Fallback — Local Workspace Records)'
  };
}

function buildWorkspaceContextSummary(params: {
  orders: ExportOrder[];
  assessments: FinancialAssessment[];
  documents: DocumentRecord[];
  payments: PaymentRecord[];
}): string {
  const metrics = calculatePortfolioMetrics(
    params.orders,
    params.assessments,
    params.documents,
    params.payments
  );
  const assessmentMap = new Map(params.assessments.map((a) => [a.exportOrderId, a]));

  const orderLines = params.orders.map((o) => {
    const a = assessmentMap.get(o.id);
    const docs = params.documents.filter((d) => d.exportOrderId === o.id);
    const reqDocs = docs.filter((d) => d.requirementLevel === 'REQUIRED');
    const verDocs = reqDocs.filter((d) => isDocumentVerifiedOrApproved(d.status));
    const unverifiedList = reqDocs
      .filter((d) => !isDocumentVerifiedOrApproved(d.status))
      .map((d) => `${d.id}:${d.documentType}[${d.status}]`)
      .join(', ');
    const pays = params.payments
      .filter((p) => p.exportOrderId === o.id)
      .map((p) => `${p.id}:${p.paymentType} ${p.currency} ${p.amount} [${p.paymentStatus}]`)
      .join('; ');

    return `Order ${o.orderNumber} | Buyer: ${o.buyerName} (${o.buyerCountry}) | Product: ${o.productName} | Value: ${o.currency} ${o.orderValue} (INR ${
      a?.convertedValueInr ?? 0
    }) | Margin: ${a?.profitMarginPercent ?? 0}% (Profit INR ${
      a?.estimatedProfitInr ?? 0
    }) | Status: ${o.orderStatus} | ShipmentDate: ${o.expectedShipmentDate} | RequiredDocs: ${
      verDocs.length
    }/${reqDocs.length} verified (Unverified: ${unverifiedList || 'None'}) | Payments: ${
      pays || 'None'
    }`;
  });

  return [
    `Portfolio Totals: ${metrics.totalOrders} orders (${metrics.activeOrders} active, ${metrics.completedOrders} completed) | Total Value INR ${metrics.totalOrderValueInr} | Captured/Simulated INR ${metrics.capturedPaymentValueInr} | Outstanding INR ${metrics.outstandingPaymentValueInr} | Est Profit INR ${metrics.totalEstimatedProfitInr} (Avg Margin ${metrics.averageProfitMarginPercent}%) | Doc Readiness ${metrics.documentReadinessRatePercent}%`,
    ...orderLines
  ].join('\n');
}

export async function runLiveOrFallbackNlQuery(params: {
  query: string;
  orders: ExportOrder[];
  assessments: FinancialAssessment[];
  documents: DocumentRecord[];
  payments: PaymentRecord[];
}): Promise<{
  answerTitle: string;
  answerBullets: string[];
  citedOrders: string[];
  engineLabel: string;
  isLiveGemini: boolean;
}> {
  try {
    const workspaceContext = buildWorkspaceContextSummary(params);
    const response = await fetch('/api/ai/query', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        query: params.query,
        workspaceContext
      })
    });

    if (response.ok) {
      const payload = await response.json();
      if (payload?.liveGemini && payload?.result) {
        return {
          answerTitle: payload.result.answerTitle || 'Grounded Portfolio Analysis',
          answerBullets: Array.isArray(payload.result.answerBullets)
            ? payload.result.answerBullets
            : [],
          citedOrders: Array.isArray(payload.result.citedOrders) ? payload.result.citedOrders : [],
          engineLabel: 'Live Gemini 3.8 Flash (Server-Side API • Grounded in Workspace Records)',
          isLiveGemini: true
        };
      }
    }
  } catch {
    // Fall back cleanly to deterministic query engine
  }

  const deterministic = answerWorkspaceBusinessQuery(params);
  return {
    ...deterministic,
    isLiveGemini: false
  };
}

/**
 * PART 10 — STRUCTURED AI ADVISOR RECOMMENDATIONS
 * Each recommendation strictly identifies:
 * 1. Relevant source records (`sourceRecords`)
 * 2. The issue identified (`issueIdentified`)
 * 3. The reasoning summary (`reasoningSummary`)
 * 4. Recommended next action (`recommendedNextAction`)
 * 5. Whether human approval is required (`requiresHumanApproval`)
 */
export function buildDeterministicAdvisorRecommendations(params: {
  orders: ExportOrder[];
  assessments: FinancialAssessment[];
  documents: DocumentRecord[];
  payments: PaymentRecord[];
}): {
  executiveSummary: string;
  recommendations: StructuredAIRecommendation[];
  engineLabel: string;
  isLiveGemini: boolean;
} {
  const { orders, assessments, documents, payments } = params;
  const metrics = calculatePortfolioMetrics(orders, assessments, documents, payments);
  const receivables = calculateReceivablesAndCurrencyAnalytics(
    orders,
    assessments,
    documents,
    payments
  );
  const assessmentMap = new Map(assessments.map((a) => [a.exportOrderId, a]));
  const recommendations: StructuredAIRecommendation[] = [];

  for (const order of orders) {
    const orderDocs = documents.filter((d) => d.exportOrderId === order.id);
    const orderPayments = payments.filter((p) => p.exportOrderId === order.id);
    const assessment = assessmentMap.get(order.id);

    // 1. Document Completeness & Compliance Blocker Analysis
    const rejectedDocs = orderDocs.filter(
      (d) => d.status === 'REJECTED' || d.status === 'REUPLOAD_REQUIRED'
    );
    if (rejectedDocs.length > 0) {
      recommendations.push({
        id: `adv_doc_rej_${order.id}`,
        category: 'DOCUMENT_COMPLETENESS',
        severity: 'CRITICAL',
        sourceRecords: [order.orderNumber, ...rejectedDocs.map((d) => `${d.id} (${d.documentType})`)],
        issueIdentified: `${rejectedDocs.length} mandatory export document(s) flagged [${rejectedDocs
          .map((d) => d.status)
          .join(', ')}] on ${order.orderNumber} (${order.buyerName}).`,
        reasoningSummary: `${rejectedDocs
          .map((d) => `${d.documentType}: ${d.reviewerNote}`)
          .join(' | ')}. Customs LEO / vessel gate-in at ${
          order.destinationPort
        } cannot proceed until a corrected PDF is uploaded and SHA-256 checksum is re-verified.`,
        recommendedNextAction: `Open Document Centre for ${order.orderNumber}, attach the corrected PDF (≤15 MB), and submit for Compliance Officer verification.`,
        requiresHumanApproval: true,
        approvalRole: 'Exporter Compliance Officer (AI cannot certify documents)',
        targetScreenRoute: 'documents',
        orderId: order.id
      });
    }

    // 2. Pending Mandatory Pre-Shipment Documentation
    const missingRequired = orderDocs.filter(
      (d) => d.requirementLevel === 'REQUIRED' && !isDocumentVerifiedOrApproved(d.status)
    );
    if (missingRequired.length > 0 && rejectedDocs.length === 0) {
      recommendations.push({
        id: `adv_shipment_docs_${order.id}`,
        category: 'SHIPMENT_READINESS',
        severity: 'WARNING',
        sourceRecords: [order.orderNumber, ...missingRequired.map((d) => `${d.id} (${d.documentType})`)],
        issueIdentified: `Shipment readiness for ${order.orderNumber} (${order.destinationPort}, Expected: ${order.expectedShipmentDate}) is blocked by ${missingRequired.length} unverified mandatory document(s).`,
        reasoningSummary: `Mandatory checklist items [${missingRequired
          .map((d) => `${d.documentType}: ${d.status}`)
          .join(', ')}] are incomplete. Digital Twin Stage 5 requires 7/7 mandatory documents verified before Stage 8 completion.`,
        recommendedNextAction: `Inspect Shipment Intelligence & Document Centre for ${order.orderNumber} to complete pre-shipment document verification.`,
        requiresHumanApproval: true,
        approvalRole: 'Export Operations Lead & Compliance Reviewer',
        targetScreenRoute: 'shipment_intel',
        orderId: order.id
      });
    }

    // 3. Financial & Receivable Settlement Exposure Analysis
    const capturedAmount = orderPayments
      .filter((p) => isPaymentSettled(p.paymentStatus))
      .reduce((sum, p) => sum + p.amount, 0);
    const pendingPays = orderPayments.filter(
      (p) =>
        p.paymentStatus === 'DEMO_PENDING' ||
        p.paymentStatus === 'PENDING_APPROVAL' ||
        p.paymentStatus === 'APPROVED'
    );
    const expectedAdvance = round2(order.orderValue * (order.advancePercentage / 100));
    const fx = assessment?.exchangeRate ?? 84.2;

    if (capturedAmount < expectedAdvance) {
      const uncollectedInr = round2((order.orderValue - capturedAmount) * fx);
      recommendations.push({
        id: `adv_fin_pay_${order.id}`,
        category: 'FINANCIAL_RISK',
        severity: capturedAmount === 0 ? 'CRITICAL' : 'WARNING',
        sourceRecords: [
          order.orderNumber,
          ...(assessment ? [assessment.id] : []),
          ...orderPayments.map((p) => `${p.id} (${p.paypalOrderId})`)
        ],
        issueIdentified:
          pendingPays.length > 0
            ? `Approved advance payment ${pendingPays[0].paypalOrderId} (${order.currency} ${pendingPays[0].amount.toLocaleString()}) is awaiting capture on ${order.orderNumber}.`
            : `Zero advance collected against ${order.orderNumber} (${order.advancePercentage}% expected = ${order.currency} ${expectedAdvance.toLocaleString()}).`,
        reasoningSummary: `Total uncollected contract exposure is ${order.currency} ${round2(
          order.orderValue - capturedAmount
        ).toLocaleString()} (₹${formatInrShort(uncollectedInr)} @ FX ${fx}). Contract terms require ${
          order.paymentTerms
        } prior to cargo release.`,
        recommendedNextAction:
          pendingPays.length > 0
            ? `Review ${pendingPays[0].paypalOrderId} in Payment Hub and execute human-authorized capture.`
            : `Initialize the ${order.advancePercentage}% advance payment order (${order.currency} ${expectedAdvance.toLocaleString()}) in Payment Hub.`,
        requiresHumanApproval: true,
        approvalRole: 'Exporter Treasury & Finance Approver (AI cannot capture payments)',
        targetScreenRoute: 'payments',
        orderId: order.id
      });
    }

    // 4. Order Profitability & FX Sensitivity Analysis
    if (assessment) {
      const feeSharePct =
        assessment.convertedValueInr > 0
          ? round2((assessment.paymentFeeInr / assessment.convertedValueInr) * 100)
          : 0;
      if (assessment.profitMarginPercent < 17 || feeSharePct >= 3.8) {
        recommendations.push({
          id: `adv_order_margin_${order.id}`,
          category: 'ORDER_ANALYSIS',
          severity: assessment.profitMarginPercent < 12 ? 'WARNING' : 'ADVISORY',
          sourceRecords: [order.orderNumber, assessment.id],
          issueIdentified: `${order.orderNumber} (${order.currency} ${order.orderValue.toLocaleString()}) carries a ${
            assessment.profitMarginPercent
          }% net margin (₹${formatInrShort(
            assessment.estimatedProfitInr
          )}) with ₹${formatInrShort(assessment.paymentFeeInr)} (${feeSharePct}%) in gateway fees.`,
          reasoningSummary: `Converted gross value is ₹${formatInrShort(
            assessment.convertedValueInr
          )} @ FX ${assessment.exchangeRate}, while procurement (₹${formatInrShort(
            assessment.procurementCostInr
          )}) and logistics (₹${formatInrShort(
            assessment.logisticsCostInr
          )}) leave ${assessment.profitMarginPercent}% net margin.`,
          recommendedNextAction: `Use the isolated What-If Financial Simulator on ${order.orderNumber} to evaluate a ±5% FX hedge and CAD bank wire settlement for the ${
            100 - order.advancePercentage
          }% balance.`,
          requiresHumanApproval: true,
          approvalRole: 'CFO / Trade Finance Controller (AI never modifies saved assessments)',
          targetScreenRoute: 'financials',
          orderId: order.id
        });
      }
    }
  }

  // 5. Portfolio Performance Summary Recommendation
  recommendations.push({
    id: 'adv_portfolio_summary',
    category: 'PORTFOLIO_PERFORMANCE',
    severity: 'ADVISORY',
    sourceRecords: orders.map((o) => o.orderNumber),
    issueIdentified: `Portfolio spans ${metrics.totalOrders} export orders worth ₹${formatInrShort(
      metrics.totalOrderValueInr
    )} across ${Object.keys(metrics.countryDistribution).length} destination countries with ₹${formatInrShort(
      receivables.uncollectedExposureInr
    )} uncollected receivables.`,
    reasoningSummary: `Simulated/captured settlements stand at ₹${formatInrShort(
      metrics.capturedPaymentValueInr
    )} (${metrics.paymentCompletionRatePercent}% completion), average net margin is ${
      metrics.averageProfitMarginPercent
    }%, and mandatory document readiness is ${metrics.documentReadinessRatePercent}%.`,
    recommendedNextAction:
      'Review the Operational Command Centre and Receivables Intelligence dashboard to clear pending pre-shipment document and advance gates.',
    requiresHumanApproval: false,
    approvalRole: 'Informational Portfolio Telemetry (Human approval required before any mutation)',
    targetScreenRoute: 'command_centre'
  });

  const executiveSummary = `Analyzed ${metrics.totalOrders} export orders (Total Value: ₹${formatInrShort(
    metrics.totalOrderValueInr
  )} • Estimated Net Profit: ₹${formatInrShort(
    metrics.totalEstimatedProfitInr
  )} @ ${metrics.averageProfitMarginPercent}% avg margin). Identified ${
    recommendations.filter((r) => r.severity === 'CRITICAL').length
  } critical blockers and ${
    recommendations.filter((r) => r.severity === 'WARNING').length
  } operational warnings requiring human review.`;

  return {
    executiveSummary,
    recommendations,
    engineLabel: 'Deterministic Autonomous Trade Advisor (Fallback — Grounded in Domain Engine)',
    isLiveGemini: false
  };
}

export async function requestLiveOrFallbackPortfolioAdvisory(params: {
  orders: ExportOrder[];
  assessments: FinancialAssessment[];
  documents: DocumentRecord[];
  payments: PaymentRecord[];
  focusArea?: string;
}): Promise<{
  executiveSummary: string;
  recommendations: StructuredAIRecommendation[];
  engineLabel: string;
  isLiveGemini: boolean;
}> {
  const fallback = buildDeterministicAdvisorRecommendations(params);
  try {
    const workspaceContext = buildWorkspaceContextSummary(params);
    const response = await fetch('/api/ai/advisor', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        workspaceContext,
        focusArea: params.focusArea || 'ALL'
      })
    });

    if (response.ok) {
      const payload = await response.json();
      if (
        payload?.liveGemini &&
        payload?.advisory &&
        Array.isArray(payload.advisory.recommendations) &&
        payload.advisory.recommendations.length > 0
      ) {
        const mapped: StructuredAIRecommendation[] = payload.advisory.recommendations.map(
          (rec: Record<string, unknown>, idx: number) => ({
            id: String(rec.id || `gemini_rec_${idx}`),
            category:
              (rec.category as StructuredAIRecommendation['category']) || 'PORTFOLIO_PERFORMANCE',
            severity:
              rec.severity === 'CRITICAL' || rec.severity === 'WARNING'
                ? rec.severity
                : 'ADVISORY',
            sourceRecords: Array.isArray(rec.sourceRecords)
              ? rec.sourceRecords.map(String)
              : ['PORTFOLIO'],
            issueIdentified: String(rec.issueIdentified || ''),
            reasoningSummary: String(rec.reasoningSummary || ''),
            recommendedNextAction: String(rec.recommendedNextAction || ''),
            requiresHumanApproval: Boolean(rec.requiresHumanApproval ?? true),
            approvalRole: String(
              rec.approvalRole || 'Exporter Compliance / Finance Approver'
            ),
            targetScreenRoute: 'command_centre'
          })
        );

        return {
          executiveSummary: String(
            payload.advisory.executiveSummary || fallback.executiveSummary
          ),
          recommendations: mapped,
          engineLabel: 'Live Gemini 3.8 Flash Autonomous Advisor (Server-Side API)',
          isLiveGemini: true
        };
      }
    }
  } catch {
    // Fall back cleanly to deterministic advisor
  }

  return fallback;
}
