import type {
  ExportOrder,
  PayPalSandboxConfigStatus,
  PayPalSandboxTransaction,
  WorkspaceMode
} from '../types/models';

export async function syncAuthoritativeOrdersToServer(orders: ExportOrder[]): Promise<void> {
  try {
    await fetch('/api/orders/sync-authoritative', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ orders })
    });
  } catch {
    // Non-fatal in offline preview
  }
}

export async function fetchPayPalSandboxConfigStatus(
  workspaceMode: WorkspaceMode
): Promise<PayPalSandboxConfigStatus> {
  const response = await fetch(
    `/api/paypal/config-status?workspaceMode=${encodeURIComponent(workspaceMode)}`
  );
  if (!response.ok) {
    throw new Error(`Failed to fetch PayPal Sandbox config status (HTTP ${response.status}).`);
  }
  return (await response.json()) as PayPalSandboxConfigStatus;
}

export async function createPayPalSandboxOrder(params: {
  orderId: string;
  paymentType: 'ADVANCE' | 'BALANCE' | 'FULL';
  workspaceMode: WorkspaceMode;
  requestedAmount?: number;
  requestedCurrency?: string;
}): Promise<{
  paypalOrderId: string;
  approvalUrl: string;
  amount: number;
  currency: string;
  status: string;
  createdAt: string;
  transaction: PayPalSandboxTransaction;
}> {
  const baseUrl = window.location.origin + window.location.pathname;
  const response = await fetch('/api/paypal/create-order', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      orderId: params.orderId,
      paymentType: params.paymentType,
      workspaceMode: params.workspaceMode,
      requestedAmount: params.requestedAmount,
      requestedCurrency: params.requestedCurrency,
      returnUrl: baseUrl,
      cancelUrl: `${baseUrl}?paypal_cancel=1&niryat_order=${encodeURIComponent(params.orderId)}`
    })
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.message || data.error || `PayPal create-order failed (HTTP ${response.status}).`);
  }
  return data;
}

export async function fetchPayPalSandboxOrderStatus(params: {
  paypalOrderId: string;
  orderId?: string;
}): Promise<{
  paypalOrderId: string;
  orderId: string;
  status: string;
  rawPayPalStatus: string;
  paypalCaptureId: string;
  amount: number;
  currency: string;
  payerEmail?: string;
  transaction: PayPalSandboxTransaction;
}> {
  const qs = new URLSearchParams({ paypalOrderId: params.paypalOrderId });
  if (params.orderId) {
    qs.set('orderId', params.orderId);
  }
  const response = await fetch(`/api/paypal/order-status?${qs.toString()}`);
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.message || data.error || `PayPal order-status check failed (HTTP ${response.status}).`);
  }
  return data;
}

export async function capturePayPalSandboxOrder(params: {
  paypalOrderId: string;
  orderId: string;
}): Promise<{
  paypalOrderId: string;
  paypalCaptureId: string;
  orderId: string;
  status: string;
  amount: number;
  currency: string;
  payerEmail?: string;
  capturedAt: string;
  transaction: PayPalSandboxTransaction;
}> {
  const response = await fetch('/api/paypal/capture-order', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      paypalOrderId: params.paypalOrderId,
      orderId: params.orderId
    })
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.message || data.error || `PayPal capture-order failed (HTTP ${response.status}).`);
  }
  return data;
}

export async function cancelPayPalSandboxOrder(params: {
  paypalOrderId: string;
  orderId?: string;
}): Promise<{
  paypalOrderId: string;
  status: string;
  transaction: PayPalSandboxTransaction;
}> {
  const response = await fetch('/api/paypal/cancel-order', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params)
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.message || data.error || `PayPal cancel-order failed (HTTP ${response.status}).`);
  }
  return data;
}

export function inspectPayPalRedirectParams(): {
  type: 'RETURN_APPROVED' | 'CANCELLED' | null;
  paypalOrderId: string | null;
  payerId: string | null;
  niryatOrderId: string | null;
} {
  if (typeof window === 'undefined') {
    return { type: null, paypalOrderId: null, payerId: null, niryatOrderId: null };
  }
  const params = new URLSearchParams(window.location.search);
  const isReturn = params.get('paypal_return') === '1';
  const isCancel = params.get('paypal_cancel') === '1';
  const token = params.get('token');
  const payerId = params.get('PayerID');
  const niryatOrderId = params.get('niryat_order');

  if (isReturn && token) {
    return {
      type: 'RETURN_APPROVED',
      paypalOrderId: token,
      payerId,
      niryatOrderId
    };
  }
  if (isCancel && token) {
    return {
      type: 'CANCELLED',
      paypalOrderId: token,
      payerId: null,
      niryatOrderId
    };
  }
  return { type: null, paypalOrderId: null, payerId: null, niryatOrderId: null };
}

export function clearPayPalRedirectParamsFromUrl(): void {
  if (typeof window === 'undefined') return;
  const url = new URL(window.location.href);
  url.searchParams.delete('paypal_return');
  url.searchParams.delete('paypal_cancel');
  url.searchParams.delete('token');
  url.searchParams.delete('PayerID');
  url.searchParams.delete('niryat_order');
  window.history.replaceState({}, document.title, url.pathname + url.search);
}
