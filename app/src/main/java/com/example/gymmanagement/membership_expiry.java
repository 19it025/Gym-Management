package com.example.gymmanagement;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.firebase.ui.firestore.FirestoreRecyclerAdapter;
import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class membership_expiry extends AppCompatActivity {

    FirebaseFirestore firebaseFirestore;
    FirebaseAuth mAuth;
    FirestoreRecyclerAdapter adapter;

    RecyclerView recyclerView;

    Toolbar toolbar;

    Date expiry_date = null;
    Date due_date = null;
    long days_remaining;

    SmsManager smsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_membership_expiry);

        String permission = Manifest.permission.SEND_SMS;
        int res = getApplicationContext().checkCallingOrSelfPermission(permission);
        if(res == PackageManager.PERMISSION_DENIED)
        {
            ActivityCompat.requestPermissions(membership_expiry.this, new String[]{Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS}, PackageManager.PERMISSION_GRANTED);
        }

        smsManager = SmsManager.getDefault();

        toolbar = findViewById(R.id.expiry_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        firebaseFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        recyclerView = findViewById(R.id.expiry_recycler_view);

        final Calendar calendar = Calendar.getInstance();
        final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy", Locale.US);

        calendar.add(Calendar.DAY_OF_MONTH, 7);

        final String next = sdf.format(calendar.getTime());

        Log.d("TAG","expiry date: " + next);


        Query query = firebaseFirestore.collection(mAuth.getUid()).document("user info").collection("clients");
        FirestoreRecyclerOptions<client_list> options = new FirestoreRecyclerOptions.Builder<client_list>().setQuery(query, client_list.class).build();

        adapter = new FirestoreRecyclerAdapter<client_list, MembershipViewHolder>(options) {
            @NonNull
            @Override
            public MembershipViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.membership_expiry_list,parent,false);
                return new MembershipViewHolder(view);
            }
            @Override
            protected void onBindViewHolder(@NonNull MembershipViewHolder holder, int position, @NonNull final client_list model) {

                String due = model.getDue_date();
                try
                {
                    expiry_date = sdf.parse(next);
                    due_date = sdf.parse(due);

                    Calendar calendar1 = Calendar.getInstance();
                    Date curr_date = calendar1.getTime();
                    days_remaining = diff_in_days(due_date, curr_date);
                }
                catch (ParseException e)
                {
                    e.printStackTrace();
                }
                Log.d("TAG", "client due date"+due+" "+due_date.before(expiry_date)+"");
                if(due_date.before(expiry_date))
                {
                    holder.expiring_c_name.setText(model.getName().toUpperCase());
                    if(days_remaining>0)
                    {
                        if(days_remaining==1)
                        {
                            holder.plan_expiring.setText("Plan expiring tomorrow");
                            final DocumentReference documentReference = FirebaseFirestore.getInstance().collection(mAuth.getUid()).document("user info").collection("clients").document(model.getName());
                            documentReference.addSnapshotListener(new EventListener<DocumentSnapshot>() {
                                @Override
                                public void onEvent(@Nullable DocumentSnapshot value, @Nullable FirebaseFirestoreException error) {
                                    String status = value.getString("expiring_tomorrow");
                                    if(status.equals("false"))
                                    {
                                        smsManager.sendTextMessage(model.getPhone(),null, "Oops! Your membership @GYMSTER is going to expire. Only "+days_remaining+" day left.",null,null);
                                        HashMap<String, Object> hashMap = new HashMap<>();
                                        hashMap.put("expiring_tomorrow", "true");
                                        documentReference.update(hashMap);
                                    }
                                }
                            });
                        }
                        else
                            holder.plan_expiring.setText("Plan expiring in "+(days_remaining+1)+" days");
                    }
                    else if (days_remaining==0)
                    {
                        holder.plan_expiring.setText("Plan expiring today");
                        final DocumentReference documentReference = FirebaseFirestore.getInstance().collection(mAuth.getUid()).document("user info").collection("clients").document(model.getName());
                        documentReference.addSnapshotListener(new EventListener<DocumentSnapshot>() {
                            @Override
                            public void onEvent(@Nullable DocumentSnapshot value, @Nullable FirebaseFirestoreException error) {
                                String status = value.getString("expiring_today");
                                if(status.equals("false"))
                                {
                                    smsManager.sendTextMessage(model.getPhone(),null, "Oops! Your membership @GYMSTER is expiring today",null,null);
                                    HashMap<String, Object> hashMap = new HashMap<>();
                                    hashMap.put("expiring_today", "true");
                                    documentReference.update(hashMap);
                                }
                            }
                        });
                    }
                    else
                    {
                        holder.plan_expiring.setText("Plan expired");
                        holder.plan_expiring.setTextColor(Color.RED);
                        final DocumentReference documentReference = FirebaseFirestore.getInstance().collection(mAuth.getUid()).document("user info").collection("clients").document(model.getName());
                        documentReference.addSnapshotListener(new EventListener<DocumentSnapshot>() {
                            @Override
                            public void onEvent(@Nullable DocumentSnapshot value, @Nullable FirebaseFirestoreException error) {
                                String status = value.getString("expired");
                                if(status.equals("false"))
                                {
                                    smsManager.sendTextMessage(model.getPhone(),null, "Your membership @GYMSTER has been expired.",null,null);
                                    HashMap<String, Object> hashMap = new HashMap<>();
                                    hashMap.put("expired", "true");
                                    documentReference.update(hashMap);
                                }
                            }
                        });
                    }
                }
                else
                    holder.cardView.setVisibility(View.GONE);
            }
        };

        recyclerView.setLayoutManager(new LinearLayoutManager(membership_expiry.this));
        recyclerView.setAdapter(adapter);
    }

    private class MembershipViewHolder extends RecyclerView.ViewHolder {

        CardView cardView;
        TextView expiring_c_name, plan_expiring;

        public MembershipViewHolder(@NonNull View itemView) {
            super(itemView);

            expiring_c_name = itemView.findViewById(R.id.txt_expiring_c_name);
            plan_expiring = itemView.findViewById(R.id.txt_plan_expiring);
            cardView = itemView.findViewById(R.id.card_view);
        }
    }

    public long diff_in_days(Date d1, Date d2) {

        long difference_In_Time = d1.getTime() - d2.getTime();
        long difference_In_Days = (difference_In_Time/(1000 * 60 * 60 * 24))%365;
        Log.d("ex_dates", difference_In_Days+"");
        return difference_In_Days;
    }

    @Override
    protected void onStart() {
        super.onStart();
        adapter.startListening();
    }

    @Override
    protected void onStop() {
        super.onStop();
        adapter.stopListening();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
        startActivity(new Intent(membership_expiry.this, dashboard.class));
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        finish();
        startActivity(new Intent(membership_expiry.this, dashboard.class));
        return super.onOptionsItemSelected(item);
    }
}