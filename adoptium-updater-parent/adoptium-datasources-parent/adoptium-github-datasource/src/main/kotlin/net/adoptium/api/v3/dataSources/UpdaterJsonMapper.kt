package net.adoptium.api.v3.dataSources

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver
import com.fasterxml.jackson.datatype.jsonp.JSONPModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import jakarta.ws.rs.Produces
import jakarta.ws.rs.ext.Provider
import org.slf4j.LoggerFactory

import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler
import com.fasterxml.jackson.databind.JsonMappingException

@Provider
class UpdaterJsonMapper {
    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)

        val mapper: ObjectMapper = ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .registerModule(KotlinModule.Builder().build())
            .registerModule(JavaTimeModule())
            .registerModule(JSONPModule())
            .addHandler(CustomDeserializationProblemHandler())
    }

    private class CustomDeserializationProblemHandler() : DeserializationProblemHandler() {
        override fun handleUnknownProperty(context: DeserializationContext, parser: JsonParser, deserializer: JsonDeserializer<*>?, beanOrClass: Any?, property: String): Boolean {
          LOGGER.error("DESERIALIZATION ERROR handleUnknownProperty: "+beanOrClass+" "+property)
          return super<DeserializationProblemHandler>.handleUnknownProperty(context, parser, deserializer, beanOrClass, property)
        }

        override fun handleInstantiationProblem(context: DeserializationContext, instClass: Class<*>?, argument: Any?, t: Throwable): Any {
          LOGGER.error("DESERIALIZATION ERROR handleInstantiationProblem: "+instClass+" "+argument+" "+t)
          return super<DeserializationProblemHandler>.handleInstantiationProblem(context, instClass, argument, t)
        }

        override fun handleMissingInstantiator(context: DeserializationContext, instClass: Class<*>?, parser: JsonParser, msg: String): Any {
          LOGGER.error("DESERIALIZATION ERROR handleInstantiationProblem: "+instClass+" "+msg)
          return super<DeserializationProblemHandler>.handleMissingInstantiator(context, instClass, parser, msg)
        }

        override fun handleUnexpectedToken(context: DeserializationContext, targetType: Class<*>?, token: JsonToken, parser: JsonParser, msg: String) : Any {
          LOGGER.error("DESERIALIZATION ERROR handleUnexpectedToken: "+targetType+" "+token+" "+msg)
          return super<DeserializationProblemHandler>.handleUnexpectedToken(context, targetType, token, parser, msg)
        }

        override fun handleUnknownTypeId(context: DeserializationContext, baseType: JavaType, subTypeId: String, idResolver: TypeIdResolver, msg: String) : JavaType {
          LOGGER.error("DESERIALIZATION ERROR handleUnknownTypeId: "+baseType+" "+subTypeId+" "+idResolver+" "+msg)
          return super<DeserializationProblemHandler>.handleUnknownTypeId(context, baseType, subTypeId, idResolver, msg)
        }

        override fun handleWeirdKey(context: DeserializationContext, rawKeyType: Class<*>?, keyValue: String, msg: String) : Any {
          LOGGER.error("DESERIALIZATION ERROR handleWeirdKey: "+rawKeyType+" "+keyValue+" "+msg)
          return super<DeserializationProblemHandler>.handleWeirdKey(context, rawKeyType, keyValue, msg)
        }

        override fun handleWeirdNumberValue(context: DeserializationContext, targetType: Class<*>?, valueToConvert: Number, msg: String) : Any {
          LOGGER.error("DESERIALIZATION ERROR handleWeirdNumberValue: "+targetType+" "+valueToConvert+" "+msg)
          return super<DeserializationProblemHandler>.handleWeirdNumberValue(context, targetType, valueToConvert, msg)
        }

        override fun handleWeirdStringValue(context: DeserializationContext, targetType: Class<*>?, valueToConvert: String, msg: String) : Any {
          LOGGER.error("DESERIALIZATION ERROR handleWeirdStringValue: "+targetType+" "+valueToConvert+" "+msg)
          return super<DeserializationProblemHandler>.handleWeirdStringValue(context, targetType, valueToConvert, msg)
        }
    }


    @Produces
    fun getObjectMapper(): ObjectMapper {
        return mapper
    }
}
