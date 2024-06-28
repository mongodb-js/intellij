package com.mongodb.jbplugin.editor

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.util.VirtualFileDataSourceProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile

class MongoDbVirtualFileDataSourceProvider : VirtualFileDataSourceProvider() {
    /**
     * This needs to be synchronised with the EditorToolbarDecorator field with the same name.
     *
     * @see EditorToolbarDecorator
     */
    private val attachedDataSource: Key<LocalDataSource> = Key.create("com.mongodb.jbplugin.AttachedDataSource")

    override fun getDataSource(
        project: Project,
        file: VirtualFile,
    ): DbDataSource? {
        val facade = DbPsiFacade.getInstance(project)
        val attachedDataSource = file.getUserData(attachedDataSource) ?: return null

        return facade.findDataSource(attachedDataSource.uniqueId)
    }
}
