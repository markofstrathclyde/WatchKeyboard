package uk.org.textentry.wearwatch_shared;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.ScrollView;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A bunch of utility methods to make coding easier....
 *
 * Includes
 *   - Check if we are on the emulator
 *   - memory and timestamp string creators
 *   - bunch of simple array vector calculations
 *   - filename string creator and file permission checker
 *   - scrolling functions
 *   - Levenshtein distance functions
 *
 *   Distributed under MIT License
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
public class Util {

    public static final boolean IS_EMULATOR = isEmulator();
    public static final boolean DEBUG = IS_EMULATOR;

    private static final boolean isEmulator() {
        // Modified from http://stackoverflow.com/questions/2799097/how-can-i-detect-when-an-android-application-is-running-in-the-emulator to have limit of 3
        int rating = 0;

        Log.d("MDD", "PRODUCT = "+Build.PRODUCT);
        Log.d("MDD", "MANUFACTURER = "+Build.MANUFACTURER);
        Log.d("MDD", "BRAND = "+Build.BRAND);
        Log.d("MDD", "DEVICE = "+Build.DEVICE);
        Log.d("MDD", "MODEL = "+Build.MODEL);
        Log.d("MDD", "HARDWARE = "+Build.HARDWARE);
        Log.d("MDD", "FINGERPRINT = "+Build.FINGERPRINT);


        if ((Build.PRODUCT.equals("sdk")) || (Build.PRODUCT.equals("google_sdk"))
                || (Build.PRODUCT.equals("sdk_x86")) || (Build.PRODUCT.equals("vbox86p")) || (Build.PRODUCT.equals("sdk_google_phone_x86"))
                || (Build.PRODUCT.equals("sdk_google_aw_x86")) || (Build.PRODUCT.equals("sdk_google_phone_x86_64"))) {
            rating++;
        }
        if ((Build.MANUFACTURER.equals("unknown")) || (Build.MANUFACTURER.equals("Genymotion"))) {
            rating++;
        }
        if ((Build.BRAND.equals("generic")) || (Build.BRAND.equals("generic_x86")) || (Build.BRAND.equals("Android"))) {
            rating++;
        }
        if ((Build.DEVICE.equals("generic")) || (Build.DEVICE.equals("generic_x86")) || (Build.DEVICE.equals("vbox86p"))) {
            rating++;
        }
        if ((Build.MODEL.equals("sdk")) || (Build.MODEL.equals("google_sdk")) || (Build.MODEL.equals("sdk_google_aw_x86"))
                || (Build.MODEL.equals("Android SDK built for x86"))) {
            rating++;
        }
        if ((Build.HARDWARE.equals("goldfish")) || (Build.HARDWARE.equals("vbox86")) || (Build.HARDWARE.equals("ranchu"))) {
            rating++;
        }
        if ((Build.FINGERPRINT.contains("generic/sdk/generic"))
                || (Build.FINGERPRINT.contains("sdk_google_phone_x86_64/generic_x86_64"))
                || (Build.FINGERPRINT.contains("generic_x86/sdk_x86/generic_x86"))
                || (Build.FINGERPRINT.contains("generic/google_sdk/generic"))
                || (Build.FINGERPRINT.contains("generic/google_sdk/generic"))
                || (Build.FINGERPRINT.contains("sdk_google_phone_x86/generic_x86"))
                || (Build.FINGERPRINT.contains("generic_x86/sdk_google_aw_x86"))
                ) {
            rating++;
        }

        LogCat.d( "Running on Emulator is "+(rating>4)+" ("+rating+")");
        return rating > 4;

    }

