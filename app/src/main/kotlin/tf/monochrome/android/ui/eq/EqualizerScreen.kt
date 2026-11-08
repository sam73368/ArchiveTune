package tf.monochrome.android.ui.eq

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tf.monochrome.android.audio.eq.AutoEqEngine
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import tf.monochrome.android.devedit.DevEditable
import tf.monochrome.android.domain.model.EqBand
import tf.monochrome.android.domain.model.FilterType
import tf.monochrome.android.ui.components.bounceClick
import tf.monochrome.android.ui.components.liquidGlass
import kotlin.math.roundToInt
import tf.monochrome.android.ui.navigation.LocalMiniPlayerInset
import moe.rukamori.archivetune.ui.component.UnglassedDialogTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    viewModel: EqViewModel = hiltViewModel()
) {
    val eqEnabled by viewModel.eqEnabled.collectAsStateWithLifecycle()
    val toneControls by viewModel.toneControls.collectAsStateWithLifecycle()
    val currentBands by viewModel.currentBands.collectAsStateWithLifecycle()
    val currentPreamp by viewModel.currentPreamp.collectAsStateWithLifecycle()
    val autoPreamp by viewModel.autoPreamp.collectAsStateWithLifecycle()
    val availableTargets by viewModel.availableTargets.collectAsStateWithLifecycle()
    val selectedTarget by viewModel.selectedTarget.collectAsStateWithLifecycle()
    val activePreset by viewModel.activePreset.collectAsStateWithLifecycle()
    val allPresets by viewModel.allPresets.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isCalculating by viewModel.isCalculating.collectAsStateWithLifecycle()
    val originalMeasurement by viewModel.originalMeasurement.collectAsStateWithLifecycle()
    val selectedHeadphone by viewModel.selectedHeadphone.collectAsStateWithLifecycle()
    val stereoMode by viewModel.stereoMode.collectAsStateWithLifecycle()
    val currentBandsR by viewModel.currentBandsR.collectAsStateWithLifecycle()
    val originalMeasurementR by viewModel.originalMeasurementR.collectAsStateWithLifecycle()
    val editChannel by viewModel.editChannel.collectAsStateWithLifecycle()
    val measurementLabelL by viewModel.measurementLabelL.collectAsStateWithLifecycle()
    val measurementLabelR by viewModel.measurementLabelR.collectAsStateWithLifecycle()
    val measurementSampleL by viewModel.measurementSampleL.collectAsStateWithLifecycle()
    val measurementSampleR by viewModel.measurementSampleR.collectAsStateWithLifecycle()
    val smoothing by viewModel.smoothing.collectAsStateWithLifecycle()
    val algorithm by viewModel.algorithm.collectAsStateWithLifecycle()

    val editRight = stereoMode && editChannel == EqChannel.RIGHT
    val activeBands = if (editRight) currentBandsR else currentBands
    val activeMeasurement =
        if (editRight) originalMeasurementR.ifEmpty { originalMeasurement }
        else originalMeasurement
    val bandCount by viewModel.bandCount.collectAsStateWithLifecycle()
    val maxFrequency by viewModel.maxFrequency.collectAsStateWithLifecycle()
    val availableHeadphones by viewModel.availableHeadphones.collectAsStateWithLifecycle()
    val showTutorial by viewModel.showTutorial.collectAsStateWithLifecycle()

    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var showTargetMenu by remember { mutableStateOf(false) }
    var showHeadphoneSelect by remember { mutableStateOf(false) }
    var showPresetMenu by remember { mutableStateOf(false) }
    var showBandsExpanded by rememberSaveable { mutableStateOf(true) }
    var showProfilesExpanded by rememberSaveable { mutableStateOf(true) }
    var saveName by rememberSaveable { mutableStateOf("") }
    var saveDescription by rememberSaveable { mutableStateOf("") }
    var showTargetNameDialog by rememberSaveable { mutableStateOf(false) }
    var importForRight by rememberSaveable { mutableStateOf(false) }
    var showProfileImport by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var headphoneSelectForRight by rememberSaveable { mutableStateOf(false) }
    var pendingTargetData by rememberSaveable { mutableStateOf("") }
    var targetName by rememberSaveable { mutableStateOf("") }
    var presetToDelete by remember { mutableStateOf<tf.monochrome.android.domain.model.EqPreset?>(null) }

    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.bandClampEvents.collect { cap ->
            Toast.makeText(
                context,
                "Clamped to \u00b1${cap.toInt()} dB (AutoEQ limit)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val measurementFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val rawData = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (!rawData.isNullOrEmpty()) {
                viewModel.importMeasurementData(
                    rawData,
                    if (importForRight) EqChannel.RIGHT else EqChannel.LEFT,
                )
            } else {
                android.widget.Toast.makeText(context, "Couldn't read the selected file", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            viewModel.clearError()
            android.widget.Toast.makeText(context, "Couldn't read the selected file", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val targetFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val rawData = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (!rawData.isNullOrEmpty()) {
                pendingTargetData = rawData
                targetName = ""
                showTargetNameDialog = true
            } else {
                android.widget.Toast.makeText(context, "Couldn't read the selected file", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, "Couldn't read the selected file", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    var pendingEqExport by remember { mutableStateOf<String?>(null) }
    val eqExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val text = pendingEqExport
        pendingEqExport = null
        if (uri != null && text != null) {
            val ok = try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                true
            } catch (_: Exception) {
                false
            }
            android.widget.Toast.makeText(
                context,
                if (ok) "EQ exported" else "Couldn't export EQ",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    if (showTutorial) {
        AutoEqTutorialDialog(onDismiss = { viewModel.dismissTutorial() })
    }

    val bandsSubtitle = buildString {
        if (stereoMode) append(if (editRight) "Right ear · " else "Left ear · ")
        append(activeBands.size).append(" bands")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp + LocalMiniPlayerInset.current)
        ) {
            item {
              DevEditable("eq_title_section", Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "PRECISION AUTOEQ",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Headphone correction filters generator.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { showHelp = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                contentDescription = "Help",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = eqEnabled,
                            onCheckedChange = { viewModel.toggleEq() }
                        )
                    }
                }
              }
            }

            item {
                EqEngineStatusCard(
                    eqEnabled = eqEnabled,
                    onEnable = { viewModel.toggleEq() },
                )
            }

            if (!error.isNullOrEmpty()) {
                item {
                    EqDismissibleError(
                        message = error ?: "",
                        onDismiss = { viewModel.clearError() },
                    )
                }
            }

            item {
              DevEditable("eq_graph", Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    var smoothingDrag by remember(smoothing) { mutableFloatStateOf(smoothing) }
                    if (stereoMode) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = !editRight,
                                onClick = { viewModel.setEditChannel(EqChannel.LEFT) },
                                label = { Text("Left ear") },
                            )
                            FilterChip(
                                selected = editRight,
                                onClick = { viewModel.setEditChannel(EqChannel.RIGHT) },
                                label = { Text("Right ear") },
                            )
                        }
                    }

                    val displayMeasurement = remember(activeMeasurement, smoothing) {
                        AutoEqEngine.smoothCurve(activeMeasurement, smoothing)
                    }
                    val otherMeasurement =
                        if (editRight) originalMeasurement else originalMeasurementR
                    val displayOther = remember(otherMeasurement, smoothing) {
                        AutoEqEngine.smoothCurve(otherMeasurement, smoothing)
                    }
                    FrequencyResponseGraph(
                        originalCurve = displayMeasurement,
                        targetCurve = selectedTarget.data,
                        eqBands = activeBands,
                        preamp = currentPreamp,
                        onBandDragged = { bandId, freq, gain ->
                            viewModel.updateBandByDrag(bandId, freq, gain)
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                        secondaryMeasurement = if (stereoMode) displayOther else emptyList(),
                        secondaryBands =
                            if (stereoMode) {
                                if (editRight) currentBands else currentBandsR
                            } else null,
                        primaryIsRight = editRight,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "SMOOTHING",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = smoothingDrag,
                            onValueChange = { smoothingDrag = it.roundToInt().toFloat().coerceIn(0f, 100f) },
                            onValueChangeFinished = { viewModel.setSmoothing(smoothingDrag) },
                            valueRange = 0f..100f,
                            modifier = Modifier.weight(1f).height(28.dp)
                        )
                        Text(
                            "${smoothingDrag.roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
              }
            }

            item {
                EqSectionHeader(
                    step = 1,
                    title = "Source",
                    subtitle = "Your headphone and the sound you aim for",
                )
            }

            item {
              EqCard {
                DevEditable("eq_headphone_selector", Modifier.fillMaxWidth()) {
                  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("HEADPHONE MODEL")

                    DevEditable("eq_database_section", Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .liquidGlass(shape = RoundedCornerShape(8.dp))
                                .clickable {
                                    headphoneSelectForRight = false
                                    showHeadphoneSelect = true
                                }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Search model (e.g. HD 600)...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "AutoEq Repo · ${availableHeadphones.size} models",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    if (stereoMode) {
                        Text(
                            "LEFT EAR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SelectorRow(
                            value = measurementLabelL
                                ?: selectedHeadphone?.name
                                ?: "Select left measurement...",
                            onClick = {
                                headphoneSelectForRight = false
                                showHeadphoneSelect = true
                            },
                            trailingIcon = {
                                MeasurementTrailing(
                                    sample = measurementSampleL,
                                    onStep = { forward ->
                                        viewModel.cycleMeasurementSample(EqChannel.LEFT, forward)
                                    },
                                    importDescription = "Import left measurement file",
                                    onImport = {
                                        importForRight = false
                                        measurementFilePicker.launch("text/*")
                                    },
                                )
                            }
                        )
                        Text(
                            "RIGHT EAR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SelectorRow(
                            value = measurementLabelR ?: "Select right measurement...",
                            onClick = {
                                headphoneSelectForRight = true
                                showHeadphoneSelect = true
                            },
                            trailingIcon = {
                                MeasurementTrailing(
                                    sample = measurementSampleR,
                                    onStep = { forward ->
                                        viewModel.cycleMeasurementSample(EqChannel.RIGHT, forward)
                                    },
                                    importDescription = "Import right measurement file",
                                    onImport = {
                                        importForRight = true
                                        measurementFilePicker.launch("text/*")
                                    },
                                )
                            }
                        )
                    } else {
                        SelectorRow(
                            value = selectedHeadphone?.name ?: "Select headphone...",
                            onClick = {
                                headphoneSelectForRight = false
                                showHeadphoneSelect = true
                            },
                            trailingIcon = {
                                MeasurementTrailing(
                                    sample = measurementSampleL,
                                    onStep = { forward ->
                                        viewModel.cycleMeasurementSample(EqChannel.LEFT, forward)
                                    },
                                    importDescription = "Import measurement file",
                                    onImport = {
                                        importForRight = false
                                        measurementFilePicker.launch("text/*")
                                    },
                                )
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "2-channel calibration",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Separate left/right corrections. Non-destructive to " +
                                    "switch off; system-wide EQ stays mono.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = stereoMode,
                            onCheckedChange = { viewModel.setStereoMode(it) }
                        )
                    }

                    TextButton(onClick = { showProfileImport = true }) {
                        Icon(
                            Icons.Default.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import EQ profile (APO txt / CSV)")
                    }
                  }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                DevEditable("eq_target_selector", Modifier.fillMaxWidth()) {
                  Column {
                    SectionLabel("TARGET")
                    Box {
                        SelectorRow(
                            value = selectedTarget.label,
                            onClick = { showTargetMenu = true },
                            trailingIcon = {
                                IconButton(
                                    onClick = { targetFilePicker.launch("text/*") },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .liquidGlass(
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.UploadFile,
                                        contentDescription = "Import custom target",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )

                        UnglassedDialogTheme {
                        DropdownMenu(
                            expanded = showTargetMenu,
                            onDismissRequest = { showTargetMenu = false }
                        ) {
                            availableTargets.forEach { target ->
                                val isCustom = target.id.startsWith("custom_")
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(target.label, modifier = Modifier.weight(1f))
                                            if (isCustom) {
                                                IconButton(
                                                    onClick = {
                                                        viewModel.deleteCustomTarget(target.id)
                                                        showTargetMenu = false
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Delete",
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.selectTarget(target.id)
                                        showTargetMenu = false
                                    }
                                )
                            }
                        }
                        }
                    }
                  }
                }
              }
            }

            item {
                EqSectionHeader(
                    step = 2,
                    title = "Generate",
                    subtitle = "Compute the correction filters",
                )
            }

            item {
              EqCard {
                DevEditable("eq_algorithm_row", Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "ALGORITHM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        tf.monochrome.android.audio.eq.AutoEqAlgorithm.entries.forEach { algo ->
                            FilterChip(
                                selected = algorithm == algo,
                                onClick = { viewModel.setAlgorithm(algo) },
                                label = { Text(algo.label) },
                            )
                        }
                    }
                }

                DevEditable("eq_parameters_row", Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ParameterDropdown(
                            label = "FILTER BANDS",
                            value = bandCount.toString(),
                            options = listOf("5", "10", "15", "20", "31"),
                            onValueChanged = { viewModel.setBandCount(it.toInt()) },
                            modifier = Modifier.weight(1f)
                        )
                        ParameterDropdown(
                            label = "MAX HZ",
                            value = formatFreqLabel(maxFrequency),
                            options = listOf("8k", "12k", "16k", "20k"),
                            onValueChanged = {
                                viewModel.setMaxFrequency(parseFreqLabel(it))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                DevEditable("eq_autoeq_button", Modifier.fillMaxWidth()) {
                    GradientAutoEqButton(
                        isCalculating = isCalculating,
                        onClick = { if (!isCalculating) viewModel.runAutoEq() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
              }
            }

            item {
                EqSectionHeader(
                    step = 3,
                    title = "Adjust",
                    subtitle = "Overall level and tone",
                )
            }

            item {
              EqCard {
                DevEditable("eq_preamp_slider", Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Preamp",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (autoPreamp) "Auto · ${currentPreamp.roundToInt()} dB"
                                else "${currentPreamp.roundToInt()} dB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = if (currentPreamp.isNaN()) 0f else currentPreamp.coerceIn(-24f, 24f),
                            onValueChange = { viewModel.setPreamp(it) },
                            valueRange = -24f..24f,
                            steps = 47,
                            enabled = !autoPreamp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                DevEditable("eq_auto_preamp_toggle", Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Automatic preamp",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Compensates the combined EQ + tone boost so the signal can't clip.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoPreamp,
                            onCheckedChange = { viewModel.setAutoPreamp(it) }
                        )
                    }
                }
              }
            }

            item {
                tf.monochrome.android.ui.player.ToneControlsPanel(
                    tone = toneControls,
                    accent = MaterialTheme.colorScheme.primary,
                    onChange = viewModel::setToneControls,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            item {
              DevEditable("eq_bands_header", Modifier.fillMaxWidth()) {
                EqSectionHeader(
                    step = 4,
                    title = "Filters",
                    subtitle = bandsSubtitle,
                    onClick = { showBandsExpanded = !showBandsExpanded },
                    trailing = {
                        Icon(
                            imageVector = if (showBandsExpanded) Icons.Default.ExpandLess
                            else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                )
              }
            }

            item {
              DevEditable("eq_action_row", Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EqActionButton(
                        icon = Icons.Default.Refresh,
                        label = "Reset",
                        onClick = { viewModel.resetToFlat() },
                        modifier = Modifier.weight(1f),
                    )
                    EqActionButton(
                        icon = Icons.Default.Download,
                        label = "Export",
                        onClick = {
                            if (activeBands.isEmpty()) {
                                Toast.makeText(context, "No EQ bands to export", Toast.LENGTH_SHORT).show()
                            } else {
                                pendingEqExport = buildParametricEqText(activeBands, currentPreamp)
                                eqExportLauncher.launch(
                                    if (editRight) "MonochromeEQ-R.txt" else "MonochromeEQ.txt"
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    EqActionButton(
                        icon = Icons.Default.Save,
                        label = "Save",
                        onClick = {
                            val hp = selectedHeadphone?.name
                            saveName = if (hp.isNullOrBlank()) selectedTarget.label
                                       else "$hp — ${selectedTarget.label}"
                            saveDescription = ""
                            showSaveDialog = true
                        },
                        enabled = currentBands.isNotEmpty(),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                }
              }
            }

            if (showBandsExpanded) {
                items(activeBands) { band ->
                    EqBandSlider(
                        band = band,
                        onBandChanged = { viewModel.updateBand(band.id, it) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            item {
              DevEditable("eq_saved_profiles_header", Modifier.fillMaxWidth()) {
                EqSectionHeader(
                    step = 5,
                    title = "Saved profiles",
                    subtitle = if (allPresets.isEmpty()) "Nothing saved yet"
                    else "${allPresets.size} saved",
                    onClick = { showProfilesExpanded = !showProfilesExpanded },
                    trailing = {
                        Icon(
                            imageVector = if (showProfilesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                )
              }
            }

            if (showProfilesExpanded) {
                if (allPresets.isEmpty()) {
                    item {
                        Text(
                            "No saved profiles yet. Run AutoEq, then tap Save in the Filters section.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    items(allPresets) { preset ->
                        val isActive = activePreset?.id == preset.id
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .liquidGlass(shape = RoundedCornerShape(10.dp))
                                .bounceClick(onClick = { viewModel.loadPreset(preset.id) })
                        ) {
                            EqProfileMiniGraph(
                                bands = preset.bands,
                                preamp = preset.preamp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp, vertical = 2.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (isActive) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Active",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        preset.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isActive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (preset.description.isNotBlank()) {
                                        Text(
                                            preset.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        "${preset.bands.size} bands · ${preset.targetName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (preset.isCustom) {
                                    IconButton(
                                        onClick = { presetToDelete = preset },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete preset",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.error
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

    if (showSaveDialog) {

        UnglassedDialogTheme {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text("Save EQ Preset") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = saveName,
                            onValueChange = { saveName = it },
                            label = { Text("Preset Name") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = saveDescription,
                            onValueChange = { saveDescription = it },
                            label = { Text("Description") },
                            minLines = 2
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (saveName.isNotEmpty()) {
                                viewModel.saveAsPreset(saveName, saveDescription)
                                showSaveDialog = false
                                saveName = ""
                                saveDescription = ""
                            }
                        }
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
                }
            )
        }
    }

    if (showHelp) {
        AutoEqHelpSheet(onDismiss = { showHelp = false })
    }

    if (showProfileImport) {
        ImportEqProfileSheet(
            maxBandGainDb = EqLimits.AUTOEQ_MAX_BAND_DB,
            perEar = true,
            onDismiss = { showProfileImport = false },
            onImport = { left, right, name ->
                viewModel.importApoProfile(left, right, name)
            },
            onMeasurementDetected = { raw, channel ->
                viewModel.importMeasurementData(raw, channel ?: EqChannel.LEFT)
            },
        )
    }

    if (showHeadphoneSelect) {

        UnglassedDialogTheme {
            AlertDialog(
                onDismissRequest = { showHeadphoneSelect = false },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                ),
                content = {
                    HeadphoneSelectScreen(
                        viewModel = viewModel,
                        channel = if (headphoneSelectForRight) EqChannel.RIGHT else EqChannel.LEFT,
                        onHeadphoneSelected = { showHeadphoneSelect = false },
                        onDismiss = { showHeadphoneSelect = false }
                    )
                }
            )
        }
    }

    if (showTargetNameDialog) {
        UnglassedDialogTheme {
            AlertDialog(
                onDismissRequest = { showTargetNameDialog = false },
                title = { Text("Name Custom Target") },
                text = {
                    OutlinedTextField(
                        value = targetName,
                        onValueChange = { targetName = it },
                        label = { Text("Target Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (targetName.isNotBlank()) {
                                viewModel.importCustomTarget(pendingTargetData, targetName.trim())
                                showTargetNameDialog = false
                                pendingTargetData = ""
                            }
                        }
                    ) { Text("Import") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showTargetNameDialog = false
                        pendingTargetData = ""
                    }) { Text("Cancel") }
                }
            )
        }
    }

    presetToDelete?.let { preset ->
        UnglassedDialogTheme {
            AlertDialog(
                onDismissRequest = { presetToDelete = null },
                title = { Text("Delete Profile") },
                text = { Text("Delete \"${preset.name}\"?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deletePreset(preset.id)
                        presetToDelete = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { presetToDelete = null }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun EqBandSlider(
    band: EqBand,
    onBandChanged: (EqBand) -> Unit,
    modifier: Modifier = Modifier,

    onDelete: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(shape = RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove band",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            FilterType.values().forEach { type ->
                val isSel = band.type == type
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .liquidGlass(
                            shape = RoundedCornerShape(6.dp),
                            tintAlpha = if (isSel) 0.45f else 0.15f,
                        )
                        .bounceClick(onClick = { onBandChanged(band.copy(type = type)) })
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when (type) {
                            FilterType.PEAKING -> "PEAK"
                            FilterType.LOWSHELF -> "LOW-S"
                            FilterType.HIGHSHELF -> "HIGH-S"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Freq", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${band.freq.toInt()} Hz", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        val minLogFreq = kotlin.math.log10(20f)
        val maxLogFreq = kotlin.math.log10(20000f)
        val currentLogFreq = kotlin.math.log10(band.freq.coerceIn(20f, 20000f))
        val freqRatio = (currentLogFreq - minLogFreq) / (maxLogFreq - minLogFreq)
        Slider(
            value = if (freqRatio.isNaN()) 0.5f else freqRatio.coerceIn(0f, 1f),
            onValueChange = {
                val newLog = minLogFreq + it * (maxLogFreq - minLogFreq)
                val newFreq = java.lang.Math.pow(10.0, newLog.toDouble()).toFloat()
                onBandChanged(band.copy(freq = newFreq))
            },
            modifier = Modifier.fillMaxWidth().height(32.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Gain", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${band.gain.roundToInt()} dB",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    band.gain > 0 -> MaterialTheme.colorScheme.primary
                    band.gain < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
        Slider(
            value = if (band.gain.isNaN()) 0f
                    else band.gain.coerceIn(-EqLimits.AUTOEQ_MAX_BAND_DB, EqLimits.AUTOEQ_MAX_BAND_DB),
            onValueChange = { onBandChanged(band.copy(gain = it)) },
            valueRange = -EqLimits.AUTOEQ_MAX_BAND_DB..EqLimits.AUTOEQ_MAX_BAND_DB,
            steps = 2 * EqLimits.AUTOEQ_MAX_BAND_DB.toInt() - 1,
            modifier = Modifier.fillMaxWidth().height(32.dp)
        )

        if (band.q > 0f) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Q-Factor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("%.2f".format(band.q), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            Slider(
                value = if (band.q.isNaN()) 1f else band.q.coerceIn(0.1f, 10f),
                onValueChange = { onBandChanged(band.copy(q = it)) },
                valueRange = 0.1f..10f,
                modifier = Modifier.fillMaxWidth().height(32.dp)
            )
        }
    }
}

@Composable
private fun MeasurementTrailing(
    sample: String?,
    onStep: (forward: Boolean) -> Unit,
    importDescription: String,
    onImport: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (sample != null) {
            SampleStepper(label = sample, onStep = onStep)
            Spacer(modifier = Modifier.width(6.dp))
        }
        IconButton(
            onClick = onImport,
            modifier = Modifier
                .size(44.dp)
                .liquidGlass(shape = RoundedCornerShape(8.dp))
        ) {
            Icon(
                Icons.Default.UploadFile,
                contentDescription = importDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SampleStepper(
    label: String,
    onStep: (forward: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .size(width = 36.dp, height = 44.dp)
            .liquidGlass(shape = RoundedCornerShape(8.dp)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.KeyboardArrowUp,
            contentDescription = "Next sample",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable { onStep(true) }
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = "Previous sample",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable { onStep(false) }
        )
    }
}

private fun formatFreqLabel(freq: Float): String = "${(freq / 1000).toInt()}k"

private fun parseFreqLabel(label: String): Float =
    label.removeSuffix("k").toFloat() * 1000f

private fun buildParametricEqText(
    bands: List<tf.monochrome.android.domain.model.EqBand>,
    preamp: Float,
): String {
    val us = java.util.Locale.US
    val sb = StringBuilder()
    sb.append("Preamp: ").append(String.format(us, "%.1f", preamp)).append(" dB\n")
    var n = 1
    for (b in bands) {
        if (!b.enabled) continue
        val code = when (b.type) {
            tf.monochrome.android.domain.model.FilterType.LOWSHELF -> "LSC"
            tf.monochrome.android.domain.model.FilterType.HIGHSHELF -> "HSC"
            else -> "PK"
        }
        sb.append(String.format(us, "Filter %d: ON %s Fc %.0f Hz Gain %.1f dB Q %.2f\n", n, code, b.freq, b.gain, b.q))
        n++
    }
    return sb.toString()
}
