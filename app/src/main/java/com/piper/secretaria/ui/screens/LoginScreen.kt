package com.piper.secretaria.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piper.secretaria.ui.MainViewModel
import com.piper.secretaria.ui.components.FluteMascot
import com.piper.secretaria.ui.components.GradientButton
import com.piper.secretaria.ui.theme.PiperColors

/** Login no painel web. Depois de entrar uma vez a sessão fica salva no aparelho. */
@Composable
fun LoginScreen(vm: MainViewModel, onBack: (() -> Unit)?) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val error by vm.loginState.collectAsStateWithLifecycle()
    val busy by vm.loggingIn.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf(settings.panelUrl) }
    var user by rememberSaveable { mutableStateOf(settings.accountUser) }
    var password by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(settings.loggedIn) { if (settings.loggedIn && onBack != null) onBack() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .background(PiperColors.headerGradient)
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White)
                }
            }
            FluteMascot(Modifier.size(170.dp), talking = busy)
            Text("Piper", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Text(
                "Entre com sua conta do painel para eu ficar mais inteligente (ChatGPT) e sincronizar com a web.",
                color = Color(0xFFDDD6FE), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
            )
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        url, { url = it }, label = { Text("Endereço do painel") },
                        placeholder = { Text("https://seu-painel.vercel.app") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    OutlinedTextField(
                        user, { user = it }, label = { Text("Usuário") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    )
                    OutlinedTextField(
                        password, { password = it }, label = { Text("Senha") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(4.dp))
                    if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    else GradientButton("Entrar", icon = null, modifier = Modifier.fillMaxWidth()) {
                        if (url.isNotBlank() && user.isNotBlank() && password.isNotBlank()) vm.login(url, user, password)
                    }
                }
            }
            if (onBack == null) {
                TextButton(onClick = { vm.useOffline() }, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Usar sem conta (modo offline)", color = Color.White)
                }
            }
        }
    }
}
