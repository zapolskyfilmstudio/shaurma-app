package com.shaurma.mvp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shaurma.mvp.ui.AppNavigation
import com.shaurma.mvp.ui.AppViewModel
import com.shaurma.mvp.ui.BlockedScreen
import com.shaurma.mvp.ui.StartupScreen
import com.shaurma.mvp.ui.theme.ShaurmaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShaurmaTheme {
                val viewModel: AppViewModel = hiltViewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                when {
                    state.isLoading -> StartupScreen(message = "Загружаем меню...")
                    state.isBlocked -> BlockedScreen()
                    else -> AppNavigation()
                }
            }
        }
    }
}
