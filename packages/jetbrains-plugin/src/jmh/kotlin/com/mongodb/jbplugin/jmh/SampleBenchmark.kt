package com.mongodb.jbplugin.jmh

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.infra.Blackhole

/**
 * Sample benchmark, does not do anything useful.
 */
open class SampleBenchmark {
    @Benchmark
    fun init(bh: Blackhole) {
        bh.consume(1)
        // Do nothing
    }
}
