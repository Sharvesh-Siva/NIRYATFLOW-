package com.example.data.sample

import android.content.Context
import com.example.data.model.ActivityRecord
import com.example.data.model.DocumentRecord
import com.example.data.model.ExportOrder
import com.example.data.model.FinancialAssessment
import com.example.data.model.PaymentRecord
import com.google.firebase.Timestamp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date

/**
 * Local JSON persistence store for the isolated Sample Business Workspace
 * ("Tamil Nadu Global Exports Pvt. Ltd.", Chennai).
 *
 * Ensures that all edits made inside the Sample Business Workspace (creating/editing/deleting
 * orders, updating shipment statuses, saving financial assessments, uploading/verifying documents,
 * and capturing sample payments) survive application restarts while remaining 100% isolated from
 * Production Firestore. Calling `resetToCleanSeed()` wipes the local override file and restores
 * the original 6-order seed dataset.
 */
class SampleWorkspaceLocalStore(context: Context) {

    private val storeFile: File = File(context.applicationContext.filesDir, STORE_FILE_NAME)

    fun loadOrSeed(nowMillis: Long = System.currentTimeMillis()): SampleWorkspaceSeedBundle {
        if (!storeFile.exists()) {
            val fresh = SampleBusinessWorkspaceSeeder.createSeedBundle(nowMillis)
            saveBundle(fresh)
            return fresh
        }
        return runCatching {
            val raw = storeFile.readText()
            deserializeBundle(JSONObject(raw))
        }.getOrElse {
            val fallback = SampleBusinessWorkspaceSeeder.createSeedBundle(nowMillis)
            saveBundle(fallback)
            fallback
        }
    }

    fun saveBundle(bundle: SampleWorkspaceSeedBundle) {
        runCatching {
            val json = serializeBundle(bundle)
            storeFile.writeText(json.toString())
        }
    }

    fun resetToCleanSeed(nowMillis: Long = System.currentTimeMillis()): SampleWorkspaceSeedBundle {
        runCatching {
            if (storeFile.exists()) {
                storeFile.delete()
            }
        }
        val clean = SampleBusinessWorkspaceSeeder.createSeedBundle(nowMillis)
        saveBundle(clean)
        return clean
    }

