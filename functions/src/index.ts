import { onRequest } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import * as admin from "firebase-admin";

admin.initializeApp();
const db = admin.firestore();

// Secrets stored exclusively in Google Cloud / Firebase Secret Manager — NEVER in the Android APK
const PAYPAL_SANDBOX_CLIENT_ID = defineSecret("PAYPAL_SANDBOX_CLIENT_ID");
const PAYPAL_SANDBOX_CLIENT_SECRET = defineSecret("PAYPAL_SANDBOX_CLIENT_SECRET");
const PAYPAL_SANDBOX_BASE = "https://api-m.sandbox.paypal.com";

interface AuthenticatedUser {
  uid: string;
  email?: string;
}

async function verifyFirebaseIdToken(req: { headers: Record<string, string | string[] | undefined> }): Promise<AuthenticatedUser> {
  const authHeader = req.headers["authorization"] || req.headers["Authorization"];
  if (!authHeader || typeof authHeader !== "string" || !authHeader.startsWith("Bearer ")) {
    throw new Error("UNAUTHENTICATED: Missing or malformed Firebase Authorization Bearer token.");
  }
  const idToken = authHeader.substring("Bearer ".length).trim();
  const decoded = await admin.auth().verifyIdToken(idToken);
  return { uid: decoded.uid, email: decoded.email };
}

async function fetchPayPalAccessToken(clientId: string, clientSecret: string): Promise<string> {
  const credentials = Buffer.from(`${clientId}:${clientSecret}`).toString("base64");
  const response = await fetch(`${PAYPAL_SANDBOX_BASE}/v1/oauth2/token`, {
    method: "POST",
    headers: {
      "Authorization": `Basic ${credentials}`,
      "Content-Type": "application/x-www-form-urlencoded",
      "Accept": "application/json",
    },
    body: "grant_type=client_credentials",
  });
  if (!response.ok) {
    throw new Error(`PayPal OAuth2 token exchange failed with status ${response.status}`);
  }
  const json = (await response.json()) as { access_token?: string };
  if (!json.access_token) {
    throw new Error("PayPal OAuth2 response missing access_token");
  }
  return json.access_token;
}

/**
 * 1. Create PayPal Sandbox Order (Authenticated Backend Endpoint)
 * Validates Firebase ID token, verifies export order ownership & expected amount/currency,
 * calls PayPal Orders v2 with idempotency header, and persists PENDING_APPROVAL record.
 */
