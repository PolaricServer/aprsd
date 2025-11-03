/* 
 * Copyright (C) 2014-2025 by LA7ECA, Øyvind Hanssen (ohanssen@acm.org)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 */

package no.polaric.aprsd.filter;
import no.polaric.core.*;
import java.io.*;
import java.util.*;

/**
 * Parser stub for view profile configuration files.
 * TODO: Implement actual parsing logic.
 */
public class Parser {
    
    private Map<String, RuleSet> _profiles;
    private TagRuleSet _tagrules;
    
    public Parser(ServerConfig api, FileReader reader, String filename) {
        _profiles = new LinkedHashMap<String, RuleSet>();
        _tagrules = new TagRuleSet();
    }
    
    public void parse() {
        // TODO: Implement parsing logic
    }
    
    public Map<String, RuleSet> getProfiles() {
        return _profiles;
    }
    
    public TagRuleSet getTagRules() {
        return _tagrules;
    }
}
