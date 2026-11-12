import {
  ActivityRecord,
  BusinessWorkspaceProfile,
  DocumentRecord,
  ExportOrder,
  FinancialAssessment,
  PaymentRecord
} from '../types/models';
import { calculateAssessment } from '../domain/financialEngine';

export const SAMPLE_USER_ID = 'sample_workspace_tn_exports';
const SAMPLE_STORAGE_KEY = 'niryat_sample_workspace_v1';
const PROD_STORAGE_KEY = 'niryat_production_workspace_v1';

const DAY_MS = 24 * 60 * 60 * 1000;
const HOUR_MS = 60 * 60 * 1000;

export const SAMPLE_WORKSPACE_PROFILE: BusinessWorkspaceProfile = {
  companyName: 'Tamil Nadu Global Exports Pvt. Ltd.',
  iecNumber: 'IEC-0419082731 (Sample Demonstration Profile)',
  gstin: '33AABCT4829K1Z8 (Sample Demo Entity)',
  adCode: 'AD-6390021-CHENNAI-PORT',
  primaryBank: 'State Bank of India — Overseas Trade Finance Branch, Chennai',
  headquarters: 'Guindy Industrial Estate, Chennai, Tamil Nadu 600032, India',
  exportSpecialties:
    'Organic Spices, GOTS Cotton Apparel, Cold-Pressed Moringa Oil, Finished Leather & Precision CNC Valve Bodies',
  settlementCurrencies: ['EUR', 'USD', 'AED', 'GBP', 'JPY'],
  workspaceBannerText:
    'SAMPLE BUSINESS WORKSPACE — Tamil Nadu Global Exports Pvt. Ltd. (Chennai, India) • Pre-loaded multi-corridor MSME export portfolio for interactive demonstration'
};

export interface WorkspaceDataset {
  orders: ExportOrder[];
  assessments: FinancialAssessment[];
  documents: DocumentRecord[];
  payments: PaymentRecord[];
  activities: ActivityRecord[];
}

