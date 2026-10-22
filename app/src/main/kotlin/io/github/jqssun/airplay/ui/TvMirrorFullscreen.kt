package io.github.jqssun.airplay.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jqssun.airplay.R
import kotlinx.coroutines.delay

@Composable
internal fun TvMirrorFullscreen(video: @Composable () -> Unit, onExit: () -> Unit, onPip: () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    val home = remember { FocusRequester() }
    val root = remember { FocusRequester() }
    LaunchedEffect(visible, interaction) {
        if (visible) {
            withFrameNanos { }; if (interaction == 0) home.requestFocus()
            delay(8000); visible = false; root.requestFocus()
        }
    }
    BackHandler { if (visible) { visible = false; root.requestFocus() } else onExit() }
    Box(Modifier.fillMaxSize().background(Color.Black).focusRequester(root).onPreviewKeyEvent { e ->
        if (e.key in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter, Key.Enter)) {
            if (!visible) {
                if (e.type == KeyEventType.KeyDown) { visible = true; interaction = 0 }
                true
            } else { if (e.type == KeyEventType.KeyDown) interaction++; false }
        } else false
    }.focusable(), contentAlignment = Alignment.Center) {
        video()
        if (visible) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF5101826))))
            .padding(horizontal = 48.dp, vertical = 27.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                TvAction(stringResource(R.string.tv_back_home), onExit, Modifier.width(260.dp).focusRequester(home), icon = Icons.Default.Home)
                TvAction(stringResource(R.string.cd_pip), onPip, Modifier.width(200.dp), icon = Icons.Default.PictureInPictureAlt)
            }
            Text(stringResource(R.string.tv_remote_hint), color = TvMuted, fontSize = 15.sp)
        }
    }
}
