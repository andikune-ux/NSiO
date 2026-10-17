package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.DownloadItem
import com.example.data.model.MediaItem
import com.example.data.model.Profile
import com.example.data.model.ProfileSettings
import com.example.data.model.SourcePlaylist

@Database(
    entities = [MediaItem::class, Profile::class, ProfileSettings::class, DownloadItem::class, SourcePlaylist::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun profileDao(): ProfileDao
    abstract fun downloadDao(): DownloadDao
    abstract fun sourcePlaylistDao(): SourcePlaylistDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE media_items ADD COLUMN playlistId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE media_items ADD COLUMN sourceUrl TEXT NOT NULL DEFAULT ''")
                database.execSQL("CREATE TABLE IF NOT EXISTS source_playlists (id TEXT NOT NULL PRIMARY KEY, profileId TEXT NOT NULL, name TEXT NOT NULL, url TEXT NOT NULL, sourceType TEXT NOT NULL DEFAULT 'LINK', itemCount INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_source_playlists_profileId ON source_playlists(profileId)")
            }
        }
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "net_stream_io.db"
                ).addMigrations(MIGRATION_1_2).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