export function buildSeededSampleDataset(nowMs = Date.now()): WorkspaceDataset {
  const tsOrder001 = nowMs - 4 * HOUR_MS; // 4h ago (in 1D, 1W, 1M, 3M, 1Y, ALL)
  const tsOrder002 = nowMs - 3 * DAY_MS; // 3d ago (in 1W, 1M, 3M, 1Y, ALL)
  const tsOrder003 = nowMs - 6 * DAY_MS; // 6d ago (in 1W, 1M, 3M, 1Y, ALL)
  const tsOrder004 = nowMs - 18 * DAY_MS; // 18d ago (in 1M, 3M, 1Y, ALL)
  const tsOrder005 = nowMs - 55 * DAY_MS; // 55d ago (in 3M, 1Y, ALL)
  const tsOrder006 = nowMs - 210 * DAY_MS; // 210d ago (in 1Y, ALL)

  const orders: ExportOrder[] = [
    {
      id: 'ORDER-001',
      userId: SAMPLE_USER_ID,
      orderNumber: 'ORDER-001',
      exporterName: SAMPLE_WORKSPACE_PROFILE.companyName,
      exporterContact: 'exports@tnglobalexports.in | +91 44 2250 4890',
      exporterAddress: SAMPLE_WORKSPACE_PROFILE.headquarters,
      buyerName: 'Hanseatic Spice & Botanicals GmbH',
      buyerCountry: 'Germany',
      buyerContact: 'procurement@hanseatic-spice.de | +49 40 3890 1120',
      buyerAddress: 'Am Sandtorkai 48, HafenCity, 20457 Hamburg, Germany',
      productName: 'Organic Erode Turmeric Finger & Alleppey Curcumin Powder (HS 091030)',
      productDescription:
        'EU Organic Certified High-Curcumin (5.2%) Erode Turmeric Finger & Steam-Sterilized Powder in 25kg Food-Grade Kraft Multi-Wall Bags',
      quantity: 12000,
      unit: 'KG',
      unitPrice: 4.1,
      orderValue: 49200,
      currency: 'EUR',
      incoterm: 'CIF',
      destinationPort: 'Port of Hamburg, Germany',
      orderDate: '2026-04-08',
      expectedShipmentDate: '2026-04-28',
      paymentTerms: '40% Advance via PayPal Sandbox, 60% CAD against Original B/L',
      advancePercentage: 40,
      orderStatus: 'PAYMENT_INITIATED',
      extractionSource: 'AI_EXTRACTED_CONFIRMED',
      createdAtEpochMs: tsOrder001,
      updatedAtEpochMs: tsOrder001
    },
    {
      id: 'ORDER-002',
      userId: SAMPLE_USER_ID,
      orderNumber: 'ORDER-002',
      exporterName: SAMPLE_WORKSPACE_PROFILE.companyName,
      exporterContact: 'apparel@tnglobalexports.in | +91 421 247 9910',
      exporterAddress: 'Tirupur Knitwear Export Cluster Unit IV, Tamil Nadu 641604, India',
      buyerName: 'Pacific Coast Sustainable Apparel Inc.',
      buyerCountry: 'United States',
      buyerContact: 'sourcing@pacificcoastapparel.com | +1 310 555 0198',
      buyerAddress: '1450 OceanGate Blvd, Suite 800, Long Beach, CA 90802, USA',
      productName: '100% GOTS Organic Combed Cotton Interlock Baby & Kids Rompers (HS 611120)',
      productDescription:
        '180 GSM Bio-Washed Organic Cotton Knitted Apparel, Azo-Free Reactive Dyed, Individual Retail Polybag with Barcode',
      quantity: 8500,
      unit: 'PCS',
      unitPrice: 6.8,
      orderValue: 57800,
      currency: 'USD',
      incoterm: 'FOB',
      destinationPort: 'Port of Long Beach, USA',
      orderDate: '2026-03-19',
      expectedShipmentDate: '2026-04-02',
      paymentTerms: '100% Full Settlement Captured via PayPal Sandbox',
      advancePercentage: 100,
      orderStatus: 'COMPLETED',
      extractionSource: 'AI_EXTRACTED_CONFIRMED',
      createdAtEpochMs: tsOrder002,
      updatedAtEpochMs: tsOrder002
    },
    {
      id: 'ORDER-003',
      userId: SAMPLE_USER_ID,
      orderNumber: 'ORDER-003',
      exporterName: SAMPLE_WORKSPACE_PROFILE.companyName,
      exporterContact: 'agro@tnglobalexports.in | +91 452 253 7712',
      exporterAddress: 'Madurai Botanical Extraction Park, Kappalur SIPCOT, Tamil Nadu 625008, India',
      buyerName: 'Al Noor Natural Cosmetics & Nutraceuticals LLC',
      buyerCountry: 'United Arab Emirates',
      buyerContact: 'imports@alnoornaturals.ae | +971 4 881 4490',
      buyerAddress: 'Plot S20412, Jebel Ali Free Zone South (JAFZA), Dubai, UAE',
      productName: 'Cold-Pressed Virgin Moringa Seed Oil & Leaf Extract Powder (HS 151590)',
      productDescription:
        'Cosmetic-Grade Cold-Pressed Moringa Oleifera Seed Oil in 200L Nitrogen-Flushed HDPE Drums with Halal & Phytosanitary Dossier',
      quantity: 2400,
      unit: 'KG',
      unitPrice: 36.5,
      orderValue: 87600,
      currency: 'AED',
      incoterm: 'CIF',
      destinationPort: 'Jebel Ali Port, Dubai, UAE',
      orderDate: '2026-04-04',
      expectedShipmentDate: '2026-04-22',
      paymentTerms: '30% Advance via PayPal Sandbox, 70% against Airway/Sea Bill',
      advancePercentage: 30,
      orderStatus: 'FINANCIALLY_ASSESSED',
      extractionSource: 'MANUAL',
      createdAtEpochMs: tsOrder003,
      updatedAtEpochMs: tsOrder003
    },
    {
      id: 'ORDER-004',
      userId: SAMPLE_USER_ID,
      orderNumber: 'ORDER-004',
      exporterName: SAMPLE_WORKSPACE_PROFILE.companyName,
      exporterContact: 'leather@tnglobalexports.in | +91 4174 244 801',
      exporterAddress: 'Ambur Leather Tannery & Footwear SEZ, Vellore District, Tamil Nadu 635802, India',
      buyerName: 'Mayfair Heritage Leathergoods Ltd.',
      buyerCountry: 'United Kingdom',
      buyerContact: 'buying@mayfairheritage.co.uk | +44 20 7946 0821',
      buyerAddress: '24 Savile Row, Mayfair, London W1S 3PR, United Kingdom',
      productName: 'LWG Gold-Rated Full-Grain Vegetable-Tanned Leather Satchels & Folios (HS 420211)',
      productDescription:
        'Handcrafted Executive Briefcases & Travel Satchels with Solid Brass Hardware, Cotton Dust Bags & UK REACH Compliance Certificate',
      quantity: 1100,
      unit: 'PCS',
      unitPrice: 34.0,
      orderValue: 37400,
      currency: 'GBP',
      incoterm: 'FOB',
      destinationPort: 'Port of Felixstowe, United Kingdom',
      orderDate: '2026-03-29',
      expectedShipmentDate: '2026-04-19',
      paymentTerms: '50% Advance via PayPal Sandbox, 50% CAD',
      advancePercentage: 50,
      orderStatus: 'DOCS_READY',
      extractionSource: 'AI_EXTRACTED_CONFIRMED',
      createdAtEpochMs: tsOrder004,
      updatedAtEpochMs: tsOrder004
    },
    {
      id: 'ORDER-005',
      userId: SAMPLE_USER_ID,
      orderNumber: 'ORDER-005',
      exporterName: SAMPLE_WORKSPACE_PROFILE.companyName,
      exporterContact: 'engineering@tnglobalexports.in | +91 422 267 3390',
      exporterAddress: 'Coimbatore Precision Foundry & CNC Cluster, Peelamedu, Tamil Nadu 641004, India',
      buyerName: 'Kansai Industrial Fluid Controls Co., Ltd.',
      buyerCountry: 'Japan',
      buyerContact: 'global-qa@kansaifluid.co.jp | +81 6 6201 7740',
      buyerAddress: '2-3-18 Nakanoshima, Kita-ku, Osaka 530-0005, Japan',
      productName: 'CNC-Machined SS316L High-Pressure Cryogenic Valve Bodies (HS 848190)',
      productDescription:
        'JIS B2001 Compliant Stainless Steel 316L Precision Machined Valve Castings with 3.1 Mill Test Certificate & Ultrasonic NDT Report',
      quantity: 1500,
      unit: 'PCS',
      unitPrice: 4100.0,
      orderValue: 6150000,
      currency: 'JPY',
      incoterm: 'CIF',
      destinationPort: 'Port of Yokohama, Japan',
      orderDate: '2026-04-11',
      expectedShipmentDate: '2026-05-06',
      paymentTerms: '30% Advance via PayPal Sandbox, 70% Sight L/C',
      advancePercentage: 30,
      orderStatus: 'VALIDATED',
      extractionSource: 'AI_EXTRACTED_CONFIRMED',
      createdAtEpochMs: tsOrder005,
      updatedAtEpochMs: tsOrder005
    },
    {
      id: 'ORDER-006',
      userId: SAMPLE_USER_ID,
      orderNumber: 'ORDER-006',
      exporterName: SAMPLE_WORKSPACE_PROFILE.companyName,
      exporterContact: 'exports@tnglobalexports.in | +91 44 2250 4890',
      exporterAddress: SAMPLE_WORKSPACE_PROFILE.headquarters,
      buyerName: 'Great Lakes Specialty Foods Corp.',
      buyerCountry: 'United States',
      buyerContact: 'supplychain@greatlakesfoods.com | +1 312 555 0149',
      buyerAddress: '400 N Michigan Ave, Suite 1100, Chicago, IL 60611, USA',
      productName: 'Tellicherry TGSEB Black Pepper & Cochin Ginger Oleoresin (HS 090411)',
      productDescription:
        'Steam-Sterilized Tellicherry Garbled Special Extra Bold Black Peppercorns & Food-Grade Ginger Oleoresin in 25kg Drums',
      quantity: 10000,
      unit: 'KG',
      unitPrice: 6.2,
      orderValue: 62000,
      currency: 'USD',
      incoterm: 'CIF',
      destinationPort: 'Port of New York / New Jersey, USA',
      orderDate: '2025-09-14',
      expectedShipmentDate: '2025-10-08',
      paymentTerms: '100% Full Settlement Captured via PayPal Sandbox',
      advancePercentage: 100,
      orderStatus: 'COMPLETED',
      extractionSource: 'MANUAL',
      createdAtEpochMs: tsOrder006,
      updatedAtEpochMs: tsOrder006
    }
  ];

  const assessments: FinancialAssessment[] = [
    calculateAssessment({
      userId: SAMPLE_USER_ID,
      exportOrderId: 'ORDER-001',
      existingId: 'FA-ORDER-001',
      quantity: 12000,
      unitPrice: 4.1,
      currency: 'EUR',
      exchangeRate: 91.5,
      advancePercentage: 40,
      paymentFeePercentage: 3.9,
      paymentFixedFee: 0.35,
      procurementCostInr: 2890000,
      logisticsCostInr: 315000,
      packagingCostInr: 125000,
      otherCostsInr: 82000,
      assumptionsNote:
        'EUR/INR Spot 91.50 | CIF Hamburg Reefer-Dry Consolidation | EU Eurofins Curcumin & Aflatoxin Lab Assay Included'
    }),
    calculateAssessment({
      userId: SAMPLE_USER_ID,
      exportOrderId: 'ORDER-002',
      existingId: 'FA-ORDER-002',
      quantity: 8500,
      unitPrice: 6.8,
      currency: 'USD',
      exchangeRate: 84.2,
      advancePercentage: 100,
      paymentFeePercentage: 4.4,
      paymentFixedFee: 0.3,
      procurementCostInr: 3120000,
      logisticsCostInr: 245000,
      packagingCostInr: 148000,
      otherCostsInr: 68000,
      assumptionsNote:
        'USD/INR Realized 84.20 | FOB Tuticorin/Chennai Feeder to Long Beach | GOTS Transaction Certificate & CPSIA Testing Verified'
    }),
    calculateAssessment({
      userId: SAMPLE_USER_ID,
      exportOrderId: 'ORDER-003',
      existingId: 'FA-ORDER-003',
      quantity: 2400,
      unitPrice: 36.5,
      currency: 'AED',
      exchangeRate: 22.92,
      advancePercentage: 30,
      paymentFeePercentage: 3.9,
      paymentFixedFee: 1.1,
      procurementCostInr: 1385000,
      logisticsCostInr: 162000,
      packagingCostInr: 74000,
      otherCostsInr: 49000,
      assumptionsNote:
        'AED/INR Pegged Cross 22.92 | CIF Jebel Ali Fast Sea Corridor | Halal & Dubai Municipality Montaji Registration'
    }),
    calculateAssessment({
      userId: SAMPLE_USER_ID,
      exportOrderId: 'ORDER-004',
      existingId: 'FA-ORDER-004',
      quantity: 1100,
      unitPrice: 34.0,
      currency: 'GBP',
      exchangeRate: 108.4,
      advancePercentage: 50,
      paymentFeePercentage: 4.1,
      paymentFixedFee: 0.3,
      procurementCostInr: 2540000,
      logisticsCostInr: 220000,
      packagingCostInr: 118000,
      otherCostsInr: 64000,
      assumptionsNote:
        'GBP/INR 108.40 | FOB Chennai to Felixstowe | LWG Gold Tannery Traceability & UK REACH Hexavalent Chromium Zero-Defect Assay'
    }),
    calculateAssessment({
      userId: SAMPLE_USER_ID,
      exportOrderId: 'ORDER-005',
      existingId: 'FA-ORDER-005',
      quantity: 1500,
      unitPrice: 4100.0,
      currency: 'JPY',
      exchangeRate: 0.56,
      advancePercentage: 30,
      paymentFeePercentage: 3.8,
      paymentFixedFee: 40.0,
      procurementCostInr: 2260000,
      logisticsCostInr: 210000,
      packagingCostInr: 96000,
      otherCostsInr: 78000,
      assumptionsNote:
        'JPY/INR 0.5600 | CIF Yokohama VCI Anti-Corrosion Crating | EN 10204 3.1 Mill Test Certificate & Third-Party TUV Inspection'
    }),
    calculateAssessment({
      userId: SAMPLE_USER_ID,
      exportOrderId: 'ORDER-006',
      existingId: 'FA-ORDER-006',
      quantity: 10000,
      unitPrice: 6.2,
      currency: 'USD',
      exchangeRate: 84.1,
      advancePercentage: 100,
      paymentFeePercentage: 4.4,
      paymentFixedFee: 0.3,
      procurementCostInr: 3410000,
      logisticsCostInr: 310000,
      packagingCostInr: 135000,
      otherCostsInr: 72000,
      assumptionsNote:
        'USD/INR Realized 84.10 | CIF New York/New Jersey | US FDA Prior Notice & Spices Board India Certificate Verified'
    })
  ];

  const documents: DocumentRecord[] = [
    ...buildOrder001Docs(tsOrder001),
    ...buildOrder002Docs(tsOrder002),
    ...buildOrder003Docs(tsOrder003),
    ...buildOrder004Docs(tsOrder004),
    ...buildOrder005Docs(tsOrder005),
    ...buildOrder006Docs(tsOrder006)
  ];

  const payments: PaymentRecord[] = [
    {
      id: 'PAY-ORDER-001-ADV',
      userId: SAMPLE_USER_ID,
      exportOrderId: 'ORDER-001',
      paypalOrderId: 'DEMO-TXN-8M920418H9102483B',
      paypalCaptureId: 'DEMO-CAP-4K192837F0192834L',
      approvalUrl: 'https://demo.niryatflow.local/simulated-checkout?token=DEMO-TXN-8M920418H9102483B',
      amount: 19680,
      currency: 'EUR',
      paymentType: 'ADVANCE',
      paymentStatus: 'DEMO_CAPTURED',
      verificationDetails:
        'SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION • 40% Advance Settled (EUR 19,680.00) • Hanseatic Spice GmbH',
      createdAtEpochMs: tsOrder001,
      updatedAtEpochMs: tsOrder001
    },
    {
      id: 'PAY-ORDER-001-BAL',
      userId: SAMPLE_USER_ID,
      exportOrderId: 'ORDER-001',
      paypalOrderId: 'DEMO-TXN-9P771209X8821904C',
      paypalCaptureId: '',
      approvalUrl: 'https://demo.niryatflow.local/simulated-checkout?token=DEMO-TXN-9P771209X8821904C',
      amount: 29520,
      currency: 'EUR',
      paymentType: 'BALANCE',
      paymentStatus: 'DEMO_PENDING',
      verificationDetails:
        'SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION • 60% CAD Balance Awaiting Original B/L Presentation (EUR 29,520.00)',
      createdAtEpochMs: tsOrder001,
      updatedAtEpochMs: tsOrder001
    },
    {
      id: 'PAY-ORDER-002-FULL',
      userId: SAMPLE_USER_ID,
      exportOrderId: 'ORDER-002',
      paypalOrderId: 'DEMO-TXN-3T551092W7162539A',
      paypalCaptureId: 'DEMO-CAP-7N883412M5510928K',
      approvalUrl: 'https://demo.niryatflow.local/simulated-checkout?token=DEMO-TXN-3T551092W7162539A',
      amount: 57800,
      currency: 'USD',
      paymentType: 'FULL',
      paymentStatus: 'DEMO_CAPTURED',
      verificationDetails:
        'SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION • 100% Export Settlement Captured (USD 57,800.00) • e-BRC Generated',
      createdAtEpochMs: tsOrder002,
      updatedAtEpochMs: tsOrder002
    },
    {
      id: 'PAY-ORDER-003-ADV',
      userId: SAMPLE_USER_ID,
      exportOrderId: 'ORDER-003',
      paypalOrderId: 'DEMO-TXN-6B119045Y2093847D',
      paypalCaptureId: '',
      approvalUrl: 'https://demo.niryatflow.local/simulated-checkout?token=DEMO-TXN-6B119045Y2093847D',
      amount: 26280,
      currency: 'AED',
      paymentType: 'ADVANCE',
      paymentStatus: 'DEMO_PENDING',
      verificationDetails:
        'SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION • 30% Advance Approved by Al Noor Natural Cosmetics LLC • Ready for Immediate Capture',
      createdAtEpochMs: tsOrder003,
      updatedAtEpochMs: tsOrder003
    },
    {
      id: 'PAY-ORDER-004-ADV',
      userId: SAMPLE_USER_ID,
      exportOrderId: 'ORDER-004',
      paypalOrderId: 'DEMO-TXN-1L449082R6610293E',
      paypalCaptureId: 'DEMO-CAP-2V990184P3391024Q',
      approvalUrl: 'https://demo.niryatflow.local/simulated-checkout?token=DEMO-TXN-1L449082R6610293E',
      amount: 18700,
      currency: 'GBP',
      paymentType: 'ADVANCE',
      paymentStatus: 'DEMO_CAPTURED',
      verificationDetails:
        'SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION • 50% Advance Captured (GBP 18,700.00) • Mayfair Heritage Leathergoods Ltd.',
      createdAtEpochMs: tsOrder004,
      updatedAtEpochMs: tsOrder004
    },
    {
      id: 'PAY-ORDER-006-FULL',
      userId: SAMPLE_USER_ID,
      exportOrderId: 'ORDER-006',
      paypalOrderId: 'DEMO-TXN-5Z882104G4491028M',
      paypalCaptureId: 'DEMO-CAP-9R110294K8821039W',
      approvalUrl: 'https://demo.niryatflow.local/simulated-checkout?token=DEMO-TXN-5Z882104G4491028M',
      amount: 62000,
      currency: 'USD',
      paymentType: 'FULL',
      paymentStatus: 'DEMO_CAPTURED',
      verificationDetails:
        'SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION • 100% Export Settlement Captured (USD 62,000.00) • Great Lakes Specialty Foods Corp.',
      createdAtEpochMs: tsOrder006,
      updatedAtEpochMs: tsOrder006
    }
  ];

  const activities: ActivityRecord[] = [
    {
      id: 'ACT-001',
      userId: SAMPLE_USER_ID,
      exportOrderId: 'ORDER-001',
      eventType: 'AI_EXTRACTION',
      title: 'Gemini AI Purchase Order Extraction Confirmed',
      description:
        'Extracted 12,000 KG Organic Erode Turmeric PO from Hanseatic Spice GmbH (97% confidence score).',
      createdAtEpochMs: tsOrder001
    },
    {
      id: 'ACT-002',
      userId: SAMPLE_USER_ID,
      exportOrderId: 'ORDER-001',
      eventType: 'FINANCIAL_ASSESSMENT',
      title: 'EUR/INR Financial Viability Locked at 24.16% Margin',
      description:
        'Gross EUR 49,200 (₹45.02 L) • Est. Net Profit ₹10.88 L after EU lab assay & Hamburg CIF freight.',
      createdAtEpochMs: tsOrder001
    },
    {
      id: 'ACT-003',
      userId: SAMPLE_USER_ID,
      exportOrderId: 'ORDER-001',
      eventType: 'PAYMENT_CAPTURED',
      title: '40% Advance Captured in Demo Mode (EUR 19,680)',
      description:
        'Capture ID DEMO-CAP-4K192837F0192834L verified against ORDER-001 (SIMULATED PAYMENT).',
      createdAtEpochMs: tsOrder001
    },
    {
      id: 'ACT-004',
      userId: SAMPLE_USER_ID,
      exportOrderId: 'ORDER-001',
      eventType: 'DOCUMENT_PENDING',
      title: 'Master Bill of Lading Awaiting Carrier Release',
      description: '6 of 7 mandatory documents verified; Hapag-Lloyd Master B/L under review.',
      createdAtEpochMs: tsOrder001
    },
    {
      id: 'ACT-005',
      userId: SAMPLE_USER_ID,
      exportOrderId: 'ORDER-002',
      eventType: 'ORDER_COMPLETED',
      title: 'ORDER-002 Dispatched & 100% Settled (USD 57,800)',
      description:
        'All 7 mandatory export documents verified & full USD 57,800 (₹48.67 L) captured for Long Beach, USA.',
      createdAtEpochMs: tsOrder002
    },
    {
      id: 'ACT-006',
      userId: SAMPLE_USER_ID,
      exportOrderId: 'ORDER-003',
      eventType: 'DOCUMENT_FLAGGED',
      title: 'Certificate of Origin Flagged for Consignee Address Correction',
      description:
        'UAE Chamber Certificate of Origin marked REUPLOAD_REQUIRED due to JAFZA Plot number typo.',
      createdAtEpochMs: tsOrder003
    },
    {
      id: 'ACT-007',
      userId: SAMPLE_USER_ID,
      exportOrderId: 'ORDER-003',
      eventType: 'PAYMENT_APPROVED',
      title: 'AED 26,280 Advance Approved by Dubai Buyer',
      description:
        'Demo Ref DEMO-TXN-6B119045Y2093847D is in DEMO_PENDING status and ready for one-click capture.',
      createdAtEpochMs: tsOrder003
    },
    {
      id: 'ACT-008',
      userId: SAMPLE_USER_ID,
      exportOrderId: 'ORDER-004',
      eventType: 'PAYMENT_CAPTURED',
      title: '50% Advance Captured for UK Leather Consignment (GBP 18,700)',
      description:
        'Capture ID DEMO-CAP-2V990184P3391024Q settled (₹20.27 L) for Mayfair Heritage Leathergoods Ltd.',
      createdAtEpochMs: tsOrder004
    },
    {
      id: 'ACT-009',
      userId: SAMPLE_USER_ID,
      exportOrderId: 'ORDER-005',
      eventType: 'AI_EXTRACTION',
      title: 'Japanese Industrial PO Extracted (JPY 6,150,000)',
      description:
        '1,500 PCS SS316L Cryogenic Valve Bodies for Kansai Industrial Fluid Controls Co., Ltd., Osaka.',
      createdAtEpochMs: tsOrder005
    },
    {
      id: 'ACT-010',
      userId: SAMPLE_USER_ID,
      exportOrderId: 'ORDER-006',
      eventType: 'ORDER_COMPLETED',
      title: 'Historical US Spice Export Completed (USD 62,000)',
      description:
        '10,000 KG Tellicherry Black Pepper settled in full (Capture ID DEMO-CAP-9R110294K8821039W).',
      createdAtEpochMs: tsOrder006
    }
  ];

  return { orders, assessments, documents, payments, activities };
}

