package com.chuankangkk.wrenchlift.ui.worker

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val palette: WrenchPalette
    @Composable get() = LocalWrenchPalette.current

/** 面板：低对比底 + 发丝线描边，不做厚重卡片阴影。 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(WrenchDimens.panelShape)
            .background(palette.panel)
            .border(WrenchDimens.hairline, palette.hairline, WrenchDimens.panelShape)
            .padding(padding),
        content = content,
    )
}

enum class ButtonTone { Accent, Danger, Neutral }

/** 控制柱按钮：大触控、少字、图标 + 文字。 */
@Composable
fun ControlButton(
    text: String,
    icon: WrenchIcon?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Neutral,
    enabled: Boolean = true,
    filled: Boolean = tone != ButtonTone.Neutral,
    height: Dp = WrenchDimens.motionButton,
) {
    val p = palette
    val container = when {
        !enabled -> p.disabledFill
        filled && tone == ButtonTone.Accent -> p.accent
        filled && tone == ButtonTone.Danger -> p.danger
        else -> p.panel
    }
    val content = when {
        !enabled -> p.disabledText
        filled && tone == ButtonTone.Accent -> p.onAccent
        filled && tone == ButtonTone.Danger -> p.onDanger
        tone == ButtonTone.Danger -> p.danger
        tone == ButtonTone.Accent -> p.accent
        else -> p.textPrimary
    }
    val border = when {
        !enabled -> p.hairline
        filled -> Color.Transparent
        tone == ButtonTone.Danger -> p.danger
        tone == ButtonTone.Accent -> p.accent
        else -> p.hairline
    }
    val animatedContainer by animateColorAsState(container, tween(160), label = "button")
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = height),
        shape = WrenchDimens.buttonShape,
        border = BorderStroke(if (filled && enabled) 0.dp else 1.dp, border),
        colors = ButtonDefaults.buttonColors(
            containerColor = animatedContainer, contentColor = content,
            disabledContainerColor = p.disabledFill, disabledContentColor = p.disabledText,
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        elevation = null,
    ) {
        if (icon != null) {
            LineIcon(icon, content, size = 20.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = WrenchType.buttonStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 顶栏/页面里的小状态条：圆点 + 白话。 */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: WrenchIcon? = null,
    onClick: (() -> Unit)? = null,
) {
    val p = palette
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .heightIn(min = WrenchDimens.touch)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(shape)
                .background(color.copy(alpha = if (p.isDark) 0.16f else 0.10f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) LineIcon(icon, color, size = 16.dp) else Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(text, style = WrenchType.bodyStrong.copy(fontSize = 14.sp), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 大号读数：宽度不够时自动缩字号，绝不裁切数字。 */
@Composable
fun FitNumber(text: String, maxSize: TextUnit, color: Color, modifier: Modifier = Modifier, minScale: Float = 0.5f) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val widthPx = with(density) { maxWidth.toPx() }
        val size = remember(text, maxSize, widthPx) {
            var scale = 1f
            while (scale > minScale) {
                val style = WrenchType.number(maxSize * scale)
                val measured = measurer.measure(text, style, maxLines = 1, softWrap = false)
                if (measured.size.width <= widthPx) break
                scale -= 0.04f
            }
            maxSize * scale
        }
        Text(text, style = WrenchType.number(size), color = color, maxLines = 1, softWrap = false)
    }
}

/** 开关整行热区 ≥ 48dp。 */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    icon: WrenchIcon? = null,
    titleColor: Color = palette.textPrimary,
) {
    val p = palette
    Row(
        modifier
            .heightIn(min = WrenchDimens.touch)
            .clip(WrenchDimens.buttonShape)
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            LineIcon(icon, if (enabled) titleColor else p.disabledText, size = 20.dp)
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = WrenchType.buttonStyle, color = if (enabled) titleColor else p.disabledText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = WrenchType.captionStyle, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = p.accent, checkedThumbColor = p.onAccent,
                uncheckedTrackColor = p.panelSunken, uncheckedThumbColor = p.textTertiary, uncheckedBorderColor = p.hairline,
            ),
        )
    }
}

@Composable
fun WrenchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    numeric: Boolean = false,
    decimal: Boolean = false,
    placeholder: String? = null,
) {
    val p = palette
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1) },
        placeholder = placeholder?.let { { Text(it, maxLines = 1) } },
        singleLine = true,
        enabled = enabled,
        textStyle = WrenchType.number(16.sp, androidx.compose.ui.text.font.FontWeight.Normal).copy(color = p.textPrimary),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else if (numeric) KeyboardType.Number else KeyboardType.Text),
        shape = WrenchDimens.buttonShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = p.accent, unfocusedBorderColor = p.hairline, focusedLabelColor = p.accent,
            unfocusedLabelColor = p.textSecondary, cursorColor = p.accent,
            focusedContainerColor = p.panel, unfocusedContainerColor = p.panel, disabledContainerColor = p.panelSunken,
        ),
        modifier = modifier.heightIn(min = 56.dp),
    )
}

/** 确认对话框：语义不弱化，按钮少。 */
@Composable
fun WrenchDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmTone: ButtonTone = ButtonTone.Accent,
    dismissText: String? = "取消",
    extra: (@Composable RowScope.() -> Unit)? = null,
    body: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val p = palette
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(WrenchDimens.sheetRadius),
        containerColor = p.panel,
        titleContentColor = p.textPrimary,
        textContentColor = p.textSecondary,
        title = { Text(title, style = WrenchType.headlineStyle.copy(fontSize = 19.sp)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message, style = WrenchType.bodyStyle.copy(fontSize = 16.sp, lineHeight = 23.sp), color = p.textSecondary)
                body?.invoke(this)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                extra?.invoke(this)
                ControlButton(confirmText, null, onConfirm, tone = confirmTone, height = WrenchDimens.touch)
            }
        },
        dismissButton = dismissText?.let {
            {
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = WrenchDimens.touch)) {
                    Text(it, style = WrenchType.buttonStyle, color = p.textSecondary)
                }
            }
        },
    )
}

/** 次级页面顶栏：返回 + 标题 + 右侧动作。 */
@Composable
fun PageTopBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    val p = palette
    Row(
        modifier.fillMaxWidth().heightIn(min = WrenchDimens.topBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) BackButton(onBack)
        Text(title, style = WrenchType.headlineStyle.copy(fontSize = 19.sp), color = p.textPrimary, modifier = Modifier.weight(1f).padding(start = if (onBack == null) 4.dp else 0.dp), maxLines = 1)
        actions()
    }
}

@Composable
fun BackButton(onBack: () -> Unit, label: String = "返回", modifier: Modifier = Modifier) {
    val p = palette
    Row(
        modifier
            .heightIn(min = WrenchDimens.touch)
            .clip(WrenchDimens.buttonShape)
            .clickable(role = Role.Button, onClick = onBack)
            .padding(start = 2.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(WrenchDimens.touch, WrenchDimens.touch), contentAlignment = Alignment.Center) {
            LineIcon(WrenchIcon.Back, p.textPrimary, size = 22.dp)
        }
        Text(label, style = WrenchType.buttonStyle, color = p.textPrimary)
    }
}

@Composable
fun IconAction(icon: WrenchIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = palette
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = WrenchDimens.touch),
        shape = WrenchDimens.buttonShape,
        border = BorderStroke(1.dp, p.hairline),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        LineIcon(icon, p.textPrimary, size = 18.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, style = WrenchType.buttonStyle, color = p.textPrimary)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = WrenchType.captionStyle.copy(fontSize = 13.sp), color = palette.textSecondary, modifier = modifier)
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(palette.hairline))
}
