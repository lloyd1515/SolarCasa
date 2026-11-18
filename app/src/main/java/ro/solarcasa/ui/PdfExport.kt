package ro.solarcasa.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File

/** Writes a simple A4 PDF report and opens the Android share sheet (download, e-mail, Drive …). */
object PdfExport {
    private const val WIDTH = 595
    private const val HEIGHT = 842
    private const val MARGIN = 40f

    fun share(context: Context, fileName: String, title: String, lines: List<String>) {
        val doc = PdfDocument()
        val text = Paint().apply { textSize = 11f; isAntiAlias = true }
        val heading = Paint().apply { textSize = 16f; isFakeBoldText = true; isAntiAlias = true }
        var number = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, number).create())
        page.canvas.drawText(title, MARGIN, 60f, heading)
        var y = 92f
        lines.forEach { line ->
            if (y > HEIGHT - MARGIN) {
                doc.finishPage(page)
                page = doc.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, ++number).create())
                y = 60f
            }
            page.canvas.drawText(line, MARGIN, y, text)
            y += 17f
        }
        doc.finishPage(page)

        val file = File(File(context.cacheDir, "reports").apply { mkdirs() }, fileName)
        file.outputStream().use(doc::writeTo)
        doc.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, title))
    }
}
