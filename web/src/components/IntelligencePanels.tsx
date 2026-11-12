import React, { useState } from 'react';
import {
  ExportOrder,
  FinancialAssessment,
  DocumentRecord,
  PaymentRecord,
  ActivityRecord,
  WorkspaceMode,
  WebNavTab,
  ShipmentIntelligenceItem,
  OrderReceivableBreakdown,
  CurrencyExposureSummary,
  BuyerAnalyticsItem,
  StructuredAIRecommendation,
  TradeRiskAlert,
  PerformanceMetrics
} from '../types/models';
import { formatInrShort, round2 } from '../domain/financialEngine';
import {
  AVAILABLE_REPORTS,
  ReportTypeId,
  buildReportData,
  downloadReportAsCsv,
  triggerPrintFriendlyPdfReport
} from '../services/reportingService';

interface CommandCentreProps {
  pendingPayments: PaymentRecord[];
  paymentExceptions: PaymentRecord[];
  incompleteDocs: DocumentRecord[];
  attentionOrders: ShipmentIntelligenceItem[];
  riskAlerts: TradeRiskAlert[];
  activities: ActivityRecord[];
  onNavigate: (tab: WebNavTab, orderId?: string) => void;
}

export const CommandCentreView: React.FC<CommandCentreProps> = ({
  pendingPayments,
  paymentExceptions,
  incompleteDocs,
  attentionOrders,
  riskAlerts,
  activities,
  onNavigate
}) => {
  const criticalAndWarningRisks = riskAlerts.filter(
    (r) => r.severity === 'CRITICAL' || r.severity === 'WARNING'
  );

  return (
    <div>
      <div style={{ marginBottom: '1.25rem' }}>
        <h1 style={{ fontSize: '1.6rem', fontWeight: 700 }}>Operational Command Centre</h1>
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.86rem' }}>
          Centralized operations control surfacing pending approvals, orders requiring attention, incomplete compliance documentation, payment exceptions, risk alerts, and workflow milestones.
        </p>
      </div>

      <div className="grid-4" style={{ marginBottom: '1.25rem' }}>
        <div className="kpi-card">
          <div className="kpi-label">Pending Remittance Approvals</div>
          <div className="kpi-value" style={{ color: 'var(--amber-primary)' }}>
            {pendingPayments.length}
          </div>
          <div className="kpi-sub">Awaiting bank/treasury verification</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-label">Orders Requiring Attention</div>
          <div className="kpi-value" style={{ color: 'var(--crimson-primary)' }}>
            {attentionOrders.length}
          </div>
          <div className="kpi-sub">Blocked or approaching shipment gate</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-label">Incomplete Documentation</div>
          <div className="kpi-value" style={{ color: 'var(--amber-primary)' }}>
            {incompleteDocs.length}
          </div>
          <div className="kpi-sub">Missing, Rejected, or Pending Review</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-label">Critical &amp; Warning Risk Alerts</div>
          <div className="kpi-value" style={{ color: 'var(--crimson-primary)' }}>
            {criticalAndWarningRisks.length}
          </div>
          <div className="kpi-sub">Active compliance &amp; settlement flags</div>
        </div>
      </div>

      <div className="grid-2" style={{ marginBottom: '1.25rem' }}>
        <div className="surface-card">
          <h3 style={{ fontSize: '0.98rem', fontWeight: 700, marginBottom: '0.75rem' }}>
            1. Pending Approvals &amp; Payment Exceptions
          </h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
            {[...pendingPayments, ...paymentExceptions].length === 0 ? (
              <div style={{ color: 'var(--text-muted)', fontSize: '0.84rem' }}>
                No pending payment approvals or payment exceptions.
              </div>
            ) : (
              [...pendingPayments, ...paymentExceptions].map((pay) => (
                <div
                  key={pay.id}
                  style={{
                    padding: '0.75rem',
                    borderRadius: 'var(--radius-sm)',
                    border: '1px solid var(--border-subtle)',
                    backgroundColor: 'var(--bg-subtle)',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    gap: '0.75rem'
                  }}
                >
                  <div>
                    <div style={{ fontWeight: 700, fontSize: '0.84rem' }}>
                      {pay.exportOrderId} — {pay.currency} {pay.amount.toLocaleString()} ({pay.paymentType})
                    </div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                      {pay.verificationDetails}
                    </div>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem' }}>
                    <span className="badge badge-amber">{pay.paymentStatus}</span>
                    <button
                      type="button"
                      className="btn btn-outline btn-xs"
                      onClick={() => onNavigate('payments', pay.exportOrderId)}
                    >
                      Resolve
                    </button>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        <div className="surface-card">
          <h3 style={{ fontSize: '0.98rem', fontWeight: 700, marginBottom: '0.75rem' }}>
            2. Orders Requiring Immediate Operational Attention
          </h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
            {attentionOrders.map((item) => (
              <div
                key={item.order.id}
                style={{
                  padding: '0.75rem',
                  borderRadius: 'var(--radius-sm)',
                  border: '1px solid var(--crimson-border)',
                  backgroundColor: '#FEF2F2',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  gap: '0.75rem'
                }}
              >
                <div>
                  <div style={{ fontWeight: 700, fontSize: '0.84rem' }}>
                    {item.order.orderNumber} — {item.order.buyerName} ({item.shipmentRiskStatus})
                  </div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                    {item.shipmentRiskReason}
                  </div>
                </div>
                <button
                  type="button"
                  className="btn btn-primary btn-xs"
                  onClick={() => onNavigate('shipment_intel', item.order.id)}
                >
                  Inspect Shipment
                </button>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="grid-2">
        <div className="surface-card">
          <h3 style={{ fontSize: '0.98rem', fontWeight: 700, marginBottom: '0.75rem' }}>
            3. Incomplete &amp; Flagged Trade Documentation ({incompleteDocs.length})
          </h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', maxHeight: '320px', overflowY: 'auto' }}>
            {incompleteDocs.slice(0, 12).map((doc) => (
              <div
                key={doc.id}
                style={{
                  padding: '0.6rem 0.75rem',
                  borderRadius: 'var(--radius-sm)',
                  border: '1px solid var(--border-subtle)',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  gap: '0.5rem'
                }}
              >
                <div>
                  <div style={{ fontWeight: 600, fontSize: '0.82rem' }}>
                    {doc.exportOrderId}: {doc.documentType}
                  </div>
                  <div style={{ fontSize: '0.73rem', color: 'var(--text-muted)' }}>
                    {doc.reviewerNote}
                  </div>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                  <span
                    className={`badge ${
                      doc.status === 'MISSING' ||
                      doc.status === 'REJECTED' ||
                      doc.status === 'REUPLOAD_REQUIRED'
                        ? 'badge-crimson'
                        : 'badge-amber'
                    }`}
                  >
                    {doc.status}
                  </span>
                  <button
                    type="button"
                    className="btn btn-outline btn-xs"
                    onClick={() => onNavigate('documents', doc.exportOrderId)}
                  >
                    Open Doc
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="surface-card">
          <h3 style={{ fontSize: '0.98rem', fontWeight: 700, marginBottom: '0.75rem' }}>
            4. Recent Business Activities &amp; Workflow Milestones
          </h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.55rem', maxHeight: '320px', overflowY: 'auto' }}>
            {activities.slice(0, 8).map((act) => (
              <div
                key={act.id}
                style={{
                  padding: '0.6rem 0.75rem',
                  borderRadius: 'var(--radius-sm)',
                  backgroundColor: 'var(--bg-subtle)',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  gap: '0.6rem'
                }}
              >
                <div>
                  <div style={{ fontWeight: 600, fontSize: '0.82rem' }}>
                    <span className="badge badge-gold" style={{ marginRight: '0.4rem' }}>
                      {act.eventType}
                    </span>
                    {act.title}
                  </div>
                  <div style={{ fontSize: '0.74rem', color: 'var(--text-secondary)', marginTop: '0.15rem' }}>
                    {act.description}
                  </div>
                </div>
                {act.exportOrderId && (
                  <button
                    type="button"
                    className="btn btn-outline btn-xs"
                    onClick={() => onNavigate('digital_twin', act.exportOrderId)}
                  >
                    {act.exportOrderId}
                  </button>
                )}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

interface ShipmentIntelProps {
  items: ShipmentIntelligenceItem[];
  onNavigate: (tab: WebNavTab, orderId?: string) => void;
}

export const ShipmentIntelligenceView: React.FC<ShipmentIntelProps> = ({ items, onNavigate }) => {
  return (
    <div>
      <div style={{ marginBottom: '1rem' }}>
        <h1 style={{ fontSize: '1.6rem', fontWeight: 700 }}>
          Shipment &amp; Logistics Intelligence
        </h1>
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.86rem' }}>
          Derived deterministically from the 8-stage Export Order Digital Twin workflow, compliance documentation state, and port loading milestones.
        </p>
      </div>

      <div className="governance-banner simulated">
        <div>
          <strong>OPERATIONAL DATA NOTICE:</strong> Live ocean carrier AIS/GPS vessel tracking, container numbers, and real-time port authority telemetry are not connected. Milestone readiness, stage progression, and logistics exceptions below are derived from the authoritative 8-stage Digital Twin workflow and order records.
        </div>
        <span className="badge badge-indigo">8-Stage Digital Twin Source of Truth</span>
      </div>

      <div className="grid-4" style={{ marginBottom: '1.25rem' }}>
        <div className="kpi-card">
          <div className="kpi-label">Avg Portfolio Readiness</div>
          <div className="kpi-value" style={{ color: 'var(--emerald-primary)' }}>
            {items.length > 0
              ? Math.round(
                  items.reduce((s, i) => s + i.twin.overallProgressPercent, 0) / items.length
                )
              : 0}
            %
          </div>
          <div className="kpi-sub">Across {items.length} export consignments</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-label">Blocked / At-Risk Shipments</div>
          <div className="kpi-value" style={{ color: 'var(--crimson-primary)' }}>
            {items.filter((i) => i.shipmentRiskStatus === 'AT_RISK_BLOCKED').length}
          </div>
          <div className="kpi-sub">Blocked by docs or pending advance</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-label">Pending Shipment Docs</div>
          <div className="kpi-value" style={{ color: 'var(--amber-primary)' }}>
            {items.reduce((s, i) => s + i.pendingDocs.length, 0)}
          </div>
          <div className="kpi-sub">Unverified mandatory trade docs</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-label">Logistics Exceptions</div>
          <div className="kpi-value" style={{ color: 'var(--crimson-primary)' }}>
            {items.reduce((s, i) => s + i.logisticsExceptions.length, 0)}
          </div>
          <div className="kpi-sub">Active pre-shipment blockers</div>
        </div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
        {items.map((item) => (
          <div
            key={item.order.id}
            className={`surface-card ${
              item.shipmentRiskStatus === 'AT_RISK_BLOCKED'
                ? 'crimson-accent'
                : item.shipmentRiskStatus === 'APPROACHING_GATE'
                ? 'amber-accent'
                : ''
            }`}
          >
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'flex-start',
                flexWrap: 'wrap',
                gap: '0.75rem',
                marginBottom: '0.85rem'
              }}
            >
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
                  <span className="mono-code" style={{ fontWeight: 700, fontSize: '0.95rem' }}>
                    {item.order.orderNumber}
                  </span>
                  <span style={{ fontWeight: 700 }}>{item.order.buyerName}</span>
                  <span className="badge badge-neutral">{item.order.incoterm}</span>
                  <span
                    className={`badge ${
                      item.shipmentRiskStatus === 'AT_RISK_BLOCKED'
                        ? 'badge-crimson'
                        : item.shipmentRiskStatus === 'APPROACHING_GATE'
                        ? 'badge-amber'
                        : 'badge-emerald'
                    }`}
                  >
                    {item.shipmentRiskStatus}
                  </span>
                </div>
                <div style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', marginTop: '0.25rem' }}>
                  Destination: <strong>{item.order.destinationPort}</strong> ({item.order.buyerCountry}) | Expected Shipment:{' '}
                  <span className="mono-code">{item.order.expectedShipmentDate}</span> | Active Gate:{' '}
                  <strong>{item.twin.currentStageLabel}</strong>
                </div>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <span className="mono-metric" style={{ fontWeight: 700, fontSize: '0.9rem' }}>
                  Readiness: {item.twin.overallProgressPercent}% (Docs: {item.twin.verifiedRequiredDocs}/{item.twin.totalRequiredDocs})
                </span>
                <button
                  type="button"
                  className="btn btn-outline btn-xs"
                  onClick={() => onNavigate('digital_twin', item.order.id)}
                >
                  Open 8-Stage Twin
                </button>
              </div>
            </div>

            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(8, minmax(110px, 1fr))',
                gap: '0.4rem',
                overflowX: 'auto',
                paddingBottom: '0.4rem',
                marginBottom: '0.75rem'
              }}
            >
              {item.twin.stages.map((st) => (
                <div
                  key={st.stageNumber}
                  style={{
                    padding: '0.5rem',
                    borderRadius: 'var(--radius-sm)',
                    border: '1px solid var(--border-subtle)',
                    backgroundColor:
                      st.status === 'COMPLETED'
                        ? 'var(--emerald-subtle)'
                        : st.status === 'ACTION_REQUIRED'
                        ? 'var(--crimson-subtle)'
                        : st.status === 'IN_PROGRESS'
                        ? 'var(--gold-subtle)'
                        : 'var(--bg-subtle)',
                    fontSize: '0.71rem'
                  }}
                >
                  <div style={{ fontWeight: 700 }}>
                    {st.stageNumber}. {st.title}
                  </div>
                  <div className="mono-code" style={{ fontSize: '0.66rem', marginTop: '0.15rem' }}>
                    {st.status}
                  </div>
                </div>
              ))}
            </div>

            <div className="grid-2" style={{ gap: '0.75rem', fontSize: '0.78rem' }}>
              <div style={{ padding: '0.6rem 0.75rem', backgroundColor: 'var(--bg-subtle)', borderRadius: 'var(--radius-sm)' }}>
                <strong>Logistics Exceptions &amp; Risk Indicator:</strong>
                <div style={{ color: 'var(--text-secondary)', marginTop: '0.2rem' }}>
                  {item.shipmentRiskReason}
                </div>
                {item.logisticsExceptions.length > 0 && (
                  <ul style={{ paddingLeft: '1.1rem', marginTop: '0.25rem', color: 'var(--crimson-primary)' }}>
                    {item.logisticsExceptions.map((ex, i) => (
                      <li key={i}>{ex}</li>
                    ))}
                  </ul>
                )}
              </div>

              <div style={{ padding: '0.6rem 0.75rem', backgroundColor: 'var(--bg-subtle)', borderRadius: 'var(--radius-sm)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <strong>Pending / Flagged Shipment Documents ({item.pendingDocs.length}):</strong>
                  <button
                    type="button"
                    className="btn btn-outline btn-xs"
                    onClick={() => onNavigate('documents', item.order.id)}
                  >
                    Verify Docs
                  </button>
                </div>
                <div style={{ marginTop: '0.3rem', display: 'flex', gap: '0.35rem', flexWrap: 'wrap' }}>
                  {item.pendingDocs.length === 0 ? (
                    <span className="badge badge-emerald">All Mandatory Documents Verified</span>
                  ) : (
                    item.pendingDocs.map((d) => (
                      <span
                        key={d.id}
                        className={`badge ${
                          d.status === 'MISSING' ||
                          d.status === 'REJECTED' ||
                          d.status === 'REUPLOAD_REQUIRED'
                            ? 'badge-crimson'
                            : 'badge-amber'
                        }`}
                      >
                        {d.documentType}: {d.status}
                      </span>
                    ))
                  )}
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

interface ReceivablesProps {
  analytics: {
    portfolioMetrics: PerformanceMetrics;
    liveCapturedInr: number;
    simulatedCapturedInr: number;
    totalSettledOrSimulatedInr: number;
    pendingApprovalInr: number;
    uncollectedExposureInr: number;
    orderBreakdowns: OrderReceivableBreakdown[];
    currencyExposures: CurrencyExposureSummary[];
  };
}

export const ReceivablesIntelligenceView: React.FC<ReceivablesProps> = ({ analytics }) => {
  const [cashFlowCollectionRatePct, setCashFlowCollectionRatePct] = useState<number>(85);
  const [cashFlowFxDriftPct, setCashFlowFxDriftPct] = useState<number>(0);

  const projectedInflow = round2(
    analytics.uncollectedExposureInr *
      (cashFlowCollectionRatePct / 100) *
      (1 + cashFlowFxDriftPct / 100)
  );
  const projectedTotalCash = round2(analytics.totalSettledOrSimulatedInr + projectedInflow);

  return (
    <div>
      <div style={{ marginBottom: '1.25rem' }}>
        <h1 style={{ fontSize: '1.6rem', fontWeight: 700 }}>
          Receivables &amp; Cash Flow Intelligence
        </h1>
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.86rem' }}>
          Dedicated financial operations dashboard tracking live captured settlements, simulated demo settlements, uncollected receivables, foreign currency exposure, and isolated cash-flow scenarios.
        </p>
      </div>

      <div className="governance-banner simulated">
        <div>
          <strong>TREASURY ACCOUNTING RULE:</strong> Simulated payments (<code>DEMO_CAPTURED</code>) are explicitly separated from actual live received funds (<code>CAPTURED</code>). Overdue aging is never inferred unless a formal credit due date exists on the record.
        </div>
        <span className="badge badge-emerald">Strict Treasury Separation</span>
      </div>

      <div className="grid-4" style={{ marginBottom: '1.25rem' }}>
        <div className="kpi-card">
          <div className="kpi-label">Actual Live Captured (CAPTURED)</div>
          <div className="kpi-value" style={{ color: 'var(--emerald-primary)' }}>
            ₹{formatInrShort(analytics.liveCapturedInr)}
          </div>
          <div className="kpi-sub">Verified live bank settlements</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-label">Simulated Demo Captures</div>
          <div className="kpi-value" style={{ color: 'var(--indigo-primary)' }}>
            ₹{formatInrShort(analytics.simulatedCapturedInr)}
          </div>
          <div className="kpi-sub">DEMO_CAPTURED (Sandbox mode)</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-label">Pending Approval Remittances</div>
          <div className="kpi-value" style={{ color: 'var(--amber-primary)' }}>
            ₹{formatInrShort(analytics.pendingApprovalInr)}
          </div>
          <div className="kpi-sub">Awaiting treasury capture</div>
        </div>
        <div className="kpi-card">
          <div className="kpi-label">Uncollected Receivable Exposure</div>
          <div className="kpi-value" style={{ color: 'var(--crimson-primary)' }}>
            ₹{formatInrShort(analytics.uncollectedExposureInr)}
          </div>
          <div className="kpi-sub">Remaining contract exposure</div>
        </div>
      </div>

      <div className="grid-2" style={{ marginBottom: '1.25rem' }}>
        <div className="surface-card">
          <h3 style={{ fontSize: '0.98rem', fontWeight: 700, marginBottom: '0.75rem' }}>
            Foreign Currency Exposure Overview
          </h3>
          <div className="table-scroll">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Currency</th>
                  <th>Orders</th>
                  <th>Total Foreign</th>
                  <th>INR Equivalent</th>
                  <th>Settled / Demo</th>
                  <th>Uncollected Exposure</th>
                  <th>Portfolio %</th>
                </tr>
              </thead>
              <tbody>
                {analytics.currencyExposures.map((ce) => (
                  <tr key={ce.currency}>
                    <td className="mono-code" style={{ fontWeight: 700 }}>
                      {ce.currency}
                    </td>
                    <td className="mono-metric">{ce.orderCount}</td>
                    <td className="mono-metric">
                      {ce.currency} {ce.totalForeignAmount.toLocaleString()}
                    </td>
                    <td className="mono-metric">₹{formatInrShort(ce.totalInrEquivalent)}</td>
                    <td className="mono-metric" style={{ color: 'var(--emerald-primary)' }}>
                      ₹{formatInrShort(ce.settledOrSimulatedInr)}
                    </td>
                    <td className="mono-metric" style={{ color: 'var(--amber-primary)', fontWeight: 600 }}>
                      ₹{formatInrShort(ce.uncollectedExposureInr)}
                    </td>
                    <td className="mono-metric">{ce.portfolioSharePercent}%</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="surface-card gold-accent">
          <h3 style={{ fontSize: '0.98rem', fontWeight: 700, marginBottom: '0.5rem' }}>
            Isolated Cash-Flow Projection &amp; What-If Scenario
          </h3>
          <p style={{ fontSize: '0.76rem', color: 'var(--text-secondary)', marginBottom: '0.85rem' }}>
            Simulate expected cash realization from uncollected receivables under varying collection realization rates and FX movements without mutating ledger records.
          </p>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem' }}>
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', fontWeight: 600 }}>
                <span>Projected Receivable Collection Realization Rate (%)</span>
                <span className="mono-metric">{cashFlowCollectionRatePct}%</span>
              </div>
              <input
                type="range"
                min={50}
                max={100}
                step={5}
                style={{ width: '100%' }}
                value={cashFlowCollectionRatePct}
                onChange={(e) => setCashFlowCollectionRatePct(Number(e.target.value))}
              />
            </div>

            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', fontWeight: 600 }}>
                <span>Settlement FX Movement on Uncollected Balance (%)</span>
                <span className="mono-metric">{cashFlowFxDriftPct}%</span>
              </div>
              <input
                type="range"
                min={-10}
                max={10}
                step={0.5}
                style={{ width: '100%' }}
                value={cashFlowFxDriftPct}
                onChange={(e) => setCashFlowFxDriftPct(Number(e.target.value))}
              />
            </div>

            <div style={{ padding: '0.85rem', backgroundColor: 'var(--bg-surface)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-strong)', fontSize: '0.82rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.3rem' }}>
                <span>Current Settled / Demo Captured:</span>
                <strong className="mono-metric">₹{formatInrShort(analytics.totalSettledOrSimulatedInr)}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.3rem' }}>
                <span>Projected Additional Inflow (Scenario):</span>
                <strong className="mono-metric" style={{ color: 'var(--gold-dark)' }}>
                  +₹{formatInrShort(projectedInflow)}
                </strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Projected Total Cumulative Realization:</span>
                <strong className="mono-metric" style={{ color: 'var(--emerald-primary)' }}>
                  ₹{formatInrShort(projectedTotalCash)}
                </strong>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="surface-card">
        <h3 style={{ fontSize: '0.98rem', fontWeight: 700, marginBottom: '0.75rem' }}>
          Order-Wise Receivables, Settlement &amp; Milestone Aging Breakdown
        </h3>
        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                <th>Order ID</th>
                <th>Buyer</th>
                <th>Converted INR</th>
                <th>Live Captured</th>
                <th>Simulated Demo</th>
                <th>Pending Approval</th>
                <th>Uncollected Exposure</th>
                <th>Payment Terms &amp; Aging Category</th>
              </tr>
            </thead>
            <tbody>
              {analytics.orderBreakdowns.map((b) => (
                <tr key={b.order.id}>
                  <td className="mono-code" style={{ fontWeight: 700 }}>
                    {b.order.orderNumber}
                  </td>
                  <td>{b.order.buyerName}</td>
                  <td className="mono-metric">₹{formatInrShort(b.convertedOrderValueInr)}</td>
                  <td className="mono-metric" style={{ color: 'var(--emerald-primary)', fontWeight: 600 }}>
                    ₹{formatInrShort(b.liveCapturedInr)}
                  </td>
                  <td className="mono-metric" style={{ color: 'var(--indigo-primary)' }}>
                    ₹{formatInrShort(b.simulatedCapturedInr)}
                  </td>
                  <td className="mono-metric" style={{ color: 'var(--amber-primary)' }}>
                    ₹{formatInrShort(b.pendingApprovalInr)}
                  </td>
                  <td className="mono-metric" style={{ fontWeight: 700 }}>
                    ₹{formatInrShort(b.uncollectedExposureInr)}
                  </td>
                  <td>
                    <div style={{ fontSize: '0.76rem', fontWeight: 600 }}>{b.agingCategoryLabel}</div>
                    <div style={{ fontSize: '0.71rem', color: 'var(--text-muted)' }}>
                      {b.dueDateStatusLabel}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

interface BuyerIntelProps {
  buyers: BuyerAnalyticsItem[];
}

export const BuyerIntelligenceView: React.FC<BuyerIntelProps> = ({ buyers }) => {
  return (
    <div>
      <div style={{ marginBottom: '1.25rem' }}>
        <h1 style={{ fontSize: '1.6rem', fontWeight: 700 }}>
          Buyer Intelligence &amp; Concentration Analytics
        </h1>
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.86rem' }}>
          Buyer-wise export order distribution, INR valuation, concentration share, payment track record, and risk indicators derived strictly from existing buyer records.
        </p>
      </div>

      <div className="governance-banner simulated">
        <div>
          <strong>EXTERNAL CREDIT DATA POLICY:</strong> External credit bureau ratings (e.g., ECGC, Dun &amp; Bradstreet) and historical market default feeds are not fabricated. Where external data is unavailable, the platform explicitly reports <code>Insufficient data</code>.
        </div>
        <span className="badge badge-gold">{buyers.length} Active Buyers</span>
      </div>

      <div className="surface-card" style={{ marginBottom: '1.25rem' }}>
        <h3 style={{ fontSize: '0.98rem', fontWeight: 700, marginBottom: '0.75rem' }}>
          Buyer Revenue Concentration Share (% of Total Portfolio Value)
        </h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
          {buyers.map((buyer) => (
            <div key={buyer.buyerName}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', marginBottom: '0.2rem' }}>
                <span>
                  <strong>{buyer.buyerName}</strong> ({buyer.buyerCountry}) —{' '}
                  {buyer.orders.map((o) => o.orderNumber).join(', ')}
                </span>
                <span className="mono-metric" style={{ fontWeight: 700 }}>
                  ₹{formatInrShort(buyer.totalValueInr)} ({buyer.portfolioConcentrationPct}%)
                </span>
              </div>
              <div className="progress-track">
                <div
                  className="progress-fill"
                  style={{ width: `${Math.min(100, buyer.portfolioConcentrationPct)}%` }}
                />
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="grid-2">
        {buyers.map((buyer) => (
          <div key={buyer.buyerName} className="surface-card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.65rem' }}>
              <div>
                <h3 style={{ fontSize: '1rem', fontWeight: 700 }}>{buyer.buyerName}</h3>
                <div style={{ fontSize: '0.76rem', color: 'var(--text-secondary)' }}>
                  {buyer.buyerCountry} • Ports: {buyer.destinationPorts.join(', ')}
                </div>
              </div>
              <span className="badge badge-gold">Share: {buyer.portfolioConcentrationPct}%</span>
            </div>

            <div className="grid-3" style={{ gap: '0.5rem', marginBottom: '0.75rem' }}>
              <div style={{ padding: '0.55rem', backgroundColor: 'var(--bg-subtle)', borderRadius: 'var(--radius-sm)' }}>
                <div className="kpi-label">Order Value</div>
                <div className="mono-metric" style={{ fontWeight: 700, fontSize: '0.88rem' }}>
                  ₹{formatInrShort(buyer.totalValueInr)}
                </div>
                <div className="mono-code" style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>
                  {buyer.orderCount} order(s) • Avg Margin {buyer.averageMarginPct ?? 0}%
                </div>
              </div>
              <div style={{ padding: '0.55rem', backgroundColor: 'var(--bg-subtle)', borderRadius: 'var(--radius-sm)' }}>
                <div className="kpi-label">Settled / Demo</div>
                <div className="mono-metric" style={{ fontWeight: 700, fontSize: '0.88rem', color: 'var(--emerald-primary)' }}>
                  ₹{formatInrShort(buyer.settledOrSimulatedInr)}
                </div>
                <div style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>
                  Collection: {buyer.collectionRatePct}%
                </div>
              </div>
              <div style={{ padding: '0.55rem', backgroundColor: 'var(--bg-subtle)', borderRadius: 'var(--radius-sm)' }}>
                <div className="kpi-label">Uncollected</div>
                <div className="mono-metric" style={{ fontWeight: 700, fontSize: '0.88rem', color: 'var(--amber-primary)' }}>
                  ₹{formatInrShort(buyer.uncollectedExposureInr)}
                </div>
                <div style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>
                  Docs Ready: {buyer.docReadinessPct}%
                </div>
              </div>
            </div>

            <div style={{ fontSize: '0.76rem', marginBottom: '0.4rem' }}>
              <strong>External Credit Bureau Rating:</strong>{' '}
              <span style={{ color: 'var(--text-muted)' }}>{buyer.externalCreditRatingStatus}</span>
            </div>

            <div style={{ fontSize: '0.76rem', marginBottom: '0.5rem' }}>
              <strong>Historical Default History:</strong>{' '}
              <span style={{ color: 'var(--text-muted)' }}>{buyer.historicalDefaultHistoryStatus}</span>
            </div>

            <div style={{ fontSize: '0.76rem', marginBottom: '0.5rem' }}>
              <strong>Buyer Risk Indicators ({buyer.riskAlerts.length}):</strong>
              {buyer.riskAlerts.length === 0 ? (
                <span className="badge badge-emerald" style={{ marginLeft: '0.4rem' }}>
                  No Active Buyer Risk Flags
                </span>
              ) : (
                <ul style={{ paddingLeft: '1.1rem', marginTop: '0.2rem', color: 'var(--crimson-primary)' }}>
                  {buyer.riskAlerts.map((ri) => (
                    <li key={ri.id}>
                      [{ri.severity}] {ri.title}
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div style={{ fontSize: '0.74rem', color: 'var(--text-secondary)' }}>
              <strong>Activity Timeline ({buyer.activities.length}):</strong>
              {buyer.activities.slice(0, 2).map((act) => (
                <div key={act.id} style={{ marginTop: '0.15rem' }}>
                  • {act.title} — {act.description}
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

interface AiRiskProps {
  nlQueryInput: string;
  setNlQueryInput: (v: string) => void;
  nlQueryLoading: boolean;
  onExecuteNlQuery: () => void;
  nlQueryResult: {
    answerTitle: string;
    answerBullets: string[];
    citedOrders: string[];
    engineLabel: string;
  } | null;
  advisorLoading: boolean;
  onRunPortfolioAdvisory: () => void;
  executiveSummary: string;
  advisorEngineLabel: string;
  structuredRecommendations: StructuredAIRecommendation[];
  onNavigate: (tab: WebNavTab, orderId?: string) => void;
}

export const AiRiskAndAdvisorView: React.FC<AiRiskProps> = ({
  nlQueryInput,
  setNlQueryInput,
  nlQueryLoading,
  onExecuteNlQuery,
  nlQueryResult,
  advisorLoading,
  onRunPortfolioAdvisory,
  executiveSummary,
  advisorEngineLabel,
  structuredRecommendations,
  onNavigate
}) => {
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem', flexWrap: 'wrap', gap: '0.75rem' }}>
        <div>
          <h1 style={{ fontSize: '1.6rem', fontWeight: 700 }}>
            Trade Risk Intelligence &amp; Autonomous AI Advisor
          </h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.86rem' }}>
            Natural-language portfolio query engine and 5-point structured AI recommendations grounded in authoritative order, financial, and compliance records.
          </p>
        </div>
        <button
          type="button"
          className="btn btn-gold"
          disabled={advisorLoading}
          onClick={onRunPortfolioAdvisory}
        >
          {advisorLoading ? 'Synthesizing Advisory...' : 'Refresh Live / Fallback AI Synthesis'}
        </button>
      </div>

      <div className="surface-card gold-accent" style={{ marginBottom: '1.25rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.6rem', flexWrap: 'wrap', gap: '0.5rem' }}>
          <h3 style={{ fontSize: '0.98rem', fontWeight: 700 }}>
            Natural-Language Portfolio Query Engine
          </h3>
          <div style={{ display: 'flex', gap: '0.35rem', flexWrap: 'wrap' }}>
            {[
              'Which orders have missing or rejected documents?',
              'Show unpaid advances and receivable exposure',
              'Rank orders by highest profit margin',
              'Summarize shipment readiness and buyer concentration'
            ].map((sampleQ) => (
              <button
                key={sampleQ}
                type="button"
                className="btn btn-outline btn-xs"
                onClick={() => setNlQueryInput(sampleQ)}
              >
                {sampleQ.slice(0, 32)}...
              </button>
            ))}
          </div>
        </div>

        <div style={{ display: 'flex', gap: '0.6rem', flexWrap: 'wrap' }}>
          <input
            className="input-field"
            style={{ flex: 1, minWidth: '240px' }}
            value={nlQueryInput}
            onChange={(e) => setNlQueryInput(e.target.value)}
            placeholder="Ask about revenue, margins, documents, buyers, or risks..."
          />
          <button
            type="button"
            className="btn btn-primary"
            disabled={nlQueryLoading}
            onClick={onExecuteNlQuery}
          >
            {nlQueryLoading ? 'Querying...' : 'Run Query'}
          </button>
        </div>

        {nlQueryResult && (
          <div style={{ marginTop: '0.85rem', padding: '0.85rem', backgroundColor: 'var(--bg-surface)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-strong)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.4rem', flexWrap: 'wrap', gap: '0.4rem' }}>
              <strong style={{ fontSize: '0.84rem' }}>{nlQueryResult.answerTitle}</strong>
              <span className="badge badge-indigo">{nlQueryResult.engineLabel}</span>
            </div>
            <ul style={{ paddingLeft: '1.2rem', fontSize: '0.82rem', color: 'var(--text-secondary)', marginBottom: '0.5rem' }}>
              {nlQueryResult.answerBullets.map((b, i) => (
                <li key={i} style={{ marginBottom: '0.2rem' }}>{b}</li>
              ))}
            </ul>
            {nlQueryResult.citedOrders.length > 0 && (
              <div style={{ fontSize: '0.74rem', color: 'var(--text-muted)' }}>
                <strong>Cited Source Orders:</strong> {nlQueryResult.citedOrders.join(', ')}
              </div>
            )}
          </div>
        )}
      </div>

      <div className="surface-card emerald-accent" style={{ marginBottom: '1.25rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem', flexWrap: 'wrap', gap: '0.4rem' }}>
          <h3 style={{ fontSize: '0.96rem', fontWeight: 700 }}>
            Autonomous AI Portfolio Executive Summary
          </h3>
          <span className="badge badge-emerald">{advisorEngineLabel}</span>
        </div>
        <p style={{ fontSize: '0.84rem', color: 'var(--text-primary)' }}>{executiveSummary}</p>
      </div>

      <div>
        <h3 style={{ fontSize: '1.05rem', fontWeight: 700, marginBottom: '0.75rem' }}>
          Structured 5-Point Autonomous AI Advisor Recommendations ({structuredRecommendations.length})
        </h3>
        <div className="grid-2">
          {structuredRecommendations.map((rec) => (
            <div
              key={rec.id}
              className={`surface-card ${
                rec.severity === 'CRITICAL'
                  ? 'crimson-accent'
                  : rec.severity === 'WARNING'
                  ? 'amber-accent'
                  : ''
              }`}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem', flexWrap: 'wrap', gap: '0.4rem' }}>
                <span className="badge badge-gold">{rec.category}</span>
                <div style={{ display: 'flex', gap: '0.35rem' }}>
                  <span
                    className={`badge ${
                      rec.severity === 'CRITICAL'
                        ? 'badge-crimson'
                        : rec.severity === 'WARNING'
                        ? 'badge-amber'
                        : 'badge-emerald'
                    }`}
                  >
                    {rec.severity}
                  </span>
                  <span className="badge badge-indigo">
                    {rec.requiresHumanApproval ? 'Human Approval Required' : 'Advisory Telemetry'}
                  </span>
                </div>
              </div>

              <div style={{ fontSize: '0.8rem', display: 'flex', flexDirection: 'column', gap: '0.45rem' }}>
                <div>
                  <strong>1. Relevant Source Records:</strong>{' '}
                  <span className="mono-code">{rec.sourceRecords.join(', ')}</span>
                </div>
                <div>
                  <strong>2. Issue Identified:</strong> {rec.issueIdentified}
                </div>
                <div>
                  <strong>3. Reasoning Summary:</strong>{' '}
                  <span style={{ color: 'var(--text-secondary)' }}>{rec.reasoningSummary}</span>
                </div>
                <div>
                  <strong>4. Recommended Next Action:</strong>{' '}
                  <span style={{ color: 'var(--emerald-primary)', fontWeight: 600 }}>
                    {rec.recommendedNextAction}
                  </span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '0.25rem' }}>
                  <span>
                    <strong>5. Approval Governance:</strong> {rec.approvalRole}
                  </span>
                  <button
                    type="button"
                    className="btn btn-outline btn-xs"
                    onClick={() => onNavigate(rec.targetScreenRoute, rec.orderId)}
                  >
                    Inspect Record
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

interface ReportingProps {
  workspaceMode: WorkspaceMode;
  prodUserEmail: string;
  orders: ExportOrder[];
  assessments: FinancialAssessment[];
  documents: DocumentRecord[];
  payments: PaymentRecord[];
  risks: TradeRiskAlert[];
  activities: ActivityRecord[];
  onToast: (msg: string) => void;
}

export const ReportingCenterView: React.FC<ReportingProps> = ({
  workspaceMode,
  prodUserEmail,
  orders,
  assessments,
  documents,
  payments,
  risks,
  activities,
  onToast
}) => {
  const [selectedReportType, setSelectedReportType] = useState<ReportTypeId>('EXPORT_ORDER_SUMMARY');

  const activeReportData = buildReportData({
    reportType: selectedReportType,
    workspaceMode,
    prodUserEmail,
    orders,
    assessments,
    documents,
    payments,
    activities
  });

  const counts = {
    orders: orders.length,
    assessments: assessments.length,
    documents: documents.length,
    payments: payments.length,
    risks: risks.length,
    activities: activities.length
  };

  return (
    <div>
      <div style={{ marginBottom: '1.25rem' }}>
        <h1 style={{ fontSize: '1.6rem', fontWeight: 700 }}>
          Enterprise Reporting &amp; Audit Exports (CSV &amp; Print-Friendly PDF)
        </h1>
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.86rem' }}>
          Generate downloadable CSV spreadsheets and print-friendly PDF reports directly from authoritative workspace records with timestamps and workspace classification.
        </p>
      </div>

      <div className="grid-3" style={{ marginBottom: '1.25rem' }}>
        {AVAILABLE_REPORTS.map((rep) => (
          <div
            key={rep.id}
            className={`surface-card ${selectedReportType === rep.id ? 'gold-accent' : ''}`}
            style={{ display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}
          >
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.35rem' }}>
                <h3 style={{ fontSize: '0.95rem', fontWeight: 700 }}>{rep.title}</h3>
                <span className="badge badge-neutral">{rep.recordCountLabel(counts)}</span>
              </div>
              <p style={{ fontSize: '0.77rem', color: 'var(--text-secondary)', marginBottom: '0.85rem' }}>
                {rep.description}
              </p>
            </div>
            <div style={{ display: 'flex', gap: '0.45rem', flexWrap: 'wrap' }}>
              <button
                type="button"
                className="btn btn-outline btn-xs"
                onClick={() => setSelectedReportType(rep.id)}
              >
                Preview Table
              </button>
              <button
                type="button"
                className="btn btn-primary btn-xs"
                onClick={() => {
                  const fileName = downloadReportAsCsv({
                    reportType: rep.id,
                    workspaceMode,
                    prodUserEmail,
                    orders,
                    assessments,
                    documents,
                    payments,
                    activities
                  });
                  onToast(`Downloaded ${fileName}`);
                }}
              >
                Download CSV
              </button>
              <button
                type="button"
                className="btn btn-gold btn-xs"
                onClick={() => {
                  triggerPrintFriendlyPdfReport({
                    reportType: rep.id,
                    workspaceMode,
                    prodUserEmail,
                    orders,
                    assessments,
                    documents,
                    payments,
                    activities
                  });
                  onToast(`Prepared Print-Friendly PDF for ${rep.title}`);
                }}
              >
                Print / Save PDF
              </button>
            </div>
          </div>
        ))}
      </div>

      <div className="surface-card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.85rem', flexWrap: 'wrap', gap: '0.75rem' }}>
          <div>
            <h3 style={{ fontSize: '1.05rem', fontWeight: 700 }}>{activeReportData.title}</h3>
            <div style={{ fontSize: '0.76rem', color: 'var(--text-secondary)' }}>
              {activeReportData.companyHeader} • Generated:{' '}
              <span className="mono-code">{activeReportData.generatedAt}</span>
            </div>
          </div>
          <span className={`badge ${workspaceMode === 'SAMPLE_BUSINESS' ? 'badge-amber' : 'badge-emerald'}`}>
            {workspaceMode === 'SAMPLE_BUSINESS' ? 'SAMPLE DEMO DATA' : 'PRODUCTION DATA'}
          </span>
        </div>

        <div className="grid-4" style={{ marginBottom: '0.9rem' }}>
          {activeReportData.summaryMetrics.map((sm) => (
            <div key={sm.label} style={{ padding: '0.6rem 0.75rem', backgroundColor: 'var(--bg-subtle)', borderRadius: 'var(--radius-sm)' }}>
              <div className="kpi-label">{sm.label}</div>
              <div className="mono-metric" style={{ fontWeight: 700, fontSize: '0.92rem' }}>
                {sm.value}
              </div>
            </div>
          ))}
        </div>

        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                {activeReportData.headers.map((h) => (
                  <th key={h}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {activeReportData.rows.slice(0, 25).map((row, rIdx) => (
                <tr key={rIdx}>
                  {row.map((cell, cIdx) => (
                    <td key={cIdx}>{cell}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
