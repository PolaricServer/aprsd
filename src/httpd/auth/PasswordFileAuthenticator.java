/* 
 * Copyright (C) 2017 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
 
 
package no.polaric.aprsd.http;

import org.pac4j.core.context.Pac4jConstants;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.exception.CredentialsException;
import org.pac4j.core.exception.HttpAction;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.core.credentials.UsernamePasswordCredentials;
import org.pac4j.core.credentials.authenticator.Authenticator;
import java.io.*; 
import java.util.*; 
import org.apache.commons.codec.digest.*;




/**
 * Use a simple password file. For small numbers of user/password pairs. 
 * For larger numbers, use a database instead. 
 */
public class PasswordFileAuthenticator implements Authenticator<UsernamePasswordCredentials> {

    private Map<String, String> _pwmap = new HashMap<String, String>();

    
    
    public PasswordFileAuthenticator(String file) {
        try {
           BufferedReader rd = new BufferedReader(new FileReader(file));
           while (rd.ready() )
           {
               String line = rd.readLine();
               if (!line.startsWith("#")) 
               {                 
                   String[] x = line.split(":");  
                   String username = x[0].trim();
                   String passwd = x[1].trim();
                   _pwmap.put(username, passwd);
                   String tt = _pwmap.get(username); 
               }
           }
        }
        catch (IOException e) {
           AuthService._api.log().log("WARNING: Couldn't open htpasswd file: "+e.getMessage());
        } 
    }
    
    
    

    @Override
    public void validate(final UsernamePasswordCredentials credentials, final WebContext context) 
           throws HttpAction, CredentialsException 
    {
        if (credentials == null) {
            throwsException("No credential");
        }
        String username = credentials.getUsername();
        String password = credentials.getPassword();
        if (CommonHelper.isBlank(username)) {
            throwsException("Username cannot be blank");
        }
        if (CommonHelper.isBlank(password)) {
            throwsException("Password cannot be blank");
        }

        String storedPwd = _pwmap.get(username);
        if (storedPwd == null)
           throwsException("Unknown user: '"+username+"'");
           
        if (storedPwd.startsWith("$apr1$")) { 
           if (!storedPwd.equals(Md5Crypt.apr1Crypt(password, storedPwd)))
              throwsException("Invalid password");
        }
        else
           throwsException("Unknown password format for user: "+username);

        /* Create a user profile */
        final CommonProfile profile = new CommonProfile();
        profile.setId(username);
        profile.addAttribute(Pac4jConstants.USERNAME, username);
        credentials.setUserProfile(profile);
    }

    
    
    
    protected void throwsException(final String message) throws CredentialsException {
        throw new CredentialsException(message);
    }
    
    
}
