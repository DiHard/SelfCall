package ru.ifx.selfcall.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

object Routes {
    const val NAME = "name"
    const val ROOMS = "rooms"
    const val CALL = "call/{roomName}"
    fun call(room: String) = "call/$room"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.NAME) {
        composable(Routes.NAME) {
            NameScreen(onContinue = {
                nav.navigate(Routes.ROOMS) {
                    popUpTo(Routes.NAME) { inclusive = true }
                }
            })
        }
        composable(Routes.ROOMS) {
            RoomsScreen(
                onJoin = { room -> nav.navigate(Routes.call(room)) },
                onChangeName = {
                    nav.navigate(Routes.NAME) {
                        popUpTo(Routes.ROOMS) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.CALL) { entry ->
            val room = entry.arguments?.getString("roomName").orEmpty()
            CallScreen(
                roomName = room,
                onLeave = { nav.popBackStack() },
            )
        }
    }
}
