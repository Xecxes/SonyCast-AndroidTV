package io.github.jqssun.airplay.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jqssun.airplay.R
import io.github.jqssun.airplay.service.AirPlayService.ServerState
import io.github.jqssun.airplay.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.NetworkInterface

private enum class TvPage(val title: Int, val icon: ImageVector) {
    HOME(R.string.tv_home, Icons.Default.Cast),
    GUIDE(R.string.tv_guide, Icons.Default.HelpOutline),
    SETTINGS(R.string.tab_settings, Icons.Default.Settings),
    LOGS(R.string.tab_logs, Icons.AutoMirrored.Filled.Article)
}

@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun TvDashboard(viewModel: MainViewModel, video: @Composable () -> Unit, onFullscreen: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(TvPage.HOME) }
    val navFocus = remember { List(TvPage.entries.size) { FocusRequester() } }
    val contentFocus = remember { List(TvPage.entries.size) { FocusRequester() } }
    var navHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(page) { withFrameNanos { }; contentFocus[page.ordinal].requestFocus() }
    BackHandler(page != TvPage.HOME || !navHasFocus) {
        if (page != TvPage.HOME) page = TvPage.HOME else navFocus[0].requestFocus()
    }
    Row(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(TvBackground, Color(0xFF111F30))))
        .padding(horizontal = 48.dp, vertical = 27.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.width(168.dp).fillMaxHeight().onFocusChanged { navHasFocus = it.hasFocus },
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.padding(bottom = 32.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.CastConnected, null, Modifier.size(30.dp), tint = TvAccent)
                Text(stringResource(R.string.app_name), color = TvText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            }
            TvPage.entries.forEach { item ->
                TvAction(stringResource(item.title), { page = item }, icon = item.icon,
                    selected = page == item, modifier = Modifier.fillMaxWidth()
                        .focusRequester(navFocus[item.ordinal])
                        .focusProperties {
                            left = FocusRequester.Cancel
                            right = contentFocus[page.ordinal]
                            if (item.ordinal == 0) up = FocusRequester.Cancel
                            if (item.ordinal == TvPage.entries.lastIndex) down = FocusRequester.Cancel
                        })
            }
            Spacer(Modifier.weight(1f))
            Text("AirPlay + DLNA", color = TvMuted, fontSize = 14.sp)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (page) {
                    TvPage.HOME -> TvCastHome(viewModel, video, contentFocus[0], navFocus[0],
                        onGuide = { page = TvPage.GUIDE }, onFullscreen = onFullscreen)
                    TvPage.GUIDE -> TvConnectionGuide(contentFocus[1], navFocus[1]) { page = TvPage.HOME }
                    TvPage.SETTINGS -> TvSettingsScreen(viewModel, contentFocus[2], navFocus[2])
                    TvPage.LOGS -> TvLogs(viewModel, contentFocus[3], navFocus[3])
                }
            }
            Text(stringResource(R.string.tv_remote_hint), color = TvMuted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TvCastHome(vm: MainViewModel, video: @Composable () -> Unit, first: FocusRequester,
                       nav: FocusRequester, onGuide: () -> Unit, onFullscreen: () -> Unit) {
    val name by vm.serverName.collectAsState()
    val state by vm.serverState.collectAsState()
    val connections by vm.connectionCount.collectAsState()
    val dlna by vm.dlnaEnabled.collectAsState()
    val audio by vm.audioOnly.collectAsState()
    var confirmStop by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    LaunchedEffect(state) {
        address = withContext(Dispatchers.IO) {
            runCatching { NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }.firstOrNull { it is java.net.Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress.orEmpty() }.getOrDefault("")
        }
    }
    val status = stringResource(when (state) {
        ServerState.RUNNING -> R.string.tv_ready_status
        ServerState.ERROR -> R.string.tv_error_status
        else -> R.string.tv_stopped_status
    })
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).background(if (state == ServerState.RUNNING) TvAccent else Color(0xFFFFBA95), CircleShape))
            Text(status, color = TvAccent, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            if (address.isNotEmpty()) Text(stringResource(R.string.tv_status_address, address), color = TvMuted, fontSize = 14.sp)
        }
        if (connections > 0 && audio) {
            TvAudioHome(vm, first, nav, Modifier.weight(1f))
        } else if (connections > 0 && state == ServerState.RUNNING) {
            Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black, RoundedCornerShape(16.dp))) { video() }
            TvAction(stringResource(R.string.tv_view_fullscreen), onFullscreen, icon = Icons.Default.Fullscreen,
                modifier = Modifier.width(250.dp).focusRequester(first).focusProperties { left = nav })
        } else {
            Column(Modifier.weight(1f).fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF1B3540), TvPanel)), RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFF3A5662), RoundedCornerShape(20.dp)).padding(28.dp),
                verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.Cast, null, Modifier.size(42.dp), tint = TvAccent)
                Spacer(Modifier.height(12.dp))
                Text(if (state == ServerState.RUNNING) stringResource(R.string.tv_ready) else status,
                    color = TvText, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.tv_choose_device), color = TvMuted, fontSize = 16.sp)
                Text(name, color = TvText, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.tv_protocol_status, "AirPlay", if (dlna) "DLNA" else stringResource(R.string.tv_off)),
                    color = TvAccent, fontSize = 16.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvAction(stringResource(if (state == ServerState.RUNNING) R.string.tv_guide else R.string.tv_start),
                    { if (state == ServerState.RUNNING) onGuide() else vm.startServer() },
                    icon = if (state == ServerState.RUNNING) Icons.Default.HelpOutline else Icons.Default.PlayArrow,
                    modifier = Modifier.weight(1f).focusRequester(first).focusProperties { left = nav })
                TvAction(stringResource(if (state == ServerState.RUNNING) R.string.tv_stop else R.string.tv_guide),
                    { if (state == ServerState.RUNNING) confirmStop = true else onGuide() },
                    icon = if (state == ServerState.RUNNING) Icons.Default.Stop else Icons.Default.HelpOutline,
                    modifier = Modifier.weight(1f))
            }
        }
        Text(stringResource(R.string.tv_network_hint), color = TvMuted, fontSize = 15.sp)
    }
    if (confirmStop) TvConfirm(stringResource(R.string.tv_stop_confirm), stringResource(R.string.tv_stop_confirm_desc),
        onDismiss = { confirmStop = false }, onConfirm = { vm.stopServer(); confirmStop = false })
}

