package com.example.ui.screens.watch

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.ui.screens.report.ReportSourceDialog
import com.example.ui.screens.watch.dialogs.BraveRecommendationDialog
import com.example.ui.screens.watch.dialogs.OpenInBrowserSheet
import com.example.ui.screens.watch.dialogs.SecureDnsGuideSheet
import com.example.ui.screens.watch.dialogs.ShieldSettingsSheet
import com.example.ui.screens.watch.helper.AdBlockerHelper
import com.example.ui.screens.watch.helper.WatchScripts
import com.example.ui.theme.ReeloBlack
import com.example.ui.theme.ReeloYellow
import com.example.ui.theme.SuccessGreen
import kotlinx.coroutines.delay
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    viewModel: WatchViewModel,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var customVideoView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var swipeNotificationText by remember { mutableStateOf<String?>(null) }

    // Initialize bundled ad domain blocklist
    LaunchedEffect(Unit) {
        AdBlockerHelper.init(context)
        viewModel.refreshDnsStatus(context)
    }

    // Refresh DNS detection on app resume (Feature 3)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDnsStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Keep screen on for watching
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            viewModel.saveCurrentProgress()
            activity?.requestedOrientation = originalOrientation
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.let { window ->
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
            webViewRef?.destroy()
        }
    }

    // Dynamic Orientation Control: 9:16 Portrait vs 16:9 Landscape
    LaunchedEffect(uiState.isLandscapeMode) {
        activity?.let { act ->
            if (uiState.isLandscapeMode) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                val insetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                val insetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Clear swipe notification toast after 1.5s
    LaunchedEffect(swipeNotificationText) {
        if (swipeNotificationText != null) {
            delay(1500)
            swipeNotificationText = null
        }
    }

    // Handle resume seek command to HTML5 video (Feature 7)
    LaunchedEffect(uiState.seekCommandId) {
        val targetSec = uiState.targetSeekSeconds
        if (targetSec != null) {
            webViewRef?.evaluateJavascript(
                "if (window.__reeloExecuteCommand) { window.__reeloExecuteCommand({type: 'seek', time: $targetSec}); }",
                null
            )
        }
    }

    // Configure Local DoH Proxy for WebView if enabled (default false)
    DisposableEffect(uiState.useSecureDns) {
        if (uiState.useSecureDns && WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            val port = viewModel.startLocalProxy()
            if (port > 0) {
                val proxyConfig = ProxyConfig.Builder()
                    .addProxyRule("127.0.0.1:$port")
                    .build()
                runCatching {
                    ProxyController.getInstance().setProxyOverride(proxyConfig, { runnable -> runnable.run() }, {})
                }
            }
        } else {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                runCatching {
                    ProxyController.getInstance().clearProxyOverride({ runnable -> runnable.run() }, {})
                }
            }
            viewModel.stopLocalProxy()
        }

        onDispose {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                runCatching {
                    ProxyController.getInstance().clearProxyOverride({ runnable -> runnable.run() }, {})
                }
            }
            viewModel.stopLocalProxy()
        }
    }

    // Hardware Back Handling
    BackHandler {
        if (customVideoView != null) {
            customViewCallback?.onCustomViewHidden()
            customVideoView = null
            customViewCallback = null
        } else if (uiState.isLandscapeMode) {
            viewModel.setLandscapeMode(false)
        } else if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            onExit()
        }
    }

    // =========================================================================
    // DIALOGS & BOTTOM SHEETS
    // =========================================================================

    // Feature 1: Open in Browser Sheet
    if (uiState.showOpenInBrowserSheet) {
        OpenInBrowserSheet(
            originalUrl = uiState.currentSource?.url ?: "",
            currentUrl = uiState.currentPageUrl,
            onDismiss = { viewModel.toggleOpenInBrowserSheet(false) },
            onOpenSecureDnsGuide = { viewModel.toggleSecureDnsSheet(true) }
        )
    }

    // Feature 2: Brave Recommendation Popup
    if (uiState.showBraveDialog) {
        BraveRecommendationDialog(
            originalUrl = uiState.currentSource?.url ?: "",
            onDismiss = { viewModel.dismissBraveDialog() },
            onDontShowAgain = { viewModel.setBraveDontShowAgain() }
        )
    }

    // Feature 3: Guided Secure DNS Sheet
    if (uiState.showSecureDnsSheet) {
        SecureDnsGuideSheet(
            dnsStatus = uiState.dnsStatus,
            onDismiss = { viewModel.toggleSecureDnsSheet(false) },
            onReloadStream = { webViewRef?.reload() }
        )
    }

    // Feature 4, 5, 6: Shield & Ad Protection Settings Sheet
    if (uiState.showShieldSheet) {
        val currentHost = Uri.parse(uiState.currentPageUrl ?: uiState.currentSource?.url ?: "").host
        val isHostDisabled = currentHost != null && uiState.disabledShieldHosts.contains(currentHost.lowercase())

        ShieldSettingsSheet(
            stayOnOriginalSite = uiState.stayOnOriginalSite,
            adCleanerEnabled = uiState.adCleanerEnabled,
            popupGatedPlayers = uiState.popupGatedPlayers,
            isShieldDisabledForCurrentHost = isHostDisabled,
            currentHost = currentHost,
            blockedRedirectsCount = uiState.blockedRedirectsCount,
            onToggleStayOnOriginalSite = { viewModel.toggleStayOnOriginalSite(it) },
            onToggleAdCleaner = { viewModel.toggleAdCleaner(it) },
            onTogglePopupGatedPlayers = { viewModel.togglePopupGatedPlayers(it) },
            onToggleShieldForCurrentHost = { viewModel.toggleShieldForCurrentHost() },
            onResetBlockedCount = { viewModel.resetBlockedCount() },
            onDismiss = { viewModel.toggleShieldSheet(false) }
        )
    }

    // Report Source Dialog
    if (uiState.showReportDialog) {
        ReportSourceDialog(
            sourceLabel = uiState.currentSource?.label ?: "Current Server",
            onDismiss = { viewModel.toggleReportDialog(false) },
            onSubmit = { reason, details ->
                viewModel.reportCurrentSource(reason, details)
            }
        )
    }

    // Comments Bottom Sheet
    if (uiState.showCommentsSheet) {
        com.example.ui.screens.comments.CommentsBottomSheet(
            comments = uiState.comments,
            isLoggedIn = uiState.isUserLoggedIn,
            onDismiss = { viewModel.toggleCommentsSheet(false) },
            onPostComment = { viewModel.postComment(it) },
            onLikeComment = { viewModel.toggleCommentLike(it) },
            onRequireLogin = { /* Login prompt */ }
        )
    }

    // Servers & Controls Bottom Sheet
    if (uiState.showServersSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.toggleServersSheet(false) },
            containerColor = Color(0xFF1E1E1E),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Streaming Servers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ReeloYellow
                    )

                    Surface(
                        color = Color(0xFF333333),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable { viewModel.toggleUserAgentMode() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (uiState.userAgentMode == UserAgentMode.MOBILE) Icons.Default.PhoneAndroid else Icons.Default.Computer,
                                contentDescription = null,
                                tint = ReeloYellow,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "UA: ${uiState.userAgentMode.label}",
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Text(
                    text = "Select an alternative server if playback is buffering or unavailable:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )

                uiState.sources.forEach { source ->
                    val isSelected = uiState.currentSource?.id == source.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectSource(source) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF2A2810) else Color(0xFF292929)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ReeloYellow)) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = source.label,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) ReeloYellow else Color.White
                                )
                                Text(
                                    text = "${source.quality ?: "Auto"} • ${source.language ?: "Original"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.LightGray
                                )
                            }
                            if (isSelected) {
                                Surface(
                                    color = ReeloYellow,
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Active",
                                        tint = ReeloBlack,
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Divider(color = Color(0xFF3A3A3A))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = { viewModel.markCurrentEpisodeAsWatched() }
                    ) {
                        Icon(imageVector = Icons.Default.DoneAll, contentDescription = null, tint = SuccessGreen)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mark as Watched", color = SuccessGreen)
                    }

                    OutlinedButton(
                        onClick = { viewModel.toggleReportDialog(true) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ReeloYellow)
                    ) {
                        Icon(imageVector = Icons.Default.ReportProblem, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Report Problem")
                    }
                }
            }
        }
    }

    // =========================================================================
    // MAIN CONTAINER
    // =========================================================================
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (customVideoView != null) {
            AndroidView(
                factory = {
                    (customVideoView?.parent as? ViewGroup)?.removeView(customVideoView)
                    customVideoView!!
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (!uiState.isLandscapeMode) {
            // =========================================================
            // 9:16 VERTICAL REEL PORTRAIT LAYOUT
            // =========================================================
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(44.dp)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onExit,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("watch_exit_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit Watch",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = uiState.series?.title ?: "Watching Episode",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val ep = uiState.currentEpisode
                            Text(
                                text = if (ep != null) "S${ep.seasonNumber}:E${ep.episodeNumber} - ${ep.title}" else "Loading episode...",
                                color = ReeloYellow,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Globe Icon: Open in Browser (Feature 1)
                        IconButton(
                            onClick = { viewModel.toggleOpenInBrowserSheet(true) },
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(0xFF222222), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Open in browser",
                                tint = ReeloYellow,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Shield Icon with Blocked Count Badge (Feature 4, 5, 6)
                        Box {
                            IconButton(
                                onClick = { viewModel.toggleShieldSheet(true) },
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color(0xFF222222), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Shield & Ad Control",
                                    tint = if (uiState.blockedRedirectsCount > 0) ReeloYellow else Color.LightGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (uiState.blockedRedirectsCount > 0) {
                                Surface(
                                    color = Color.Red,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(14.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${uiState.blockedRedirectsCount}",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Rotate to 16:9 Landscape Button
                        IconButton(
                            onClick = { viewModel.toggleLandscapeMode() },
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(0xFF222222), CircleShape)
                                .testTag("toggle_landscape_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = "16:9 Landscape Mode",
                                tint = ReeloYellow,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Central Reel Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black)
                ) {
                    val sourceUrl = uiState.currentSource?.url
                    if (!sourceUrl.isNullOrBlank() && !uiState.hasError) {
                        AndroidView(
                            factory = { ctx ->
                                createConfiguredWebView(
                                    context = ctx,
                                    viewModel = viewModel,
                                    sourceUrl = sourceUrl,
                                    onShowCustomView = { v, cb -> customVideoView = v; customViewCallback = cb },
                                    onHideCustomView = { customViewCallback?.onCustomViewHidden(); customVideoView = null; customViewCallback = null },
                                    onUpdateNavState = { back, fwd -> canGoBack = back; canGoForward = fwd },
                                    onAttach = { webViewRef = it }
                                )
                            },
                            update = { view ->
                                updateWebView(view, viewModel, sourceUrl, uiState.userAgentMode.value)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Gestures: double tap to like & vertical drag for episode switch
                    var dragYAccumulator by remember { mutableFloatStateOf(0f) }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(uiState.currentEpisode?.id) {
                                detectTapGestures(
                                    onDoubleTap = { offset ->
                                        viewModel.toggleLike(offset.x, offset.y)
                                    }
                                )
                            }
                            .pointerInput(uiState.currentEpisode?.id) {
                                detectVerticalDragGestures(
                                    onDragStart = { dragYAccumulator = 0f },
                                    onDragEnd = {
                                        if (dragYAccumulator < -80f) {
                                            viewModel.nextEpisode()
                                            swipeNotificationText = "Next Episode ▶"
                                        } else if (dragYAccumulator > 80f) {
                                            viewModel.previousEpisode()
                                            swipeNotificationText = "◀ Previous Episode"
                                        }
                                        dragYAccumulator = 0f
                                    },
                                    onVerticalDrag = { _, dragAmount ->
                                        dragYAccumulator += dragAmount
                                    }
                                )
                            }
                    )

                    // Double-Tap Animated Heart Overlay
                    val heartCoords = uiState.heartAnimationCoordinates
                    if (heartCoords != null) {
                        LaunchedEffect(heartCoords) {
                            delay(800)
                            viewModel.dismissHeartAnimation()
                        }
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Heart Like",
                            tint = ReeloYellow,
                            modifier = Modifier
                                .offset { IntOffset(heartCoords.first.toInt() - 36, heartCoords.second.toInt() - 36) }
                                .size(72.dp)
                        )
                    }

                    // Swipe Feedback Notification Toast
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AnimatedVisibility(
                                visible = swipeNotificationText != null,
                                enter = fadeIn() + slideInVertically(),
                                exit = fadeOut() + slideOutVertically()
                            ) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, ReeloYellow)
                                ) {
                                    Text(
                                        text = swipeNotificationText ?: "",
                                        color = ReeloYellow,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Feature 7: Resume Playback Floating Chip
                    if (uiState.showResumeChip && !uiState.resumeChipText.isNullOrBlank()) {
                        Surface(
                            color = Color(0xF01A1A1A),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, ReeloYellow),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .clickable { viewModel.startOverPlayback() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = ReeloYellow,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = uiState.resumeChipText ?: "",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Start over",
                                    color = ReeloYellow,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Loading Progress Indicator
                    if (uiState.webViewProgress in 1..99) {
                        LinearProgressIndicator(
                            progress = { uiState.webViewProgress / 100f },
                            color = ReeloYellow,
                            trackColor = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.TopCenter)
                        )
                    }

                    // Feature 4: Ad Redirect Blocked Snackbar with "Allow once" (5s)
                    val blockedToast = uiState.blockedRedirectToast
                    if (blockedToast != null) {
                        LaunchedEffect(blockedToast) {
                            delay(5000)
                            viewModel.dismissBlockedToast()
                        }
                        Surface(
                            color = Color(0xF0202020),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF444444)),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = ReeloYellow,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = blockedToast,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                TextButton(
                                    onClick = { viewModel.triggerAllowOnce() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Allow once (5s)", color = ReeloYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Error Overlay
                    if (uiState.hasError) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xF0101010)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = ReeloYellow,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = uiState.errorMessage ?: "Playback error",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        onClick = { viewModel.tryNextServer() },
                                        colors = ButtonDefaults.buttonColors(containerColor = ReeloYellow, contentColor = ReeloBlack)
                                    ) {
                                        Text("Try Next Server", fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val currentSource = uiState.currentSource
                                            if (currentSource != null) viewModel.selectSource(currentSource)
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                    ) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    }

                    // Right Side Action Column (Reels / TikTok Style)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleLike() },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (uiState.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (uiState.isLiked) Color.Red else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.toggleCommentsSheet(true) },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .testTag("watch_comments_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubble,
                                contentDescription = "Comments",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.toggleServersSheet(true) },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .testTag("watch_servers_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Servers & Quality",
                                tint = ReeloYellow,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.toggleReportDialog(true) },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReportProblem,
                                contentDescription = "Report Source",
                                tint = Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Bottom Controls & Episode Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141414))
                        .navigationBarsPadding()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = { viewModel.previousEpisode() }
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = ReeloYellow, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Prev Ep", color = ReeloYellow, fontSize = 12.sp)
                        }

                        Text(
                            text = "Swipe Up/Down for Ep",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.DarkGray
                        )

                        TextButton(
                            onClick = { viewModel.nextEpisode() }
                        ) {
                            Text("Next Ep", color = ReeloYellow, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = ReeloYellow, modifier = Modifier.size(16.dp))
                        }
                    }

                    val rightRailEpisodes = uiState.episodes.filter {
                        if (uiState.seasons.size > 1) it.seasonNumber == uiState.selectedSeasonNumber else true
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(rightRailEpisodes, key = { it.id }) { ep ->
                            val isCurrent = ep.id == uiState.currentEpisode?.id
                            val isWatched = uiState.watchedEpisodeIds.contains(ep.id)

                            Surface(
                                color = if (isCurrent) ReeloYellow else if (isWatched) Color(0xFF222222) else Color(0xFF333333),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clickable { viewModel.selectEpisode(ep) }
                                    .testTag("watch_episode_${ep.episodeNumber}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Ep ${ep.episodeNumber}",
                                        color = if (isCurrent) ReeloBlack else if (isWatched) Color.Gray else Color.White,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                    if (isWatched && !isCurrent) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.Gray,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // =========================================================
            // 16:9 LANDSCAPE FULLSCREEN LAYOUT
            // =========================================================
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Navigation Rail: back, forward, reload, exit, servers, browser, shield
                AnimatedVisibility(
                    visible = uiState.railsExpanded,
                    enter = slideInHorizontally(initialOffsetX = { -it }),
                    exit = slideOutHorizontally(targetOffsetX = { -it })
                ) {
                    Column(
                        modifier = Modifier
                            .width(52.dp)
                            .fillMaxHeight()
                            .background(Color(0xE6141414))
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Exit / Switch to portrait
                            IconButton(
                                onClick = { viewModel.toggleLandscapeMode() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("watch_exit_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ScreenRotation,
                                    contentDescription = "Switch to 9:16 Portrait",
                                    tint = ReeloYellow
                                )
                            }

                            // Browser Back
                            IconButton(
                                onClick = { webViewRef?.goBack() },
                                enabled = canGoBack,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Browser Back",
                                    tint = if (canGoBack) ReeloYellow else Color.DarkGray
                                )
                            }

                            // Browser Forward
                            IconButton(
                                onClick = { webViewRef?.goForward() },
                                enabled = canGoForward,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Browser Forward",
                                    tint = if (canGoForward) ReeloYellow else Color.DarkGray
                                )
                            }

                            // Reload Page
                            IconButton(
                                onClick = { webViewRef?.reload() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reload Page",
                                    tint = ReeloYellow
                                )
                            }

                            // Exit Button
                            IconButton(
                                onClick = onExit,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Exit to Details",
                                    tint = Color.White
                                )
                            }

                            // Feature 1: Open in Browser (Globe Icon)
                            IconButton(
                                onClick = { viewModel.toggleOpenInBrowserSheet(true) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = "Open in Browser",
                                    tint = ReeloYellow
                                )
                            }

                            // Feature 4, 5, 6: Shield Icon with Badge
                            Box {
                                IconButton(
                                    onClick = { viewModel.toggleShieldSheet(true) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = "Shield Protection",
                                        tint = if (uiState.blockedRedirectsCount > 0) ReeloYellow else Color.LightGray
                                    )
                                }
                                if (uiState.blockedRedirectsCount > 0) {
                                    Surface(
                                        color = Color.Red,
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(14.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${uiState.blockedRedirectsCount}",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Servers Button
                        IconButton(
                            onClick = { viewModel.toggleServersSheet(true) },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("watch_servers_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Servers & Quality",
                                tint = ReeloYellow
                            )
                        }
                    }
                }

                // Center WebView Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color.Black)
                ) {
                    val sourceUrl = uiState.currentSource?.url
                    if (!sourceUrl.isNullOrBlank() && !uiState.hasError) {
                        AndroidView(
                            factory = { ctx ->
                                createConfiguredWebView(
                                    context = ctx,
                                    viewModel = viewModel,
                                    sourceUrl = sourceUrl,
                                    onShowCustomView = { v, cb -> customVideoView = v; customViewCallback = cb },
                                    onHideCustomView = { customViewCallback?.onCustomViewHidden(); customVideoView = null; customViewCallback = null },
                                    onUpdateNavState = { back, fwd -> canGoBack = back; canGoForward = fwd },
                                    onAttach = { webViewRef = it }
                                )
                            },
                            update = { view ->
                                updateWebView(view, viewModel, sourceUrl, uiState.userAgentMode.value)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Feature 7: Resume Playback Floating Chip (Landscape)
                    if (uiState.showResumeChip && !uiState.resumeChipText.isNullOrBlank()) {
                        Surface(
                            color = Color(0xF01A1A1A),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, ReeloYellow),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .clickable { viewModel.startOverPlayback() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = ReeloYellow,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = uiState.resumeChipText ?: "",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Start over",
                                    color = ReeloYellow,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Feature 4: Ad Redirect Blocked Snackbar with "Allow once" (5s) (Landscape)
                    val blockedToast = uiState.blockedRedirectToast
                    if (blockedToast != null) {
                        LaunchedEffect(blockedToast) {
                            delay(5000)
                            viewModel.dismissBlockedToast()
                        }
                        Surface(
                            color = Color(0xF0202020),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF444444)),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = ReeloYellow,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = blockedToast,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                TextButton(
                                    onClick = { viewModel.triggerAllowOnce() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Allow once (5s)", color = ReeloYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (uiState.webViewProgress in 1..99) {
                        LinearProgressIndicator(
                            progress = { uiState.webViewProgress / 100f },
                            color = ReeloYellow,
                            trackColor = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.TopCenter)
                        )
                    }

                    // Rails Toggle Handle
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clickable { viewModel.toggleRailsExpanded() }
                    ) {
                        Icon(
                            imageVector = if (uiState.railsExpanded) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                            contentDescription = "Toggle Rails",
                            tint = ReeloYellow,
                            modifier = Modifier.padding(6.dp).size(20.dp)
                        )
                    }
                }

                // Right Rail: Episodes List
                AnimatedVisibility(
                    visible = uiState.railsExpanded,
                    enter = slideInHorizontally(initialOffsetX = { it }),
                    exit = slideOutHorizontally(targetOffsetX = { it })
                ) {
                    val rightRailEpisodes = uiState.episodes.filter {
                        if (uiState.seasons.size > 1) it.seasonNumber == uiState.selectedSeasonNumber else true
                    }
                    val listState = rememberLazyListState()

                    LaunchedEffect(uiState.currentEpisode?.id) {
                        val index = rightRailEpisodes.indexOfFirst { it.id == uiState.currentEpisode?.id }
                        if (index >= 0) {
                            listState.animateScrollToItem(index)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .width(62.dp)
                            .fillMaxHeight()
                            .background(Color(0xE6141414))
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            items(rightRailEpisodes, key = { it.id }) { ep ->
                                val isCurrent = ep.id == uiState.currentEpisode?.id
                                val isWatched = uiState.watchedEpisodeIds.contains(ep.id)

                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isCurrent) ReeloYellow else if (isWatched) Color(0xFF1E1E1E) else Color(0xFF2D2D2D)
                                        )
                                        .clickable { viewModel.selectEpisode(ep) }
                                        .testTag("watch_episode_${ep.episodeNumber}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${ep.episodeNumber}",
                                        color = if (isCurrent) ReeloBlack else if (isWatched) Color.Gray else Color.White,
                                        fontWeight = if (isCurrent) FontWeight.Black else FontWeight.SemiBold,
                                        fontSize = 15.sp
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

// =============================================================================
// WEBVIEW CONFIGURATION & SECURITY INTERCEPTION
// =============================================================================

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
    context: android.content.Context,
    viewModel: WatchViewModel,
    sourceUrl: String,
    onShowCustomView: (View?, WebChromeClient.CustomViewCallback?) -> Unit,
    onHideCustomView: () -> Unit,
    onUpdateNavState: (Boolean, Boolean) -> Unit,
    onAttach: (WebView) -> Unit
): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(android.graphics.Color.BLACK)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            loadWithOverviewMode = true
            useWideViewPort = true
            userAgentString = viewModel.uiState.value.userAgentMode.value
        }

        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

        // Feature 5 & 7: Inject Document Start Scripts (Main frame + subframes)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(this, WatchScripts.AD_CLEANER_JS, setOf("*"))
            WebViewCompat.addDocumentStartJavaScript(this, WatchScripts.VIDEO_RESUME_BRIDGE_JS, setOf("*"))
        }

        // Feature 7: WebMessageListener for ReeloBridge communication
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(this, "ReeloBridge", setOf("*")) { view, message, sourceOrigin, isMainFrame, replyProxy ->
                try {
                    val rawData = message.data
                    if (!rawData.isNullOrBlank()) {
                        val json = JSONObject(rawData)
                        when (json.optString("type")) {
                            "ready" -> {
                                val dur = json.optInt("duration", 0)
                                viewModel.onVideoReady(dur)
                            }
                            "timeupdate" -> {
                                val cur = json.optInt("currentTime", 0)
                                val dur = json.optInt("duration", 0)
                                val ended = json.optBoolean("ended", false)
                                viewModel.onVideoTimeUpdate(cur, dur, ended)
                            }
                            "no_video" -> {
                                viewModel.onNoVideoFound()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ReeloResume", "Bridge message error: ${e.message}")
                }
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                viewModel.setWebViewProgress(newProgress)
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                onShowCustomView(view, callback)
            }

            override fun onHideCustomView() {
                onHideCustomView()
            }

            // Feature 6: Window / Popup Handling
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                val state = viewModel.uiState.value
                if (state.popupGatedPlayers) {
                    // Headless unattached background WebView to satisfy popup requirement without disturbing video
                    Log.d("ReeloAds", "Loading popup-gated player window in headless background WebView")
                    val headless = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(v: WebView?, url: String?) {
                                Handler(Looper.getMainLooper()).postDelayed({ destroy() }, 1000)
                            }
                        }
                    }
                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                    transport?.webView = headless
                    resultMsg?.sendToTarget()

                    // Safety timeout: destroy headless after 2.5s
                    Handler(Looper.getMainLooper()).postDelayed({
                        runCatching { headless.destroy() }
                    }, 2500)
                    return true
                }
                // Default DENY popups
                return false
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.deny()
            }
        }

        webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                view?.let {
                    (it.parent as? ViewGroup)?.removeView(it)
                    it.destroy()
                }
                viewModel.onWebPageError("Stream process stopped. Please try next server.")
                return true
            }

            // Feature 4: Main-frame navigation intercept (Stay on original site)
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase()

                // Block non-http(s) schemes like market://, intent://
                if (scheme != "http" && scheme != "https") {
                    Log.d("ReeloNav", "Blocked non-http(s) scheme: $uri")
                    return true
                }

                // Subframe / iframe requests are permitted for video players
                if (request.isForMainFrame == false) {
                    return false
                }

                val newHost = uri.host
                if (!viewModel.isHostTrusted(newHost)) {
                    viewModel.onAdRedirectBlocked(uri.toString())
                    return true // Block navigation
                }

                viewModel.updateCurrentPageUrl(uri.toString())
                return false
            }

            // Safety net: in onPageStarted, inspect main frame URL
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (url.isNullOrBlank()) return

                viewModel.updateCurrentPageUrl(url)
                val uri = Uri.parse(url)
                val host = uri.host

                // If before user interaction, record initial server redirect as trusted
                viewModel.addInitialRedirectHost(url)

                if (!viewModel.isHostTrusted(host)) {
                    Log.d("ReeloNav", "Safety net caught untrusted host: $host, reversing navigation")
                    view?.stopLoading()
                    viewModel.onAdRedirectBlocked(url)

                    val backList = view?.copyBackForwardList()
                    var returned = false
                    if (backList != null && backList.size > 1) {
                        for (i in backList.currentIndex - 1 downTo 0) {
                            val item = backList.getItemAtIndex(i)
                            val itemHost = Uri.parse(item.url).host
                            if (viewModel.isHostTrusted(itemHost)) {
                                view.goBackOrForward(i - backList.currentIndex)
                                returned = true
                                break
                            }
                        }
                    }
                    if (!returned && !sourceUrl.isNullOrBlank()) {
                        val headers = createUniversalHeaders(sourceUrl, viewModel.uiState.value.userAgentMode.value)
                        view?.loadUrl(sourceUrl, headers)
                    }
                }
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                if (url.isNullOrBlank()) return

                val host = Uri.parse(url).host
                if (!viewModel.isHostTrusted(host)) {
                    Log.d("ReeloNav", "History update detected untrusted host: $host, stopping")
                    view?.stopLoading()
                    viewModel.onAdRedirectBlocked(url)
                    view?.loadUrl(sourceUrl, createUniversalHeaders(sourceUrl, viewModel.uiState.value.userAgentMode.value))
                }
            }

            // Feature 5: Sub-resource ad domain blocking
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (viewModel.uiState.value.adCleanerEnabled) {
                    val host = request?.url?.host
                    if (AdBlockerHelper.isAdHost(host)) {
                        Log.d("ReeloAds", "Intercepted ad subresource: ${request?.url}")
                        return AdBlockerHelper.createEmptyResponse()
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel()
                viewModel.onWebPageError("SSL Security Verification Failed")
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    viewModel.onWebPageError("Failed to load stream page (${error?.description})")
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                onUpdateNavState(view?.canGoBack() ?: false, view?.canGoForward() ?: false)

                // Fallback script evaluation if DOCUMENT_START_SCRIPT is unsupported
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    view?.evaluateJavascript(WatchScripts.AD_CLEANER_JS, null)
                    view?.evaluateJavascript(WatchScripts.VIDEO_RESUME_BRIDGE_JS, null)
                }
            }
        }

        val headers = createUniversalHeaders(sourceUrl, viewModel.uiState.value.userAgentMode.value)
        loadUrl(sourceUrl, headers)
        onAttach(this)
    }
}

private fun updateWebView(view: WebView, viewModel: WatchViewModel, sourceUrl: String?, userAgent: String) {
    view.settings.userAgentString = userAgent
    if (view.url != sourceUrl && !sourceUrl.isNullOrBlank()) {
        view.clearHistory()
        val headers = createUniversalHeaders(sourceUrl, userAgent)
        view.loadUrl(sourceUrl, headers)
    }
}

private fun createUniversalHeaders(url: String, userAgent: String): Map<String, String> {
    val uri = Uri.parse(url)
    val host = uri.host ?: ""
    val headers = mutableMapOf<String, String>()
    if (host.isNotEmpty()) {
        headers["Referer"] = "${uri.scheme ?: "https"}://$host/"
        headers["Origin"] = "${uri.scheme ?: "https"}://$host"
    }
    headers["User-Agent"] = userAgent
    return headers
}
