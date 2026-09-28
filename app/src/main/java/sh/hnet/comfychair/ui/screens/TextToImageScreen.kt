package sh.hnet.comfychair.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import sh.hnet.comfychair.MediaViewerActivity
import sh.hnet.comfychair.R
import sh.hnet.comfychair.ui.components.generate.ratioOf
import sh.hnet.comfychair.ui.components.generate.FitAspectBox
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.runtime.saveable.rememberSaveable
import sh.hnet.comfychair.ui.components.shared.ResolutionPresetRow
import sh.hnet.comfychair.WorkflowEditorActivity
import sh.hnet.comfychair.connection.ConnectionManager
import sh.hnet.comfychair.model.ScreenType
import sh.hnet.comfychair.queue.JobRegistry
import sh.hnet.comfychair.ui.components.AppMenuDropdown
import sh.hnet.comfychair.ui.components.PromptLibraryDialog
import sh.hnet.comfychair.ui.components.PromptPresetDialog
import sh.hnet.comfychair.ui.components.shared.PromptPresetDropdown
import sh.hnet.comfychair.ui.components.shared.rememberSpellCheckVisualTransformation
import sh.hnet.comfychair.ui.theme.Dimensions
import sh.hnet.comfychair.ui.components.config.ConfigBottomSheetContent
import sh.hnet.comfychair.ui.components.config.UnifiedCallbacks
import sh.hnet.comfychair.ui.components.config.toBottomSheetConfig
import sh.hnet.comfychair.storage.AppSettings
import sh.hnet.comfychair.ui.components.GenerationButton
import sh.hnet.comfychair.ui.components.GenerationProgressBar
import sh.hnet.comfychair.viewmodel.ConnectionStatus
import sh.hnet.comfychair.viewmodel.GenerationViewModel
import sh.hnet.comfychair.viewmodel.PromptPresetEvent
import sh.hnet.comfychair.viewmodel.PromptPresetViewModel
import sh.hnet.comfychair.viewmodel.TextToImageEvent
import sh.hnet.comfychair.viewmodel.TextToImageViewModel
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.hnet.comfychair.cache.MediaCache
import sh.hnet.comfychair.storage.LocalGalleryStore
import sh.hnet.comfychair.util.MetadataParser
import sh.hnet.comfychair.util.PngMetadataExtractor
import sh.hnet.comfychair.viewmodel.GalleryItem
import sh.hnet.comfychair.viewmodel.MediaViewerItem
import sh.hnet.comfychair.ui.components.generate.Brand
import sh.hnet.comfychair.ui.components.generate.BatchTile
import sh.hnet.comfychair.ui.components.generate.ChipRow
import sh.hnet.comfychair.ui.components.generate.EditValueDialog
import sh.hnet.comfychair.ui.components.generate.GenCard
import sh.hnet.comfychair.ui.components.generate.MetaLine
import sh.hnet.comfychair.ui.components.generate.ModeMenuButton
import sh.hnet.comfychair.ui.components.generate.ModeTabs
import sh.hnet.comfychair.ui.components.generate.OverlayChip
import sh.hnet.comfychair.ui.components.generate.ParamTile
import sh.hnet.comfychair.ui.components.generate.PickOptionDialog
import sh.hnet.comfychair.ui.components.generate.PillChip
import sh.hnet.comfychair.ui.components.generate.ProgressPill
import sh.hnet.comfychair.ui.components.generate.RecentResultsStrip
import sh.hnet.comfychair.ui.components.generate.SeedDialog
import sh.hnet.comfychair.ui.components.generate.StatusDot
import sh.hnet.comfychair.ui.components.generate.WorkflowChip
import sh.hnet.comfychair.ui.components.generate.metaText

