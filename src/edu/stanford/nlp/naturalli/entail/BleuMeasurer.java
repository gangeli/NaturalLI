/*
 *  Copyright (c) 2009 Ondrej Dusek
 *  All rights reserved.
 * 
 *  Redistribution and use in source and binary forms, with or without modification, 
 *  are permitted provided that the following conditions are met:
 *  Redistributions of source code must retain the above copyright notice, this list 
 *  of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice, this 
 *  list of conditions and the following disclaimer in the documentation and/or other 
 *  materials provided with the distribution.
 *  Neither the name of Ondrej Dusek nor the names of their contributors may be
 *  used to endorse or promote products derived from this software without specific 
 *  prior written permission.
 * 
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 *  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 *  INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE 
 *  OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
 *  OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/**
 * @file BleuMeasurer.java An implementation of the BLEU measure.
 * @author Ondřej Dušek
 *
 * (Copied to Stanford NLP by Gabor Angeli)
 */
package edu.stanford.nlp.naturalli.entail;

import java.util.Enumeration;
import java.util.Hashtable;

/**
 * An implementation of the BLEU measure.
 */
public class BleuMeasurer {

    /* CONSTANTS */

    /** We'll consider up to 4-grams in BLEU */
    private final int MAX_NGRAM;

    /* FIELDS */
    
    /** Number of clipped hits throughout the corpus */
    private int clippedHits [];
    /** Number of n-grams in the candidate corpus */
    private int candLenghts [];

    /** Length of the reference corpus */
    private int refLength;


    /* METHODS */

    /**
     * Constructor, just inits (zeroes) everything.
     */
    public BleuMeasurer(int MAX_NGRAM){
        this.MAX_NGRAM = MAX_NGRAM;
        this.candLenghts = new int [MAX_NGRAM];
        this.clippedHits = new int [MAX_NGRAM];
    }

    public BleuMeasurer(){
        this(4);
    }

    /**
     * Adds a sentence to the considered corpus (the statistics are
     * summed up).
     *
     * @param refTokens the reference translation
     * @param candTokens the candidate translation
     */
    public void addSentence(String [] refTokens, String [] candTokens){

        for (int i = 1; i <= MAX_NGRAM; ++i){
            // adds clipped hits to stats for corpus
            saveClippedHits(i, refTokens, candTokens);
            // adds total n-gram candidate length to stats for corpus
            this.candLenghts[i-1] += candTokens.length - i + 1;
        }

        // adds reference lenght to stats for corpus
        this.refLength += refTokens.length;
    }

    /**
     * Resets the currently computed statistics to zero.
     *
     */
    public void reset(){

        this.refLength = 0;
        this.candLenghts = new int [MAX_NGRAM];
        this.clippedHits = new int [MAX_NGRAM];
    }


    /**
     * Returns the current BLEU score, according to the stored
     * statistics.
     *
     * @return the current BLEU score
     */
    public double bleu(){

        double bp = 1.0; // brevity penalty
        double precAvg = 0.0; // modified n-gram precisions
        double bleu;

        if (this.candLenghts[0] <= this.refLength){
            bp = Math.exp(1.0 - this.refLength/this.candLenghts[0]);
        }

        for (int i = 0; i < MAX_NGRAM; ++i){
            precAvg += (1.0/MAX_NGRAM) * Math.log(this.clippedHits[i] / (double)this.candLenghts[i]);
        }

        bleu = bp * Math.exp(precAvg);
        return bleu;
    }

    /**
     * Saves the clipped hits for a sentence to the total statistics.
     *
     * @param nGram the n-gram setting (1 to 4)
     * @param refTokens the reference translation
     * @param candTokens  the candidate translation
     */
    private void saveClippedHits(int nGram, String [] refTokens, String [] candTokens) {

        Hashtable<String,Integer> candStats, refStats;

        candStats = collectStatistics(candTokens, nGram);
        refStats = collectStatistics(refTokens, nGram);

        Enumeration<String> candNGrams = candStats.keys();
        while (candNGrams.hasMoreElements()){

            String curNGram = candNGrams.nextElement();
            int candCnt, refCnt = 0;

            candCnt = candStats.get(curNGram);

            if (refStats.containsKey(curNGram)){
                refCnt = refStats.get(curNGram);
            }

            this.clippedHits[nGram-1] += Math.min(candCnt, refCnt);
        }
    }

    /**
     * Collects statistics for given n-gram and a string (used for computing
     * modified n-gram precision)
     * @param tokens the string whose statistics are to be computed
     * @param nGram n-gram setting
     * @return the statistics -- for each possible n-gram, there is a key in the hashtable, and
     *        the number of occurrences is the value
     */
    private static Hashtable<String,Integer> collectStatistics(String [] tokens, int nGram){

        Hashtable<String,Integer> stats = new Hashtable<String,Integer>();

        for (int i = 0; i < tokens.length; ++i){

            String nGramString = makeNGram(tokens, i, nGram);

            if (nGramString == null){
                break;
            }

            if (stats.containsKey(nGramString)){
                stats.put(nGramString, stats.get(nGramString) + 1);
            }
            else {
                stats.put(nGramString, 1);
            }
        }
        return stats;
    }

    /**
     * Composes an n-gram out of the first nGram tokens from the position off in the
     * candTokens array. Returns null if the desired nGram is out of range.
     *
     * @param candTokens array where to get the tokens from
     * @param off starting position
     * @param nGram the n-gram setting (how long an n-gram to make)
     * @returns the desired n-gram or null if out of range
     */
    private static String makeNGram(String[] candTokens, int off, int nGram) {

        StringBuilder sb;

        if (off + nGram > candTokens.length){
            return null;
        }

        sb = new StringBuilder(candTokens[off]);

        for (int j = off + 1; j < off + nGram; ++j){
            sb.append(' ');
            sb.append(candTokens[j]);
        }

        return sb.toString();
    }

}
