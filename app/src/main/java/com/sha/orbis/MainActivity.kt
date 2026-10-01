package com.sha.orbis

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sha.orbis.call.OrbisCallManager
import com.sha.orbis.call.OrbisMissedCallManager
import com.sha.orbis.data.SessionManager
import com.sha.orbis.permissions.PermissionGate
import com.sha.orbis.security.BiometricAuthManager
import com.sha.orbis.storage.ConversationRepository
import com.sha.orbis.storage.FriendRequestRepository
import com.sha.orbis.ui.app.OrbisApp
import com.sha.orbis.ui.auth.AuthScreen
import com.sha.orbis.ui.i18n.LocaleManager
import com.sha.orbis.ui.landing.LandingScreen
import com.sha.orbis.ui.onboarding.OnboardingFlow
import com.sha.orbis.ui.splash.SplashScreen
import com.sha.orbis.ui.theme.OrbisTheme
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import com.sha.orbis.social.SocialPost
import com.sha.orbis.storage.SocialRepository
import com.sha.orbis.ui.share.ExternalSharePayload
import com.sha.orbis.ui.share.ExternalShareTargetDialog
import com.sha.orbis.ui.social.CreatePostDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppScreenState {
    SPLASH,
    ONBOARDING,
    AUTH,
    LANDING,
    BIOMETRIC_LOCK,
    SIM_LOCK,
    MAIN
}

data class PendingNavIntent(
    val convId: String? = null,
    val phone: String? = null,
    val displayName: String? = null,
    val postId: String? = null,
    val actionType: String? = null,
    val isCallback: Boolean = false,
    val isVideoCall: Boolean = false
)

class MainActivity : FragmentActivity() {

    private var screenState by mutableStateOf(AppScreenState.SPLASH)
    private var pendingNav by mutableStateOf<PendingNavIntent?>(null)
    private var simLockReason by mutableStateOf(com.sha.orbis.security.SimSecurityManager.SimLockReason.NO_SIM)
    private var pendingExternalShare by mutableStateOf<ExternalSharePayload?>(null)
    private var pendingCreatePostText by mutableStateOf<String?>(null)
    private var showExternalCreatePostDialog by mutableStateOf(false)

    private fun extractExternalShareIntent(intent: Intent?): ExternalSharePayload? {
        if (intent == null) return null
        val action = intent.action ?: return null
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null

        val rawText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val text = if (!rawText.isNullOrBlank()) {
            if (rawText.length > 4096) rawText.take(4096) else rawText
        } else null
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.take(256)
        val mimeType = intent.type

        val singleUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }

