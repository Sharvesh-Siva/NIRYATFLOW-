import React, { useState } from 'react';
import type {
  ExportOrder,
  PaymentRecord,
  PaymentHubMode,
  PayPalSandboxConfigStatus,
  PayPalSandboxTransaction,
  WorkspaceMode
} from '../types/models';
import { SIMULATED_PAYMENT_BANNER } from '../services/demoPaymentService';
import { NiryatIcon } from './NiryatIcon';

export interface PaymentHubDualModeViewProps {
  selectedOrder: ExportOrder;
  workspaceMode: WorkspaceMode;
  demoPayments: PaymentRecord[];
  newPaymentType: 'ADVANCE' | 'BALANCE' | 'FULL';
  setNewPaymentType: (v: 'ADVANCE' | 'BALANCE' | 'FULL') => void;
  newPaymentAmountForeign: number;
  setNewPaymentAmountForeign: (v: number) => void;
  onCreateDemoPayment: () => void;
  onTransitionDemoPayment: (
    payment: PaymentRecord,
    outcome: 'DEMO_CAPTURED' | 'DEMO_FAILED' | 'DEMO_CANCELLED'
  ) => void;
  paypalConfig: PayPalSandboxConfigStatus | null;
  paypalTransactions: PayPalSandboxTransaction[];
  paypalBusyOrderId: string | null;
  paypalErrorBanner: string | null;
  onRefreshPayPalConfig: () => void;
  onCreatePayPalOrder: (paymentType: 'ADVANCE' | 'BALANCE' | 'FULL') => void;
  onCheckPayPalOrderStatus: (paypalOrderId: string, orderId: string) => void;
  onCapturePayPalOrder: (paypalOrderId: string, orderId: string) => void;
  onCancelPayPalOrder: (paypalOrderId: string, orderId: string) => void;
}

