package com.example.data.sample

import com.example.data.model.ActivityRecord
import com.example.data.model.DocumentRecord
import com.example.data.model.ExportOrder
import com.example.data.model.FinancialAssessment
import com.example.data.model.PaymentRecord
import com.example.domain.FinancialCalculationEngine
import com.google.firebase.Timestamp
import java.util.Date

enum class WorkspaceMode {
    PRODUCTION,
    SAMPLE_BUSINESS
}

data class BusinessWorkspaceProfile(
    val businessId: String = "niryat_workspace_001",
    val businessName: String,
    val businessType: String,
    val primaryBase: String,
    val contactEmail: String,
    val productCategories: String,
    val reportingCurrency: String = "INR",
    val role: String = "Export Operations Lead",
    val iecReference: String = "0419084721 (Demo MSME Reference)",
    val gstStateCode: String = "33 - Tamil Nadu",
    val isSampleWorkspace: Boolean = false
) {
    val primaryCategories: String
        get() = productCategories
}

data class SampleWorkspaceSeedBundle(
    val profile: BusinessWorkspaceProfile,
    val orders: List<ExportOrder>,
    val assessments: List<FinancialAssessment>,
    val documents: List<DocumentRecord>,
    val payments: List<PaymentRecord>,
    val activities: List<ActivityRecord>
)

/**
 * Isolated Sample Business Workspace Seeder for:
 * "Tamil Nadu Global Exports Pvt. Ltd." (Chennai, Tamil Nadu, India)
 *
 * Strictly isolated from Production Firestore collections.
 * Includes staggered timestamps across 1D, 1W, 1M, 3M, and 1Y so the live
 * dashboard time-period selector filters and recalculates distinct KPIs and time-series buckets.
 */
object SampleBusinessWorkspaceSeeder {

    const val SAMPLE_USER_ID = "sample_workspace_tn_exports"
    private const val HOUR_MS = 60L * 60L * 1000L
    private const val DAY_MS = 24L * HOUR_MS

    fun defaultSampleBusinessProfile(): BusinessWorkspaceProfile {
        return BusinessWorkspaceProfile(
            businessName = "Tamil Nadu Global Exports Pvt. Ltd.",
            businessType = "MSME Export Company",
            primaryBase = "Chennai, Tamil Nadu, India",
            contactEmail = "exports@tnglobalexports.in",
            productCategories = "Textiles, Leather Goods, Spices, Engineering Components",
            reportingCurrency = "INR",
            iecReference = "0419084721 (Sample MSME Reference)",
            gstStateCode = "33 - Tamil Nadu",
            isSampleWorkspace = true
        )
    }

    fun defaultProductionProfile(userEmail: String): BusinessWorkspaceProfile {
        return BusinessWorkspaceProfile(
            businessName = "Production Export Workspace",
            businessType = "MSME Exporter / Manufacturer",
            primaryBase = "Chennai / Mumbai, India",
            contactEmail = userEmail.ifBlank { "exporter@niryatflow.in" },
            productCategories = "Apparel, Agro Commodities, Engineering Goods",
            reportingCurrency = "INR",
            iecReference = "Configure in Business Profile",
            gstStateCode = "India MSME",
            isSampleWorkspace = false
        )
    }

    private fun tsAgo(nowMillis: Long, offsetMillis: Long): Timestamp {
        return Timestamp(Date((nowMillis - offsetMillis).coerceAtLeast(1_000_000L)))
    }

    fun createSeedBundle(nowMillis: Long = System.currentTimeMillis()): SampleWorkspaceSeedBundle {
        val profile = defaultSampleBusinessProfile()
        val orders = buildSampleOrders(profile.businessName, nowMillis)
        val assessments = buildSampleAssessments(orders, nowMillis)
        val documents = buildSampleDocuments(orders, nowMillis)
        val payments = buildSamplePayments(orders, nowMillis)
        val activities = buildSampleActivities(nowMillis)
        return SampleWorkspaceSeedBundle(
            profile = profile,
            orders = orders,
            assessments = assessments,
            documents = documents,
            payments = payments,
            activities = activities
        )
    }

