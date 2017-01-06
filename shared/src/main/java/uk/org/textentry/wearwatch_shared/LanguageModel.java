package uk.org.textentry.wearwatch_shared;
/**
 * The LanguageModel models the language and predicts the next character based
 * on the learned patterns from the dictionary.
 *
 *  Alphabet is fixed at simple Latin 26 character alphabet plus dash and apostrophe
 *
 *  Currently records max 7-gram patterns - so uses previous 7 characters to predict
 *  the next. Modelling is based on WittenBell gentle degradation when pattern for longer
 *  strings is missing or rare (so "x this" degrades to " this" as x is rare at end of a word
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
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class LanguageModel {

    double[] unigrams = new double[128];
    double unigramTotal = 0;
    NGram ngram=new NGram();

    public LanguageModel(){
    }

    /**
     * Learn the given sentance in the language model - assumes a full sentance and not, say, individual words
     * @param sentance a sentance in lowercase without punctuation
     */
    public void learn(String sentance){
        String s = " "+filter(sentance)+" ";
        for (int i=0; i<s.length(); i++){
            char c = s.charAt(i);
            if (c>128) Log.e("MDD", "Error character "+c);
            unigrams[c]++;
            unigramTotal++;
            for (int j=Math.max(0,i-7); j<i; j++)
                ngram.learn(s.substring(j,i),c);
        }
    }

    /**
     * Filter the given string to skip all non word characters
     * Word characters currently defined as abcdefghijklmnopqrstuvwxyz-'
     * @param s the original string with possible invalid characters
     * @return the new string with only valid characters
     */
    private String filter(String s){
        StringBuilder sb = new StringBuilder(s);
        boolean prevWasSpace = true;
        for (int i=0; i<s.length(); i++){
            char c = s.charAt(i);
            if ( (Character.isAlphabetic(c)) || (c=='\'') || (c=='-') ){
                sb.append(Character.toLowerCase(c));
                prevWasSpace = false;
            } else if (!prevWasSpace) {
                sb.append(' ');
                prevWasSpace = true;
            }
        }
        return sb.toString().trim();
    }

    /**
     * Get the probability that a space occurs after string
     * Used to estimate likiehood of s being at the end of a word
     * Floored to range of 0.2 ... 1.0 so never fully certain
     * @param s the string that a space may or may not follow
     * @return estimated probability that a space could follow the given s
     */
    public double probBeforeSpaceFloored(String s){
        double OUTPUT_LOW=0.1, OUTPUT_HIGH=1.0;

        double[] matches = ngram.getAll(s);
        double cS = Util.arraySum(matches);
        if (cS==0)
            return OUTPUT_LOW;
        else
//            return (matches[' ']/cS + 0.25) / 1.2;
            return  ((matches[' ']/cS )  ) * (OUTPUT_HIGH - OUTPUT_LOW) + OUTPUT_LOW;

    }

    /**
     * Get the Witten Bell n-gram probabilities for all characters for the given string
     * Currently capped to a length of 7 but the source string can be longer and is trimmed
     *
     * Witten Bell mixes sames length prediction with shorter predictions using a lambda weighting
     * between the two scores based on the confidence it has in the same length prediction.
     *
     * For example if s="this is" then the most 7-gram character probabilities for "this is" will be
     * merged with the probabilities of "his is", "is is", "s is", " is", "is", "s" and finally the
     * unigram probability of characters independently of history. This is complex but ensures that
     * the best long evidence is used appropriately.
     * @param s the "history" - what has been typed so far
     * @return an array of probabilities [0...1] for each character in the full ASCII 7-bit char set [0...127]
     */
    //TODO Consider ways of stopping recursion early
    public double[] wittenBell(String s){
        if (s.length()>7) s = s.substring(s.length()-7);
        double[] matches = ngram.getAll(s);
        double uS = Util.countNonZero(matches);
        double cS = Util.arraySum(matches);

        double uniS = 0; for (int i=0; i<128; i++) uniS+=unigrams[i];

        if (cS==0){
			// Context unknown - based on shorter
            if (s.length()==0)
                return Util.product(1.0/uniS, unigrams);
            else
                return wittenBell(s.substring(1));
        }
        double lambda = 1-(uS/(uS+cS));
        double[] wittenbell;
        if (s.length()>1)
            wittenbell = Util.add( Util.product(lambda, Util.product(1.0/cS,matches)) , Util.product(1.0-lambda, wittenBell(s.substring(1))) );
        else
            wittenbell = Util.add( Util.product(lambda, Util.product(1.0/cS,matches)) , Util.product(1.0-lambda, Util.product(1.0/uniS, unigrams)) );

        return wittenbell;
    }

    class NGram{
        Map<String,NextStore> store = new HashMap<>();

        public void learn(String context, char c){
            if (!store.containsKey(context))
                store.put(context, new NextStore());
            store.get(context).freq[c]++;
        }

        public double[] getAll(String context){
            if (store.containsKey(context))
                return store.get(context).freq;
            else
                return new double[128];
        }
    }
    class NextStore{
        double[] freq = new double[128];
    }
}