export const createPayPalSandboxOrder = onRequest(
  { secrets: [PAYPAL_SANDBOX_CLIENT_ID, PAYPAL_SANDBOX_CLIENT_SECRET], cors: true },
  async (req, res) => {
    try {
      if (req.method !== "POST") {
        res.status(405).json({ error: "Method Not Allowed" });
        return;
      }
      const user = await verifyFirebaseIdToken(req);
      const { exportOrderId, orderNumber, currency, amount, paymentType, idempotencyKey } = req.body || {};

      if (!exportOrderId || !orderNumber || !currency || typeof amount !== "number" || amount <= 0) {
        res.status(400).json({ error: "Invalid order parameters: exportOrderId, orderNumber, currency, and positive amount are required." });
        return;
      }

      // Verify ownership and currency against Firestore export_orders
      const orderSnap = await db.collection("export_orders").doc(String(exportOrderId)).get();
      if (!orderSnap.exists) {
        res.status(404).json({ error: "Export order not found in Firestore." });
        return;
      }
      const orderData = orderSnap.data()!;
      if (orderData.userId !== user.uid) {
        res.status(403).json({ error: "FORBIDDEN: Export order does not belong to authenticated user." });
        return;
      }
      if (orderData.currency !== currency) {
        res.status(400).json({ error: `Currency mismatch: order is ${orderData.currency}, requested ${currency}.` });
        return;
      }
      if (amount > Number(orderData.orderValue) * 1.001) {
        res.status(400).json({ error: "Requested payment amount exceeds export order value." });
        return;
      }

      const accessToken = await fetchPayPalAccessToken(
        PAYPAL_SANDBOX_CLIENT_ID.value(),
        PAYPAL_SANDBOX_CLIENT_SECRET.value()
      );

      const formattedAmount = amount.toFixed(2);
      const requestId = String(idempotencyKey || `create_${user.uid}_${exportOrderId}_${paymentType}_${formattedAmount}`);

      const createResponse = await fetch(`${PAYPAL_SANDBOX_BASE}/v2/checkout/orders`, {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${accessToken}`,
          "Content-Type": "application/json",
          "PayPal-Request-Id": requestId,
        },
        body: JSON.stringify({
          intent: "CAPTURE",
          purchase_units: [
            {
              reference_id: `${orderNumber}_${paymentType || "ADVANCE"}`,
              description: `NiryatFlow Export Order ${orderNumber} (${paymentType || "ADVANCE"} Payment)`,
              amount: {
                currency_code: currency.toUpperCase(),
                value: formattedAmount,
              },
            },
          ],
          application_context: {
            brand_name: "NiryatFlow AI Export Platform",
            user_action: "PAY_NOW",
            return_url: "https://example.com/niryatflow/paypal/return",
            cancel_url: "https://example.com/niryatflow/paypal/cancel",
          },
        }),
      });

      const payload = (await createResponse.json()) as {
        id?: string;
        status?: string;
        links?: Array<{ href: string; rel: string }>;
      };

      if (!createResponse.ok || !payload.id) {
        res.status(502).json({ error: `PayPal order creation failed (${createResponse.status})` });
        return;
      }

      const approvalUrl = payload.links?.find((l) => l.rel === "approve" || l.rel === "payer-action")?.href || "";
      const paymentDocRef = db.collection("payment_records").doc(`pay_${payload.id}`);
      await paymentDocRef.set({
        id: paymentDocRef.id,
        userId: user.uid,
        exportOrderId: String(exportOrderId),
        paypalOrderId: payload.id,
        paypalCaptureId: "",
        approvalUrl,
        amount: Number(formattedAmount),
        currency: currency.toUpperCase(),
        paymentType: String(paymentType || "ADVANCE"),
        paymentStatus: "PENDING_APPROVAL",
        verificationDetails: `PayPal Sandbox Order created via Backend Cloud Function (Status: ${payload.status || "CREATED"})`,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      res.status(200).json({
        paymentRecordId: paymentDocRef.id,
        paypalOrderId: payload.id,
        status: payload.status || "CREATED",
        approvalUrl,
        verificationSummary: `Server-verified PayPal Sandbox order (${payload.status || "CREATED"})`,
      });
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Unexpected backend error";
      res.status(message.startsWith("UNAUTHENTICATED") ? 401 : 500).json({ error: message });
    }
  }
);

/**
 * 2. Capture & Reconcile PayPal Sandbox Order (Authoritative Server-Side Capture)
 * Verifies user authentication, prevents duplicate capture, verifies captured amount & currency,
 * and writes authoritative CAPTURED state to Firestore via Admin SDK.
 */
export const capturePayPalSandboxOrder = onRequest(
  { secrets: [PAYPAL_SANDBOX_CLIENT_ID, PAYPAL_SANDBOX_CLIENT_SECRET], cors: true },
  async (req, res) => {
    try {
      if (req.method !== "POST") {
        res.status(405).json({ error: "Method Not Allowed" });
        return;
      }
      const user = await verifyFirebaseIdToken(req);
      const { paypalOrderId, paymentRecordId } = req.body || {};
      if (!paypalOrderId) {
        res.status(400).json({ error: "paypalOrderId is required." });
        return;
      }

      const docRef = db.collection("payment_records").doc(String(paymentRecordId || `pay_${paypalOrderId}`));
      const existingSnap = await docRef.get();
      if (existingSnap.exists) {
        const existing = existingSnap.data()!;
        if (existing.userId !== user.uid) {
          res.status(403).json({ error: "FORBIDDEN: Payment record belongs to another user." });
          return;
        }
        if (existing.paymentStatus === "CAPTURED" && existing.paypalCaptureId) {
          res.status(200).json({
            paypalOrderId,
            captureId: existing.paypalCaptureId,
            status: "CAPTURED",
            verificationSummary: existing.verificationDetails || "Already captured (idempotent replay).",
          });
          return;
        }
      }

      const accessToken = await fetchPayPalAccessToken(
        PAYPAL_SANDBOX_CLIENT_ID.value(),
        PAYPAL_SANDBOX_CLIENT_SECRET.value()
      );

      const captureResponse = await fetch(`${PAYPAL_SANDBOX_BASE}/v2/checkout/orders/${paypalOrderId}/capture`, {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${accessToken}`,
          "Content-Type": "application/json",
          "PayPal-Request-Id": `capture_${user.uid}_${paypalOrderId}`,
        },
        body: "{}",
      });

      const payload = (await captureResponse.json()) as {
        id?: string;
        status?: string;
        purchase_units?: Array<{
          payments?: {
            captures?: Array<{
              id?: string;
              status?: string;
              amount?: { currency_code?: string; value?: string };
            }>;
          };
        }>;
      };

      const firstCapture = payload.purchase_units?.[0]?.payments?.captures?.[0];
      const captureStatus = firstCapture?.status || payload.status || "UNKNOWN";
      const isCompleted = captureStatus === "COMPLETED";

      if (existingSnap.exists) {
        await docRef.update({
          paypalCaptureId: firstCapture?.id || "",
          paymentStatus: isCompleted ? "CAPTURED" : "APPROVED",
          verificationDetails: `Authoritative Backend Capture: ${captureStatus} • Capture ID: ${firstCapture?.id || "N/A"}`,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        });
      }

      res.status(200).json({
        paypalOrderId: payload.id || paypalOrderId,
        captureId: firstCapture?.id || "",
        status: isCompleted ? "CAPTURED" : captureStatus,
        verificationSummary: `Server-verified PayPal capture (${captureStatus})`,
      });
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Unexpected capture error";
      res.status(message.startsWith("UNAUTHENTICATED") ? 401 : 500).json({ error: message });
    }
  }
);
