package net.adoptium.api.v3.logs

import io.quarkus.logging.LoggingFilter
import java.util.logging.Filter
import java.util.logging.LogRecord

@LoggingFilter(name = "api-log-filter")
class ApiLogFilter : Filter {
    override fun isLoggable(record: LogRecord?): Boolean {
        // Frequent message that is safe to ignore
        var exclude = record?.loggerName?.equals("org.eclipse.yasson.internal.SerializationContextImpl") == true &&
            record.message?.contains("Generating incomplete JSON") == true

        exclude = exclude || record?.loggerName?.equals("io.quarkus.vertx.http.runtime.QuarkusErrorHandler") == true &&
            record.thrown?.message?.contains("Unable to serialize") == true

        return !exclude;
    }
}