    private fun buildSampleOrders(exporterName: String, nowMillis: Long): List<ExportOrder> {
        return listOf(
            // ORDER-001: Created 4 hours ago -> Included in 1D, 1W, 1M, 3M, 1Y, ALL
            ExportOrder(
                id = "sample_ord_001",
                userId = SAMPLE_USER_ID,
                orderNumber = "ORDER-001",
                exporterName = exporterName,
                exporterContact = "+91 44 2815 4900 • exports@tnglobalexports.in",
                exporterAddress = "Plot 42, Guindy Industrial Estate, Chennai, Tamil Nadu 600032, India",
                buyerName = "NordTextil GmbH",
                buyerCountry = "Germany",
                buyerContact = "procurement@nordtextil.de • +49 40 3198 220",
                buyerAddress = "Speicherstadt Block M, 20457 Hamburg, Germany",
                productName = "Organic Cotton Knitted Apparel",
                productDescription = "GOTS-Certified 180 GSM Organic Combed Cotton Crewneck & Polo Apparel Assortment",
                quantity = 5000.0,
                unit = "PCS",
                unitPrice = 19.50,
                orderValue = 97500.0,
                currency = "EUR",
                incoterm = "FOB",
                destinationPort = "Port of Hamburg (DEHAM)",
                orderDate = "2025-03-04",
                expectedShipmentDate = "2025-03-29",
                paymentTerms = "30% Advance via PayPal Sandbox, 70% CAD against Bill of Lading",
                advancePercentage = 30.0,
                orderStatus = "SHIPMENT_IN_TRANSIT",
                extractionSource = "AI_EXTRACTED_CONFIRMED",
                createdAt = tsAgo(nowMillis, 4L * HOUR_MS),
                updatedAt = tsAgo(nowMillis, 2L * HOUR_MS)
            ),
            // ORDER-002: Created 3 days ago -> Included in 1W, 1M, 3M, 1Y, ALL (Not 1D)
            ExportOrder(
                id = "sample_ord_002",
                userId = SAMPLE_USER_ID,
                orderNumber = "ORDER-002",
                exporterName = exporterName,
                exporterContact = "+91 44 2815 4900 • exports@tnglobalexports.in",
                exporterAddress = "Plot 42, Guindy Industrial Estate, Chennai, Tamil Nadu 600032, India",
                buyerName = "Pacific Leather Co.",
                buyerCountry = "United States",
                buyerContact = "imports@pacificleather.us • +1 310 555 0194",
                buyerAddress = "840 Harbor Blvd, Los Angeles, CA 90017, USA",
                productName = "Handcrafted Leather Wallets",
                productDescription = "Full-Grain Vegetable-Tanned Ambur Leather Bifold Wallets with RFID Shielding",
                quantity = 2000.0,
                unit = "PCS",
                unitPrice = 16.00,
                orderValue = 32000.0,
                currency = "USD",
                incoterm = "CIF",
                destinationPort = "Port of Los Angeles (USLAX)",
                orderDate = "2025-03-08",
                expectedShipmentDate = "2025-04-06",
                paymentTerms = "30% Advance via PayPal Sandbox, 70% Net 30 Sight Draft",
                advancePercentage = 30.0,
                orderStatus = "AWAITING_PAYMENT",
                extractionSource = "MANUAL",
                createdAt = tsAgo(nowMillis, 3L * DAY_MS),
                updatedAt = tsAgo(nowMillis, 2L * DAY_MS)
            ),
            // ORDER-003: Created 5 days ago -> Included in 1W, 1M, 3M, 1Y, ALL (Not 1D)
            ExportOrder(
                id = "sample_ord_003",
                userId = SAMPLE_USER_ID,
                orderNumber = "ORDER-003",
                exporterName = exporterName,
                exporterContact = "+91 44 2815 4900 • exports@tnglobalexports.in",
                exporterAddress = "Plot 42, Guindy Industrial Estate, Chennai, Tamil Nadu 600032, India",
                buyerName = "Al Noor Trading LLC",
                buyerCountry = "United Arab Emirates",
                buyerContact = "trade@alnoortrading.ae • +971 4 881 7400",
                buyerAddress = "Jebel Ali Free Zone South, Dubai, UAE",
                productName = "Turmeric & Spice Export Pack",
                productDescription = "Erode GI-Grade High-Curcumin (5.2%) Turmeric Finger & Ground Spice Export Cartons",
                quantity = 8000.0,
                unit = "KG",
                unitPrice = 15.00,
                orderValue = 120000.0,
                currency = "AED",
                incoterm = "CFR",
                destinationPort = "Jebel Ali Port, Dubai (AEJEA)",
                orderDate = "2025-02-28",
                expectedShipmentDate = "2025-03-22",
                paymentTerms = "30% Advance Settled, 70% against Phytosanitary & Origin Cert",
                advancePercentage = 30.0,
                orderStatus = "DOCS_READY",
                extractionSource = "AI_EXTRACTED_CONFIRMED",
                createdAt = tsAgo(nowMillis, 5L * DAY_MS),
                updatedAt = tsAgo(nowMillis, 4L * DAY_MS)
            ),
            // ORDER-004: Created 16 days ago -> Included in 1M, 3M, 1Y, ALL (Not 1D or 1W)
            ExportOrder(
                id = "sample_ord_004",
                userId = SAMPLE_USER_ID,
                orderNumber = "ORDER-004",
                exporterName = exporterName,
                exporterContact = "+91 44 2815 4900 • exports@tnglobalexports.in",
                exporterAddress = "Plot 42, Guindy Industrial Estate, Chennai, Tamil Nadu 600032, India",
                buyerName = "Tropicana Foods",
                buyerCountry = "Singapore",
                buyerContact = "supplychain@tropicanafoods.sg • +65 6720 4410",
                buyerAddress = "18 Jurong Port Road, Singapore 619093",
                productName = "Processed Chilli & Masala Export",
                productDescription = "Steam-Sterilized Guntur Sannam Chilli Powder & Blended South Indian Curry Masala",
                quantity = 3500.0,
                unit = "KG",
                unitPrice = 4.80,
                orderValue = 16800.0,
                currency = "USD",
                incoterm = "FOB",
                destinationPort = "Port of Singapore (SGSIN)",
                orderDate = "2025-03-11",
                expectedShipmentDate = "2025-04-12",
                paymentTerms = "40% Advance via PayPal Sandbox, 60% CAD",
                advancePercentage = 40.0,
                orderStatus = "DOCUMENTATION_PENDING",
                extractionSource = "MANUAL",
                createdAt = tsAgo(nowMillis, 16L * DAY_MS),
                updatedAt = tsAgo(nowMillis, 14L * DAY_MS)
            ),
            // ORDER-005: Created 45 days ago -> Included in 3M, 1Y, ALL (Not 1D, 1W, or 1M)
            ExportOrder(
                id = "sample_ord_005",
                userId = SAMPLE_USER_ID,
                orderNumber = "ORDER-005",
                exporterName = exporterName,
                exporterContact = "+91 44 2815 4900 • exports@tnglobalexports.in",
                exporterAddress = "Plot 42, Guindy Industrial Estate, Chennai, Tamil Nadu 600032, India",
                buyerName = "Midlands Industrial Ltd.",
                buyerCountry = "United Kingdom",
                buyerContact = "sourcing@midlandsindustrial.co.uk • +44 121 496 0821",
                buyerAddress = "Aston Science Park, Birmingham B7 4BJ, United Kingdom",
                productName = "Precision Machined Steel Valves",
                productDescription = "CNC-Turned SS316 High-Pressure Flow Control Valves (Ambattur Precision Division)",
                quantity = 1200.0,
                unit = "UNITS",
                unitPrice = 45.00,
                orderValue = 54000.0,
                currency = "GBP",
                incoterm = "CIF",
                destinationPort = "Port of Felixstowe (GBFXT)",
                orderDate = "2025-01-29",
                expectedShipmentDate = "2025-02-24",
                paymentTerms = "100% Settled via PayPal Cross-Border Checkout",
                advancePercentage = 30.0,
                orderStatus = "COMPLETED",
                extractionSource = "AI_EXTRACTED_CONFIRMED",
                createdAt = tsAgo(nowMillis, 45L * DAY_MS),
                updatedAt = tsAgo(nowMillis, 40L * DAY_MS)
            ),
            // ORDER-006: Created 150 days ago -> Included in 1Y, ALL (Not 1D, 1W, 1M, or 3M)
            ExportOrder(
                id = "sample_ord_006",
                userId = SAMPLE_USER_ID,
                orderNumber = "ORDER-006",
                exporterName = exporterName,
                exporterContact = "+91 44 2815 4900 • exports@tnglobalexports.in",
                exporterAddress = "Plot 42, Guindy Industrial Estate, Chennai, Tamil Nadu 600032, India",
                buyerName = "Sakura Home Living",
                buyerCountry = "Japan",
                buyerContact = "buying@sakurahome.co.jp • +81 3 5550 8190",
                buyerAddress = "Minato-ku Shibaura 3-chome, Tokyo 108-0023, Japan",
                productName = "Handloom Home Textile Sets",
                productDescription = "Karur Handloom Dyed Table Linen, Cushion Covers & Organic Cotton Throws",
                quantity = 1500.0,
                unit = "SETS",
                unitPrice = 3466.6667,
                orderValue = 5200000.0,
                currency = "JPY",
                incoterm = "FOB",
                destinationPort = "Port of Yokohama (JPYOK)",
                orderDate = "2025-03-14",
                expectedShipmentDate = "2025-04-18",
                paymentTerms = "30% Advance via PayPal Sandbox, 70% Letter of Credit",
                advancePercentage = 30.0,
                orderStatus = "ORDER_PROCESSING",
                extractionSource = "AI_EXTRACTED_CONFIRMED",
                createdAt = tsAgo(nowMillis, 150L * DAY_MS),
                updatedAt = tsAgo(nowMillis, 145L * DAY_MS)
            )
        )
    }

