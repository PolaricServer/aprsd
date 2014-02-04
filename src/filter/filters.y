  
%{
import java.io.*;
import java.util.*;
%}

/* YACC Declarations */
%token <sval>  STRING 
%token <sval>  IDENT
%token <obj>   BOOLEAN VALUE
%token         AND OR NOT ARROW PROFILE
%token         ERROR

%type <obj>  action actions expr rule

%left '~'
%left AND OR NOT 


/* Grammar follows */
%%

input : stmts
      ; 
     
stmts : stmts stmt | /* empty */       
      ;
         
stmt  : profile | assignment 
      ; 

assignment : IDENT '=' expr ';'
                              {  if ($1.matches("infra|INFRA|moving|MOVING|fulldigi|FULLDIGI|igate|IGATE"))
                                   yyerror("Cannot redefine predicate '"+$1+"'"); 
                                else
                                   predicates.put($1, (Pred) $3); 
                              }     
           ; 


profile : PROFILE IDENT '{' rules '}' 
                             { profiles.put($2, ruleset); 
                               System.out.println("*** View profile '"+$2+"' ok");
                               ruleset=null; }
        | error 
                            
        ;
        
      
rules : rules rule           { if ($2 != null) 
                                 { ruleset.add((Rule)$2); }
                             }
                             
      | /* Empty */          { ruleset = new RuleSet();  }
      
      ;
     
     
     
rule : expr ARROW '{' actions '}' ';' 
                              {  $$=new Rule((Pred) $1, action); } 
     | error ';'              {  $$ = null; } 
     ;

     
     
expr : '(' expr ')'           {  $$=$2; }

     |  NOT expr              {  $$=Pred.NOT((Pred)$2); }          
                              
     |  expr AND expr         {  $$=Pred.AND((Pred)$1, (Pred)$3); }  
                              
     |  expr OR expr          {  $$=Pred.OR((Pred)$1, (Pred)$3); }  
                              
     |  IDENT '~' STRING      {  if ($1.matches("ident|IDENT")) 
                                      $$=Pred.Ident($3); 
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
     ;
       
       

actions : actions ',' action  { action.merge((Action) $3); }

        | action              { action=(Action) $1; }
        ; 
       
       
action : IDENT '=' STRING     { if ($1.matches("STYLE|style")) 
                                   $$=new Action(false,false,false,false,$3);
                                else {
                                   $$=new Action(false,false,false,false,"");
                                   yyerror("Unknown identifier '"+$1+"'"); 
                                }
                              }
                              
       | IDENT                { $$=new Action( 
                                   $1.matches("hide-ident"), $1.matches("hide-trail"),
                                   $1.matches("hide-all"), $1.matches("show-path"), "");
                                   
                                if (!$1.matches("(hide-(ident|all|trail))|show-path"))
                                   yyerror("Unknown identifier '"+$1+"'"); 
                              }
       ; 

       
%%

  private Action action; 
  private RuleSet ruleset; 
  private Map<String, RuleSet> profiles = new HashMap<String,RuleSet>(); 
  private Map<String, Pred> predicates = new HashMap<String,Pred>(); 
  
  
  /* a reference to the lexer object */
  private Lexer lexer;

  /* interface to the lexer */
  private int yylex () {
    int yyl_return = -1;
    try {
      yyl_return = lexer.yylex();
    }
    catch (IOException e) {
      System.out.println("IO error :"+e);
    }
    return yyl_return;
  }
  
  /* error reporting */
  public void yyerror (String error) {
    System.out.println ("ERROR [line "+lexer.line()+"]: " + error);
  }

  public Map<String, RuleSet> getProfiles() 
      {return profiles; }
      
  public void parse()
      { yyparse(); }
      
  /* lexer is created in the constructor */
  public Parser(Reader r) {
    lexer = new Lexer(r, this);
    
    /* Install predefined predicates */
    predicates.put("infra",    Pred.Infra()); 
    predicates.put("moving",   Pred.Moving());
    predicates.put("fulldigi", Pred.Infra(true,false));
    predicates.put("igate",    Pred.Infra(false,true));
  }

  /* that's how you use the parser */
  public static void main(String args[]) throws IOException {
    Parser yyparser = new Parser(new FileReader(args[0]));
    yyparser.yyparse();    
  }