function SAMPLE_WORKSPACE_ID_NAME(): string {
  return SAMPLE_WORKSPACE_PROFILE.companyName;
}

function makeDoc(
  orderId: string,
  index: number,
  docType: string,
  reqLevel: 'REQUIRED' | 'OPTIONAL',
  status: DocumentRecord['status'],
  fileName: string,
  note: string,
  ts: number
): DocumentRecord {
  const hexSeed = `${orderId}-${index}-${docType}`.split('').reduce((acc, ch) => acc + ch.charCodeAt(0), 0);
  const sha = status === 'MISSING'
    ? ''
    : Array.from({ length: 64 }, (_, i) => ((hexSeed + i * 7) % 16).toString(16)).join('');

  return {
    id: `DOC-${orderId}-${index}`,
    userId: SAMPLE_USER_ID,
    exportOrderId: orderId,
    documentType: docType,
    requirementLevel: reqLevel,
    status,
    fileName,
    fileReference: fileName ? `browser-local://${orderId.toLowerCase()}/${fileName}` : '',
    reviewerNote: note,
    fileMimeType: 'application/pdf',
    fileSizeBytes: status === 'MISSING' ? 0 : 245760 + index * 18432,
    sha256Checksum: sha,
    versionNumber: status === 'REUPLOAD_REQUIRED' ? 2 : 1,
    reviewerRole: status === 'VERIFIED' || status === 'APPROVED' ? 'EXPORTER_COMPLIANCE_OFFICER' : '',
    createdAtEpochMs: ts,
    updatedAtEpochMs: ts
  };
}

