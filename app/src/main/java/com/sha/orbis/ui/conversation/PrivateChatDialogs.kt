package com.sha.orbis.ui.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R
import kotlinx.coroutines.delay

@Composable
fun PrivateChatPasswordSetupDialog(
    conversationTitle: String,
    isChange: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (password: String, recoveryAnswers: List<String>) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var answer1 by remember { mutableStateOf("") }
    var answer2 by remember { mutableStateOf("") }
    var answer3 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val passShortError = stringResource(R.string.private_chat_error_pass_short)
    val passMismatchError = stringResource(R.string.private_chat_error_pass_mismatch)
    val answersRequiredError = stringResource(R.string.private_chat_error_answers_required)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isChange) {
                    stringResource(R.string.private_chat_change_pass_title)
                } else {
                    stringResource(R.string.private_chat_lock_title, conversationTitle)
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
            ) {
                Text(
                    text = stringResource(R.string.private_chat_pass_desc),
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.private_chat_pass_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(stringResource(R.string.private_chat_pass_confirm_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = answer1,
                    onValueChange = { answer1 = it },
                    label = { Text(stringResource(R.string.private_chat_secret_question_1)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = answer2,
                    onValueChange = { answer2 = it },
                    label = { Text(stringResource(R.string.private_chat_secret_question_2)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = answer3,
                    onValueChange = { answer3 = it },
                    label = { Text(stringResource(R.string.private_chat_secret_question_3)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(text = error!!, color = androidx.compose.material3.MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = when {
                        password.length < 4 -> passShortError
                        password != confirmPassword -> passMismatchError
                        answer1.isBlank() || answer2.isBlank() || answer3.isBlank() -> answersRequiredError
                        else -> null
                    }
                    if (error == null) {
                        onConfirm(password, listOf(answer1, answer2, answer3))
                    }
                }
            ) {
                Text(stringResource(if (isChange) R.string.private_chat_change_pass_action else R.string.private_chat_lock_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun PrivateChatUnlockDialog(
    conversationTitle: String,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onUnlock: (password: String) -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.private_chat_unlock_title, conversationTitle), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.private_chat_pass_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!errorMessage.isNullOrBlank()) {
                    Text(text = errorMessage, color = androidx.compose.material3.MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onUnlock(password) }) {
                Text(stringResource(R.string.private_chat_unlock_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun PrivateChatRecoverDialog(
    conversationTitle: String,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onRecover: (answers: List<String>) -> Unit
) {
    var answer1 by remember { mutableStateOf("") }
    var answer2 by remember { mutableStateOf("") }
    var answer3 by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.private_chat_recover_title, conversationTitle), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = answer1,
                    onValueChange = { answer1 = it },
                    label = { Text(stringResource(R.string.private_chat_secret_question_1)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = answer2,
                    onValueChange = { answer2 = it },
                    label = { Text(stringResource(R.string.private_chat_secret_question_2)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = answer3,
                    onValueChange = { answer3 = it },
                    label = { Text(stringResource(R.string.private_chat_secret_question_3)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!errorMessage.isNullOrBlank()) {
                    Text(text = errorMessage, color = androidx.compose.material3.MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onRecover(listOf(answer1, answer2, answer3)) }) {
                Text(stringResource(R.string.private_chat_recover_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun PrivateChatRecoveredPasswordDialog(
    password: String,
    onDismiss: () -> Unit
) {
    LaunchedEffect(password) {
        delay(5000L)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.private_chat_recovered_pass_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.private_chat_recovered_pass_desc))
                Text(text = password, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
