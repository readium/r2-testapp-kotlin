package org.readium.r2.testapp.catalogs


import androidx.lifecycle.LiveData
import org.readium.r2.testapp.db.CatalogDao
import org.readium.r2.testapp.domain.model.OPDS

class OpdsRepository(private val catalogDao: CatalogDao) {

    suspend fun insertOpds(opds: OPDS): Long {
        return catalogDao.insertOpds(opds)
    }

    fun getOpdsFromDatabase(): LiveData<List<OPDS>> = catalogDao.getOpdsModels()

    suspend fun deleteOpds(id: Long) = catalogDao.deleteOpds(id)
}