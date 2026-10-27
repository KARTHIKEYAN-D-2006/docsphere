package com.docsphere.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Files : Screen("files")
    data object Chat : Screen("chat/{sessionId}") {
        fun withSession(sessionId: String = "new") = "chat/$sessionId"
        const val ROUTE_BASE = "chat"
    }
    data object Drive : Screen("drive")
    data object Settings : Screen("settings")
    data object FileViewer : Screen("file_viewer/{docId}") {
        fun withDoc(docId: String) = "file_viewer/${android.net.Uri.encode(docId)}"
        const val ROUTE_BASE = "file_viewer"
    }
}
