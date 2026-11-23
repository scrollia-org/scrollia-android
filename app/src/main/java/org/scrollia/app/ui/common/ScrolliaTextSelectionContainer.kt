/**
 * Copyright (c) [2022 - Present] Stɑrry Shivɑm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scrollia.app.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import org.scrollia.app.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


private const val MENU_ITEM_COPY = 0
private const val MENU_ITEM_SHARE = 1
private const val MENU_ITEM_WEB = 2
private const val MENU_ITEM_TRANSLATE = 3
private const val MENU_ITEM_DICTIONARY = 4
private const val MENU_ITEM_FEEDBACK = 5

/**
 * Custom Text ActionMode callback. Used in pair with [ScrolliaSelectionToolbar]. Follow [TextToolbar] for more info.
 */
private class ScrolliaTextActionModeCallback(
    private val context: Context,
    var rect: Rect = Rect.Zero,
    var onCopyRequested: (() -> Unit)? = null,
    var onShareRequested: (() -> Unit)? = null,
    var onWebSearchRequested: (() -> Unit)? = null,
    var onTranslateRequested: (() -> Unit)? = null,
    var onDictionaryRequested: (() -> Unit)? = null,
    var onFeedbackRequested: (() -> Unit)? = null
) : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        requireNotNull(menu)
        requireNotNull(mode)

        onCopyRequested?.let {
            menu.add(0, MENU_ITEM_COPY, 0, context.getString(R.string.copy))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }

        onShareRequested?.let {
            menu.add(0, MENU_ITEM_SHARE, 1, context.getString(R.string.share))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }

        onWebSearchRequested?.let {
            menu.add(0, MENU_ITEM_WEB, 2, context.getString(R.string.web_search))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }

        onTranslateRequested?.let {
            menu.add(0, MENU_ITEM_TRANSLATE, 3, context.getString(R.string.translate))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }

        onDictionaryRequested?.let {
            menu.add(0, MENU_ITEM_DICTIONARY, 4, context.getString(R.string.dictionary))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }

        onFeedbackRequested?.let {
            menu.add(0, MENU_ITEM_FEEDBACK, 5, context.getString(R.string.selection_report_typo))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }

        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        return false
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        when (item!!.itemId) {
            MENU_ITEM_COPY -> onCopyRequested?.invoke()
            MENU_ITEM_SHARE -> onShareRequested?.invoke()
            MENU_ITEM_WEB -> onWebSearchRequested?.invoke()
            MENU_ITEM_TRANSLATE -> onTranslateRequested?.invoke()
            MENU_ITEM_DICTIONARY -> onDictionaryRequested?.invoke()
            MENU_ITEM_FEEDBACK -> onFeedbackRequested?.invoke()
            else -> return false
        }
        mode?.finish()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode?) {}
}

/**
 * Floating [ScrolliaTextActionModeCallback].
 */
private class FloatingTextActionModeCallback(
    val callback: ScrolliaTextActionModeCallback
) : ActionMode.Callback2() {
    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        return callback.onActionItemClicked(mode, item)
    }

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        return callback.onCreateActionMode(mode, menu)
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        return callback.onPrepareActionMode(mode, menu)
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
        callback.onDestroyActionMode(mode)
    }

    override fun onGetContentRect(mode: ActionMode?, view: View?, outRect: android.graphics.Rect?) {
        val rect = callback.rect
        outRect?.set(
            rect.left.toInt(),
            rect.top.toInt(),
            rect.right.toInt(),
            rect.bottom.toInt()
        )
    }
}

/**
 * Custom Selection Toolbar.
 * Used in pair with [ScrolliaSelectionContainer] to display custom toolbar.
 */
