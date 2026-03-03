package edu.geng.plantapp.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Add
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
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.geng.plantapp.data.local.DataStoreManager
import edu.geng.plantapp.data.remote.NetworkClient
import edu.geng.plantapp.repository.AuthRepository
import edu.geng.plantapp.repository.Resource
import edu.geng.plantapp.ui.screens.home.GlassCard
import edu.geng.plantapp.ui.screens.profile.viewmodel.ProfileState
import edu.geng.plantapp.ui.screens.profile.viewmodel.ProfileViewModel
import edu.geng.plantapp.ui.screens.profile.viewmodel.ProfileViewModelFactory
import edu.geng.plantapp.ui.screens.profile.viewmodel.ExpertViewModel
import edu.geng.plantapp.ui.screens.profile.viewmodel.ExpertViewModelFactory
import edu.geng.plantapp.ui.screens.profile.viewmodel.ExpertStatsState
import edu.geng.plantapp.ui.screens.profile.viewmodel.ContributionListState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import android.net.Uri
import coil.compose.AsyncImage
import edu.geng.plantapp.repository.ExpertRepository
import edu.geng.plantapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onLogoutSuccess: () -> Unit
) {
    val context = LocalContext.current
    val dsManager = remember { DataStoreManager(context) }
    val authRepo = remember { AuthRepository(NetworkClient.authApi, dsManager) }
    val viewModel: ProfileViewModel = viewModel(factory = ProfileViewModelFactory(authRepo))

    val uiState by viewModel.uiState.collectAsState()
    val logoutState by viewModel.logoutState.collectAsState()
    val verifyState by viewModel.verifyState.collectAsState()

    val expertRepo = remember { ExpertRepository(NetworkClient.expertApi, dsManager, context) }
    val expertViewModel: ExpertViewModel = viewModel(factory = ExpertViewModelFactory(expertRepo))
    val statsState by expertViewModel.statsState.collectAsState()
    val contributeState by expertViewModel.contributeState.collectAsState()
    val listState by expertViewModel.listState.collectAsState()

    var showContributeDialog by remember { mutableStateOf(false) }
    var contributeDiseaseName by remember { mutableStateOf("") }
    var contributeTreatmentPlan by remember { mutableStateOf("") }
    var contributeImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val multiplePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 3)
    ) { uris -> 
        contributeImageUris = uris
    }

    var showVerifyDialog by remember { mutableStateOf(false) }
    var expertCodeInput by remember { mutableStateOf("") }
    
    var showRejectReasonDialog by remember { mutableStateOf<edu.geng.plantapp.data.remote.ContributionItem?>(null) }
    var resubmitDiseaseName by remember { mutableStateOf("") }
    var resubmitTreatmentPlan by remember { mutableStateOf("") }
    var resubmitImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }

    val resubmitPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 3)
    ) { uris -> 
        resubmitImageUris = uris
    }

    LaunchedEffect(Unit) {
        viewModel.fetchProfile()
    }

    LaunchedEffect(uiState) {
        if (uiState is ProfileState.Success && (uiState as ProfileState.Success).profile.is_expert == true) {
            expertViewModel.fetchStats()
            expertViewModel.fetchContributions(1)
        }
    }

    LaunchedEffect(logoutState) {
        if (logoutState is Resource.Success) {
            onLogoutSuccess()
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
                    title = { Text("用户资料库", color = TextMainDark, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = PrimaryLight)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                when (uiState) {
                    is ProfileState.Loading -> {
                        CircularProgressIndicator(color = PrimaryLight)
                    }
                    is ProfileState.Error -> {
                        Text(
                            "无法加载用户信息",
                            color = ErrorColor,
                            fontWeight = FontWeight.Bold
                        )
                        Button(onClick = { viewModel.fetchProfile() }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight)) {
                            Text("重试", color = Color.White)
                        }
                    }
                    is ProfileState.Success -> {
                        val profile = (uiState as ProfileState.Success).profile

                        // 头像及用户名区
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = "User Avatar",
                            modifier = Modifier.size(100.dp),
                            tint = PrimaryLight
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = profile.username ?: "Unknown",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMainDark
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = if (profile.is_expert == true) PrimaryGreen else SurfaceDark,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (profile.is_expert == true) Color.Transparent else SurfaceBorderLight)
                            ) {
                                Text(
                                    text = if (profile.is_expert == true) "专家" else "普通",
                                    color = if (profile.is_expert == true) Color.White else TextMutedDark,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // 玻璃卡片信息栏
                        GlassCard {
                            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Info, contentDescription = null, tint = SuccessColor, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("账户权限身份", fontSize = 14.sp, color = TextMutedDark)
                                        val identity = if (profile.is_expert == true) "权威农业护卫专家" else "普通用户"
                                        Text(identity, fontSize = 16.sp, color = TextMainDark, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Divider(color = SurfaceBorderLight)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Info, contentDescription = null, tint = WarningColor, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("生涯存证总数", fontSize = 14.sp, color = TextMutedDark)
                                        Text("${profile.total_recognitions ?: 0} 次识别历史", fontSize = 16.sp, color = TextMainDark, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        if (profile.is_expert == true) {
                            Spacer(modifier = Modifier.height(8.dp))
                            GlassCard {
                                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text("专家卓越贡献卡", fontSize = 16.sp, color = TextMainDark, fontWeight = FontWeight.Bold)
                                    HorizontalDivider(color = SurfaceBorderLight)
                                    if (statsState is ExpertStatsState.Loading) {
                                        CircularProgressIndicator(color = PrimaryLight)
                                    } else if (statsState is ExpertStatsState.Success) {
                                        val stats = (statsState as ExpertStatsState.Success).stats
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(stats.total.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextMainDark)
                                                Text("总申请", fontSize = 12.sp, color = TextMutedDark)
                                            }
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(stats.pending.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = WarningColor)
                                                Text("待审核", fontSize = 12.sp, color = TextMutedDark)
                                            }
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(stats.accepted.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = SuccessColor)
                                                Text("已采纳", fontSize = 12.sp, color = TextMutedDark)
                                            }
                                        }
                                    }
                                    Button(
                                        onClick = { showContributeDialog = true },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("贡献植物病理知识", color = Color.White, fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("近期贡献记录", fontSize = 14.sp, color = TextMainDark, fontWeight = FontWeight.Bold)
                                    
                                    if (listState is ContributionListState.Loading) {
                                        CircularProgressIndicator(color = PrimaryLight, modifier = Modifier.align(Alignment.CenterHorizontally))
                                    } else if (listState is ContributionListState.Success) {
                                        val listResp = (listState as ContributionListState.Success).listResp
                                        if (listResp.items.isEmpty()) {
                                            Text("暂无贡献记录", color = TextMutedDark, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                                        } else {
                                            Column {
                                                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("病理", modifier = Modifier.weight(1f), fontSize = 12.sp, color = TextMutedDark)
                                                    Text("时间", modifier = Modifier.weight(1.5f), fontSize = 12.sp, color = TextMutedDark)
                                                    Text("状态", modifier = Modifier.weight(0.5f), fontSize = 12.sp, color = TextMutedDark, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                                }
                                                listResp.items.forEach { item ->
                                                    val statusColor = when(item.status) {
                                                        "待审核" -> WarningColor
                                                        "已采纳" -> SuccessColor
                                                        "已驳回" -> ErrorColor
                                                        else -> TextMutedDark
                                                    }
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(item.disease_name ?: "-", modifier = Modifier.weight(1f), fontSize = 14.sp, color = TextMainDark, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                        Text(item.created_at?.split(" ")?.firstOrNull() ?: "-", modifier = Modifier.weight(1.5f), fontSize = 12.sp, color = TextMutedDark)
                                                        TextButton(
                                                            onClick = { 
                                                                if (item.status == "已驳回") {
                                                                    showRejectReasonDialog = item
                                                                    resubmitDiseaseName = item.disease_name ?: ""
                                                                    resubmitTreatmentPlan = item.treatment_plan ?: ""
                                                                    resubmitImageUris = emptyList()
                                                                    expertViewModel.resetContributeState()
                                                                }
                                                            },
                                                            modifier = Modifier.weight(0.5f).height(24.dp),
                                                            contentPadding = PaddingValues(0.dp)
                                                        ) {
                                                            Text(item.status ?: "未知", fontSize = 12.sp, color = statusColor, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                    HorizontalDivider(color = SurfaceBorderLight, thickness = 0.5.dp)
                                                }
                                                
                                                // Pagination controls
                                                if (listResp.pages > 1) {
                                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                        TextButton(
                                                            onClick = { expertViewModel.fetchContributions(listResp.current_page - 1) },
                                                            enabled = listResp.current_page > 1
                                                        ) {
                                                            Text("上一页", color = if (listResp.current_page > 1) PrimaryLight else TextMutedDark, fontSize = 12.sp)
                                                        }
                                                        Text("${listResp.current_page} / ${listResp.pages}", color = TextMutedDark, fontSize = 12.sp)
                                                        TextButton(
                                                            onClick = { expertViewModel.fetchContributions(listResp.current_page + 1) },
                                                            enabled = listResp.current_page < listResp.pages
                                                        ) {
                                                            Text("下一页", color = if (listResp.current_page < listResp.pages) PrimaryLight else TextMutedDark, fontSize = 12.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (profile.is_expert != true) {
                            Button(
                                onClick = { showVerifyDialog = true },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("激活专家特权", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f)) // 占位把按钮推到底部

                        Button(
                            onClick = { viewModel.logout() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorColor),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        ) {
                            if (logoutState is Resource.Loading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Icon(Icons.Filled.ExitToApp, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("断开端云连接 (退出登录)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    if (showVerifyDialog) {
        val profile = (uiState as? ProfileState.Success)?.profile
        AlertDialog(
            onDismissRequest = { 
                showVerifyDialog = false 
                viewModel.resetVerifyState()
            },
            containerColor = BackgroundDarkEnd,
            titleContentColor = PrimaryLight,
            textContentColor = TextMainDark,
            title = { Text("专家身份认证", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("请输入管理员分配的 8 位专家邀请码:", fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))
                    OutlinedTextField(
                        value = expertCodeInput,
                        onValueChange = { expertCodeInput = it },
                        placeholder = { Text("例如：a1b2c3d4", color = TextMutedDark) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryLight,
                            unfocusedBorderColor = SurfaceBorderLight,
                        ),
                        singleLine = true,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                    
                    if (verifyState is Resource.Loading) {
                        CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp).align(Alignment.CenterHorizontally), color = PrimaryLight)
                    } else if (verifyState is Resource.Error) {
                        Text(
                            text = (verifyState as Resource.Error).message ?: "认证失败",
                            color = ErrorColor,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else if (verifyState is Resource.Success) {
                        Text(
                            text = "认证成功！资料库已刷新",
                            color = SuccessColor,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(1000)
                            showVerifyDialog = false
                            viewModel.resetVerifyState()
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "如果您想申请专家权限，请联系作者，邮箱: luoruiGeng@163.com",
                        color = TextMutedDark,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (profile != null && expertCodeInput.isNotBlank()) {
                            viewModel.verifyExpert(profile.username ?: "", expertCodeInput)
                        }
                    },
                    enabled = verifyState !is Resource.Loading && verifyState !is Resource.Success && expertCodeInput.isNotBlank()
                ) {
                    Text("认证", color = PrimaryLight)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showVerifyDialog = false 
                    viewModel.resetVerifyState()
                }) {
                    Text("取消", color = TextMutedDark)
                }
            }
        )
    }

    if (showContributeDialog) {
        AlertDialog(
            onDismissRequest = { 
                showContributeDialog = false 
                expertViewModel.resetContributeState()
            },
            containerColor = BackgroundDarkEnd,
            titleContentColor = PrimaryLight,
            textContentColor = TextMainDark,
            title = { Text("贡献病理知识", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = contributeDiseaseName,
                        onValueChange = { contributeDiseaseName = it },
                        label = { Text("病理名称 (例如：苹果黑星病)", color = TextMutedDark) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryLight,
                            unfocusedBorderColor = SurfaceBorderLight,
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = contributeTreatmentPlan,
                        onValueChange = { contributeTreatmentPlan = it },
                        label = { Text("描述及防治方法", color = TextMutedDark) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryLight,
                            unfocusedBorderColor = SurfaceBorderLight,
                        ),
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("上传凭证病理图片（最多3张可选）", color = TextMutedDark, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        contributeImageUris.forEach { uri ->
                            Box(modifier = Modifier.size(60.dp)) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                        .background(SurfaceDark, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { contributeImageUris = contributeImageUris.filter { it != uri } },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(20.dp)
                                        .padding(2.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "删除该图片",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                        if (contributeImageUris.size < 3) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(SurfaceDark, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .clickable {
                                        multiplePhotoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "添加图片", tint = PrimaryLight)
                            }
                        }
                    }

                    if (contributeState is Resource.Loading) {
                        CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp).align(Alignment.CenterHorizontally), color = PrimaryLight)
                    } else if (contributeState is Resource.Error) {
                        Text(
                            text = (contributeState as Resource.Error).message ?: "提交失败",
                            color = ErrorColor,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else if (contributeState is Resource.Success) {
                        Text(
                            text = "提交成功！感谢您的贡献，正在审核中",
                            color = SuccessColor,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(1000)
                            showContributeDialog = false
                            contributeDiseaseName = ""
                            contributeTreatmentPlan = ""
                            contributeImageUris = emptyList()
                            expertViewModel.resetContributeState()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (contributeDiseaseName.isNotBlank() && contributeTreatmentPlan.isNotBlank()) {
                            expertViewModel.submitContribution(contributeDiseaseName, contributeTreatmentPlan, contributeImageUris)
                        }
                    },
                    enabled = contributeState !is Resource.Loading && contributeState !is Resource.Success && contributeDiseaseName.isNotBlank() && contributeTreatmentPlan.isNotBlank()
                ) {
                    Text("提交", color = PrimaryLight)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showContributeDialog = false 
                    expertViewModel.resetContributeState()
                }) {
                    Text("取消", color = TextMutedDark)
                }
            }
        )
    }

    if (showRejectReasonDialog != null) {
        val item = showRejectReasonDialog!!
        AlertDialog(
            onDismissRequest = { 
                showRejectReasonDialog = null 
                expertViewModel.resetContributeState()
            },
            containerColor = BackgroundDarkEnd,
            titleContentColor = ErrorColor,
            textContentColor = TextMainDark,
            title = { Text("驳回详情与重新提交", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("驳回原因：", color = ErrorColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(item.reject_reason ?: "无", color = TextMainDark, fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))
                    
                    if (item.image_url != null && item.image_url != "default.jpg") {
                        val imageUrls = item.image_url.split(",")
                        Text("原图存档（点击放大查看）：", color = TextMutedDark, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            imageUrls.forEach { img ->
                                val imgUrl = edu.geng.plantapp.data.remote.NetworkClient.BASE_URL.replace("api/v1/", "") + "uploads/contributions/" + img
                                AsyncImage(
                                    model = imgUrl,
                                    contentDescription = "Original Upload",
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                        .clickable { fullScreenImageUrl = imgUrl },
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = resubmitDiseaseName,
                        onValueChange = { resubmitDiseaseName = it },
                        label = { Text("病理名称", color = TextMutedDark) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryLight,
                            unfocusedBorderColor = SurfaceBorderLight,
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = resubmitTreatmentPlan,
                        onValueChange = { resubmitTreatmentPlan = it },
                        label = { Text("防治方法", color = TextMutedDark) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryLight,
                            unfocusedBorderColor = SurfaceBorderLight,
                        ),
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("重新上传凭证图片 (将覆盖原图，可选)", color = TextMutedDark, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        resubmitImageUris.forEach { uri ->
                            Box(modifier = Modifier.size(60.dp)) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                        .background(SurfaceDark, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { resubmitImageUris = resubmitImageUris.filter { it != uri } },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(20.dp)
                                        .padding(2.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.CircleShape)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "删除该图片", tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                        if (resubmitImageUris.size < 3) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(SurfaceDark, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .clickable {
                                        resubmitPhotoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "添加图片", tint = PrimaryLight)
                            }
                        }
                    }

                    if (contributeState is Resource.Loading) {
                        CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp).align(Alignment.CenterHorizontally), color = PrimaryLight)
                    } else if (contributeState is Resource.Error) {
                        Text(
                            text = (contributeState as Resource.Error).message ?: "提交失败",
                            color = ErrorColor,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else if (contributeState is Resource.Success) {
                        Text(
                            text = "已重新提交，等待审核...",
                            color = SuccessColor,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(1000)
                            showRejectReasonDialog = null
                            expertViewModel.resetContributeState()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (resubmitDiseaseName.isNotBlank() && resubmitTreatmentPlan.isNotBlank()) {
                            expertViewModel.resubmitContribution(item.id, resubmitDiseaseName, resubmitTreatmentPlan, resubmitImageUris)
                        }
                    },
                    enabled = contributeState !is Resource.Loading && contributeState !is Resource.Success && resubmitDiseaseName.isNotBlank() && resubmitTreatmentPlan.isNotBlank()
                ) {
                    Text("修改并重新提交", color = PrimaryLight, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showRejectReasonDialog = null 
                    expertViewModel.resetContributeState()
                }) {
                    Text("取消放弃", color = TextMutedDark)
                }
            }
        )
    }

    // Full-screen image viewer overlay — wrapped in Dialog so it renders ABOVE AlertDialog
    if (fullScreenImageUrl != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { fullScreenImageUrl = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            },
                            onTap = {
                                fullScreenImageUrl = null
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = fullScreenImageUrl,
                    contentDescription = "放大查看",
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.5f, 5f)
                                offset = Offset(
                                    x = offset.x + pan.x,
                                    y = offset.y + pan.y
                                )
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )

                // Close button
                IconButton(
                    onClick = { fullScreenImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
                }

                // Hint text
                Text(
                    text = "双击放大/缩小 · 单击关闭",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                )
            }
        }
    }
}
