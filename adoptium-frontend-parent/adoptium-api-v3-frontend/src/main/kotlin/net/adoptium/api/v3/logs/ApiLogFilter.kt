package net.adoptium.api.v3.logs

import io.quarkus.logging.LoggingFilter
import io.quarkus.vertx.http.runtime.QuarkusErrorHandler
import java.util.logging.Filter
import java.util.logging.LogRecord

@LoggingFilter(name = "api-log-filter")
class ApiLogFilter : Filter {
    override fun isLoggable(record: LogRecord?): Boolean {
        // Frequent message that is safe to ignore
        val exclude = record?.loggerName?.equals(QuarkusErrorHandler::class.java.name) == true &&
            record.message?.contains("Unable to serialize property") == true

        return !exclude;
    }
}
