package uk.org.textentry.wearwatch_shared;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashSet;

/**
 * The keyboard view - the base model that knows where all keys are, draws them and calculates the
 * probability of a tap being on each key
 *
 * Currently the tap model is assumes a circular distribution around each key that is the same for
 * each key - effectively a complex way of doing distance to key.
 *
 *  Alphabet is fixed at simple Latin 26 character alphabet plus dash and apostrophe
 *
 *  Flexible design supports transparency of keyboard and variable sizing
 *
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

public class KeyboardView extends View {
    Context context;
    private Point[] keyLocations;
    private double opaqueness = 0.9;
    private KeyboardEventHandler eventListener;
    private char[] charSet;
    private String[] suggestions;
    private int[] suggestionsX;
    private int suggestionBarCentreY = 20, suggestionBarBottom = 40;
    private Rect suggestBarBackgroundRect = new Rect(0,100,500,200);
    private boolean keyboardIsHidden = false;

    private static int baseKeyColor = Color.argb(200,150,200,200);
    private static int baseBackgroundColor = Color.argb(200,255,255,255);
    private static int baseDarkBackgroundColor = Color.argb(200,200,200,200);
    private static int HIGHLIGHTCOLOR = Color.argb(255,255,0,0);
    private static final float SPACEATTOP = 0.05f, SPACEATBOTTOM = 0.12f, SPACELEFTRIGHT=0.05f;
    private static double SD_FOR_TAPS_IN_PIXELS = 0.9;
    private static final boolean SHOW_KEY_CENTRES = false;

    private static final String ROW1="qwertyuiop", ROW2="asdfghjkl", ROW3="-zxcvbnm'";
    private static final int WIDEST_ROW = Math.max(Math.max(ROW1.length(), ROW2.length()),ROW3.length());
    private static final CharSet CHAR_SET = new CharSet(ROW1+ROW2+ROW3);
    private double bottomOfKeyboard;

    public KeyboardView(Context context){
        super(context);
        commonConstructor(context);
    }

    public KeyboardView(Context context, AttributeSet attribs){
        super(context, attribs);
        commonConstructor(context);
    }

    public KeyboardView(Context context, AttributeSet attribs, int defStyle){
        super(context, attribs, defStyle);
        commonConstructor(context);
    }

    private void commonConstructor(Context context)  {
        this.context = context;
        charSet = new char[CHAR_SET.size()];
        try {
            for (int i=0; i<CHAR_SET.size(); i++) charSet[i]=indexToChar(i);
        } catch (KeyboardException e) {
            LogCat.e("ERROR IN COMMON CONSTRUCTOR - "+e.getMessage());
            e.printStackTrace();
        }

        baseKeyColor = ContextCompat.getColor(context, R.color.keyboardColour);
    }

    private Point[] getLocationsForSize(float fullWidth, float fullHeight, float xoffset, float yoffset, float width, float height, double... rowStretches) throws KeyboardException {
        //Measurements adjusted to leave space for additional top row of suggestions
        String[] rows = {ROW1, ROW2, ROW3};
        if ( (rowStretches.length!=0)&&(rowStretches.length!=rows.length)) throw new ArrayIndexOutOfBoundsException("Row stretches do not match the keyboard row count of "+rows.length);

        double keyWidth = width/WIDEST_ROW;
        double keyHeight = height/(rows.length+1.0);

        bottomOfKeyboard = yoffset+height+keyHeight/2; //half keyheight added for comfort margin.

        Point[] pts = new Point[charSet.length];
        suggestionBarCentreY = Math.round(yoffset+(int)Math.round(0.5*keyHeight));
        suggestionBarBottom =  (int)Math.round(suggestionBarCentreY+0.33*keyHeight) ; //Math.round(yoffset+(int)Math.round(1.0*keyHeight));
        suggestBarBackgroundRect = new Rect(0, (int)Math.round(suggestionBarCentreY-0.33*keyHeight), (int)Math.round(fullWidth), (int)Math.round(suggestionBarCentreY+0.33*keyHeight));

        for (int row = 0; row<rows.length; row++){
            double offset = (WIDEST_ROW*keyWidth-rows[row].length()*keyWidth)/2;
            double rowKeyWidth = keyWidth;
            if (rowStretches.length!=0){
                rowKeyWidth=rowKeyWidth*rowStretches[row];
                offset = offset - (rowKeyWidth-keyWidth)*rows[row].length()/2.0;
           }
            for (int c=0; c<rows[row].length(); c++) {
                pts[charToIndex(rows[row].charAt(c))] = new Point(Math.round(xoffset + (int) Math.round(offset + (0.5 + c) * rowKeyWidth)), Math.round(yoffset + (int) Math.round((1.5 + row) * keyHeight)));
            }
        }

        return pts;
    }

    public void configureAsHidden(){
        keyboardIsHidden=true;
    }

    /**
     * Setup the keyboard parameters.
     * A rather odd way of doing it but by calling configure instead of relying on this.getWidth or this.getHeight
     * then we can run this code offline in experimental mode and allow engine parameters to be set too.
     * @param width width of the keyboard in on-screen pixels (e.g. 320)
     * @param height height of the keyboard
     * @param flexibilityOfTapInKeyWidths the standard deviation of taps from the centre of the key as a fraction of the width of a key, e.g. 1.0 = 1 keywidth
     */
    public void configure(int width, int height, int extraSpaceAtTop, double flexibilityOfTapInKeyWidths, double row1stretch, double row2stretch, double row3stretch){
        int spaceattop = (int)Math.round(height*SPACEATTOP)+extraSpaceAtTop;
        int spaceatbottom = (int)Math.round(height*SPACEATBOTTOM);

        keyboardIsHidden = false;
        try {
            keyLocations = getLocationsForSize(width, height, SPACELEFTRIGHT*width, spaceattop, width-2*SPACELEFTRIGHT*width, height-(spaceattop+spaceatbottom), row1stretch, row2stretch, row3stretch);
        } catch (KeyboardException e) {
            LogCat.e("ERROR IN CONFIGURING KEYBOARD "+e.getMessage());
            e.printStackTrace();
        }

        this.SD_FOR_TAPS_IN_PIXELS = flexibilityOfTapInKeyWidths * width/10.0;//assumes widest row is 10 chars
    }

    @Override
    protected void onDraw(Canvas canvas){
        super.onDraw(canvas);

        if ( (!keyboardIsHidden) && (canvas.getWidth()>0) && (keyLocations!=null)){
            int keyColor =    Color.argb(opaqueMode==OPAQUE_ALL_FULL?200:(int)Math.round(40+215*opaqueness), (int)Math.round(Color.red(baseKeyColor)*(1-opaqueness)), (int)Math.round(Color.green(baseKeyColor)*(1-opaqueness)), (int)Math.round(Color.blue(baseKeyColor)*(1-opaqueness)));
            int backgroundColor = Color.argb(opaqueMode==OPAQUE_ALL_FULL?0:(int)Math.round(0+240*opaqueness), Color.red(baseBackgroundColor), Color.green(baseBackgroundColor), Color.blue(baseBackgroundColor));
            int darkBackgroundColor = Color.argb(opaqueMode==OPAQUE_ALL_FULL?255:(int)Math.round(0+200*opaqueness), Color.red(baseDarkBackgroundColor), Color.green(baseDarkBackgroundColor), Color.blue(baseDarkBackgroundColor));

            float fontsize = 30;
            Paint paintBG = new Paint(), paintText = new Paint(), paintHighlight = new Paint(), paintDarkBackground = new Paint();
            paintBG.setColor(backgroundColor);
            paintText.setColor(keyColor);
            paintText.setTextSize(fontsize);
            paintHighlight.setColor(HIGHLIGHTCOLOR);
            paintDarkBackground.setColor(darkBackgroundColor);

            canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), paintBG);

            canvas.drawRect(suggestBarBackgroundRect, paintDarkBackground);

            try {
                for (int i = 0; i<keyLocations.length; i++){
                    Point p = keyLocations[i];
                    String c = ""+indexToChar(i);
                    float charWidth = paintText.measureText(c);
                    canvas.drawText(c,p.x-charWidth/2, p.y+fontsize*0.3f, paintText);

                    if (SHOW_KEY_CENTRES) canvas.drawRect(p.x-2,p.y-2,p.x+2,p.y+2, paintHighlight);
                }
            } catch (KeyboardException e) {
                LogCat.e("ERROR IN DRAW - "+e.getMessage());
                e.printStackTrace();
            }

            if ( (suggestions!=null) && (suggestions.length>0)){
                double offsetX = canvas.getWidth()*0.2;
                double suggestWidth = (canvas.getWidth()-2*offsetX)/suggestions.length;
                suggestionsX = new int[suggestions.length];
                int suggestionsY = (int) Math.round(suggestionBarCentreY+fontsize*0.3f);
                for (int s=0; s<suggestions.length; s++) {
                    float stdTextSize = paintText.getTextSize();
                    float sWidth = paintText.measureText(suggestions[s]);
                    while (sWidth>suggestWidth){
                        paintText.setTextSize(paintText.getTextSize()*0.9f);
                        sWidth = paintText.measureText(suggestions[s]);
                    }
                    paintText.setTextSize(paintText.getTextSize()*0.95f);
                    sWidth = paintText.measureText(suggestions[s]);
                    suggestionsX[s] = (int) Math.round(offsetX + (s + 0.5) * suggestWidth);
                    float x = suggestionsX[s] - sWidth/2;
                    canvas.drawText(suggestions[s], x, suggestionsY, paintText);
                    paintText.setTextSize(stdTextSize);

                    if (SHOW_KEY_CENTRES) canvas.drawRect(suggestionsX[s]-2,suggestionsY-2-fontsize/2+2,suggestionsX[s],suggestionsY+2-fontsize/2+2, paintHighlight);

                }
            }
        }
        else {
            // ignoring draw - keyboard is hidden
        }
    }

    public void setSuggestions(String... suggestions){
        this.suggestions = suggestions;
        invalidate();
    }
    public void clearSuggestions(){
        this.suggestions = null;
        invalidate();
    }

    private void handleSuggestBar(float x, float y){
        if ( (suggestions!=null) && (suggestions.length>0)) {
            double minDistance = Double.MAX_VALUE;
            int minIndex = 0;
            for (int i = 0; i < suggestionsX.length; i++) {
                double d = Math.abs(suggestionsX[i]-x);//distance(touch, new Point(suggestionsX[i], suggestionBarCentreY));
                if (d < minDistance) {
                    minDistance = d;
                    minIndex = i;
                }
            }
            String suggestionPicked = suggestions[minIndex];

            //Tell the eventListener about the new word then start a new word
            eventListener.onKeyboardSuggestionPicked(suggestionPicked);
        }
    }

    public void handleTap(MotionEvent ev) throws KeyboardException {

        if (!keyboardIsHidden) {
            Point touch = new Point((int) Math.round(ev.getRawX()), (int) Math.round(ev.getRawY()));

            LogCat.d("Tap at "+ev.getRawY() +" (cooked="+ev.getY()+") suggestbot = "+suggestionBarBottom + " keyboardbot = "+bottomOfKeyboard);

            float y = ev.getRawY();

            if (y < suggestionBarBottom) {
                handleSuggestBar(touch.x,touch.y);
            } else if (y<=bottomOfKeyboard){
                double minDistance = Double.MAX_VALUE;
                int minIndex = 0;
                for (int i = 0; i < keyLocations.length; i++) {
                    double d = distance(touch, keyLocations[i]);
                    if (d < minDistance) {
                        minDistance = d;
                        minIndex = i;
                    }
                }
                if (eventListener != null) {
                    char c = indexToChar(minIndex);
                    if (Util.IS_EMULATOR && (c == '↑'))
                        eventListener.onKeyboardBackspace();
                    else if (Util.IS_EMULATOR && (c == ','))
                        eventListener.onKeyboardSpace();
                    else
                        eventListener.onKeyboardLetter(touch.x, touch.y, c);
                } else {
                    LogCat.e("No event sent - no listener registered");
                }
            } else {
                // Tap ignored - off keyboard
            }
        }
        else {
            // ignoring tap - keyboard is hidden"
        }
    }

    public void handleHorizontalFling(boolean toLeft){
        if (!keyboardIsHidden)
            if (toLeft)
                eventListener.onKeyboardBackspace();
            else
                eventListener.onKeyboardSpace();
        //else "ignoring horizontal fling - keyboard is hidden");
    }

    public Point[] getKeyLocations(){
        return keyLocations;
    }

    private double distance(Point p1, Point p2){
        return Math.sqrt( (p1.x-p2.x)*(p1.x-p2.x) + (p1.y-p2.y)*(p1.y-p2.y) );
    }

        public int charToIndex(char c) throws KeyboardException {
        if (Character.isAlphabetic(c))
            return Character.toLowerCase(c)-'a';
        else switch (c){
            case '-': return 26;
            case '\'': return 27;
            default: throw new KeyboardException("Unsupported character code "+c);
        }
    }

    public char indexToChar(int i) throws KeyboardException {
        if (i<26)
            return (char)('a'+i);
        else switch (i){
            case 26: return '-';
            case 27: return '\'';
            default: throw new KeyboardException("Unsupported character code "+i);
        }
    }

    public static final int OPAQUE_ALL_FULL=1, OPAQUE_VARIABLE=2;
    private static final double OPAQUE_DEFAULT=0.9;
    private int opaqueMode = OPAQUE_ALL_FULL;
    public void setOpaquenessMode(int mode){
        this.opaqueMode = mode;
        this.opaqueness = OPAQUE_DEFAULT;
    }
    public void setOpaqueness(double opaqueness){
        if (this.opaqueness!=opaqueness) {
            this.opaqueness = opaqueness;
            this.invalidate();
        }
    }

    public void setKeyboardEventHandler(KeyboardEventHandler eventListener){
        this.eventListener = eventListener;
    }

    public char[] getCharSet(){
        return charSet;
    }

    public static final Point SPACE_POINT = new Point(-1,-1);
    public Point getKeyCentre(char c) throws KeyboardException {
        if (c==' ') return SPACE_POINT;
        return keyLocations[charToIndex(c)];
    }

    public double distance(char expectedChar, int x, int y) {
        try {
            Point p1 = this.getKeyCentre(expectedChar);
            Point p2 = new Point(x,y);
            return Math.sqrt(Math.pow((p2.x - p1.x), 2) + Math.pow((p2.y - p1.y), 2));
        } catch (KeyboardException e) {
            return 9999;
        }
    }

    public interface KeyboardEventHandler {
        public void onKeyboardLetter(int x, int y, char nearestChar);
        public void onKeyboardBackspace();
        public void onKeyboardSpace();
        public void onKeyboardSuggestionPicked(String s);
    }

    public static double test2DGuassianDistance(Point target, Point tap, double sx, double sy, double cutDistanceAsSD){
        //returns 2D Guassian Distance but only if horizontall and verticalling within 2 sd, else returns 0 as shortcut
        if ( Math.abs(tap.x-target.x)>cutDistanceAsSD*sx || Math.abs(tap.y-target.y)>cutDistanceAsSD*sy)
            return 0;
        else {
            double zx = (tap.x-target.x)/sx;
            double zy = (tap.y-target.y)/sy;

            return Math.exp(-( (zx*zx+zy*zy)/2 ));
        }
    }

    public double[] letterProbabilitiesForTap(int x, int y) {//TODO make guassian
        double[] d = new double[keyLocations.length];
        double sum = 0;
        Point pt = new Point(x, y);
        for (int i = 0; i < keyLocations.length; i++) {
            d[i] = test2DGuassianDistance(pt,keyLocations[i], SD_FOR_TAPS_IN_PIXELS, SD_FOR_TAPS_IN_PIXELS, 3.0);
            sum=sum+d[i];
        }
        double[] ascii = new double[128];
        for (int i = 0; i < keyLocations.length; i++) {
            try {
                char c = indexToChar(i);
                if (c<128)
                    ascii[c]=d[i]/sum;
            } catch (KeyboardException e) {
                LogCat.e("ERROR IN LETTER PROBABILITIES CALCULATION FOR ILLEGAL CHARACTER ON KEYBOARD");
                e.printStackTrace();
            }
        }
        return ascii;
    }

    /**
     * Returns the total distance between the two Strings as a fraction of their length
     * so an average of 1.0 says that every key is 1.0 pixels from the other.
     * @param s1 one string for comparison
     * @param s2 second string for comparison
     * @return  distance between strings / length of strings or MAX_VALUE if different lengths
     */
    public double stringDistance(String s1, String s2) throws KeyboardException {
        if (s1.length()!=s2.length())
            return Double.MAX_VALUE;
        else {
            double p = 0;
            for (int i=0; i<s1.length(); i++)
                p = p + distance(  keyLocations[charToIndex(s1.charAt(i))]  ,  keyLocations[charToIndex(s2.charAt(i))] );
            return p/s1.length();
        }
    }

    private static class CharSet extends HashSet<Character> {
        public CharSet(){
            super();
        }
        public CharSet(String s){
            super();
            for (int i=0; i<s.length(); i++)
                add(s.charAt(i));
        }
    }

    public class KeyboardException extends Exception{
        public KeyboardException(String s) {
            super(s);
        }
    }
}
