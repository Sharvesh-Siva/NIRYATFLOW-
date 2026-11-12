package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics. semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.ui.theme.ChampagneAccent
import com.example.ui.theme.CriticalCrimsonBg
import com.example.ui.theme.CriticalCrimsonTone
import com.example.ui.theme.IvoryBackground
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MutedInk
import com.example.ui.theme.OnGoldDarkInk
import com.example.ui.theme.PositiveEmerald
import com.example.ui.theme.PrimaryInk
import com.example.ui.theme.PureWhiteSurface
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.SoftChampagneBg
import com.example.ui.theme.TradeBlueAccent
import com.example.ui.theme.WarmBorder
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Restrained Cinematic Trade-Route Launch Experience.
 * The existing NiryatFlow AI emblem at the top is interactive: tapping the emblem (or the subtle
 * "Explore Sample Business" label beneath it) navigates directly to the Sample Business Workspace
 * (Tamil Nadu Global Exports Pvt. Ltd., Chennai) with a subtle scale + ripple transition.
 */
@Composable
fun CinematicLaunchScreen(
    onLaunchComplete: () -> Unit,
    onEnterSampleWorkspace: () -> Unit = {}
) {
    var stage by remember { mutableStateOf(0) }
    var emblemTapped by remember { mutableStateOf(false) }
    val emblemInteractionSource = remember { MutableInteractionSource() }
    val isEmblemPressed by emblemInteractionSource.collectIsPressedAsState()
    val emblemScale by animateFloatAsState(
        targetValue = when {
            emblemTapped -> 0.94f
            isEmblemPressed -> 0.96f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "launch_emblem_scale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "trade_routes")
    val routeProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "route_progress"
    )
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_radius"
    )

    LaunchedEffect(emblemTapped) {
        if (emblemTapped) {
            delay(110L)
            onEnterSampleWorkspace()
        } else {
            stage = 1
            delay(2200L)
            if (!emblemTapped) {
                onLaunchComplete()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IvoryBackground)
            .padding(24.dp)
    ) {
        // Subtle Minimalist Trade Corridors Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("trade_route_canvas")
        ) {
            val w = size.width
            val h = size.height
            val indiaHub = Offset(w * 0.50f, h * 0.44f)
            val destinations = listOf(
                Offset(w * 0.16f, h * 0.28f), // Europe
                Offset(w * 0.34f, h * 0.38f), // Middle East
                Offset(w * 0.84f, h * 0.32f), // East Asia
                Offset(w * 0.16f, h * 0.56f), // Americas
                Offset(w * 0.82f, h * 0.58f)  // Oceania
            )

            // Delicate architectural grid lines
            for (i in 1..5) {
                val y = h * (i / 6f)
                drawLine(
                    color = WarmBorder,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
            }

            destinations.forEachIndexed { idx, dest ->
                val control = Offset(
                    x = (indiaHub.x + dest.x) / 2f,
                    y = minOf(indiaHub.y, dest.y) - (h * 0.10f)
                )
                val path = Path().apply {
                    moveTo(indiaHub.x, indiaHub.y)
                    quadraticTo(control.x, control.y, dest.x, dest.y)
                }
                drawPath(
                    path = path,
                    color = if (idx % 2 == 0) ChampagneAccent.copy(alpha = 0.45f) else PositiveEmerald.copy(alpha = 0.35f),
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                )

                val t = (routeProgress + idx * 0.21f) % 1f
                val oneMinusT = 1f - t
                val px = oneMinusT * oneMinusT * indiaHub.x +
                        2f * oneMinusT * t * control.x +
                        t * t * dest.x
                val py = oneMinusT * oneMinusT * indiaHub.y +
                        2f * oneMinusT * t * control.y +
                        t * t * dest.y

                drawCircle(
                    color = if (idx % 2 == 0) ChampagneAccent else PositiveEmerald,
                    radius = 5.5f,
                    center = Offset(px, py)
                )
                drawCircle(
                    color = PrimaryInk,
                    radius = 4f,
                    center = dest
                )
            }

            drawCircle(
                color = ChampagneAccent.copy(alpha = 0.25f),
                radius = 24f * pulseRadius,
                center = indiaHub
            )
            drawCircle(
                color = TradeBlueAccent,
                radius = 9f,
                center = indiaHub
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Interactive Launch Emblem + Subtle "Explore Sample Business" Label
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .scale(emblemScale)
                        .clip(RoundedCornerShape(50))
                        .background(PureWhiteSurface)
                        .border(1.dp, WarmBorder, RoundedCornerShape(50))
                        .clickable(
                            interactionSource = emblemInteractionSource,
                            indication = ripple(bounded = true, color = ChampagneAccent),
                            role = Role.Button,
                            onClick = { emblemTapped = true }
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                        .semantics {
                            contentDescription = "NiryatFlow AI Emblem - Tap to Explore Sample Business Workspace"
                        }
                        .testTag("niryat_emblem_sample_entry")
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ChampagneAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NiryatFlow AI",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryInk
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Explore Sample Business",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = SecondaryInk,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            role = Role.Button,
                            onClick = { emblemTapped = true }
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("launch_explore_sample_label")
                )
            }

            AnimatedVisibility(
                visible = stage >= 1,
                enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 4 }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = "Export Operations &\nFinancial Intelligence",
                        style = MaterialTheme.typography.displayLarge,
                        color = PrimaryInk,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Connected Digital Twin • Deterministic INR Viability • PayPal Sandbox",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryInk,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Button(
                onClick = onLaunchComplete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ChampagneAccent,
                    contentColor = OnGoldDarkInk
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("skip_launch_button")
            ) {
                Text(
                    text = "Continue",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Continue",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Silent Auto-Sign-In helper using GetGoogleIdOption in its own request.
 */
fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: Exception) {
            onUnauthenticated()
        }
    }
}

/**
 * Interactive Sign-In using GetSignInWithGoogleOption exclusively.
 */
fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context as Activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign in failed")
        }
    }
}

