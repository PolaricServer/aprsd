  
%{
import java.io.*;
import java.util.*;
import no.polaric.aprsd.ServerAPI;
%}

/* YACC Declarations */
%token <sval>  STRING 
%token <sval>  IDENT
%token <sval>  RELOP
%token <obj>   NUM

%token <obj>   BOOLEAN VALUE
%token         AND OR NOT ARROW PROFILE AUTOTAG PUBLIC
%token         ERROR

%type <obj>  action actions expr rule tag_rule public

/* Associativity and precedence */
%left OR
%left AND
%right NOT 
%nonassoc '~'

/* Grammar follows */
%%

input : stmts
      ; 
     
stmts : stmts stmt | /* empty */       
      ;
         
stmt  : profile | assignment | autotag
      ; 

assignment : IDENT '=' expr ';'
                              {  if ($1.matches("infra|INFRA|moving|MOVING|fulldigi|FULLDIGI|igate|IGATE"))
                                   yyerror("Cannot redefine predicate '"+$1+"'"); 
                                else
                                   predicates.put($1, (Pred) $3); 
                              }    
           | error
           ; 


profile : public PROFILE IDENT '{' rules '}' 
                             { if ((boolean)$1) 
                                  ruleset.setPublic(); 
                               profiles.put($3, ruleset); 
                               _api.log().debug("ViewFilter", "View profile '"+$3+"' ok");
                               ruleset=null; }
        | error 
                            
        ;
        
  
public : PUBLIC              { $$ = true; }
       | /* empty */         { $$ = false; }
       ; 
      
      
rules : rules rule           { if ($2 != null) 
                                 { ruleset.add((Rule)$2); }
                             }
                             
      | /* Empty */          { ruleset = new RuleSet();  }
      
      ;
     
     
     
rule : expr ARROW '{' actions '}' ';' 
                              {  $$ = new Rule((Pred) $1, action); } 
     | error ';'              {  $$ = null; } 
     ;

     
     
expr : '(' expr ')'           {  $$=$2; }

     |  NOT expr              {  $$=Pred.NOT((Pred)$2); }          
                              
     |  expr AND expr         {  $$=Pred.AND((Pred)$1, (Pred)$3); }  
                              
     |  expr OR expr          {  $$=Pred.OR((Pred)$1, (Pred)$3); }  
     
     |  IDENT RELOP NUM       {   if ($1.matches("scale|SCALE"))
                                     $$=Pred.Scale((Long) $3, $2);
                                  else if ($1.matches("speed|SPEED"))
                                     $$=Pred.Speed((Long) $3, $2);
                                  else if ($1.matches("max-speed|MAX-SPEED"))
                                     $$=Pred.MaxSpeed((Long) $3, $2);
                                  else if ($1.matches("((average|avg)-speed)|((AVERAGE|AVG)-SPEED)"))
                                     $$=Pred.AvgSpeed((Long) $3, $2);   
                                  else {
                                      $$=Pred.FALSE(); 
                                      yyerror("Unknown identifier '"+$1+"'"); 
                                 }  
                              }
                              
                              
     |  IDENT '~' STRING      {  if ($1.matches("ident|IDENT")) 
                                      $$=Pred.Ident($3); 
                                 else if ($1.matches("path|PATH"))
                                      $$=Pred.Path($3);
                                 else if ($1.matches("source|SOURCE")) 
                                      $$=Pred.Source($3); 
                                 else if ($1.matches("symbol|SYMBOL")) 
                                      $$=Pred.AprsSym($3);      
                                 else {
                                      $$=Pred.FALSE(); 
                                      yyerror("Tried to match with unknown element '"+$1+"'"); 
                                 }
                              }
                              
     |  IDENT                 {  if (predicates.get($1) != null) 
                                      $$=predicates.get($1);
                                 else {
                                      $$=Pred.FALSE(); 
                                      yyerror("Unknown identifier '"+$1+"'"); 
                                 } 
                              }
                              
     | TAG IDENT              { $$=Pred.Tag($2); }  
     
     ;
       
       

