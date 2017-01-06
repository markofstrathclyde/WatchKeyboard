package uk.org.textentry.wearkeyboardapp;
/*
    Crash exception handler  http://stackoverflow.com/questions/4427515/using-global-exception-handling-on-android
 */

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v4.view.GestureDetectorCompat;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.BoxInsetLayout;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.concurrent.TimeUnit;

import uk.org.textentry.wearwatch_shared.KeyboardView;
import uk.org.textentry.wearwatch_shared.LogCat;
import uk.org.textentry.wearwatch_shared.TextStats;
import uk.org.textentry.wearwatch_shared.Util;
import uk.org.textentry.wearwatch_shared.WordPredictor;


public class MainWatchActivity extends WearableActivity implements GestureDetector.OnGestureListener, KeyboardView.KeyboardEventHandler, View.OnTouchListener {
    public static final int STATE_TYPING = 0;
    public static final int STATE_READING = 1;
    private static final String EMERGENCY_LOG_FILENAME = "error_log.txt";
    private static final double TAP_FLEXIBILITY_KEYWIDTHS = 0.8;
        private static final int CONNECTION_TIME_OUT_MS=600;
    private static final long NOTSTARTED = -1;
    private static final long DOUBLETAPTIMEOUT=500;

    private long nextButtonLastTap=NOTSTARTED;
    private TextView mTextView;
    private KeyboardView keyboardView;
    private GestureDetectorCompat mDetector;
    private WordPredictor predictor;
    private Button nextButton;
    private ScrollView textScrollView;

    private String versionString="";
    private int currentDisplayState = STATE_TYPING;
    private boolean onResumeSetUpDone = false;

    private String nodeId;

    private boolean hadFirstLetter = false; private long startTimeMS = NOTSTARTED;

    private boolean afterBackSpace=false;
    private boolean newWord=true;
    private Vibrator myVibrator =null;

    private GoogleApiClient googleApiMsgClient =null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LogCat.d(" ");
        LogCat.d("-------------------------------------------------------------");
        LogCat.d(" ");
        LogCat.d("onCreate");

        mTextView = null;
        keyboardView = null;
        mDetector = null;
        if (predictor != null) predictor.destroy();
        predictor = null;
        System.gc();

        setContentView(R.layout.activity_main_watch);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        BoxInsetLayout mContainerView = (BoxInsetLayout) findViewById(R.id.container);
        mContainerView.setOnTouchListener(this);