function buildOrder001Docs(ts: number): DocumentRecord[] {
  return [
    makeDoc('ORDER-001', 1, 'Commercial Invoice', 'REQUIRED', 'VERIFIED', 'CI_ORDER001_Hanseatic_EUR49200.pdf', 'Verified against EUR 49,200 CIF Hamburg contract', ts),
    makeDoc('ORDER-001', 2, 'Packing List', 'REQUIRED', 'VERIFIED', 'PL_ORDER001_480Bags_12000KG.pdf', '480 Kraft bags x 25 KG net weight verified', ts),
    makeDoc('ORDER-001', 3, 'Purchase Order / Contract', 'REQUIRED', 'VERIFIED', 'PO_Hanseatic_DE_2026_0408.pdf', 'Signed buyer PO with EU organic clause', ts),
    makeDoc('ORDER-001', 4, 'Shipping Bill', 'REQUIRED', 'APPROVED', 'SB_ICEGATE_Chennai_8849201.pdf', 'ICEGATE LEO granted under RoDTEP scheme', ts),
    makeDoc('ORDER-001', 5, 'Bill of Lading / Airway Bill', 'REQUIRED', 'UNDER_REVIEW', 'Draft_BL_HapagLloyd_HAM2026.pdf', 'Draft B/L uploaded; awaiting vessel sailing confirmation', ts),
    makeDoc('ORDER-001', 6, 'Certificate of Origin', 'REQUIRED', 'VERIFIED', 'COO_SpicesBoard_EU_Rex_001.pdf', 'REX Registered Exporter Statement verified', ts),
    makeDoc('ORDER-001', 7, 'Insurance Certificate', 'OPTIONAL', 'VERIFIED', 'MarineIns_ICICI_CIF110Pct_001.pdf', 'ICC(A) All-Risk Marine Cover at 110% CIF value', ts),
    makeDoc('ORDER-001', 8, 'Payment Receipt / Confirmation', 'REQUIRED', 'VERIFIED', 'Demo_Receipt_4K192837F_EUR19680.pdf', '40% Advance receipt verified (EUR 19,680.00)', ts)
  ];
}

