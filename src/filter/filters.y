  
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

input : profiles
      ; 
     
profiles : profiles profile
         | /* empty */       
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
                              
     |  IDENT                 {  if ($1.matches("infra|INFRA"))
                                      $$=Pred.Infra(); 
                                 else if ($1.matches("moving|MOVING")) 
                                      $$=Pred.Moving(); 
                                 else if ($1.matches("fulldigi|FULLDIGI"))
                                      $$=Pred.Infra(true,false); 
                                 else if ($1.matches("igate|IGATE"))
                                      $$=Pred.Infra(false,true); 
                                 else {
                                      $$=Pred.FALSE(); 
                                      yyerror("Unknown identifier '"+$1+"'"); 
                                 } 
                              }
     ;
       
       

actions : actions ',' action  { action.merge((Action) $3); }

        | action              { action=(Action) $1; }
        ; 
       
       
action : IDENT '=' STRING     { $$=new Action(false,false,false,false,
                                   ($1.matches("CSS|css")? $3 : "")); 
                              }
                              
       | IDENT                { $$=new Action( 
                                   $1.matches("hide-ident"), $1.matches("hide-trail"),
                                   $1.matches("hide-all"), $1.matches("show-path"),"");
                              }
       ; 

       
%%

  private Action action; 
  private RuleSet ruleset; 
  private Map<String, RuleSet> profiles = new HashMap<String,RuleSet>(); 
  
  
  /* a reference to the lexer object */
  private Lexer lexer;

  /* interface to the lexer */
  private int yylex () {
    int yyl_return = -1;
    try {
      yyl_return = lexer.yylex();
    }
    catch (IOException e) {
      System.err.println("IO error :"+e);
    }
    return yyl_return;
  }
  
  /* error reporting */
  public void yyerror (String error) {
    System.err.println ("ERROR [line "+lexer.line()+"]: " + error);
  }

  public Map<String, RuleSet> getProfiles() 
      {return profiles; }
      
  public void parse()
      { yyparse(); }
      
  /* lexer is created in the constructor */
  public Parser(Reader r) {
    lexer = new Lexer(r, this);
  }

  /* that's how you use the parser */
  public static void main(String args[]) throws IOException {
    Parser yyparser = new Parser(new FileReader(args[0]));
    yyparser.yyparse();    
  }