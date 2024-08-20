package net.adoptium.api.v3.dataSources.persitence.mongo

import com.mongodb.MongoCommandException
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.runBlocking

abstract class MongoInterface {

    inline fun <reified T : Any> createCollection(database: MongoDatabase, collectionName: String): MongoCollection<T> {
        runBlocking {
            try {
                database.createCollection(collectionName)
            } catch (e: MongoCommandException) {
                if (e.errorCode == 48) {
                    // collection already exists ... ignore
                } else {
                    throw e
                }
            }
        }
        return database.getCollection(collectionName, T::class.java)

    }
}
