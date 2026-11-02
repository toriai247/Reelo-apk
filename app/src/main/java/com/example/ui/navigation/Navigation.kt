package com.example.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.di.AppContainer
import com.example.ui.screens.details.SeriesDetailsScreen
import com.example.ui.screens.details.SeriesDetailsViewModel
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.profile.ProfileViewModel
import com.example.ui.screens.search.SearchScreen
import com.example.ui.screens.search.SearchViewModel
import com.example.ui.screens.watch.WatchScreen
import com.example.ui.screens.watch.WatchViewModel
import com.example.ui.theme.ReeloBlack
import com.example.ui.theme.ReeloYellow
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
    object Watchlist : Screen("watchlist")
    object Profile : Screen("profile")
    object Details : Screen("details/{slug}") {
        fun createRoute(slug: String) = "details/$slug"
    }
    object Watch : Screen("watch/{seriesId}?episodeId={episodeId}") {
        fun createRoute(seriesId: String, episodeId: String? = null): String {
            return if (episodeId != null) "watch/$seriesId?episodeId=$episodeId" else "watch/$seriesId"
        }
    }
}

data class BottomNavItem(
    val title: String,
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem("Home", Screen.Home.route, Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem("Search", Screen.Search.route, Icons.Filled.Search, Icons.Outlined.Search),
    BottomNavItem("Watchlist", Screen.Watchlist.route, Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
    BottomNavItem("Profile", Screen.Profile.route, Icons.Filled.Person, Icons.Outlined.Person)
)

@Composable
fun ReeloNavGraph(
    navController: NavHostController,
    container: AppContainer,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isTopLevelDestination = bottomNavItems.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (isTopLevelDestination) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title
                                )
                            },
                            label = { Text(item.title) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = ReeloYellow,
                                selectedIconColor = ReeloBlack,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.testTag("nav_${item.title.lowercase()}")
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(if (isTopLevelDestination) innerPadding else androidx.compose.foundation.layout.PaddingValues(0.dp))
        ) {
            // Home Screen
            composable(Screen.Home.route) {
                val viewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = HomeViewModel.provideFactory(container.homeRepository, container.settingsRepository)
                )
                HomeScreen(
                    viewModel = viewModel,
                    onSeriesClick = { series ->
                        navController.navigate(Screen.Details.createRoute(series.slug))
                    },
                    onWatchClick = { seriesId, episodeId ->
                        navController.navigate(Screen.Watch.createRoute(seriesId, episodeId))
                    },
                    onSearchClick = {
                        navController.navigate(Screen.Search.route)
                    }
                )
            }

            // Search Screen
            composable(Screen.Search.route) {
                val viewModel: SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = SearchViewModel.provideFactory(container.seriesRepository, container.homeRepository, container.settingsRepository)
                )
                SearchScreen(
                    viewModel = viewModel,
                    onSeriesClick = { series ->
                        navController.navigate(Screen.Details.createRoute(series.slug))
                    }
                )
            }

            // Watchlist Screen
            composable(Screen.Watchlist.route) {
                val viewModel: com.example.ui.screens.watchlist.WatchlistViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = com.example.ui.screens.watchlist.WatchlistViewModel.provideFactory(container.userActionsRepository, container.authRepository)
                )
                com.example.ui.screens.watchlist.WatchlistScreen(
                    viewModel = viewModel,
                    onSeriesClick = { series ->
                        navController.navigate(Screen.Details.createRoute(series.slug))
                    },
                    onLoginClick = {
                        navController.navigate(Screen.Profile.route)
                    }
                )
            }

            // Profile Screen
            composable(Screen.Profile.route) {
                val viewModel: ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = ProfileViewModel.provideFactory(container.authRepository, container.settingsRepository)
                )
                ProfileScreen(viewModel = viewModel)
            }

            // Series Details Screen
            composable(
                route = Screen.Details.route,
                arguments = listOf(navArgument("slug") { type = NavType.StringType })
            ) { backStackEntry ->
                val slug = backStackEntry.arguments?.getString("slug") ?: ""
                val viewModel: SeriesDetailsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = SeriesDetailsViewModel.provideFactory(
                        slug = slug,
                        seriesRepo = container.seriesRepository,
                        episodeRepo = container.episodeRepository,
                        userActionsRepo = container.userActionsRepository,
                        commentRepo = container.commentRepository,
                        authRepo = container.authRepository
                    )
                )
                SeriesDetailsScreen(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onWatchEpisode = { seriesId, episodeId ->
                        navController.navigate(Screen.Watch.createRoute(seriesId, episodeId))
                    },
                    onWatchTrailer = { trailerUrl ->
                        // Trailer opens in Watch Screen
                        navController.navigate(Screen.Watch.createRoute(slug, null))
                    },
                    onRelatedSeriesClick = { related ->
                        navController.navigate(Screen.Details.createRoute(related.slug))
                    },
                    onRequireLogin = {
                        navController.navigate(Screen.Profile.route)
                    }
                )
            }

            // Watch Screen (WebView Player, Landscape)
            composable(
                route = Screen.Watch.route,
                arguments = listOf(
                    navArgument("seriesId") { type = NavType.StringType },
                    navArgument("episodeId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val seriesId = backStackEntry.arguments?.getString("seriesId") ?: ""
                val episodeId = backStackEntry.arguments?.getString("episodeId")
                val viewModel: WatchViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = WatchViewModel.provideFactory(
                        seriesId = seriesId,
                        initialEpisodeId = episodeId,
                        seriesRepo = container.seriesRepository,
                        episodeRepo = container.episodeRepository,
                        sourceRepo = container.sourceRepository,
                        userActionsRepo = container.userActionsRepository,
                        settingsRepo = container.settingsRepository
                    )
                )
                WatchScreen(
                    viewModel = viewModel,
                    onExit = { navController.popBackStack() }
                )
            }
        }
    }
}
