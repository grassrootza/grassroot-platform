package za.org.grassroot.language;

import org.antlr.runtime.*;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.CommonTreeNodeStream;
import org.antlr.runtime.tree.Tree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.language.generated.DateLexer;
import za.org.grassroot.language.generated.DateParser;
import za.org.grassroot.language.generated.DateWalker;
import za.org.grassroot.language.generated.TreeRewrite;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

/**
 * @author Joe Stelmach
 */
public class Parser {
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
        _logger.info("Inside the constructor of the parser that resides inside the project");
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
        _logger.debug("Inside the Big Boy, The Parser, in the parse method");
        // lex the input value to obtain our global token stream
        ANTLRInputStream input = null;
        try {
            input = new ANTLRNoCaseInputStream(new ByteArrayInputStream(value.getBytes()));
        } catch (IOException e) {
            _logger.error("could not lex input", e);
        }
        DateLexer lexer = new DateLexer(input);
        _logger.info("Brace yourself, we are about to collect global token streams from a lexed input");
        List<TokenStream> streams = collectTokenStreams(new CommonTokenStream(lexer));
        _logger.debug("We're back from collecting our global token streams, this is what we got " + streams + ". Is it correct?");

        // and parse each of them
        List<DateGroup> groups = new ArrayList<DateGroup>();
        TokenStream lastStream = null;
        for (TokenStream stream : streams) {
            lastStream = stream;
            List<Token> tokens = ((NattyTokenSource) stream.getTokenSource()).getTokens();
            _logger.debug("And then hell broke loose with " + tokens.size() + " tokens found in this stream.");
            DateGroup group = singleParse(stream, value, referenceDate);
            _logger.debug("There is your golden group's date token part: " + group.getDates());
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
                            if (!DateParser.FOLLOW_empty_in_parse186.member(token.getType())) {
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
                    _logger.debug("Getting rid of some meaningless information in our \"" + group.getText() + "\" stream");
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
        _logger.debug("Just budged in the Parser for single dates... Slowly closing in to find the token stream culprit here ->: " + stream.get(0).getInputStream());
        List<Token> tokens = ((NattyTokenSource) stream.getTokenSource()).getTokens();
        _logger.debug("Two minutes before we bust any bug from parsing \"" + tokens.get(0).getInputStream() + "\"" + " stream...");
        if (tokens.isEmpty()) return group;

        StringBuilder tokenString = new StringBuilder();
        _logger.debug("...one minute now... hang in there. Bugs can run and hide in this \"" + tokens.get(0).getInputStream() + "\" stream");
        for (Token token : tokens) {
            _logger.debug("Looping and tokenizing...token is " + token);
            tokenString.append(DateParser.tokenNames[token.getType()]);
            tokenString.append(" ");
        }

        try {
            // parse

            ParseListener listener = new ParseListener();

            DateParser parser = new DateParser(stream, listener);
            DateParser.parse_return parseReturn = parser.parse();
    _logger.debug("After parsing \"{}\", this is the name of the AST built: \"{}\". " +
            "Just a peek view of the first leaf. It is \"{}\"",
            stream, parseReturn.getTree(), ((Tree) parseReturn.getTree()).getChild(0));

            Tree tree = (Tree) parseReturn.getTree();
            // we only continue if a meaningful syntax tree has been built
            if (tree.getChildCount() > 0) {
                _logger.info("Inside the parser...the builder built us this string: \"" + tokenString+"\".");

                // rewrite the tree (temporary fix for http://www.antlr.org/jira/browse/ANTLR-427)
                CommonTreeNodeStream nodes = new CommonTreeNodeStream(tree);
                TreeRewrite s = new TreeRewrite(nodes);
                tree = (CommonTree) s.downup(tree);

                // and walk it
                nodes = new CommonTreeNodeStream(tree);
                nodes.setTokenStream(stream);
                DateWalker walker = new DateWalker(nodes);
                walker.setReferenceDate(referenceDate);
                walker.getState().setDefaultTimeZone(_defaultTimeZone);
                walker.parse();
                _logger.info("Based on the parsed tokens, this is the Abstract Syntax Tree built *****>> {} <<*****", tree.toStringTree());

                // run through the results and append the parse information
                group = walker.getState().getDateGroup();
                ParseLocation location = listener.getDateGroupLocation();
                group.setLine(location.getLine());
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
            _logger.debug("Could not parse input", e);
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

        // walk through the token stream and build a collection
        // of sub token streams that represent possible date locations
        List<Token> currentGroup = null;
        List<List<Token>> groups = new ArrayList<List<Token>>();
        Token currentToken;
        int currentTokenType;
        StringBuilder tokenString = new StringBuilder();
        _logger.debug("Inside a stream token collector... About to give you a list of somehow unreadable tokens and very readable token types");
        while ((currentToken = stream.getTokenSource().nextToken()).getType() != DateLexer.EOF) {
            currentTokenType = currentToken.getType();
            _logger.debug("Token -> {} and it's type -> {}", currentToken, currentTokenType);
            tokenString.append(DateParser.tokenNames[currentTokenType]).append(" ");

            // we're currently NOT collecting for a possible date group
            if (currentGroup == null) {
                // skip over white space and known tokens that cannot be the start of a date
                if (currentTokenType != DateLexer.WHITE_SPACE &&
                        DateParser.FOLLOW_empty_in_parse186.member(currentTokenType)) {

                    currentGroup = new ArrayList<Token>();
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

        List<TokenStream> streams = new ArrayList<TokenStream>();
        for (List<Token> group : groups) {
            if (!group.isEmpty()) {
                StringBuilder builder = new StringBuilder();
                builder.append("This is how ANTLR interprets/rewrites your input based on the above token types: ");
                for (Token token : group) {
                    builder.append(DateParser.tokenNames[token.getType()]).append(" ");
                }
                _logger.info(builder.toString());

                streams.add(new CommonTokenStream(new NattyTokenSource(group)));
            }
        }
        _logger.info("Pheew... Now about to leave the stream token collector. Got a list of token streams with size " + stream.size());
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
