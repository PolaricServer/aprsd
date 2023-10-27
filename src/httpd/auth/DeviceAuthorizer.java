 
package no.polaric.aprsd.http;

import org.pac4j.core.authorization.authorizer.Authorizer;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.profile.*;
import java.util.List;


/**
 */
public class DeviceAuthorizer implements Authorizer {

    public DeviceAuthorizer() { }

    
    @Override
    public boolean isAuthorized(final WebContext context, final SessionStore ss, final List<UserProfile> profile) {
        var auth = AuthService.getAuthInfo(context);  
        if (auth==null)
            return true;
        return false;
    }

 

    @Override
    public String toString() {
        return "DeviceAuthorizer";
    }
}
