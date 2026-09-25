package com.sha.orbis.notification

import com.sha.orbis.util.OtpExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class InAppNotificationData(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val body: String,
    val senderPhone: String? = null,
    val convId: String? = null,
    val isPlainSms: Boolean = false,
    val avatarPath: String? = null,
    val otpCode: String? = null,
    val simSlot: Int = -1,
    val timestamp: Long = System.currentTimeMillis()
)

object InAppNotificationManager {

    private val _currentNotification = MutableStateFlow<InAppNotificationData?>(null)
    val currentNotification: StateFlow<InAppNotificationData?> = _currentNotification.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private var autoDismissJob: Job? = null

    var onNavigateToConversation: ((convId: String?, phone: String?, isPlainSms: Boolean) -> Unit)? = null

    fun postNotification(
        title: String,
        body: String,
        senderPhone: String? = null,
        convId: String? = null,
        isPlainSms: Boolean = false,
        avatarPath: String? = null,
        simSlot: Int = -1
    ) {
        val detectedOtp = OtpExtractor.extract(body)
        val data = InAppNotificationData(
            title = title,
            body = body,
            senderPhone = senderPhone,
            convId = convId,
            isPlainSms = isPlainSms,
            avatarPath = avatarPath,
            otpCode = detectedOtp,
            simSlot = simSlot
        )

        scope.launch {
            autoDismissJob?.cancel()
            _currentNotification.value = data

            // Auto dismiss after 6 seconds (or 10 seconds if it contains an OTP code so user has time to copy)
            val displayDuration = if (detectedOtp != null) 10_000L else 6_000L
            autoDismissJob = launch {
                delay(displayDuration)
                if (_currentNotification.value?.id == data.id) {
                    _currentNotification.value = null
                }
            }
        }
    }

    fun dismiss(id: String? = null) {
        if (id == null || _currentNotification.value?.id == id) {
            autoDismissJob?.cancel()
            _currentNotification.value = null
        }
    }

    fun clickCurrentNotification() {
        val current = _currentNotification.value ?: return
        dismiss(current.id)
        onNavigateToConversation?.invoke(current.convId, current.senderPhone, current.isPlainSms)
    }
}
