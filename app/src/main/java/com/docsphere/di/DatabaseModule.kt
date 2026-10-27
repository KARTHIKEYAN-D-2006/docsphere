package com.docsphere.di

import android.content.Context
import androidx.room.Room
import com.docsphere.data.local.ChunkDao
import com.docsphere.data.local.DocSphereDatabase
import com.docsphere.data.local.DocumentDao
import com.docsphere.data.local.DocumentFieldDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DocSphereDatabase {
        return Room.databaseBuilder(
            context,
            DocSphereDatabase::class.java,
            "docsphere.db"
        )
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(
                DocSphereDatabase.MIGRATION_2_3,
                DocSphereDatabase.MIGRATION_3_4,
                DocSphereDatabase.MIGRATION_4_5,
                DocSphereDatabase.MIGRATION_5_6,
                DocSphereDatabase.MIGRATION_6_7,
                DocSphereDatabase.MIGRATION_1_3,
                DocSphereDatabase.MIGRATION_1_4,
                DocSphereDatabase.MIGRATION_2_4,
                DocSphereDatabase.MIGRATION_1_5,
                DocSphereDatabase.MIGRATION_2_5,
                DocSphereDatabase.MIGRATION_3_5,
                DocSphereDatabase.MIGRATION_1_6,
                DocSphereDatabase.MIGRATION_2_6,
                DocSphereDatabase.MIGRATION_3_6,
                DocSphereDatabase.MIGRATION_4_6,
                DocSphereDatabase.MIGRATION_1_7,
                DocSphereDatabase.MIGRATION_2_7,
                DocSphereDatabase.MIGRATION_3_7,
                DocSphereDatabase.MIGRATION_4_7,
                DocSphereDatabase.MIGRATION_5_7,
                DocSphereDatabase.MIGRATION_7_8,
                DocSphereDatabase.MIGRATION_8_9
            )
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onOpen(db)
                    // Ensure legacy chat_messages table and old conversation records are completely erased
                    db.execSQL("DROP TABLE IF EXISTS `chat_messages`")
                }
            })
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideDocumentDao(database: DocSphereDatabase): DocumentDao {
        return database.documentDao()
    }

    @Provides
    fun provideChunkDao(database: DocSphereDatabase): ChunkDao {
        return database.chunkDao()
    }

    @Provides
    fun provideDocumentFieldDao(database: DocSphereDatabase): DocumentFieldDao {
        return database.documentFieldDao()
    }

    @Provides
    fun provideChunkEmbeddingDao(database: DocSphereDatabase): com.docsphere.data.local.ChunkEmbeddingDao {
        return database.chunkEmbeddingDao()
    }
}