export const PaymentHubDualModeView: React.FC<PaymentHubDualModeViewProps> = ({
  selectedOrder,
  demoPayments,
  newPaymentType,
  setNewPaymentType,
  newPaymentAmountForeign,
  setNewPaymentAmountForeign,
  onCreateDemoPayment,
  onTransitionDemoPayment,
  paypalConfig,
  paypalTransactions,
  paypalBusyOrderId,
  paypalErrorBanner,
  onRefreshPayPalConfig,
  onCreatePayPalOrder,
  onCheckPayPalOrderStatus,
  onCapturePayPalOrder,
  onCancelPayPalOrder
}) => {
  const [hubMode, setHubMode] = useState<PaymentHubMode>('PAYPAL_SANDBOX');
  const [sandboxPaymentType, setSandboxPaymentType] = useState<'ADVANCE' | 'BALANCE' | 'FULL'>(
    'ADVANCE'
  );

  const gross = Number(selectedOrder.orderValue);
  const advPct = Number(selectedOrder.advancePercentage);
  const advanceVal = Number((gross * (advPct / 100)).toFixed(2));
  const balanceVal = Number(Math.max(0, gross - advanceVal).toFixed(2));
  const authoritativeSandboxAmount =
    sandboxPaymentType === 'ADVANCE'
      ? advanceVal > 0
        ? advanceVal
        : gross
      : sandboxPaymentType === 'BALANCE'
      ? balanceVal > 0
        ? balanceVal
        : gross
      : gross;

  const renderSandboxStatusBadge = (status: PayPalSandboxTransaction['status']) => {
    switch (status) {
      case 'COMPLETED':
        return (
          <span className="badge badge-emerald">
            <NiryatIcon name="check-circle-2" size="compact" /> COMPLETED
          </span>
        );
      case 'APPROVED':
        return (
          <span className="badge badge-gold">
            <NiryatIcon name="shield-check" size="compact" /> APPROVED (READY TO CAPTURE)
          </span>
        );
      case 'CREATED':
        return (
          <span className="badge badge-amber">
            <NiryatIcon name="clock" size="compact" /> CREATED (AWAITING BUYER)
          </span>
        );
      case 'CANCELLED':
        return (
          <span className="badge badge-neutral">
            <NiryatIcon name="x-circle" size="compact" /> CANCELLED
          </span>
        );
      case 'FAILED':
      default:
        return (
          <span className="badge badge-crimson">
            <NiryatIcon name="alert-triangle" size="compact" /> FAILED
          </span>
        );
    }
  };

  return (
    <div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          marginBottom: '1.1rem',
          flexWrap: 'wrap',
          gap: '0.75rem'
        }}
      >
        <div>
          <h1
            style={{
              fontSize: '1.6rem',
              fontWeight: 700,
              display: 'flex',
              alignItems: 'center',
              gap: '0.55rem'
            }}
          >
            <NiryatIcon name="credit-card" size="section" color="var(--primary-teal)" />
            Payment Hub — Settlement &amp; Remittance Operations ({selectedOrder.orderNumber})
          </h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.86rem', marginTop: '0.15rem' }}>
            Dual-architecture trade settlement hub supporting both isolated Demo Mode state transitions and genuine PayPal Sandbox Orders v2 REST API transactions.
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <span className="badge badge-indigo">
            <NiryatIcon name="lock" size="compact" /> Server-Only Secret Architecture
          </span>
          <button
            type="button"
            className="btn btn-outline btn-xs"
            onClick={onRefreshPayPalConfig}
          >
            <NiryatIcon name="refresh-cw" size="compact" /> Refresh Gateway Status
          </button>
        </div>
      </div>

      <div className="payment-mode-grid" role="tablist" aria-label="Payment Hub Settlement Mode">
        <button
          type="button"
          role="tab"
          aria-selected={hubMode === 'PAYPAL_SANDBOX'}
          className={`payment-mode-card ${hubMode === 'PAYPAL_SANDBOX' ? 'active-paypal' : ''}`}
          onClick={() => setHubMode('PAYPAL_SANDBOX')}
        >
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              width: '100%',
              marginBottom: '0.45rem'
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <NiryatIcon
                name="globe"
                size="section"
                color="var(--emerald-primary)"
                active={hubMode === 'PAYPAL_SANDBOX'}
              />
              <span className="heading-font" style={{ fontWeight: 700, fontSize: '0.98rem' }}>
                PayPal Sandbox Mode (Orders v2 API)
              </span>
            </div>
            <span
              className={`badge ${
                paypalConfig?.configured ? 'badge-emerald' : 'badge-amber'
              }`}
            >
              {paypalConfig?.configured ? 'SANDBOX CREDENTIALS ACTIVE' : 'SANDBOX CONFIGURED VIA SECRETS'}
            </span>
          </div>
          <p style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>
            Real PayPal Sandbox payments via backend <code>/api/paypal/create-order</code>, buyer approval URL redirect, <code>/api/paypal/order-status</code>, and idempotent <code>/api/paypal/capture-order</code>.
          </p>
          <div
            className="mono-code"
            style={{
              marginTop: '0.55rem',
              fontSize: '0.71rem',
              color: 'var(--emerald-primary)',
              fontWeight: 600
            }}
          >
            Independent Sandbox Ledger: {paypalTransactions.length} Record(s) • Environment: SANDBOX
          </div>
        </button>

        <button
          type="button"
          role="tab"
          aria-selected={hubMode === 'DEMO'}
          className={`payment-mode-card ${hubMode === 'DEMO' ? 'active-demo' : ''}`}
          onClick={() => setHubMode('DEMO')}
        >
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              width: '100%',
              marginBottom: '0.45rem'
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <NiryatIcon
                name="wallet"
                size="section"
                color="var(--gold-dark)"
                active={hubMode === 'DEMO'}
              />
              <span className="heading-font" style={{ fontWeight: 700, fontSize: '0.98rem' }}>
                Demo Mode (Instant State Simulator)
              </span>
            </div>
            <span className="badge badge-gold">SIMULATED LEDGER</span>
          </div>
          <p style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>
            Simulated demo payments for instant workflow testing via <code>DemoPaymentService</code> without external API credentials. Never overwrites genuine PayPal Sandbox records.
          </p>
          <div
            className="mono-code"
            style={{
              marginTop: '0.55rem',
              fontSize: '0.71rem',
              color: 'var(--gold-dark)',
              fontWeight: 600
            }}
          >
            Independent Demo Ledger: {demoPayments.length} Seeded / Simulated Record(s)
          </div>
        </button>
      </div>

      {hubMode === 'PAYPAL_SANDBOX' && (
        <div>
          <div
            className={`governance-banner ${
              paypalConfig?.configured ? 'production' : 'demo'
            }`}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
              <NiryatIcon
                name={paypalConfig?.configured ? 'shield-check' : 'alert-triangle'}
                size="nav"
              />
              <div>
                <strong>
                  PAYPAL SANDBOX ORDERS v2 GATEWAY (
                  {paypalConfig?.apiBaseUrl || 'https://api-m.sandbox.paypal.com'}):
                </strong>{' '}
                {paypalConfig?.message ||
                  'Checking server-side PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET readiness...'}
              </div>
            </div>
            <div style={{ display: 'flex', gap: '0.4rem', alignItems: 'center' }}>
              <span className="badge badge-indigo">ENV: SANDBOX</span>
              <span
                className={`badge ${
                  paypalConfig?.configured ? 'badge-emerald' : 'badge-amber'
                }`}
              >
                {paypalConfig?.configured ? 'OAUTH2 READY' : 'AWAITING SECRETS'}
              </span>
            </div>
          </div>

          {paypalErrorBanner && (
            <div
              className="surface-card crimson-accent"
              style={{ marginBottom: '1rem', padding: '0.85rem 1rem' }}
              role="alert"
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.5rem',
                  color: 'var(--crimson-primary)',
                  fontWeight: 700,
                  fontSize: '0.84rem'
                }}
              >
                <NiryatIcon name="alert-triangle" size="compact" />
                <span>PayPal Sandbox Operation Notice:</span>
              </div>
              <div style={{ fontSize: '0.8rem', color: 'var(--text-primary)', marginTop: '0.2rem' }}>
                {paypalErrorBanner}
              </div>
            </div>
          )}

          <div className="surface-card emerald-accent" style={{ marginBottom: '1.25rem' }}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'flex-start',
                flexWrap: 'wrap',
                gap: '1rem'
              }}
            >
              <div style={{ maxWidth: '640px' }}>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.45rem',
                    marginBottom: '0.25rem'
                  }}
                >
                  <span className="badge badge-emerald">STEP 1: CREATE ORDER</span>
                  <span className="mono-code" style={{ color: 'var(--text-muted)' }}>
                    POST /api/paypal/create-order
                  </span>
                </div>
                <h3 style={{ fontSize: '1.02rem', fontWeight: 700 }}>
                  Create Authoritative PayPal Sandbox Order for {selectedOrder.orderNumber}
                </h3>
                <p
                  style={{
                    fontSize: '0.78rem',
                    color: 'var(--text-secondary)',
                    marginTop: '0.2rem'
                  }}
                >
                  Buyer: <strong>{selectedOrder.buyerName}</strong> ({selectedOrder.buyerCountry}) •
                  Contract Value:{' '}
                  <strong className="mono-metric">
                    {selectedOrder.currency} {selectedOrder.orderValue.toLocaleString()}
                  </strong>{' '}
                  • Advance Term: <strong>{selectedOrder.advancePercentage}%</strong>
                </p>
                <p
                  style={{
                    fontSize: '0.74rem',
                    color: 'var(--text-muted)',
                    marginTop: '0.3rem'
                  }}
                >
                  Security Rule: Payment amount and currency are calculated and validated strictly on the server from the authoritative NiryatFlow order record. Browser-manipulated amounts are automatically rejected.
                </p>
              </div>

              <div
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '0.55rem',
                  minWidth: '300px'
                }}
              >
                <div style={{ display: 'flex', gap: '0.55rem', alignItems: 'center' }}>
                  <div style={{ flex: 1 }}>
                    <label className="field-label" htmlFor="sandbox-tranche-select">
                      Settlement Tranche
                    </label>
                    <select
                      id="sandbox-tranche-select"
                      className="select-field"
                      value={sandboxPaymentType}
                      onChange={(e) =>
                        setSandboxPaymentType(e.target.value as 'ADVANCE' | 'BALANCE' | 'FULL')
                      }
                    >
                      <option value="ADVANCE">
                        ADVANCE ({selectedOrder.advancePercentage}%)
                      </option>
                      <option value="BALANCE">
                        BALANCE ({100 - selectedOrder.advancePercentage}%)
                      </option>
                      <option value="FULL">FULL CONTRACT (100%)</option>
                    </select>
                  </div>

                  <div style={{ flex: 1 }}>
                    <label className="field-label">Authoritative Server Amount</label>
                    <div
                      className="input-field mono-metric"
                      style={{
                        backgroundColor: 'var(--bg-subtle)',
                        fontWeight: 700,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between'
                      }}
                    >
                      <span>{selectedOrder.currency}</span>
                      <span>{authoritativeSandboxAmount.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
                    </div>
                  </div>
                </div>

                <button
                  type="button"
                  className="btn btn-primary"
                  disabled={paypalBusyOrderId === 'CREATE'}
                  onClick={() => onCreatePayPalOrder(sandboxPaymentType)}
                >
                  <NiryatIcon name="credit-card" size="compact" />
                  {paypalBusyOrderId === 'CREATE'
                    ? 'Creating PayPal Sandbox Order...'
                    : `Create PayPal Sandbox Order (${selectedOrder.currency} ${authoritativeSandboxAmount.toLocaleString()})`}
                </button>
              </div>
            </div>
          </div>

          <div className="surface-card">
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
                <h3 style={{ fontSize: '1rem', fontWeight: 700 }}>
                  PayPal Sandbox Transaction Ledger ({paypalTransactions.length} Records)
                </h3>
                <p style={{ fontSize: '0.76rem', color: 'var(--text-secondary)' }}>
                  Displays genuine PayPal Order IDs, Capture IDs, buyer approval links, and verified server capture statuses. Returning from PayPal redirect never marks a transaction completed without verified server capture.
                </p>
              </div>
              <span className="badge badge-emerald">
                <NiryatIcon name="shield-check" size="compact" /> Orders v2 Verified Ledger
              </span>
            </div>

            {paypalTransactions.length === 0 ? (
              <div
                style={{
                  padding: '2rem 1.25rem',
                  textAlign: 'center',
                  backgroundColor: 'var(--bg-elevated)',
                  borderRadius: 'var(--radius-md)',
                  border: '1px dashed var(--border-strong)'
                }}
              >
                <NiryatIcon name="credit-card" size="section" color="var(--text-muted)" />
                <div style={{ fontWeight: 700, fontSize: '0.92rem', marginTop: '0.45rem' }}>
                  No PayPal Sandbox Orders Created Yet in This Workspace
                </div>
                <p
                  style={{
                    fontSize: '0.8rem',
                    color: 'var(--text-secondary)',
                    maxWidth: '520px',
                    margin: '0.25rem auto 0'
                  }}
                >
                  Select an export order above (e.g., ORDER-001 in USD, ORDER-004 in GBP, or ORDER-005 in EUR) and click &ldquo;Create PayPal Sandbox Order&rdquo; to initiate a genuine PayPal Orders v2 checkout flow.
                </p>
              </div>
            ) : (
              <div className="table-scroll">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>NiryatFlow Order</th>
                      <th>PayPal Order ID</th>
                      <th>PayPal Capture ID</th>
                      <th>Amount &amp; Currency</th>
                      <th>Sandbox Status</th>
                      <th>Environment</th>
                      <th>Timestamp</th>
                      <th>Verification &amp; Lifecycle Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {paypalTransactions.map((tx) => {
                      const isBusy = paypalBusyOrderId === tx.paypalOrderId;
                      return (
                        <tr key={tx.paypalOrderId}>
                          <td>
                            <div className="mono-code" style={{ fontWeight: 700 }}>
                              {tx.orderNumber}
                            </div>
                            <div style={{ fontSize: '0.72rem', color: 'var(--text-secondary)' }}>
                              {tx.buyerName}
                            </div>
                            <span className="badge badge-neutral" style={{ marginTop: '0.2rem' }}>
                              {tx.paymentType}
                            </span>
                          </td>
                          <td className="mono-code" style={{ fontWeight: 700 }}>
                            {tx.paypalOrderId}
                          </td>
                          <td className="mono-code">
                            {tx.paypalCaptureId ? (
                              <span style={{ color: 'var(--emerald-primary)', fontWeight: 700 }}>
                                {tx.paypalCaptureId}
                              </span>
                            ) : (
                              <span style={{ color: 'var(--text-muted)' }}>Not Captured Yet</span>
                            )}
                          </td>
                          <td className="mono-metric" style={{ fontWeight: 700 }}>
                            {tx.currency}{' '}
                            {tx.amount.toLocaleString(undefined, {
                              minimumFractionDigits: 2,
                              maximumFractionDigits: 2
                            })}
                          </td>
                          <td>{renderSandboxStatusBadge(tx.status)}</td>
                          <td>
                            <span className="badge badge-indigo">
                              {tx.environment.toUpperCase()}
                            </span>
                          </td>
                          <td className="mono-code" style={{ fontSize: '0.72rem' }}>
                            {new Date(tx.updatedAtEpochMs).toISOString().replace('T', ' ').slice(0, 19)} UTC
                          </td>
                          <td style={{ minWidth: '300px' }}>
                            <div
                              style={{
                                fontSize: '0.74rem',
                                color: 'var(--text-secondary)',
                                marginBottom: '0.45rem'
                              }}
                            >
                              {tx.verificationSummary}
                            </div>
                            <div style={{ display: 'flex', gap: '0.38rem', flexWrap: 'wrap' }}>
                              {tx.approvalUrl && tx.status !== 'COMPLETED' && tx.status !== 'CANCELLED' && (
                                <a
                                  href={tx.approvalUrl}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="btn btn-gold btn-xs"
                                  style={{ textDecoration: 'none' }}
                                >
                                  <NiryatIcon name="external-link" size="compact" /> 1. Approve on PayPal Sandbox
                                </a>
                              )}
                              <button
                                type="button"
                                className="btn btn-outline btn-xs"
                                disabled={isBusy}
                                onClick={() =>
                                  onCheckPayPalOrderStatus(tx.paypalOrderId, tx.exportOrderId)
                                }
                              >
                                <NiryatIcon name="refresh-cw" size="compact" /> 2. Verify Status
                              </button>
                              <button
                                type="button"
                                className="btn btn-primary btn-xs"
                                disabled={isBusy || tx.status === 'COMPLETED' || tx.status === 'CANCELLED'}
                                onClick={() =>
                                  onCapturePayPalOrder(tx.paypalOrderId, tx.exportOrderId)
                                }
                              >
                                <NiryatIcon name="check-circle-2" size="compact" /> 3. Capture Order
                              </button>
                              {tx.status !== 'COMPLETED' && tx.status !== 'CANCELLED' && (
                                <button
                                  type="button"
                                  className="btn btn-danger btn-xs"
                                  disabled={isBusy}
                                  onClick={() =>
                                    onCancelPayPalOrder(tx.paypalOrderId, tx.exportOrderId)
                                  }
                                >
                                  Cancel
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}

      {hubMode === 'DEMO' && (
        <div>
          <div className="governance-banner demo">
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.55rem' }}>
              <NiryatIcon name="info" size="nav" />
              <div>
                <strong>{SIMULATED_PAYMENT_BANNER}:</strong> Demo payment orders use{' '}
                <code>DemoPaymentService</code> with idempotency protection and zero client-side PayPal secrets. Demo captures are explicitly labeled <code>DEMO_CAPTURED</code>.
              </div>
            </div>
            <span className="badge badge-amber">Isolated Demo Ledger</span>
          </div>

          <div className="surface-card gold-accent" style={{ marginBottom: '1.25rem' }}>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: '0.75rem'
              }}
            >
              <div>
                <h3 style={{ fontSize: '0.96rem', fontWeight: 700 }}>
                  Initiate Simulated Demo Payment for {selectedOrder.orderNumber}
                </h3>
                <p style={{ fontSize: '0.76rem', color: 'var(--text-secondary)' }}>
                  Buyer: {selectedOrder.buyerName} • Contract: {selectedOrder.currency}{' '}
                  {selectedOrder.orderValue.toLocaleString()}
                </p>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', flexWrap: 'wrap' }}>
                <select
                  aria-label="Demo Payment Type"
                  className="select-field"
                  style={{ width: '130px' }}
                  value={newPaymentType}
                  onChange={(e) =>
                    setNewPaymentType(e.target.value as 'ADVANCE' | 'BALANCE' | 'FULL')
                  }
                >
                  <option value="ADVANCE">ADVANCE</option>
                  <option value="BALANCE">BALANCE</option>
                  <option value="FULL">FULL</option>
                </select>
                <input
                  aria-label="Demo Payment Foreign Amount"
                  type="number"
                  className="input-field mono-metric"
                  style={{ width: '150px' }}
                  min={1}
                  value={newPaymentAmountForeign}
                  onChange={(e) => setNewPaymentAmountForeign(Number(e.target.value))}
                />
                <button
                  type="button"
                  className="btn btn-gold"
                  onClick={onCreateDemoPayment}
                >
                  <NiryatIcon name="plus" size="compact" /> Create Demo Payment ({selectedOrder.currency})
                </button>
              </div>
            </div>
          </div>

          <div className="surface-card">
            <h3 style={{ fontSize: '0.98rem', fontWeight: 700, marginBottom: '0.75rem' }}>
              Demo &amp; Seeded Remittance Ledger ({demoPayments.length} Records)
            </h3>
            <div className="table-scroll">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Payment ID / Ref</th>
                    <th>Order ID</th>
                    <th>Type</th>
                    <th>Foreign Amount</th>
                    <th>Status</th>
                    <th>Verification Details</th>
                    <th>Demo Lifecycle Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {demoPayments.map((tx) => (
                    <tr key={tx.id}>
                      <td className="mono-code" style={{ fontWeight: 700 }}>
                        {tx.id}
                        <div style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>
                          OrderRef: {tx.paypalOrderId}
                        </div>
                      </td>
                      <td className="mono-code">{tx.exportOrderId}</td>
                      <td>
                        <span className="badge badge-neutral">{tx.paymentType}</span>
                      </td>
                      <td className="mono-metric" style={{ fontWeight: 600 }}>
                        {tx.currency} {tx.amount.toLocaleString()}
                      </td>
                      <td>
                        <span
                          className={`badge ${
                            tx.paymentStatus === 'CAPTURED'
                              ? 'badge-emerald'
                              : tx.paymentStatus === 'DEMO_CAPTURED'
                              ? 'badge-indigo'
                              : tx.paymentStatus.includes('FAIL') ||
                                tx.paymentStatus.includes('CANCEL')
                              ? 'badge-crimson'
                              : 'badge-amber'
                          }`}
                        >
                          {tx.paymentStatus}
                        </span>
                      </td>
                      <td style={{ fontSize: '0.75rem', maxWidth: '300px' }}>
                        {tx.verificationDetails}
                      </td>
                      <td>
                        <div style={{ display: 'flex', gap: '0.35rem' }}>
                          <button
                            type="button"
                            className="btn btn-primary btn-xs"
                            disabled={tx.paymentStatus === 'DEMO_CAPTURED'}
                            onClick={() => onTransitionDemoPayment(tx, 'DEMO_CAPTURED')}
                          >
                            Capture Demo
                          </button>
                          <button
                            type="button"
                            className="btn btn-danger btn-xs"
                            onClick={() => onTransitionDemoPayment(tx, 'DEMO_FAILED')}
                          >
                            Fail
                          </button>
                          <button
                            type="button"
                            className="btn btn-outline btn-xs"
                            onClick={() => onTransitionDemoPayment(tx, 'DEMO_CANCELLED')}
                          >
                            Cancel
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
    </div>
  );
};

export default PaymentHubDualModeView;
