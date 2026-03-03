package edu.geng.plantapp.ui.screens.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import edu.geng.plantapp.data.local.DataStoreManager
import edu.geng.plantapp.data.remote.NetworkClient
import edu.geng.plantapp.repository.AuthRepository
import edu.geng.plantapp.ui.screens.auth.viewmodel.AuthState
import edu.geng.plantapp.ui.screens.auth.viewmodel.AuthViewModel
import edu.geng.plantapp.ui.screens.auth.viewmodel.AuthViewModelFactory
import edu.geng.plantapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit = {},
    onNavigateToRegister: () -> Unit = {}
) {
    val context = LocalContext.current

    val dsManager = remember { DataStoreManager(context) }
    // Instantiate Repositories and ViewModel manually
    val authRepo = remember {
        AuthRepository(NetworkClient.authApi, dsManager)
    }
    val viewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(authRepo)
    )

    // Observe State
    val loginState by viewModel.loginState.collectAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var snackbarColor by remember { mutableStateOf(ErrorColor) }

    // URL Setting State
    var showUrlDialog by remember { mutableStateOf(false) }
    var tempUrl by remember { mutableStateOf(NetworkClient.BASE_URL) }

    // Read stored credentials
    val storedRememberMe by dsManager.rememberMeFlow.collectAsState(initial = false)
    val storedCredentials by dsManager.savedCredentialsFlow.collectAsState(initial = Pair("", ""))

    LaunchedEffect(storedRememberMe) {
        rememberMe = storedRememberMe
    }

    LaunchedEffect(storedCredentials) {
        if (storedCredentials.first.isNotEmpty()) {
            username = storedCredentials.first
            password = storedCredentials.second
        }
    }

    // Read custom Base URL from DataStore
    val customBaseUrl by dsManager.baseUrlFlow.collectAsState(initial = null)

    LaunchedEffect(customBaseUrl) {
        customBaseUrl?.let {
            if (it.isNotBlank()) {
                NetworkClient.updateBaseUrl(it)
                tempUrl = it
            }
        }
    }

    // Snackbar Setup for toasts
    val snackbarHostState = remember { SnackbarHostState() }

    // Side Effects for state changes (Error or Success)
    LaunchedEffect(loginState) {
        when (loginState) {
            is AuthState.Error -> {
                snackbarColor = ErrorColor
                snackbarHostState.showSnackbar(
                    message = (loginState as AuthState.Error).message,
                    duration = SnackbarDuration.Short
                )
                viewModel.resetState()
            }

            is AuthState.Success -> {
                launch {
                    snackbarColor = SuccessColor
                    snackbarHostState.showSnackbar(
                        message = "认证成功，正在切入主系统...",
                        duration = SnackbarDuration.Short
                    )
                }
                delay(500) // 缩短停顿至500ms
                onLoginSuccess()
            }

            else -> {}
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = snackbarColor,
                    contentColor = Color.White
                )
            }
        },
        containerColor = Color.Transparent
    ) { paddingVals ->
        // Floating Animations
        val infiniteTransition = rememberInfiniteTransition(label = "floating")
        val offsetY1 by infiniteTransition.animateFloat(
            initialValue = -50f, targetValue = 50f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "circle1"
        )
        val offsetY2 by infiniteTransition.animateFloat(
            initialValue = 50f, targetValue = -50f,
            animationSpec = infiniteRepeatable(
                animation = tween(5000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "circle2"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(BackgroundDarkStart, BackgroundDarkEnd),
                        start = Offset(0f, 0f),
                        end = Offset(1000f, 2000f)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // -- Settings Icon (Wrench) in Top Right --
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 40.dp, end = 16.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                IconButton(onClick = { showUrlDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "服务器设置",
                        tint = PrimaryLight.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            // Decorative Blurred Circles (Glassmorphism backdrop)
            Box(
                modifier = Modifier
                    .offset(x = (-80).dp, y = offsetY1.dp - 200.dp)
                    .size(250.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x2AFFFFFF), Color.Transparent)
                        ),
                        shape = CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .offset(x = 100.dp, y = offsetY2.dp + 150.dp)
                    .size(250.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(PrimaryLight.copy(alpha = 0.25f), Color.Transparent)
                        ),
                        shape = CircleShape
                    )
            )

            // Login Glass Card
            Box(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .background(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = SurfaceBorderLight,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(32.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Header
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "PlantVillage",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMainDark
                        )
                        Text(
                            text = "绿洲生态鉴权终端",
                            fontSize = 14.sp,
                            color = TextMutedDark,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Username Input
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("账号", color = TextMutedDark) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryLight,
                            unfocusedBorderColor = SurfaceBorderLight,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = PrimaryLight
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Password Input
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码", color = TextMutedDark) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryLight,
                            unfocusedBorderColor = SurfaceBorderLight,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = PrimaryLight
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Remember Me Checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = PrimaryLight,
                                uncheckedColor = SurfaceBorderLight,
                                checkmarkColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("记住登录凭证", color = TextMutedDark, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Submit Button
                    Button(
                        onClick = {
                            if (username.isEmpty() || password.isEmpty()) {
                                coroutineScope.launch {
                                    snackbarColor = WarningColor
                                    snackbarHostState.showSnackbar(
                                        message = "请输入账号和密码",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            } else {
                                viewModel.login(username, password, rememberMe)
                            }
                        },
                        enabled = loginState !is AuthState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryGreen,
                            disabledContainerColor = PrimaryGreen.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (loginState is AuthState.Loading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Login",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }

                    // Register TextButton
                    TextButton(onClick = onNavigateToRegister) {
                        Text("没有账号？去注册新账号 ->", color = PrimaryLight)
                    }
                }
            }
        }
    }

    // --- BASE_URL Setting Dialog ---
    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            containerColor = BackgroundDarkEnd,
            titleContentColor = PrimaryLight,
            textContentColor = TextMainDark,
            title = { Text("API 服务器设置", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "由于 GitHub 转发地址经常变动，请在此输入最新的 API 基准地址：",
                        fontSize = 13.sp,
                        color = TextMutedDark
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        label = { Text("Base URL", color = TextMutedDark) },
                        placeholder = { Text("https://xxx.devtunnels.ms/api/v1/") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryLight,
                            unfocusedBorderColor = SurfaceBorderLight,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text(
                        "提示：如果能够正常访问，则不要修改。如果要修改则路径需以 / 结尾",
                        fontSize = 11.sp,
                        color = WarningColor.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempUrl.isNotBlank()) {
                            coroutineScope.launch {
                                dsManager.saveBaseUrl(tempUrl)
                                NetworkClient.updateBaseUrl(tempUrl)
                                showUrlDialog = false
                                snackbarColor = SuccessColor
                                snackbarHostState.showSnackbar("服务器地址已更新")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryLight)
                ) {
                    Text("保存并路由")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("取消", color = TextMutedDark)
                }
            }
        )
    }
}
