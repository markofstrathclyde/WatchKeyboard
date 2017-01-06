package uk.org.textentry.wearwatch_shared;

import android.graphics.Point;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
/**
 * A class to support word prediction
 *
 * Text entry is handled by the word predictor - the current word is predicted based
 * on the history (characters before the current word) and the tap sequence within this
 * word.
 *
 * Uses the LanguageModel to predict the most likely next letter combining this with the
 * likeliehood of the current tap being on a key from the KeyboardView
 *
 * Class code ends with three very long methods that include the dictionary - bit ugly but
 * simple approach to bulding the dictionary. On release these contain the
 *
 *  Distributed under MIT License
 *
 *  Copyright (c) 2017 Mark Dunlop at University of Strathclyde (Scotland, UK, EU)
 *  https://personal.cis.strath.ac.uk/mark.dunlop/research/
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE..
 */

/**
 * Version 0: Nearest tap only
 * Created by Mark on 01/06/2016 but radically overhauled on 9/8/16
 */
public class WordPredictor {
    private static final boolean RUNTESTS=false;

    private static final int NUMBER_OF_TOP_STRINGS_KEEP = 5; //number of best possible strings to keep a history of when calculating possible strings
    private static final int NUMBER_SUGGESTIONS = 3; //number of suggestions to return on suggestion bar

    private KeyboardView keyboard;
    private LanguageModel lm;

    /*  Status variables

        The possibleStrings contains a set of possible best previous tap interpretations
        so, for example if the user typed FI then FI DO FO GO and GI would like be in the
        list as alternative previous letters.
        To save calculating on backspace we maintain currentPossibleLength

        Time is measured from first to last character
        exludes spaces but includes suggestion picking and all other taps plus backspace

        The history is the text before the current word
        The stack contains the "master" history while the string is a conventient variable for
        use in prediction - it's updated on space and backspace
        It's initialised to space as cheap way of handling start of sentance
     */

    private PredictionResult lastGivenResults = new PredictionResult("","");
    private Set<WeightedString> possibleStrings = new HashSet<>();
    private int currentPossibleLength = 0;
    private long firstCharacterTimeMS = -1, lastCharacterTimeMS=-1;
    private String history = " ";
    private Stack<String> historyStack = new Stack<>();
    private Stack<Point> previousTaps = new Stack<>();
    private int backspaceCount = 0, suggestionsPickedCount = 0;

    /**
     * Constructor for WordPredictor - takes a Keyboard specification
     *
     * @param keyboard the KeyboardView of the keyboard - used to get probabilities of taps
     */
    public WordPredictor(KeyboardView keyboard) {
        this.keyboard = keyboard;
        lm = new LanguageModel();
        //learnEnron();
        learnCommonWords();
    }

    /**
     * Return best predictions for tap x,y on the object's Keyboard
     *
     * @param x x-coordinate of the latest tap on the keyboard
     * @param y y-coordinate of the latest tap on the keyboard
     * @return best predictions based on history of taps and the current x,y coordinate
     */
    public PredictionResult suggestionFor(int x, int y) {
        lastCharacterTimeMS = System.currentTimeMillis();
        if (firstCharacterTimeMS==-1) firstCharacterTimeMS=lastCharacterTimeMS;

        previousTaps.push(new Point(x,y));

        //add the empty string to possibleStrings if it is empty - makes loops easier later
        if (possibleStrings.size() == 0) {
            possibleStrings = new HashSet<>(possibleStrings);
            possibleStrings.add(new WeightedString("",1));
            currentPossibleLength = 0;
        }

        //get letter probabilities for tap
        double[] locationProbs = keyboard.letterProbabilitiesForTap(x, y);


        //Get most likely top NUMBER_OF_TOP_STRINGS_KEEP candidate next letters based on all possibleStrings
        TopN topPossibleStrings = new TopN(NUMBER_OF_TOP_STRINGS_KEEP);
        final char[] CHARSET="abcdefghijklmnopqrstuvwxyz'-".toCharArray();
        for (WeightedString s : possibleStrings) {
            double[] lmProbabilities = lm.wittenBell(history + s.getString());
            double[] finalProbs = new double[128];
            for (int i = 0; i < CHARSET.length; i++) {
                char c = CHARSET[i];
                if (locationProbs[c] > 0.001) {

                    finalProbs[c] = locationProbs[c] * lmProbabilities[c];

                    if (finalProbs[c] > 0.00001) {
                        String str = s.getString();
                        double prevWeight = s.getWeight();
                        topPossibleStrings.add(new WeightedString(str + c, prevWeight * finalProbs[c] * finalProbs[c]));
                    }
                }
            }
        }

        TopN.TopNWeightedObject[] tps = topPossibleStrings.getValues();

        //Adjust weights to bias for end of word matching
        //This makes certain (or at least most likely?) that the top suggestion is what you get if you hit space
        //Implemented post main probabilities search to limit use of probBeforeSpaceFloored to only the top N
        for (TopN.TopNWeightedObject tto : tps)
            tto.multiplyByWeight(lm.probBeforeSpaceFloored(" " + ((WeightedString) tto).getString()));
        Arrays.sort(tps);

        //update possibleStrings with the possibleLetters
        Set<WeightedString> newPossibleStrings = new HashSet<>();
        for (TopN.TopNWeightedObject tno : tps)
            newPossibleStrings.add((WeightedString) tno);

        //finalise the predictions based on the topPossibleStrings
        int numberSuggestions = Math.min(tps.length, NUMBER_SUGGESTIONS);
        String[] topStrings = new String[numberSuggestions];
        for (int i = 0; i < numberSuggestions; i++)
            topStrings[i] = ((WeightedString) tps[i]).getString();

        if (numberSuggestions==0)
            lastGivenResults = new PredictionResult((history.trim().length()==0?"":history.trim()+" "), "", topStrings);
        else
            lastGivenResults = new PredictionResult((history.trim().length()==0?"":history.trim()+" ")+topStrings[0], topStrings[0], topStrings);

        possibleStrings = newPossibleStrings;
        currentPossibleLength++;

        return lastGivenResults;
    }

    /**
     * Converts the stack to a space separated and space surrounded string
     *
     * @param stack a stack of strings to be converted - strings should not contain space
     * @return a space separated string of the words in the stack, e.g. "word1_word2_word3...word4"
     */
    private static String stackToString(Stack<String> stack) {
        Enumeration<String> e = stack.elements();
        StringBuilder sb = new StringBuilder();
        while (e.hasMoreElements()) {
            sb.append(" ");
            sb.append(e.nextElement());
        }
        return sb.toString().trim();
    }

    /**
     * User has hit space - handle this in the prediction model and get new predictions
     *
     * @return the predictions after space
     */
    public PredictionResult suggestionOnSpace() {
        historyStack.push(lastGivenResults.currentSuggestion);
        history = stackToString(historyStack)+" ";

        lastGivenResults = new PredictionResult(history, lastGivenResults.currentSuggestion);
        possibleStrings = new HashSet<>();
        currentPossibleLength = 0;
        previousTaps = new Stack<>();
        return lastGivenResults;
    }

    /**
     * User has hit backspace - handle this in the prediction model and get new predictions
     *
     * @return the predictions after deleteLast
     */
    public PredictionResult deleteLast() throws KeyboardView.KeyboardException {

        lastCharacterTimeMS = System.currentTimeMillis();
        if (firstCharacterTimeMS==-1) firstCharacterTimeMS=lastCharacterTimeMS;

        backspaceCount++;

        if (currentPossibleLength > 0) { //Delete inside current word
            //ugly bit 1 - recreate the predictions by retapping the tap sequence
            possibleStrings = new HashSet<>();
            currentPossibleLength = 0;
            Stack<Point> oldTaps = new Stack<>();
            previousTaps.pop();
            if (previousTaps.empty()) {
                // deleteLast taken out the last letter
                lastGivenResults = new PredictionResult(history.equals(" ")?"":history.trim()+" ", "");
            } else {
                // deleteLast letters left - so reenter them
                for (Point p : previousTaps) oldTaps.add(p);
                previousTaps = new Stack<>();
                for (Point p : oldTaps) //deleteLast Retyping
                    suggestionFor(p.x, p.y);
            }
        } else { //Delete space and go to previous word on stack
            if (!historyStack.empty()){
                //ugly bit 2 - recreate the word by tapping its key centres
                String s = historyStack.pop();
                history = stackToString(historyStack)+" ";
                possibleStrings = new HashSet<>();
                currentPossibleLength = 0;
                previousTaps = new Stack<>();
                for (int i=0; i<s.length(); i++){//deleteLast Retyping
                    Point p = keyboard.getKeyCentre(s.charAt(i));
                    suggestionFor(p.x, p.y);
                }
            }
        }
        return lastGivenResults;
    }

    /**
     * Method returns the history of the text
     * @return the text before the current word
     */
    public String getHistory(){
        return history;
    }

    /**
     * Update internal models for user having made a suggestion bar selection
     *
     * @param s the string of the picked suggestion
     */
    public PredictionResult suggestionPicked(String s) {
        lastGivenResults.fullText = (history.equals(" ")?"":history+" ")+s;
        lastGivenResults.currentSuggestion = s;
        suggestionsPickedCount++;
        return suggestionOnSpace();
    }

    public void destroy() {
        keyboard = null;
        lm = null;
        lastGivenResults = null;
        possibleStrings = null;
        history = null;
        historyStack = null;
        previousTaps = null;
    }

    /**
     * A simple class to store strings of results
     * The "CurrentSuggestion" is the favoured in-line display
     * with other (variable number) suggestions as desired by
     * prediction engine for display as alternatives in suggestion
     * bar
     */
    public class PredictionResult {
        public String fullText;
        public String currentSuggestion;
        public String[] predictions;

