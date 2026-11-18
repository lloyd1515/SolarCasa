package ro.solarcasa.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import ro.solarcasa.R
import kotlin.math.roundToInt

/** Actions of the top bar on every screen (the "Alerte" bell). The app provides them. */
val LocalTopBarActions = staticCompositionLocalOf<@Composable RowScope.() -> Unit> { {} }

/** Standard screen: top bar, scrollable content limited to 720 dp on tablets, space for the assistant button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Înapoi") }
                }
            },
            actions = LocalTopBarActions.current,
        )
        Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 720.dp).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
                Spacer(Modifier.height(96.dp))
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 8.dp).semantics { heading() },
    )
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    @DrawableRes icon: Int? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (icon != null) Icon(painterResource(icon), contentDescription = null)
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                }
            }
            content()
        }
    }
}

/**
 * Card for AI content: always marked with ✦, with "De ce?" (explanation, confidence)
 * and the user's choices (Aplică / Modifică / Ignoră) in [actions].
 */
@Composable
fun AiCard(
    text: String,
    modifier: Modifier = Modifier,
    title: String = "Asistentul Solar",
    why: String? = null,
    confidence: Int? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    var showWhy by rememberSaveable { mutableStateOf(false) }
    InfoCard(modifier, title, R.drawable.ic_auto_awesome, MaterialTheme.colorScheme.tertiaryContainer) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        if (confidence != null) Tag("Încredere $confidence%", MaterialTheme.colorScheme.tertiary)
        if (showWhy && why != null) {
            Text(why, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            actions()
            if (why != null) {
                TextButton(onClick = { showWhy = !showWhy }) { Text(if (showWhy) "Ascunde explicația" else "De ce?") }
            }
        }
    }
}

/** Short label. The text carries the meaning; the color only supports it. */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun KeyValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** Main action of a screen: full width, 56 dp high, in the thumb zone (Fitts' law). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun <T> SegmentedChoice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label(option), maxLines = 2) }
        }
    }
}

/**
 * Confirmation for critical actions: slide the handle to the end.
 * Accessible alternatives (WCAG 2.5.1): press and hold for 1 second, or the TalkBack "activate" action.
 */
@Composable
fun SlideToConfirm(text: String, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    val handle = 56.dp
    val handlePx = with(LocalDensity.current) { handle.toPx() }
    var width by remember { mutableIntStateOf(0) }
    var offset by remember { mutableFloatStateOf(0f) }
    val max = (width - handlePx).coerceAtLeast(1f)
    val hint by rememberInfiniteTransition(label = "hint").animateFloat(
        0.4f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "hintAlpha",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), CircleShape)
                .onSizeChanged { width = it.width }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        var released = false
                        withTimeoutOrNull(1_000) { waitForUpOrCancellation(); released = true }
                        if (!released) onConfirm()
                    }
                }
                .semantics {
                    role = Role.Button
                    onClick(label = text) { onConfirm(); true }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                "$text  »",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center).alpha(hint),
            )
            Box(
                Modifier
                    .offset { IntOffset(offset.roundToInt(), 0) }
                    .padding(4.dp)
                    .size(handle)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .draggable(
                        rememberDraggableState { delta -> offset = (offset + delta).coerceIn(0f, max) },
                        Orientation.Horizontal,
                        onDragStopped = {
                            if (offset > max * 0.85f) onConfirm()
                            offset = 0f
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_keyboard_double_arrow_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Text("sau ține apăsat 1 secundă", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}
