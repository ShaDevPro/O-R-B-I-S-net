package com.sha.orbis.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import com.sha.orbis.nostr.client.RelayClient
import com.sha.orbis.nostr.client.RelayPoolManager

private val ColorSkyBlue = Color(0xFF00A3FF)
private val ColorConnected = Color(0xFF00E676)
private val ColorConnecting = Color(0xFFFFB300)
private val ColorDisconnected = Color(0xFFFF5252)

/**
 * Pastille UI/UX Premium affichant en temps réel l'état des relais décentralisés Nostr.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayStatusPill(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val relayPool = remember { RelayPoolManager.getInstance(context) }
    val poolHealth by relayPool.poolHealth.collectAsState()
    var showSheet by remember { mutableStateOf(false) }

    val dotColor = when {
        poolHealth.connectedCount > 0 -> ColorConnected
        poolHealth.details.any { it.state == RelayClient.State.CONNECTING } -> ColorConnecting
        else -> ColorDisconnected
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF151922))
            .border(1.dp, ColorSkyBlue.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .clickable { showSheet = true }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Point d'état lumineux (statique, zéro recomposition / CPU 0%)
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )

        // Libellé court
        val labelText = if (poolHealth.connectedCount > 0) {
            "${poolHealth.connectedCount}/${poolHealth.totalRelays}"
        } else if (poolHealth.details.any { it.state == RelayClient.State.CONNECTING }) {
            stringResource(R.string.nostr_relays_connecting)
        } else {
            stringResource(R.string.nostr_relays_disconnected)
        }

        Text(
            text = labelText,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )

        if (poolHealth.averagePingMs > 0) {
            Text(
                text = "${poolHealth.averagePingMs}ms",
                color = ColorSkyBlue,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    if (showSheet) {
        RelayStatusBottomSheet(
            poolHealth = poolHealth,
            onDismiss = { showSheet = false },
            onReconnect = { relayPool.start() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayStatusBottomSheet(
    poolHealth: RelayPoolManager.PoolHealth,
    onDismiss: () -> Unit,
    onReconnect: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F131C)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(ColorSkyBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = ColorSkyBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column {
                    Text(
                        text = stringResource(R.string.nostr_relays_title),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.nostr_relays_subtitle),
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Liste des relais
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(poolHealth.details) { detail ->
                    RelayItemCard(detail = detail)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bouton Reconnexion
            Button(
                onClick = onReconnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorSkyBlue),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.nostr_relays_reconnect_btn),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RelayItemCard(detail: RelayPoolManager.RelayDetail) {
    val (statusText, statusColor) = when (detail.state) {
        RelayClient.State.CONNECTED -> Pair("Connecté", ColorConnected)
        RelayClient.State.CONNECTING -> Pair("Connexion...", ColorConnecting)
        RelayClient.State.ERROR -> Pair("Erreur", ColorDisconnected)
        RelayClient.State.DISCONNECTED -> Pair("Déconnecté", Color.Gray)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF161B26))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = detail.url.replace("wss://", ""),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            if (detail.pingMs > 0 && detail.state == RelayClient.State.CONNECTED) {
                Text(
                    text = "Latence : ${detail.pingMs} ms",
                    color = ColorSkyBlue,
                    fontSize = 11.sp
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
