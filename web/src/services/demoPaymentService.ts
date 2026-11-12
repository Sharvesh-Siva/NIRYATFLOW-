import { ActivityRecord, ExportOrder, PaymentRecord } from '../types/models';
import { round2 } from '../domain/financialEngine';

export const SIMULATED_PAYMENT_BANNER =
  'SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION';

function deterministicHash(seed: string): string {
  let h1 = 0xdeadbeef ^ seed.length;
  let h2 = 0x41c6ce57 ^ seed.length;
  for (let i = 0; i < seed.length; i++) {
    const ch = seed.charCodeAt(i);
    h1 = Math.imul(h1 ^ ch, 2654435761);
    h2 = Math.imul(h2 ^ ch, 1597334677);
  }
  h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
  h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);
  const hex = (h1 >>> 0).toString(16).toUpperCase().padStart(8, '0') +
    (h2 >>> 0).toString(16).toUpperCase().padStart(8, '0');
  return hex.slice(0, 14);
}

export function createDemoPayment(params: {
  order: ExportOrder;
  amount: number;
  currency: string;
  paymentType: 'ADVANCE' | 'BALANCE' | 'FULL';
  existingPayments: PaymentRecord[];
  userId: string;
}): {
  payment: PaymentRecord;
  activity: ActivityRecord;
  isDuplicatePrevented: boolean;
  message: string;
} {
  const safeAmount = round2(Math.max(0.01, params.amount));
  const safeCurrency = (params.currency || params.order.currency || 'USD').toUpperCase();

  // Check idempotency: reuse existing DEMO_PENDING / PENDING_APPROVAL payment for same order, type & amount
  const existing = params.existingPayments.find(
    (p) =>
      p.exportOrderId === params.order.id &&
      p.paymentType === params.paymentType &&
      Math.abs(p.amount - safeAmount) < 0.01 &&
      (p.paymentStatus === 'DEMO_PENDING' || p.paymentStatus === 'PENDING_APPROVAL')
  );

  const now = Date.now();
  if (existing) {
    return {
      payment: existing,
      activity: {
        id: `ACT-IDEMP-${now}`,
        userId: params.userId,
        exportOrderId: params.order.id,
        eventType: 'DEMO_PAYMENT_IDEMPOTENT',
        title: `Idempotent Demo Reference Reused (${existing.paypalOrderId})`,
        description: `Duplicate creation prevented for ${params.order.orderNumber} (${safeCurrency} ${safeAmount.toLocaleString()}).`,
        createdAtEpochMs: now,
        updatedAtEpochMs: now
      },
      isDuplicatePrevented: true,
      message: `Idempotent guard reused existing pending reference ${existing.paypalOrderId} (${SIMULATED_PAYMENT_BANNER}).`
    };
  }

  const refSeed = `${params.order.id}|${params.paymentType}|${safeAmount.toFixed(2)}|${safeCurrency}|${params.existingPayments.length}`;
  const txnRef = `DEMO-TXN-${deterministicHash(refSeed)}`;

  const payment: PaymentRecord = {
    id: `PAY-${params.order.id}-${now}`,
    userId: params.userId,
    exportOrderId: params.order.id,
    paypalOrderId: txnRef,
    paypalCaptureId: '',
    approvalUrl: `https://demo.niryatflow.local/simulated-checkout?token=${txnRef}`,
    amount: safeAmount,
    currency: safeCurrency,
    paymentType: params.paymentType,
    paymentStatus: 'DEMO_PENDING',
    verificationDetails: `${SIMULATED_PAYMENT_BANNER} • Created ${params.paymentType} Order (${safeCurrency} ${safeAmount.toLocaleString()}) • Awaiting Simulated Capture`,
    createdAtEpochMs: now,
    updatedAtEpochMs: now
  };

  const activity: ActivityRecord = {
    id: `ACT-PAY-CREATE-${now}`,
    userId: params.userId,
    exportOrderId: params.order.id,
    eventType: 'DEMO_PAYMENT_CREATED',
    title: `Simulated Payment Created (${txnRef})`,
    description: `${SIMULATED_PAYMENT_BANNER} — ${params.paymentType} of ${safeCurrency} ${safeAmount.toLocaleString()} initialized for ${params.order.orderNumber}.`,
    createdAtEpochMs: now,
    updatedAtEpochMs: now
  };

  return {
    payment,
    activity,
    isDuplicatePrevented: false,
    message: `Created simulated payment ${txnRef} in DEMO_PENDING state.`
  };
}

export function transitionDemoPayment(params: {
  payment: PaymentRecord;
  orderNumber: string;
  outcome: 'DEMO_CAPTURED' | 'DEMO_FAILED' | 'DEMO_CANCELLED';
  userId: string;
}): {
  updatedPayment: PaymentRecord;
  activity: ActivityRecord;
  message: string;
} {
  const now = Date.now();

  if (params.payment.paymentStatus === 'DEMO_CAPTURED' && params.outcome === 'DEMO_CAPTURED') {
    return {
      updatedPayment: params.payment,
      activity: {
        id: `ACT-IDEMP-CAP-${now}`,
        userId: params.userId,
        exportOrderId: params.payment.exportOrderId,
        eventType: 'DEMO_PAYMENT_IDEMPOTENT',
        title: `Idempotent Capture Preserved (${params.payment.paypalCaptureId})`,
        description: `Payment ${params.payment.paypalOrderId} is already settled in DEMO_CAPTURED state.`,
        createdAtEpochMs: now,
        updatedAtEpochMs: now
      },
      message: `Transaction ${params.payment.paypalOrderId} is already settled (${params.payment.paypalCaptureId}).`
    };
  }

  const capRef =
    params.outcome === 'DEMO_CAPTURED'
      ? `DEMO-CAP-${deterministicHash(params.payment.paypalOrderId + '|CAPTURE')}`
      : '';

  const detailsByOutcome: Record<'DEMO_CAPTURED' | 'DEMO_FAILED' | 'DEMO_CANCELLED', string> = {
    DEMO_CAPTURED: `${SIMULATED_PAYMENT_BANNER} • Settled ${params.payment.currency} ${params.payment.amount.toLocaleString()} • Capture Ref ${capRef}`,
    DEMO_FAILED: `${SIMULATED_PAYMENT_BANNER} • Simulated Bank/FX Authorization Decline on ${params.payment.paypalOrderId}`,
    DEMO_CANCELLED: `${SIMULATED_PAYMENT_BANNER} • Simulated Buyer Checkout Cancellation on ${params.payment.paypalOrderId}`
  };

  const updatedPayment: PaymentRecord = {
    ...params.payment,
    paypalCaptureId: capRef,
    paymentStatus: params.outcome,
    verificationDetails: detailsByOutcome[params.outcome],
    updatedAtEpochMs: now
  };

  const activity: ActivityRecord = {
    id: `ACT-PAY-TRANS-${now}`,
    userId: params.userId,
    exportOrderId: params.payment.exportOrderId,
    eventType: `PAYMENT_${params.outcome}`,
    title: `Simulated Payment ${params.outcome.replace('DEMO_', '')} (${params.payment.paypalOrderId})`,
    description: `${detailsByOutcome[params.outcome]} for ${params.orderNumber}.`,
    createdAtEpochMs: now,
    updatedAtEpochMs: now
  };

  return {
    updatedPayment,
    activity,
    message: `${params.orderNumber}: Payment ${params.payment.paypalOrderId} transitioned to ${params.outcome}.`
  };
}
