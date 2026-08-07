package ru.reserve.sin.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.patch
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.reserve.sin.data.local.CategoryEntity
import ru.reserve.sin.data.local.TransactionEntity

class ServerSyncClient {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    suspend fun createCategory(serverUrl: String, token: String, category: CategoryEntity): RemoteCategory {
        val response = client.post("$serverUrl/api/v1/categories") {
            authenticated(token)
            setBody(
                CategoryRequest(
                    name = category.name,
                    targetAmountRub = category.targetAmountRub,
                    sortOrder = category.sortOrder,
                    isVisibleOnHome = category.isVisibleOnHome,
                    clientCategoryId = category.id,
                ),
            )
        }
        require(response.status == HttpStatusCode.Created || response.status == HttpStatusCode.OK) { "Не удалось отправить категорию" }
        return response.body()
    }

    suspend fun updateCategory(serverUrl: String, token: String, category: CategoryEntity): RemoteCategory {
        val remoteId = requireNotNull(category.remoteId) { "Не найдена серверная категория" }
        val response = client.patch("$serverUrl/api/v1/categories/$remoteId") {
            authenticated(token)
            setBody(
                CategoryPatchRequest(
                    name = category.name,
                    targetAmountRub = category.targetAmountRub,
                    sortOrder = category.sortOrder,
                    isArchived = category.isArchived,
                    isVisibleOnHome = category.isVisibleOnHome,
                ),
            )
        }
        require(response.status == HttpStatusCode.OK) { "Не удалось обновить категорию" }
        return response.body()
    }

    suspend fun changes(serverUrl: String, token: String, after: Long): RemoteChanges {
        val response = client.get("$serverUrl/api/v1/changes?after=$after") { authenticated(token) }
        require(response.status == HttpStatusCode.OK) { "Не удалось получить изменения сервера" }
        return response.body()
    }

    suspend fun createTransaction(serverUrl: String, token: String, transaction: TransactionEntity, categoryId: String): RemoteTransaction {
        val response = client.post("$serverUrl/api/v1/transactions") {
            authenticated(token)
            setBody(TransactionRequest(categoryId, transaction.amountRub, transaction.comment, transaction.occurredAt, transaction.clientOperationId))
        }
        require(response.status == HttpStatusCode.Created || response.status == HttpStatusCode.OK) { "Не удалось отправить операцию" }
        return response.body<SingleTransactionResponse>().transaction
    }

    suspend fun createBatch(
        serverUrl: String,
        token: String,
        transactions: List<TransactionEntity>,
        categoryIds: Map<String, String>,
    ): List<RemoteTransaction> {
        val first = transactions.first()
        val response = client.post("$serverUrl/api/v1/transaction-batches") {
            authenticated(token)
            setBody(
                BatchRequest(
                    occurredAt = first.occurredAt,
                    comment = first.comment,
                    transactions = transactions.map { transaction ->
                        BatchTransactionRequest(categoryIds.getValue(transaction.clientOperationId), transaction.amountRub, transaction.clientOperationId)
                    },
                ),
            )
        }
        require(response.status == HttpStatusCode.Created || response.status == HttpStatusCode.OK) { "Не удалось отправить группу операций" }
        return response.body<BatchResponse>().transactions
    }

    fun close() = client.close()

    private fun io.ktor.client.request.HttpRequestBuilder.authenticated(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}

@Serializable
private data class CategoryRequest(
    val name: String,
    @kotlinx.serialization.SerialName("target_amount_rub") val targetAmountRub: Long?,
    @kotlinx.serialization.SerialName("sort_order") val sortOrder: Long,
    @kotlinx.serialization.SerialName("is_visible_on_home") val isVisibleOnHome: Boolean,
    @kotlinx.serialization.SerialName("client_category_id") val clientCategoryId: String,
)

@Serializable
private data class CategoryPatchRequest(
    val name: String,
    @kotlinx.serialization.SerialName("target_amount_rub") val targetAmountRub: Long?,
    @kotlinx.serialization.SerialName("sort_order") val sortOrder: Long,
    @kotlinx.serialization.SerialName("is_archived") val isArchived: Boolean,
    @kotlinx.serialization.SerialName("is_visible_on_home") val isVisibleOnHome: Boolean,
)

@Serializable
data class RemoteCategory(val id: String, @kotlinx.serialization.SerialName("updated_at") val updatedAt: String, val revision: Long)

@Serializable
private data class TransactionRequest(
    @kotlinx.serialization.SerialName("category_id") val categoryId: String,
    @kotlinx.serialization.SerialName("amount_rub") val amountRub: Long,
    val comment: String?,
    @kotlinx.serialization.SerialName("occurred_at") val occurredAt: String,
    @kotlinx.serialization.SerialName("client_operation_id") val clientOperationId: String,
)

@Serializable
private data class BatchRequest(
    @kotlinx.serialization.SerialName("occurred_at") val occurredAt: String,
    val comment: String?,
    val transactions: List<BatchTransactionRequest>,
)

@Serializable
private data class BatchTransactionRequest(
    @kotlinx.serialization.SerialName("category_id") val categoryId: String,
    @kotlinx.serialization.SerialName("amount_rub") val amountRub: Long,
    @kotlinx.serialization.SerialName("client_operation_id") val clientOperationId: String,
)

@Serializable
private data class SingleTransactionResponse(val transaction: RemoteTransaction)

@Serializable
private data class BatchResponse(val transactions: List<RemoteTransaction>)

@Serializable
data class RemoteTransaction(
    val id: String,
    @kotlinx.serialization.SerialName("client_operation_id") val clientOperationId: String,
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String,
    val revision: Long,
)

@Serializable
data class RemoteChanges(
    val categories: List<RemoteCategoryChange>,
    val labels: List<RemoteLabel>,
    val transactions: List<RemoteTransactionChange>,
    val revision: Long,
)

@Serializable
data class RemoteCategoryChange(
    val id: String,
    @kotlinx.serialization.SerialName("client_category_id") val clientCategoryId: String?,
    val name: String,
    val currency: String,
    @kotlinx.serialization.SerialName("target_amount_rub") val targetAmountRub: Long?,
    @kotlinx.serialization.SerialName("sort_order") val sortOrder: Long,
    @kotlinx.serialization.SerialName("is_archived") val isArchived: Boolean,
    @kotlinx.serialization.SerialName("is_visible_on_home") val isVisibleOnHome: Boolean,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String,
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String,
    val revision: Long,
)

@Serializable
data class RemoteLabel(
    val id: String,
    val name: String,
    @kotlinx.serialization.SerialName("sort_order") val sortOrder: Long,
    @kotlinx.serialization.SerialName("is_archived") val isArchived: Boolean,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String,
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String,
    val revision: Long,
)

@Serializable
data class RemoteTransactionChange(
    val id: String,
    @kotlinx.serialization.SerialName("category_id") val categoryId: String,
    @kotlinx.serialization.SerialName("label_id") val labelId: String?,
    @kotlinx.serialization.SerialName("batch_id") val batchId: String?,
    @kotlinx.serialization.SerialName("amount_rub") val amountRub: Long,
    val comment: String?,
    @kotlinx.serialization.SerialName("occurred_at") val occurredAt: String,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String,
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String,
    @kotlinx.serialization.SerialName("client_operation_id") val clientOperationId: String,
    @kotlinx.serialization.SerialName("is_cancelled") val isCancelled: Boolean,
    val revision: Long,
)
