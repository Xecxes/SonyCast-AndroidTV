package io.github.jqssun.airplay.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal val TvBackground = Color(0xFF0A101B)
internal val TvPanel = Color(0xFF141E2D)
internal val TvText = Color(0xFFF4F7FC)
internal val TvMuted = Color(0xFFBAC8DC)
internal val TvAccent = Color(0xFF83F2D1)
internal val TvInk = Color(0xFF101B2C)

/** One focus target per action, with focus distinct from the persistent selection. */
@Composable
internal fun TvAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supporting: String? = null,
    value: String? = null,
    selected: Boolean? = null,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.025f else 1f, label = "tvFocusScale")
    val shape = RoundedCornerShape(12.dp)
    val fg = if (focused) TvInk else TvText
    val secondary = if (focused) Color(0xFF34465A) else TvMuted
    Row(
        modifier.graphicsLayer {
            scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.45f
        }.onFocusChanged { focused = it.isFocused }
            .background(if (focused) TvText else if (selected == true) Color(0xFF213D45) else Color(0xFF1D2B3D), shape)
            .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else if (selected == true) TvAccent else Color(0xFF485B73), shape)
            .semantics { if (selected != null) this.selected = selected }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = if (supporting != null) 76.dp else 52.dp)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            if (icon != null) Icon(icon, null, Modifier.size(24.dp))
            Column(Modifier.weight(1f, fill = value != null || supporting != null)) {
                Text(label, color = fg, fontSize = if (supporting != null) 19.sp else 17.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (supporting != null) Text(supporting, color = secondary, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            if (value != null) Text(value, color = if (focused) TvInk else TvAccent, fontSize = 16.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 190.dp))
        }
    }
}

@Composable
internal fun TvHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = TvText, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) Text(subtitle, color = TvMuted, fontSize = 15.sp, maxLines = 2)
    }
}

@Composable
internal fun TvModal(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(560.dp).heightIn(max = 450.dp)
            .background(TvPanel, RoundedCornerShape(20.dp))
            .border(1.dp, Color(0xFF647890), RoundedCornerShape(20.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, color = TvText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
