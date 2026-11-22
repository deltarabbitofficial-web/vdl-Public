package com.example.vdl

import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

private fun c(hex: String) = Color.parseColor(hex)

class MainActivity : AppCompatActivity() {

    private class AppTheme(val name: String, val bg: Int, val card: Int, val accent: Int, val text: Int, val sub: Int)

    private val themes = listOf(
        AppTheme("Dark blue", c("#0F1115"), c("#1A1D24"), c("#3B82F6"), c("#E8E8E8"), c("#9AA0A6")),
        AppTheme("Light", c("#F6F7F9"), c("#FFFFFF"), c("#2563EB"), c("#111827"), c("#6B7280")),
        AppTheme("Green", c("#0B1410"), c("#14221B"), c("#16A34A"), c("#E6F4EA"), c("#8FAE9A")),
        AppTheme("Purple", c("#120F1A"), c("#1E1A2B"), c("#8B5CF6"), c("#EEE9FB"), c("#A79BC4"))
    )

    private lateinit var prefs: android.content.SharedPreferences
    private var themeIdx = 0
    private var ready = false

    private lateinit var root: ScrollView
    private lateinit var title: TextView
    private lateinit var hint: TextView
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var resultCard: LinearLayout
    private lateinit var videoTitle: TextView
    private lateinit var spinner: Spinner
    private lateinit var bar: ProgressBar
    private lateinit var progText: TextView
    private val buttons = mutableListOf<Button>()
    private var labels: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("vdl", Context.MODE_PRIVATE)
        themeIdx = prefs.getInt("theme", 0).coerceIn(0, themes.size - 1)
        buildUi()
        applyTheme()
        setStatus("Setting up…")
        Thread {
            try {
                YoutubeDL.getInstance().init(application)
                FFmpeg.getInstance().init(application)
                ready = true
                runOnUiThread { setStatus("Ready. Paste a link.") }
            } catch (e: Exception) {
                runOnUiThread { setStatus("Setup failed: ${e.message}") }
            }
        }.start()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp(top: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun box(color: Int, radius: Int = 12) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun mkButton(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        buttons.add(b)
        return b
    }

    private fun buildUi() {
        root = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(48), dp(20), dp(24))
        root.addView(col)

        title = TextView(this).apply { text = "Video Downloader"; textSize = 24f }
        hint = TextView(this).apply {
            text = "YouTube, TikTok, Instagram, Facebook link paste koro"
            textSize = 13f
        }
        input = EditText(this).apply {
            this.hint = "https://..."
            setSingleLine()
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        status = TextView(this).apply { textSize = 13f }

        col.addView(title, lp(0))
        col.addView(hint, lp(4))
        col.addView(mkButton("Theme") { nextTheme() }, lp(12))
        col.addView(input, lp(16))
        col.addView(mkButton("Paste from clipboard") { paste() }, lp(8))
        col.addView(mkButton("Check link") { checkLink() }, lp(8))
        col.addView(status, lp(8))

        resultCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            visibility = View.GONE
        }
        videoTitle = TextView(this).apply { textSize = 15f }
        spinner = Spinner(this)
        resultCard.addView(videoTitle, lp(0))
        resultCard.addView(spinner, lp(8))
        resultCard.addView(mkButton("Download") { download() }, lp(8))
        col.addView(resultCard, lp(16))

        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        progText = TextView(this).apply { textSize = 13f }
        col.addView(bar, lp(16))
        col.addView(progText, lp(4))

        setContentView(root)
    }

    private fun applyTheme() {
        val t = themes[themeIdx]
        root.setBackgroundColor(t.bg)
        title.setTextColor(t.text)
        videoTitle.setTextColor(t.text)
        listOf(hint, status, progText).forEach { it.setTextColor(t.sub) }
        input.setTextColor(t.text)
        input.setHintTextColor(t.sub)
        input.background = box(t.card, 10)
        resultCard.background = box(t.card)
        buttons.forEach {
            it.background = box(t.accent, 10)
            it.setTextColor(Color.WHITE)
        }
        bar.progressTintList = ColorStateList.valueOf(t.accent)
        if (labels.isNotEmpty()) setQualities(labels)
    }

