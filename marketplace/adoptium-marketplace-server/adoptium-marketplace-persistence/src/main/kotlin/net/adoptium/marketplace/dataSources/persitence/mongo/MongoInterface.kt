package net.adoptium.marketplace.dataSources.persitence.mongo

import kotlinx.coroutines.runBlocking
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.slf4j.LoggerFactory

abstract class MongoInterface(mongoClient: MongoClient) {
    protected val database: CoroutineDatabase = mongoClient.getDatabase()

    companion object {
        @JvmStatic
        val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    inline fun <reified T : Any> initDb(database: CoroutineDatabase, collectionName: String, crossinline onCollectionCreated: ((CoroutineCollection<T>) -> Unit) = {}): CoroutineCollection<T> {
        return runBlocking {
            return@runBlocking if (!database.listCollectionNames().contains(collectionName)) {
                try {
                    // TODO add indexes
                    database.createCollection(collectionName)
                    val collection = database.getCollection<T>(collectionName)
                    onCollectionCreated(collection)
                    collection
                } catch (e: Exception) {
                    LOGGER.error("Failed to create db", e)
                    database.getCollection(collectionName)
                }
            } else {
                database.getCollection(collectionName)
            }
        }
    }
}
