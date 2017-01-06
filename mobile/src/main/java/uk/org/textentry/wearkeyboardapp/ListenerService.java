package uk.org.textentry.wearkeyboardapp;

/**
 * An Android service that listens for messages  from the watch and rebroadcasts them
 * so that the main activity gets told about them
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

import android.content.Intent;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import uk.org.textentry.wearwatch_shared.LogCat;

public class ListenerService extends WearableListenerService {

    String nodeId;
    private static final int CONNECTION_TIME_OUT_MS=500;

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        LogCat.d("ListenerService.onMessageReceived");
        nodeId = messageEvent.getSourceNodeId();

        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        intent.setAction("uk.org.textentry.wearwatch.Broadcast");
        intent.putExtra("messageFromWatch", messageEvent.getPath());
        sendBroadcast(intent);
    }

}
