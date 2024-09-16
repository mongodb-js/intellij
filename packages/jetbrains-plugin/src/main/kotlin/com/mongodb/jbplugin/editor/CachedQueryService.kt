package com.mongodb.jbplugin.editor

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.isConnected
import com.mongodb.jbplugin.accessadapter.slice.BuildInfo
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasTargetCluster
import io.github.z4kn4fein.semver.Version
import kotlinx.coroutines.CoroutineScope

/**
 * Attempts to parse a query for a PsiElement if there is one, given the dialect of the current
 * file. Caches the result inside the PsiElement metadata and only reparses the query if that
 * element or it's children change.
 *
 * It might return null if there is no query to parse.
 *
 * @property coroutineScope
 */
@Service(Service.Level.PROJECT)
class CachedQueryService(
    val coroutineScope: CoroutineScope
) {
    private val queryCacheKey = Key.create<CachedValue<Node<PsiElement>>>("QueryCache")

    fun queryAt(expression: PsiElement, forceParsing: Boolean = false): Node<PsiElement>? {
        val dataSource = expression.containingFile.dataSource
        val dialect = expression.containingFile.dialect ?: return null
        if (!dialect.parser.isCandidateForQuery(expression)) {
            return null
        }

        val attachment = dialect.parser.attachment(expression)
        val psiManager = PsiManager.getInstance(expression.project)
        if (!forceParsing && !psiManager.areElementsEquivalent(expression, attachment)) {
            return null
        }

        val cacheManager = CachedValuesManager.getManager(attachment.project)
        attachment.getUserData(queryCacheKey)?.let {
            return decorateWithMetadata(dataSource, attachment.getUserData(queryCacheKey)!!.value)
        }

        val cachedValue = cacheManager.createCachedValue {
            val parsedAst = dialect.parser.parse(expression)
            CachedValueProvider.Result.create(parsedAst, attachment)
        }

        attachment.putUserData(queryCacheKey, cachedValue)
        return decorateWithMetadata(dataSource, attachment.getUserData(queryCacheKey)!!.value)
    }

    private fun decorateWithMetadata(dataSource: LocalDataSource?, query: Node<PsiElement>): Node<PsiElement> {
        val queryWithDb = query.source.containingFile.database?.let {
            query.queryWithOverwrittenDatabase(it)
        } ?: query

        return runCatching {
            if (dataSource != null && dataSource.isConnected()) {
                val readModel = query.source.project.getService(DataGripBasedReadModelProvider::class.java)
                val buildInfo = readModel.slice(dataSource, BuildInfo.Slice)

                queryWithDb.withTargetCluster(
                    HasTargetCluster(Version.parse(buildInfo.version))
                )
            } else {
                queryWithDb
            }
        }.getOrDefault(queryWithDb)
    }
}