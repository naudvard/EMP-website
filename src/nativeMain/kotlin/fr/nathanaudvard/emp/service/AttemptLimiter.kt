package fr.nathanaudvard.emp.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Instant

// Failed attempts of the latest window, per client and in all, so that codes cannot be swept. Kept in memory:
// a restart forgets them, which a code's short life makes harmless
class AttemptLimiter(private val perClient: Int, private val total: Int, private val window: Duration) {
    private val mutex = Mutex()
    private val failures = ArrayDeque<Pair<String, Instant>>()

    suspend fun allows(client: String, now: Instant): Boolean = mutex.withLock {
        while (failures.firstOrNull()?.let { it.second <= now - window } == true) failures.removeFirst()
        failures.size < total && failures.count { it.first == client } < perClient
    }

    suspend fun fail(client: String, now: Instant) = mutex.withLock { failures.addLast(client to now) }
}
