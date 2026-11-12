package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AIExtractedOrderDraft
import com.example.domain.FinancialCalculationEngine
import com.example.ui.components.FintechTone
import com.example.ui.components.RulerTickVisual
import com.example.ui.components.StatusPill
import com.example.ui.components.WarmSurfaceCard
import com.example.ui.theme.ChampagneAccent
import com.example.ui.theme.CriticalCrimsonBg
import com.example.ui.theme.CriticalCrimsonTone
import com.example.ui.theme.IvoryBackground
import com.example.ui.theme.IvorySecondaryBg
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PositiveEmerald
import com.example.ui.theme.PrimaryInk
import com.example.ui.theme.PureWhiteSurface
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.SoftChampagneBg
import com.example.ui.theme.SpaceGroteskFamily
import com.example.ui.theme.WarmBorder
import com.example.ui.theme.WarningAmberBg
import com.example.ui.theme.WarningAmberTone
import com.example.ui.viewmodel.AiTaskState

private val ONBOARDING_STEPS = listOf(
    "1. Buyer Info",
    "2. Product Info",
    "3. Quantity & Pricing",
    "4. Currency & Payment Terms",
    "5. Shipping Info",
    "6. Review & Confirm"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewExportOrderScreen(
    defaultOrderNumber: String,
    extractionState: AiTaskState<AIExtractedOrderDraft>,
    isSubmitting: Boolean,
    onRunAiExtraction: (String, Bitmap?) -> Unit,
    onResetExtraction: () -> Unit,
    onSubmitOrder: (
        orderNumber: String,
        exporterName: String,
        exporterContact: String,
        exporterAddress: String,
        buyerName: String,
        buyerCountry: String,
        buyerContact: String,
        buyerAddress: String,
        productName: String,
        productDescription: String,
        quantity: Double,
        unit: String,
        unitPrice: Double,
        currency: String,
        incoterm: String,
        destinationPort: String,
        orderDate: String,
        expectedShipmentDate: String,
        paymentTerms: String,
        advancePercentage: Double,
        extractionSource: String
    ) -> Unit,
    onBack: () -> Unit
) {
    BackHandler {
        if (extractionState !is AiTaskState.Idle) onResetExtraction()
        onBack()
    }

    val context = LocalContext.current
    var isAiAssisted by rememberSaveable { mutableStateOf(false) }
    var currentStep by rememberSaveable { mutableIntStateOf(0) }

    var rawPoInput by rememberSaveable {
        mutableStateOf(
            """
            PURCHASE ORDER #DE-2026-8841
            Exporter: Niryat Global Precision Exports Pvt. Ltd., Pune, India (exports@niryatglobal.in)
            Buyer: RheinMetallurg GmbH, Hamburg, Germany (procurement@rheinmetallurg.de)
            Destination Port: Hamburg (DEHAM) | Incoterm: CIF
            Commodity: CNC Forged Stainless Steel Flanges AISI 316L
            Quantity: 3,200 PCS @ EUR 26.50 / PC
            Order Date: 2026-05-10 | Expected Shipment Date: 2026-06-15
            Payment Terms: 35% Advance via PayPal Sandbox, 65% against Bill of Lading
            """.trimIndent()
        )
    }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var attachedImageLabel by rememberSaveable { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    attachedBitmap = BitmapFactory.decodeStream(stream)
                    attachedImageLabel = "PO Document Scan Attached"
                }
            }
        }
    }

    // Form fields
    var orderNumber by rememberSaveable { mutableStateOf(defaultOrderNumber) }
    var exporterName by rememberSaveable { mutableStateOf("Niryat Global Exports Pvt. Ltd.") }
    var exporterContact by rememberSaveable { mutableStateOf("exports@niryatglobal.in") }
    var exporterAddress by rememberSaveable { mutableStateOf("MIDC Chakan, Pune, Maharashtra, India") }
    var buyerName by rememberSaveable { mutableStateOf("RheinMetallurg GmbH") }
    var buyerCountry by rememberSaveable { mutableStateOf("Germany") }
    var buyerContact by rememberSaveable { mutableStateOf("procurement@rheinmetallurg.de") }
    var buyerAddress by rememberSaveable { mutableStateOf("HafenCity 42, 20457 Hamburg, Germany") }
    var productName by rememberSaveable { mutableStateOf("CNC Forged Stainless Steel Flanges") }
    var productDescription by rememberSaveable { mutableStateOf("AISI 316L Forged Raised Face Flanges EN 1092-1") }
    var quantityText by rememberSaveable { mutableStateOf("3200") }
    var unit by rememberSaveable { mutableStateOf("PCS") }
    var unitPriceText by rememberSaveable { mutableStateOf("26.50") }
    var currency by rememberSaveable { mutableStateOf("EUR") }
    var incoterm by rememberSaveable { mutableStateOf("CIF") }
    var destinationPort by rememberSaveable { mutableStateOf("Hamburg (DEHAM)") }
    var orderDate by rememberSaveable { mutableStateOf("2026-05-10") }
    var expectedShipmentDate by rememberSaveable { mutableStateOf("2026-06-15") }
    var paymentTerms by rememberSaveable { mutableStateOf("35% Advance via PayPal Sandbox, 65% BL CAD") }
    var advancePercentageText by rememberSaveable { mutableStateOf("35") }
    var extractionSource by rememberSaveable { mutableStateOf("MANUAL") }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    val extractedDraft = (extractionState as? AiTaskState.Success)?.data

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IvoryBackground),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .testTag("new_export_order_list"),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Minimal Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("new_order_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = PrimaryInk
                            )
                        }
                        Column {
                            Text(
                                text = "NEW EXPORT ORDER",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChampagneAccent,
                                fontFamily = JetBrainsMonoFamily
                            )
                            Text(
                                text = "Guided Order Onboarding",
                                style = MaterialTheme.typography.headlineMedium,
                                color = PrimaryInk,
                                fontFamily = SpaceGroteskFamily,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    StatusPill(
                        label = if (isAiAssisted) "AI-Assisted" else "Manual Flow",
                        tone = if (isAiAssisted) FintechTone.ACCENT_GOLD else FintechTone.NEUTRAL
                    )
                }
            }

            // Segmented Toggle: Guided Manual vs Gemini AI Extraction
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = IvorySecondaryBg,
                    border = BorderStroke(1.dp, WarmBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (!isAiAssisted) PureWhiteSurface else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { isAiAssisted = false }
                                .testTag("mode_manual_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = null,
                                    tint = if (!isAiAssisted) PrimaryInk else SecondaryInk,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Guided Entry",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (!isAiAssisted) PrimaryInk else SecondaryInk,
                                    fontWeight = if (!isAiAssisted) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isAiAssisted) PureWhiteSurface else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { isAiAssisted = true }
                                .testTag("mode_ai_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (isAiAssisted) ChampagneAccent else SecondaryInk,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AI PO Extraction",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isAiAssisted) PrimaryInk else SecondaryInk,
                                    fontWeight = if (isAiAssisted) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // AI-Assisted PO Extraction Panel
            item {
                AnimatedVisibility(
                    visible = isAiAssisted,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    WarmSurfaceCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = PureWhiteSurface,
                        borderColor = ChampagneAccent.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "GEMINI MULTIMODAL PO PARSER",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ChampagneAccent,
                                    fontFamily = JetBrainsMonoFamily
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Extract Structured Fields from Buyer PO",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PrimaryInk
                                )
                            }
                            StatusPill(
                                label = "Human Review Required",
                                tone = FintechTone.ACCENT_GOLD
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    rawPoInput = """
                                        PURCHASE ORDER #DE-2026-8841
                                        Buyer: RheinMetallurg GmbH, Hamburg, Germany (procurement@rheinmetallurg.de)
                                        Destination Port: Hamburg (DEHAM) | Incoterm: CIF
                                        Product: CNC Forged Stainless Steel Flanges AISI 316L
                                        Quantity: 3,200 PCS @ EUR 26.50 / PC
                                        Order Date: 2026-05-10 | Expected Shipment Date: 2026-06-15
                                        Payment Terms: 35% Advance via PayPal Sandbox, 65% against Bill of Lading
                                    """.trimIndent()
                                },
                                label = { Text("Sample German Engineering PO", style = MaterialTheme.typography.labelSmall) }
                            )
                            FilterChip(
                                selected = false,
                                onClick = {
                                    rawPoInput = """
                                        PO REF: US-2026-4410 | Buyer: Pacific Apparel Co, Los Angeles, USA (imports@pacificapparel.us)
                                        Product: Organic GOTS Combed Cotton Men's Polo Shirts
                                        Qty: 12,000 PCS @ USD 8.40 / PC | Currency: USD | Incoterm: FOB
                                        Payment Terms: 40% Advance via PayPal, 60% Sight LC
                                        Note: Destination port and shipment date missing on buyer email.
                                    """.trimIndent()
                                },
                                label = { Text("Sample US Apparel PO (With Missing Fields)", style = MaterialTheme.typography.labelSmall) }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        MinimalFormTextField(
                            value = rawPoInput,
                            onValueChange = { rawPoInput = it },
                            label = "Buyer PO Text / Email Thread",
                            minLines = 4,
                            testTag = "raw_po_input_field"
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, WarmBorder),
                                modifier = Modifier.testTag("attach_po_image_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Attach PO Scan",
                                    modifier = Modifier.size(16.dp),
                                    tint = PrimaryInk
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (attachedImageLabel.isNotBlank()) "Scan Attached" else "Attach Scan",
                                    color = PrimaryInk,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            Button(
                                onClick = { onRunAiExtraction(rawPoInput, attachedBitmap) },
                                enabled = extractionState !is AiTaskState.Loading && rawPoInput.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryInk,
                                    contentColor = PureWhiteSurface
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("run_ai_po_extraction_button")
                            ) {
                                if (extractionState is AiTaskState.Loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = PureWhiteSurface
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Extracting PO...")
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = ChampagneAccent
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Extract & Populate Steps")
                                }
                            }
                        }

                        if (extractionState is AiTaskState.Error) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = extractionState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = CriticalCrimsonTone
                            )
                        }

                        if (extractedDraft != null) {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = WarmBorder)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = PositiveEmerald,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Confidence: ${extractedDraft.confidenceScore}%",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = PrimaryInk,
                                        fontFamily = JetBrainsMonoFamily
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (extractedDraft.exporterName.isNotBlank()) exporterName = extractedDraft.exporterName
                                        if (extractedDraft.exporterContact.isNotBlank()) exporterContact = extractedDraft.exporterContact
                                        if (extractedDraft.exporterAddress.isNotBlank()) exporterAddress = extractedDraft.exporterAddress
                                        if (extractedDraft.buyerName.isNotBlank()) buyerName = extractedDraft.buyerName
                                        if (extractedDraft.buyerCountry.isNotBlank()) buyerCountry = extractedDraft.buyerCountry
                                        if (extractedDraft.buyerContact.isNotBlank()) buyerContact = extractedDraft.buyerContact
                                        if (extractedDraft.buyerAddress.isNotBlank()) buyerAddress = extractedDraft.buyerAddress
                                        if (extractedDraft.productName.isNotBlank()) productName = extractedDraft.productName
                                        if (extractedDraft.productDescription.isNotBlank()) productDescription = extractedDraft.productDescription
                                        if (extractedDraft.quantity > 0) quantityText = extractedDraft.quantity.toString()
                                        if (extractedDraft.unit.isNotBlank()) unit = extractedDraft.unit
                                        if (extractedDraft.unitPrice > 0) unitPriceText = extractedDraft.unitPrice.toString()
                                        if (extractedDraft.currency.isNotBlank()) currency = extractedDraft.currency.uppercase()
                                        if (extractedDraft.incoterm.isNotBlank()) incoterm = extractedDraft.incoterm.uppercase()
                                        if (extractedDraft.destinationPort.isNotBlank()) destinationPort = extractedDraft.destinationPort
                                        if (extractedDraft.orderDate.isNotBlank()) orderDate = extractedDraft.orderDate
                                        if (extractedDraft.expectedShipmentDate.isNotBlank()) expectedShipmentDate = extractedDraft.expectedShipmentDate
                                        if (extractedDraft.paymentTerms.isNotBlank()) paymentTerms = extractedDraft.paymentTerms
                                        if (extractedDraft.advancePercentage > 0) advancePercentageText = extractedDraft.advancePercentage.toInt().toString()
                                        extractionSource = "AI_EXTRACTED_CONFIRMED"
                                        currentStep = 5
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PositiveEmerald,
                                        contentColor = PureWhiteSurface
                                    ),
                                    modifier = Modifier.testTag("apply_ai_extracted_fields_button")
                                ) {
                                    Text("Apply & Review", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            if (extractedDraft.aiExtractionNotes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = extractedDraft.aiExtractionNotes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryInk
                                )
                            }

                            if (extractedDraft.missingFields.isNotEmpty() ||
                                extractedDraft.uncertainFields.isNotEmpty() ||
                                extractedDraft.conflictsDetected.isNotEmpty()
                            ) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = WarningAmberBg,
                                    border = BorderStroke(1.dp, WarningAmberTone.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (extractedDraft.missingFields.isNotEmpty()) {
                                            Text(
                                                text = "Missing Fields: ${extractedDraft.missingFields.joinToString(", ")}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = WarningAmberTone
                                            )
                                        }
                                        if (extractedDraft.uncertainFields.isNotEmpty()) {
                                            Text(
                                                text = "Low Confidence: ${extractedDraft.uncertainFields.joinToString(", ")}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = WarningAmberTone
                                            )
                                        }
                                        if (extractedDraft.conflictsDetected.isNotEmpty()) {
                                            Text(
                                                text = "Conflicts: ${extractedDraft.conflictsDetected.joinToString(", ")}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = CriticalCrimsonTone
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Step Progress Indicator Bar
            item {
                WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ONBOARDING_STEPS[currentStep],
                            style = MaterialTheme.typography.titleMedium,
                            color = PrimaryInk,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "STEP ${currentStep + 1} OF ${ONBOARDING_STEPS.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChampagneAccent,
                            fontFamily = JetBrainsMonoFamily
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { (currentStep + 1).toFloat() / ONBOARDING_STEPS.size.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = ChampagneAccent,
                        trackColor = IvorySecondaryBg
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ONBOARDING_STEPS.forEachIndexed { index, stepTitle ->
                            val isCurrent = index == currentStep
                            val isCompleted = index < currentStep
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when {
                                    isCurrent -> PrimaryInk
                                    isCompleted -> SoftChampagneBg
                                    else -> IvorySecondaryBg
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { currentStep = index }
                                    .testTag("onboarding_step_chip_$index")
                            ) {
                                Text(
                                    text = stepTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        isCurrent -> PureWhiteSurface
                                        isCompleted -> PrimaryInk
                                        else -> SecondaryInk
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Progressive Step Content
            item {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "OnboardingStepContent"
                ) { step ->
                    WarmSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        when (step) {
                            0 -> {
                                // Step 1: Buyer & Exporter Info
                                Text(
                                    text = "COUNTERPARTY & EXPORTER DETAILS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondaryInk,
                                    fontFamily = JetBrainsMonoFamily
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    MinimalFormTextField(
                                        value = orderNumber,
                                        onValueChange = { orderNumber = it },
                                        label = "Order Reference #",
                                        isMonospace = true,
                                        modifier = Modifier.weight(1f),
                                        testTag = "input_order_number"
                                    )
                                    MinimalFormTextField(
                                        value = buyerCountry,
                                        onValueChange = { buyerCountry = it },
                                        label = "Buyer Country *",
                                        modifier = Modifier.weight(1f),
                                        testTag = "input_buyer_country"
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                MinimalFormTextField(
                                    value = buyerName,
                                    onValueChange = { buyerName = it },
                                    label = "Buyer Company / Counterparty Name *",
                                    testTag = "input_buyer_name"
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    MinimalFormTextField(
                                        value = buyerContact,
                                        onValueChange = { buyerContact = it },
                                        label = "Buyer Email / Contact",
                                        keyboardType = KeyboardType.Email,
                                        modifier = Modifier.weight(1f)
                                    )
                                    MinimalFormTextField(
                                        value = exporterName,
                                        onValueChange = { exporterName = it },
                                        label = "Exporter Entity",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            1 -> {
                                // Step 2: Product Info
                                Text(
                                    text = "EXPORT COMMODITY SPECIFICATION",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondaryInk,
                                    fontFamily = JetBrainsMonoFamily
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                MinimalFormTextField(
                                    value = productName,
                                    onValueChange = { productName = it },
                                    label = "Product Name / Title *",
                                    testTag = "input_product_name"
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                MinimalFormTextField(
                                    value = productDescription,
                                    onValueChange = { productDescription = it },
                                    label = "Technical Specification & Grade",
                                    minLines = 3
                                )
                            }

                            2 -> {
                                // Step 3: Quantity & Pricing with Ruler-Tick Visualizer
                                val qtyVal = quantityText.toDoubleOrNull() ?: 0.0
                                val priceVal = unitPriceText.toDoubleOrNull() ?: 0.0
                                val totalForeign = FinancialCalculationEngine.calculateGrossOrderValue(qtyVal, priceVal)
                                val fx = FinancialCalculationEngine.defaultExchangeRateForCurrency(currency)
                                val totalInr = totalForeign * fx

                                Text(
                                    text = "QUANTITY & UNIT PRICING",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondaryInk,
                                    fontFamily = JetBrainsMonoFamily
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    MinimalFormTextField(
                                        value = quantityText,
                                        onValueChange = { quantityText = it },
                                        label = "Quantity *",
                                        keyboardType = KeyboardType.Decimal,
                                        isMonospace = true,
                                        modifier = Modifier.weight(1f),
                                        testTag = "input_quantity"
                                    )
                                    MinimalFormTextField(
                                        value = unit,
                                        onValueChange = { unit = it },
                                        label = "Unit (PCS/KG)",
                                        isMonospace = true,
                                        modifier = Modifier.weight(0.7f)
                                    )
                                    MinimalFormTextField(
                                        value = unitPriceText,
                                        onValueChange = { unitPriceText = it },
                                        label = "Unit Price ($currency) *",
                                        keyboardType = KeyboardType.Decimal,
                                        isMonospace = true,
                                        modifier = Modifier.weight(1f),
                                        testTag = "input_unit_price"
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = IvoryBackground,
                                    border = BorderStroke(1.dp, WarmBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Bottom
                                        ) {
                                            Text(
                                                text = "$currency ${String.format("%,.2f", totalForeign)}",
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = PrimaryInk,
                                                fontFamily = SpaceGroteskFamily,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "≈ ₹${FinancialCalculationEngine.formatInrShort(totalInr)}",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = ChampagneAccent,
                                                fontFamily = JetBrainsMonoFamily
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        RulerTickVisual(
                                            progressFraction = (totalInr / 12_000_000.0).toFloat().coerceIn(0.12f, 0.92f)
                                        )
                                    }
                                }
                            }

                            3 -> {
                                // Step 4: Currency & Payment Terms
                                Text(
                                    text = "CURRENCY & SETTLEMENT TERMS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondaryInk,
                                    fontFamily = JetBrainsMonoFamily
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("EUR", "USD", "GBP", "AED").forEach { curr ->
                                        FilterChip(
                                            selected = currency == curr,
                                            onClick = { currency = curr },
                                            label = { Text(curr, fontFamily = JetBrainsMonoFamily) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryInk,
                                                selectedLabelColor = PureWhiteSurface
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    MinimalFormTextField(
                                        value = advancePercentageText,
                                        onValueChange = { advancePercentageText = it },
                                        label = "Advance Payment %",
                                        keyboardType = KeyboardType.Number,
                                        isMonospace = true,
                                        modifier = Modifier.weight(0.8f)
                                    )
                                    MinimalFormTextField(
                                        value = paymentTerms,
                                        onValueChange = { paymentTerms = it },
                                        label = "Payment Terms Narrative",
                                        modifier = Modifier.weight(1.5f)
                                    )
                                }
                            }

                            4 -> {
                                // Step 5: Shipping Info
                                Text(
                                    text = "INCOTERM, PORT & DISPATCH SCHEDULE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondaryInk,
                                    fontFamily = JetBrainsMonoFamily
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("FOB", "CIF", "CFR", "DAP", "EXW").forEach { term ->
                                        FilterChip(
                                            selected = incoterm == term,
                                            onClick = { incoterm = term },
                                            label = { Text(term, fontFamily = JetBrainsMonoFamily) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PrimaryInk,
                                                selectedLabelColor = PureWhiteSurface
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                MinimalFormTextField(
                                    value = destinationPort,
                                    onValueChange = { destinationPort = it },
                                    label = "Destination Port *"
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    MinimalFormTextField(
                                        value = orderDate,
                                        onValueChange = { orderDate = it },
                                        label = "Order Date (YYYY-MM-DD)",
                                        isMonospace = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    MinimalFormTextField(
                                        value = expectedShipmentDate,
                                        onValueChange = { expectedShipmentDate = it },
                                        label = "Shipment Date (YYYY-MM-DD)",
                                        isMonospace = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            else -> {
                                // Step 6: Review & Confirm
                                val qtyVal = quantityText.toDoubleOrNull() ?: 0.0
                                val priceVal = unitPriceText.toDoubleOrNull() ?: 0.0
                                val totalForeign = FinancialCalculationEngine.calculateGrossOrderValue(qtyVal, priceVal)

                                Text(
                                    text = "REVIEW & CONFIRM EXPORT ORDER",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ChampagneAccent,
                                    fontFamily = JetBrainsMonoFamily
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = IvoryBackground,
                                    border = BorderStroke(1.dp, WarmBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SummaryRow("Order Reference", orderNumber)
                                        SummaryRow("Buyer", "$buyerName ($buyerCountry)")
                                        SummaryRow("Product", productName)
                                        SummaryRow("Quantity & Price", "$quantityText $unit @ $currency $unitPriceText")
                                        SummaryRow("Gross Contract Value", "$currency ${String.format("%,.2f", totalForeign)}")
                                        SummaryRow("Incoterm & Destination", "$incoterm • $destinationPort")
                                        SummaryRow("Payment Terms", "$paymentTerms ($advancePercentageText% Advance)")
                                        SummaryRow("Source", extractionSource)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (validationError != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CriticalCrimsonBg,
                        border = BorderStroke(1.dp, CriticalCrimsonTone.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = CriticalCrimsonTone,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = validationError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = CriticalCrimsonTone
                            )
                        }
                    }
                }
            }

            // Navigation & Submit Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 0) {
                        OutlinedButton(
                            onClick = { currentStep = (currentStep - 1).coerceAtLeast(0) },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, WarmBorder),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = PrimaryInk
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Previous", color = PrimaryInk)
                        }
                    }

                    if (currentStep < ONBOARDING_STEPS.lastIndex) {
                        OutlinedButton(
                            onClick = { currentStep = (currentStep + 1).coerceAtMost(ONBOARDING_STEPS.lastIndex) },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, PrimaryInk),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("next_step_button")
                        ) {
                            Text("Next Step", color = PrimaryInk, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = PrimaryInk
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val qty = quantityText.toDoubleOrNull() ?: 0.0
                            val price = unitPriceText.toDoubleOrNull() ?: 0.0
                            val adv = advancePercentageText.toDoubleOrNull() ?: 30.0
                            if (buyerName.isBlank() || productName.isBlank() || destinationPort.isBlank() || qty <= 0.0 || price <= 0.0) {
                                validationError = "Please complete Buyer Name, Product Name, Destination Port, and positive Quantity & Unit Price."
                                return@Button
                            }
                            validationError = null
                            onSubmitOrder(
                                orderNumber,
                                exporterName,
                                exporterContact,
                                exporterAddress,
                                buyerName,
                                buyerCountry,
                                buyerContact,
                                buyerAddress,
                                productName,
                                productDescription,
                                qty,
                                unit,
                                price,
                                currency,
                                incoterm,
                                destinationPort,
                                orderDate,
                                expectedShipmentDate,
                                paymentTerms,
                                adv,
                                extractionSource
                            )
                        },
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryInk,
                            contentColor = PureWhiteSurface
                        ),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(52.dp)
                            .testTag("submit_create_order_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ChampagneAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSubmitting) "Saving..." else "Confirm & Launch Order",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = SecondaryInk,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = PrimaryInk,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
fun MinimalFormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    isMonospace: Boolean = false,
    minLines: Int = 1,
    testTag: String? = null
) {
    val appliedModifier = if (testTag != null) modifier.testTag(testTag) else modifier
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = if (isMonospace) JetBrainsMonoFamily else MaterialTheme.typography.bodyMedium.fontFamily,
            color = PrimaryInk
        ),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryInk,
            unfocusedBorderColor = WarmBorder,
            focusedContainerColor = PureWhiteSurface,
            unfocusedContainerColor = PureWhiteSurface,
            focusedLabelColor = PrimaryInk,
            unfocusedLabelColor = SecondaryInk
        ),
        modifier = appliedModifier
    )
}
