package com.docsphere.data.local

import androidx.room.TypeConverter
import com.docsphere.data.model.SensitivityTag
import com.docsphere.data.model.SourceType

class Converters {

    @TypeConverter
    fun fromSourceType(value: SourceType): String = value.name

    @TypeConverter
    fun toSourceType(value: String): SourceType = SourceType.valueOf(value)

    @TypeConverter
    fun fromSensitivityTag(value: SensitivityTag): String = value.name

    @TypeConverter
    fun toSensitivityTag(value: String): SensitivityTag = SensitivityTag.valueOf(value)
}
