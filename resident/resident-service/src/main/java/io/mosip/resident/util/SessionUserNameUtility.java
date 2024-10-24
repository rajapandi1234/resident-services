package io.mosip.resident.util;

import io.mosip.resident.constant.ResidentConstants;
import io.mosip.resident.exception.ApisResourceAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * @author Kamesh Shekhar Prasad
 */

@Component
public class SessionUserNameUtility {

    @Autowired
    private Environment environment;

    @Autowired
    private AvailableClaimValueUtility availableClaimValueUtility;

    public String getSessionUserName() {
        String name = null;
        try {
            name = availableClaimValueUtility.getAvailableClaimValue(this.environment.getProperty(ResidentConstants.NAME_FROM_PROFILE));
            if (name == null || name.trim().isEmpty()) {
                name = ResidentConstants.UNKNOWN;
            }
        } catch (ApisResourceAccessException e) {
            throw new RuntimeException(e);
        }
        return name;
    }
}
