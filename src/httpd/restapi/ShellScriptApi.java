 
/* 
 * Copyright (C) 2020-2023 by Øyvind Hanssen (ohanssen@acm.org)
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
import spark.Request;
import spark.Response;
import spark.route.Routes;
import static spark.Spark.get;
import static spark.Spark.put;
import static spark.Spark.*;
import java.util.*; 
import java.io.*;
import no.polaric.aprsd.*;
import java.lang.ProcessBuilder.Redirect;
import java.util.concurrent.TimeUnit; 



/**
 * Implement REST API for calling shell script.  
 */
 
public class ShellScriptApi extends ServerBase {

    private ServerAPI _api; 
    private Map<String, Script> _scripts = new HashMap<String, Script>();
    private String _fname;
    private String _sdir;
    private File   _slog;
    
    public ShellScriptApi(ServerAPI api) {
        super(api);
        _api = api;
        /* Set up config */
        String confdir = System.getProperties().getProperty("confdir", "."); 
        _fname = confdir+"/scripts.conf";
        _sdir = System.getProperties().getProperty("scriptdir", confdir+"/scripts");
        String logfile = System.getProperties().getProperty("logdir", ".")+"/scripts.log";
      
        if ( readConfig(_fname) ) 
            /* If config file exists, set up log file for script output */
            _slog = new File(logfile);
        
        /* Scan subdirectory script-conf.d for additional config files 
         * placed there by plugins. 
         */
        File sconfdir = new File(confdir+"/script-conf.d");
        File[] files = sconfdir.listFiles( new FileFilter() {
            public boolean accept(File x)
                { return x.canRead() && x.getName().matches(".*\\.conf"); }
        });
        for (File f : files) {
            if (readConfig(f.getAbsolutePath()) && _slog == null)
                _slog = new File(logfile);
        }
    }
    
    
    public static class Script {
        public ScriptInfo sinfo;
        public String cmd;
        public int nargs;
        public boolean rtext;
        public boolean longrun;
        public Script (String name, String c, int n, boolean t, boolean lr, String descr) {
            sinfo = new ScriptInfo(name, descr);
            cmd = c;
            nargs = n;
            rtext = t;
            longrun = lr;
        }
    }
    
    
    /* Info about a command/script */
    public static class ScriptInfo {
        public String name;
        public String descr;
        public ScriptInfo(String n, String d) {
            name = n; descr = d;
        }
    }
    
    
    /* Arguments that can be used when invoking a command/script */
    public static class ScriptArg {
        public String[] args;
    }
    

    
    public static class ProcessRunner {
        
        private ProcessBuilder _pb; 
        private Thread _thread;
        private Script _script;
        private String _rtext; 
        private String _uid; 
        private ServerAPI _api;   
        private static final int NOT_EXPIRE = 60; 
        
        
        public ProcessRunner(ServerAPI api, String uid, ProcessBuilder p, Script scr) {
            _pb = p;
            _script = scr;
            _api = api; 
            _uid = uid;
        }
        
