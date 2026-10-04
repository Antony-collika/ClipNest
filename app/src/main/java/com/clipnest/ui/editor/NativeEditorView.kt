    }

    fun setEditorBackgroundColor(color: Int) {
        setBackgroundColor(color)
    }

    fun setEditorTextColor(color: Int) {
        if (appliedEditorTextColor == color) return
        appliedEditorTextColor = color
        setTextColor(color)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            textCursorDrawable = GradientDrawable().apply { setColor(color); setSize(dp(2), dp(24)) }
        }
        invalidate()
    }