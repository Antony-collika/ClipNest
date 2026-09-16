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
 * Experiment B restores the viewport and deliberately moves the caret to a text
 * offset inside that viewport. Android may subsequently restore the old selection
 * when focus is acquired, so the enforcement stays synchronized with pre-draw
 * until the user starts interacting with the editor.
 */
class EditorScrollRestoreController(private val application: Application) : Application.ActivityLifecycleCallbacks {
    private data class ViewportSnapshot(val documentKey: String, val scrollY: Int)

    private val snapshots = LinkedHashMap<String, ViewportSnapshot>(4, 0.75f, true)
    private var lastEditor: WeakReference<NativeEditorView>? = null
    private var lastActivity: WeakReference<Activity>? = null
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var restoreEditor: WeakReference<NativeEditorView>? = null
    private var restorePreDrawListener: ViewTreeObserver.OnPreDrawListener? = null

    fun install() {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        lastActivity = WeakReference(activity)
        installGlobalLayoutObserver(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        stopCaretEnforcement()
        captureCurrentEditor(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (lastActivity?.get() === activity) {
            removeGlobalLayoutObserver(activity)
            stopCaretEnforcement()
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
                // Capture the outgoing editor before replacing the identity. Do not
                // capture the incoming editor here: its scrollY is usually zero and
                // would overwrite the viewport snapshot we actually want to restore.
                captureCurrentEditor(activity)
                lastEditor = WeakReference(editor)
                scheduleRestoreAfterLayout(editor)
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

    private fun scheduleRestoreAfterLayout(editor: NativeEditorView) {
        val key = documentKey(editor)
        if (key.isBlank()) return
        val snapshot = snapshots[key] ?: return
        val target = snapshot.scrollY
        stopCaretEnforcement()

        val observer = editor.viewTreeObserver
        if (!observer.isAlive) return

        lateinit var preDrawListener: ViewTreeObserver.OnPreDrawListener
        preDrawListener = ViewTreeObserver.OnPreDrawListener {
            if (documentKey(editor) != key || lastEditor?.get() !== editor) {
                stopCaretEnforcement()
                return@OnPreDrawListener true
            }

            // A real touch means the user has taken control of the caret. From this
            // point on Android's normal selection/scroll behavior must be allowed.
            if (editor.isPressed) {
                stopCaretEnforcement()
                return@OnPreDrawListener true
            }

            val layout = editor.layout ?: return@OnPreDrawListener true
            val maxScroll = (layout.height - editor.height).coerceAtLeast(0)
            val restoredScrollY = target.coerceIn(0, maxScroll)

            // Always establish the viewport first. The selection operation below is
            // deliberately made while this exact viewport is active.
            if (editor.scrollY != restoredScrollY) {
                editor.scrollTo(editor.scrollX, restoredScrollY)
            }

            val visibleTop = restoredScrollY + editor.paddingTop
            val visibleBottom = (restoredScrollY + editor.height - editor.paddingBottom).coerceAtLeast(visibleTop)
            val targetY = visibleTop + (visibleBottom - visibleTop) / 2
            val targetLine = layout.getLineForVertical(targetY).coerceIn(0, layout.lineCount - 1)
            val targetOffset = layout.getLineStart(targetLine).coerceIn(0, editor.length())

            val currentLine = runCatching { layout.getLineForOffset(editor.selectionStart) }.getOrDefault(targetLine)
            val visibleTopLine = layout.getLineForVertical(visibleTop)
            val visibleBottomLine = layout.getLineForVertical(visibleBottom)
            val selectionInsideViewport = currentLine in visibleTopLine..visibleBottomLine

            if (!selectionInsideViewport) {
                editor.setSelection(targetOffset)
                // setSelection() can synchronously invoke bringPointIntoView().
                // Re-apply the restored viewport immediately so that Android cannot
                // turn the caret move into a scroll back to the old selection.
                editor.scrollTo(editor.scrollX, restoredScrollY)
                EditorDiagnosticLog.log("SCROLL", "viewport caret enforcement target=$targetOffset restoredScrollY=$restoredScrollY oldSelection=${editor.selectionStart}-${editor.selectionEnd}")
            }
            true
        }
        restoreEditor = WeakReference(editor)
        restorePreDrawListener = preDrawListener
        observer.addOnPreDrawListener(preDrawListener)
    }

    private fun stopCaretEnforcement() {
        val listener = restorePreDrawListener ?: return
        val editor = restoreEditor?.get()
        val observer = editor?.viewTreeObserver
        if (observer?.isAlive == true) observer.removeOnPreDrawListener(listener)
        restorePreDrawListener = null
        restoreEditor = null
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
