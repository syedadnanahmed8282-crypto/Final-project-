package com.example

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.AppDatabase
import com.example.data.model.DailyEntry
import com.example.data.repository.DailyEntryRepository
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- ENGLISH TO BENGALI DIGITS TRANSLATION UTILS ---
fun String.toBanglaDigits(): String {
    val englishDigits = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
    val banglaDigits = listOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    var result = ""
    for (char in this) {
        val index = englishDigits.indexOf(char)
        if (index != -1) {
            result += banglaDigits[index]
        } else {
            result += char
        }
    }
    return result
}

fun Number.toBangla(): String {
    val strVal = this.toString()
    // Strip decimal part if it's .0 as requested by user
    val formattedStr = if (this is Double || this is Float) {
        val doubleVal = this.toDouble()
        if (doubleVal % 1.0 == 0.0) {
            doubleVal.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", doubleVal)
        }
    } else {
        strVal
    }
    return formattedStr.toBanglaDigits()
}

fun getMonthNameBengali(index: Int): String {
    return when (index) {
        1 -> "১ম মাস"
        2 -> "২য় মাস"
        3 -> "৩য় মাস"
        4 -> "৪র্থ মাস"
        5 -> "৫ম মাস"
        6 -> "৬ষ্ঠ মাস"
        7 -> "৭ম মাস"
        8 -> "৮ম মাস"
        9 -> "৯ম মাস"
        10 -> "১০ম মাস"
        else -> "${index.toBangla()}তম মাস"
    }
}

// Premium Mode models (Top-level)
class PremiumDaySummary(
    val dateMillis: Long,
    val totalIncome: Double,
    val totalExpense: Double,
    val nasta: Double,
    val bhat: Double,
    val gariBhara: Double,
    val onnano: Double
)

