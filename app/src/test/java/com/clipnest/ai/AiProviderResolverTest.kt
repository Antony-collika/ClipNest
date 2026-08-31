package com.clipnest.ai

import kotlin.test.Test
import kotlin.test.assertSame

class AiProviderResolverTest {
    @Test
    fun resolvesConfiguredProvider() {
        val gemini = object : AiProvider {
            override suspend fun generate(request: AiRequest): Result<AiResponse> = Result.success(AiResponse("gemini"))
        }
        val vercel = object : AiProvider {
            override suspend fun generate(request: AiRequest): Result<AiResponse> = Result.success(AiResponse("vercel"))
        }
        val resolver = AiProviderResolver(gemini, vercel)

        assertSame(gemini, resolver.resolve(AiProviderType.GEMINI))
        assertSame(vercel, resolver.resolve(AiProviderType.VERCEL))
    }
}
