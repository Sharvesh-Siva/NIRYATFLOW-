package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.repository.NiryatRepository
import com.example.data.sample.SampleBusinessWorkspaceSeeder
import com.example.data.sample.SampleWorkspaceLocalStore
import com.example.data.sample.WorkspaceMode
import com.example.ui.components.AppBottomNavigation
import com.example.ui.components.CinematicLaunchScreen
import com.example.ui.components.NiryatAuthScreen
import com.example.ui.components.PrimaryNavTab
import com.example.ui.components.signOutNiryatUser
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DocumentCentreScreen
import com.example.ui.screens.ExportOrderDigitalTwinScreen
import com.example.ui.screens.FinancialIntelligenceScreen
import com.example.ui.screens.NewExportOrderScreen
import com.example.ui.screens.OrdersHubScreen
import com.example.ui.screens.PayPalPaymentsScreen
import com.example.ui.screens.ProfileAndMoreScreen
import com.example.ui.theme.ChampagneAccent
import com.example.ui.theme.IvoryBackground
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LightGoldAccent
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OnGoldDarkInk
import com.example.ui.theme.PrimaryInk
import com.example.ui.theme.PureWhiteSurface
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.SoftChampagneBg
import com.example.ui.theme.SpaceGroteskFamily
import com.example.ui.theme.WarmBorder
import com.example.ui.viewmodel.NiryatViewModel
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import java.lang.reflect.Field

private const val TAG = "AppCheck"
private const val EXTRA_DEBUG_TOKEN = "com.example.app.DEBUG_TOKEN"
private const val WORKSPACE_PREFS_NAME = "niryat_workspace_prefs"
private const val PREF_KEY_WORKSPACE_MODE = "active_workspace_mode"
private const val PREF_KEY_SAMPLE_GUEST_ACTIVE = "sample_guest_active"

private fun configureAppCheck(activity: ComponentActivity, intent: Intent) {
    val isDebuggable =
        (activity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (!isDebuggable) {
        Log.i(TAG, "Release build detected: Play Integrity App Check enforcement active.")
        return
    }
    val token = intent.getStringExtra(EXTRA_DEBUG_TOKEN)
    val factory = DebugAppCheckProviderFactory.getInstance()
    if (!token.isNullOrEmpty()) {
        try {
            val secretField: Field =
                DebugAppCheckProviderFactory::class.java.getDeclaredField("debugSecret")
            secretField.isAccessible = true
            secretField.set(factory, token)
            Log.i(TAG, "Injected App Check debug token from intent extra")
        } catch (e: ReflectiveOperationException) {
            Log.e(TAG, "Failed to inject App Check debug token", e)
        }
    }
    Firebase.appCheck.installAppCheckProviderFactory(factory)
}

object NiryatRoutes {
    const val ROUTE_DASHBOARD = "dashboard"
    const val ROUTE_ORDERS = "orders"
    const val ROUTE_NEW_ORDER = "new_order"
    const val ROUTE_FINANCIAL_INTELLIGENCE = "financial_intelligence"
    const val ROUTE_DOCUMENT_CENTRE = "document_centre"
    const val ROUTE_PAYPAL_PAYMENTS = "paypal_payments"
    const val ROUTE_DIGITAL_TWIN = "digital_twin"
    const val ROUTE_MORE = "more"
}

private val PRIMARY_NAV_TABS = listOf(
    PrimaryNavTab(
        route = NiryatRoutes.ROUTE_DASHBOARD,
        label = "Home",
        icon = Icons.Default.Home
    ),
    PrimaryNavTab(
        route = NiryatRoutes.ROUTE_ORDERS,
        label = "Orders",
        icon = Icons.Default.Inventory2
    ),
    PrimaryNavTab(
        route = NiryatRoutes.ROUTE_FINANCIAL_INTELLIGENCE,
        label = "Finance",
        icon = Icons.Default.AccountBalance
    ),
    PrimaryNavTab(
        route = NiryatRoutes.ROUTE_DOCUMENT_CENTRE,
        label = "Documents",
        icon = Icons.Default.Description
    ),
    PrimaryNavTab(
        route = NiryatRoutes.ROUTE_MORE,
        label = "More",
        icon = Icons.Default.MoreHoriz
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                NiryatRootApp()
            }
        }
    }
}

