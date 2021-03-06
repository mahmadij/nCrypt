package com.example.kevin.tempappp;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.provider.Telephony;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.util.ArrayList;
import java.util.Collections;


public class MainActivity extends Activity {

    // Our Phone Number!
    private String phoneNumber;

    // Menu Items
    ListView lv;
    TextView temptxtview;
    MenuAdapter adapter;

    // Main Conversation Listing
    ArrayList<Conversation> smsConversationList = new ArrayList<>();
    ArrayList<Integer> deletedThreads = new ArrayList<>();
    // Database Object
    public DatabaseHelper db;

    // Encryption Object
    private Encryption encryption;

    // Globals
    public static nCryptApplication globals;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Setup and generate new encryption keys
        encryption = new Encryption();
        encryption.PrepareKeys();

        // Setup
        setupPhoneNumber();
        setupDatabase();

        temptxtview = new TextView(this);

        adapter = new MenuAdapter(this, smsConversationList, phoneNumber);

        lv = (ListView) findViewById(R.id.msgListView);
        lv.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        lv.setAdapter(adapter);

        //boolean securityOverrideFactor = true;
        //if(!SecurityOverride.isDefaultSecurityEnabled(getApplicationContext()))
        //    securityOverrideFactor = SecurityOverride.setDefaultSecurityEnabled(getApplicationContext(), true);

        //System.out.println("Security Override: " + securityOverrideFactor);

        //deleteConversation("5195207040");

        //For debug, uncomment if you want to reset your db.
        //ResetDeletedThreadsAndMessages();

        LoadConversations();

