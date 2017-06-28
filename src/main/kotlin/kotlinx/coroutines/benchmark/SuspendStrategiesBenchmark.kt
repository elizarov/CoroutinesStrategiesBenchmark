/*
 * Copyright (c) 2014, Oracle America, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package kotlinx.coroutines.benchmark

import org.openjdk.jmh.annotations.*
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.experimental.intrinsics.suspendCoroutineOrReturn

const val SIZE = 65536
val indexRange = 0..SIZE - 4 step 4

var expectedSum: Int = 0

val bytes = ByteArray(SIZE).apply {
    val r = Random(1)
    var sum = 0
    for (i in indexRange) {
        val x = r.nextInt()
        sum += x
        this[i] = x.toByte()
        this[i + 1] = (x shr 8).toByte()
        this[i + 2] = (x shr 16).toByte()
        this[i + 3] = (x shr 24).toByte()
    }
    expectedSum = sum
}

private inline val Byte.ui: Int get() = this.toInt() and 0xff

@Fork(1)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
open class NoSuspendBenchmark {
    var index = 0

    // --------------------- NoSuspendPlain ---------------------

    fun readByteNoSuspendPlain(): Byte {
        return bytes[index++]
    }

    fun readIntNoSuspendPlain(): Int {
        return readByteNoSuspendPlain().ui or
            (readByteNoSuspendPlain().ui shl 8) or
            (readByteNoSuspendPlain().ui shl 16) or
            (readByteNoSuspendPlain().ui shl 24)
    }

    fun readSumNoSuspendPlain(): Int {
        var sum = 0
        for (i in indexRange) sum += readIntNoSuspendPlain()
        return sum
    }

    @Benchmark
    fun testNoSuspendPlain() {
        index = 0
        val sum = readSumNoSuspendPlain()
        check(sum == expectedSum)
    }
    
    
    // --------------------- NoSuspendSM ---------------------

    fun readByteNoSuspendSM(): Byte {
        return bytes[index++]
    }

    fun readIntNoSuspendSM(): Int {
        var label = 0
        var acc = 0
        var v: Byte = 0
        while (true) {
            when (label) {
                0 -> {
                    label = 1
                    v = readByteNoSuspendSM()
                }
                1 -> {
                    acc = v.ui
                    label = 2
                    v = readByteNoSuspendSM()
                }
                2 -> {
                    acc = (v.ui shl 8) or acc
                    label = 3
                    v = readByteNoSuspendSM()
                }
                3 -> {
                    acc = (v.ui shl 16) or acc
                    label = 4
                    v = readByteNoSuspendSM()
                }
                4 -> {
                    acc = (v.ui shl 24) or acc
                    return acc
                }
            }
        }
    }

    fun readSumNoSuspendSM(): Int {
        var label = 0
        var sum = 0
        val iter = indexRange.iterator()
        var v = 0
        while (true) {
            when (label) {
                0 -> {
                    label = 1
                    if (!iter.hasNext()) return 0
                    iter.nextInt()
                    v = readIntNoSuspendSM()
                }
                1 -> {
                    sum += v
                    if (!iter.hasNext()) return sum
                    iter.nextInt()
                    v = readIntNoSuspendSM()
                }
            }
        }
    }

    @Benchmark
    fun testNoSuspendSM() {
        index = 0
        val sum = readSumNoSuspendSM()
        check(sum == expectedSum)
    }
    // --------------------- NoSuspendSMBoxed ---------------------

    fun readByteNoSuspendSMBoxed(): Byte? {
        return bytes[index++]
    }

    fun readIntNoSuspendSMBoxed(): Int? {
        var label = 0
        var acc = 0
        var v: Any? = null
        while (true) {
            when (label) {
                0 -> {
                    label = 1
                    v = readByteNoSuspendSMBoxed()
                }
                1 -> {
                    acc = (v as Byte).ui
                    label = 2
                    v = readByteNoSuspendSMBoxed()
                }
                2 -> {
                    acc = ((v as Byte).ui shl 8) or acc
                    label = 3
                    v = readByteNoSuspendSMBoxed()
                }
                3 -> {
                    acc = ((v as Byte).ui shl 16) or acc
                    label = 4
                    v = readByteNoSuspendSMBoxed()
                }
                4 -> {
                    acc = ((v as Byte).ui shl 24) or acc
                    return acc
                }
            }
        }
    }

    fun readSumNoSuspendSMBoxed(): Int? {
        var label = 0
        var sum = 0
        val iter = indexRange.iterator()
        var v: Any? = null
        while (true) {
            when (label) {
                0 -> {
                    label = 1
                    if (!iter.hasNext()) return 0
                    iter.nextInt()
                    v = readIntNoSuspendSMBoxed()
                }
                1 -> {
                    sum += v as Int
                    if (!iter.hasNext()) return sum
                    iter.nextInt()
                    v = readIntNoSuspendSMBoxed()
                }
            }
        }
    }

    @Benchmark
    fun testNoSuspendSMBoxed() {
        index = 0
        val sum = readSumNoSuspendSMBoxed()
        check(sum == expectedSum)
    }
}

@Fork(1)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
open class SuspendStrategiesBenchmark {
    @Param("1", "16", "256", "4096", "65536")
    var suspendEvery = 0

    var index = 0
    var scheduled: Runnable? = null

    private fun shallSuspend(): Boolean = (index + 1) % suspendEvery == 0

    private fun runTestBlocking(block: suspend () -> Int): Int {
        var result: Int? = null
        val cont = block.createCoroutine(object : Continuation<Int> {
            override val context: CoroutineContext get() = EmptyCoroutineContext
            override fun resume(value: Int) { result = value }
            override fun resumeWithException(exception: Throwable) { throw exception }
        })
        scheduled = Runnable { cont.resume(Unit) }
        while (true) {
            val cur = scheduled ?: break
            scheduled = null
            cur.run()
        }
        return result!!
    }

    // --------------------- Intrinsic ---------------------

    private suspend fun readByteSuspendIntrinsic(): Byte = suspendCoroutineOrReturn { cont ->
        scheduled = Runnable {
            cont.resume(bytes[index++])
        }
        COROUTINE_SUSPENDED
    }

    private suspend fun readByteIntrinsic(): Byte {
        // fast path -- no suspend
        if (!shallSuspend()) return bytes[index++]
        // slow path -- suspend
        return readByteSuspendIntrinsic()
    }

    private suspend fun readIntIntrinsic(): Int {
        return readByteIntrinsic().ui or
            (readByteIntrinsic().ui shl 8) or
            (readByteIntrinsic().ui shl 16) or
            (readByteIntrinsic().ui shl 24)
    }

    private suspend fun readSumIntrinsic(): Int {
        var sum = 0
        for (i in indexRange) sum += readIntIntrinsic()
        return sum
    }

    @Benchmark
    fun testIntrinsic() {
        index = 0
        val sum = runTestBlocking { readSumIntrinsic() }
        check(sum == expectedSum)
    }

    // --------------------- Normal ---------------------

    private suspend fun readByteSuspendNormal(): Byte = suspendCoroutine { cont ->
        scheduled = Runnable { cont.resume(bytes[index++]) }
    }

    private suspend fun readByteNormal(): Byte {
        // fast path -- no suspend
        if (!shallSuspend()) return bytes[index++]
        // slow path -- suspend
        return readByteSuspendNormal()
    }

    private suspend fun readIntNormal(): Int {
        return readByteNormal().ui or
            (readByteNormal().ui shl 8) or
            (readByteNormal().ui shl 16) or
            (readByteNormal().ui shl 24)
    }

    private suspend fun readSumNormal(): Int {
        var sum = 0
        for (i in indexRange) sum += readIntNormal()
        return sum
    }

    @Benchmark
    fun testNormal() {
        index = 0
        val sum = runTestBlocking { readSumNormal() }
        check(sum == expectedSum)
    }

    // --------------------- SimpleTag ---------------------

    private fun readByteSuspendSimpleTag(cont: Continuation<Byte>): Any? {
        scheduled = Runnable { cont.resume(bytes[index++]) }
        return COROUTINE_SUSPENDED
    }

    private fun readByteSimpleTag(cont: Continuation<Byte>): Any? {
        // fast path -- no suspend
        if (!shallSuspend()) return bytes[index++]
        // slow path -- suspend
        return readByteSuspendSimpleTag(cont)
    }

    private inner class ReadIntSimpleTagSM(private val completion: Continuation<Int>) : Continuation<Byte> {
        override val context: CoroutineContext = EmptyCoroutineContext
        override fun resumeWithException(exception: Throwable) { completion.resumeWithException(exception) }

        override fun resume(value: Byte) {
            val result = doResume(value)
            if (result === COROUTINE_SUSPENDED) return
            completion.resume(result as Int)
        }

        private var label = 0
        private var acc = 0

        fun doResume(value: Byte): Any? {
            var v: Any? = value
            while (true) {
                when (label) {
                    0 -> {
                        label = 1
                        v = readByteSimpleTag(this)
                        if (v === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    }
                    1 -> {
                        acc = (v as Byte).ui
                        label = 2
                        v = readByteSimpleTag(this)
                        if (v === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    }
                    2 -> {
                        acc = ((v as Byte).ui shl 8) or acc
                        label = 3
                        v = readByteSimpleTag(this)
                        if (v === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    }
                    3 -> {
                        acc = ((v as Byte).ui shl 16) or acc
                        label = 4
                        v = readByteSimpleTag(this)
                        if (v === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    }
                    4 -> {
                        acc = ((v as Byte).ui shl 24) or acc
                        return acc
                    }
                }
            }
        }
    }

    private fun readIntSimpleTag(cont: Continuation<Int>): Any? = ReadIntSimpleTagSM(cont).doResume(0)

    private inner class ReadSumSimpleTagSM(private val completion: Continuation<Int>): Continuation<Int> {
        override val context: CoroutineContext = EmptyCoroutineContext
        override fun resumeWithException(exception: Throwable) { completion.resumeWithException(exception) }

        override fun resume(value: Int) {
            val result = doResume(value)
            if (result === COROUTINE_SUSPENDED) return
            completion.resume(result as Int)
        }

        private var label = 0
        private var sum = 0
        private val iter = indexRange.iterator()

        fun doResume(value: Int): Any? {
            var v: Any? = value
            while (true) {
                when (label) {
                    0 -> {
                        label = 1
                        if (!iter.hasNext()) return 0
                        iter.nextInt()
                        v = readIntSimpleTag(this)
                        if (v === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    }
                    1 -> {
                        sum += v as Int
                        if (!iter.hasNext()) return sum
                        iter.nextInt()
                        v = readIntSimpleTag(this)
                        if (v === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    }
                }
            }
        }

    }

    private fun readSumSimpleTag(cont: Continuation<Int>): Any? = ReadSumSimpleTagSM(cont).doResume(0)

    @Benchmark
    fun testSimpleTag() {
        index = 0
        val sum = runTestBlocking {
            suspendCoroutineOrReturn<Int> { cont ->
                readSumSimpleTag(cont)
            }
        }
        check(sum == expectedSum)
    }
    
    // --------------------- SimpleSFun ---------------------

    interface ContinuationSFun<in T> : Continuation<T> {
        fun suspend()
    }

    private fun readByteSuspendSimpleSFun(cont: ContinuationSFun<Byte>): Byte {
        scheduled = Runnable { cont.resume(bytes[index++]) }
        cont.suspend()
        return 0
    }

    private fun readByteSimpleSFun(cont: ContinuationSFun<Byte>): Byte {
        // fast path -- no suspend
        if (!shallSuspend()) return bytes[index++]
        // slow path -- suspend
        return readByteSuspendSimpleSFun(cont)
    }

    private inner class ReadIntSimpleSFunSM(private val completion: ContinuationSFun<Int>) : ContinuationSFun<Byte> {
        override val context: CoroutineContext = EmptyCoroutineContext
        override fun resumeWithException(exception: Throwable) { completion.resumeWithException(exception) }

        override fun resume(value: Byte) {
            val result = doResume(value)
            if (suspended) return
            completion.resume(result)
        }

        override fun suspend() { suspended = true }

        private var suspended = false
        private var label = 0
        private var acc = 0

        fun doResume(value: Byte): Int {
            suspended = false
            var v = value
            while (true) {
                when (label) {
                    0 -> {
                        label = 1
                        v = readByteSimpleSFun(this)
                        if (suspended) { completion.suspend(); return 0 }
                    }
                    1 -> {
                        acc = v.ui
                        label = 2
                        v = readByteSimpleSFun(this)
                        if (suspended) { completion.suspend(); return 0 }
                    }
                    2 -> {
                        acc = (v.ui shl 8) or acc
                        label = 3
                        v = readByteSimpleSFun(this)
                        if (suspended) { completion.suspend(); return 0 }
                    }
                    3 -> {
                        acc = (v.ui shl 16) or acc
                        label = 4
                        v = readByteSimpleSFun(this)
                        if (suspended) { completion.suspend(); return 0 }
                    }
                    4 -> {
                        acc = (v.ui shl 24) or acc
                        return acc
                    }
                }
            }
        }
    }

    private fun readIntSimpleSFun(cont: ContinuationSFun<Int>): Int = ReadIntSimpleSFunSM(cont).doResume(0)

    private inner class ReadSumSimpleSFunSM(private val completion: ContinuationSFun<Int>): ContinuationSFun<Int> {
        override val context: CoroutineContext = EmptyCoroutineContext
        override fun resumeWithException(exception: Throwable) { completion.resumeWithException(exception) }

        override fun resume(value: Int) {
            val result = doResume(value)
            if (suspended) return
            completion.resume(result)
        }

        override fun suspend() { suspended = true }

        private var suspended = false
        private var label = 0
        private var sum = 0
        private val iter = indexRange.iterator()

        fun doResume(value: Int): Int {
            suspended = false
            var v = value
            while (true) {
                when (label) {
                    0 -> {
                        label = 1
                        if (!iter.hasNext()) return 0
                        iter.nextInt()
                        v = readIntSimpleSFun(this)
                        if (suspended) { completion.suspend(); return 0 }
                    }
                    1 -> {
                        sum += v
                        if (!iter.hasNext()) return sum
                        iter.nextInt()
                        v = readIntSimpleSFun(this)
                        if (suspended) { completion.suspend(); return 0 }
                    }
                }
            }
        }

    }

    private fun readSumSimpleSFun(cont: ContinuationSFun<Int>): Any? = ReadSumSimpleSFunSM(cont).doResume(0)

    private class AdapterSFun(private val delegate: Continuation<Int>) : ContinuationSFun<Int>, Continuation<Int> by delegate {
        var suspended = false

        override fun suspend() {
            suspended = false
        }
    }

    @Benchmark
    fun testSimpleSFun() {
        index = 0
        val sum = runTestBlocking {
            suspendCoroutineOrReturn<Int> { cont ->
                val adapter = AdapterSFun(cont)
                val result = readSumSimpleSFun(adapter)
                if (adapter.suspended) COROUTINE_SUSPENDED else result
            }
        }
        check(sum == expectedSum)
    }

    // --------------------- InplaceTag ---------------------

    private fun readByteSuspendInplaceTag(cont: Continuation<Byte>): Any? {
        scheduled = Runnable { cont.resume(bytes[index++]) }
        return COROUTINE_SUSPENDED
    }

    private fun readByteInplaceTag(cont: Continuation<Byte>): Any? {
        // fast path -- no suspend
        if (!shallSuspend()) return bytes[index++]
        // slow path -- suspend
        return readByteSuspendInplaceTag(cont)
    }

    private inner class ReadIntInplaceTagSM(private val completion: Continuation<Int>) : Continuation<Byte> {
        override val context: CoroutineContext = EmptyCoroutineContext
        override fun resumeWithException(exception: Throwable) { completion.resumeWithException(exception) }

        override fun resume(value: Byte) {
            v = value
            val result = readIntInplaceTag(this)
            if (result === COROUTINE_SUSPENDED) return
            completion.resume(result as Int)
        }

        @JvmField var label = 0
        @JvmField var acc = 0
        @JvmField var v: Any? = null
    }

    private fun readIntInplaceTag(cont: Continuation<*>): Any? {
        @Suppress("UNCHECKED_CAST")
        val sm = cont as? ReadIntInplaceTagSM ?: ReadIntInplaceTagSM(cont as Continuation<Int>)
        var v: Any? = sm.v
        while (true) {
            when (sm.label) {
                0 -> {
                    sm.label = 1
                    v = readByteInplaceTag(sm)
                    if (v === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                }
                1 -> {
                    sm.acc = (v as Byte).ui
                    sm.label = 2
                    v = readByteInplaceTag(sm)
                    if (v === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                }
                2 -> {
                    sm.acc = ((v as Byte).ui shl 8) or sm.acc
                    sm.label = 3
                    v = readByteInplaceTag(sm)
                    if (v === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                }
                3 -> {
                    sm.acc = ((v as Byte).ui shl 16) or sm.acc
                    sm.label = 4
                    v = readByteInplaceTag(sm)
                    if (v === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                }
                4 -> {
                    sm.acc = ((v as Byte).ui shl 24) or sm.acc
                    return sm.acc
                }
            }
        }
    }

    private inner class ReadSumInplaceTagSM(private val completion: Continuation<Int>): Continuation<Int> {
        override val context: CoroutineContext = EmptyCoroutineContext
        override fun resumeWithException(exception: Throwable) { completion.resumeWithException(exception) }

        override fun resume(value: Int) {
            v = value
            val result = readSumInplaceTag(this)
            if (result === COROUTINE_SUSPENDED) return
            completion.resume(result as Int)
        }

        @JvmField var label = 0
        @JvmField var sum = 0
        @JvmField val iter = indexRange.iterator()
        @JvmField var v: Any? = null
    }

    private fun readSumInplaceTag(cont: Continuation<*>): Any? {
        @Suppress("UNCHECKED_CAST")
        val sm = cont as? ReadSumInplaceTagSM ?: ReadSumInplaceTagSM(cont as Continuation<Int>)
        var v: Any? = sm.v
        while (true) {
            when (sm.label) {
                0 -> {
                    sm.label = 1
                    if (!sm.iter.hasNext()) return 0
                    sm.iter.nextInt()
                    v = readIntInplaceTag(sm)
                    if (v === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                }
                1 -> {
                    sm.sum += v as Int
                    if (!sm.iter.hasNext()) return sm.sum
                    sm.iter.nextInt()
                    v = readIntInplaceTag(sm)
                    if (v === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                }
            }
        }
    }

    @Benchmark
    fun testInplaceTag() {
        index = 0
        val sum = runTestBlocking {
            suspendCoroutineOrReturn<Int> { cont ->
                readSumInplaceTag(cont)
            }
        }
        check(sum == expectedSum)
    }

    // --------------------- LazyTag ---------------------

    interface Suspender<out T> {
        fun doSuspend(cont: Continuation<T>): Any?
    }

    private inline fun <T> suspendLazy(cont: Continuation<T>?, crossinline block: (Continuation<T>) -> Any?): Any? =
        if (cont == null) object : Suspender<T> {
            override fun doSuspend(cont: Continuation<T>): Any? = block(cont)
        } else
            block(cont)

    private fun readByteSuspendLazyTag(cont: Continuation<Byte>?): Any? = suspendLazy(cont) { cont ->
        scheduled = Runnable { cont.resume(bytes[index++]) }
        COROUTINE_SUSPENDED
    }

    private fun readByteLazyTag(cont: Continuation<Byte>?): Any? {
        // fast path -- no suspend
        if (!shallSuspend()) return bytes[index++]
        // slow path -- suspend
        return readByteSuspendLazyTag(cont)
    }

    private inner class ReadIntLazyTagSM(
        @JvmField val completion: Continuation<Int>,
        @JvmField var label: Int,
        @JvmField var acc: Int
    ) : Continuation<Byte> {
        override val context: CoroutineContext = EmptyCoroutineContext
        override fun resumeWithException(exception: Throwable) { completion.resumeWithException(exception) }

        override fun resume(value: Byte) {
            v = value
            val result = readIntLazyTag(this)
            if (result === COROUTINE_SUSPENDED) return
            completion.resume(result as Int)
        }

        @JvmField var v: Any? = null
    }

    @Suppress("UNCHECKED_CAST")
    private fun readIntLazyTag(cont: Continuation<*>?): Any? {
        val sm = cont as? ReadIntLazyTagSM
        var label = sm?.label ?: 0
        var acc = sm?.acc ?: 0
        var v: Byte = sm?.v as? Byte ?: 0
        while (true) {
            when (label) {
                0 -> {
                    label = 1
                    if (sm != null) {
                        sm.label = label
                        sm.acc = acc
                    }
                    val w = readByteLazyTag(sm)
                    if (w === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    if (w is Suspender<*>) return suspendLazy(cont as? Continuation<Int>) { cont ->
                        (w as Suspender<Byte>).doSuspend(ReadIntLazyTagSM(cont, label, acc))
                    }
                    v = w as Byte
                }
                1 -> {
                    acc = v.ui
                    label = 2
                    if (sm != null) {
                        sm.label = label
                        sm.acc = acc
                    }
                    val w = readByteLazyTag(sm)
                    if (w === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    if (w is Suspender<*>) return suspendLazy(cont as? Continuation<Int>) { cont ->
                        (w as Suspender<Byte>).doSuspend(ReadIntLazyTagSM(cont, label, acc))
                    }
                    v = w as Byte
                }
                2 -> {
                    acc = (v.ui shl 8) or acc
                    label = 3
                    if (sm != null) {
                        sm.label = label
                        sm.acc = acc
                    }
                    val w = readByteLazyTag(sm)
                    if (w === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    if (w is Suspender<*>) return suspendLazy(cont as? Continuation<Int>) { cont ->
                        (w as Suspender<Byte>).doSuspend(ReadIntLazyTagSM(cont, label, acc))
                    }
                    v = w as Byte
                }
                3 -> {
                    acc = (v.ui shl 16) or acc
                    label = 4
                    if (sm != null) {
                        sm.label = label
                        sm.acc = acc
                    }
                    val w = readByteLazyTag(sm)
                    if (w === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    if (w is Suspender<*>) return suspendLazy(cont as? Continuation<Int>) { cont ->
                        (w as Suspender<Byte>).doSuspend(ReadIntLazyTagSM(cont, label, acc))
                    }
                    v = w as Byte
                }
                4 -> {
                    acc = (v.ui shl 24) or acc
                    return acc
                }
            }
        }
    }

    private inner class ReadSumLazyTagSM(
        @JvmField val completion: Continuation<Int>,
        @JvmField var label: Int,
        @JvmField var sum: Int,
        @JvmField val iter: IntIterator
    ): Continuation<Int> {
        override val context: CoroutineContext = EmptyCoroutineContext
        override fun resumeWithException(exception: Throwable) { completion.resumeWithException(exception) }

        override fun resume(value: Int) {
            v = value
            val result = readSumLazyTag(this)
            if (result === COROUTINE_SUSPENDED) return
            completion.resume(result as Int)
        }

        @JvmField var v: Any? = null
    }

    @Suppress("UNCHECKED_CAST")
    private fun readSumLazyTag(cont: Continuation<*>): Any? {
        val sm = cont as? ReadSumLazyTagSM
        var label = sm?.label ?: 0
        var sum = sm?.sum ?: 0
        val iter = sm?.iter ?: indexRange.iterator()
        var v: Int = sm?.v as? Int ?: 0
        while (true) {
            when (label) {
                0 -> {
                    label = 1
                    if (!iter.hasNext()) return 0
                    iter.nextInt()
                    if (sm != null) {
                        sm.label = label
                        sm.sum = sum
                    }
                    val w = readIntLazyTag(sm)
                    if (w === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    if (w is Suspender<*>) return suspendLazy(cont as? Continuation<Int>) { cont ->
                        (w as Suspender<Int>).doSuspend(ReadSumLazyTagSM(cont, label, sum, iter))
                    }
                    v = w as Int
                }
                1 -> {
                    sum += v
                    if (!iter.hasNext()) return sum
                    iter.nextInt()
                    if (sm != null) {
                        sm.label = label
                        sm.sum = sum
                    }
                    val w = readIntLazyTag(sm)
                    if (w === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    if (w is Suspender<*>) return suspendLazy(cont as? Continuation<Int>) { cont ->
                        (w as Suspender<Int>).doSuspend(ReadSumLazyTagSM(cont, label, sum, iter))
                    }
                    v = w as Int
                }
            }
        }
    }

    @Benchmark
    fun testLazyTag() {
        index = 0
        val sum = runTestBlocking {
            suspendCoroutineOrReturn<Int> { cont ->
                readSumLazyTag(cont)
            }
        }
        check(sum == expectedSum)
    }
    // --------------------- LazyOptTag ---------------------

    private fun readByteSuspendLazyOptTag(cont: Continuation<Byte>?): Any? = suspendLazy(cont) { cont ->
        scheduled = Runnable { cont.resume(bytes[index++]) }
        COROUTINE_SUSPENDED
    }

    private fun readByteLazyOptTag(cont: Continuation<Byte>?): Any? {
        // fast path -- no suspend
        if (!shallSuspend()) return bytes[index++]
        // slow path -- suspend
        return readByteSuspendLazyOptTag(cont)
    }

    private inner class ReadIntLazyOptTagSM(
        @JvmField val completion: Continuation<Int>,
        @JvmField var label: Int,
        @JvmField var acc: Int
    ) : Continuation<Byte> {
        override val context: CoroutineContext = EmptyCoroutineContext
        override fun resumeWithException(exception: Throwable) { completion.resumeWithException(exception) }

        override fun resume(value: Byte) {
            v = value
            val result = readIntLazyOptTag(this)
            if (result === COROUTINE_SUSPENDED) return
            completion.resume(result as Int)
        }

        @JvmField var v: Byte = 0
    }

    @Suppress("UNCHECKED_CAST")
    private fun readIntLazyOptTag(cont: Continuation<*>?): Any? {
        val sm = cont as? ReadIntLazyOptTagSM
        var label = sm?.label ?: 0
        var acc = sm?.acc ?: 0
        var v: Byte = sm?.v ?: 0
        while (true) {
            when (label) {
                0 -> {
                    label = 1
                    if (sm != null) {
                        sm.label = label
                        sm.acc = acc
                    }
                    val w = readByteLazyOptTag(sm)
                    if (w === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    if (w is Suspender<*>) return suspendLazy(cont as? Continuation<Int>) { cont ->
                        (w as Suspender<Byte>).doSuspend(ReadIntLazyOptTagSM(cont, label, acc))
                    }
                    v = w as Byte
                }
                1 -> {
                    acc = v.ui
                    label = 2
                    if (sm != null) {
                        sm.label = label
                        sm.acc = acc
                    }
                    val w = readByteLazyOptTag(sm)
                    if (w === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    if (w is Suspender<*>) return suspendLazy(cont as? Continuation<Int>) { cont ->
                        (w as Suspender<Byte>).doSuspend(ReadIntLazyOptTagSM(cont, label, acc))
                    }
                    v = w as Byte
                }
                2 -> {
                    acc = (v.ui shl 8) or acc
                    label = 3
                    if (sm != null) {
                        sm.label = label
                        sm.acc = acc
                    }
                    val w = readByteLazyOptTag(sm)
                    if (w === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    if (w is Suspender<*>) return suspendLazy(cont as? Continuation<Int>) { cont ->
                        (w as Suspender<Byte>).doSuspend(ReadIntLazyOptTagSM(cont, label, acc))
                    }
                    v = w as Byte
                }
                3 -> {
                    acc = (v.ui shl 16) or acc
                    label = 4
                    if (sm != null) {
                        sm.label = label
                        sm.acc = acc
                    }
                    val w = readByteLazyOptTag(sm)
                    if (w === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    if (w is Suspender<*>) return suspendLazy(cont as? Continuation<Int>) { cont ->
                        (w as Suspender<Byte>).doSuspend(ReadIntLazyOptTagSM(cont, label, acc))
                    }
                    v = w as Byte
                }
                4 -> {
                    acc = (v.ui shl 24) or acc
                    return acc
                }
            }
        }
    }

    private inner class ReadSumLazyOptTagSM(
        @JvmField val completion: Continuation<Int>,
        @JvmField var label: Int,
        @JvmField var sum: Int,
        @JvmField val iter: IntIterator
    ): Continuation<Int> {
        override val context: CoroutineContext = EmptyCoroutineContext
        override fun resumeWithException(exception: Throwable) { completion.resumeWithException(exception) }

        override fun resume(value: Int) {
            v = value
            val result = readSumLazyOptTag(this)
            if (result === COROUTINE_SUSPENDED) return
            completion.resume(result as Int)
        }

        @JvmField var v: Int = 0
    }

    @Suppress("UNCHECKED_CAST")
    private fun readSumLazyOptTag(cont: Continuation<*>): Any? {
        val sm = cont as? ReadSumLazyOptTagSM
        var label = sm?.label ?: 0
        var sum = sm?.sum ?: 0
        val iter = sm?.iter ?: indexRange.iterator()
        var v: Int = sm?.v ?: 0
        while (true) {
            when (label) {
                0 -> {
                    label = 1
                    if (!iter.hasNext()) return 0
                    iter.nextInt()
                    if (sm != null) {
                        sm.label = label
                        sm.sum = sum
                    }
                    val w = readIntLazyOptTag(sm)
                    if (w === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    if (w is Suspender<*>) return suspendLazy(cont as? Continuation<Int>) { cont ->
                        (w as Suspender<Int>).doSuspend(ReadSumLazyOptTagSM(cont, label, sum, iter))
                    }
                    v = w as Int
                }
                1 -> {
                    sum += v
                    if (!iter.hasNext()) return sum
                    iter.nextInt()
                    if (sm != null) {
                        sm.label = label
                        sm.sum = sum
                    }
                    val w = readIntLazyOptTag(sm)
                    if (w === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    if (w is Suspender<*>) return suspendLazy(cont as? Continuation<Int>) { cont ->
                        (w as Suspender<Int>).doSuspend(ReadSumLazyOptTagSM(cont, label, sum, iter))
                    }
                    v = w as Int
                }
            }
        }
    }

    @Benchmark
    fun testLazyOptTag() {
        index = 0
        val sum = runTestBlocking {
            suspendCoroutineOrReturn<Int> { cont ->
                readSumLazyOptTag(cont)
            }
        }
        check(sum == expectedSum)
    }
    // --------------------- LazyNBTag ---------------------

    private fun readByteSuspendLazyNBTag(cont: Continuation<Byte>?): Any? = suspendLazy(cont) { cont ->
        scheduled = Runnable { cont.resume(bytes[index++]) }
        COROUTINE_SUSPENDED
    }

    private fun readByteLazyNBTag(cont: Continuation<Byte>?): Any? {
        // fast path -- no suspend
        if (!shallSuspend()) return bytes[index++]
        // slow path -- suspend
        return readByteSuspendLazyNBTag(cont)
    }

    private inner class ReadIntLazyNBTagSM(
        @JvmField val completion: Continuation<Int>,
        @JvmField var label: Int,
        @JvmField var acc: Int
    ) : Continuation<Byte> {
        override val context: CoroutineContext = EmptyCoroutineContext
        override fun resumeWithException(exception: Throwable) { completion.resumeWithException(exception) }

        override fun resume(value: Byte) {
            v = value
            val result = readIntLazyNBTag(this)
            if (result === COROUTINE_SUSPENDED) return
            completion.resume(result as Int)
        }

        @JvmField var v: Byte = 0
    }

    @Suppress("UNCHECKED_CAST")
    private fun readIntLazyNBTag(cont: Continuation<*>?): Any? {
        val sm = cont as? ReadIntLazyNBTagSM
        var label = sm?.label ?: 0
        var acc = sm?.acc ?: 0
        var v: Byte = sm?.v ?: 0
        while (true) {
            when (label) {
                0 -> {
                    label = 1
                    if (sm != null) {
                        sm.label = label
                        sm.acc = acc
                    }
                    val w = readByteLazyNBTag(sm)
                    if (w === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    val label0 = label; val acc0 = acc
                    if (w is Suspender<*>) return suspendLazy(cont as? Continuation<Int>) { cont ->
                        (w as Suspender<Byte>).doSuspend(ReadIntLazyNBTagSM(cont, label0, acc0))
                    }
                    v = w as Byte
                }
                1 -> {
                    acc = v.ui
                    label = 2
                    if (sm != null) {
                        sm.label = label
                        sm.acc = acc
                    }
                    val w = readByteLazyNBTag(sm)
                    if (w === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    val label0 = label; val acc0 = acc
                    if (w is Suspender<*>) return suspendLazy(cont as? Continuation<Int>) { cont ->
                        (w as Suspender<Byte>).doSuspend(ReadIntLazyNBTagSM(cont, label0, acc0))
                    }
                    v = w as Byte
                }
                2 -> {
                    acc = (v.ui shl 8) or acc
                    label = 3
                    if (sm != null) {
                        sm.label = label
                        sm.acc = acc
                    }
                    val w = readByteLazyNBTag(sm)
                    if (w === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    val label0 = label; val acc0 = acc
                    if (w is Suspender<*>) return suspendLazy(cont as? Continuation<Int>) { cont ->
                        (w as Suspender<Byte>).doSuspend(ReadIntLazyNBTagSM(cont, label0, acc0))
                    }
                    v = w as Byte
                }
                3 -> {
                    acc = (v.ui shl 16) or acc
                    label = 4
                    if (sm != null) {
                        sm.label = label
                        sm.acc = acc
                    }
                    val w = readByteLazyNBTag(sm)
                    if (w === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    val label0 = label; val acc0 = acc
                    if (w is Suspender<*>) return suspendLazy(cont as? Continuation<Int>) { cont ->
                        (w as Suspender<Byte>).doSuspend(ReadIntLazyNBTagSM(cont, label0, acc0))
                    }
                    v = w as Byte
                }
                4 -> {
                    acc = (v.ui shl 24) or acc
                    return acc
                }
            }
        }
    }

    private inner class ReadSumLazyNBTagSM(
        @JvmField val completion: Continuation<Int>,
        @JvmField var label: Int,
        @JvmField var sum: Int,
        @JvmField val iter: IntIterator
    ): Continuation<Int> {
        override val context: CoroutineContext = EmptyCoroutineContext
        override fun resumeWithException(exception: Throwable) { completion.resumeWithException(exception) }

        override fun resume(value: Int) {
            v = value
            val result = readSumLazyNBTag(this)
            if (result === COROUTINE_SUSPENDED) return
            completion.resume(result as Int)
        }

        @JvmField var v: Int = 0
    }

    @Suppress("UNCHECKED_CAST")
    private fun readSumLazyNBTag(cont: Continuation<*>): Any? {
        val sm = cont as? ReadSumLazyNBTagSM
        var label = sm?.label ?: 0
        var sum = sm?.sum ?: 0
        val iter = sm?.iter ?: indexRange.iterator()
        var v: Int = sm?.v ?: 0
        while (true) {
            when (label) {
                0 -> {
                    label = 1
                    if (!iter.hasNext()) return 0
                    iter.nextInt()
                    if (sm != null) {
                        sm.label = label
                        sm.sum = sum
                    }
                    val w = readIntLazyNBTag(sm)
                    if (w === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    val label0 = label; val sum0 = sum
                    if (w is Suspender<*>) return suspendLazy(cont as? Continuation<Int>) { cont ->
                        (w as Suspender<Int>).doSuspend(ReadSumLazyNBTagSM(cont, label0, sum0, iter))
                    }
                    v = w as Int
                }
                1 -> {
                    sum += v
                    if (!iter.hasNext()) return sum
                    iter.nextInt()
                    if (sm != null) {
                        sm.label = label
                        sm.sum = sum
                    }
                    val w = readIntLazyNBTag(sm)
                    if (w === COROUTINE_SUSPENDED) return COROUTINE_SUSPENDED
                    val label0 = label; val sum0 = sum
                    if (w is Suspender<*>) return suspendLazy(cont as? Continuation<Int>) { cont ->
                        (w as Suspender<Int>).doSuspend(ReadSumLazyNBTagSM(cont, label0, sum0, iter))
                    }
                    v = w as Int
                }
            }
        }
    }

    @Benchmark
    fun testLazyNBTag() {
        index = 0
        val sum = runTestBlocking {
            suspendCoroutineOrReturn<Int> { cont ->
                readSumLazyNBTag(cont)
            }
        }
        check(sum == expectedSum)
    }

    // --------------------- LazySEx ---------------------

    abstract class SuspendException : Throwable(null, null, false, false) {
        abstract fun doSuspend(cont: Continuation<*>): Any?
    }

    object SuspendException0 : SuspendException() {
        override fun doSuspend(cont: Continuation<*>): Any? = COROUTINE_SUSPENDED
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <T> suspendLazySEx(cont: Continuation<T>?, crossinline block: (Continuation<T>) -> Any?): T =
        if (cont == null) throw object : SuspendException() {
            override fun doSuspend(cont: Continuation<*>): Any? = block(cont as Continuation<T>)
        } else {
            val result = block(cont)
            if (result == COROUTINE_SUSPENDED) throw SuspendException0 else result as T
        }


    private fun readByteSuspendLazySEx(cont: Continuation<Byte>?): Byte = suspendLazySEx(cont) { cont ->
        scheduled = Runnable { cont.resume(bytes[index++]) }
        COROUTINE_SUSPENDED
    }

    private fun readByteLazySEx(cont: Continuation<Byte>?): Byte {
        // fast path -- no suspend
        if (!shallSuspend()) return bytes[index++]
        // slow path -- suspend
        return readByteSuspendLazySEx(cont)
    }

    private inner class ReadIntLazySExSM(
        @JvmField val completion: Continuation<Int>,
        @JvmField var label: Int,
        @JvmField var acc: Int
    ) : Continuation<Byte> {
        override val context: CoroutineContext = EmptyCoroutineContext
        override fun resumeWithException(exception: Throwable) { completion.resumeWithException(exception) }

        override fun resume(value: Byte) {
            v = value
            val result = try { readIntLazySEx(this) }
            catch (e: SuspendException) { return }
            completion.resume(result)
        }

        @JvmField var v: Byte = 0
    }

    @Suppress("UNCHECKED_CAST")
    private fun readIntLazySEx(cont: Continuation<*>?): Int {
        val sm = cont as? ReadIntLazySExSM
        var label = sm?.label ?: 0
        var acc = sm?.acc ?: 0
        var v: Byte = sm?.v ?: 0
        while (true) {
            when (label) {
                0 -> {
                    label = 1
                    if (sm != null) {
                        sm.label = label
                        sm.acc = acc
                    }
                    try {
                        v = readByteLazySEx(sm)
                    } catch (e: SuspendException) {
                        if (e === SuspendException0) throw e
                        val label0 = label; val acc0 = acc
                        return suspendLazySEx(cont as? Continuation<Int>) { cont ->
                            e.doSuspend(ReadIntLazySExSM(cont, label0, acc0))
                        }
                    }
                }
                1 -> {
                    acc = v.ui
                    label = 2
                    if (sm != null) {
                        sm.label = label
                        sm.acc = acc
                    }
                    try {
                        v = readByteLazySEx(sm)
                    } catch (e: SuspendException) {
                        if (e === SuspendException0) throw e
                        val label0 = label; val acc0 = acc
                        return suspendLazySEx(cont as? Continuation<Int>) { cont ->
                            e.doSuspend(ReadIntLazySExSM(cont, label0, acc0))
                        }
                    }
                }
                2 -> {
                    acc = (v.ui shl 8) or acc
                    label = 3
                    if (sm != null) {
                        sm.label = label
                        sm.acc = acc
                    }
                    try {
                        v = readByteLazySEx(sm)
                    } catch (e: SuspendException) {
                        if (e === SuspendException0) throw e
                        val label0 = label; val acc0 = acc
                        return suspendLazySEx(cont as? Continuation<Int>) { cont ->
                            e.doSuspend(ReadIntLazySExSM(cont, label0, acc0))
                        }
                    }
                }
                3 -> {
                    acc = (v.ui shl 16) or acc
                    label = 4
                    if (sm != null) {
                        sm.label = label
                        sm.acc = acc
                    }
                    try {
                        v = readByteLazySEx(sm)
                    } catch (e: SuspendException) {
                        if (e === SuspendException0) throw e
                        val label0 = label; val acc0 = acc
                        return suspendLazySEx(cont as? Continuation<Int>) { cont ->
                            e.doSuspend(ReadIntLazySExSM(cont, label0, acc0))
                        }
                    }
                }
                4 -> {
                    acc = (v.ui shl 24) or acc
                    return acc
                }
            }
        }
    }

    private inner class ReadSumLazySExSM(
        @JvmField val completion: Continuation<Int>,
        @JvmField var label: Int,
        @JvmField var sum: Int,
        @JvmField val iter: IntIterator
    ): Continuation<Int> {
        override val context: CoroutineContext = EmptyCoroutineContext
        override fun resumeWithException(exception: Throwable) { completion.resumeWithException(exception) }

        override fun resume(value: Int) {
            v = value
            val result = try { readSumLazySEx(this) }
            catch (e: SuspendException) { return }
            completion.resume(result)
        }

        @JvmField var v: Int = 0
    }

    @Suppress("UNCHECKED_CAST")
    private fun readSumLazySEx(cont: Continuation<*>): Int {
        val sm = cont as? ReadSumLazySExSM
        var label = sm?.label ?: 0
        var sum = sm?.sum ?: 0
        val iter = sm?.iter ?: indexRange.iterator()
        var v: Int = sm?.v ?: 0
        while (true) {
            when (label) {
                0 -> {
                    label = 1
                    if (!iter.hasNext()) return 0
                    iter.nextInt()
                    if (sm != null) {
                        sm.label = label
                        sm.sum = sum
                    }
                    try {
                        v = readIntLazySEx(sm)
                    } catch (e: SuspendException) {
                        if (e === SuspendException0) throw e
                        val label0 = label; val sum0 = sum
                        return suspendLazySEx(cont as? Continuation<Int>) { cont ->
                            e.doSuspend(ReadSumLazySExSM(cont, label0, sum0, iter))
                        }
                    }
                }
                1 -> {
                    sum += v
                    if (!iter.hasNext()) return sum
                    iter.nextInt()
                    if (sm != null) {
                        sm.label = label
                        sm.sum = sum
                    }
                    try {
                        v = readIntLazySEx(sm)
                    } catch (e: SuspendException) {
                        if (e === SuspendException0) throw e
                        val label0 = label; val sum0 = sum
                        return suspendLazySEx(cont as? Continuation<Int>) { cont ->
                            e.doSuspend(ReadSumLazySExSM(cont, label0, sum0, iter))
                        }
                    }
                }
            }
        }
    }

    @Benchmark
    fun testLazySEx() {
        index = 0
        val sum = runTestBlocking {
            suspendCoroutineOrReturn<Int> { cont ->
                try { readSumLazySEx(cont) }
                catch (e: SuspendException) { COROUTINE_SUSPENDED }
            }
        }
        check(sum == expectedSum)
    }

}

fun main(args: Array<String>) {
    val b = SuspendStrategiesBenchmark()
    b.suspendEvery = 65536
    b.testLazyNBTag()
}
