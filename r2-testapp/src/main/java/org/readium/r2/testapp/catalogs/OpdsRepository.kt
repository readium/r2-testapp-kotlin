package org.readium.r2.testapp.catalogs


import androidx.lifecycle.LiveData
import org.readium.r2.testapp.db.OpdsDao
import org.readium.r2.testapp.domain.model.OPDS

class OpdsRepository(private val opdsDao: OpdsDao) {

    suspend fun insertOpds(opds: OPDS): Long {
        return opdsDao.insertOpds(opds)
    }

    fun getOpdsFromDatabase(): LiveData<List<OPDS>> = opdsDao.getOpdsModels()

    suspend fun deleteOpds(id: Long) = opdsDao.deleteOpds(id)
}