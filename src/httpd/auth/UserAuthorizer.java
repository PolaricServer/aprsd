 
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
    private boolean sar = false;

    
    public UserAuthorizer() { }

    
    public UserAuthorizer(final int lvl) {
        admin = (lvl >= 2);
        sar = (lvl >= 1);
    }

    
    @Override
    public boolean isAuthorized(final WebContext context, final SessionStore ss, final List<UserProfile> profile) {
        var auth = AuthService.getAuthInfo(context);  
        if (auth==null)
            return false;
        if (admin) return auth.admin; 
        if (sar) return auth.sar || auth.admin;
        return true;
    }

 

    @Override
    public String toString() {
        return "UserAuthorizer[" + (admin? "admin" : "sar") + "]";
    }
}
