package com.clipnest

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.clipnest.ui.editor.NativeEditorView
import java.lang.ref.WeakReference

/**
 * Keeps the editor's two pieces of position state independent:
 * - selectionStart/selectionEnd: the caret/selection
 * - scrollY: the viewport
 *
 * Internal/external document switches can reuse the same NativeEditorView, so the
 * controller also watches the Editable identity. This lets it capture the outgoing
 * position before setText()/setSelection() replace it.
 */
class EditorViewportRestoreProvider : ContentProvider() {
    private var callbacks: Application.ActivityLifecycleCallbacks? = null

    override fun onCreate(): Boolean {
        val application = context?.applicationContext as? Application ?: return false
        val controller = Controller()
        callbacks = controller
        application.registerActivityLifecycleCallbacks(controller)
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private class Controller : Application.ActivityLifecycleCallbacks {
        private data class Snapshot(
            val documentKey: String,
            val selectionStart: Int,
            val selectionEnd: Int,
            val scrollY: Int
        )

        private val snapshots = LinkedHashMap<String, ArrayDeque<Snapshot>>(6, 0.75f, true)
        private var lastEditor: WeakReference<NativeEditorView>? = null
        private var lastTextObject: Any? = null
        private var lastDocumentKey: String = ""
        private var lastSelectionStart = 0
        private var lastSelectionEnd = 0
        private var lastScrollY = 0
        private var lastActivity: WeakReference<Activity>? = null
        private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
        private var pendingPreDraw: ViewTreeObserver.OnPreDrawListener? = null
        private var pendingTouchEditor: NativeEditorView? = null
        private var pendingTarget: Snapshot? = null
        private var pendingStableFrames = 0

        override fun onActivityResumed(activity: Activity) {
            lastActivity = WeakReference(activity)
            installLayoutObserver(activity)
        }

        override fun onActivityPaused(activity: Activity) {
            captureCurrentEditor(activity)
            clearPendingRestore()
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (lastActivity?.get() === activity) {
                removeLayoutObserver(activity)
                clearPendingRestore()
                lastActivity = null
                lastEditor = null
                lastTextObject = null
                lastDocumentKey = ""
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit

        private fun installLayoutObserver(activity: Activity) {
            removeLayoutObserver(activity)
            val decor = activity.window.decorView
            val listener = ViewTreeObserver.OnGlobalLayoutListener {
                val editor = findEditor(decor)
                if (editor == null) {
                    captureCurrentEditor(activity)
                    return@OnGlobalLayoutListener
                }

                val previousEditor = lastEditor?.get()
                if (previousEditor !== editor) {
                    if (previousEditor != null) captureEditor(previousEditor)
                    lastEditor = WeakReference(editor)
                    lastTextObject = editor.text
                    lastDocumentKey = documentKey(editor)
                    rememberCurrentPosition(editor)
                    scheduleRestoreIfAvailable(editor, lastDocumentKey)
                    return@OnGlobalLayoutListener
                }

                val currentTextObject = editor.text
                val currentKey = documentKey(editor)
                if (currentTextObject !== lastTextObject) {
                    captureLastObservedPosition()
                    clearPendingRestore()
                    lastTextObject = currentTextObject
                    lastDocumentKey = currentKey
                    rememberCurrentPosition(editor)
                    scheduleRestoreIfAvailable(editor, currentKey)
                } else {
                    rememberCurrentPosition(editor)
                }
            }
            layoutListener = listener
            decor.viewTreeObserver.addOnGlobalLayoutListener(listener)
        }

        private fun removeLayoutObserver(activity: Activity) {
            val listener = layoutListener ?: return
            val observer = activity.window.decorView.viewTreeObserver
            if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
            layoutListener = null
        }

        private fun captureCurrentEditor(activity: Activity) {
            val editor = lastEditor?.get() ?: findEditor(activity.window.decorView) ?: return
            captureEditor(editor)
            lastEditor = WeakReference(editor)
            lastTextObject = editor.text
            lastDocumentKey = documentKey(editor)
            rememberCurrentPosition(editor)
        }

        private fun captureEditor(editor: NativeEditorView) {
            val key = documentKey(editor)
            if (key.isEmpty()) return
            val snapshot = Snapshot(
                documentKey = key,
                selectionStart = editor.selectionStart.coerceAtLeast(0),
                selectionEnd = editor.selectionEnd.coerceAtLeast(0),
                scrollY = editor.scrollY.coerceAtLeast(0)
            )
            store(snapshot)
        }

        private fun captureLastObservedPosition() {
            if (lastDocumentKey.isEmpty()) return
            store(
                Snapshot(
                    documentKey = lastDocumentKey,
                    selectionStart = lastSelectionStart,
                    selectionEnd = lastSelectionEnd,
                    scrollY = lastScrollY
                )
            )
        }

        private fun store(snapshot: Snapshot) {
            val snapshotsForKey = snapshots.getOrPut(snapshot.documentKey) { ArrayDeque(2) }
            snapshotsForKey.removeAll { it.selectionStart == snapshot.selectionStart && it.selectionEnd == snapshot.selectionEnd && it.scrollY == snapshot.scrollY }
            snapshotsForKey.addFirst(snapshot)
            while (snapshotsForKey.size > 2) snapshotsForKey.removeLast()
            while (snapshots.size > 8) snapshots.entries.iterator().let { it.next(); it.remove() }
        }

        private fun rememberCurrentPosition(editor: NativeEditorView) {
            lastSelectionStart = editor.selectionStart.coerceAtLeast(0)
            lastSelectionEnd = editor.selectionEnd.coerceAtLeast(lastSelectionStart)
            lastScrollY = editor.scrollY.coerceAtLeast(0)
        }

        private fun scheduleRestoreIfAvailable(editor: NativeEditorView, key: String) {
            clearPendingRestore()
            if (key.isEmpty()) return
            val target = snapshots[key]?.firstOrNull() ?: return
            pendingTarget = target
            pendingTouchEditor = editor
            pendingStableFrames = 0

            editor.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) clearPendingRestore()
                false
            }

            val observer = editor.viewTreeObserver
            val listener = object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val currentKey = documentKey(editor)
                    val targetSnapshot = pendingTarget
                    if (lastEditor?.get() !== editor || currentKey != key || targetSnapshot == null) {
                        clearPendingRestore()
                        return true
                    }

                    val maxScroll = maxScrollY(editor)
                    if (maxScroll < targetSnapshot.scrollY && targetSnapshot.scrollY > 0) {
                        pendingStableFrames = 0
                        return true
                    }

                    val targetSelectionStart = targetSnapshot.selectionStart.coerceIn(0, editor.length())
                    val targetSelectionEnd = targetSnapshot.selectionEnd.coerceIn(targetSelectionStart, editor.length())
                    if (editor.selectionStart != targetSelectionStart || editor.selectionEnd != targetSelectionEnd) {
                        editor.setSelection(targetSelectionStart, targetSelectionEnd)
                    }
                    val clampedScroll = targetSnapshot.scrollY.coerceIn(0, maxScroll)
                    if (editor.scrollY != clampedScroll) {
                        editor.scrollTo(editor.scrollX, clampedScroll)
                    }

                    val selectionMatches = editor.selectionStart == targetSelectionStart && editor.selectionEnd == targetSelectionEnd
                    val scrollMatches = editor.scrollY == clampedScroll
                    pendingStableFrames = if (selectionMatches && scrollMatches) pendingStableFrames + 1 else 0

                    if (pendingStableFrames >= 3) {
                        observer.removeOnPreDrawListener(this)
                        pendingPreDraw = null
                        pendingTouchEditor?.setOnTouchListener(null)
                        pendingTouchEditor = null
                        pendingTarget = null
                        pendingStableFrames = 0
                    }
                    return true
                }
            }
            pendingPreDraw = listener
            observer.addOnPreDrawListener(listener)
        }

        private fun maxScrollY(editor: NativeEditorView): Int {
            val layoutHeight = editor.layout?.height ?: 0
            if (layoutHeight <= 0 || editor.height <= 0) return 0
            return (layoutHeight + editor.compoundPaddingTop + editor.compoundPaddingBottom - editor.height).coerceAtLeast(0)
        }

        private fun clearPendingRestore() {
            pendingPreDraw?.let { listener ->
                val editor = pendingTouchEditor
                val observer = editor?.viewTreeObserver
                if (observer?.isAlive == true) observer.removeOnPreDrawListener(listener)
            }
            pendingPreDraw = null
            pendingTouchEditor?.setOnTouchListener(null)
            pendingTouchEditor = null
            pendingTarget = null
            pendingStableFrames = 0
        }

        private fun documentKey(editor: NativeEditorView): String {
            val text = editor.text?.toString().orEmpty()
            if (text.isEmpty()) return ""
            return "${text.length}:${text.hashCode()}"
        }

        private fun findEditor(root: View): NativeEditorView? {
            if (root is NativeEditorView) return root
            if (root !is ViewGroup) return null
            for (index in 0 until root.childCount) {
                findEditor(root.getChildAt(index))?.let { return it }
            }
            return null
        }
    }
}
