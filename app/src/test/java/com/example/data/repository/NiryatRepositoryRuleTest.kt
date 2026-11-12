package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.DocumentRecord
import com.example.data.model.ExportOrder
import com.example.domain.FinancialCalculationEngine
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class NiryatRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun sampleOrder(uid: String) = ExportOrder(
        userId = uid,
        orderNumber = "NF-2026-${UUID.randomUUID().toString().take(4)}",
        exporterName = "Deccan Organic Exports Pvt Ltd",
        exporterContact = "ops@deccanexports.in",
        exporterAddress = "Pune, Maharashtra, India",
        buyerName = "Thames Specialty Foods Ltd",
        buyerCountry = "United Kingdom",
        buyerContact = "imports@thamesfoods.co.uk",
        buyerAddress = "London, UK",
        productName = "Alphonso Mango Pulp Grade A",
        productDescription = "HS 08045040 - Aseptic Totapuri & Alphonso Blend",
        quantity = 2000.0,
        unit = "KG",
        unitPrice = 4.50,
        orderValue = 9000.0,
        currency = "USD",
        incoterm = "CIF",
        destinationPort = "Port of Felixstowe",
        orderDate = "2026-11-09",
        expectedShipmentDate = "2026-11-30",
        paymentTerms = "40% Advance, 60% CAD",
        advancePercentage = 40.0,
        orderStatus = "CREATED",
        extractionSource = "MANUAL"
    )

    @Test
    fun createAndObserveOrder_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = NiryatRepository(firestore)
        val createRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createOrder(sampleOrder(uid), initializeDefaultChecklist = false)
        }
        assertTrue(createRes.isSuccess)
        val orderId = createRes.getOrThrow()
        assertTrue(orderId.isNotEmpty())

        val emittedOrders = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeOrders(uid).first { list -> list.any { it.id == orderId } }
        }
        assertTrue(emittedOrders.any { it.id == orderId })
    }

    @Test
    fun getOrderById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = NiryatRepository(firestore)
        val orderId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createOrder(sampleOrder(aliceUid), initializeDefaultChecklist = false).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = NiryatRepository(firestore)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobRepo.getOrderById(orderId).getOrThrow()
            }
            fail("Expected FirebaseFirestoreException PERMISSION_DENIED for cross-user read")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    @Test
    fun observeOrders_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = NiryatRepository(firestore)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeOrders(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    @Test
    fun deterministicFinancialCalculationEngine_computesExactValues() {
        val assessment = FinancialCalculationEngine.calculateAssessment(
            userId = "u1",
            exportOrderId = "ord1",
            quantity = 1000.0,
            unitPrice = 10.0,
            currency = "USD",
            exchangeRate = 84.0,
            advancePercentage = 30.0,
            paymentFeePercentage = 4.0,
            paymentFixedFee = 0.0,
            procurementCostInr = 500000.0,
            logisticsCostInr = 80000.0,
            packagingCostInr = 20000.0,
            otherCostsInr = 6400.0
        )
        assertEquals(10000.0, assessment.grossOrderValue, 0.001)
        assertEquals(3000.0, assessment.advanceAmount, 0.001)
        assertEquals(7000.0, assessment.outstandingAmount, 0.001)
        assertEquals(840000.0, assessment.convertedValueInr, 0.001)
        assertEquals(33600.0, assessment.paymentFeeInr, 0.001)
        assertEquals(806400.0, assessment.estimatedNetRevenueInr, 0.001)
        assertEquals(200000.0, assessment.estimatedProfitInr, 0.001)
        assertEquals(24.8, assessment.profitMarginPercent, 0.01)

        val docs = listOf(
            DocumentRecord(requirementLevel = "REQUIRED", status = "VERIFIED"),
            DocumentRecord(requirementLevel = "REQUIRED", status = "UPLOADED"),
            DocumentRecord(requirementLevel = "OPTIONAL", status = "MISSING")
        )
        val (readinessPct, counts) = FinancialCalculationEngine.calculateDocumentReadiness(docs)
        assertEquals(50.0, readinessPct, 0.01)
        assertEquals(1 to 2, counts)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@niryatflow.test"
        const val BOB_EMAIL = "bob@niryatflow.test"
        const val DEFAULT_TIMEOUT_MS = 15000L
        const val FLOW_TIMEOUT_MS = 15000L
    }
}
