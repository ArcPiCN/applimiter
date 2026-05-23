package com.example.applimiter

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.applimiter.ui.screen.AddRuleNavBus
import com.example.applimiter.ui.screen.AddRuleScreen
import com.example.applimiter.ui.screen.AppListScreen
import com.example.applimiter.ui.screen.AppRulesScreen
import com.example.applimiter.ui.screen.MainScaffold
import com.example.applimiter.ui.theme.AppLimiterTheme
import com.example.applimiter.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = Color.Transparent.toArgb(),
                darkScrim = Color.Transparent.toArgb()
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = Color.Transparent.toArgb(),
                darkScrim = Color.Transparent.toArgb()
            )
        )
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            AppLimiterTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    val vm: MainViewModel = viewModel()

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            MainScaffold(
                viewModel = vm,
                onAddRuleClick = {
                    // 入口：选 App → 进该应用的规则页（在 app_list 接收回调里完成）
                    nav.navigate("app_list?forApp=true")
                },
                onEditRuleClick = { id -> nav.navigate("edit_rule/$id") },
                onOpenAppRules = { pkg -> nav.navigate("app_rules/$pkg") }
            )
        }
        composable(
            route = "app_rules/{packageName}",
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { entry ->
            val pkg = entry.arguments?.getString("packageName") ?: return@composable
            AppRulesScreen(
                viewModel = vm,
                packageName = pkg,
                onBack = { nav.popBackStack() },
                onEditRule = { id -> nav.navigate("edit_rule/$id") },
                onAddRule = {
                    AddRuleNavBus.lockedPackageName = pkg
                    nav.navigate("add_rule")
                }
            )
        }
        composable("add_rule") {
            AddRuleScreen(
                viewModel = vm,
                onPickApp = { nav.navigate("app_list") },
                onDone = { nav.popBackStack() },
                onCancel = { nav.popBackStack() }
            )
        }
        composable(
            route = "edit_rule/{ruleId}",
            arguments = listOf(navArgument("ruleId") { type = NavType.LongType })
        ) { backStackEntry ->
            val ruleId = backStackEntry.arguments?.getLong("ruleId")
            AddRuleScreen(
                viewModel = vm,
                onPickApp = { nav.navigate("app_list") },
                onDone = { nav.popBackStack() },
                onCancel = { nav.popBackStack() },
                editingRuleId = ruleId
            )
        }
        composable(
            route = "app_list?forApp={forApp}",
            arguments = listOf(navArgument("forApp") {
                type = NavType.StringType
                defaultValue = "false"
            })
        ) { entry ->
            val forApp = entry.arguments?.getString("forApp") == "true"
            AppListScreen(
                onAppPicked = { pkg, name ->
                    if (forApp) {
                        // 进入该应用的规则页（用 popUpTo 让回退栈干净）
                        nav.navigate("app_rules/$pkg") {
                            popUpTo("home")
                        }
                    } else {
                        AddRuleNavBus.pickedApp = pkg to name
                        nav.popBackStack()
                    }
                },
                onCancel = { nav.popBackStack() }
            )
        }
    }
}
