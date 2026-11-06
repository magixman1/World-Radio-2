package com.example.worldradio

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

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
                        Text(
                            "Dorota Radio",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
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
                    // Mini-odtwarzacz w stylu pigułki Samsung Music
                    viewModel.currentStation?.let { station ->
                        SamsungMiniPlayer(
                            station = station,
                            isPlaying = viewModel.isPlaying,
                            isFavorite = viewModel.isFavorite(station),
                            onPlayPause = { viewModel.togglePlayPause() },
                            onNext = { viewModel.nextStation() },
                            onPrev = { viewModel.previousStation() },
                            onToggleFav = { viewModel.toggleFavorite(station) },
                            onExpand = { isPlayerExpanded = true }
                        )
                    }
                    // Dolne zakładki w stylu Samsung Music (tekstowe / minimalistyczne ikony)
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

        // Pełnoekranowy odtwarzacz w stylu Samsung Music (One UI)
        AnimatedVisibility(
            visible = isPlayerExpanded && viewModel.currentStation != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            viewModel.currentStation?.let { station ->
                SamsungFullPlayerScreen(
                    station = station,
                    isPlaying = viewModel.isPlaying,
                    isFavorite = viewModel.isFavorite(station),
                    onPlayPause = { viewModel.togglePlayPause() },
                    onNext = { viewModel.nextStation() },
                    onPrev = { viewModel.previousStation() },
                    onToggleFav = { viewModel.toggleFavorite(station) },
                    onCollapse = { isPlayerExpanded = false }
                )
            }
        }
    }

    if (showAddDialog) {
        AddStationDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, url, country ->
                viewModel.addCustomStation(name, url, country)
                showAddDialog = false
                selectedTab = 1
            }
        )
    }
}

@Composable
fun SamsungMiniPlayer(
    station: RadioStation,
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
            // Okrągła okładka / obracająca się tarcza jak w Samsung Music
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .rotate(if (isPlaying) rotation else 0f)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF7B61FF), Color(0xFF00D2FF))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

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
                    text = station.country.ifEmpty { "Stacja radiowa" },
                    color = Color(0xFFA0A5BA),
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

