package com.example.worldradio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(this)
            } else {
                darkColorScheme(
                    primary = Color(0xFF7B61FF),
                    secondary = Color(0xFF00D2FF),
                    background = Color(0xFF0B0C14),
                    surface = Color(0xFF171926)
                )
            }
            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0B0C14)
                ) {
                    RadioApp()
                }
            }
        }
    }
}

@Composable
fun StationArtwork(
    faviconUrl: String?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp
) {
    val cleanUrl = faviconUrl?.trim().orEmpty()
    if (cleanUrl.startsWith("http")) {
        SubcomposeAsyncImage(
            model = cleanUrl,
            contentDescription = "Ikona stacji",
            modifier = modifier.background(Color(0xFF1E2235)),
            contentScale = ContentScale.Crop,
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(iconSize)
                    )
                }
            },
            error = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        )
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Radio,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioApp(viewModel: RadioViewModel = viewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var isPlayerExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color(0xFF0B0C14),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0B0C14),
                        titleContentColor = Color.White
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.mipmap.ic_launcher_round),
                                contentDescription = "Logo Dorota Radio",
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Dorota Radio",
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Dodaj własną stację",
                                tint = Color.White
                            )
                        }
                    }
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0B0C14))
                ) {
                    viewModel.currentStation?.let { station ->
                        SamsungMiniPlayer(
                            station = station,
                            songTitle = viewModel.currentSongTitle,
                            isPlaying = viewModel.isPlaying,
                            isFavorite = viewModel.isFavorite(station),
                            onPlayPause = { viewModel.togglePlayPause() },
                            onNext = { viewModel.nextStation() },
                            onPrev = { viewModel.previousStation() },
                            onToggleFav = { viewModel.toggleFavorite(station) },
                            onExpand = { isPlayerExpanded = true }
                        )
                    }
                    NavigationBar(
                        containerColor = Color(0xFF0B0C14),
                        tonalElevation = 0.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Default.Search, contentDescription = null) },
                            label = { Text("Odkrywaj", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF8C7AE6),
                                selectedTextColor = Color(0xFF8C7AE6),
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = Color(0xFF1F2235)
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                            label = { Text("Ulubione (${viewModel.favoriteStations.size})", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF8C7AE6),
                                selectedTextColor = Color(0xFF8C7AE6),
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = Color(0xFF1F2235)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                if (selectedTab == 0) {
                    SearchScreen(viewModel)
                } else {
                    FavoritesScreen(viewModel)
                }
            }
        }

        AnimatedVisibility(
            visible = isPlayerExpanded && viewModel.currentStation != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            viewModel.currentStation?.let { station ->
                SamsungFullPlayerScreen(
                    station = station,
                    songTitle = viewModel.currentSongTitle,
                    isPlaying = viewModel.isPlaying,
                    isFavorite = viewModel.isFavorite(station),
                    onPlayPause = { viewModel.togglePlayPause() },
                    onNext = { viewModel.nextStation() },
                    onPrev = { viewModel.previousStation() },
                    onToggleFav = { viewModel.toggleFavorite(station) },
                    onCollapse = { isPlayerExpanded = false },
                    onStopPlayback = { viewModel.stopPlayback() }
                )
            }
        }
    }

    if (showAddDialog) {
        AddStationDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, url, country, iconUrl ->
                viewModel.addCustomStation(name, url, country, iconUrl)
                showAddDialog = false
                selectedTab = 1
            }
        )
    }
}

@Composable
fun SamsungMiniPlayer(
    station: RadioStation,
    songTitle: String,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onToggleFav: () -> Unit,
    onExpand: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "disc_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "disc_rotation"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(12.dp, RoundedCornerShape(32.dp))
            .clip(RoundedCornerShape(32.dp))
            .clickable { onExpand() },
        color = Color(0xFF1F2233),
        shape = RoundedCornerShape(32.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StationArtwork(
                faviconUrl = station.favicon,
                iconSize = 24.dp,
                modifier = Modifier
                    .size(46.dp)
                    .rotate(if (isPlaying) rotation else 0f)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF7B61FF), Color(0xFF00D2FF))
                        )
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name.trim(),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = songTitle,
                    color = Color(0xFF00D2FF),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Poprzednia",
                    tint = Color.White
                )
            }

            IconButton(onClick = onPlayPause, modifier = Modifier.size(42.dp)) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Odtwórz/Wstrzymaj",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Następna",
                    tint = Color.White
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SamsungFullPlayerScreen(
    station: RadioStation,
    songTitle: String,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onToggleFav: () -> Unit,
    onCollapse: () -> Unit,
    onStopPlayback: () -> Unit
) {
    BackHandler(onBack = onCollapse)

    var showWebPlayer by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var currentVolume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat())
    }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF281C4D),
            Color(0xFF131629),
            Color(0xFF090A12)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Zwiń odtwarzacz",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = if (showWebPlayer) "ODTWARZACZ WEB (RAYO)" else "TERAZ ODTWARZANE",
                color = Color(0xFFB5B9C9),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Row {
                // Przycisk przełączania na odtwarzacz Web (WebView dla stacji Rayo / Beverley FM)
                IconButton(onClick = {
                    if (!showWebPlayer) onStopPlayback()
                    showWebPlayer = !showWebPlayer
                }) {
                    Icon(
                        imageVector = if (showWebPlayer) Icons.Default.Radio else Icons.Default.Public,
                        contentDescription = "Przełącz na WebPlayer / Stream",
                        tint = if (showWebPlayer) Color(0xFF00D2FF) else Color
