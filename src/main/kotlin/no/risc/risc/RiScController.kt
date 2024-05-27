package no.risc.risc

import no.risc.exception.exceptions.InvalidAccessTokensException
import no.risc.github.GithubAccessToken
import no.risc.github.GithubAppConnector
import no.risc.github.GithubStatus
import no.risc.infra.connector.GoogleApiConnector
import no.risc.infra.connector.models.AccessTokens
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.risc.models.RiScWrapperObject
import no.risc.risc.models.UserInfo
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/risc")
class RiScController(
    private val riScService: RiScService,
    private val githubAppConnector: GithubAppConnector,
    private val googleApiConnector: GoogleApiConnector,
) {
    @GetMapping("/{repositoryOwner}/{repositoryName}/all")
    fun getRiScFilenames(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
    ): ResponseEntity<List<RiScContentResultDTO>> {
        val accessTokens = getAccessTokens(gcpAccessToken, repositoryName)
        if (!accessTokens.isValid()) {
            return ResponseEntity.status(401).body(listOf(RiScContentResultDTO.INVALID_ACCESS_TOKENS))
        }

        val result = riScService.fetchAllRiScs(repositoryOwner, repositoryName, accessTokens)

        return ResponseEntity.ok().body(result)
    }

    @PostMapping("/{repositoryOwner}/{repositoryName}", produces = ["text/plain"])
    fun createNewRiSc(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @RequestBody riSc: RiScWrapperObject,
    ): ProcessRiScResultDTO {
        val accessTokens = getAccessTokens(gcpAccessToken, repositoryName)

        if (!(accessTokens.isValid() && riSc.userInfo.isValid())) {
            throw InvalidAccessTokensException(
                "Invalid risk scorecard result: ${ProcessingStatus.InvalidAccessTokens.message}"
            )
        }

        return riScService.createRiSc(
                owner = repositoryOwner,
                repository = repositoryName,
                accessTokens = accessTokens,
                content = riSc,
            )
    }

    @PutMapping("/{repositoryOwner}/{repositoryName}/{id}", produces = ["application/json"])
    fun editRiSc(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable id: String,
        @PathVariable repositoryName: String,
        @RequestBody riSc: RiScWrapperObject,
    ): ProcessRiScResultDTO {
        val accessTokens = getAccessTokens(gcpAccessToken, repositoryName)
        if (!(accessTokens.isValid() && riSc.userInfo.isValid())) {
            throw InvalidAccessTokensException(
                "Invalid risk scorecard result: ${ProcessingStatus.InvalidAccessTokens.message}"
            )
        }

        return riScService.updateRiSc(repositoryOwner, repositoryName, id, riSc, accessTokens)
    }

    @PostMapping("/{repositoryOwner}/{repositoryName}/publish/{id}", produces = ["application/json"])
    fun sendRiScForPublishing(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable id: String,
        @RequestBody userInfo: UserInfo,
    ): ResponseEntity<PublishRiScResultDTO> {
        val accessTokens = getAccessTokens(gcpAccessToken, repositoryName)
        if (!(accessTokens.isValid() && userInfo.isValid())) {
            return ResponseEntity.status(401).body(PublishRiScResultDTO.INVALID_ACCESS_TOKENS)
        }

        val result =
            riScService.publishRiSc(
                owner = repositoryOwner,
                repository = repositoryName,
                riScId = id,
                accessTokens = accessTokens,
                userInfo = userInfo,
            )

        return when (result.status) {
            ProcessingStatus.CreatedPullRequest -> ResponseEntity.ok().body(result)
            else -> ResponseEntity.internalServerError().body(result)
        }
    }

    @GetMapping("/schemas/latest")
    fun fetchLatestJSONSchema(): ResponseEntity<String> {
        val result = riScService.fetchLatestJSONSchema()
        return when (result.status) {
            GithubStatus.Success -> ResponseEntity.ok().body(result.data)
            else -> ResponseEntity.internalServerError().body(result.status.toString())
        }
    }

    private fun getAccessTokens(
        gcpAccessToken: String,
        repositoryName: String,
    ): AccessTokens {
        if (!googleApiConnector.validateAccessToken(gcpAccessToken)) {
            return AccessTokens(
                GithubAccessToken(""),
                GCPAccessToken(""),
            )
        }
        val githubAccessTokenFromApp = githubAppConnector.getAccessTokenFromApp(repositoryName)
        val accessTokens =
            AccessTokens(
                githubAccessTokenFromApp,
                GCPAccessToken(gcpAccessToken),
            )
        return accessTokens
    }
}