actions : actions ',' action  { action.merge((Action) $3); }

        | action              { action=(Action) $1; }
        ; 
       
       
action : IDENT '=' STRING     { if ($1.matches("STYLE|style")) 
                                   $$=new Action(false,false,false,false,false,$3,null,-1,-1);
                                else if ($1.matches("ICON|icon"))
                                   $$=new Action(false,false,false,false,false,"",$3,-1,-1);
                                else {
                                   $$=new Action(false,false,false,false,false,"",null,-1,-1);
                                   yyerror("Unknown identifier '"+$1+"'"); 
                                }
                              }
                              
       | IDENT '=' NUM        { if ($1.matches("trail-time"))
                                   $$ = new  Action(false,false,false,false,false,"",null, (long) $3, -1);
                                else if ($1.matches("trail-(len|length)"))
                                   $$ = new  Action(false,false,false,false,false,"",null, -1, (long) $3);  
                                else {
                                   $$=new Action(false,false,false,false,false,"",null,-1,-1);
                                   yyerror("Unknown identifier '"+$1+"'"); 
                                }  
                              }
                              
       | IDENT                { $$=new Action( 
                                   $1.matches("hide-ident"), $1.matches("hide-trail"),
                                   $1.matches("hide-all"), $1.matches("show-path"),
                                   $1.matches("set-public"), "", null, -1, -1);
                                   
                                if (!$1.matches("(hide-(ident|all|trail))|show-path|set-public"))
                                   yyerror("Unknown identifier '"+$1+"'"); 
                              }
       ; 
       
       
       
autotag : AUTOTAG '{' tag_rules '}' 
        ;
     
     
tag_rules : tag_rules tag_rule   
                             { if ($2 != null) 
                                 { tagrules.add((TagRule)$2); }
                             }
          | /* Empty */      { tagrules = new TagRuleSet();  }
          ;
      
      
tag_rule : expr ARROW '{' tag_actions '}' ';' 
                              { $$ = new TagRule((Pred) $1, tagaction); }
         | error ';'          { $$ = null; }
         ;
     
     
tag_actions : tag_actions ',' TAG IDENT  
                              { tagaction.add($4); }
            | TAG IDENT       { tagaction=new LinkedList<String>(); tagaction.add($2); }
            ;
            



       
%%

  private Action action; 
  private List<String> tagaction;
  private RuleSet ruleset; 
  private TagRuleSet tagrules; 
  private Map<String, RuleSet> profiles = new HashMap<String,RuleSet>(); 
  private Map<String, Pred> predicates = new HashMap<String,Pred>(); 
  
  private ServerAPI _api; 
  private String _filename;
  
  /* a reference to the lexer object */
  private Lexer lexer;

  /* interface to the lexer */
  private int yylex () {
    int yyl_return = -1;
    try {
      yyl_return = lexer.yylex();
    }
    catch (IOException e) {
      _api.log().error("ViewFilter", "IO error :"+e);
    }
    return yyl_return;
  }
  
  /* error reporting */
  public void yyerror (String error) {
     _api.log().error(null, "In config file '"+_filename+"', line "+lexer.line()+": " + error);
  }

  public Map<String, RuleSet> getProfiles() 
      { return profiles; }
      
  public TagRuleSet getTagRules () 
      { return tagrules; }
      
  public void parse()
      { yyparse(); }
      
  /* lexer is created in the constructor */
  public Parser(ServerAPI api, Reader r, String fname) {
    lexer = new Lexer(r, this);
    _api = api;
    _filename = fname; 
    
    /* Install predefined predicates */
    predicates.put("infra",    Pred.Infra()); 
    predicates.put("moving",   Pred.Moving());
    predicates.put("fulldigi", Pred.Infra(true,false));
    predicates.put("igate",    Pred.Infra(false,true));
  }

