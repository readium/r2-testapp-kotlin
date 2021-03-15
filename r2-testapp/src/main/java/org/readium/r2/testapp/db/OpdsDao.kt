package org.readium.r2.testapp.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.readium.r2.testapp.domain.model.OPDS

@Dao
interface OpdsDao {

    /**
     * Inserts an OPDS
     * @param opds The OPDS model to insert
     * @return ID of the OPDS model that was added (primary key)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOpds(opds: OPDS): Long

    /**
     * Retrieve list of OPDS models based on OPDS model
     * @return List of OPDS models as LiveData
     */
    @Query("SELECT * FROM " + OPDS.TABLE_NAME + " WHERE " + OPDS.TITLE + " = :title AND " + OPDS.HREF + " = :href AND " + OPDS.TYPE + " = :type")
    fun getOpdsModels(title: String, href: String, type: Int): LiveData<List<OPDS>>

    /**
     * Retrieve list of all OPDS models
     * @return List of OPDS models as LiveData
     */
    @Query("SELECT * FROM " + OPDS.TABLE_NAME)
    fun getOpdsModels(): LiveData<List<OPDS>>

    /**
     * Deletes an OPDS model
     * @param id The id of the OPDS model to delete
     */
    @Query("DELETE FROM " + OPDS.TABLE_NAME + " WHERE " + OPDS.ID + " = :id")
    suspend fun deleteOpds(id: Long)
}