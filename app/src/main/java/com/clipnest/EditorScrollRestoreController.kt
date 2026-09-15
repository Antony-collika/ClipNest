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
 * Same-process editor viewport restoration experiment.
 *
 * When the editor view is recreated, restore the viewport first and then move the
 * cursor into that restored viewport. This deliberately gives Android no reason to
 * scroll the document back toward a distant caret.
 */
class EditorScrollRestoreController(private val application: Application) : Application.ActivityLifecycleCallbacks {
    private data class ViewportSnapshot(val documentKey: String, val scrollY: Int)

    private val snapshots = LinkedHashMap<String, ViewportSnapshot>(4, 0.75f, true)
    private var lastEditor: WeakReference<NativeEditorView>? = null
    private var lastActivity: WeakReference<Activity>? = null
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
                scheduleRestore(editor)
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
    }

    private fun scheduleRestore(editor: NativeEditorView) {
        val key = documentKey(editor)
        if (key.isBlank()) return
        val snapshot = snapshots[key] ?: return
        val target = snapshot.scrollY

        editor.postOnAnimation {
            if (documentKey(editor) != key || lastEditor?.get() !== editor) return@postOnAnimation

            // First restore the exact viewport that the user left.
            editor.scrollTo(editor.scrollX, target)

            // Then place the caret at the start of the first visible line. The caret
            // is intentionally changed in this experiment: it is now inside the
            // restored viewport, so Android has no reason to auto-scroll elsewhere.
            val layout = editor.layout ?: return@postOnAnimation
            val safeY = target.coerceIn(0, (layout.height - editor.height).coerceAtLeast(0))
            val visibleLine = layout.getLineForVertical(safeY)
            val visibleOffset = layout.getLineStart(visibleLine).coerceIn(0, editor.length())
            editor.setSelection(visibleOffset)
            EditorDiagnosticLog.log("SCROLL", "cursor-in-viewport restore scrollY=$target cursor=$visibleOffset")
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
        while (snapshots.size > 4) {
            val iterator = snapshots.entries.iterator()
            iterator.next()
            iterator.remove()
        }
    }
}

class ClipNestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EditorScrollRestoreController(this).install()
    }
}