/**
 * Text-to-Image generation screen
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TextToImageScreen(
    generationViewModel: GenerationViewModel,
    textToImageViewModel: TextToImageViewModel,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current

    // Prompt preset ViewModel
    val presetViewModel: PromptPresetViewModel = viewModel()
    val lifecycleOwner = LocalLifecycleOwner.current

    // State and effects
    // Initialize ViewModels
    LaunchedEffect(Unit) {
        val client = generationViewModel.getClient()
        if (client != null) {
            textToImageViewModel.initialize(context, client)
        }
        presetViewModel.initialize(context, ScreenType.TEXT_TO_IMAGE)
    }

    // Refresh presets when screen resumes (catches external changes from Media Viewer)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                presetViewModel.refreshPresets()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Collect state
    val generationState by generationViewModel.generationState.collectAsState()
    val connectionStatus by generationViewModel.connectionStatus.collectAsState()
    val uiState by textToImageViewModel.uiState.collectAsState()
    val queueState by JobRegistry.queueState.collectAsState()
    val isConnecting by ConnectionManager.isConnecting.collectAsState()
    val presetUiState by presetViewModel.uiState.collectAsState()

    // Check if THIS screen owns the currently executing job (for progress bar)
    val isThisScreenExecuting = queueState.executingOwnerId == TextToImageViewModel.OWNER_ID

    // Check offline mode
    val isOfflineMode = remember { AppSettings.isOfflineMode(context) }
    var spellCheckEnabled by remember { mutableStateOf(AppSettings.isPromptSpellCheckEnabled(context)) }
    var promptExpandEnabled by remember { mutableStateOf(AppSettings.isPromptExpandEnabled(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                spellCheckEnabled = AppSettings.isPromptSpellCheckEnabled(context)
                promptExpandEnabled = AppSettings.isPromptExpandEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val positivePromptTransformation = rememberSpellCheckVisualTransformation(
        text = uiState.positivePrompt,
        enabled = spellCheckEnabled
    )
    val imeHeight = WindowInsets.ime.getBottom(LocalDensity.current)
    var prevImeHeight by remember { mutableStateOf(imeHeight) }
    SideEffect { prevImeHeight = imeHeight }
    var promptFocused by remember { mutableStateOf(false) }
    val expandPrompt = promptExpandEnabled && promptFocused && imeHeight > 0 && imeHeight >= prevImeHeight

    // Fetch models when connected
    LaunchedEffect(connectionStatus) {
        if (connectionStatus == ConnectionStatus.CONNECTED) {
            textToImageViewModel.fetchModels()
        }
    }

    // Register event handler when screen is active
    DisposableEffect(Unit) {
        textToImageViewModel.startListening(generationViewModel)
        onDispose {
            textToImageViewModel.stopListening(generationViewModel)
        }
    }

    // Handle when a NEW job starts executing for this screen
    // Using both executingPromptId and executingOwnerId as keys handles the race condition
    // where execution_start arrives before job registration (owner becomes known later)
    LaunchedEffect(queueState.executingPromptId, queueState.executingOwnerId) {
        val promptId = queueState.executingPromptId
        if (queueState.executingOwnerId == TextToImageViewModel.OWNER_ID && promptId != null) {
            textToImageViewModel.clearPreviewForExecution(promptId)
            textToImageViewModel.startListening(generationViewModel)
        }
    }

    // Event handling
    LaunchedEffect(Unit) {
        textToImageViewModel.events.collect { event ->
            when (event) {
                is TextToImageEvent.ShowToast -> {
                    Toast.makeText(context, context.getString(event.messageResId), Toast.LENGTH_SHORT).show()
                }
                is TextToImageEvent.ShowToastMessage -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Preset event handling
    LaunchedEffect(Unit) {
        presetViewModel.events.collect { event ->
            when (event) {
                is PromptPresetEvent.PresetApplied -> {
                    textToImageViewModel.onPositivePromptChange(event.prompt)
                }
                is PromptPresetEvent.ShowToast -> {
                    Toast.makeText(context, context.getString(event.messageResId), Toast.LENGTH_SHORT).show()
                }
                is PromptPresetEvent.MaxFavoritesReached -> {
                    Toast.makeText(context, context.getString(R.string.prompt_preset_max_favorites), Toast.LENGTH_SHORT).show()
                }
                is PromptPresetEvent.ResetPrompt -> {
                    textToImageViewModel.resetPromptToDefault()
                }
            }
        }
    }

    // UI composition
    var showOptionsBottomSheet by remember { mutableStateOf(false) }
    val optionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Settings (shared by the options sheet and the wide-screen side panel)
    val callbacks = remember(textToImageViewModel) {
        UnifiedCallbacks(
            onWorkflowChange = textToImageViewModel::onWorkflowChange,
            onViewWorkflow = {
                val workflowId = textToImageViewModel.uiState.value.availableWorkflows
                    .find { it.name == textToImageViewModel.uiState.value.selectedWorkflow }?.id
                if (workflowId != null) {
                    context.startActivity(WorkflowEditorActivity.createIntent(context, workflowId))
                }
            },
            onNegativePromptChange = textToImageViewModel::onNegativePromptChange,
            onCheckpointChange = textToImageViewModel::onCheckpointChange,
            onUnetChange = textToImageViewModel::onUnetChange,
            onVaeChange = textToImageViewModel::onVaeChange,
            onClipChange = textToImageViewModel::onClipChange,
            onClip1Change = textToImageViewModel::onClip1Change,
            onClip2Change = textToImageViewModel::onClip2Change,
            onClip3Change = textToImageViewModel::onClip3Change,
            onClip4Change = textToImageViewModel::onClip4Change,
            onTextEncoderChange = textToImageViewModel::onTextEncoderChange,
            onLatentUpscaleModelChange = textToImageViewModel::onLatentUpscaleModelChange,
            onMandatoryLoraChange = textToImageViewModel::onMandatoryLoraChange,
            onWidthChange = textToImageViewModel::onWidthChange,
            onHeightChange = textToImageViewModel::onHeightChange,
            onStepsChange = textToImageViewModel::onStepsChange,
            onCfgChange = textToImageViewModel::onCfgChange,
            onSamplerChange = textToImageViewModel::onSamplerChange,
            onSchedulerChange = textToImageViewModel::onSchedulerChange,
            onRandomSeedToggle = textToImageViewModel::onRandomSeedToggle,
            onSeedChange = textToImageViewModel::onSeedChange,
            onRandomizeSeed = textToImageViewModel::onRandomizeSeed,
            onDenoiseChange = textToImageViewModel::onDenoiseChange,
            onBatchSizeChange = textToImageViewModel::onBatchSizeChange,
            onUpscaleMethodChange = textToImageViewModel::onUpscaleMethodChange,
            onScaleByChange = textToImageViewModel::onScaleByChange,
            onStopAtClipLayerChange = textToImageViewModel::onStopAtClipLayerChange,
            onAddLora = textToImageViewModel::onAddLora,
            onRemoveLora = textToImageViewModel::onRemoveLora,
            onLoraNameChange = textToImageViewModel::onLoraNameChange,
            onLoraStrengthChange = textToImageViewModel::onLoraStrengthChange
        )
    }
    val bottomSheetConfig = remember(uiState, callbacks) { uiState.toBottomSheetConfig(callbacks) }

    // Which recent result is shown in the preview (null = latest generation)
    var selectedRecent by remember { mutableStateOf<GalleryItem?>(null) }

    // Wide layout: settings card collapsed by default
    var settingsExpanded by rememberSaveable { mutableStateOf(false) }

    // Small edit dialogs for the parameter tiles
    var editParam by remember { mutableStateOf<String?>(null) }

    fun generate(front: Boolean) {
        if (!textToImageViewModel.hasValidConfiguration()) return
        val workflowJson = textToImageViewModel.prepareWorkflowJson()
        if (workflowJson == null) {
            Toast.makeText(context, context.getString(R.string.error_failed_load_workflow), Toast.LENGTH_SHORT).show()
            return
        }
        selectedRecent = null
        generationViewModel.startGeneration(workflowJson, TextToImageViewModel.OWNER_ID, front = front) { success, _, errorMessage ->
            if (!success) {
                Toast.makeText(context, errorMessage ?: context.getString(R.string.error_generation_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun showRecent(item: GalleryItem) {
        if (item.isVideo) {
            context.startActivity(
                MediaViewerActivity.createGalleryIntent(
                    context = context,
                    hostname = generationViewModel.getHostname(),
                    port = generationViewModel.getPort(),
                    items = listOf(
                        MediaViewerItem(item.promptId, item.filename, item.subfolder, item.type, item.isVideo, item.index)
                    ),
                    initialIndex = 0
                )
            )
            return
        }
        selectedRecent = item
        scope.launch {
            MediaCache.fetchImage(item.toCacheKey(), item.subfolder, item.type)?.let {
                textToImageViewModel.onPreviewBitmapChange(it)
            }
        }
    }

    // "Copy settings": load the generation record of the shown image back into the form
    fun copySettings() {
        scope.launch {
            val meta = withContext(Dispatchers.IO) {
                val serverId = ConnectionManager.currentServerId
                val item = selectedRecent
                val json: String? = if (item != null) {
                    LocalGalleryStore.localFile(context, serverId, item.toCacheKey())
                        ?.let { PngMetadataExtractor.extractPromptMetadata(it.readBytes()) }
                        ?: LocalGalleryStore.loadGenerationRecord(context, serverId, item.promptId)
                } else {
                    val name = uiState.currentImageFilename
                    val client = ConnectionManager.clientOrNull
                    if (name != null && client != null) {
                        kotlin.coroutines.suspendCoroutine { cont ->
                            client.fetchRawBytes(name, uiState.currentImageSubfolder ?: "", uiState.currentImageType ?: "output") { b, _ ->
                                cont.resumeWith(Result.success(b?.let { PngMetadataExtractor.extractPromptMetadata(it) }))
                            }
                        }
                    } else null
                }
                json?.let { MetadataParser.parseWorkflowJson(it) }
            }
            if (meta == null) {
                Toast.makeText(context, R.string.msg_no_generation_info, Toast.LENGTH_SHORT).show()
                return@launch
            }
            meta.positivePrompt?.let { textToImageViewModel.onPositivePromptChange(it) }
            meta.negativePrompt?.let { textToImageViewModel.onNegativePromptChange(it) }
            meta.steps?.let { textToImageViewModel.onStepsChange(it.toString()) }
            meta.cfg?.let { textToImageViewModel.onCfgChange(it.toString()) }
            meta.sampler?.let { textToImageViewModel.onSamplerChange(it) }
            meta.seed?.let {
                if (textToImageViewModel.uiState.value.randomSeed) textToImageViewModel.onRandomSeedToggle()
                textToImageViewModel.onSeedChange(it.toString())
            }
            Toast.makeText(context, R.string.msg_settings_copied, Toast.LENGTH_SHORT).show()
        }
    }

    val caps = uiState.capabilities
    val connected = connectionStatus == ConnectionStatus.CONNECTED
    val progressVisible = isThisScreenExecuting && generationState.maxProgress > 0 && generationState.progress > 0

    // ---------- Pieces ----------

    val serverMenu: @Composable () -> Unit = {
        Box {
            AppMenuDropdown(onSettings = onNavigateToSettings, onLogout = onLogout)
            StatusDot(connected, Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp))
        }
    }

    val workflowChip: @Composable (Modifier) -> Unit = { m ->
        WorkflowChip(
            workflows = uiState.availableWorkflows.map { it.name },
            selected = uiState.selectedWorkflow,
            onSelect = textToImageViewModel::onWorkflowChange,
            modifier = m
        )
    }

    // Always shown; greyed out until there is an image
    val imageActions: @Composable () -> Unit = {
        val hasImage = uiState.previewBitmap != null
        run {
            OverlayChip(stringResource(R.string.button_save), enabled = hasImage) {
                textToImageViewModel.saveToGallery { success ->
                    val messageRes = if (success) R.string.msg_image_saved_to_gallery else R.string.error_save_image
                    Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
                }
            }
            OverlayChip(stringResource(R.string.button_share), enabled = hasImage) {
                textToImageViewModel.getShareIntent()?.let { intent ->
                    context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.share_image)))
                }
            }
            OverlayChip(stringResource(R.string.button_copy_settings), enabled = hasImage) { copySettings() }
        }
    }

    val openPreviewViewer: () -> Unit = {
        uiState.previewBitmap?.let { bitmap ->
            context.startActivity(
                MediaViewerActivity.createSingleImageIntent(
                    context = context,
                    bitmap = bitmap,
                    hostname = generationViewModel.getHostname(),
                    port = generationViewModel.getPort(),
                    filename = selectedRecent?.filename ?: uiState.currentImageFilename,
                    subfolder = selectedRecent?.subfolder ?: uiState.currentImageSubfolder,
                    type = selectedRecent?.type ?: uiState.currentImageType
                )
            )
        }
    }

    val promptField: @Composable (Modifier, Boolean) -> Unit = { m, fill ->
        OutlinedTextField(
            value = uiState.positivePrompt,
            onValueChange = {
                textToImageViewModel.onPositivePromptChange(it)
                presetViewModel.clearActivePreset()
            },
            label = { Text(stringResource(R.string.hint_prompt)) },
            modifier = m.onFocusChanged { promptFocused = it.isFocused },
            minLines = 3,
            maxLines = if (fill) Int.MAX_VALUE else 4,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = spellCheckEnabled),
            visualTransformation = positivePromptTransformation,
            leadingIcon = {
                PromptPresetDropdown(
                    favorites = presetUiState.favorites,
                    activePresetId = presetUiState.activePresetId,
                    currentPromptIsEmpty = uiState.positivePrompt.isEmpty(),
                    onPresetSelected = { presetViewModel.onPresetSelected(it) },
                    onOpenLibrary = { presetViewModel.showLibrary() },
                    onSaveCurrentPrompt = { presetViewModel.showSaveDialog(uiState.positivePrompt) },
                    onResetPrompt = { presetViewModel.resetPrompt() }
                )
            },
            trailingIcon = {
                if (uiState.positivePrompt.isNotEmpty()) {
                    IconButton(onClick = { textToImageViewModel.onPositivePromptChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.content_description_clear))
                    }
                }
            }
        )
    }

    val negativeLine: @Composable () -> Unit = {
        if (caps.hasNegativePrompt) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            ) {
                Text("NEG", color = Brand.NegRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = uiState.negativePrompt,
                    onValueChange = textToImageViewModel::onNegativePromptChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp)
                )
            }
        }
    }

    val favoritesRow: @Composable () -> Unit = {
        if (presetUiState.favorites.isNotEmpty()) {
            ChipRow {
                presetUiState.favorites.forEach { preset ->
                    PillChip(preset.name, onClick = { presetViewModel.onPresetSelected(preset.id) }, leading = "★")
                }
            }
        }
    }

    val ratioRow: @Composable () -> Unit = {
        if (caps.hasWidth && caps.hasHeight) {
            ResolutionPresetRow(
                width = uiState.width,
                height = uiState.height,
                onSelect = { w, h ->
                    textToImageViewModel.onWidthChange(w.toString())
                    textToImageViewModel.onHeightChange(h.toString())
                }
            )
        }
    }

    val paramsRow: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (caps.hasSteps) ParamTile(stringResource(R.string.label_steps), uiState.steps, { editParam = "steps" }, Modifier.weight(1f))
            if (caps.hasCfg) ParamTile(stringResource(R.string.label_cfg), uiState.cfg, { editParam = "cfg" }, Modifier.weight(1f))
            if (caps.hasSeed) ParamTile(
                stringResource(R.string.label_seed),
                if (uiState.randomSeed) "🎲" else uiState.seed,
                { editParam = "seed" }, Modifier.weight(1f)
            )
            if (caps.hasSamplerName) ParamTile(stringResource(R.string.label_sampler), uiState.sampler, { editParam = "sampler" }, Modifier.weight(1f))
            ParamTile(stringResource(R.string.label_more), "⋯", { showOptionsBottomSheet = true }, Modifier.weight(0.8f))
        }
    }

    val loraRow: @Composable () -> Unit = {
        if (caps.hasLora) {
            ChipRow {
                uiState.loraChain.forEach { lora ->
                    PillChip(
                        "${lora.name.substringAfterLast('/').substringBeforeLast('.')}  ${"%.1f".format(lora.strength)}",
                        onClick = { showOptionsBottomSheet = true }, accent = true
                    )
                }
                PillChip("+ LoRA", onClick = {
                    textToImageViewModel.onAddLora()
                    showOptionsBottomSheet = true
                }, dashed = true)
            }
        }
    }

    val generateRow: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GenerationButton(
                queueSize = queueState.totalQueueSize,
                isExecuting = queueState.isExecuting,
                isEnabled = uiState.positivePrompt.isNotBlank(),
                isOfflineMode = isOfflineMode,
                isFetching = uiState.isFetching,
                isConnecting = isConnecting,
                onGenerate = { generate(front = false) },
                onCancelCurrent = { generationViewModel.cancelGeneration { } },
                onAddToFrontOfQueue = { generate(front = true) },
                onClearQueue = {
                    generationViewModel.getClient()?.clearQueue { success ->
                        val messageRes = if (success) R.string.msg_queue_cleared_success else R.string.error_queue_clear
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
            if (caps.hasBatchSize) {
                BatchTile(uiState.batchSize, textToImageViewModel::onBatchSizeChange, Modifier.fillMaxHeight())
            }
            // All workflow settings (models, LoRA, sampler, ...) in the options sheet
            OutlinedIconButton(
                onClick = { showOptionsBottomSheet = true },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.button_options))
            }
        }
    }

    // Preview card shape: the selected resolution (falls back to the shown image, then square)
    val previewRatio = (if (caps.hasWidth && caps.hasHeight) ratioOf(uiState.width, uiState.height) else null)
        ?: uiState.previewBitmap?.let { it.width.toFloat() / it.height }
        ?: 1f

    val metaInfo = metaText(uiState.previewBitmap, if (uiState.randomSeed) null else uiState.seed, uiState.selectedWorkflow.ifEmpty { null })

    // ---------- Layout ----------

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp

        if (!isWide) {
            // ===== Phone (folded) =====
            Column(Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 8.dp)
                ) {
                    workflowChip(Modifier.weight(1f, fill = false))
                    Spacer(Modifier.weight(1f))
                    ModeMenuButton()
                    serverMenu()
                }

                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!expandPrompt) {
                        // Preview with overlays; its shape follows the selected resolution
                        FitAspectBox(
                            ratio = previewRatio,
                            modifier = Modifier.weight(1f).fillMaxWidth().heightIn(min = 120.dp)
                        ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clickable(enabled = uiState.previewBitmap != null && !isThisScreenExecuting) { openPreviewViewer() }
                        ) {
                            uiState.previewBitmap?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = stringResource(R.string.content_description_generated_image),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            ProgressPill(generationState.progress, generationState.maxProgress, Modifier.align(Alignment.TopStart).padding(10.dp), active = progressVisible)
                            Row(
                                Modifier.align(Alignment.TopEnd).padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) { imageActions() }
                            if (uiState.previewBitmap != null && metaInfo.isNotEmpty()) {
                                MetaLine(metaInfo, Modifier.align(Alignment.BottomStart).padding(10.dp))
                            }
                        }
                        }

                        RecentResultsStrip(
                            selectedKey = selectedRecent?.toCacheKey()?.keyString,
                            onSelect = { showRecent(it) }
                        )
                    }

                    GenCard(if (expandPrompt) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth()) {
                        Column((if (expandPrompt) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).padding(horizontal = 8.dp, vertical = 6.dp)) {
                            promptField(
                                if (expandPrompt) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth(),
                                expandPrompt
                            )
                            negativeLine()
                        }
                    }

                    if (!expandPrompt) {
                        favoritesRow()
                        ratioRow()
                        paramsRow()
                        loraRow()
                    }
                    generateRow()
                }
            }
        } else {
            // ===== Wide (unfolded) =====
            Row(
                Modifier.fillMaxSize().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Left: modes, image actions, image (keeps ratio), info + recent results
                GenCard(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(14.dp)
                        ) {
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = Brand.Lime)) { append("C") }
                                    withStyle(SpanStyle(color = Brand.Blue)) { append("M") }
                                },
                                fontWeight = FontWeight.ExtraBold, fontSize = 18.sp
                            )
                            Spacer(Modifier.width(12.dp))
                            ModeTabs(Modifier.weight(1f))
                        }
                        HorizontalDivider()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 14.dp)
                        ) {
                            ProgressPill(generationState.progress, generationState.maxProgress, translucent = false, active = progressVisible)
                            Spacer(Modifier.weight(1f))
                            imageActions()
                        }
                        FitAspectBox(
                            ratio = previewRatio,
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
                            boxModifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .clickable(enabled = uiState.previewBitmap != null && !isThisScreenExecuting) { openPreviewViewer() }
                        ) {
                            uiState.previewBitmap?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = stringResource(R.string.content_description_generated_image),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (metaInfo.isNotEmpty() && uiState.previewBitmap != null) {
                                Text(metaInfo, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RecentResultsStrip(
                                selectedKey = selectedRecent?.toCacheKey()?.keyString,
                                onSelect = { showRecent(it) }
                            )
                        }
                    }
                }

                // Right: workflow, prompt, all settings, ratio, generate
                Column(
                    Modifier.weight(0.85f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        workflowChip(Modifier.weight(1f))
                        serverMenu()
                    }
                    GenCard(Modifier.fillMaxWidth().weight(0.9f)) {
                        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
                            promptField(Modifier.fillMaxWidth().weight(1f), true)
                            favoritesRow()
                        }
                    }
                    // All settings (negative prompt, models, LoRA, ...) — collapsed by default
                    GenCard(Modifier.fillMaxWidth().then(if (settingsExpanded) Modifier.weight(1f) else Modifier)) {
                        Column(if (settingsExpanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { settingsExpanded = !settingsExpanded }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.button_options), fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    listOf(
                                        uiState.selectedCheckpoint.ifEmpty { uiState.selectedUnet }
                                            .substringAfterLast('/').substringBeforeLast('.'),
                                        if (caps.hasSteps) "${uiState.steps} steps" else "",
                                        if (caps.hasCfg) "CFG ${uiState.cfg}" else "",
                                        if (uiState.loraChain.isNotEmpty()) "LoRA ${uiState.loraChain.size}" else ""
                                    ).filter { it.isNotBlank() }.joinToString(" · "),
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    if (settingsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }
                            if (settingsExpanded) {
                                Box(Modifier.weight(1f)) {
                                    ConfigBottomSheetContent(
                                        config = bottomSheetConfig,
                                        workflowName = uiState.selectedWorkflow,
                                        spellCheckEnabled = spellCheckEnabled
                                    )
                                }
                            }
                        }
                    }
                    ratioRow()
                    generateRow()
                }
            }
        }
    }

    // Parameter edit dialogs
    when (editParam) {
        "steps" -> EditValueDialog(stringResource(R.string.label_steps), uiState.steps, true,
            { textToImageViewModel.onStepsChange(it); editParam = null }, { editParam = null })
        "cfg" -> EditValueDialog(stringResource(R.string.label_cfg), uiState.cfg, true,
            { textToImageViewModel.onCfgChange(it); editParam = null }, { editParam = null })
        "seed" -> SeedDialog(uiState.randomSeed, uiState.seed,
            onRandomToggle = textToImageViewModel::onRandomSeedToggle,
            onSeedChange = textToImageViewModel::onSeedChange,
            onDismiss = { editParam = null })
        "sampler" -> PickOptionDialog(stringResource(R.string.label_sampler), uiState.availableSamplers, uiState.sampler,
            { textToImageViewModel.onSamplerChange(it); editParam = null }, { editParam = null })
    }

    if (showOptionsBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOptionsBottomSheet = false },
            sheetState = optionsSheetState,
            contentWindowInsets = { WindowInsets.safeDrawing }
        ) {
            ConfigBottomSheetContent(
                config = bottomSheetConfig,
                workflowName = uiState.selectedWorkflow,
                spellCheckEnabled = spellCheckEnabled
            )
        }
    }

    // Prompt Library Dialog
    if (presetUiState.showLibrarySideSheet) {
        PromptLibraryDialog(
            presets = presetViewModel.getFilteredPresets(),
            availableTags = presetUiState.availableTags,
            searchQuery = presetUiState.searchQuery,
            selectedTags = presetUiState.selectedTags,
            filterFavoritesOnly = presetUiState.filterFavoritesOnly,
            activePresetId = presetUiState.activePresetId,
            onSearchQueryChange = { presetViewModel.onSearchQueryChange(it) },
            onTagToggle = { presetViewModel.onTagToggle(it) },
            onToggleFavoritesFilter = { presetViewModel.onToggleFavoritesFilter() },
            onPresetSelected = { presetViewModel.onPresetSelected(it) },
            onToggleFavorite = { presetViewModel.onToggleFavorite(it) },
            onEditPreset = { presetViewModel.showEditDialog(it) },
            onDuplicatePreset = { presetViewModel.onDuplicatePreset(it) },
            onDeletePreset = { presetViewModel.onDeletePreset(it) },
            onDismiss = { presetViewModel.dismissLibrary() }
        )
    }

    // Prompt Preset Save/Edit Dialog
    if (presetUiState.showSaveDialog) {
        PromptPresetDialog(
            editingPreset = presetUiState.editingPreset,
            currentPrompt = presetUiState.currentPromptForSave,
            existingTags = presetUiState.availableTags,
            isNameTaken = { name, excludeId -> presetViewModel.isNameTaken(name, excludeId) },
            onDismiss = { presetViewModel.dismissSaveDialog() },
            onSave = { name, prompt, tags -> presetViewModel.onSavePreset(name, prompt, tags) }
        )
    }
}