@Composable
private fun TvAudioHome(vm: MainViewModel, first: FocusRequester, nav: FocusRequester, modifier: Modifier) {
    val track by vm.trackInfo.collectAsState()
    val playing by vm.playing.collectAsState()
    val pos by vm.positionMs.collectAsState()
    val duration by vm.durationMs.collectAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        TvHeading(stringResource(R.string.tv_now_playing))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(180.dp).background(TvPanel, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                if (track.coverArt != null) Image(track.coverArt!!.asImageBitmap(), null,
                    Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Icon(Icons.Default.MusicNote, null, Modifier.size(72.dp), tint = TvAccent)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(track.title.ifBlank { stringResource(R.string.unknown_track) }, color = TvText,
                    fontSize = 28.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = TvMuted, fontSize = 20.sp, maxLines = 1)
                if (duration > 0) LinearProgressIndicator(progress = { (pos.toFloat() / duration).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(), color = TvAccent, trackColor = Color(0xFF43536B))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvAction(stringResource(R.string.tv_previous), vm::dacpPrev, icon = Icons.Default.SkipPrevious,
                modifier = Modifier.weight(1f).focusProperties { left = nav })
            TvAction(stringResource(if (playing) R.string.tv_pause else R.string.tv_play), vm::dacpPlayPause,
                icon = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                modifier = Modifier.weight(1f).focusRequester(first))
            TvAction(stringResource(R.string.tv_next), vm::dacpNext, icon = Icons.Default.SkipNext, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun TvConnectionGuide(first: FocusRequester, nav: FocusRequester, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TvHeading(stringResource(R.string.tv_guide), stringResource(R.string.tv_guide_subtitle))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GuideCard("01", R.string.tv_airplay_title, R.string.tv_airplay_desc, Modifier.weight(1f))
            GuideCard("02", R.string.tv_dlna_title, R.string.tv_dlna_desc, Modifier.weight(1f))
        }
        Text(stringResource(R.string.tv_network_desc), color = TvMuted, fontSize = 15.sp)
        TvAction(stringResource(R.string.tv_back_home), onHome, icon = Icons.Default.Home,
            modifier = Modifier.width(260.dp).focusRequester(first).focusProperties { left = nav })
    }
}

@Composable
private fun GuideCard(number: String, title: Int, description: Int, modifier: Modifier) {
    Column(modifier.fillMaxHeight().background(TvPanel, RoundedCornerShape(16.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(number, color = TvAccent, fontSize = 32.sp, fontWeight = FontWeight.Light)
        Text(stringResource(title), color = TvText, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(description), color = TvMuted, fontSize = 17.sp, lineHeight = 26.sp)
    }
}

@Composable
private fun TvLogs(vm: MainViewModel, first: FocusRequester, nav: FocusRequester) {
    val logs by vm.logs.collectAsState()
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var viewportFocus by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvHeading(stringResource(R.string.tab_logs), stringResource(R.string.tv_logs_hint))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvAction(stringResource(R.string.btn_export), vm::exportLogs, icon = Icons.Default.SaveAlt,
                modifier = Modifier.width(180.dp).focusRequester(first).focusProperties { left = nav })
            TvAction(stringResource(R.string.btn_clear), { confirmClear = true }, modifier = Modifier.width(160.dp))
        }
        LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth()
            .background(TvPanel, RoundedCornerShape(12.dp))
            .border(if (viewportFocus) 3.dp else 1.dp, if (viewportFocus) TvAccent else Color(0xFF485B73), RoundedCornerShape(12.dp))
            .focusProperties { left = nav }.onFocusChanged { viewportFocus = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) false
                else when (e.key) {
                    Key.DirectionDown -> { scope.launch { list.scroll { scrollBy(90f) } }; true }
                    Key.DirectionUp -> if (list.canScrollBackward) { scope.launch { list.scroll { scrollBy(-90f) } }; true } else false
                    else -> false
                }
            }.focusable(), contentPadding = PaddingValues(18.dp)) {
            if (logs.isEmpty()) item { Text(stringResource(R.string.tv_empty_logs), color = TvMuted, fontSize = 16.sp) }
            itemsIndexed(logs.takeLast(500)) { _, line -> Text(line, color = TvText, fontSize = 14.sp,
                fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 5.dp)) }
        }
    }
    if (confirmClear) TvConfirm(stringResource(R.string.tv_clear_confirm), "", { confirmClear = false },
        { vm.clearLogs(); confirmClear = false })
}