class PremiumDayEditData(
    val dateMillis: Long,
    val income: Double,
    val nasta: Double,
    val bhat: Double,
    val gariBhara: Double,
    val onnano: Double
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = DailyEntryRepository(database.dailyEntryDao())
        val factory = MainViewModelFactory(repository, applicationContext)

        setContent {
            val viewModel: MainViewModel = viewModel(factory = factory)
            val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()

            MyApplicationTheme(themeName = currentTheme) {
                var showSplashScreen by remember { mutableStateOf(true) }

                if (showSplashScreen) {
                    SmartManagerSplashScreen(onFinish = { showSplashScreen = false })
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        TakaHishabMainScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

// --- VIEWMODEL & FACTORY DESIGN ---
class MainViewModel(private val repository: DailyEntryRepository, private val context: Context) : ViewModel() {
    private val prefs = context.getSharedPreferences("TakaHishabPrefs", Context.MODE_PRIVATE)

    // Profile and Google Auto-Sync States
    val googleEmail = MutableStateFlow(prefs.getString("GOOGLE_EMAIL", "") ?: "")
    val googleName = MutableStateFlow(prefs.getString("GOOGLE_NAME", "") ?: "")
    val isGoogleSignedIn = MutableStateFlow(prefs.getBoolean("IS_GOOGLE_SIGNED_IN", false))
    val profileName = MutableStateFlow(prefs.getString("PROFILE_NAME", "আহমেদ রাসেল") ?: "আহমেদ রাসেল")
    val profileAvatarIndex = MutableStateFlow(prefs.getInt("PROFILE_AVATAR_INDEX", 0))
    val profileCustomAvatarUri = MutableStateFlow(prefs.getString("PROFILE_CUSTOM_AVATAR_URI", "") ?: "")

    private var backupJob: kotlinx.coroutines.Job? = null

    fun triggerAutoBackup() {
        if (!isGoogleSignedIn.value) return
        backupJob?.cancel()
        backupJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(3000)
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null) {
                val backupContent = exportBackup()
                uploadToGoogleDrive(context, account, backupContent) { _, _ -> }
            }
        }
    }

    fun updateProfileName(name: String) {
        profileName.value = name
        prefs.edit().putString("PROFILE_NAME", name).apply()
    }

    fun updateProfileAvatarIndex(index: Int) {
        profileAvatarIndex.value = index
        // When setting standard avatar, clear custom URI
        profileCustomAvatarUri.value = ""
        prefs.edit().putInt("PROFILE_AVATAR_INDEX", index).putString("PROFILE_CUSTOM_AVATAR_URI", "").apply()
    }

    fun updateProfileCustomAvatarUri(uriString: String) {
        profileCustomAvatarUri.value = uriString
        prefs.edit().putString("PROFILE_CUSTOM_AVATAR_URI", uriString).apply()
    }

    fun setGoogleSignIn(email: String, name: String, signedIn: Boolean) {
        isGoogleSignedIn.value = signedIn
        googleEmail.value = email
        googleName.value = name
        prefs.edit()
            .putBoolean("IS_GOOGLE_SIGNED_IN", signedIn)
            .putString("GOOGLE_EMAIL", email)
            .putString("GOOGLE_NAME", name)
            .apply()
        if (signedIn) {
            // Also enable Google Drive auto backup
            if (profileName.value == "আহমেদ রাসেল" && name.isNotBlank()) {
                updateProfileName(name)
            }
            triggerAutoBackup()
        }
    }

    val currentTheme = MutableStateFlow(prefs.getString("THEME", "DEFAULT") ?: "DEFAULT")
    
    // Multi-Khata / Dual Mode States
    val currentKhataMode = MutableStateFlow(prefs.getString("KHATA_MODE", "MAIN") ?: "MAIN")
    
    val selectedFolderMain = MutableStateFlow(prefs.getString("SELECTED_FOLDER_MAIN", "সব ফোল্ডার") ?: "সব ফোল্ডার")
    val selectedFolderAlt = MutableStateFlow(prefs.getString("SELECTED_FOLDER_ALT", "সব ফোল্ডার") ?: "সব ফোল্ডার")
    val selectedFolderPremium = MutableStateFlow(prefs.getString("SELECTED_FOLDER_PREMIUM", "সব ফোল্ডার") ?: "সব ফোল্ডার")
    
    val selectedFolder = MutableStateFlow(
        when (prefs.getString("KHATA_MODE", "MAIN")) {
            "ALT" -> prefs.getString("SELECTED_FOLDER_ALT", "সব ফোল্ডার") ?: "সব ফোল্ডার"
            "PREMIUM" -> prefs.getString("SELECTED_FOLDER_PREMIUM", "সব ফোল্ডার") ?: "সব ফোল্ডার"
            else -> prefs.getString("SELECTED_FOLDER_MAIN", "সব ফোল্ডার") ?: "সব ফোল্ডার"
        }
    )

    val mainFolders = MutableStateFlow(
        prefs.getString("FOLDERS", "সাধারণ হিসাব,ব্যক্তিগত খরচ,ব্যবসার হিসাব,ফ্যামিলি বাজেট")
            ?.split(",")?.toMutableList() ?: mutableListOf("সাধারণ হিসাব", "ব্যক্তিগত খরচ", "ব্যবসার হিসাব", "ফ্যামিলি বাজেট")
    )
    
    val altFolders = MutableStateFlow(
        prefs.getString("FOLDERS_ALT", "ব্যক্তিগত খাতা,অফিস খরচ,বিকল্প হিসাব")
            ?.split(",")?.toMutableList() ?: mutableListOf("ব্যক্তিগত খাতা", "অফিস খরচ", "বিকল্প হিসাব")
    )

    val premiumFolders = MutableStateFlow(
        prefs.getString("FOLDERS_PREMIUM", "আয় খাতা,ব্যয় খাতা,ব্যক্তিগত খাতা,অন্যান্য")
            ?.split(",")?.toMutableList() ?: mutableListOf("আয় খাতা", "ব্যয় খাতা", "ব্যক্তিগত খাতা", "অন্যান্য")
    )

    val foldersList: StateFlow<List<String>> = combine(currentKhataMode, mainFolders, altFolders, premiumFolders) { mode, main, alt, premium ->
        when (mode) {
            "MAIN" -> main
            "ALT" -> alt
            else -> premium
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customCategoriesList = MutableStateFlow(
        prefs.getString("CUSTOM_CATEGORIES", "খাদ্য,যাতায়াত,বেতন,উপহার,অন্যান্য")
            ?.split(",")?.toMutableList() ?: mutableListOf("খাদ্য", "যাতায়াত", "বেতন", "উপহার", "অন্যান্য")
    )

    val wageRate = MutableStateFlow(prefs.getFloat("WAGE_RATE", 50.0f).toDouble())
    val dailyTarget = MutableStateFlow(prefs.getInt("DAILY_TARGET", 800))
    val dailyBudget = MutableStateFlow(prefs.getFloat("DAILY_BUDGET", 500.0f).toDouble())
    val monthlyBudget = MutableStateFlow(prefs.getFloat("MONTHLY_BUDGET", 15000.0f).toDouble())
    val monthlyIncomeTarget = MutableStateFlow(prefs.getFloat("MONTHLY_INCOME_TARGET", 20000.0f).toDouble())

    // Mode specific settings
    val mainDailyLimit = MutableStateFlow(prefs.getFloat("MAIN_DAILY_LIMIT", prefs.getFloat("DAILY_BUDGET", 500.0f)).toDouble())
    val mainMonthlyLimit = MutableStateFlow(prefs.getFloat("MAIN_MONTHLY_LIMIT", prefs.getFloat("MONTHLY_BUDGET", 15000.0f)).toDouble())
    val mainGoodsRate = MutableStateFlow(prefs.getFloat("MAIN_WAGE_RATE", prefs.getFloat("WAGE_RATE", 50.0f)).toDouble())
    val mainDailyTarget = MutableStateFlow(prefs.getInt("MAIN_DAILY_TARGET", prefs.getInt("DAILY_TARGET", 800)).toDouble())

    val altDailyLimit = MutableStateFlow(prefs.getFloat("ALT_DAILY_LIMIT", 500.0f).toDouble())
    val altMonthlyLimit = MutableStateFlow(prefs.getFloat("ALT_MONTHLY_LIMIT", 15000.0f).toDouble())
    val altGoodsRate = MutableStateFlow(prefs.getFloat("ALT_WAGE_RATE", 50.0f).toDouble())
    val altDailyTarget = MutableStateFlow(prefs.getInt("ALT_DAILY_TARGET", 800).toDouble())

    val premiumDailyLimit = MutableStateFlow(prefs.getFloat("PREMIUM_DAILY_LIMIT", 500.0f).toDouble())
    val premiumMonthlyLimit = MutableStateFlow(prefs.getFloat("PREMIUM_MONTHLY_LIMIT", 15000.0f).toDouble())
    val premiumGoodsRate = MutableStateFlow(prefs.getFloat("PREMIUM_WAGE_RATE", 50.0f).toDouble())
    val premiumDailyTarget = MutableStateFlow(prefs.getInt("PREMIUM_DAILY_TARGET", 800).toDouble())

    fun updateModeSettings(
        mode: String,
        dailyLimit: Double,
        monthlyLimit: Double,
        goodsRate: Double,
        dailyTargetVal: Double
    ) {
        val edit = prefs.edit()
        when (mode) {
            "MAIN" -> {
                mainDailyLimit.value = dailyLimit
                mainMonthlyLimit.value = monthlyLimit
                mainGoodsRate.value = goodsRate
                mainDailyTarget.value = dailyTargetVal
                edit.putFloat("MAIN_DAILY_LIMIT", dailyLimit.toFloat())
                edit.putFloat("MAIN_MONTHLY_LIMIT", monthlyLimit.toFloat())
                edit.putFloat("MAIN_WAGE_RATE", goodsRate.toFloat())
                edit.putInt("MAIN_DAILY_TARGET", dailyTargetVal.toInt())
            }
            "ALT" -> {
                altDailyLimit.value = dailyLimit
                altMonthlyLimit.value = monthlyLimit
                altGoodsRate.value = goodsRate
                altDailyTarget.value = dailyTargetVal
                edit.putFloat("ALT_DAILY_LIMIT", dailyLimit.toFloat())
                edit.putFloat("ALT_MONTHLY_LIMIT", monthlyLimit.toFloat())
                edit.putFloat("ALT_WAGE_RATE", goodsRate.toFloat())
                edit.putInt("ALT_DAILY_TARGET", dailyTargetVal.toInt())
            }
            "PREMIUM" -> {
                premiumDailyLimit.value = dailyLimit
                premiumMonthlyLimit.value = monthlyLimit
                premiumGoodsRate.value = goodsRate
                premiumDailyTarget.value = dailyTargetVal
                edit.putFloat("PREMIUM_DAILY_LIMIT", dailyLimit.toFloat())
                edit.putFloat("PREMIUM_MONTHLY_LIMIT", monthlyLimit.toFloat())
                edit.putFloat("PREMIUM_WAGE_RATE", goodsRate.toFloat())
                edit.putInt("PREMIUM_DAILY_TARGET", dailyTargetVal.toInt())
            }
        }
        edit.apply()

        // If currently active mode is the one being updated, sync active flows
        if (currentKhataMode.value == mode) {
            wageRate.value = goodsRate
            dailyTarget.value = dailyTargetVal.toInt()
            dailyBudget.value = dailyLimit
            monthlyBudget.value = monthlyLimit
            prefs.edit()
                .putFloat("WAGE_RATE", goodsRate.toFloat())
                .putInt("DAILY_TARGET", dailyTargetVal.toInt())
                .putFloat("DAILY_BUDGET", dailyLimit.toFloat())
                .putFloat("MONTHLY_BUDGET", monthlyLimit.toFloat())
                .apply()
        }
    }

    // Premium Mode Targets
    val premiumDailyIncomeTarget = MutableStateFlow(prefs.getFloat("PREMIUM_DAILY_INCOME_TARGET", 1000.0f).toDouble())
    val premiumDailyExpenseTarget = MutableStateFlow(prefs.getFloat("PREMIUM_DAILY_EXPENSE_TARGET", 500.0f).toDouble())

    fun updatePremiumTargets(newIncomeTarget: Double, newExpenseTarget: Double) {
        premiumDailyIncomeTarget.value = newIncomeTarget
        premiumDailyExpenseTarget.value = newExpenseTarget
        prefs.edit()
            .putFloat("PREMIUM_DAILY_INCOME_TARGET", newIncomeTarget.toFloat())
            .putFloat("PREMIUM_DAILY_EXPENSE_TARGET", newExpenseTarget.toFloat())
            .apply()
    }

    fun getDayStartMillis(millis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun addOrUpdatePremiumIncome(dateMillis: Long, amount: Double) {
        viewModelScope.launch {
            val dayStart = getDayStartMillis(dateMillis)
            val existingList = repository.allEntries.first()
            val existing = existingList.find { 
                it.folderName == "✦ প্রিমিয়াম" && 
                getDayStartMillis(it.dateMillis) == dayStart && 
                it.isIncome && 
                it.category == "আয়" 
            }
            if (existing != null) {
                repository.update(existing.copy(income = existing.income + amount))
            } else {
                repository.insert(
                    DailyEntry(
                        dateMillis = dayStart,
                        income = amount,
                        isIncome = true,
                        category = "আয়",
                        folderName = "✦ প্রিমিয়াম"
                    )
                )
            }
        }
    }

    fun addOrUpdatePremiumExpense(dateMillis: Long, category: String, amount: Double) {
        viewModelScope.launch {
            val dayStart = getDayStartMillis(dateMillis)
            val existingList = repository.allEntries.first()
            val existing = existingList.find { 
                it.folderName == "✦ প্রিমিয়াম" && 
                getDayStartMillis(it.dateMillis) == dayStart && 
                !it.isIncome && 
                it.category == category 
            }
            if (existing != null) {
                repository.update(existing.copy(expense = existing.expense + amount))
            } else {
                repository.insert(
                    DailyEntry(
                        dateMillis = dayStart,
                        expense = amount,
                        isIncome = false,
                        category = category,
                        folderName = "✦ প্রিমিয়াম"
                    )
                )
            }
        }
    }

    fun savePremiumDayData(dateMillis: Long, income: Double, nasta: Double, bhat: Double, gariBhara: Double, onnano: Double, folderName: String) {
        viewModelScope.launch {
            val dayStart = getDayStartMillis(dateMillis)
            val existingList = repository.allEntries.first()
            
            // Determine the target folder name
            var resolvedFolder = folderName
            if (resolvedFolder == "সব ফোল্ডার") {
                val existingDayEntries = existingList.filter { 
                    it.folderName.startsWith("✦ ") && 
                    getDayStartMillis(it.dateMillis) == dayStart 
                }
                val foundFolderWithDiamond = existingDayEntries.firstOrNull()?.folderName
                if (foundFolderWithDiamond != null) {
                    resolvedFolder = foundFolderWithDiamond.removePrefix("✦ ")
                } else {
                    resolvedFolder = premiumFolders.value.firstOrNull() ?: "আয় খাতা"
                }
            }

            val folderWithDiamond = if (resolvedFolder.startsWith("✦ ")) resolvedFolder else "✦ $resolvedFolder"
            
            val toDelete = existingList.filter { 
                it.folderName == folderWithDiamond && 
                getDayStartMillis(it.dateMillis) == dayStart 
            }
            for (entry in toDelete) {
                repository.delete(entry)
            }
            if (income > 0.0) {
                repository.insert(
                    DailyEntry(
                        dateMillis = dayStart,
                        income = income,
                        isIncome = true,
                        category = "আয়",
                        folderName = folderWithDiamond
                    )
                )
            }
            if (nasta > 0.0) {
                repository.insert(
                    DailyEntry(
                        dateMillis = dayStart,
                        expense = nasta,
                        isIncome = false,
                        category = "নাস্তা",
                        folderName = folderWithDiamond
                    )
                )
            }
            if (bhat > 0.0) {
                repository.insert(
                    DailyEntry(
                        dateMillis = dayStart,
                        expense = bhat,
                        isIncome = false,
                        category = "ভাত",
                        folderName = folderWithDiamond
                    )
                )
            }
            if (gariBhara > 0.0) {
                repository.insert(
                    DailyEntry(
                        dateMillis = dayStart,
                        expense = gariBhara,
                        isIncome = false,
                        category = "গাড়ি ভাড়া",
                        folderName = folderWithDiamond
                    )
                )
            }
            if (onnano > 0.0) {
                repository.insert(
                    DailyEntry(
                        dateMillis = dayStart,
                        expense = onnano,
                        isIncome = false,
                        category = "অন্যান্য",
                        folderName = folderWithDiamond
                    )
                )
            }
        }
    }

    fun deletePremiumDayData(dateMillis: Long, folderName: String) {
        viewModelScope.launch {
            val dayStart = getDayStartMillis(dateMillis)
            val existingList = repository.allEntries.first()
            val toDelete = existingList.filter { 
                val isPremiumFolder = it.folderName.startsWith("✦ ")
                val matchesFolder = if (folderName == "সব ফোল্ডার") isPremiumFolder else {
                    val folderWithDiamond = if (folderName.startsWith("✦ ")) folderName else "✦ $folderName"
                    it.folderName == folderWithDiamond
                }
                matchesFolder && getDayStartMillis(it.dateMillis) == dayStart 
            }
            for (entry in toDelete) {
                repository.delete(entry)
            }
        }
    }

    val allEntries: StateFlow<List<DailyEntry>> = combine(repository.allEntries, currentKhataMode) { entries, mode ->
        when (mode) {
            "MAIN" -> {
                entries.filter { !it.folderName.startsWith("★ ") && !it.folderName.startsWith("✦ ") }
            }
            "ALT" -> {
                entries.filter { it.folderName.startsWith("★ ") }
                    .map { it.copy(folderName = it.folderName.removePrefix("★ ")) }
            }
            else -> { // PREMIUM
                entries.filter { it.folderName.startsWith("✦ ") }
                    .map { it.copy(folderName = it.folderName.removePrefix("✦ ")) }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val rawAllEntries: StateFlow<List<DailyEntry>> = repository.allEntries.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun changeTheme(theme: String) {
        currentTheme.value = theme
        prefs.edit().putString("THEME", theme).apply()
    }

    fun changeKhataMode(mode: String) {
        currentKhataMode.value = mode
        prefs.edit().putString("KHATA_MODE", mode).apply()
        when (mode) {
            "MAIN" -> selectedFolder.value = selectedFolderMain.value
            "ALT" -> selectedFolder.value = selectedFolderAlt.value
            "PREMIUM" -> selectedFolder.value = selectedFolderPremium.value
        }
        val limit = when (mode) {
            "MAIN" -> mainDailyLimit.value
            "ALT" -> altDailyLimit.value
            else -> premiumDailyLimit.value
        }
        val mLimit = when (mode) {
            "MAIN" -> mainMonthlyLimit.value
            "ALT" -> altMonthlyLimit.value
            else -> premiumMonthlyLimit.value
        }
        val rate = when (mode) {
            "MAIN" -> mainGoodsRate.value
            "ALT" -> altGoodsRate.value
            else -> premiumGoodsRate.value
        }
        val target = when (mode) {
            "MAIN" -> mainDailyTarget.value
            "ALT" -> altDailyTarget.value
            else -> premiumDailyTarget.value
        }
        dailyBudget.value = limit
        monthlyBudget.value = mLimit
        wageRate.value = rate
        dailyTarget.value = target.toInt()
    }

    fun selectFolder(folder: String) {
        selectedFolder.value = folder
        when (currentKhataMode.value) {
            "MAIN" -> {
                selectedFolderMain.value = folder
                prefs.edit().putString("SELECTED_FOLDER_MAIN", folder).apply()
            }
            "ALT" -> {
                selectedFolderAlt.value = folder
                prefs.edit().putString("SELECTED_FOLDER_ALT", folder).apply()
            }
            "PREMIUM" -> {
                selectedFolderPremium.value = folder
                prefs.edit().putString("SELECTED_FOLDER_PREMIUM", folder).apply()
            }
        }
        prefs.edit().putString("SELECTED_FOLDER", folder).apply()
    }

    fun addFolder(folder: String) {
        if (folder.isNotBlank() && !folder.startsWith("★ ") && !folder.startsWith("✦ ")) {
            when (currentKhataMode.value) {
                "MAIN" -> {
                    val current = mainFolders.value.toMutableList()
                    if (!current.contains(folder)) {
                        current.add(folder)
                        mainFolders.value = current
                        prefs.edit().putString("FOLDERS", current.joinToString(",")).apply()
                    }
                }
                "ALT" -> {
                    val current = altFolders.value.toMutableList()
                    if (!current.contains(folder)) {
                        current.add(folder)
                        altFolders.value = current
                        prefs.edit().putString("FOLDERS_ALT", current.joinToString(",")).apply()
                    }
                }
                "PREMIUM" -> {
                    val current = premiumFolders.value.toMutableList()
                    if (!current.contains(folder)) {
                        current.add(folder)
                        premiumFolders.value = current
                        prefs.edit().putString("FOLDERS_PREMIUM", current.joinToString(",")).apply()
                    }
                }
            }
        }
    }

    fun deleteFolder(folder: String) {
        when (currentKhataMode.value) {
            "MAIN" -> {
                val current = mainFolders.value.toMutableList()
                if (current.contains(folder) && folder != "সাধারণ হিসাব" && current.size > 1) {
                    current.remove(folder)
                    mainFolders.value = current
                    prefs.edit().putString("FOLDERS", current.joinToString(",")).apply()
                    if (selectedFolder.value == folder) {
                        selectFolder("সব ফোল্ডার")
                    }
                    viewModelScope.launch {
                        val entriesInFolder = repository.allEntries.first().filter { it.folderName == folder }
                        for (entry in entriesInFolder) {
                            repository.delete(entry)
                        }
                    }
                }
            }
            "ALT" -> {
                val current = altFolders.value.toMutableList()
                val folderWithStar = "★ $folder"
                if (current.contains(folder) && folder != "সাধারণ হিসাব" && folder != "ব্যক্তিগত খাতা" && current.size > 1) {
                    current.remove(folder)
                    altFolders.value = current
                    prefs.edit().putString("FOLDERS_ALT", current.joinToString(",")).apply()
                    if (selectedFolder.value == folder) {
                        selectFolder("সব ফোল্ডার")
                    }
                    viewModelScope.launch {
                        val entriesInFolder = repository.allEntries.first().filter { it.folderName == folderWithStar }
                        for (entry in entriesInFolder) {
                            repository.delete(entry)
                        }
                    }
                }
            }
            "PREMIUM" -> {
                val current = premiumFolders.value.toMutableList()
                val folderWithDiamond = "✦ $folder"
                if (current.contains(folder) && folder != "আয় খাতা" && folder != "ব্যয় খাতা" && current.size > 1) {
                    current.remove(folder)
                    premiumFolders.value = current
                    prefs.edit().putString("FOLDERS_PREMIUM", current.joinToString(",")).apply()
                    if (selectedFolder.value == folder) {
                        selectFolder("সব ফোল্ডার")
                    }
                    viewModelScope.launch {
                        val entriesInFolder = repository.allEntries.first().filter { it.folderName == folderWithDiamond }
                        for (entry in entriesInFolder) {
                            repository.delete(entry)
                        }
                    }
                }
            }
        }
    }

    fun addCustomCategory(cat: String) {
        val current = customCategoriesList.value.toMutableList()
        if (!current.contains(cat) && cat.isNotBlank()) {
            current.add(cat)
            customCategoriesList.value = current
            prefs.edit().putString("CUSTOM_CATEGORIES", current.joinToString(",")).apply()
        }
    }

    fun updateSettings(newRate: Double, newTarget: Int, newBudget: Double) {
        wageRate.value = newRate
        dailyTarget.value = newTarget
        dailyBudget.value = newBudget
        prefs.edit()
            .putFloat("WAGE_RATE", newRate.toFloat())
            .putInt("DAILY_TARGET", newTarget)
            .putFloat("DAILY_BUDGET", newBudget.toFloat())
            .apply()
    }

    fun updateMonthlyBudget(newMonthlyBudget: Double) {
        monthlyBudget.value = newMonthlyBudget
        prefs.edit().putFloat("MONTHLY_BUDGET", newMonthlyBudget.toFloat()).apply()
    }

    fun updateMonthlyIncomeTarget(newMonthlyIncomeTarget: Double) {
        monthlyIncomeTarget.value = newMonthlyIncomeTarget
        prefs.edit().putFloat("MONTHLY_INCOME_TARGET", newMonthlyIncomeTarget.toFloat()).apply()
    }

    fun insertEntry(entry: DailyEntry) {
        viewModelScope.launch {
            val adjustedEntry = if (currentKhataMode.value == "ALT" && !entry.folderName.startsWith("★ ")) {
                entry.copy(folderName = "★ ${entry.folderName}")
            } else if (currentKhataMode.value == "PREMIUM" && !entry.folderName.startsWith("✦ ")) {
                entry.copy(folderName = "✦ ${entry.folderName}")
            } else {
                entry
            }
            repository.insert(adjustedEntry)
            triggerAutoBackup()
        }
    }

    fun updateEntry(entry: DailyEntry) {
        viewModelScope.launch {
            val adjustedEntry = if (currentKhataMode.value == "ALT" && !entry.folderName.startsWith("★ ")) {
                entry.copy(folderName = "★ ${entry.folderName}")
            } else if (currentKhataMode.value == "PREMIUM" && !entry.folderName.startsWith("✦ ")) {
                entry.copy(folderName = "✦ ${entry.folderName}")
            } else {
                entry
            }
            repository.update(adjustedEntry)
            triggerAutoBackup()
        }
    }

    fun deleteEntry(entry: DailyEntry) {
        viewModelScope.launch {
            repository.delete(entry)
            triggerAutoBackup()
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAll()
            triggerAutoBackup()
        }
    }

    fun exportBackup(): String {
        return try {
            val entries = rawAllEntries.value
            val jsonArray = JSONArray()
            for (e in entries) {
                val obj = JSONObject()
                obj.put("id", e.id)
                obj.put("dateMillis", e.dateMillis)
                obj.put("quantity", e.quantity)
                obj.put("expense", e.expense)
                obj.put("note", e.note)
                obj.put("folderName", e.folderName)
                obj.put("income", e.income)
                obj.put("category", e.category)
                obj.put("isIncome", e.isIncome)
                jsonArray.put(obj)
            }
            val root = JSONObject()
            root.put("entries", jsonArray)
            root.put("wageRate", wageRate.value)
            root.put("dailyTarget", dailyTarget.value)
            root.put("dailyBudget", dailyBudget.value)
            root.put("monthlyBudget", monthlyBudget.value)
            root.put("monthlyIncomeTarget", monthlyIncomeTarget.value)
            root.put("folders", mainFolders.value.joinToString(","))
            root.put("categories", customCategoriesList.value.joinToString(","))
            
            // Return base64 or raw string
            root.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun exportBackupCsv(): String {
        return try {
            val entries = rawAllEntries.value
            val sb = java.lang.StringBuilder()
            // CSV Header
            sb.append("আইডি,তারিখ (Timestamp),তারিখ (পঠনযোগ্য),খাতা,ধরণ,ক্যাটাগরি,পরিমাণ (পিস),আয় (টাকা),ব্যয় (টাকা),মন্তব্য\n")
            
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            for (e in entries) {
                val dateStr = sdf.format(Date(e.dateMillis))
                val typeStr = if (e.isIncome) "আয়" else "ব্যয়/মাল"
                
                fun escapeCsv(value: String): String {
                    val escaped = value.replace("\"", "\"\"")
                    return if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")) {
                        "\"$escaped\""
                    } else {
                        escaped
                    }
                }
                
                sb.append("${e.id},")
                  .append("${e.dateMillis},")
                  .append("${escapeCsv(dateStr)},")
                  .append("${escapeCsv(e.folderName)},")
                  .append("${escapeCsv(typeStr)},")
                  .append("${escapeCsv(e.category)},")
                  .append("${e.quantity},")
                  .append("${e.income},")
                  .append("${e.expense},")
                  .append("${escapeCsv(e.note)}\n")
            }
            sb.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun importBackup(jsonStr: String, overwrite: Boolean = true): Boolean {
        return try {
            val root = JSONObject(jsonStr)
            
            if (root.has("wageRate")) {
                val newRate = root.getDouble("wageRate")
                val newTarget = root.getInt("dailyTarget")
                val newBudget = root.optDouble("dailyBudget", 500.0)
                updateSettings(newRate, newTarget, newBudget)
            }

            if (root.has("monthlyBudget")) {
                val newMonthlyBudget = root.optDouble("monthlyBudget", 15000.0)
                updateMonthlyBudget(newMonthlyBudget)
            }

            if (root.has("monthlyIncomeTarget")) {
                val newMonthlyIncomeTarget = root.optDouble("monthlyIncomeTarget", 20000.0)
                updateMonthlyIncomeTarget(newMonthlyIncomeTarget)
            }

            if (root.has("folders")) {
                val list = root.getString("folders").split(",").toMutableList()
                if (currentKhataMode.value == "MAIN") {
                    mainFolders.value = list
                    prefs.edit().putString("FOLDERS", list.joinToString(",")).apply()
                } else {
                    altFolders.value = list
                    prefs.edit().putString("FOLDERS_ALT", list.joinToString(",")).apply()
                }
            }

            if (root.has("categories")) {
                val list = root.getString("categories").split(",").toMutableList()
                customCategoriesList.value = list
                prefs.edit().putString("CUSTOM_CATEGORIES", list.joinToString(",")).apply()
            }

            val entriesArray = root.getJSONArray("entries")
            viewModelScope.launch {
                val existingEntries = repository.allEntries.first()
                if (overwrite) {
                    repository.clearAll()
                }
                for (i in 0 until entriesArray.length()) {
                    val obj = entriesArray.getJSONObject(i)
                    val dateMillis = obj.getLong("dateMillis")
                    val quantity = obj.getInt("quantity")
                    val expense = obj.optDouble("expense", 0.0)
                    val note = obj.optString("note", "")
                    val folderName = obj.optString("folderName", "সাধারণ হিসাব")
                    val income = obj.optDouble("income", 0.0)
                    val category = obj.optString("category", "অন্যান্য")
                    val isIncome = obj.optBoolean("isIncome", false)
                    
                    if (!overwrite) {
                        val duplicateExists = existingEntries.any {
                            it.dateMillis == dateMillis &&
                            it.quantity == quantity &&
                            Math.abs(it.expense - expense) < 0.01 &&
                            it.note == note &&
                            it.folderName == folderName &&
                            Math.abs(it.income - income) < 0.01 &&
                            it.category == category &&
                            it.isIncome == isIncome
                        }
                        if (duplicateExists) continue
                    }
                    
                    val entry = DailyEntry(
                        dateMillis = dateMillis,
                        quantity = quantity,
                        expense = expense,
                        note = note,
                        folderName = folderName,
                        income = income,
                        category = category,
                        isIncome = isIncome
                    )
                    repository.insert(entry)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importBackupCsv(csvStr: String, overwrite: Boolean = false): Boolean {
        return try {
            val lines = csvStr.lines()
            if (lines.size <= 1) return false
            
            val headers = lines[0].split(",")
            if (headers.size < 5) return false
            
            viewModelScope.launch {
                val existingEntries = repository.allEntries.first()
                if (overwrite) {
                    repository.clearAll()
                }
                
                for (i in 1 until lines.size) {
                    val line = lines[i].trim()
                    if (line.isEmpty()) continue
                    
                    val tokens = parseCsvLine(line)
                    if (tokens.size < 10) continue
                    
                    val dateMillis = tokens[1].toLongOrNull() ?: continue
                    val folderName = tokens[3]
                    val category = tokens[5]
                    val quantity = tokens[6].toIntOrNull() ?: 0
                    val income = tokens[7].toDoubleOrNull() ?: 0.0
                    val expense = tokens[8].toDoubleOrNull() ?: 0.0
                    val note = tokens[9]
                    val isIncome = income > 0.0 || tokens[4] == "আয়"
                    
                    if (!overwrite) {
                        val duplicateExists = existingEntries.any {
                            it.dateMillis == dateMillis &&
                            it.quantity == quantity &&
                            Math.abs(it.expense - expense) < 0.01 &&
                            it.note == note &&
                            it.folderName == folderName &&
                            Math.abs(it.income - income) < 0.01 &&
                            it.category == category &&
                            it.isIncome == isIncome
                        }
                        if (duplicateExists) continue
                    }
                    
                    val entry = DailyEntry(
                        dateMillis = dateMillis,
                        quantity = quantity,
                        expense = expense,
                        note = note,
                        folderName = folderName,
                        income = income,
                        category = category,
                        isIncome = isIncome
                    )
                    repository.insert(entry)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var curVal = java.lang.StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (inQuotes) {
                if (ch == '\"') {
                    if (i + 1 < line.length && line[i + 1] == '\"') {
                        curVal.append('\"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    curVal.append(ch)
                }
            } else {
                if (ch == '\"') {
                    inQuotes = true
                } else if (ch == ',') {
                    result.add(curVal.toString())
                    curVal = java.lang.StringBuilder()
                } else {
                    curVal.append(ch)
                }
            }
            i++
        }
        result.add(curVal.toString())
        return result
    }

    fun uploadToGoogleDrive(
        context: Context,
        account: GoogleSignInAccount,
        backupContent: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val client = OkHttpClient()
        val scopeName = "oauth2:https://www.googleapis.com/auth/drive.appdata"
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val accessToken = GoogleAuthUtil.getToken(context, account.account!!, scopeName)
                if (accessToken.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "গুগল ড্রাইভ টোকেন পাওয়া যায়নি!")
                    }
                    return@launch
                }
                
                // 1. Search for existing file
                val searchRequest = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='backup_takahishab.json'")
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                    
                client.newCall(searchRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            onResult(false, "ড্রাইভ ফাইল সার্চ ব্যর্থ: ${response.code}")
                        }
                        return@launch
                    }
                    
                    val bodyStr = response.body?.string() ?: ""
                    val searchRoot = JSONObject(bodyStr)
                    val filesArray = searchRoot.getJSONArray("files")
                    var fileId: String? = null
                    if (filesArray.length() > 0) {
                        fileId = filesArray.getJSONObject(0).getString("id")
                    }
                    
                    if (fileId != null) {
                        // Update existing file
                        val mediaRequest = Request.Builder()
                            .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
                            .header("Authorization", "Bearer $accessToken")
                            .patch(backupContent.toRequestBody("application/json".toMediaTypeOrNull()))
                            .build()
                            
                        client.newCall(mediaRequest).execute().use { updateResponse ->
                            withContext(Dispatchers.Main) {
                                if (updateResponse.isSuccessful) {
                                    onResult(true, "গুগল ড্রাইভে ডাটা ব্যাকআপ সফল হয়েছে!")
                                } else {
                                    onResult(false, "ড্রাইভ ফাইল আপডেট ব্যর্থ: ${updateResponse.code}")
                                }
                            }
                        }
                    } else {
                        // Create new file metadata
                        val metadataJson = JSONObject()
                        metadataJson.put("name", "backup_takahishab.json")
                        val parents = JSONArray()
                        parents.put("appDataFolder")
                        metadataJson.put("parents", parents)
                        
                        val createMetaRequest = Request.Builder()
                            .url("https://www.googleapis.com/drive/v3/files")
                            .header("Authorization", "Bearer $accessToken")
                            .post(metadataJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                            .build()
                            
                        client.newCall(createMetaRequest).execute().use { metaResponse ->
                            if (!metaResponse.isSuccessful) {
                                withContext(Dispatchers.Main) {
                                    onResult(false, "ড্রাইভ মেটাডাটা তৈরি ব্যর্থ: ${metaResponse.code}")
                                }
                                return@launch
                            }
                            
                            val newFileId = JSONObject(metaResponse.body?.string() ?: "").getString("id")
                            
                            // Upload content
                            val mediaRequest = Request.Builder()
                                .url("https://www.googleapis.com/upload/drive/v3/files/$newFileId?uploadType=media")
                                .header("Authorization", "Bearer $accessToken")
                                .patch(backupContent.toRequestBody("application/json".toMediaTypeOrNull()))
                                .build()
                                
                            client.newCall(mediaRequest).execute().use { createResponse ->
                                withContext(Dispatchers.Main) {
                                    if (createResponse.isSuccessful) {
                                        onResult(true, "গুগল ড্রাইভে ব্যাকআপ সফলভাবে সংরক্ষণ করা হয়েছে!")
                                    } else {
                                        onResult(false, "ড্রাইভ আপলোড ব্যর্থ: ${createResponse.code}")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResult(false, "ত্রুটি: ${e.localizedMessage}")
                }
            }
        }
    }

    fun restoreFromGoogleDrive(
        context: Context,
        account: GoogleSignInAccount,
        onResult: (Boolean, String, String?) -> Unit
    ) {
        val client = OkHttpClient()
        val scopeName = "oauth2:https://www.googleapis.com/auth/drive.appdata"
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val accessToken = GoogleAuthUtil.getToken(context, account.account!!, scopeName)
                if (accessToken.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "গুগল ড্রাইভ টোকেন পাওয়া যায়নি!", null)
                    }
                    return@launch
                }
                
                // 1. Search for the file
                val searchRequest = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='backup_takahishab.json'")
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                    
                client.newCall(searchRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            onResult(false, "ড্রাইভ সার্চ ব্যর্থ: ${response.code}", null)
                        }
                        return@launch
                    }
                    
                    val bodyStr = response.body?.string() ?: ""
                    val searchRoot = JSONObject(bodyStr)
                    val filesArray = searchRoot.getJSONArray("files")
                    if (filesArray.length() == 0) {
                        withContext(Dispatchers.Main) {
                            onResult(false, "গুগল ড্রাইভে কোনো ব্যাকআপ ফাইল পাওয়া যায়নি!", null)
                        }
                        return@launch
                    }
                    
                    val fileId = filesArray.getJSONObject(0).getString("id")
                    
                    // 2. Download the file
                    val downloadRequest = Request.Builder()
                        .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                        .header("Authorization", "Bearer $accessToken")
                        .build()
                        
                    client.newCall(downloadRequest).execute().use { downloadResponse ->
                        withContext(Dispatchers.Main) {
                            if (downloadResponse.isSuccessful) {
                                val backupContent = downloadResponse.body?.string() ?: ""
                                onResult(true, "গুগল ড্রাইভ থেকে ব্যাকআপ সফলভাবে ডাউনলোড করা হয়েছে!", backupContent)
                            } else {
                                onResult(false, "ডাউনলোড ব্যর্থ: ${downloadResponse.code}", null)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResult(false, "ত্রুটি: ${e.localizedMessage}", null)
                }
            }
        }
    }
}

class MainViewModelFactory(
    private val repository: DailyEntryRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// --- MAIN SCREEN LAYOUT WITH FULL COMPOSE EDGE TO EDGE ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TakaHishabMainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val entries by viewModel.allEntries.collectAsStateWithLifecycle()
    val rawEntries by viewModel.rawAllEntries.collectAsStateWithLifecycle()
    val folders by viewModel.foldersList.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val categories by viewModel.customCategoriesList.collectAsStateWithLifecycle()

    val wageRate by viewModel.wageRate.collectAsStateWithLifecycle()
    val dailyTarget by viewModel.dailyTarget.collectAsStateWithLifecycle()
    val dailyBudget by viewModel.dailyBudget.collectAsStateWithLifecycle()
    val monthlyBudget by viewModel.monthlyBudget.collectAsStateWithLifecycle()
    val monthlyIncomeTarget by viewModel.monthlyIncomeTarget.collectAsStateWithLifecycle()
    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()
    val currentKhataMode by viewModel.currentKhataMode.collectAsStateWithLifecycle()
    val currentAvatarIdx by viewModel.profileAvatarIndex.collectAsStateWithLifecycle()
    val profileCustomAvatarUri by viewModel.profileCustomAvatarUri.collectAsStateWithLifecycle()

    // Screen controllers
    var activeSubScreen by rememberSaveable { mutableStateOf("HOME") }
    var activeBottomTab by rememberSaveable { mutableStateOf("Home") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("সব ক্যাটাগরি") }
    var sortNewestFirst by remember { mutableStateOf(true) }
    var showGraphs by remember { mutableStateOf(true) }
    var showMonthlyProgressSection by remember { mutableStateOf(true) }
    var showFoldersSection by remember { mutableStateOf(false) }

    // Dialog flags
    var showAddDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var entryToEdit by remember { mutableStateOf<DailyEntry?>(null) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showIncomeTargetDialog by remember { mutableStateOf(false) }
    var showMonthlyHistoryDialog by remember { mutableStateOf(false) }

    fun getDayStartMillis(millis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    val premiumDailyIncomeTarget by viewModel.premiumDailyIncomeTarget.collectAsStateWithLifecycle()
    val premiumDailyExpenseTarget by viewModel.premiumDailyExpenseTarget.collectAsStateWithLifecycle()

    var premiumIncomeInput by remember { mutableStateOf("") }
    var premiumExpenseInput by remember { mutableStateOf("") }
    var premiumSelectedDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var showPremiumDatePicker by remember { mutableStateOf(false) }
    var premiumEditDayDialogData by remember { mutableStateOf<PremiumDayEditData?>(null) }
    var premiumDayToDeleteMillis by remember { mutableStateOf<Long?>(null) }

    var incomeTargetInput by remember(premiumDailyIncomeTarget) { mutableStateOf(premiumDailyIncomeTarget.toInt().toString()) }
    var expenseTargetInput by remember(premiumDailyExpenseTarget) { mutableStateOf(premiumDailyExpenseTarget.toInt().toString()) }

    var isCategoryBreakdownMinimized by remember { mutableStateOf(false) }
    var isGraphMinimized by remember { mutableStateOf(false) }
    var selectedGraphFilter by remember { mutableStateOf("LAST_7_DAYS") }
    var graphOffsetWeeks by remember { mutableStateOf(0) }
    var showPremiumAddDialog by remember { mutableStateOf(false) }
    var showPremiumTargetsEditDialog by remember { mutableStateOf(false) }
    var sortNewestFirstPremium by remember { mutableStateOf(true) }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    BackHandler(enabled = activeBottomTab != "Home" || activeSubScreen != "HOME") {
        if (activeBottomTab != "Home") {
            activeBottomTab = "Home"
        } else if (activeSubScreen == "DAILY_ACCOUNTS") {
            activeSubScreen = "NOTEBOOK_SELECTOR"
        } else if (activeSubScreen != "HOME") {
            activeSubScreen = "HOME"
        }
    }

    // Glassmorphic Theme configuration helper
    val isGlass = currentTheme == "GLASS"
    val cardBgColor = if (isGlass) {
        Color.White.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val cardBorderColor = if (isGlass) {
        Color.White.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }

    // Filtered entries list
    val filteredEntries = remember(entries, selectedFolder, searchQuery, selectedCategoryFilter, sortNewestFirst, currentKhataMode) {
        var list = entries.filter {
            selectedFolder == "সব ফোল্ডার" || it.folderName == selectedFolder
        }

        if (currentKhataMode == "ALT") {
            list = list.filter { !it.isIncome }
        }

        if (searchQuery.isNotBlank()) {
            list = list.filter {
                it.note.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true) ||
                it.quantity.toString().contains(searchQuery) ||
                it.expense.toString().contains(searchQuery) ||
                it.income.toString().contains(searchQuery)
            }
        }

        if (selectedCategoryFilter != "সব ক্যাটাগরি") {
            list = list.filter { it.category == selectedCategoryFilter }
        }

        list = if (sortNewestFirst) {
            list.sortedWith(compareByDescending<DailyEntry> { it.dateMillis }.thenByDescending { it.id })
        } else {
            list.sortedWith(compareBy<DailyEntry> { it.dateMillis }.thenBy { it.id })
        }
        list
    }

    val premiumDailyGrouped = remember(filteredEntries, sortNewestFirstPremium) {
        val groups = mutableMapOf<Long, MutableList<DailyEntry>>()
        for (entry in filteredEntries) {
            val dayStart = getDayStartMillis(entry.dateMillis)
            if (!groups.containsKey(dayStart)) {
                groups[dayStart] = mutableListOf()
            }
            groups[dayStart]?.add(entry)
        }
        val mapped = groups.map { (dayStart, entries) ->
            val totalIncome = entries.filter { it.isIncome }.sumOf { it.income }
            val nasta = entries.filter { !it.isIncome && it.category == "নাস্তা" }.sumOf { it.expense }
            val bhat = entries.filter { !it.isIncome && it.category == "ভাত" }.sumOf { it.expense }
            val gariBhara = entries.filter { !it.isIncome && it.category == "গাড়ি ভাড়া" }.sumOf { it.expense }
            val onnano = entries.filter { !it.isIncome && it.category == "অন্যান্য" }.sumOf { it.expense }
            val totalExpense = nasta + bhat + gariBhara + onnano
            PremiumDaySummary(
                dateMillis = dayStart,
                totalIncome = totalIncome,
                totalExpense = totalExpense,
                nasta = nasta,
                bhat = bhat,
                gariBhara = gariBhara,
                onnano = onnano
            )
        }
        if (sortNewestFirstPremium) {
            mapped.sortedByDescending { it.dateMillis }
        } else {
            mapped.sortedBy { it.dateMillis }
        }
    }

    // Analytics calculations
    val analytics = remember(filteredEntries, wageRate) {
        var totalQty = 0
        var totalIncome = 0.0
        var totalExpense = 0.0

        val uniqueDaysSet = mutableSetOf<String>()
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        for (e in filteredEntries) {
            totalQty += e.quantity
            // Production Income = (quantity / 100) * wageRate
            val prodIncome = (e.quantity / 100.0) * wageRate
            val entryIncome = if (e.isIncome) e.income else prodIncome
            totalIncome += entryIncome
            totalExpense += e.expense

            val dayStr = formatter.format(Date(e.dateMillis))
            uniqueDaysSet.add(dayStr)
        }

        val totalDays = if (uniqueDaysSet.isEmpty()) 1 else uniqueDaysSet.size
        val netProfit = totalIncome - totalExpense

        object {
            val qty = totalQty
            val income = totalIncome
            val expense = totalExpense
            val net = netProfit
            val workdays = if (uniqueDaysSet.isEmpty()) 0 else uniqueDaysSet.size
            val avgQty = if (uniqueDaysSet.isEmpty()) 0.0 else (totalQty.toDouble() / totalDays)
            val avgExpense = if (uniqueDaysSet.isEmpty()) 0.0 else (totalExpense / totalDays)
            val avgNet = if (uniqueDaysSet.isEmpty()) 0.0 else (netProfit / totalDays)
        }
    }

    // Today's total expense for budget tracking
    val todayExpense = remember(entries) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val todayEnd = cal.timeInMillis

        entries.filter { it.dateMillis in todayStart until todayEnd }.sumOf { it.expense }
    }

    // 30-day block analysis (allUniqueDays is sorted ascending)
    val allUniqueDays = remember(entries, selectedFolder) {
        val filtered = entries.filter { selectedFolder == "সব ফোল্ডার" || it.folderName == selectedFolder }
        filtered.map { getDayStartMillis(it.dateMillis) }
            .distinct()
            .sorted()
    }
    val completedMonthCount = remember(allUniqueDays) {
        allUniqueDays.size / 30
    }
    val activePeriodStartDayIndex = remember(completedMonthCount) {
        completedMonthCount * 30
    }
    val activePeriodStartMillis = remember(allUniqueDays, activePeriodStartDayIndex) {
        if (allUniqueDays.isNotEmpty()) allUniqueDays[activePeriodStartDayIndex] else 0L
    }

    val activeMonthEntries = remember(entries, selectedFolder, activePeriodStartMillis) {
        val filtered = entries.filter { selectedFolder == "সব ফোল্ডার" || it.folderName == selectedFolder }
        if (allUniqueDays.isNotEmpty()) {
            filtered.filter { getDayStartMillis(it.dateMillis) >= activePeriodStartMillis }
        } else {
            emptyList()
        }
    }

    // Current month's total expense for budget tracking
    val currentMonthExpenses = remember(activeMonthEntries) {
        activeMonthEntries.filter { !it.isIncome }.sumOf { it.expense }
    }

    // Current month's total income for target tracking
    val currentMonthIncome = remember(activeMonthEntries, wageRate) {
        activeMonthEntries.sumOf { e ->
            val prodIncome = (e.quantity / 100.0) * wageRate
            if (e.isIncome) e.income else prodIncome
        }
    }

    // Current month's unique entry days
    val currentMonthDays = remember(activeMonthEntries) {
        val uniqueDays = activeMonthEntries.map { getDayStartMillis(it.dateMillis) }.distinct()
        if (uniqueDays.isEmpty()) 1 else uniqueDays.size
    }

    // Scaffold with status safe areas and beautiful edge-to-edge
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        floatingActionButton = {
            if (activeBottomTab == "Home" && activeSubScreen == "DAILY_ACCOUNTS") {
                if (currentKhataMode == "PREMIUM") {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("add_entry_fab_premium")
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            showPremiumAddDialog = true
                        }
                        .drawBehind {
                            // Soft transparent green outer halo glow
                            drawCircle(
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                radius = (size.minDimension / 2f) + 6f
                            )
                        }
                        .background(
                            color = Color(0xFF064E3B).copy(alpha = 0.65f), // Transparent dark green
                            shape = CircleShape
                        )
                        .border(
                            width = 1.2.dp,
                            color = Color(0xFF10B981).copy(alpha = 0.45f), // Matching clean green border
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "হিসাব যোগ",
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else if (currentKhataMode == "ALT") {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("add_entry_fab")
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            showAddDialog = true
                        }
                        .background(
                            color = Color(0xFF10B981).copy(alpha = 0.25f), // transparent emerald green background
                            shape = CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = Color(0xFF10B981).copy(alpha = 0.5f), // clean transparent green border
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "হিসাব যোগ",
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("add_entry_fab")
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            showAddDialog = true
                        }
                        .background(
                            color = (if (currentKhataMode == "ALT") Color(0xFFFBBF24) else Color(0xFF3B82F6)).copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                        .border(
                            width = 1.2.dp,
                            color = (if (currentKhataMode == "ALT") Color(0xFFFBBF24) else Color(0xFF3B82F6)).copy(alpha = 0.55f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "হিসাব যোগ",
                        tint = (if (currentKhataMode == "ALT") Color(0xFFFBBF24) else Color(0xFF3B82F6)).copy(alpha = 0.9f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        // Custom decorative backgrounds for Glassmorphism
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    if (currentKhataMode == "PREMIUM") {
                        // Drawing premium royal blue with neon green & purple orbs
                        drawRect(color = Color(0xFF03001C)) // Very dark midnight indigo/violet
                        // Glowing Neon Royal Blue orb in top left
                        drawCircle(
                            color = Color(0xFF301E67).copy(alpha = 0.5f),
                            radius = size.minDimension * 0.9f,
                            center = Offset(size.width * 0.1f, size.height * 0.15f)
                        )
                        // Glowing Purple/Indigo orb in bottom right
                        drawCircle(
                            color = Color(0xFF5B8FB9).copy(alpha = 0.2f),
                            radius = size.minDimension * 0.8f,
                            center = Offset(size.width * 0.9f, size.height * 0.85f)
                        )
                    } else if (currentKhataMode == "ALT") {
                        // Premium Dark Royal Blue solid background
                        drawRect(color = Color(0xFF060B1E))
                        // Neon royal blue glowing orb in the upper right
                        drawCircle(
                            color = Color(0xFF1E40AF).copy(alpha = 0.25f),
                            radius = size.minDimension * 0.6f,
                            center = Offset(size.width * 0.85f, size.height * 0.15f)
                        )
                        // Accent golden-yellow hint glow in the bottom left
                        drawCircle(
                            color = Color(0xFFD97706).copy(alpha = 0.08f),
                            radius = size.minDimension * 0.4f,
                            center = Offset(size.width * 0.15f, size.height * 0.85f)
                        )
                    } else if (currentTheme == "DEFAULT") {
                        // Plain Dark Royal Blue solid background
                        drawRect(color = Color(0xFF0A142F))
                    } else if (currentTheme == "GLASS") {
                        // Drawing high-fidelity gradient glows for glassmorphism
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF0A1931), Color(0xFF020617)),
                                center = Offset(size.width * 0.5f, size.height * 0.1f),
                                radius = size.minDimension * 1.5f
                            )
                        )
                        drawCircle(
                            color = Color(0xFF3B82F6).copy(alpha = 0.18f),
                            radius = size.minDimension * 0.45f,
                            center = Offset(size.width * 0.9f, size.height * 0.2f)
                        )
                        drawCircle(
                            color = Color(0xFF1E3A8A).copy(alpha = 0.22f),
                            radius = size.minDimension * 0.6f,
                            center = Offset(size.width * 0.1f, size.height * 0.8f)
                        )
                    } else if (currentTheme == "CARBON") {
                        drawRect(color = Color(0xFF0F1115))
                    } else {
                        drawRect(color = Color.Transparent)
                    }
                }
                .padding(innerPadding)
        ) {
            if (activeBottomTab == "Search") {
                SearchTabScreen(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    selectedCategoryFilter = selectedCategoryFilter,
                    onCategoryFilterChange = { selectedCategoryFilter = it },
                    categories = categories,
                    entries = entries,
                    wageRate = wageRate,
                    cardBgColor = cardBgColor,
                    isGlass = isGlass,
                    cardBorderColor = cardBorderColor,
                    onEdit = { item ->
                        entryToEdit = item
                        showAddDialog = true
                    },
                    onDelete = { item ->
                        viewModel.deleteEntry(item)
                        Toast.makeText(context, "হিসাব ডিলিট করা হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                )
            } else if (activeBottomTab == "Profile") {
                ProfileTabScreen(
                    entries = entries,
                    folders = folders,
                    currentKhataMode = currentKhataMode,
                    wageRate = wageRate,
                    viewModel = viewModel,
                    context = context,
                    showSettingsDialog = { showSettingsDialog = true },
                    onBack = { activeBottomTab = "Home" }
                )
            } else {
                if (activeSubScreen == "HOME") {
                    DashboardHomeScreen(
                        entries = entries,
                        wageRate = wageRate,
                        folders = folders,
                        currentKhataMode = currentKhataMode,
                        currentAvatarIdx = currentAvatarIdx,
                        profileCustomAvatarUri = profileCustomAvatarUri,
                        onNavigateToDailyAccounts = { activeSubScreen = "NOTEBOOK_SELECTOR" },
                        onNavigateToReportsGraphs = { activeSubScreen = "REPORTS_GRAPHS" },
                        onNavigateToBudget = { showBudgetDialog = true },
                        onNavigateToGoals = { showIncomeTargetDialog = true },
                        onNavigateToSettings = { showSettingsDialog = true },
                        onNavigateToSetBudget = { activeSubScreen = "SET_BUDGET" },
                        onProfileClick = { activeBottomTab = "Profile" }
                    )
                } else if (activeSubScreen == "NOTEBOOK_SELECTOR") {
                    NoteBookSelectorScreen(
                        currentKhataMode = currentKhataMode,
                        onChangeMode = { mode -> viewModel.changeKhataMode(mode) },
                        onSelectModeComplete = { activeSubScreen = "DAILY_ACCOUNTS" },
                        onBack = { activeSubScreen = "HOME" }
                    )
                } else if (activeSubScreen == "REPORTS_GRAPHS") {
                    ReportsGraphsSubScreen(
                        rawEntries = rawEntries,
                        wageRate = wageRate,
                        onBack = { activeSubScreen = "HOME" }
                    )
                } else if (activeSubScreen == "SET_BUDGET") {
                    SetBudgetScreen(
                        viewModel = viewModel,
                        onBack = { activeSubScreen = "HOME" }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        // 1. TOP HEADER BAR
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Left side: Back Button
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (activeSubScreen != "HOME") {
                                        IconButton(
                                            onClick = { activeSubScreen = "NOTEBOOK_SELECTOR" },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowBack,
                                                contentDescription = "ফিরে যান",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }

                                // Right side: Headline / Title
                                Column(
                                    horizontalAlignment = Alignment.End
                                ) {
                                    if (currentKhataMode == "PREMIUM") {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "আয় ব্যয় ট্রেকার",
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.White
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(Color(0xFF1E1B4B).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                                        .border(1.dp, Color(0xFF818CF8).copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.Stars,
                                                        contentDescription = null,
                                                        tint = Color(0xFFFBBF24), // Glowing gold star
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "ড্যাসবোর্ড ৩: আয়-ব্যয়",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF10B981), // Neon Green
                                                modifier = Modifier.padding(end = 2.dp)
                                            )
                                        }
                                    } else if (currentKhataMode == "ALT") {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "ডেইলি খরচ ট্র্যাকার",
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.White
                                                )
                                                // Custom glowing gold wallet logo
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(Color(0xFF2563EB).copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                                        .border(1.dp, Color(0xFF60A5FA).copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.AccountBalanceWallet,
                                                        contentDescription = null,
                                                        tint = Color(0xFFFBBF24), // Golden-yellow
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "এক্সপ্লোর মোড: শুধুমাত্র খরচ",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFBBF24), // Golden-yellow accent subheader
                                                modifier = Modifier.padding(end = 2.dp)
                                            )
                                        }
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "টাকা-হিসাব",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White
                                            )
                                            // "ডেইলি ট্র্যাকার" Pill Badge matching screenshot but dynamic for khata mode
                                            Surface(
                                                shape = RoundedCornerShape(20.dp),
                                                color = Color.Transparent,
                                                border = BorderStroke(1.dp, if (currentKhataMode == "MAIN") Color(0xFF3B82F6).copy(alpha = 0.4f) else Color(0xFF10B981).copy(alpha = 0.4f)),
                                                modifier = Modifier.padding(end = 4.dp)
                                            ) {
                                                Text(
                                                    text = if (currentKhataMode == "MAIN") "মালের হিসাব" else "দৈনিক খরছ",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (currentKhataMode == "MAIN") Color(0xFF60A5FA) else Color(0xFF34D399),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                            // Custom logo icon matching screenshot with 3D bevel and glow
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .drawBehind {
                                                        // Ambient glow
                                                        drawRoundRect(
                                                            color = Color(0xFF3B82F6).copy(alpha = 0.25f),
                                                            topLeft = Offset(-2f, -2f),
                                                            size = Size(size.width + 4f, size.height + 4f),
                                                            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                                                        )
                                                        // Depth shadow
                                                        drawRoundRect(
                                                            color = Color(0xFF020617).copy(alpha = 0.6f),
                                                            topLeft = Offset(0f, 3f),
                                                            size = size,
                                                            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                                                        )
                                                    }
                                                    .background(
                                                        brush = Brush.verticalGradient(
                                                            colors = listOf(
                                                                Color(0xFF1E293B).copy(alpha = 0.95f),
                                                                Color(0xFF0F172A).copy(alpha = 0.95f)
                                                            )
                                                        ),
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        brush = Brush.verticalGradient(
                                                            colors = listOf(
                                                                Color(0xFF60A5FA).copy(alpha = 0.8f),
                                                                Color(0xFF3B82F6).copy(alpha = 0.1f)
                                                            )
                                                        ),
                                                        shape = RoundedCornerShape(12.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.AccountBalanceWallet,
                                                    contentDescription = null,
                                                    tint = Color(0xFF3B82F6),
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .drawBehind {
                                                            drawCircle(
                                                                color = Color(0xFF3B82F6).copy(alpha = 0.2f),
                                                                radius = 18f
                                                            )
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                if (currentKhataMode == "PREMIUM") {
                    // --- PREMIUM MODE CONTENT ---

                    // 1. REAL-TIME SUMMARY CARD WITH 3D BORDER & NEON GLOWS
                    item {
                        val totalIncome = premiumDailyGrouped.sumOf { it.totalIncome }
                        val totalExpense = premiumDailyGrouped.sumOf { it.totalExpense }
                        val balance = totalIncome - totalExpense
                        val totalDays = if (premiumDailyGrouped.isEmpty()) 1 else premiumDailyGrouped.size
                        val avgDailyIncome = totalIncome / totalDays
                        val avgDailyExpense = totalExpense / totalDays

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .drawBehind {
                                    drawRoundRect(
                                        color = Color(0xFF030712).copy(alpha = 0.6f),
                                        topLeft = Offset(0f, 6f),
                                        size = size,
                                        cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())
                                    )
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.5.dp, Color(0xFF818CF8).copy(alpha = 0.6f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "রিয়েল-টাইম সারাংশ",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Total Income Box
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(Color(0xFF10B981).copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                            .padding(10.dp)
                                    ) {
                                        Text("মোট আয়", fontSize = 11.sp, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("৳ ${totalIncome.toBangla()}", fontSize = 15.sp, color = Color(0xFF10B981), fontWeight = FontWeight.ExtraBold)
                                    }

                                    // Total Expense Box
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(Color(0xFFEF4444).copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                            .padding(10.dp)
                                    ) {
                                        Text("মোট ব্যয়", fontSize = 11.sp, color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("৳ ${totalExpense.toBangla()}", fontSize = 15.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.ExtraBold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Remaining Balance Box (Full Width)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF06B6D4).copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                        .border(1.dp, Color(0xFF06B6D4).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("অবশিষ্ট ব্যালেন্স", fontSize = 12.sp, color = Color(0xFF22D3EE), fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("৳ ${balance.toBangla()}", fontSize = 18.sp, color = Color(0xFF06B6D4), fontWeight = FontWeight.ExtraBold)
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Divider(color = Color.White.copy(alpha = 0.08f))

                                Spacer(modifier = Modifier.height(10.dp))

                                // Averages Row
                                val blinkAvgIncome = avgDailyIncome < premiumDailyIncomeTarget
                                val blinkAvgExpense = avgDailyExpense > premiumDailyExpenseTarget

                                val premiumBlinkAlpha = if (blinkAvgIncome || blinkAvgExpense) {
                                    val transition = rememberInfiniteTransition(label = "premiumBlink")
                                    val alphaVal by transition.animateFloat(
                                        initialValue = 0.3f,
                                        targetValue = 1.0f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(durationMillis = 600, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "premiumBlinkAlpha"
                                    )
                                    alphaVal
                                } else {
                                    1.0f
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("গড় দৈনিক আয়", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                        Text(
                                            text = "৳ ${avgDailyIncome.toBangla()}",
                                            fontSize = 13.sp,
                                            color = Color(0xFF34D399),
                                            fontWeight = FontWeight.Bold,
                                            modifier = if (blinkAvgIncome) Modifier.alpha(premiumBlinkAlpha) else Modifier
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("গড় দৈনিক ব্যয়", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                        Text(
                                            text = "৳ ${avgDailyExpense.toBangla()}",
                                            fontSize = 13.sp,
                                            color = Color(0xFFFBBF24),
                                            fontWeight = FontWeight.Bold,
                                            modifier = if (blinkAvgExpense) Modifier.alpha(premiumBlinkAlpha) else Modifier
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. ADVANCED MONITORING
                    item {
                        AdvanceMonitoringCard(premiumDailyGrouped = premiumDailyGrouped)
                    }

                    // 4. DAILY INCOME & EXPENSE GRAPH WITH ADVANCED TIME-PERIOD FILTERS
                    item {
                        val graphDaysAndOffset = remember(premiumDailyGrouped, selectedGraphFilter, graphOffsetWeeks) {
                            val list = mutableListOf<Pair<String, Pair<Double, Double>>>()
                            val sdfDisplay = java.text.SimpleDateFormat("dd/MM", java.util.Locale("bn"))
                            
                            val cal = java.util.Calendar.getInstance()
                            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                            cal.set(java.util.Calendar.MINUTE, 0)
                            cal.set(java.util.Calendar.SECOND, 0)
                            cal.set(java.util.Calendar.MILLISECOND, 0)
                            val todayStart = cal.timeInMillis

                            val firstEntryMillis = premiumDailyGrouped.map { it.dateMillis }.minOrNull() ?: todayStart
                            val calFirst = java.util.Calendar.getInstance().apply {
                                timeInMillis = firstEntryMillis
                                set(java.util.Calendar.HOUR_OF_DAY, 0)
                                set(java.util.Calendar.MINUTE, 0)
                                set(java.util.Calendar.SECOND, 0)
                                set(java.util.Calendar.MILLISECOND, 0)
                            }
                            val firstDayStart = calFirst.timeInMillis

                            val maxEntryMillis = premiumDailyGrouped.map { it.dateMillis }.maxOrNull() ?: todayStart
                            val latestDayStart = maxOf(todayStart, maxEntryMillis)

                            val diffMillis = latestDayStart - firstDayStart
                            val totalDays = (diffMillis / (24L * 60 * 60 * 1000)).toInt() + 1

                            val maxPages = when (selectedGraphFilter) {
                                "TODAY" -> totalDays
                                "YESTERDAY" -> totalDays - 1
                                "LAST_7_DAYS" -> ((latestDayStart - firstDayStart) / (7L * 24 * 60 * 60 * 1000) + 1).toInt().coerceAtLeast(1)
                                "LAST_30_DAYS" -> ((latestDayStart - firstDayStart) / (30L * 24 * 60 * 60 * 1000) + 1).toInt().coerceAtLeast(1)
                                else -> 1
                            }

                            val displayOffset = graphOffsetWeeks.coerceIn(0, (maxPages - 1).coerceAtLeast(0))

                            when (selectedGraphFilter) {
                                "TODAY" -> {
                                    val targetDayMillis = latestDayStart - displayOffset * 24L * 60 * 60 * 1000
                                    val tempCal = java.util.Calendar.getInstance()
                                    tempCal.timeInMillis = targetDayMillis
                                    val dateDisplay = sdfDisplay.format(tempCal.time).toBanglaDigits()
                                    val dayOfWeek = tempCal.get(java.util.Calendar.DAY_OF_WEEK)
                                    val dayName = when (dayOfWeek) {
                                        java.util.Calendar.SUNDAY -> "রবি"
                                        java.util.Calendar.MONDAY -> "সোম"
                                        java.util.Calendar.TUESDAY -> "মঙ্গল"
                                        java.util.Calendar.WEDNESDAY -> "বুধ"
                                        java.util.Calendar.THURSDAY -> "বৃহঃ"
                                        java.util.Calendar.FRIDAY -> "শুক্র"
                                        java.util.Calendar.SATURDAY -> "শনি"
                                        else -> "আজ"
                                    }
                                    val label = "$dayName ($dateDisplay)"
                                    val entry = premiumDailyGrouped.find { it.dateMillis == targetDayMillis }
                                    val income = entry?.totalIncome ?: 0.0
                                    val expense = entry?.totalExpense ?: 0.0
                                    list.add(label to (income to expense))
                                }
                                "YESTERDAY" -> {
                                    val targetDayMillis = latestDayStart - (1 + displayOffset) * 24L * 60 * 60 * 1000
                                    val tempCal = java.util.Calendar.getInstance()
                                    tempCal.timeInMillis = targetDayMillis
                                    val dateDisplay = sdfDisplay.format(tempCal.time).toBanglaDigits()
                                    val dayOfWeek = tempCal.get(java.util.Calendar.DAY_OF_WEEK)
                                    val dayName = when (dayOfWeek) {
                                        java.util.Calendar.SUNDAY -> "রবি"
                                        java.util.Calendar.MONDAY -> "সোম"
                                        java.util.Calendar.TUESDAY -> "মঙ্গল"
                                        java.util.Calendar.WEDNESDAY -> "বুধ"
                                        java.util.Calendar.THURSDAY -> "বৃহঃ"
                                        java.util.Calendar.FRIDAY -> "শুক্র"
                                        java.util.Calendar.SATURDAY -> "শনি"
                                        else -> "গতকাল"
                                    }
                                    val label = "$dayName ($dateDisplay)"
                                    val entry = premiumDailyGrouped.find { it.dateMillis == targetDayMillis }
                                    val income = entry?.totalIncome ?: 0.0
                                    val expense = entry?.totalExpense ?: 0.0
                                    list.add(label to (income to expense))
                                }
                                "LAST_7_DAYS" -> {
                                    val windowEndMillis = latestDayStart - (displayOffset * 7 * 24L * 60 * 60 * 1000)
                                    val windowStartMillis = windowEndMillis - 6 * 24L * 60 * 60 * 1000
                                    val tempCal = java.util.Calendar.getInstance()
                                    tempCal.timeInMillis = windowStartMillis
                                    for (i in 0..6) {
                                        val targetStart = getDayStartMillis(tempCal.timeInMillis)
                                        val dateDisplay = sdfDisplay.format(tempCal.time).toBanglaDigits()
                                        val dayOfWeek = tempCal.get(java.util.Calendar.DAY_OF_WEEK)
                                        val dayName = when (dayOfWeek) {
                                            java.util.Calendar.SUNDAY -> "রবি"
                                            java.util.Calendar.MONDAY -> "সোম"
                                            java.util.Calendar.TUESDAY -> "মঙ্গল"
                                            java.util.Calendar.WEDNESDAY -> "বুধ"
                                            java.util.Calendar.THURSDAY -> "বৃহঃ"
                                            java.util.Calendar.FRIDAY -> "শুক্র"
                                            java.util.Calendar.SATURDAY -> "শনি"
                                            else -> ""
                                        }
                                        val label = "$dayName ($dateDisplay)"
                                        val entry = premiumDailyGrouped.find { it.dateMillis == targetStart }
                                        val income = entry?.totalIncome ?: 0.0
                                        val expense = entry?.totalExpense ?: 0.0
                                        list.add(label to (income to expense))
                                        tempCal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                                    }
                                }
                                "LAST_30_DAYS" -> {
                                    val windowEndMillis = latestDayStart - (displayOffset * 30 * 24L * 60 * 60 * 1000)
                                    val windowStartMillis = windowEndMillis - 29 * 24L * 60 * 60 * 1000
                                    val tempCal = java.util.Calendar.getInstance()
                                    tempCal.timeInMillis = windowStartMillis
                                    for (i in 0..29) {
                                        val targetStart = getDayStartMillis(tempCal.timeInMillis)
                                        val dateDisplay = sdfDisplay.format(tempCal.time).toBanglaDigits()
                                        val dayOfWeek = tempCal.get(java.util.Calendar.DAY_OF_WEEK)
                                        val dayName = when (dayOfWeek) {
                                            java.util.Calendar.SUNDAY -> "রবি"
                                            java.util.Calendar.MONDAY -> "সোম"
                                            java.util.Calendar.TUESDAY -> "মঙ্গল"
                                            java.util.Calendar.WEDNESDAY -> "বুধ"
                                            java.util.Calendar.THURSDAY -> "বৃহঃ"
                                            java.util.Calendar.FRIDAY -> "শুক্র"
                                            java.util.Calendar.SATURDAY -> "শনি"
                                            else -> ""
                                        }
                                        val label = "$dayName ($dateDisplay)"
                                        val entry = premiumDailyGrouped.find { it.dateMillis == targetStart }
                                        val income = entry?.totalIncome ?: 0.0
                                        val expense = entry?.totalExpense ?: 0.0
                                        list.add(label to (income to expense))
                                        tempCal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                                    }
                                }
                            }
                            Triple(list, displayOffset, maxPages)
                        }
                        val graphDays = graphDaysAndOffset.first
                        val displayOffset = graphDaysAndOffset.second
                        val maxPages = graphDaysAndOffset.third

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF030D26)),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, Color(0xFF1D4ED8).copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Graph header with click-to-collapse toggle
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isGraphMinimized = !isGraphMinimized }
                                        .padding(bottom = if (isGraphMinimized) 0.dp else 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BarChart,
                                            contentDescription = null,
                                            tint = Color(0xFF818CF8),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "দৈনিক আয় ও ব্যয় গ্রাফ",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Text(
                                        text = if (isGraphMinimized) "∨" else "^",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF818CF8)
                                    )
                                }

                                if (!isGraphMinimized) {
                                    // 1. FILTER CHIP TAB ROW
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val filters = listOf(
                                            "TODAY" to "আজ",
                                            "YESTERDAY" to "গতকাল",
                                            "LAST_7_DAYS" to "গত ৭ দিন",
                                            "LAST_30_DAYS" to "গত ৩০ দিন"
                                        )
                                        
                                        filters.forEach { (key, label) ->
                                            val isSelected = selectedGraphFilter == key
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (isSelected) Color(0xFF818CF8).copy(alpha = 0.15f)
                                                        else Color.White.copy(alpha = 0.02f)
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isSelected) Color(0xFF818CF8) else Color.White.copy(alpha = 0.05f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable {
                                                        selectedGraphFilter = key
                                                        graphOffsetWeeks = 0 // Reset navigation offset on filter change
                                                    }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) Color(0xFF818CF8) else Color(0xFF94A3B8)
                                                )
                                            }
                                        }
                                    }

                                    // 2. TIMELINE NAVIGATION CONTROLS
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { graphOffsetWeeks++ },
                                            enabled = displayOffset < maxPages - 1,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(
                                                    if (displayOffset < maxPages - 1) Color.White.copy(alpha = 0.05f)
                                                    else Color.White.copy(alpha = 0.02f),
                                                    CircleShape
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowBack,
                                                contentDescription = "পূর্ববর্তী",
                                                tint = if (displayOffset < maxPages - 1) Color.White else Color.White.copy(alpha = 0.3f)
                                            )
                                        }

                                        val navigationLabel = when (selectedGraphFilter) {
                                            "TODAY" -> {
                                                when (displayOffset) {
                                                    0 -> "আজ"
                                                    1 -> "গতকাল"
                                                    else -> "${displayOffset.toBangla()} দিন পূর্বে"
                                                }
                                            }
                                            "YESTERDAY" -> {
                                                when (displayOffset) {
                                                    0 -> "গতকাল"
                                                    1 -> "২ দিন পূর্বে"
                                                    else -> "${(displayOffset + 1).toBangla()} দিন পূর্বে"
                                                }
                                            }
                                            "LAST_7_DAYS" -> {
                                                when (displayOffset) {
                                                    0 -> "গত ৭ দিন"
                                                    1 -> "১ সপ্তাহ পূর্বে"
                                                    else -> "${displayOffset.toBangla()} সপ্তাহ পূর্বে"
                                                }
                                            }
                                            "LAST_30_DAYS" -> {
                                                when (displayOffset) {
                                                    0 -> "গত ৩০ দিন"
                                                    1 -> "১ মাস পূর্বে"
                                                    else -> "${displayOffset.toBangla()} মাস পূর্বে"
                                                }
                                            }
                                            else -> ""
                                        }

                                        Text(
                                            text = navigationLabel,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )

                                        IconButton(
                                            onClick = { if (graphOffsetWeeks > 0) graphOffsetWeeks-- },
                                            enabled = displayOffset > 0,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(
                                                    if (displayOffset > 0) Color.White.copy(alpha = 0.05f)
                                                    else Color.White.copy(alpha = 0.02f),
                                                    CircleShape
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowForward,
                                                contentDescription = "পরবর্তী",
                                                tint = if (displayOffset > 0) Color.White else Color.White.copy(alpha = 0.3f)
                                            )
                                        }
                                    }

                                    // 3. PROMINENT LARGE DISPLAY FOR SINGLE DAY VIEWS (TODAY/YESTERDAY)
                                    if (selectedGraphFilter == "TODAY" || selectedGraphFilter == "YESTERDAY") {
                                        val dayData = graphDays.firstOrNull()?.second ?: (0.0 to 0.0)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(horizontal = 4.dp)
                                                    .background(Color(0xFF10B981).copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                                    .border(BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.2f)), RoundedCornerShape(12.dp))
                                                    .padding(vertical = 12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.TrendingUp,
                                                    contentDescription = null,
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("মোট আয়", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("৳ ${dayData.first.toInt().toString().toBanglaDigits()}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF10B981))
                                            }
                                            
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(horizontal = 4.dp)
                                                    .background(Color(0xFFEF4444).copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                                    .border(BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.2f)), RoundedCornerShape(12.dp))
                                                    .padding(vertical = 12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.TrendingDown,
                                                    contentDescription = null,
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("মোট ব্যয়", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("৳ ${dayData.second.toInt().toString().toBanglaDigits()}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFFEF4444))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    // Canvas-drawn bezier line chart exactly matching the reference image
                                    val maxVal = remember(graphDays) {
                                        val maxExpense = graphDays.maxOfOrNull { it.second.second } ?: 0.0
                                        val maxIncome = graphDays.maxOfOrNull { it.second.first } ?: 0.0
                                        val max = maxOf(maxIncome, maxExpense)
                                        if (max == 0.0) {
                                            500.0
                                        } else {
                                            val rawMax = max * 1.15
                                            if (rawMax <= 50.0) {
                                                50.0
                                             } else if (rawMax <= 100.0) {
                                                 100.0
                                             } else if (rawMax <= 250.0) {
                                                 250.0
                                             } else if (rawMax <= 500.0) {
                                                 500.0
                                             } else {
                                                 val step = if (rawMax <= 1000) 200.0 
                                                            else if (rawMax <= 5000) 1000.0 
                                                            else if (rawMax <= 10000) 2000.0
                                                            else 5000.0
                                                 (Math.ceil(rawMax / step) * step).coerceAtLeast(500.0)
                                             }
                                        }
                                    }

                                    // Centered Legend matching the reference image exactly
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp, bottom = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(end = 24.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 24.dp, height = 6.dp)
                                                    .background(Color(0xFF10B981), RoundedCornerShape(3.dp))
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "আয়",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 24.dp, height = 6.dp)
                                                    .background(Color(0xFFEF4444), RoundedCornerShape(3.dp))
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "ব্যয়",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    // Graph layout inside BoxWithConstraints for perfect floating overlay placement
                                    BoxWithConstraints(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        val containerWidth = maxWidth
                                        val containerHeight = 180.dp
                                        
                                        var activeDashboardPopupText by remember { mutableStateOf<String?>(null) }
                                        LaunchedEffect(activeDashboardPopupText) {
                                            if (activeDashboardPopupText != null) {
                                                kotlinx.coroutines.delay(3000)
                                                activeDashboardPopupText = null
                                            }
                                        }
                                        val infiniteTransition = rememberInfiniteTransition(label = "badge_blink")
                                        val badgeAlpha by infiniteTransition.animateFloat(
                                            initialValue = 1f,
                                            targetValue = 0.3f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                                                repeatMode = RepeatMode.Reverse
                                            ),
                                            label = "badge_alpha"
                                        )
                                        
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(containerHeight)
                                        ) {
                                            // Y-Axis Labels Column
                                            Column(
                                                modifier = Modifier
                                                    .width(50.dp)
                                                    .fillMaxHeight(),
                                                verticalArrangement = Arrangement.SpaceBetween,
                                                horizontalAlignment = Alignment.End
                                             ) {
                                                 val steps = 5
                                                 for (i in 0..steps) {
                                                     val labelValue = maxVal * (1.0 - i.toDouble() / steps)
                                                     val formattedLabel = if (labelValue == 0.0) {
                                                         "৳০"
                                                     } else {
                                                         "৳" + String.format(Locale.US, "%,d", labelValue.toInt()).toBanglaDigits()
                                                     }
                                                     Text(
                                                         text = formattedLabel,
                                                         fontSize = 10.sp,
                                                         color = Color(0xFF94A3B8).copy(alpha = 0.8f),
                                                         fontWeight = FontWeight.Bold
                                                     )
                                                 }
                                             }

                                             Spacer(modifier = Modifier.width(10.dp))

                                             // Canvas drawing the beautiful curves, stars, glows, fills and axes
                                             Canvas(
                                                 modifier = Modifier
                                                     .weight(1f)
                                                     .fillMaxHeight()
                                             ) {
                                                 val width = size.width
                                                 val height = size.height

                                                val stepX = if (graphDays.size > 1) width / (graphDays.size - 1) else width / 2f

                                                val incomePoints = graphDays.mapIndexed { index, (_, data) ->
                                                    val x = if (graphDays.size > 1) index * stepX else width / 2f
                                                    val y = height - ((data.first / maxVal) * height).toFloat()
                                                    Offset(x, y)
                                                }

                                                val expensePoints = graphDays.mapIndexed { index, (_, data) ->
                                                    val x = if (graphDays.size > 1) index * stepX else width / 2f
                                                    val y = height - ((data.second / maxVal) * height).toFloat()
                                                    Offset(x, y)
                                                }

                                                // Background Stars/Sparkles (Cosmic Slate reference image style)
                                                val stars = listOf(
                                                    Offset(width * 0.15f, height * 0.25f),
                                                    Offset(width * 0.35f, height * 0.45f),
                                                    Offset(width * 0.45f, height * 0.15f),
                                                    Offset(width * 0.65f, height * 0.35f),
                                                    Offset(width * 0.85f, height * 0.22f)
                                                )
                                                stars.forEach { star ->
                                                    drawCircle(Color(0xFF3B82F6).copy(alpha = 0.25f), radius = 5.dp.toPx(), center = star)
                                                    drawCircle(Color.White, radius = 1.dp.toPx(), center = star)
                                                }

                                                // Draw beautiful axes: left vertical axis & bottom horizontal axis
                                                val axisColor = Color(0xFF1D4ED8).copy(alpha = 0.6f)
                                                val axisStrokeWidth = 1.5.dp.toPx()
                                                drawLine(
                                                    color = axisColor,
                                                    start = Offset(0f, 0f),
                                                    end = Offset(0f, height),
                                                    strokeWidth = axisStrokeWidth
                                                )
                                                drawLine(
                                                    color = axisColor,
                                                    start = Offset(0f, height),
                                                    end = Offset(width, height),
                                                    strokeWidth = axisStrokeWidth
                                                )

                                                // Draw vertical day separator grids as subtle technical mesh
                                                for (index in graphDays.indices) {
                                                    val x = if (graphDays.size > 1) index * stepX else width / 2f
                                                    drawLine(
                                                        color = Color(0xFF334155).copy(alpha = 0.12f),
                                                        start = Offset(x, 0f),
                                                        end = Offset(x, height),
                                                        strokeWidth = 1.dp.toPx()
                                                    )
                                                }

                                                // Draw horizontal grid lines
                                                val gridLines = 5
                                                for (i in 0..gridLines) {
                                                    val y = (height / gridLines) * i
                                                    drawLine(
                                                        color = Color(0xFF334155).copy(alpha = 0.08f),
                                                        start = Offset(0f, y),
                                                        end = Offset(width, y),
                                                        strokeWidth = 1.dp.toPx()
                                                    )
                                                }


                                                val strokeWidthPx = if (graphDays.size == 30) 1.2.dp.toPx() else 1.8.dp.toPx()

                                                // Draw Income Area Gradient Shadow (Green) with smooth bezier interpolation
                                                val incomeFillPath = Path().apply {
                                                    if (incomePoints.isNotEmpty()) {
                                                        moveTo(incomePoints.first().x, height)
                                                        lineTo(incomePoints.first().x, incomePoints.first().y)
                                                        if (incomePoints.size > 1) {
                                                            for (i in 0 until incomePoints.size - 1) {
                                                                val p1 = incomePoints[i]
                                                                val p2 = incomePoints[i + 1]
                                                                val cp1 = Offset(p1.x + (p2.x - p1.x) / 2f, p1.y)
                                                                val cp2 = Offset(p1.x + (p2.x - p1.x) / 2f, p2.y)
                                                                cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
                                                            }
                                                        }
                                                        lineTo(incomePoints.last().x, height)
                                                        close()
                                                    }
                                                }
                                                drawPath(
                                                    path = incomeFillPath,
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(Color(0xFF10B981).copy(alpha = 0.22f), Color(0xFF10B981).copy(alpha = 0.04f), Color.Transparent),
                                                        startY = 0f,
                                                        endY = height
                                                    )
                                                )

                                                // Draw Expense Area Gradient Shadow (Red) with smooth bezier interpolation
                                                val expenseFillPath = Path().apply {
                                                    if (expensePoints.isNotEmpty()) {
                                                        moveTo(expensePoints.first().x, height)
                                                        lineTo(expensePoints.first().x, expensePoints.first().y)
                                                        if (expensePoints.size > 1) {
                                                            for (i in 0 until expensePoints.size - 1) {
                                                                val p1 = expensePoints[i]
                                                                val p2 = expensePoints[i + 1]
                                                                val cp1 = Offset(p1.x + (p2.x - p1.x) / 2f, p1.y)
                                                                val cp2 = Offset(p1.x + (p2.x - p1.x) / 2f, p2.y)
                                                                cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
                                                            }
                                                        }
                                                        lineTo(expensePoints.last().x, height)
                                                        close()
                                                    }
                                                }
                                                drawPath(
                                                    path = expenseFillPath,
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(Color(0xFFEF4444).copy(alpha = 0.18f), Color(0xFFEF4444).copy(alpha = 0.03f), Color.Transparent),
                                                        startY = 0f,
                                                        endY = height
                                                    )
                                                )

                                                // Draw Income Smooth Curve (Green) with glows
                                                val incomePath = Path().apply {
                                                    if (incomePoints.isNotEmpty()) {
                                                        moveTo(incomePoints.first().x, incomePoints.first().y)
                                                        if (incomePoints.size > 1) {
                                                            for (i in 0 until incomePoints.size - 1) {
                                                                val p1 = incomePoints[i]
                                                                val p2 = incomePoints[i + 1]
                                                                val cp1 = Offset(p1.x + (p2.x - p1.x) / 2f, p1.y)
                                                                val cp2 = Offset(p1.x + (p2.x - p1.x) / 2f, p2.y)
                                                                cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
                                                            }
                                                        }
                                                    }
                                                }
                                                // Glow layer 1
                                                drawPath(
                                                    path = incomePath,
                                                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                                                    style = Stroke(width = strokeWidthPx * 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                                )
                                                // Glow layer 2
                                                drawPath(
                                                    path = incomePath,
                                                    color = Color(0xFF10B981).copy(alpha = 0.24f),
                                                    style = Stroke(width = strokeWidthPx * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                                )
                                                // Sharp line
                                                drawPath(
                                                    path = incomePath,
                                                    color = Color(0xFF10B981),
                                                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                                )

                                                // Draw Expense Smooth Curve (Red) with glows
                                                val expensePath = Path().apply {
                                                    if (expensePoints.isNotEmpty()) {
                                                        moveTo(expensePoints.first().x, expensePoints.first().y)
                                                        if (expensePoints.size > 1) {
                                                            for (i in 0 until expensePoints.size - 1) {
                                                                val p1 = expensePoints[i]
                                                                val p2 = expensePoints[i + 1]
                                                                val cp1 = Offset(p1.x + (p2.x - p1.x) / 2f, p1.y)
                                                                val cp2 = Offset(p1.x + (p2.x - p1.x) / 2f, p2.y)
                                                                cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
                                                            }
                                                        }
                                                    }
                                                }
                                                // Glow layer 1
                                                drawPath(
                                                    path = expensePath,
                                                    color = Color(0xFFEF4444).copy(alpha = 0.12f),
                                                    style = Stroke(width = strokeWidthPx * 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                                )
                                                // Glow layer 2
                                                drawPath(
                                                    path = expensePath,
                                                    color = Color(0xFFEF4444).copy(alpha = 0.24f),
                                                    style = Stroke(width = strokeWidthPx * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                                )
                                                // Sharp line
                                                drawPath(
                                                    path = expensePath,
                                                    color = Color(0xFFEF4444),
                                                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                                )

                                                // Draw data point dots/circles
                                                incomePoints.forEachIndexed { idx, p ->
                                                    val isToday = idx == incomePoints.size - 1 && displayOffset == 0
                                                    val dotRadius = if (graphDays.size == 30) 2.dp else if (isToday) 6.dp else 4.dp
                                                    val innerRadius = if (graphDays.size == 30) 1.dp else if (isToday) 3.5.dp else 2.5.dp
                                                    drawCircle(
                                                        color = Color(0xFF10B981).copy(alpha = 0.22f),
                                                        radius = dotRadius.toPx(),
                                                        center = p
                                                    )
                                                    drawCircle(
                                                        color = Color(0xFF10B981),
                                                        radius = innerRadius.toPx(),
                                                        center = p
                                                    )
                                                }

                                                expensePoints.forEachIndexed { idx, p ->
                                                    val isToday = idx == expensePoints.size - 1 && displayOffset == 0
                                                    val dotRadius = if (graphDays.size == 30) 2.dp else if (isToday) 6.dp else 4.dp
                                                    val innerRadius = if (graphDays.size == 30) 1.dp else if (isToday) 3.5.dp else 2.5.dp
                                                    drawCircle(
                                                        color = Color(0xFFEF4444).copy(alpha = 0.22f),
                                                        radius = dotRadius.toPx(),
                                                        center = p
                                                    )
                                                    drawCircle(
                                                        color = Color(0xFFEF4444),
                                                        radius = innerRadius.toPx(),
                                                        center = p
                                                    )
                                                }

                                        }
                                    }

                                    // Overlay Floating Glowing Badges for Peaks exactly like reference image
                                    val canvasWidth = containerWidth - 60.dp
                                    if (graphDays.isNotEmpty() && canvasWidth > 0.dp) {
                                        val stepXDp = if (graphDays.size > 1) canvasWidth / (graphDays.size - 1) else canvasWidth / 2f

                                        val maxIncomeVal = graphDays.maxOfOrNull { it.second.first } ?: 0.0
                                        val maxIncomeIndex = graphDays.indexOfFirst { it.second.first == maxIncomeVal }

                                        val maxExpenseVal = graphDays.maxOfOrNull { it.second.second } ?: 0.0
                                        val maxExpenseIndex = graphDays.indexOfFirst { it.second.second == maxExpenseVal }

                                        // Green money bag above the Income peak
                                        if (maxIncomeVal > 0.0 && maxIncomeIndex >= 0) {
                                            val xDp = 60.dp + (stepXDp * maxIncomeIndex)
                                            val yPercent = (maxIncomeVal / maxVal).toFloat().coerceIn(0f, 1f)
                                            val yDp = 180.dp - (180 * yPercent).dp

                                            Box(
                                                modifier = Modifier
                                                    .offset(x = xDp - 12.dp, y = yDp - 26.dp)
                                                    .size(24.dp)
                                                    .alpha(badgeAlpha)
                                                    .background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape)
                                                    .border(BorderStroke(1.dp, Color(0xFF10B981)), CircleShape)
                                                    .clickable {
                                                        val dateStr = graphDays[maxIncomeIndex].first.substringAfter("(").substringBefore(")")
                                                        activeDashboardPopupText = "$dateStr এর মোট আয়: ৳ ${maxIncomeVal.toInt().toString().toBanglaDigits()}"
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Paid,
                                                    contentDescription = null,
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        // Green money bag above another non-zero income point
                                        val otherIncomeIndex = graphDays.indexOfFirst { it.second.first > 0.0 && it.second.first != maxIncomeVal }
                                        if (otherIncomeIndex >= 0) {
                                            val otherVal = graphDays[otherIncomeIndex].second.first
                                            val xDp = 60.dp + (stepXDp * otherIncomeIndex)
                                            val yPercent = (otherVal / maxVal).toFloat().coerceIn(0f, 1f)
                                            val yDp = 180.dp - (180 * yPercent).dp

                                            Box(
                                                modifier = Modifier
                                                    .offset(x = xDp - 12.dp, y = yDp - 26.dp)
                                                    .size(24.dp)
                                                    .alpha(badgeAlpha)
                                                    .background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape)
                                                    .border(BorderStroke(1.dp, Color(0xFF10B981)), CircleShape)
                                                    .clickable {
                                                        val dateStr = graphDays[otherIncomeIndex].first.substringAfter("(").substringBefore(")")
                                                        activeDashboardPopupText = "$dateStr এর মোট আয়: ৳ ${otherVal.toInt().toString().toBanglaDigits()}"
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Paid,
                                                    contentDescription = null,
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        // Red coin stack above the Expense peak
                                        if (maxExpenseVal > 0.0 && maxExpenseIndex >= 0) {
                                            val xDp = 60.dp + (stepXDp * maxExpenseIndex)
                                            val yPercent = (maxExpenseVal / maxVal).toFloat().coerceIn(0f, 1f)
                                            val yDp = 180.dp - (180 * yPercent).dp

                                            Box(
                                                modifier = Modifier
                                                    .offset(x = xDp - 12.dp, y = yDp - 26.dp)
                                                    .size(24.dp)
                                                    .alpha(badgeAlpha)
                                                    .background(Color(0xFFEF4444).copy(alpha = 0.2f), CircleShape)
                                                    .border(BorderStroke(1.dp, Color(0xFFEF4444)), CircleShape)
                                                    .clickable {
                                                        val dateStr = graphDays[maxExpenseIndex].first.substringAfter("(").substringBefore(")")
                                                        activeDashboardPopupText = "$dateStr এর মোট ব্যয়: ৳ ${maxExpenseVal.toInt().toString().toBanglaDigits()}"
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Paid,
                                                    contentDescription = null,
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }

                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = activeDashboardPopupText != null,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically(),
                                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                                    ) {
                                        activeDashboardPopupText?.let { text ->
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.95f)),
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f)),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Info,
                                                        contentDescription = null,
                                                        tint = Color(0xFF60A5FA),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                     Text(
                                                         text = text,
                                                         fontSize = 12.sp,
                                                         fontWeight = FontWeight.Bold,
                                                         color = Color.White
                                                     )
                                                 }
                                             }
                                         }
                                     }
                                 }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Bottom Day Labels (Horizontal timeline) perfectly aligned with the dots!
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Spacer(modifier = Modifier.width(60.dp))

                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        graphDays.forEachIndexed { index, (dayLabel, _) ->
                                            val showLabel = when (graphDays.size) {
                                                30 -> index % 5 == 0 || index == 29
                                                else -> true
                                            }
                                            if (showLabel) {
                                                val namePart = dayLabel.substringBefore(" ")
                                                val datePart = dayLabel.substringAfter("(").substringBefore(")")

                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        text = namePart,
                                                        fontSize = if (graphDays.size == 30) 8.sp else 10.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = Color(0xFF94A3B8).copy(alpha = 0.8f)
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = datePart,
                                                        fontSize = if (graphDays.size == 30) 9.sp else 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            } else {
                                                Box(modifier = Modifier.width(1.dp))
                                            }
                                        }
                                    }
                                }

                            }
                        }
                    }
                }

                // PREMIUM MODE FOLDERS SECTION
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showFoldersSection = !showFoldersSection },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (!showFoldersSection) "হিসাব খাতা: $selectedFolder" else "হিসাব খাতা / ফোল্ডারসমূহ",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = if (showFoldersSection) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "ফোল্ডারসমূহ দেখান বা লুকান",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // + নতুন ফোল্ডার Button
                            IconButton(
                                onClick = { showAddFolderDialog = true },
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(0xFF312E81).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "নতুন ফোল্ডার",
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Horizontal scroll list of folder pills
                        if (showFoldersSection) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item {
                                    CustomFolderPill(
                                        label = "সব হিসাব",
                                        isSelected = selectedFolder == "সব ফোল্ডার",
                                        leadingIcon = Icons.Default.List,
                                        onClick = { viewModel.selectFolder("সব ফোল্ডার") }
                                    )
                                }

                                items(folders) { folder ->
                                    CustomFolderPill(
                                        label = folder,
                                        isSelected = selectedFolder == folder,
                                        leadingIcon = Icons.Default.Folder,
                                        showDelete = (folder != "আয় খাতা" && folder != "ব্যয় খাতা" && folders.size > 1),
                                        onClick = { viewModel.selectFolder(folder) },
                                        onDeleteClick = {
                                            viewModel.deleteFolder(folder)
                                            Toast.makeText(context, "ফোল্ডার এবং হিসাব মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                    // 5. HISTORY LOG HEADER
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(18.dp))
                                Text("তারিখ অনুযায়ী ইতিহাস", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("(${premiumDailyGrouped.size.toBangla()} দিন)", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                            }
                            
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF818CF8).copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.3f)),
                                modifier = Modifier.clickable { sortNewestFirstPremium = !sortNewestFirstPremium }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (sortNewestFirstPremium) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                        contentDescription = "সাজানো",
                                        tint = Color(0xFF818CF8),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = if (sortNewestFirstPremium) "নতুন উপরে" else "পুরাতন উপরে",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF818CF8)
                                    )
                                }
                            }
                        }
                    }

                    // 6. HISTORY LIST ITEMS WITH EDIT ICON (SLIMMER PRETTIER DESIGN)
                    if (premiumDailyGrouped.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("কোনো হিসাবের তথ্য পাওয়া যায়নি!", color = Color(0xFF94A3B8).copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        items(premiumDailyGrouped) { day ->
                            val formattedDate = remember {
                                val sdf = java.text.SimpleDateFormat("dd MMMM, yyyy", java.util.Locale("bn"))
                                sdf.format(java.util.Date(day.dateMillis)).toBanglaDigits()
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(formattedDate, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        
                                        // Income and Expense indicators
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text("আয়: ৳ ${day.totalIncome.toBangla()}", fontSize = 10.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                            Text("ব্যয়: ৳ ${day.totalExpense.toBangla()}", fontSize = 10.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        // Detailed category breakdown tags
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            val list = mutableListOf<String>()
                                            if (day.nasta > 0.0) list.add("নাস্তা: ৳${day.nasta.toInt()}")
                                            if (day.bhat > 0.0) list.add("ভাত: ৳${day.bhat.toInt()}")
                                            if (day.gariBhara > 0.0) list.add("ভাড়া: ৳${day.gariBhara.toInt()}")
                                            if (day.onnano > 0.0) list.add("অন্যান্য: ৳${day.onnano.toInt()}")

                                            val breakdownText = if (list.isEmpty()) "কোনো খরচ নেই" else list.joinToString(" | ")
                                            Text(breakdownText.toBanglaDigits(), fontSize = 9.sp, color = Color(0xFF94A3B8))
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                premiumEditDayDialogData = PremiumDayEditData(
                                                    dateMillis = day.dateMillis,
                                                    income = day.totalIncome,
                                                    nasta = day.nasta,
                                                    bhat = day.bhat,
                                                    gariBhara = day.gariBhara,
                                                    onnano = day.onnano
                                                )
                                            },
                                            modifier = Modifier
                                                .background(Color(0xFF818CF8).copy(alpha = 0.1f), CircleShape)
                                                .size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "সম্পাদনা", tint = Color(0xFF818CF8), modifier = Modifier.size(12.dp))
                                        }

                                        IconButton(
                                            onClick = {
                                                premiumDayToDeleteMillis = day.dateMillis
                                            },
                                            modifier = Modifier
                                                .background(Color(0xFFEF4444).copy(alpha = 0.1f), CircleShape)
                                                .size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "মুছে ফেলুন", tint = Color(0xFFEF4444), modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (currentKhataMode != "PREMIUM") {
                    // 2. ANALYTICS & AVERAGES COMBINED MAIN CARD
                    item {
                        if (currentKhataMode == "ALT") {
                        // Compute today's total expense for ALT mode
                        val todayMillis = remember {
                            val cal = Calendar.getInstance()
                            cal.set(Calendar.HOUR_OF_DAY, 0)
                            cal.set(Calendar.MINUTE, 0)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            cal.timeInMillis
                        }
                        val todayExpense = remember(filteredEntries, todayMillis) {
                            filteredEntries.filter { e ->
                                e.dateMillis >= todayMillis && e.dateMillis < todayMillis + 24 * 60 * 60 * 1000L
                            }.sumOf { it.expense }
                        }

                        val expensePercentage = if (monthlyBudget > 0) ((currentMonthExpenses / monthlyBudget) * 100).toInt() else 0
                        val remainingBudget = if (monthlyBudget > currentMonthExpenses) (monthlyBudget - currentMonthExpenses) else 0.0

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1329)),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.5.dp, Color(0xFF2563EB).copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Dashboard Title Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(Color(0xFFFBBF24).copy(alpha = 0.15f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Dashboard,
                                                contentDescription = null,
                                                tint = Color(0xFFFBBF24),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = "ড্যাশবোর্ড",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Split into Left and Right Columns
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left Column: Key Expense Stats
                                    Column(
                                        modifier = Modifier.weight(1.1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // 1. মোট খরচ
                                        AltStatItem(
                                            title = "মোট খরচ",
                                            value = "৳ ${analytics.expense.toBangla()}",
                                            iconColor = Color(0xFFF87171),
                                            bgColor = Color(0xFFEF4444).copy(alpha = 0.1f)
                                        )
                                        // 2. দৈনিক খরচ
                                        val isDailyExceeded = dailyBudget > 0 && todayExpense > dailyBudget
                                        AltStatItem(
                                            title = if (isDailyExceeded) "দৈনিক খরচ (লিমিট পার)" else "দৈনিক খরচ (লিমিট: ৳ ${dailyBudget.toInt().toBangla()})",
                                            value = "৳ ${todayExpense.toBangla()}",
                                            iconColor = if (isDailyExceeded) Color(0xFFEF4444) else Color(0xFF3B82F6),
                                            bgColor = (if (isDailyExceeded) Color(0xFFEF4444) else Color(0xFF3B82F6)).copy(alpha = 0.1f),
                                            showWarning = isDailyExceeded
                                        )
                                        // 3. গড় দৈনিক খরচ
                                        AltStatItem(
                                            title = "গড় দৈনিক খরচ",
                                            value = "৳ ${analytics.avgExpense.toBangla()}",
                                            iconColor = Color(0xFF10B981),
                                            bgColor = Color(0xFF10B981).copy(alpha = 0.1f),
                                            blinkIcon = isDailyExceeded
                                        )
                                        // 4. লিমিটের বাইরে খরচ (Shown if exceeded)
                                        val limitExceededAmount = currentMonthExpenses - monthlyBudget
                                        val isExceeded = monthlyBudget > 0 && limitExceededAmount > 0
                                        if (isExceeded) {
                                            AltStatItem(
                                                title = "লিমিটের বাইরে খরচ",
                                                value = "৳ ${limitExceededAmount.toBangla()}",
                                                iconColor = Color(0xFFEF4444),
                                                bgColor = Color(0xFFEF4444).copy(alpha = 0.2f),
                                                showWarning = true
                                            )
                                        }
                                    }

                                    // Right Column: Circular Budget Goal Graph
                                    Column(
                                        modifier = Modifier.weight(0.9f),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        ) {
                                            Text(
                                                text = "মাসিক খরচের লিমিট (বাকি)",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF94A3B8),
                                                textAlign = TextAlign.Center
                                            )
                                        }

                                        val circleBlinkAlpha = if (expensePercentage >= 60) {
                                            val transition = rememberInfiniteTransition(label = "circleBlink")
                                            val alphaVal by transition.animateFloat(
                                                initialValue = 0.3f,
                                                targetValue = 1.0f,
                                                animationSpec = infiniteRepeatable(
                                                    animation = tween(durationMillis = 600, easing = LinearEasing),
                                                    repeatMode = RepeatMode.Reverse
                                                ),
                                                label = "circleBlinkAlpha"
                                            )
                                            alphaVal
                                        } else {
                                            1.0f
                                        }

                                        // Goal Graph using Canvas
                                        Box(
                                            modifier = Modifier.size(110.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                val strokeWidth = 10.dp.toPx()
                                                val sizeOffset = strokeWidth
                                                val arcSize = size.minDimension - sizeOffset

                                                // Back Track
                                                drawArc(
                                                    color = Color.White.copy(alpha = 0.05f),
                                                    startAngle = -225f,
                                                    sweepAngle = 270f,
                                                    useCenter = false,
                                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                                    topLeft = Offset(sizeOffset / 2, sizeOffset / 2),
                                                    size = Size(arcSize, arcSize)
                                                )

                                                // Active Progress
                                                val sweepAngle = ((expensePercentage.coerceIn(0, 100) / 100f) * 270f)
                                                drawArc(
                                                    brush = Brush.sweepGradient(
                                                        colors = listOf(
                                                            Color(0xFF3B82F6), // Sky Blue
                                                            Color(0xFFF59E0B), // Amber Yellow
                                                            Color(0xFFEF4444)  // Warning Red
                                                        )
                                                    ),
                                                    startAngle = -225f,
                                                    sweepAngle = sweepAngle,
                                                    useCenter = false,
                                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                                    topLeft = Offset(sizeOffset / 2, sizeOffset / 2),
                                                    size = Size(arcSize, arcSize)
                                                )

                                                if (expensePercentage >= 60) {
                                                    drawArc(
                                                        color = Color(0xFFFBBF24).copy(alpha = circleBlinkAlpha),
                                                        startAngle = -225f,
                                                        sweepAngle = sweepAngle,
                                                        useCenter = false,
                                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                                        topLeft = Offset(sizeOffset / 2, sizeOffset / 2),
                                                        size = Size(arcSize, arcSize)
                                                    )
                                                }
                                            }

                                            // Text content inside circle
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = "${expensePercentage.toBangla()}%",
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = if (expensePercentage > 100) Color(0xFFEF4444) else Color.White
                                                )
                                                Text(
                                                    text = "ব্যয়িত",
                                                    fontSize = 9.sp,
                                                    color = Color(0xFF94A3B8),
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "৳ ${remainingBudget.toInt().toBangla()} বাকি",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFFBBF24) // Yellow gold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "৳ ${currentMonthExpenses.toInt().toBangla()} / ৳ ${monthlyBudget.toInt().toBangla()}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White.copy(alpha = 0.9f)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
                            shape = RoundedCornerShape(24.dp),
                            border = if (isGlass) BorderStroke(1.dp, cardBorderColor) else null
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Icon inside square rounded box
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(Color(0xFF3B82F6).copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.BarChart,
                                            contentDescription = null,
                                            tint = Color(0xFF60A5FA),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "মোট হিসাবসমূহ",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // 2x2 Grid of accounts items - Styled with custom White, Yellow, Red value text colors
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        GridAccountItem(
                                            title = "মোট মাল",
                                            value = "${analytics.qty.toBangla()} পিস",
                                            icon = Icons.Default.Layers,
                                            iconBgColor = Color(0xFF5B21B6).copy(alpha = 0.25f),
                                            iconColor = Color(0xFF8B5CF6),
                                            modifier = Modifier.weight(1f),
                                            valueColor = Color.White // সাদা
                                        )
                                        GridAccountItem(
                                            title = "মোট আয়",
                                            value = "৳ ${analytics.income.toBangla()}",
                                            icon = Icons.Default.TrendingUp,
                                            iconBgColor = Color(0xFFD97706).copy(alpha = 0.25f),
                                            iconColor = Color(0xFFFBBF24),
                                            modifier = Modifier.weight(1f),
                                            valueColor = Color(0xFFFCD34D) // হলুদ
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        GridAccountItem(
                                            title = "মোট খরচ",
                                            value = "৳ ${analytics.expense.toBangla()}",
                                            icon = Icons.Default.AccountBalanceWallet,
                                            iconBgColor = Color(0xFFBE123C).copy(alpha = 0.25f),
                                            iconColor = Color(0xFFF43F5E),
                                            modifier = Modifier.weight(1f),
                                            valueColor = Color(0xFFF87171) // লাল
                                        )
                                        GridAccountItem(
                                            title = "বাকি (নিট লাভ)",
                                            value = "৳ ${analytics.net.toBangla()}",
                                            icon = Icons.Default.CheckCircle,
                                            iconBgColor = Color(0xFF0F766E).copy(alpha = 0.25f),
                                            iconColor = Color(0xFF14B8A6),
                                            modifier = Modifier.weight(1f),
                                            valueColor = Color(0xFF34D399) // প্রফেশনাল মিন্ট গ্রিন
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // Average Title Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "দৈনিক গড় হিসাব (মোট কাজের দিন দিয়ে ভাগ)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF94A3B8)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Average horizontal items (3 pills matching screenshot)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    DashboardAvgPillItem(
                                        title = "গড় মাল",
                                        value = "${analytics.avgQty.toBangla()} পিস",
                                        valueColor = Color.White, // সাদা
                                        modifier = Modifier.weight(1f)
                                    )
                                    DashboardAvgPillItem(
                                        title = "গড় খরচ",
                                        value = "৳ ${analytics.avgExpense.toBangla()}",
                                        valueColor = Color(0xFFF87171), // লাল
                                        modifier = Modifier.weight(1f)
                                    )
                                    DashboardAvgPillItem(
                                        title = "গড় লাভ",
                                        value = "৳ ${analytics.avgNet.toBangla()}",
                                        valueColor = Color(0xFFFCD34D), // হলুদ
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // COLLAPSIBLE MONTHLY TRACKER SECTION
                if (currentKhataMode != "ALT") {
                    item {
                        // Header of Collapsible Section
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showMonthlyProgressSection = !showMonthlyProgressSection }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(0xFF3B82F6).copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = null,
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "মাসিক ট্রেকার",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = if (showMonthlyProgressSection) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "অগ্রগতি লুকান বা দেখান",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (showMonthlyProgressSection) {
                        item {
                            val expenseProgressFraction = if (monthlyBudget > 0) (currentMonthExpenses / monthlyBudget).toFloat() else 0f
                        val expenseProgressPercent = (expenseProgressFraction * 100).toInt()
                        val remainingBudget = monthlyBudget - currentMonthExpenses

                        val currentMonthNetProfit = currentMonthIncome - currentMonthExpenses
                        val incomeProgressFraction = if (monthlyIncomeTarget > 0) (currentMonthNetProfit / monthlyIncomeTarget).toFloat() else 0f
                        val incomeProgressFractionForSweep = incomeProgressFraction.coerceIn(0f, 1f)
                        val incomeProgressPercent = (incomeProgressFraction * 100).toInt()
                        val incomeProgressPercentDisplay = incomeProgressPercent.coerceAtLeast(0)

                        val currentMonthAvgNetProfit = if (currentMonthDays > 0) currentMonthNetProfit / currentMonthDays else 0.0
                        val expectedDailyNetProfit = if (monthlyIncomeTarget > 0) (monthlyIncomeTarget / 30.0) else 100.0
                        val successRate = if (expectedDailyNetProfit > 0) {
                            ((currentMonthAvgNetProfit / expectedDailyNetProfit) * 100).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }

                        // Infinite transition for warning blink effect
                        val infiniteTransition = rememberInfiniteTransition(label = "blink")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 0.2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 167, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "alpha"
                        )

                        val shouldBlinkExpense = expenseProgressPercent >= 60
                        val shouldBlinkSuccess = successRate < 50

                        val gradientBorder = BorderStroke(
                            width = 2.dp,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFFF5252), // Coral Red
                                    Color(0xFF4CAF50), // Emerald Green
                                    Color(0xFF009688)  // Teal
                                )
                            )
                        )

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131A2E).copy(alpha = 0.9f)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, brush = gradientBorder.brush)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. MONTHLY EXPENSE LIMIT REMINDER
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "Monthly Expense Limit Reminder",
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White.copy(alpha = 0.7f),
                                                textAlign = TextAlign.Center,
                                                maxLines = 1
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        Box(
                                            modifier = Modifier.size(80.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                val strokeWidth = 4.dp.toPx()
                                                drawCircle(
                                                    color = Color(0xFFFF5252).copy(alpha = 0.12f),
                                                    style = Stroke(width = strokeWidth)
                                                )
                                                drawArc(
                                                    color = Color(0xFFFF5252),
                                                    startAngle = -90f,
                                                    sweepAngle = expenseProgressFraction.coerceIn(0f, 1f) * 360f,
                                                    useCenter = false,
                                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                                    alpha = if (shouldBlinkExpense) alpha else 1f
                                                )
                                            }
                                            
                                            // Small warning icon overlay
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = 2.dp, y = (-2).dp)
                                                    .size(12.dp)
                                                    .background(Color(0xFFFF5252), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(7.dp)
                                                )
                                            }
                                            
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier.padding(4.dp)
                                            ) {
                                                Text(
                                                    text = "${expenseProgressPercent.toBangla()}% ব্যবহৃত",
                                                    fontSize = 7.sp,
                                                    color = Color(0xFFFF5252),
                                                    fontWeight = FontWeight.Black,
                                                    textAlign = TextAlign.Center
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "৳ ${currentMonthExpenses.toBangla()} / ৳ ${monthlyBudget.toBangla()}",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White,
                                                    textAlign = TextAlign.Center
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "মাসিক খরচের লিমিট\n(বাকি)",
                                                    fontSize = 5.sp,
                                                    color = Color.White.copy(alpha = 0.6f),
                                                    textAlign = TextAlign.Center,
                                                    lineHeight = 6.sp
                                                )
                                            }
                                        }
                                    }
 
                                    // Divider Dash
                                    Text(
                                        text = "—",
                                        color = Color.White.copy(alpha = 0.25f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
 
                                    // 2. MONTHLY INCOME TARGET REMINDER
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "Monthly Income Target Reminder",
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White.copy(alpha = 0.7f),
                                                textAlign = TextAlign.Center,
                                                maxLines = 1
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        Box(
                                            modifier = Modifier.size(80.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                val strokeWidth = 4.dp.toPx()
                                                drawCircle(
                                                    color = Color(0xFF4CAF50).copy(alpha = 0.12f),
                                                    style = Stroke(width = strokeWidth)
                                                )
                                                drawArc(
                                                    color = Color(0xFF4CAF50),
                                                    startAngle = -90f,
                                                    sweepAngle = incomeProgressFractionForSweep * 360f,
                                                    useCenter = false,
                                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                                )
                                            }
                                            
                                            // Small trending icon overlay
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = 2.dp, y = (-2).dp)
                                                    .size(12.dp)
                                                    .background(Color(0xFF4CAF50), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.TrendingUp,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(7.dp)
                                                )
                                            }
                                            
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier.padding(3.dp)
                                            ) {
                                                Text(
                                                    text = "${incomeProgressPercentDisplay.toBangla()}% পূরণ হয়েছে",
                                                    fontSize = 7.sp,
                                                    color = Color(0xFF4CAF50),
                                                    fontWeight = FontWeight.Black,
                                                    textAlign = TextAlign.Center,
                                                    lineHeight = 8.sp
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "৳ ${currentMonthNetProfit.toBangla()} / ৳ ${monthlyIncomeTarget.toBangla()}",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White,
                                                    textAlign = TextAlign.Center
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "মাসিক ইনকাম টার্গেট",
                                                    fontSize = 5.sp,
                                                    color = Color.White.copy(alpha = 0.6f),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
 
                                    // Divider Dash
                                    Text(
                                        text = "—",
                                        color = Color.White.copy(alpha = 0.25f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
 
                                    // 3. MONTHLY SUCCESS RATE
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Monthly Success Rate",
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White.copy(alpha = 0.7f),
                                            textAlign = TextAlign.Center,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        Box(
                                            modifier = Modifier.size(80.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                val strokeWidth = 4.dp.toPx()
                                                drawCircle(
                                                    color = Color(0xFF009688).copy(alpha = 0.12f),
                                                    style = Stroke(width = strokeWidth)
                                                )
                                                drawArc(
                                                    color = Color(0xFF009688),
                                                    startAngle = -90f,
                                                    sweepAngle = (successRate / 100f).coerceIn(0f, 1f) * 360f,
                                                    useCenter = false,
                                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                                                    alpha = if (shouldBlinkSuccess) alpha else 1f
                                                )
                                            }
                                            
                                            // Small trophy icon overlay
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = 2.dp, y = (-2).dp)
                                                    .size(12.dp)
                                                    .background(Color(0xFF009688), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.EmojiEvents,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(7.dp)
                                                )
                                            }
                                            
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier.padding(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.EmojiEvents,
                                                    contentDescription = null,
                                                    tint = Color(0xFF009688),
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${successRate.toBangla()}%",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White,
                                                    textAlign = TextAlign.Center
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "মাসিক সাফল্যের হার",
                                                    fontSize = 5.sp,
                                                    color = Color.White.copy(alpha = 0.6f),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Sleek small blue capsule button for Monthly Tracker History
                                Box(
                                    modifier = Modifier
                                        .width(56.dp)
                                        .height(20.dp)
                                        .background(
                                            color = Color(0xFF3B82F6), 
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            showMonthlyHistoryDialog = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "ইতিহাস",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }

                                // Interactive Dialog 3: Monthly History Dialog
                                if (showMonthlyHistoryDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showMonthlyHistoryDialog = false },
                                        title = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.History,
                                                    contentDescription = null,
                                                    tint = Color(0xFF60A5FA),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Text(
                                                    text = "মাসিক ট্র্যাকার ইতিহাস",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.White,
                                                    fontSize = 18.sp
                                                )
                                            }
                                        },
                                        text = {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 420.dp)
                                            ) {
                                                if (completedMonthCount == 0) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 24.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Info,
                                                            contentDescription = null,
                                                            tint = Color(0xFF94A3B8).copy(alpha = 0.5f),
                                                            modifier = Modifier.size(48.dp)
                                                        )
                                                        Text(
                                                            text = "কোনো ইতিহাস নেই",
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White
                                                        )
                                                        Text(
                                                            text = "অন্তত ৩০ দিনের হিসাবের এন্ট্রি যুক্ত হলে এখানে মাস ভিত্তিক ইতিহাস তৈরি হবে। বর্তমানে ${(allUniqueDays.size).toBangla()} দিনের এন্ট্রি আছে।",
                                                            fontSize = 12.sp,
                                                            color = Color(0xFF94A3B8),
                                                            textAlign = TextAlign.Center,
                                                            lineHeight = 16.sp
                                                        )
                                                    }
                                                } else {
                                                    val filtered = remember(entries, selectedFolder) {
                                                        entries.filter { selectedFolder == "সব ফোল্ডার" || it.folderName == selectedFolder }
                                                    }
                                                    LazyColumn(
                                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        items(completedMonthCount) { reversedIndex ->
                                                            val monthIndex = completedMonthCount - reversedIndex
                                                            val startIdx = (monthIndex - 1) * 30
                                                            val endIdx = monthIndex * 30 - 1
                                                            val monthStartDay = allUniqueDays[startIdx]
                                                            val monthEndDay = allUniqueDays[endIdx]

                                                            val monthEntries = remember(filtered, monthStartDay, monthEndDay) {
                                                                filtered.filter {
                                                                    val dayStart = getDayStartMillis(it.dateMillis)
                                                                    dayStart in monthStartDay..monthEndDay
                                                                }
                                                            }

                                                            val incomeSum = remember(monthEntries, wageRate) {
                                                                monthEntries.sumOf { e ->
                                                                    val prodIncome = (e.quantity / 100.0) * wageRate
                                                                    if (e.isIncome) e.income else prodIncome
                                                                }
                                                            }

                                                            val expenseSum = remember(monthEntries) {
                                                                monthEntries.filter { !it.isIncome }.sumOf { it.expense }
                                                            }

                                                            val netProfit = incomeSum - expenseSum

                                                            val avgNetProfit = netProfit / 30.0
                                                            val expectedDailyNetProfit = if (monthlyIncomeTarget > 0) (monthlyIncomeTarget / 30.0) else 100.0
                                                            val successRateVal = if (expectedDailyNetProfit > 0) {
                                                                ((avgNetProfit / expectedDailyNetProfit) * 100).toInt().coerceIn(0, 100)
                                                            } else {
                                                                0
                                                            }

                                                            val sdf = remember { SimpleDateFormat("dd MMM, yyyy", Locale("bn", "BD")) }
                                                            val dateRangeStr = "${sdf.format(Date(monthStartDay)).toBanglaDigits()} হতে ${sdf.format(Date(monthEndDay)).toBanglaDigits()}"

                                                            Card(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF131C33)),
                                                                shape = RoundedCornerShape(12.dp),
                                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                                            ) {
                                                                Column(
                                                                    modifier = Modifier.padding(12.dp),
                                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Text(
                                                                            text = getMonthNameBengali(monthIndex),
                                                                            fontWeight = FontWeight.Bold,
                                                                            fontSize = 14.sp,
                                                                            color = Color(0xFF60A5FA)
                                                                        )
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .background(
                                                                                    color = if (successRateVal >= 75) Color(0xFF10B981).copy(alpha = 0.15f)
                                                                                            else if (successRateVal >= 50) Color(0xFFF59E0B).copy(alpha = 0.15f)
                                                                                            else Color(0xFFEF4444).copy(alpha = 0.15f),
                                                                                    shape = RoundedCornerShape(4.dp)
                                                                                )
                                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                        ) {
                                                                            Text(
                                                                                text = "সাফল্য: ${successRateVal.toBangla()}%",
                                                                                fontSize = 11.sp,
                                                                                fontWeight = FontWeight.Bold,
                                                                                color = if (successRateVal >= 75) Color(0xFF34D399)
                                                                                        else if (successRateVal >= 50) Color(0xFFFBBF24)
                                                                                        else Color(0xFFF87171)
                                                                            )
                                                                        }
                                                                    }

                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.CalendarToday,
                                                                            contentDescription = null,
                                                                            tint = Color(0xFF94A3B8),
                                                                            modifier = Modifier.size(12.dp)
                                                                        )
                                                                        Text(
                                                                            text = "তারিখ: $dateRangeStr",
                                                                            fontSize = 11.sp,
                                                                            color = Color(0xFF94A3B8)
                                                                        )
                                                                    }

                                                                    Divider(color = Color.White.copy(alpha = 0.05f))

                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                                    ) {
                                                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                                            Text("মোট আয়", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                                                            Text("৳ ${incomeSum.toInt().toBangla()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                                                                        }
                                                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                                            Text("মোট ব্যয়", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                                                            Text("৳ ${expenseSum.toInt().toBangla()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                                                                        }
                                                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                                            Text("নিট লাভ", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                                                            Text("৳ ${netProfit.toInt().toBangla()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(onClick = { showMonthlyHistoryDialog = false }) {
                                                Text("বন্ধ করুন", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        containerColor = Color(0xFF0D1527)
                                    )
                                }

                                // Interactive Dialog 1: Change Monthly & Daily Budget Limits
                                if (showBudgetDialog) {
                                    var inputBudgetVal by remember {
                                        mutableStateOf(
                                            if (monthlyBudget % 1.0 == 0.0) monthlyBudget.toInt().toString() else monthlyBudget.toString()
                                        )
                                    }
                                    var inputDailyBudgetVal by remember {
                                        mutableStateOf(
                                            if (dailyBudget % 1.0 == 0.0) dailyBudget.toInt().toString() else dailyBudget.toString()
                                        )
                                    }
                                    AlertDialog(
                                        onDismissRequest = { showBudgetDialog = false },
                                        title = {
                                            Text(
                                                text = "খরচের লিমিট নির্ধারণ",
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFF5252)
                                            )
                                        },
                                        text = {
                                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Text(
                                                    "আপনার খরচ লিমিটসমূহ নির্ধারণ করুন:",
                                                    fontSize = 13.sp,
                                                    color = Color.White.copy(alpha = 0.7f)
                                                )
                                                
                                                // Monthly limit field
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(
                                                        "মাসিক খরচের লিমিট বাজেট (টাকা):",
                                                        fontSize = 11.sp,
                                                        color = Color.White.copy(alpha = 0.5f),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    OutlinedTextField(
                                                        value = inputBudgetVal,
                                                        onValueChange = { inputBudgetVal = it },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        shape = RoundedCornerShape(14.dp),
                                                        singleLine = true,
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedBorderColor = Color(0xFFFF5252),
                                                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                                            focusedContainerColor = Color(0xFF131C33),
                                                            unfocusedContainerColor = Color(0xFF131C33),
                                                            focusedTextColor = Color.White,
                                                            unfocusedTextColor = Color.White
                                                        ),
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }

                                                // Daily limit field
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(
                                                        "দৈনিক খরচের লিমিট বাজেট (টাকা):",
                                                        fontSize = 11.sp,
                                                        color = Color.White.copy(alpha = 0.5f),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    OutlinedTextField(
                                                        value = inputDailyBudgetVal,
                                                        onValueChange = { inputDailyBudgetVal = it },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        shape = RoundedCornerShape(14.dp),
                                                        singleLine = true,
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedBorderColor = Color(0xFFFF5252),
                                                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                                            focusedContainerColor = Color(0xFF131C33),
                                                            unfocusedContainerColor = Color(0xFF131C33),
                                                            focusedTextColor = Color.White,
                                                            unfocusedTextColor = Color.White
                                                        ),
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(
                                                onClick = {
                                                    val enteredBudget = inputBudgetVal.toDoubleOrNull()
                                                    val enteredDaily = inputDailyBudgetVal.toDoubleOrNull()
                                                    if (enteredBudget != null && enteredBudget > 0 && enteredDaily != null && enteredDaily > 0) {
                                                        viewModel.updateMonthlyBudget(enteredBudget)
                                                        viewModel.updateSettings(wageRate, dailyTarget, enteredDaily)
                                                        showBudgetDialog = false
                                                        Toast.makeText(context, "খরচের লিমিটসমূহ সফলভাবে আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "সঠিক টাকার পরিমাণ লিখুন", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            ) {
                                                Text("নিশ্চিত করুন", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                                             }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showBudgetDialog = false }) {
                                                Text("বাতিল", color = Color.White.copy(alpha = 0.5f))
                                            }
                                        },
                                        containerColor = Color(0xFF0D1527)
                                    )
                                }

                                // Interactive Dialog 2: Change Monthly Income Target
                                if (showIncomeTargetDialog) {
                                    var inputTargetVal by remember { mutableStateOf(monthlyIncomeTarget.toString()) }
                                    AlertDialog(
                                        onDismissRequest = { showIncomeTargetDialog = false },
                                        title = {
                                            Text(
                                                text = "মাসিক ইনকাম লক্ষ্য নির্ধারণ",
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4CAF50)
                                            )
                                        },
                                        text = {
                                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Text(
                                                    "আপনার চলতি মাসের ইনকাম লক্ষ্য লিখুন:",
                                                    fontSize = 13.sp,
                                                    color = Color.White.copy(alpha = 0.7f)
                                                )
                                                OutlinedTextField(
                                                    value = inputTargetVal,
                                                    onValueChange = { inputTargetVal = it },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    shape = RoundedCornerShape(14.dp),
                                                    singleLine = true,
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = Color(0xFF4CAF50),
                                                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                                        focusedContainerColor = Color(0xFF131C33),
                                                        unfocusedContainerColor = Color(0xFF131C33),
                                                        focusedTextColor = Color.White,
                                                        unfocusedTextColor = Color.White
                                                    ),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(
                                                onClick = {
                                                    val enteredTarget = inputTargetVal.toDoubleOrNull()
                                                    if (enteredTarget != null && enteredTarget > 0) {
                                                        viewModel.updateMonthlyIncomeTarget(enteredTarget)
                                                        showIncomeTargetDialog = false
                                                        Toast.makeText(context, "ইনকাম লক্ষ্য সফলভাবে আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "সঠিক ইনকাম লক্ষ্য লিখুন", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            ) {
                                                Text("নিশ্চিত করুন", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showIncomeTargetDialog = false }) {
                                                Text("বাতিল", color = Color.White.copy(alpha = 0.5f))
                                            }
                                        },
                                        containerColor = Color(0xFF0D1527)
                                    )
                                }
                            }
                        }


                    }
                }
                }

                // 3. FOLDERS / SESSIONS HEADER & Horizontal list
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showFoldersSection = !showFoldersSection },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (!showFoldersSection) "হিসাব খাতা: $selectedFolder" else "হিসাব খাতা / ফোল্ডারসমূহ",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = if (showFoldersSection) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "ফোল্ডারসমূহ দেখান বা লুকান",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // + নতুন ফোল্ডার Button matching screenshot
                            IconButton(
                                onClick = { showAddFolderDialog = true },
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(0xFF1E3A8A).copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "নতুন ফোল্ডার",
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Horizontal scroll list of folder pills matching screenshot
                        if (showFoldersSection) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item {
                                    CustomFolderPill(
                                        label = "সব হিসাব",
                                        isSelected = selectedFolder == "সব ফোল্ডার",
                                        leadingIcon = Icons.Default.List,
                                        onClick = { viewModel.selectFolder("সব ফোল্ডার") }
                                    )
                                }

                                item {
                                    CustomFolderPill(
                                        label = "সাধারণ হিসাব",
                                        isSelected = selectedFolder == "সাধারণ হিসাব",
                                        leadingIcon = Icons.Default.Folder,
                                        onClick = { viewModel.selectFolder("সাধারণ হিসাব") }
                                    )
                                }

                                items(folders.filter { it != "সাধারণ হিসাব" }) { folder ->
                                    CustomFolderPill(
                                        label = folder,
                                        isSelected = selectedFolder == folder,
                                        leadingIcon = Icons.Default.Folder,
                                        showDelete = true,
                                        onClick = { viewModel.selectFolder(folder) },
                                        onDeleteClick = {
                                            viewModel.deleteFolder(folder)
                                            Toast.makeText(context, "ফোল্ডার এবং হিসাব মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. INCOME-EXPENSE TREND CHART (COLLAPSIBLE)
                item {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) { showGraphs = !showGraphs }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (showGraphs) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color(0xFF3B82F6),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showGraphs) "আয়-ব্যয় গ্রাফ লুকান" else "আয়-ব্যয় গ্রাফ দেখুন",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF3B82F6)
                            )
                        }

                        AnimatedVisibility(
                            visible = showGraphs,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                IncomeExpenseTrendChart(
                                    filteredEntries = filteredEntries,
                                    wageRate = wageRate,
                                    currentKhataMode = currentKhataMode
                                )
                            }
                        }
                    }
                }

                // 5. DAILY HISTORY LIST HEADER WITH BADGE
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "দৈনিক হিসাবের ইতিহাস",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            // Total workdays badge matching screenshot
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E3A8A).copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "মোট কাজ: ${analytics.workdays.toBangla()} দিন",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF60A5FA),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // SEARCH BAR & SORT BUTTON ROW
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("হিসাব খুঁজুন (যেমন: ২০২৬ বা মন্তব্য)", fontSize = 12.sp, color = Color(0xFF94A3B8)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = null,
                                                tint = Color(0xFF94A3B8),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF3B82F6).copy(alpha = 0.5f),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                    focusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                                    unfocusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.3f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("search_field"),
                                textStyle = TextStyle(fontSize = 12.sp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            IconButton(
                                onClick = { sortNewestFirst = !sortNewestFirst },
                                modifier = Modifier
                                    .background(
                                        Color(0xFF1E3A8A).copy(alpha = 0.4f),
                                        CircleShape
                                    )
                                    .size(46.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = "সাজান",
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // 6. GROUPED HISTORY LIST VIEW OR EMPTY PLACEHOLDER
                if (filteredEntries.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8).copy(alpha = 0.3f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "কোনো হিসাবের তথ্য পাওয়া যায়নি!",
                                    color = Color(0xFF94A3B8).copy(alpha = 0.5f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else {
                    val groupedEntries = filteredEntries.groupBy { entry ->
                        val date = Date(entry.dateMillis)
                        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("bn", "BD"))
                        monthFormat.format(date)
                    }

                    groupedEntries.forEach { (monthName, monthList) ->
                        stickyHeader {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Transparent)
                                    .padding(vertical = 6.dp)
                            ) {
                                Text(
                                    text = monthName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF60A5FA),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }

                        items(monthList) { item ->
                            HistoryListItemRow(
                                entry = item,
                                wageRate = wageRate,
                                onEdit = {
                                    entryToEdit = item
                                    showAddDialog = true
                                },
                                onDelete = {
                                    viewModel.deleteEntry(item)
                                    Toast.makeText(context, "হিসাব ডিলিট করা হয়েছে", Toast.LENGTH_SHORT).show()
                                },
                                cardBgColor = cardBgColor,
                                isGlass = isGlass,
                                cardBorderColor = cardBorderColor
                            )
                        }
                    }
                }
                    }
                }
            }
        }
            }

    // --- DIALOGS CONTROLLERS ---

    // 1. ADD / EDIT RECORD DIALOG
    if (showAddDialog) {
        AddEditEntryDialog(
            entryToEdit = entryToEdit,
            folders = folders,
            categories = categories,
            wageRate = wageRate,
            dailyTarget = dailyTarget,
            defaultFolder = selectedFolder,
            isAltMode = (currentKhataMode == "ALT"),
            onDismiss = {
                showAddDialog = false
                entryToEdit = null
            },
            onSave = { updatedQty, updatedExpense, updatedIncome, updatedCategory, updatedIsIncome, updatedNote, updatedFolder, updatedDate ->
                if (entryToEdit == null) {
                    val newEntry = DailyEntry(
                        dateMillis = updatedDate,
                        quantity = updatedQty,
                        expense = updatedExpense,
                        income = updatedIncome,
                        category = updatedCategory,
                        isIncome = updatedIsIncome,
                        note = updatedNote,
                        folderName = updatedFolder
                    )
                    viewModel.insertEntry(newEntry)
                    Toast.makeText(context, "হিসাব সফলভাবে যোগ হয়েছে!", Toast.LENGTH_SHORT).show()
                } else {
                    val edited = entryToEdit!!.copy(
                        dateMillis = updatedDate,
                        quantity = updatedQty,
                        expense = updatedExpense,
                        income = updatedIncome,
                        category = updatedCategory,
                        isIncome = updatedIsIncome,
                        note = updatedNote,
                        folderName = updatedFolder
                    )
                    viewModel.updateEntry(edited)
                    Toast.makeText(context, "হিসাব সফলভাবে আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                }
                showAddDialog = false
                entryToEdit = null
            }
        )
    }

    // 2. SETTINGS, THEME & BACKUP DIALOG
    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }

    // PREMIUM MODE DIALOGS
    if (showPremiumDatePicker) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = premiumSelectedDate
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance()
                newCal.set(year, month, dayOfMonth)
                premiumSelectedDate = newCal.timeInMillis
                showPremiumDatePicker = false
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
        showPremiumDatePicker = false
    }

    if (premiumEditDayDialogData != null) {
        val editData = premiumEditDayDialogData!!
        var editIncome by remember { mutableStateOf(editData.income.toInt().toString()) }
        var editNasta by remember { mutableStateOf(editData.nasta.toInt().toString()) }
        var editBhat by remember { mutableStateOf(editData.bhat.toInt().toString()) }
        var editGariBhara by remember { mutableStateOf(editData.gariBhara.toInt().toString()) }
        var editOnnano by remember { mutableStateOf(editData.onnano.toInt().toString()) }

        val formattedDate = remember {
            val sdf = SimpleDateFormat("dd MMMM, yyyy", Locale("bn"))
            sdf.format(Date(editData.dateMillis)).toBanglaDigits()
        }

        AlertDialog(
            onDismissRequest = { premiumEditDayDialogData = null },
            containerColor = Color(0xFF0F172A),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(1.5.dp, Color(0xFF818CF8).copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            title = {
                Text(
                    text = "হিসাব সংশোধন: $formattedDate",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = editIncome,
                        onValueChange = { editIncome = it },
                        label = { Text("আয় (টাকা)", color = Color(0xFF10B981)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = Color(0xFF10B981).copy(alpha = 0.4f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = editNasta,
                        onValueChange = { editNasta = it },
                        label = { Text("নাস্তা খরচ (টাকা)", color = Color(0xFFFBBF24)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFBBF24),
                            unfocusedBorderColor = Color(0xFFFBBF24).copy(alpha = 0.4f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = editBhat,
                        onValueChange = { editBhat = it },
                        label = { Text("ভাত খরচ (টাকা)", color = Color(0xFFFBBF24)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFBBF24),
                            unfocusedBorderColor = Color(0xFFFBBF24).copy(alpha = 0.4f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = editGariBhara,
                        onValueChange = { editGariBhara = it },
                        label = { Text("গাড়ি ভাড়া (টাকা)", color = Color(0xFFFBBF24)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFBBF24),
                            unfocusedBorderColor = Color(0xFFFBBF24).copy(alpha = 0.4f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = editOnnano,
                        onValueChange = { editOnnano = it },
                        label = { Text("অন্যান্য খরচ (টাকা)", color = Color(0xFFFBBF24)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFBBF24),
                            unfocusedBorderColor = Color(0xFFFBBF24).copy(alpha = 0.4f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val income = editIncome.toDoubleOrNull() ?: 0.0
                        val nasta = editNasta.toDoubleOrNull() ?: 0.0
                        val bhat = editBhat.toDoubleOrNull() ?: 0.0
                        val gariBhara = editGariBhara.toDoubleOrNull() ?: 0.0
                        val onnano = editOnnano.toDoubleOrNull() ?: 0.0

                        viewModel.savePremiumDayData(
                            editData.dateMillis,
                            income,
                            nasta,
                            bhat,
                            gariBhara,
                            onnano,
                            selectedFolder
                        )
                        premiumEditDayDialogData = null
                        Toast.makeText(context, "হিসাব সফলভাবে সংশোধন করা হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("সংরক্ষণ", color = Color(0xFF818CF8), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val currentMillis = editData.dateMillis
                            premiumEditDayDialogData = null
                            premiumDayToDeleteMillis = currentMillis
                        }
                    ) {
                        Text("মুছে ফেলুন", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { premiumEditDayDialogData = null }) {
                        Text("বাতিল", color = Color(0xFF94A3B8))
                    }
                }
            }
        )
    }

    if (premiumDayToDeleteMillis != null) {
        val deleteDateMillis = premiumDayToDeleteMillis!!
        val formattedDeleteDate = remember {
            val sdf = SimpleDateFormat("dd MMMM, yyyy", Locale("bn"))
            sdf.format(Date(deleteDateMillis)).toBanglaDigits()
        }

        AlertDialog(
            onDismissRequest = { premiumDayToDeleteMillis = null },
            containerColor = Color(0xFF0F172A),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            title = {
                Text(
                    text = "হিসাব মুছে ফেলুন",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "আপনি কি নিশ্চিতভাবে $formattedDeleteDate তারিখের সম্পূর্ণ হিসাবটি মুছে ফেলতে চান?",
                    color = Color(0xFFE2E8F0),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePremiumDayData(deleteDateMillis, selectedFolder)
                        premiumDayToDeleteMillis = null
                        Toast.makeText(context, "হিসাবটি সফলভাবে মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("মুছে ফেলুন", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { premiumDayToDeleteMillis = null }) {
                    Text("বাতিল", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    if (showPremiumAddDialog) {
        var addSelectedDate by remember { mutableStateOf(System.currentTimeMillis()) }
        var addIncome by remember { mutableStateOf("") }
        var addNasta by remember { mutableStateOf("") }
        var addBhat by remember { mutableStateOf("") }
        var addGariBhara by remember { mutableStateOf("") }
        var addOnnano by remember { mutableStateOf("") }

        var showFolderDropdown by remember { mutableStateOf(false) }
        var selectedFolderForAdd by remember {
            mutableStateOf(
                if (selectedFolder == "সব ফোল্ডার") {
                    folders.firstOrNull() ?: "আয় খাতা"
                } else {
                    selectedFolder
                }
            )
        }

        val formattedDate = remember(addSelectedDate) {
            val sdf = java.text.SimpleDateFormat("dd MMMM, yyyy", java.util.Locale("bn"))
            sdf.format(java.util.Date(addSelectedDate)).toBanglaDigits()
        }

        AlertDialog(
            onDismissRequest = { showPremiumAddDialog = false },
            containerColor = Color(0xFF0F172A),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(1.5.dp, Color(0xFF818CF8).copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            title = {
                Text(
                    text = "নতুন প্রিমিয়াম এন্ট্রি",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Date Selector Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val cal = Calendar.getInstance()
                                cal.timeInMillis = addSelectedDate
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val newCal = Calendar.getInstance()
                                        newCal.set(year, month, dayOfMonth)
                                        addSelectedDate = newCal.timeInMillis
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                  ).show()
                            }
                            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "তারিখ",
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Text("তারিখ:", fontSize = 12.sp, color = Color(0xFF94A3B8))
                            Text(formattedDate, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "পরিবর্তন করুন",
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Folder Selector dropdown
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "হিসাব খাতা / ফোল্ডার",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .background(Color(0xFF131C33), RoundedCornerShape(12.dp))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(12.dp))
                                    .clickable {
                                        showFolderDropdown = true
                                    }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = selectedFolderForAdd,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showFolderDropdown,
                                onDismissRequest = { showFolderDropdown = false },
                                modifier = Modifier.fillMaxWidth(0.75f)
                            ) {
                                folders.forEach { folderName ->
                                    DropdownMenuItem(
                                        text = { Text(folderName) },
                                        onClick = {
                                            selectedFolderForAdd = folderName
                                            showFolderDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 1. Income field
                    OutlinedTextField(
                        value = addIncome,
                        onValueChange = { addIncome = it },
                        label = { Text("আয় (টাকা)", color = Color(0xFF10B981)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = Color(0xFF10B981).copy(alpha = 0.4f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    // 2. Nasta Expense field
                    OutlinedTextField(
                        value = addNasta,
                        onValueChange = { addNasta = it },
                        label = { Text("নাস্তা খরচ (টাকা)", color = Color(0xFFFBBF24)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFBBF24),
                            unfocusedBorderColor = Color(0xFFFBBF24).copy(alpha = 0.4f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    // 3. Bhat Expense field
                    OutlinedTextField(
                        value = addBhat,
                        onValueChange = { addBhat = it },
                        label = { Text("ভাত খরচ (টাকা)", color = Color(0xFFF59E0B)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFF59E0B),
                            unfocusedBorderColor = Color(0xFFF59E0B).copy(alpha = 0.4f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    // 4. Gari Bhara field
                    OutlinedTextField(
                        value = addGariBhara,
                        onValueChange = { addGariBhara = it },
                        label = { Text("গাড়ি ভাড়া (টাকা)", color = Color(0xFF60A5FA)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF60A5FA),
                            unfocusedBorderColor = Color(0xFF60A5FA).copy(alpha = 0.4f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    // 5. Onnano field
                    OutlinedTextField(
                        value = addOnnano,
                        onValueChange = { addOnnano = it },
                        label = { Text("অন্যান্য খরচ (টাকা)", color = Color(0xFF94A3B8)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF94A3B8),
                            unfocusedBorderColor = Color(0xFF94A3B8).copy(alpha = 0.4f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val incomeVal = addIncome.toDoubleOrNull() ?: 0.0
                        val nastaVal = addNasta.toDoubleOrNull() ?: 0.0
                        val bhatVal = addBhat.toDoubleOrNull() ?: 0.0
                        val gariVal = addGariBhara.toDoubleOrNull() ?: 0.0
                        val onnanoVal = addOnnano.toDoubleOrNull() ?: 0.0

                        viewModel.savePremiumDayData(
                            addSelectedDate,
                            incomeVal,
                            nastaVal,
                            bhatVal,
                            gariVal,
                            onnanoVal,
                            selectedFolderForAdd
                        )
                        showPremiumAddDialog = false
                        Toast.makeText(context, "হিসাব সফলভাবে যোগ করা হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("যোগ করুন", color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPremiumAddDialog = false }) {
                    Text("বাতিল", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    if (showPremiumTargetsEditDialog) {
        var editIncomeTarget by remember { mutableStateOf(premiumDailyIncomeTarget.toInt().toString()) }
        var editExpenseTarget by remember { mutableStateOf(premiumDailyExpenseTarget.toInt().toString()) }

        AlertDialog(
            onDismissRequest = { showPremiumTargetsEditDialog = false },
            containerColor = Color(0xFF0F172A),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(1.5.dp, Color(0xFFFBBF24).copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            title = {
                Text(
                    text = "দৈনিক বাজেট ও লিমিট পরিবর্তন",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "আপনার দৈনিক টার্গেট সেট করুন। এটি রিয়েল-টাইম সারাংশে ব্যবহার করা হবে।",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )

                    // Daily Income Target Input
                    OutlinedTextField(
                        value = editIncomeTarget,
                        onValueChange = { editIncomeTarget = it },
                        label = { Text("দৈনিক আয় টার্গেট", color = Color(0xFF10B981)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = Color(0xFF10B981).copy(alpha = 0.4f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    // Daily Expense Target Input
                    OutlinedTextField(
                        value = editExpenseTarget,
                        onValueChange = { editExpenseTarget = it },
                        label = { Text("দৈনিক ব্যয় লিমিট", color = Color(0xFFFBBF24)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFBBF24),
                            unfocusedBorderColor = Color(0xFFFBBF24).copy(alpha = 0.4f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val incomeVal = editIncomeTarget.toDoubleOrNull() ?: 0.0
                        val expenseVal = editExpenseTarget.toDoubleOrNull() ?: 0.0

                        viewModel.updatePremiumTargets(incomeVal, expenseVal)
                        showPremiumTargetsEditDialog = false
                        Toast.makeText(context, "টার্গেট সফলভাবে আপডেট করা হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("সংরক্ষণ", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPremiumTargetsEditDialog = false }) {
                    Text("বাতিল", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    // 3. ADD FOLDER DIALOG
    if (showAddFolderDialog) {
        var newFolderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddFolderDialog = false },
            title = { Text("নতুন হিসাবের খাতা / ফোল্ডার", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = { Text("ফোল্ডারের নাম (যেমন: ফ্যামিলি বাজেট)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.addFolder(newFolderName.trim())
                            Toast.makeText(context, "ফোল্ডার তৈরি হয়েছে!", Toast.LENGTH_SHORT).show()
                            showAddFolderDialog = false
                        }
                    }
                ) {
                    Text("তৈরি করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFolderDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }
}
}

// --- DYNAMICALLY RENDERED INTERACTIVE LINE CHART (EXPENSE TREND) ---
@Composable
fun ExpenseLineChart(filteredEntries: List<DailyEntry>) {
    val dailyExpenses = remember(filteredEntries) {
        val groupedMap = mutableMapOf<Long, Double>()
        for (e in filteredEntries) {
            // Group by day-start timestamp
            val cal = Calendar.getInstance()
            cal.timeInMillis = e.dateMillis
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis
            groupedMap[dayStart] = (groupedMap[dayStart] ?: 0.0) + e.expense
        }
        // Take last 7 days sorted chronologically
        groupedMap.toList().sortedBy { it.first }.takeLast(7)
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    if (dailyExpenses.size < 2) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "হিস্টোরিতে ২ বা ততোধিক ভিন্ন দিনের খরচ থাকলে এখানে গ্রাফ প্রদর্শিত হবে।",
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    } else {
        val maxVal = remember(dailyExpenses) {
            val max = dailyExpenses.maxOf { it.second }
            if (max == 0.0) 100.0 else max * 1.2
        }

        val dateFormatter = remember { SimpleDateFormat("dd MMM", Locale("bn", "BD")) }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 10.dp, bottom = 18.dp, start = 8.dp, end = 8.dp)
        ) {
            val width = size.width
            val height = size.height

            val stepX = width / (dailyExpenses.size - 1)
            val points = dailyExpenses.mapIndexed { index, pair ->
                val x = index * stepX
                val y = height - ((pair.second / maxVal) * height).toFloat()
                Offset(x, y)
            }

            // Draw grid guidelines
            val gridLines = 3
            for (i in 0..gridLines) {
                val y = (height / gridLines) * i
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Draw line area gradient shadow
            val fillPath = Path().apply {
                moveTo(points.first().x, height)
                for (p in points) {
                    lineTo(p.x, p.y)
                }
                lineTo(points.last().x, height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.25f), Color.Transparent),
                    startY = 0f,
                    endY = height
                )
            )

            // Draw clean trend connection line
            val connectionPath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val p = points[i]
                    lineTo(p.x, p.y)
                }
            }
            drawPath(
                path = connectionPath,
                color = primaryColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Draw circles & value text labels above points
            points.forEachIndexed { index, p ->
                drawCircle(
                    color = primaryColor,
                    radius = 4.dp.toPx(),
                    center = p
                )
                drawCircle(
                    color = Color.White,
                    radius = 1.5.dp.toPx(),
                    center = p
                )
            }
        }
    }
}

// --- ADVANCED MONITORING NEON DONUT CHART ---
@Composable
fun AdvanceMonitoringCard(
    premiumDailyGrouped: List<PremiumDaySummary>
) {
    val totalIncome = remember(premiumDailyGrouped) { premiumDailyGrouped.sumOf { it.totalIncome } }
    val totalExpense = remember(premiumDailyGrouped) { premiumDailyGrouped.sumOf { it.totalExpense } }
    val balance = totalIncome - totalExpense

    val nastaSum = remember(premiumDailyGrouped) { premiumDailyGrouped.sumOf { it.nasta } }
    val bhatSum = remember(premiumDailyGrouped) { premiumDailyGrouped.sumOf { it.bhat } }
    val gariSum = remember(premiumDailyGrouped) { premiumDailyGrouped.sumOf { it.gariBhara } }
    val onnanoSum = remember(premiumDailyGrouped) { premiumDailyGrouped.sumOf { it.onnano } }

    val hasData = totalExpense > 0.0

    val nastaPercent = if (hasData) (nastaSum / totalExpense).toFloat() else 0.0f
    val bhatPercent = if (hasData) (bhatSum / totalExpense).toFloat() else 0.0f
    val gariPercent = if (hasData) (gariSum / totalExpense).toFloat() else 0.0f
    val onnanoPercent = if (hasData) (onnanoSum / totalExpense).toFloat() else 0.0f

    val nastaColor = Color(0xFFFF2A6D) // Glowing neon red/pink
    val bhatColor = Color(0xFFFFD700)  // Glowing golden yellow
    val gariColor = Color(0xFF05FF80)  // Glowing neon green
    val onnanoColor = Color(0xFFB500FF) // Glowing neon purple/magenta

    var isMinimized by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF030E4F)), // Premium deep space dark blue
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, Color(0xFF1E3A8A).copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable { isMinimized = !isMinimized }
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0xFF05FF80).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF05FF80).copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = null,
                            tint = Color(0xFF05FF80),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "এডভান্স মনিটরিং",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF05FF80).copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, Color(0xFF05FF80).copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "রিয়েল-টাইম",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF05FF80),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    IconButton(
                        onClick = { isMinimized = !isMinimized },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isMinimized) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = "মিনিমাইজ",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            if (!isMinimized) {
                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: ভাড়াঁ and অন্যান্য percentages
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(gariColor, CircleShape)
                                )
                                Text(
                                    text = "ভাড়া",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                            Text(
                                text = "${(gariPercent * 100).toInt().toBangla()}%",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = gariColor
                            )
                            Text(
                                text = "৳ ${gariSum.toInt().toBangla()}",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(onnanoColor, CircleShape)
                                )
                                Text(
                                    text = "অন্যান্য",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                            Text(
                                text = "${(onnanoPercent * 100).toInt().toBangla()}%",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = onnanoColor
                            )
                            Text(
                                text = "৳ ${onnanoSum.toInt().toBangla()}",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    // Center: Smaller, compact neon donut chart
                    Box(
                        modifier = Modifier
                            .size(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeW = 10.dp.toPx()
                            drawCircle(
                                color = Color(0xFF0E2280).copy(alpha = 0.4f),
                                radius = size.width / 2f - 2.dp.toPx(),
                                style = Stroke(width = 0.8.dp.toPx())
                            )
                            drawCircle(
                                color = Color(0xFF0E2280).copy(alpha = 0.4f),
                                radius = size.width / 2f - strokeW - 10.dp.toPx(),
                                style = Stroke(width = 0.8.dp.toPx())
                            )
                        }

                        Canvas(
                            modifier = Modifier.size(116.dp)
                        ) {
                            val strokeWidthPx = 10.dp.toPx()
                            val paddingPx = 2.dp.toPx()
                            val donutSize = Size(size.width - paddingPx * 2, size.height - paddingPx * 2)
                            val topLeftOffset = Offset(paddingPx, paddingPx)

                            val segments = listOf(
                                Triple(gariPercent, gariColor, "ভাড়া"),
                                Triple(nastaPercent, nastaColor, "নাস্তা"),
                                Triple(bhatPercent, bhatColor, "ভাত"),
                                Triple(onnanoPercent, onnanoColor, "অন্যান্য")
                            )

                            var currentAngle = -90f

                            segments.forEach { (percent, color, label) ->
                                val sweepAngle = percent * 360f
                                val actualSweep = sweepAngle - 3f
                                val actualStart = currentAngle + 1.5f

                                if (actualSweep > 0f) {
                                    drawArc(
                                        color = color.copy(alpha = 0.2f),
                                        startAngle = actualStart,
                                        sweepAngle = actualSweep,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidthPx + 6.dp.toPx(), cap = StrokeCap.Round),
                                        size = donutSize,
                                        topLeft = topLeftOffset
                                    )

                                    drawArc(
                                        color = color,
                                        startAngle = actualStart,
                                        sweepAngle = actualSweep,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                                        size = donutSize,
                                        topLeft = topLeftOffset
                                    )
                                }
                                currentAngle += sweepAngle
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "অবশিষ্ট:",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = "৳ ${balance.toBangla()}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF38BDF8)
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = "৳ ${totalExpense.toBangla()}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }

                    // Right Column: নাস্তা and ভাত percentages
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "নাস্তা",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFCBD5E1)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(nastaColor, CircleShape)
                                )
                            }
                            Text(
                                text = "${(nastaPercent * 100).toInt().toBangla()}%",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = nastaColor
                            )
                            Text(
                                text = "৳ ${nastaSum.toInt().toBangla()}",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "ভাত",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFCBD5E1)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(bhatColor, CircleShape)
                                )
                            }
                            Text(
                                text = "${(bhatPercent * 100).toInt().toBangla()}%",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = bhatColor
                            )
                            Text(
                                text = "৳ ${bhatSum.toInt().toBangla()}",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "অবশিষ্ট: ৳ ${balance.toBangla()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                        Box(modifier = Modifier.size(3.dp).background(Color.White.copy(alpha = 0.3f), CircleShape))
                        Text(
                            text = "মোট ব্যয়: ৳ ${totalExpense.toBangla()}",
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .width(80.dp)
                            .height(6.dp)
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(3.dp))
                            .clip(RoundedCornerShape(3.dp)),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        if (hasData) {
                            Box(modifier = Modifier.weight(gariPercent.coerceAtLeast(0.01f)).fillMaxHeight().background(gariColor))
                            Box(modifier = Modifier.weight(onnanoPercent.coerceAtLeast(0.01f)).fillMaxHeight().background(onnanoColor))
                            Box(modifier = Modifier.weight(nastaPercent.coerceAtLeast(0.01f)).fillMaxHeight().background(nastaColor))
                            Box(modifier = Modifier.weight(bhatPercent.coerceAtLeast(0.01f)).fillMaxHeight().background(bhatColor))
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.1f)))
                        }
                    }
                }
            }
        }
    }
}

// --- DYNAMICALLY RENDERED INTERACTIVE DONUT PIE CHART ---
@Composable
fun ExpenseDonutChart(filteredEntries: List<DailyEntry>) {
    val categoryTotals = remember(filteredEntries) {
        val totals = mutableMapOf<String, Double>()
        for (e in filteredEntries) {
            if (e.expense > 0) {
                totals[e.category] = (totals[e.category] ?: 0.0) + e.expense
            }
        }
        totals.toList().sortedByDescending { it.second }
    }

    val totalExpenseSum = categoryTotals.sumOf { it.second }

    if (totalExpenseSum == 0.0) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "কোনো ক্যাটাগরি খরচ যোগ করা হয়নি।",
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    } else {
        val colors = listOf(
            Color(0xFF06B6D4), // Teal
            Color(0xFFEF4444), // Rose Red
            Color(0xFF10B981), // Green
            Color(0xFFF59E0B), // Amber
            Color(0xFF3B82F6), // Blue
            Color(0xFF818CF8)  // Indigo
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Arc Donut Drawing on Canvas
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val cardBg = MaterialTheme.colorScheme.surface
                Canvas(modifier = Modifier.fillMaxSize()) {
                    var startAngle = -90f
                    categoryTotals.forEachIndexed { index, (cat, amount) ->
                        val sweep = ((amount / totalExpenseSum) * 360f).toFloat()
                        val color = colors[index % colors.size]

                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
                            size = Size(size.width - 20.dp.toPx(), size.height - 20.dp.toPx()),
                            topLeft = Offset(10.dp.toPx(), 10.dp.toPx())
                        )
                        startAngle += sweep
                    }
                }

                // Centered text showing summary inside donut holes
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("মোট ব্যয়", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text("৳ ${totalExpenseSum.toBangla()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Non-scrollable Legends (preventing nested scroll crash)
            Column(
                modifier = Modifier
                    .weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                categoryTotals.take(4).forEachIndexed { idx, (cat, amt) ->
                    val percentage = (amt / totalExpenseSum) * 100f
                    val color = colors[idx % colors.size]

                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$cat: ${percentage.toBangla()}%",
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// --- DASHBOARD ITEMS FOR GRID POLISHING ---
@Composable
fun DashboardItem(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.15f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun DashboardAvgItem(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

// --- BEAUTIFUL CANVAS-BASED DUAL LINE INCOME-EXPENSE CHART ---
@Composable
fun IncomeExpenseTrendChart(filteredEntries: List<DailyEntry>, wageRate: Double, currentKhataMode: String = "MAIN") {
    var weekOffset by remember { mutableStateOf(0) }
    var selectedTrendFilter by remember { mutableStateOf("LAST_7_DAYS") }

    val displayOffsetAndMaxPages = remember(filteredEntries, weekOffset, selectedTrendFilter) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis

        val firstEntryMillis = filteredEntries.map { it.dateMillis }.minOrNull() ?: todayStart
        val calFirst = Calendar.getInstance().apply {
            timeInMillis = firstEntryMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val firstDayStart = calFirst.timeInMillis

        val maxEntryMillis = filteredEntries.map { e -> e.dateMillis }.maxOrNull() ?: todayStart
        val latestDayStart = maxOf(todayStart, maxEntryMillis)

        val diffMillis = latestDayStart - firstDayStart
        val totalDays = (diffMillis / (24L * 60 * 60 * 1000)).toInt() + 1

        when (selectedTrendFilter) {
            "TODAY" -> {
                val targetDayMillis = (latestDayStart - weekOffset * 24L * 60 * 60 * 1000).coerceAtLeast(firstDayStart)
                val displayOffset = ((latestDayStart - targetDayMillis) / (24L * 60 * 60 * 1000)).toInt()
                Pair(displayOffset, totalDays)
            }
            "YESTERDAY" -> {
                val targetDayMillis = (latestDayStart - (1 + weekOffset) * 24L * 60 * 60 * 1000).coerceAtLeast(firstDayStart)
                val displayOffset = ((latestDayStart - targetDayMillis) / (24L * 60 * 60 * 1000)).toInt() - 1
                Pair(displayOffset, totalDays - 1)
            }
            "LAST_7_DAYS" -> {
                val maxPages = (totalDays + 6) / 7
                val targetPage = ((maxPages - 1) - weekOffset).coerceIn(0, maxPages - 1)
                val displayOffset = (maxPages - 1) - targetPage
                Pair(displayOffset, maxPages)
            }
            "LAST_30_DAYS" -> {
                val maxPages = (totalDays + 29) / 30
                val targetPage = ((maxPages - 1) - weekOffset).coerceIn(0, maxPages - 1)
                val displayOffset = (maxPages - 1) - targetPage
                Pair(displayOffset, maxPages)
            }
            else -> Pair(0, 1)
        }
    }
    val displayOffset = displayOffsetAndMaxPages.first
    val maxPages = displayOffsetAndMaxPages.second

    val trendData = remember(filteredEntries, wageRate, weekOffset, selectedTrendFilter, displayOffset, maxPages) {
        val groupedIncome = mutableMapOf<String, Double>()
        val groupedExpense = mutableMapOf<String, Double>()
        val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        
        for (e in filteredEntries) {
            val dateKey = keyFormat.format(Date(e.dateMillis))
            
            val prodIncome = (e.quantity / 100.0) * wageRate
            val entryIncome = if (e.isIncome) e.income else prodIncome
            
            groupedIncome[dateKey] = (groupedIncome[dateKey] ?: 0.0) + entryIncome
            groupedExpense[dateKey] = (groupedExpense[dateKey] ?: 0.0) + e.expense
        }
        
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis

        val firstEntryMillis = filteredEntries.map { it.dateMillis }.minOrNull() ?: todayStart
        val calFirst = Calendar.getInstance().apply {
            timeInMillis = firstEntryMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val firstDayStart = calFirst.timeInMillis

        val maxEntryMillis = filteredEntries.map { e -> e.dateMillis }.maxOrNull() ?: todayStart
        val latestDayStart = maxOf(todayStart, maxEntryMillis)

        val targetDays = when (selectedTrendFilter) {
            "TODAY" -> {
                val targetDayMillis = latestDayStart - displayOffset * 24L * 60 * 60 * 1000
                val dateKey = keyFormat.format(Date(targetDayMillis))
                listOf(Pair(targetDayMillis, dateKey))
            }
            "YESTERDAY" -> {
                val targetDayMillis = latestDayStart - (1 + displayOffset) * 24L * 60 * 60 * 1000
                val dateKey = keyFormat.format(Date(targetDayMillis))
                listOf(Pair(targetDayMillis, dateKey))
            }
            "LAST_7_DAYS" -> {
                val offsetDays = displayOffset * 7
                val endMillis = latestDayStart - (offsetDays * 24L * 60 * 60 * 1000)
                val startMillis = endMillis - (6 * 24L * 60 * 60 * 1000)
                (0..6).map { dayOffset ->
                    val dayCal = Calendar.getInstance()
                    dayCal.timeInMillis = startMillis
                    dayCal.add(Calendar.DAY_OF_YEAR, dayOffset)
                    val dateKey = keyFormat.format(dayCal.time)
                    Pair(dayCal.timeInMillis, dateKey)
                }
            }
            "LAST_30_DAYS" -> {
                val offsetDays = displayOffset * 30
                val endMillis = latestDayStart - (offsetDays * 24L * 60 * 60 * 1000)
                val startMillis = endMillis - (29 * 24L * 60 * 60 * 1000)
                (0..29).map { dayOffset ->
                    val dayCal = Calendar.getInstance()
                    dayCal.timeInMillis = startMillis
                    dayCal.add(Calendar.DAY_OF_YEAR, dayOffset)
                    val dateKey = keyFormat.format(dayCal.time)
                    Pair(dayCal.timeInMillis, dateKey)
                }
            }
            else -> emptyList()
        }
        
        targetDays.map { (dayMillis, dateKey) ->
            val inc = groupedIncome[dateKey] ?: 0.0
            val exp = groupedExpense[dateKey] ?: 0.0
            Triple(dayMillis, inc, exp)
        }
    }

    val maxVal = remember(trendData, currentKhataMode) {
        val maxExpense = trendData.maxOfOrNull { it.third } ?: 0.0
        val max = if (currentKhataMode == "ALT") maxExpense else {
            val maxIncome = trendData.maxOfOrNull { it.second } ?: 0.0
            maxOf(maxIncome, maxExpense)
        }
        if (max == 0.0) {
            500.0
        } else {
            val rawMax = max * 1.15
            if (rawMax <= 50.0) {
                50.0
            } else if (rawMax <= 100.0) {
                100.0
            } else if (rawMax <= 250.0) {
                250.0
            } else if (rawMax <= 500.0) {
                500.0
            } else {
                val step = if (rawMax <= 1000) 200.0 
                           else if (rawMax <= 5000) 1000.0 
                           else if (rawMax <= 10000) 2000.0
                           else 5000.0
                (Math.ceil(rawMax / step) * step).coerceAtLeast(500.0)
            }
        }
    }

    val weekLabel = remember(trendData, selectedTrendFilter) {
        if (trendData.isEmpty()) "" else {
            val firstDay = trendData.first().first
            val sdf = SimpleDateFormat("d MMMM", Locale("bn", "BD"))
            val startStr = sdf.format(Date(firstDay)).toBanglaDigits()
            if (selectedTrendFilter == "TODAY" || selectedTrendFilter == "YESTERDAY" || trendData.size == 1) {
                startStr
            } else {
                val lastDay = trendData.last().first
                val endStr = sdf.format(Date(lastDay)).toBanglaDigits()
                "$startStr - $endStr"
            }
        }
    }

    val dayFormatter = remember { SimpleDateFormat("EEE", Locale("bn", "BD")) }
    val dateFormatter = remember { SimpleDateFormat("d", Locale("bn", "BD")) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF030D26)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFF1D4ED8).copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 1. FILTER CHIP TAB ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val filters = listOf(
                    "TODAY" to "আজ",
                    "YESTERDAY" to "গতকাল",
                    "LAST_7_DAYS" to "গত ৭ দিন",
                    "LAST_30_DAYS" to "গত ৩০ দিন"
                )
                
                filters.forEach { (key, label) ->
                    val isSelected = selectedTrendFilter == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) Color(0xFF3B82F6).copy(alpha = 0.15f)
                                else Color.White.copy(alpha = 0.02f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                selectedTrendFilter = key
                                weekOffset = 0 // Reset navigation offset on filter change
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF60A5FA) else Color(0xFF94A3B8)
                        )
                    }
                }
            }

            // 2. TIMELINE NAVIGATION CONTROLS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { weekOffset++ },
                    enabled = displayOffset < maxPages - 1,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (displayOffset < maxPages - 1) Color(0xFF1E293B).copy(alpha = 0.6f) else Color.Transparent,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = "পূর্ববর্তী",
                        tint = if (displayOffset < maxPages - 1) Color.White else Color.White.copy(alpha = 0.2f)
                    )
                }

                val navigationLabel = when (selectedTrendFilter) {
                    "TODAY" -> {
                        when (displayOffset) {
                            0 -> "আজ"
                            1 -> "গতকাল"
                            else -> "${displayOffset.toBangla()} দিন পূর্বে"
                        }
                    }
                    "YESTERDAY" -> {
                        when (displayOffset) {
                            0 -> "গতকাল"
                            1 -> "২ দিন পূর্বে"
                            else -> "${(displayOffset + 1).toBangla()} দিন পূর্বে"
                        }
                    }
                    "LAST_7_DAYS" -> {
                        when (displayOffset) {
                            0 -> "গত ৭ দিন"
                            1 -> "১ সপ্তাহ পূর্বে"
                            else -> "${displayOffset.toBangla()} সপ্তাহ পূর্বে"
                        }
                    }
                    "LAST_30_DAYS" -> {
                        when (displayOffset) {
                            0 -> "গত ৩০ দিন"
                            1 -> "১ মাস পূর্বে"
                            else -> "${displayOffset.toBangla()} মাস পূর্বে"
                        }
                    }
                    else -> ""
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = weekLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = navigationLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF3B82F6)
                    )
                }

                IconButton(
                    onClick = { if (weekOffset > 0) weekOffset-- },
                    enabled = displayOffset > 0,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (displayOffset > 0) Color(0xFF1E293B).copy(alpha = 0.6f) else Color.Transparent,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = "পরবর্তী",
                        tint = if (displayOffset > 0) Color.White else Color.White.copy(alpha = 0.2f)
                    )
                }
            }

            // 3. PROMINENT LARGE DISPLAY FOR SINGLE DAY VIEWS (TODAY/YESTERDAY)
            if (selectedTrendFilter == "TODAY" || selectedTrendFilter == "YESTERDAY") {
                val dayData = trendData.firstOrNull() ?: Triple(0L, 0.0, 0.0)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentKhataMode != "ALT") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                                .background(Color(0xFF10B981).copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .border(BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.2f)), RoundedCornerShape(12.dp))
                                .padding(vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("মোট আয়", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("৳ ${dayData.second.toInt().toString().toBanglaDigits()}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF10B981))
                        }
                    }
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .background(Color(0xFFEF4444).copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.2f)), RoundedCornerShape(12.dp))
                            .padding(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("মোট ব্যয়", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("৳ ${dayData.third.toInt().toString().toBanglaDigits()}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFFEF4444))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Centered Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentKhataMode != "ALT") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 24.dp, height = 6.dp)
                                .background(Color(0xFF10B981), RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "আয়",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 24.dp, height = 6.dp)
                            .background(Color(0xFFEF4444), RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ব্যয়",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Graph Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                // Y-Axis Labels Column
                Column(
                    modifier = Modifier
                        .width(50.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    val steps = 5
                    for (i in 0..steps) {
                        val labelValue = maxVal * (1.0 - i.toDouble() / steps)
                        val formattedLabel = if (labelValue == 0.0) {
                            "৳০"
                        } else {
                            "৳" + String.format(Locale.US, "%,d", labelValue.toInt()).toBanglaDigits()
                        }
                        Text(
                            text = formattedLabel,
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8).copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Graph Layout with Badge Overlays
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    val containerWidth = maxWidth
                    val containerHeight = 180.dp

                    var activePopupText by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(activePopupText) {
                        if (activePopupText != null) {
                            kotlinx.coroutines.delay(3000)
                            activePopupText = null
                        }
                    }
                    val infiniteTransition = rememberInfiniteTransition(label = "trend_badge_blink")
                    val badgeAlpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "trend_badge_alpha"
                    )

                    // Graph Canvas
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val width = size.width
                        val height = size.height

                        val stepX = if (trendData.size > 1) width / (trendData.size - 1) else width / 2f
                        
                        val incomePoints = trendData.mapIndexed { index, triple ->
                            val x = if (trendData.size > 1) index * stepX else width / 2f
                            val y = height - ((triple.second / maxVal) * height).toFloat()
                            Offset(x, y)
                        }

                        val expensePoints = trendData.mapIndexed { index, triple ->
                            val x = if (trendData.size > 1) index * stepX else width / 2f
                            val y = height - ((triple.third / maxVal) * height).toFloat()
                            Offset(x, y)
                        }

                        // Background Stars/Sparkles (Cosmic Slate style)
                        val stars = listOf(
                            Offset(width * 0.15f, height * 0.25f),
                            Offset(width * 0.35f, height * 0.45f),
                            Offset(width * 0.45f, height * 0.15f),
                            Offset(width * 0.65f, height * 0.35f),
                            Offset(width * 0.85f, height * 0.22f)
                        )
                        stars.forEach { star ->
                            drawCircle(Color(0xFF3B82F6).copy(alpha = 0.25f), radius = 5.dp.toPx(), center = star)
                            drawCircle(Color.White, radius = 1.dp.toPx(), center = star)
                        }

                        // Draw beautiful axes: left vertical axis & bottom horizontal axis
                        val axisColor = Color(0xFF1D4ED8).copy(alpha = 0.6f)
                        val axisStrokeWidth = 1.5.dp.toPx()
                        drawLine(
                            color = axisColor,
                            start = Offset(0f, 0f),
                            end = Offset(0f, height),
                            strokeWidth = axisStrokeWidth
                        )
                        drawLine(
                            color = axisColor,
                            start = Offset(0f, height),
                            end = Offset(width, height),
                            strokeWidth = axisStrokeWidth
                        )

                        // Draw vertical day separator grids to make a beautiful technical mesh
                        for (index in trendData.indices) {
                            val x = if (trendData.size > 1) index * stepX else width / 2f
                            val isToday = index == trendData.size - 1 && weekOffset == 0
                            drawLine(
                                color = if (isToday) Color(0xFF3B82F6).copy(alpha = 0.2f) else Color(0xFF334155).copy(alpha = 0.1f),
                                start = Offset(x, 0f),
                                end = Offset(x, height),
                                strokeWidth = if (isToday) 1.5.dp.toPx() else 1.dp.toPx()
                            )
                        }

                        // Draw horizontal grid lines
                        val gridLines = 5
                        for (i in 0..gridLines) {
                            val y = (height / gridLines) * i
                            drawLine(
                                color = Color(0xFF334155).copy(alpha = 0.12f),
                                start = Offset(0f, y),
                                end = Offset(width, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        // Draw Income Area Gradient Shadow (Green) with smooth bezier interpolation
                        if (currentKhataMode != "ALT") {
                            val incomeFillPath = Path().apply {
                                if (incomePoints.isNotEmpty()) {
                                    moveTo(incomePoints.first().x, height)
                                    lineTo(incomePoints.first().x, incomePoints.first().y)
                                    if (incomePoints.size > 1) {
                                        for (i in 0 until incomePoints.size - 1) {
                                            val p1 = incomePoints[i]
                                            val p2 = incomePoints[i + 1]
                                            val cp1 = Offset(p1.x + (p2.x - p1.x) / 2f, p1.y)
                                            val cp2 = Offset(p1.x + (p2.x - p1.x) / 2f, p2.y)
                                            cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
                                        }
                                    }
                                    lineTo(incomePoints.last().x, height)
                                    close()
                                }
                            }
                            drawPath(
                                path = incomeFillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFF10B981).copy(alpha = 0.22f), Color(0xFF10B981).copy(alpha = 0.04f), Color.Transparent),
                                    startY = 0f,
                                    endY = height
                                )
                            )
                        }

                        // Draw Expense Area Gradient Shadow (Red) with smooth bezier interpolation
                        val expenseFillPath = Path().apply {
                            if (expensePoints.isNotEmpty()) {
                                moveTo(expensePoints.first().x, height)
                                lineTo(expensePoints.first().x, expensePoints.first().y)
                                if (expensePoints.size > 1) {
                                    for (i in 0 until expensePoints.size - 1) {
                                        val p1 = expensePoints[i]
                                        val p2 = expensePoints[i + 1]
                                        val cp1 = Offset(p1.x + (p2.x - p1.x) / 2f, p1.y)
                                        val cp2 = Offset(p1.x + (p2.x - p1.x) / 2f, p2.y)
                                        cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
                                    }
                                }
                                lineTo(expensePoints.last().x, height)
                                close()
                            }
                        }
                        drawPath(
                            path = expenseFillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFFEF4444).copy(alpha = 0.18f), Color(0xFFEF4444).copy(alpha = 0.03f), Color.Transparent),
                                startY = 0f,
                                endY = height
                            )
                        )

                        val strokeWidth = if (trendData.size == 30) 1.2.dp.toPx() else 1.8.dp.toPx()

                        // Draw Income Smooth Curve (Green) with dual glowing halos
                        if (currentKhataMode != "ALT") {
                            val incomePath = Path().apply {
                                if (incomePoints.isNotEmpty()) {
                                    moveTo(incomePoints.first().x, incomePoints.first().y)
                                    if (incomePoints.size > 1) {
                                        for (i in 0 until incomePoints.size - 1) {
                                            val p1 = incomePoints[i]
                                            val p2 = incomePoints[i + 1]
                                            val cp1 = Offset(p1.x + (p2.x - p1.x) / 2f, p1.y)
                                            val cp2 = Offset(p1.x + (p2.x - p1.x) / 2f, p2.y)
                                            cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
                                        }
                                    }
                                }
                            }
                            // Glow layer 1
                            drawPath(
                                path = incomePath,
                                color = Color(0xFF10B981).copy(alpha = 0.12f),
                                style = Stroke(width = strokeWidth * 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                            // Glow layer 2
                            drawPath(
                                path = incomePath,
                                color = Color(0xFF10B981).copy(alpha = 0.24f),
                                style = Stroke(width = strokeWidth * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                            // Sharp path
                            drawPath(
                                path = incomePath,
                                color = Color(0xFF10B981),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }

                        // Draw Expense Smooth Curve (Red) with dual glowing halos
                        val expensePath = Path().apply {
                            if (expensePoints.isNotEmpty()) {
                                moveTo(expensePoints.first().x, expensePoints.first().y)
                                if (expensePoints.size > 1) {
                                    for (i in 0 until expensePoints.size - 1) {
                                        val p1 = expensePoints[i]
                                        val p2 = expensePoints[i + 1]
                                        val cp1 = Offset(p1.x + (p2.x - p1.x) / 2f, p1.y)
                                        val cp2 = Offset(p1.x + (p2.x - p1.x) / 2f, p2.y)
                                        cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
                                    }
                                }
                            }
                        }
                        // Glow layer 1
                        drawPath(
                            path = expensePath,
                            color = Color(0xFFEF4444).copy(alpha = 0.12f),
                            style = Stroke(width = strokeWidth * 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        // Glow layer 2
                        drawPath(
                            path = expensePath,
                            color = Color(0xFFEF4444).copy(alpha = 0.24f),
                            style = Stroke(width = strokeWidth * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        // Sharp path
                        drawPath(
                            path = expensePath,
                            color = Color(0xFFEF4444),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )

                        // Draw glowing dots on data points with professional double halos
                        if (currentKhataMode != "ALT") {
                            incomePoints.forEachIndexed { idx, p ->
                                val isToday = idx == incomePoints.size - 1 && weekOffset == 0
                                val dotRadius = if (trendData.size == 30) 2.dp else if (isToday) 6.dp else 4.dp
                                val innerRadius = if (trendData.size == 30) 1.dp else if (isToday) 3.5.dp else 2.5.dp
                                drawCircle(
                                    color = Color(0xFF10B981).copy(alpha = 0.22f),
                                    radius = dotRadius.toPx(),
                                    center = p
                                )
                                drawCircle(
                                    color = Color(0xFF10B981),
                                    radius = innerRadius.toPx(),
                                    center = p
                                )
                            }
                        }

                        expensePoints.forEachIndexed { idx, p ->
                            val isToday = idx == expensePoints.size - 1 && weekOffset == 0
                            val dotRadius = if (trendData.size == 30) 2.dp else if (isToday) 6.dp else 4.dp
                            val innerRadius = if (trendData.size == 30) 1.dp else if (isToday) 3.5.dp else 2.5.dp
                            drawCircle(
                                color = Color(0xFFEF4444).copy(alpha = 0.22f),
                                radius = dotRadius.toPx(),
                                center = p
                            )
                            drawCircle(
                                color = Color(0xFFEF4444),
                                radius = innerRadius.toPx(),
                                center = p
                            )
                        }
                    }

                    // Floating Glowing Badges for Peaks exactly like reference image
                    val canvasWidth = containerWidth
                    if (trendData.isNotEmpty() && canvasWidth > 0.dp) {
                        val stepXDp = if (trendData.size > 1) canvasWidth / (trendData.size - 1) else canvasWidth / 2f

                        val maxIncomeVal = trendData.maxOfOrNull { it.second } ?: 0.0
                        val maxIncomeIndex = trendData.indexOfFirst { it.second == maxIncomeVal }

                        val maxExpenseVal = trendData.maxOfOrNull { it.third } ?: 0.0
                        val maxExpenseIndex = trendData.indexOfFirst { it.third == maxExpenseVal }

                        // Green money bag above the Income peak
                        if (currentKhataMode != "ALT" && maxIncomeVal > 0.0 && maxIncomeIndex >= 0) {
                            val xDp = stepXDp * maxIncomeIndex
                            val yPercent = (maxIncomeVal / maxVal).toFloat().coerceIn(0f, 1f)
                            val yDp = 180.dp - (180 * yPercent).dp

                            Box(
                                modifier = Modifier
                                    .offset(x = xDp - 12.dp, y = yDp - 26.dp)
                                    .size(24.dp)
                                    .alpha(badgeAlpha)
                                    .background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape)
                                    .border(BorderStroke(1.dp, Color(0xFF10B981)), CircleShape)
                                    .clickable {
                                        val dayCal = Calendar.getInstance().apply { timeInMillis = trendData[maxIncomeIndex].first }
                                        val dateStr = SimpleDateFormat("d MMMM", Locale("bn", "BD")).format(dayCal.time).toBanglaDigits()
                                        activePopupText = "$dateStr এর মোট আয়: ৳ ${maxIncomeVal.toInt().toString().toBanglaDigits()}"
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Paid,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // Green money bag above another non-zero income point
                        if (currentKhataMode != "ALT") {
                            val otherIncomeIndex = trendData.indexOfFirst { it.second > 0.0 && it.second != maxIncomeVal }
                            if (otherIncomeIndex >= 0) {
                                val otherVal = trendData[otherIncomeIndex].second
                                val xDp = stepXDp * otherIncomeIndex
                                val yPercent = (otherVal / maxVal).toFloat().coerceIn(0f, 1f)
                                val yDp = 180.dp - (180 * yPercent).dp

                                Box(
                                    modifier = Modifier
                                        .offset(x = xDp - 12.dp, y = yDp - 26.dp)
                                        .size(24.dp)
                                        .alpha(badgeAlpha)
                                        .background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape)
                                        .border(BorderStroke(1.dp, Color(0xFF10B981)), CircleShape)
                                        .clickable {
                                            val dayCal = Calendar.getInstance().apply { timeInMillis = trendData[otherIncomeIndex].first }
                                            val dateStr = SimpleDateFormat("d MMMM", Locale("bn", "BD")).format(dayCal.time).toBanglaDigits()
                                            activePopupText = "$dateStr এর মোট আয়: ৳ ${otherVal.toInt().toString().toBanglaDigits()}"
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Paid,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        // Red coin stack above the Expense peak
                        if (maxExpenseVal > 0.0 && maxExpenseIndex >= 0) {
                            val xDp = stepXDp * maxExpenseIndex
                            val yPercent = (maxExpenseVal / maxVal).toFloat().coerceIn(0f, 1f)
                            val yDp = 180.dp - (180 * yPercent).dp

                            Box(
                                modifier = Modifier
                                    .offset(x = xDp - 12.dp, y = yDp - 26.dp)
                                    .size(24.dp)
                                    .alpha(badgeAlpha)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.2f), CircleShape)
                                    .border(BorderStroke(1.dp, Color(0xFFEF4444)), CircleShape)
                                    .clickable {
                                        val dayCal = Calendar.getInstance().apply { timeInMillis = trendData[maxExpenseIndex].first }
                                        val dateStr = SimpleDateFormat("d MMMM", Locale("bn", "BD")).format(dayCal.time).toBanglaDigits()
                                        activePopupText = "$dateStr এর মোট ব্যয়: ৳ ${maxExpenseVal.toInt().toString().toBanglaDigits()}"
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Paid,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = activePopupText != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                    ) {
                        activePopupText?.let { text ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.95f)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFF60A5FA),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = text,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Day Labels (Horizontal timeline) perfectly aligned with the dots!
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Left spacer offsets the labels perfectly to match the Canvas area
                Spacer(modifier = Modifier.width(60.dp))

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    trendData.forEachIndexed { index, triple ->
                        val showLabel = when (trendData.size) {
                            30 -> index % 5 == 0 || index == 29
                            else -> true
                        }
                        if (showLabel) {
                            val dayStr = dayFormatter.format(Date(triple.first))
                            val dateStr = dateFormatter.format(Date(triple.first)).toBanglaDigits()
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = dayStr,
                                    fontSize = if (trendData.size == 30) 8.sp else 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF94A3B8).copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = dateStr,
                                    fontSize = if (trendData.size == 30) 9.sp else 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        } else {
                            Box(modifier = Modifier.width(1.dp))
                        }
                    }
                }
            }
        }
    }
}

// --- PREMIUM CUSTOM DESIGN COMPONENTS MATCHING SCREENSHOT ---

@Composable
fun AltStatItem(
    title: String,
    value: String,
    iconColor: Color,
    bgColor: Color,
    showWarning: Boolean = false,
    blinkIcon: Boolean = false,
    modifier: Modifier = Modifier
) {
    val icon = when {
        showWarning -> Icons.Default.Warning
        title.contains("মোট") -> Icons.Default.BarChart
        title.contains("গড়") -> Icons.Default.Info
        else -> Icons.Default.Star
    }

    val iconAlpha = if (blinkIcon) {
        val infiniteTransition = rememberInfiniteTransition(label = "iconBlink")
        val alphaVal by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "iconBlinkAlpha"
        )
        alphaVal
    } else {
        1.0f
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF132247).copy(alpha = 0.6f),
        border = BorderStroke(1.dp, if (showWarning) Color(0xFFEF4444).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(bgColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor.copy(alpha = iconAlpha),
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (showWarning) Color(0xFFEF4444) else Color.White
                )
            }
        }
    }
}

@Composable
fun GridAccountItem(
    title: String,
    value: String,
    icon: ImageVector,
    iconBgColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White
) {
    val glowColor = iconColor.copy(alpha = 0.08f)
    Surface(
        modifier = modifier
            .height(68.dp)
            .drawBehind {
                // Drop shadow
                drawRoundRect(
                    color = Color(0xFF020617).copy(alpha = 0.3f),
                    topLeft = Offset(0f, 2f),
                    size = size,
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                )
                // Soft glow
                drawRoundRect(
                    color = glowColor,
                    topLeft = Offset(-2f, -2f),
                    size = Size(size.width + 4f, size.height + 4f),
                    cornerRadius = CornerRadius(17.dp.toPx(), 17.dp.toPx())
                )
            },
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E293B).copy(alpha = 0.55f), // Sleek semi-translucent slate card
        border = BorderStroke(
            1.dp,
            Brush.verticalGradient(
                colors = listOf(
                    iconColor.copy(alpha = 0.3f), // Bright highlight top
                    Color.Transparent             // Fading to bottom
                )
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Glowing 3D icon box
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .drawBehind {
                        drawRoundRect(
                            color = iconColor.copy(alpha = 0.15f),
                            topLeft = Offset(-2f, -2f),
                            size = Size(size.width + 4f, size.height + 4f),
                            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                        )
                    }
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(iconBgColor.copy(alpha = 0.9f), iconBgColor.copy(alpha = 0.6f))
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        1.dp,
                        iconColor.copy(alpha = 0.4f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = valueColor
                )
            }
        }
    }
}

@Composable
fun DashboardAvgPillItem(
    title: String,
    value: String,
    valueColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(54.dp)
            .drawBehind {
                // Glow using the value text color
                drawRoundRect(
                    color = valueColor.copy(alpha = 0.05f),
                    topLeft = Offset(-1f, -1f),
                    size = Size(size.width + 2f, size.height + 2f),
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                )
                // 3D Bevel Bottom shadow
                drawRoundRect(
                    color = Color(0xFF020617).copy(alpha = 0.3f),
                    topLeft = Offset(0f, 2f),
                    size = size,
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                )
            },
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.55f), // Deep slate rounded pill
        border = BorderStroke(
            0.8.dp,
            Brush.verticalGradient(
                colors = listOf(
                    valueColor.copy(alpha = 0.2f),
                    Color.Transparent
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }
    }
}

@Composable
fun CustomFolderPill(
    label: String,
    isSelected: Boolean,
    leadingIcon: ImageVector,
    showDelete: Boolean = false,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0xFF1E3A8A).copy(alpha = 0.8f) else Color.Transparent,
        border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = Modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = if (isSelected) Color(0xFF60A5FA) else Color(0xFF94A3B8),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) Color.White else Color(0xFFCBD5E1)
            )
            if (showDelete) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "মুছুন",
                    tint = if (isSelected) Color.White.copy(alpha = 0.7f) else Color(0xFFEF4444).copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(13.dp)
                        .clickable { onDeleteClick() }
                )
            }
        }
    }
}

// --- CLEAN COMPACT ROW-BY-ROW HISTORIES ---
@Composable
fun HistoryListItemRow(
    entry: DailyEntry,
    wageRate: Double,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    cardBgColor: Color,
    isGlass: Boolean,
    cardBorderColor: Color
) {
    val formatter = remember { SimpleDateFormat("dd MMMM yyyy", Locale("bn", "BD")) }
    val displayDate = formatter.format(Date(entry.dateMillis))

    // Computations
    val productionIncome = (entry.quantity / 100.0) * wageRate
    val calculatedIncome = if (entry.isIncome) entry.income else productionIncome
    val netBalance = calculatedIncome - entry.expense

    // Delete safety confirmation
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("ডিলিট করার নিশ্চিতকরণ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Red) },
            text = { Text("আপনি কি সত্যিই এই হিসাবের রেকর্ডটি মুছে ফেলতে চান?", fontSize = 12.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("হ্যাঁ, মুছুন", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("বাতিল")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onEdit() },
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        shape = RoundedCornerShape(16.dp),
        border = if (isGlass) BorderStroke(1.dp, cardBorderColor) else BorderStroke(1.dp, Color.White.copy(alpha = 0.03f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Row 1: Date, Badge, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = displayDate,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    // Compact Status Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (netBalance >= 0) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (netBalance >= 0) Color(0xFF10B981).copy(alpha = 0.3f) else Color(0xFFEF4444).copy(alpha = 0.3f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (netBalance >= 0) "✓ লাভ" else "✗ লোকসান",
                                color = if (netBalance >= 0) Color(0xFF34D399) else Color(0xFFF87171),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Delete Button
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "মুছুন",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Stats in bangla annotated string
            val annotatedStats = remember(entry.quantity, calculatedIncome, entry.expense) {
                androidx.compose.ui.text.buildAnnotatedString {
                    append("মাল: ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                        append("${entry.quantity.toBangla()} পিস")
                    }
                    append("   •   আয়: ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF10B981))) {
                        append("৳ ${calculatedIncome.toBangla()}")
                    }
                    append("   •   খরচ: ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))) {
                        append("৳ ${entry.expense.toBangla()}")
                    }
                }
            }

            Text(
                text = annotatedStats,
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )

            if (entry.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "মন্তব্য: ${entry.note}",
                    fontSize = 10.sp,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF94A3B8).copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// --- DYNAMIC INPUT DIALOG FLOW (WITH LIVE TARGET TRACKING!) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditEntryDialog(
    entryToEdit: DailyEntry?,
    folders: List<String>,
    categories: List<String>,
    wageRate: Double,
    dailyTarget: Int,
    onDismiss: () -> Unit,
    onSave: (qty: Int, expense: Double, income: Double, category: String, isIncome: Boolean, note: String, folder: String, date: Long) -> Unit,
    defaultFolder: String = "সাধারণ হিসাব",
    isAltMode: Boolean = false
) {
    val context = LocalContext.current

    // Fields states
    var inputQty by remember { mutableStateOf(entryToEdit?.quantity?.let { if (it > 0) it.toString() else "" } ?: "") }
    var inputExpense by remember { mutableStateOf(entryToEdit?.expense?.let { if (it > 0.0) it.toInt().toString() else "" } ?: "") }
    var inputNote by remember { mutableStateOf(entryToEdit?.note ?: "") }
    var selectedFolder by remember {
        mutableStateOf(
            entryToEdit?.folderName ?: if (defaultFolder == "সব ফোল্ডার") {
                folders.firstOrNull() ?: "সাধারণ হিসাব"
            } else {
                defaultFolder
            }
        )
    }
    var selectedDateMillis by remember { mutableStateOf(entryToEdit?.dateMillis ?: System.currentTimeMillis()) }

    var showFolderDropdown by remember { mutableStateOf(false) }

    val displaySdf = remember { SimpleDateFormat("MM/dd/yyyy", Locale.US) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1527)),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // TITLE ROW
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isAltMode) {
                            if (entryToEdit == null) "নতুন খরচ যুক্ত করুন" else "খরচ সংশোধন করুন"
                        } else {
                            if (entryToEdit == null) "নতুন দৈনিক হিসাব যুক্ত করুন" else "দৈনিক হিসাব সংশোধন করুন"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "বন্ধ করুন",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.08f))

                // 1. DATE SELECTOR
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "তারিখ নির্বাচন করুন",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    
                    // Clickable Date Box
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(Color(0xFF131C33), RoundedCornerShape(14.dp))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(14.dp))
                            .clickable {
                                val calendar = Calendar.getInstance()
                                calendar.timeInMillis = selectedDateMillis
                                val datePickerDialog = DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val selectedCal = Calendar.getInstance()
                                        selectedCal.set(year, month, dayOfMonth)
                                        selectedDateMillis = selectedCal.timeInMillis
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                )
                                datePickerDialog.show()
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = displaySdf.format(Date(selectedDateMillis)),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Quick buttons: "আজ" and "গতকাল"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // "আজ" Button
                        Button(
                            onClick = {
                                selectedDateMillis = System.currentTimeMillis()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("আজ", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // "গতকাল" Button
                        Button(
                            onClick = {
                                selectedDateMillis = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("গতকাল", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 2. QTY INPUT ("মালের পরিমাণ (পিস)")
                if (!isAltMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "মালের পরিমাণ (পিস)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                        val enteredQty = inputQty.toIntOrNull() ?: 0
                        val (targetStatusText, targetColor) = when {
                            enteredQty >= dailyTarget -> Pair("আজকের লক্ষ্য পূরণ হয়েছে!", Color(0xFF10B981)) // Green
                            enteredQty >= dailyTarget / 2 -> Pair("আজকের লক্ষ্য পূরণ হতে ${(dailyTarget - enteredQty).toBangla()} পিস বাকি", Color(0xFFFBBF24)) // Yellow
                            else -> Pair("আজকের লক্ষ্য পূরণ হতে ${(dailyTarget - enteredQty).toBangla()} পিস বাকি", Color(0xFFFF5252)) // Red
                        }
                        OutlinedTextField(
                            value = inputQty,
                            onValueChange = { inputQty = it },
                            placeholder = { Text("টার্গেট: ${dailyTarget.toBangla()} পিস (যেমন: ৮৫০)", color = Color(0xFF94A3B8).copy(alpha = 0.5f)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = targetColor,
                                unfocusedBorderColor = targetColor.copy(alpha = 0.3f),
                                focusedContainerColor = Color(0xFF131C33),
                                unfocusedContainerColor = Color(0xFF131C33),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("qty_input")
                        )

                        Text(
                            text = targetStatusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = targetColor,
                            modifier = Modifier.padding(start = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // Quick select chips under Qty: +100, +500, +800, +1000
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(100, 500, 800, 1000).forEach { increment ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .background(Color(0xFF1E293B).copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                                        .border(BorderStroke(1.dp, Color(0xFF1E3A8A).copy(alpha = 0.5f)), RoundedCornerShape(18.dp))
                                        .clickable {
                                            val current = inputQty.toIntOrNull() ?: 0
                                            inputQty = (current + increment).toString()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+ ${increment.toBangla()}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF60A5FA)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. EXPENSE INPUT ("দৈনিক খরচ (টাকা)")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "দৈনিক খরচ (টাকা)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    OutlinedTextField(
                        value = inputExpense,
                        onValueChange = { inputExpense = it },
                        placeholder = { Text("যেমন: ৬৫০", color = Color(0xFF94A3B8).copy(alpha = 0.5f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.05f),
                            focusedContainerColor = Color(0xFF131C33),
                            unfocusedContainerColor = Color(0xFF131C33),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("expense_amount_input")
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Quick select chips under Expense: +50, +100, +200, +500
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(50, 100, 200, 500).forEach { increment ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .background(Color(0xFF1E293B).copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                                    .border(BorderStroke(1.dp, Color(0xFF1E3A8A).copy(alpha = 0.5f)), RoundedCornerShape(18.dp))
                                    .clickable {
                                        val current = inputExpense.toIntOrNull() ?: 0
                                        inputExpense = (current + increment).toString()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+ ${increment.toBangla()}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF60A5FA)
                                )
                            }
                        }
                    }
                }

                // 4. FOLDER SELECTOR
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "হিসাব খাতা বা ফোল্ডার",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .background(Color(0xFF131C33), RoundedCornerShape(14.dp))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(14.dp))
                                .clickable {
                                    showFolderDropdown = true
                                }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = selectedFolder,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showFolderDropdown,
                            onDismissRequest = { showFolderDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            folders.forEach { folderName ->
                                DropdownMenuItem(
                                    text = { Text(folderName) },
                                    onClick = {
                                        selectedFolder = folderName
                                        showFolderDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 5. NOTE INPUT
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "মন্তব্য বা নোট (ঐচ্ছিক)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    OutlinedTextField(
                        value = inputNote,
                        onValueChange = { inputNote = it },
                        placeholder = { Text("যেমন: অগ্রিম নিয়েছি", color = Color(0xFF94A3B8).copy(alpha = 0.5f)) },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.05f),
                            focusedContainerColor = Color(0xFF131C33),
                            unfocusedContainerColor = Color(0xFF131C33),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("note_input")
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // SAVE & CLOSE ROW BUTTONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // "বাতিল" Button
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("বাতিল", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    // "সংরক্ষণ করুন" Button
                    Button(
                        onClick = {
                            val qty = inputQty.toIntOrNull() ?: 0
                            val expense = inputExpense.toDoubleOrNull() ?: 0.0
                            val income = 0.0
                            val category = entryToEdit?.category ?: "অন্যান্য"
                            val isIncome = false

                            onSave(qty, expense, income, category, isIncome, inputNote, selectedFolder, selectedDateMillis)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3B82F6),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = if (entryToEdit == null) "সংরক্ষণ করুন" else "সংরক্ষণ করুন",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// --- DYNAMIC SETTINGS DIALOG (TARGETS, THEMES, DATABASE RESET, BACKUPS) ---
@Composable
fun SettingsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()
    val currentKhataMode by viewModel.currentKhataMode.collectAsStateWithLifecycle()
    val folders by viewModel.foldersList.collectAsStateWithLifecycle()
    val categories by viewModel.customCategoriesList.collectAsStateWithLifecycle()

    val wageRate by viewModel.wageRate.collectAsStateWithLifecycle()
    val dailyTarget by viewModel.dailyTarget.collectAsStateWithLifecycle()
    val dailyBudget by viewModel.dailyBudget.collectAsStateWithLifecycle()
    val monthlyBudget by viewModel.monthlyBudget.collectAsStateWithLifecycle()

    var inputRate by remember { mutableStateOf(wageRate.toString()) }
    var inputTarget by remember { mutableStateOf(dailyTarget.toString()) }
    var inputBudget by remember { mutableStateOf(dailyBudget.toString()) }
    var inputMonthlyBudget by remember { mutableStateOf(monthlyBudget.toString()) }

    var backupStringInput by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showOtherSettings by remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val content = viewModel.exportBackup()
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                }
                Toast.makeText(context, "ব্যাকআপ সফলভাবে ফাইলে সংরক্ষণ করা হয়েছে!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "ব্যাকআপ ফাইল তৈরিতে ব্যর্থতা: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/comma-separated-values")
    ) { uri ->
        if (uri != null) {
            try {
                val content = viewModel.exportBackupCsv()
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                }
                Toast.makeText(context, "এক্সেল হিসাব সফলভাবে CSV ফাইলে সংরক্ষণ করা হয়েছে!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "CSV ফাইল তৈরিতে ব্যর্থতা: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    var restoreFileUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showRestoreOptionsDialog by remember { mutableStateOf(false) }
    var restoreIsCsv by remember { mutableStateOf(false) }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            restoreFileUri = uri
            restoreIsCsv = uri.toString().endsWith(".csv", ignoreCase = true) || 
                           context.contentResolver.getType(uri)?.contains("csv", ignoreCase = true) == true
            showRestoreOptionsDialog = true
        }
    }

    var driveStatusText by remember { mutableStateOf("গুগল ড্রাইভে ডাটা ব্যাকআপ রাখতে লগইন করুন") }
    var isDriveSyncing by remember { mutableStateOf(false) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                driveStatusText = "গুগল ড্রাইভ কানেক্টেড: ${account.email}"
                Toast.makeText(context, "গুগল সাইন-ইন সফল হয়েছে!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            val msg = when (e.statusCode) {
                10 -> "ডেভেলপার কী বা SHA-1 ফিঙ্গারপ্রিন্ট গুগল ক্লাউড কনসোলে রেজিস্টার করা নেই (Error 10)।"
                else -> "গুগল সাইন-ইন ব্যর্থ হয়েছে: ${e.message}"
            }
            driveStatusText = "লগইন ব্যর্থতা: ${e.statusCode}"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1527)),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // TITLE ROW
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "সেটিংস ও ব্যাকআপ",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "বন্ধ করুন",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.08f))

                // ০. KHATA MODE SELECTOR REMOVED
                Spacer(modifier = Modifier.height(1.dp))

                Divider(color = Color.White.copy(alpha = 0.08f))

                // ১. DAILY WORK TARGET INPUT REMOVED
                Spacer(modifier = Modifier.height(1.dp))

                // ২. VISUAL THEMES SELECTOR
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "অ্যাকটিভিটি থিম বা দৃশ্যপট",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val themeOptions = listOf(
                            Triple("DEFAULT", "গাঢ় নীল", Color(0xFF1E3A8A)),
                            Triple("CARBON", "কয়লা কালো", Color(0xFF1E293B)),
                            Triple("GLASS", "উজ্জ্বল কাঁচ", Color.White)
                        )

                        themeOptions.forEach { (themeId, label, color) ->
                            val isSelected = currentTheme == themeId
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(90.dp)
                                    .background(Color(0xFF131C33), RoundedCornerShape(18.dp))
                                    .border(
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.05f)
                                        ),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .clickable { viewModel.changeTheme(themeId) }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Circle dot
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                border = BorderStroke(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) Color(0xFF3B82F6) else Color(0xFF94A3B8).copy(alpha = 0.5f)
                                                ),
                                                shape = CircleShape
                                            )
                                    )
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color(0xFF60A5FA) else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }
                }

                // ৩. BACKUP & RESTORE PANEL REMOVED
                Spacer(modifier = Modifier.height(1.dp))

                    // ৩.১ লোকাল ফাইল ব্যাকআপ (অফলাইন)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "লোকাল ফাইল ব্যাকআপ (অফলাইন)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF60A5FA)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                                    val fileName = "SmartManager_Backup_${sdf.format(Date())}.json"
                                    createDocumentLauncher.launch(fileName)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E293B),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = null,
                                        tint = Color(0xFF3B82F6),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "ব্যাকআপ সেভ",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                                    val fileName = "SmartManager_Excel_${sdf.format(Date())}.csv"
                                    createCsvLauncher.launch(fileName)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E293B),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GridOn,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "এক্সেল ফাইল",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                openDocumentLauncher.launch(arrayOf("application/json", "text/plain", "text/comma-separated-values", "*/*"))
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E293B),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "ফাইল থেকে রিস্টোর করুন",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Divider(color = Color.White.copy(alpha = 0.08f))

                    // ৩.২ গুগল ড্রাইভ ক্লাউড ব্যাকআপ (অনলাইন)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "ক্লাউড সিঙ্ক (গুগল ড্রাইভ)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFBBF24)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    isDriveSyncing = true
                                    driveStatusText = "গুগল ড্রাইভের সাথে সিঙ্ক করা হচ্ছে..."
                                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                        .requestEmail()
                                        .requestScopes(Scope("https://www.googleapis.com/auth/drive.appdata"))
                                        .build()
                                    val googleSignInClient = GoogleSignIn.getClient(context, gso)
                                    val account = GoogleSignIn.getLastSignedInAccount(context)
                                    if (account != null) {
                                        val backupContent = viewModel.exportBackup()
                                        viewModel.uploadToGoogleDrive(context, account, backupContent) { success, msg ->
                                            isDriveSyncing = false
                                            driveStatusText = msg
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        // Start sign-in flow
                                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E1B4B),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.3f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = Color(0xFF818CF8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "ড্রাইভ ব্যাকআপ",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    isDriveSyncing = true
                                    driveStatusText = "গুগল ড্রাইভ থেকে ব্যাকআপ খোঁজা হচ্ছে..."
                                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                        .requestEmail()
                                        .requestScopes(Scope("https://www.googleapis.com/auth/drive.appdata"))
                                        .build()
                                    val googleSignInClient = GoogleSignIn.getClient(context, gso)
                                    val account = GoogleSignIn.getLastSignedInAccount(context)
                                    if (account != null) {
                                        viewModel.restoreFromGoogleDrive(context, account) { success, msg, content ->
                                            isDriveSyncing = false
                                            driveStatusText = msg
                                            if (success && content != null) {
                                                val importSuccess = viewModel.importBackup(content, overwrite = true)
                                                if (importSuccess) {
                                                    Toast.makeText(context, "গুগল ড্রাইভ থেকে রিস্টোর সফল হয়েছে!", Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(context, "রিস্টোর করা ডাটা প্রসেস করতে ব্যর্থতা!", Toast.LENGTH_LONG).show()
                                                }
                                            } else {
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } else {
                                        googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E1B4B),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.3f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "ড্রাইভ রিস্টোর",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Text(
                            text = driveStatusText,
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                // Interactive Local Restore Options Dialog
                if (showRestoreOptionsDialog && restoreFileUri != null) {
                    Dialog(onDismissRequest = { showRestoreOptionsDialog = false }) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1527)),
                            border = BorderStroke(1.dp, Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "তথ্য পুনরুদ্ধারের ধরণ নির্বাচন করুন",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "আপনি কি আগের তথ্যের সাথে নতুন ডাটা যুক্ত করতে চান নাকি আগের সব ডাটা মুছে ফেলতে চান?",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8)
                                )

                                Button(
                                    onClick = {
                                        val uri = restoreFileUri!!
                                        try {
                                            val inputStream = context.contentResolver.openInputStream(uri)
                                            val content = inputStream?.bufferedReader()?.use { it.readText() }
                                            if (content != null) {
                                                val success = if (restoreIsCsv) {
                                                    viewModel.importBackupCsv(content, overwrite = false)
                                                } else {
                                                    viewModel.importBackup(content, overwrite = false)
                                                }
                                                if (success) {
                                                    Toast.makeText(context, "তথ্য সফলভাবে ডুপ্লিকেট ছাড়া যুক্ত করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "ফাইল ইম্পোর্ট করতে ব্যর্থতা! ফাইলটি চেক করুন।", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "ত্রুটি: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                        showRestoreOptionsDialog = false
                                        restoreFileUri = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("তথ্য যুক্ত করুন (ডুপ্লিকেট ছাড়া)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                }

                                Button(
                                    onClick = {
                                        val uri = restoreFileUri!!
                                        try {
                                            val inputStream = context.contentResolver.openInputStream(uri)
                                            val content = inputStream?.bufferedReader()?.use { it.readText() }
                                            if (content != null) {
                                                val success = if (restoreIsCsv) {
                                                    viewModel.importBackupCsv(content, overwrite = true)
                                                } else {
                                                    viewModel.importBackup(content, overwrite = true)
                                                }
                                                if (success) {
                                                    Toast.makeText(context, "পূর্বের সব মুছে সফলভাবে রিস্টোর করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "ফাইল ইম্পোর্ট করতে ব্যর্থতা! ফাইলটি চেক করুন।", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "ত্রুটি: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                        showRestoreOptionsDialog = false
                                        restoreFileUri = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("সব মুছে নতুন করে রিস্টোর করুন", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                }

                                TextButton(
                                    onClick = {
                                        showRestoreOptionsDialog = false
                                        restoreFileUri = null
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("বাতিল করুন", color = Color.White)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Divider(color = Color.White.copy(alpha = 0.08f))

                // ৪. DANGEROUS AREA (RESET DATABASE)
                Button(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444).copy(alpha = 0.08f),
                        contentColor = Color(0xFFEF4444)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "হিসাবের সকল তথ্য মুছুন",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF87171)
                        )
                    }
                }
            }
        }
    }

    // Backup Import Dialog
    if (showImportDialog) {
        Dialog(onDismissRequest = { showImportDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1527)),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "ব্যাকআপ কোড আমদানি করুন",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    OutlinedTextField(
                        value = backupStringInput,
                        onValueChange = { backupStringInput = it },
                        placeholder = { Text("এখানে ব্যাকআপ কোড পেস্ট করুন...", color = Color(0xFF94A3B8).copy(alpha = 0.5f)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.05f),
                            focusedContainerColor = Color(0xFF131C33),
                            unfocusedContainerColor = Color(0xFF131C33),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        textStyle = TextStyle(fontSize = 12.sp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { showImportDialog = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("বাতিল", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (backupStringInput.trim().isBlank()) {
                                    Toast.makeText(context, "দয়া করে কোড বক্সে পেস্ট করুন!", Toast.LENGTH_SHORT).show()
                                } else {
                                    val success = viewModel.importBackup(backupStringInput.trim())
                                    if (success) {
                                        Toast.makeText(context, "ব্যাকআপ সফলভাবে পুনরুদ্ধার হয়েছে!", Toast.LENGTH_SHORT).show()
                                        backupStringInput = ""
                                        showImportDialog = false
                                    } else {
                                        Toast.makeText(context, "ভুল ব্যাকআপ কোড! আবার চেক করুন।", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3B82F6),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1.2f).height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("আমদানি করুন", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("চিরতরে ডিলিট করার সর্তকতা!", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444)) },
            text = { Text("আপনি কি নিশ্চিতভাবে আপনার ডায়েরির সকল খাতার হিসাব চিরতরে মুছে ফেলতে চান? রিসেট করার পর সব ডাটা হারিয়ে যাবে।", color = Color(0xFF94A3B8)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllData()
                        Toast.makeText(context, "সকল ডাটা মুছে ফেলা হয়েছে!", Toast.LENGTH_SHORT).show()
                        showClearConfirm = false
                    }
                ) {
                    Text("হ্যাঁ, ডিলিট করুন", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("বাতিল", color = Color.White)
                }
            },
            containerColor = Color(0xFF0D1527),
            titleContentColor = Color.White,
            textContentColor = Color(0xFF94A3B8)
        )
    }
}

@Composable
fun DashboardHomeScreen(
    entries: List<DailyEntry>,
    wageRate: Double,
    folders: List<String>,
    currentKhataMode: String,
    currentAvatarIdx: Int,
    profileCustomAvatarUri: String,
    onNavigateToDailyAccounts: () -> Unit,
    onNavigateToReportsGraphs: () -> Unit,
    onNavigateToBudget: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSetBudget: () -> Unit,
    onProfileClick: () -> Unit
) {
    val context = LocalContext.current
    
    // Computing total calculations
    val totalIncome = entries.sumOf { e ->
        val prodIncome = (e.quantity / 100.0) * wageRate
        if (e.isIncome) e.income else prodIncome
    }
    val totalExpense = entries.filter { !it.isIncome }.sumOf { it.expense }
    val balance = totalIncome - totalExpense

    val sdfDay = remember { SimpleDateFormat("EEEE", Locale("bn", "BD")) }
    val sdfDate = remember { SimpleDateFormat("d MMMM, yyyy", Locale("bn", "BD")) }
    val banglaDay = sdfDay.format(Date())
    val banglaDate = sdfDate.format(Date()).toBanglaDigits()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. TOP HEADER SECTION WITH GREETING
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Shiny animated Smart Manager text (bold, theme-matched blue/cyan, transparent yet clear, gentle glass light motion effect)
                val infiniteTransition = rememberInfiniteTransition(label = "shining")
                val animatedOffset by infiniteTransition.animateFloat(
                    initialValue = -150f,
                    targetValue = 450f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "offset"
                )

                val shineBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF3B82F6).copy(alpha = 0.35f),
                        Color(0xFF3B82F6).copy(alpha = 0.35f),
                        Color(0xFF60A5FA).copy(alpha = 0.45f),
                        Color(0xFFFFFFFF).copy(alpha = 0.45f), // gentle subtle shining light
                        Color(0xFF22D3EE).copy(alpha = 0.45f),
                        Color(0xFF3B82F6).copy(alpha = 0.35f),
                        Color(0xFF3B82F6).copy(alpha = 0.35f)
                    ),
                    start = Offset(animatedOffset, 0f),
                    end = Offset(animatedOffset + 120f, 120f)
                )

                Text(
                    text = "Smart Manager",
                    style = TextStyle(
                        brush = shineBrush,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Simple subtitle without animation or effects
                Text(
                    text = "আপনার সব হিসাব আমার কাছে সুরক্ষিত!",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF94A3B8)
                )
            }
            
            // Profile icon replacing Date Pill in the top-right corner
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onProfileClick() },
                contentAlignment = Alignment.Center
            ) {
                UserProfileAvatar(
                    avatarIndex = currentAvatarIdx,
                    customUriString = profileCustomAvatarUri,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 2. REAL-TIME SUMMARY CARD WITH 3D BORDER & NEON GLOWS
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.5.dp, Color(0xFF3B82F6).copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "রিয়েল-টাইম সারাংশ (সব খাতা)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )
                
                Spacer(modifier = Modifier.height(14.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Total Income (green accent)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFF10B981), CircleShape))
                            Text("মোট আয়", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        }
                        Text(
                            text = "৳ ${totalIncome.toInt().toBangla()}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF34D399)
                        )
                    }

                    // Total Expense (red accent)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFFEF4444), CircleShape))
                            Text("মোট ব্যয়", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        }
                        Text(
                            text = "৳ ${totalExpense.toInt().toBangla()}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFF87171)
                        )
                    }

                    // Remaining Balance (cyan accent)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFF22D3EE), CircleShape))
                            Text("অবশিষ্ট", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        }
                        Text(
                            text = "৳ ${balance.toInt().toBangla()}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF22D3EE)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mini balance ratio gauge bar
                val totalSum = totalIncome + totalExpense
                val incomePercent = if (totalSum > 0) (totalIncome / totalSum).toFloat() else 0.5f
                val expensePercent = 1f - incomePercent

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "আয়: ${(incomePercent * 100).toInt().toBangla()}%",
                            fontSize = 9.sp,
                            color = Color(0xFF34D399),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ব্যয়: ${(expensePercent * 100).toInt().toBangla()}%",
                            fontSize = 9.sp,
                            color = Color(0xFFF87171),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(if (incomePercent > 0f) incomePercent else 0.01f)
                                    .background(Color(0xFF10B981))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(if (expensePercent > 0f) expensePercent else 0.01f)
                                    .background(Color(0xFFEF4444))
                            )
                        }
                    }
                }
            }
        }

        // 3. FEATURE CARDS GRID HEADER
        Text(
            text = "হিসাব ড্যাসবোর্ড ও ফিচারসমূহ",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        // 4. THE TWO-COLUMN GRID OF FEATURE CARDS
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DashboardCard(
                    title = "নোট বুক",
                    subtext = "আয়-ব্যয় ও মালের হিসাব",
                    icon = Icons.Default.Calculate,
                    iconColor = Color(0xFF3B82F6),
                    modifier = Modifier.weight(1f)
                ) {
                    onNavigateToDailyAccounts()
                }

                DashboardCard(
                    title = "রিপোর্ট ও গ্রাফ",
                    subtext = "আয়-ব্যয় চার্ট ও এনালাইসিস",
                    icon = Icons.Default.TrendingUp,
                    iconColor = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                ) {
                    onNavigateToReportsGraphs()
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                DashboardCard(
                    title = "সেট বাজেট",
                    subtext = "দৈনিক ও মাসিক লিমিট, মালের দর ও দৈনিক লক্ষ্য সেট করুন",
                    icon = Icons.Default.PieChart,
                    iconColor = Color(0xFFFBBF24),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    onNavigateToSetBudget()
                }
            }
        }
    }
}

@Composable
fun NoteBookSelectorScreen(
    currentKhataMode: String,
    onChangeMode: (String) -> Unit,
    onSelectModeComplete: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Back Button & Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = { onBack() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "ফিরে যান",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "নোট বুক",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }

        Text(
            text = "অনুগ্রহ করে আপনার হিসাবের মোড নির্বাচন করুন:",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Mode 1: আয় ব্যয় (PREMIUM)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (currentKhataMode == "PREMIUM") Color(0xFF1E1B4B) else Color(0xFF1E293B),
                border = BorderStroke(
                    width = if (currentKhataMode == "PREMIUM") 2.dp else 1.dp,
                    color = if (currentKhataMode == "PREMIUM") Color(0xFF818CF8) else Color.White.copy(alpha = 0.1f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onChangeMode("PREMIUM")
                        onSelectModeComplete()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF818CF8).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "আয় ব্যয়",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "✦ বিস্তারিত আয় এবং ব্যয় ট্র্যাকিং মোড",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }

            // Mode 2: দৈনিক খরচ (ALT)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (currentKhataMode == "ALT") Color(0xFF064E3B) else Color(0xFF1E293B),
                border = BorderStroke(
                    width = if (currentKhataMode == "ALT") 2.dp else 1.dp,
                    color = if (currentKhataMode == "ALT") Color(0xFF34D399) else Color.White.copy(alpha = 0.1f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onChangeMode("ALT")
                        onSelectModeComplete()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF34D399).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "দৈনিক খরচ",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "★ শুধুমাত্র দৈনিক খরচ এবং ব্যক্তিগত খরচের হিসাব",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }

            // Mode 3: মালের হিসাব (MAIN)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (currentKhataMode == "MAIN") Color(0xFF1E3A8A) else Color(0xFF1E293B),
                border = BorderStroke(
                    width = if (currentKhataMode == "MAIN") 2.dp else 1.dp,
                    color = if (currentKhataMode == "MAIN") Color(0xFF3B82F6) else Color.White.copy(alpha = 0.1f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onChangeMode("MAIN")
                        onSelectModeComplete()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF3B82F6).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = null,
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "মালের হিসাব",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "💼 মাল বেচাকেনা, কাস্টমার লেজার ও সাধারণ খাতা",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReportsGraphsSubScreen(
    rawEntries: List<DailyEntry>,
    wageRate: Double,
    onBack: () -> Unit
) {
    var selectedReportMode by remember { mutableStateOf("PREMIUM") }

    val premiumEntries = remember(rawEntries) {
        rawEntries.filter { it.folderName.startsWith("✦ ") }
            .map { it.copy(folderName = it.folderName.removePrefix("✦ ")) }
    }
    val altEntries = remember(rawEntries) {
        rawEntries.filter { it.folderName.startsWith("★ ") }
            .map { it.copy(folderName = it.folderName.removePrefix("★ ")) }
            .filter { !it.isIncome }
    }
    val mainEntries = remember(rawEntries) {
        rawEntries.filter { !it.folderName.startsWith("★ ") && !it.folderName.startsWith("✦ ") }
    }

    val currentList = when (selectedReportMode) {
        "PREMIUM" -> premiumEntries
        "ALT" -> altEntries
        else -> mainEntries
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Back Button & Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = { onBack() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "ফিরে যান",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "রিপোর্ট এবং গ্রাফ",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stacked selectors (one under another)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Selector 1: আয় ব্যয় (PREMIUM)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedReportMode == "PREMIUM") Color(0xFF1E1B4B) else Color(0xFF1E293B),
                    border = BorderStroke(
                        width = if (selectedReportMode == "PREMIUM") 2.dp else 1.dp,
                        color = if (selectedReportMode == "PREMIUM") Color(0xFF818CF8) else Color.White.copy(alpha = 0.05f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedReportMode = "PREMIUM" }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFF818CF8).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Stars, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                        }
                        Column {
                            Text("আয় ব্যয় রিপোর্ট", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("✦ বিস্তারিত আয় এবং ব্যয় গতিধারা ও গ্রাফ", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        }
                    }
                }

                // Selector 2: দৈনিক খরচ (ALT)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedReportMode == "ALT") Color(0xFF064E3B) else Color(0xFF1E293B),
                    border = BorderStroke(
                        width = if (selectedReportMode == "ALT") 2.dp else 1.dp,
                        color = if (selectedReportMode == "ALT") Color(0xFF34D399) else Color.White.copy(alpha = 0.05f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedReportMode = "ALT" }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFF34D399).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.TrendingDown, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                        }
                        Column {
                            Text("দৈনিক খরচ রিপোর্ট", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("★ শুধুমাত্র দৈনিক খরচের গতিধারা ও গ্রাফ", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        }
                    }
                }

                // Selector 3: মালের হিসাব (MAIN)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedReportMode == "MAIN") Color(0xFF1E3A8A) else Color(0xFF1E293B),
                    border = BorderStroke(
                        width = if (selectedReportMode == "MAIN") 2.dp else 1.dp,
                        color = if (selectedReportMode == "MAIN") Color(0xFF3B82F6) else Color.White.copy(alpha = 0.05f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedReportMode = "MAIN" }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFF3B82F6).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Calculate, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(16.dp))
                        }
                        Column {
                            Text("মালের হিসাব রিপোর্ট", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("💼 কাস্টমার লেজার, সাধারণ গতিধারা ও গ্রাফ", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        }
                    }
                }
            }

            Divider(color = Color.White.copy(alpha = 0.1f))

            // Charts display area below selectors
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = when (selectedReportMode) {
                        "PREMIUM" -> "আয়-ব্যয় গতিধারা (৭ দিন / ৩০ দিন)"
                        "ALT" -> "দৈনিক খরচ গতিধারা (৭ দিন / ৩০ দিন)"
                        else -> "মালের হিসাব গতিধারা (৭ দিন / ৩০ দিন)"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF60A5FA)
                )
                IncomeExpenseTrendChart(
                    filteredEntries = currentList,
                    wageRate = wageRate,
                    currentKhataMode = selectedReportMode
                )

                Divider(color = Color.White.copy(alpha = 0.05f))

                Text(
                    text = "ক্যাটাগরি ভিত্তিক খরচের হিসাব (Donut Chart)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF34D399)
                )
                ExpenseDonutChart(filteredEntries = currentList)

                Divider(color = Color.White.copy(alpha = 0.05f))

                Text(
                    text = "দৈনিক খরচের লাইন চার্ট (Line Chart)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFBBF24)
                )
                ExpenseLineChart(filteredEntries = currentList)
            }
        }
    }
}

@Composable
fun SearchTabScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCategoryFilter: String,
    onCategoryFilterChange: (String) -> Unit,
    categories: List<String>,
    entries: List<DailyEntry>,
    wageRate: Double,
    cardBgColor: Color,
    isGlass: Boolean,
    cardBorderColor: Color,
    onEdit: (DailyEntry) -> Unit,
    onDelete: (DailyEntry) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "হিসাব অনুসন্ধান",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
        
        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("খুঁজুন (যেমন: নাস্তা, গাড়ি ভাড়া, নোট)", color = Color(0xFF94A3B8), fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF60A5FA)) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF3B82F6),
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                focusedContainerColor = Color(0xFF0F172A),
                unfocusedContainerColor = Color(0xFF0F172A),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        )

        // Category Filter row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val allCats = listOf("সব ক্যাটাগরি") + categories
            items(allCats) { cat ->
                val isSelected = selectedCategoryFilter == cat
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isSelected) Color(0xFF3B82F6) else Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onCategoryFilterChange(cat) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Filter and Search Logic
        val filtered = remember(entries, searchQuery, selectedCategoryFilter) {
            entries.filter { entry ->
                val matchesQuery = searchQuery.isEmpty() || 
                        entry.category.contains(searchQuery, ignoreCase = true) || 
                        entry.note.contains(searchQuery, ignoreCase = true) ||
                        entry.folderName.contains(searchQuery, ignoreCase = true)
                val matchesCategory = selectedCategoryFilter == "সব ক্যাটাগরি" || entry.category == selectedCategoryFilter
                matchesQuery && matchesCategory
            }
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF475569).copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "কোনো হিসাব পাওয়া যায়নি!",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(filtered) { item ->
                    HistoryListItemRow(
                        entry = item,
                        wageRate = wageRate,
                        onEdit = { onEdit(item) },
                        onDelete = { onDelete(item) },
                        cardBgColor = cardBgColor,
                        isGlass = isGlass,
                        cardBorderColor = cardBorderColor
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileTabScreen(
    entries: List<DailyEntry>,
    folders: List<String>,
    currentKhataMode: String,
    wageRate: Double,
    viewModel: MainViewModel,
    context: Context,
    showSettingsDialog: () -> Unit,
    onBack: () -> Unit
) {
    val totalIncome = entries.sumOf { e ->
        val prodIncome = (e.quantity / 100.0) * wageRate
        if (e.isIncome) e.income else prodIncome
    }
    val totalExpense = entries.filter { !it.isIncome }.sumOf { it.expense }
    val balance = totalIncome - totalExpense

    // Profile and Google states
    val googleEmail by viewModel.googleEmail.collectAsStateWithLifecycle()
    val googleName by viewModel.googleName.collectAsStateWithLifecycle()
    val isGoogleSignedIn by viewModel.isGoogleSignedIn.collectAsStateWithLifecycle()
    val profileName by viewModel.profileName.collectAsStateWithLifecycle()
    val currentAvatarIdx by viewModel.profileAvatarIndex.collectAsStateWithLifecycle()
    val profileCustomAvatarUri by viewModel.profileCustomAvatarUri.collectAsStateWithLifecycle()

    var showAvatarDialog by remember { mutableStateOf(false) }
    var showNameEditDialog by remember { mutableStateOf(false) }
    var tempNameInput by remember { mutableStateOf("") }
    var isManualSyncing by remember { mutableStateOf(false) }
    var showAlternativeLoginDialog by remember { mutableStateOf(false) }
    var alternativeEmailInput by remember { mutableStateOf("") }
    var alternativeNameInput by remember { mutableStateOf("") }

    val avatarConfig = listOf(
        Pair(Color(0xFF3B82F6), Icons.Default.Person),       // Cyan/Blue
        Pair(Color(0xFFEC4899), Icons.Default.Favorite),     // Pink/Heart
        Pair(Color(0xFFFBBF24), Icons.Default.Star),         // Amber/Star
        Pair(Color(0xFF10B981), Icons.Default.Face),         // Emerald/Face
        Pair(Color(0xFF8B5CF6), Icons.Default.ThumbUp),      // Purple/ThumbUp
        Pair(Color(0xFF06B6D4), Icons.Default.CheckCircle)   // Cyan/Check
    )

    val (avatarColor, avatarIcon) = avatarConfig.getOrNull(currentAvatarIdx) ?: avatarConfig[0]

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            viewModel.updateProfileCustomAvatarUri(uri.toString())
            showAvatarDialog = false
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                val email = account.email ?: ""
                val name = account.displayName ?: ""
                viewModel.setGoogleSignIn(email, name, true)
                Toast.makeText(context, "গুগল সাইন-ইন সফল হয়েছে!", Toast.LENGTH_SHORT).show()

                // Automatic restore of previous backup if it exists
                viewModel.restoreFromGoogleDrive(context, account) { success, msg, content ->
                    if (success && !content.isNullOrBlank()) {
                        val imported = viewModel.importBackup(content, overwrite = true)
                        if (imported) {
                            Toast.makeText(context, "পূর্বের ব্যাকআপ ডাটা সফলভাবে পুনরুদ্ধার করা হয়েছে!", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        println("ProfileRestore: No previous drive backup found: $msg")
                    }
                }
            }
        } catch (e: ApiException) {
            e.printStackTrace()
            showAlternativeLoginDialog = true
            Toast.makeText(context, "সরাসরি গুগল কানেকশনে সমস্যা হচ্ছে, দয়া করে বিকল্প লগইন পদ্ধতিটি ব্যবহার করুন।", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title Row with a standard Back arrow
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "ফিরে যান",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "আমার প্রোফাইল",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }

        // Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Avatar circle (Clickable to change avatar)
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clickable { showAvatarDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        UserProfileAvatar(
                            avatarIndex = currentAvatarIdx,
                            customUriString = profileCustomAvatarUri,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Tiny edit badge
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(Color(0xFF1E293B), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                .align(Alignment.BottomEnd),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }

                    // Email & Name (Clickable name to edit)
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clickable {
                                tempNameInput = profileName
                                showNameEditDialog = true
                            }
                        ) {
                            Text(
                                text = profileName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "সম্পাদনা",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        
                        Text(
                            text = if (isGoogleSignedIn && googleEmail.isNotBlank()) googleEmail else "গুগল দিয়ে লগইন করা নেই",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.05f))

                // Authentication Status and Actions
                if (!isGoogleSignedIn) {
                    // Show Sign In Button with alternative fallback
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .requestEmail()
                                    .requestScopes(Scope("https://www.googleapis.com/auth/drive.appdata"))
                                    .build()
                                val googleSignInClient = GoogleSignIn.getClient(context, gso)
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24)
                                )
                                Text(
                                    text = "জিমেইল দিয়ে লগইন করুন",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                            }
                        }
                        
                        Text(
                            text = "সরাসরি বিকল্প পদ্ধতিতে লগইন করতে এখানে ক্লিক করুন",
                            fontSize = 11.sp,
                            color = Color(0xFF3B82F6),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable { showAlternativeLoginDialog = true }
                                .padding(top = 4.dp)
                        )
                    }
                } else {
                    // Logged in: Show Auto-backup status and actions
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Success auto-backup badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "✓ গুগল ড্রাইভ অটো-ব্যাকআপ সক্রিয় রয়েছে",
                                fontSize = 11.sp,
                                color = Color(0xFF34D399),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Sync & Logout buttons row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Manual Upload/Backup Button
                            Button(
                                onClick = {
                                    val account = GoogleSignIn.getLastSignedInAccount(context)
                                    if (account != null) {
                                        isManualSyncing = true
                                        val backupContent = viewModel.exportBackup()
                                        viewModel.uploadToGoogleDrive(context, account, backupContent) { success, msg ->
                                            isManualSyncing = false
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isManualSyncing) "সিঙ্ক হচ্ছে..." else "ম্যানুয়াল ব্যাকআপ",
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Logout Button
                            Button(
                                onClick = {
                                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                                    GoogleSignIn.getClient(context, gso).signOut().addOnCompleteListener {
                                        viewModel.setGoogleSignIn("", "", false)
                                        Toast.makeText(context, "লগআউট করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D).copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.2f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ExitToApp,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "লগআউট করুন",
                                        fontSize = 11.sp,
                                        color = Color(0xFFFCA5A5),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Stats section
        Text(
            text = "অ্যাপ স্ট্যাটিস্টিকস",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Total entries
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("মোট এন্ট্রি সংখ্যা", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Text("${entries.size.toBangla()} টি", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Divider(color = Color.White.copy(alpha = 0.05f))

                // Total folders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("মোট খাতা / ফোল্ডার", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Text("${folders.size.toBangla()} টি", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Divider(color = Color.White.copy(alpha = 0.05f))

                // Active Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("সক্রিয় খাতা মোড", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Text(
                        text = if (currentKhataMode == "MAIN") "মালের হিসাব" else if (currentKhataMode == "ALT") "দৈনিক খরচ" else "প্রিমিয়াম",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF34D399)
                    )
                }
                Divider(color = Color.White.copy(alpha = 0.05f))

                // Wage rate
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("মালের মজুরি রেট", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Text("৳ ${wageRate.toBangla()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Action settings shortcut
        Text(
            text = "কুইক অ্যাকশন",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        // Settings Dialog Button
        Button(
            onClick = { showSettingsDialog() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("অ্যাপ সেটিংস ও থিম পরিবর্তন", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }

    // Avatar Selection Dialog
    if (showAvatarDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarDialog = false },
            containerColor = Color(0xFF0D1527),
            title = { Text("প্রোফাইল ছবি পরিবর্তন করুন", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 10.dp)
                ) {
                    Text("একটি ডিফল্ট অবতার বেছে নিন:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(avatarConfig.size) { idx ->
                            val (color, icon) = avatarConfig[idx]
                            val isSel = currentAvatarIdx == idx && profileCustomAvatarUri.isBlank()
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(color.copy(alpha = 0.2f))
                                    .border(if (isSel) 2.5.dp else 1.dp, if (isSel) color else Color.White.copy(alpha = 0.1f), CircleShape)
                                    .clickable {
                                        viewModel.updateProfileAvatarIndex(idx)
                                        showAvatarDialog = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    
                    Divider(color = Color.White.copy(alpha = 0.05f))
                    
                    // Button to select from gallery
                    Button(
                        onClick = {
                            galleryLauncher.launch("image/*")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "মোবাইল গ্যালারি থেকে ছবি দিন",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAvatarDialog = false }) {
                    Text("বন্ধ করুন", color = Color(0xFF3B82F6))
                }
            }
        )
    }

    // Name Editing Dialog
    if (showNameEditDialog) {
        AlertDialog(
            onDismissRequest = { showNameEditDialog = false },
            containerColor = Color(0xFF0D1527),
            title = { Text("আপনার নাম সেট করুন", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = tempNameInput,
                    onValueChange = { tempNameInput = it },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        focusedContainerColor = Color(0xFF131C33),
                        unfocusedContainerColor = Color(0xFF131C33)
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (tempNameInput.isNotBlank()) {
                        viewModel.updateProfileName(tempNameInput)
                    }
                    showNameEditDialog = false
                }) {
                    Text("সংরক্ষণ করুন", color = Color(0xFF10B981))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameEditDialog = false }) {
                    Text("বাতিল", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    // Alternative Login Dialog
    if (showAlternativeLoginDialog) {
        AlertDialog(
            onDismissRequest = { showAlternativeLoginDialog = false },
            containerColor = Color(0xFF0D1527),
            title = {
                Text(
                    text = "বিকল্প জিমেইল লগইন",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "গুগল প্লে সার্ভিস বা কানেকশন ত্রুটির কারণে সরাসরি লগইন সম্ভব না হলে আপনার জিমেইল ও নাম দিয়ে বিকল্প উপায়ে লগইন করুন:",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    OutlinedTextField(
                        value = alternativeEmailInput,
                        onValueChange = { alternativeEmailInput = it },
                        label = { Text("জিমেইল এড্রেস", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedContainerColor = Color(0xFF131C33),
                            unfocusedContainerColor = Color(0xFF131C33),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = alternativeNameInput,
                        onValueChange = { alternativeNameInput = it },
                        label = { Text("আপনার নাম", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedContainerColor = Color(0xFF131C33),
                            unfocusedContainerColor = Color(0xFF131C33),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (alternativeEmailInput.isNotBlank() && alternativeNameInput.isNotBlank()) {
                            viewModel.setGoogleSignIn(alternativeEmailInput, alternativeNameInput, true)
                            showAlternativeLoginDialog = false
                            Toast.makeText(context, "লগইন সফল হয়েছে!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "দয়া করে সঠিক জিমেইল ও নাম লিখুন!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    Text("লগইন সম্পন্ন করুন", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAlternativeLoginDialog = false }) {
                    Text("বাতিল", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

@Composable
fun DashboardCard(
    title: String,
    subtext: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(96.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(iconColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFF475569),
                    modifier = Modifier.size(14.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtext,
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SetBudgetScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("PREMIUM") } // Tabs: PREMIUM, ALT, MAIN

    // Mode-specific states from ViewModel
    val premiumDailyIncomeTargetState by viewModel.premiumDailyIncomeTarget.collectAsStateWithLifecycle()
    val premiumDailyExpenseTargetState by viewModel.premiumDailyExpenseTarget.collectAsStateWithLifecycle()

    val altDailyLimitState by viewModel.altDailyLimit.collectAsStateWithLifecycle()
    val altMonthlyLimitState by viewModel.altMonthlyLimit.collectAsStateWithLifecycle()

    val mainGoodsRateState by viewModel.mainGoodsRate.collectAsStateWithLifecycle()
    val mainDailyLimitState by viewModel.mainDailyLimit.collectAsStateWithLifecycle()
    val mainMonthlyLimitState by viewModel.mainMonthlyLimit.collectAsStateWithLifecycle()

    // Mode-specific input states
    var premiumDailyIncomeInput by remember(premiumDailyIncomeTargetState) {
        mutableStateOf(premiumDailyIncomeTargetState.toInt().toString())
    }
    var premiumDailyExpenseInput by remember(premiumDailyExpenseTargetState) {
        mutableStateOf(premiumDailyExpenseTargetState.toInt().toString())
    }

    var altDailyLimitInput by remember(altDailyLimitState) {
        mutableStateOf(altDailyLimitState.toInt().toString())
    }
    var altMonthlyLimitInput by remember(altMonthlyLimitState) {
        mutableStateOf(altMonthlyLimitState.toInt().toString())
    }

    var mainGoodsRateInput by remember(mainGoodsRateState) {
        mutableStateOf(mainGoodsRateState.toInt().toString())
    }
    var mainDailyLimitInput by remember(mainDailyLimitState) {
        mutableStateOf(mainDailyLimitState.toInt().toString())
    }
    var mainMonthlyLimitInput by remember(mainMonthlyLimitState) {
        mutableStateOf(mainMonthlyLimitState.toInt().toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Back Button & Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = { onBack() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "ফিরে যান",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "বাজেট ও লিমিট সেটিংস",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }

        Text(
            text = "নিচে প্রতিটি হিসাব মোডের দৈনিক লিমিট, মাসিক লিমিট, মালের দর ও লক্ষ্য আলাদাভাবে সেট করতে পারবেন।",
            fontSize = 12.sp,
            color = Color(0xFF94A3B8)
        )

        // Mode tabs selectors (3 buttons side-by-side)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabs = listOf(
                Triple("PREMIUM", "আয় ব্যয়", Color(0xFF818CF8)),
                Triple("ALT", "দৈনিক খরচ", Color(0xFF34D399)),
                Triple("MAIN", "মালের হিসাব", Color(0xFF3B82F6))
            )

            tabs.forEach { (modeCode, modeLabel, themeColor) ->
                val isSelected = selectedTab == modeCode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (isSelected) themeColor.copy(alpha = 0.15f) else Color(0xFF1E293B),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) themeColor else Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { selectedTab = modeCode }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = modeLabel,
                        color = if (isSelected) themeColor else Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Active mode theme details
        val currentTabColor = when (selectedTab) {
            "PREMIUM" -> Color(0xFF818CF8)
            "ALT" -> Color(0xFF34D399)
            else -> Color(0xFF3B82F6)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = BorderStroke(1.dp, currentTabColor.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(currentTabColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (selectedTab) {
                                "PREMIUM" -> Icons.Default.Stars
                                "ALT" -> Icons.Default.TrendingDown
                                else -> Icons.Default.Calculate
                            },
                            contentDescription = null,
                            tint = currentTabColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = when (selectedTab) {
                            "PREMIUM" -> "আয় ব্যয় মোডের বাজেট কনফিগারেশন"
                            "ALT" -> "দৈনিক খরচ মোডের বাজেট কনফিগারেশন"
                            else -> "মালের হিসাব মোডের বাজেট কনফিগারেশন"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Divider(color = Color.White.copy(alpha = 0.05f))

                if (selectedTab == "PREMIUM") {
                    // Input 1: দৈনিক আয় টার্গেট
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "দৈনিক আয় টার্গেট (টাকা)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8)
                        )
                        OutlinedTextField(
                            value = premiumDailyIncomeInput,
                            onValueChange = { premiumDailyIncomeInput = it.filter { char -> char.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = currentTabColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                focusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                                unfocusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            placeholder = { Text("যেমন: ১০০০", color = Color(0xFF475569)) }
                        )
                    }

                    // Input 2: দৈনিক ব্যয় লিমিট
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "দৈনিক ব্যয় লিমিট (টাকা)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8)
                        )
                        OutlinedTextField(
                            value = premiumDailyExpenseInput,
                            onValueChange = { premiumDailyExpenseInput = it.filter { char -> char.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = currentTabColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                focusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                                unfocusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            placeholder = { Text("যেমন: ৫০০", color = Color(0xFF475569)) }
                        )
                    }
                } else if (selectedTab == "ALT") {
                    // Input 1: দৈনিক খরচের লিমিট
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "দৈনিক খরচের লিমিট (টাকা)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8)
                        )
                        OutlinedTextField(
                            value = altDailyLimitInput,
                            onValueChange = { altDailyLimitInput = it.filter { char -> char.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = currentTabColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                focusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                                unfocusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            placeholder = { Text("যেমন: ৫০০", color = Color(0xFF475569)) }
                        )
                    }

                    // Input 2: মাসিক খরচের লিমিট
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "মাসিক খরচের লিমিট (টাকা)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8)
                        )
                        OutlinedTextField(
                            value = altMonthlyLimitInput,
                            onValueChange = { altMonthlyLimitInput = it.filter { char -> char.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = currentTabColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                focusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                                unfocusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            placeholder = { Text("যেমন: ১৫০০০", color = Color(0xFF475569)) }
                        )
                    }
                } else {
                    // Input 1: প্রতি ১০০ পিস মালের দাম
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "প্রতি ১০০ পিস মালের দাম (টাকা)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8)
                        )
                        OutlinedTextField(
                            value = mainGoodsRateInput,
                            onValueChange = { mainGoodsRateInput = it.filter { char -> char.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = currentTabColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                focusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                                unfocusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            placeholder = { Text("যেমন: ৫০", color = Color(0xFF475569)) }
                        )
                    }

                    // Input 2: দৈনিক খরচ লিমিট
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "দৈনিক খরচ লিমিট (টাকা)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8)
                        )
                        OutlinedTextField(
                            value = mainDailyLimitInput,
                            onValueChange = { mainDailyLimitInput = it.filter { char -> char.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = currentTabColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                focusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                                unfocusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            placeholder = { Text("যেমন: ৫০০", color = Color(0xFF475569)) }
                        )
                    }

                    // Input 3: মাসিক খরচ লিমিট
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "মাসিক খরচ লিমিট (টাকা)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8)
                        )
                        OutlinedTextField(
                            value = mainMonthlyLimitInput,
                            onValueChange = { mainMonthlyLimitInput = it.filter { char -> char.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = currentTabColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                focusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                                unfocusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            placeholder = { Text("যেমন: ১৫০০০", color = Color(0xFF475569)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (selectedTab == "PREMIUM") {
                            val inc = premiumDailyIncomeInput.toDoubleOrNull() ?: 0.0
                            val exp = premiumDailyExpenseInput.toDoubleOrNull() ?: 0.0
                            viewModel.updatePremiumTargets(inc, exp)
                        } else if (selectedTab == "ALT") {
                            val dLimit = altDailyLimitInput.toDoubleOrNull() ?: 0.0
                            val mLimit = altMonthlyLimitInput.toDoubleOrNull() ?: 0.0
                            viewModel.updateModeSettings(
                                mode = "ALT",
                                dailyLimit = dLimit,
                                monthlyLimit = mLimit,
                                goodsRate = viewModel.altGoodsRate.value,
                                dailyTargetVal = viewModel.altDailyTarget.value
                            )
                        } else {
                            val rate = mainGoodsRateInput.toDoubleOrNull() ?: 0.0
                            val dLimit = mainDailyLimitInput.toDoubleOrNull() ?: 0.0
                            val mLimit = mainMonthlyLimitInput.toDoubleOrNull() ?: 0.0
                            viewModel.updateModeSettings(
                                mode = "MAIN",
                                dailyLimit = dLimit,
                                monthlyLimit = mLimit,
                                goodsRate = rate,
                                dailyTargetVal = viewModel.mainDailyTarget.value
                            )
                        }

                        Toast.makeText(
                            context,
                            "সেটিংস সফলভাবে সংরক্ষিত হয়েছে!",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = currentTabColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "সংরক্ষণ করুন",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun SmartManagerSplashScreen(onFinish: () -> Unit) {
    val greetings = listOf(
        "Hello", 
        "Hola", 
        "স্বাগতম", 
        "Namaste", 
        "Bonjour", 
        "Ciao", 
        "Konnichiwa", 
        "Welcome"
    )
    
    var greetingIndex by remember { mutableStateOf(0) }
    
    // Smooth progress animation: 0f to 1f over 4000ms
    val progress = remember { Animatable(0f) }
    
    // Scale and fade-in animations for Logo and Text
    val scale = remember { Animatable(0.5f) }
    val alpha = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        // Cycle greetings every 500ms
        launch {
            while (true) {
                kotlinx.coroutines.delay(500)
                greetingIndex = (greetingIndex + 1) % greetings.size
            }
        }
        
        // Animate scale & alpha
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 800)
            )
        }
        
        // Animate the progress bar from 0f to 1f in 4000ms
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 4000, easing = LinearEasing)
        )
        
        // Finished loading!
        onFinish()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF020916),
                        Color(0xFF051730),
                        Color(0xFF01050E)
                    )
                )
            )
    ) {
        // Luxury golden glowing aura behind the central logo
        Box(
            modifier = Modifier
                .size(360.dp)
                .align(Alignment.Center)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE2B93B).copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Elegant top spacing
            Spacer(modifier = Modifier.height(64.dp))
            
            // Central Branding Layout
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.alpha(alpha.value)
            ) {
                // Double-circle luxury gold framed App Logo
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(scale.value)
                        .align(Alignment.CenterHorizontally)
                ) {
                    // Outer golden circle
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(2.5.dp, Color(0xFFE2B93B).copy(alpha = 0.8f), CircleShape)
                            .padding(6.dp)
                            .border(1.2.dp, Color(0xFFE2B93B), CircleShape)
                            .clip(CircleShape)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_app_logo),
                            contentDescription = "Smart Manager Logo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Smart Manager Main Title in Golden Text
                Text(
                    text = "Smart Manager",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFE2B93B),
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.2.sp
                )
                
                // Welcome subtitle
                Text(
                    text = "Welcome",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE2B93B).copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.5.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Dynamic Multilingual Welcome Greetings in Center of loading line
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(
                        targetState = greetings[greetingIndex],
                        animationSpec = tween(durationMillis = 300),
                        label = "GreetingCrossfade"
                    ) { text ->
                        Text(
                            text = text,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
            
            // Bottom Section with Progress Loading line and Powered By text
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 36.dp)
            ) {
                // Animated Left-to-Right Golden Progress Loading Line
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.value)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFB8860B),
                                        Color(0xFFE2B93B),
                                        Color(0xFFFFF8DC)
                                    )
                                )
                            )
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // "power by Ahmed Rasel" text
                Text(
                    text = "power by Ahmed Rasel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFFE2B93B).copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}

@Composable
fun UserProfileAvatar(
    avatarIndex: Int,
    customUriString: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val avatarConfig = listOf(
        Pair(Color(0xFF3B82F6), Icons.Default.Person),       // Cyan/Blue
        Pair(Color(0xFFEC4899), Icons.Default.Favorite),     // Pink/Heart
        Pair(Color(0xFFFBBF24), Icons.Default.Star),         // Amber/Star
        Pair(Color(0xFF10B981), Icons.Default.Face),         // Emerald/Face
        Pair(Color(0xFF8B5CF6), Icons.Default.ThumbUp),      // Purple/ThumbUp
        Pair(Color(0xFF06B6D4), Icons.Default.CheckCircle)   // Cyan/Check
    )
    val (defaultColor, defaultIcon) = avatarConfig.getOrNull(avatarIndex) ?: avatarConfig[0]

    if (!customUriString.isNullOrBlank()) {
        val bitmap = remember(customUriString) {
            try {
                val uri = android.net.Uri.parse(customUriString)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    android.graphics.BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "প্রোফাইল ছবি",
                modifier = modifier
                    .clip(CircleShape)
                    .border(1.5.dp, Color(0xFFE2B93B), CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            // Fallback to default avatar if image cannot be loaded
            Box(
                modifier = modifier
                    .background(defaultColor.copy(alpha = 0.15f), CircleShape)
                    .border(1.5.dp, defaultColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = defaultIcon,
                    contentDescription = null,
                    tint = defaultColor,
                    modifier = Modifier.fillMaxSize(0.5f)
                )
            }
        }
    } else {
        // Show default index-based avatar
        Box(
            modifier = modifier
                .background(defaultColor.copy(alpha = 0.15f), CircleShape)
                .border(1.5.dp, defaultColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = defaultIcon,
                contentDescription = null,
                tint = defaultColor,
                modifier = Modifier.fillMaxSize(0.5f)
            )
        }
    }
}
