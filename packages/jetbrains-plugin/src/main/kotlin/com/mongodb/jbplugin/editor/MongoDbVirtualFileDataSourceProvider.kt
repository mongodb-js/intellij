package com.mongodb.jbplugin.editor

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.localDataSource
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.util.VirtualFileDataSourceProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

val PsiFile.dataSource: LocalDataSource?
    get() =
        MongoDbVirtualFileDataSourceProvider()
            .getDataSource(
                project,
                virtualFile,
            )?.localDataSource

/**
 * Returns the data source, if attached to the editor through the MongoDB Plugin.
 */
class MongoDbVirtualFileDataSourceProvider : VirtualFileDataSourceProvider() {
    object Keys {
        internal val attachedDataSource: Key<LocalDataSource> = Key.create("com.mongodb.jbplugin.AttachedDataSource")
    }

    override fun getDataSource(
        project: Project,
        file: VirtualFile,
    ): DbDataSource? {
        val facade = DbPsiFacade.getInstance(project)
        val attachedDataSource = file.getUserData(Keys.attachedDataSource) ?: return null

        return facade.findDataSource(attachedDataSource.uniqueId)
    }
}
