package fr.nathanaudvard.emp.service

import io.ktor.util.generateNonceBlocking
import io.ktor.util.sha256

// 256 bits from the system's secure random source
fun newToken(): String = generateNonceBlocking(64)

fun hashToken(token: String): String = sha256(token.encodeToByteArray()).toHexString()

// Takes the same time wherever the strings differ, so that a token cannot be guessed char by char
fun secureEquals(a: String, b: String): Boolean {
    var difference = a.length xor b.length
    for (i in 0 until minOf(a.length, b.length)) difference = difference or (a[i].code xor b[i].code)
    return difference == 0
}

// Without the characters mistaken for one another: 0 and O, 1, I and L
private const val CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

const val DEVICE_CODE_LENGTH = 6

fun newDeviceCode(): String = buildString {
    while (length < DEVICE_CODE_LENGTH) {
        for (byte in newToken().hexToByteArray()) {
            val value = byte.toInt() and 0xFF
            // The values above the last whole round of the alphabet would favour its first characters
            if (length < DEVICE_CODE_LENGTH && value < 256 - 256 % CODE_ALPHABET.length) {
                append(CODE_ALPHABET[value % CODE_ALPHABET.length])
            }
        }
    }
}

// Typed in any case, with or without its dash
fun normalizeDeviceCode(input: String): String = input.uppercase().filter { it.isLetterOrDigit() }
