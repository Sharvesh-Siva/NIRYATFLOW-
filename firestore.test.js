const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_exporter_1";
const BOB_UID = "bob_exporter_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validOrderPayload(uid) {
  const now = new Date();
  return {
    userId: uid,
    orderNumber: "NF-2026-1001",
    exporterName: "Bharat Spices & Agro Exports",
    exporterContact: "exports@bharatspices.in",
    exporterAddress: "Kochi, Kerala, India",
    buyerName: "Nordic Foods GmbH",
    buyerCountry: "Germany",
    buyerContact: "procurement@nordicfoods.de",
    buyerAddress: "Hamburg, Germany",
    productName: "Organic Malabar Black Pepper",
    productDescription: "HS 090411 - 550 GL Grade",
    quantity: 5000,
    unit: "KG",
    unitPrice: 8.5,
    orderValue: 42500.0,
    currency: "USD",
    incoterm: "FOB",
    destinationPort: "Port of Hamburg",
    orderDate: "2026-11-09",
    expectedShipmentDate: "2026-11-25",
    paymentTerms: "30% Advance, 70% CAD",
    advancePercentage: 30.0,
    orderStatus: "CREATED",
    extractionSource: "MANUAL",
    createdAt: now,
    updatedAt: now,
  };
}

function validPaymentPayload(uid, paymentStatus) {
  const now = new Date();
  return {
    userId: uid,
    exportOrderId: "order_1",
    paypalOrderId: "PAYID-TEST-001",
    paypalCaptureId: paymentStatus === "CAPTURED" ? "CAP-TEST-001" : "",
    approvalUrl: "https://www.sandbox.paypal.com/checkoutnow?token=PAYID-TEST-001",
    amount: 12750.0,
    currency: "USD",
    paymentType: "ADVANCE",
    paymentStatus,
    verificationDetails: "Test payment record",
    createdAt: now,
    updatedAt: now,
  };
}

function validDocumentPayload(uid, status) {
  const now = new Date();
  return {
    userId: uid,
    exportOrderId: "order_1",
    documentType: "Commercial Invoice",
    requirementLevel: "REQUIRED",
    status,
    fileName: "commercial_invoice.pdf",
    fileReference: "local://niryat_documents/order_1/commercial_invoice.pdf",
    reviewerNote: "Uploaded for review",
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user: cannot list or read export_orders", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("export_orders").get());
  await assertFails(unauthDb.collection("export_orders").doc("order_1").get());
});

test("Authenticated user: can create and query own export_orders", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("export_orders").doc("order_1").set(validOrderPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("export_orders").where("userId", "==", ALICE_UID).get()
  );
});

test("Authenticated user: cannot read or update another user's export_order", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("export_orders")
      .doc("bob_order")
      .set(validOrderPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("export_orders").doc("bob_order").get());
  await assertFails(
    aliceDb.collection("export_orders").doc("bob_order").update({
      orderStatus: "VALIDATED",
      updatedAt: new Date(),
    })
  );
});

test("Security Hardening (Part 5): Ordinary client CANNOT falsify paymentStatus as CAPTURED", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  // Creating PENDING_APPROVAL succeeds
  await assertSucceeds(
    aliceDb
      .collection("payment_records")
      .doc("pay_1")
      .set(validPaymentPayload(ALICE_UID, "PENDING_APPROVAL"))
  );
  // Creating CAPTURED directly from client fails
  await assertFails(
    aliceDb
      .collection("payment_records")
      .doc("pay_forged")
      .set(validPaymentPayload(ALICE_UID, "CAPTURED"))
  );
  // Updating PENDING_APPROVAL -> CAPTURED directly from client fails
  await assertFails(
    aliceDb.collection("payment_records").doc("pay_1").update({
      paymentStatus: "CAPTURED",
      paypalCaptureId: "FORGED-CAP-999",
      updatedAt: new Date(),
    })
  );
});

test("Security Hardening (Part 5): Ordinary client CANNOT self-verify document_records without REVIEWER claim", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  // Uploading as UPLOADED or PENDING_REVIEW succeeds
  await assertSucceeds(
    aliceDb
      .collection("document_records")
      .doc("doc_1")
      .set(validDocumentPayload(ALICE_UID, "PENDING_REVIEW"))
  );
  // Ordinary user attempting to mark as VERIFIED fails
  await assertFails(
    aliceDb.collection("document_records").doc("doc_1").update({
      status: "VERIFIED",
      updatedAt: new Date(),
    })
  );
  // Authorized Reviewer CAN mark as VERIFIED
  const reviewerDb = testEnv
    .authenticatedContext(ALICE_UID, { role: "REVIEWER" })
    .firestore();
  await assertSucceeds(
    reviewerDb.collection("document_records").doc("doc_1").update({
      status: "VERIFIED",
      updatedAt: new Date(),
    })
  );
});

test("Shadow update test: rejects ghost field on export_orders", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const payload = { ...validOrderPayload(ALICE_UID), isAdminBypass: true };
  await assertFails(aliceDb.collection("export_orders").doc("order_ghost").set(payload));
});

test("Query trust test: fails list query without userId filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("export_orders").get());
});