fun signOutNiryatUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

/**
 * Minimalist Warm-Ivory Onboarding Sign-In Screen:
 * - Preserves the NiryatFlow AI emblem/logo at the top of the screen and makes it directly clickable
 *   with a subtle ripple + scale transition and a subtle "Explore Sample Business" label near the emblem.
 * - Tapping the emblem or the subtle label opens the Sample Business Workspace
 *   (Tamil Nadu Global Exports Pvt. Ltd., Chennai) immediately without redirecting to Google Sign-In.
 * - Maintains full Google Sign-In via Jetpack CredentialManager for the Production Workspace.
 */
@Composable
fun NiryatAuthScreen(
    onAuthSuccess: () -> Unit,
    onExploreSampleWorkspace: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sampleTransitionTriggered by remember { mutableStateOf(false) }

    val emblemInteractionSource = remember { MutableInteractionSource() }
    val isEmblemPressed by emblemInteractionSource.collectIsPressedAsState()
    val emblemScale by animateFloatAsState(
        targetValue = when {
            sampleTransitionTriggered -> 0.94f
            isEmblemPressed -> 0.96f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "auth_emblem_scale"
    )

    LaunchedEffect(sampleTransitionTriggered) {
        if (sampleTransitionTriggered) {
            delay(110L)
            onExploreSampleWorkspace()
        }
    }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                if (!sampleTransitionTriggered) {
                    onAuthSuccess()
                }
            },
            onUnauthenticated = {},
            scope = scope
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IvoryBackground)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Brand wordmark header with interactive NiryatFlow AI emblem + subtle "Explore Sample Business" label
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .scale(emblemScale)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = emblemInteractionSource,
                        indication = ripple(bounded = true, color = ChampagneAccent),
                        role = Role.Button,
                        onClick = { sampleTransitionTriggered = true }
                    )
                    .padding(vertical = 4.dp, horizontal = 4.dp)
                    .semantics {
                        contentDescription = "NiryatFlow AI Emblem - Tap to Explore Sample Business Workspace"
                    }
                    .testTag("auth_emblem_sample_entry")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SoftChampagneBg)
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "NiryatFlow AI Emblem",
                        tint = ChampagneAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "NiryatFlow AI",
                    style = MaterialTheme.typography.titleMedium,
                    color = PrimaryInk
                )
            }

            Text(
                text = "Explore Sample Business",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                color = SecondaryInk,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        role = Role.Button,
                        onClick = { sampleTransitionTriggered = true }
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("explore_sample_business_button")
            )
        }

        // Main editorial headline & floating preview card
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Connect Your\nExport System",
                style = MaterialTheme.typography.displayLarge,
                color = PrimaryInk
            )
            Text(
                text = "Transform every international purchase order into a connected Digital Twin with deterministic INR finance, document readiness, and PayPal Sandbox collection.",
                style = MaterialTheme.typography.bodyLarge,
                color = SecondaryInk
            )

            WarmSurfaceCard(
                backgroundColor = PureWhiteSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Real-Time Lifecycle",
                            style = MaterialTheme.typography.labelLarge,
                            color = SecondaryInk
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Export Digital Twin",
                            style = MaterialTheme.typography.titleLarge,
                            color = PrimaryInk
                        )
                    }
                    StatusPill(
                        label = "8 Connected Stages",
                        tone = FintechTone.POSITIVE
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                RulerTickVisual(progressFraction = 0.72f)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Order Intake", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = MutedInk)
                    Text("INR Viability", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = MutedInk)
                    Text("PayPal Capture", fontFamily = JetBrainsMonoFamily, fontSize = 11.sp, color = PositiveEmerald)
                }
            }

            if (errorMessage != null) {
                Surface(
                    color = CriticalCrimsonBg,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CriticalCrimsonTone,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        // Bottom action: Production Google Sign-In
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { msg ->
                            isLoading = false
                            errorMessage = msg
                        },
                        scope = scope,
                        onAuthCancelled = {
                            isLoading = false
                        }
                    )
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ChampagneAccent,
                    contentColor = OnGoldDarkInk
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = OnGoldDarkInk,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Sign in with Google",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
