package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.lifecycleScope
import com.example.ai.configureAppCheck
import com.example.data.AppDatabase
import com.example.data.LocalMediaStorage
import com.example.data.StudioRepository
import com.example.security.FirebaseAuthManager
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.ProjectHubScreen
import com.example.ui.theme.LumaCutTheme
import com.example.viewmodel.EditorViewModel
import com.example.viewmodel.ProjectsViewModel

const val ROUTE_PROJECT_HUB = "project_hub"
const val ROUTE_EDITOR_PATTERN = "editor/{projectId}"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        FirebaseAuthManager.getInstance(this).attemptSilentAutoSignIn(this, lifecycleScope)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            LumaCutTheme {
                LumaCutApp()
            }
        }
    }
}

@Composable
private fun LumaCutApp() {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) {
        val db = AppDatabase.getInstance(context)
        StudioRepository(
            projectDao = db.projectDao(),
            customFontDao = db.customFontDao(),
            mediaStorage = LocalMediaStorage(context)
        )
    }

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ROUTE_PROJECT_HUB
    ) {
        composable(ROUTE_PROJECT_HUB) {
            val projectsViewModel: ProjectsViewModel = viewModel(
                factory = ProjectsViewModel.factory(repository)
            )
            ProjectHubScreen(
                viewModel = projectsViewModel,
                onOpenProject = { projectId ->
                    navController.navigate("editor/$projectId")
                }
            )
        }

        composable(
            route = ROUTE_EDITOR_PATTERN,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            val editorViewModel: EditorViewModel = viewModel(
                key = "editor_$projectId",
                factory = EditorViewModel.factory(context, projectId, repository)
            )
            EditorScreen(
                viewModel = editorViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
