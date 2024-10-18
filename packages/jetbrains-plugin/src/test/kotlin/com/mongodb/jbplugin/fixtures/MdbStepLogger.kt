package com.mongodb.jbplugin.fixtures

import com.intellij.remoterobot.stepsProcessing.StepProcessor
import com.intellij.remoterobot.stepsProcessing.log
import com.intellij.remoterobot.utils.Color
import com.intellij.remoterobot.utils.color

class MdbStepLogger(private val indentVal: String = "------ ") : StepProcessor {
    private val ignorableStepTitlesForExceptionLogging = setOf(
        "Wait until Gradle project is in sync",
        "Search 'Idea frame'"
    )
    private var indent = ThreadLocal.withInitial { 0 }

    private fun indents() = buildString {
        repeat(indent.get()) { append(indentVal) }
    }

    override fun doBeforeStep(stepTitle: String) {
        log.info(indents() + stepTitle)
        indent.set(indent.get().plus(1))
    }

    override fun doOnSuccess(stepTitle: String) {
    }

    override fun doOnFail(stepTitle: String, e: Throwable) {
        if (ignorableStepTitlesForExceptionLogging.any { stepTitle.contains(it) }) {
            log.warn(
                "${indents()}Failed on step: $stepTitle (${getClassFileNameAndMethod()})".color(
                    Color.RED
                )
            )
        } else {
            log.warn(
                "${indents()}Failed on step: $stepTitle (${getClassFileNameAndMethod()})".color(
                    Color.RED
                ),
                e
            )
        }
    }

    override fun doAfterStep(stepTitle: String) {
        indent.set(indent.get().minus(1))
    }

    private fun getClassFileNameAndMethod(): String {
        return Thread.currentThread().stackTrace[3]?.let { "${it.fileName}_${it.methodName}" }
            ?: "---"
    }
}
