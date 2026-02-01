package com.example.quandointv

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import android.graphics.Color
import android.graphics.Typeface
import java.util.Calendar
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.TextView
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import java.time.LocalTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.io.File
import android.widget.EditText
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
import androidx.appcompat.app.AppCompatDelegate
import java.util.concurrent.TimeUnit
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginEnd

class MainActivity : AppCompatActivity() {
    private lateinit var scrollView: ScrollView
    private lateinit var container: LinearLayout
    private lateinit var searchButton: Button
    private lateinit var programs: Array<String>
    private lateinit var channels: Array<Channel>
    private var connectionError = false

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class Channel(
        var name: String = "",
        var url: String = ""
    )

    private val privacyPolicyLink = "https://github.com/Messina-Agata/QuandoInTV/blob/main/PrivacyPolicy.md"
    private val termsOfServiceLink = "https://github.com/Messina-Agata/QuandoInTV/blob/main/TermsOfService.md"
    private val attributionLink = "https://github.com/Messina-Agata/QuandoInTV/blob/main/Attribution.md"
    private val licenseLink = "https://github.com/Messina-Agata/QuandoInTV/blob/main/LICENSE.md"
    private val aboutLink = "https://github.com/Messina-Agata/QuandoInTV/blob/main/README.md"

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val bottomInset = maxOf(ime, systemBars) // massimo tra tastiera e navigation bar
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                bottomInset
            )
            insets
        }
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setTitleTextColor(getColor(R.color.giallo))
        val textView = toolbar.getChildAt(0) as? TextView
        textView?.textSize = 25f
        scrollView = findViewById<ScrollView>(R.id.scrollView)
        container = findViewById<LinearLayout>(R.id.container)
        try {
            lifecycleScope.launch {
                findChannels()
                insertProgramsToSearch()
            }
        } catch (ex: Exception) {
            CoroutineScope(Dispatchers.IO).launch {
                withContext(Dispatchers.Main) {
                    showErrorMessage(ex.toString())
                }
            }
            throw ex
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_toolbar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_info -> {
                showInfoDialog()
                true
            }
            R.id.action_notifications -> {
                showNotificationDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showInfoDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_info, null)
        val privacyPolicy = dialogView.findViewById<TextView>(R.id.privacyPolicy)
        privacyPolicy.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyLink))
            startActivity(intent)
        }
        val termsOfService = dialogView.findViewById<TextView>(R.id.termsOfService)
        termsOfService.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(termsOfServiceLink))
            startActivity(intent)
        }
        val attribution = dialogView.findViewById<TextView>(R.id.attribution)
        attribution.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(attributionLink))
            startActivity(intent)
        }
        val license = dialogView.findViewById<TextView>(R.id.license)
        license.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(licenseLink))
            startActivity(intent)
        }
        val about = dialogView.findViewById<TextView>(R.id.about)
        about.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(aboutLink))
            startActivity(intent)
        }
        AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .show()
    }

    private fun showNotificationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_notifications, null)
        val prefs = getSharedPreferences("notifiche", MODE_PRIVATE)
        dialogView.findViewById<CheckBox>(R.id.chk_lun).isChecked = prefs.getBoolean("lun", false)
        dialogView.findViewById<CheckBox>(R.id.chk_mar).isChecked = prefs.getBoolean("mar", false)
        dialogView.findViewById<CheckBox>(R.id.chk_mer).isChecked = prefs.getBoolean("mer", false)
        dialogView.findViewById<CheckBox>(R.id.chk_gio).isChecked = prefs.getBoolean("gio", false)
        dialogView.findViewById<CheckBox>(R.id.chk_ven).isChecked = prefs.getBoolean("ven", false)
        dialogView.findViewById<CheckBox>(R.id.chk_sab).isChecked = prefs.getBoolean("sab", false)
        dialogView.findViewById<CheckBox>(R.id.chk_dom).isChecked = prefs.getBoolean("dom", false)
        val savedHour = prefs.getInt("hour", -1)
        val savedMinute = prefs.getInt("minute", -1)
        val hourSpinner = dialogView.findViewById<Spinner>(R.id.spinner_hour)
        val minuteSpinner = dialogView.findViewById<Spinner>(R.id.spinner_minute)
        val hours = (0..23).map { String.format("%02d", it) }
        hourSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, hours)
        val minutes = (0..59).map { String.format("%02d", it) }
        minuteSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, minutes)
        if (savedHour >= 0) hourSpinner.setSelection(savedHour)
        if (savedMinute >= 0) minuteSpinner.setSelection(savedMinute)
        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val selectedHour = hourSpinner.selectedItem.toString()
                val selectedMinute = minuteSpinner.selectedItem.toString()
                val prefs = getSharedPreferences("notifiche", MODE_PRIVATE)
                prefs.edit().apply {
                    putBoolean("lun", dialogView.findViewById<CheckBox>(R.id.chk_lun).isChecked)
                    putBoolean("mar", dialogView.findViewById<CheckBox>(R.id.chk_mar).isChecked)
                    putBoolean("mer", dialogView.findViewById<CheckBox>(R.id.chk_mer).isChecked)
                    putBoolean("gio", dialogView.findViewById<CheckBox>(R.id.chk_gio).isChecked)
                    putBoolean("ven", dialogView.findViewById<CheckBox>(R.id.chk_ven).isChecked)
                    putBoolean("sab", dialogView.findViewById<CheckBox>(R.id.chk_sab).isChecked)
                    putBoolean("dom", dialogView.findViewById<CheckBox>(R.id.chk_dom).isChecked)
                    putInt("hour", selectedHour.toInt())
                    putInt("minute", selectedMinute.toInt())
                    apply()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                cancelAllNotifications() // Cancella la vecchia programmazione
                requestExactAlarmPermission()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val alarmManager = getSystemService(AlarmManager::class.java)
                    if (!alarmManager.canScheduleExactAlarms()) {
                        Toast.makeText(this, "Per attivare i promemoria devi abilitare gli allarmi esatti.", Toast.LENGTH_LONG).show()
                    }
                    else {
                        scheduleNotifications() // Rischedula le notifiche
                    }
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }
    }

    private fun cancelAllNotifications() {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val days = listOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY
        )
        for (day in days) {
            val intent = Intent(this, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                day,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun scheduleNotifications() {
        val prefs = getSharedPreferences("notifiche", MODE_PRIVATE)
        val days = listOf(
            "lun" to Calendar.MONDAY,
            "mar" to Calendar.TUESDAY,
            "mer" to Calendar.WEDNESDAY,
            "gio" to Calendar.THURSDAY,
            "ven" to Calendar.FRIDAY,
            "sab" to Calendar.SATURDAY,
            "dom" to Calendar.SUNDAY
        )
        val hour = prefs.getInt("hour", 0)
        val minute = prefs.getInt("minute", 0)
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        for ((key, dayOfWeek) in days) {
            if (prefs.getBoolean(key, false)) {
                val intent = Intent(this, ReminderReceiver::class.java).apply {
                    putExtra("day", dayOfWeek)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    this,
                    dayOfWeek,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val calendar = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, dayOfWeek)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)

                    // Se l'orario è già passato oggi, programma per la prossima settimana
                    if (before(Calendar.getInstance())) {
                        add(Calendar.WEEK_OF_YEAR, 1)
                    }
                }

                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
                Toast.makeText(this, "Promemoria impostato", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "Notifiche non autorizzate per questa app", Toast.LENGTH_SHORT).show()
            }
        }

    private suspend fun findChannels() {
        val sURL = "https://guidatv.quotidiano.net/"
        val siteContent = getWebPage(sURL)
        if (siteContent.isEmpty()) {
            connectionError = true
            showErrorMessage("Errore di connessione")
            val retryButton = Button(this).apply {
                text = "Riprova"
                layoutParams = LinearLayout.LayoutParams(250, WRAP_CONTENT).apply { gravity =
                    Gravity.CENTER
                }
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.gray))
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.grigio_scuro))
                setOnClickListener {
                    currentFocus?.clearFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
                    container.removeAllViews()
                    try {
                        lifecycleScope.launch {
                            findChannels()
                            insertProgramsToSearch()
                        }
                    } catch (ex: Exception) {
                        CoroutineScope(Dispatchers.IO).launch {
                            withContext(Dispatchers.Main) {
                                showErrorMessage(ex.toString())
                            }
                        }
                        throw ex
                    }
                }
            }
            container.addView(retryButton)
            return
        }
        else {
            connectionError = false
        }

        val rx = Regex(
            "<section class=\"channel channel-thumbnail\".*?</section>",
            RegexOption.DOT_MATCHES_ALL
        )
        val matches = rx.findAll(siteContent).toList()

        val rx2 = Regex(
            "(?<=class=\"channel-name\">)(.*?)(?=</span>)",
            RegexOption.IGNORE_CASE
        )
        val matches2 = rx2.findAll(siteContent).toList()

        channels = Array(matches.size) { index ->
            val section = matches[index].value

            val rxUrl = Regex(
                "(?<=a href=\")/(.*?)(?=\")",
                RegexOption.IGNORE_CASE
            )
            val matchUrl = rxUrl.find(section)

            val url = sURL + (matchUrl?.groupValues?.get(1) ?: "")
            val name = matches2.getOrNull(index)?.value ?: ""

            Channel(name = name, url = url)
        }
    }

    private suspend fun getWebPage(url: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                response.body?.string() ?: ""
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    ""
                }
            }
        }
    }

    private fun showErrorMessage(text: String) {
        val warning = TextView(this).apply {
            this.text = text
            textSize = 20f
            setBackgroundColor(Color.RED)
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 10
            layoutParams = params
        }
        container.addView(warning)
    }

    private fun insertProgramsToSearch() {
        var i = 0
        addTextBlock("Programmi da cercare", i++)
        val file = File(filesDir, "programs.txt")
        if (!file.exists()) {
            file.writeText("")   // crea file vuoto
        }
        val lines = file.readLines()
        for (line in lines) {
            addLayoutWithTextBoxAndButton(line, i++)
        }
        addLayoutWithAddAndSearchButtons(i++)
    }

    private fun addTextBlock(text: String, tag: Int) {
        val title = TextView(this).apply {
            this.text = text
            height = 120
            textSize = 30f
            gravity = Gravity.CENTER
            setTypeface(this.typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.verde))
            this.tag = tag
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 10, 0, 20)
        title.layoutParams = params
        container.addView(title)
    }

    private fun addLayoutWithTextBoxAndButton(line: String, tag: Int) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            this.tag = tag
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 150)
        }

        val edit = EditText(this).apply {
            setText(line)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            height = 150
            width = 600
            textSize = 20f
            setOnFocusChangeListener { v, hasFocus ->
                if (!hasFocus) textBoxLostFocus(this)
            }
        }

        val removeButton = Button(this).apply {
            text = "Rimuovi"
            height = 150
            width = 200
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.blu))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.white))
            setOnClickListener {
                currentFocus?.clearFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
                removeButtonClick(this)
            }
        }

        layout.addView(edit)
        layout.addView(removeButton)
        container.addView(layout)
    }

    private fun addLayoutWithAddAndSearchButtons(tag: Int) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            this.tag = tag
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 200)
        }

        val addButton = Button(this).apply {
            text = "Aggiungi programma"
            height = 200
            width = 400
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.blu))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.white))
            setOnClickListener {
                currentFocus?.clearFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
                addButtonClick(this) }
        }

        searchButton = Button(this).apply {
            isEnabled = !connectionError
            text = "Cerca programmi"
            height = 200
            width = 400
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.blu))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.white))
            setOnClickListener {
                currentFocus?.clearFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.blu_scuro))
                searchButtonClick(this)
            }
        }

        layout.addView(addButton)
        layout.addView(searchButton)
        container.addView(layout)
    }

    private fun textBoxLostFocus(edit: EditText) {
        val parent = edit.parent as LinearLayout
        val index = parent.tag as Int

        val file = File(filesDir, "programs.txt")
        val lines = if (file.exists()) file.readLines().toMutableList() else mutableListOf()

        if (index > lines.size) {
            lines.add(edit.text.toString())
        } else {
            if (index - 1 >= 0 && index - 1 < lines.size) {
                lines[index - 1] = edit.text.toString()
            } else {
                lines.add(edit.text.toString())
            }
        }

        file.writeText(lines.joinToString("\n"))
    }

    private fun removeButtonClick(button: Button) {
        val parent = button.parent as LinearLayout
        val index = parent.tag as Int

        val file = File(filesDir, "programs.txt")
        if (!file.exists()) return

        val lines = file.readLines().toMutableList()

        if (index <= lines.size && index > 0) {
            lines.removeAt(index - 1)
            file.writeText(lines.joinToString("\n"))
        }

        container.removeAllViews()
        insertProgramsToSearch()
    }

    private fun addButtonClick(button: Button) {
        val parent = button.parent as LinearLayout
        val index = parent.tag as Int

        container.removeViews(index, container.childCount - index)

        addLayoutWithTextBoxAndButton("", index)
        addLayoutWithAddAndSearchButtons(index + 1)
    }

    private fun searchButtonClick(button: Button) {
        loadProgramsToSearch()
        if (!::programs.isInitialized || programs.isEmpty()) return
        val parent = button.parent as LinearLayout
        val index = parent.tag as Int
        if (container.childCount > index) {
            container.removeViews(index + 1, container.childCount - (index + 1))
        }
        lifecycleScope.launch {
            findPrograms()
        }
    }

    private fun loadProgramsToSearch() {
        val file = File(filesDir, "programs.txt")

        if (!file.exists()) {
            file.createNewFile()
        }

        val lines = file.readLines()

        if (lines.isEmpty()) {
            programs = emptyArray()
            CoroutineScope(Dispatchers.IO).launch {
                withContext(Dispatchers.Main) {
                    showErrorMessage("Nessun programma inserito")
                }

            }
            return
        }

        programs = lines.toTypedArray()
    }

    private suspend fun findPrograms() = withContext(Dispatchers.Main) {
        val today = LocalDate.now()
        val tasks = mutableListOf<Deferred<String>>()
        val metadata = mutableListOf<Pair<Int, String>>()
        if (channels.isEmpty())
            return@withContext

        // Avvio richieste HTTP
        for (j in channels.indices) {
            for (d in 0 until 7) {
                val day = today.plusDays(d.toLong())
                val dayString = day.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                tasks.add(async(Dispatchers.IO) {
                    getWebPage(channels[j].url + dayString)
                })
                metadata.add(Pair(j, dayString))
            }
        }
        val results = tasks.awaitAll()
        if (results.any { it.isEmpty() }) {
            showErrorMessage("Errore di connessione. Controlla la tua connessione e riavvia la ricerca")
            searchButton.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.blu))
            return@withContext
        }

        // Elaborazione risultati
        for (i in results.indices) {
            val (channelIndex, dayString) = metadata[i]
            val siteContentRaw = results[i]
            if (siteContentRaw.isEmpty()) return@withContext
            var siteContent = siteContentRaw
            val startIndex = siteContent.indexOf("<section id=\"faqs\">")
            if (startIndex < 0) continue
            siteContent = siteContent.substring(startIndex)
            val endIndex = siteContent.indexOf("</li></ul>")
            if (endIndex < 0) continue
            siteContent = siteContent.substring(0, endIndex + 5)
            val rx = Regex("(?<=<li>)(.*?)(?=</li>)", RegexOption.IGNORE_CASE)
            val matches = rx.findAll(siteContent).toList()
            if (matches.isEmpty()) continue
            val lastTimeString = matches.last().value.substring(0, 5)
            val lastTime = LocalTime.parse("$lastTimeString:00")
            val firstTime = LocalTime.parse("06:00:00")
            var elementsCount = matches.size
            if (lastTime == firstTime) elementsCount--
            for (ctr in 0 until elementsCount) {
                val matchValue = matches[ctr].value
                for (program in programs) {
                    if (matchValue.contains(program, ignoreCase = true)) {

                        // Primo blocco (nome programma)
                        val found = TextView(this@MainActivity).apply {
                            text = program
                            height = 80
                            textSize = 20f
                            gravity = Gravity.CENTER
                            setTypeface(this.typeface, Typeface.BOLD)
                            setBackgroundColor(Color.parseColor("#87CEFA")) // LightSkyBlue
                            val params = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            layoutParams = params
                            params.setMargins(20, 10, 20, 0)
                        }
                        container.addView(found)

                        // Secondo blocco (dettagli)
                        val timeString = matchValue.substring(0, 5)
                        val time = LocalTime.parse("$timeString:00")
                        val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
                        val dayDate = LocalDate.parse(dayString, dateFormatter)
                        val finalDate =
                            if (time >= LocalTime.parse("00:00:00") &&
                                time < LocalTime.parse("06:00:00")
                            ) dayDate.plusDays(1)
                            else dayDate
                        val found2 = TextView(this@MainActivity).apply {
                            text = "${finalDate.format(dateFormatter)} ${channels[channelIndex].name} $matchValue"
                            val params = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            layoutParams = params
                            params.setMargins(20, 0, 20, 0)
                            textSize = 20f
                            gravity = Gravity.START
                            isSingleLine = false
                            maxLines = Int.MAX_VALUE
                        }
                        container.addView(found2)
                    }
                }
            }
        }

        // Messaggio finale
        val end = TextView(this@MainActivity).apply {
            text = "Ricerca completata"
            height = 80
            textSize = 20f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setTypeface(this.typeface, Typeface.BOLD)
            setTextColor(Color.GREEN)
        }
        searchButton.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.blu))
        container.addView(end)
    }
}
