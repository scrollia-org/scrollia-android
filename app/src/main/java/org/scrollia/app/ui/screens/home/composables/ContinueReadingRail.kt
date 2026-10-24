package org.scrollia.app.ui.screens.home.composables

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.scrollia.app.R
import org.scrollia.app.helpers.weakHapticFeedback
import org.scrollia.app.ui.screens.home.viewmodels.ContinueReadingItem
import org.scrollia.app.ui.screens.reader.main.activities.ReaderActivity
import org.scrollia.app.ui.screens.reader.main.activities.ReaderConstants
import org.scrollia.app.ui.theme.frauncesFont
import org.scrollia.app.ui.theme.interFont

private val CardWidth = 116.dp

/**
 * "Continue reading" rail (docs/DESIGN.md §14). Tap resumes in the reader at the saved
 * position; long press opens the reader detail screen.
 */
@Composable
fun ContinueReadingRail(
    items: List<ContinueReadingItem>,
    onOpenDetails: (libraryItemId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(id = R.string.home_continue_reading)
                .uppercase(LocalConfiguration.current.locales[0]),
            fontFamily = interFont,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 10.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items, key = { it.libraryItemId }) { item ->
                ContinueReadingCard(
                    item = item,
                    onClick = {
                        val intent = Intent(context, ReaderActivity::class.java)
                        intent.putExtra(ReaderConstants.EXTRA_LIBRARY_ITEM_ID, item.libraryItemId)
                        context.startActivity(intent)
                    },
                    onLongClick = { onOpenDetails(item.libraryItemId) }
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueReadingCard(
    item: ContinueReadingItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val view = LocalView.current
    Column(
        modifier = Modifier
            .width(CardWidth)
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(
                onClick = {
                    view.weakHapticFeedback()
                    onClick()
                },
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            if (item.coverFile != null) {
                AsyncImage(
                    model = item.coverFile,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Typographic stand-in, never stock art (DESIGN.md §10).
                Text(
                    text = item.title,
                    fontFamily = frauncesFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(10.dp)
                )
            }
        }
        LinearProgressIndicator(
            progress = { item.progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(2.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
            drawStopIndicator = {}
        )
        Row(modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = stringResource(id = R.string.progress_percent, item.progressPercent),
                fontFamily = interFont,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = item.title,
            fontFamily = frauncesFont,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            text = item.authors,
            fontFamily = interFont,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
