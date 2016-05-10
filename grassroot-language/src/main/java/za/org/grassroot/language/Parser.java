package za.org.grassroot.language;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.language.generated.DateLexer;
import za.org.grassroot.language.generated.DateParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

import static za.org.grassroot.language.generated.DateParser.ParseContext;
import static za.org.grassroot.language.generated.DateParser.VOCABULARY;

/**
 * @author Joe Stelmach
 */
public class Parser{
    private TimeZone _defaultTimeZone;

    private static final Logger _logger = LoggerFactory.getLogger(Parser.class);


    /**
     * Tokens that should be removed from the end any list of tokens before parsing. These are
     * valid tokens, but could never add any meaningful parsing information when located at the
     * end of a token stream.
     */
    private static final Set<Integer> IGNORED_TRAILING_TOKENS =
            new HashSet<Integer>(Arrays.asList(new Integer[]{
                    DateLexer.DOT,
                    DateLexer.COLON,
                    DateLexer.COMMA,
                    DateLexer.DASH,
                    DateLexer.SLASH,
                    DateLexer.DOT,
                    DateLexer.PLUS,
                    DateLexer.SINGLE_QUOTE
            }));

    /**
     * Creates a new parser using the given time zone as the default
     *
     * @param defaultTimeZone
     */
    public Parser(TimeZone defaultTimeZone) {
        _defaultTimeZone = defaultTimeZone;
    }

    /**
     * Creates a new parser with no explicit default time zone (default will be US/Eastern)
     */
    public Parser() {
        _defaultTimeZone = TimeZone.getDefault();
    }

    /**
     * Parses the given input value for one or more groups of
     * date alternatives
     *
     * @param value
     * @return
     */
    public List<DateGroup> parse(String value) {
        return parse(value, new Date());
    }

    /**
     * Parses the given input value for one or more groups of
     * date alternatives with relative dates resolved according
     * to referenceDate
     *
     * @param value
     * @param referenceDate
     * @return
     */
    public List<DateGroup> parse(String value, Date referenceDate) {
        _logger.info("Inside the parser, referenceDate={}", referenceDate);
        // lex the input value to obtain our global token stream
        ANTLRInputStream input = null;
        try {
            input = new ANTLRNoCaseInputStream(new ByteArrayInputStream(value.getBytes()));
        } catch (IOException e) {
            _logger.error("could not lex input", e);
        }

        DateLexer lexer = new DateLexer(input);
        _logger.info("About to call \"collectTokenStreams\"");
        List<TokenStream> streams = collectTokenStreams(new CommonTokenStream(lexer));
        _logger.info("We're back from collecting our global token streams, this is what we got {}", streams.get(0));

        // and parse each of them
        List<DateGroup> groups = new ArrayList<>();
        TokenStream lastStream;
        for (TokenStream stream : streams) {
            lastStream = stream;
            List<Token> tokens = ((NattyTokenSource) stream.getTokenSource()).getTokens();
            _logger.info("And then hell broke loose with " + tokens.size() + " tokens found in this stream.");
            _logger.info("About to call singleParse, referenceDate={} | value={} | stream={}", referenceDate, value, stream.getText());
            DateGroup group = singleParse(stream, value, referenceDate);
            _logger.info("There is your group's date token part: " + (group != null ? group.getDates() : "null"));
            while ((group == null || group.getDates().size() == 0) && tokens.size() > 0) {
                if (group == null || group.getDates().size() == 0) {

                    // we have two options:
                    // 1. Continuously remove tokens from the end of the stream and re-parse.  This will
                    //    recover from the case of an extraneous token at the end of the token stream.
                    //    For example: 'june 20th on'
                    List<Token> endRemovedTokens = new ArrayList<Token>(tokens);

                    while ((group == null || group.getDates().isEmpty()) && !endRemovedTokens.isEmpty()) {

                        endRemovedTokens = endRemovedTokens.subList(0, endRemovedTokens.size() - 1);
                        TokenStream newStream = new CommonTokenStream(new NattyTokenSource(endRemovedTokens));
                        group = singleParse(newStream, value, referenceDate);
                        lastStream = newStream;
                    }

                    // 2. Continuously look for another possible starting point in the token
                    //    stream and re-parse.
                    while ((group == null || group.getDates().isEmpty()) && tokens.size() >= 1) {

                        tokens = tokens.subList(1, tokens.size());
                        Iterator<Token> iter = tokens.iterator();
                        while (iter.hasNext()) {
                            Token token = iter.next();
                            if (!(token.getText().isEmpty())) {
                                iter.remove();
                            } else {
                                break;
                            }
                        }

                        TokenStream newStream = new CommonTokenStream(new NattyTokenSource(tokens));

                        group = singleParse(newStream, value, referenceDate);

                        lastStream = newStream;
                    }

                }
            }
            // If a group with at least one date was found, we'll most likely want to add it to our list,
            // but not if multiple streams were found and the group contains only numeric time information.
            // For example: A full text string of '1' should parse to 1 o'clock, but 'I need 1 hard drive'
            // should result in no groups found.
            if (group != null && !group.getDates().isEmpty() &&
                    (streams.size() == 1 || !group.isDateInferred() || !isAllNumeric(lastStream))) {

                // Additionally, we'll only accept this group if the associated text does not have an
                // alphabetic character to the immediate left or right, which would indicate a portion
                // of a word was tokenized. For example, 'nightingale' will result in a 'NIGHT' token,
                // but there's clearly no datetime information there.
                group.setFullText(value);
                String prefix = group.getPrefix(1);
                String suffix = group.getSuffix(1);
                if ((prefix.isEmpty() || !Character.isLetter(prefix.charAt(0))) &&
                        (suffix.isEmpty() || !Character.isLetter(suffix.charAt(0)))) {
                    _logger.info("Getting rid of some meaningless information in our \"" + group.getText() + "\" stream");
                    groups.add(group);
                }
            }
        }

        return groups;
    }

