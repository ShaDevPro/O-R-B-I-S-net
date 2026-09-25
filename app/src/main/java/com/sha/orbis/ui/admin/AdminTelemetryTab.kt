package com.sha.orbis.ui.admin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.BuildConfig
import com.sha.orbis.R
import com.sha.orbis.telemetry.TelemetryManager
import com.sha.orbis.update.AppUpdateManager
import com.sha.orbis.update.RemoteAppConfig
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminTelemetryTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val telemetryManager = remember { TelemetryManager.getInstance(context) }
    val updateManager = remember { AppUpdateManager.getInstance(context) }

    var isUnlocked by remember { mutableStateOf(!telemetryManager.sessionAdminSecret.isNullOrBlank()) }
    var passwordInput by remember { mutableStateOf(telemetryManager.sessionAdminSecret ?: "") }
    var rememberPassword by remember { mutableStateOf(telemetryManager.isSecretSavedLocally) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var statsJson by remember { mutableStateOf<JSONObject?>(null) }
    var configState by remember { mutableStateOf(updateManager.currentConfig) }

    // Backend URL settings
    var showServerSettings by remember { mutableStateOf(false) }
    var customBackendUrl by remember { mutableStateOf(telemetryManager.backendUrl) }

    fun refreshStats() {
        isLoading = true
        scope.launch {
            val res = telemetryManager.fetchAdminStats()
            if (res.isSuccess && res.data != null) {
                statsJson = res.data.optJSONObject("stats")
                val cfgJson = res.data.optJSONObject("config")
                if (cfgJson != null) {
                    configState = RemoteAppConfig.fromJson(cfgJson)
                    updateManager.onConfigReceived(configState)
                }
            } else if (res.statusCode == 401 || res.statusCode == 403) {
                isUnlocked = false
                telemetryManager.sessionAdminSecret = null
                authErrorMessage = context.getString(R.string.telemetry_auth_invalid_pwd)
            } else {
                Toast.makeText(context, context.getString(R.string.telemetry_fetch_error), Toast.LENGTH_SHORT).show()
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        if (isUnlocked) {
            refreshStats()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // En-tête avec bouton de synchronisation et paramètres de serveur
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.telemetry_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "${stringResource(R.string.telemetry_backend_server)}: ${telemetryManager.backendUrl}",
                        fontSize = 11.sp,
                        color = Color(0xFF0284C7)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { showServerSettings = !showServerSettings },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = if (showServerSettings) Color(0xFF0284C7) else Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (isUnlocked) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val ok = telemetryManager.syncTelemetry()
                                    if (ok) {
                                        Toast.makeText(context, context.getString(R.string.telemetry_sync_success), Toast.LENGTH_SHORT).show()
                                        refreshStats()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.telemetry_sync_error), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { refreshStats() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF0284C7), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color(0xFF0F172A),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                telemetryManager.rememberAdminSecret(null, false)
                                isUnlocked = false
                                statsJson = null
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = stringResource(R.string.telemetry_auth_lock_btn),
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Panneau rétractable pour changer l'URL du backend Vercel
            if (showServerSettings) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.telemetry_server_config_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )

                    OutlinedTextField(
                        value = customBackendUrl,
                        onValueChange = { customBackendUrl = it },
                        label = { Text(stringResource(R.string.telemetry_url_label), fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                telemetryManager.backendUrl = customBackendUrl
                                showServerSettings = false
                                Toast.makeText(context, context.getString(R.string.telemetry_saved_locally), Toast.LENGTH_SHORT).show()
                                if (isUnlocked) refreshStats()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.telemetry_apply_btn), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (!isUnlocked) {
            // Carte de déverrouillage sécurisé par le backend
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0284C7).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Text(
                        text = stringResource(R.string.telemetry_auth_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = stringResource(R.string.telemetry_auth_desc),
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    if (authErrorMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = authErrorMessage ?: "",
                                    color = Color(0xFFDC2626),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(stringResource(R.string.telemetry_auth_password_label)) },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Mémoriser la clé sur cet appareil",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                        Switch(
                            checked = rememberPassword,
                            onCheckedChange = { rememberPassword = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF0284C7)
                            )
                        )
                    }

                    Button(
                        onClick = {
                            if (passwordInput.isBlank()) return@Button
                            isAuthenticating = true
                            authErrorMessage = null
                            scope.launch {
                                val res = telemetryManager.fetchAdminStats(secretOverride = passwordInput)
                                if (res.isSuccess && res.data != null) {
                                    telemetryManager.rememberAdminSecret(passwordInput, rememberPassword)
                                    isUnlocked = true
                                    statsJson = res.data.optJSONObject("stats")
                                    val cfgJson = res.data.optJSONObject("config")
                                    if (cfgJson != null) {
                                        configState = RemoteAppConfig.fromJson(cfgJson)
                                        updateManager.onConfigReceived(configState)
                                    }
                                } else if (res.statusCode == 401 || res.statusCode == 403) {
                                    authErrorMessage = context.getString(R.string.telemetry_auth_invalid_pwd)
                                } else {
                                    authErrorMessage = res.errorMessage ?: context.getString(R.string.telemetry_fetch_error)
                                }
                                isAuthenticating = false
                            }
                        },
                        enabled = !isAuthenticating && passwordInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (isAuthenticating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.telemetry_auth_unlock_btn),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        } else {

        val source = statsJson?.optString("source", "memory_fallback") ?: "memory_fallback"

        if (source == "memory_fallback") {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(22.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Mode Mémoire Éphémère Vercel (Upstash Redis non lié)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF92400E)
                        )
                        Text(
                            text = "Sur Vercel, chaque requête s'exécute dans une instance serverless isolée. Sans base Redis, les métriques sont réinitialisées et les forçages de mise à jour ne persistent pas entre conteneurs. Pour activer la persistance mondiale, connectez Upstash Redis (gratuit) dans Vercel > Storage.",
                            fontSize = 11.sp,
                            color = Color(0xFFB45309),
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // Métriques Clés (KPI Cards)
        val dau = statsJson?.optInt("dau", 1) ?: 1
        val mau = statsJson?.optInt("mau", 1) ?: 1
        val totalCountries = statsJson?.optInt("totalCountries", 1) ?: 1

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.telemetry_kpi_dau),
                value = "$dau",
                subtitle = stringResource(R.string.telemetry_kpi_dau_sub),
                accentColor = Color(0xFF16A34A)
            )
            KpiCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.telemetry_kpi_mau),
                value = "$mau",
                subtitle = stringResource(R.string.telemetry_kpi_mau_sub),
                accentColor = Color(0xFF0284C7)
            )
            KpiCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.telemetry_kpi_countries),
                value = "$totalCountries",
                subtitle = if (source == "upstash_redis") "Redis Edge" else "Local Edge",
                accentColor = Color(0xFF9333EA)
            )
        }

        // Section Répartition par Pays (GeoIP Edge)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.Public, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.telemetry_countries_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                }

                val countriesArray = statsJson?.optJSONArray("countries")
                if (countriesArray == null || countriesArray.length() == 0) {
                    Text(
                        text = stringResource(R.string.telemetry_no_countries_yet),
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    var totalPings = 0
                    for (i in 0 until countriesArray.length()) {
                        val item = countriesArray.getJSONObject(i)
                        totalPings += item.optInt("count", 0)
                    }
                    if (totalPings == 0) totalPings = 1

                    for (i in 0 until countriesArray.length()) {
                        val item = countriesArray.getJSONObject(i)
                        val code = item.optString("country", "XX")
                        val count = item.optInt("count", 0)
                        val pct = (count.toFloat() / totalPings.toFloat())
                        CountryRow(code = code, count = count, percentage = pct)
                    }
                }
            }
        }

        // Section Taux d'Utilisation des Fonctionnalités
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.telemetry_features_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                }

                val featuresArray = statsJson?.optJSONArray("features")
                if (featuresArray == null || featuresArray.length() == 0) {
                    Text(
                        text = stringResource(R.string.telemetry_no_features_yet),
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    for (i in 0 until featuresArray.length()) {
                        val item = featuresArray.getJSONObject(i)
                        val feat = item.optString("feature", "")
                        val count = item.optInt("count", 0)
                        FeatureUsageRow(featureKey = feat, count = count)
                    }
                }
            }
        }

        // Section Pilotage des Mises à Jour & Forçage
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (configState.forceUpdate) Color(0xFFDC2626).copy(alpha = 0.5f) else Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = if (configState.forceUpdate) Color(0xFFDC2626) else Color(0xFF0284C7),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.telemetry_update_management_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                }

                Text(
                    text = stringResource(R.string.telemetry_update_management_desc),
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 16.sp
                )

                // Switch Mise à jour obligatoire
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (configState.forceUpdate) Color(0xFFFEF2F2) else Color(0xFFF8FAFC))
                        .border(1.dp, if (configState.forceUpdate) Color(0xFFFECACA) else Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.telemetry_force_update_toggle_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (configState.forceUpdate) Color(0xFFDC2626) else Color(0xFF0F172A)
                        )
                        Text(
                            text = stringResource(R.string.telemetry_force_update_toggle_sub),
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    Switch(
                        checked = configState.forceUpdate,
                        onCheckedChange = { configState = configState.copy(forceUpdate = it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFDC2626)
                        )
                    )
                }

                // Formulaire des versions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = configState.minRequiredVersionCode.toString(),
                        onValueChange = {
                            val v = it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0
                            configState = configState.copy(minRequiredVersionCode = v)
                        },
                        label = { Text(stringResource(R.string.telemetry_min_code_label), fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = configState.minRequiredVersionName,
                        onValueChange = { configState = configState.copy(minRequiredVersionName = it) },
                        label = { Text(stringResource(R.string.telemetry_min_name_label), fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = configState.downloadUrl,
                    onValueChange = { configState = configState.copy(downloadUrl = it) },
                    label = { Text(stringResource(R.string.telemetry_download_url_label), fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Notes de versions multilingues
                Text(
                    text = stringResource(R.string.telemetry_release_notes_section),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color(0xFF0F172A)
                )

                OutlinedTextField(
                    value = configState.releaseNotesFr,
                    onValueChange = { configState = configState.copy(releaseNotesFr = it) },
                    label = { Text("🇫🇷 Français", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                OutlinedTextField(
                    value = configState.releaseNotesEn,
                    onValueChange = { configState = configState.copy(releaseNotesEn = it) },
                    label = { Text("🇬🇧 English", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                OutlinedTextField(
                    value = configState.releaseNotesAr,
                    onValueChange = { configState = configState.copy(releaseNotesAr = it) },
                    label = { Text("🇩🇿 العربية", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Boutons d'action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            // Test de simulation de forçage
                            updateManager.simulateForceUpdate(true)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.telemetry_test_dialog_btn), fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                val ok = telemetryManager.pushRemoteConfig(configState)
                                if (ok) {
                                    Toast.makeText(context, context.getString(R.string.telemetry_config_pushed_success), Toast.LENGTH_LONG).show()
                                    refreshStats()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.telemetry_config_pushed_error), Toast.LENGTH_LONG).show()
                                }
                                isLoading = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.telemetry_push_config_btn),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun KpiCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = accentColor)
            Text(text = subtitle, fontSize = 9.sp, color = Color(0xFF94A3B8))
        }
    }
}

@Composable
private fun CountryRow(code: String, count: Int, percentage: Float) {
    val emoji = getCountryEmoji(code)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = emoji, fontSize = 18.sp)
        Text(text = code, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), fontSize = 12.sp, modifier = Modifier.width(36.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFE2E8F0))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF0284C7))
            )
        }

        val pctInt = (percentage * 100).toInt()
        Text(
            text = "$count ($pctInt%)",
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(65.dp)
        )
    }
}

@Composable
private fun FeatureUsageRow(featureKey: String, count: Int) {
    val (label, icon) = when (featureKey) {
        "app_launch" -> Pair("Lancements de l'App", "🚀")
        "call_voice" -> Pair("Appels Vocaux E2EE", "📞")
        "call_video" -> Pair("Appels Vidéo E2EE", "📹")
        "story_view" -> Pair("Vues Stories", "👀")
        "story_post" -> Pair("Stories Postées", "✨")
        "family_circle_action" -> Pair("Cocon Famille Privé", "👨‍👩‍👧‍👦")
        "nostr_dm" -> Pair("Messages Chiffrés Nostr", "💬")
        "sos_duress" -> Pair("Sécurité / Duress", "🚨")
        else -> Pair(featureKey, "⚡")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = icon, fontSize = 14.sp)
            Text(text = label, fontSize = 12.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Medium)
        }
        Text(text = count.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
    }
}

private fun getCountryEmoji(code: String): String {
    if (code.length != 2) return "🌐"
    val offset = 127397
    return String(Character.toChars(code[0].code + offset)) + String(Character.toChars(code[1].code + offset))
}
