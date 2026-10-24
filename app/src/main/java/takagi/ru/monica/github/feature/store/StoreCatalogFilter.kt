package takagi.ru.monica.github.feature.store

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import takagi.ru.monica.github.domain.FdroidApp

data class FdroidCatalogInputs(
    val apps: List<FdroidApp>,
    val installed: Map<String, String>,
    val updates: List<FdroidApp>,
    val filter: FdroidCatalogFilter,
    val query: String
)

data class FdroidCatalogProjection(val inputs: FdroidCatalogInputs, val apps: List<FdroidApp>)

internal fun StoreUiState.fdroidCatalogInputs() =
    FdroidCatalogInputs(fdroidApps, fdroidInstalled, updates, fdroidFilter, query.trim())

@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<StoreUiState>.fdroidCatalogProjections(dispatcher: CoroutineDispatcher): Flow<FdroidCatalogProjection> =
    map { it.fdroidCatalogInputs() }.distinctUntilChanged().mapLatest { inputs ->
        FdroidCatalogProjection(inputs, withContext(dispatcher) { filterFdroidCatalog(inputs) })
    }

/** Cancellation is checked while scanning so a newer query does not wait for an obsolete one. */
internal suspend fun filterFdroidCatalog(inputs: FdroidCatalogInputs): List<FdroidApp> {
    val candidates = if (inputs.filter == FdroidCatalogFilter.UPDATES) inputs.updates else inputs.apps
    if (inputs.filter != FdroidCatalogFilter.INSTALLED && inputs.query.isEmpty()) return candidates
    val context = currentCoroutineContext()
    return candidates.filter { app ->
        context.ensureActive()
        (inputs.filter != FdroidCatalogFilter.INSTALLED || app.packageName in inputs.installed) &&
            (inputs.query.isEmpty() || app.name.contains(inputs.query, true) ||
                app.packageName.contains(inputs.query, true) || app.summary?.contains(inputs.query, true) == true)
    }
}
