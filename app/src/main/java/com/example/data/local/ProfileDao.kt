package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Profile
import com.example.data.model.ProfileSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY isDefault DESC, createdAt ASC")
    fun getAllProfiles(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles ORDER BY isDefault DESC, createdAt ASC")
    suspend fun getAllProfilesList(): List<Profile>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): Profile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: Profile)

    @Update
    suspend fun updateProfile(profile: Profile)

    @Delete
    suspend fun deleteProfile(profile: Profile)

    @Query("SELECT * FROM profile_settings WHERE profileId = :profileId LIMIT 1")
    suspend fun getSettings(profileId: String): ProfileSettings?

    @Query("SELECT * FROM profile_settings WHERE profileId = :profileId LIMIT 1")
    fun getSettingsFlow(profileId: String): Flow<ProfileSettings?>

    @Query("SELECT * FROM profile_settings")
    suspend fun getAllSettingsList(): List<ProfileSettings>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: ProfileSettings)

    @Update
    suspend fun updateSettings(settings: ProfileSettings)
}