        public PredictionResult(String fullText, String currentSuggestion, String... predictions) {
            this.fullText = fullText;
            this.currentSuggestion = currentSuggestion;
            this.predictions = predictions;
        }
    }

    public TextStats finishSentanceAndStartAnew(){
       boolean inword = (this.currentPossibleLength>0);
        String finalText = history.trim()+ (inword? " "+lastGivenResults.currentSuggestion : "");
        TextStats ts = new TextStats(finalText, lastCharacterTimeMS-firstCharacterTimeMS, backspaceCount, suggestionsPickedCount);

        history = " ";//initialised to space as cheap way of handling start of sentance
        historyStack = new Stack<>();
        previousTaps = new Stack<>();
        firstCharacterTimeMS = -1;
        lastCharacterTimeMS = -1;
        possibleStrings = new HashSet<>();
        currentPossibleLength = 0;
        lastGivenResults = new PredictionResult("","");
        backspaceCount = 0;
        suggestionsPickedCount = 0;

        return ts;
    }

    public void learn(String s){l(s);}
    private void l(String s){
        lm.learn(s);
    }

    private int test_correct=0, test_total=0;
    private void test(String s){
        finishSentanceAndStartAnew();
        try {
            for (int i=0;i<s.length(); i++) {
                if (s.charAt(i)==' '){
                    this.suggestionOnSpace();
                } else {
                    Point p = keyboard.getKeyCentre(s.charAt(i));
                    this.suggestionFor(p.x, p.y);
                }
            }
        } catch (KeyboardView.KeyboardException e) {
            e.printStackTrace();
        }
        String ts = finishSentanceAndStartAnew().getFinalPhrase();
        if (!ts.equals(s)) {
            LogCat.d("TEST ERROR >" + s + "< != >" + ts + "<");
        } else
            test_correct++;
        test_total++;
    }


