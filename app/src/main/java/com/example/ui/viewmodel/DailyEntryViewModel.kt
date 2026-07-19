package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.DailyEntry
import com.example.data.repository.DailyEntryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class DailyEntryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DailyEntryRepository
    private val sharedPrefs = application.getSharedPreferences("takahishab_prefs", Context.MODE_PRIVATE)

    // ১. দৈনিক লক্ষ্যমাত্রা বা টার্গেট (পিস)
    private val _dailyTarget = MutableStateFlow(sharedPrefs.getInt("takahishab_daily_target", 800))
    val dailyTarget: StateFlow<Int> = _dailyTarget

    fun updateDailyTarget(target: Int) {
        sharedPrefs.edit().putInt("takahishab_daily_target", target).apply()
        _dailyTarget.value = target
    }

    // ২. মজুরির হার প্রতি ১০০ পিসে (৳)
    private val _wageRate = MutableStateFlow(sharedPrefs.getFloat("takahishab_wage_rate", 50.0f).toDouble())
    val wageRate: StateFlow<Double> = _wageRate

    fun updateWageRate(rate: Double) {
        sharedPrefs.edit().putFloat("takahishab_wage_rate", rate.toFloat()).apply()
        _wageRate.value = rate
    }

    // ৩. থিম সেটিংস: "DEFAULT" (ডার্ক ব্লু), "CARBON" (নেভি ডার্ক/কয়লা), "GLASS" (হালকা গ্লাস)
    private val _currentTheme = MutableStateFlow(sharedPrefs.getString("takahishab_theme", "DEFAULT") ?: "DEFAULT")
    val currentTheme: StateFlow<String> = _currentTheme

    fun updateTheme(themeName: String) {
        sharedPrefs.edit().putString("takahishab_theme", themeName).apply()
        _currentTheme.value = themeName
    }

    init {
        val database = AppDatabase.getDatabase(application)
        repository = DailyEntryRepository(database.dailyEntryDao())
    }

    // ৪. সকল হিসাব রেকর্ড ফ্লো
    val allEntries: StateFlow<List<DailyEntry>> = repository.allEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ৫. হিসাবের বই বা ফোল্ডারসমূহ
    private val _customFolders = MutableStateFlow(
        sharedPrefs.getStringSet("takahishab_custom_folders", setOf("সাধারণ হিসাব"))?.toSet() ?: setOf("সাধারণ হিসাব")
    )
    val customFolders: StateFlow<Set<String>> = _customFolders

    val allFolders: StateFlow<List<String>> = combine(allEntries, _customFolders) { entries, custom ->
        val fromEntries = entries.map { it.folderName }.toSet()
        (listOf("সব হিসাব", "সাধারণ হিসাব") + custom + fromEntries).distinct().sortedBy { if (it == "সব হিসাব") "" else it }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("সব হিসাব", "সাধারণ হিসাব"))

    private val _selectedFolder = MutableStateFlow(sharedPrefs.getString("takahishab_selected_folder", "সব হিসাব") ?: "সব হিসাব")
    val selectedFolder: StateFlow<String> = _selectedFolder

    fun updateSelectedFolder(folderName: String) {
        sharedPrefs.edit().putString("takahishab_selected_folder", folderName).apply()
        _selectedFolder.value = folderName
    }

    fun addCustomFolder(folderName: String) {
        val trimmed = folderName.trim()
        if (trimmed.isEmpty()) return
        val updated = _customFolders.value.toMutableSet()
        if (updated.add(trimmed)) {
            sharedPrefs.edit().putStringSet("takahishab_custom_folders", updated).apply()
            _customFolders.value = updated
        }
    }

    fun deleteCustomFolder(folderName: String) {
        val updated = _customFolders.value.toMutableSet()
        if (updated.remove(folderName)) {
            sharedPrefs.edit().putStringSet("takahishab_custom_folders", updated).apply()
            _customFolders.value = updated
        }
        if (_selectedFolder.value == folderName) {
            updateSelectedFolder("সব হিসাব")
        }
    }

    // ৬. নির্বাচিত ফোল্ডার অনুযায়ী ফিল্টারকৃত রেকর্ড তালিকা
    val filteredEntries: StateFlow<List<DailyEntry>> = combine(allEntries, selectedFolder) { entries, folder ->
        if (folder == "সব হিসাব") {
            entries
        } else {
            entries.filter { it.folderName == folder }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ৭. ড্যাশবোর্ড সামারি (প্রকৃত কারিগর হিসাবের নীতি অনুযায়ী হিসাবকৃত)
    val dashboardSummary: StateFlow<DashboardSummary> = combine(filteredEntries, wageRate, dailyTarget) { entries, wage, target ->
        var totalPcs = 0
        var totalIncome = 0.0
        var totalExpense = 0.0
        var daysTargetMet = 0
        var maxIncome = 0.0
        var minExpense = if (entries.isNotEmpty()) Double.MAX_VALUE else 0.0

        entries.forEach { entry ->
            totalPcs += entry.quantity
            val calculatedWage = (entry.quantity / 100.0) * wage
            totalIncome += calculatedWage
            totalExpense += entry.expense
            
            if (entry.quantity >= target) {
                daysTargetMet++
            }
            if (calculatedWage > maxIncome) {
                maxIncome = calculatedWage
            }
            if (entry.expense < minExpense) {
                minExpense = entry.expense
            }
        }

        if (minExpense == Double.MAX_VALUE) {
            minExpense = 0.0
        }

        val netProfit = totalIncome - totalExpense
        val totalDays = entries.map { 
            // Group by days
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = it.dateMillis }
            "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
        }.distinct().size

        val avgPcs = if (totalDays > 0) totalPcs.toDouble() / totalDays else 0.0
        val avgExpense = if (totalDays > 0) totalExpense / totalDays else 0.0
        val avgProfit = if (totalDays > 0) netProfit / totalDays else 0.0
        val targetMetPercent = if (totalDays > 0) (daysTargetMet.toDouble() / totalDays) * 100.0 else 0.0

        DashboardSummary(
            totalPcs = totalPcs,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            netProfit = netProfit,
            avgPcs = avgPcs,
            avgExpense = avgExpense,
            avgProfit = avgProfit,
            totalDays = totalDays,
            maxIncome = maxIncome,
            minExpense = minExpense,
            daysTargetMet = daysTargetMet,
            targetMetPercent = targetMetPercent
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary())

    // ৮. ডেটাবেস ক্রুড অ্যাকশনসমূহ
    fun addEntry(dateMillis: Long, quantity: Int, expense: Double, note: String, folderName: String) {
        viewModelScope.launch {
            repository.insert(
                DailyEntry(
                    dateMillis = dateMillis,
                    quantity = quantity,
                    expense = expense,
                    note = note,
                    folderName = folderName
                )
            )
        }
    }

    fun updateEntry(id: Int, dateMillis: Long, quantity: Int, expense: Double, note: String, folderName: String) {
        viewModelScope.launch {
            repository.update(
                DailyEntry(
                    id = id,
                    dateMillis = dateMillis,
                    quantity = quantity,
                    expense = expense,
                    note = note,
                    folderName = folderName
                )
            )
        }
    }

    fun deleteEntry(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    // ৯. অফলাইন ব্যাকআপ এক্সপোর্ট ফাংশন
    fun exportBackup(): String {
        val entries = allEntries.value
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"wageRate\":").append(wageRate.value).append(",")
        sb.append("\"dailyTarget\":").append(dailyTarget.value).append(",")
        sb.append("\"folders\":[")
        customFolders.value.forEachIndexed { index, folder ->
            sb.append("\"").append(folder.replace("\"", "\\\"")).append("\"")
            if (index < customFolders.value.size - 1) sb.append(",")
        }
        sb.append("],\"entries\":[")
        entries.forEachIndexed { index, entry ->
            sb.append("{")
            sb.append("\"dateMillis\":").append(entry.dateMillis).append(",")
            sb.append("\"quantity\":").append(entry.quantity).append(",")
            sb.append("\"expense\":").append(entry.expense).append(",")
            sb.append("\"folderName\":\"").append(entry.folderName.replace("\"", "\\\"")).append("\",")
            sb.append("\"note\":\"").append(entry.note.replace("\"", "\\\"")).append("\"")
            sb.append("}")
            if (index < entries.size - 1) sb.append(",")
        }
        sb.append("]}")
        return sb.toString()
    }

    // ১০. অফলাইন ব্যাকআপ ইম্পোর্ট ফাংশন (উভয় ফরম্যাট সাপোর্ট করে: অবজেক্ট বা এন্ট্রি অ্যারে)
    fun importBackup(backupStr: String): Boolean {
        try {
            val trimmed = backupStr.trim()
            if (trimmed.isEmpty()) return false

            val parsedEntries = mutableListOf<DailyEntry>()
            val parsedFolders = mutableListOf<String>()
            var importedWageRate: Double? = null
            var importedTarget: Int? = null

            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                // পূর্ণ জেসন অবজেক্ট ফরম্যাট
                // ১. পার্স wageRate
                val wageRegex = "\"wageRate\"\\s*:\\s*([0-9.]+)"
                val wageMatch = Regex(wageRegex).find(trimmed)
                if (wageMatch != null) {
                    importedWageRate = wageMatch.groupValues[1].toDoubleOrNull()
                }

                // ২. পার্স dailyTarget
                val targetRegex = "\"dailyTarget\"\\s*:\\s*([0-9]+)"
                val targetMatch = Regex(targetRegex).find(trimmed)
                if (targetMatch != null) {
                    importedTarget = targetMatch.groupValues[1].toIntOrNull()
                }

                // ৩. পার্স ফোল্ডারসমূহ
                val foldersRegex = "\"folders\"\\s*:\\s*\\[([^\\]]*)\\]"
                val foldersMatch = Regex(foldersRegex).find(trimmed)
                if (foldersMatch != null) {
                    val rawFolders = foldersMatch.groupValues[1]
                    rawFolders.split(",").forEach {
                        val folder = it.trim().removeSurrounding("\"").trim()
                        if (folder.isNotEmpty()) parsedFolders.add(folder)
                    }
                }

                // ৪. পার্স রেকর্ডসমূহ
                val entriesRegex = "\"entries\"\\s*:\\s*\\[(.*)\\]\\s*\\}$"
                val entriesMatch = Regex(entriesRegex, RegexOption.DOT_MATCHES_ALL).find(trimmed)
                if (entriesMatch != null) {
                    val rawEntriesList = entriesMatch.groupValues[1]
                    parseEntriesArray(rawEntriesList, parsedEntries)
                } else {
                    // বিকল্প খোঁজ
                    val altEntriesRegex = "\"entries\"\\s*:\\s*\\[([^\\]]*)\\]"
                    val altMatch = Regex(altEntriesRegex).find(trimmed)
                    if (altMatch != null) {
                        parseEntriesArray(altMatch.groupValues[1], parsedEntries)
                    }
                }
            } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                // শুধুমাত্র এন্ট্রি অ্যারে ফরম্যাট
                parseEntriesArray(trimmed.substring(1, trimmed.length - 1), parsedEntries)
            } else {
                return false
            }

            if (parsedEntries.isEmpty()) return false

            // ইম্পোর্ট সফল হলে ডাটা সেট করা হচ্ছে
            viewModelScope.launch {
                // ১. ফোল্ডার মার্জ
                if (parsedFolders.isNotEmpty()) {
                    val updated = _customFolders.value.toMutableSet()
                    updated.addAll(parsedFolders)
                    sharedPrefs.edit().putStringSet("takahishab_custom_folders", updated).apply()
                    _customFolders.value = updated
                }
                // ২. রেট ও টার্গেট আপডেট
                importedWageRate?.let { updateWageRate(it) }
                importedTarget?.let { updateDailyTarget(it) }

                // ৩. ডাটাবেসে রেকর্ড যুক্ত করা হচ্ছে (ডুপ্লিকেট এড়ানো হচ্ছে)
                parsedEntries.forEach { repository.insert(it) }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun parseEntriesArray(rawArray: String, outputList: MutableList<DailyEntry>) {
        if (rawArray.isEmpty()) return
        val objects = rawArray.split("},{", "} , {", "}, {")
        for (objStr in objects) {
            val cleanObj = objStr.replace("{", "").replace("}", "")
            val pairs = cleanObj.split(",")
            var dateMillis: Long = 0
            var quantity = 0
            var expense = 0.0
            var note = ""
            var folderName = "সাধারণ হিসাব"

            for (pair in pairs) {
                val keyValue = pair.split(":")
                if (keyValue.size >= 2) {
                    val key = keyValue[0].trim().replace("\"", "")
                    val value = keyValue.drop(1).joinToString(":").trim()
                    when (key) {
                        "dateMillis" -> dateMillis = value.toLongOrNull() ?: 0
                        "quantity" -> quantity = value.toIntOrNull() ?: 0
                        "expense" -> expense = value.toDoubleOrNull() ?: 0.0
                        "note" -> note = value.removeSurrounding("\"").replace("\\\"", "\"")
                        "folderName" -> folderName = value.removeSurrounding("\"").replace("\\\"", "\"")
                    }
                }
            }
            if (dateMillis > 0) {
                outputList.add(
                    DailyEntry(
                        dateMillis = dateMillis,
                        quantity = quantity,
                        expense = expense,
                        note = note,
                        folderName = folderName
                    )
                )
            }
        }
    }

    companion object {
        // বাংলা সংখ্যায় রূপান্তর ফাংশন
        fun convertToBanglaDigits(input: String): String {
            val banglaDigits = mapOf(
                '0' to '০', '1' to '১', '2' to '২', '3' to '৩', '4' to '৪',
                '5' to '৫', '6' to '৬', '7' to '৭', '8' to '৮', '9' to '৯'
            )
            return input.map { char -> banglaDigits[char] ?: char }.joinToString("")
        }

        // সংখ্যা বিন্যাস ফাংশন
        fun formatNumber(value: Double, asPcs: Boolean = false, isCurrency: Boolean = false): String {
            val formatted = if (value % 1.0 == 0.0) {
                value.toInt().toString()
            } else {
                String.format(Locale.US, "%.1f", value)
            }
            val bangla = convertToBanglaDigits(formatted)
            return when {
                asPcs -> "$bangla পিস"
                isCurrency -> "৳ $bangla"
                else -> bangla
            }
        }

        fun formatInteger(value: Int, asPcs: Boolean = false): String {
            val bangla = convertToBanglaDigits(value.toString())
            return if (asPcs) "$bangla পিস" else bangla
        }
    }
}

data class DashboardSummary(
    val totalPcs: Int = 0,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val netProfit: Double = 0.0,
    val avgPcs: Double = 0.0,
    val avgExpense: Double = 0.0,
    val avgProfit: Double = 0.0,
    val totalDays: Int = 0,
    val maxIncome: Double = 0.0,
    val minExpense: Double = 0.0,
    val daysTargetMet: Int = 0,
    val targetMetPercent: Double = 0.0
)
