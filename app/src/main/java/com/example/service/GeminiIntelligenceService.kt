package com.example.service

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AIExtractedOrderDraft
import com.example.data.model.DocumentRecord
import com.example.data.model.ExportOrder
import com.example.data.model.FinancialAssessment
import com.example.data.model.PaymentRecord
import com.example.data.model.PerformanceMetrics
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiIntelligence"

/**
 * Gemini AI Intelligence Layer via Firebase AI Logic (`Firebase.ai`).
 * Strictly separated from financial calculation authority and order persistence:
 * - Extracts structured export-order drafts with confidence/missing/uncertain indicators for human review.
 * - Explains deterministic financial results without modifying calculated numbers.
 * - Generates grounded operational insights from actual portfolio performance metrics.
 */
class GeminiIntelligenceService {

    private val extractionSchema = Schema.obj(
        properties = mapOf(
            "exporterName" to Schema.string(description = "Exporter company name in India"),
            "exporterContact" to Schema.string(description = "Exporter email or phone"),
            "exporterAddress" to Schema.string(description = "Exporter address"),
            "buyerName" to Schema.string(description = "Foreign buyer company name"),
            "buyerCountry" to Schema.string(description = "Destination country of buyer"),
            "buyerContact" to Schema.string(description = "Buyer email or phone"),
            "buyerAddress" to Schema.string(description = "Buyer shipping address"),
            "productName" to Schema.string(description = "Exported product name"),
            "productDescription" to Schema.string(description = "Product specifications, grade, or HS code"),
            "quantity" to Schema.double(description = "Numeric quantity ordered"),
            "unit" to Schema.string(description = "Unit of measurement such as KG, MT, PCS, BOX"),
            "unitPrice" to Schema.double(description = "Unit price in order currency"),
            "currency" to Schema.string(description = "3-letter ISO currency code such as USD, EUR, GBP, AED"),
            "incoterm" to Schema.string(description = "Incoterm such as FOB, CIF, EXW, DDP, CFR"),
            "destinationPort" to Schema.string(description = "Destination port or airport"),
            "orderDate" to Schema.string(description = "Order date in YYYY-MM-DD format"),
            "expectedShipmentDate" to Schema.string(description = "Expected shipment date in YYYY-MM-DD format"),
            "paymentTerms" to Schema.string(description = "Commercial payment terms description"),
            "advancePercentage" to Schema.double(description = "Advance payment percentage from 0 to 100"),
            "confidenceScore" to Schema.integer(description = "Extraction confidence score from 0 to 100"),
            "missingFields" to Schema.array(
                items = Schema.string(),
                description = "List of export order fields missing from the source input"
            ),
            "uncertainFields" to Schema.array(
                items = Schema.string(),
                description = "List of fields with ambiguous or low-confidence values"
            ),
            "conflictsDetected" to Schema.array(
                items = Schema.string(),
                description = "Any conflicting numbers or terms noticed in the input"
            ),
            "aiExtractionNotes" to Schema.string(description = "Brief summary of extraction findings for human review")
        )
    )

