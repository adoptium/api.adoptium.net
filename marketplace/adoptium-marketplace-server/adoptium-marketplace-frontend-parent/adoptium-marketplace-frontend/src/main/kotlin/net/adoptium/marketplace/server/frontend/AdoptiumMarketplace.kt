package net.adoptium.marketplace.server.frontend

import io.quarkus.arc.profile.UnlessBuildProfile
import io.quarkus.runtime.Startup
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition
import org.eclipse.microprofile.openapi.annotations.info.Info
import org.eclipse.microprofile.openapi.annotations.servers.Server
import javax.enterprise.context.ApplicationScoped
import javax.ws.rs.ApplicationPath
import javax.ws.rs.core.Application

@OpenAPIDefinition(
    servers = [
        Server(url = ServerConfig.SERVER),
        Server(url = ServerConfig.STAGING_SERVER)
    ],
    info = Info(title = "AdoptiumMarketplace", version = "1.0.0", description = ServerConfig.DESCRIPTION)
)
@UnlessBuildProfile("test")
@ApplicationScoped
@ApplicationPath("/")
@Startup
class AdoptiumMarketplace : Application()
