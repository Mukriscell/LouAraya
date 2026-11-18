package cl.duoc.louaraya.taller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@RestController
class PushController {
    private final IdentityClient identities;
    private final CampaignPush push;

    PushController(IdentityClient identities, CampaignPush push) {
        this.identities = identities;
        this.push = push;
    }

    @PutMapping("/push/devices/{installationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void register(@RequestHeader(value = AUTHORIZATION, required = false) String auth,
                  @PathVariable String installationId, @RequestBody PushBroker.Subscription subscription) {
        push.register(identities.requireProfile(auth), installationId, subscription);
    }

    @DeleteMapping("/push/devices/{installationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unregister(@RequestHeader(value = AUTHORIZATION, required = false) String auth,
                    @PathVariable String installationId) {
        push.unregister(identities.requireProfile(auth), installationId);
    }
}
