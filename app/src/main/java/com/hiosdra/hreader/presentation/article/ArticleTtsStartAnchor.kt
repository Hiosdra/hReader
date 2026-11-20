package com.hiosdra.hreader.presentation.article

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

private const val ANCHOR_LOOKUP_TIMEOUT_MS = 1_000L
private const val ANCHOR_MAX_LENGTH = 400

internal class ArticleTtsStartAnchors {
    private val providers = mutableMapOf<Long, suspend () -> String?>()

    fun register(entryId: Long, provider: suspend () -> String?) {
        providers[entryId] = provider
    }

    fun unregister(entryId: Long, provider: suspend () -> String?) {
        if (providers[entryId] === provider) providers.remove(entryId)
    }

    suspend fun anchorFor(entryId: Long): String? {
        val provider = providers[entryId] ?: return null
        return withTimeoutOrNull(ANCHOR_LOOKUP_TIMEOUT_MS) { provider() }?.takeIf(String::isNotBlank)
    }
}

internal class ArticleViewportGeometry {
    var viewport: LayoutCoordinates? = null
    var webView: LayoutCoordinates? = null

    fun visibleTopInWebViewPx(): Int {
        val viewportCoordinates = viewport?.takeIf { it.isAttached } ?: return 0
        val webViewCoordinates = webView?.takeIf { it.isAttached } ?: return 0
        val webViewTop = viewportCoordinates.localPositionOf(webViewCoordinates, Offset.Zero).y
        return (-webViewTop).coerceAtLeast(0f).roundToInt()
    }
}

private const val PARAGRAPH_SELECTOR = "p,li,h1,h2,h3,h4,h5,h6,blockquote,pre,dd,dt,td,th,figcaption"

internal val SELECTED_PARAGRAPH_SCRIPT = """
    (function(){
      var selection=window.getSelection();
      if(!selection||!selection.rangeCount)return '';
      var node=selection.anchorNode;
      var element=node&&(node.nodeType===1?node:node.parentElement);
      var block=element&&element.closest('$PARAGRAPH_SELECTOR');
      var source=block||element;
      var text=((source&&(source.innerText||source.textContent))||selection.toString()||'').trim();
      selection.removeAllRanges();
      return text.substring(0,$ANCHOR_MAX_LENGTH);
    })()
""".trimIndent()

internal fun firstVisibleParagraphScript(visibleTopPx: Int): String = """
    (function(topPx){
      var selector='$PARAGRAPH_SELECTOR';
      var limit=topPx/(window.devicePixelRatio||1)+24;
      var nodes=document.body.querySelectorAll(selector);
      var first=true;
      for(var i=0;i<nodes.length;i++){
        var el=nodes[i];
        if(el.querySelector(selector)||el.closest('nav,aside,footer'))continue;
        var text=(el.innerText||el.textContent||'').trim();
        if(!text)continue;
        if(el.getBoundingClientRect().bottom>limit)return first?'':text.substring(0,$ANCHOR_MAX_LENGTH);
        first=false;
      }
      return '';
    })(${visibleTopPx.coerceAtLeast(0)})
""".trimIndent()

internal fun decodeJavascriptString(result: String?): String? {
    if (result == null || result.length < 2 || !result.startsWith('"') || !result.endsWith('"')) return null
    val body = result.substring(1, result.length - 1)
    return runCatching { unescapeJsonString(body) }.getOrNull()
}

private fun unescapeJsonString(body: String): String = buildString {
    var index = 0
    while (index < body.length) {
        val character = body[index]
        if (character != '\\') {
            append(character)
            index++
            continue
        }
        val escaped = body[index + 1]
        if (escaped == 'u') {
            append(body.substring(index + 2, index + 6).toInt(16).toChar())
            index += 6
            continue
        }
        append(
            when (escaped) {
                'n' -> '\n'
                't' -> '\t'
                'r' -> '\r'
                'b' -> '\b'
                'f' -> '\u000C'
                else -> escaped
            }
        )
        index += 2
    }
}
