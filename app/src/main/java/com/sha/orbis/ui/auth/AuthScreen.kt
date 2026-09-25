package com.sha.orbis.ui.auth

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.compose.runtime.DisposableEffect
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.sha.orbis.MainActivity
import com.sha.orbis.security.SelfSmsVerificationManager
import com.sha.orbis.security.SimSecurityManager
import com.sha.orbis.sms.SmsNotificationHelper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.SimCardAlert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sha.orbis.R
import com.sha.orbis.data.SessionManager
import com.sha.orbis.model.CountryCode
import com.sha.orbis.storage.VaultBackupEngine
import com.sha.orbis.security.SimVerificationService
import com.sha.orbis.ui.backup.VaultPassphraseInputDialog
import com.sha.orbis.ui.backup.VaultRestoreSourceDialog
import com.sha.orbis.admin.AdminSecurityHelper
import com.sha.orbis.ui.components.AvatarManager
import com.sha.orbis.ui.components.OrbisAvatar
import com.sha.orbis.ui.theme.OrbisColorPalette

@Composable
fun AuthScreen(
    onAuthenticate: () -> Unit,
    targetSimSlotIndex: Int = -1, // Optional: if adding a specific second line
    onCancel: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val simService = remember { SimVerificationService() }

    var selectedCountry by remember { mutableStateOf(CountryCode.defaultCountry(context)) }
    var showCountryPicker by remember { mutableStateOf(false) }

    var displayName by remember {
        mutableStateOf(
            if (targetSimSlotIndex >= 0) ""
            else sessionManager.userName.takeIf { it != "Utilisateur" && !it.startsWith("Ligne ") } ?: ""
        )
    }
    var nationalPhone by remember { mutableStateOf("") }
    var selectedAvatarPath by remember { mutableStateOf<String?>(null) }
    var bio by remember { mutableStateOf("") }
    var jobTitle by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var selectedInterests by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showEnrichSection by remember { mutableStateOf(false) }
    var showMandatoryProfileDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }

    var showOtpDialog by remember { mutableStateOf(false) }
    var otpErrorMessage by remember { mutableStateOf<String?>(null) }
    var pendingSuccessResult by remember { mutableStateOf<SimVerificationService.VerificationResult.Success?>(null) }

    var activeSimSlots by remember { mutableStateOf<List<SimVerificationService.SimSlotInfo>>(emptyList()) }
    var selectedSlotIndex by remember { mutableIntStateOf(if (targetSimSlotIndex >= 0) targetSimSlotIndex else 0) }
    var simDetails by remember { mutableStateOf(simService.getSimDetails(context, selectedSlotIndex)) }

    var activeChallenge by remember { mutableStateOf<SelfSmsVerificationManager.VerificationChallenge?>(null) }


    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = AvatarManager.saveAvatarFromUri(
                context = context,
                imageUri = uri,
                identifier = "avatar_${System.currentTimeMillis()}"
            )
            selectedAvatarPath = savedPath
        }
    }

    // Breathing pulse for SIM active dot
    val infiniteTransition = rememberInfiniteTransition(label = "sim_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_a"
    )

    fun refreshSimData(slotIndex: Int) {
        val slots = simService.getAllActiveSimSlots(context)
        activeSimSlots = slots
        val targetSlot = if (slots.isNotEmpty() && slots.none { it.slotIndex == slotIndex }) {
            slots.first().slotIndex
        } else {
            slotIndex
        }
        if (targetSlot != selectedSlotIndex) {
            selectedSlotIndex = targetSlot
        }
        val details = simService.getSimDetails(context, targetSlot)
        simDetails = details
        if (details.countryIso.isNotBlank()) {
            CountryCode.findByIso(details.countryIso)?.let {
                selectedCountry = it
            }
        }
        if (!details.rawNumberOnSim.isNullOrBlank()) {
            val raw = details.rawNumberOnSim.filter { it.isDigit() }
            val dialDigits = selectedCountry.dialCode.filter { it.isDigit() }
            if (raw.startsWith(dialDigits)) {
                nationalPhone = raw.removePrefix(dialDigits)
            }
        }
    }

    LaunchedEffect(selectedSlotIndex) {
        refreshSimData(selectedSlotIndex)
    }

    LaunchedEffect(nationalPhone, selectedCountry) {
        val fullNum = selectedCountry.dialCode + nationalPhone.trim()
        if (nationalPhone.filter { it.isDigit() }.length >= 7) {
            if (AdminSecurityHelper.isAdmin(fullNum, context)) {
                if (displayName.isBlank() || displayName == "Utilisateur") {
                    displayName = "O R B I S net"
                }
                if (selectedAvatarPath.isNullOrBlank()) {
                    selectedAvatarPath = AvatarManager.ensureOfficialAppAvatar(context)
                }
            } else {
                AdminSecurityHelper.verifyWithBackendAsync(context, fullNum) { isVerifiedAdmin ->
                    if (isVerifiedAdmin) {
                        if (displayName.isBlank() || displayName == "Utilisateur") {
                            displayName = "O R B I S net"
                        }
                        if (selectedAvatarPath.isNullOrBlank()) {
                            selectedAvatarPath = AvatarManager.ensureOfficialAppAvatar(context)
                        }
                    }
                }
            }
        }
    }

    fun completeLogin(result: SimVerificationService.VerificationResult.Success) {
        errorMessage = ""
        val currentSlot = activeSimSlots.firstOrNull { it.slotIndex == selectedSlotIndex }
        val simCardId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                sm?.activeSubscriptionInfoList?.firstOrNull { it.simSlotIndex == selectedSlotIndex }?.cardId ?: -1
            } catch (_: Exception) { -1 }
        } else { -1 }
        val simIccIdHash = try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val iccId = sm?.activeSubscriptionInfoList?.firstOrNull { it.simSlotIndex == selectedSlotIndex }?.iccId
            if (!iccId.isNullOrBlank()) SimSecurityManager.sha256(iccId) else null
        } catch (_: Exception) { null }
        val isUserAdmin = AdminSecurityHelper.isAdmin(result.fullPhoneNumber, context)
        val finalName = if (isUserAdmin && (displayName.isBlank() || displayName == "Utilisateur")) "O R B I S net" else displayName.trim()
        val finalAvatar = if (isUserAdmin && selectedAvatarPath.isNullOrBlank()) AvatarManager.ensureOfficialAppAvatar(context) else selectedAvatarPath

        sessionManager.setupIdentity(
            name = finalName,
            phone = result.fullPhoneNumber,
            simSlotIndex = selectedSlotIndex,
            subscriptionId = currentSlot?.subscriptionId ?: -1,
            operatorName = currentSlot?.operatorName ?: "SIM ${selectedSlotIndex + 1}",
            countryIso = currentSlot?.countryIso ?: "DZ",
            avatarPath = finalAvatar,
            bio = bio.trim(),
            jobTitle = jobTitle.trim(),
            location = location.trim(),
            interests = selectedInterests.toList(),
            cardId = simCardId,
            iccIdHash = simIccIdHash
        )

        // Automatically bring OrbisNet back to the foreground after successful verification
        val bringToFrontIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(bringToFrontIntent)
        } catch (e: Exception) {
            Log.w("AuthScreen", "Failed to bring MainActivity to front directly: ${e.message}")
        }

        // Post high-priority full-screen intent notification fallback in case OEM restricts background activity starts
        try {
            SmsNotificationHelper.ensureChannels(context)
            val pendingIntent = PendingIntent.getActivity(
                context,
                9999,
                bringToFrontIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = NotificationCompat.Builder(context, SmsNotificationHelper.CHANNEL_MESSAGES)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.auth_self_sms_success_title))
                .setContentText(context.getString(R.string.auth_self_sms_success_desc))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pendingIntent, true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            val notificationManager = NotificationManagerCompat.from(context)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                notificationManager.notify(9999, notif)
            }
        } catch (e: Exception) {
            Log.w("AuthScreen", "Failed to trigger bring-to-front notification fallback: ${e.message}")
        }

        onAuthenticate()
    }

    fun launchSmsApp(result: SimVerificationService.VerificationResult.Success) {
        try {
            val challenge = activeChallenge ?: run {
                val targetSlot = activeSimSlots.firstOrNull { it.slotIndex == selectedSlotIndex }
                val subId = targetSlot?.subscriptionId ?: -1
                val newChallenge = SelfSmsVerificationManager.prepareChallenge(context, result.fullPhoneNumber, subId)
                activeChallenge = newChallenge
                newChallenge
            }
            context.startActivity(challenge.intent)
        } catch (e: Exception) {
            Log.e("AuthScreen", "Failed to launch SMS app: ${e.message}")
            otpErrorMessage = context.getString(R.string.auth_self_sms_error_launch_sms)
        }
    }

    fun initiateOtpChallenge(result: SimVerificationService.VerificationResult.Success) {
        pendingSuccessResult = result
        otpErrorMessage = null
        try {
            val targetSlot = activeSimSlots.firstOrNull { it.slotIndex == selectedSlotIndex }
            val subId = targetSlot?.subscriptionId ?: -1
            activeChallenge = SelfSmsVerificationManager.prepareChallenge(context, result.fullPhoneNumber, subId)
        } catch (e: Exception) {
            Log.e("AuthScreen", "Failed to prepare challenge: ${e.message}")
        }
        showOtpDialog = true
    }

    val smsConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val message = result.data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)
            if (!message.isNullOrBlank()) {
                val isValid = SelfSmsVerificationManager.verifyIncomingMessage(message)
                if (isValid) {
                    showOtpDialog = false
                    val res = pendingSuccessResult
                    if (res != null) {
                        completeLogin(res)
                    }
                } else {
                    otpErrorMessage = context.getString(R.string.auth_self_sms_invalid_code)
                }
            }
        }
    }

    DisposableEffect(showOtpDialog) {
        if (!showOtpDialog) return@DisposableEffect onDispose {}

        val smsConsentReceiver = object : BroadcastReceiver() {
            override fun onReceive(recvContext: Context?, intent: Intent?) {
                if (SmsRetriever.SMS_RETRIEVED_ACTION == intent?.action) {
                    val extras = intent?.extras ?: return
                    @Suppress("DEPRECATION")
                    val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        extras.getParcelable(SmsRetriever.EXTRA_STATUS, com.google.android.gms.common.api.Status::class.java)
                    } else {
                        extras.getParcelable(SmsRetriever.EXTRA_STATUS)
                    }
                    if (status?.statusCode == com.google.android.gms.common.api.CommonStatusCodes.SUCCESS) {
                        // 1. Direct message retrieval via SMS Retriever API (0-tap automated, no contact filter)
                        val directMessage = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE)
                        if (!directMessage.isNullOrBlank()) {
                            val isValid = SelfSmsVerificationManager.verifyIncomingMessage(directMessage)
                            if (isValid) {
                                showOtpDialog = false
                                val res = pendingSuccessResult
                                if (res != null) {
                                    completeLogin(res)
                                }
                                return
                            }
                        }

                        // 2. Consent Intent via SMS User Consent API (1-tap system dialog)
                        @Suppress("DEPRECATION")
                        val consentIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT, Intent::class.java)
                        } else {
                            extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT)
                        }
                        if (consentIntent != null) {
                            try {
                                smsConsentLauncher.launch(consentIntent)
                            } catch (e: Exception) {
                                Log.w("AuthScreen", "Failed to launch SMS consent: ${e.message}")
                            }
                        }
                    }
                }
            }
        }

        val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        ContextCompat.registerReceiver(
            context,
            smsConsentReceiver,
            intentFilter,
            SmsRetriever.SEND_PERMISSION,
            null,
            ContextCompat.RECEIVER_EXPORTED
        )

        onDispose {
            try {
                context.unregisterReceiver(smsConsentReceiver)
            } catch (_: Exception) {}
        }
    }

    fun handleVerificationResult(result: SimVerificationService.VerificationResult) {
        when (result) {
            is SimVerificationService.VerificationResult.Success -> {
                var isUserAdmin = AdminSecurityHelper.isAdmin(result.fullPhoneNumber, context)
                if (!isUserAdmin) {
                    isUserAdmin = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                        AdminSecurityHelper.verifyWithBackendSync(context, result.fullPhoneNumber)
                    }
                }
                if (result.requiresOtpChallenge && !isUserAdmin) {
                    initiateOtpChallenge(result)
                } else {
                    completeLogin(result)
                }
            }
            is SimVerificationService.VerificationResult.Failure -> {
                errorMessage = if (result.formatArgs.isNotEmpty()) {
                    context.getString(result.messageResId, *result.formatArgs.toTypedArray())
                } else {
                    context.getString(result.messageResId)
                }
            }
        }
        isVerifying = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        val hasPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val hasPhoneNumbers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
        } else true

        val canReadSim = hasPhoneState || hasPhoneNumbers ||
                grantResults[Manifest.permission.READ_PHONE_STATE] == true ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && grantResults[Manifest.permission.READ_PHONE_NUMBERS] == true)

        if (!canReadSim) {
            errorMessage = context.getString(R.string.auth_error_permission)
            isVerifying = false
            return@rememberLauncherForActivityResult
        }

        refreshSimData(selectedSlotIndex)
        val result = simService.verifyIdentityWithSim(
            context = context,
            dialCode = selectedCountry.dialCode,
            nationalNumber = nationalPhone,
            targetSlotIndex = selectedSlotIndex
        )
        handleVerificationResult(result)
    }

    var showRestoreSourceDialog by remember { mutableStateOf(false) }

    if (showRestoreSourceDialog) {
        VaultRestoreSourceDialog(
            onDismiss = { showRestoreSourceDialog = false },
            onRestoreSuccess = { stats ->
                showRestoreSourceDialog = false
                onAuthenticate()
            }
        )
    }

    if (showCountryPicker) {
        CountryCodePickerDialog(
            selectedCountry = selectedCountry,
            onCountrySelected = { selectedCountry = it },
            onDismissRequest = { showCountryPicker = false }
        )
    }

    if (showMandatoryProfileDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showMandatoryProfileDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.auth_mandatory_profile_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.auth_mandatory_profile_desc),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMandatoryProfileDialog = false
                        if (selectedAvatarPath.isNullOrBlank()) {
                            photoPickerLauncher.launch("image/*")
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.auth_mandatory_profile_btn),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }

    if (showOtpDialog && pendingSuccessResult != null) {
        OtpVerificationDialog(
            phoneNumber = pendingSuccessResult!!.fullPhoneNumber,
            sessionToken = activeChallenge?.token ?: SelfSmsVerificationManager.getActiveToken(),
            errorMessage = otpErrorMessage,
            onOpenSmsApp = {
                pendingSuccessResult?.let { launchSmsApp(it) }
            },
            onResend = {
                val res = pendingSuccessResult
                if (res != null) {
                    try {
                        val targetSlot = activeSimSlots.firstOrNull { it.slotIndex == selectedSlotIndex }
                        val subId = targetSlot?.subscriptionId ?: -1
                        val newChallenge = SelfSmsVerificationManager.prepareChallenge(context, res.fullPhoneNumber, subId)
                        activeChallenge = newChallenge
                        context.startActivity(newChallenge.intent)
                    } catch (e: Exception) {
                        Log.e("AuthScreen", "Failed to resend SMS: ${e.message}")
                    }
                }
            },
            onValidateToken = { text ->
                val isValid = SelfSmsVerificationManager.verifyToken(text) ||
                        SelfSmsVerificationManager.verifyIncomingMessage(text)
                if (isValid) {
                    showOtpDialog = false
                    val res = pendingSuccessResult
                    if (res != null) {
                        completeLogin(res)
                    }
                } else {
                    otpErrorMessage = context.getString(R.string.auth_self_sms_invalid_code)
                }
            },
            onDismiss = {
                showOtpDialog = false
                pendingSuccessResult = null
                isVerifying = false
                activeChallenge = null
                SelfSmsVerificationManager.clear()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Header Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (simDetails.isPresent) Icons.Default.SimCard else Icons.Default.SimCardAlert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (targetSimSlotIndex >= 0) stringResource(R.string.auth_add_line_title) else stringResource(R.string.auth_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.auth_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (onCancel != null) {
                    OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(10.dp)) {
                        Text("Annuler", fontSize = 12.sp)
                    }
                }
            }

            // Master Interactive SIM Selector Card (100% i18n & Premium Dual-SIM Switcher)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (simDetails.isPresent) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header: Status dot + Section Title + Hardware Badge Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (simDetails.isPresent && simDetails.isReady) OrbisColorPalette.StatusActive
                                        else MaterialTheme.colorScheme.error
                                    )
                                    .alpha(if (simDetails.isPresent) pulseAlpha else 1f)
                            )
                            Text(
                                text = if (activeSimSlots.size > 1) {
                                    stringResource(R.string.auth_dual_sim_detected)
                                } else if (simDetails.isPresent) {
                                    stringResource(R.string.auth_sim_detected_active, simDetails.operatorName, simDetails.countryIso)
                                } else {
                                    stringResource(R.string.auth_sim_detected_none)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (simDetails.isPresent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        // Status Badge Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (simDetails.isPresent) OrbisColorPalette.StatusActive.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (simDetails.isPresent) stringResource(R.string.auth_sim_hardware_ok) else stringResource(R.string.auth_sim_hardware_req),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (simDetails.isPresent) OrbisColorPalette.StatusActive else MaterialTheme.colorScheme.error,
                                maxLines = 1
                            )
                        }
                    }

                    // Interactive Dual-SIM Switcher Cards
                    if (activeSimSlots.size > 1) {
                        Text(
                            text = stringResource(R.string.auth_sim_tap_to_switch),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            activeSimSlots.forEach { slot ->
                                val isSelected = selectedSlotIndex == slot.slotIndex
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            selectedSlotIndex = slot.slotIndex
                                            refreshSimData(slot.slotIndex)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SimCard,
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = if (slot.slotIndex == 0) stringResource(R.string.auth_sim_slot_1) else stringResource(R.string.auth_sim_slot_2),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 12.sp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = slot.operatorName.ifBlank { stringResource(R.string.auth_sim_slot_secondary) },
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Inputs Form
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // WhatsApp-style Circular Profile Photo Picker
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 2.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        OrbisAvatar(
                            avatarPath = selectedAvatarPath,
                            name = displayName.ifBlank { "Orbis" },
                            size = 80.dp,
                            onClick = { photoPickerLauncher.launch("image/*") }
                        )

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .border(2.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .clickable { photoPickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = stringResource(R.string.auth_photo_label),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Text(
                        text = if (selectedAvatarPath != null) stringResource(R.string.auth_photo_change) else stringResource(R.string.auth_photo_add),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clickable { photoPickerLauncher.launch("image/*") }
                    )

                    val fullNumber = selectedCountry.dialCode + nationalPhone.trim()
                    val isNameReservedError = remember(displayName, fullNumber) {
                        !com.sha.orbis.admin.AdminSecurityHelper.canUseName(fullNumber, displayName)
                    }

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.auth_name_label)) },
                        singleLine = true,
                        isError = isNameReservedError,
                        supportingText = {
                            if (isNameReservedError) {
                                Text(
                                    text = stringResource(R.string.error_reserved_username),
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    // Phone Number with Country Code Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .clickable { showCountryPicker = true }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(selectedCountry.flagEmoji, fontSize = 18.sp)
                                Text(
                                    text = selectedCountry.dialCode,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = nationalPhone,
                            onValueChange = { nationalPhone = it.filter { ch -> ch.isDigit() } },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.auth_phone_label)) },
                            placeholder = { Text("655123456") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }

                    if (nationalPhone.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.auth_intl_format, "${selectedCountry.dialCode}${nationalPhone.trimStart('0')}"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Expandable "Personnaliser mon profil (Optionnel)"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .clickable { showEnrichSection = !showEnrichSection }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Column {
                                Text(stringResource(R.string.auth_profile_enrich_title), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(stringResource(R.string.auth_profile_enrich_subtitle), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Icon(
                            imageVector = if (showEnrichSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = showEnrichSection) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Bio Field
                            OutlinedTextField(
                                value = bio,
                                onValueChange = { bio = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.auth_bio_label)) },
                                placeholder = { Text(stringResource(R.string.auth_bio_hint)) },
                                singleLine = false,
                                maxLines = 2,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )

                            // Quick Bio Preset Chips
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val presets = listOf("🔒 Chiffré & Souverain", "⚡ Disponible par GSM", "🚀 Membre Orbis", "🛡️ Hors-ligne E2EE")
                                presets.forEach { preset ->
                                    AssistChip(
                                        onClick = { bio = preset },
                                        label = { Text(preset, fontSize = 11.sp) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            labelColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }

                            // Job & Location Fields in a Row
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = jobTitle,
                                    onValueChange = { jobTitle = it },
                                    modifier = Modifier.weight(1f),
                                    label = { Text(stringResource(R.string.auth_job_label)) },
                                    placeholder = { Text(stringResource(R.string.auth_job_hint)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )

                                OutlinedTextField(
                                    value = location,
                                    onValueChange = { location = it },
                                    modifier = Modifier.weight(1f),
                                    label = { Text(stringResource(R.string.auth_location_label)) },
                                    placeholder = { Text(stringResource(R.string.auth_location_hint)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }

                            // Interests Tags
                            Text(
                                text = stringResource(R.string.auth_interests_label),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            val interestOptions = listOf(
                                stringResource(R.string.auth_chip_tech),
                                stringResource(R.string.auth_chip_design),
                                stringResource(R.string.auth_chip_science),
                                stringResource(R.string.auth_chip_business),
                                stringResource(R.string.auth_chip_sport),
                                stringResource(R.string.auth_chip_music),
                                stringResource(R.string.auth_chip_crypto),
                                stringResource(R.string.auth_chip_nature)
                            )

                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                interestOptions.forEach { interest ->
                                    val isSelected = selectedInterests.contains(interest)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedInterests = if (isSelected) selectedInterests - interest else selectedInterests + interest
                                        },
                                        label = { Text(interest, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                            selectedLabelColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }
                        }
                    }

                    if (errorMessage.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // Action Buttons
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Sovereign & Free Network Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.auth_self_sms_banner_sovereign),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Button(
                onClick = {
                    val fullNum = selectedCountry.dialCode + nationalPhone.trim()
                    var isUserAdmin = AdminSecurityHelper.isAdmin(fullNum, context)
                    if (!isUserAdmin && nationalPhone.filter { it.isDigit() }.length >= 7) {
                        isUserAdmin = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                            AdminSecurityHelper.verifyWithBackendSync(context, fullNum)
                        }
                    }

                    if (isUserAdmin) {
                        if (displayName.isBlank() || displayName == "Utilisateur") {
                            displayName = "O R B I S net"
                        }
                        if (selectedAvatarPath.isNullOrBlank()) {
                            selectedAvatarPath = AvatarManager.ensureOfficialAppAvatar(context)
                        }
                    } else {
                        val cleanName = displayName.trim()
                        val isGenericName = cleanName.equals("Utilisateur", ignoreCase = true) ||
                                            cleanName.startsWith("Ligne ", ignoreCase = true) ||
                                            cleanName.isEmpty()
                        val isMissingPhoto = selectedAvatarPath.isNullOrBlank()

                        if (isGenericName || isMissingPhoto) {
                            showMandatoryProfileDialog = true
                            return@Button
                        }
                        if (!AdminSecurityHelper.canUseName(fullNum, displayName, context)) {
                            errorMessage = context.getString(R.string.error_reserved_username)
                            return@Button
                        }
                    }

                    if (nationalPhone.filter { it.isDigit() }.length < 7) {
                        errorMessage = context.getString(R.string.auth_error_phone)
                        return@Button
                    }

                    errorMessage = ""
                    isVerifying = true

                    val hasPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                    val hasPhoneNumbers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
                    } else true

                    if (hasPhoneState || hasPhoneNumbers) {
                        refreshSimData(selectedSlotIndex)
                        val result = simService.verifyIdentityWithSim(
                            context = context,
                            dialCode = selectedCountry.dialCode,
                            nationalNumber = nationalPhone,
                            targetSlotIndex = selectedSlotIndex
                        )

                        handleVerificationResult(result)
                    } else {
                        val permissionsToRequest = mutableListOf(
                            Manifest.permission.READ_PHONE_STATE
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            permissionsToRequest.add(Manifest.permission.READ_PHONE_NUMBERS)
                        }
                        permissionLauncher.launch(permissionsToRequest.toTypedArray())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isVerifying,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = if (isVerifying) stringResource(R.string.auth_verifying) else stringResource(R.string.auth_btn_connect),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    if (!isVerifying) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Restore from .orbis backup (for phone switch or re-install)
            TextButton(
                onClick = { showRestoreSourceDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.vault_restore_onboarding_btn),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
