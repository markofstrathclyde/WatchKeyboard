package uk.org.textentry.wearkeyboardapp;
/**
 * A simple activity on the Mobile that receives messages from the watch and shows
 * them in a console. Can be extended for file logging or other actions based on
 * received messages.
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
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ScrollView;
import android.widget.TextView;

import uk.org.textentry.wearwatch_shared.LogCat;
import uk.org.textentry.wearwatch_shared.Util;

public class MainPhoneActivity extends AppCompatActivity {
    private TextView consoleTV;
    private ScrollView consoleScroller;
    BroadcastListener receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main_phone);
        consoleScroller = (ScrollView)findViewById(R.id.scrollViewConsole);

        consoleTV = (TextView) findViewById(R.id.consoleTV);

        String appName = getResources().getString(R.string.app_name);
        try {
            addToConsole( "Mobile "+appName+" v"+getPackageManager().getPackageInfo(getPackageName(), 0).versionName+(Util.IS_EMULATOR ?"e":""));
        } catch (Exception e){addToConsole(appName);}

        IntentFilter filter = new IntentFilter("uk.org.textentry.wearwatch.Broadcast");
        receiver = new BroadcastListener(this);
        registerReceiver(receiver, filter);
    }

    @Override
    protected void onResume(){
        super.onResume();
        LogCat.d("onResume");
    }

    @Override
    protected void onPause() {
        LogCat.d("onPause");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        LogCat.d("onDestroy");
        unregisterReceiver(receiver);
        super.onDestroy();
        System.exit(0);//force a full exit to be paranoid the connection is closed and killed
    }

    public void onReceiveMessage(String s) {
        LogCat.d("Activity onReceiveMessage "+s);
        boolean processed = false;
        if (s.length()>0) {
            addToConsole(s);
        }
    }

    private void addToConsole(String s) {
        LogCat.d("      --> "+s);
        consoleTV.append(s+"\n");
        Util.scrollToBottom(consoleScroller);
    }
}
