import express from 'express';
import { createServer as createViteServer } from 'vite';
import path from 'path';
import fs from 'fs';
import dotenv from 'dotenv';
import { GoogleGenAI, Type } from '@google/genai';
import { buildSeededSampleDataset } from './src/data/sampleWorkspaceSeeder';
import type {
  ExportOrder,
  PayPalSandboxTransaction,
  PayPalSandboxStatus,
  WorkspaceMode
} from './src/types/models';

dotenv.config();

const PORT = 3000;
const PAYPAL_SANDBOX_BASE_URL = 'https://api-m.sandbox.paypal.com';
const LEDGER_FILE_PATH = path.resolve(process.cwd(), '.paypal-sandbox-ledger.json');

// Supported PayPal Orders v2 currency codes
const PAYPAL_SUPPORTED_CURRENCIES = new Set([
  'USD',
  'EUR',
  'GBP',
  'AUD',
  'CAD',
  'SGD',
  'JPY',
  'CHF',
  'HKD',
  'NZD',
  'SEK',
  'DKK',
  'NOK',
  'PLN',
  'CZK',
  'HUF',
  'ILS',
  'MXN',
  'BRL',
  'MYR',
  'PHP',
  'TWD',
  'THB'
]);

function getGeminiClient(): GoogleGenAI | null {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey || apiKey === 'MY_GEMINI_API_KEY' || apiKey.trim() === '') {
    return null;
  }
  return new GoogleGenAI({ apiKey });
}

function getPayPalCredentials(): {
  clientId: string;
  clientSecret: string;
  env: 'sandbox';
  configured: boolean;
} {
  const clientId = (process.env.PAYPAL_CLIENT_ID || '').trim();
  const clientSecret = (process.env.PAYPAL_CLIENT_SECRET || '').trim();
  const placeholderIds = new Set(['', 'YOUR_PAYPAL_SANDBOX_CLIENT_ID', 'MY_PAYPAL_CLIENT_ID']);
  const placeholderSecrets = new Set([
    '',
    'YOUR_PAYPAL_SANDBOX_CLIENT_SECRET',
    'MY_PAYPAL_CLIENT_SECRET'
  ]);
  const configured =
    !placeholderIds.has(clientId) &&
    !placeholderSecrets.has(clientSecret) &&
    clientId.length > 8 &&
    clientSecret.length > 8;
  return {
    clientId,
    clientSecret,
    env: 'sandbox',
    configured
  };
}

// Server-side OAuth2 token cache (never exposed to browser)
let cachedPayPalAccessToken: { token: string; expiresAtMs: number } | null = null;

async function generatePayPalAccessToken(): Promise<string> {
  const creds = getPayPalCredentials();
  if (!creds.configured) {
    throw new Error(
      'PAYPAL_CREDENTIALS_MISSING: Server-side PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET are not configured in AI Studio Secrets.'
    );
  }

  const now = Date.now();
  if (cachedPayPalAccessToken && cachedPayPalAccessToken.expiresAtMs > now + 30_000) {
    return cachedPayPalAccessToken.token;
  }

  const basicAuth = Buffer.from(`${creds.clientId}:${creds.clientSecret}`).toString('base64');
  const response = await fetch(`${PAYPAL_SANDBOX_BASE_URL}/v1/oauth2/token`, {
    method: 'POST',
    headers: {
      Authorization: `Basic ${basicAuth}`,
      'Content-Type': 'application/x-www-form-urlencoded',
      Accept: 'application/json'
    },
    body: 'grant_type=client_credentials'
  });

  if (!response.ok) {
    const errBody = await response.text().catch(() => '');
    let safeDetail = `HTTP ${response.status}`;
    try {
      const parsed = JSON.parse(errBody);
      safeDetail = parsed.error_description || parsed.message || safeDetail;
    } catch {
      // keep safeDetail
    }
    throw new Error(`PAYPAL_OAUTH_FAILED: Unable to authenticate with PayPal Sandbox (${safeDetail}).`);
  }

  const data = (await response.json()) as { access_token: string; expires_in?: number };
  if (!data.access_token) {
    throw new Error('PAYPAL_OAUTH_FAILED: PayPal Sandbox did not return an access_token.');
  }

  cachedPayPalAccessToken = {
    token: data.access_token,
    expiresAtMs: now + (data.expires_in ? data.expires_in * 1000 : 3000 * 1000)
  };
  return data.access_token;
}

