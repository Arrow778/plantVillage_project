package edu.geng.plantapp.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import android.graphics.ImageDecoder
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import edu.geng.plantapp.ml.TFLiteHelper
import edu.geng.plantapp.ui.theme.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.geng.plantapp.data.local.DataStoreManager
import edu.geng.plantapp.data.remote.NetworkClient
import edu.geng.plantapp.repository.FeedbackRepository
import edu.geng.plantapp.ui.screens.home.viewmodel.HomeState
import edu.geng.plantapp.ui.screens.home.viewmodel.HomeViewModel
import edu.geng.plantapp.ui.screens.home.viewmodel.HomeViewModelFactory
import edu.geng.plantapp.repository.PredictRepository
import edu.geng.plantapp.ui.screens.result.viewmodel.ResultViewModel
import edu.geng.plantapp.ui.screens.result.viewmodel.ResultViewModelFactory
import edu.geng.plantapp.ui.screens.result.viewmodel.WikiState
import edu.geng.plantapp.ui.screens.home.viewmodel.HistorySyncState
import edu.geng.plantapp.repository.Resource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToResult: (String, String?) -> Unit = { _, _ -> },
    onNavigateToProfile: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val dsManager = remember { DataStoreManager(context) }
    val feedbackRepo = remember {
        FeedbackRepository(
            NetworkClient.predictApiExtension,
            NetworkClient.feedbackApi,
            dsManager
        )
    }
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(feedbackRepo))
    val uiState by viewModel.uiState.collectAsState()

    val predictRepository = remember { PredictRepository(NetworkClient.predictApi, dsManager) }
    val resultViewModel: ResultViewModel =
        viewModel(factory = ResultViewModelFactory(predictRepository))
    val wikiState by resultViewModel.wikiState.collectAsState()

    val tfliteHelper = remember { TFLiteHelper(context) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val currentBitmap by viewModel.currentBitmap.collectAsState()
    val recognitionResult by viewModel.recognitionResult.collectAsState()
    val cloudPredictState by viewModel.cloudPredictState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val feedbackState by viewModel.feedbackState.collectAsState()
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var feedbackComment by remember { mutableStateOf("") }

    val hasShownWelcome by dsManager.hasShownWelcomeFlow.collectAsState(initial = true)
    var showWelcomeDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(hasShownWelcome) {
        if (!hasShownWelcome) {
            showWelcomeDialog = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.fetchHistory()
    }

    LaunchedEffect(recognitionResult?.label) {
        recognitionResult?.label?.let {
            resultViewModel.fetchWikiInfo(it)
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            try {
                val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val result = tfliteHelper.classify(softwareBitmap)
                viewModel.setPrediction(softwareBitmap, result)
                if (result != null) {
                    viewModel.syncEdgeResult(softwareBitmap, result.label, result.confidence)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            try {
                // 读取相片转为 Bitmap
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                // 转换色彩空间以适配张量
                val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                // 原生推理 (0ms 网络延迟)
                val result = tfliteHelper.classify(softwareBitmap)
                viewModel.setPrediction(softwareBitmap, result)
                if (result != null) {
                    viewModel.syncEdgeResult(softwareBitmap, result.label, result.confidence)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
                            "🌿 农作物检测中枢",
                            color = TextMainDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    actions = {
                        IconButton(onClick = { showWelcomeDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "使用说明",
                                tint = PrimaryLight,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(onClick = onNavigateToHistory) {
                            Icon(
                                imageVector = Icons.Filled.List,
                                contentDescription = "历史档案",
                                tint = PrimaryLight,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        IconButton(onClick = onNavigateToProfile) {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = "个人中心",
                                tint = PrimaryLight,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Main Analysis Entry Card (Glassmorphism layout matching admin panel vibe)
                GlassCard {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            "AI 智能识别引擎",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryLight
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "随时上传受损植物叶片样本，模型(tflite、torch)与私有无幻觉大模型(langchain)百科将强强联动分析，快速出具有效的农业指导方针。",
                            fontSize = 14.sp,
                            color = TextMutedDark,
                            lineHeight = 22.sp
                        )
                    }
                }

                // AI Tool Hint
                Text(
                    text = "💡 提示：AI 只是一个工具，回答内容只作为参考。",
                    color = WarningColor,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                if (currentBitmap != null) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        GlassCard {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = currentBitmap!!.asImageBitmap(),
                                    contentDescription = "待分析样本",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(280.dp)
                                        .clip(RoundedCornerShape(16.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                if (recognitionResult != null) {
                                    Text(
                                        text = "初步诊断: ${
                                            TFLiteHelper.getChineseName(
                                                recognitionResult!!.label
                                            )
                                        }",
                                        color = PrimaryLight,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "检测置信度: ${"%.2f".format(recognitionResult!!.confidence * 100)}%",
                                        color = TextMutedDark,
                                        fontSize = 14.sp
                                    )
                                } else {
                                    Text(
                                        "💡 图片已成功捕获，正在提取特征...",
                                        color = TextMutedDark,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    // Wiki State Inline Render
                    if (recognitionResult != null) {
                        when (wikiState) {
                            is WikiState.Loading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = PrimaryLight)
                                }
                            }

                            is WikiState.Error -> {
                                val errorMsg = (wikiState as WikiState.Error).message
                                GlassCard {
                                    Column(
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Filled.Warning,
                                            contentDescription = "错误",
                                            tint = ErrorColor
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "检索中断: $errorMsg",
                                            color = ErrorColor,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }

                            is WikiState.Success -> {
                                val wikiData = (wikiState as WikiState.Success).response.data
                                GlassCard {
                                    Column(modifier = Modifier.padding(24.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "百科文献诊断建议",
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PrimaryLight,
                                                modifier = Modifier.weight(1f)
                                            )
                                            var dangerColor = PrimaryLight
                                            if (wikiData?.danger_level == "高危") dangerColor =
                                                ErrorColor
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = dangerColor.copy(alpha = 0.2f),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    dangerColor
                                                )
                                            ) {
                                                Text(
                                                    text = wikiData?.danger_level ?: "未知评级",
                                                    color = dangerColor,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(
                                                        horizontal = 12.dp,
                                                        vertical = 4.dp
                                                    )
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // Symptoms
                                        wikiData?.symptoms?.let { syms ->
                                            if (syms.isNotEmpty()) {
                                                Text(
                                                    "典型症状表征",
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextMainDark,
                                                    fontSize = 16.sp
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(bottom = 6.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Info,
                                                        contentDescription = "症状",
                                                        tint = PrimaryGreen,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        syms,
                                                        color = TextMutedDark,
                                                        fontSize = 14.sp
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // Treatments
                                        wikiData?.standard_treatment?.let { treats ->
                                            if (treats.isNotEmpty()) {
                                                Text(
                                                    "综合防治方案",
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextMainDark,
                                                    fontSize = 16.sp
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(bottom = 6.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.CheckCircle,
                                                        contentDescription = "治疗",
                                                        tint = PrimaryLight,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        treats,
                                                        color = TextMutedDark,
                                                        fontSize = 14.sp
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(24.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(onClick = {
                                                viewModel.clearPrediction()
                                                resultViewModel.resetWikiState()
                                            }) {
                                                Text(
                                                    "清除诊断",
                                                    color = ErrorColor,
                                                    fontSize = 14.sp
                                                )
                                            }

                                            Row {
                                                val historyId =
                                                    (syncState as? HistorySyncState.Success)?.historyId
                                                if (historyId != null && historyId != -1) {
                                                    // 👍 Like button
                                                    TextButton(
                                                        onClick = {
                                                            viewModel.submitFeedback(
                                                                historyId,
                                                                1,
                                                                ""
                                                            )
                                                        },
                                                        enabled = feedbackState !is Resource.Loading && feedbackState !is Resource.Success
                                                    ) {
                                                        if (feedbackState is Resource.Success) {
                                                            Text(
                                                                "✅ 已反馈",
                                                                color = SuccessColor,
                                                                fontSize = 14.sp
                                                            )
                                                        } else {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(
                                                                    Icons.Default.ThumbUp,
                                                                    contentDescription = "准确",
                                                                    tint = SuccessColor,
                                                                    modifier = Modifier.size(15.dp)
                                                                )
                                                                Spacer(Modifier.width(4.dp))
                                                                Text(
                                                                    "识别准确",
                                                                    color = SuccessColor,
                                                                    fontSize = 14.sp
                                                                )
                                                            }
                                                        }
                                                    }
                                                    // 👎 Dislike / correction
                                                    TextButton(
                                                        onClick = { showFeedbackDialog = true },
                                                        enabled = feedbackState !is Resource.Loading && feedbackState !is Resource.Success
                                                    ) {
                                                        Text(
                                                            "我要纠错",
                                                            color = WarningColor,
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            else -> {}
                        }
                    }

                    // Cloud Predict Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = { viewModel.predictCloud() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5)),
                            shape = RoundedCornerShape(12.dp),
                            enabled = cloudPredictState !is Resource.Loading
                        ) {
                            if (cloudPredictState is Resource.Loading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Warning,
                                    "云端专家引擎",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "专家引擎 (PyTorch)",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (cloudPredictState is Resource.Error) {
                        Text(
                            text = (cloudPredictState as Resource.Error).message ?: "云端访问失败",
                            color = ErrorColor,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                } else {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Central Buttons for Image Action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { takePictureLauncher.launch(null) },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Filled.Add, "实时拍摄", modifier = Modifier.padding(end = 8.dp))
                        Text("实时拍照", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            "图库选图",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("图库上传", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showFeedbackDialog) {
        val historyId = (syncState as? HistorySyncState.Success)?.historyId ?: -1
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            containerColor = BackgroundDarkEnd,
            titleContentColor = PrimaryLight,
            textContentColor = TextMainDark,
            title = { Text("提交算法纠错反馈", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "AI 并不是无所不知的，如果它识别错误，请告知我们正确名称或描述，帮助我们继续迭代模型。",
                        fontSize = 14.sp,
                        color = TextMutedDark
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = feedbackComment,
                        onValueChange = { feedbackComment = it },
                        placeholder = {
                            Text(
                                "例如：这不是黑星病，这是褐斑病...",
                                color = TextMutedDark
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryLight,
                            unfocusedBorderColor = SurfaceBorderLight,
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (feedbackState is Resource.Error) {
                        Text(
                            (feedbackState as Resource.Error).message ?: "提交失败",
                            color = ErrorColor,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else if (feedbackState is Resource.Success) {
                        Text(
                            "反馈已成功送达审计池！",
                            color = SuccessColor,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(1500)
                            showFeedbackDialog = false
                            feedbackComment = ""
                            viewModel.resetSyncState()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (historyId != -1 && feedbackComment.isNotBlank()) {
                            viewModel.submitFeedback(historyId, -1, feedbackComment)
                        }
                    },
                    enabled = feedbackState !is Resource.Loading && feedbackState !is Resource.Success && feedbackComment.isNotBlank()
                ) {
                    if (feedbackState is Resource.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = PrimaryLight
                        )
                    } else {
                        Text("确认提交", color = PrimaryLight)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text("取消", color = TextMutedDark)
                }
            }
        )
    }

    if (showWelcomeDialog) {
        AlertDialog(
            onDismissRequest = {
                showWelcomeDialog = false
                coroutineScope.launch { dsManager.setWelcomeShown() }
            },
            containerColor = BackgroundDarkEnd,
            titleContentColor = PrimaryLight,
            textContentColor = TextMainDark,
            tonalElevation = 8.dp,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = PrimaryLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("欢迎使用农作物检测系统", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "本系统基于 ResNet50 深度残差模型，配合农业知识图谱，为您提供端云协同的植物病害监测服务。",
                        fontSize = 14.sp,
                        color = TextMutedDark,
                        lineHeight = 20.sp
                    )

                    Divider(color = SurfaceBorderLight, thickness = 0.5.dp)

                    Text(
                        "当前系统可精准识别：",
                        fontWeight = FontWeight.SemiBold,
                        color = TextMainDark
                    )
                    Text(
                        "🍎 苹果、🍇 葡萄、🥔 马铃薯、🍓 草莓、🍅 番茄 等数十种植物的健康状态及常见病虫害。",
                        fontSize = 13.sp,
                        color = TextMutedDark
                    )

                    Text("专家权限认证：", fontWeight = FontWeight.SemiBold, color = TextMainDark)

                    Text(
                        "若您是资深农技专家，可联系管理员获取邀请码，在“个人中心”输入验证即可获得专家权限，参与云端处方审计。",
                        fontSize = 13.sp,
                        color = TextMutedDark
                    )
                    Text("注意：tflite是本地模型，torch是云端模型。如果tflite置信度过低，可以尝试使用云端模型进行预测。", color = WarningColor)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWelcomeDialog = false
                        coroutineScope.launch { dsManager.setWelcomeShown() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("开始探索", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
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

fun saveBitmapToCache(context: android.content.Context, bitmap: android.graphics.Bitmap): String {
    val file = java.io.File(context.cacheDir, "temp_img.jpg")
    val out = java.io.FileOutputStream(file)
    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, out)
    out.flush()
    out.close()
    return file.absolutePath
}
