import {
  ActivityRecord,
  DocumentRecord,
  ExportOrder,
  FinancialAssessment,
  PaymentRecord,
  WorkspaceMode
} from '../types/models';
import {
  calculatePortfolioMetrics,
  calculateReceivablesAndCurrencyAnalytics,
  defaultExchangeRateForCurrency,
  evaluatePortfolioRisks,
  formatInrShort,
  round2
} from '../domain/financialEngine';
import { SAMPLE_WORKSPACE_PROFILE } from '../data/sampleWorkspaceSeeder';

export type ReportTypeId =
  | 'EXPORT_ORDER_SUMMARY'
  | 'FINANCIAL_PERFORMANCE_SUMMARY'
  | 'RECEIVABLES_REPORT'
  | 'TRADE_RISK_ASSESSMENT'
  | 'DOCUMENT_VERIFICATION_SUMMARY'
  | 'BUSINESS_ACTIVITY_REPORT';

export interface ReportMetadata {
  id: ReportTypeId;
  title: string;
  subtitle: string;
  description: string;
  recordCountLabel: (counts: {
    orders: number;
    assessments: number;
    documents: number;
    payments: number;
    risks: number;
    activities: number;
  }) => string;
}

export const AVAILABLE_REPORTS: ReportMetadata[] = [
  {
    id: 'EXPORT_ORDER_SUMMARY',
    title: 'Export Order Summary Report',
    subtitle: 'Complete consignment register, Incoterms, ports, quantities & lifecycle status',
    description:
      'Lists all export orders in the active workspace with buyer details, HS commodities, contract values, INR equivalents, and extraction sources.',
    recordCountLabel: (c) => `${c.orders} Export Orders`
  },
  {
    id: 'FINANCIAL_PERFORMANCE_SUMMARY',
    title: 'Financial Performance & Margin Report',
    subtitle: 'Deterministic INR conversions, gateway fees, operating cost structure & net margins',
    description:
      'Order-by-order profitability breakdown including spot FX rates, procurement, ocean freight, packaging, payment gateway fees, and net export margins.',
    recordCountLabel: (c) => `${c.assessments} Financial Assessments`
  },
  {
    id: 'RECEIVABLES_REPORT',
    title: 'Receivables & Settlement Exposure Report',
    subtitle: 'Captured vs. simulated demo settlements, pending approvals & currency exposure',
    description:
      'Tracks advance and balance collection status across EUR, USD, AED, GBP, and JPY with explicit distinction between simulated demo captures and live settlements.',
    recordCountLabel: (c) => `${c.payments} Payment Records • ${c.orders} Orders`
  },
  {
    id: 'TRADE_RISK_ASSESSMENT',
    title: 'Trade-Risk & Compliance Assessment Report',
    subtitle: 'Documentation, settlement, and margin risk alerts with evidence & mitigation',
    description:
      'Generated from the deterministic TradeRiskEngine, detailing critical and warning flags across mandatory export documents, unpaid advances, and thin margins.',
    recordCountLabel: (c) => `${c.risks} Active Risk Alerts`
  },
  {
    id: 'DOCUMENT_VERIFICATION_SUMMARY',
    title: 'Document Verification & SHA-256 Audit Report',
    subtitle: 'Mandatory pre-shipment checklist statuses, file versions & cryptographic hashes',
    description:
      'Complete audit trail of all mandatory and optional export documents, reviewer notes, version numbers, and Web Crypto SHA-256 integrity checksums.',
    recordCountLabel: (c) => `${c.documents} Document Records`
  },
  {
    id: 'BUSINESS_ACTIVITY_REPORT',
    title: 'Business Activity & Immutable Audit Log',
    subtitle: 'Chronological operational, financial, document, and payment event history',
    description:
      'Full chronological log of AI PO extractions, financial assessments, document attachments, and simulated payment state transitions.',
    recordCountLabel: (c) => `${c.activities} Audit Events`
  }
];

