package edu.geng.plantapp.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.geng.plantapp.data.local.DataStoreManager
import edu.geng.plantapp.data.remote.NetworkClient
import edu.geng.plantapp.ml.TFLiteHelper
import edu.geng.plantapp.repository.FeedbackRepository
import edu.geng.plantapp.repository.Resource
import edu.geng.plantapp.ui.screens.home.GlassCard
import edu.geng.plantapp.ui.screens.home.viewmodel.HomeState
import edu.geng.plantapp.ui.screens.home.viewmodel.HomeViewModel
import edu.geng.plantapp.ui.screens.home.viewmodel.HomeViewModelFactory
import edu.geng.plantapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToResult: (String, String?, Int) -> Unit
) {
    val context = LocalContext.current
    val dsManager = remember { DataStoreManager(context) }
    val feedbackRepo = remember {
        FeedbackRepository(
            NetworkClient.predictApiExtension,
            NetworkClient.feedbackApi,
            dsManager,
            context
        )
    }
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(feedbackRepo))
    val uiState by viewModel.uiState.collectAsState()


    var currentPage by remember { mutableIntStateOf(1) }
    val itemsPerPage = 6
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.fetchHistory()
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(BackgroundDarkStart, BackgroundDarkEnd),
                    start = Offset(0f, 0f),
                    end = Offset(100f, 2000f)
                )
            )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "历史档案库",
                            color = TextMainDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = TextMainDark
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                when (uiState) {
                    is HomeState.Loading -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            CircularProgressIndicator(color = PrimaryLight)
                        }
                    }

                    is HomeState.Error -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                "获取历史档案失败: ${(uiState as HomeState.Error).message}",
                                color = ErrorColor,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    is HomeState.Success -> {
                        val allHistoryList = (uiState as HomeState.Success).history

                        // Search bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                currentPage = 1
                            },
                            placeholder = { Text("搜索病害名称...", color = TextMutedDark, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索", tint = PrimaryLight) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = ""; currentPage = 1 }) {
                                        Icon(Icons.Default.Close, contentDescription = "清除", tint = TextMutedDark)
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryLight,
                                unfocusedBorderColor = SurfaceBorderLight,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = PrimaryLight
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(16.dp)
                        )

                        // Fuzzy filter
                        val filteredList = if (searchQuery.isBlank()) {
                            allHistoryList
                        } else {
                            val q = searchQuery.lowercase()
                            allHistoryList.filter { item ->
                                val prediction = item.prediction?.lowercase() ?: ""
                                val chineseName = TFLiteHelper.getChineseName(item.prediction ?: "").lowercase()
                                prediction.contains(q) || chineseName.contains(q)
                            }
                        }

                        if (filteredList.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (searchQuery.isNotBlank()) "未找到匹配的档案记录" else "暂无历史存证",
                                    color = TextMutedDark,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            val totalPages =
                                if (filteredList.isEmpty()) 1 else (filteredList.size + itemsPerPage - 1) / itemsPerPage
                            if (currentPage > totalPages && totalPages > 0) currentPage = totalPages
                            val displayList = filteredList.drop((currentPage - 1) * itemsPerPage)
                                .take(itemsPerPage)

                            Column(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(bottom = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(
                                        items = displayList,
                                        key = { item -> item.id ?: item.hashCode() }
                                    ) { item ->
                                        GlassCard {
                                            Row(
                                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = TFLiteHelper.getChineseName(
                                                                item.prediction ?: ""
                                                            ),
                                                            color = TextMainDark,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 16.sp
                                                        )
                                                        if (item.engine != null) {
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            val isPth = item.engine == "pth"
                                                            val badgeColor =
                                                                if (isPth) Color(0xFF42A5F5) else Color(
                                                                    0xFF66BB6A
                                                                )
                                                            Text(
                                                                text = if (isPth) "☁️ torch" else "📱 tflite",
                                                                color = badgeColor,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier
                                                                    .border(
                                                                        1.dp,
                                                                        badgeColor,
                                                                        RoundedCornerShape(4.dp)
                                                                    )
                                                                    .padding(
                                                                        horizontal = 4.dp,
                                                                        vertical = 2.dp
                                                                    )
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "${item.confidence ?: "--%"} · ${item.time ?: "未知时间"}",
                                                        color = TextMutedDark,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                                // Action: 看处方 only
                                                TextButton(
                                                    onClick = {
                                                        val imageUrl =
                                                            if (item.image_url.isNullOrEmpty()) null
                                                            else NetworkClient.BASE_URL.replace("api/v1/", "") + "uploads/recognition/" + item.image_url
                                                        onNavigateToResult(item.prediction ?: "Unknown", imageUrl, item.id)
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                                ) {
                                                    Text("看处方", color = PrimaryLight, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                                // 分页控制底座
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { if (currentPage > 1) currentPage-- },
                                        enabled = currentPage > 1
                                    ) {
                                        Text(
                                            "上一页",
                                            color = if (currentPage > 1) PrimaryLight else Color.Gray
                                        )
                                    }

                                    Text(
                                        text = "$currentPage / $totalPages",
                                        color = TextMutedDark,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    TextButton(
                                        onClick = { if (currentPage < totalPages) currentPage++ },
                                        enabled = currentPage < totalPages
                                    ) {
                                        Text(
                                            "下一页",
                                            color = if (currentPage < totalPages) PrimaryLight else Color.Gray
                                        )
                                    }
                                } // end of pagination Row
                            } // end of Column
                        } // end of else (non-empty filteredList)
                    } // end of HomeState.Success

                    else -> {}
                } // end of when
            } // end of Scaffold content Column
        } // end of Scaffold
    } // end of outer Box
}

// Reusable Glass Card UI Widget
@Composable
fun GlassCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark, shape = RoundedCornerShape(20.dp))
            .border(1.dp, SurfaceBorderLight, shape = RoundedCornerShape(20.dp))
    ) {
        content()
    }
}