    public static String buildString(){
        StringBuffer sb = new StringBuffer();
        sb.append(""+Build.PRODUCT);
        sb.append(" "+Build.MANUFACTURER);
        sb.append(" "+Build.BRAND);
        sb.append(" "+Build.DEVICE);
        sb.append(" "+Build.MODEL);
        sb.append(" "+Build.HARDWARE);
        sb.append(" "+Build.FINGERPRINT);
        return sb.toString();
    }
    public static String getMemoryProfile() {

        final double MEG = 1024*1024.0;

//        Double allocated = new Double(Debug.getNativeHeapAllocatedSize())/new Double((1048576));
//        Double available = new Double(Debug.getNativeHeapSize())/1048576.0;
//        Double free = new Double(Debug.getNativeHeapFreeSize())/1048576.0;

        long freeSize = 0L;
        long totalSize = 0L;
        long maxSize = 0L;
        long availableSize = 0L;
        try {
            Runtime info = Runtime.getRuntime();
            freeSize = info.freeMemory();
            totalSize = info.totalMemory();
            maxSize = info.maxMemory();
            availableSize = maxSize - (totalSize-freeSize);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return String.format("%.2fMB", availableSize/MEG);
    }



    // --- Array Functions

    public static double arraySum(double[] a){
        double sum=0;
        for (int i=0; i<a.length; i++)
            sum+=a[i];
        return sum;
    }
    public static double countNonZero(double[] a){
        double sum=0;
        for (int i=0; i<a.length; i++)
            if (a[i]>0) sum++;
        return sum;
    }
    public static double[] product(double c, double[] a){
        double[] r = new double[a.length];
        for (int i=0; i<a.length; i++)
            r[i]=c*a[i];
        return r;
    }
    public static double[] dotProduct(double[]a, double[] b){
        if (a.length!=b.length) throw new ArrayIndexOutOfBoundsException("Array sizes do not match");
        double[] r = new double[a.length];
        for (int i=0; i<a.length; i++)
            r[i]=a[i]*b[i];
        return r;
    }
    public static double[] add(double[]a, double[] b){
        if (a.length!=b.length) throw new ArrayIndexOutOfBoundsException("Array sizes do not match");
        double[] r = new double[a.length];
        for (int i=0; i<a.length; i++)
            r[i]=a[i]+b[i];
        return r;
    }

    public static String getTimeStamp(){
        SimpleDateFormat s = new SimpleDateFormat("HH:mm:ss");
        String timestamp = s.format(new Date());
        return timestamp;
    }

    public static String getDateTimeID() {
        SimpleDateFormat s = new SimpleDateFormat("yyMMdd_HHmm");
        String timestamp = s.format(new Date());
        return timestamp;
    }

    public static void scrollToBottom(final ScrollView scrollView){
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    public static void scrollToTop(ScrollView scrollView){
        scrollView.scrollTo(0, 0);
    }

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    public static void verifyStoragePermissions(Activity activity) {
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    public static String constructedFileName(final Context CONTEXT, final String SUFFIX, final boolean TSV){
        // Handy utility for consistent naming of files in logs
        //.tsv isn't a standard but on Windows it isn't widely used so can then be mapped to Excel to make double clicking work
        String appName = CONTEXT.getResources().getString(R.string.app_name);
        appName = appName.replaceAll("[ /\\:]","");
        return appName+"_"+getDateTimeID()+"_"+SUFFIX+(TSV?".tsv":".txt");
    }



    /**
     * Probably most useful use of Levenshtein Distance
     *
     * @return  distance between the two given strings
     */
    public static int levenshteinDistanceIgnoreCaseAndPadding(final String S1, final String S2){
        return levenshteinDistance( S1.trim().toLowerCase(), S2.trim().toLowerCase());
    }

    private static int levenshteinDistance (CharSequence lhs, CharSequence rhs) {
        /**
         * Calculates the Levenshtein Distance between two strings - how many edits to lhs results in rhs
         * Edits can be additions, deletions or substitutions
         * Result can be used as a metric for how close one string is to another but is not adjusted for length so "hi" and "ha" have a distance of 1 as do "hwllo how are you"  and "hbllo how are you"
         * Also does not take into account distance on keyboard - so a substituion of "e" for "w" is a cost of 1, as is "p" for "w" despite being much further apart
         * Source and description at  https://en.wikibooks.org/wiki/Algorithm_Implementation/Strings/Levenshtein_distance#Java
         */

        int len0 = lhs.length() + 1;
        int len1 = rhs.length() + 1;

        // the array of distances
        int[] cost = new int[len0];
        int[] newcost = new int[len0];

        // initial cost of skipping prefix in String s0
        for (int i = 0; i < len0; i++) cost[i] = i;

        // dynamically computing the array of distances

        // transformation cost for each letter in s1
        for (int j = 1; j < len1; j++) {
            // initial cost of skipping prefix in String s1
            newcost[0] = j;

            // transformation cost for each letter in s0
            for(int i = 1; i < len0; i++) {
                // matching current letters in both strings
                int match = (lhs.charAt(i - 1) == rhs.charAt(j - 1)) ? 0 : 1;

                // computing cost for each transformation
                int cost_replace = cost[i - 1] + match;
                int cost_insert  = cost[i] + 1;
                int cost_delete  = newcost[i - 1] + 1;

                // keep minimum cost
                newcost[i] = Math.min(Math.min(cost_insert, cost_delete), cost_replace);
            }

            // swap cost/newcost arrays
            int[] swap = cost; cost = newcost; newcost = swap;
        }

        // the distance is the cost for transforming all letters in both strings
        return cost[len0 - 1];
    }
}
