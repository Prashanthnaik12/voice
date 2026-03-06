package com.voiceassistant.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.voiceassistant.R;
import com.voiceassistant.utils.CommandProcessor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class ContactsActivity extends AppCompatActivity {

    private CommandProcessor commandProcessor;
    private RecyclerView rvGreetings, rvContacts;
    private GreetingsAdapter greetingsAdapter;
    private ContactsAdapter contactsAdapter;
    private List<Map.Entry<String, String>> greetingsList = new ArrayList<>();
    private List<String[]> contactsList = new ArrayList<>(); // [name, number, nicknames]

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);

        commandProcessor = new CommandProcessor(this);

        rvGreetings = findViewById(R.id.rvGreetings);
        rvContacts = findViewById(R.id.rvContacts);

        loadGreetings();
        loadContacts();

        greetingsAdapter = new GreetingsAdapter(greetingsList);
        rvGreetings.setLayoutManager(new LinearLayoutManager(this));
        rvGreetings.setAdapter(greetingsAdapter);

        contactsAdapter = new ContactsAdapter(contactsList);
        rvContacts.setLayoutManager(new LinearLayoutManager(this));
        rvContacts.setAdapter(contactsAdapter);

        findViewById(R.id.btnAddGreeting).setOnClickListener(v -> showAddGreetingDialog());
        findViewById(R.id.btnAddContact).setOnClickListener(v -> showAddContactDialog());
    }

    private void loadGreetings() {
        greetingsList.clear();
        greetingsList.addAll(commandProcessor.getCustomGreetings().entrySet());
    }

    private void loadContacts() {
        contactsList.clear();
        try {
            String json = getSharedPreferences("voice_assistant", MODE_PRIVATE)
                    .getString("custom_contacts", "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                contactsList.add(new String[]{
                    obj.getString("name"),
                    obj.getString("number"),
                    obj.optString("nicknames", "")
                });
            }
        } catch (Exception e) { /* Ignore */ }
    }

    private void showAddGreetingDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_greeting, null);
        EditText etTrigger = dialogView.findViewById(R.id.etTrigger);
        EditText etResponse = dialogView.findViewById(R.id.etResponse);

        new AlertDialog.Builder(this)
            .setTitle(R.string.add_greeting)
            .setView(dialogView)
            .setPositiveButton(R.string.save, (d, w) -> {
                String trigger = etTrigger.getText().toString().trim();
                String response = etResponse.getText().toString().trim();
                if (!trigger.isEmpty() && !response.isEmpty()) {
                    commandProcessor.addCustomGreeting(trigger, response);
                    loadGreetings();
                    greetingsAdapter.notifyDataSetChanged();
                    Toast.makeText(this, R.string.greeting_saved, Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void showAddContactDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null);
        EditText etName = dialogView.findViewById(R.id.etName);
        EditText etNumber = dialogView.findViewById(R.id.etNumber);
        EditText etNicknames = dialogView.findViewById(R.id.etNicknames);

        new AlertDialog.Builder(this)
            .setTitle(R.string.add_contact)
            .setView(dialogView)
            .setPositiveButton(R.string.save, (d, w) -> {
                String name = etName.getText().toString().trim();
                String number = etNumber.getText().toString().trim();
                String nicknames = etNicknames.getText().toString().trim();
                if (!name.isEmpty() && !number.isEmpty()) {
                    saveCustomContact(name, number, nicknames);
                    loadContacts();
                    contactsAdapter.notifyDataSetChanged();
                    Toast.makeText(this, R.string.contact_saved, Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void saveCustomContact(String name, String number, String nicknames) {
        try {
            String json = getSharedPreferences("voice_assistant", MODE_PRIVATE)
                    .getString("custom_contacts", "[]");
            JSONArray arr = new JSONArray(json);
            JSONObject obj = new JSONObject();
            obj.put("name", name.toLowerCase());
            obj.put("number", number);
            obj.put("nicknames", nicknames.toLowerCase());
            arr.put(obj);
            getSharedPreferences("voice_assistant", MODE_PRIVATE)
                    .edit().putString("custom_contacts", arr.toString()).apply();
        } catch (Exception e) { /* Ignore */ }
    }

    // Adapters
    class GreetingsAdapter extends RecyclerView.Adapter<GreetingsAdapter.VH> {
        List<Map.Entry<String, String>> data;
        GreetingsAdapter(List<Map.Entry<String, String>> data) { this.data = data; }
        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_greeting, p, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            Map.Entry<String, String> entry = data.get(pos);
            h.tvTrigger.setText("\"" + entry.getKey() + "\"");
            h.tvResponse.setText("→ " + entry.getValue());
        }
        @Override public int getItemCount() { return data.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView tvTrigger, tvResponse;
            VH(View v) { super(v); tvTrigger = v.findViewById(R.id.tvTrigger); tvResponse = v.findViewById(R.id.tvResponse); }
        }
    }

    class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.VH> {
        List<String[]> data;
        ContactsAdapter(List<String[]> data) { this.data = data; }
        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_contact, p, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            String[] c = data.get(pos);
            h.tvName.setText(c[0]);
            h.tvNumber.setText(c[1]);
            if (c[2] != null && !c[2].isEmpty()) h.tvNicknames.setText("aka: " + c[2]);
        }
        @Override public int getItemCount() { return data.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvNumber, tvNicknames;
            VH(View v) { super(v); tvName = v.findViewById(R.id.tvName); tvNumber = v.findViewById(R.id.tvNumber); tvNicknames = v.findViewById(R.id.tvNicknames); }
        }
    }
}
