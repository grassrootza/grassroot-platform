package za.org.grassroot.language;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.language.generated.DateParserBaseListener;

import java.util.*;

/**
 * Responsible for collecting parse information from the debug parser
 *
 * @author Joe Stelmach
 */
public class ParseListener extends DateParserBaseListener {

    private static Logger logger = LoggerFactory.getLogger(ParseListener.class);

    private int backtracking = 0;
    private Map<String, Stack<List<Token>>> _ruleMap;
    private Map<String, List<ParseLocation>> _locations;
    private ParseLocation _dateGroupLocation;

    public ParseListener() {
        _ruleMap = new HashMap<>();
        _locations = new HashMap<>();
    }

    public Map<String, List<ParseLocation>> getLocations() {
        return _locations;
    }

    public ParseLocation getDateGroupLocation() {
        return _dateGroupLocation;
    }

    // don't add backtracking or cyclic DFA nodes
    public void enterDecision(int d, boolean couldBacktrack) {
        backtracking++;
    }

    public void exitDecision(int i) {
        backtracking--;
    }

    @Override
    public void enterEveryRule(ParserRuleContext ctx) {
        logger.debug("Inside the \"enterEveryRule\" method.");
        String ruleName = ctx.getPayload().getText();
        Stack<List<Token>> tokenListStack = _ruleMap.get(ruleName);
        if (tokenListStack == null) {
            tokenListStack = new Stack<>();
            _ruleMap.put(ruleName, tokenListStack);
        }
        tokenListStack.push(new ArrayList<>());
    }

    @Override
    public void exitEveryRule(ParserRuleContext ctx) {
        List<Token> tokenList = _ruleMap.get(ctx.getText()).pop();
        logger.info("Before we check the token size and this is the context rule {}", ctx.getRuleContext().getText());
        if (tokenList.size() > 0) {
            logger.info("inside exit every rule and this is the context rule {}", ctx.getParent().getText());
            boolean isAlternative = ctx.getText().equals("date_time_alternative");
            StringBuilder builder = new StringBuilder();
            for (Token token : tokenList) {
                builder.append(token.getText());
            }
            String text = builder.toString();
            int line = tokenList.get(0).getLine();
            int start = tokenList.get(0).getCharPositionInLine() + 1;
            int end = start + text.length();

            ParseLocation location = new ParseLocation();
            location.setRuleName(ctx.getRuleContext().getText());
            location.setText(text);
            location.setLine(line);
            location.setStart(start);
            location.setEnd(end);
            logger.info("Is this a flippin date alt?? Answer: {}", isAlternative);
            if (isAlternative) {
                _dateGroupLocation = location;
            }

            List<ParseLocation> list = _locations.get(location.getRuleName());
            if (list == null) {
                list = new ArrayList<>();
                _locations.put(location.getRuleName(), list);
            }

            list.add(location);
        }
    }

    public void enterRule(String filename, String ruleName) {
        if (backtracking > 0) return;

        Stack<List<Token>> tokenListStack = _ruleMap.get(ruleName);
        if (tokenListStack == null) {
            tokenListStack = new Stack<List<Token>>();
            _ruleMap.put(ruleName, tokenListStack);
        }

        tokenListStack.push(new ArrayList<>());
        logger.debug("entering ruleName={}", ruleName);
    }

    public void exitRule(String filename, String ruleName) {
        if (backtracking > 0) return;

        List<Token> tokenList = _ruleMap.get(ruleName).pop();

        if (tokenList.size() > 0) {
            boolean isAlternative = ruleName.equals("date_time_alternative");
            StringBuilder builder = new StringBuilder();
            for (Token token : tokenList) {
                builder.append(token.getText());
            }
            String text = builder.toString();
            int line = tokenList.get(0).getLine();
            int start = tokenList.get(0).getCharPositionInLine() + 1;
            int end = start + text.length();

            ParseLocation location = new ParseLocation();
            location.setRuleName(ruleName);
            location.setText(text);
            location.setLine(line);
            location.setStart(start);
            location.setEnd(end);

            if (isAlternative) {
                _dateGroupLocation = location;
            }

            List<ParseLocation> list = _locations.get(location.getRuleName());
            if (list == null) {
                list = new ArrayList<>();
                _locations.put(location.getRuleName(), list);
            }

            list.add(location);
        }

        logger.debug("exiting rule {}, parseLocations={}", ruleName, tokenList.toString());
    }

    public void consumeToken(Token token) {
        if (backtracking > 0) return;
        for (Stack<List<Token>> stack : _ruleMap.values()) {
            for (List<Token> tokenList : stack) {
                tokenList.add(token);
            }
        }
    }

    public void consumeHiddenToken(Token token) {
        if (backtracking > 0) return;
    }

    public void recognitionException(RecognitionException e) {
        if (backtracking > 0) return;
    }

}