function buildOrder002Docs(ts: number): DocumentRecord[] {
  return [
    makeDoc('ORDER-002', 1, 'Commercial Invoice', 'REQUIRED', 'VERIFIED', 'CI_ORDER002_PacificCoast_USD57800.pdf', 'Verified 8,500 PCS @ USD 6.80 FOB Chennai', ts),
    makeDoc('ORDER-002', 2, 'Packing List', 'REQUIRED', 'VERIFIED', 'PL_ORDER002_170Cartons_Tirupur.pdf', '170 export master cartons verified', ts),
    makeDoc('ORDER-002', 3, 'Purchase Order / Contract', 'REQUIRED', 'VERIFIED', 'PO_PacificCoast_LB_2026_0319.pdf', 'Buyer contract & CPSIA compliance addendum verified', ts),
    makeDoc('ORDER-002', 4, 'Shipping Bill', 'REQUIRED', 'VERIFIED', 'SB_ICEGATE_Tuticorin_7731904.pdf', 'Duty Drawback & RoSCTL claim registered', ts),
    makeDoc('ORDER-002', 5, 'Bill of Lading / Airway Bill', 'REQUIRED', 'VERIFIED', 'OBL_Maersk_LongBeach_MSKU9012.pdf', 'Clean On-Board Ocean Bill of Lading released', ts),
    makeDoc('ORDER-002', 6, 'Certificate of Origin', 'REQUIRED', 'VERIFIED', 'COO_AEPC_Tirupur_002.pdf', 'AEPC Certificate of Origin & GOTS Transaction Certificate', ts),
    makeDoc('ORDER-002', 7, 'Insurance Certificate', 'OPTIONAL', 'VERIFIED', 'FOB_Transit_Cover_Tirupur_Port.pdf', 'Inland factory-to-port transit insurance verified', ts),
    makeDoc('ORDER-002', 8, 'Payment Receipt / Confirmation', 'REQUIRED', 'VERIFIED', 'Demo_FullSettlement_7N883412M_USD57800.pdf', '100% settlement verified (USD 57,800.00)', ts)
  ];
}

