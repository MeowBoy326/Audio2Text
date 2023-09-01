package com.example.audio2text

class DictionaryRepository(private val dictionaryDao: DictionaryDao) {
    fun mapEntityToItem(entity: Dictionary): DictionaryItem {
        return DictionaryItem(
            id = entity.id,
            name = entity.name,
            fileRelativePath = entity.fileRelativePath,
            isDownloadComplete = false,
            isDownloading = false,
            isFailed = false,
            progress = 0
        )
    }

    fun mapItemToEntity(item: DictionaryItem): Dictionary {
        return Dictionary(
            id = item.id,
            name = item.name,
            isDownloaded = item.isDownloadComplete,
            fileRelativePath = item.fileRelativePath
        )
    }

    fun updateDictionaryState(id: String, action: (Dictionary) -> Unit) {
        val entity = dictionaryDao.getDictionaryById(id)
        val updatedEntity = entity.apply { this?.let { action(it) } }
        if (updatedEntity != null) {
            dictionaryDao.update(updatedEntity)
        }
    }

    fun updateDatabaseEntities(updatedStates: List<DictionaryItem>) {
        val entitiesToUpdate = updatedStates.map { mapItemToEntity(it) }
        dictionaryDao.updateAll(entitiesToUpdate)
    }
}