package com.pricelens.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pricelens.core.ShareInbox
import com.pricelens.ui.screens.AdapterStatusScreen
import com.pricelens.ui.screens.CompareScreen
import com.pricelens.ui.screens.ImportScreen
import com.pricelens.ui.screens.ProductDetailScreen
import com.pricelens.ui.screens.ProductsScreen
import com.pricelens.ui.screens.SearchScreen
import com.pricelens.ui.screens.WatchScreen

object Routes {
    const val PRODUCTS = "products"
    const val SEARCH = "search"
    const val IMPORT = "import"
    const val WATCH = "watches"
    const val ADAPTERS = "adapters"
    const val PRODUCT_DETAIL = "product/{spuId}"
    const val COMPARE = "compare/{spuId}"

    fun productDetail(spuId: Long) = "product/$spuId"
    fun compare(spuId: Long) = "compare/$spuId"
}

private val topLevelRoutes = setOf(
    Routes.PRODUCTS, Routes.SEARCH, Routes.IMPORT, Routes.WATCH, Routes.ADAPTERS
)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = backStackEntry?.destination?.route
    val sharedText by ShareInbox.text.collectAsState()

    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) {
            navController.navigate(Routes.IMPORT)
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in topLevelRoutes) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.PRODUCTS,
                        onClick = { navController.navigateSingleTop(Routes.PRODUCTS) },
                        icon = { Icon(Icons.Filled.List, contentDescription = null) },
                        label = { Text("商品") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SEARCH,
                        onClick = { navController.navigateSingleTop(Routes.SEARCH) },
                        icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        label = { Text("搜索") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.IMPORT,
                        onClick = { navController.navigateSingleTop(Routes.IMPORT) },
                        icon = { Icon(Icons.Filled.AddCircle, contentDescription = null) },
                        label = { Text("采集") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.WATCH,
                        onClick = { navController.navigateSingleTop(Routes.WATCH) },
                        icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                        label = { Text("关注") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.ADAPTERS,
                        onClick = { navController.navigateSingleTop(Routes.ADAPTERS) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("状态") }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.PRODUCTS,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.PRODUCTS) {
                ProductsScreen(
                    onOpenSearch = { navController.navigateSingleTop(Routes.SEARCH) },
                    onOpenImport = { navController.navigateSingleTop(Routes.IMPORT) },
                    onOpenProduct = { navController.navigate(Routes.productDetail(it)) },
                    onOpenCompare = { navController.navigate(Routes.compare(it)) }
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen()
            }
            composable(Routes.IMPORT) {
                ImportScreen()
            }
            composable(Routes.WATCH) {
                WatchScreen()
            }
            composable(Routes.ADAPTERS) {
                AdapterStatusScreen()
            }
            composable(
                Routes.PRODUCT_DETAIL,
                arguments = listOf(navArgument("spuId") { type = NavType.LongType })
            ) { entry ->
                val spuId = entry.arguments?.getLong("spuId") ?: 0L
                ProductDetailScreen(
                    spuId = spuId,
                    onBack = { navController.popBackStack() },
                    onOpenCompare = { navController.navigate(Routes.compare(spuId)) }
                )
            }
            composable(
                Routes.COMPARE,
                arguments = listOf(navArgument("spuId") { type = NavType.LongType })
            ) { entry ->
                val spuId = entry.arguments?.getLong("spuId") ?: 0L
                CompareScreen(spuId = spuId, onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun androidx.navigation.NavController.navigateSingleTop(route: String) {
    navigate(route) {
        popUpTo(Routes.PRODUCTS) { inclusive = false }
        launchSingleTop = true
    }
}