function escapeCsvCell(value: string | number | boolean | null | undefined): string {
  const str = value === null || value === undefined ? '' : String(value);
  if (str.includes(',') || str.includes('"') || str.includes('\n')) {
    return `"${str.replace(/"/g, '""')}"`;
  }
  return str;
}

export function buildReportData(params: {
  reportType: ReportTypeId;
  workspaceMode: WorkspaceMode;
  prodUserEmail: string;
  orders: ExportOrder[];
  assessments: FinancialAssessment[];
  documents: DocumentRecord[];
  payments: PaymentRecord[];
  activities: ActivityRecord[];
  generatedAtIso?: string;
}): {
  title: string;
  generatedAt: string;
  workspaceClassificationBanner: string;
  companyHeader: string;
  summaryMetrics: Array<{ label: string; value: string }>;
  headers: string[];
  rows: string[][];
} {
  const generatedAt = params.generatedAtIso || new Date().toISOString();
  const isDemo = params.workspaceMode === 'SAMPLE_BUSINESS';
  const workspaceClassificationBanner = isDemo
    ? 'DEMO DATA CLASSIFICATION: SAMPLE BUSINESS WORKSPACE (Tamil Nadu Global Exports Pvt. Ltd.) — Contains pre-loaded sample export orders, locally stored documents, and SIMULATED PAYMENTS (DEMO_CAPTURED / DEMO_PENDING).'
    : `PRODUCTION DATA CLASSIFICATION: ISOLATED PRODUCTION WORKSPACE (${params.prodUserEmail}) — Contains user-created production records.`;

  const companyHeader = isDemo
    ? `${SAMPLE_WORKSPACE_PROFILE.companyName} | ${SAMPLE_WORKSPACE_PROFILE.iecNumber} | ${SAMPLE_WORKSPACE_PROFILE.gstin}`
    : `Authenticated Exporter (${params.prodUserEmail}) | Production Ledger`;

  const assessmentMap = new Map(params.assessments.map((a) => [a.exportOrderId, a]));
  const portfolioMetrics = calculatePortfolioMetrics(
    params.orders,
    params.assessments,
    params.documents,
    params.payments
  );
  const receivables = calculateReceivablesAndCurrencyAnalytics(
    params.orders,
    params.assessments,
    params.documents,
    params.payments
  );
  const risks = evaluatePortfolioRisks(
    params.orders,
    params.assessments,
    params.documents,
    params.payments
  );

  switch (params.reportType) {
    case 'EXPORT_ORDER_SUMMARY': {
      const headers = [
        'Order Number',
        'Order Date',
        'Expected Shipment',
        'Buyer Name',
        'Destination Country',
        'Destination Port',
        'Product / HS Code',
        'Quantity',
        'Unit',
        'Unit Price',
        'Currency',
        'Gross Order Value',
        'Converted INR Value',
        'Incoterm',
        'Advance %',
        'Order Status',
        'Extraction Source',
        'Data Mode'
      ];
      const rows = params.orders.map((o) => {
        const a = assessmentMap.get(o.id);
        const inrVal =
          a?.convertedValueInr ??
          round2(o.orderValue * defaultExchangeRateForCurrency(o.currency));
        return [
          o.orderNumber,
          o.orderDate,
          o.expectedShipmentDate,
          o.buyerName,
          o.buyerCountry,
          o.destinationPort,
          o.productName,
          String(o.quantity),
          o.unit,
          o.unitPrice.toFixed(2),
          o.currency,
          o.orderValue.toFixed(2),
          inrVal.toFixed(2),
          o.incoterm,
          `${o.advancePercentage}%`,
          o.orderStatus,
          o.extractionSource,
          isDemo ? 'SAMPLE_DEMO' : 'PRODUCTION'
        ];
      });
      return {
        title: 'Export Order Summary Report',
        generatedAt,
        workspaceClassificationBanner,
        companyHeader,
        summaryMetrics: [
          { label: 'Total Orders', value: String(portfolioMetrics.totalOrders) },
          { label: 'Active / Completed', value: `${portfolioMetrics.activeOrders} / ${portfolioMetrics.completedOrders}` },
          { label: 'Total Portfolio Value (INR)', value: `₹${formatInrShort(portfolioMetrics.totalOrderValueInr)}` },
          { label: 'Data Classification', value: isDemo ? 'SAMPLE DEMO DATA' : 'PRODUCTION DATA' }
        ],
        headers,
        rows
      };
    }

    case 'FINANCIAL_PERFORMANCE_SUMMARY': {
      const headers = [
        'Order ID',
        'Assessment ID',
        'Currency',
        'Gross Value (Foreign)',
        'Exchange Rate (INR)',
        'Converted Value (INR)',
        'Gateway Fee (INR)',
        'Net Revenue (INR)',
        'Procurement Cost (INR)',
        'Logistics Cost (INR)',
        'Packaging Cost (INR)',
        'Other Costs (INR)',
        'Estimated Net Profit (INR)',
        'Net Profit Margin (%)',
        'Assumptions Note'
      ];
      const rows = params.orders.map((o) => {
        const a = assessmentMap.get(o.id);
        if (!a) {
          return [
            o.orderNumber,
            'UNASSESSED',
            o.currency,
            o.orderValue.toFixed(2),
            String(defaultExchangeRateForCurrency(o.currency)),
            round2(o.orderValue * defaultExchangeRateForCurrency(o.currency)).toFixed(2),
            'Insufficient data',
            'Insufficient data',
            'Insufficient data',
            'Insufficient data',
            'Insufficient data',
            'Insufficient data',
            'Insufficient data',
            'Insufficient data',
            'Assessment not yet saved'
          ];
        }
        return [
          o.orderNumber,
          a.id,
          a.currency,
          a.grossOrderValue.toFixed(2),
          String(a.exchangeRate),
          a.convertedValueInr.toFixed(2),
          a.paymentFeeInr.toFixed(2),
          a.estimatedNetRevenueInr.toFixed(2),
          a.procurementCostInr.toFixed(2),
          a.logisticsCostInr.toFixed(2),
          a.packagingCostInr.toFixed(2),
          a.otherCostsInr.toFixed(2),
          a.estimatedProfitInr.toFixed(2),
          `${a.profitMarginPercent.toFixed(2)}%`,
          a.assumptionsNote
        ];
      });
      return {
        title: 'Financial Performance & Profitability Summary',
        generatedAt,
        workspaceClassificationBanner,
        companyHeader,
        summaryMetrics: [
          { label: 'Total Gross Value (INR)', value: `₹${formatInrShort(portfolioMetrics.totalOrderValueInr)}` },
          { label: 'Total Estimated Profit (INR)', value: `₹${formatInrShort(portfolioMetrics.totalEstimatedProfitInr)}` },
          { label: 'Average Net Margin', value: `${portfolioMetrics.averageProfitMarginPercent}%` },
          { label: 'Data Classification', value: isDemo ? 'SAMPLE DEMO DATA' : 'PRODUCTION DATA' }
        ],
        headers,
        rows
      };
    }

    case 'RECEIVABLES_REPORT': {
      const headers = [
        'Order Number',
        'Buyer Name',
        'Currency',
        'FX Rate',
        'Contract Value (Foreign)',
        'Converted Value (INR)',
        'Expected Advance (Foreign)',
        'Live Captured (INR)',
        'Simulated Demo Captured (INR)',
        'Pending Approval (INR)',
        'Uncollected Exposure (Foreign)',
        'Uncollected Exposure (INR)',
        'Aging / Milestone Category',
        'Due Date / Terms Basis'
      ];
      const rows = receivables.orderBreakdowns.map((b) => [
        b.order.orderNumber,
        b.order.buyerName,
        b.currency,
        String(b.exchangeRate),
        b.grossOrderValueForeign.toFixed(2),
        b.convertedOrderValueInr.toFixed(2),
        b.expectedAdvanceForeign.toFixed(2),
        b.liveCapturedInr.toFixed(2),
        b.simulatedCapturedInr.toFixed(2),
        b.pendingApprovalInr.toFixed(2),
        b.uncollectedExposureForeign.toFixed(2),
        b.uncollectedExposureInr.toFixed(2),
        b.agingCategoryLabel,
        b.dueDateStatusLabel
      ]);
      return {
        title: 'Receivables & Cash-Flow Exposure Report',
        generatedAt,
        workspaceClassificationBanner,
        companyHeader,
        summaryMetrics: [
          { label: 'Total Portfolio Value', value: `₹${formatInrShort(portfolioMetrics.totalOrderValueInr)}` },
          { label: 'Simulated Demo Captured', value: `₹${formatInrShort(receivables.simulatedCapturedInr)}` },
          { label: 'Live Captured Funds', value: `₹${formatInrShort(receivables.liveCapturedInr)}` },
          { label: 'Outstanding Receivables', value: `₹${formatInrShort(receivables.uncollectedExposureInr)}` }
        ],
        headers,
        rows
      };
    }

    case 'TRADE_RISK_ASSESSMENT': {
      const headers = [
        'Risk ID',
        'Order Number',
        'Category',
        'Severity',
        'Risk Title',
        'Factual Evidence',
        'Recommended Mitigation Action',
        'Target Module'
      ];
      const rows = risks.map((r) => [
        r.id,
        r.orderNumber,
        r.category,
        r.severity,
        r.title,
        r.evidence,
        r.recommendedAction,
        r.targetScreenRoute
      ]);
      return {
        title: 'Trade-Risk & Compliance Assessment Report',
        generatedAt,
        workspaceClassificationBanner,
        companyHeader,
        summaryMetrics: [
          { label: 'Total Risk Alerts', value: String(risks.length) },
          { label: 'Critical Risks', value: String(risks.filter((r) => r.severity === 'CRITICAL').length) },
          { label: 'Warning Risks', value: String(risks.filter((r) => r.severity === 'WARNING').length) },
          { label: 'Advisory Notices', value: String(risks.filter((r) => r.severity === 'ADVISORY').length) }
        ],
        headers,
        rows
      };
    }

    case 'DOCUMENT_VERIFICATION_SUMMARY': {
      const headers = [
        'Document ID',
        'Order ID',
        'Document Type',
        'Requirement Level',
        'Status',
        'File Name',
        'Version',
        'Size (Bytes)',
        'SHA-256 Checksum',
        'Storage Scope',
        'Reviewer Note'
      ];
      const rows = params.documents.map((d) => [
        d.id,
        d.exportOrderId,
        d.documentType,
        d.requirementLevel,
        d.status,
        d.fileName || 'MISSING',
        `v${d.versionNumber}`,
        String(d.fileSizeBytes),
        d.sha256Checksum || 'Not computed (No file)',
        'BROWSER_LOCAL_UNOFFICIAL',
        d.reviewerNote
      ]);
      return {
        title: 'Document Verification & SHA-256 Checksum Summary',
        generatedAt,
        workspaceClassificationBanner,
        companyHeader,
        summaryMetrics: [
          { label: 'Total Checklist Items', value: String(params.documents.length) },
          { label: 'Mandatory Readiness Rate', value: `${portfolioMetrics.documentReadinessRatePercent}%` },
          {
            label: 'Flagged for Re-Upload',
            value: String(
              params.documents.filter(
                (d) => d.status === 'REJECTED' || d.status === 'REUPLOAD_REQUIRED'
              ).length
            )
          },
          { label: 'Certification Status', value: 'LOCALLY VERIFIED (NOT GOVT CERTIFIED)' }
        ],
        headers,
        rows
      };
    }

    case 'BUSINESS_ACTIVITY_REPORT': {
      const headers = [
        'Activity ID',
        'Order ID',
        'Event Type',
        'Timestamp (ISO)',
        'Headline',
        'Detailed Audit Description',
        'Workspace Mode'
      ];
      const rows = params.activities.map((act) => [
        act.id,
        act.exportOrderId,
        act.eventType,
        act.createdAtEpochMs ? new Date(act.createdAtEpochMs).toISOString() : generatedAt,
        act.title,
        act.description,
        isDemo ? 'SAMPLE_BUSINESS_DEMO' : 'PRODUCTION'
      ]);
      return {
        title: 'Business Activity & Immutable Audit History Report',
        generatedAt,
        workspaceClassificationBanner,
        companyHeader,
        summaryMetrics: [
          { label: 'Total Audit Events', value: String(params.activities.length) },
          { label: 'Orders Covered', value: String(params.orders.length) },
          { label: 'Generation Time', value: generatedAt.slice(0, 19).replace('T', ' ') + ' UTC' },
          { label: 'Data Classification', value: isDemo ? 'SAMPLE DEMO DATA' : 'PRODUCTION DATA' }
        ],
        headers,
        rows
      };
    }
  }
}

