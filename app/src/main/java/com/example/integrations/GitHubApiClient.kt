package com.example.integrations

import com.example.security.SecretRedactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GitHubUser(
    val login: String,
    val name: String,
    val publicRepos: Int,
    val scopes: String
)

data class GitHubRepo(
    val fullName: String,
    val name: String,
    val isPrivate: Boolean,
    val defaultBranch: String,
    val htmlUrl: String
)

class GitHubApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun testToken(token: String): Result<GitHubUser> = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext Result.failure(Exception("GitHub Token is empty"))

        try {
            val request = Request.Builder()
                .url("https://api.github.com/user")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", "SHEN-Termutoxic-App")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val msg = SecretRedactor.redact("GitHub API Error ${response.code}: $bodyStr")
                    return@withContext Result.failure(Exception(msg))
                }

                val json = JSONObject(bodyStr)
                val login = json.optString("login", "Unknown")
                val name = json.optString("name", login)
                val publicRepos = json.optInt("public_repos", 0)
                val scopes = response.header("x-oauth-scopes") ?: "Read/Write Access"

                Result.success(
                    GitHubUser(
                        login = login,
                        name = name,
                        publicRepos = publicRepos,
                        scopes = scopes
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception(SecretRedactor.redact(e.localizedMessage ?: "Unknown error")))
        }
    }

    suspend fun getUserRepos(token: String): Result<List<GitHubRepo>> = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext Result.failure(Exception("GitHub Token is empty"))

        try {
            val request = Request.Builder()
                .url("https://api.github.com/user/repos?sort=updated&per_page=30")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", "SHEN-Termutoxic-App")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("GitHub API Error ${response.code}"))
                }

                val array = JSONArray(bodyStr)
                val repos = mutableListOf<GitHubRepo>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    repos.add(
                        GitHubRepo(
                            fullName = obj.optString("full_name"),
                            name = obj.optString("name"),
                            isPrivate = obj.optBoolean("private", false),
                            defaultBranch = obj.optString("default_branch", "main"),
                            htmlUrl = obj.optString("html_url")
                        )
                    )
                }
                Result.success(repos)
            }
        } catch (e: Exception) {
            Result.failure(Exception(SecretRedactor.redact(e.localizedMessage ?: "Error fetching repos")))
        }
    }
}
