package com.docsphere.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import com.docsphere.ui.chat.ChatScreen
import com.docsphere.ui.drive.DriveScreen
import com.docsphere.ui.files.FilesScreen
import com.docsphere.ui.home.HomeScreen
import com.docsphere.ui.settings.SettingsScreen
import com.docsphere.ui.theme.*
import com.docsphere.ui.viewer.FileViewerRoute

data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector? = null,
    val iconDrawable: Int? = null
)

private val navItems = listOf(
    NavItem(Screen.Home.route, "Browse", icon = Icons.Default.Folder),
    NavItem(Screen.Files.route, "Files", icon = Icons.Default.Description),
    NavItem(Screen.Chat.withSession(), "AI Chat", iconDrawable = com.docsphere.R.drawable.docsphere_ai_logo),
    NavItem(Screen.Drive.route, "Drive", iconDrawable = com.docsphere.R.drawable.ic_nav_drive),
    NavItem(Screen.Settings.route, "Settings", icon = Icons.Default.Settings),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocSphereNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isImeVisible = WindowInsets.isImeVisible
    // Hide bottom navigation bar when IME is visible or in full-screen File Viewer
    val hideBottomBar = isImeVisible || (currentRoute?.startsWith(Screen.FileViewer.ROUTE_BASE) == true)

    Scaffold(
        containerColor = GoogleBackgroundDark,
        bottomBar = {
            AnimatedVisibility(
                visible = !hideBottomBar,
                enter = fadeIn(tween(150)) + expandVertically(tween(150)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
            ) {
                DocSphereBottomBar(
                    navItems = navItems,
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(animationSpec = tween(180)) +
                slideInHorizontally(animationSpec = tween(180)) { it / 14 }
            },
            exitTransition = { fadeOut(animationSpec = tween(120)) },
            popEnterTransition = { fadeIn(animationSpec = tween(180)) },
            popExitTransition = {
                fadeOut(animationSpec = tween(120)) +
                slideOutHorizontally(animationSpec = tween(180)) { it / 14 }
            }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToFiles = { category ->
                        navController.navigate(Screen.Files.route)
                    },
                    onNavigateToChat = { navController.navigate(Screen.Chat.withSession()) },
                    onNavigateToDrive = { navController.navigate(Screen.Drive.route) },
                    onOpenFile = { doc ->
                        navController.navigate(Screen.FileViewer.withDoc(doc.id))
                    }
                )
            }
            composable(Screen.Files.route) {
                FilesScreen(
                    onOpenFile = { doc ->
                        navController.navigate(Screen.FileViewer.withDoc(doc.id))
                    },
                    onAskAi = { doc ->
                        navController.navigate(Screen.Chat.withSession())
                    }
                )
            }
            composable(Screen.Chat.route) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val activity = context as? androidx.activity.ComponentActivity
                val chatViewModel: com.docsphere.ui.chat.ChatViewModel =
                    if (activity != null) androidx.hilt.navigation.compose.hiltViewModel(activity)
                    else androidx.hilt.navigation.compose.hiltViewModel()
                ChatScreen(
                    viewModel = chatViewModel,
                    onOpenFile = { docId ->
                        // Use encoded string so we don't break navigation if the uri has slashes
                        navController.navigate(Screen.FileViewer.withDoc(docId))
                    }
                )
            }
            composable(Screen.Drive.route) {
                DriveScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
            composable(
                route = Screen.FileViewer.route,
                arguments = listOf(
                    navArgument("docId") { type = NavType.StringType }
                )
            ) {
                FileViewerRoute(
                    onBack = { navController.popBackStack() },
                    onAskAi = { doc ->
                        navController.navigate(Screen.Chat.withSession())
                    }
                )
            }
        }
    }
}

@Composable
private fun DocSphereBottomBar(
    navItems: List<NavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    Surface(
        color = GoogleSurfaceDark,
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEachIndexed { index, item ->
                val isChatCenter = index == 2 || item.route == Screen.Chat.withSession()
                val isSelected = when {
                    item.route == Screen.Chat.withSession() ->
                        currentRoute?.startsWith(Screen.Chat.ROUTE_BASE) == true
                    else -> currentRoute == item.route
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(onClick = { onNavigate(item.route) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier.size(30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.iconDrawable != null) {
                            Image(
                                painter = painterResource(id = item.iconDrawable),
                                contentDescription = item.label,
                                modifier = Modifier.size(if (isChatCenter) 28.dp else 22.dp)
                            )
                        } else if (item.icon != null) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (isSelected) GoogleBlue else GoogleTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) GoogleBlue else GoogleTextSecondary,
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}
