package uk.org.textentry.wearwatch_shared;
/**
 * A Simple utility class that holds details of a sentance entry for logging purposes
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
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class TextStats {
    JSONObject json = new JSONObject();
    String finalPhrase;
    boolean valid=false;

    public TextStats(String finalPhrase, long millisecondsToType, int backspaces, int suggestions){
        if (finalPhrase.length()!=0)
            try {
                finalPhrase = finalPhrase.trim();
                this.finalPhrase = finalPhrase;
                double words = finalPhrase.length()/5.0;
                double minutesToType = millisecondsToType/(1000.0*60);
                String seconds = String.format("%.1f", millisecondsToType/1000.0);
                String wpm = String.format("%.2f", (words/minutesToType));
                json.put("finalString", finalPhrase);
                json.put("inputTime", seconds);
                json.put("wpm", wpm);
                json.put("backspaces", backspaces);
                json.put("suggestions", suggestions);
                valid=millisecondsToType>0;
            } catch (JSONException e) {
                LogCat.e("JSON error "+e.getMessage());
            }
    }

    public String toTabSeparatedString(){
        StringBuilder sb = new StringBuilder();
        Iterator<String> it = json.keys();
        while (it.hasNext())
            try {
                sb.append(json.get(it.next())+"\t");
            } catch (JSONException e) {
                sb.append("???\t");
            }

        return sb.toString();
    }

    public String getFinalPhrase() {
        return finalPhrase;
    }

    public boolean valid() {
        return valid;
    }
}
