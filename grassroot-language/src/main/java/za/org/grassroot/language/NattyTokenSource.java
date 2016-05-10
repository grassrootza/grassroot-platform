package za.org.grassroot.language;
import org.antlr.v4.runtime.*;

import java.util.List;

public class NattyTokenSource implements TokenSource {
  private static final Token EOF_TOKEN = new CommonToken(Token.EOF);

  private List<Token> _tokens;
  private int _index = 0;
  
  public NattyTokenSource(List<Token> tokens) {
    _tokens = tokens;
  }

  public Token nextToken() {
    return _tokens.size() > _index ? _tokens.get(_index++) : EOF_TOKEN;
  }

  @Override
  public int getLine() {
    return 0;
  }

  @Override
  public int getCharPositionInLine() {
    return 0;
  }

  @Override
  public CharStream getInputStream() {
    return null;
  }

  public String getSourceName() {
    return "language";
  }

  @Override
  public void setTokenFactory(TokenFactory<?> tokenFactory) {

  }

  @Override
  public TokenFactory<?> getTokenFactory() {
    return null;
  }

  public List<Token> getTokens() {
    return _tokens;  
  }
}
