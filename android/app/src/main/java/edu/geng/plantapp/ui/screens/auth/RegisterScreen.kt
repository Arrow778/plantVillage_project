package edu.geng.plantapp.ui.screens.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import edu.geng.plantapp.data.local.DataStoreManager
import edu.geng.plantapp.data.remote.NetworkClient
import edu.geng.plantapp.repository.AuthRepository
import edu.geng.plantapp.ui.screens.auth.viewmodel.RegisterState
import edu.geng.plantapp.ui.screens.auth.viewmodel.RegisterViewModel
import edu.geng.plantapp.ui.screens.auth.viewmodel.RegisterViewModelFactory
import edu.geng.plantapp.ui.theme.*

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit = {},
    onNavigateBackToLogin: () -> Unit = {}
) {
    val context = LocalContext.current

    val authRepo = remember {
        AuthRepository(NetworkClient.authApi, DataStoreManager(context))
    }
    val viewModel: RegisterViewModel = viewModel(
        factory = RegisterViewModelFactory(authRepo)
    )

    val registerState by viewModel.registerState.collectAsState()

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(registerState) {
        when (registerState) {
            is RegisterState.Error -> {
                snackbarHostState.showSnackbar(
                    message = (registerState as RegisterState.Error).message,
                    duration = SnackbarDuration.Short
                )
                viewModel.resetState()
            }
            is RegisterState.Success -> {
                launch {
                    snackbarHostState.showSnackbar(
                        message = "注册成功，即将返回登录...",
                        duration = SnackbarDuration.Short
                    )
                }
                delay(800)
                onRegisterSuccess()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                val isSuccess = registerState is RegisterState.Success
                Snackbar(
                    snackbarData = data,
                    containerColor = if (isSuccess) SuccessColor else ErrorColor,
                    contentColor = Color.White
                )
            }
        },
        containerColor = Color.Transparent
    ) { paddingVals ->
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
                )
                .padding(paddingVals),
            contentAlignment = Alignment.Center
        ) {
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
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "账户注册",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMainDark
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("账号名称", color = TextMutedDark) },
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

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("电子邮箱（可以留空）", color = TextMutedDark) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
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

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("输入密码", color = TextMutedDark) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
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

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("确认密码", color = TextMutedDark) },
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

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (username.isEmpty() || password.isEmpty()) {
                                // show error or ignore
                                return@Button
                            }
                            if (password != confirmPassword) {
                                // Just a simple local check
                                return@Button
                            }
                            viewModel.register(username, email, password)
                        },
                        enabled = registerState !is RegisterState.Loading && password == confirmPassword && password.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryGreen,
                            disabledContainerColor = PrimaryGreen.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (registerState is RegisterState.Loading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "立 即 注 册",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                    
                    TextButton(onClick = onNavigateBackToLogin) {
                        Text("<- 已有账号？返回登录", color = PrimaryLight)
                    }
                }
            }
        }
    }
}