@Composable
fun NiryatRootApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val prefs = remember {
        context.getSharedPreferences(WORKSPACE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    var showSplash by rememberSaveable { mutableStateOf(true) }
    var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }
    var isExploringSampleWithoutAuth by rememberSaveable {
        mutableStateOf(prefs.getBoolean(PREF_KEY_SAMPLE_GUEST_ACTIVE, true))
    }
    var preferredWorkspaceModeName by rememberSaveable {
        mutableStateOf(
            prefs.getString(
                PREF_KEY_WORKSPACE_MODE,
                if (Firebase.auth.currentUser != null) WorkspaceMode.PRODUCTION.name else WorkspaceMode.SAMPLE_BUSINESS.name
            ) ?: WorkspaceMode.SAMPLE_BUSINESS.name
        )
    }

    fun persistWorkspaceContext(mode: WorkspaceMode, sampleGuestActive: Boolean) {
        preferredWorkspaceModeName = mode.name
        isExploringSampleWithoutAuth = sampleGuestActive
        prefs.edit()
            .putString(PREF_KEY_WORKSPACE_MODE, mode.name)
            .putBoolean(PREF_KEY_SAMPLE_GUEST_ACTIVE, sampleGuestActive)
            .apply()
    }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (showSplash) {
        CinematicLaunchScreen(
            onLaunchComplete = {
                showSplash = false
            },
            onEnterSampleWorkspace = {
                persistWorkspaceContext(
                    mode = WorkspaceMode.SAMPLE_BUSINESS,
                    sampleGuestActive = true
                )
                showSplash = false
            }
        )
        return
    }

    val activeUser = currentUser
    if (activeUser == null && !isExploringSampleWithoutAuth) {
        NiryatAuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
                persistWorkspaceContext(
                    mode = WorkspaceMode.PRODUCTION,
                    sampleGuestActive = false
                )
            },
            onExploreSampleWorkspace = {
                persistWorkspaceContext(
                    mode = WorkspaceMode.SAMPLE_BUSINESS,
                    sampleGuestActive = true
                )
            }
        )
    } else {
        val vmKey = if (activeUser != null) {
            "niryat_vm_prod_${activeUser.uid}"
        } else {
            "niryat_vm_sample_guest"
        }
        val targetWorkspaceMode = if (activeUser == null || preferredWorkspaceModeName == WorkspaceMode.SAMPLE_BUSINESS.name) {
            WorkspaceMode.SAMPLE_BUSINESS
        } else {
            WorkspaceMode.PRODUCTION
        }

        val vm: NiryatViewModel = viewModel(
            key = vmKey,
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return NiryatViewModel(
                        repository = if (activeUser != null) NiryatRepository(context.applicationContext) else null,
                        currentUserId = activeUser?.uid ?: SampleBusinessWorkspaceSeeder.SAMPLE_USER_ID,
                        initialMode = targetWorkspaceMode,
                        sampleLocalStore = SampleWorkspaceLocalStore(context.applicationContext)
                    ) as T
                }
            }
        )

        LaunchedEffect(targetWorkspaceMode) {
            if (vm.portfolioState.value.workspaceMode != targetWorkspaceMode) {
                vm.switchWorkspaceMode(targetWorkspaceMode)
            }
        }

        NiryatAuthenticatedShell(
            userEmail = activeUser?.email ?: SampleBusinessWorkspaceSeeder.defaultSampleBusinessProfile().contactEmail,
            isGuestSampleSession = activeUser == null && isExploringSampleWithoutAuth,
            niryatViewModel = vm,
            onSwitchWorkspaceContext = { mode ->
                if (mode == WorkspaceMode.PRODUCTION && activeUser == null) {
                    // Unauthenticated user requesting Production Workspace -> go to Google Sign-In screen
                    persistWorkspaceContext(
                        mode = WorkspaceMode.PRODUCTION,
                        sampleGuestActive = false
                    )
                } else {
                    persistWorkspaceContext(
                        mode = mode,
                        sampleGuestActive = (mode == WorkspaceMode.SAMPLE_BUSINESS && activeUser == null)
                    )
                    vm.switchWorkspaceMode(mode)
                }
            },
            onExitSampleToAuth = {
                persistWorkspaceContext(
                    mode = WorkspaceMode.PRODUCTION,
                    sampleGuestActive = false
                )
            },
            onSignOut = {
                if (activeUser != null) {
                    signOutNiryatUser(
                        credentialManager = credentialManager,
                        scope = scope,
                        onSignOutComplete = {
                            persistWorkspaceContext(
                                mode = WorkspaceMode.PRODUCTION,
                                sampleGuestActive = false
                            )
                            currentUser = null
                        }
                    )
                } else {
                    persistWorkspaceContext(
                        mode = WorkspaceMode.PRODUCTION,
                        sampleGuestActive = false
                    )
                }
            }
        )
    }
}