        textScrollView = (ScrollView) findViewById(R.id.scrollView);
        textScrollView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return false;
            }
        });

        mTextView = (TextView) findViewById(R.id.text);
        keyboardView = (KeyboardView) findViewById(R.id.keyboardView);

        predictor = new WordPredictor(keyboardView);
    }

    @Override
    public void onResume(){
        super.onResume();
        LogCat.d("onResume");

        if (onResumeSetUpDone) {
            quit();//we've been here before - trying to unPause without onCreate - which is how it should be but apparently isn't
        } else {

            try {
                versionString = (getResources().getString(R.string.app_name) + " v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName + (Util.IS_EMULATOR ? "e" : ""));
            } catch (Exception ignored) {
            }

            mDetector = new GestureDetectorCompat(this, this);

            googleApiMsgClient = getGoogleApiClient(this);
            retrieveDeviceNode();

            keyboardView.setOpaqueness(0.8);
            keyboardView.setKeyboardEventHandler(this);

            nextButton = (Button) findViewById(R.id.nextButton);
            nextButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    //Only allow next button if "double tapped"
                    Long now = System.currentTimeMillis();
                    if (view.getVisibility() == View.VISIBLE) {
                        if (nextButtonLastTap == NOTSTARTED)
                            nextButtonLastTap = now;
                        else if ((now - nextButtonLastTap) <= DOUBLETAPTIMEOUT) {
                            nextButtonLastTap = NOTSTARTED;
                            nextPhrase();
                        } else
                            nextButtonLastTap = now; //timed out
                    }
                }
            });

            setupForDisplayState(STATE_TYPING);
            mTextView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN)
                        if (currentDisplayState == STATE_READING) {
                            setupForDisplayState(STATE_TYPING);
                        } else {
                            setupForDisplayState(STATE_READING);
                        }
                    return true;
                }
            });

            //set event response to when the keyboard is laid out
            final ViewTreeObserver vto = keyboardView.getViewTreeObserver();
            final ViewTreeObserver.OnGlobalLayoutListener vtol = new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    LogCat.d("Keyboard layout done");
                    if (!onResumeSetUpDone) {
                        startInput();
                        onResumeSetUpDone = true;
                    }
                }
            };
            vto.addOnGlobalLayoutListener(vtol);

            //Add default exception handler that logs the exception to a file and then crashes as normal
            //Thanks to http://stackoverflow.com/questions/4427515/using-global-exception-handling-on-android
            final Thread.UncaughtExceptionHandler oldHandler =
                    Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(
                    new Thread.UncaughtExceptionHandler() {
                        @Override
                        public void uncaughtException(
                                Thread paramThread,
                                Throwable paramThrowable
                        ) {
                            //Do my own error handling here
                            Writer writer = new StringWriter();
                            PrintWriter printWriter = new PrintWriter(writer);
                            paramThrowable.printStackTrace(printWriter);
                            String err = paramThrowable.getMessage() + "\t" + writer.toString();
                            writeLocalCrashLogFile(err);

                            if (oldHandler != null)
                                oldHandler.uncaughtException(
                                        paramThread,
                                        paramThrowable
                                ); //Delegates to Android's error handling
                            else
                                System.exit(2); //Prevents the service/app from freezing
                        }
                    });

            //Check if crash recover file exists and if so, send the message to the phone after 10sec to allow normal booting
            try {
                final byte[] content = new byte[500];
                FileInputStream fos = openFileInput(EMERGENCY_LOG_FILENAME);
                final int length = fos.read(content);
                fos.close();
                deleteFile(EMERGENCY_LOG_FILENAME);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(10000);
                            LogCat.e("**** CRASH RECOVERY FILE READ ****");
                            String err = new String(content, 0, length);
                            LogCat.e(err);
                            sendMessageToPhone("ERROR", err);
                        } catch (Exception ignored) {
                        }
                    }
                }).start();
            } catch (IOException ignored) {
            }

        }
        sendMessageToPhone("Resumed", "Wear "+versionString);
    }

    private void writeLocalCrashLogFile(String err){
        LogCat.d("writeLocalCrashLogFile");
        try {
            FileOutputStream fos = openFileOutput(EMERGENCY_LOG_FILENAME, Context.MODE_WORLD_READABLE);
            err = " >> "+Util.getTimeStamp()+"\t"+err;
            fos.write(err.getBytes());
            fos.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    protected void onPause() {
        LogCat.d("onPause");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        LogCat.d("onDestroy");

        googleApiMsgClient.disconnect();

        super.onDestroy();

        mTextView = null;
        keyboardView = null;
        mDetector = null;
        predictor.destroy();
        predictor = null;
        System.gc();

        System.exit(0);//Belts and Braces - actually exit rather than allowing Android to keep things around "for quick restart"
        //TODO fix memory leaks and exploit the keeping around
    }

    private void startInput(){
        nextPhrase();
    }

    private void nextPhrase(){
        if (mTextView.length()>0) {
            TextStats predictorStats = predictor.finishSentanceAndStartAnew();
            if (predictorStats.valid()) {
                String predictorStatsString = predictorStats.toTabSeparatedString();
                sendMessageToPhone("sentenceData", predictorStatsString);
            }
        }
        setupForDisplayState(currentDisplayState);
        hadFirstLetter=false;
        afterBackSpace=false;
    }

    /*
    Convenience method that for default new state conditions of empty string and white
     */
    private void setupForDisplayState(int newState) {
        setupForDisplayState(newState, "|");
    }

    /*
        Setup the display for a study condition and state
        Involves setting the size of text area and keyboard plus changing the behaviour of the accelerometer
     */
    private void setupForDisplayState(int newState, String initialString){
        final int STD=45, LEFT=STD, RIGHT=STD, TOP=STD-2, BOTTOM=STD-5;
        final int LEFT_ONE=LEFT+20, RIGHT_ONE=RIGHT+20, TOP_ONE=TOP-10, BOTTOM_ONE=BOTTOM+220;

        sendMessageToPhone("displayStateChange", "New state = "+currentDisplayState+ "  "+ Util.getMemoryProfile());
        System.gc();//good time to slip in a little garbage collection!

        currentDisplayState = newState;
        nextButton.setVisibility(View.INVISIBLE);
        keyboardView.setSuggestions();

        //Set up text view area
        BoxInsetLayout.LayoutParams layoutParams=new BoxInsetLayout.LayoutParams(textScrollView.getLayoutParams());
        switch (newState){
            case STATE_TYPING:
                layoutParams.setMargins(LEFT_ONE, TOP_ONE, RIGHT_ONE, BOTTOM_ONE);
                break;
            case STATE_READING:
                layoutParams.setMargins(LEFT, TOP, RIGHT, BOTTOM);
                break;
        }
        textScrollView.setLayoutParams(layoutParams);
        if (initialString!=null) mTextView.setText(initialString);
        Util.scrollToBottom(textScrollView);
        textScrollView.setSmoothScrollingEnabled(false);

        //Set size of keyboard
        switch (newState){
            case STATE_TYPING:
                keyboardView.configure(keyboardView.getWidth(), keyboardView.getHeight(), 40, TAP_FLEXIBILITY_KEYWIDTHS, 1.0, 1.1, 0.9);
                break;
            case STATE_READING:
                keyboardView.configureAsHidden();
                break;
        }
        keyboardView.setOpaquenessMode(KeyboardView.OPAQUE_ALL_FULL);
        keyboardView.invalidate();
    }

    private void quit(){
        setupForDisplayState(STATE_READING,"Thank you\n\nClosing app...");
        nextButton.setVisibility(View.INVISIBLE);
        sendMessageToPhone("quit",Util.getTimeStamp());
        new Thread( new Runnable() { //Give 2s to close connection - I think?
            @Override
            public void run() {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
                finish();
            }
        }).start();
    }


    private GoogleApiClient getGoogleApiClient(Context context) {
        return new GoogleApiClient.Builder(context)
                .addApi(Wearable.API)
                .build();
    }
    private void retrieveDeviceNode() {
        if (googleApiMsgClient !=null)
            new Thread(new Runnable() {
                @Override
                public void run() {
                    googleApiMsgClient.blockingConnect(CONNECTION_TIME_OUT_MS, TimeUnit.MILLISECONDS);
                    NodeApi.GetConnectedNodesResult result =
                            Wearable.NodeApi.getConnectedNodes(googleApiMsgClient).await();
                    List<Node> nodes = result.getNodes();
                    if (nodes.size() > 0) {
                        nodeId = nodes.get(0).getId();
                    }
//                    googleApiMsgClient.disconnect();
                }
            }).start();
        else
            LogCat.e("Could not retrieveDeviceNode - null");
    }

    void sendMessageToPhone(final String tag, final String message) {
//        final GoogleApiClient client = getGoogleApiClient(this);
        LogCat.d("Sending message to phone: "+ tag+" -> "+message);
        final String nodeId = this.nodeId;
        final long startTimeMS = this.startTimeMS;
        final long nowTimeMS = System.currentTimeMillis();
        if ((googleApiMsgClient!=null) && (nodeId != null)) {
            final String timestamp = Util.getTimeStamp();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    LogCat.d("started thread");
                    googleApiMsgClient.blockingConnect(CONNECTION_TIME_OUT_MS, TimeUnit.MILLISECONDS);
                    Wearable.MessageApi.sendMessage(googleApiMsgClient, nodeId, timestamp+"\t"+""+"\t"+tag+"\t"+(startTimeMS==NOTSTARTED?"":nowTimeMS-startTimeMS)+"\t"+message, null);
//                    googleApiMsgClient.disconnect();
                }
            }).start();
        }
        else LogCat.d("Couldn't message phone - something's null "+(googleApiMsgClient==null) +" "+(nodeId == null));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        LogCat.d("Key "+keyCode);
        if ((keyCode == 0)) {
            onTwoTouches();//fake two touches for emulator on task-switch key
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            return true;
        }
        return false;
    }