    private fun serializeBundle(bundle: SampleWorkspaceSeedBundle): JSONObject {
        val root = JSONObject()

        val p = bundle.profile
        root.put("profile", JSONObject().apply {
            put("businessName", p.businessName)
            put("businessType", p.businessType)
            put("primaryBase", p.primaryBase)
            put("contactEmail", p.contactEmail)
            put("productCategories", p.productCategories)
            put("reportingCurrency", p.reportingCurrency)
            put("iecReference", p.iecReference)
            put("gstStateCode", p.gstStateCode)
            put("isSampleWorkspace", p.isSampleWorkspace)
        })

        root.put("orders", JSONArray().apply {
            bundle.orders.forEach { o ->
                put(JSONObject().apply {
                    put("id", o.id)
                    put("userId", o.userId)
                    put("orderNumber", o.orderNumber)
                    put("exporterName", o.exporterName)
                    put("exporterContact", o.exporterContact)
                    put("exporterAddress", o.exporterAddress)
                    put("buyerName", o.buyerName)
                    put("buyerCountry", o.buyerCountry)
                    put("buyerContact", o.buyerContact)
                    put("buyerAddress", o.buyerAddress)
                    put("productName", o.productName)
                    put("productDescription", o.productDescription)
                    put("quantity", o.quantity)
                    put("unit", o.unit)
                    put("unitPrice", o.unitPrice)
                    put("orderValue", o.orderValue)
                    put("currency", o.currency)
                    put("incoterm", o.incoterm)
                    put("destinationPort", o.destinationPort)
                    put("orderDate", o.orderDate)
                    put("expectedShipmentDate", o.expectedShipmentDate)
                    put("paymentTerms", o.paymentTerms)
                    put("advancePercentage", o.advancePercentage)
                    put("orderStatus", o.orderStatus)
                    put("extractionSource", o.extractionSource)
                    put("createdAtMs", o.createdAt?.toDate()?.time ?: System.currentTimeMillis())
                    put("updatedAtMs", o.updatedAt?.toDate()?.time ?: System.currentTimeMillis())
                })
            }
        })

        root.put("assessments", JSONArray().apply {
            bundle.assessments.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id)
                    put("userId", a.userId)
                    put("exportOrderId", a.exportOrderId)
                    put("grossOrderValue", a.grossOrderValue)
                    put("currency", a.currency)
                    put("exchangeRate", a.exchangeRate)
                    put("convertedValueInr", a.convertedValueInr)
                    put("advancePercentage", a.advancePercentage)
                    put("advanceAmount", a.advanceAmount)
                    put("outstandingAmount", a.outstandingAmount)
                    put("paymentFeePercentage", a.paymentFeePercentage)
                    put("paymentFixedFee", a.paymentFixedFee)
                    put("paymentFeeInr", a.paymentFeeInr)
                    put("procurementCostInr", a.procurementCostInr)
                    put("logisticsCostInr", a.logisticsCostInr)
                    put("packagingCostInr", a.packagingCostInr)
                    put("otherCostsInr", a.otherCostsInr)
                    put("estimatedNetRevenueInr", a.estimatedNetRevenueInr)
                    put("estimatedProfitInr", a.estimatedProfitInr)
                    put("profitMarginPercent", a.profitMarginPercent)
                    put("assumptionsNote", a.assumptionsNote)
                    put("createdAtMs", a.createdAt?.toDate()?.time ?: System.currentTimeMillis())
                    put("updatedAtMs", a.updatedAt?.toDate()?.time ?: System.currentTimeMillis())
                })
            }
        })

        root.put("documents", JSONArray().apply {
            bundle.documents.forEach { d ->
                put(JSONObject().apply {
                    put("id", d.id)
                    put("userId", d.userId)
                    put("exportOrderId", d.exportOrderId)
                    put("documentType", d.documentType)
                    put("requirementLevel", d.requirementLevel)
                    put("status", d.status)
                    put("fileName", d.fileName)
                    put("fileReference", d.fileReference)
                    put("reviewerNote", d.reviewerNote)
                    put("fileMimeType", d.fileMimeType)
                    put("fileSizeBytes", d.fileSizeBytes)
                    put("sha256Checksum", d.sha256Checksum)
                    put("versionNumber", d.versionNumber)
                    put("reviewerRole", d.reviewerRole)
                    put("createdAtMs", d.createdAt?.toDate()?.time ?: System.currentTimeMillis())
                    put("updatedAtMs", d.updatedAt?.toDate()?.time ?: System.currentTimeMillis())
                })
            }
        })

        root.put("payments", JSONArray().apply {
            bundle.payments.forEach { pay ->
                put(JSONObject().apply {
                    put("id", pay.id)
                    put("userId", pay.userId)
                    put("exportOrderId", pay.exportOrderId)
                    put("paypalOrderId", pay.paypalOrderId)
                    put("paypalCaptureId", pay.paypalCaptureId)
                    put("approvalUrl", pay.approvalUrl)
                    put("amount", pay.amount)
                    put("currency", pay.currency)
                    put("paymentType", pay.paymentType)
                    put("paymentStatus", pay.paymentStatus)
                    put("verificationDetails", pay.verificationDetails)
                    put("createdAtMs", pay.createdAt?.toDate()?.time ?: System.currentTimeMillis())
                    put("updatedAtMs", pay.updatedAt?.toDate()?.time ?: System.currentTimeMillis())
                })
            }
        })

        root.put("activities", JSONArray().apply {
            bundle.activities.forEach { act ->
                put(JSONObject().apply {
                    put("id", act.id)
                    put("userId", act.userId)
                    put("exportOrderId", act.exportOrderId)
                    put("eventType", act.eventType)
                    put("title", act.title)
                    put("description", act.description)
                    put("createdAtMs", act.createdAt?.toDate()?.time ?: System.currentTimeMillis())
                    put("updatedAtMs", act.updatedAt?.toDate()?.time ?: System.currentTimeMillis())
                })
            }
        })

        return root
    }

    private fun deserializeBundle(root: JSONObject): SampleWorkspaceSeedBundle {
        val pObj = root.optJSONObject("profile")
        val profile = if (pObj != null) {
            BusinessWorkspaceProfile(
                businessName = pObj.optString("businessName", "Tamil Nadu Global Exports Pvt. Ltd."),
                businessType = pObj.optString("businessType", "MSME Export Company"),
                primaryBase = pObj.optString("primaryBase", "Chennai, Tamil Nadu, India"),
                contactEmail = pObj.optString("contactEmail", "exports@tnglobalexports.in"),
                productCategories = pObj.optString("productCategories", "Textiles, Leather Goods, Spices, Engineering Components"),
                reportingCurrency = pObj.optString("reportingCurrency", "INR"),
                iecReference = pObj.optString("iecReference", "0419084721 (Sample MSME Reference)"),
                gstStateCode = pObj.optString("gstStateCode", "33 - Tamil Nadu"),
                isSampleWorkspace = pObj.optBoolean("isSampleWorkspace", true)
            )
        } else {
            SampleBusinessWorkspaceSeeder.defaultSampleBusinessProfile()
        }

        val ordersArr = root.optJSONArray("orders") ?: JSONArray()
        val orders = (0 until ordersArr.length()).map { idx ->
            val o = ordersArr.getJSONObject(idx)
            ExportOrder(
                id = o.optString("id"),
                userId = o.optString("userId", SampleBusinessWorkspaceSeeder.SAMPLE_USER_ID),
                orderNumber = o.optString("orderNumber"),
                exporterName = o.optString("exporterName"),
                exporterContact = o.optString("exporterContact"),
                exporterAddress = o.optString("exporterAddress"),
                buyerName = o.optString("buyerName"),
                buyerCountry = o.optString("buyerCountry"),
                buyerContact = o.optString("buyerContact"),
                buyerAddress = o.optString("buyerAddress"),
                productName = o.optString("productName"),
                productDescription = o.optString("productDescription"),
                quantity = o.optDouble("quantity", 0.0),
                unit = o.optString("unit", "PCS"),
                unitPrice = o.optDouble("unitPrice", 0.0),
                orderValue = o.optDouble("orderValue", 0.0),
                currency = o.optString("currency", "USD"),
                incoterm = o.optString("incoterm", "FOB"),
                destinationPort = o.optString("destinationPort"),
                orderDate = o.optString("orderDate"),
                expectedShipmentDate = o.optString("expectedShipmentDate"),
                paymentTerms = o.optString("paymentTerms"),
                advancePercentage = o.optDouble("advancePercentage", 30.0),
                orderStatus = o.optString("orderStatus", "CREATED"),
                extractionSource = o.optString("extractionSource", "MANUAL"),
                createdAt = Timestamp(Date(o.optLong("createdAtMs", System.currentTimeMillis()))),
                updatedAt = Timestamp(Date(o.optLong("updatedAtMs", System.currentTimeMillis())))
            )
        }

        val assessArr = root.optJSONArray("assessments") ?: JSONArray()
        val assessments = (0 until assessArr.length()).map { idx ->
            val a = assessArr.getJSONObject(idx)
            FinancialAssessment(
                id = a.optString("id"),
                userId = a.optString("userId", SampleBusinessWorkspaceSeeder.SAMPLE_USER_ID),
                exportOrderId = a.optString("exportOrderId"),
                grossOrderValue = a.optDouble("grossOrderValue", 0.0),
                currency = a.optString("currency", "USD"),
                exchangeRate = a.optDouble("exchangeRate", 84.20),
                convertedValueInr = a.optDouble("convertedValueInr", 0.0),
                advancePercentage = a.optDouble("advancePercentage", 30.0),
                advanceAmount = a.optDouble("advanceAmount", 0.0),
                outstandingAmount = a.optDouble("outstandingAmount", 0.0),
                paymentFeePercentage = a.optDouble("paymentFeePercentage", 3.4),
                paymentFixedFee = a.optDouble("paymentFixedFee", 0.30),
                paymentFeeInr = a.optDouble("paymentFeeInr", 0.0),
                procurementCostInr = a.optDouble("procurementCostInr", 0.0),
                logisticsCostInr = a.optDouble("logisticsCostInr", 0.0),
                packagingCostInr = a.optDouble("packagingCostInr", 0.0),
                otherCostsInr = a.optDouble("otherCostsInr", 0.0),
                estimatedNetRevenueInr = a.optDouble("estimatedNetRevenueInr", 0.0),
                estimatedProfitInr = a.optDouble("estimatedProfitInr", 0.0),
                profitMarginPercent = a.optDouble("profitMarginPercent", 0.0),
                assumptionsNote = a.optString("assumptionsNote"),
                createdAt = Timestamp(Date(a.optLong("createdAtMs", System.currentTimeMillis()))),
                updatedAt = Timestamp(Date(a.optLong("updatedAtMs", System.currentTimeMillis())))
            )
        }

        val docsArr = root.optJSONArray("documents") ?: JSONArray()
        val documents = (0 until docsArr.length()).map { idx ->
            val d = docsArr.getJSONObject(idx)
            DocumentRecord(
                id = d.optString("id"),
                userId = d.optString("userId", SampleBusinessWorkspaceSeeder.SAMPLE_USER_ID),
                exportOrderId = d.optString("exportOrderId"),
                documentType = d.optString("documentType"),
                requirementLevel = d.optString("requirementLevel", "REQUIRED"),
                status = d.optString("status", "MISSING"),
                fileName = d.optString("fileName"),
                fileReference = d.optString("fileReference"),
                reviewerNote = d.optString("reviewerNote"),
                fileMimeType = d.optString("fileMimeType", "application/pdf"),
                fileSizeBytes = d.optLong("fileSizeBytes", 0L),
                sha256Checksum = d.optString("sha256Checksum", ""),
                versionNumber = d.optInt("versionNumber", 1),
                reviewerRole = d.optString("reviewerRole", ""),
                createdAt = Timestamp(Date(d.optLong("createdAtMs", System.currentTimeMillis()))),
                updatedAt = Timestamp(Date(d.optLong("updatedAtMs", System.currentTimeMillis())))
            )
        }

        val payArr = root.optJSONArray("payments") ?: JSONArray()
        val payments = (0 until payArr.length()).map { idx ->
            val pay = payArr.getJSONObject(idx)
            PaymentRecord(
                id = pay.optString("id"),
                userId = pay.optString("userId", SampleBusinessWorkspaceSeeder.SAMPLE_USER_ID),
                exportOrderId = pay.optString("exportOrderId"),
                paypalOrderId = pay.optString("paypalOrderId"),
                paypalCaptureId = pay.optString("paypalCaptureId"),
                approvalUrl = pay.optString("approvalUrl"),
                amount = pay.optDouble("amount", 0.0),
                currency = pay.optString("currency", "USD"),
                paymentType = pay.optString("paymentType", "ADVANCE"),
                paymentStatus = pay.optString("paymentStatus", "NOT_INITIATED"),
                verificationDetails = pay.optString("verificationDetails"),
                createdAt = Timestamp(Date(pay.optLong("createdAtMs", System.currentTimeMillis()))),
                updatedAt = Timestamp(Date(pay.optLong("updatedAtMs", System.currentTimeMillis())))
            )
        }

        val actArr = root.optJSONArray("activities") ?: JSONArray()
        val activities = (0 until actArr.length()).map { idx ->
            val act = actArr.getJSONObject(idx)
            ActivityRecord(
                id = act.optString("id"),
                userId = act.optString("userId", SampleBusinessWorkspaceSeeder.SAMPLE_USER_ID),
                exportOrderId = act.optString("exportOrderId"),
                eventType = act.optString("eventType"),
                title = act.optString("title"),
                description = act.optString("description"),
                createdAt = Timestamp(Date(act.optLong("createdAtMs", System.currentTimeMillis()))),
                updatedAt = Timestamp(Date(act.optLong("updatedAtMs", System.currentTimeMillis())))
            )
        }

        return SampleWorkspaceSeedBundle(
            profile = profile,
            orders = orders,
            assessments = assessments,
            documents = documents,
            payments = payments,
            activities = activities
        )
    }

    companion object {
        private const val STORE_FILE_NAME = "sample_workspace_store_v1.json"
    }
}
