package com.clipnest.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FtsSearchQueryTest {

    @Test
    fun vietnameseDiacriticsAreNormalizedAndPrefixMatched() {
        assertEquals(""nguyen"*", FtsSearchQuery.fromUserQuery("Nguyễn"))
        assertEquals(""nguy"*", FtsSearchQuery.fromUserQuery("nguy"))
    }

    @Test
    fun multipleKeywordsUseAndSemantics() {
        assertEquals(
            ""nguyen"* AND "van"*",
            FtsSearchQuery.fromUserQuery("Nguyễn văn")
        )
    }

    @Test
    fun punctuationBecomesSeparators() {
        assertEquals(
            ""clip"* AND "nest"*",
            FtsSearchQuery.fromUserQuery("clip-nest")
        )
    }

    @Test
    fun phraseSearchIsPreserved() {
        assertEquals(
            ""android database"",
            FtsSearchQuery.fromUserQuery(""Android database"")
        )
    }

    @Test
    fun orAndNotAreSupported() {
        assertEquals(
            ""android"* OR "kotlin"*",
            FtsSearchQuery.fromUserQuery("android OR kotlin")
        )
        assertEquals(
            ""android"* NOT "ios"*",
            FtsSearchQuery.fromUserQuery("android NOT ios")
        )
    }

    @Test
    fun lowercaseOperatorWordsRemainSearchTerms() {
        assertEquals(
            ""or"* AND "not"*",
            FtsSearchQuery.fromUserQuery("or not")
        )
    }

    @Test
    fun punctuationOnlyQueryIsSafe() {
        assertEquals("", FtsSearchQuery.fromUserQuery("... --- !!!"))
    }
}