        public String getText() 
            { return _rtext; }
            
        
        public int runAndWait(int timeout) throws IOException, InterruptedException {
            Process p = _pb.start();
            if (p.waitFor(timeout, TimeUnit.SECONDS)) {
                _rtext = null;
                        
                /* If return text (from stdout) is requested */
                if (_script.rtext) {
                    _rtext = "";
                    BufferedReader bri = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    while ((line = bri.readLine()) != null)
                        _rtext += "\r\n" + line;
                }
                return p.exitValue(); 
            }    
            else {
                p.destroyForcibly();
                return -1;
            }
        }
        
        
        public void startDetached()  { 
            Thread thread = new Thread( () -> {
                try {
                    int res = runAndWait(14400); 
                    if (res == 0) {
                        _api.getWebserver().notifyUser(_uid, 
                            new ServerAPI.Notification("system", "system", 
                                _script.sinfo.name +": "+ (getText()!=null ? getText() : "success"), new Date(), NOT_EXPIRE));
                    }    
                    else if (res == -1) {
                        _api.getWebserver().notifyUser(_uid, 
                            new ServerAPI.Notification("error", "system", 
                                _script.sinfo.name +": killed (timeout): ", new Date(), NOT_EXPIRE));
                    }   
                    else {
                        _api.getWebserver().notifyUser(_uid, 
                            new ServerAPI.Notification("error", "system", 
                                _script.sinfo.name + ": "+(getText()!=null ? "("+res+") "+getText() : "Error ("+res+")"), 
                                new Date(), NOT_EXPIRE));
                    }
                } catch (Exception e) {
                    e.printStackTrace(System.out);
                }
                
            }); 
            thread.start();
        }
        
    }
    
    
    /** 
     * Return an error status message to client 
     */
    public String ERROR(Response resp, int status, String msg)
      { resp.status(status); return msg; }
      
      
    /*
     * config file consists of lines of the format: 
     *   name command number-of-args description
     */ 
    private boolean readConfig(String file) {   
        BufferedReader  rd;        
        try {
            rd = new BufferedReader(new FileReader(file));
            int lineno = 0;
            while (rd.ready()) {
                lineno++;
                String line = rd.readLine();
                if (!line.startsWith("#") && !(line.length() == 0) && !line.matches("\\s+")) {               
                    String[] x = line.split("\\s+", 6);  
                    if (x.length < 5 ||  
                        !x[0].matches("[a-zA-Z0-9\\-\\_]+") || 
                        !x[1].matches("[a-zA-Z0-9\\.\\-\\_]+") || !x[2].matches("[0-9]+") ||
                        !x[3].toUpperCase().matches("TRUE|FALSE") ||
                        !x[4].toUpperCase().matches("TRUE|FALSE"))
                    {
                        _api.log().warn("ShellScriptApi", "Syntax error in config. Line: "+lineno);
                        continue;
                    }
                    int nargs = Integer.parseInt(x[2]);
                    boolean txt = Boolean.parseBoolean(x[3]);
                    boolean longr = Boolean.parseBoolean(x[4]);
                    _scripts.put(x[0], new Script(x[0], x[1], nargs, txt, longr, x[5]));
                }
            }
            return true;
        }
        catch (FileNotFoundException  e) 
            { _api.log().error("ShellScriptApi", "No config file present."); }
        catch (Exception  e) 
            { _api.log().error("ShellScriptApi", ""+e); }
        return false; 
    }
    
    
      
    
    /** 
     * Set up the webservices. 
     */
    public void start() {     
       
        
        /* 
         * GET /scripts 
         * Get list of available commands/scripts 
         */
        get("/scripts", "application/json", (req, resp) -> {
            List<ScriptInfo> res = new ArrayList<ScriptInfo>(); 
            for (Script x: _scripts.values())
                res.add(x.sinfo);
            return res;
        }, ServerBase::toJson );
        

        
        /* 
         * POST /scripts/<ident> 
         * Execute a script/command. Arguments given as JSON data
         */
        post("/scripts/*", (req, resp) -> {
            var name = req.splat()[0];
            var script = _scripts.get(name);
            var uid = getAuthInfo(req).userid;
            
            try {
                ScriptArg arg = null;
                if (req.body().length() > 0) {
                    arg = (ScriptArg) 
                        ServerBase.fromJson(req.body(), ScriptArg.class);
                    if (arg==null)
                        return ERROR(resp, 400, "Couldn't parse input");   
                }
                if (script == null)
                    return ERROR(resp, 404, "Script "+name+" not found");   
                    
                if ((arg==null || arg.args==null) && script.nargs > 0)
                    return ERROR(resp, 400, "Script "+name+": Missing arguments");
                
                if (arg != null && arg.args != null && script.nargs != arg.args.length)
                    return ERROR(resp, 400, "Script "+name+": Expected "+script.nargs+" arguments, got "+arg.args.length);
                
                String cmd = _sdir+"/"+script.cmd;
                List<String> cmdarg = new ArrayList<String>(); 
                cmdarg.add(cmd);
                if (script.nargs > 0) 
                    for (String a : arg.args)
                        cmdarg.add(a);

                _api.log().debug("ShellScriptApi", "Invoking script: "+cmd);
                ProcessBuilder pb = new ProcessBuilder(cmdarg);
                pb.redirectError(Redirect.appendTo(_slog));
                if (!script.rtext) 
                    pb.redirectOutput(Redirect.appendTo(_slog));
                
                /* 
                 * By default, scripts aren't expected to run for a long time and not very often. 
                 * Scripts that start long running things are marked as so and will fork them off
                 */
                synchronized (this) {
                    ProcessRunner runner = new ProcessRunner(_api, uid, pb, script);
                    if (script.longrun) {
                        runner.startDetached();
                        return "Script launched ok"; 
                    }
                    else {
                        int res = runner.runAndWait(10);
                        if (res == -1) 
                            return ERROR(resp, 500, "Script "+name+": exceeded max time. Killed!");
                        else
                            return res+": "+runner.getText();
                    }
                }
                
            } catch (Exception e1) { 
                e1.printStackTrace(System.out);
                return ERROR(resp, 500, "Script "+name+": "+e1.getMessage()); 
            }
        });
        
        
    }


}