function buildOrder003Docs(ts: number): DocumentRecord[] {
  return [
    makeDoc('ORDER-003', 1, 'Commercial Invoice', 'REQUIRED', 'VERIFIED', 'CI_ORDER003_AlNoor_AED87600.pdf', 'Verified 2,400 KG Cold-Pressed Moringa Oil @ AED 36.50', ts),
    makeDoc('ORDER-003', 2, 'Packing List', 'REQUIRED', 'VERIFIED', 'PL_ORDER003_12Drums_NitrogenHDPE.pdf', '12 x 200KG HDPE Drums palletized', ts),
    makeDoc('ORDER-003', 3, 'Purchase Order / Contract', 'REQUIRED', 'VERIFIED', 'PO_AlNoor_JAFZA_2026_0404.pdf', 'Signed JAFZA Dubai procurement contract', ts),
    makeDoc('ORDER-003', 4, 'Shipping Bill', 'REQUIRED', 'PENDING_REVIEW', 'SB_Draft_Chennai_Moringa_003.pdf', 'Awaiting AD Code bank endorsement check', ts),
    makeDoc('ORDER-003', 5, 'Bill of Lading / Airway Bill', 'REQUIRED', 'MISSING', '', 'Booking confirmed on Emirates Shipping Line; B/L pending gate-in', ts),
    makeDoc('ORDER-003', 6, 'Certificate of Origin', 'REQUIRED', 'REUPLOAD_REQUIRED', 'COO_CEPA_IndiaUAE_Draft1.pdf', 'Flagged: JAFZA Plot S20412 misspelled as S24012 on Box 2; please re-upload corrected PDF', ts),
    makeDoc('ORDER-003', 7, 'Insurance Certificate', 'OPTIONAL', 'UPLOADED', 'MarineCover_Dubai_AED96360.pdf', 'Uploaded marine policy certificate', ts),
    makeDoc('ORDER-003', 8, 'Payment Receipt / Confirmation', 'REQUIRED', 'PENDING_REVIEW', 'Demo_Approval_6B119045Y_AED26280.pdf', 'Buyer approved AED 26,280 advance; capture pending in Payment Hub', ts)
  ];
}

