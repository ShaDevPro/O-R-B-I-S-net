package com.sha.orbis.ai.core

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Proprietary ORBIS Vector Mathematics Engine.
 * Vectorized, zero-dependency linear algebra and neural operations in pure Kotlin.
 */
object OrbisVectorMath {

    /**
     * Computes the dot product of two float vectors.
     */
    fun dotProduct(a: FloatArray, b: FloatArray): Float {
        val n = min(a.size, b.size)
        var sum = 0.0f
        var i = 0
        // Unroll loop for SIMD / CPU pipeline efficiency
        while (i + 3 < n) {
            sum += a[i] * b[i] + a[i + 1] * b[i + 1] + a[i + 2] * b[i + 2] + a[i + 3] * b[i + 3]
            i += 4
        }
        while (i < n) {
            sum += a[i] * b[i]
            i++
        }
        return sum
    }

    /**
     * Computes the Euclidean norm (magnitude) of a vector.
     */
    fun norm(a: FloatArray): Float {
        var sum = 0.0f
        for (v in a) {
            sum += v * v
        }
        return sqrt(sum)
    }

    /**
     * Computes Cosine Similarity between two vectors: (A . B) / (||A|| * ||B||).
     * Returns a score in range [-1.0, 1.0].
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        val dot = dotProduct(a, b)
        val normA = norm(a)
        val normB = norm(b)
        if (normA <= 1e-7f || normB <= 1e-7f) return 0.0f
        val sim = dot / (normA * normB)
        return sim.coerceIn(-1.0f, 1.0f)
    }

    /**
     * In-place L2 normalization of a float vector.
     */
    fun normalizeL2(v: FloatArray): FloatArray {
        val mag = norm(v)
        if (mag > 1e-7f) {
            val inv = 1.0f / mag
            for (i in v.indices) {
                v[i] *= inv
            }
        }
        return v
    }

    /**
     * Element-wise vector addition: C = A + B.
     */
    fun add(a: FloatArray, b: FloatArray): FloatArray {
        val n = min(a.size, b.size)
        val res = FloatArray(n)
        for (i in 0 until n) {
            res[i] = a[i] + b[i]
        }
        return res
    }

    /**
     * Vector scaled addition (for online learning & centroid shifting): A += B * scale.
     */
    fun addScaledInPlace(target: FloatArray, source: FloatArray, scale: Float) {
        val n = min(target.size, source.size)
        for (i in 0 until n) {
            target[i] += source[i] * scale
        }
    }

    /**
     * Dense feed-forward projection: Y = X * W + Bias.
     * X: [inDim], W: [inDim, outDim] flattened row-major, Bias: [outDim].
     */
    fun dense(input: FloatArray, weights: FloatArray, bias: FloatArray, inDim: Int, outDim: Int): FloatArray {
        val output = FloatArray(outDim)
        for (j in 0 until outDim) {
            var sum = if (j < bias.size) bias[j] else 0.0f
            var i = 0
            while (i < inDim) {
                val weightIdx = i * outDim + j
                if (weightIdx < weights.size) {
                    sum += input[i] * weights[weightIdx]
                }
                i++
            }
            output[j] = sum
        }
        return output
    }

    /**
     * Softmax activation across a float vector with numerical stability.
     */
    fun softmax(v: FloatArray): FloatArray {
        if (v.isEmpty()) return FloatArray(0)
        var maxVal = v[0]
        for (i in 1 until v.size) {
            if (v[i] > maxVal) maxVal = v[i]
        }
        val expVals = FloatArray(v.size)
        var sum = 0.0f
        for (i in v.indices) {
            val e = exp(v[i] - maxVal)
            expVals[i] = e
            sum += e
        }
        if (sum > 0.0f) {
            val invSum = 1.0f / sum
            for (i in expVals.indices) {
                expVals[i] *= invSum
            }
        }
        return expVals
    }

    /**
     * Standard Sigmoid activation function: 1 / (1 + exp(-x)).
     */
    fun sigmoid(x: Float): Float {
        return (1.0f / (1.0f + exp(-x))).coerceIn(0.0f, 1.0f)
    }

    /**
     * Scaled Dot-Product Self-Attention Score:
     * Attention(Q, K) = Softmax( (Q . K) / sqrt(d_k) ).
     */
    fun attentionScore(query: FloatArray, key: FloatArray): Float {
        val d = min(query.size, key.size)
        if (d == 0) return 0.0f
        val dot = dotProduct(query, key)
        val scaled = dot / sqrt(d.toFloat())
        return sigmoid(scaled)
    }

    /**
     * INT8 Quantization: Encodes float array into signed bytes with scale and zero-point.
     * Saves 75% memory footprint while preserving precision.
     */
    fun quantizeToInt8(floats: FloatArray): QuantizedVector {
        if (floats.isEmpty()) return QuantizedVector(ByteArray(0), 1.0f, 0)
        var minVal = floats[0]
        var maxVal = floats[0]
        for (v in floats) {
            if (v < minVal) minVal = v
            if (v > maxVal) maxVal = v
        }
        val range = max(maxVal - minVal, 1e-7f)
        val scale = range / 255.0f
        val zeroPoint = (-minVal / scale).toInt().coerceIn(0, 255) - 128

        val bytes = ByteArray(floats.size)
        for (i in floats.indices) {
            val q = (floats[i] / scale + zeroPoint).toInt()
            bytes[i] = q.coerceIn(-128, 127).toByte()
        }
        return QuantizedVector(bytes, scale, zeroPoint)
    }

    /**
     * INT8 Dequantization: Decodes signed bytes back to floats: (byte - zeroPoint) * scale.
     */
    fun dequantizeFromInt8(quantized: QuantizedVector): FloatArray {
        val floats = FloatArray(quantized.data.size)
        val scale = quantized.scale
        val zeroPoint = quantized.zeroPoint
        for (i in quantized.data.indices) {
            floats[i] = (quantized.data[i].toInt() - zeroPoint) * scale
        }
        return floats
    }

    data class QuantizedVector(
        val data: ByteArray,
        val scale: Float,
        val zeroPoint: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is QuantizedVector) return false
            return data.contentEquals(other.data) && scale == other.scale && zeroPoint == other.zeroPoint
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + scale.hashCode()
            result = 31 * result + zeroPoint
            return result
        }
    }
}
