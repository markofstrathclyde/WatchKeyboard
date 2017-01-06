package uk.org.textentry.wearwatch_shared;

/**
 * A utility class that manages the TopN objects
 * Each object must implement the TopNWeightedObject interface that specifies a getWeight() method
 * As each element is added it is insertion sorted into the topN store if it beats with current Nth element
 * getValues returns the sorted list in decreasing weight (best at 0)
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
//TODO Add Generic Typing

public class TopN {

    private final int N;
    private int numberElementsInArray;
    private TopNWeightedObject[] values;

    public TopN(int n){
        this.N = n;
        values = new TopNWeightedObject[N];
        numberElementsInArray = 0;
    }
    public void add(TopNWeightedObject obj){
        //find where to put it in the array
        int putAt = numberElementsInArray;
        for (int i=numberElementsInArray-1; i>=0; i--) {
            if (obj.getWeight() > values[i].getWeight())
                putAt = i;
            else
                break;//can't improve so give up
        }

        //if inside the array, shuffle and add
        if (putAt<N) {
            //shuffle elements up the array to make space
            for (int i=N-1; i>putAt; i--)
                values[i]=values[i-1];

            //store new value
            values[putAt]=obj;
        }

        numberElementsInArray=Math.min(N,numberElementsInArray+1);
    }
    public TopNWeightedObject[] getValues(){
        if (numberElementsInArray==N)
            return values;
        else {
            TopNWeightedObject[] shortList = new TopNWeightedObject[numberElementsInArray];
            for (int i=0; i<numberElementsInArray; i++)
                shortList[i]=values[i];
            return shortList;
        }
    }

    public interface TopNWeightedObject {
        public double getWeight();
        public void multiplyByWeight(double d);
    }
}