function buildOrder004Docs(ts: number): DocumentRecord[] {
  return [
    makeDoc('ORDER-004', 1, 'Commercial Invoice', 'REQUIRED', 'VERIFIED', 'CI_ORDER004_Mayfair_GBP37400.pdf', 'Verified 1,100 Leather Satchels @ GBP 34.00', ts),
    makeDoc('ORDER-004', 2, 'Packing List', 'REQUIRED', 'VERIFIED', 'PL_ORDER004_110Cartons_Ambur.pdf', '110 moisture-barrier export cartons verified', ts),
    makeDoc('ORDER-004', 3, 'Purchase Order / Contract', 'REQUIRED', 'VERIFIED', 'PO_Mayfair_London_2026_0329.pdf', 'Savile Row buyer purchase contract verified', ts),
    makeDoc('ORDER-004', 4, 'Shipping Bill', 'REQUIRED', 'VERIFIED', 'SB_ICEGATE_Chennai_CLE_004.pdf', 'Council for Leather Exports (CLE) norms verified', ts),
    makeDoc('ORDER-004', 5, 'Bill of Lading / Airway Bill', 'REQUIRED', 'APPROVED', 'BL_MSC_Felixstowe_MEDU4410.pdf', 'House B/L approved for Felixstowe sailing', ts),
    makeDoc('ORDER-004', 6, 'Certificate of Origin', 'REQUIRED', 'VERIFIED', 'COO_CLE_UK_DCTS_004.pdf', 'UK Developing Countries Trading Scheme (DCTS) origin verified', ts),
    makeDoc('ORDER-004', 7, 'Insurance Certificate', 'OPTIONAL', 'NOT_APPLICABLE', '', 'FOB shipment — buyer covers ocean marine insurance under open cover', ts),
    makeDoc('ORDER-004', 8, 'Payment Receipt / Confirmation', 'REQUIRED', 'VERIFIED', 'Demo_Receipt_2V990184P_GBP18700.pdf', '50% Advance (GBP 18,700.00) captured and verified', ts)
  ];
}