export function downloadReportAsCsv(params: {
  reportType: ReportTypeId;
  workspaceMode: WorkspaceMode;
  prodUserEmail: string;
  orders: ExportOrder[];
  assessments: FinancialAssessment[];
  documents: DocumentRecord[];
  payments: PaymentRecord[];
  activities: ActivityRecord[];
}): string {
  const report = buildReportData(params);
  const metaLines = [
    `# REPORT TITLE,${escapeCsvCell(report.title)}`,
    `# GENERATED AT (ISO),${escapeCsvCell(report.generatedAt)}`,
    `# WORKSPACE CLASSIFICATION,${escapeCsvCell(report.workspaceClassificationBanner)}`,
    `# ENTITY HEADER,${escapeCsvCell(report.companyHeader)}`,
    ''
  ];

  const headerLine = report.headers.map(escapeCsvCell).join(',');
  const dataLines = report.rows.map((r) => r.map(escapeCsvCell).join(','));
  const csvContent = '\uFEFF' + [...metaLines, headerLine, ...dataLines].join('\n');

  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  const datePart = report.generatedAt.slice(0, 10);
  const modeTag = params.workspaceMode === 'SAMPLE_BUSINESS' ? 'SAMPLE_DEMO' : 'PRODUCTION';
  const fileName = `NiryatFlow_${params.reportType}_${modeTag}_${datePart}.csv`;

  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);

  return fileName;
}

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/**
 * Generates a print-friendly PDF report using a hidden iframe (never uses window.open).
 */