    private fun buildSampleAssessments(orders: List<ExportOrder>, nowMillis: Long): List<FinancialAssessment> {
        val configs = mapOf(
            "sample_ord_001" to AssessmentSeedConfig(
                fxRate = 91.11,
                feePct = 2.90,
                fixedFee = 15.0,
                procurementInr = 5_180_000.0,
                logisticsInr = 740_000.0,
                packagingInr = 210_000.0,
                otherInr = 125_000.0,
                note = "EUR/INR hedged at ₹91.11 • Tiruppur & Guindy apparel cluster cost baseline."
            ),
            "sample_ord_002" to AssessmentSeedConfig(
                fxRate = 83.28,
                feePct = 3.40,
                fixedFee = 20.0,
                procurementInr = 1_520_000.0,
                logisticsInr = 290_000.0,
                packagingInr = 88_000.0,
                otherInr = 46_000.0,
                note = "USD/INR spot ₹83.28 • Ambur tannery procurement + CIF Los Angeles air-sea freight."
            ),
            "sample_ord_003" to AssessmentSeedConfig(
                fxRate = 22.875,
                feePct = 2.60,
                fixedFee = 45.0,
                procurementInr = 1_640_000.0,
                logisticsInr = 215_000.0,
                packagingInr = 95_000.0,
                otherInr = 38_000.0,
                note = "AED/INR ₹22.875 • Erode APMC spice procurement + Chennai-Jebel Ali reefer container."
            ),
            "sample_ord_004" to AssessmentSeedConfig(
                fxRate = 83.33,
                feePct = 3.20,
                fixedFee = 15.0,
                procurementInr = 820_000.0,
                logisticsInr = 112_000.0,
                packagingInr = 48_000.0,
                otherInr = 29_000.0,
                note = "USD/INR ₹83.33 • Sterilized spice pack FOB Chennai to Jurong Port."
            ),
            "sample_ord_005" to AssessmentSeedConfig(
                fxRate = 109.54,
                feePct = 2.80,
                fixedFee = 18.0,
                procurementInr = 3_190_000.0,
                logisticsInr = 490_000.0,
                packagingInr = 140_000.0,
                otherInr = 92_000.0,
                note = "GBP/INR ₹109.54 • High-margin CNC valve order settled 100% via PayPal."
            ),
            "sample_ord_006" to AssessmentSeedConfig(
                fxRate = 0.573,
                feePct = 3.10,
                fixedFee = 1500.0,
                procurementInr = 1_760_000.0,
                logisticsInr = 265_000.0,
                packagingInr = 84_000.0,
                otherInr = 42_000.0,
                note = "JPY/INR ₹0.573 • Karur handloom linen sets FOB Chennai to Yokohama."
            )
        )

        return orders.map { order ->
            val cfg = configs[order.id]!!
            FinancialCalculationEngine.calculateAssessment(
                userId = SAMPLE_USER_ID,
                exportOrderId = order.id,
                existingId = "sample_fin_${order.id}",
                quantity = order.quantity,
                unitPrice = order.unitPrice,
                currency = order.currency,
                exchangeRate = cfg.fxRate,
                advancePercentage = order.advancePercentage,
                paymentFeePercentage = cfg.feePct,
                paymentFixedFee = cfg.fixedFee,
                procurementCostInr = cfg.procurementInr,
                logisticsCostInr = cfg.logisticsInr,
                packagingCostInr = cfg.packagingInr,
                otherCostsInr = cfg.otherInr,
                assumptionsNote = cfg.note
            ).copy(
                createdAt = order.createdAt ?: tsAgo(nowMillis, DAY_MS),
                updatedAt = order.updatedAt ?: tsAgo(nowMillis, HOUR_MS)
            )
        }
    }

