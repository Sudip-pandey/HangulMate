package com.hangulmate.ui.camera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.hangulmate.R
import com.hangulmate.domain.model.LearningState
import com.hangulmate.domain.model.VocabularyWord
import com.hangulmate.ml.ClassificationCandidate
import com.hangulmate.ml.TranslationBreakdown
import com.hangulmate.ui.theme.Spacing
import java.io.ByteArrayOutputStream

/**
 * Camera Scan screen — two modes behind tabs at the top:
 * Scan Text (ML Kit Korean OCR + dictionary breakdown) and
 * Scan Object (on-device MobileNet classification + dictionary lookup).
 *
 * Photos are processed on-device only and never leave the device.
 * [onWordClick] opens the dictionary detail for a word id. The parent
 * (navigation) wires that callback; this screen never navigates itself.
 */
@Composable
fun CameraScreen(
    onWordClick: (String) -> Unit,
    onAddWord: (String) -> Unit = {},
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            val activity = context as? Activity
            val showRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.CAMERA,
                )
            } ?: false
            permanentlyDenied = askedOnce && !showRationale
        }
        askedOnce = true
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { viewModel.importImage(it) }
    }

    // CameraX use cases (kept across recompositions; bound once permission exists).
    val previewView = remember { PreviewView(context) }
    val preview = remember { Preview.Builder().build() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraError by remember { mutableStateOf(false) }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                try {
                    val provider = providerFuture.get()
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    provider.unbindAll()
                    camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                    )
                } catch (_: Exception) {
                    cameraError = true
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    LaunchedEffect(state.torchOn, camera) {
        camera?.cameraControl?.enableTorch(state.torchOn)
    }

    fun capturePhoto() {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.toBitmap()
                    image.close()
                    if (bitmap != null) {
                        viewModel.onPhotoCaptured(bitmap)
                    } else {
                        viewModel.onCaptureFailed()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    viewModel.onCaptureFailed()
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.camera_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        // One-time recognizer warm-up state: shown while the bundled
        // Korean model initialises, and honestly when it failed (manual
        // text entry below still works).
        if (state.ocrModelPreparing) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(
                    text = stringResource(R.string.camera_ocr_preparing),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (state.ocrModelFailed) {
            WarningCard(stringResource(R.string.camera_ocr_unavailable))
        }

        state.noticeRes?.let { noticeRes ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(noticeRes),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.clearNotice() }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }

        if (!hasPermission) {
            Card {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = stringResource(R.string.camera_permission_rationale),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (permanentlyDenied) {
                        Text(
                            text = stringResource(R.string.camera_permission_permanently_denied),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null),
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.camera_open_settings))
                        }
                    } else {
                        if (askedOnce) {
                            Text(
                                text = stringResource(R.string.camera_permission_denied),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.camera_grant_permission))
                        }
                    }
                }
            }
        }

        ModeTabs(
            selected = state.mode,
            onSelect = { viewModel.setMode(it) },
        )

        val bitmap = state.capturedBitmap
        if (bitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat()),
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
                val ocr = state.ocrState
                if (state.mode == CameraMode.TEXT && ocr is OcrUiState.Success) {
                    OcrOverlay(
                        blocks = ocr.result.blocks,
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else if (hasPermission) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (cameraError) {
                Text(
                    text = stringResource(R.string.camera_no_camera),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.camera_capture_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (bitmap == null && hasPermission) {
                Button(onClick = { capturePhoto() }) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.camera_capture_photo))
                }
            }
            OutlinedButton(onClick = { galleryLauncher.launch("image/*") }) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.camera_import_gallery))
            }
            if (bitmap == null && hasPermission) {
                OutlinedButton(onClick = { viewModel.setTorch(!state.torchOn) }) {
                    Icon(
                        if (state.torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        stringResource(
                            if (state.torchOn) R.string.camera_torch_on else R.string.camera_torch_off,
                        ),
                    )
                }
            }
            if (bitmap != null) {
                OutlinedButton(onClick = { viewModel.clearCapture() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.camera_retake))
                }
            }
        }

        if (state.mode == CameraMode.TEXT) {
            TextModeContent(
                state = state,
                viewModel = viewModel,
                onWordClick = onWordClick,
                onAddWord = onAddWord,
            )
        } else {
            ObjectModeContent(
                state = state,
                viewModel = viewModel,
                onWordClick = onWordClick,
            )
        }
    }
}