        val multipleUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
        }

        if (text.isNullOrBlank() && singleUri == null && multipleUris.isEmpty()) return null

        return ExternalSharePayload(
            text = text,
            subject = subject,
            singleMediaUri = singleUri,
            multipleMediaUris = multipleUris,
            mimeType = mimeType
        )
    }

    private fun extractNavIntent(intent: Intent?): PendingNavIntent? {
        if (intent == null) return null
        val convId = intent.getStringExtra("extra_conv_id")
        val rawPhone = intent.getStringExtra("extra_phone") ?: intent.getStringExtra("extra_peer_phone")
        val rawName = intent.getStringExtra("extra_title") ?: intent.getStringExtra("extra_peer_name")
        val postId = intent.getStringExtra("extra_post_id")
        val actionType = intent.getStringExtra("extra_action_type")
        val isCallback = intent.getBooleanExtra("extra_is_callback", false) ||
                intent.action == com.sha.orbis.call.OrbisCallActionReceiver.ACTION_CALL_BACK
        val isVideo = intent.getBooleanExtra("extra_is_video", false)

        return when {
            isCallback && !rawPhone.isNullOrBlank() -> PendingNavIntent(
                convId = convId,
                phone = rawPhone,
                displayName = rawName,
                isCallback = true,
                isVideoCall = isVideo
            )
            !convId.isNullOrBlank() || !rawPhone.isNullOrBlank() || !postId.isNullOrBlank() -> PendingNavIntent(
                convId = convId,
                phone = rawPhone,
                displayName = rawName,
                postId = postId,
                actionType = actionType,
                isCallback = false,
                isVideoCall = isVideo
            )
            else -> null
        }
    }

    private fun handlePendingCallback(nav: PendingNavIntent) {
        val phone = nav.phone ?: return
        OrbisMissedCallManager.cancelMissedCallNotification(this, phone)
        val sessionManager = SessionManager(this)
        val convRepo = ConversationRepository(this, sessionManager.activeAccountId)
        val savedContacts = convRepo.loadContacts()
        val contact = savedContacts.find { FriendRequestRepository.isSamePhone(it.phone, phone) }
        val peerName = nav.displayName?.takeIf { it.isNotBlank() } ?: contact?.name ?: phone
        val peerAvatar = contact?.avatarPath
        val peerNostrKey = contact?.publicKey?.takeIf { FriendRequestRepository.isValidNostrKey(it) }

        OrbisCallManager.startOutgoingCall(
            context = this,
            peerPhone = phone,
            peerName = peerName,
            peerAvatar = peerAvatar,
            myPhone = sessionManager.userPhone,
            peerNostrKey = peerNostrKey,
            isVideoCall = nav.isVideoCall
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val call = com.sha.orbis.call.OrbisCallManager.callState.value
        val isIncoming = intent?.getBooleanExtra("extra_incoming_call", false) == true
        val isRinging = isIncoming || call?.status == com.sha.orbis.call.CallStatus.INCOMING_RINGING || call?.status == com.sha.orbis.call.CallStatus.OUTGOING_RINGING
        val isVideo = call?.isVideoCall == true
        updateLockScreenFlags(showWhenLocked = call != null || isIncoming, keepScreenOn = isRinging || isVideo)
    }

    override fun onResume() {
        super.onResume()
        val call = com.sha.orbis.call.OrbisCallManager.callState.value
        val isIncoming = intent?.getBooleanExtra("extra_incoming_call", false) == true
        val isRinging = isIncoming || call?.status == com.sha.orbis.call.CallStatus.INCOMING_RINGING || call?.status == com.sha.orbis.call.CallStatus.OUTGOING_RINGING
        val isVideo = call?.isVideoCall == true
        updateLockScreenFlags(showWhenLocked = call != null || isIncoming, keepScreenOn = isRinging || isVideo)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val call = com.sha.orbis.call.OrbisCallManager.callState.value
        val isIncoming = intent.getBooleanExtra("extra_incoming_call", false)
        val isRinging = isIncoming || call?.status == com.sha.orbis.call.CallStatus.INCOMING_RINGING || call?.status == com.sha.orbis.call.CallStatus.OUTGOING_RINGING
        val isVideo = call?.isVideoCall == true
        val isCallActive = call != null || isIncoming
        updateLockScreenFlags(showWhenLocked = isCallActive, keepScreenOn = isRinging || isVideo)

        if (isCallActive) {
            // ── Demande automatique des autorisations système natives (Audio/Vidéo/Notifications) ──
        if (!PermissionGate.hasCallPermissions(this, isVideo = true)) {
            val needed = PermissionGate.requiredCallPermissions(this, isVideo = true)
            if (needed.isNotEmpty()) {
                requestPermissions(needed, 1002)
            }
        }


        val sessionManager = SessionManager(this)
            if (sessionManager.isAuthenticated) {
                screenState = AppScreenState.MAIN
            }
        }

        val nav = extractNavIntent(intent)
        if (nav != null) {
            if (!nav.phone.isNullOrBlank()) {
                OrbisMissedCallManager.cancelMissedCallNotification(this, nav.phone)
            }
            if (nav.isCallback && !nav.phone.isNullOrBlank()) {
                handlePendingCallback(nav)
                pendingNav = null
            } else {
                val handler = com.sha.orbis.notification.InAppNotificationManager.onNavigateToConversation
                if (screenState == AppScreenState.MAIN && handler != null) {
                    handler.invoke(nav.convId, nav.phone, false)
                    pendingNav = null
                } else {
                    pendingNav = nav
                }
            }
        }

        val sharePayload = extractExternalShareIntent(intent)
        if (sharePayload != null) {
            pendingExternalShare = sharePayload
            val sessionManager = SessionManager(this)
            if (sessionManager.isAuthenticated && screenState != AppScreenState.MAIN) {
                screenState = AppScreenState.MAIN
            }
        }
    }

    fun updateLockScreenFlags(showWhenLocked: Boolean, keepScreenOn: Boolean = false) {
        val km = getSystemService(android.content.Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        val isLocked = km?.isKeyguardLocked == true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(showWhenLocked)
            setTurnScreenOn(showWhenLocked)
        }

        // Sur les ROMs chinoises (Vivo OriginOS, Xiaomi HyperOS, Oppo ColorOS), les window flags restent indispensables
        @Suppress("DEPRECATION")
        if (showWhenLocked) {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        } else if (isLocked) {
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ── Protection contre les captures d'écran et enregistrements d'écran dans toute l'application ──
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        val call = com.sha.orbis.call.OrbisCallManager.callState.value
        val isIncomingCall = intent?.getBooleanExtra("extra_incoming_call", false) == true || call != null
        val isRinging = isIncomingCall || call?.status == com.sha.orbis.call.CallStatus.INCOMING_RINGING || call?.status == com.sha.orbis.call.CallStatus.OUTGOING_RINGING
        val isVideo = call?.isVideoCall == true
        updateLockScreenFlags(showWhenLocked = isIncomingCall, keepScreenOn = isRinging || isVideo)
        com.sha.orbis.security.AppIntegrityGuard.verifyIntegrity(this)
        LocaleManager.applySavedLocale(this)
        enableEdgeToEdge()

        // ── Demande automatique des autorisations système natives (Audio/Vidéo/Notifications) ──
        if (!PermissionGate.hasCallPermissions(this, isVideo = true)) {
            val needed = PermissionGate.requiredCallPermissions(this, isVideo = true)
            if (needed.isNotEmpty()) {
                requestPermissions(needed, 1002)
            }
        }
        // ── Popup native Android d'optimisation batterie (uniquement si ce n'est pas un appel entrant et pas encore demandé) ──
        if (!isIncomingCall && com.sha.orbis.security.BatteryOptimizationHelper.shouldPrompt(this)) {
            com.sha.orbis.security.BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(this)
        }

        val sessionManager = SessionManager(this)
        screenState = if (isIncomingCall && sessionManager.isAuthenticated) AppScreenState.MAIN else AppScreenState.SPLASH
        pendingNav = extractNavIntent(intent)
        pendingExternalShare = extractExternalShareIntent(intent)

        // Démarrage du monitoring SIM en temps réel (retrait / remplacement de carte)
        com.sha.orbis.security.SimSecurityManager.startRealTimeMonitoring(this) { status ->
            if (status is com.sha.orbis.security.SimSecurityManager.SimSecurityStatus.Invalid && sessionManager.isAuthenticated) {
                simLockReason = status.reason
                screenState = AppScreenState.SIM_LOCK
            }
        }

        setContent {
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        val currentCall = com.sha.orbis.call.OrbisCallManager.callState.value
                        if (currentCall?.status == com.sha.orbis.call.CallStatus.INCOMING_RINGING ||
                            currentCall?.status == com.sha.orbis.call.CallStatus.CONNECTING ||
                            currentCall?.status == com.sha.orbis.call.CallStatus.CONNECTED) {
                            com.sha.orbis.call.OrbisCallNotificationHelper.cancelCallNotification(this@MainActivity)
                        }
                        if (sessionManager.isAuthenticated) {
                            // SIM security check before resuming network services
                            val simStatus = com.sha.orbis.security.SimSecurityManager.verifyActiveSim(this@MainActivity)
                            if (simStatus is com.sha.orbis.security.SimSecurityManager.SimSecurityStatus.Invalid) {
                                simLockReason = simStatus.reason
                                screenState = AppScreenState.SIM_LOCK
                            } else {
                                com.sha.orbis.nostr.service.NostrForegroundService.start(this@MainActivity)
                                com.sha.orbis.nostr.service.NostrSyncManager.getInstance(this@MainActivity).reconnect(force = false)
                                com.sha.orbis.sync.scheduler.SovereignSyncScheduler.onAppOpened(this@MainActivity)
                                com.sha.orbis.data.OrbisBadgeHub.refresh(this@MainActivity)
                            }
                        } else {
                            com.sha.orbis.data.OrbisBadgeHub.refresh(this@MainActivity)
                        }
                    } else if (event == Lifecycle.Event.ON_STOP) {
                        val currentCall = com.sha.orbis.call.OrbisCallManager.callState.value
                        if (currentCall?.status == com.sha.orbis.call.CallStatus.INCOMING_RINGING) {
                            com.sha.orbis.call.OrbisCallNotificationHelper.showIncomingCallNotification(
                                context = this@MainActivity,
                                callId = currentCall.callId,
                                callerPhone = currentCall.peerPhone,
                                callerName = currentCall.peerName
                            )
                        }
                        // Re-lock app with biometric/PIN when app is stopped (screen off, user switches apps)
                        if (sessionManager.isAuthenticated && sessionManager.isBiometricEnabled && (screenState == AppScreenState.MAIN || screenState == AppScreenState.LANDING)) {
                            screenState = AppScreenState.BIOMETRIC_LOCK
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            var showOemAutoStartDialog by remember { mutableStateOf(false) }

            LaunchedEffect(screenState) {
                if (screenState == AppScreenState.MAIN && !isIncomingCall) {
                    if (com.sha.orbis.security.OemAutoStartHelper.shouldPrompt(this@MainActivity)) {
                        showOemAutoStartDialog = true
                    }
                }
            }

            val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
            ) {
                // Permissions updated
            }

            val voiceCallPermissionLauncher = com.sha.orbis.permissions.rememberOrbisPermissionLauncher(
                permission = com.sha.orbis.permissions.OrbisPermission.CALL_VOICE,
                onGranted = {
                    com.sha.orbis.call.OrbisCallManager.acceptCall(withVideo = false)
                }
            )

            val videoCallPermissionLauncher = com.sha.orbis.permissions.rememberOrbisPermissionLauncher(
                permission = com.sha.orbis.permissions.OrbisPermission.CALL_VIDEO,
                onGranted = {
                    com.sha.orbis.call.OrbisCallManager.acceptCall(withVideo = true)
                }
            )

            fun acceptIncomingCall(withVideo: Boolean) {
                if (withVideo) {
                    videoCallPermissionLauncher.launch()
                } else {
                    voiceCallPermissionLauncher.launch()
                }
            }

            LaunchedEffect(screenState) {
                if (screenState == AppScreenState.MAIN || screenState == AppScreenState.AUTH || screenState == AppScreenState.LANDING) {
                    val needed = com.sha.orbis.permissions.PermissionGate.requiredPermissions().filter { p ->
                        androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, p) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    if (needed.isNotEmpty()) {
                        permissionLauncher.launch(needed.toTypedArray())
                    }
                }
            }

            fun triggerBiometricAuth(onSuccess: () -> Unit) {
                if (BiometricAuthManager.isBiometricAvailable(this)) {
                    BiometricAuthManager.authenticate(
                        activity = this,
                        onSuccess = onSuccess,
                        onError = { /* Let user retry manually */ }
                    )
                } else {
                    onSuccess()
                }
            }

            LaunchedEffect(Unit) {
                com.sha.orbis.call.OrbisCallManager.init(this@MainActivity)
            }

            val activeCall by com.sha.orbis.call.OrbisCallManager.callState.collectAsState()
            val callFeedback by com.sha.orbis.call.OrbisCallManager.callFeedback.collectAsState()

            LaunchedEffect(activeCall) {
                val call = activeCall
                val isRinging = call?.status == com.sha.orbis.call.CallStatus.INCOMING_RINGING ||
                               call?.status == com.sha.orbis.call.CallStatus.OUTGOING_RINGING ||
                               call?.status == com.sha.orbis.call.CallStatus.OUTGOING_CALLING
                val isVideo = call?.isVideoCall == true
                updateLockScreenFlags(showWhenLocked = call != null, keepScreenOn = isRinging || isVideo)
                if (activeCall != null && androidx.core.content.ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        android.Manifest.permission.RECORD_AUDIO
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO))
                }
            }

            com.sha.orbis.ui.i18n.ProvideOrbisLocale {
                OrbisTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val currentFeedback = callFeedback
                        if (currentFeedback != null) {
                            com.sha.orbis.ui.call.OrbisCallFeedbackDialog(
                                feedback = currentFeedback,
                                onDismiss = { com.sha.orbis.call.OrbisCallManager.clearCallFeedback() },
                                onOpenChat = { phone ->
                                    com.sha.orbis.call.OrbisCallManager.clearCallFeedback()
                                    com.sha.orbis.notification.InAppNotificationManager.onNavigateToConversation?.invoke(
                                        null,
                                        phone,
                                        false
                                    )
                                }
                            )
                        }

                        if (showOemAutoStartDialog) {
                            AlertDialog(
                                onDismissRequest = {
                                    com.sha.orbis.security.OemAutoStartHelper.markPrompted(this@MainActivity)
                                    showOemAutoStartDialog = false
                                },
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Text(text = "Appels & Messages en veille", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    }
                                },
                                text = {
                                    Text(
                                        text = "${com.sha.orbis.security.OemAutoStartHelper.getBrandInstructions(this@MainActivity)}\n\nCette autorisation est indispensable pour que votre téléphone sonne lors des appels entrants lorsque l'application est fermée.",
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                confirmButton = {
                                    Button(onClick = {
                                        showOemAutoStartDialog = false
                                        com.sha.orbis.security.OemAutoStartHelper.openAutoStartSettings(this@MainActivity)
                                    }) {
                                        Text("Autoriser")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = {
                                        com.sha.orbis.security.OemAutoStartHelper.markPrompted(this@MainActivity)
                                        showOemAutoStartDialog = false
                                    }) {
                                        Text("Plus tard")
                                    }
                                }
                            )
                        }

                        val callSession = activeCall
                        if (callSession != null) {
                            if (callSession.isVideoCall) {
                                com.sha.orbis.ui.call.OrbisVideoCallScreen(
                                    session = callSession,
                                    onAcceptVideoCall = {
                                        acceptIncomingCall(withVideo = true)
                                    },
                                    onAcceptVoiceOnly = {
                                        acceptIncomingCall(withVideo = false)
                                    },
                                    onDeclineCall = { com.sha.orbis.call.OrbisCallManager.rejectCall() },
                                    onToggleCamera = { com.sha.orbis.call.OrbisCallManager.toggleCamera() },
                                    onSwitchCamera = { com.sha.orbis.call.OrbisCallManager.switchCamera() },
                                    onToggleMute = { com.sha.orbis.call.OrbisCallManager.toggleMute() },
                                    onToggleSpeaker = { com.sha.orbis.call.OrbisCallManager.toggleSpeaker() },
                                    onEndCall = { com.sha.orbis.call.OrbisCallManager.endCall() }
                                )
                            } else {
                                com.sha.orbis.ui.call.OrbisVoiceCallScreen(
                                    session = callSession,
                                    onAcceptCall = {
                                        acceptIncomingCall(withVideo = false)
                                    },
                                    onDeclineCall = { com.sha.orbis.call.OrbisCallManager.rejectCall() },
                                    onToggleMute = { com.sha.orbis.call.OrbisCallManager.toggleMute() },
                                    onToggleSpeaker = { com.sha.orbis.call.OrbisCallManager.toggleSpeaker() },
                                    onEndCall = { com.sha.orbis.call.OrbisCallManager.endCall() }
                                )
                            }
                        } else {
                            AnimatedContent(
                                targetState = screenState,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "main_screen_state"
                            ) { state ->
                                when (state) {
                                    AppScreenState.SPLASH -> {
                                        SplashScreen(onFinish = {
                                            screenState = when {
                                                !sessionManager.isOnboardingCompleted -> AppScreenState.ONBOARDING
                                                !sessionManager.isAuthenticated -> AppScreenState.AUTH
                                                sessionManager.isBiometricEnabled -> AppScreenState.BIOMETRIC_LOCK
                                                else -> AppScreenState.MAIN
                                            }
                                        })
                                    }
                                    AppScreenState.ONBOARDING -> {
                                        OnboardingFlow(onComplete = {
                                            sessionManager.isOnboardingCompleted = true
                                            screenState = if (!sessionManager.isAuthenticated) AppScreenState.AUTH else AppScreenState.LANDING
                                        })
                                    }
                                    AppScreenState.AUTH -> {
                                        AuthScreen(onAuthenticate = {
                                            sessionManager.isAuthenticated = true
                                            com.sha.orbis.nostr.service.NostrForegroundService.start(this@MainActivity)
                                            screenState = AppScreenState.LANDING
                                        })
                                    }
                                    AppScreenState.LANDING -> {
                                        LandingScreen(onContinue = {
                                            screenState = AppScreenState.MAIN
                                        })
                                    }
                                    AppScreenState.BIOMETRIC_LOCK -> {
                                        LaunchedEffect(Unit) {
                                            triggerBiometricAuth(onSuccess = { screenState = AppScreenState.MAIN })
                                        }
                                        BiometricLockScreen(onUnlockClick = {
                                            triggerBiometricAuth(onSuccess = { screenState = AppScreenState.MAIN })
                                        })
                                    }
                                    AppScreenState.SIM_LOCK -> {
                                        com.sha.orbis.ui.security.SimLockScreen(
                                            lockReason = simLockReason,
                                            registeredPhone = sessionManager.activeAccount?.phoneNumber ?: "",
                                            onRetry = {
                                                val status = com.sha.orbis.security.SimSecurityManager.verifyActiveSim(this@MainActivity)
                                                if (status is com.sha.orbis.security.SimSecurityManager.SimSecurityStatus.Valid) {
                                                    screenState = if (sessionManager.isBiometricEnabled) AppScreenState.BIOMETRIC_LOCK else AppScreenState.MAIN
                                                } else if (status is com.sha.orbis.security.SimSecurityManager.SimSecurityStatus.Invalid) {
                                                    simLockReason = status.reason
                                                }
                                            },
                                            onSignOut = {
                                                sessionManager.isAuthenticated = false
                                                screenState = AppScreenState.AUTH
                                            }
                                        )
                                    }
                                    AppScreenState.MAIN -> {
                                        LaunchedEffect(Unit) {
                                            com.sha.orbis.nostr.service.NostrForegroundService.start(this@MainActivity)
                                        }
                                        LaunchedEffect(pendingNav) {
                                            val nav = pendingNav
                                            if (nav != null) {
                                                if (!nav.phone.isNullOrBlank()) {
                                                    OrbisMissedCallManager.cancelMissedCallNotification(this@MainActivity, nav.phone)
                                                }
                                                if (nav.isCallback && !nav.phone.isNullOrBlank()) {
                                                    handlePendingCallback(nav)
                                                    pendingNav = null
                                                } else {
                                                    com.sha.orbis.notification.InAppNotificationManager.onNavigateToConversation?.invoke(
                                                        nav.convId,
                                                        nav.phone,
                                                        false
                                                    )
                                                    pendingNav = null
                                                }
                                            }
                                        }
                                        val initialConvId = intent?.getStringExtra("extra_conv_id")
                                        val initialPhone = intent?.getStringExtra("extra_phone")
                                        val initialTitle = intent?.getStringExtra("extra_title")
                                        val initialPostId = intent?.getStringExtra("extra_post_id")
                                        val initialActionType = intent?.getStringExtra("extra_action_type")
                                        OrbisApp(
                                            initialConvId = initialConvId,
                                            initialPhone = initialPhone,
                                            initialTitle = initialTitle,
                                            initialPostId = initialPostId,
                                            initialActionType = initialActionType
                                        )

                                        val externalShare = pendingExternalShare
                                        if (externalShare != null) {
                                            ExternalShareTargetDialog(
                                                payload = externalShare,
                                                onDismiss = { pendingExternalShare = null },
                                                onNavigateToConversation = { convId, phone, displayName ->
                                                    pendingExternalShare = null
                                                    com.sha.orbis.notification.InAppNotificationManager.onNavigateToConversation?.invoke(
                                                        convId,
                                                        phone,
                                                        false
                                                    )
                                                },
                                                onOpenCreatePost = { initialText, mediaUris ->
                                                    pendingExternalShare = null
                                                    pendingCreatePostText = initialText
                                                    showExternalCreatePostDialog = true
                                                }
                                            )
                                        }

                                        if (showExternalCreatePostDialog) {
                                            val session = remember { SessionManager(this@MainActivity) }
                                            val socialRepo = remember { SocialRepository(this@MainActivity) }
                                            val nostrSync = remember { com.sha.orbis.nostr.service.NostrSyncManager.getInstance(this@MainActivity) }
                                            val scope = rememberCoroutineScope()

                                            CreatePostDialog(
                                                authorPhone = session.userPhone,
                                                authorName = session.userName.ifBlank { "Moi" },
                                                authorAvatarPath = session.userAvatarPath,
                                                initialContent = pendingCreatePostText ?: "",
                                                onDismiss = {
                                                    showExternalCreatePostDialog = false
                                                    pendingCreatePostText = null
                                                },
                                                onPostCreated = { content, hashtags, poll, circleId, excludedCircleIds, excludedPhones, isOfficial, isPinned, role, mediaType, mediaPath, mediaData, mediaUrl ->
                                                    val normalizedAuthorPhone = com.sha.orbis.data.ContactsPickerHelper.normalizePhoneNumber(session.userPhone)
                                                    val newPost = SocialPost(
                                                        id = "post_${UUID.randomUUID().toString().take(8)}",
                                                        authorPhone = normalizedAuthorPhone,
                                                        authorName = session.userName.ifBlank { "Moi" },
                                                        authorAvatarPath = session.userAvatarPath,
                                                        content = content,
                                                        hashtags = hashtags,
                                                        timestamp = System.currentTimeMillis(),
                                                        targetCircleId = circleId,
                                                        excludedCircleIds = excludedCircleIds,
                                                        excludedPhones = excludedPhones,
                                                        rsaSignature = "sig_rsa_valid",
                                                        poll = poll,
                                                        reactions = emptyList(),
                                                        comments = emptyList(),
                                                        isPinned = isPinned,
                                                        isOfficialAnnouncement = isOfficial,
                                                        authorRole = role,
                                                        mediaType = mediaType,
                                                        mediaPath = mediaPath,
                                                        mediaData = mediaData,
                                                        mediaUrl = mediaUrl
                                                    )
                                                    socialRepo.addPost(newPost)
                                                    scope.launch(Dispatchers.IO) {
                                                        try {
                                                            nostrSync.publishPost(newPost)
                                                        } catch (e: Exception) {
                                                            android.util.Log.w("MainActivity", "Erreur diffusion Nostr: ${e.message}")
                                                        }
                                                    }
                                                    Toast.makeText(this@MainActivity, getString(R.string.external_share_publish_success), Toast.LENGTH_SHORT).show()
                                                    showExternalCreatePostDialog = false
                                                    pendingCreatePostText = null
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Top Heads-Up Floating Notification Banner (with OTP copy)
                        com.sha.orbis.ui.components.InAppNotificationBanner(
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        com.sha.orbis.security.SimSecurityManager.stopRealTimeMonitoring(this)
    }
}

@Composable
private fun BiometricLockScreen(onUnlockClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(46.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = androidx.compose.ui.res.stringResource(R.string.biometric_lock_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = androidx.compose.ui.res.stringResource(R.string.biometric_lock_desc),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onUnlockClick,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.height(50.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(androidx.compose.ui.res.stringResource(R.string.biometric_btn_unlock), fontWeight = FontWeight.Bold)
            }
        }
    }
}
