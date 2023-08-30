package net.adoptium.api.v3

import io.quarkus.arc.profile.UnlessBuildProfile
import io.quarkus.runtime.Startup
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition
import org.eclipse.microprofile.openapi.annotations.info.Info
import org.eclipse.microprofile.openapi.annotations.servers.Server
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.ApplicationPath
import jakarta.ws.rs.core.Application

@OpenAPIDefinition(
    servers = [
        Server(url = ServerConfig.SERVER),
        Server(url = ServerConfig.STAGING_SERVER)
    ],
    info = Info(title = "v3", version = "3.0.0", description = ServerConfig.DESCRIPTION)
)
@UnlessBuildProfile("test")
@ApplicationScoped
@ApplicationPath("/")
@Startup
class V3 : Application()
