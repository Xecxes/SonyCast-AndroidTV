package io.github.jqssun.airplay.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.mirror.MirrorSenderActivity
import io.github.jqssun.airplay.viewmodel.MainViewModel
import kotlinx.coroutines.launch

private data class TvSetting(val title: Int, val desc: Int, val value: String, val onClick: () -> Unit)
private data class TvEditor(val title: Int, val value: String,
    val choices: List<Pair<String, String>>? = null, val numeric: Boolean = false,
    val validate: (String) -> Boolean = { it.isNotBlank() }, val save: (String) -> Unit)

@Composable
fun TvSettingsScreen(vm: MainViewModel, first: FocusRequester, nav: FocusRequester) {
    var category by rememberSaveable { mutableIntStateOf(0) }
    val categoryFocus = remember(first) { listOf(first, FocusRequester(), FocusRequester(), FocusRequester()) }
    val firstRow = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    LaunchedEffect(category) { list.scrollToItem(0) }
    var editor by remember { mutableStateOf<TvEditor?>(null) }
    var restart by remember { mutableStateOf(false) }
    var lastRow by remember { mutableStateOf<FocusRequester?>(null) }
    fun restoreRow() { scope.launch { withFrameNanos { }; lastRow?.requestFocus() } }
    val ctx = LocalContext.current
    val on = stringResource(R.string.tv_on)
    val off = stringResource(R.string.tv_off)
    val name by vm.serverName.collectAsState()
    val autoStart by vm.autoStart.collectAsState()
    val boot by vm.bootAutoStart.collectAsState()
    val dlna by vm.dlnaEnabled.collectAsState()
    val mirror by vm.mirrorEnabled.collectAsState()
    val autoFull by vm.autoFullscreen.collectAsState()
    val launch by vm.launchOnConnect.collectAsState()
    val autoAudio by vm.autoAudioMode.collectAsState()
    val pin by vm.requirePin.collectAsState()
    val newConnections by vm.allowNewConn.collectAsState()
    val port by vm.serverPort.collectAsState()
    val resolution by vm.resolution.collectAsState()
    val fps by vm.maxFps.collectAsState()
    val overscan by vm.overscanned.collectAsState()
    val h265 by vm.h265Enabled.collectAsState()
    val alac by vm.alacEnabled.collectAsState()
    val aac by vm.aacEnabled.collectAsState()
    val swAlac by vm.swAlacEnabled.collectAsState()
    val latency by vm.audioLatencyMs.collectAsState()
    val developer by vm.developerOptions.collectAsState()
    val debug by vm.debugEnabled.collectAsState()
    val drop by vm.keyAllowFrameDrop.collectAsState()
    val sdr by vm.enforceSdr.collectAsState()
    val realtime by vm.realtimeDecoderPriority.collectAsState()
    val rateHint by vm.operatingRateHint.collectAsState()
    val scheduled by vm.scheduledOutputBufferRelease.collectAsState()
    val benchmark by vm.benchmarkLog.collectAsState()
    val buffer by vm.audioBufferMultiplier.collectAsState()
    val entries = mutableListOf<TvSetting>()
    fun flag(title: Int, desc: Int, value: Boolean, save: (Boolean) -> Unit) {
        entries += TvSetting(title, desc, if (value) "✓ $on" else off) { save(!value) }
    }
    fun text(title: Int, desc: Int, value: String, numeric: Boolean = false,
             validate: (String) -> Boolean = { it.isNotBlank() }, save: (String) -> Unit) {
        entries += TvSetting(title, desc, value) { editor = TvEditor(title, value, numeric = numeric, validate = validate, save = save) }
    }
    fun choice(title: Int, desc: Int, value: String, choices: List<Pair<String, String>>, save: (String) -> Unit) {
        entries += TvSetting(title, desc, choices.firstOrNull { it.first == value }?.second ?: value) {
            editor = TvEditor(title, value, choices = choices, save = save)
        }
    }
    when (category) {
        0 -> {
            text(R.string.setting_server_name, R.string.setting_server_name_desc, name) { vm.setServerName(it.trim()) }
            flag(R.string.setting_auto_start, R.string.setting_auto_start_desc, autoStart, vm::setAutoStart)
            flag(R.string.setting_boot_auto_start, R.string.setting_boot_auto_start_desc, boot, vm::setBootAutoStart)
            flag(R.string.setting_dlna, R.string.setting_dlna_desc, dlna, vm::setDlnaEnabled)
            flag(R.string.setting_auto_fullscreen, R.string.setting_auto_fullscreen_desc, autoFull, vm::setAutoFullscreen)
            flag(R.string.setting_launch_on_connect, R.string.setting_launch_on_connect_desc, launch, vm::setLaunchOnConnect)
            entries += TvSetting(R.string.tv_permissions, R.string.tv_permissions_desc, "›") { openTvPermissions(ctx) }
        }
        1 -> {
            choice(R.string.setting_resolution, R.string.setting_resolution_desc, resolution,
                listOf("auto" to stringResource(R.string.setting_resolution_auto), "1280x720" to "720p",
                    "1920x1080" to "1080p", "3840x2160" to "4K"), vm::setResolution)
            choice(R.string.setting_max_fps, R.string.setting_max_fps_desc, fps.toString(),
                listOf("24", "30", "60", "120").map { it to "$it fps" }) { vm.setMaxFps(it.toInt()) }
            flag(R.string.setting_overscanned, R.string.setting_overscanned_desc, overscan, vm::setOverscanned)
            flag(R.string.setting_h265, R.string.setting_h265_desc, h265, vm::setH265Enabled)
            flag(R.string.setting_alac, R.string.setting_alac_desc, alac, vm::setAlacEnabled)
            flag(R.string.setting_aac, R.string.setting_aac_desc, aac, vm::setAacEnabled)
            flag(R.string.setting_sw_alac, R.string.setting_sw_alac_desc, swAlac, vm::setSwAlacEnabled)
            flag(R.string.setting_auto_audio_mode, R.string.setting_auto_audio_mode_desc, autoAudio, vm::setAutoAudioMode)
        }
        2 -> {
            flag(R.string.setting_require_pin, R.string.setting_require_pin_desc, pin, vm::setRequirePin)
            flag(R.string.setting_allow_new_conn, R.string.setting_allow_new_conn_desc, newConnections, vm::setAllowNewConn)
            flag(R.string.setting_mirror, R.string.setting_mirror_desc, mirror, vm::setMirrorEnabled)
            text(R.string.setting_server_port, R.string.setting_server_port_desc, port.toString(), true,
                { it.toIntOrNull()?.let { p -> p in 1..65535 } == true }) { vm.setServerPort(it.toInt()) }
        }
        3 -> {
            flag(R.string.setting_developer_options, R.string.setting_developer_options_desc, developer, vm::setDeveloperOptions)
            flag(R.string.setting_debug_overlay, R.string.setting_debug_overlay_desc, debug, vm::setDebugEnabled)
            if (developer) {
                flag(R.string.setting_key_allow_frame_drop, R.string.setting_key_allow_frame_drop_desc, drop, vm::setKeyAllowFrameDrop)
                flag(R.string.setting_enforce_sdr, R.string.setting_enforce_sdr_desc, sdr, vm::setEnforceSdr)
                flag(R.string.setting_realtime_decoder_priority, R.string.setting_realtime_decoder_priority_desc, realtime, vm::setRealtimeDecoderPriority)
                flag(R.string.setting_operating_rate_hint, R.string.setting_operating_rate_hint_desc, rateHint, vm::setOperatingRateHint)
                flag(R.string.setting_scheduled_output_buffer_release, R.string.setting_scheduled_output_buffer_release_desc, scheduled, vm::setScheduledOutputBufferRelease)
                choice(R.string.setting_audio_delay, R.string.setting_audio_delay_desc, latency.toString(),
                    listOf("-1" to off) + listOf(0, 100, 150, 250, 500, 750, 1000).map { it.toString() to "$it ms" }) { vm.setAudioLatencyMs(it.toInt()) }
                choice(R.string.setting_audio_buffer_multiplier, R.string.setting_audio_buffer_multiplier_desc, buffer.toString(),
                    (4..8).map { it.toString() to "${it}×" }) { vm.setAudioBufferMultiplier(it.toInt()) }
                flag(R.string.setting_benchmark_log, R.string.setting_benchmark_log_desc, benchmark, vm::setBenchmarkLog)
            }
            entries += TvSetting(R.string.mirror_button, R.string.setting_mirror_desc, "›") {
                ctx.startActivity(Intent(ctx, MirrorSenderActivity::class.java))
            }
        }
    }
    entries += TvSetting(R.string.tv_restart, R.string.tv_restart_desc, "↻") { restart = true }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvHeading(stringResource(R.string.tab_settings), stringResource(R.string.tv_settings_hint))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(R.string.tv_common, R.string.tv_picture_audio, R.string.tv_connection, R.string.tv_advanced).forEachIndexed { index, label ->
                TvAction(stringResource(label), { category = index }, selected = category == index,
                    modifier = Modifier.weight(1f).focusRequester(categoryFocus[index]).onPreviewKeyEvent { event ->
                        if (event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown) {
                            scope.launch { list.scrollToItem(0); withFrameNanos { }; firstRow.requestFocus() }
                            true
                        } else false
                    }.focusProperties {
                        if (index == 0) left = nav
                    })
            }
        }
        key(category) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(entries, key = { _, row -> row.title }) { index, setting ->
                    val rowFocus = remember { if (index == 0) firstRow else FocusRequester() }
                    TvAction(stringResource(setting.title), { lastRow = rowFocus; setting.onClick() },
                        supporting = stringResource(setting.desc), value = setting.value,
                        modifier = Modifier.fillMaxWidth().focusRequester(rowFocus).focusProperties {
                            left = nav
                            if (index == 0) up = categoryFocus[category]
                        })
                }
            }
        }
    }
    editor?.let { current ->
        TvSettingEditor(current, onDismiss = { editor = null; restoreRow() }, onSave = {
            current.save(it); editor = null; restoreRow()
        })
    }
    if (restart) TvConfirm(stringResource(R.string.tv_restart), stringResource(R.string.tv_restart_desc),
        { restart = false; restoreRow() }, {
            restart = false
            vm.stopServer()
            // This scope belongs to the settings page; restart is immediate and
            // completes before a navigation event can dispose the page.
            vm.startServer()
            restoreRow()
        })
}