    public void learnCommonWords(){
        //Some sample words that people usually type on a new keyboard
        l("hello"); l("test"); l("keyboard");

        //Top 100 words from https://en.wiktionary.org/wiki/Category:200_English_basic_words  -- duplicate to boost scores
        l("above"); l("left"); l("cross");    l("show"); l("above"); l("left"); l("cross");    l("show");
        l("after");    l("less"); l("day");  l("sleep"); l("after");    l("less"); l("day");  l("sleep");
        l("again");    l("line"); l("different");    l("something"); l("again");    l("line"); l("different");    l("something");
        l("air");  l("look"); l("during");   l("sound"); l("air");  l("look"); l("during");   l("sound");
        l("along");    l("me");   l("eat");  l("still"); l("along");    l("me");   l("eat");  l("still");
        l("also"); l("men");  l("end");  l("such"); l("also"); l("men");  l("end");  l("such");
        l("always");   l("might");    l("even"); l("take"); l("always");   l("might");    l("even"); l("take");
        l("another");  l("much"); l("every");    l("tell"); l("another");  l("much"); l("every");    l("tell");
        l("any");  l("must"); l("few");  l("think"); l("any");  l("must"); l("few");  l("think");
        l("around");   l("name"); l("find"); l("thought"); l("around");   l("name"); l("find"); l("thought");
        l("away"); l("never");    l("food"); l("three"); l("away"); l("never");    l("food"); l("three");
        l("back"); l("new");  l("form"); l("through"); l("back"); l("new");  l("form"); l("through");
        l("bad");  l("next"); l("get");  l("together"); l("bad");  l("next"); l("get");  l("together");
        l("because");  l("number");   l("give"); l("too"); l("because");  l("number");   l("give"); l("too");
        l("behind");   l("off");  l("go");   l("under"); l("behind");   l("off");  l("go");   l("under");
        l("below");    l("ok");   l("great");    l("until"); l("below");    l("ok");   l("great");    l("until");
        l("between");  l("old");  l("help"); l("us"); l("between");  l("old");  l("help"); l("us");
        l("big");  l("our");  l("here"); l("want"); l("big");  l("our");  l("here"); l("want");
        l("both"); l("own");  l("home"); l("well"); l("both"); l("own");  l("home"); l("well");
        l("bus");  l("part"); l("house");    l("went"); l("bus");  l("part"); l("house");    l("went");
        l("bye");  l("place");    l("important");    l("while"); l("bye");  l("place");    l("important");    l("while");
        l("came"); l("put");  l("keep"); l("why"); l("came"); l("put");  l("keep"); l("why");
        l("car");  l("right");    l("large");    l("without"); l("car");  l("right");    l("large");    l("without");
        l("children"); l("same"); l("last"); l("work"); l("children"); l("same"); l("last"); l("work");
        l("come"); l("set");  l("later");    l("world"); l("come"); l("set");  l("later");    l("world");

        //Top 1000 words from http://www.bckelk.ukfsn.org/words/uk1000n.html   -- duplicate to boost scores roughly proportional to log of frequency
        l("the");l("the");l("the");l("the");	l("hope");l("hope");	l("run");	l("breakfast");
        l("and");l("and");l("and");l("and");	l("called");l("called");	l("purpose");	l("rich");
        l("to");l("to");l("to");l("to");	l("nor");l("nor");	l("character");	l("engaged");
        l("of");l("of");l("of");l("of");	l("words");l("words");	l("body");	l("proper");
        l("a");l("a");l("a");l("a");	l("hear");l("hear");	l("ran");	l("talked");
        l("i");l("i");l("i");	l("brought");l("brought");	l("past");	l("respect");
        l("in");l("in");l("in");	l("set");l("set");	l("order");	l("fixed");
        l("was");l("was");l("was");	l("each");l("each");	l("need");	l("hill");
        l("he");l("he");l("he");	l("replied");l("replied");	l("pleased");	l("wall");
        l("that");l("that");l("that");	l("wish");l("wish");	l("trouble");	l("determined");
        l("it");l("it");l("it");	l("voice");l("voice");	l("whatever");	l("wild");
        l("his");l("his");l("his");	l("whole");l("whole");	l("dinner");	l("shut");
        l("her");l("her");l("her");	l("together");l("together");	l("happened");	l("top");
        l("you");l("you");l("you");	l("manner");l("manner");	l("sitting");	l("plain");
        l("as");l("as");l("as");	l("new");l("new");	l("getting");	l("scene");
        l("had");l("had");l("had");	l("believe");l("believe");	l("there's");l("there's");	l("sweet");
        l("with");l("with");l("with");	l("course");l("course");	l("besides");	l("especially");
        l("for");l("for");l("for");	l("least");l("least");	l("soul");	l("public");
        l("she");l("she");l("she");	l("years");l("years");	l("ill");	l("acquaintance");
        l("not");l("not");l("not");	l("answered");l("answered");	l("early");	l("forget");
        l("at");l("at");l("at");	l("among");l("among");	l("rose");	l("history");
        l("but");l("but");l("but");	l("stood");l("stood");	l("aunt");l("aunt");	l("pale");
        l("be");l("be");l("be");	l("sat");l("sat");	l("hundred");	l("pray");
        l("my");l("my");l("my");	l("speak");l("speak");	l("minutes");	l("books");
        l("on");l("on");l("on");	l("leave");l("leave");	l("across");	l("afternoon");
        l("have");l("have");l("have");	l("work");l("work");	l("carried");	l("man's");
        l("him");l("him");l("him");	l("keep");l("keep");	l("sit");	l("otherwise");
        l("is");l("is");l("is");	l("taken");l("taken");	l("observed");	l("mention");
        l("said");l("said");l("said");	l("end");l("end");	l("suddenly");	l("position");
        l("me");l("me");l("me");	l("less");l("less");	l("creature");	l("speech");
        l("which");l("which");l("which");	l("present");l("present");	l("conversation");	l("gate");
        l("by");l("by");l("by");	l("family");l("family");	l("worse");	l("'em");
        l("so");l("so");l("so");	l("often");l("often");	l("six");	l("boys");
        l("this");l("this");l("this");	l("wife");l("wife");	l("quiet");	l("yours");
        l("all");l("all");l("all");	l("whether");l("whether");	l("chair");	l("drink");
        l("from");l("from");l("from");	l("master");l("master");	l("doctor");l("doctor");	l("slowly");
        l("they");l("they");l("they");	l("coming");l("coming");	l("tone");	l("broke");
        l("no");l("no");l("no");	l("mean");l("mean");	l("standing");	l("clothes");
        l("were");l("were");l("were");	l("returned");l("returned");	l("living");	l("fond");
        l("if");l("if");l("if");	l("evening");l("evening");	l("sorry");	l("pride");
        l("would");l("would");l("would");	l("light");l("light");	l("stand");	l("watch");
        l("or");l("or");l("or");	l("money");l("money");	l("meet");	l("sooner");
        l("when");l("when");l("when");	l("cannot");l("cannot");	l("instead");	l("settled");
        l("what");l("what");l("what");	l("whose");l("whose");	l("wished");	l("paid");
        l("there");l("there");l("there");	l("boy");l("boy");	l("ah");	l("reply");
        l("been");l("been");l("been");	l("days");l("days");	l("lived");	l("tea");
        l("one");l("one");l("one");	l("near");l("near");	l("try");	l("lie");
        l("could");l("could");l("could");	l("matter");l("matter");	l("red");	l("running");
        l("very");l("very");l("very");	l("suppose");l("suppose");	l("smile");	l("died");
        l("an");l("an");l("an");	l("gentleman");l("gentleman");	l("sound");	l("gentle");
        l("who");l("who");l("who");	l("used");l("used");	l("expected");	l("particularly");
        l("them");l("them");l("them");	l("says");l("says");	l("silent");	l("allowed");
        l("mr");l("mr");l("mr");	l("really");l("really");	l("common");	l("outside");
        l("we");l("we");l("we");	l("rest");l("rest");	l("meant");	l("placed");
        l("now");l("now");l("now");	l("business");l("business");	l("tried");	l("joy");
        l("more");l("more");l("more");	l("full");l("full");	l("until");l("until");	l("hearing");
        l("out");l("out");l("out");	l("help");l("help");	l("mouth");	l("note");
        l("do");l("do");l("do");	l("child");l("child");	l("distance");	l("condition");
        l("are");l("are");l("are");	l("sort");l("sort");	l("occasion");	l("follow");
        l("up");l("up");l("up");	l("passed");l("passed");	l("cut");	l("begin");
        l("their");l("their");l("their");	l("lay");l("lay");	l("marry");	l("neck");
        l("your");l("your");l("your");	l("small");l("small");	l("likely");	l("serious");
        l("will");l("will");l("will");	l("behind");l("behind");	l("length");	l("hurt");
        l("little");l("little");	l("girl");l("girl");	l("story");	l("kindness");
        l("than");l("than");	l("feel");l("feel");	l("visit");	l("mere");
        l("then");l("then");	l("fire");l("fire");	l("deep");	l("farther");
        l("some");l("some");	l("care");l("care");	l("seems");	l("changed");
        l("into");l("into");	l("alone");l("alone");	l("street");	l("o'clock");
        l("any");l("any");	l("open");l("open");	l("remained");	l("passing");
        l("well");l("well");	l("person");l("person");	l("become");	l("girls");
        l("much");l("much");	l("call");l("call");	l("led");	l("force");
        l("about");l("about");	l("given");l("given");	l("speaking");	l("situation");
        l("time");l("time");	l("i'll");l("i'll");	l("natural");	l("greater");
        l("know");l("know");	l("sometimes");l("sometimes");	l("giving");	l("expression");
        l("should");l("should");	l("making");l("making");	l("further");	l("eat");
        l("man");l("man");	l("short");l("short");	l("struck");	l("reading");
        l("did");l("did");	l("else");l("else");	l("week");	l("spoken");
        l("like");l("like");	l("large");l("large");	l("loved");	l("raised");
        l("upon");l("upon");	l("within");l("within");	l("drew");	l("anybody");
        l("such");l("such");	l("chapter");l("chapter");	l("seem");	l("started");
        l("never");l("never");	l("true");l("true");	l("church");	l("following");
        l("only");l("only");	l("country");l("country");	l("knows");	l("although");
        l("good");l("good");	l("times");l("times");	l("object");	l("sea");
        l("how");l("how");	l("ask");l("ask");	l("ladies");	l("proud");
        l("before");l("before");	l("answer");l("answer");	l("marriage");	l("future");
        l("other");l("other");	l("air");l("air");	l("book");	l("quick");
        l("see");l("see");	l("kept");l("kept");	l("appearance");	l("safe");
        l("must");l("must");	l("hour");l("hour");	l("pay");	l("temper");
        l("am");l("am");	l("letter");l("letter");	l("i've");l("i've");	l("laughing");
        l("own");l("own");	l("happy");l("happy");	l("obliged");	l("ears");
        l("come");l("come");	l("reason");l("reason");	l("particular");	l("difficulty");
        l("down");l("down");	l("pretty");l("pretty");	l("pass");	l("meaning");
        l("say");l("say");	l("husband");l("husband");	l("thank");	l("servant");
        l("after");l("after");	l("certain");l("certain");	l("form");	l("sad");
        l("think");l("think");	l("others");l("others");	l("knowing");	l("advantage");
        l("made");l("made");	l("ought");l("ought");	l("lips");	l("appear");
        l("might");l("might");	l("does");l("does");	l("knowledge");	l("offer");
        l("being");l("being");	l("known");l("known");	l("former");	l("breath");
        l("mrs");l("mrs");	l("it's");l("it's");	l("blood");	l("opposite");
        l("again");l("again");	l("bed");l("bed");	l("sake");	l("number");
        l("great");l("great");	l("table");l("table");	l("fortune");	l("miserable");
        l("two");l("two");	l("that's");l("that's");	l("necessary");	l("law");
        l("can");l("can");	l("ready");l("ready");	l("presence");	l("rising");
        l("go");l("go");	l("read");l("read");	l("feelings");	l("favour");
        l("over");l("over");	l("already");l("already");	l("corner");	l("save");
        l("too");l("too");	l("pleasure");l("pleasure");	l("beautiful");	l("twice");
        l("here");l("here");	l("either");l("either");	l("talking");	l("single");
        l("came");l("came");	l("means");l("means");	l("spirit");	l("blue");
        l("old");l("old");	l("spoke");l("spoke");	l("ago");	l("noise");
        l("thought");l("thought");	l("taking");l("taking");	l("foot");	l("stone");
        l("himself");l("himself");	l("friends");l("friends");	l("circumstances");	l("mistress");
        l("where");l("where");	l("talk");l("talk");	l("wind");	l("surprised");
        l("our");l("our");	l("hard");l("hard");	l("presently");	l("allow");
        l("may");l("may");	l("walked");l("walked");	l("comes");	l("spot");
        l("first");l("first");	l("turn");l("turn");	l("attention");	l("burst");
        l("way");l("way");	l("strong");l("strong");	l("wait");	l("keeping");
        l("has");l("has");	l("thus");l("thus");	l("play");	l("line");
        l("though");l("though");	l("yourself");l("yourself");	l("easy");	l("understood");
        l("without");l("without");	l("high");l("high");	l("real");	l("court");
        l("went");l("went");	l("along");l("along");	l("clear");	l("finding");
        l("us");l("us");	l("above");l("above");	l("worth");	l("direction");
        l("away");l("away");	l("feeling");l("feeling");	l("cause");	l("anxious");
        l("day");l("day");	l("glad");l("glad");	l("send");	l("pocket");
        l("make");l("make");	l("children");l("children");	l("spirits");	l("around");
        l("these");l("these");	l("doubt");l("doubt");	l("chance");	l("conduct");
        l("young");l("young");	l("nature");l("nature");	l("didn't");	l("loss");
        l("nothing");l("nothing");	l("themselves");l("themselves");	l("view");	l("fresh");
        l("long");l("long");	l("black");l("black");	l("pleasant");	l("below");
        l("shall");l("shall");	l("hardly");l("hardly");	l("party");	l("hall");
        l("sir");l("sir");	l("town");l("town");	l("beginning");	l("satisfaction");
        l("back");l("back");	l("sense");l("sense");	l("horses");	l("land");
        l("don't");l("don't");	l("saying");l("saying");	l("stopped");	l("telling");
        l("house");l("house");	l("deal");l("deal");	l("notice");	l("passion");
        l("ever");l("ever");	l("account");l("account");	l("duty");	l("floor");
        l("yet");l("yet");	l("use");l("use");	l("he's");	l("break");
        l("take");l("take");	l("white");l("white");	l("age");	l("lying");
        l("every");l("every");	l("bad");l("bad");	l("figure");	l("waited");
        l("hand");l("hand");	l("everything");l("everything");	l("leaving");	l("closed");
        l("most");l("most");	l("can't");l("can't");	l("sleep");	l("meeting");
        l("last");l("last");	l("neither");l("neither");	l("entirely");	l("trying");
        l("eyes");l("eyes");	l("wanted");l("wanted");	l("twenty");	l("seat");
        l("its");l("its");	l("mine");l("mine");	l("fall");	l("king");
        l("miss");l("miss");	l("close");l("close");	l("promise");	l("confidence");
        l("having");l("having");	l("return");l("return");	l("months");	l("offered");
        l("off");l("off");	l("dark");l("dark");	l("broken");	l("stranger");
        l("looked");l("looked");	l("fell");l("fell");	l("heavy");	l("somebody");
        l("even");l("even");	l("subject");l("subject");	l("secret");	l("matters");
        l("while");l("while");	l("bear");l("bear");	l("thousand");	l("noble");
        l("dear");l("dear");	l("appeared");l("appeared");	l("happiness");	l("pardon");
        l("look");l("look");	l("fear");l("fear");	l("comfort");	l("private");
        l("many");l("many");	l("state");l("state");	l("minute");	l("sharp");
        l("life");l("life");	l("thinking");l("thinking");	l("act");	l("evil");
        l("still");l("still");	l("also");l("also");	l("human");	l("weeks");
        l("mind");l("mind");	l("point");l("point");	l("fancy");	l("justice");
        l("quite");l("quite");	l("therefore");l("therefore");	l("strength");	l("hot");
        l("another");l("another");	l("fine");l("fine");	l("showed");	l("cast");
        l("those");l("those");	l("case");l("case");	l("pounds");	l("letters");
        l("just");l("just");	l("doing");l("doing");	l("nearly");	l("youth");
        l("head");l("head");	l("held");l("held");	l("probably");	l("lives");
        l("tell");l("tell");	l("certainly");l("certainly");	l("captain");l("captain");	l("health");
        l("better");l("better");	l("walk");l("walk");	l("piece");	l("finished");
        l("always");l("always");	l("lost");	l("school");	l("hoped");
        l("saw");l("saw");	l("question");l("question");	l("write");	l("holding");
        l("seemed");l("seemed");	l("company");l("company");	l("laughed");	l("touch");
        l("put");l("put");	l("continued");l("continued");	l("reached");	l("spite");
        l("face");l("face");	l("fellow");l("fellow");	l("repeated");	l("delight");
        l("let");l("let");	l("truth");l("truth");	l("walking");	l("bound");
        l("took");l("took");	l("water");l("water");	l("father's");	l("consequence");
        l("poor");l("poor");	l("possible");l("possible");	l("heaven");	l("rain");
        l("place");l("place");	l("hold");	l("beauty");	l("wouldn't");
        l("why");l("why");	l("afraid");l("afraid");	l("shook");	l("third");
        l("done");l("done");	l("bring");	l("sun");	l("hung");
        l("herself");l("herself");	l("honour");l("honour");	l("waiting");	l("ways");
        l("found");l("found");	l("low");	l("moved");	l("weather");
        l("through");l("through");	l("ground");l("ground");	l("bit");	l("written");
        l("same");l("same");	l("added");l("added");	l("desire");	l("difference");
        l("going");l("going");	l("five");	l("news");	l("kitchen");
        l("under");l("under");	l("remember");	l("front");	l("she's");
        l("enough");l("enough");	l("except");l("except");	l("effect");	l("mother's");
        l("soon");l("soon");	l("power");l("power");	l("laugh");	l("persons");
        l("home");l("home");	l("seeing");	l("uncle");	l("quarter");
        l("give");l("give");	l("dead");l("dead");	l("fit");	l("promised");
        l("indeed");l("indeed");	l("i'm");l("i'm");	l("miles");	l("hopes");
        l("left");l("left");	l("usual");	l("handsome");	l("brown");
        l("get");l("get");	l("able");	l("caught");	l("nay");
        l("once");l("once");	l("second");	l("hat");	l("seven");
        l("mother");l("mother");	l("arms");l("arms");	l("regard");	l("simple");
        l("heard");l("heard");	l("late");	l("gentlemen");	l("wood");
        l("myself");l("myself");	l("opinion");l("opinion");	l("supposed");	l("beside");
        l("rather");l("rather");	l("window");l("window");	l("easily");	l("middle");
        l("love");l("love");	l("brother");l("brother");	l("impossible");	l("ashamed");
        l("knew");l("knew");	l("live");	l("glass");	l("lose");
        l("got");l("got");	l("four");	l("resolved");	l("dreadful");
        l("lady");l("lady");	l("none");	l("grew");	l("move");
        l("room");l("room");	l("death");	l("consider");	l("generally");
        l("something");l("something");	l("arm");	l("green");	l("cousin");
        l("yes");l("yes");	l("road");	l("considered");	l("surely");
        l("thing");l("thing");	l("hair");	l("unless");	l("satisfied");
        l("father");l("father");	l("sister");l("sister");	l("stop");	l("bent");
        l("perhaps");l("perhaps");	l("entered");	l("forth");	l("shoulder");
        l("sure");l("sure");	l("sent");	l("expect");	l("art");
        l("heart");l("heart");	l("married");l("married");	l("perfectly");	l("field");
        l("oh");l("oh");	l("longer");	l("altogether");	l("quickly");
        l("right");l("right");	l("immediately");l("immediately");	l("surprise");	l("thrown");
        l("against");l("against");	l("god");l("god");	l("sudden");	l("tired");
        l("three");l("three");	l("women");l("women");	l("free");	l("share");
        l("men");l("men");	l("hours");	l("exactly");	l("pair");
        l("night");l("night");	l("ten");	l("grave");	l("to-morrow");
        l("people");l("people");	l("understand");	l("carriage");	l("aware");
        l("door");l("door");	l("son");	l("believed");	l("colour");
        l("told");l("told");	l("horse");l("horse");	l("service");	l("writing");
        l("round");l("round");	l("wonder");	l("angry");	l("whenever");
        l("because");l("because");	l("cold");	l("putting");	l("quietly");
        l("woman");l("woman");	l("beyond");	l("carry");	l("fool");
        l("till");l("till");	l("please");	l("everybody");	l("forced");
        l("felt");l("felt");	l("fair");	l("mentioned");	l("touched");
        l("between");l("between");	l("became");	l("looks");	l("smiling");
        l("both");l("both");	l("sight");	l("scarcely");	l("taste");
        l("side");l("side");	l("met");	l("society");	l("dog");
        l("seen");l("seen");	l("afterwards");	l("affection");	l("spent");
        l("morning");l("morning");	l("eye");	l("exclaimed");	l("steps");
        l("began");l("began");	l("year");	l("dress");	l("worst");
        l("whom");l("whom");	l("show");	l("die");	l("legs");
        l("however");l("however");	l("general");	l("earth");	l("watched");
        l("asked");l("asked");	l("itself");	l("latter");	l("ay");
        l("things");l("things");	l("silence");	l("garden");	l("thee");
        l("part");l("part");	l("lord");	l("step");	l("eight");
        l("almost");l("almost");	l("wrong");	l("perfect");	l("worthy");
        l("moment");l("moment");	l("turning");	l("countenance");	l("wrote");
        l("looking");l("looking");	l("daughter");l("daughter");	l("liked");	l("manners");
        l("want");l("want");	l("stay");	l("dare");	l("proceeded");
        l("far");l("far");	l("forward");	l("pain");	l("frightened");
        l("hands");l("hands");	l("o");l("o");	l("companion");	l("somewhat");
        l("gone");l("gone");	l("interest");	l("journey");	l("born");
        l("world");l("world");	l("thoughts");	l("paper");	l("greatest");
        l("few");l("few");	l("followed");	l("opportunity");	l("charge");
        l("towards");l("towards");	l("won't");l("won't");	l("makes");	l("degree");
        l("gave");l("gave");	l("different");	l("honest");	l("shame");
        l("friend");l("friend");	l("opened");	l("arrived");	l("places");
        l("name");l("name");	l("several");	l("you'll");	l("ma'am");
        l("best");l("best");	l("idea");	l("bright");	l("couldn't");
        l("word");l("word");	l("received");	l("pity");	l("tongue");
        l("turned");l("turned");	l("change");	l("directly");	l("according");
        l("kind");l("kind");	l("laid");	l("cry");	l("box");
        l("cried");l("cried");	l("strange");	l("trust");	l("wine");
        l("since");l("since");	l("nobody");	l("fast");	l("filled");
        l("anything");l("anything");	l("fact");	l("ye");l("ye");	l("servants");
        l("next");l("next");	l("during");	l("warm");	l("calling");
        l("find");l("find");	l("feet");	l("danger");	l("fallen");
        l("half");l("half");	l("tears");	l("trees");	l("supper");


        //top 1000 words generated by Gennaro Imperatore from British National Corpus  - duplicated roughly proportional to log of frequency in English
        l("the");l("the");l("the");l("the");
        l("of");l("of");l("of");l("of");
        l("and");l("and");l("and");l("and");
        l("be");l("be");l("be");l("be");
        l("to");l("to");l("to");l("to");
        l("a");l("a");l("a");
        l("in");l("in");l("in");
        l("for");l("for");l("for");
        l("have");l("have");l("have");
        l("that");l("that");l("that");
        l("on");l("on");l("on");
        l("with");l("with");l("with");
        l("it");l("it");l("it");
        l("as");l("as");l("as");
        l("are");l("are");l("are");
        l("you");l("you");l("you");
        l("this");l("this");l("this");
        l("by");l("by");l("by");
        l("i");l("i");l("i");
        l("at");l("at");l("at");
        l("from");l("from");l("from");
        l("or");l("or");l("or");
        l("will");l("will");l("will");
        l("we");l("we");l("we");
        l("an");l("an");l("an");
        l("not");l("not");l("not");
        l("do");l("do");l("do");
        l("which");l("which");l("which");
        l("but");l("but");l("but");
        l("all");l("all");l("all");
        l("can");l("can");l("can");
        l("they");l("they");l("they");
        l("your");l("your");
        l("their");l("their");
        l("use");l("use");
        l("he");l("he");
        l("if");l("if");
        l("one");l("one");
        l("more");l("more");
        l("there");l("there");
        l("make");l("make");
        l("work");l("work");
        l("his");l("his");
        l("other");l("other");
        l("our");l("our");
        l("new");l("new");
        l("also");l("also");
        l("who");l("who");
        l("about");l("about");
        l("time");l("time");
        l("so");l("so");
        l("up");l("up");
        l("out");l("out");
        l("what");l("what");
        l("take");l("take");
        l("would");l("would");
        l("year");l("year");
        l("when");l("when");
        l("good");l("good");
        l("some");l("some");
        l("any");l("any");
        l("its");l("its");
        l("get");l("get");
        l("people");l("people");
        l("these");l("these");
        l("may");l("may");
        l("no");l("no");
        l("my");l("my");
        l("say");l("say");
        l("information");l("information");
        l("see");l("see");
        l("go");l("go");
        l("include");l("include");
        l("into");l("into");
        l("need");l("need");
        l("service");l("service");
        l("them");l("them");
        l("only");l("only");
        l("give");l("give");
        l("first");l("first");
        l("should");l("should");
        l("provide");l("provide");
        l("how");l("how");
        l("find");l("find");
        l("than");l("than");
        l("two");l("two");
        l("such");l("such");
        l("very");l("very");
        l("then");l("then");
        l("now");l("now");
        l("us");l("us");
        l("over");l("over");
        l("like");l("like");
        l("most");l("most");
        l("many");l("many");
        l("where");l("where");
        l("day");l("day");
        l("know");l("know");
        l("way");l("way");
        l("well");l("well");
        l("just");l("just");
        l("come");l("come");
        l("through");l("through");
        l("after");l("after");
        l("support");l("support");
        l("area");l("area");
        l("look");l("look");
        l("group");l("group");
        l("could");l("could");
        l("help");l("help");
        l("site");l("site");
        l("her");l("her");
        l("part");l("part");
        l("me");l("me");
        l("back");l("back");
        l("number");l("number");
        l("place");l("place");
        l("those");l("those");
        l("school");l("school");
        l("she");l("she");
        l("change");l("change");
        l("research");l("research");
        l("system");l("system");
        l("uk");l("uk");
        l("local");l("local");
        l("great");l("great");
        l("here");l("here");
        l("think");l("think");
        l("world");l("world");
        l("business");l("business");
        l("available");l("available");
        l("set");l("set");
        l("follow");l("follow");
        l("show");l("show");
        l("between");l("between");
        l("each");l("each");
        l("member");l("member");
        l("child");l("child");
        l("development");l("development");
        l("company");l("company");
        l("before");l("before");
        l("course");l("course");
        l("high");l("high");
        l("right");l("right");
        l("student");l("student");
        l("report");l("report");
        l("much");l("much");
        l("both");l("both");
        l("life");l("life");
        l("project");l("project");
        l("own");l("own");
        l("want");l("want");
        l("home");l("home");
        l("university");l("university");
        l("offer");l("offer");
        l("form");l("form");
        l("must");l("must");
        l("issue");l("issue");
        l("because");l("because");
        l("within");l("within");
        l("last");l("last");
        l("even");l("even");
        l("health");l("health");
        l("please");l("please");
        l("start");l("start");
        l("book");l("book");
        l("call");l("call");
        l("under");l("under");
        l("case");l("case");
        l("him");l("him");
        l("end");l("end");
        l("public");l("public");
        l("become");l("become");
        l("study");l("study");
        l("order");l("order");
        l("post");l("post");
        l("three");l("three");
        l("design");l("design");
        l("london");l("london");
        l("page");l("page");
        l("however");l("however");
        l("contact");l("contact");
        l("centre");l("centre");
        l("community");l("community");
        l("level");l("level");
        l("problem");l("problem");
        l("develop");l("develop");
        l("result");l("result");
        l("national");l("national");
        l("view");l("view");
        l("play");l("play");
        l("still");l("still");
        l("further");l("further");
        l("name");l("name");
        l("same");l("same");
        l("house");l("house");
        l("run");l("run");
        l("experience");l("experience");
        l("thing");l("thing");
        l("down");l("down");
        l("point");l("point");
        l("leave");l("leave");
        l("large");l("large");
        l("council");l("council");
        l("programme");l("programme");
        l("government");l("government");
        l("full");l("full");
        l("small");l("small");
        l("require");l("require");
        l("base");l("base");
        l("different");l("different");
        l("long");l("long");
        l("write");l("write");
        l("off");l("off");
        l("link");l("link");
        l("old");l("old");
        l("plan");l("plan");
        l("during");l("during");
        l("open");l("open");
        l("detail");l("detail");
        l("man");l("man");
        l("event");l("event");
        l("team");l("team");
        l("top");l("top");
        l("free");l("free");
        l("range");l("range");
        l("around");l("around");
        l("mean");l("mean");
        l("access");l("access");
        l("country");l("country");
        l("process");l("process");
        l("week");l("week");
        l("another");l("another");
        l("live");l("live");
        l("management");l("management");
        l("while");l("while");
        l("example");l("example");
        l("next");l("next");
        l("keep");l("keep");
        l("hold");l("hold");
        l("family");l("family");
        l("state");l("state");
        l("list");l("list");
        l("allow");l("allow");
        l("interest");l("interest");
        l("ask");l("ask");
        l("try");l("try");
        l("lead");l("lead");
        l("staff");l("staff");
        l("policy");l("policy");
        l("too");l("too");
        l("cost");l("cost");
        l("put");l("put");
        l("every");l("every");
        l("without");l("without");
        l("education");l("education");
        l("learn");l("learn");
        l("read");l("read");
        l("question");l("question");
        l("few");l("few");
        l("term");l("term");
        l("pay");l("pay");
        l("road");l("road");
        l("present");l("present");
        l("review");l("review");
        l("against");l("against");
        l("training");l("training");
        l("create");l("create");
        l("tell");l("tell");
        l("receive");l("receive");
        l("office");l("office");
        l("note");l("note");
        l("important");l("important");
        l("meet");l("meet");
        l("feel");l("feel");
        l("date");l("date");
        l("return");l("return");
        l("little");l("little");
        l("second");l("second");
        l("social");l("social");
        l("application");l("application");
        l("care");l("care");
        l("control");l("control");
        l("general");l("general");
        l("visit");l("visit");
        l("involve");l("involve");
        l("line");l("line");
        l("international");l("international");
        l("web");l("web");
        l("young");l("young");
        l("subject");l("subject");
        l("move");l("move");
        l("bring");l("bring");
        l("website");l("website");
        l("activity");l("activity");
        l("able");l("able");
        l("type");l("type");
        l("product");l("product");
        l("water");l("water");
        l("cover");l("cover");
        l("really");l("really");
        l("am");l("am");
        l("practice");l("practice");
        l("might");l("might");
        l("possible");l("possible");
        l("record");l("record");
        l("increase");l("increase");
        l("consider");l("consider");
        l("add");l("add");
        l("quality");l("quality");
        l("value");l("value");
        l("price");l("price");
        l("city");l("city");
        l("again");l("again");
        l("build");l("build");
        l("why");l("why");
        l("opportunity");l("opportunity");
        l("since");l("since");
        l("action");l("action");
        l("continue");l("continue");
        l("individual");l("individual");
        l("early");l("early");
        l("don't");l("don't");
        l("seem");l("seem");
        l("send");l("send");
        l("complete");l("complete");
        l("power");l("power");
        l("act");l("act");
        l("month");l("month");
        l("click");l("click");
        l("british");l("british");
        l("game");l("game");
        l("skill");l("skill");
        l("organisation");l("organisation");
        l("party");l("party");
        l("online");l("online");
        l("produce");l("produce");
        l("standard");l("standard");
        l("market");l("market");
        l("always");l("always");
        l("history");l("history");
        l("ensure");l("ensure");
        l("car");l("car");
        l("key");l("key");
        l("word");l("word");
        l("material");l("material");
        l("benefit");l("benefit");
        l("turn");l("turn");
        l("main");l("main");
        l("user");l("user");
        l("law");l("law");
        l("hand");l("hand");
        l("resource");l("resource");
        l("late");l("late");
        l("person");l("person");
        l("news");l("news");
        l("section");l("section");
        l("head");l("head");
        l("music");l("music");
        l("future");l("future");
        l("apply");l("apply");
        l("never");l("never");
        l("together");l("together");
        l("john");l("john");
        l("although");l("although");
        l("feature");l("feature");
        l("god");l("god");
        l("society");l("society");
        l("often");l("often");
        l("address");l("address");
        l("department");l("department");
        l("property");l("property");
        l("room");l("room");
        l("big");l("big");
        l("building");l("building");
        l("whether");l("whether");
        l("cause");l("cause");
        l("effect");l("effect");
        l("search");l("search");
        l("age");l("age");
        l("idea");l("idea");
        l("current");l("current");
        l("body");l("body");
        l("side");l("side");
        l("low");l("low");
        l("food");l("food");
        l("art");l("art");
        l("technology");l("technology");
        l("once");l("once");
        l("role");l("role");
        l("file");l("file");
        l("aim");l("aim");
        l("four");l("four");
        l("close");l("close");
        l("datum");l("datum");
        l("special");l("special");
        l("fact");l("fact");
        l("carry");l("carry");
        l("condition");l("condition");
        l("job");l("job");
        l("church");l("church");
        l("meeting");l("meeting");
        l("something");l("something");
        l("publish");l("publish");
        l("rate");l("rate");
        l("concern");l("concern");
        l("south");l("south");
        l("less");l("less");
        l("paper");l("paper");
        l("away");l("away");
        l("across");l("across");
        l("love");l("love");
        l("approach");l("approach");
        l("believe");l("believe");
        l("club");l("club");
        l("deal");l("deal");
        l("far");l("far");
        l("scheme");l("scheme");
        l("network");l("network");
        l("rather");l("rather");
        l("period");l("period");
        l("account");l("account");
        l("money");l("money");
        l("contain");l("contain");
        l("woman");l("woman");
        l("until");l("until");
        l("human");l("human");
        l("street");l("street");
        l("lot");l("lot");
        l("advice");l("advice");
        l("north");l("north");
        l("test");l("test");
        l("particular");l("particular");
        l("authority");l("authority");
        l("industry");l("industry");
        l("already");l("already");
        l("performance");l("performance");
        l("let");l("let");
        l("begin");l("begin");
        l("article");l("article");
        l("film");l("film");
        l("appear");l("appear");
        l("major");l("major");
        l("personal");l("personal");
        l("hour");l("hour");
        l("today");l("today");
        l("light");l("light");
        l("professional");l("professional");
        l("model");l("model");
        l("comment");l("comment");
        l("above");l("above");
        l("environment");l("environment");
        l("email");
        l("committee");
        l("document");
        l("per");
        l("class");
        l("buy");
        l("west");
        l("customer");
        l("wide");
        l("either");
        l("force");
        l("share");
        l("war");
        l("language");
        l("image");
        l("england");
        l("risk");
        l("field");
        l("easy");
        l("clear");
        l("whole");
        l("source");
        l("hope");
        l("though");
        l("shall");
        l("yet");
        l("computer");
        l("improve");
        l("night");
        l("claim");
        l("talk");
        l("real");
        l("patient");
        l("college");
        l("park");
        l("face");
        l("fund");
        l("director");
        l("english");
        l("town");
        l("trust");
        l("several");
        l("reason");
        l("join");
        l("expect");
        l("drive");
        l("short");
        l("board");
        l("travel");
        l("charge");
        l("win");
        l("award");
        l("internet");
        l("focus");
        l("agree");
        l("guide");
        l("press");
        l("remain");
        l("european");
        l("position");
        l("along");
        l("choose");
        l("court");
        l("decision");
        l("friend");
        l("mr");
        l("matter");
        l("science");
        l("text");
        l("east");
        l("content");
        l("knowledge");
        l("method");
        l("single");
        l("hear");
        l("sound");
        l("mark");
        l("sure");
        l("check");
        l("ever");
        l("land");
        l("unit");
        l("software");
        l("below");
        l("library");
        l("pass");
        l("stage");
        l("space");
        l("least");
        l("understand");
        l("grow");
        l("due");
        l("manager");
        l("five");
        l("evidence");
        l("identify");
        l("conference");
        l("sale");
        l("quite");
        l("wish");
        l("sign");
        l("card");
        l("release");
        l("happen");
        l("upon");
        l("manage");
        l("financial");
        l("association");
        l("hard");
        l("series");
        l("reduce");
        l("walk");
        l("thank");
        l("safety");
        l("police");
        l("enough");
        l("common");
        l("insurance");
        l("supply");
        l("sector");
        l("security");
        l("facility");
        l("raise");
        l("later");
        l("cannot");
        l("direct");
        l("appropriate");
        l("achieve");
        l("past");
        l("describe");
        l("option");
        l("decide");
        l("message");
        l("stop");
        l("suggest");
        l("version");
        l("copy");
        l("assessment");
        l("fall");
        l("strategy");
        l("relate");
        l("therefore");
        l("recent");
        l("currently");
        l("bit");
        l("enjoy");
        l("answer");
        l("lose");
        l("treatment");
        l("purpose");
        l("collection");
        l("near");
        l("enable");
        l("half");
        l("establish");
        l("response");
        l("officer");
        l("final");
        l("bad");
        l("structure");
        l("spend");
        l("various");
        l("size");
        l("stand");
        l("private");
        l("seek");
        l("requirement");
        l("welcome");
        l("parent");
        l("particularly");
        l("hotel");
        l("energy");
        l("third");
        l("ground");
        l("bank");
        l("player");
        l("story");
        l("united");
        l("deliver");
        l("speak");
        l("client");
        l("trade");
        l("minute");
        l("likely");
        l("sell");
        l("rule");
        l("scotland");
        l("reference");
        l("amount");
        l("step");
        l("location");
        l("teacher");
        l("specific");
        l("strong");
        l("break");
        l("display");
        l("reach");
        l("towards");
        l("white");
        l("enter");
        l("throughout");
        l("learning");
        l("picture");
        l("kind");
        l("mind");
        l("encourage");
        l("original");
        l("david");
        l("regard");
        l("commission");
        l("measure");
        l("central");
        l("air");
        l("item");
        l("tax");
        l("code");
        l("agency");
        l("select");
        l("nature");
        l("death");
        l("update");
        l("heart");
        l("fire");
        l("request");
        l("legal");
        l("choice");
        l("challenge");
        l("analysis");
        l("necessary");
        l("anyone");
        l("letter");
        l("draw");
        l("impact");
        l("discuss");
        l("function");
        l("credit");
        l("royal");
        l("medium");
        l("hospital");
        l("potential");
        l("outside");
        l("simple");
        l("green");
        l("accept");
        l("themselves");
        l("garden");
        l("serve");
        l("union");
        l("almost");
        l("production");
        l("black");
        l("addition");
        l("medical");
        l("political");
        l("planning");
        l("relevant");
        l("solution");
        l("plant");
        l("total");
        l("author");
        l("discussion");
        l("teaching");
        l("excellent");
        l("itself");
        l("forward");
        l("similar");
        l("partnership");
        l("provision");
        l("save");
        l("probably");
        l("table");
        l("target");
        l("st");
        l("six");
        l("front");
        l("transport");
        l("explain");
        l("usually");
        l("soon");
        l("band");
        l("program");
        l("britain");
        l("services");
        l("relationship");
        l("survey");
        l("useful");
        l("modern");
        l("represent");
        l("register");
        l("grant");
        l("especially");
        l("animal");
        l("europe");
        l("contract");
        l("nothing");
        l("remember");
        l("simply");
        l("die");
        l("holiday");
        l("character");
        l("station");
        l("degree");
        l("career");
        l("certain");
        l("everyone");
        l("actually");
        l("maintain");
        l("affect");
        l("promote");
        l("introduce");
        l("box");
        l("video");
        l("train");
        l("success");
        l("responsibility");
        l("via");
        l("effective");
        l("figure");
        l("title");
        l("track");
        l("notice");
        l("rise");
        l("shop");
        l("aspect");
        l("anything");
        l("economic");
        l("basis");
        l("recently");
        l("eye");
        l("exist");
        l("perhaps");
        l("difficult");
        l("colour");
        l("phone");
        l("style");
        l("cut");
        l("bear");
        l("american");
        l("sense");
        l("campaign");
        l("someone");
        l("watch");
        l("launch");
        l("attend");
        l("map");
        l("executive");
        l("prepare");
        l("dr");
        l("additional");
        l("communication");
        l("statement");
        l("annual");
        l("funding");
        l("environmental");
        l("region");
        l("successful");
        l("lord");
        l("operation");
        l("century");
        l("payment");
        l("difference");
        l("gain");
        l("among");
        l("transfer");
        l("significant");
        l("situation");
        l("wales");
        l("fine");
        l("scottish");
        l("stay");
        l("round");
        l("previous");
        l("limit");
        l("data");
        l("match");
        l("TRUE");
        l("sport");
        l("accord");
        l("independent");
        l("tool");
        l("natural");
        l("disease");
        l("variety");
        l("entry");
        l("teach");
        l("hall");
        l("partner");
        l("refer");
        l("chance");
        l("ability");
        l("protection");
        l("obtain");
        l("equipment");
        l("remove");
        l("race");
        l("village");
        l("housing");
        l("session");
        l("basic");
        l("primary");
        l("agreement");
        l("piece");
        l("demand");
        l("cross");
        l("de");
        l("print");
        l("culture");
        l("rest");
        l("object");
        l("progress");
        l("attempt");
        l("poor");
        l("red");
        l("summer");
        l("million");
        l("procedure");
        l("sit");
        l("whilst");
        l("fee");
        l("window");
        l("labour");
        l("recognise");
        l("academic");
        l("element");
        l("fit");
        l("drug");
        l("ago");
        l("store");
        l("perform");
        l("forum");
        l("responsible");
        l("behind");
        l("investment");
        l("leader");
        l("publication");
        l("bill");
        l("operate");
        l("respect");
        l("associate");
        l("county");
        l("season");
        l("worker");
        l("minister");
        l("database");
        l("everything");
        l("understanding");
        l("fail");
        l("couple");
        l("miss");
        l("institute");
        l("evening");
        l("fully");
        l("museum");
        l("northern");
        l("purchase");
        l("wall");
        l("effort");
        l("digital");
        l("attack");
        l("ireland");
        l("sort");
        l("exercise");
        l("proposal");
        l("machine");
        l("paul");
        l("compare");
        l("journal");
        l("global");
        l("delivery");
        l("loss");
        l("charity");
        l("employment");
        l("sea");
        l("secretary");
        l("wait");
        l("whose");
        l("determine");
        l("door");
        l("better");
        l("tree");
        l("official");
        l("christian");
        l("song");
        l("tour");
        l("format");
        l("sometimes");
        l("propose");
        l("protect");
        l("king");
        l("technique");
        l("river");
        l("explore");
        l("popular");
        l("factor");
        l("morning");
        l("topic");
        l("adult");
        l("relation");
        l("bar");
        l("introduction");
        l("prove");
        l("regulation");
        l("recommend");
        l("express");
        l("specialist");
        l("occur");
        l("route");
        l("thus");
        l("star");
        l("treat");
        l("movement");
        l("population");
        l("cell");
        l("speed");
        l("moment");
        l("practical");
        l("vehicle");
        l("regional");
        l("waste");
        l("telephone");
        l("growth");
        l("theory");
        l("download");
        l("finish");
        l("commercial");
        l("description");
        l("technical");
        l("himself");
        l("interview");
        l("appeal");
        l("guidance");
        l("competition");
        l("plus");
        l("else");
        l("task");
        l("engine");
        l("damage");
        l("cent");
        l("undertake");
        l("employee");
        l("nhs");
        l("avoid");
        l("mention");
        l("screen");
        l("employer");
        l("limited");
        l("former");
        l("interesting");

        //top 500 word bigrams generated by Gennaro Imperatore from British National Corpus  - duplicated roughly proportional to log of frequency in English
        l("of the");l("of the");l("of the");
        l("in the");l("in the");
        l("to the");l("to the");
        l("it be");l("it be");
        l("on the");l("on the");
        l("be a");l("be a");
        l("and the");l("and the");
        l("for the");l("for the");
        l("to be");l("to be");
        l("have be");l("have be");
        l("at the");l("at the");
        l("be the");l("be the");
        l("with the");l("with the");
        l("from the");l("from the");
        l("will be");l("will be");
        l("by the");l("by the");
        l("of a");l("of a");
        l("there be");l("there be");
        l("that the");l("that the");
        l("in a");l("in a");
        l("as a");l("as a");
        l("have a");l("have a");
        l("if you");l("if you");
        l("this be");l("this be");
        l("with a");l("with a");
        l("be not");l("be not");
        l("can be");l("can be");
        l("for a");l("for a");
        l("i have");l("i have");
        l("do n't");l("do n't");
        l("do not");l("do not");
        l("one of");l("one of");
        l("to a");l("to a");
        l("as the");l("as the");
        l("and a");l("and a");
        l("that be");l("that be");
        l("the first");l("the first");
        l("i be");l("i be");
        l("have to");l("have to");
        l("we have");l("we have");
        l("you can");l("you can");
        l("need to");l("need to");
        l("he be");
        l("part of");
        l("be to");
        l("there are");
        l("number of");
        l("the same");
        l("on a");
        l("which be");
        l("such as");
        l("should be");
        l("you have");
        l("of this");
        l("and be");
        l("you will");
        l("be an");
        l("they are");
        l("all the");
        l("be in");
        l("may be");
        l("you are");
        l("want to");
        l("the uk");
        l("in this");
        l("about the");
        l("into the");
        l("the world");
        l("to make");
        l("go to");
        l("would be");
        l("as well");
        l("we are");
        l("the most");
        l("the new");
        l("able to");
        l("a new");
        l("to have");
        l("over the");
        l("be use");
        l("to do");
        l("up to");
        l("out of");
        l("have the");
        l("to get");
        l("be also");
        l("range of");
        l("well as");
        l("be that");
        l("back to");
        l("and i");
        l("what be");
        l("the good");
        l("be make");
        l("not be");
        l("some of");
        l("use the");
        l("by a");
        l("more than");
        l("they have");
        l("and to");
        l("use of");
        l("that it");
        l("be no");
        l("and other");
        l("of their");
        l("i am");
        l("be able");
        l("through the");
        l("he have");
        l("and have");
        l("to see");
        l("who have");
        l("it have");
        l("the other");
        l("within the");
        l("a good");
        l("of our");
        l("i do");
        l("member of");
        l("they be");
        l("we will");
        l("if the");
        l("that you");
        l("that they");
        l("a few");
        l("to take");
        l("of these");
        l("and it");
        l("try to");
        l("from a");
        l("the end");
        l("end of");
        l("during the");
        l("are not");
        l("must be");
        l("make a");
        l("how to");
        l("and in");
        l("of his");
        l("at a");
        l("who be");
        l("the follow");
        l("but the");
        l("be very");
        l("look at");
        l("the last");
        l("the time");
        l("the university");
        l("use to");
        l("a number");
        l("for example");
        l("and that");
        l("to help");
        l("i think");
        l("university of");
        l("of your");
        l("the right");
        l("that we");
        l("of all");
        l("to use");
        l("the next");
        l("when the");
        l("i will");
        l("we be");
        l("the way");
        l("in which");
        l("to provide");
        l("at least");
        l("under the");
        l("of an");
        l("you be");
        l("to find");
        l("and then");
        l("say that");
        l("in their");
        l("be one");
        l("could be");
        l("and we");
        l("a very");
        l("see the");
        l("it will");
        l("you to");
        l("the main");
        l("access to");
        l("but it");
        l("provide a");
        l("lot of");
        l("that i");
        l("seem to");
        l("a great");
        l("in order");
        l("are the");
        l("like to");
        l("base on");
        l("between the");
        l("the '");
        l("ensure that");
        l("to work");
        l("the government");
        l("come to");
        l("that have");
        l("after the");
        l("that he");
        l("order to");
        l("be available");
        l("we can");
        l("a lot");
        l("she be");
        l("due to");
        l("to ensure");
        l("make the");
        l("their own");
        l("be on");
        l("work with");
        l("as an");
        l("the company");
        l("on this");
        l("so that");
        l("to go");
        l("the national");
        l("do you");
        l("work in");
        l("the second");
        l("for all");
        l("for more");
        l("type of");
        l("will have");
        l("be take");
        l("which have");
        l("include the");
        l("rather than");
        l("of its");
        l("be n't");
        l("you do");
        l("continue to");
        l("have not");
        l("around the");
        l("be give");
        l("the year");
        l("relate to");
        l("click here");
        l("in an");
        l("of course");
        l("but i");
        l("information on");
        l("level of");
        l("all of");
        l("the site");
        l("deal with");
        l("be at");
        l("for this");
        l("where the");
        l("be more");
        l("area of");
        l("in his");
        l("also be");
        l("be now");
        l("way to");
        l("the whole");
        l("the work");
        l("be find");
        l("as it");
        l("lead to");
        l("take the");
        l("would have");
        l("those who");
        l("will not");
        l("return to");
        l("that there");
        l("the number");
        l("to this");
        l("the day");
        l("carry out");
        l("the public");
        l("it would");
        l("be do");
        l("or the");
        l("the only");
        l("ca n't");
        l("when you");
        l("into a");
        l("have an");
        l("to give");
        l("across the");
        l("to your");
        l("most of");
        l("get a");
        l("who are");
        l("that a");
        l("which the");
        l("the late");
        l("that are");
        l("i would");
        l("take a");
        l("make it");
        l("a small");
        l("accord to");
        l("the use");
        l("you may");
        l("in your");
        l("be go");
        l("development of");
        l("the local");
        l("and their");
        l("wish to");
        l("which are");
        l("we do");
        l("not to");
        l("of any");
        l("that this");
        l("opportunity to");
        l("link to");
        l("with an");
        l("are a");
        l("up the");
        l("a large");
        l("the top");
        l("the two");
        l("them to");
        l("the case");
        l("time to");
        l("take place");
        l("such a");
        l("form of");
        l("be still");
        l("a little");
        l("find out");
        l("would like");
        l("more information");
        l("before the");
        l("in addition");
        l("web site");
        l("and you");
        l("many of");
        l("you want");
        l("the future");
        l("the need");
        l("look for");
        l("they will");
        l("' the");
        l("than the");
        l("the result");
        l("set up");
        l("in our");
        l("interest in");
        l("have no");
        l("get the");
        l("the area");
        l("this year");
        l("the british");
        l("kind of");
        l("the project");
        l("likely to");
        l("and will");
        l("the council");
        l("information about");
        l("way of");
        l("the development");
        l("a wide");
        l("for your");
        l("as they");
        l("young people");
        l("of them");
        l("the course");
        l("involve in");
        l("use a");
        l("be be");
        l("of which");
        l("the city");
        l("the country");
        l("the old");
        l("the school");
        l("go on");
        l("to their");
        l("focus on");
        l("and its");
        l("to top");
        l("have have");
        l("a bit");
        l("give the");
        l("not only");
        l("list of");
        l("use in");
        l("variety of");
        l("the great");
        l("it to");
        l("result of");
        l("if they");
        l("aspect of");
        l("you need");
        l("the fact");
        l("term of");
        l("on your");
        l("or a");
        l("be so");
        l("and his");
        l("per cent");
        l("to develop");
        l("the high");
        l("when i");
        l("because of");
        l("to all");
        l("refer to");
        l("top of");
        l("and are");
        l("do the");
        l("be just");
        l("and they");
        l("detail of");
        l("people who");
        l("to say");
        l("to keep");
        l("of my");
        l("he say");
        l("if it");
        l("amount of");
        l("work on");
        l("the current");
        l("the past");
        l("for an");
        l("a range");
        l("for their");
        l("throughout the");
        l("it do");
        l("post by");
        l("are also");
        l("at all");
        l("they do");
        l("mean that");
        l("not a");
        l("to an");
        l("the united");
        l("follow the");
        l("be see");
        l("be only");
        l("not have");
        l("and he");
        l("' and");
        l("in my");
        l("live in");
        l("as i");
        l("of it");
        l("will also");
        l("responsible for");
        l("do it");
        l("to you");
        l("in all");
        l("the internet");
        l("the problem");
        l("out the");
        l("become a");
        l("for you");
        l("as part");
        l("to meet");
        l("you should");
        l("they can");
        l("and so");
        l("please contact");
        l("the information");
        l("health and");
        l("on to");
        l("the department");
        l("us to");
        l("and there");
        l("i can");
        l("include a");
        l("create a");
        l("aim to");
        l("in its");
        l("are available");
        l("year of");
        l("against the");
        l("be hold");
        l("of people");
        l("and how");
        l("be it");
        l("the house");
        l("further information");
        l("the child");
        l("to support");
        l("the people");
        l("your own");
        l("note that");
        l("and more");
        l("and for");
        l("like a");
        l("believe that");
        l("as to");
        l("group of");
        l("are in");
        l("right to");
        l("might be");
        l("the service");
        l("may have");
        l("subject to");
        l("attempt to");
        l("as you");
        l("the early");
        l("be all");
    }

