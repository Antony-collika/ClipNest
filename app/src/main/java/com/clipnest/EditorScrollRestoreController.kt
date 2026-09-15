package com.clipnest

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.clipnest.ui.editor.NativeEditorView
import java.lang.ref.WeakReference

/**
 * Keeps the visible editor viewport stable when Compose replaces the native editor view
 * during same-process navigation or external-document round trips.
 *
 * Cursor restoration and process-restart restoration remain owned by EditorViewModel.
 * This controller only remembers a live viewport long enough to re-apply it after the
 * replacement view has completed its first layout/focus passes.
 */
class EditorScrollRestoreController(private val application: Application) : Application.ActivityLifecycleCallbacks {
    private data class ViewportSnapshot(val documentKey: String, val scrollY: Int)

    private val snapshots = LinkedHashMap<String, ViewportSnapshot>(4, 0.75f, true)
    private var lastEditor: WeakReference<NativeEditorView>? = null
    private var lastActivity: WeakReference<Activity>? = null
    private var lastEditorKey: String? = null
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    fun install() {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        lastActivity = WeakReference(activity)
        installGlobalLayoutObserver(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        captureCurrentEditor(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (lastActivity?.get() === activity) {
            removeGlobalLayoutObserver(activity)
            lastActivity = null
            lastEditor = null
            lastEditorKey = null
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private fun installGlobalLayoutObserver(activity: Activity) {
        val decor = activity.window.decorView
        removeGlobalLayoutObserver(activity)
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val editor = findEditor(decor)
            if (editor == null) {
                captureCurrentEditor(activity)
                return@OnGlobalLayoutListener
            }

            if (lastEditor?.get() !== editor) {
                captureCurrentEditor(activity)
                lastEditor = WeakReference(editor)
                lastEditorKey = documentKey(editor)
                scheduleRestore(editor, lastEditorKey)
            }
        }
        globalLayoutListener = listener
        decor.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun removeGlobalLayoutObserver(activity: Activity) {
        val listener = globalLayoutListener ?: return
        val observer = activity.window.decorView.viewTreeObserver
        if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
        globalLayoutListener = null
    }

    private fun captureCurrentEditor(activity: Activity) {
        val editor = lastEditor?.get() ?: findEditor(activity.window.decorView) ?: return
        val key = documentKey(editor)
        if (key.isBlank()) return
        snapshots[key] = ViewportSnapshot(key, editor.scrollY)
        trimSnapshots()
        lastEditor = WeakReference(editor)
        lastEditorKey = key
    }

    private fun scheduleRestore(editor: NativeEditorView, key: String?) {
        if (key == null) return
        val snapshot = snapshots[key] ?: return
        val target = snapshot.scrollY

        // The editor's own restore runs during layout. These frame-delayed checks are a
        // final guard against Android's later cursor/focus/layout passes moving the
        // viewport back to the caret.
        editor.postOnAnimation {
            applyScrollIfStillCurrent(editor, key, target)
            editor.postOnAnimation {
                applyScrollIfStillCurrent(editor, key, target)
                editor.postOnAnimation {
                    applyScrollIfStillCurrent(editor, key, target)
                }
            }
        }
    }

    private fun applyScrollIfStillCurrent(editor: NativeEditorView, key: String, target: Int) {
        if (documentKey(editor) != key) return
        val max = (editor.computeVerticalScrollRange() - editor.computeVerticalScrollExtent()).coerceAtLeast(0)
        val clamped = target.coerceIn(0, max)
        if (editor.scrollY != clamped) {
            editor.scrollTo(editor.scrollX, clamped)
        }
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

    private fun trimSnapshots() {
        while (snapshots.size > 4) snapshots.entries.iterator().let { iterator -> iterator.next(); iterator.remove() }
    }
}

class ClipNestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EditorScrollRestoreController(this).install()
    }
}