    private val extractionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = extractionSchema
            }
        )
    }

    private val explanationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    suspend fun extractOrderFromTextOrImage(
        rawText: String,
        documentBitmap: Bitmap? = null
    ): Result<AIExtractedOrderDraft> {
        return try {
            val promptText = """
                You are the NiryatFlow AI Export Order Intelligence Engine for Indian MSME exporters.
                Extract structured export order fields from the provided purchase order text or document image.
                Rules:
                1. Never invent prices or quantities that are not present; if missing, set numeric fields to 0 and list the field name in missingFields.
                2. Highlight any ambiguous values in uncertainFields and any calculation mismatches in conflictsDetected.
                3. Provide an accurate confidenceScore (0-100) and concise aiExtractionNotes.
                
                Input Text / Purchase Order Description:
                $rawText
            """.trimIndent()

            val response = withTimeout(60_000L) {
                if (documentBitmap != null) {
                    extractionModel.generateContent(
                        content {
                            image(documentBitmap)
                            text(promptText)
                        }
                    )
                } else {
                    extractionModel.generateContent(promptText)
                }
            }

            val jsonString = response.text ?: throw IllegalStateException("Gemini returned an empty extraction response.")
            val json = JSONObject(jsonString)

            fun parseStringList(key: String): List<String> {
                val arr = json.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).mapNotNull { idx ->
                    arr.optString(idx)?.takeIf { it.isNotBlank() }
                }
            }

            val draft = AIExtractedOrderDraft(
                exporterName = json.optString("exporterName", ""),
                exporterContact = json.optString("exporterContact", ""),
                exporterAddress = json.optString("exporterAddress", ""),
                buyerName = json.optString("buyerName", ""),
                buyerCountry = json.optString("buyerCountry", ""),
                buyerContact = json.optString("buyerContact", ""),
                buyerAddress = json.optString("buyerAddress", ""),
                productName = json.optString("productName", ""),
                productDescription = json.optString("productDescription", ""),
                quantity = json.optDouble("quantity", 0.0),
                unit = json.optString("unit", "KG").ifBlank { "KG" },
                unitPrice = json.optDouble("unitPrice", 0.0),
                currency = json.optString("currency", "USD").ifBlank { "USD" },
                incoterm = json.optString("incoterm", "FOB").ifBlank { "FOB" },
                destinationPort = json.optString("destinationPort", ""),
                orderDate = json.optString("orderDate", ""),
                expectedShipmentDate = json.optString("expectedShipmentDate", ""),
                paymentTerms = json.optString("paymentTerms", "30% Advance, 70% CAD"),
                advancePercentage = json.optDouble("advancePercentage", 30.0),
                confidenceScore = json.optInt("confidenceScore", 85),
                missingFields = parseStringList("missingFields"),
                uncertainFields = parseStringList("uncertainFields"),
                conflictsDetected = parseStringList("conflictsDetected"),
                aiExtractionNotes = json.optString("aiExtractionNotes", "Extracted via Gemini 3.8 Flash. Please review before confirming.")
            )
            Result.success(draft)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Order extraction timed out", e)
            Result.failure(IllegalStateException("AI extraction timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Order extraction failed", e)
            Result.failure(e)
        }
    }

    suspend fun explainFinancialViability(
        order: ExportOrder,
        assessment: FinancialAssessment
    ): Result<String> {
        return try {
            val prompt = """
                You are the NiryatFlow AI Export Financial Analyst.
                Analyze the following DETERMINISTICALLY CALCULATED export order figures (do NOT alter or recalculate any numbers; ground every observation strictly in these exact figures):
                
                Order: ${order.orderNumber} - ${order.productName} (${order.quantity} ${order.unit} @ ${order.currency} ${order.unitPrice})
                Buyer: ${order.buyerName} (${order.buyerCountry}), Incoterm: ${order.incoterm}
                Gross Order Value: ${assessment.currency} ${assessment.grossOrderValue}
                Exchange Rate Assumption: 1 ${assessment.currency} = ₹${assessment.exchangeRate}
                Converted Gross Revenue: ₹${assessment.convertedValueInr}
                Advance Payment (${assessment.advancePercentage}%): ${assessment.currency} ${assessment.advanceAmount}
                Outstanding Balance: ${assessment.currency} ${assessment.outstandingAmount}
                Payment Gateway Fees (${assessment.paymentFeePercentage}% + ${assessment.currency} ${assessment.paymentFixedFee}): ₹${assessment.paymentFeeInr}
                Estimated Net Revenue: ₹${assessment.estimatedNetRevenueInr}
                Procurement Cost: ₹${assessment.procurementCostInr}
                Logistics Cost: ₹${assessment.logisticsCostInr}
                Packaging Cost: ₹${assessment.packagingCostInr}
                Other Export Costs: ₹${assessment.otherCostsInr}
                Estimated Net Profit: ₹${assessment.estimatedProfitInr}
                Net Profit Margin: ${assessment.profitMarginPercent}%
                
                Provide a concise, executive 4-bullet analysis covering:
                1. Margin Health & Cost Drivers (which cost categories or payment fees impact profitability most).
                2. Working Capital & Cash Flow (advance coverage vs procurement/logistics outlay).
                3. FX Sensitivity Observation (how exchange-rate movements impact the ₹${assessment.estimatedProfitInr} profit).
                4. Actionable Operational Recommendation for the MSME exporter.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                explanationModel.generateContent(prompt)
            }
            val text = response.text?.trim()
            if (!text.isNullOrBlank()) {
                Result.success(text)
            } else {
                Result.failure(IllegalStateException("Model returned an empty financial explanation."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Financial explanation timed out", e)
            Result.failure(IllegalStateException("AI explanation timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Financial explanation failed", e)
            Result.failure(e)
        }
    }

    suspend fun generatePortfolioInsights(
        metrics: PerformanceMetrics,
        orders: List<ExportOrder>
    ): Result<String> {
        return try {
            if (orders.isEmpty()) {
                return Result.success("No export orders recorded yet. Create or extract an export order to generate AI operational insights grounded in your portfolio data.")
            }
            val orderSummaries = orders.take(10).joinToString("\n") {
                "- ${it.orderNumber}: ${it.productName} to ${it.buyerCountry} (${it.currency} ${it.orderValue}, Status: ${it.orderStatus})"
            }
            val prompt = """
                You are the NiryatFlow AI Export Operations Advisor.
                Provide an executive operational summary grounded strictly in these actual portfolio records (distinguish verified facts from AI observations):
                
                Total Orders: ${metrics.totalOrders} (Active: ${metrics.activeOrders}, Completed: ${metrics.completedOrders}, Pending: ${metrics.pendingOrders})
                Total Portfolio Export Value: ₹${metrics.totalOrderValueInr}
                Captured Payments: ₹${metrics.capturedPaymentValueInr} (${metrics.paymentCompletionRatePercent}% collection rate)
                Outstanding Balance: ₹${metrics.outstandingPaymentValueInr}
                Total Estimated Profit: ₹${metrics.totalEstimatedProfitInr} (Avg Margin: ${metrics.averageProfitMarginPercent}%)
                Average Document Readiness: ${metrics.documentReadinessRatePercent}%
                
                Recent Orders:
                $orderSummaries
                
                Provide 3 concise, high-impact observations on:
                1. Documentation & Shipment Readiness Bottlenecks
                2. Payment Collection & Receivables Exposure
                3. Portfolio Profitability & Geographic Concentration
            """.trimIndent()

            val response = withTimeout(60_000L) {
                explanationModel.generateContent(prompt)
            }
            val text = response.text?.trim()
            if (!text.isNullOrBlank()) {
                Result.success(text)
            } else {
                Result.failure(IllegalStateException("Model returned an empty portfolio insight response."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Portfolio insights timed out", e)
            Result.failure(IllegalStateException("AI insights timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Portfolio insights failed", e)
            Result.failure(e)
        }
    }

    suspend fun answerNaturalLanguageBusinessQuery(
        query: String,
        businessName: String,
        isSampleWorkspace: Boolean,
        metrics: PerformanceMetrics,
        orders: List<ExportOrder>,
        assessments: List<FinancialAssessment>,
        documents: List<DocumentRecord>,
        payments: List<PaymentRecord>
    ): Result<String> {
        return try {
            if (orders.isEmpty()) {
                return Result.success("No export orders are currently recorded in $businessName. Create an order or switch to the Sample Business Workspace to run natural-language queries.")
            }

            val assessmentMap = assessments.associateBy { it.exportOrderId }
            val docsByOrder = documents.groupBy { it.exportOrderId }
            val paymentsByOrder = payments.groupBy { it.exportOrderId }

            val structuredContext = orders.joinToString("\n") { ord ->
                val fin = assessmentMap[ord.id]
                val docs = docsByOrder[ord.id].orEmpty()
                val reqDocs = docs.filter { it.requirementLevel == "REQUIRED" }
                val verifiedDocs = reqDocs.count { it.status == "VERIFIED" }
                val missingDocNames = reqDocs.filter { it.status != "VERIFIED" }.joinToString(", ") { "${it.documentType}(${it.status})" }
                val pays = paymentsByOrder[ord.id].orEmpty()
                val paySummary = if (pays.isEmpty()) "No payment initiated" else pays.joinToString("; ") { "${it.paymentType} ${it.currency} ${it.amount} [${it.paymentStatus}]" }

                "- ${ord.orderNumber} (${ord.productName} -> ${ord.buyerName}, ${ord.buyerCountry}): " +
                    "Value=${ord.currency} ${ord.orderValue}, Status=${ord.orderStatus}, " +
                    "Margin=${fin?.profitMarginPercent ?: 15.0}%, EstProfitINR=₹${fin?.estimatedProfitInr ?: 0.0}, " +
                    "DocsVerified=$verifiedDocs/${reqDocs.size} (Pending: ${missingDocNames.ifBlank { "None" }}), " +
                    "Payments=($paySummary)"
            }

            val prompt = """
                You are the NiryatFlow AI Business Query Engine for $businessName (${if (isSampleWorkspace) "SAMPLE BUSINESS WORKSPACE - ZERO-COST DEMO MODE WITH SIMULATED PAYMENTS [DEMO_CAPTURED] AND LOCALLY STORED DOCUMENTS" else "PRODUCTION WORKSPACE"}).
                Answer the exporter's natural-language query strictly using the authorized workspace records below.
                Do NOT invent orders, amounts, or statuses not present in the context.
                ${if (isSampleWorkspace) "IMPORTANT: Clearly note that payments marked DEMO_CAPTURED / DEMO_PENDING are SIMULATED PAYMENTS (not real PayPal transactions) and documents are locally stored demo records." else ""}
                
                Workspace Portfolio Totals (Base Currency: INR):
                - Total Orders: ${metrics.totalOrders} (Active: ${metrics.activeOrders}, Completed: ${metrics.completedOrders})
                - Total Export Value: ₹${metrics.totalOrderValueInr}
                - Captured Payments: ₹${metrics.capturedPaymentValueInr} (${metrics.paymentCompletionRatePercent}%)
                - Outstanding Receivables: ₹${metrics.outstandingPaymentValueInr}
                - Average Profit Margin: ${metrics.averageProfitMarginPercent}%
                - Country Revenue Breakdown (INR): ${metrics.countryDistribution.entries.joinToString { "${it.key}: ₹${it.value}" }}
                
                Order-by-Order Authorized Records:
                $structuredContext
                
                Exporter Question: "$query"
                
                Provide a direct, concise, factual answer referencing specific Order Numbers, Buyers, Currencies, and exact figures.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                explanationModel.generateContent(prompt)
            }
            val text = response.text?.trim()
            if (!text.isNullOrBlank()) {
                Result.success(text)
            } else {
                Result.failure(IllegalStateException("Gemini returned an empty response for your query."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "NL Business query timed out", e)
            Result.failure(IllegalStateException("AI query timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "NL Business query failed", e)
            Result.failure(e)
        }
    }
}
