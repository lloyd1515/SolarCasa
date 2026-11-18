package ro.solarcasa.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Series(val label: String, val values: List<Double>, val color: Color)

/** Grouped bars. [highlight] marks groups with a warning background, [hatched] marks incomplete data. */
@Composable
fun BarChart(
    labels: List<String>,
    series: List<Series>,
    description: String,
    modifier: Modifier = Modifier,
    highlight: Set<Int> = emptySet(),
    hatched: Set<Int> = emptySet(),
    height: Dp = 180.dp,
) {
    val warn = MaterialTheme.colorScheme.errorContainer
    val axis = MaterialTheme.colorScheme.outline
    val max = series.maxOf { it.values.max() }.coerceAtLeast(1.0)
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val group = size.width / labels.size
            val bar = group * 0.8f / series.size
            labels.indices.forEach { i ->
                val left = i * group
                if (i in highlight) drawRect(warn, Offset(left, 0f), Size(group, size.height))
                series.forEachIndexed { s, serie ->
                    val h = (serie.values[i] / max * size.height * 0.95).toFloat()
                    val topLeft = Offset(left + group * 0.1f + s * bar, size.height - h)
                    drawRect(serie.color, topLeft, Size(bar * 0.9f, h))
                    if (i in hatched) hatch(topLeft, Size(bar * 0.9f, h))
                }
            }
            drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), 2f)
        }
        AxisLabels(labels)
        Legend(series)
    }
}

/** Two or more lines over the same hours (for example real vs. expected production). */
@Composable
fun LineChart(labels: List<String>, series: List<Series>, description: String, modifier: Modifier = Modifier) {
    val axis = MaterialTheme.colorScheme.outline
    val max = series.maxOf { it.values.max() }.coerceAtLeast(0.1)
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val step = size.width / (labels.size - 1)
            series.forEach { serie ->
                val path = Path()
                serie.values.forEachIndexed { i, v ->
                    val p = Offset(i * step, (size.height - v / max * size.height * 0.95).toFloat())
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    drawCircle(serie.color, 5f, p)
                }
                drawPath(path, serie.color, style = Stroke(width = 6f))
            }
            drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), 2f)
        }
        AxisLabels(labels.mapIndexed { i, l -> if (i % 2 == 0) l else "" })
        Legend(series)
    }
}

/** Day × hour grid; darker cells mean more energy. */
@Composable
fun Heatmap(rows: List<List<Double>>, rowLabels: List<String>, description: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.secondary
    val max = rows.maxOf { it.max() }
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }) {
        rows.forEachIndexed { r, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rowLabels[r], style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(44.dp))
                Canvas(Modifier.weight(1f).height(14.dp)) {
                    val w = size.width / row.size
                    row.forEachIndexed { h, v ->
                        drawRect(color.copy(alpha = (0.08 + 0.92 * v / max).toFloat()), Offset(h * w, 1f), Size(w - 1f, size.height - 2f))
                    }
                }
            }
        }
        Row {
            Spacer(Modifier.width(44.dp))
            AxisLabels(List(24) { if (it % 6 == 0) "%02d".format(it) else "" }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AxisLabels(labels: List<String>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        labels.forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1)
        }
    }
}

@Composable
private fun Legend(series: List<Series>) {
    if (series.size < 2) return
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        series.forEach {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(Modifier.size(12.dp).background(it.color))
                Text(it.label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun DrawScope.hatch(topLeft: Offset, size: Size) = clipRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height) {
    var x = topLeft.x - size.height
    while (x < topLeft.x + size.width) {
        drawLine(Color.White, Offset(x, topLeft.y + size.height), Offset(x + size.height, topLeft.y), 3f)
        x += 12f
    }
}
