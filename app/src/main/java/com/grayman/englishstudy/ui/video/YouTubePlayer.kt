package com.grayman.englishstudy.ui.video

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Controls the embedded YouTube player and reports where it is. The player is YouTube's own
 * IFrame player (the official way to show a YouTube video in another app) inside a WebView.
 */
class YouTubeController {
    internal var webView: WebView? = null
    var ready by mutableStateOf(false)
        internal set
    var playing by mutableStateOf(false)
        internal set
    var timeMs by mutableLongStateOf(0L)
        internal set
    /** YouTube's error code: 101 and 150 mean the owner does not allow playing it in other apps. */
    var error by mutableStateOf<Int?>(null)
        internal set

    fun seekTo(ms: Long, play: Boolean = true) = js("seekTo(${ms / 1000.0}, $play)")
    fun play() = js("play()")
    fun pause() = js("pause()")
    fun toggle() = if (playing) pause() else play()
    fun setRate(rate: Float) = js("setRate($rate)")

    private fun js(code: String) {
        webView?.evaluateJavascript(code, null)
    }
}

private class Bridge(private val controller: YouTubeController) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface fun onReady() = main.post { controller.ready = true }.let { }
    @JavascriptInterface fun onState(state: Int) = main.post { controller.playing = state == 1 || state == 3 }.let { }
    @JavascriptInterface fun onTime(ms: Double) = main.post { controller.timeMs = ms.toLong() }.let { }
    @JavascriptInterface fun onError(code: Int) = main.post { controller.error = code }.let { }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayer(videoId: String, controller: YouTubeController, modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                // Without fixed layout params a WebView in Compose wraps its content, and the page then
                // sees a viewport 0px tall: the player plays but is drawn with no height.
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                setBackgroundColor(android.graphics.Color.BLACK)
                addJavascriptInterface(Bridge(controller), "Android")
                // YouTube refuses embeds without a referring web origin, so the page gets one.
                loadDataWithBaseURL(ORIGIN, playerHtml(videoId), "text/html", "utf-8", null)
                controller.webView = this
            }
        },
    )
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) controller.pause() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            controller.webView = null
        }
    }
}

private const val ORIGIN = "https://englishdaily.grayman.app"

private fun playerHtml(videoId: String) = """
<!DOCTYPE html>
<html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;padding:0;background:#000;width:100%;height:100%;overflow:hidden}#p,iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0}</style>
</head><body>
<div id="p"></div>
<script>
var player;
var tag = document.createElement('script');
tag.src = "https://www.youtube.com/iframe_api";
document.head.appendChild(tag);
function onYouTubeIframeAPIReady() {
  player = new YT.Player('p', {
    width: '100%', height: '100%', videoId: '$videoId',
    playerVars: { playsinline: 1, rel: 0, modestbranding: 1, cc_load_policy: 0, iv_load_policy: 3, origin: '$ORIGIN', widget_referrer: '$ORIGIN' },
    events: {
      onReady: function() { Android.onReady(); },
      onStateChange: function(e) { Android.onState(e.data); },
      onError: function(e) { Android.onError(e.data); }
    }
  });
}
function seekTo(s, play) { if (!player) return; player.seekTo(s, true); if (play) player.playVideo(); }
function play() { if (player) player.playVideo(); }
function pause() { if (player && player.pauseVideo) player.pauseVideo(); }
function setRate(r) { if (player) player.setPlaybackRate(r); }
setInterval(function() { if (player && player.getCurrentTime) Android.onTime(player.getCurrentTime() * 1000); }, 200);
</script>
</body></html>
"""
