/*
 * Copyright 2017 Thomas Andres
 *
 * This file is part of Kandroid.
 *
 * Kandroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Kandroid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
 */

package in.andres.kandroid.ui;

import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;

import com.thebluealliance.spectrum.SpectrumDialog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;

import in.andres.kandroid.BuildConfig;
import in.andres.kandroid.Constants;
import in.andres.kandroid.R;
import in.andres.kandroid.Utils;
import in.andres.kandroid.kanboard.KanboardAPI;
import in.andres.kandroid.kanboard.KanboardColor;
import in.andres.kandroid.kanboard.KanboardTask;
import in.andres.kandroid.kanboard.events.OnCreateTaskListener;
import in.andres.kandroid.kanboard.events.OnGetDefaultColorListener;
import in.andres.kandroid.kanboard.events.OnGetDefaultColorsListener;
import in.andres.kandroid.kanboard.events.OnGetVersionListener;
import in.andres.kandroid.kanboard.events.OnUpdateTaskListener;

public class TaskEditActivity extends AppCompatActivity implements OnCreateTaskListener, OnUpdateTaskListener, OnGetDefaultColorListener, OnGetDefaultColorsListener, OnGetVersionListener {
    private KanboardTask task;
    private String taskTitle;
    private String taskDescription;
    private Date startDate;
    private Date dueDate;
    private double timeEstimated;
    private double timeSpent;
    private boolean isNewTask = false;
    private int swimlaneId;
    private int columnId;
    private int ownerId;
    private int creatorId;
    private String colorId;
    private int projectid;
    private Hashtable<Integer, String> projectUsers;
    private Dictionary<String, KanboardColor> kanboardColors;
    private int[] colorArray;
    private String defaultColor;

    private EditText editTextTitle;
    private EditText editTextDescription;
    private Button btnStartDate;
    private Button btnDueDate;
    private Button btnColor;
    private EditText editHoursEstimated;
    private EditText editHoursSpent;
    private Spinner spinnerProjectUsers;

    private KanboardAPI kanboardAPI;

