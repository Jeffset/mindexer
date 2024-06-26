package io.github.jeffset.mindexer

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun <T> Flow<T>.chunked(
    maxCount: Int
) = channelFlow {
    val mutex = Mutex()
    val buffer = mutableListOf<T>()
    collect {
        mutex.withLock {
            buffer.add(it)
            if (buffer.size >= maxCount) {
                send(buffer.toList())
                buffer.clear()
            }
        }
    }

    // Emit the remaining
    mutex.withLock {
        if (buffer.isNotEmpty()) {
            send(buffer.toList())
            buffer.clear()
        }
    }
}
