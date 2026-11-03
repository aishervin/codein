package com.example.integrations

import com.example.security.SecretRedactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CloudflareTokenStatus(
    val id: String,
    val status: String,
    val accountsCount: Int,
    val zonesCount: Int
)

data class CloudflareZone(
    val id: String,
    val name: String,
    val status: String,
    val nameServers: List<String>
)

data class CloudflareDnsRecord(
    val id: String,
    val type: String,
    val name: String,
    val content: String,
    val proxied: Boolean,
    val ttl: Int
)

data class CloudflareTunnel(
    val id: String,
    val name: String,
    val status: String,
    val createdAt: String
)

class CloudflareApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun verifyToken(token: String): Result<CloudflareTokenStatus> = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext Result.failure(Exception("Cloudflare Token is empty"))

        try {
            val request = Request.Builder()
                .url("https://api.cloudflare.com/client/v4/user/tokens/verify")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception(SecretRedactor.redact("Cloudflare Verify Error ${response.code}: $bodyStr")))
                }

                val json = JSONObject(bodyStr)
                val resultObj = json.optJSONObject("result")
                val status = resultObj?.optString("status", "active") ?: "active"
                val id = resultObj?.optString("id", "verified_token") ?: "verified_token"

                // Fetch counts
                val accounts = getAccounts(token).getOrDefault(emptyList())
                val zones = getZones(token).getOrDefault(emptyList())

                Result.success(
                    CloudflareTokenStatus(
                        id = id,
                        status = status,
                        accountsCount = accounts.size,
                        zonesCount = zones.size
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(SecretRedactor.redact(e.localizedMessage ?: "Unknown Cloudflare error")))
        }
    }

    suspend fun getAccounts(token: String): Result<List<Pair<String, String>>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.cloudflare.com/client/v4/accounts")
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}"))

                val json = JSONObject(bodyStr)
                val resultArr = json.optJSONArray("result") ?: JSONArray()
                val list = mutableListOf<Pair<String, String>>()
                for (i in 0 until resultArr.length()) {
                    val obj = resultArr.getJSONObject(i)
                    list.add(Pair(obj.optString("id"), obj.optString("name")))
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getZones(token: String): Result<List<CloudflareZone>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.cloudflare.com/client/v4/zones?per_page=50")
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}"))

                val json = JSONObject(bodyStr)
                val resultArr = json.optJSONArray("result") ?: JSONArray()
                val list = mutableListOf<CloudflareZone>()
                for (i in 0 until resultArr.length()) {
                    val obj = resultArr.getJSONObject(i)
                    val nsArr = obj.optJSONArray("name_servers") ?: JSONArray()
                    val nsList = mutableListOf<String>()
                    for (j in 0 until nsArr.length()) {
                        nsList.add(nsArr.getString(j))
                    }
                    list.add(
                        CloudflareZone(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            status = obj.optString("status"),
                            nameServers = nsList
                        )
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listDnsRecords(token: String, zoneId: String): Result<List<CloudflareDnsRecord>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.cloudflare.com/client/v4/zones/$zoneId/dns_records")
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}"))

                val json = JSONObject(bodyStr)
                val resultArr = json.optJSONArray("result") ?: JSONArray()
                val list = mutableListOf<CloudflareDnsRecord>()
                for (i in 0 until resultArr.length()) {
                    val obj = resultArr.getJSONObject(i)
                    list.add(
                        CloudflareDnsRecord(
                            id = obj.optString("id"),
                            type = obj.optString("type"),
                            name = obj.optString("name"),
                            content = obj.optString("content"),
                            proxied = obj.optBoolean("proxied", true),
                            ttl = obj.optInt("ttl", 1)
                        )
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createDnsRecord(
        token: String,
        zoneId: String,
        type: String,
        name: String,
        content: String,
        proxied: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("type", type)
                put("name", name)
                put("content", content)
                put("ttl", 1)
                put("proxied", proxied)
            }

            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://api.cloudflare.com/client/v4/zones/$zoneId/dns_records")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(bodyStr)
                    val recId = json.optJSONObject("result")?.optString("id", "") ?: ""
                    Result.success("DNS Record created successfully (ID: $recId)")
                } else {
                    Result.failure(Exception(SecretRedactor.redact("Failed to create DNS: $bodyStr")))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteDnsRecord(token: String, zoneId: String, recordId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.cloudflare.com/client/v4/zones/$zoneId/dns_records/$recordId")
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTunnels(token: String, accountId: String): Result<List<CloudflareTunnel>> = withContext(Dispatchers.IO) {
        if (accountId.isBlank()) return@withContext Result.success(emptyList())
        try {
            val request = Request.Builder()
                .url("https://api.cloudflare.com/client/v4/accounts/$accountId/tunnels")
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}"))

                val json = JSONObject(bodyStr)
                val resultArr = json.optJSONArray("result") ?: JSONArray()
                val list = mutableListOf<CloudflareTunnel>()
                for (i in 0 until resultArr.length()) {
                    val obj = resultArr.getJSONObject(i)
                    list.add(
                        CloudflareTunnel(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            status = obj.optString("status", "healthy"),
                            createdAt = obj.optString("created_at")
                        )
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