// Authoritative server-side order registry
// Initialized with the 6 non-negotiable seeded sample orders and extensible with validated user-confirmed orders
const authoritativeSampleOrders = new Map<string, ExportOrder>();
for (const order of buildSeededSampleDataset().orders) {
  authoritativeSampleOrders.set(order.id, order);
}
const authoritativeUserOrders = new Map<string, ExportOrder>();

function lookupAuthoritativeOrder(orderId: string, workspaceMode?: string): ExportOrder | null {
  if (!orderId || typeof orderId !== 'string') return null;
  if (workspaceMode === 'SAMPLE_BUSINESS') {
    return authoritativeSampleOrders.get(orderId) || authoritativeUserOrders.get(orderId) || null;
  }
  return authoritativeUserOrders.get(orderId) || authoritativeSampleOrders.get(orderId) || null;
}

function computeAuthoritativePaymentAmount(
  order: ExportOrder,
  paymentType: 'ADVANCE' | 'BALANCE' | 'FULL'
): number {
  const gross = Number(order.orderValue);
  const advPct = Number(order.advancePercentage);
  const advanceAmount = Number((gross * (advPct / 100)).toFixed(2));
  const balanceAmount = Number(Math.max(0, gross - advanceAmount).toFixed(2));

  if (paymentType === 'ADVANCE') {
    return advanceAmount > 0 ? advanceAmount : Number(gross.toFixed(2));
  }
  if (paymentType === 'BALANCE') {
    return balanceAmount > 0 ? balanceAmount : Number(gross.toFixed(2));
  }
  return Number(gross.toFixed(2));
}

// Persistent server-side PayPal Sandbox transaction ledger
const sandboxLedger = new Map<string, PayPalSandboxTransaction>();
const inFlightCaptures = new Set<string>();

function loadSandboxLedgerFromDisk(): void {
  try {
    if (fs.existsSync(LEDGER_FILE_PATH)) {
      const raw = fs.readFileSync(LEDGER_FILE_PATH, 'utf-8');
      const parsed = JSON.parse(raw) as PayPalSandboxTransaction[];
      if (Array.isArray(parsed)) {
        for (const item of parsed) {
          if (item && item.paypalOrderId) {
            sandboxLedger.set(item.paypalOrderId, item);
          }
        }
      }
    }
  } catch {
    // start with clean in-memory ledger if disk read fails
  }
}

function persistSandboxLedgerToDisk(): void {
  try {
    const all = Array.from(sandboxLedger.values()).sort(
      (a, b) => b.createdAtEpochMs - a.createdAtEpochMs
    );
    fs.writeFileSync(LEDGER_FILE_PATH, JSON.stringify(all, null, 2), 'utf-8');
  } catch {
    // non-fatal if read-only filesystem
  }
}

loadSandboxLedgerFromDisk();

function mapPayPalStatusToInternal(rawStatus: string): PayPalSandboxStatus {
  switch ((rawStatus || '').toUpperCase()) {
    case 'CREATED':
    case 'SAVED':
    case 'PAYER_ACTION_REQUIRED':
      return 'CREATED';
    case 'APPROVED':
      return 'APPROVED';
    case 'COMPLETED':
      return 'COMPLETED';
    case 'VOIDED':
      return 'CANCELLED';
    default:
      return 'FAILED';
  }
}

