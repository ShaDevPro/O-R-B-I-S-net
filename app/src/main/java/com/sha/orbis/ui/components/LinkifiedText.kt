package com.sha.orbis.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sha.orbis.R

object LinkTextHelper {
    val URL_REGEX = Regex("""(https?://[^\s<>"'()]+|www\.[^\s<>"'()]+)""", RegexOption.IGNORE_CASE)

    fun extractUrls(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val safeText = if (text.length > 2500) text.take(2500) else text
        return try {
            URL_REGEX.findAll(safeText).map { it.value }.toList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun openUrl(context: Context, rawUrl: String) {
        val url = if (!rawUrl.startsWith("http://", ignoreCase = true) && !rawUrl.startsWith("https://", ignoreCase = true)) {
            "https://$rawUrl"
        } else {
            rawUrl
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.social_link_open_error), Toast.LENGTH_SHORT).show()
        }
    }

    fun copyUrl(context: Context, rawUrl: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("URL", rawUrl)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, context.getString(R.string.social_link_copied), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }
}

private const val MAX_COLLAPSED_LENGTH = 2000
private const val MAX_EXPANDED_LENGTH = 8000

/**
 * Composant de texte intelligent détectant automatiquement les URLs dans le contenu.
 * - Clic court : Ouvre le lien dans le navigateur
 * - Appui long : Copie le lien dans le presse-papier avec confirmation Toast
 * - Protection native contre le gel/crash sur les chaînes de caractères excessivement longues (HarfBuzz LineBreaker)
 */
@Composable
fun LinkifiedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isExcessive = text.length > MAX_COLLAPSED_LENGTH
    var isExpanded by remember(text) { mutableStateOf(false) }

    val truncatedText = remember(text, isExpanded) {
        when {
            !isExcessive -> text
            isExpanded -> {
                if (text.length > MAX_EXPANDED_LENGTH) {
                    text.take(MAX_EXPANDED_LENGTH) + "… [Texte tronqué]"
                } else {
                    text
                }
            }
            else -> text.take(MAX_COLLAPSED_LENGTH) + "…"
        }
    }

    val matches = remember(truncatedText) {
        try {
            val safeForRegex = if (truncatedText.length > 2500) truncatedText.take(2500) else truncatedText
            LinkTextHelper.URL_REGEX.findAll(safeForRegex).toList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    Column(modifier = modifier) {
        if (matches.isEmpty()) {
            Text(
                text = truncatedText,
                style = style,
                color = color,
                maxLines = maxLines,
                overflow = overflow
            )
        } else {
            val annotatedString = remember(truncatedText, linkColor) {
                buildAnnotatedString {
                    var lastIndex = 0
                    for (match in matches) {
                        val start = match.range.first
                        val end = match.range.last + 1
                        if (start > lastIndex && start <= truncatedText.length) {
                            append(truncatedText.substring(lastIndex, start))
                        }
                        val url = match.value
                        pushStringAnnotation(tag = "URL", annotation = url)
                        pushStyle(
                            SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        append(url)
                        pop()
                        pop()
                        lastIndex = end
                    }
                    if (lastIndex < truncatedText.length) {
                        append(truncatedText.substring(lastIndex))
                    }
                }
            }

            var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

            Text(
                text = annotatedString,
                modifier = Modifier.pointerInput(annotatedString) {
                    detectTapGestures(
                        onTap = { pos ->
                            try {
                                val layout = layoutResult
                                if (layout != null) {
                                    val offset = layout.getOffsetForPosition(pos)
                                    if (offset in 0 until annotatedString.length) {
                                        val urlAnnotation = annotatedString.getStringAnnotations("URL", offset, offset).firstOrNull()
                                        if (urlAnnotation != null) {
                                            LinkTextHelper.openUrl(context, urlAnnotation.item)
                                            return@detectTapGestures
                                        }
                                    }
                                }
                                onTextClick?.invoke()
                            } catch (_: Throwable) {
                                onTextClick?.invoke()
                            }
                        },
                        onLongPress = { pos ->
                            try {
                                val layout = layoutResult
                                if (layout != null) {
                                    val offset = layout.getOffsetForPosition(pos)
                                    if (offset in 0 until annotatedString.length) {
                                        val urlAnnotation = annotatedString.getStringAnnotations("URL", offset, offset).firstOrNull()
                                        if (urlAnnotation != null) {
                                            LinkTextHelper.copyUrl(context, urlAnnotation.item)
                                        }
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                    )
                },
                onTextLayout = { try { layoutResult = it } catch (_: Throwable) {} },
                style = style,
                color = color,
                maxLines = maxLines,
                overflow = overflow
            )
        }

        if (isExcessive) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(if (isExpanded) R.string.text_show_less else R.string.text_show_more),
                color = linkColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.clickable { isExpanded = !isExpanded }
            )
        }
    }
}