//--- Handle keys ----//


    @SuppressLint("SetTextI18n")
    @Override
    public void onKeyboardLetter(int x, int y, char nearestChar) {
        LogCat.d("onKeyboardLetter "+nearestChar);
        vibrate(false);

        WordPredictor.PredictionResult result = predictor.suggestionFor(x,y);
        keyboardView.setSuggestions(result.predictions);
        mTextView.setText(result.fullText+"|");

        if (!hadFirstLetter){
            hadFirstLetter=true;
            startTimeMS = System.currentTimeMillis();
            Util.scrollToBottom(textScrollView);
            nextButton.setVisibility(View.VISIBLE);
        }

        afterBackSpace=false;
        newWord=false;

        Util.scrollToBottom(textScrollView);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onKeyboardBackspace() {
        LogCat.d("onKeyboardBackspace");
        try {
            vibrate(true);
            WordPredictor.PredictionResult result = predictor.deleteLast();
            String currentWord = result.currentSuggestion;
            keyboardView.setSuggestions(result.predictions);
            mTextView.setText(result.fullText+"|");
            newWord=currentWord.length()==0;
        } catch (KeyboardView.KeyboardException e) {
            LogCat.e("Error on backspace");
            e.printStackTrace();
        }
        Util.scrollToBottom(textScrollView);
        afterBackSpace=true;
    }


    @SuppressLint("SetTextI18n")
    @Override
    public void onKeyboardSpace() {
        LogCat.d("onKeyboardSpace");
        if (!newWord) {//not already put in a space
            vibrate(true);
            WordPredictor.PredictionResult finalWord = predictor.suggestionOnSpace();
            keyboardView.clearSuggestions();
            mTextView.setText(finalWord.fullText + "|");
            newWord = true;
        }
        Util.scrollToBottom(textScrollView);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onKeyboardSuggestionPicked(String s) {
        LogCat.d("onKeyboardSuggestionPicked");
        vibrate(true);
        WordPredictor.PredictionResult result = predictor.suggestionPicked(s);
        mTextView.setText(result.fullText+"|");
        keyboardView.clearSuggestions();
        Util.scrollToBottom(textScrollView);
        newWord = true;
    }

    //----- Handle Touch Events ----//
    public void onTwoTouches(){
        LogCat.d("Two touches on background");
         quit();
    }

    @Override
    public void onBackPressed() {
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        LogCat.d("onTouchEvent e");
        this.mDetector.onTouchEvent(event);
        if ((event.getPointerCount()==2) && (event.getActionMasked()==MotionEvent.ACTION_POINTER_UP)) {
            onTwoTouches();//useful for special controls like exit
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onSingleTapUp(MotionEvent motionEvent) {
        LogCat.d("onSingleTapUp");
        try {
            keyboardView.handleTap(motionEvent);
        } catch (KeyboardView.KeyboardException e) {
            LogCat.e("ERROR ON SINGLE TAP CAUSED BY KEYBOARD EXCEPTION " + e);
        }
        return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (Math.abs(velocityX)>Math.abs(velocityY))//largely horizontal{
            keyboardView.handleHorizontalFling(velocityX<0);
        return true;
    }

    @Override
    public boolean onDown(MotionEvent motionEvent) {        LogCat.d("onDown");
        return true;}
    @Override
    public void onShowPress(MotionEvent motionEvent) {        LogCat.d("onShowPress");
    }
    @Override
    public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {LogCat.d("onScroll");return true;}
    @Override
    public void onLongPress(MotionEvent motionEvent) {}

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        onTouchEvent(motionEvent);return true;
    }

    private void vibrate(final boolean longer){
        if (myVibrator ==null)
            myVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        (new Thread() {
            public void run() {
                myVibrator.vibrate(10*(longer?3:1));
            }
        }).start();
    }

}
