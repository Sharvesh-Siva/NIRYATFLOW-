package com.example

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.sample.SampleBusinessWorkspaceSeeder
import com.example.data.sample.SampleWorkspaceLocalStore
import com.example.data.sample.WorkspaceMode
import com.example.domain.FinancialCalculationEngine
import com.example.ui.components.AppBottomNavigation
import com.example.ui.components.CinematicLaunchScreen
import com.example.ui.components.PrimaryNavTab
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DocumentCentreScreen
import com.example.ui.screens.PayPalPaymentsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AiTaskState
import com.example.ui.viewmodel.NiryatPortfolioState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("NiryatFlow AI", appName)
    }

    @Test
    fun `tapping launch screen NiryatFlow AI emblem opens Sample Business Workspace`() {
        var enteredSampleWorkspace = false

        composeTestRule.setContent {
            MyApplicationTheme {
                CinematicLaunchScreen(
                    onLaunchComplete = {},
                    onEnterSampleWorkspace = { enteredSampleWorkspace = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("niryat_emblem_sample_entry").assertIsDisplayed()
        composeTestRule.onNodeWithText("Explore Sample Business").assertIsDisplayed()
        composeTestRule.onNodeWithTag("niryat_emblem_sample_entry").performClick()

        composeTestRule.mainClock.advanceTimeBy(250L)
        composeTestRule.waitForIdle()
        assertTrue(enteredSampleWorkspace)
    }

    @Test
    fun `AppBottomNavigation navigates across all five modules`() {
        val tabs = listOf(
            PrimaryNavTab("dashboard", "Home", Icons.Default.Home),
            PrimaryNavTab("orders", "Orders", Icons.Default.Inventory2),
            PrimaryNavTab("financial_intelligence", "Finance", Icons.Default.AccountBalance),
            PrimaryNavTab("document_centre", "Documents", Icons.Default.Description),
            PrimaryNavTab("more", "More", Icons.Default.MoreHoriz)
        )
        var currentRoute by mutableStateOf("dashboard")

        composeTestRule.setContent {
            MyApplicationTheme {
                AppBottomNavigation(
                    tabs = tabs,
                    activeTabRoute = currentRoute,
                    onSelectTab = { currentRoute = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("nav_orders").performClick()
        assertEquals("orders", currentRoute)

        composeTestRule.onNodeWithTag("nav_financial_intelligence").performClick()
        assertEquals("financial_intelligence", currentRoute)

        composeTestRule.onNodeWithTag("nav_document_centre").performClick()
        assertEquals("document_centre", currentRoute)

        composeTestRule.onNodeWithTag("nav_more").performClick()
        assertEquals("more", currentRoute)

        composeTestRule.onNodeWithTag("nav_dashboard").performClick()
        assertEquals("dashboard", currentRoute)
    }

    @Test
    fun `SampleWorkspaceLocalStore persists edits across restarts and resets cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SampleWorkspaceLocalStore(context)

        val initial = store.resetToCleanSeed()
        assertEquals(6, initial.orders.size)

        // Simulate deleting 1 order and updating status of ORDER-001, then saving to local store
        val modifiedOrders = initial.orders.drop(1)
        store.saveBundle(initial.copy(orders = modifiedOrders))

        // Simulate app restart by creating a new SampleWorkspaceLocalStore instance
        val reloadedAfterRestart = SampleWorkspaceLocalStore(context).loadOrSeed()
        assertEquals(5, reloadedAfterRestart.orders.size)

        // Reset restores all 6 orders
        val afterReset = store.resetToCleanSeed()
        assertEquals(6, afterReset.orders.size)
    }

    @Test
    fun `DashboardScreen time period selector dynamically recalculates KPIs for 1D 1W 1M and 1Y`() {
        val bundle = SampleBusinessWorkspaceSeeder.createSeedBundle()
        val metrics = FinancialCalculationEngine.calculatePortfolioMetrics(
            orders = bundle.orders,
            assessments = bundle.assessments,
            documents = bundle.documents,
            payments = bundle.payments
        )
        val portfolio = NiryatPortfolioState(
            workspaceMode = WorkspaceMode.SAMPLE_BUSINESS,
            businessProfile = bundle.profile,
            orders = bundle.orders,
            assessments = bundle.assessments,
            documents = bundle.documents,
            payments = bundle.payments,
            activities = bundle.activities,
            selectedOrderId = bundle.orders.first().id,
            performanceMetrics = metrics,
            isLoading = false
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                DashboardScreen(
                    portfolio = portfolio,
                    userEmail = "exports@tnglobalexports.in",
                    aiInsightsState = AiTaskState.Idle,
                    onSelectOrder = {},
                    onNavigate = {},
                    onRequestAiInsights = {}
                )
            }
        }

        // Default is 1M (4 orders within last 30 days)
        composeTestRule.onNodeWithTag("dashboard_period_summary_text")
            .assertTextContains("Last 30 Days: 4 order(s)", substring = true)

        // Switch to 1D (1 order within last 24 hours)
        composeTestRule.onNodeWithTag("period_pill_1D").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_period_summary_text")
            .assertTextContains("Last 24 Hours: 1 order(s)", substring = true)

        // Switch to 1W (3 orders within last 7 days)
        composeTestRule.onNodeWithTag("period_pill_1W").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_period_summary_text")
            .assertTextContains("Last 7 Days: 3 order(s)", substring = true)

        // Switch to 1Y (6 orders within last 12 months)
        composeTestRule.onNodeWithTag("period_pill_1Y").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_period_summary_text")
            .assertTextContains("Last 12 Months: 6 order(s)", substring = true)
    }

    @Test
    fun `PayPalPaymentsScreen in Demo Mode works without backend URL and displays SIMULATED PAYMENT banner`() {
        val bundle = SampleBusinessWorkspaceSeeder.createSeedBundle()
        val portfolio = NiryatPortfolioState(
            workspaceMode = WorkspaceMode.SAMPLE_BUSINESS,
            businessProfile = bundle.profile,
            orders = bundle.orders,
            assessments = bundle.assessments,
            documents = bundle.documents,
            payments = bundle.payments,
            activities = bundle.activities,
            selectedOrderId = "sample_ord_002", // ORDER-002 has DEMO_PENDING payment
            isLoading = false
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                PayPalPaymentsScreen(
                    portfolio = portfolio,
                    isPayPalConfigured = false, // Zero-cost Demo Mode without PAYPAL_BACKEND_BASE_URL
                    isPaymentBusy = false,
                    paypalAuthCheckStatus = null,
                    onVerifyPayPalOAuth = {},
                    onSelectOrder = {},
                    onCreatePayPalOrder = { _, _, _ -> },
                    onCapturePayPalOrder = {},
                    onReconcilePayPalOrder = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("simulated_payment_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("simulated_payment_banner_text")
            .assertTextContains("SIMULATED PAYMENT — NOT A REAL PAYPAL TRANSACTION", substring = true)
    }

    @Test
    fun `DocumentCentreScreen displays local device storage notice`() {
        val bundle = SampleBusinessWorkspaceSeeder.createSeedBundle()
        val portfolio = NiryatPortfolioState(
            workspaceMode = WorkspaceMode.SAMPLE_BUSINESS,
            businessProfile = bundle.profile,
            orders = bundle.orders,
            assessments = bundle.assessments,
            documents = bundle.documents,
            payments = bundle.payments,
            activities = bundle.activities,
            selectedOrderId = bundle.orders.first().id,
            isLoading = false
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                DocumentCentreScreen(
                    portfolio = portfolio,
                    onSelectOrder = {},
                    onInitializeChecklist = {},
                    onUpdateDocument = { _, _, _, _, _, _ -> },
                    onAddCustomDocument = { _, _, _ -> },
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("local_document_storage_notice").assertIsDisplayed()
        composeTestRule.onNodeWithTag("local_document_storage_notice")
            .assertTextContains("LOCALLY STORED ON DEVICE", substring = true)
    }
}
