package com.achtsamweg.remindful

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.FrameLayout
import org.json.JSONArray
import java.util.Calendar
import androidx.annotation.Keep
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var currentSafeInsets: androidx.core.graphics.Insets? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            enableEdgeToEdge()

            createNotificationChannel()
            requestPermissionsIfNecessary()

            // Root-Container erstellen, der das Padding aufnimmt
            val rootLayout = FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                // Hintergrundfarbe passend zur App setzen, damit es beim Starten nicht blitzt
                setBackgroundColor(android.graphics.Color.parseColor("#f8fafc"))
            }

            // Create WebView programmatically
            webView = WebView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        try {
                            injectMaterialYouColors()
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                    }
                }
                webChromeClient = WebChromeClient()
                
                // Inject JS Bridge
                addJavascriptInterface(WebAppInterface(this@MainActivity), "AndroidApp")
            }

            rootLayout.addView(webView)
            setContentView(rootLayout)

            // 100% kugelsicheres Inset-Handling: Der äußere FrameLayout erhält das Padding.
            // Dadurch wird der WebView niemals von der Notch, Statusleiste oder Tastatur überlagert.
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
                try {
                    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                    val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                    
                    // Der untere Abstand ist das Maximum aus Navigationsleiste und aufklappender Tastatur
                    val bottomPadding = max(systemBars.bottom, ime.bottom)
                    
                    v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding)
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
                WindowInsetsCompat.CONSUMED
            }

            webView.loadUrl("file:///android_asset/index.html")
        } catch (e: Throwable) {
            e.printStackTrace()
            Toast.makeText(this, "Crash prevented: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun injectMaterialYouColors() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                // Dynamische Farben (Material You) aus dem Android OS extrahieren
                val primary = String.format("#%06X", 0xFFFFFF and resources.getColor(android.R.color.system_accent1_500, theme))
                val bg = String.format("#%06X", 0xFFFFFF and resources.getColor(android.R.color.system_neutral1_50, theme))
                val surface = String.format("#%06X", 0xFFFFFF and resources.getColor(android.R.color.system_accent1_100, theme))
                val text = String.format("#%06X", 0xFFFFFF and resources.getColor(android.R.color.system_neutral1_900, theme))
                
                // An das Web-Frontend senden
                val js = "if(window.app && typeof window.app.applyMaterialYou === 'function') { window.app.applyMaterialYou('$primary', '$bg', '$text', '$surface'); }"
                webView.evaluateJavascript(js, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun requestPermissionsIfNecessary() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Re:Mindful Erinnerungen"
            val descriptionText = "Tägliche Erinnerungen für dein Tagebuch"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("remindful_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    // JS Bridge class
    @Keep
    inner class WebAppInterface(private val activity: AppCompatActivity) {
        
        @Keep
        @JavascriptInterface
        fun triggerTestNotification(text: String) {
            val intent = Intent(activity, NotificationReceiver::class.java).apply {
                putExtra("title", "Re:Mindful")
                putExtra("message", text)
            }
            activity.sendBroadcast(intent)
            
            activity.runOnUiThread {
                Toast.makeText(activity, "Notification ausgelöst", Toast.LENGTH_SHORT).show()
            }
        }

        @Keep
        @JavascriptInterface
        fun scheduleNotification(enabled: Boolean, time: String, note: String) {
            val alarmManager = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(activity, NotificationReceiver::class.java).apply {
                putExtra("title", "Re:Mindful Erinnerung")
                putExtra("message", if (note.isEmpty()) "Zeit für dich! Wie war dein Tag?" else note)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                activity, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            if (enabled) {
                val parts = time.split(":")
                if (parts.size == 2) {
                    val calendar = Calendar.getInstance().apply {
                        timeInMillis = System.currentTimeMillis()
                        set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                        set(Calendar.MINUTE, parts[1].toInt())
                        set(Calendar.SECOND, 0)
                        if (timeInMillis <= System.currentTimeMillis()) {
                            add(Calendar.DAY_OF_YEAR, 1)
                        }
                    }
                    
                    alarmManager.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        AlarmManager.INTERVAL_DAY,
                        pendingIntent
                    )
                    activity.runOnUiThread {
                        Toast.makeText(activity, "Tägliche Erinnerung für $time Uhr aktiviert", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                alarmManager.cancel(pendingIntent)
                activity.runOnUiThread {
                    Toast.makeText(activity, "Erinnerung deaktiviert", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @Keep
        @JavascriptInterface
        fun generateAndSavePdf(jsonPayload: String, fileName: String, userName: String) {
            try {
                val jsonArray = JSONArray(jsonPayload)
                val pdfDocument = PdfDocument()
                val paint = Paint()
                val textPaint = android.text.TextPaint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 14f
                    isAntiAlias = true
                }
                
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
                var page = pdfDocument.startPage(pageInfo)
                var canvas = page.canvas
                var yPosition = 50f
                
                // Lese aktuelle Farben (Workaround für WebView Interop)
                val primaryTeal = android.graphics.Color.parseColor("#0d9488")
                val softTeal = android.graphics.Color.parseColor("#f0fdfa")
                val textMain = android.graphics.Color.parseColor("#334155")
                
                // Titel "Tagebuch von X"
                paint.color = primaryTeal
                paint.textSize = 32f
                paint.isFakeBoldText = true
                canvas.drawText("Tagebuch von $userName", 50f, yPosition, paint)
                
                yPosition += 25f
                paint.color = textMain
                paint.textSize = 14f
                paint.isFakeBoldText = false
                canvas.drawText("Dein Weg zu mehr Achtsamkeit und persönlichem Wachstum.", 50f, yPosition, paint)
                yPosition += 50f

                // Datum-Gruppierung (einfacher State)
                var lastDate = ""

                for (i in 0 until jsonArray.length()) {
                    val entry = jsonArray.getJSONObject(i)
                    val text = entry.optString("text", "")
                    val fullDate = entry.optString("timestamp", "")
                    
                    // Format: 2023-10-01 -> 01.10.2023 (Deutsches Datum)
                    val dateOnly = if (fullDate.length >= 10) {
                        val y = fullDate.substring(0, 4)
                        val m = fullDate.substring(5, 7)
                        val d = fullDate.substring(8, 10)
                        "$d.$m.$y"
                    } else "Unbekannt"
                    
                    val timeOnly = if (fullDate.length >= 16) fullDate.substring(11, 16) else ""
                    val type = entry.optString("type", "Eintrag").uppercase()
                    val emoji = entry.optString("moodEmoji", "")
                    
                    if (yPosition > 700f) {
                        pdfDocument.finishPage(page)
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        yPosition = 50f
                    }
                    
                    // Neues Datum? -> Überschrift
                    if (dateOnly != lastDate) {
                        lastDate = dateOnly
                        yPosition += 20f
                        
                        // Hintergrund für Datum
                        val rect = android.graphics.RectF(40f, yPosition - 25f, 555f, yPosition + 15f)
                        paint.color = softTeal
                        canvas.drawRoundRect(rect, 10f, 10f, paint)
                        
                        paint.color = primaryTeal
                        paint.isFakeBoldText = true
                        paint.textSize = 18f
                        canvas.drawText("✨ $dateOnly ✨", 50f, yPosition, paint)
                        yPosition += 40f
                        
                        paint.color = textMain
                        paint.textSize = 14f
                    }
                    
                    // Header des Eintrags: Uhrzeit, Typ, Emoji
                    paint.isFakeBoldText = true
                    canvas.drawText("$timeOnly | $type $emoji", 50f, yPosition, paint)
                    yPosition += 20f
                    
                    // Text mit StaticLayout (automatischer Zeilenumbruch!)
                    val staticLayout = android.text.StaticLayout.Builder.obtain(text, 0, text.length, textPaint, 495)
                            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                            .setLineSpacing(0f, 1.2f)
                            .setIncludePad(false)
                            .build()

                    canvas.save()
                    canvas.translate(50f, yPosition)
                    staticLayout.draw(canvas)
                    canvas.restore()
                    
                    yPosition += staticLayout.height + 35f // Abstand zum nächsten Eintrag
                }
                pdfDocument.finishPage(page)

                // Speichern
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                
                val uri = activity.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                if (uri != null) {
                    activity.contentResolver.openOutputStream(uri)?.use { 
                        pdfDocument.writeTo(it)
                    }
                    activity.runOnUiThread {
                        Toast.makeText(activity, "PDF erfolgreich in 'Downloads' gespeichert", Toast.LENGTH_LONG).show()
                    }
                } else {
                    activity.runOnUiThread {
                        Toast.makeText(activity, "Fehler beim Erstellen der PDF-Datei", Toast.LENGTH_SHORT).show()
                    }
                }
                pdfDocument.close()
                
            } catch (e: Exception) {
                e.printStackTrace()
                activity.runOnUiThread {
                    Toast.makeText(activity, "PDF Generierung fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}