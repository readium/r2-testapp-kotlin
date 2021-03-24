package org.readium.r2.testapp.catalogs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.readium.r2.testapp.db.BookDatabase
import org.readium.r2.testapp.domain.model.OPDS

class CatalogViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: OpdsRepository

    init {
        val opdsDao = BookDatabase.getDatabase(application).opdsDao()
        repository = OpdsRepository(opdsDao)
    }

    val opds = repository.getOpdsFromDatabase()

    fun insertOpds(opds: OPDS) = viewModelScope.launch {
        repository.insertOpds(opds)
    }

    fun deleteOpds(id: Long) = viewModelScope.launch {
        repository.deleteOpds(id)
    }
}