@Composable
private fun ModeTabs(
    selected: CameraMode,
    onSelect: (CameraMode) -> Unit,
) {
    val tabs = listOf(
        stringResource(R.string.camera_mode_scan_text),
        stringResource(R.string.camera_mode_scan_object),
    )
    val selectedIndex = if (selected == CameraMode.TEXT) 0 else 1
    TabRow(selectedTabIndex = selectedIndex) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedIndex == index,
                onClick = {
                    onSelect(if (index == 0) CameraMode.TEXT else CameraMode.OBJECT)
                },
                text = { Text(title) },
            )
        }
    }
}

@Composable
private fun WarningCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// MODE 1 — Scan Text / Translate
// ---------------------------------------------------------------------------

@Composable
private fun TextModeContent(
    state: CameraUiState,
    viewModel: CameraViewModel,
    onWordClick: (String) -> Unit,
    onAddWord: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        when (val ocr = state.ocrState) {
            is OcrUiState.Processing -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        text = stringResource(
                            if (ocr.preparingModel) {
                                R.string.camera_ocr_preparing
                            } else {
                                R.string.camera_ocr_reading
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            OcrUiState.Unavailable -> WarningCard(
                stringResource(R.string.camera_ocr_unavailable),
            )
            is OcrUiState.Error -> {
                WarningCard(stringResource(R.string.camera_ocr_error))
                if (state.capturedBitmap != null) {
                    OutlinedButton(onClick = { viewModel.retryOcr() }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            is OcrUiState.Success -> {
                if (ocr.result.fullText.isBlank()) {
                    Text(
                        text = stringResource(R.string.camera_ocr_no_text),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            OcrUiState.Idle -> Unit
        }

        OutlinedTextField(
            value = state.extractedText,
            onValueChange = { viewModel.updateExtractedText(it) },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    stringResource(
                        if (state.capturedBitmap != null) {
                            R.string.camera_extracted_text_label
                        } else {
                            R.string.camera_manual_entry_label
                        },
                    ),
                )
            },
            minLines = 3,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(
                onClick = { viewModel.analyzeText(state.extractedText) },
                enabled = state.extractedText.isNotBlank() && !state.breakdownLoading,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.camera_search_this_text))
            }
            IconButton(
                onClick = { viewModel.copyExtractedText() },
                enabled = state.extractedText.isNotBlank(),
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.copy),
                )
            }
            IconButton(
                onClick = { viewModel.shareExtractedText() },
                enabled = state.extractedText.isNotBlank(),
            ) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = stringResource(R.string.share),
                )
            }
        }

        Text(
            text = stringResource(R.string.camera_ocr_model_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.breakdownLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }

        state.breakdown?.let { breakdown ->
            BreakdownSection(
                breakdown = breakdown,
                state = state,
                viewModel = viewModel,
                onWordClick = onWordClick,
                onAddWord = onAddWord,
            )
        }
    }
}

