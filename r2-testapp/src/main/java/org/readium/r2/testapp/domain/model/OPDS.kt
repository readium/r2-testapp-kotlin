package org.readium.r2.testapp.domain.model

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = OPDS.TABLE_NAME)
data class OPDS(
        @PrimaryKey
        @ColumnInfo(name = ID)
        var id: Long? = null,
        @ColumnInfo(name = TITLE)
        var title: String,
        @ColumnInfo(name = HREF)
        var href: String,
        @ColumnInfo(name = TYPE)
        var type: Int
) : Parcelable {
    companion object {

        const val TABLE_NAME = "OPDS"
        const val ID = "ID"
        const val TITLE = "TITLE"
        const val HREF = "HREF"
        const val TYPE = "TYPE"
    }
}