    private data class AssessmentSeedConfig(
        val fxRate: Double,
        val feePct: Double,
        val fixedFee: Double,
        val procurementInr: Double,
        val logisticsInr: Double,
        val packagingInr: Double,
        val otherInr: Double,
        val note: String
    )

    private fun buildSampleDocuments(orders: List<ExportOrder>, nowMillis: Long): List<DocumentRecord> {
        val fiveCoreDocs = listOf(
            "Commercial Invoice",
            "Packing List",
            "Bill of Lading / Airway Bill",
            "Certificate of Origin",
            "Shipping Bill"
        )

        val statusMatrix: Map<String, List<Pair<String, String>>> = mapOf(
            "sample_ord_001" to listOf(
                "VERIFIED" to "Checked against EUR 97,500 NordTextil proforma invoice",
                "VERIFIED" to "5,000 PCS carton weights & HS Code 6109.10 verified",
                "VERIFIED" to "On-board Bill of Lading issued at Chennai Port (INMAA)",
                "VERIFIED" to "REX / Chamber of Commerce Certificate of Origin verified",
                "VERIFIED" to "ICEGATE Let Export Order (LEO) checklist verified"
            ),
            "sample_ord_002" to listOf(
                "VERIFIED" to "Commercial Invoice USD 32,000 verified",
                "VERIFIED" to "2,000 PCS leather wallet packing list verified",
                "UNDER_REVIEW" to "Draft Airway Bill under freight forwarder review",
                "VERIFIED" to "CLRI & Chamber of Commerce Certificate of Origin verified",
                "UPLOADED" to "Draft Shipping Bill uploaded, awaiting customs broker check"
            ),
            "sample_ord_003" to listOf(
                "VERIFIED" to "AED 120,000 Commercial Invoice verified",
                "VERIFIED" to "8,000 KG spice bags packing manifest verified",
                "UPLOADED" to "Feeder vessel Bill of Lading draft attached",
                "VERIFIED" to "Spices Board & UAE Certificate of Origin verified",
                "VERIFIED" to "Shipping Bill verified for Dubai reefer dispatch"
            ),
            "sample_ord_004" to listOf(
                "VERIFIED" to "USD 16,800 Commercial Invoice verified",
                "UPLOADED" to "Preliminary packing list uploaded",
                "MISSING" to "Awaiting vessel booking confirmation for Singapore",
                "UNDER_REVIEW" to "ASEAN-India FTA Certificate of Origin under review",
                "MISSING" to "Awaiting FSSAI & phytosanitary batch test numbers"
            ),
            "sample_ord_005" to listOf(
                "VERIFIED" to "GBP 54,000 Final Commercial Invoice verified",
                "VERIFIED" to "1,200 CNC Steel Valves crate manifest verified",
                "VERIFIED" to "Clean Bill of Lading delivered to Midlands Industrial Ltd.",
                "VERIFIED" to "UK Certificate of Origin & Mill Test Certificate verified",
                "VERIFIED" to "Export Shipping Bill & e-BRC reconciliation complete"
            ),
            "sample_ord_006" to listOf(
                "VERIFIED" to "JPY 5,200,000 Commercial Invoice verified",
                "VERIFIED" to "1,500 Karur handloom sets packing list verified",
                "MISSING" to "Vessel slot to Port of Yokohama pending booking",
                "UPLOADED" to "IJCEPA Certificate of Origin draft uploaded",
                "UNDER_REVIEW" to "Handloom mark & Shipping Bill under internal review"
            )
        )

        val docs = mutableListOf<DocumentRecord>()
        orders.forEach { order ->
            val statuses = statusMatrix[order.id] ?: emptyList()
            fiveCoreDocs.forEachIndexed { idx, docTitle ->
                val (status, note) = statuses.getOrElse(idx) { "MISSING" to "Awaiting upload" }
                val slug = docTitle.lowercase().replace(" / ", "_").replace(' ', '_')
                val fileName = if (status == "MISSING") "" else "${slug}_${order.orderNumber.lowercase()}.pdf"
                val fileRef = if (status == "MISSING") "" else "sample://tn-global-exports/${order.orderNumber}/$fileName"
                docs.add(
                    DocumentRecord(
                        id = "sample_doc_${order.id}_${idx + 1}",
                        userId = SAMPLE_USER_ID,
                        exportOrderId = order.id,
                        documentType = docTitle,
                        requirementLevel = "REQUIRED",
                        status = status,
                        fileName = fileName,
                        fileReference = fileRef,
                        reviewerNote = "$note • LOCALLY STORED DEMO RECORD — NOT UPLOADED TO CLOUD",
                        fileMimeType = "application/pdf",
                        fileSizeBytes = if (status == "MISSING") 0L else 248_500L + (idx * 32_100L),
                        sha256Checksum = if (status == "MISSING") "" else "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b85${idx}",
                        versionNumber = 1,
                        reviewerRole = if (status == "VERIFIED") "COMPLIANCE_REVIEWER" else "",
                        createdAt = order.createdAt ?: tsAgo(nowMillis, DAY_MS),
                        updatedAt = order.updatedAt ?: tsAgo(nowMillis, HOUR_MS)
                    )
                )
            }
        }
        return docs
    }

