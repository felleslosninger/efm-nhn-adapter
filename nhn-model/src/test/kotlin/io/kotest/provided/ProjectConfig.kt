package io.kotest.provided

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.engine.concurrency.SpecExecutionMode
import io.kotest.engine.coroutines.CoroutineDispatcherFactory
import io.kotest.engine.coroutines.ThreadPerSpecCoroutineContextFactory

/**
 * Unit tests (surefire) run several specs at the same time. Surefire turns this on with the system property
 * `nhn.test.parallel`, see the parent pom. Tests started without the property run one spec at a time. Tests inside a
 * spec always run one at a time.
 */
class ProjectConfig : AbstractProjectConfig() {
    private val parallel = System.getProperty("nhn.test.parallel").toBoolean()

    override val specExecutionMode =
        if (parallel) SpecExecutionMode.LimitedConcurrency(Runtime.getRuntime().availableProcessors())
        else SpecExecutionMode.Sequential

    // By default Kotest runs every spec on one thread, so blocking specs would not overlap
    override val coroutineDispatcherFactory: CoroutineDispatcherFactory? =
        if (parallel) ThreadPerSpecCoroutineContextFactory else null
}
