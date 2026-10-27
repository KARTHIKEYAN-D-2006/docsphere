package com.docsphere.di

import android.content.Context
import androidx.work.WorkManager
import com.docsphere.data.extractor.TextExtractor
import com.docsphere.data.preferences.DocSpherePreferences
import com.docsphere.data.scanner.LocalStorageScanner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideLocalStorageScanner(
        @ApplicationContext context: Context,
        preferences: DocSpherePreferences
    ): LocalStorageScanner = LocalStorageScanner(context, preferences)

    @Provides
    @Singleton
    fun provideTextExtractor(
        @ApplicationContext context: Context
    ): TextExtractor = TextExtractor(context)

    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context
    ): WorkManager = WorkManager.getInstance(context)
}
