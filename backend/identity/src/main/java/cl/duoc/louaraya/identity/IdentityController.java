package cl.duoc.louaraya.identity;

import cl.duoc.louaraya.common.Profile;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class IdentityController {
    private final IdentityService identity;

    IdentityController(IdentityService identity) {
        this.identity = identity;
    }

    @PostMapping("/auth/login")
    IdentityService.Session login(@RequestBody IdentityService.Login credentials) {
        return identity.login(credentials);
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    IdentityService.Session register(@RequestBody IdentityService.Registration registration) {
        return identity.register(registration);
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        identity.logout(authorization);
    }

    @GetMapping("/profiles/me")
    Profile profile(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return identity.profile(authorization);
    }

    @PatchMapping("/profiles/me")
    Profile update(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                   @RequestBody JsonNode updates) {
        return identity.update(authorization, updates);
    }
}
