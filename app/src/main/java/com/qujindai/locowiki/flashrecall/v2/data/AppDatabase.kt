package com.qujindai.locowiki.flashrecall.v2.data

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        EntityEntity::class,
        AliasEntity::class,
        SourceEntity::class,
        FactEntity::class,
        FactSearchFts::class,
        ImportBatchEntity::class,
        LatencyTraceEntity::class,
        MeetingSessionEntity::class,
        UtteranceEntity::class,
        QueryRecordEntity::class,
        EvidenceSnapshotEntity::class,
        SpeakerProfileEntity::class,
        SpeakerEnrollmentSampleEntity::class,
        SpeakerEmbeddingEntity::class,
        SpeakerClusterEntity::class,
        QuestionThreadEntity::class,
        QuestionThreadUtteranceEntity::class,
    ],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entityDao(): EntityDao
    abstract fun aliasDao(): AliasDao
    abstract fun sourceDao(): SourceDao
    abstract fun factDao(): FactDao
    abstract fun ftsDao(): FtsDao
    abstract fun importBatchDao(): ImportBatchDao
    abstract fun latencyDao(): LatencyDao
    abstract fun meetingSessionDao(): MeetingSessionDao
    abstract fun utteranceDao(): UtteranceDao
    abstract fun queryRecordDao(): QueryRecordDao
    abstract fun evidenceSnapshotDao(): EvidenceSnapshotDao
    abstract fun speakerProfileDao(): SpeakerProfileDao
    abstract fun speakerEmbeddingDao(): SpeakerEmbeddingDao
    abstract fun speakerClusterDao(): SpeakerClusterDao
    abstract fun questionThreadDao(): QuestionThreadDao
    abstract fun questionThreadUtteranceDao(): QuestionThreadUtteranceDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "flash_recall_v3.db",
            )
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
                .also { instance = it }
        }
    }
}