        public void testEnronPhrases(){
        if (RUNTESTS) {
            LogCat.d("Testing....");
            test("you're the greatest");
            test("i'm on a plane");
            test("i don't have the distraction of taking care of mimi");
            test("i'm going to class");
            test("i'll call you in the morning");
            test("i'm in stan's office");
            test("don't forget the wood");
            test("i'm still here");
            test("we're on the way");
            test("what's his problem");
            test("a gift isn't necessary");
            test("i'm waiting until she comes home");
            test("i'm not planning on doing anything this week");
            test("don't they have some conflicts here");
            test("don't make me pull tapes on whether you understood our fee");
            test("we don't seem to have any positive income there");
            test("what's your proposal");
            test("disney was great and i've been to eight baseball games");
            test("i'm glad you liked it");
            test("i've never worked with her");
            test("i'm glad she likes her tree");
            test("it's not looking too good is it");
            test("i'll get you one");
            test("what's going on");
            test("what's your phone number");
            test("i'll catch up with you tomorrow");
            test("you have a nice holiday too");
            test("we need to talk about this month");
            test("what about jay");
            test("we are waiting on the cold front");
            test("ken agreed yesterday");
            test("neil has been asking around");
            test("are you available");
            test("that would likely be an expensive option");
            test("good for you");
            test("we will keep you posted");
            test("do we have anyone in portland");
            test("no surprise there");
            test("hope you guys are doing fine");
            test("are you going to call");
            test("did that happen");
            test("i would be glad to participate");
            test("i worked on the grade level promotion");
            test("i have a request");
            test("what is this");
            test("travis is in charge");
            test("can you handle");
            test("their key decision maker did not show which is not a good sign");
            test("can you help get this cleared up");
            test("i have a high level in my office");
            test("thanks i will");
            test("are you being a baby");
            test("did you get this");
            test("florida is great");
            test("i sent it to her");
            test("i will call");
            test("please let me know if you learn anything at the floor meeting");
            test("please revise accordingly");
            test("could you see where this stands");
            test("see you on the third");
            test("did we get ours back");
            test("what is up with ene");
            test("are you sure");
            test("sorry about that");
            test("is that ok");
            test("jan has a lot of detail");
            test("need to watch closely");
            test("what do you think");
            test("i should have more info by our meeting this afternoon");
            test("are you there");
            test("i can review afterwards and get back to you tonight");
            test("i hope he is having a fantastic time");
            test("can you resend me the doyle email from last week");
            test("if so what was it");
            test("this seems fine to me");
            test("what a pain");
            test("pressure to finish my review");
            test("i like it");
            test("will it be delivered");
            test("was wondering if you and natalie connected");
            test("not at this time");
            test("we will get you a copy");
            test("i will follow up with him as soon as the dust settles");
            test("or are you going to be tied up with dinner");
            test("is this the only time available");
            test("no there will be plenty of others");
            test("what is the purpose of this");
            test("no can do");
            test("nice weather for it");
            test("i think those are the right dates");
            test("thai sounds good");
            test("do you want to fax it to my hotel");
            test("did you differ from me");
            test("are you going to join us for lunch");
            test("is she done yet");
            test("thanks for the quick turnaround");
            test("how are you");
            test("please call tomorrow if possible");
            test("we are all fragile");
            test("i would like to attend if so");
            test("i can return earlier");
            test("i am trying again");
            test("i will bring john brindle");
            test("he would love anything about rocks");
            test("what do you hear");
            test("hope your trip to florida was good");
            test("she called and wants to come over this am");
            test("see you soon");
            test("it reads like she is in");
            test("has dynegy made a specific request");
            test("i am walking in now");
            test("they have capacity now");
            test("tell her to get my expense report done");
            test("i am out of town on business tonight");
            test("not even close");
            test("chris foster is in");
            test("they are more efficiently pooled");
            test("could you try ringing her");
            test("do you need it today");
            test("keep me posted");
            test("john this message concerns me");
            test("call me to give me a heads up");
            test("and leave my school alone");
            test("what is in the plan");
            test("where do you want to meet to walk over there");
            test("i am almost speechless");
            test("suggest you get facts before judging anyone");
            test("we just need a sitter");
            test("we must be consistent");
            test("she has absolutely everything");
            test("this is good i think");
            test("we can have wine and catch up");
            test("money wise that is");
            test("what is wrong");
            test("where are you");
            test("thanks good job");
            test("hopefully this can wait until monday");
            test("no employment claims for gas or power");
            test("why do you ask");
            test("i agree since i am at the bank right now");
            test("i was planning to attend");
            test("that would be great");
            test("thank you for your prompt reply");
            test("can you help me here");
            test("i changed that in one prior draft");
            test("what is the cost issue");
            test("please send me an email");
            test("what a jerk");
            test("i wanted to go drinking with you");
            test("no material impact");
            test("i will be back friday");
            test("if not can i call you");
            test("do you still need me to sign something");
            test("both of us are still here");
            test("not even in yet");
            test("how soon do you need it");
            test("what number should he call you on");
            test("are you feeling better");
            test("have i mentioned how much i love houston traffic");
            test("take what you can get");
            test("should systems manage the migration");
            test("i think that is the right answer");
            test("this looks fine");
            test("get with mary for format");
            test("i hope you are feeling better");
            test("are you getting all the information you need");
            test("have a great trip");
            test("did you talk to ava this morning");
            test("can you help");
            test("has anyone else heard anything");
            test("is it over");
            test("ok with me");
            test("you can talk to becky");
            test("i talked to duran");
            test("i agreed terms with greg");
            test("i am at the lake");
            test("i told you silly");
            test("wednesday is definitely a hot chocolate day");
            test("thanks for your concern");
            test("thursday works better for me");
            test("what is the mood");
            test("i am on my way");
            test("do we need to discuss");
            test("just playing with you");
            test("thanks for checking with me");
            test("this is very sensitive");
            test("can we have them until we move");
            test("are you in today");
            test("let it rip");

            LogCat.d("Got " + test_correct + "/" + test_total);
        }
    }
    private void learnEnron() {
        l("i like it");l("please let me know if you learn anything at the floor meeting");
        l("no can do");l("don't make me pull tapes on whether you understood our fee");
        l("is that ok");l("disney was great and i've been to eight baseball games");
        l("is it over");l("i should have more info by our meeting this afternoon");
        l("ok with me");l("i will follow up with him as soon as the dust settles");
        l("let it rip");l("i don't have the distraction of taking care of mimi");
        l("i will call");l("i can review afterwards and get back to you tonight");
        l("what a pain");l("can you resend me the doyle email from last week");
        l("how are you");l("have i mentioned how much i love houston traffic");
        l("what a jerk");l("we don't seem to have any positive income there");
        l("good for you");l("i'm not planning on doing anything this week");
        l("what is this");l("where do you want to meet to walk over there");
        l("are you sure");l("are you getting all the information you need");
        l("see you soon");l("suggest you get facts before judging anyone");
        l("can you help");l("wednesday is definitely a hot chocolate day");
        l("thanks i will");l("was wondering if you and natalie connected");
        l("are you there");l("or are you going to be tied up with dinner");
        l("what is wrong");l("she called and wants to come over this am");
        l("where are you");l("that would likely be an expensive option");
        l("i'm on a plane");l("i agree since i am at the bank right now");
        l("i'm still here");l("tell her to get my expense report done");
        l("what about jay");l("do you still need me to sign something");
        l("can you handle");l("i worked on the grade level promotion");
        l("not even close");l("no employment claims for gas or power");
        l("keep me posted");l("i hope he is having a fantastic time");
        l("why do you ask");l("i am out of town on business tonight");
        l("i am on my way");l("hopefully this can wait until monday");
        l("what's going on");l("don't they have some conflicts here");
        l("did that happen");l("should systems manage the migration");
        l("is she done yet");l("are you going to join us for lunch");
        l("thanks good job");l("he would love anything about rocks");
        l("not even in yet");l("hope your trip to florida was good");
        l("this looks fine");l("has dynegy made a specific request");
        l("we're on the way");l("no there will be plenty of others");
        l("i'll get you one");l("i think those are the right dates");
        l("i have a request");l("do you want to fax it to my hotel");
        l("did you get this");l("i changed that in one prior draft");
        l("florida is great");l("what number should he call you on");
        l("i sent it to her");l("i'm waiting until she comes home");
        l("sorry about that");l("we need to talk about this month");
        l("not at this time");l("we are waiting on the cold front");
        l("thai sounds good");l("can you help get this cleared up");
        l("what do you hear");l("i have a high level in my office");
        l("i am at the lake");l("please call tomorrow if possible");
        l("i told you silly");l("they are more efficiently pooled");
        l("what is the mood");l("i wanted to go drinking with you");
        l("are you in today");l("i think that is the right answer");
        l("are you available");l("did you talk to ava this morning");
        l("no surprise there");l("it's not looking too good is it");
        l("what do you think");l("i'll catch up with you tomorrow");
        l("if so what was it");l("could you see where this stands");
        l("i am trying again");l("is this the only time available");
        l("have a great trip");l("thanks for the quick turnaround");
        l("i talked to duran");l("thank you for your prompt reply");
        l("i'm going to class");l("i would be glad to participate");
        l("what's his problem");l("has anyone else heard anything");
        l("we are all fragile");l("can we have them until we move");
        l("chris foster is in");l("do we have anyone in portland");
        l("money wise that is");l("john this message concerns me");
        l("no material impact");l("call me to give me a heads up");
        l("you're the greatest");l("she has absolutely everything");
        l("travis is in charge");l("we can have wine and catch up");
        l("what is up with ene");l("i hope you are feeling better");
        l("nice weather for it");l("i'll call you in the morning");
        l("i am walking in now");l("hope you guys are doing fine");
        l("what is in the plan");l("pressure to finish my review");
        l("that would be great");l("i would like to attend if so");
        l("i'm in stan's office");l("thursday works better for me");
        l("what's your proposal");l("i'm glad she likes her tree");
        l("ken agreed yesterday");l("you have a nice holiday too");
        l("are you being a baby");l("neil has been asking around");
        l("see you on the third");l("what is the purpose of this");
        l("did we get ours back");l("thanks for checking with me");
        l("will it be delivered");l("i've never worked with her");
        l("i can return earlier");l("please revise accordingly");
        l("do you need it today");l("i will bring john brindle");
        l("this is good i think");l("could you try ringing her");
        l("can you help me here");l("and leave my school alone");
        l("don't forget the wood");l("both of us are still here");
        l("i'm glad you liked it");l("what's your phone number");
        l("are you going to call");l("i was planning to attend");
        l("need to watch closely");l("get with mary for format");
        l("this seems fine to me");l("i agreed terms with greg");
        l("we just need a sitter");l("we will keep you posted");
        l("we must be consistent");l("jan has a lot of detail");
        l("i will be back friday");l("it reads like she is in");
        l("if not can i call you");l("please send me an email");
        l("take what you can get");l("how soon do you need it");
        l("you can talk to becky");l("thanks for your concern");
        l("do we need to discuss");l("they have capacity now");
        l("just playing with you");l("i am almost speechless");
        l("a gift isn't necessary");l("what is the cost issue");
        l("we will get you a copy");l("are you feeling better");
        l("did you differ from me");l("this is very sensitive");
        l("their key decision maker did not show which is not a good sign");
    }
}