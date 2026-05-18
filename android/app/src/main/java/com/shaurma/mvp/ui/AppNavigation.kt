package com.shaurma.mvp.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object Routes {
    const val MAIN = "main"
    const val CART = "cart"
    const val ORDERS = "orders"
    const val PROFILE = "profile"
    const val CATEGORY = "category/{categoryId}"
    const val PRODUCT = "product/{itemId}"

    fun category(categoryId: Int) = "category/$categoryId"
    fun product(itemId: Int) = "product/$itemId"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(
                onCategoryClick = { navController.navigate(Routes.category(it)) },
                onCartClick = { navController.navigate(Routes.CART) },
                onOrdersClick = { navController.navigate(Routes.ORDERS) },
                onProfileClick = { navController.navigate(Routes.PROFILE) },
            )
        }
        composable(
            route = Routes.CATEGORY,
            arguments = listOf(navArgument("categoryId") { type = NavType.IntType }),
        ) {
            CategoryScreen(
                onBack = { navController.popBackStack() },
                onProductClick = { navController.navigate(Routes.product(it)) },
            )
        }
        composable(
            route = Routes.PRODUCT,
            arguments = listOf(navArgument("itemId") { type = NavType.IntType }),
        ) {
            ProductScreen(
                onBack = { navController.popBackStack() },
                onAdded = { navController.popBackStack() },
            )
        }
        composable(Routes.CART) {
            CartScreen(
                onBack = { navController.popBackStack() },
                onEditItem = { itemId -> navController.navigate(Routes.product(itemId)) },
                onOrderCreated = {
                    navController.navigate(Routes.ORDERS) {
                        popUpTo(Routes.MAIN)
                    }
                },
            )
        }
        composable(Routes.ORDERS) {
            OrdersScreen(
                onBack = { navController.popBackStack() },
                onMainMenu = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.PROFILE) {
            ProfileScreen(onBack = { navController.popBackStack() })
        }
    }
}
