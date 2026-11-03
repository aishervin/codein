package com.example.termux

import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import java.io.ByteArrayOutputStream

data class SSHKeyPair(
    val privateKeyPem: String,
    val publicKeyAuthorizedFormat: String
)

object SSHKeyGenerator {

    fun generateKeyPair(comment: String = "shen@termutoxic"): SSHKeyPair {
        val jsch = JSch()
        val keyPair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048)

        val privateKeyStream = ByteArrayOutputStream()
        val publicKeyStream = ByteArrayOutputStream()

        keyPair.writePrivateKey(privateKeyStream)
        keyPair.writePublicKey(publicKeyStream, comment)

        val privateKeyPem = privateKeyStream.toString("UTF-8")
        val publicKeyAuthorizedFormat = publicKeyStream.toString("UTF-8").trim()

        keyPair.dispose()

        return SSHKeyPair(
            privateKeyPem = privateKeyPem,
            publicKeyAuthorizedFormat = publicKeyAuthorizedFormat
        )
    }
}