    private View.OnClickListener btnColorClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            new SpectrumDialog.Builder(getBaseContext())
                    .setColors(colorArray)
                    .setSelectedColor(kanboardColors.get(colorId != null ? colorId : defaultColor).getBackground())
                    .setDismissOnColorSelected(true)
                    .setTitle(R.string.taskedit_seclect_color)
                    .setOnColorSelectedListener(new SpectrumDialog.OnColorSelectedListener() {
                        @Override
                        public void onColorSelected(boolean positiveResult, @ColorInt int color) {
                            if (positiveResult) {
                                Enumeration<String> iter = kanboardColors.keys();
                                while (iter.hasMoreElements()) {
                                    String key = iter.nextElement();
                                    if (kanboardColors.get(key).getBackground() == color) {
                                        colorId = key;
                                        break;
                                    }
                                }
                                setButtonColor();
                            }
                        }
                    }).build().show(getSupportFragmentManager(), "color_dialog");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_edit);
        setupActionBar();

        editTextTitle = (EditText) findViewById(R.id.edit_task_title);
        editTextDescription = (EditText) findViewById(R.id.edit_task_description);
        btnStartDate = (Button) findViewById(R.id.btn_start_date);
        btnDueDate = (Button) findViewById(R.id.btn_due_date);
        btnColor = (Button) findViewById(R.id.color_button);
        btnColor.setOnClickListener(btnColorClick);
        btnColor.setText(Utils.fromHtml(getString(R.string.taskedit_color, "")));
        editHoursEstimated = (EditText) findViewById(R.id.edit_hours_estimated);
        editHoursSpent = (EditText) findViewById(R.id.edit_hours_spent);
        spinnerProjectUsers = (Spinner) findViewById(R.id.user_spinner);

        if (getIntent().hasExtra("task")) {
            isNewTask = false;
            task = (KanboardTask) getIntent().getSerializableExtra("task");
            taskTitle = task.getTitle();
            taskDescription = task.getDescription();
            startDate = task.getDateStarted();
            dueDate = task.getDateDue();
            timeEstimated = task.getTimeEstimated();
            timeSpent = task.getTimeSpent();
            ownerId = task.getOwnerId();
            colorId = task.getColorId();
            setActionBarTitle(getString(R.string.taskview_fab_edit_task));
        } else {
            isNewTask = true;
            projectid = getIntent().getIntExtra("projectid", 0);
//            colorId = getIntent().getIntExtra("colorid", 0);
            creatorId = getIntent().getIntExtra("creatorid", 0);
            ownerId = getIntent().getIntExtra("ownerid", 0);
            columnId = getIntent().getIntExtra("columnid",0);
            swimlaneId = getIntent().getIntExtra("swimlaneid", 0);
            setActionBarTitle(getString(R.string.taskedit_new_task));
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this.getBaseContext());
        try {
            kanboardAPI = new KanboardAPI(preferences.getString("serverurl", ""), preferences.getString("username", ""), preferences.getString("password", ""));
            kanboardAPI.addOnCreateTaskListener(this);
            kanboardAPI.addOnUpdateTaskListener(this);
            kanboardAPI.addOnGetDefaultColorListener(this);
            kanboardAPI.addOnGetDefaultColorsListener(this);
            kanboardAPI.addOnGetVersionListener(this);
            kanboardAPI.getDefaultTaskColor();
            kanboardAPI.getDefaultTaskColors();
            kanboardAPI.getVersion();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (getIntent().hasExtra("projectusers")) {
            if (getIntent().getSerializableExtra("projectusers") instanceof HashMap) {
                projectUsers = new Hashtable<>((HashMap<Integer, String>) getIntent().getSerializableExtra("projectusers"));
                ArrayList<String> possibleOwners = Collections.list(projectUsers.elements());
                possibleOwners.add(0, "");
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, possibleOwners);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerProjectUsers.setAdapter(adapter);
                if (ownerId != 0) {
                    spinnerProjectUsers.setSelection(possibleOwners.indexOf(projectUsers.get(ownerId)));
                }
            }
        }

        editTextTitle.setText(taskTitle);
        editTextDescription.setText(taskDescription);
        editHoursEstimated.setText(Double.toString(timeEstimated));
        editHoursSpent.setText(Double.toString(timeSpent));
        btnStartDate.setText(Utils.fromHtml(getString(R.string.taskview_date_start, startDate)));
        btnDueDate.setText(Utils.fromHtml(getString(R.string.taskview_date_due, dueDate)));

        btnStartDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Calendar calendar = Calendar.getInstance();
                if (startDate != null)
                    calendar.setTime(startDate);

                DatePickerDialog dlgDate = new DatePickerDialog(TaskEditActivity.this, new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                        Calendar calendar = Calendar.getInstance();
                        calendar.set(Calendar.YEAR, year);
                        calendar.set(Calendar.MONTH, month);
                        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        startDate = calendar.getTime();
                        btnStartDate.setText(Utils.fromHtml(getString(R.string.taskview_date_start, startDate)));
                    }
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
                dlgDate.setButton(DatePickerDialog.BUTTON_NEUTRAL, getString(R.string.taskedit_clear_date), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startDate = null;
                        btnStartDate.setText(Utils.fromHtml(getString(R.string.taskview_date_start, startDate)));
                    }
                });
                dlgDate.show();
            }
        });
        btnDueDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Calendar calendar = Calendar.getInstance();
                if (dueDate != null)
                    calendar.setTime(dueDate);

                DatePickerDialog dlgDate = new DatePickerDialog(TaskEditActivity.this, new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                        Calendar calendar = Calendar.getInstance();
                        calendar.set(Calendar.YEAR, year);
                        calendar.set(Calendar.MONTH, month);
                        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        dueDate = calendar.getTime();
                        btnDueDate.setText(Utils.fromHtml(getString(R.string.taskview_date_due, dueDate)));
                    }
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
                dlgDate.setButton(DatePickerDialog.BUTTON_NEUTRAL, getString(R.string.taskedit_clear_date), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dueDate = null;
                        btnDueDate.setText(Utils.fromHtml(getString(R.string.taskview_date_due, dueDate)));
                    }
                });
                dlgDate.show();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.action_save:
                ownerId = spinnerProjectUsers.getSelectedItemPosition();
                if (spinnerProjectUsers.getSelectedItemPosition() != 0) {
                    for (Enumeration<Integer> iter = projectUsers.keys(); iter.hasMoreElements();) {
                        Integer key = iter.nextElement();
                        if (projectUsers.get(key).contentEquals((String) spinnerProjectUsers.getSelectedItem())) {
                            ownerId = key;
                            break;
                        }
                    }
                }
                if (isNewTask) {
                    kanboardAPI.createTask(editTextTitle.getText().toString(), projectid, colorId != null ? colorId : defaultColor, columnId, ownerId, null, dueDate, editTextDescription.getText().toString(), null, null, swimlaneId, null, null, null, null, null, null, null, startDate);

                } else {
                    kanboardAPI.updateTask(task.getId(), editTextTitle.getText().toString(), colorId != null ? colorId : defaultColor, ownerId, dueDate, editTextDescription.getText().toString(), null, null, null, null, null, null, null, null, null, startDate);
                }
                ProgressBar prog = new ProgressBar(TaskEditActivity.this);
                prog.getIndeterminateDrawable().setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.MULTIPLY);
                item.setActionView(prog);
                item.expandActionView();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_edittask_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(getString(R.string.taskview_fab_edit_task));
        }
    }

    private void setActionBarTitle(@NonNull String title) {
        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle(title);
    }

    @Override
    public void onCreateTask(boolean success, Integer taskid) {
        setResult(Constants.ResultChanged, new Intent());
        finish();
    }

    @Override
    public void onUpdateTask(boolean success) {
        setResult(Constants.ResultChanged, new Intent());
        finish();
    }

    @Override
    public void onGetDefaultColor(boolean success, String colorid) {
        defaultColor = colorid;
        setButtonColor();
    }

    @Override
    public void onGetDefaultColors(boolean success, Dictionary<String, KanboardColor> colors) {
        kanboardColors = colors;
        colorArray = new int[kanboardColors.size()];
        int i = 0;
        Enumeration<String> iter = kanboardColors.keys();
        while (iter.hasMoreElements()) {
            String key = iter.nextElement();
            colorArray[i] = kanboardColors.get(key).getBackground();
            i++;
        }
        Log.d(Constants.TAG, colors.toString());
        setButtonColor();
    }

    @Override
    public void onGetVersion(boolean success, int[] version, String tag) {
        if (success) {
            if (version[0] >= 1 &&
                    version[1] >= 0 &&
                    version[2] >= 40) {
                if (BuildConfig.DEBUG)
                    btnStartDate.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setButtonColor() {
        if (kanboardColors == null || defaultColor == null)
            return;

        btnColor.setEnabled(true);

        Drawable dot = ResourcesCompat.getDrawable(getResources(), R.drawable.shape_circle, null);
        if (colorId != null) {
            dot.setColorFilter(kanboardColors.get(colorId).getBackground(), PorterDuff.Mode.MULTIPLY);
            btnColor.setText(Utils.fromHtml(getString(R.string.taskedit_color, kanboardColors.get(colorId).getName())));
        } else {
            dot.setColorFilter(kanboardColors.get(defaultColor).getBackground(), PorterDuff.Mode.MULTIPLY);
            btnColor.setText(Utils.fromHtml(getString(R.string.taskedit_color, kanboardColors.get(defaultColor).getName())));
        }
        btnColor.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null);
    }
}