    private fun nextTheme() {
        themeIdx = (themeIdx + 1) % themes.size
        prefs.edit().putInt("theme", themeIdx).apply()
        applyTheme()
        setStatus("Theme: ${themes[themeIdx].name}")
    }

    private fun setStatus(msg: String) {
        status.text = msg
    }

    private fun paste() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val txt = cm.primaryClip?.getItemAt(0)?.text?.toString()
        if (txt.isNullOrBlank()) setStatus("Clipboard khali") else input.setText(txt.trim())
    }

    private fun setQualities(list: List<String>) {
        labels = list
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, list) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(themes[themeIdx].text)
                return v
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent) as TextView
                v.setTextColor(themes[themeIdx].text)
                v.setBackgroundColor(themes[themeIdx].card)
                return v
            }
        }
        spinner.adapter = adapter
    }

    private fun checkLink() {
        val url = input.text.toString().trim()
        if (url.isEmpty()) { setStatus("Link daalo"); return }
        if (!ready) { setStatus("Setup ekhono shesh hoyni, kichhukhon pore chesta koro"); return }
        setStatus("Checking…")
        resultCard.visibility = View.GONE
        Thread {
            try {
                val info = YoutubeDL.getInstance().getInfo(url)
                val heights = (info.formats ?: emptyList())
                    .map { it.height }
                    .filter { it > 0 }
                    .distinct()
                    .sortedDescending()
                val list = (if (heights.isEmpty()) listOf(720) else heights).map { "${it}p" } + "Audio only"
                runOnUiThread {
                    videoTitle.text = info.title ?: "Video"
                    setQualities(list)
                    resultCard.visibility = View.VISIBLE
                    setStatus("")
                }
            } catch (e: Exception) {
                runOnUiThread { setStatus("Error: ${e.message?.take(200)}") }
            }
        }.start()
    }

    private fun download() {
        val url = input.text.toString().trim()
        val label = spinner.selectedItem as? String ?: return
        val dir = File(cacheDir, "dl")
        dir.deleteRecursively()
        dir.mkdirs()

        val req = YoutubeDLRequest(url)
        req.addOption("--no-playlist")
        req.addOption("-o", dir.absolutePath + "/%(title).80s.%(ext)s")
        if (label == "Audio only") {
            req.addOption("-f", "ba[ext=m4a]/ba")
        } else {
            val h = label.removeSuffix("p")
            req.addOption("-f", "bv*[height<=$h]+ba/b[height<=$h]/b")
            req.addOption("--merge-output-format", "mp4")
        }

        bar.progress = 0
        bar.visibility = View.VISIBLE
        progText.text = "Starting…"
        Thread {
            try {
                YoutubeDL.getInstance().execute(req, null) { p, _, _ ->
                    val pct = p.toInt().coerceIn(0, 100)
                    runOnUiThread {
                        bar.progress = pct
                        progText.text = "$pct%"
                    }
                }
                val f = dir.listFiles()?.firstOrNull() ?: throw Exception("File paoa jayni")
                saveToDownloads(f)
                runOnUiThread {
                    bar.progress = 100
                    progText.text = "Saved to Downloads: ${f.name}"
                }
            } catch (e: Exception) {
                runOnUiThread { progText.text = "Error: ${e.message?.take(200)}" }
            }
        }.start()
    }

    private fun saveToDownloads(f: File) {
        val mime = when (f.extension.lowercase()) {
            "mp4" -> "video/mp4"
            "m4a" -> "audio/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            else -> "application/octet-stream"
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, f.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download")
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw Exception("Downloads e save kora jayni")
        contentResolver.openOutputStream(uri)!!.use { out ->
            f.inputStream().use { it.copyTo(out) }
        }
        f.delete()
    }
}
