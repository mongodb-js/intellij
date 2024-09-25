package com.mongodb.jbplugin.editor

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.localDataSource
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.util.VirtualFileDataSourceProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.Dialect

private const val KEY_PREFIX = "com.mongodb.jbplugin"

val PsiFile.dataSource: LocalDataSource?
    get() = runCatching {
        MongoDbVirtualFileDataSourceProvider()
            .getDataSource(
                project,
                virtualFile,
            )?.localDataSource
    }.getOrNull()

val PsiFile.database: String?
    get() = runCatching {
        MongoDbVirtualFileDataSourceProvider()
            .getDatabase(
                virtualFile,
            )
    }.getOrNull()

val PsiFile.dialect: Dialect<PsiElement, Project>?
    get() = runCatching {
        MongoDbVirtualFileDataSourceProvider()
            .getDialect(
                virtualFile,
            )
    }.getOrNull()

/**
 * Returns the data source, if attached to the editor through the MongoDB Plugin.
 */
class MongoDbVirtualFileDataSourceProvider : VirtualFileDataSourceProvider() {
    object Keys {
        internal val attachedDataSource: Key<LocalDataSource> = Key.create(
            "$KEY_PREFIX.AttachedDataSource"
        )
        internal val attachedDatabase: Key<String> = Key.create("$KEY_PREFIX.AttachedDatabase")
        internal val attachedDialect: Key<Dialect<PsiElement, Project>?> = Key.create(
            "$KEY_PREFIX.AttachedDialect"
        )
        internal val attachedToolbar: Key<MdbJavaEditorToolbar> = Key.create(
            "$KEY_PREFIX.AttachedToolbar"
        )
    }

    override fun getDataSource(
        project: Project,
        file: VirtualFile,
    ): DbDataSource? {
        val facade = DbPsiFacade.getInstance(project)
        val attachedDataSource = file.getUserData(Keys.attachedDataSource) ?: return null

        return facade.findDataSource(attachedDataSource.uniqueId)
    }

    fun getDatabase(
        file: VirtualFile
    ): String? = file.getUserData(Keys.attachedDatabase)

    fun getDialect(
        file: VirtualFile
    ): Dialect<PsiElement, Project>? = file.getUserData(Keys.attachedDialect)
}
