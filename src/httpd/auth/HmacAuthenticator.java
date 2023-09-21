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

import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.exception.CredentialsException;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.credentials.TokenCredentials;
import org.pac4j.core.credentials.authenticator.Authenticator;

import java.io.*; 
import java.util.*; 
import no.polaric.aprsd.*;
import org.apache.commons.codec.digest.*;
import java.security.MessageDigest;




/**
 * Use a simple password file. For small numbers of user/password pairs. 
 * For larger numbers, use a database instead. 
 */
public class HmacAuthenticator implements Authenticator {

    private final Map<String, String> _keymap = new HashMap<String, String>();
    private final Set<String> _devices = new HashSet<String> ();
    private ServerAPI _api; 
    private final String _file;   
    private UserDb _users;
    private final DuplicateChecker _dup = new DuplicateChecker(2000);
        // A fixed size of this can be a vulnerability?
    
    
    
    public HmacAuthenticator(ServerAPI api, String file, UserDb lu) {
        _api = api; 
        _file = file;
        _users = lu;
        load();
    }
       
       
       
    public final void setUserKey(String userid, String key) {
        _keymap.remove(userid);
        _keymap.put(userid, key);
    }

    
    /**
     * Load keys from file.
     * Assume that these are not personal users but device/server groups. 
     */
    private void load() {
        try {
            /* Open key file */
            BufferedReader rd = new BufferedReader(new FileReader(_file));
            while (rd.ready() )
            {
                /* Read a line */
                String line = rd.readLine();
                if (!line.startsWith("#") && line.length() > 1) {
                    if (line.matches(".*:.*"))
                    {                 
                        String[] x = line.split(":");  
                        String userid = x[0].trim();
                        String key = x[1].trim();
                        _keymap.put(userid, key);
                        _devices.add(userid);
                    }
                    else
                        _api.log().warn("HmacAuthenticator", "Bad line in key file: "+line);
                }
            }
        }
        catch (IOException e) {
           _api.log().warn("HmacAuthenticator", "Couldn't open hmackeys file: "+e.getMessage());
        } 
    }
    
    
    
    @Override
    public void validate(Credentials cred, WebContext context, SessionStore sstore) 
           throws CredentialsException 
    {
        if (cred == null) 
            return; // throwsException("No credential");
        if (! (cred instanceof TokenCredentials))
            throwsException("Credentials is not a token type");
 
        /* We use a token credentials type - it is just a string that must be parsed further */
        TokenCredentials tcred = (TokenCredentials) cred;
        String[] token = tcred.getToken().split(";");
        if (token.length < 3 || token.length > 4)
            throwsException("Invalid Authorization header");
        String userid = token[0].trim();
        String nonce = token[1].trim();
        String rmac = token[2].trim();
        if (rmac==null && nonce==null)
            throwsException("Lack of authentication header info");
            
        /* The role-name is optional */
        String role = null;
        if (token.length == 4)
            role = token[3].trim();
            
        /* Check hmac auth */
        Optional<String> bodyHash = context.getRequestAttribute("bodyHash");
        String bh = (bodyHash.isPresent() ? bodyHash.get() : "");
        User ui = checkAuth(userid, nonce, rmac, bh);
        
        /* Create a user profile */
        _api.log().debug("HmacAuthenticator", "Validate: creating user profile");
        final CommonProfile profile = new CommonProfile();
        profile.setId(userid);
   
        /* 
         * If ui is null here, the userid is not a personal user but a server or a device. 
         * The kay was found in the keyfile. 
         */
        profile.addAttribute("userInfo", ui);
        
        profile.addAttribute("role", getRole(ui, role));
        tcred.setUserProfile(profile);
    }

    
    public Group getRole(User u, String rname) {
        if (rname==null)
            return null;
        Group g = _users.getGroupDb().get(rname);
        if (g==null || !u.roleAllowed(g)) {
            _api.log().debug("HmacAuthenticator", "Group/role not known or not allowed: "+rname);
            return null;
        }
        return g;
    }
    
        
        
    /* Check authentication fields: nonce, hmac and data) */
    public final User checkAuth(String userid, String nonce, String rmac, String data) 
        throws CredentialsException
    {
        if (_dup.contains(nonce)) 
            throwsException("Duplicate request");

        User ui = _users.get(userid);
        if (ui==null && !_devices.contains(userid))
            throwsException("Unknown userid: "+userid);

        /* Get key from keymap.get(userid) */
        String key = _keymap.get(userid);
        if (key==null)
            throwsException("No key for user: "+userid+". Login needed");
        
        /* Compute a mac and check if it is equal to the remote mac */
        if (SecUtils.hmacB64(nonce + data, key, 44).equals(rmac))
            throwsException("HMAC mismatch");
        _dup.add(nonce);
        return ui;
    }
    
    

    
    protected void throwsException(final String message) throws CredentialsException {
        _api.log().info("HmacAuthenticator", "Auth failed: " + message);
         throw new CredentialsException(message);
    }
    
    
}
