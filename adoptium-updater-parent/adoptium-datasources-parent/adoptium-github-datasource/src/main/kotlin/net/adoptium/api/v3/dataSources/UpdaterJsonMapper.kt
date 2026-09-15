package net.adoptium.api.v3.dataSources

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.ws.rs.Produces
import jakarta.ws.rs.ext.Provider
import org.slf4j.LoggerFactory
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JavaType
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.deser.DeserializationProblemHandler
import tools.jackson.databind.deser.ValueInstantiator
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.jsontype.TypeIdResolver
import tools.jackson.datatype.jsonp.JSONPModule
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.KotlinModule

@Provider
class UpdaterJsonMapper {
    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)

        val mapper: JsonMapper = JsonMapper.builder()
            .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .addModule(KotlinModule.Builder().disable(KotlinFeature.StrictNullChecks).build())
            .addModule(JSONPModule())
            .addHandler(CustomDeserializationProblemHandler())
            .build()
    }

    private class CustomDeserializationProblemHandler : DeserializationProblemHandler() {
        override fun handleUnknownProperty(context: DeserializationContext, parser: JsonParser, deserializer: ValueDeserializer<*>?, beanOrClass: Any?, property: String): Boolean {
          LOGGER.error("DESERIALIZATION ERROR handleUnknownProperty: $beanOrClass $property")
          return super.handleUnknownProperty(context, parser, deserializer, beanOrClass, property)
        }

        override fun handleInstantiationProblem(context: DeserializationContext, instClass: Class<*>?, argument: Any?, t: Throwable): Any {
          LOGGER.error("DESERIALIZATION ERROR handleInstantiationProblem: $instClass $argument $t")
          return super.handleInstantiationProblem(context, instClass, argument, t)
        }

        override fun handleMissingInstantiator(context: DeserializationContext, instClass: Class<*>?, valueInstantiator: ValueInstantiator?, parser: JsonParser, msg: String): Any {
          LOGGER.error("DESERIALIZATION ERROR handleInstantiationProblem: $instClass $msg")
          return super.handleMissingInstantiator(context, instClass, valueInstantiator, parser, msg)
        }

        override fun handleUnexpectedToken(context: DeserializationContext, targetType: JavaType, token: JsonToken, parser: JsonParser, msg: String) : Any {
          LOGGER.error("DESERIALIZATION ERROR handleUnexpectedToken: $targetType $token $msg")
          return super.handleUnexpectedToken(context, targetType, token, parser, msg)
        }

        override fun handleUnknownTypeId(context: DeserializationContext, baseType: JavaType, subTypeId: String, idResolver: TypeIdResolver, msg: String) : JavaType {
          LOGGER.error("DESERIALIZATION ERROR handleUnknownTypeId: $baseType $subTypeId $idResolver $msg")
          return super.handleUnknownTypeId(context, baseType, subTypeId, idResolver, msg)
        }

        override fun handleWeirdKey(context: DeserializationContext, rawKeyType: Class<*>?, keyValue: String, msg: String) : Any {
          LOGGER.error("DESERIALIZATION ERROR handleWeirdKey: $rawKeyType $keyValue $msg")
          return super.handleWeirdKey(context, rawKeyType, keyValue, msg)
        }

        override fun handleWeirdNumberValue(context: DeserializationContext, targetType: Class<*>?, valueToConvert: Number, msg: String) : Any {
          LOGGER.error("DESERIALIZATION ERROR handleWeirdNumberValue: $targetType $valueToConvert $msg")
          return super.handleWeirdNumberValue(context, targetType, valueToConvert, msg)
        }

        override fun handleWeirdStringValue(context: DeserializationContext, targetType: Class<*>?, valueToConvert: String, msg: String) : Any {
          LOGGER.error("DESERIALIZATION ERROR handleWeirdStringValue: $targetType $valueToConvert $msg")
          return super.handleWeirdStringValue(context, targetType, valueToConvert, msg)
        }
    }


    @Produces
    fun getObjectMapper(): JsonMapper {
        return mapper
    }
}
