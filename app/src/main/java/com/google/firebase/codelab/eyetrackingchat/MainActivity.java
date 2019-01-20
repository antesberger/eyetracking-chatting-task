/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.codelab.eyetrackingchat;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.crashlytics.android.Crashlytics;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.firebase.ui.database.SnapshotParser;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.appindexing.FirebaseAppIndex;
import com.google.firebase.appindexing.Indexable;
import com.google.firebase.appindexing.builders.Indexables;
import com.google.firebase.appindexing.builders.PersonBuilder;
import com.firebase.ui.auth.AuthUI;

import io.fabric.sdk.android.Fabric;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;


public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener, GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageTextView;
        TextView messengerTextView;

        public MessageViewHolder(View v) {
            super(v);
            messageTextView = (TextView) itemView.findViewById(R.id.messageTextView);
            messengerTextView = (TextView) itemView.findViewById(R.id.messengerTextView);
        }
    }

    private static final String TAG = "MainActivity";
    public static final String MESSAGES_CHILD = "messages";
    public static final String ANONYMOUS = "anonymous";
    private String mUsername;
    private SharedPreferences mSharedPreferences;
    private static final String MESSAGE_URL = "http://friendlychat.firebase.google.com/message/";

    private Button mSendButton;
    private RecyclerView mMessageRecyclerView;
    private LinearLayoutManager mLinearLayoutManager;
    private ProgressBar mProgressBar;
    private EditText mMessageEditText;

    // Firebase instance variables
    private DatabaseReference mFirebaseDatabaseReference;
    private FirebaseRecyclerAdapter<ChatMessage, MessageViewHolder> mFirebaseAdapter;

    private int SIGN_IN_REQUEST_CODE = 10;
    String participant = "empty";
    String startTime = "no-date";
    String startTimeMs = "no-ms";

    private GestureDetectorCompat mDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fabric.with(this, new Crashlytics());

        Bundle bundle = getIntent().getExtras();
        if(bundle != null) {
            participant = bundle.getString("participant");
            startTime = bundle.getString("startTime");
            startTimeMs = String.valueOf(Calendar.getInstance().getTimeInMillis());
        }

        if (hasEyetrackingStarted()) {
            setContentView(R.layout.activity_main);
            mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            mUsername = ANONYMOUS; //defaul username

            // Initialize Firebase Auth
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                // Start sign in/sign up activity
                startActivityForResult(
                        AuthUI.getInstance()
                                .createSignInIntentBuilder()
                                .build(),
                        SIGN_IN_REQUEST_CODE
                );
            } else {
                // User is already signed in. Therefore, display a welcome Toast
                Toast.makeText(
                        this,
                        "Welcome " + participant,
                        Toast.LENGTH_LONG
                ).show();
                mUsername = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
            }

            //initialize headline of motionEvent log
            writeFileOnInternalStorage(MainActivity.this, "motionEvents.txt", "pointerID; eventTime; action; relativeX; relativeY; rawX; rawY; xPrecision; yPrecision; downTime; orientation; pressure; size; edgeFlags; actionButton; metaState; toolType; toolMajor; toolMinor;");

            // Initialize ProgressBar and RecyclerView.
            mProgressBar = findViewById(R.id.progressBar);
            mMessageRecyclerView = findViewById(R.id.messageRecyclerView);
            mLinearLayoutManager = new LinearLayoutManager(this);
            mLinearLayoutManager.setStackFromEnd(true);
            mMessageRecyclerView.setLayoutManager(mLinearLayoutManager);

            // New child entries
            mFirebaseDatabaseReference = FirebaseDatabase.getInstance().getReference();
            SnapshotParser<ChatMessage> parser = new SnapshotParser<ChatMessage>() {
                @Override
                public ChatMessage parseSnapshot(DataSnapshot dataSnapshot) {
                    ChatMessage chatMessage = dataSnapshot.getValue(ChatMessage.class);
                    if (chatMessage != null) {
                        chatMessage.setId(dataSnapshot.getKey());
                    }
                    return chatMessage;
                }
            };

            final DatabaseReference messagesRef = mFirebaseDatabaseReference.child(MESSAGES_CHILD);
            FirebaseRecyclerOptions<ChatMessage> options =
                    new FirebaseRecyclerOptions.Builder<ChatMessage>()
                            .setQuery(messagesRef.orderByChild("messageTime").startAt(startTimeMs), parser)
                            .build();

            mProgressBar.setVisibility(ProgressBar.INVISIBLE);
            mFirebaseAdapter = new FirebaseRecyclerAdapter<ChatMessage, MessageViewHolder>(options) {
                @Override
                public MessageViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {


                    LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
                    return new MessageViewHolder(inflater.inflate(R.layout.item_message, viewGroup, false));
                }

                @Override
                protected void onBindViewHolder(final MessageViewHolder viewHolder, int position, ChatMessage chatMessage) {
                    mProgressBar.setVisibility(ProgressBar.INVISIBLE);
                    if (chatMessage.getText() != null) {
                        viewHolder.messageTextView.setText(chatMessage.getText());
                        viewHolder.messageTextView.setVisibility(TextView.VISIBLE);

                        // write this message to the on-device index
                        FirebaseAppIndex.getInstance().update(getMessageIndexable(chatMessage));

                        // reset viewholder layout
                        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
                        resetParams.weight = 1.0f;
                        resetParams.gravity = Gravity.LEFT;
                        viewHolder.messageTextView.setLayoutParams(resetParams);
                        viewHolder.messengerTextView.setLayoutParams(resetParams);
                        viewHolder.messageTextView.setBackgroundResource(R.drawable.incoming_message_bubble);

                        if (mUsername.equals(chatMessage.getName())) {
                            Log.d("SENDER", "onBindViewHolder: " + viewHolder.getLayoutPosition());
                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
                            params.weight = 1.0f;
                            params.gravity = Gravity.RIGHT;

                            viewHolder.messageTextView.setLayoutParams(params);
                            viewHolder.messageTextView.setBackgroundResource(R.drawable.outgoing_message_bubble);
                            viewHolder.messengerTextView.setLayoutParams(params);
                        }
                    }

                    viewHolder.messengerTextView.setText(chatMessage.getName());
                }
            };

            mFirebaseAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    super.onItemRangeInserted(positionStart, itemCount);
                    int friendlyMessageCount = mFirebaseAdapter.getItemCount();
                    int lastVisiblePosition =
                            mLinearLayoutManager.findLastCompletelyVisibleItemPosition();
                    // If the recycler view is initially being loaded or the
                    // user is at the bottom of the list, scroll to the bottom
                    // of the list to show the newly added message.
                    if (lastVisiblePosition == -1 ||
                            (positionStart >= (friendlyMessageCount - 1) &&
                                    lastVisiblePosition == (positionStart - 1))) {
                        mMessageRecyclerView.scrollToPosition(positionStart);
                    }
                }
            });

            mMessageRecyclerView.setAdapter(mFirebaseAdapter);

            mMessageEditText = findViewById(R.id.messageEditText);
            mMessageEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    if (charSequence.toString().trim().length() > 0) {
                        mSendButton.setEnabled(true);
                    } else {
                        mSendButton.setEnabled(false);
                    }
                }

                @Override
                public void afterTextChanged(Editable editable) {
                }
            });

            mSendButton = findViewById(R.id.sendButton);
            mSendButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mUsername = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
                    ChatMessage chatMessage = new ChatMessage(
                            mMessageEditText.getText().toString(),
                            mUsername);
                    mFirebaseDatabaseReference.child(MESSAGES_CHILD)
                            .push().setValue(chatMessage);
                    writeFileOnInternalStorage(MainActivity.this, "messages.txt", mMessageEditText.getText().toString());
                    mMessageEditText.setText("");
                }
            });

            mMessageRecyclerView.setOnTouchListener(onTouchListener);
            mMessageEditText.setOnTouchListener(onTouchListener);
            mSendButton.setOnTouchListener(onTouchListener);
            mDetector = new GestureDetectorCompat(this, this);
            mDetector.setOnDoubleTapListener(this);
        } else {
           Intent intent = new Intent(MainActivity.this, StartScreen.class);
           startActivity(intent);
           Toast.makeText(
                   MainActivity.this,
                   "Recheck your ID and make sure that the eyetracking has started",
                   Toast.LENGTH_LONG
           ).show();
        }
    }

    View.OnTouchListener onTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int historySize = event.getHistorySize();
            int pointerCount = event.getPointerCount();

            //Action Move events are batched together -> loop through historical data since last event trigger
            for (int h = 0; h < historySize; h++) {
                for (int p = 0; p < pointerCount; p++) {
                    try {
                        Integer pointerId = event.getPointerId(p);
                        Long eventTime = event.getHistoricalEventTime(h);
                        String action = MotionEvent.actionToString(event.getAction());
                        Float relativeX = event.getHistoricalY(p, h);
                        Float relativeY = event.getHistoricalX(p, h);
                        Float rawX = null;
                        Float rawY = null;
                        Float xPrecision = null;
                        Float yPrecision = null;
                        Long downTime = null;
                        Float orientation = event.getHistoricalOrientation(p,h);
                        Float pressure = event.getHistoricalPressure(p,h);
                        Float size = event.getHistoricalSize(p,h);
                        Integer edgeFlags = null;
                        Integer actionButton = null;
                        Integer metaState = null;
                        Integer toolType = null;
                        Float toolMajor = event.getHistoricalToolMajor(p, h);
                        Float toolMinor = event.getHistoricalToolMinor(p, h);

                        String log = String.format(Locale.GERMAN, "%d; %o; %s; %f; %f; %f; %f; %f; %f; %o; %f; %f; %f; %d; %d; %d; %d; %f; %f;",
                                pointerId,
                                eventTime,
                                action,
                                relativeX,
                                relativeY,
                                rawX,
                                rawY,
                                xPrecision,
                                yPrecision,
                                downTime,
                                orientation,
                                pressure,
                                size,
                                edgeFlags,
                                actionButton,
                                metaState,
                                toolType,
                                toolMajor,
                                toolMinor
                        );

                        writeFileOnInternalStorage(MainActivity.this, "motionEvents.txt", log);
                        writeFileOnInternalStorage(MainActivity.this, "rawHistoricalEvent.txt", event.toString());
                    } catch (Exception e) {
                        Log.e("Historical", "onTouch", e );
                    }
                }
            }

            //most current event data
            for (int p = 0; p < pointerCount; p++) {
                try {
                    Integer pointerId = event.getPointerId(p);
                    Long eventTime = event.getEventTime();
                    String action = MotionEvent.actionToString(event.getAction());
                    Float relativeX = event.getX(p);
                    Float relativeY = event.getY(p);
                    Float rawX = event.getRawX();
                    Float rawY = event.getRawY();
                    Float xPrecision = event.getXPrecision();
                    Float yPrecision = event.getYPrecision();
                    Long downTime = event.getDownTime();
                    Float orientation = event.getOrientation(p);
                    Float pressure = event.getPressure(p);
                    Float size = event.getSize(p);
                    Integer edgeFlags = event.getEdgeFlags();
                    Integer actionButton = event.getActionButton();
                    Integer metaState = event.getMetaState();
                    Integer toolType = event.getToolType(p);
                    Float toolMajor = event.getToolMajor(p);
                    Float toolMinor = event.getToolMinor(p);

                    String log = String.format(Locale.GERMAN, "%d; %o; %s; %f; %f; %f; %f; %f; %f; %o; %f; %f; %f; %d; %d; %d; %d; %f; %f;",
                            pointerId,
                            eventTime,
                            action,
                            relativeX,
                            relativeY,
                            rawX,
                            rawY,
                            xPrecision,
                            yPrecision,
                            downTime,
                            orientation,
                            pressure,
                            size,
                            edgeFlags,
                            actionButton,
                            metaState,
                            toolType,
                            toolMajor,
                            toolMinor
                    );

                    writeFileOnInternalStorage(MainActivity.this, "motionEvents.txt", log);
                    writeFileOnInternalStorage(MainActivity.this, "rawEvent.txt", event.toString());
                } catch (Exception e) {
                    Log.e("Historical", "onTouch", e );
                }
            }

            if (mDetector.onTouchEvent(event)) {
                return true;
            }
            return false;
        }
    };

    @Override
    public void onPause() {
        if (hasEyetrackingStarted()) {
            mFirebaseAdapter.stopListening();
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        if (hasEyetrackingStarted()) {
            mFirebaseAdapter.startListening();
        }
        super.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                AuthUI.getInstance().signOut(this)
                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                Toast.makeText(MainActivity.this,
                                        "You have been signed out.",
                                        Toast.LENGTH_LONG)
                                        .show();

                                // Close activity
                                finish();
                            }
                        });
            case R.id.task_done_menu:
                Intent intent = new Intent(MainActivity.this, StartScreen.class);
                startActivity(intent);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // An unresolvable error has occurred and Google APIs (including Sign-In) will not
        // be available.
        Log.d(TAG, "onConnectionFailed:" + connectionResult);
        Toast.makeText(this, "Google Play Services error.", Toast.LENGTH_SHORT).show();
    }

    private Indexable getMessageIndexable(ChatMessage chatMessage) {
        PersonBuilder sender = Indexables.personBuilder()
                .setIsSelf(mUsername.equals(chatMessage.getName()))
                .setName(chatMessage.getName())
                .setUrl(MESSAGE_URL.concat(chatMessage.getId() + "/sender"));

        PersonBuilder recipient = Indexables.personBuilder()
                .setName(mUsername)
                .setUrl(MESSAGE_URL.concat(chatMessage.getId() + "/recipient"));

        Indexable messageToIndex = Indexables.messageBuilder()
                .setName(chatMessage.getText())
                .setUrl(MESSAGE_URL.concat(chatMessage.getId()))
                .setSender(sender)
                .setRecipient(recipient)
                .build();

        return messageToIndex;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent event) {
        writeFileOnInternalStorage(MainActivity.this, "gesture.txt", "onSingleTapConfirmed: " + event.toString());
        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent event) {
        writeFileOnInternalStorage(MainActivity.this, "gesture.txt", "onDoubleTap: " + event.toString());
        return false;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent event) {
        writeFileOnInternalStorage(MainActivity.this, "gesture.txt", "onDoubleTapEvent: " + event.toString());
        return false;
    }

    @Override
    public boolean onDown(MotionEvent event) {
        writeFileOnInternalStorage(MainActivity.this, "gesture.txt", "onDown: " + event.toString());
        return false;
    }

    @Override
    public void onShowPress(MotionEvent event) {
        writeFileOnInternalStorage(MainActivity.this, "gesture.txt", "onShowPress: " + event.toString());

    }

    @Override
    public boolean onSingleTapUp(MotionEvent event) {
        writeFileOnInternalStorage(MainActivity.this, "gesture.txt", "onSingleTapUp: " + event.toString());
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent event1, MotionEvent event2, float distanceX, float distanceY) {
        writeFileOnInternalStorage(MainActivity.this, "gesture.txt", "onScroll: " + event1.toString() + ", " + event2.toString() + ", " + distanceX + ", " + distanceY);
        return false;
    }

    @Override
    public void onLongPress(MotionEvent event) {
        writeFileOnInternalStorage(MainActivity.this, "gesture.txt", "onLongPress: " + event.toString());
    }

    @Override
    public boolean onFling(MotionEvent event1, MotionEvent event2, float velocityX, float velocityY) {
        writeFileOnInternalStorage(MainActivity.this, "gesture.txt", "onFling: " + event1.toString() + ", " + event2.toString() + ", " + velocityX + ", " + velocityY);
        return false;
    }


    public boolean hasEyetrackingStarted() {
        File file = new File(MainActivity.this.getFilesDir(), participant + "_" + startTime);
        if (participant.equals("test")) {
            return true;
        }

        if(!file.exists()){
            return false;
        } else {
            return true;
        }
    }

    public void writeFileOnInternalStorage(Context mcoContext, String sFileName, String sBody){
        SimpleDateFormat timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        File file = new File(mcoContext.getFilesDir(), participant + "_" + startTime);

        try{
            File outFile = new File(file, sFileName);
            FileWriter writer = new FileWriter(outFile, true);
            writer.append(timestamp.format(new Date()));
            writer.append("; ");
            writer.append(sBody);
            writer.append("\n");
            writer.flush();
            writer.close();

        }catch (Exception e){
            e.printStackTrace();

        }
    };
}
