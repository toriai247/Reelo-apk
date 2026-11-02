package com.example.ui.screens.watch

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.data.model.Episode
import com.example.ui.screens.report.ReportSourceDialog
import com.example.ui.theme.ReeloBlack
import com.example.ui.theme.ReeloYellow
import com.example.ui.theme.SuccessGreen

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

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var customVideoView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Enforce landscape and keep screen on for watching
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Hide system bars for immersive video experience
        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

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

    // Hardware Back Handling: Custom fullscreen -> WebView goBack -> Close WatchScreen
    BackHandler {
        if (customVideoView != null) {
            customViewCallback?.onCustomViewHidden()
            customVideoView = null
            customViewCallback = null
        } else if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            onExit()
        }
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
                Text(
                    text = "Streaming Servers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ReeloYellow
                )

                Text(
                    text = "Select an alternative server if playback is buffering or unavailable:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )

                uiState.sources.forEachIndexed { index, source ->
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

    // Main Watch Landscape Layout
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // If Custom Video View is active (Fullscreen from website), show it over everything
        if (customVideoView != null) {
            AndroidView(
                factory = {
                    (customVideoView?.parent as? ViewGroup)?.removeView(customVideoView)
                    customVideoView!!
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Rail: Navigation, Reload, Exit, Servers
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
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Exit Button
                            IconButton(
                                onClick = onExit,
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("watch_exit_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Exit Watch",
                                    tint = Color.White
                                )
                            }

                            // Back inside WebView
                            IconButton(
                                onClick = { webViewRef?.goBack() },
                                enabled = canGoBack,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Browser Back",
                                    tint = if (canGoBack) ReeloYellow else Color.DarkGray
                                )
                            }

                            // Forward inside WebView
                            IconButton(
                                onClick = { webViewRef?.goForward() },
                                enabled = canGoForward,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Browser Forward",
                                    tint = if (canGoForward) ReeloYellow else Color.DarkGray
                                )
                            }

                            // Reload
                            IconButton(
                                onClick = { webViewRef?.reload() },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reload Page",
                                    tint = ReeloYellow
                                )
                            }
                        }

                        // Bottom Servers & Settings
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { viewModel.toggleServersSheet(true) },
                                modifier = Modifier
                                    .size(40.dp)
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
                }

                // Center: WebView + Progress Bar + Overlays
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color.Black)
                ) {
                    // WebView Center Area
                    val sourceUrl = uiState.currentSource?.url
                    if (!sourceUrl.isNullOrBlank() && !uiState.hasError) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    setBackgroundColor(android.graphics.Color.BLACK)

                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        mediaPlaybackRequiresUserGesture = false
                                        allowFileAccess = false
                                        allowContentAccess = false
                                        setGeolocationEnabled(false)
                                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                        loadWithOverviewMode = true
                                        useWideViewPort = true
                                        setSupportZoom(true)
                                        builtInZoomControls = true
                                        displayZoomControls = false
                                    }

                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            viewModel.setWebViewProgress(newProgress)
                                        }

                                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                            customVideoView = view
                                            customViewCallback = callback
                                        }

                                        override fun onHideCustomView() {
                                            customViewCallback?.onCustomViewHidden()
                                            customVideoView = null
                                            customViewCallback = null
                                        }

                                        override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                                            // Popups blocked by default per security requirements unless setting allowed
                                            return uiState.allowPopups
                                        }

                                        override fun onPermissionRequest(request: PermissionRequest?) {
                                            // Deny camera/mic requests for safety
                                            request?.deny()
                                        }
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val uri = request?.url ?: return false
                                            val scheme = uri.scheme?.lowercase()

                                            // Block non-http/https
                                            if (scheme != "http" && scheme != "https") {
                                                return true
                                            }

                                            // Anti-redirect protection: block unprompted redirects to external hosts
                                            val currentHost = Uri.parse(view?.url ?: "").host
                                            val newHost = uri.host
                                            if (!request.hasGesture() && currentHost != null && newHost != null && !currentHost.equals(newHost, ignoreCase = true)) {
                                                return true
                                            }

                                            return false
                                        }

                                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                            handler?.cancel() // NEVER proceed on SSL error per security rules
                                            viewModel.onWebPageError("SSL Security Verification Failed")
                                        }

                                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                            if (request?.isForMainFrame == true) {
                                                viewModel.onWebPageError("Failed to load stream page (${error?.description})")
                                            }
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            canGoBack = view?.canGoBack() ?: false
                                            canGoForward = view?.canGoForward() ?: false
                                        }
                                    }

                                    loadUrl(sourceUrl)
                                    webViewRef = this
                                }
                            },
                            update = { view ->
                                if (view.url != sourceUrl && !sourceUrl.isNullOrBlank()) {
                                    view.clearHistory()
                                    view.loadUrl(sourceUrl)
                                    canGoBack = false
                                    canGoForward = false
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Thin Yellow Progress Bar at the top while page loads
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

                    // Error & Fallback Overlay
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
                                    text = uiState.errorMessage ?: "Source playback issue",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        onClick = { viewModel.tryNextServer() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ReeloYellow,
                                            contentColor = ReeloBlack
                                        )
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

                    // Floating Rails Toggle Handle (to collapse / expand rails for video focus)
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

                // Right Rail: Season Chip & Vertical Scrollable Episode Numbers
                AnimatedVisibility(
                    visible = uiState.railsExpanded,
                    enter = slideInHorizontally(initialOffsetX = { it }),
                    exit = slideOutHorizontally(targetOffsetX = { it })
                ) {
                    val rightRailEpisodes = uiState.episodes.filter {
                        if (uiState.seasons.size > 1) it.seasonNumber == uiState.selectedSeasonNumber else true
                    }
                    val listState = rememberLazyListState()

                    // Auto-scroll so current episode number is centered
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
                        // Season badge at top (tap to switch if multiple)
                        if (uiState.seasons.size > 1) {
                            Surface(
                                color = Color(0xFF2A2A2A),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .padding(bottom = 6.dp)
                                    .clickable {
                                        val nextSeason = (uiState.selectedSeasonNumber % uiState.seasons.size) + 1
                                        val firstOfSeason = uiState.episodes.find { it.seasonNumber == nextSeason }
                                        if (firstOfSeason != null) {
                                            viewModel.selectEpisode(firstOfSeason)
                                        }
                                    }
                            ) {
                                Text(
                                    text = "S${uiState.selectedSeasonNumber}",
                                    color = ReeloYellow,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Next Episode Chip (if completed)
                        if (uiState.isCompleted) {
                            Surface(
                                color = ReeloYellow,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .padding(bottom = 6.dp)
                                    .clickable { viewModel.nextEpisode() }
                            ) {
                                Text(
                                    text = "Next ▶",
                                    color = ReeloBlack,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Vertical list of Episode Numbers
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
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "${ep.episodeNumber}",
                                            color = if (isCurrent) ReeloBlack else if (isWatched) Color.Gray else Color.White,
                                            fontWeight = if (isCurrent) FontWeight.Black else FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        )
                                        if (isWatched && !isCurrent) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Watched",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(10.dp)
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
    }
}