@Composable
fun SamsungFullPlayerScreen(
    station: RadioStation,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onToggleFav: () -> Unit,
    onCollapse: () -> Unit
) {
    BackHandler(onBack = onCollapse)

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
        // Górny pasek ze strzałką zwijania w dół (styl One UI)
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
                text = "TERAZ ODTWARZANE",
                color = Color(0xFFB5B9C9),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            IconButton(onClick = onToggleFav) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Ulubione",
                    tint = if (isFavorite) Color(0xFFFF4D6D) else Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Duża centralna karta albumu z miękkim cieniem i zaokrągleniem Samsung Music
        Box(
            modifier = Modifier
                .size(270.dp)
                .shadow(24.dp, RoundedCornerShape(36.dp))
                .clip(RoundedCornerShape(36.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6A3DE8),
                            Color(0xFF1F7AE0),
                            Color(0xFF00D2FF)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Radio,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(96.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = Color.Black.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isPlaying) Color(0xFF00FF88) else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPlaying) "LIVE STREAM" else "PAUZA",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Tytuł stacji i kraj / tagi wyśrodkowane jak w Samsung Music
        Text(
            text = station.name.trim(),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${station.country.ifEmpty { "Świat" }} • ${station.tags.ifEmpty { "Radio internetowe" }}",
            color = Color(0xFFA5AABF),
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Wizualny pasek fali dźwiękowej / strumienia na żywo
        SamsungWaveBar(isPlaying = isPlaying)

        Spacer(modifier = Modifier.weight(1f))

        // Główne przyciski sterowania odtwarzaczem (Poprzednia, Duży okrągły Play/Pause, Następna)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrev,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Poprzednia stacja",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Surface(
                modifier = Modifier
                    .size(78.dp)
                    .shadow(16.dp, CircleShape)
                    .clip(CircleShape)
                    .clickable { onPlayPause() },
                shape = CircleShape,
                color = Color.White
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color(0xFF131629),
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            IconButton(
                onClick = onNext,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Następna stacja",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Dolny suwak głośności w stylu Samsung Music
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.VolumeDown,
                contentDescription = "Ciszej",
                tint = Color(0xFFA5AABF)
            )
            Slider(
                value = currentVolume,
                onValueChange = { newVol ->
                    currentVolume = newVol
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol.toInt(), 0)
                },
                valueRange = 0f..maxVolume.toFloat(),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF8C7AE6),
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Głośniej",
                tint = Color(0xFFA5AABF)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun SamsungWaveBar(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave_anim"
    )

    val heights = listOf(0.4f, 0.7f, 1.0f, 0.6f, 0.9f, 0.5f, 0.8f, 1.0f, 0.6f, 0.9f, 0.5f, 0.7f, 0.4f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            heights.forEachIndexed { index, base ->
                val factor = if (isPlaying) {
                    if (index % 2 == 0) base * phase else base * (1.3f - phase)
                } else {
                    0.2f
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .width(4.dp)
                        .fillMaxHeight(factor.coerceIn(0.15f, 1f))
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF8C7AE6))
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("STRUMIEŃ AUDIO", color = Color(0xFF888EA8), fontSize = 11.sp)
            Text(if (isPlaying) "NA ŻYWO" else "WSTRZYMANO", color = Color(0xFF8C7AE6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SearchScreen(viewModel: RadioViewModel) {
    var query by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Szukaj stacji lub kraju (np. RMF, Rock, Polska)...", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF171926),
                unfocusedContainerColor = Color(0xFF171926),
                focusedBorderColor = Color(0xFF8C7AE6),
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            trailingIcon = {
                IconButton(onClick = { viewModel.search(query) }) {
                    Icon(Icons.Default.Search, contentDescription = "Szukaj", tint = Color(0xFF8C7AE6))
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) })
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (viewModel.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF8C7AE6))
            }
        } else {
            StationList(
                stations = viewModel.searchResults,
                currentStation = viewModel.currentStation,
                isFavorite = { viewModel.isFavorite(it) },
                onPlay = { viewModel.playStation(it) },
                onToggleFav = { viewModel.toggleFavorite(it) }
            )
        }
    }
}

@Composable
fun FavoritesScreen(viewModel: RadioViewModel) {
    if (viewModel.favoriteStations.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Brak zapisanych stacji.\nDodaj je z wyszukiwarki lub kliknij '+' u góry.",
                color = Color.Gray,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            StationList(
                stations = viewModel.favoriteStations,
                currentStation = viewModel.currentStation,
                isFavorite = { true },
                onPlay = { viewModel.playStation(it) },
                onToggleFav = { viewModel.toggleFavorite(it) }
            )
        }
    }
}

@Composable
fun StationList(
    stations: List<RadioStation>,
    currentStation: RadioStation?,
    isFavorite: (RadioStation) -> Boolean,
    onPlay: (RadioStation) -> Unit,
    onToggleFav: (RadioStation) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(stations, key = { it.id }) { station ->
            val isSelected = currentStation?.id == station.id
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { onPlay(station) },
                shape = RoundedCornerShape(22.dp),
                color = if (isSelected) Color(0xFF282442) else Color(0xFF171926)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) Brush.linearGradient(listOf(Color(0xFF7B61FF), Color(0xFF00D2FF)))
                                else Brush.linearGradient(listOf(Color(0xFF25283D), Color(0xFF25283D)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.GraphicEq else Icons.Default.Radio,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = if (isSelected) Color.White else Color(0xFF8C7AE6)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = station.name.trim(),
                            color = if (isSelected) Color(0xFFB5A8FF) else Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${station.country.ifEmpty { "Świat" }} • ${station.tags.ifEmpty { "Radio" }}",
                            color = Color(0xFF8E93A6),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onToggleFav(station) }) {
                        Icon(
                            imageVector = if (isFavorite(station)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Ulubione",
                            tint = if (isFavorite(station)) Color(0xFFFF4D6D) else Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddStationDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String, country: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1F2233),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Dodaj własną stację") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nazwa stacji") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL strumienia (np. mp3/aac)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it },
                    label = { Text("Kraj (opcjonalnie)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank() && url.isNotBlank()) onAdd(name, url, country) },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text("Dodaj")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        }
    )
}