export function triggerPrintFriendlyPdfReport(params: {
  reportType: ReportTypeId;
  workspaceMode: WorkspaceMode;
  prodUserEmail: string;
  orders: ExportOrder[];
  assessments: FinancialAssessment[];
  documents: DocumentRecord[];
  payments: PaymentRecord[];
  activities: ActivityRecord[];
}): void {
  const report = buildReportData(params);

  const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <title>${escapeHtml(report.title)} — NiryatFlow AI</title>
  <style>
    @page { size: landscape; margin: 14mm; }
    body {
      font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, sans-serif;
      color: #141413;
      background: #FFFFFF;
      margin: 0;
      padding: 0;
      font-size: 11px;
    }
    .header-box {
      border-bottom: 2px solid #C69C3A;
      padding-bottom: 10px;
      margin-bottom: 12px;
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
    }
    .title {
      font-size: 18px;
      font-weight: 700;
      margin: 0 0 4px 0;
    }
    .subtitle {
      font-size: 11px;
      color: #5C5A55;
      margin: 0;
    }
    .classification-banner {
      background: #FAF6EC;
      border: 1px solid #C69C3A;
      padding: 8px 12px;
      border-radius: 6px;
      font-weight: 700;
      font-size: 10.5px;
      margin-bottom: 12px;
    }
    .metrics-row {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 10px;
      margin-bottom: 14px;
    }
    .metric-card {
      border: 1px solid #E6E0D6;
      border-radius: 6px;
      padding: 8px 10px;
      background: #FAF8F5;
    }
    .metric-label {
      font-size: 9.5px;
      text-transform: uppercase;
      color: #8C8881;
      font-weight: 700;
    }
    .metric-value {
      font-family: 'JetBrains Mono', monospace;
      font-size: 13px;
      font-weight: 700;
      margin-top: 3px;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      font-size: 10px;
    }
    th {
      background: #F3EFEA;
      color: #141413;
      text-align: left;
      padding: 6px 8px;
      border: 1px solid #D4CBBF;
      font-weight: 700;
      text-transform: uppercase;
      font-size: 9px;
    }
    td {
      padding: 6px 8px;
      border: 1px solid #E6E0D6;
      vertical-align: top;
      word-break: break-word;
    }
    tr:nth-child(even) td {
      background: #FAF8F5;
    }
    .footer {
      margin-top: 14px;
      font-size: 9.5px;
      color: #8C8881;
      display: flex;
      justify-content: space-between;
      border-top: 1px solid #E6E0D6;
      padding-top: 8px;
    }
  </style>
</head>
<body>
  <div class="header-box">
    <div>
      <div class="title">NiryatFlow AI — ${escapeHtml(report.title)}</div>
      <div class="subtitle">${escapeHtml(report.companyHeader)}</div>
    </div>
    <div style="text-align: right; font-family: monospace; font-size: 10px;">
      <div><strong>Generated:</strong> ${escapeHtml(report.generatedAt)}</div>
      <div><strong>Records:</strong> ${report.rows.length} row(s)</div>
    </div>
  </div>

  <div class="classification-banner">
    ${escapeHtml(report.workspaceClassificationBanner)}
  </div>

  <div class="metrics-row">
    ${report.summaryMetrics
      .map(
        (m) => `
      <div class="metric-card">
        <div class="metric-label">${escapeHtml(m.label)}</div>
        <div class="metric-value">${escapeHtml(m.value)}</div>
      </div>`
      )
      .join('')}
  </div>

  <table>
    <thead>
      <tr>
        ${report.headers.map((h) => `<th>${escapeHtml(h)}</th>`).join('')}
      </tr>
    </thead>
    <tbody>
      ${report.rows
        .map(
          (row) =>
            `<tr>${row.map((cell) => `<td>${escapeHtml(cell)}</td>`).join('')}</tr>`
        )
        .join('')}
    </tbody>
  </table>

  <div class="footer">
    <span>NiryatFlow AI Export Operations & Financial Intelligence Platform • Deterministic Domain Ledger</span>
    <span>Generated at ${escapeHtml(report.generatedAt)} • Source records preserved</span>
  </div>
</body>
</html>`;

  const existingIframe = document.getElementById('niryatflow-print-frame');
  if (existingIframe && existingIframe.parentNode) {
    existingIframe.parentNode.removeChild(existingIframe);
  }

  const iframe = document.createElement('iframe');
  iframe.id = 'niryatflow-print-frame';
  iframe.style.position = 'fixed';
  iframe.style.right = '0';
  iframe.style.bottom = '0';
  iframe.style.width = '0';
  iframe.style.height = '0';
  iframe.style.border = '0';
  document.body.appendChild(iframe);

  const doc = iframe.contentWindow?.document;
  if (doc) {
    doc.open();
    doc.write(html);
    doc.close();
    setTimeout(() => {
      iframe.contentWindow?.focus();
      iframe.contentWindow?.print();
    }, 250);
  }
}
