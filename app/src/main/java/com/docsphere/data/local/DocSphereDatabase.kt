package com.docsphere.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DocumentRoomEntity::class,
        DocumentFtsEntity::class,
        ChunkRoomEntity::class,
        ChunkFtsEntity::class,
        DocumentFieldRoomEntity::class,
        ChunkEmbeddingEntity::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class DocSphereDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun chunkDao(): ChunkDao
    abstract fun documentFieldDao(): DocumentFieldDao
    abstract fun chunkEmbeddingDao(): ChunkEmbeddingDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `documents_fts` USING FTS4(`displayName` TEXT NOT NULL, `extractedText` TEXT NOT NULL, content=`documents`)")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_documents_fts_BEFORE_UPDATE BEFORE UPDATE ON `documents` BEGIN DELETE FROM `documents_fts` WHERE `docid`=OLD.`rowid`; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_documents_fts_BEFORE_DELETE BEFORE DELETE ON `documents` BEGIN DELETE FROM `documents_fts` WHERE `docid`=OLD.`rowid`; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_documents_fts_AFTER_UPDATE AFTER UPDATE ON `documents` BEGIN INSERT INTO `documents_fts`(`docid`, `displayName`, `extractedText`) VALUES (NEW.`rowid`, NEW.`displayName`, NEW.`extractedText`); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_documents_fts_AFTER_INSERT AFTER INSERT ON `documents` BEGIN INSERT INTO `documents_fts`(`docid`, `displayName`, `extractedText`) VALUES (NEW.`rowid`, NEW.`displayName`, NEW.`extractedText`); END")
                db.execSQL("INSERT INTO `documents_fts`(`documents_fts`) VALUES('rebuild')")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `chunks` (`id` TEXT NOT NULL, `documentId` TEXT NOT NULL, `chunkIndex` INTEGER NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chunks_documentId` ON `chunks` (`documentId`)")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `chunks_fts` USING FTS4(`text` TEXT NOT NULL, content=`chunks`)")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chunks_fts_BEFORE_UPDATE BEFORE UPDATE ON `chunks` BEGIN DELETE FROM `chunks_fts` WHERE `docid`=OLD.`rowid`; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chunks_fts_BEFORE_DELETE BEFORE DELETE ON `chunks` BEGIN DELETE FROM `chunks_fts` WHERE `docid`=OLD.`rowid`; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chunks_fts_AFTER_UPDATE AFTER UPDATE ON `chunks` BEGIN INSERT INTO `chunks_fts`(`docid`, `text`) VALUES (NEW.`rowid`, NEW.`text`); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_chunks_fts_AFTER_INSERT AFTER INSERT ON `chunks` BEGIN INSERT INTO `chunks_fts`(`docid`, `text`) VALUES (NEW.`rowid`, NEW.`text`); END")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `document_fields` (
                        `id` TEXT NOT NULL,
                        `documentId` TEXT NOT NULL,
                        `fieldName` TEXT NOT NULL,
                        `fieldValue` TEXT NOT NULL,
                        `sourceChunkIndex` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_document_fields_documentId` ON `document_fields` (`documentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_document_fields_fieldName` ON `document_fields` (`fieldName`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add ocrStatus column: NULL = not an image / not yet attempted
                // "ok" = OCR succeeded, "failed:<reason>" = failed, "skipped" = too small/large
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `ocrStatus` TEXT DEFAULT NULL")
            }
        }

        // ── Compound migrations covering all upgrade paths ──────────────────────

        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `chat_messages` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `routingDecision` TEXT, `citationsJson` TEXT NOT NULL, PRIMARY KEY(`id`))")
                MIGRATION_2_3.migrate(db)
            }
        }

        val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_3.migrate(db)
                MIGRATION_3_4.migrate(db)
            }
        }

        val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
            }
        }

        val MIGRATION_1_5 = object : Migration(1, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_4.migrate(db)
                MIGRATION_4_5.migrate(db)
            }
        }

        val MIGRATION_2_5 = object : Migration(2, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_4.migrate(db)
                MIGRATION_4_5.migrate(db)
            }
        }

        val MIGRATION_3_5 = object : Migration(3, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_4.migrate(db)
                MIGRATION_4_5.migrate(db)
            }
        }

        val MIGRATION_1_6 = object : Migration(1, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_5.migrate(db)
                MIGRATION_5_6.migrate(db)
            }
        }

        val MIGRATION_2_6 = object : Migration(2, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_5.migrate(db)
                MIGRATION_5_6.migrate(db)
            }
        }

        val MIGRATION_3_6 = object : Migration(3, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_5.migrate(db)
                MIGRATION_5_6.migrate(db)
            }
        }

        val MIGRATION_4_6 = object : Migration(4, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_4_5.migrate(db)
                MIGRATION_5_6.migrate(db)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop chat_messages table to purge all stored chats and sensitive values
                db.execSQL("DROP TABLE IF EXISTS `chat_messages`")
            }
        }

        val MIGRATION_1_7 = object : Migration(1, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_6.migrate(db)
                MIGRATION_6_7.migrate(db)
            }
        }

        val MIGRATION_2_7 = object : Migration(2, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_6.migrate(db)
                MIGRATION_6_7.migrate(db)
            }
        }

        val MIGRATION_3_7 = object : Migration(3, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_6.migrate(db)
                MIGRATION_6_7.migrate(db)
            }
        }

        val MIGRATION_4_7 = object : Migration(4, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_4_6.migrate(db)
                MIGRATION_6_7.migrate(db)
            }
        }

        val MIGRATION_5_7 = object : Migration(5, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_6.migrate(db)
                MIGRATION_6_7.migrate(db)
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `document_fields` ADD COLUMN `confidence` TEXT NOT NULL DEFAULT 'VERIFIED'")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chunk_embeddings` (
                        `chunkId` TEXT NOT NULL,
                        `vector` TEXT NOT NULL,
                        PRIMARY KEY(`chunkId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chunk_embeddings_chunkId` ON `chunk_embeddings` (`chunkId`)")
            }
        }
    }
}

