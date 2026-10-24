package org.scrollia.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.scrollia.app.R
import org.scrollia.app.helpers.LoadFailure
import org.scrollia.app.ui.theme.interFont

/** Full-content Offline or Error (DESIGN.md §10, §20): a calm sentence and Retry. */
@Composable
fun LoadFailureState(failure: LoadFailure, onRetry: () -> Unit) {
    NetworkError(
        errorMessage = stringResource(
            id = when (failure) {
                LoadFailure.OFFLINE -> R.string.state_offline
                LoadFailure.ERROR -> R.string.state_error
            }
        ),
        onRetryClicked = onRetry
    )
}

/**
 * Below loaded items: dots while a later page is actually loading, or a one-line failure with
 * Retry. Nothing at the end of the list.
 */
@Composable
fun AppendFooter(isAppending: Boolean, failure: LoadFailure?, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            failure != null -> {
                Text(
                    text = stringResource(
                        id = when (failure) {
                            LoadFailure.OFFLINE -> R.string.append_failed_offline
                            LoadFailure.ERROR -> R.string.append_failed_error
                        }
                    ),
                    fontFamily = interFont,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onRetry) {
                    Text(
                        text = stringResource(id = R.string.network_error_retry),
                        fontFamily = interFont,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            isAppending -> ProgressDots()
        }
    }
}

/**
 * Ask for the next page when the end of the grid is visible. Keyed on the item count, so it
 * fires once per arrival at the end rather than on every recomposition.
 */
@Composable
fun LoadMoreEffect(gridState: LazyGridState, onLoadMore: () -> Unit, threshold: Int = 2) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow -1
            if (info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 1 - threshold) {
                info.totalItemsCount
            } else -1
        }
            .distinctUntilChanged()
            .filter { it >= 0 }
            .collect { currentOnLoadMore() }
    }
}
