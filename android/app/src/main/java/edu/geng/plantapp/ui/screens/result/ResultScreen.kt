package edu.geng.plantapp.ui.screens.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.geng.plantapp.data.local.DataStoreManager
import edu.geng.plantapp.data.remote.NetworkClient
import edu.geng.plantapp.repository.FeedbackRepository
import edu.geng.plantapp.repository.PredictRepository
import edu.geng.plantapp.repository.Resource
import edu.geng.plantapp.ui.screens.home.GlassCard
import edu.geng.plantapp.ui.screens.home.viewmodel.HomeViewModel
import edu.geng.plantapp.ui.screens.home.viewmodel.HomeViewModelFactory
import edu.geng.plantapp.ui.screens.result.viewmodel.ResultViewModel
import edu.geng.plantapp.ui.screens.result.viewmodel.ResultViewModelFactory
import edu.geng.plantapp.ui.screens.result.viewmodel.WikiState
import edu.geng.plantapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    diseaseLabel: String,
    imageUri: String? = null,
    historyId: Int = -1,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val dsManager = remember { DataStoreManager(context) }
    val predictRepository = remember { PredictRepository(NetworkClient.predictApi, dsManager) }
    val feedbackRepo = remember { FeedbackRepository(NetworkClient.predictApiExtension, NetworkClient.feedbackApi, dsManager) }

    val viewModel: ResultViewModel = viewModel(
        factory = ResultViewModelFactory(predictRepository)
    )
    val feedbackViewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(feedbackRepo)
    )

    val wikiState by viewModel.wikiState.collectAsState()
    val feedbackState by feedbackViewModel.feedbackState.collectAsState()

    var showFeedbackDialog by remember { mutableStateOf(false) }
    var feedbackComment by remember { mutableStateOf("") }
    var showFullScreenImage by remember { mutableStateOf(false) }
    val hasHistoryId = historyId != -1

    // 发起网络请求
    LaunchedEffect(diseaseLabel) {
        viewModel.fetchWikiInfo(diseaseLabel)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(BackgroundDarkStart, BackgroundDarkEnd),
                    start = Offset(0f, 0f),
                    end = Offset(0f, Float.POSITIVE_INFINITY)
                )
            )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "病害处方报告",
                            color = TextMainDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = TextMainDark)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (wikiState) {
                    is WikiState.Idle, is WikiState.Loading -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = PrimaryLight)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("正从云端百科检索 [$diseaseLabel] 的档案...", color = TextMutedDark)
                        }
                    }
                    is WikiState.Error -> {
                        val errorMsg = (wikiState as WikiState.Error).message
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = "错误", tint = ErrorColor, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "抱歉，检索中断\n$errorMsg",
                                color = TextMainDark,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 24.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onNavigateBack,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                            ) {
                                Text("返回主面板")
                            }
                        }
                    }
                    is WikiState.Success -> {
                        val wikiData = (wikiState as WikiState.Success).response.data

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            
                            if (imageUri != null) {
                                GlassCard {
                                    AsyncImage(
                                        model = imageUri,
                                        contentDescription = "预测图像",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable { showFullScreenImage = true },
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                            }

                            // 标题 & 危险等级 Card
                            GlassCard {
                                Column(modifier = Modifier.padding(24.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = wikiData?.title ?: diseaseLabel,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryLight,
                                            modifier = Modifier.weight(1f)
                                        )
                                        
                                        var dangerColor = PrimaryLight
                                        if (wikiData?.danger_level == "高危") dangerColor = ErrorColor
                                        
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = dangerColor.copy(alpha = 0.2f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, dangerColor)
                                        ) {
                                            Text(
                                                text = wikiData?.danger_level ?: "未知",
                                                color = dangerColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "系统基准标签：$diseaseLabel",
                                        fontSize = 12.sp,
                                        color = TextMutedDark.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            // 病症表现
                            GlassCard {
                                Column(modifier = Modifier.padding(24.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Info, contentDescription = null, tint = WarningColor)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "症状表现特征",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextMainDark
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        wikiData?.symptoms ?: "暂无收录症状表现",
                                        fontSize = 15.sp,
                                        color = TextMutedDark,
                                        lineHeight = 24.sp
                                    )
                                }
                            }

                            // 处方方案
                            GlassCard {
                                Column(modifier = Modifier.padding(24.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessColor)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "建议防治方案",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextMainDark
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        wikiData?.standard_treatment ?: "该病症属于特殊疑难杂症，当前基础百科库没有收录对应药方，请联系专家求助。",
                                        fontSize = 15.sp,
                                        color = TextMutedDark,
                                        lineHeight = 26.sp
                                    )
                                }
                            }
                            
                                Spacer(modifier = Modifier.height(10.dp))

                            // ==== 反馈区域 ====
                            if (hasHistoryId) {
                                GlassCard {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "💬 识别结果对您有帮助吗？",
                                            fontWeight = FontWeight.Bold,
                                            color = TextMainDark,
                                            fontSize = 15.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "您的反馈将帮助我们持续改进模型精度",
                                            fontSize = 12.sp,
                                            color = TextMutedDark
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))

                                        if (feedbackState is Resource.Success) {
                                            Text(
                                                "✅ 反馈已收到，感谢您的参与！",
                                                color = SuccessColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                        } else {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // 👍 確认准确
                                                Button(
                                                    onClick = {
                                                        feedbackViewModel.submitFeedback(historyId, 1, "")
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessColor.copy(alpha = 0.15f)),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, SuccessColor),
                                                    shape = RoundedCornerShape(12.dp),
                                                    enabled = feedbackState !is Resource.Loading
                                                ) {
                                                    Icon(
                                                        Icons.Default.ThumbUp,
                                                        contentDescription = null,
                                                        tint = SuccessColor,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("识别准确", color = SuccessColor, fontSize = 13.sp)
                                                }
                                                // 👎 纠错
                                                Button(
                                                    onClick = {
                                                        feedbackComment = ""
                                                        showFeedbackDialog = true
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.buttonColors(containerColor = WarningColor.copy(alpha = 0.15f)),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, WarningColor),
                                                    shape = RoundedCornerShape(12.dp),
                                                    enabled = feedbackState !is Resource.Loading
                                                ) {
                                                    Text("👎 我要纠错", color = WarningColor, fontSize = 13.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            } // end of feedback if-block

                            Spacer(modifier = Modifier.height(24.dp))
                        } // end of WikiState.Success Column
                    } // end of WikiState.Success
                } // end of when
            } // end of inner Box
        } // end of Scaffold
    } // end of outer Box

    // 纠错对话框
    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            containerColor = Color(0xFF1E2A1E),
            titleContentColor = ErrorColor,
            textContentColor = Color.White,
            title = { Text("识别纠错反馈", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "AI 并非无所不能，如果识别结果有误，欢迎告知我们。您的反馈将成为改进模型的宝贵数据。",
                        fontSize = 13.sp,
                        color = Color(0xFFB0BEC5),
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = feedbackComment,
                        onValueChange = { feedbackComment = it },
                        placeholder = { Text("例如：这应该是苹果黑星病，不是炭疽病", color = Color(0xFF607D8B), fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ErrorColor,
                            unfocusedBorderColor = Color(0xFF37474F),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = ErrorColor
                        ),
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 3
                    )
                    if (feedbackState is Resource.Error) {
                        Text(
                            text = (feedbackState as Resource.Error).message ?: "提交失败",
                            color = ErrorColor,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (feedbackComment.isNotBlank()) {
                            feedbackViewModel.submitFeedback(historyId, -1, feedbackComment)
                        }
                        showFeedbackDialog = false
                    },
                    enabled = feedbackComment.isNotBlank()
                ) {
                    Text("确认提交", color = ErrorColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text("取消", color = Color(0xFF607D8B))
                }
            }
        )
    }

    // 全屏图片查看
    if (showFullScreenImage && imageUri != null) {
        Dialog(
            onDismissRequest = { showFullScreenImage = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offsetX by remember { mutableFloatStateOf(0f) }
            var offsetY by remember { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "全屏图像查看",
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                val maxX = (size.width * (scale - 1)) / 2
                                val maxY = (size.height * (scale - 1)) / 2
                                offsetX = (offsetX + pan.x * scale).coerceIn(-maxX, maxX)
                                offsetY = (offsetY + pan.y * scale).coerceIn(-maxY, maxY)
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        ),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
                
                IconButton(
                    onClick = { showFullScreenImage = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