async function startServer() {
  const app = express();
  app.use(express.json({ limit: '10mb' }));

  // 1. Health & AI + PayPal Readiness Endpoint
  app.get('/api/health', (_req, res) => {
    const aiClient = getGeminiClient();
    const paypal = getPayPalCredentials();
    res.json({
      status: 'ok',
      aiConfigured: Boolean(aiClient),
      paypalSandboxConfigured: paypal.configured,
      paypalEnvironment: paypal.env,
      timestamp: Date.now()
    });
  });

  // 2. Sync validated user-created/edited orders so server can authoritatively verify them for PayPal Sandbox
  app.post('/api/orders/sync-authoritative', (req, res) => {
    try {
      const { orders } = req.body as { orders?: ExportOrder[] };
      if (!Array.isArray(orders)) {
        return res.status(400).json({ error: 'orders array is required.' });
      }
      let syncedCount = 0;
      for (const o of orders) {
        if (
          o &&
          typeof o.id === 'string' &&
          typeof o.orderNumber === 'string' &&
          typeof o.orderValue === 'number' &&
          o.orderValue > 0 &&
          typeof o.currency === 'string' &&
          typeof o.advancePercentage === 'number' &&
          o.advancePercentage >= 0 &&
          o.advancePercentage <= 100
        ) {
          // Never overwrite the 6 immutable sample orders' core amounts
          if (!authoritativeSampleOrders.has(o.id)) {
            authoritativeUserOrders.set(o.id, o);
            syncedCount++;
          }
        }
      }
      return res.json({ status: 'ok', syncedCount });
    } catch (err: any) {
      return res.status(500).json({ error: err?.message || 'Failed to sync orders.' });
    }
  });

  // ============================================================================
  // GENUINE PAYPAL SANDBOX REST API (Orders v2: https://api-m.sandbox.paypal.com)
  // ============================================================================

  // GET /api/paypal/config-status
  app.get('/api/paypal/config-status', (req, res) => {
    const creds = getPayPalCredentials();
    const workspaceMode = (req.query.workspaceMode as WorkspaceMode) || 'SAMPLE_BUSINESS';
    const transactions = Array.from(sandboxLedger.values())
      .filter((tx) => !workspaceMode || tx.workspaceMode === workspaceMode)
      .sort((a, b) => b.createdAtEpochMs - a.createdAtEpochMs);

    return res.json({
      configured: creds.configured,
      environment: 'sandbox',
      apiBaseUrl: PAYPAL_SANDBOX_BASE_URL,
      clientIdPresent: Boolean(creds.clientId && creds.clientId.length > 8),
      clientSecretPresent: Boolean(creds.clientSecret && creds.clientSecret.length > 8),
      message: creds.configured
        ? 'PayPal Sandbox credentials verified on server. Ready for live PayPal Orders v2 API calls.'
        : 'Server-side PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET are not configured in AI Studio Secrets. Configure them in the Secrets panel to enable live PayPal Sandbox checkout.',
      transactions
    });
  });

  // POST /api/paypal/create-order
  app.post('/api/paypal/create-order', async (req, res) => {
    try {
      const {
        orderId,
        paymentType = 'ADVANCE',
        workspaceMode = 'SAMPLE_BUSINESS',
        returnUrl,
        cancelUrl,
        requestedAmount,
        requestedCurrency
      } = req.body || {};

      // 1. Validate Order ID exists in authoritative server registry
      if (!orderId || typeof orderId !== 'string') {
        return res.status(400).json({
          error: 'INVALID_ORDER_ID',
          message: 'A valid NiryatFlow export order ID is required.'
        });
      }

      const order = lookupAuthoritativeOrder(orderId, workspaceMode);
      if (!order) {
        return res.status(404).json({
          error: 'ORDER_NOT_FOUND',
          message: `Export order '${orderId}' was not found in the authoritative server registry.`
        });
      }

      if (order.orderStatus === 'CANCELLED') {
        return res.status(400).json({
          error: 'ORDER_INELIGIBLE',
          message: `Export order '${order.orderNumber}' is CANCELLED and cannot initiate a PayPal Sandbox payment.`
        });
      }

      if (!['ADVANCE', 'BALANCE', 'FULL'].includes(paymentType)) {
        return res.status(400).json({
          error: 'INVALID_PAYMENT_TYPE',
          message: `Payment type must be ADVANCE, BALANCE, or FULL.`
        });
      }

      // 2. Authoritative Server-Side Amount & Currency Validation (never trust browser-submitted values)
      const authoritativeCurrency = order.currency.trim().toUpperCase();
      const authoritativeAmount = computeAuthoritativePaymentAmount(order, paymentType);

      if (
        requestedCurrency !== undefined &&
        String(requestedCurrency).trim().toUpperCase() !== authoritativeCurrency
      ) {
        return res.status(400).json({
          error: 'INVALID_CURRENCY_MISMATCH',
          message: `Rejected client currency '${requestedCurrency}'. Authoritative currency for ${order.orderNumber} is '${authoritativeCurrency}'.`
        });
      }

      if (
        requestedAmount !== undefined &&
        Math.abs(Number(requestedAmount) - authoritativeAmount) > 0.01
      ) {
        return res.status(400).json({
          error: 'INVALID_AMOUNT_MISMATCH',
          message: `Rejected client-submitted amount (${requestedAmount}). Authoritative ${paymentType} amount for ${order.orderNumber} is ${authoritativeCurrency} ${authoritativeAmount.toFixed(2)}.`
        });
      }

      if (!(authoritativeAmount > 0)) {
        return res.status(400).json({
          error: 'INVALID_ORDER_AMOUNT',
          message: `Calculated payment amount (${authoritativeAmount}) must be strictly greater than zero.`
        });
      }

      if (!PAYPAL_SUPPORTED_CURRENCIES.has(authoritativeCurrency)) {
        return res.status(400).json({
          error: 'UNSUPPORTED_PAYPAL_CURRENCY',
          message: `Order ${order.orderNumber} is denominated in '${authoritativeCurrency}', which is not supported by standard PayPal Orders v2 direct settlement. Supported currencies include USD, EUR, GBP, AUD, CAD, SGD, JPY.`
        });
      }

      // 3. Authenticate with PayPal Sandbox OAuth2 on the server
      const accessToken = await generatePayPalAccessToken();

      // 4. Build safe return & cancel URLs
      const appBaseUrl =
        (typeof returnUrl === 'string' && returnUrl.startsWith('http')
          ? returnUrl.split('?')[0]
          : process.env.APP_URL || 'http://localhost:3000');
      const finalReturnUrl = `${appBaseUrl}?paypal_return=1&niryat_order=${encodeURIComponent(order.id)}`;
      const finalCancelUrl =
        typeof cancelUrl === 'string' && cancelUrl.startsWith('http')
          ? cancelUrl
          : `${appBaseUrl}?paypal_cancel=1&niryat_order=${encodeURIComponent(order.id)}`;

      // 5. Create Order via official PayPal Orders v2 API
      const createPayload = {
        intent: 'CAPTURE',
        purchase_units: [
          {
            reference_id: order.id,
            custom_id: `${workspaceMode}:${order.id}:${paymentType}`,
            description: `NiryatFlow Export Order ${order.orderNumber} (${paymentType}) - ${order.buyerName}`.slice(
              0,
              120
            ),
            amount: {
              currency_code: authoritativeCurrency,
              value: authoritativeAmount.toFixed(2)
            }
          }
        ],
        application_context: {
          brand_name: 'NiryatFlow AI Trade Finance',
          landing_page: 'LOGIN',
          user_action: 'PAY_NOW',
          return_url: finalReturnUrl,
          cancel_url: finalCancelUrl
        }
      };

      const ppResponse = await fetch(`${PAYPAL_SANDBOX_BASE_URL}/v2/checkout/orders`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
          Accept: 'application/json',
          'PayPal-Request-Id': `niryat-create-${order.id}-${paymentType}-${Date.now()}`
        },
        body: JSON.stringify(createPayload)
      });

      const ppData = (await ppResponse.json().catch(() => ({}))) as any;

      if (!ppResponse.ok || !ppData.id) {
        const errorDetail =
          ppData?.details?.[0]?.description ||
          ppData?.message ||
          `PayPal Sandbox returned HTTP ${ppResponse.status}`;
        return res.status(ppResponse.status >= 400 ? ppResponse.status : 502).json({
          error: 'PAYPAL_CREATE_ORDER_FAILED',
          message: `PayPal Sandbox rejected order creation: ${errorDetail}`,
          paypalDebugId: ppData?.debug_id || null
        });
      }

      const approvalLinkObj = Array.isArray(ppData.links)
        ? ppData.links.find((l: any) => l.rel === 'approve' || l.rel === 'payer-action')
        : null;
      const approvalUrl = approvalLinkObj?.href || '';

      const now = Date.now();
      const transaction: PayPalSandboxTransaction = {
        id: `PP-SBX-${ppData.id}`,
        workspaceMode: workspaceMode as WorkspaceMode,
        exportOrderId: order.id,
        orderNumber: order.orderNumber,
        buyerName: order.buyerName,
        paypalOrderId: ppData.id,
        paypalCaptureId: '',
        approvalUrl,
        amount: authoritativeAmount,
        currency: authoritativeCurrency,
        paymentType,
        status: mapPayPalStatusToInternal(ppData.status || 'CREATED'),
        environment: 'sandbox',
        verificationSummary: `PayPal Sandbox Order ${ppData.id} created via /v2/checkout/orders (${ppData.status || 'CREATED'}). Awaiting buyer approval at PayPal Sandbox.`,
        rawPayPalStatus: ppData.status || 'CREATED',
        createdAtEpochMs: now,
        updatedAtEpochMs: now
      };

      sandboxLedger.set(transaction.paypalOrderId, transaction);
      persistSandboxLedgerToDisk();

      return res.status(201).json({
        paypalOrderId: transaction.paypalOrderId,
        approvalUrl: transaction.approvalUrl,
        amount: transaction.amount,
        currency: transaction.currency,
        status: transaction.status,
        createdAt: new Date(now).toISOString(),
        transaction
      });
    } catch (err: any) {
      const msg = err?.message || 'Unexpected error creating PayPal Sandbox order.';
      const status = msg.startsWith('PAYPAL_CREDENTIALS_MISSING') ? 503 : 502;
      return res.status(status).json({
        error: status === 503 ? 'PAYPAL_CREDENTIALS_MISSING' : 'PAYPAL_SANDBOX_ERROR',
        message: msg
      });
    }
  });

  // GET /api/paypal/order-status?paypalOrderId=...
  app.get('/api/paypal/order-status', async (req, res) => {
    try {
      const paypalOrderId = String(req.query.paypalOrderId || '').trim();
      const orderId = req.query.orderId ? String(req.query.orderId).trim() : undefined;

      if (!paypalOrderId) {
        return res.status(400).json({
          error: 'MISSING_PAYPAL_ORDER_ID',
          message: 'Query parameter paypalOrderId is required.'
        });
      }

      const existingTx = sandboxLedger.get(paypalOrderId);
      if (!existingTx) {
        return res.status(404).json({
          error: 'TRANSACTION_NOT_FOUND',
          message: `PayPal Order ID '${paypalOrderId}' is not registered in the NiryatFlow server ledger.`
        });
      }

      if (orderId && existingTx.exportOrderId !== orderId) {
        return res.status(400).json({
          error: 'ORDER_ASSOCIATION_MISMATCH',
          message: `PayPal Order '${paypalOrderId}' belongs to '${existingTx.exportOrderId}', not '${orderId}'.`
        });
      }

      const accessToken = await generatePayPalAccessToken();
      const ppResponse = await fetch(
        `${PAYPAL_SANDBOX_BASE_URL}/v2/checkout/orders/${encodeURIComponent(paypalOrderId)}`,
        {
          method: 'GET',
          headers: {
            Authorization: `Bearer ${accessToken}`,
            Accept: 'application/json'
          }
        }
      );

      const ppData = (await ppResponse.json().catch(() => ({}))) as any;
      if (!ppResponse.ok) {
        const errorDetail =
          ppData?.details?.[0]?.description ||
          ppData?.message ||
          `PayPal Sandbox returned HTTP ${ppResponse.status}`;
        return res.status(ppResponse.status).json({
          error: 'PAYPAL_STATUS_FETCH_FAILED',
          message: `Failed to fetch PayPal Sandbox order status: ${errorDetail}`
        });
      }

      const mappedStatus = mapPayPalStatusToInternal(ppData.status);
      const captureObj = ppData?.purchase_units?.[0]?.payments?.captures?.[0];
      const captureId = captureObj?.id || existingTx.paypalCaptureId || '';
      const payerEmail = ppData?.payer?.email_address || existingTx.payerEmail;
      const payerId = ppData?.payer?.payer_id || existingTx.payerId;

      const updatedTx: PayPalSandboxTransaction = {
        ...existingTx,
        status: mappedStatus,
        paypalCaptureId: captureId,
        payerEmail,
        payerId,
        rawPayPalStatus: ppData.status,
        verificationSummary:
          mappedStatus === 'COMPLETED' && captureId
            ? `Verified COMPLETED on PayPal Sandbox. Capture ID: ${captureId}${payerEmail ? ` | Payer: ${payerEmail}` : ''}.`
            : mappedStatus === 'APPROVED'
            ? `Buyer approved PayPal Sandbox Order ${paypalOrderId}${payerEmail ? ` (${payerEmail})` : ''}. Ready for authorized server capture.`
            : `PayPal Sandbox Order ${paypalOrderId} status verified as ${ppData.status}.`,
        updatedAtEpochMs: Date.now()
      };

      sandboxLedger.set(paypalOrderId, updatedTx);
      persistSandboxLedgerToDisk();

      return res.json({
        paypalOrderId: updatedTx.paypalOrderId,
        orderId: updatedTx.exportOrderId,
        status: updatedTx.status,
        rawPayPalStatus: ppData.status,
        paypalCaptureId: updatedTx.paypalCaptureId,
        amount: updatedTx.amount,
        currency: updatedTx.currency,
        payerEmail: updatedTx.payerEmail,
        transaction: updatedTx
      });
    } catch (err: any) {
      const msg = err?.message || 'Failed to inspect PayPal Sandbox order status.';
      const status = msg.startsWith('PAYPAL_CREDENTIALS_MISSING') ? 503 : 502;
      return res.status(status).json({
        error: status === 503 ? 'PAYPAL_CREDENTIALS_MISSING' : 'PAYPAL_SANDBOX_ERROR',
        message: msg
      });
    }
  });

  // POST /api/paypal/capture-order
  app.post('/api/paypal/capture-order', async (req, res) => {
    const paypalOrderId = String(req.body?.paypalOrderId || '').trim();
    const orderId = String(req.body?.orderId || '').trim();

    if (!paypalOrderId || !orderId) {
      return res.status(400).json({
        error: 'MISSING_PARAMETERS',
        message: 'Both paypalOrderId and orderId are required to capture a PayPal Sandbox order.'
      });
    }

    const existingTx = sandboxLedger.get(paypalOrderId);
    if (!existingTx) {
      return res.status(404).json({
        error: 'TRANSACTION_NOT_FOUND',
        message: `PayPal Order '${paypalOrderId}' does not exist in the NiryatFlow server ledger.`
      });
    }

    // Verify order association
    if (existingTx.exportOrderId !== orderId) {
      return res.status(400).json({
        error: 'ORDER_ASSOCIATION_MISMATCH',
        message: `Security violation: PayPal Order '${paypalOrderId}' is bound to '${existingTx.exportOrderId}', not '${orderId}'.`
      });
    }

    // Prevent duplicate capture calls
    if (existingTx.status === 'COMPLETED' && existingTx.paypalCaptureId) {
      return res.status(409).json({
        error: 'ALREADY_CAPTURED',
        message: `PayPal Order '${paypalOrderId}' has already been captured (Capture ID: ${existingTx.paypalCaptureId}). Duplicate capture prevented.`,
        transaction: existingTx
      });
    }

    if (inFlightCaptures.has(paypalOrderId)) {
      return res.status(409).json({
        error: 'DUPLICATE_CAPTURE_IN_PROGRESS',
        message: `A capture request for PayPal Order '${paypalOrderId}' is already in progress.`
      });
    }

    inFlightCaptures.add(paypalOrderId);
    try {
      const accessToken = await generatePayPalAccessToken();

      const ppResponse = await fetch(
        `${PAYPAL_SANDBOX_BASE_URL}/v2/checkout/orders/${encodeURIComponent(paypalOrderId)}/capture`,
        {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${accessToken}`,
            'Content-Type': 'application/json',
            Accept: 'application/json',
            'PayPal-Request-Id': `niryat-capture-${paypalOrderId}`
          }
        }
      );

      const ppData = (await ppResponse.json().catch(() => ({}))) as any;

      if (!ppResponse.ok) {
        const issue = ppData?.details?.[0]?.issue || '';
        const description =
          ppData?.details?.[0]?.description ||
          ppData?.message ||
          `PayPal Sandbox returned HTTP ${ppResponse.status}`;

        // Keep as CREATED if buyer simply hasn't approved yet, otherwise mark FAILED
        const nextStatus: PayPalSandboxStatus =
          issue === 'ORDER_NOT_APPROVED' ? 'CREATED' : 'FAILED';

        const updatedTx: PayPalSandboxTransaction = {
          ...existingTx,
          status: nextStatus,
          rawPayPalStatus: issue || `HTTP_${ppResponse.status}`,
          verificationSummary: `PayPal Sandbox capture rejected (${issue || ppResponse.status}): ${description}`,
          updatedAtEpochMs: Date.now()
        };
        sandboxLedger.set(paypalOrderId, updatedTx);
        persistSandboxLedgerToDisk();

        return res.status(ppResponse.status >= 400 ? ppResponse.status : 422).json({
          error: issue || 'PAYPAL_CAPTURE_FAILED',
          message: `PayPal Sandbox capture failed: ${description}`,
          transaction: updatedTx
        });
      }

      // Verify capture response is genuinely COMPLETED
      const captureObj = ppData?.purchase_units?.[0]?.payments?.captures?.[0];
      const captureId = captureObj?.id || '';
      const isCompleted =
        ppData.status === 'COMPLETED' &&
        (!captureObj?.status || captureObj.status === 'COMPLETED') &&
        Boolean(captureId);

      const payerEmail = ppData?.payer?.email_address || existingTx.payerEmail;
      const payerId = ppData?.payer?.payer_id || existingTx.payerId;

      const updatedTx: PayPalSandboxTransaction = {
        ...existingTx,
        status: isCompleted ? 'COMPLETED' : 'FAILED',
        paypalCaptureId: captureId,
        payerEmail,
        payerId,
        rawPayPalStatus: ppData.status,
        verificationSummary: isCompleted
          ? `PayPal Sandbox settlement COMPLETED via /v2/checkout/orders/${paypalOrderId}/capture. Capture ID: ${captureId}${payerEmail ? ` | Buyer: ${payerEmail}` : ''}.`
          : `PayPal Sandbox capture response status was '${ppData.status}' (Capture status: '${captureObj?.status || 'NONE'}'). Not marked completed.`,
        updatedAtEpochMs: Date.now()
      };

      sandboxLedger.set(paypalOrderId, updatedTx);
      persistSandboxLedgerToDisk();

      if (!isCompleted) {
        return res.status(422).json({
          error: 'CAPTURE_NOT_COMPLETED',
          message: updatedTx.verificationSummary,
          transaction: updatedTx
        });
      }

      return res.json({
        paypalOrderId: updatedTx.paypalOrderId,
        paypalCaptureId: updatedTx.paypalCaptureId,
        orderId: updatedTx.exportOrderId,
        status: updatedTx.status,
        amount: updatedTx.amount,
        currency: updatedTx.currency,
        payerEmail: updatedTx.payerEmail,
        capturedAt: new Date(updatedTx.updatedAtEpochMs).toISOString(),
        transaction: updatedTx
      });
    } catch (err: any) {
      const msg = err?.message || 'Unexpected error capturing PayPal Sandbox order.';
      const status = msg.startsWith('PAYPAL_CREDENTIALS_MISSING') ? 503 : 502;
      return res.status(status).json({
        error: status === 503 ? 'PAYPAL_CREDENTIALS_MISSING' : 'PAYPAL_SANDBOX_ERROR',
        message: msg
      });
    } finally {
      inFlightCaptures.delete(paypalOrderId);
    }
  });

  // POST /api/paypal/cancel-order (Buyer returned via cancel_url)
  app.post('/api/paypal/cancel-order', (req, res) => {
    const paypalOrderId = String(req.body?.paypalOrderId || '').trim();
    const orderId = req.body?.orderId ? String(req.body.orderId).trim() : undefined;

    if (!paypalOrderId) {
      return res.status(400).json({
        error: 'MISSING_PAYPAL_ORDER_ID',
        message: 'paypalOrderId is required.'
      });
    }

    const existingTx = sandboxLedger.get(paypalOrderId);
    if (!existingTx) {
      return res.status(404).json({
        error: 'TRANSACTION_NOT_FOUND',
        message: `PayPal Order '${paypalOrderId}' was not found in the server ledger.`
      });
    }

    if (orderId && existingTx.exportOrderId !== orderId) {
      return res.status(400).json({
        error: 'ORDER_ASSOCIATION_MISMATCH',
        message: `PayPal Order '${paypalOrderId}' does not match export order '${orderId}'.`
      });
    }

    if (existingTx.status === 'COMPLETED') {
      return res.status(409).json({
        error: 'ALREADY_COMPLETED',
        message: `PayPal Order '${paypalOrderId}' is already COMPLETED and cannot be cancelled.`,
        transaction: existingTx
      });
    }

    const updatedTx: PayPalSandboxTransaction = {
      ...existingTx,
      status: 'CANCELLED',
      rawPayPalStatus: 'CANCELLED_BY_BUYER',
      verificationSummary: `Buyer cancelled PayPal Sandbox approval flow for Order ${paypalOrderId}.`,
      updatedAtEpochMs: Date.now()
    };

    sandboxLedger.set(paypalOrderId, updatedTx);
    persistSandboxLedgerToDisk();

    return res.json({
      paypalOrderId: updatedTx.paypalOrderId,
      status: updatedTx.status,
      transaction: updatedTx
    });
  });

  // ============================================================================
  // GEMINI AI SERVER-SIDE ENDPOINTS
  // ============================================================================

  // 3. Server-side Gemini AI Purchase Order Extraction
  app.post('/api/ai/extract-po', async (req, res) => {
    const ai = getGeminiClient();
    if (!ai) {
      return res.status(503).json({
        error: 'GEMINI_API_KEY_NOT_CONFIGURED',
        message: 'Server-side Gemini API key is not configured. Using deterministic parser fallback.'
      });
    }

    const { rawInputText } = req.body;
    if (!rawInputText || typeof rawInputText !== 'string') {
      return res.status(400).json({ error: 'rawInputText is required' });
    }

    try {
      const response = await ai.models.generateContent({
        model: 'gemini-2.5-flash',
        contents: `Extract structured export purchase order fields from the following commercial text.
Do not invent values that are not present or clearly implied. If a field is missing, leave string empty or 0 and list it in missingFields.

Commercial Document Text:
${rawInputText}`,
        config: {
          responseMimeType: 'application/json',
          responseSchema: {
            type: Type.OBJECT,
            properties: {
              exporterName: { type: Type.STRING },
              exporterContact: { type: Type.STRING },
              exporterAddress: { type: Type.STRING },
              buyerName: { type: Type.STRING },
              buyerCountry: { type: Type.STRING },
              buyerContact: { type: Type.STRING },
              buyerAddress: { type: Type.STRING },
              productName: { type: Type.STRING },
              productDescription: { type: Type.STRING },
              quantity: { type: Type.NUMBER },
              unit: { type: Type.STRING },
              unitPrice: { type: Type.NUMBER },
              currency: { type: Type.STRING },
              incoterm: { type: Type.STRING },
              destinationPort: { type: Type.STRING },
              orderDate: { type: Type.STRING },
              expectedShipmentDate: { type: Type.STRING },
              paymentTerms: { type: Type.STRING },
              advancePercentage: { type: Type.NUMBER },
              confidenceScore: { type: Type.NUMBER },
              missingFields: { type: Type.ARRAY, items: { type: Type.STRING } },
              uncertainFields: { type: Type.ARRAY, items: { type: Type.STRING } },
              conflictsDetected: { type: Type.ARRAY, items: { type: Type.STRING } },
              aiExtractionNotes: { type: Type.STRING }
            }
          }
        }
      });

      const parsed = JSON.parse(response.text || '{}');
      return res.json({
        ...parsed,
        extractionEngineLabel: 'Gemini 2.5 Flash Structured Trade Extractor (Human Review Required)'
      });
    } catch (error: any) {
      return res.status(500).json({
        error: 'AI_EXTRACTION_FAILED',
        message: error?.message || 'Gemini extraction failed'
      });
    }
  });

  // 4. Server-side Gemini AI Grounded Trade Assistant Query
  app.post('/api/ai/query', async (req, res) => {
    const ai = getGeminiClient();
    if (!ai) {
      return res.status(503).json({
        error: 'GEMINI_API_KEY_NOT_CONFIGURED'
      });
    }

    const { query, workspaceSnapshot } = req.body;
    if (!query || !workspaceSnapshot) {
      return res.status(400).json({ error: 'query and workspaceSnapshot are required' });
    }

    try {
      const response = await ai.models.generateContent({
        model: 'gemini-2.5-flash',
        contents: `You are NiryatFlow AI Trade Finance & Operations Analyst.
Answer the user's question strictly grounded in the provided JSON workspace records.
Never fabricate order numbers, payment captures, or document verification statuses.
Always cite specific Order IDs (e.g. ORDER-001 to ORDER-006), currencies, and amounts from the data.

Workspace Records Snapshot:
${JSON.stringify(workspaceSnapshot)}

User Question:
${query}`
      });

      return res.json({
        answer: response.text || 'Unable to generate grounded response.',
        engine: 'Gemini 2.5 Flash Grounded Trade Copilot'
      });
    } catch (error: any) {
      return res.status(500).json({
        error: 'AI_QUERY_FAILED',
        message: error?.message || 'Gemini query failed'
      });
    }
  });

  // 5. Server-side Gemini AI Structured Portfolio Recommendations
  app.post('/api/ai/advisor', async (req, res) => {
    const ai = getGeminiClient();
    if (!ai) {
      return res.status(503).json({
        error: 'GEMINI_API_KEY_NOT_CONFIGURED'
      });
    }

    const { workspaceSnapshot } = req.body;
    if (!workspaceSnapshot) {
      return res.status(400).json({ error: 'workspaceSnapshot is required' });
    }

    try {
      const response = await ai.models.generateContent({
        model: 'gemini-2.5-flash',
        contents: `Analyze this export workspace snapshot and produce structured executive advisory notes.
Ground every finding in the exact order IDs, amounts, margin percentages, and document statuses provided.
Never invent metrics or external market figures.

Workspace Snapshot:
${JSON.stringify(workspaceSnapshot)}`,
        config: {
          responseMimeType: 'application/json',
          responseSchema: {
            type: Type.OBJECT,
            properties: {
              executiveBrief: { type: Type.STRING },
              keyFocusOrderIds: { type: Type.ARRAY, items: { type: Type.STRING } }
            }
          }
        }
      });

      return res.json(JSON.parse(response.text || '{}'));
    } catch (error: any) {
      return res.status(500).json({
        error: 'AI_ADVISOR_FAILED',
        message: error?.message || 'Gemini advisor failed'
      });
    }
  });

  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa'
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*all', (_req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`NiryatFlow AI Server running on http://0.0.0.0:${PORT}`);
  });
}

startServer();
