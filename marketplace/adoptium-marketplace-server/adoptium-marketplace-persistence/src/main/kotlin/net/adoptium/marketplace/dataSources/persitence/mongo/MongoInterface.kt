package net.adoptium.marketplace.dataSources.persitence.mongo

import kotlinx.coroutines.runBlocking
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase

abstract class MongoInterface(mongoClient: MongoClient) {
    protected val database: CoroutineDatabase = mongoClient.database

    inline fun <reified T : Any> initUpdateTimeDb(database: CoroutineDatabase, collectionName: String, crossinline onCollectionCreated: ((CoroutineCollection<T>) -> Unit) = {}): CoroutineCollection<T> {
        return runBlocking {
            return@runBlocking if (!database.listCollectionNames().contains(collectionName)) {
                // TODO add indexes
                database.createCollection(collectionName)
                val collection = database.getCollection<T>(collectionName)
                onCollectionCreated(collection)
                collection
            } else {
                database.getCollection<T>(collectionName)
            }
        }
    }
}
