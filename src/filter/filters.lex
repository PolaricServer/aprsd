package no.polaric.aprsd.filter;

%%

%byaccj
%class Lexer
%unicode
%line

%{ 
    /* store a reference to the parser object */
    private Parser yyparser;

    /* constructor taking an additional parser object */
    public Lexer(java.io.Reader r, Parser p) {
        this(r);
        this.yyparser = p;
    }
              
    public int line() {return yyline+1;}
%}

WS = [\ \t\b\012]

%%


<YYINITIAL> {

({WS}|\n)+|#.*
         { }
           
TRUE|True|true
         {    yyparser.yylval = new ParserVal(true); 
              return Parser.BOOLEAN;
         }
           
FALSE|False|false
         {    yyparser.yylval = new ParserVal(false); 
              return Parser.BOOLEAN;
         }           

(\'[^\n\']*\')|(\"[^\n\"]*\")
         {   yyparser.yylval = new ParserVal(yytext().substring(1,yytext().length()-1)); 
             return Parser.STRING; 
         }           
           
PROFILE|profile
         { return Parser.PROFILE; }
         
AND|"and"|\&     
         { return Parser.AND; }
         
OR|"or"|\|     
         { return Parser.OR; }
         
NOT|"not"|\!
         { return Parser.NOT; }
         
=>       
         { return Parser.ARROW; }
           
           
[a-zA-Z]+[0-9a-zA-Z_\-]+
         {   yyparser.yylval = new ParserVal(yytext()); 
             return Parser.IDENT; 
         }

         
[\~\;\(\)\=\{\}\,]
         { return (int) yycharat(0); }
           
.|\n  
         {   yyparser.yylval = new ParserVal("Unexpected/illegal character '"+ yytext()+"'"); 
             return Parser.ERROR; 
         }
}