@Composable
private fun TvSettingEditor(editor: TvEditor, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val initial = remember { FocusRequester() }
    var text by remember { mutableStateOf(editor.value) }
    val valid = editor.validate(text.trim())
    LaunchedEffect(Unit) { withFrameNanos { }; initial.requestFocus() }
    TvModal(stringResource(editor.title), onDismiss) {
        if (editor.choices != null) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val selectedIndex = editor.choices.indexOfFirst { it.first == editor.value }.coerceAtLeast(0)
                editor.choices.forEachIndexed { index, (value, label) ->
                    TvAction(label, { onSave(value) }, selected = value == editor.value,
                        value = if (value == editor.value) "✓" else "",
                        modifier = Modifier.fillMaxWidth().then(if (index == selectedIndex) Modifier.focusRequester(initial) else Modifier))
                }
            }
            TvAction(stringResource(R.string.btn_cancel), onDismiss, Modifier.fillMaxWidth())
        } else {
            OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().focusRequester(initial),
                singleLine = true, isError = !valid,
                keyboardOptions = KeyboardOptions(keyboardType = if (editor.numeric) KeyboardType.Number else KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onSave(text.trim()) }),
                supportingText = { if (!valid) Text(stringResource(R.string.tv_invalid_value)) })
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvAction(stringResource(R.string.btn_cancel), onDismiss, Modifier.weight(1f))
                TvAction(stringResource(R.string.btn_save), { onSave(text.trim()) }, Modifier.weight(1f), enabled = valid)
            }
        }
    }
}

internal fun openTvPermissions(ctx: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
    runCatching { ctx.startActivity(intent) }.onFailure {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
    }
}
