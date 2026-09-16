package com.clipnest

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.clipnest.ui.editor.NativeEditorView
import java.lang.ref.WeakReference

/**
 * Keeps the live editor viewport independent from the caret when Compose recreates
 * the NativeEditorView during same-process navigation.
 *
 * The snapshot is captured only at the hand-off (the editor disappears or the
 * activity pauses). Restoration is a one-shot pre-draw operation after the new
 * editor has a real layout, so it does not fight normal scrolling during editing.
 */
class EditorViewportRestoreProvider : ContentProvider() {
    private var callbacks: Application.ActivityLifecycleCallbacks? = null

    override fun onCreate(): Boolean {
        val application = context?.applicationContext as? Application ?: return false
        val controller = Controller(application)
        callbacks = controller
        application.registerActivityLifecycleCallbacks(controller)
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    private class Controller(private val application: Application) : Application.ActivityLifecycleCallbacks {
        private data class Snapshot(val documentKey: String, val scrollY: Int)

        private val snapshots = LinkedHashMap<String, Snapshot>(4, 0.75f, true)
        private var lastEditor: WeakReference<NativeEditorView>? = null
        private var lastActivity: WeakReference<Activity>? = null
        private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
        private var pendingPreDraw: ViewTreeObserver.OnPreDrawListener? = null
        private var pendingTouchEditor: NativeEditorView? = null
        private var pendingTarget: Int? = null

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
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        private fun installLayoutObserver(activity: Activity) {
            removeLayoutObserver(activity)
            val decor = activity.window.decorView
            val listener = ViewTreeObserver.OnGlobalLayoutListener {
                val editor = findEditor(decor)
                if (editor == null) {
                    captureCurrentEditor(activity)
                    return@OnGlobalLayoutListener
                }

                val previous = lastEditor?.get()
                if (previous !== editor) {
                    if (previous != null) captureEditor(previous)
                    lastEditor = WeakReference(editor)
                    scheduleRestoreIfAvailable(editor)
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
        }

        private fun captureEditor(editor: NativeEditorView) {
            val key = documentKey(editor)
            if (key.isEmpty()) return
            snapshots[key] = Snapshot(key, editor.scrollY)
            while (snapshots.size > 4) snapshots.entries.iterator().let { it.next(); it.remove() }
        }

        private fun scheduleRestoreIfAvailable(editor: NativeEditorView) {
            clearPendingRestore()
            val key = documentKey(editor)
            if (key.isEmpty()) return
            val snapshot = snapshots[key] ?: return
            val target = snapshot.scrollY
            pendingTarget = target
            pendingTouchEditor = editor
            editor.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) clearPendingRestore()
                false
            }

            val observer = editor.viewTreeObserver
            val listener = object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (documentKey(editor) != key || lastEditor?.get() !== editor) {
                        clearPendingRestore()
                        return true
                    }
                    val maxScroll = (editor.computeVerticalScrollRange() - editor.computeVerticalScrollExtent()).coerceAtLeast(0)
                    if (maxScroll < target && target > 0) {
                        // The same document is still acquiring its final text layout.
                        // Keep this listener until the real scroll range is available;
                        // this is tied to layout readiness rather than a time/frame budget.
                        return true
                    }
                    editor.scrollTo(editor.scrollX, target.coerceIn(0, maxScroll))
                    clearPendingRestore()
                    return true
                }
            }
            pendingPreDraw = listener
            observer.addOnPreDrawListener(listener)
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
