package io.curity.oauthagent.behaviors.authorizationrequest

import io.curity.oauthagent.AuthorizationServerClient
import io.curity.oauthagent.OAuthParametersProvider
import io.curity.oauthagent.controller.StartAuthorizationParameters

class ParAuthorizationRequestHandler(
        private val authorizationServerClient: AuthorizationServerClient,
        private val oAuthParametersProvider: OAuthParametersProvider) : AuthorizationRequestHandler {

    override suspend fun createRequest(parameters: StartAuthorizationParameters?): AuthorizationRequestData {

        val codeVerifier = oAuthParametersProvider.getCodeVerifier()
        val state = oAuthParametersProvider.getState()

        val authorizationRequestUrl = authorizationServerClient.getAuthorizationRequestObjectUri(state, codeVerifier, parameters)

        return AuthorizationRequestData(
                authorizationRequestUrl,
                codeVerifier,
                state
        )
    }
}