    /**
     * Determines if a token stream contains only numeric tokens
     *
     * @param stream
     * @return true if all tokens in the given stream can be parsed as an integer
     */
    private boolean isAllNumeric(TokenStream stream) {
        List<Token> tokens = ((NattyTokenSource) stream.getTokenSource()).getTokens();
        for (Token token : tokens) {
            try {
                Integer.parseInt(token.getText());
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parses the token stream for a SINGLE date time alternative.  This
     * method assumes that the entire token stream represents date and or
     * time information (no extraneous tokens)
     *
     * @param stream
     * @return
     */
    private DateGroup singleParse(TokenStream stream, String fullText, Date referenceDate) {
        DateGroup group = null;
        _logger.info("singleParse... token stream=: " + (stream.size() > 0 ? stream.get(0).getInputStream() : "null"));
        List<Token> tokens = ((NattyTokenSource) stream.getTokenSource()).getTokens();
        _logger.info("singleParse.... list of tokens={}", tokens.isEmpty() ? "" : tokens.get(0).getInputStream());

        if (tokens.isEmpty()) return group;

        StringBuilder tokenString = new StringBuilder();
        for (Token token : tokens) {
            _logger.info("Looping and tokenizing...token is " + token);
            tokenString.append(VOCABULARY.getSymbolicName(token.getType())).append(" ");
        }
        _logger.info("This is the input stream rewritten in symbolic tokens {}", tokenString);
        try {
            // parse

            ParseListener listener = new ParseListener();
            DateParser parser = new DateParser(stream);
            WalkerState walkerState = new WalkerState(referenceDate);
            ParseContext parseReturn = parser.parse();
            ParseTree tree = parseReturn;
            // we only continue if a meaningful syntax tree has been built
            _logger.info("Lets check the children= {}", tree.getChildCount());
            if (tree.getChildCount()> 0) {
                _logger.info("Inside the parser...the builder built us this string: {}", tokenString);
                // rewrite the tree (temporary fix for http://www.antlr.org/jira/browse/ANTLR-427)
               /* CommonTreeNodeStream nodes = new CommonTreeNodeStream(tree);
                TreeRewrite s = new TreeRewrite(nodes);
                tree = (CommonTree) s.downup(tree);*/

                // and walk it
                /*nodes = new CommonTreeNodeStream(tree);
                nodes.setTokenStream(stream);

                walker.setReferenceDate(referenceDate);
                walker.getState().setDefaultTimeZone(_defaultTimeZone);
                walker.parse();*/
                _logger.info("This is what the listener listened for {}", tree.getChild(0).toStringTree(parser));
                walkerState.DEFAULT.walk(listener, tree);
                _logger.info("Antlr4 does not support AST's anymore, instead generates this Parse Tree" +
                        "*****>> {} <<***** This is a blueprint of how the input text was recognised", tree.toStringTree(parser));

                // run through the results and append the parse information
                group = walkerState.getDateGroup();
                _logger.info("The group of dates is \"{}\" or more cooler as {}", group.getFullText(), group.getSyntaxTree());
                ParseLocation location = listener.getDateGroupLocation();
                _logger.info("So it actually does the group locations??? Look \"{}\" and look here as well",
                        location.getText(), location);
                int i = -1;
                group.setLine(location.getLine());
                _logger.info("This is the line of the token {} and " +
                        "this is the text {} and this is the start {}", tokens.get(i).getLine(),
                        tokens.get(i).getText(), tokens.get(i).getStartIndex());
                group.setText(location.getText());
                group.setPosition(location.getStart());
                group.setSyntaxTree(tree);
                group.setParseLocations(listener.getLocations());
                group.setFullText(fullText);

                // if the group's matching text has an immediate alphabetic prefix or suffix,
                // we ignore this result
                String prefix = group.getPrefix(1);
                String suffix = group.getSuffix(1);
                if ((!prefix.isEmpty() && Character.isLetter(prefix.charAt(0))) ||
                        (!suffix.isEmpty() && Character.isLetter(suffix.charAt(0)))) {

                    group = null;
                }

            }

        } catch (RecognitionException e) {
            _logger.info("Could not parse input", e);
        }

        return group;
    }

    /**
     * Scans the given token global token stream for a list of sub-token
     * streams representing those portions of the global stream that
     * may contain date time information
     *
     * @param stream
     * @return
     */
    private List<TokenStream> collectTokenStreams(TokenStream stream) {
    _logger.info("Just got into collectTokenStreams. This is the stream: {}", stream.getTokenSource().getInputStream());
        // walk through the token stream and build a collection
        // of sub token streams that represent possible date locations
        List<Token> currentGroup = null;
        List<List<Token>> groups = new ArrayList<>();
        Token currentToken;
        int currentTokenType;
        StringBuilder tokenString = new StringBuilder();
        _logger.info("Inside a stream token collector... About to give you a list of somehow unreadable tokens and very readable token types");
        while ((currentToken = stream.getTokenSource().nextToken()).getType() != DateLexer.EOF) {
            currentTokenType = currentToken.getType();
            _logger.info("Token -> {} and it's type -> {}", currentToken, currentTokenType);
            tokenString.append(VOCABULARY.getSymbolicName(currentTokenType)).append(" ");

            // we're currently NOT collecting for a possible date group
            if (currentGroup == null) {
                // skip over white space and known tokens that cannot be the start of a date
                if (currentTokenType != DateLexer.WHITE_SPACE &&
                        !currentToken.getText().isEmpty()) {

                    _logger.info("Non empty token with null group, creating group and adding token");
                    currentGroup = new ArrayList<>();
                    currentGroup.add(currentToken);
                }
            }
            // we're currently collecting
            else {
                // preserve white space
                if (currentTokenType == DateLexer.WHITE_SPACE) {
                    currentGroup.add(currentToken);
                } else {
                    // if this is an unknown token, we'll close out the current group
                    if (currentTokenType == DateLexer.UNKNOWN) {
                        addGroup(currentGroup, groups);
                        currentGroup = null;
                    }
                    // otherwise, the token is known and we're currently collecting for
                    // a group, so we'll add it to the current group
                    else {
                        currentGroup.add(currentToken);
                    }
                }
            }
        }

        if (currentGroup != null) {
            addGroup(currentGroup, groups);
        }

        List<TokenStream> streams = new ArrayList<>();
        _logger.info("About to loop through {} group(s)", groups.size());
        groups.stream().filter(group -> !group.isEmpty()).forEach(group -> {
            StringBuilder builder = new StringBuilder();
            builder.append("This is how ANTLR interprets/rewrites your input based on the above token types: ");
            for (Token token : group) {
                builder.append(VOCABULARY.getSymbolicName(token.getType())).append(" ");
            }
            _logger.info(builder.toString());
            streams.add(new CommonTokenStream(new NattyTokenSource(group)));
            _logger.info("Compare it with input stream: {}", streams.get(0).getText());
        });
        _logger.info("leaving stream token collector. Got a list of token streams with size " + streams.size());
        return streams;
    }

    /**
     * Cleans up the given group and adds it to the list of groups if still valid
     *
     * @param group
     * @param groups
     */
    private void addGroup(List<Token> group, List<List<Token>> groups) {

        if (group.isEmpty()) return;

        // remove trailing tokens that should be ignored
        while (!group.isEmpty() && IGNORED_TRAILING_TOKENS.contains(
                group.get(group.size() - 1).getType())) {
            group.remove(group.size() - 1);
        }

        // if the group still has some tokens left, we'll add it to our list of groups
        if (!group.isEmpty()) {
            groups.add(group);
        }
    }
}
