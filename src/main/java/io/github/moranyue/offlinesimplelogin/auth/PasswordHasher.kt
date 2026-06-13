package io.github.moranyue.offlinesimplelogin.auth

import de.mkammerer.argon2.Argon2Factory
import de.mkammerer.argon2.Argon2
import io.github.moranyue.offlinesimplelogin.config.Argon2Config

/**
 * Argon2 password hashing wrapper.
 *
 * Uses de.mkammerer:argon2-jvm for Argon2id hashing with configurable parameters.
 */
class PasswordHasher(private val config: Argon2Config) {

    private val argon2: Argon2 = Argon2Factory.create(
        Argon2Factory.Argon2Types.ARGON2id,
        config.saltLength,
        config.hashLength
    )

    /**
     * Hash a password using Argon2id with the configured parameters.
     *
     * @param password the plaintext password
     * @return the encoded hash string (contains all parameters for verification)
     */
    fun hash(password: String): String {
        return argon2.hash(config.iterations, config.memory, config.parallelism, password.toCharArray())
    }

    /**
     * Verify a password against an Argon2 hash.
     *
     * @param password the plaintext password to verify
     * @param hash the encoded hash string to verify against
     * @return true if the password matches the hash
     */
    fun verify(password: String, hash: String): Boolean {
        return argon2.verify(hash, password.toCharArray())
    }
}
