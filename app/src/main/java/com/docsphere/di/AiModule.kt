package com.docsphere.di

import android.content.Context
import com.docsphere.ai.AiRouter
import com.docsphere.ai.offline.GeminiNanoEngine
import com.docsphere.ai.online.GeminiApiEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideGeminiNanoEngine(
        @ApplicationContext context: Context,
        knowledgeBase: com.docsphere.ai.offline.OfflineKnowledgeBase,
        reasoningEngine: com.docsphere.ai.offline.DocumentReasoningEngine,
        modelManager: com.docsphere.ai.offline.OfflineModelManager,
        identityResolver: com.docsphere.data.repository.UserIdentityResolver,
        preferences: com.docsphere.data.preferences.DocSpherePreferences
    ): GeminiNanoEngine = GeminiNanoEngine(context, knowledgeBase, reasoningEngine, modelManager, identityResolver, preferences)

    @Provides
    @Singleton
    fun provideGeminiApiEngine(
        okHttpClient: OkHttpClient
    ): GeminiApiEngine = GeminiApiEngine(okHttpClient)

    @Provides
    @Singleton
    fun provideAiRouter(
        @ApplicationContext context: Context,
        nanoEngine: GeminiNanoEngine,
        apiEngine: GeminiApiEngine,
        modelManager: com.docsphere.ai.offline.OfflineModelManager
    ): AiRouter = AiRouter(context, nanoEngine, apiEngine, modelManager)
}