    private fun buildSamplePayments(orders: List<ExportOrder>, nowMillis: Long): List<PaymentRecord> {
        return listOf(
            // Captured 3 hours ago -> Included in 1D, 1W, 1M, 3M, 1Y, ALL
            PaymentRecord(
                id = "sample_pay_001",
                userId = SAMPLE_USER_ID,
                exportOrderId = "sample_ord_001",
                paypalOrderId = "DEMO-TXN-ORDER001-ADVANCE-2925000",
                paypalCaptureId = "DEMO-CAP-EUR-2925000",
                approvalUrl = "",
                amount = 29250.0, // 30% Advance of EUR 97,500
                currency = "EUR",
                paymentType = "ADVANCE",
                paymentStatus = "DEMO_CAPTURED",
                verificationDetails = "SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION • 30% Advance (EUR 29,250) demo-captured • 70% Balance (EUR 68,250) CAD on Hamburg arrival.",
                createdAt = tsAgo(nowMillis, 4L * HOUR_MS),
                updatedAt = tsAgo(nowMillis, 3L * HOUR_MS)
            ),
            // Pending Demo Payment (2 days ago) -> Not captured, never counted as received revenue
            PaymentRecord(
                id = "sample_pay_002",
                userId = SAMPLE_USER_ID,
                exportOrderId = "sample_ord_002",
                paypalOrderId = "DEMO-TXN-ORDER002-ADVANCE-960000",
                paypalCaptureId = "",
                approvalUrl = "",
                amount = 9600.0, // 30% Advance of USD 32,000
                currency = "USD",
                paymentType = "ADVANCE",
                paymentStatus = "DEMO_PENDING",
                verificationDetails = "SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION • Demo payment request for Pacific Leather Co. (USD 9,600 advance awaiting simulated capture).",
                createdAt = tsAgo(nowMillis, 2L * DAY_MS),
                updatedAt = tsAgo(nowMillis, 2L * DAY_MS)
            ),
            // Captured 4 days ago -> Included in 1W, 1M, 3M, 1Y, ALL (Not 1D)
            PaymentRecord(
                id = "sample_pay_003",
                userId = SAMPLE_USER_ID,
                exportOrderId = "sample_ord_003",
                paypalOrderId = "DEMO-TXN-ORDER003-ADVANCE-3600000",
                paypalCaptureId = "DEMO-CAP-AED-3600000",
                approvalUrl = "",
                amount = 36000.0, // 30% Advance of AED 120,000
                currency = "AED",
                paymentType = "ADVANCE",
                paymentStatus = "DEMO_CAPTURED",
                verificationDetails = "SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION • 30% Advance (AED 36,000) settled in Demo Mode • Balance AED 84,000 due against original documents.",
                createdAt = tsAgo(nowMillis, 5L * DAY_MS),
                updatedAt = tsAgo(nowMillis, 4L * DAY_MS)
            ),
            PaymentRecord(
                id = "sample_pay_004",
                userId = SAMPLE_USER_ID,
                exportOrderId = "sample_ord_004",
                paypalOrderId = "",
                paypalCaptureId = "",
                approvalUrl = "",
                amount = 6720.0, // 40% Advance of USD 16,800
                currency = "USD",
                paymentType = "ADVANCE",
                paymentStatus = "NOT_INITIATED",
                verificationDetails = "SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION • 40% Advance (USD 6,720) scheduled once Singapore phytosanitary certificate is uploaded.",
                createdAt = tsAgo(nowMillis, 15L * DAY_MS),
                updatedAt = tsAgo(nowMillis, 15L * DAY_MS)
            ),
            // Captured 40 days ago -> Included in 3M, 1Y, ALL (Not 1D, 1W, or 1M)
            PaymentRecord(
                id = "sample_pay_005",
                userId = SAMPLE_USER_ID,
                exportOrderId = "sample_ord_005",
                paypalOrderId = "DEMO-TXN-ORDER005-FULL-5400000",
                paypalCaptureId = "DEMO-CAP-GBP-5400000",
                approvalUrl = "",
                amount = 54000.0, // 100% Full Settlement of GBP 54,000
                currency = "GBP",
                paymentType = "FULL",
                paymentStatus = "DEMO_CAPTURED",
                verificationDetails = "SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION • 100% Export Invoice (GBP 54,000 / ₹59.15 Lakhs) demo-captured for Midlands Industrial Ltd.",
                createdAt = tsAgo(nowMillis, 44L * DAY_MS),
                updatedAt = tsAgo(nowMillis, 40L * DAY_MS)
            ),
            PaymentRecord(
                id = "sample_pay_006",
                userId = SAMPLE_USER_ID,
                exportOrderId = "sample_ord_006",
                paypalOrderId = "DEMO-TXN-ORDER006-ADVANCE-156000000",
                paypalCaptureId = "",
                approvalUrl = "",
                amount = 1560000.0, // 30% Advance of JPY 5,200,000
                currency = "JPY",
                paymentType = "ADVANCE",
                paymentStatus = "DEMO_PENDING",
                verificationDetails = "SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION • Demo payment created for JPY 1,560,000 advance from Sakura Home Living, Tokyo.",
                createdAt = tsAgo(nowMillis, 145L * DAY_MS),
                updatedAt = tsAgo(nowMillis, 145L * DAY_MS)
            )
        )
    }