@Suppress("DEPRECATION")
private class ScrolliaSelectionToolbar(
    private val view: View,
    context: Context,
    private val onCopyRequest: (() -> Unit)?,
    private val onShareRequest: ((String) -> Unit)?,
    private val onWebSearchRequest: ((String) -> Unit)?,
    private val onTranslateRequest: ((String) -> Unit)?,
    private val onDictionaryRequest: ((String) -> Unit)?,
    // Updated on recomposition so typo reports keep the current chapter.
    var onFeedbackRequest: ((String) -> Unit)?
) : TextToolbar {
    private var actionMode: ActionMode? = null
    private val callback = ScrolliaTextActionModeCallback(context = context)

    val clipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override var status: TextToolbarStatus by mutableStateOf(TextToolbarStatus.Hidden)

    /**
     * Compose only gives the selected text via copy-to-clipboard. Copy, read, invoke
     * [block], then restore the previous clip so Share/Meaning do not leave the
     * selection on the system clipboard.
     */
    private fun withSelectedText(onCopyRequested: (() -> Unit)?, block: (String) -> Unit) {
        val previousClipboard = clipboardManager.primaryClip
        onCopyRequested?.invoke()
        block(clipboardManager.text?.toString().orEmpty())
        if (previousClipboard != null) {
            clipboardManager.setPrimaryClip(previousClipboard)
        } else {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(null, " "))
        }
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        callback.rect = rect
        callback.onCopyRequested = {
            onCopyRequested?.invoke()
            onCopyRequest?.invoke()
        }
        // Only set handlers that exist so the floating menu never shows a dead item.
        callback.onShareRequested = onShareRequest?.let { handler ->
            {
                withSelectedText(onCopyRequested) { handler(it) }
            }
        }
        callback.onWebSearchRequested = onWebSearchRequest?.let { handler ->
            {
                withSelectedText(onCopyRequested) { handler(it) }
            }
        }
        callback.onTranslateRequested = onTranslateRequest?.let { handler ->
            {
                withSelectedText(onCopyRequested) { handler(it) }
            }
        }
        callback.onDictionaryRequested = onDictionaryRequest?.let { handler ->
            {
                withSelectedText(onCopyRequested) { handler(it) }
            }
        }
        callback.onFeedbackRequested = onFeedbackRequest?.let { handler ->
            {
                withSelectedText(onCopyRequested) { handler(it) }
            }
        }

        if (actionMode == null) {
            status = TextToolbarStatus.Shown
            actionMode = view.startActionMode(
                FloatingTextActionModeCallback(callback),
                ActionMode.TYPE_FLOATING
            )
        } else {
            actionMode?.invalidate()
        }
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        actionMode?.finish()
        actionMode = null
    }
}

/**
 * Custom selection container.
 *
 * @param onCopyRequested Callback for when the copy option is clicked.
 * @param onTranslateRequested Callback for when the translate option is clicked.
 * @param onDictionaryRequested Callback for when the dictionary option is clicked.
 * @param selectable When false only the toolbar is provided; the content places its own
 *   [SelectionContainer]s (e.g. one per page, so a selection never spans hidden content).
 * @param content Selection container content.
 */
@Composable
fun ScrolliaSelectionContainer(
    onCopyRequested: (() -> Unit),
    onShareRequested: ((String) -> Unit),
    onWebSearchRequested: ((String) -> Unit),
    onTranslateRequested: ((String) -> Unit),
    onDictionaryRequested: ((String) -> Unit),
    onFeedbackRequested: ((String) -> Unit)? = null,
    selectable: Boolean = true,
    content: @Composable (toolbarHidden: Boolean) -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current

    val scrolliaSelectionToolbar = remember {
        ScrolliaSelectionToolbar(
            view = view,
            context = context,

            onCopyRequest = {
                onCopyRequested()
            },
            onShareRequest = {
                onShareRequested(it)
            },
            onWebSearchRequest = null, // kept off until web-search QA (DESIGN §19)
            onTranslateRequest = {
                onTranslateRequested(it)
            },
            onDictionaryRequest = {
                onDictionaryRequested(it)
            },
            onFeedbackRequest = onFeedbackRequested
        )
    }
    // Refresh each composition so reports include the chapter on screen.
    scrolliaSelectionToolbar.onFeedbackRequest = onFeedbackRequested
    val isToolbarHidden = remember(scrolliaSelectionToolbar.status) {
        derivedStateOf {
            scrolliaSelectionToolbar.status == TextToolbarStatus.Hidden
        }
    }

    CompositionLocalProvider(
        LocalTextToolbar provides scrolliaSelectionToolbar
    ) {
        if (selectable) {
            SelectionContainer(Modifier.fillMaxSize()) {
                content(isToolbarHidden.value)
            }
        } else {
            content(isToolbarHidden.value)
        }
    }
}