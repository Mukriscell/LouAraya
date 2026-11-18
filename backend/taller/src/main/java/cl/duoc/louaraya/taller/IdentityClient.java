package cl.duoc.louaraya.taller;

import cl.duoc.louaraya.common.ApiFailure;
import cl.duoc.louaraya.common.Bearer;
import cl.duoc.louaraya.common.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

@Component
class IdentityClient {
    private final RestClient client;

    IdentityClient(RestClient.Builder builder, @Value("${louaraya.identity-url}") String identityUrl) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.client = builder.baseUrl(identityUrl).requestFactory(requestFactory).build();
    }

    Profile requireProfile(String authorization) {
        Bearer.require(authorization);
        try {
            Profile profile = client.get().uri("/profiles/me").header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve().body(Profile.class);
            if (profile != null) {
                return profile;
            }
            throw unavailable();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                throw ApiFailure.unauthorized();
            }
            throw unavailable();
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    private ApiFailure unavailable() {
        return new ApiFailure(HttpStatus.SERVICE_UNAVAILABLE, "El servicio de identidad no está disponible. Intenta más tarde.");
    }
}
