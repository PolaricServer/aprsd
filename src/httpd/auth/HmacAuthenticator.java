/* 
 * Copyright (C) 2017-2024 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
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
import java.net.http.*;
import no.polaric.aprsd.*;
import org.apache.commons.codec.digest.*;
import java.security.MessageDigest;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;



/**
 * Use a simple password file. For small numbers of user/password pairs. 
 * For larger numbers, use a database instead. 
 */
public class HmacAuthenticator implements Authenticator {

    private static final long MAX_SESSION_LENGTH = 7 * 24 * 60 *60 * 1000;
    
        /* Keys given to users. username->key mapping */
    private final Map<String, String> _keymap = new HashMap<String, String>();
        /* Devices (peer PS instances) with a key */
    private final Set<String> _devices = new HashSet<String> ();
        /* Person users with a timestamp */
    private final Map<String, Long> _userlogins = new HashMap<String, Long>();
    
    
    private ServerAPI _api; 
    private final String _ukeyfile, _dkeyfile;   
    private UserDb _users;
    private final DuplicateChecker _dup = new DuplicateChecker(2000);
        // A fixed size of this can be a vulnerability?
    
    
    
    public HmacAuthenticator(ServerAPI api, String dfile, String ufile, UserDb lu) {
        _api = api; 
        _dkeyfile = dfile;
        _ukeyfile = ufile;
        _users = lu;
        loadDevices();
        loadLogins();
    }
       
       
    public final String getUserKey(String userid) {
        return _keymap.get(userid);
    }
    
       
    /**
     * Set session-key for a user. 
     * This is done when user successfully logs in. A timestamp is also set to be able
     * to expire the session. 
     */
    public final void setUserKey(String userid, String key) {
        _keymap.remove(userid);
        _keymap.put(userid, key);
        _userlogins.remove(userid);
        _userlogins.put(userid, (new Date()).getTime());
    }
    
    
    
    public final void expireUserKey(String userid) {
        long now = (new Date()).getTime();
        Long ts = _userlogins.get(userid);
        if (ts == null)
            return;
        if (ts + MAX_SESSION_LENGTH < now) {
            _userlogins.remove(userid);
            _keymap.remove(userid);
        }
    }
    
    
    
    
    /**
     * Save keys to file.
     * Assume that these are personal user-logins. 
     */
    public void saveLogins() {
        try {
            final PrintWriter wr = new PrintWriter(new FileWriter(_ukeyfile));
            _keymap.forEach( (k, v) -> {
                Long ts = _userlogins.get(k);
                if (ts==null)
                    ts = (new Date()).getTime();
                if (!_devices.contains(k))
                    wr.println(k+":"+v+":"+ts);
            });
            wr.close();
            Path path = Paths.get(_ukeyfile); 
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------")); 
        }
        catch (IOException e) {
           _api.log().warn("HmacAuthenticator", "Couldn't open key file: "+e.getMessage());
        } 
    }
    
       
    /**
     * Load keys from file.
     * Assume that these are personal user-logins. 
     */
    private void loadLogins() {
        loadKeys(_ukeyfile, false); 
    }
    
    
    /**
     * Load keys from file.
     * Assume that these are not personal users but device/server groups. 
     */
    private void loadDevices() {
        loadKeys(_dkeyfile, true); 
    }
    
    
    
    /**
     * Load keys from file.
     */
    private void loadKeys(String file, boolean dev) {
        try {
            /* Open key file */
            BufferedReader rd = new BufferedReader(new FileReader(file));
            while (rd.ready() )
            {
                /* Read a line */
                String line = rd.readLine();
                if (!line.startsWith("#") && line.length() > 1) {
                    if (line.matches(".*:.*(:.*)?"))
                    {                 
                        String[] x = line.split(":");  
                        String userid = x[0].trim();
                        String key = x[1].trim();
                        _keymap.put(userid, key);
                        if (dev)
                            _devices.add(userid);
                        else {
                            long ts = (new Date()).getTime();
                            if (x.length >= 3 && x[2].matches("[0-9]+"))
                                ts = Long.parseLong(x[2].trim());
                            _userlogins.put(userid, ts);
                        }
                    }
                    else
                        _api.log().warn("HmacAuthenticator", "Bad line in key file: "+line);
                }
            }
            rd.close();
        }
        catch (IOException e) {
           _api.log().warn("HmacAuthenticator", "Couldn't open key file: "+e.getMessage());
        } 
    }
    
    
    
    @Override
    public void validate(Credentials cred, WebContext context, SessionStore sstore) 
           throws CredentialsException 
    {
        if (cred == null) 
            throwsException("No credential");
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
        final CommonProfile profile = new CommonProfile();
        profile.setId(userid);
   
        /* 
         * If ui is null here, the userid is not a personal user but a service-id for use with devices or
         * peer servers. The kay was found in the keyfile. 
         */
        if (ui==null) 
            profile.addAttribute("service", userid);
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
        expireUserKey(userid);
        String key = _keymap.get(userid);
        if (key==null)
            throwsException("No key for user: "+userid+". Login needed");

        /* Compute a mac and check if it is equal to the remote mac */
        if (!SecUtils.hmacB64(nonce + data, key, 44).equals(rmac)) {
            throwsException("HMAC mismatch ("+userid+")");
        }
        _dup.add(nonce);
        return ui;
    }
    
    
    
    public final String authString(String body, String userid) {
        String key = _keymap.get(userid);
        if (key==null) {
            _api.log().warn("HmacAuthenticator", "Key not found for user: "+userid);
            return "";
        }
        String nonce = SecUtils.b64encode( SecUtils.getRandom(8) );
        String data = (body==null || body.length() == 0 ? "" : SecUtils.xDigestB64(body, 44));
        String hmac = SecUtils.hmacB64(nonce+data, key, 44);
        return userid + ";" + nonce + ";" + hmac;
    }
    
    
    
    /* Add headers to http request */
    public final HttpRequest.Builder addAuth(HttpRequest.Builder bld, String body, String userid) {
        /* Generate authorization header */
        bld.header("Authorization", "Arctic-Hmac "+ authString(body, userid)); 
        return bld;
    }


    
    protected void throwsException(final String message) throws CredentialsException {
        _api.log().debug("HmacAuthenticator", "Auth failed: " + message);
         throw new CredentialsException(message);
    }
    
    
}
