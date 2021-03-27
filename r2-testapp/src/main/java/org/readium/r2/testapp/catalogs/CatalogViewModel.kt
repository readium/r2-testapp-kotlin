package org.readium.r2.testapp.catalogs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import nl.komponents.kovenant.Promise
import org.readium.r2.opds.OPDS1Parser
import org.readium.r2.opds.OPDS2Parser
import org.readium.r2.shared.opds.ParseData
import org.readium.r2.testapp.db.BookDatabase
import org.readium.r2.testapp.domain.model.OPDS
import java.net.MalformedURLException
import java.net.URL

class CatalogViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: OpdsRepository
    val parseData = MutableLiveData<ParseData>()

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

    fun parseOpds(opds: OPDS, error: () -> Unit) {
        var parsePromise: Promise<ParseData, Exception>? = null
        opds.href.let {
            try {
                parsePromise = if (opds.type == 1) {
                    OPDS1Parser.parseURL(URL(it))
                } else {
                    OPDS2Parser.parseURL(URL(it))
                }
            } catch (e: MalformedURLException) {
                error()
            }
        }
        parsePromise?.success {
            parseData.postValue(it)
        }
    }
}