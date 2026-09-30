package com.clipnest.domain

import java.text.Normalizer

object SearchTextNormalizer {
    fun normalize(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase()
            .trim()
}
