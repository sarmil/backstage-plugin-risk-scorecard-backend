package no.risc.security

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource


@Configuration
@EnableWebSecurity
class SecurityConfig(private val environment: Environment) {

@Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .authorizeHttpRequests { it.requestMatchers("/actuator/**").permitAll() }
            .authorizeHttpRequests { it.requestMatchers("/api/**").authenticated() }
            .oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = getAllowedOrigins()
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        configuration.allowedHeaders =
            listOf(
                "authorization",
                "content-type",
                "x-auth-token",
                "x-request-id",
                "contenttype",
                "Content-Type",
                "Authorization",
                "Microsoft-Id-Token",
                "GCP-Access-Token",
            )
        configuration.exposedHeaders = mutableListOf("x-auth-token")
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun jwtDecoder(): JwtDecoder? {
        val issuerUri = environment.getProperty("ISSUER_URI")
        val jwtDecoder = NimbusJwtDecoder.withIssuerLocation(issuerUri)
            .jwtProcessorCustomizer { customizer ->
                customizer.jwsTypeVerifier = DefaultJOSEObjectTypeVerifier(
                    JOSEObjectType.JOSE_JSON,
                    JOSEObjectType.JWT,
                    JOSEObjectType.JOSE,
                    JOSEObjectType("vnd.backstage.user"), // required for tokens issued by Backstage
                    null
                )
            }
            .build()


        return JwtDecoder { token -> jwtDecoder.decode(token) }
    }

    fun getAllowedOrigins(): List<String> {
        return listOf(
            "http://localhost:3000/",
            "https://sandbox.kartverket.dev",
            "https://kartverket.dev",
            "https://kv-ros-backstage-245zlcbrnq-lz.a.run.app",
        )
    }
}
