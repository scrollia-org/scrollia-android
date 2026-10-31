package org.scrollia.app.ui.screens.reader.main.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.scrollia.app.R
import org.scrollia.app.helpers.book.BookImporter
import org.scrollia.app.ui.screens.reader.main.viewmodel.ExternalImport
import org.scrollia.app.ui.screens.reader.main.viewmodel.ReaderTheme
import org.scrollia.app.ui.theme.frauncesFont
import org.scrollia.app.ui.theme.interFont
import org.scrollia.app.ui.theme.literataFont

/**
 * An EPUB opened from another app: a calm question, the copy's progress, or a
 * recoverable failure, in the reader's own colors and typographic style.
 */
@Composable
fun ExternalImportScreen(
    importState: ExternalImport,
    theme: ReaderTheme,
    onAdd: () -> Unit,
    onCancel: () -> Unit
) {
    val ink = theme.textColor
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(
                    id = when (importState) {
                        is ExternalImport.Confirm -> R.string.import_external_title
                        is ExternalImport.Importing -> R.string.import_external_importing
                        is ExternalImport.Failed -> R.string.import_external_failed_title
                    }
                ),
                modifier = Modifier.semantics { heading() },
                color = ink,
                fontFamily = frauncesFont,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            importState.displayName?.let { name ->
                Text(
                    text = name,
                    modifier = Modifier.padding(top = 8.dp),
                    color = ink.copy(alpha = 0.65f),
                    fontFamily = interFont,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when (importState) {
                is ExternalImport.Confirm -> Message(R.string.import_external_message, ink)
                is ExternalImport.Importing -> {
                    val progressModifier = Modifier
                        .padding(top = 24.dp, bottom = 24.dp)
                        .fillMaxWidth()
                    val color = ink.copy(alpha = 0.8f)
                    val track = ink.copy(alpha = 0.12f)
                    val progress = importState.progress
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = progressModifier,
                            color = color,
                            trackColor = track
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = progressModifier,
                            color = color,
                            trackColor = track
                        )
                    }
                }
                is ExternalImport.Failed -> Message(
                    when (importState.reason) {
                        BookImporter.Reason.NOT_EPUB -> R.string.import_error_not_epub
                        BookImporter.Reason.UNREADABLE -> R.string.import_error_unreadable
                        BookImporter.Reason.STORAGE,
                        BookImporter.Reason.DATABASE -> R.string.import_error_storage
                    },
                    ink
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(R.string.cancel), fontFamily = interFont, color = ink)
            }
            when (importState) {
                is ExternalImport.Confirm -> PrimaryButton(R.string.import_external_add, onAdd)
                is ExternalImport.Failed -> PrimaryButton(R.string.network_error_retry, onAdd)
                is ExternalImport.Importing -> Unit
            }
        }
    }
}

@Composable
private fun Message(textRes: Int, ink: androidx.compose.ui.graphics.Color) {
    Text(
        text = stringResource(textRes),
        modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        color = ink.copy(alpha = 0.72f),
        fontFamily = literataFont,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun PrimaryButton(labelRes: Int, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text(text = stringResource(labelRes), fontFamily = interFont, fontWeight = FontWeight.Medium)
    }
}