@Composable
internal fun TvConfirm(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val cancel = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; cancel.requestFocus() }
    TvModal(title, onDismiss) {
        if (message.isNotEmpty()) Text(message, color = TvMuted, fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvAction(stringResource(R.string.btn_cancel), onDismiss, Modifier.weight(1f).focusRequester(cancel))
            TvAction(stringResource(R.string.btn_ok), onConfirm, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun TvNotice(title: String, message: String, onDismiss: () -> Unit, largeMessage: Boolean = false) {
    val action = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; action.requestFocus() }
    TvModal(title, onDismiss) {
        Text(message, color = TvText, fontSize = if (largeMessage) 40.sp else 18.sp)
        TvAction(stringResource(R.string.btn_ok), onDismiss, Modifier.fillMaxWidth().focusRequester(action))
    }
}

@Composable
internal fun TvOnboarding(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; first.requestFocus() }
    TvModal(stringResource(R.string.onboard_title), onDone) {
        Text(stringResource(R.string.onboard_desc), color = TvMuted, fontSize = 18.sp)
        TvAction(stringResource(R.string.tv_permissions), { openTvPermissions(ctx) }, Modifier.fillMaxWidth())
        TvAction(stringResource(R.string.onboard_done), onDone, Modifier.fillMaxWidth().focusRequester(first))
    }
}