        startService(new Intent(this, nCryptService.class));

    }
    //end onCreate

    public void ResetDeletedThreadsAndMessages()
    {
        db.resetDeletedMessages();
        db.resetDeletedThreads();
        db.close();
    }

    public void GetDeletedThreads()
    {
        deletedThreads.clear();
        Cursor c;
        c = db.getAllDeletedThreads();
        if(c == null)
            return;

        if (c.moveToFirst())
        {
            do {
                deletedThreads.add(c.getInt(0));
            } while (c.moveToNext());
        }
        c.close();
        db.close();
    }

    public boolean isDeleted(Integer thread_id)
    {
        return deletedThreads.contains(thread_id);
    }

    public void LoadConversations()
    {
        GetDeletedThreads();
        Integer continueCount = 0;

        ContentResolver contentResolver = getContentResolver();
        Cursor smsConversationCursor = contentResolver.query(Telephony.Sms.Conversations.CONTENT_URI,
                null,
                null,
                null,
                Telephony.Sms.Conversations.DEFAULT_SORT_ORDER);
        int indexThreadId = smsConversationCursor.getColumnIndex(Telephony.Sms.Conversations.THREAD_ID);
        int indexSnippet = smsConversationCursor.getColumnIndex(Telephony.Sms.Conversations.SNIPPET);
        int indexMessageCount = smsConversationCursor.getColumnIndex(Telephony.Sms.Conversations.MESSAGE_COUNT);

        if ((indexThreadId*indexSnippet*indexMessageCount) < 0 || !smsConversationCursor.moveToFirst()) return;

        smsConversationList.clear();
        do {
            Integer thread_id = -1;
            thread_id = smsConversationCursor.getInt(indexThreadId);
            if (thread_id < 0 || isDeleted(thread_id)) continue;
            String where = Telephony.Sms.Conversations.THREAD_ID + "=" + thread_id.toString();
            Cursor smsInboxCursor = getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
                    new String[]{Telephony.Sms.Inbox.ADDRESS,
                            Telephony.Sms.Inbox.PERSON,
                            Telephony.Sms.Inbox.DATE,},
                    where,
                    null,
                    Telephony.Sms.Inbox.DEFAULT_SORT_ORDER);

            int indexAddress = smsInboxCursor.getColumnIndex(Telephony.Sms.Inbox.ADDRESS);
            int indexPerson = smsInboxCursor.getColumnIndex(Telephony.Sms.Inbox.PERSON);
            int indexDate = smsInboxCursor.getColumnIndex(Telephony.Sms.Inbox.DATE);

            if ((indexAddress*indexPerson*indexDate) < 0 || !smsInboxCursor.moveToFirst())
            {
                continueCount++;
                continue;
            }

            // Query database for name
            String friends_number = smsInboxCursor.getString(indexAddress);
            String friends_name = GetContactName(friends_number);
            Integer friends_priority = GetContactPriority(friends_number);
            

            smsConversationList.add(new Conversation(friends_name,
                    smsInboxCursor.getString(indexAddress),
                    thread_id,
                    smsConversationCursor.getString(indexSnippet),
                    Resources.FormattedDate(smsInboxCursor.getInt(indexDate)),
                            smsConversationCursor.getInt(indexMessageCount),friends_priority));

            smsInboxCursor.close();

        } while (smsConversationCursor.moveToNext());

        smsConversationCursor.close();
        Collections.sort(smsConversationList, new SortingAlgorithms());
    }
    //end LoadConversations

    @Override
    public void onResume() {
        super.onResume();

        smsConversationList = new ArrayList<>();
        adapter = new MenuAdapter(MainActivity.this , smsConversationList, phoneNumber);
        LoadConversations();
        lv = (ListView) findViewById(R.id.msgListView);
        lv.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        lv.setAdapter(adapter);

    }
    //onResume

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_chat, menu);
        return true;
    }
    //onCreateOptionsMenu

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity( new Intent( MainActivity.this, IconPopup.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }//onOptionsItemSelected


    /**
     * Not currently used, for testing porpoises
     * @param c DB Cursor?
     */
    public void DisplayContact(Cursor c)
    {
        // Public Key stored as BLOB
        Encryption friends_encryption = new Encryption();
        friends_encryption.setPublicKey((Key) DatabaseHelper.Deserialize(c.getBlob(3)));

        /*  Encryption Testing Code
        String raw_message = "Certified";
        String encrypted_message = encryption.Encrypt(raw_message);
        raw_message = encryption.Decrypt(encrypted_message);
        */

        Toast.makeText(this,
                "id: " + c.getString(0) + "\n" +
                        "Phone Number: " + c.getString(1) + "\n" +
                        "Name:   " + c.getString(2) + "\n",
                        //"RSA Key Status: " + raw_message,
                Toast.LENGTH_LONG).show();
    }//end DisplayContact

    private void setupPhoneNumber(){

        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        if(!tm.getLine1Number().isEmpty())
            phoneNumber = (String)tm.getLine1Number();
        else
            phoneNumber = "5195202520";

    }//end setupPhoneNumber

    private void setupDatabase()
    {
        db = new DatabaseHelper(this);

        try {
            //String destPath = "/data/data/" + getPackageName() +
            //        "/databases";
            String destPath = "/storage/emulated/0/";

            File f = new File(destPath);
            if (!f.exists()) {
                f.mkdirs();
                f.createNewFile();

                //---copy the db from the assets folder into
                // the databases folder---

                CopyDB(getBaseContext().getAssets().open("contacts"),
                        new FileOutputStream(destPath + "/MyDB"));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        db.insertContact("5195207040", "Steve!", encryption.getPublicKey());
        db.insertContact("5194945387", "Kevin!", encryption.getPublicKey());
        db.insertContact("5192819776", "Katrina!", encryption.getPublicKey());
        db.insertContact("5197199890", "Nick!", encryption.getPublicKey());
        //db.insertContact("this_is_a_testing_email@katrina_finds_bugs.ca", "Email!", encryption.getPublicKey());

        Cursor c;
        c = db.getAllContacts();
        if(c == null)
            return;

        if (c.moveToFirst())
        {
            do {
                //TODO This is empty?
                //DisplayContact(c);
            } while (c.moveToNext());
        }

        //db.deleteContact("5195207040");
        c.close();
        db.close();
    }
    //End setupDatabase

    public void CopyDB(InputStream inputStream,
                       OutputStream outputStream) throws IOException {
        //---copy 1K bytes at a time---
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, length);
        }
        inputStream.close();
        outputStream.close();
    }
    //End CopyDB

    private String GetContactName(String phoneno) {
        String returnName = "";
        // Open Database and Look for Contacts
        try {
            Cursor c;
            c = db.getContactByPhoneNumber(phoneno);

            // Name found!
            if (c.moveToFirst())
                returnName = c.getString(2);
                // Name not found, Add to Database!
            else
                db.insertContact(phoneno, "", null);

            c.close();
            db.close();
        }
        catch(Exception ex) {
            returnName = phoneno;
        }
        return returnName;
    }
    //End GetContactName

    private Integer GetContactPriority(String phoneno) {
        Integer returnPriority = -1;
        // Open Database and Look for Contacts
        try {
            Cursor c;
            c = db.getContactByPhoneNumber(phoneno);

            // Name found!
            if (c.moveToFirst())
                returnPriority = c.getInt(4);
                // Name not found, Add to Database!
            else
                db.insertContact(phoneno, "", null);

            c.close();
            db.close();
        }
        catch(Exception ex) {
            returnPriority = 5;
        }
        return returnPriority;
    }
    //End GetContactName

    public void ManageContact(View view) {
        String phoneNo = "";
        Intent intent = new Intent(MainActivity.this, ContactsActivity.class);
        intent.putExtra("phoneNo", phoneNo);
        intent.putExtra("MyPhoneno", phoneNumber);
        startActivity(intent);
    }//end ManageContact

    private void deleteConversation(String phoneNumber){

        String numberFilter = "address='"+ phoneNumber + "'";
        String messageid = null;

        Cursor cursor = getContentResolver().query(Uri.parse("content://sms/"),null,
                numberFilter, null, null); // Get all messages with this number

        if (cursor.moveToFirst()) {
            messageid = cursor.getString(0);
        }
        if(messageid != null)
            getContentResolver().delete(Uri.parse("content://sms/" + messageid), null, null);

        cursor.close();
    }

}//end MainActivity
