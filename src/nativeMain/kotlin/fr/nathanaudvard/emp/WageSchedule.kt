package fr.nathanaudvard.emp

import fr.nathanaudvard.emp.config.AppConfig
import fr.nathanaudvard.emp.domain.usecase.ComputationResult
import fr.nathanaudvard.emp.domain.usecase.GetAllianceSettingsUseCase
import fr.nathanaudvard.emp.domain.usecase.RunScheduledComputationUseCase
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.log
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// The daily computation, at the time the chiefs set. Woken each minute at least, for a new time to apply at once
suspend fun Application.scheduleWages() {
    val getSettings: GetAllianceSettingsUseCase = dependencies.resolve()
    val run: RunScheduledComputationUseCase = dependencies.resolve()
    val clock: Clock = dependencies.resolve()
    val tag = dependencies.resolve<AppConfig>().alliance.tag

    val job = launch {
        // The database is first read after a minute: an application stopped at once never reaches it
        val started = clock.now()
        var due: Instant? = null
        while (true) {
            delay(due?.let { minOf(it - clock.now(), 1.minutes).coerceAtLeast(Duration.ZERO) } ?: 1.minutes)
            try {
                val now = clock.now()
                val settings = getSettings(tag)
                val reached = now >= (due ?: settings.nextComputation(started))
                // Moved on before computing: a failure must not run it again
                due = settings.nextComputation(now)
                if (!reached) continue
                when (val result = run(tag)) {
                    null -> log.info("Scheduled wages of $tag skipped: computed within the minute")
                    is ComputationResult.Computed -> log.info("Scheduled wages of $tag computed")
                    is ComputationResult.Failed -> log.error("Scheduled wages of $tag failed: ${result.failure}", result.cause)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Wage schedule of $tag", e)
                delay(1.minutes)
            }
        }
    }
    // A query under way ends before the database is closed
    monitor.subscribe(ApplicationStopping) {
        job.cancel()
        runBlocking { job.join() }
    }
}
