package net.adoptium.api.v3.dataSources.persitence.mongo

import com.mongodb.MongoCommandException
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.config.APIConfig
import net.adoptium.api.v3.config.DeploymentType
import org.slf4j.LoggerFactory

abstract class MongoInterface {

    companion object {
        @JvmStatic
        val LOGGER = LoggerFactory.getLogger(MongoInterface::class.java)!!
    }

    inline fun <reified T : Any> createCollection(database: MongoDatabase, collectionName: String): MongoCollection<T> {

        runBlocking {
            try {
                database.createCollection(collectionName)
            } catch (e: MongoCommandException) {
                if (e.errorCode == 48) {
                    // collection already exists ... ignore
                } else {
                    if (APIConfig.DEPLOYMENT_TYPE == DeploymentType.UPDATER) {
                        LOGGER.warn("User does not have permission to create collection $collectionName", e)
                        throw e
                    }
                }
            }
        }
        return database.getCollection(collectionName, T::class.java)

    }
}