function buildOrder005Docs(ts: number): DocumentRecord[] {
  return [
    makeDoc('ORDER-005', 1, 'Commercial Invoice', 'REQUIRED', 'VERIFIED', 'CI_ORDER005_Kansai_JPY6150000.pdf', 'Verified 1,500 PCS SS316L Valve Bodies @ JPY 4,100', ts),
    makeDoc('ORDER-005', 2, 'Packing List', 'REQUIRED', 'UPLOADED', 'PL_ORDER005_ISPM15_VCI_Crates.pdf', 'ISPM-15 heat-treated wooden crates with VCI film', ts),
    makeDoc('ORDER-005', 3, 'Purchase Order / Contract', 'REQUIRED', 'VERIFIED', 'PO_KansaiFluid_Osaka_2026_0411.pdf', 'JIS B2001 engineering procurement contract verified', ts),
    makeDoc('ORDER-005', 4, 'Shipping Bill', 'REQUIRED', 'MISSING', '', 'Awaiting final TUV NDT certificate before filing ICEGATE Shipping Bill', ts),
    makeDoc('ORDER-005', 5, 'Bill of Lading / Airway Bill', 'REQUIRED', 'MISSING', '', 'NYK Line vessel slot reserved for Yokohama', ts),
    makeDoc('ORDER-005', 6, 'Certificate of Origin', 'REQUIRED', 'PENDING_REVIEW', 'COO_IJCEPA_EEPC_Coimbatore_005.pdf', 'India-Japan CEPA preferential tariff certificate under review', ts),
    makeDoc('ORDER-005', 7, 'Insurance Certificate', 'OPTIONAL', 'MISSING', '', 'Marine policy quote requested for CIF Yokohama', ts),
    makeDoc('ORDER-005', 8, 'Payment Receipt / Confirmation', 'REQUIRED', 'MISSING', '', '30% Advance (JPY 1,845,000) to be initiated in Payment Hub', ts)
  ];
}

function buildOrder006Docs(ts: number): DocumentRecord[] {
  return [
    makeDoc('ORDER-006', 1, 'Commercial Invoice', 'REQUIRED', 'VERIFIED', 'CI_ORDER006_GreatLakes_USD62000.pdf', 'Verified 10,000 KG Tellicherry Black Pepper @ USD 6.20', ts),
    makeDoc('ORDER-006', 2, 'Packing List', 'REQUIRED', 'VERIFIED', 'PL_ORDER006_400Drums_25KG.pdf', '400 food-grade drums verified', ts),
    makeDoc('ORDER-006', 3, 'Purchase Order / Contract', 'REQUIRED', 'VERIFIED', 'PO_GreatLakes_Chicago_2025_0914.pdf', 'Buyer contract & ASTA cleanliness spec verified', ts),
    makeDoc('ORDER-006', 4, 'Shipping Bill', 'REQUIRED', 'VERIFIED', 'SB_ICEGATE_Cochin_6610293.pdf', 'Export customs clearance completed', ts),
    makeDoc('ORDER-006', 5, 'Bill of Lading / Airway Bill', 'REQUIRED', 'VERIFIED', 'OBL_CMA_CGM_NYNJ_2025.pdf', 'Original Bill of Lading surrendered & cargo delivered', ts),
    makeDoc('ORDER-006', 6, 'Certificate of Origin', 'REQUIRED', 'VERIFIED', 'COO_SpicesBoard_US_006.pdf', 'Spices Board India Certificate & US FDA Prior Notice verified', ts),
    makeDoc('ORDER-006', 7, 'Insurance Certificate', 'OPTIONAL', 'VERIFIED', 'MarineIns_NewIndia_CIF_006.pdf', 'CIF NY/NJ Marine Insurance verified', ts),
    makeDoc('ORDER-006', 8, 'Payment Receipt / Confirmation', 'REQUIRED', 'VERIFIED', 'Demo_Receipt_9R110294K_USD62000.pdf', '100% USD 62,000 settlement captured & e-BRC closed', ts)
  ];
}

export function loadSampleWorkspace(): WorkspaceDataset {
  try {
    const raw = localStorage.getItem(SAMPLE_STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as WorkspaceDataset;
      if (parsed && Array.isArray(parsed.orders) && parsed.orders.length > 0) {
        return parsed;
      }
    }
  } catch {
    // Fallback to fresh seeded dataset
  }
  const fresh = buildSeededSampleDataset(Date.now());
  saveSampleWorkspace(fresh);
  return fresh;
}

export function saveSampleWorkspace(data: WorkspaceDataset): void {
  try {
    localStorage.setItem(SAMPLE_STORAGE_KEY, JSON.stringify(data));
  } catch {
    // Ignore storage quota errors in private browsing
  }
}

export function resetSampleWorkspace(): WorkspaceDataset {
  try {
    localStorage.removeItem(SAMPLE_STORAGE_KEY);
  } catch {
    // Ignore
  }
  const fresh = buildSeededSampleDataset(Date.now());
  saveSampleWorkspace(fresh);
  return fresh;
}

export function loadProductionWorkspace(): WorkspaceDataset {
  try {
    const raw = localStorage.getItem(PROD_STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as WorkspaceDataset;
      if (parsed && Array.isArray(parsed.orders)) {
        return parsed;
      }
    }
  } catch {
    // Ignore
  }
  return {
    orders: [],
    assessments: [],
    documents: [],
    payments: [],
    activities: []
  };
}

export function saveProductionWorkspace(data: WorkspaceDataset): void {
  try {
    localStorage.setItem(PROD_STORAGE_KEY, JSON.stringify(data));
  } catch {
    // Ignore
  }
}
