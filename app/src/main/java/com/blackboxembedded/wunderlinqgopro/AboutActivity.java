/*
WunderLINQ Client Application
Copyright (C) 2020  Keith Conger, Black Box Embedded, LLC

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/
package com.blackboxembedded.wunderlinqgopro;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class AboutActivity extends AppCompatActivity {

    private final static String TAG = "AboutActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about_activity);

        ImageView ivAppLogo = findViewById(R.id.ivLogo);
        ivAppLogo.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                String url = "http://www.wunderlinq.com";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            }

        });
        TextView tvVersion = findViewById(R.id.tvVersion);
        tvVersion.setText(String.format("%s %s", getString(R.string.version_label), BuildConfig.VERSION_NAME));
        TextView tvCompany = findViewById(R.id.tvCompany);
        tvCompany.setMovementMethod(LinkMovementMethod.getInstance());
        Button btDocumentation = findViewById(R.id.btDocumentation);
        btDocumentation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String url = "https://blackboxembedded.github.io/WunderLINQ-Documentation/en/index-gopro-android.html";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            }
        });
        Button btSendLogs = findViewById(R.id.btSendLogs);
        btSendLogs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Get current date
                Calendar cal = Calendar.getInstance();
                Date date = cal.getTime();
                SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd-HH:mm");
                String curdatetime = formatter.format(date);
                //Send file(s) using email
                Intent emailIntent = new Intent(Intent.ACTION_SEND);
                emailIntent.setType("text/plain");
                String[] to;
                to = new String[]{getString(R.string.sendlogs_email)};
                emailIntent.putExtra(Intent.EXTRA_EMAIL, to);
                //Convert from paths to Android friendly Parcelable Uri's
                File outputFile = new File(getApplicationContext().getExternalFilesDir(null), "wunderlinq-gopro.log");
                if(outputFile.exists()) {
                    emailIntent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(AboutActivity.this, "com.blackboxembedded.wunderlinqgopro.fileprovider", outputFile));
                }
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.sendlogs_subject) + " " + curdatetime);
                emailIntent.putExtra(Intent.EXTRA_TEXT, "App Version: " + BuildConfig.VERSION_NAME + "\n"
                        + "Android Version: " + Build.VERSION.RELEASE + "\n"
                        + "Manufacturer, Model: " + Build.MANUFACTURER + ", " + Build.MODEL + "\n"
                        + getString(R.string.sendlogs_body));
                emailIntent.setType("message/rfc822");
                emailIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(emailIntent, getString(R.string.sendlogs_intent_title)));
                SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(AboutActivity.this);
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putBoolean("prefDebugLogging", false);
                editor.apply();
            }

        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void recreate() {
        super.recreate();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                super.onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}