@Composable
private fun BreakdownSection(
    breakdown: TranslationBreakdown,
    state: CameraUiState,
    viewModel: CameraViewModel,
    onWordClick: (String) -> Unit,
    onAddWord: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        breakdown.phraseWord?.let { phraseWord ->
            Text(
                text = stringResource(R.string.camera_phrase_found),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            WordCard(
                word = phraseWord,
                state = state,
                viewModel = viewModel,
                onWordClick = onWordClick,
            )
        }

        // (a) Word-by-word Nepali breakdown — real dictionary meanings only.
        Text(
            text = stringResource(R.string.camera_breakdown_word_by_word),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (state.meaningLanguage == MeaningLanguage.NEPALI) {
                Button(onClick = { viewModel.setMeaningLanguage(MeaningLanguage.NEPALI) }) {
                    Text(stringResource(R.string.camera_breakdown_meaning_nepali))
                }
            } else {
                OutlinedButton(onClick = { viewModel.setMeaningLanguage(MeaningLanguage.NEPALI) }) {
                    Text(stringResource(R.string.camera_breakdown_meaning_nepali))
                }
            }
            if (state.meaningLanguage == MeaningLanguage.ENGLISH) {
                Button(onClick = { viewModel.setMeaningLanguage(MeaningLanguage.ENGLISH) }) {
                    Text(stringResource(R.string.camera_breakdown_meaning_english))
                }
            } else {
                OutlinedButton(onClick = { viewModel.setMeaningLanguage(MeaningLanguage.ENGLISH) }) {
                    Text(stringResource(R.string.camera_breakdown_meaning_english))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            for (token in breakdown.tokens) {
                val word = token.word
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = token.raw,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = when {
                            word == null -> "—"
                            state.meaningLanguage == MeaningLanguage.NEPALI -> word.nepali
                            else -> word.english
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (word == null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(2f),
                    )
                }
            }
        }

        if (breakdown.exact.isNotEmpty()) {
            Text(
                text = stringResource(R.string.camera_exact_matches),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            for (word in breakdown.exact) {
                WordCard(
                    word = word,
                    state = state,
                    viewModel = viewModel,
                    onWordClick = onWordClick,
                )
            }
        }

        if (breakdown.partial.isNotEmpty()) {
            Text(
                text = stringResource(R.string.camera_partial_matches),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            for ((token, word) in breakdown.partial) {
                WordCard(
                    word = word,
                    state = state,
                    viewModel = viewModel,
                    onWordClick = onWordClick,
                    note = token,
                )
            }
        }

        if (breakdown.unknown.isNotEmpty()) {
            Text(
                text = stringResource(R.string.camera_unknown_words),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            // Each unknown word gets its own Save button: it opens the
            // add-word form prefilled with the Korean text, and the user
            // supplies the Nepali meaning there. No meaning is guessed.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                for (unknown in breakdown.unknown) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = unknown,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { onAddWord(unknown) }) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.camera_unknown_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // (b) Full-sentence translation — honestly NOT available offline.
        Card {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.camera_full_translation_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.camera_full_translation_note),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// MODE 2 — Scan Object / Visual Dictionary
// ---------------------------------------------------------------------------

@Composable
private fun ObjectModeContent(
    state: CameraUiState,
    viewModel: CameraViewModel,
    onWordClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        when (val objectState = state.objectState) {
            ObjectUiState.Idle -> Unit
            ObjectUiState.Processing -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        text = stringResource(R.string.camera_classifying),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            ObjectUiState.ModelMissing -> WarningCard(
                stringResource(R.string.camera_object_model_missing),
            )
            is ObjectUiState.Error -> {
                WarningCard(stringResource(R.string.camera_object_error))
                if (state.capturedBitmap != null) {
                    OutlinedButton(onClick = { viewModel.retryClassification() }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            is ObjectUiState.Results -> {
                for (candidate in objectState.candidates) {
                    CandidateCard(
                        candidate = candidate,
                        state = state,
                        viewModel = viewModel,
                        onWordClick = onWordClick,
                    )
                }
            }
            is ObjectUiState.NotIdentified -> {
                WarningCard(stringResource(R.string.camera_object_not_identified))
                Text(
                    text = stringResource(R.string.camera_object_not_identified_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(
                        R.string.camera_object_top_guess,
                        objectState.topPrediction.label,
                        (objectState.topPrediction.confidence * 100).toInt(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val mapped = objectState.candidates.filter { it.word != null }
                if (mapped.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.camera_object_other_guesses),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    for (candidate in mapped) {
                        CandidateCard(
                            candidate = candidate,
                            state = state,
                            viewModel = viewModel,
                            onWordClick = onWordClick,
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.camera_object_manual_search),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        OutlinedTextField(
            value = state.manualObjectQuery,
            onValueChange = { viewModel.searchObjectManually(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.camera_object_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )
        if (state.manualObjectSearching) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
        for (word in state.manualObjectResults) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onWordClick(word.id) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = word.korean,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = word.nepali,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared word cards
// ---------------------------------------------------------------------------

@Composable
private fun WordCard(
    word: VocabularyWord,
    state: CameraUiState,
    viewModel: CameraViewModel,
    onWordClick: (String) -> Unit,
    note: String? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onWordClick(word.id) },
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WordCardBody(
                word = word,
                state = state,
                viewModel = viewModel,
                onWordClick = onWordClick,
            )
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: ClassificationCandidate,
    state: CameraUiState,
    viewModel: CameraViewModel,
    onWordClick: (String) -> Unit,
) {
    val word = candidate.word
    if (word == null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = candidate.prediction.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(
                        R.string.camera_confidence,
                        (candidate.prediction.confidence * 100).toInt(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = "${candidate.prediction.label} — " +
                    stringResource(
                        R.string.camera_confidence,
                        (candidate.prediction.confidence * 100).toInt(),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Reuse the full word card content (tappable card inside would
            // nest clicks, so render the shared body without the outer card).
            WordCardBody(
                word = word,
                state = state,
                viewModel = viewModel,
                onWordClick = onWordClick,
            )
        }
    }
}

@Composable
private fun WordCardBody(
    word: VocabularyWord,
    state: CameraUiState,
    viewModel: CameraViewModel,
    onWordClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = word.korean,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onWordClick(word.id) },
            )
            IconButton(onClick = { viewModel.toggleFavorite(word.id) }) {
                Icon(
                    if (word.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = stringResource(
                        if (word.isFavorite) {
                            R.string.camera_remove_favorite
                        } else {
                            R.string.camera_add_favorite
                        },
                    ),
                )
            }
            if (state.ttsAvailable) {
                IconButton(onClick = { viewModel.speak(word.korean) }) {
                    Icon(
                        Icons.Filled.VolumeUp,
                        contentDescription = stringResource(R.string.camera_listen),
                    )
                }
            }
        }
        Text(
            text = if (state.meaningLanguage == MeaningLanguage.NEPALI) {
                word.nepali
            } else {
                word.english
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.clickable { onWordClick(word.id) },
        )
        Text(
            text = word.romanization,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${stringResource(R.string.camera_category)}: " +
                if (state.meaningLanguage == MeaningLanguage.NEPALI) {
                    word.topic.nameNe
                } else {
                    word.topic.nameEn
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (word.exampleKo.isNotBlank()) {
            Text(
                text = "${stringResource(R.string.camera_example)}: ${word.exampleKo}",
                style = MaterialTheme.typography.bodyMedium,
            )
            val exampleTranslation =
                if (state.meaningLanguage == MeaningLanguage.NEPALI) word.exampleNe else word.exampleEn
            if (exampleTranslation.isNotBlank()) {
                Text(
                    text = exampleTranslation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedButton(
            onClick = { viewModel.saveToLearnList(word.id) },
            enabled = word.learningState != LearningState.LEARNING,
        ) {
            Text(
                stringResource(
                    if (word.learningState == LearningState.LEARNING) {
                        R.string.camera_saved_to_learn
                    } else {
                        R.string.camera_save_to_learn
                    },
                ),
            )
        }
        val relations = (word.similar + word.opposite + word.confusing)
            .filter { it.targetWordId != null }
        if (relations.isNotEmpty()) {
            Text(
                text = stringResource(R.string.camera_related),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                for (relation in relations) {
                    AssistChip(
                        onClick = {
                            relation.targetWordId?.let { onWordClick(it) }
                        },
                        label = { Text(relation.targetKorean) },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bitmap conversion
// ---------------------------------------------------------------------------

/**
 * Converts a YUV_420_888 [ImageProxy] frame into an upright [Bitmap].
 * Returns null when the frame cannot be decoded.
 */
private fun ImageProxy.toBitmap(): Bitmap? {
    val image = this.image ?: return null
    val yuvImage = runCatching {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    }.getOrNull() ?: return null
    val output = ByteArrayOutputStream()
    if (!yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, output)) {
        return null
    }
    val bytes = output.toByteArray()
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return decoded
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(
        decoded,
        0,
        0,
        decoded.width,
        decoded.height,
        matrix,
        true,
    )
}
