package uk.org.textentry.wearwatch_shared;

/**
 * Simple data element of a string with an associated weight.
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

public class WeightedString implements TopN.TopNWeightedObject, Comparable{

    private final String string;
    private double weight;

    public WeightedString(String string, double weight){
        this.string = string;
        this.weight = weight;
    }
    public String getString() {
        return string;
    }

    @Override
    public double getWeight() {
        return weight;
    }

    @Override
    public void multiplyByWeight(double d) {
        weight *= d;
    }

    public String toString(){
        return String.format("%s(%.4f)", string, weight);
    }

    @Override
    public int compareTo(Object o) {
        if (this.weight > ((WeightedString)o).weight) return -1;
        if (this.weight < ((WeightedString)o).weight) return 1;
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        return this.string.equals(((WeightedString)o).string);
    }

    @Override
    public int hashCode(){
        return string.hashCode();
    }
}