    private fun buildSampleActivities(nowMillis: Long): List<ActivityRecord> {
        val items = listOf(
            Triple("sample_ord_001", "SHIPMENT_DISPATCHED", "ORDER-001 Vessel Departed Chennai Port" to "5,000 PCS Organic Cotton Apparel loaded on MV Hapag-Lloyd bound for Hamburg (EUR 97,500)."),
            Triple("sample_ord_001", "DEMO_PAYMENT_CAPTURED", "ORDER-001 Simulated Advance Payment Captured" to "Demo-captured EUR 29,250 (30% advance) from NordTextil GmbH (DEMO-CAP-EUR-2925000)."),
            Triple("sample_ord_001", "DOC_VERIFIED", "ORDER-001 All 5 Mandatory Documents Verified" to "Commercial Invoice, Packing List, Bill of Lading, Origin Cert, and Shipping Bill marked VERIFIED."),
            Triple("sample_ord_002", "DEMO_PAYMENT_CREATED", "ORDER-002 Simulated Payment Initiated" to "Created Demo Payment DEMO-TXN-ORDER002-ADVANCE-960000 for USD 9,600 advance (Pacific Leather Co.)."),
            Triple("sample_ord_002", "DOC_UNDER_REVIEW", "ORDER-002 Airway Bill Under Review" to "Los Angeles CIF freight forwarder reviewing draft Airway Bill for 2,000 leather wallets."),
            Triple("sample_ord_002", "FINANCIAL_ASSESSED", "ORDER-002 Margin Computed at 22.8%" to "Ambur leather wallet order assessed at ₹26.65L gross value with ₹6.08L estimated net profit."),
            Triple("sample_ord_003", "DEMO_PAYMENT_CAPTURED", "ORDER-003 Simulated Advance AED 36,000 Captured" to "Al Noor Trading LLC settled 30% demo advance (DEMO-CAP-AED-3600000) for 8,000 KG Erode turmeric pack."),
            Triple("sample_ord_003", "DOC_VERIFIED", "ORDER-003 Spices Board & Origin Cert Verified" to "4 of 5 mandatory export documents verified; feeder Bill of Lading uploaded."),
            Triple("sample_ord_003", "FINANCIAL_ASSESSED", "ORDER-003 Spice Export Margin at 24.5%" to "Converted INR value ₹27.45L with ₹6.74L estimated net profit after reefer freight."),
            Triple("sample_ord_004", "DOC_ALERT", "ORDER-004 Missing Bill of Lading & Shipping Bill" to "Tropicana Foods Singapore order (USD 16,800) requires 2 mandatory documents before dispatch."),
            Triple("sample_ord_004", "FINANCIAL_ASSESSED", "ORDER-004 Financial Assessment Saved" to "Processed Chilli & Masala export valued at ₹14.00L with ₹3.44L net profit (24.5% margin)."),
            Triple("sample_ord_004", "ORDER_CREATED", "ORDER-004 Registered for Tropicana Foods" to "3,500 KG Guntur chilli & masala order created for Port of Singapore."),
            Triple("sample_ord_005", "ORDER_COMPLETED", "ORDER-005 Completed & 100% Settled" to "Midlands Industrial Ltd. (UK) GBP 54,000 CNC steel valve export fully settled & delivered."),
            Triple("sample_ord_005", "DEMO_PAYMENT_CAPTURED", "ORDER-005 Simulated Full Settlement GBP 54,000 Captured" to "Simulated payment capture DEMO-CAP-GBP-5400000 verified for ₹59.15L equivalent."),
            Triple("sample_ord_005", "DOC_VERIFIED", "ORDER-005 100% Compliance Readiness Achieved" to "All 5 mandatory UK export & mill test documents verified."),
            Triple("sample_ord_005", "FINANCIAL_ASSESSED", "ORDER-005 Highest Portfolio Margin (30.9%)" to "Ambattur precision valve shipment generated ₹18.25L net profit on ₹59.15L revenue."),
            Triple("sample_ord_006", "DEMO_PAYMENT_CREATED", "ORDER-006 Simulated JPY 1,560,000 Advance Initiated" to "Simulated payment DEMO-TXN-ORDER006-ADVANCE-156000000 generated for Sakura Home Living, Tokyo."),
            Triple("sample_ord_006", "DOC_UPLOADED", "ORDER-006 IJCEPA Origin Draft Uploaded" to "Karur handloom textile sets origin certificate uploaded for Japan customs."),
            Triple("sample_ord_006", "FINANCIAL_ASSESSED", "ORDER-006 JPY Conversion Assessed" to "JPY 5,200,000 converted at ₹0.573 = ₹29.80L with ₹7.33L estimated net profit."),
            Triple("sample_ord_006", "AI_EXTRACTION_CONFIRMED", "ORDER-006 AI Purchase Order Extraction Confirmed" to "Gemini multimodal extraction parsed Sakura Home Living PO with 96% confidence.")
        )

        return items.mapIndexed { idx, (orderId, eventType, pair) ->
            val offsetMillis = (idx + 1) * 3L * HOUR_MS
            ActivityRecord(
                id = "sample_act_${idx + 1}",
                userId = SAMPLE_USER_ID,
                exportOrderId = orderId,
                eventType = eventType,
                title = pair.first,
                description = pair.second,
                createdAt = tsAgo(nowMillis, offsetMillis),
                updatedAt = tsAgo(nowMillis, offsetMillis)
            )
        }
    }
}