@Composable
fun NiryatAuthenticatedShell(
    userEmail: String,
    isGuestSampleSession: Boolean = false,
    niryatViewModel: NiryatViewModel,
    onSwitchWorkspaceContext: (WorkspaceMode) -> Unit = { niryatViewModel.switchWorkspaceMode(it) },
    onExitSampleToAuth: () -> Unit = {},
    onSignOut: () -> Unit
) {
    var currentRoute by rememberSaveable { mutableStateOf(NiryatRoutes.ROUTE_DASHBOARD) }

    val portfolio by niryatViewModel.portfolioState.collectAsStateWithLifecycle()
    val statusBanner by niryatViewModel.statusBanner.collectAsStateWithLifecycle()
    val extractionState by niryatViewModel.extractionState.collectAsStateWithLifecycle()
    val financialAiExplanation by niryatViewModel.financialAiExplanation.collectAsStateWithLifecycle()
    val portfolioAiInsights by niryatViewModel.portfolioAiInsights.collectAsStateWithLifecycle()
    val nlQueryState by niryatViewModel.nlQueryState.collectAsStateWithLifecycle()
    val paymentBusy by niryatViewModel.paymentBusy.collectAsStateWithLifecycle()
    val orderSubmitting by niryatViewModel.orderSubmitting.collectAsStateWithLifecycle()
    val paypalAuthCheckState by niryatViewModel.paypalAuthCheckState.collectAsStateWithLifecycle()

    // Top-level BackHandler for Android back navigation preservation
    BackHandler(enabled = currentRoute != NiryatRoutes.ROUTE_DASHBOARD || isGuestSampleSession) {
        if (currentRoute != NiryatRoutes.ROUTE_DASHBOARD) {
            currentRoute = NiryatRoutes.ROUTE_DASHBOARD
        } else if (isGuestSampleSession) {
            onExitSampleToAuth()
        }
    }

    val activePrimaryTabRoute = when (currentRoute) {
        NiryatRoutes.ROUTE_DASHBOARD -> NiryatRoutes.ROUTE_DASHBOARD
        NiryatRoutes.ROUTE_ORDERS, NiryatRoutes.ROUTE_NEW_ORDER, NiryatRoutes.ROUTE_DIGITAL_TWIN -> NiryatRoutes.ROUTE_ORDERS
        NiryatRoutes.ROUTE_FINANCIAL_INTELLIGENCE, NiryatRoutes.ROUTE_PAYPAL_PAYMENTS -> NiryatRoutes.ROUTE_FINANCIAL_INTELLIGENCE
        NiryatRoutes.ROUTE_DOCUMENT_CENTRE -> NiryatRoutes.ROUTE_DOCUMENT_CENTRE
        NiryatRoutes.ROUTE_MORE, "performance" -> NiryatRoutes.ROUTE_MORE
        else -> NiryatRoutes.ROUTE_DASHBOARD
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(IvoryBackground)
    ) {
        val isWideScreen = maxWidth >= 640.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = IvoryBackground,
            bottomBar = {
                if (!isWideScreen && currentRoute != NiryatRoutes.ROUTE_NEW_ORDER) {
                    AppBottomNavigation(
                        tabs = PRIMARY_NAV_TABS,
                        activeTabRoute = activePrimaryTabRoute,
                        onSelectTab = { route -> currentRoute = route }
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    AdaptiveMinimalNavigationRail(
                        tabs = PRIMARY_NAV_TABS,
                        activeTabRoute = activePrimaryTabRoute,
                        onSelectTab = { route -> currentRoute = route },
                        onNewOrder = { currentRoute = NiryatRoutes.ROUTE_NEW_ORDER }
                    )
                    VerticalDivider(color = WarmBorder, thickness = 1.dp)
                }

                Column(modifier = Modifier.weight(1f)) {
                    // Persistent "SAMPLE WORKSPACE" Indicator across all screens when in Sample Mode
                    if (portfolio.workspaceMode == WorkspaceMode.SAMPLE_BUSINESS) {
                        Surface(
                            color = SoftChampagneBg,
                            border = BorderStroke(1.dp, ChampagneAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 4.dp)
                                .testTag("global_sample_workspace_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SAMPLE WORKSPACE • ${portfolio.businessProfile.businessName}",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryInk,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Web SaaS",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LightGoldAccent,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { currentRoute = "web_saas_portal" }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .testTag("open_web_saas_portal_button")
                                    )
                                    Text(
                                        text = "Reset",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SecondaryInk,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { niryatViewModel.resetSampleWorkspace() }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .testTag("global_reset_sample_button")
                                    )
                                    Text(
                                        text = if (isGuestSampleSession) "Sign In" else "Production",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PrimaryInk,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { onSwitchWorkspaceContext(WorkspaceMode.PRODUCTION) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .testTag("global_exit_sample_button")
                                    )
                                }
                            }
                        }
                    }

                    // Subtle Status Banner if active
                    if (!statusBanner.isNullOrBlank()) {
                        Surface(
                            color = SoftChampagneBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = statusBanner.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PrimaryInk,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = SecondaryInk,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { niryatViewModel.clearBanner() }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = currentRoute,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                            },
                            label = "MainScreenTransition"
                        ) { targetRoute ->
                            when (targetRoute) {
                                NiryatRoutes.ROUTE_DASHBOARD -> {
                                    DashboardScreen(
                                        portfolio = portfolio,
                                        userEmail = userEmail,
                                        aiInsightsState = portfolioAiInsights,
                                        nlQueryState = nlQueryState,
                                        onSelectOrder = { niryatViewModel.selectOrder(it) },
                                        onNavigate = { route ->
                                            currentRoute = if (route == "performance") NiryatRoutes.ROUTE_MORE else route
                                        },
                                        onRequestAiInsights = { niryatViewModel.requestPortfolioInsights() },
                                        onAskNaturalLanguageQuery = { query ->
                                            niryatViewModel.askNaturalLanguageBusinessQuery(query)
                                        },
                                        onSwitchWorkspace = { mode ->
                                            onSwitchWorkspaceContext(mode)
                                        },
                                        onResetSampleWorkspace = {
                                            niryatViewModel.resetSampleWorkspace()
                                        }
                                    )
                                }

                                NiryatRoutes.ROUTE_ORDERS -> {
                                    OrdersHubScreen(
                                        portfolio = portfolio,
                                        onSelectOrder = { niryatViewModel.selectOrder(it) },
                                        onNavigate = { route ->
                                            currentRoute = if (route == "performance") NiryatRoutes.ROUTE_MORE else route
                                        },
                                        onUpdateOrderStatus = { ord, newStatus ->
                                            niryatViewModel.updateOrderStatus(ord, newStatus)
                                        },
                                        onDeleteOrder = { ord ->
                                            niryatViewModel.deleteExportOrder(ord)
                                        }
                                    )
                                }

                                NiryatRoutes.ROUTE_NEW_ORDER -> {
                                    NewExportOrderScreen(
                                        defaultOrderNumber = remember { niryatViewModel.generateOrderNumber() },
                                        extractionState = extractionState,
                                        isSubmitting = orderSubmitting,
                                        onRunAiExtraction = { text, bmp ->
                                            niryatViewModel.runAiOrderExtraction(text, bmp)
                                        },
                                        onResetExtraction = { niryatViewModel.resetExtractionDraft() },
                                        onSubmitOrder = { orderNo, expName, expContact, expAddr, buyName, buyCountry, buyContact, buyAddr, prodName, prodDesc, qty, unit, price, curr, incoterm, destPort, ordDate, shipDate, payTerms, advPct, source ->
                                            niryatViewModel.createExportOrder(
                                                orderNumber = orderNo,
                                                exporterName = expName,
                                                exporterContact = expContact,
                                                exporterAddress = expAddr,
                                                buyerName = buyName,
                                                buyerCountry = buyCountry,
                                                buyerContact = buyContact,
                                                buyerAddress = buyAddr,
                                                productName = prodName,
                                                productDescription = prodDesc,
                                                quantity = qty,
                                                unit = unit,
                                                unitPrice = price,
                                                currency = curr,
                                                incoterm = incoterm,
                                                destinationPort = destPort,
                                                orderDate = ordDate,
                                                expectedShipmentDate = shipDate,
                                                paymentTerms = payTerms,
                                                advancePercentage = advPct,
                                                extractionSource = source,
                                                onCreated = {
                                                    currentRoute = NiryatRoutes.ROUTE_DIGITAL_TWIN
                                                }
                                            )
                                        },
                                        onBack = { currentRoute = NiryatRoutes.ROUTE_DASHBOARD }
                                    )
                                }

                                NiryatRoutes.ROUTE_FINANCIAL_INTELLIGENCE -> {
                                    FinancialIntelligenceScreen(
                                        portfolio = portfolio,
                                        aiExplanationState = financialAiExplanation,
                                        onSelectOrder = { niryatViewModel.selectOrder(it) },
                                        onSaveAssessment = { ord, fx, adv, feePct, fixedFee, procInr, logInr, packInr, otherInr, note ->
                                            niryatViewModel.saveFinancialAssessment(
                                                order = ord,
                                                exchangeRate = fx,
                                                advancePercentage = adv,
                                                paymentFeePercentage = feePct,
                                                paymentFixedFee = fixedFee,
                                                procurementCostInr = procInr,
                                                logisticsCostInr = logInr,
                                                packagingCostInr = packInr,
                                                otherCostsInr = otherInr,
                                                assumptionsNote = note
                                            )
                                        },
                                        onRequestAiExplanation = { ord, assess ->
                                            niryatViewModel.requestFinancialExplanation(ord, assess)
                                        },
                                        onBack = { currentRoute = NiryatRoutes.ROUTE_DASHBOARD }
                                    )
                                }

                                NiryatRoutes.ROUTE_DOCUMENT_CENTRE -> {
                                    DocumentCentreScreen(
                                        portfolio = portfolio,
                                        onSelectOrder = { niryatViewModel.selectOrder(it) },
                                        onInitializeChecklist = { orderId ->
                                            niryatViewModel.initializeChecklistIfEmpty(orderId)
                                        },
                                        onUpdateDocument = { doc, reqLevel, st, fName, fRef, note ->
                                            niryatViewModel.updateDocumentItem(
                                                doc = doc,
                                                newRequirementLevel = reqLevel,
                                                newStatus = st,
                                                newFileName = fName,
                                                newFileReference = fRef,
                                                newReviewerNote = note
                                            )
                                        },
                                        onAddCustomDocument = { orderId, docType, reqLevel ->
                                            niryatViewModel.addCustomDocument(orderId, docType, reqLevel)
                                        },
                                        onBack = { currentRoute = NiryatRoutes.ROUTE_DASHBOARD }
                                    )
                                }

                                NiryatRoutes.ROUTE_PAYPAL_PAYMENTS -> {
                                    PayPalPaymentsScreen(
                                        portfolio = portfolio,
                                        isPayPalConfigured = niryatViewModel.isPayPalConfigured,
                                        isPaymentBusy = paymentBusy,
                                        paypalAuthCheckStatus = paypalAuthCheckState,
                                        onVerifyPayPalOAuth = { niryatViewModel.verifyPayPalSandboxConnection() },
                                        onSelectOrder = { niryatViewModel.selectOrder(it) },
                                        onCreatePayPalOrder = { ord, amt, payType ->
                                            niryatViewModel.createPayPalSandboxOrder(ord, amt, payType)
                                        },
                                        onCapturePayPalOrder = { payRec ->
                                            niryatViewModel.capturePayPalSandboxOrder(payRec)
                                        },
                                        onReconcilePayPalOrder = { payRec ->
                                            niryatViewModel.reconcilePayPalOrderStatus(payRec)
                                        },
                                        onFailDemoPayment = { payRec ->
                                            niryatViewModel.failDemoPayment(payRec)
                                        },
                                        onCancelDemoPayment = { payRec ->
                                            niryatViewModel.cancelDemoPayment(payRec)
                                        },
                                        onBack = { currentRoute = NiryatRoutes.ROUTE_DASHBOARD }
                                    )
                                }

                                NiryatRoutes.ROUTE_DIGITAL_TWIN -> {
                                    ExportOrderDigitalTwinScreen(
                                        portfolio = portfolio,
                                        onSelectOrder = { niryatViewModel.selectOrder(it) },
                                        onNavigate = { route ->
                                            currentRoute = if (route == "performance") NiryatRoutes.ROUTE_MORE else route
                                        },
                                        onBack = { currentRoute = NiryatRoutes.ROUTE_ORDERS }
                                    )
                                }

                                "web_saas_portal" -> {
                                    WebSaasPortalScreen(
                                        onBackToNative = { currentRoute = NiryatRoutes.ROUTE_DASHBOARD }
                                    )
                                }

                                else -> {
                                    ProfileAndMoreScreen(
                                        portfolio = portfolio,
                                        userEmail = userEmail,
                                        isPayPalConfigured = niryatViewModel.isPayPalConfigured,
                                        paypalAuthCheckStatus = paypalAuthCheckState,
                                        aiInsightsState = portfolioAiInsights,
                                        onVerifyPayPalOAuth = { niryatViewModel.verifyPayPalSandboxConnection() },
                                        onRequestAiInsights = { niryatViewModel.requestPortfolioInsights() },
                                        onSwitchWorkspace = { mode ->
                                            onSwitchWorkspaceContext(mode)
                                        },
                                        onResetSampleWorkspace = {
                                            niryatViewModel.resetSampleWorkspace()
                                        },
                                        onUpdateBusinessProfile = { name, type, loc, email, cat, cur ->
                                            niryatViewModel.updateBusinessProfile(name, type, loc, email, cat, cur)
                                        },
                                        onExportReportCsv = { reportType ->
                                            niryatViewModel.generateBusinessReportCsv(reportType)
                                        },
                                        onNavigate = { route ->
                                            currentRoute = if (route == "performance") NiryatRoutes.ROUTE_MORE else route
                                        },
                                        onSignOut = onSignOut
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdaptiveMinimalNavigationRail(
    tabs: List<PrimaryNavTab>,
    activeTabRoute: String,
    onSelectTab: (String) -> Unit,
    onNewOrder: () -> Unit
) {
    NavigationRail(
        containerColor = PureWhiteSurface,
        contentColor = PrimaryInk,
        header = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SoftChampagneBg,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "NF",
                            style = MaterialTheme.typography.labelLarge,
                            color = LightGoldAccent,
                            fontFamily = SpaceGroteskFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                FloatingActionButton(
                    onClick = onNewOrder,
                    containerColor = ChampagneAccent,
                    contentColor = OnGoldDarkInk,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("rail_new_order_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Export Order",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxHeight()
            .width(84.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        tabs.forEach { tab ->
            val selected = tab.route == activeTabRoute
            NavigationRailItem(
                selected = selected,
                onClick = { onSelectTab(tab.route) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = LightGoldAccent,
                    selectedTextColor = PrimaryInk,
                    indicatorColor = SoftChampagneBg,
                    unselectedIconColor = SecondaryInk,
                    unselectedTextColor = SecondaryInk
                ),
                modifier = Modifier.testTag("rail_${tab.route}")
            )
        }
    }
}

@Composable
private fun WebSaasPortalScreen(
    onBackToNative: () -> Unit
) {
    BackHandler { onBackToNative() }
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = PrimaryInk,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NIRYATFLOW AI • REACT + TYPESCRIPT + VITE WEB SAAS",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ChampagneAccent
                )
                Text(
                    text = "← Native Android UI",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PureWhiteSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onBackToNative() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("back_to_native_ui_button")
                )
            }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("react_web_saas_webview"),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    settings.allowUniversalAccessFromFileURLs = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    webViewClient = WebViewClient()
                    loadUrl("file:///android_asset/web/index.html")
                }
            }
        )
    }
}

