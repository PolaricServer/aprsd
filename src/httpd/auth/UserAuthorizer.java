 
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
public class UserAuthorizer implements Authorizer {

    private boolean admin = false; 

    public UserAuthorizer() { }

    public UserAuthorizer(final boolean a) {
        admin = a;
    }

    @Override
    public boolean isAuthorized(final WebContext context, final SessionStore ss, final List<UserProfile> profile) {
        var aa = context.getRequestAttribute("authinfo"); 
        if (aa.isPresent()) {
            var auth = (AuthInfo) aa.get();
            return (admin ? auth.admin : auth.sar); 
        }
        else
            return false;
    }

 

    @Override
    public String toString() {
        return "UserAuthorizer[" + (admin? "admin" : "sar") + "]";
    }
}
