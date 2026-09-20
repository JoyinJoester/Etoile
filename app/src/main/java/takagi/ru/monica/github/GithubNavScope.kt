package takagi.ru.monica.github

import androidx.navigation.NavHostController
import takagi.ru.monica.github.di.GithubAppDependencies
import takagi.ru.monica.github.feature.auth.GithubSessionUiState

/**
 * 业务域导航图共享的上下文:依赖容器、导航控制器与会话状态。
 * sessionState 走 provider 委托——图内 Composable 每次读取都发生在组合期,
 * 因此会话变化仍能正常触发重组,不会读到过期快照。
 */
internal class GithubNavScope(
    val dependencies: GithubAppDependencies,
    val navController: NavHostController,
    private val sessionStateProvider: () -> GithubSessionUiState,
    val openUrl: (String) -> Unit,
) {
    val sessionState: GithubSessionUiState get() = sessionStateProvider()
}
