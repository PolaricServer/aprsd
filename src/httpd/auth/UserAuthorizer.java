 
package no.polaric.aprsd.http;

import org.pac4j.core.authorization.authorizer.Authorizer;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.util.CommonHelper;


import java.util.List;


/**
 */
public class UserAuthorizer implements Authorizer<CommonProfile> {

    private boolean admin = false; 

    public UserAuthorizer() { }

    public UserAuthorizer(final boolean a) {
        admin = a;
    }

    @Override
    public boolean isAuthorized(final WebContext context, final List<CommonProfile> profile) {
    
        var auth = (AuthInfo) context.getRequestAttribute("authinfo");
        return (admin ? auth.admin : auth.sar); 
    }

 

    @Override
    public String toString() {
        return "UserAuthorizer[" + (admin? "admin" : "sar") + "]";
    }
}
