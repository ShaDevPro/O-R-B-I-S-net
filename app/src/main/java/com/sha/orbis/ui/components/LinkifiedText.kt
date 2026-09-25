package com.sha.orbis.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.sha.orbis.R

object LinkTextHelper {
    val URL_REGEX = Regex("""(https?://[^\s<>"'()]+|www\.[^\s<>"'()]+)""", RegexOption.IGNORE_CASE)

    fun extractUrls(text: String): List<String> {
        return URL_REGEX.findAll(text).map { it.value }.toList()
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

/**
 * Composant de texte intelligent détectant automatiquement les URLs dans le contenu.
 * - Clic court : Ouvre le lien dans le navigateur
 * - Appui long : Copie le lien dans le presse-papier avec confirmation Toast
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
    val matches = remember(text) { LinkTextHelper.URL_REGEX.findAll(text).toList() }

    if (matches.isEmpty()) {
        Text(
            text = text,
            modifier = modifier,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow
        )
        return
    }

    val annotatedString = remember(text, linkColor) {
        buildAnnotatedString {
            var lastIndex = 0
            for (match in matches) {
                val start = match.range.first
                val end = match.range.last + 1
                if (start > lastIndex) {
                    append(text.substring(lastIndex, start))
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
                pop() // Pop style
                pop() // Pop annotation
                lastIndex = end
            }
            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotatedString,
        modifier = modifier.pointerInput(annotatedString) {
            detectTapGestures(
                onTap = { pos ->
                    val layout = layoutResult
                    if (layout != null) {
                        val offset = layout.getOffsetForPosition(pos)
                        val urlAnnotation = annotatedString.getStringAnnotations("URL", offset, offset).firstOrNull()
                        if (urlAnnotation != null) {
                            LinkTextHelper.openUrl(context, urlAnnotation.item)
                            return@detectTapGestures
                        }
                    }
                    onTextClick?.invoke()
                },
                onLongPress = { pos ->
                    val layout = layoutResult
                    if (layout != null) {
                        val offset = layout.getOffsetForPosition(pos)
                        val urlAnnotation = annotatedString.getStringAnnotations("URL", offset, offset).firstOrNull()
                        if (urlAnnotation != null) {
                            LinkTextHelper.copyUrl(context, urlAnnotation.item)
                        }
                    }
                }
            )
        },
        onTextLayout = { layoutResult = it },
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow
    )
}
