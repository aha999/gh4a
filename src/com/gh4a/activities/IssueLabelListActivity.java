/*
 * Copyright 2011 Azwan Adli Abdullah
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gh4a.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.view.ActionMode;
import android.text.Editable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ListView;

import com.gh4a.Constants;
import com.gh4a.Gh4Application;
import com.gh4a.LoadingFragmentActivity;
import com.gh4a.ProgressDialogTask;
import com.gh4a.R;
import com.gh4a.adapter.IssueLabelAdapter;
import com.gh4a.loader.LabelListLoader;
import com.gh4a.loader.LoaderCallbacks;
import com.gh4a.loader.LoaderResult;
import com.gh4a.utils.IntentUtils;
import com.gh4a.utils.ToastUtils;
import com.gh4a.utils.UiUtils;
import com.shamanland.fab.FloatingActionButton;
import com.shamanland.fab.ShowHideOnScroll;

import org.eclipse.egit.github.core.Label;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.service.LabelService;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;

public class IssueLabelListActivity extends LoadingFragmentActivity implements
        OnItemClickListener, View.OnClickListener {
    private String mRepoOwner;
    private String mRepoName;
    private EditActionMode mActionMode;
    private Label mAddedLabel;
    private boolean mShouldStartAdding;

    private FloatingActionButton mFab;
    private ShowHideOnScroll mFabListener;
    private ListView mListView;

    private LoaderCallbacks<List<Label>> mLabelCallback = new LoaderCallbacks<List<Label>>() {
        @Override
        public Loader<LoaderResult<List<Label>>> onCreateLoader(int id, Bundle args) {
            return new LabelListLoader(IssueLabelListActivity.this, mRepoOwner, mRepoName);
        }
        @Override
        public void onResultReady(LoaderResult<List<Label>> result) {
            boolean success = !result.handleError(IssueLabelListActivity.this);
            UiUtils.hideImeForView(getCurrentFocus());
            if (success) {
                mAdapter.clear();
                mAdapter.addAll(result.getData());
                mAdapter.notifyDataSetChanged();
            }
            setContentEmpty(!success);
            setContentShown(true);
        }
    };

    private IssueLabelAdapter mAdapter = new IssueLabelAdapter(this) {
        @Override
        protected void bindView(View view, Label label) {
            ViewHolder holder = (ViewHolder) view.getTag();

            super.bindView(view, label);

            if (label == mAddedLabel && mShouldStartAdding) {
                holder.editor.setHint(R.string.issue_label_new);
                mActionMode = new EditActionMode(label, holder.editor);
                startSupportActionMode(mActionMode);
                updateFabVisibility();
                mShouldStartAdding = false;
            } else {
                holder.editor.setHint(null);
            }

            Label editingLabel = mActionMode != null ? mActionMode.mLabel : null;
            if (label.equals(editingLabel)) {
                setExpanded(view, true);
                mActionMode.setEditor(holder.editor);
            } else {
                setExpanded(view, false);
            }
        }
    };

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRepoOwner = getIntent().getExtras().getString(Constants.Repository.OWNER);
        mRepoName = getIntent().getExtras().getString(Constants.Repository.NAME);

        if (hasErrorView()) {
            return;
        }

        setContentView(R.layout.issue_label_list);
        setContentShown(false);

        mListView = (ListView) findViewById(R.id.main_content);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(this);

        mFab = (FloatingActionButton) findViewById(R.id.fab_add);
        mFab.setOnClickListener(this);
        mFabListener = new ShowHideOnScroll(mFab);
        updateFabVisibility();

        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(R.string.issue_manage_labels);
        actionBar.setSubtitle(mRepoOwner + "/" + mRepoName);
        actionBar.setDisplayHomeAsUpEnabled(true);

        getSupportLoaderManager().initLoader(0, null, mLabelCallback);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (mActionMode == null) {
            mActionMode = new EditActionMode(mAdapter.getItem(position),
                    (EditText) view.findViewById(R.id.et_label));
            mAdapter.setExpanded(view, true);
            startSupportActionMode(mActionMode);
            updateFabVisibility();
        }
    }

    @Override
    protected Intent navigateUp() {
        return IntentUtils.getIssueListActivityIntent(this,
                mRepoOwner, mRepoName, Constants.Issue.STATE_OPEN);
    }

    @Override
    public void onClick(View view) {
        if (mActionMode == null) {
            mAddedLabel = new Label();
            mAddedLabel.setColor("dddddd");
            mAdapter.add(mAddedLabel);
            mShouldStartAdding = true;
            mAdapter.notifyDataSetChanged();
        }
    }

    private void updateFabVisibility() {
        if (Gh4Application.get(this).isAuthorized() && mActionMode == null) {
            mListView.setOnTouchListener(mFabListener);
            mFab.setVisibility(View.VISIBLE);
        } else {
            mListView.setOnTouchListener(null);
            mFab.setVisibility(View.GONE);
        }

    }
    private final class EditActionMode implements ActionMode.Callback {
        private String mCurrentLabelName;
        private EditText mEditor;
        private Label mLabel;

        public EditActionMode(Label label, EditText editor) {
            mCurrentLabelName = label.getName();
            mEditor = editor;
            mLabel = label;

            mEditor.setText(mCurrentLabelName);
        }

        public void setEditor(EditText editor) {
            Editable currentText = mEditor.getText();
            mEditor = editor;
            mEditor.setText(currentText);
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuItem saveItem = menu.add(Menu.NONE, Menu.FIRST, Menu.NONE, R.string.save)
                .setIcon(UiUtils.resolveDrawable(IssueLabelListActivity.this, R.attr.saveIcon));
            MenuItemCompat.setShowAsAction(saveItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);

            if (mLabel != mAddedLabel) {
                MenuItem deleteItem = menu.add(Menu.NONE, Menu.FIRST + 1, Menu.NONE, R.string.delete)
                    .setIcon(R.drawable.content_discard);
                MenuItemCompat.setShowAsAction(deleteItem, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
            }

            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
            case Menu.FIRST:
                String labelName = mEditor.getText().toString();
                String color = (String) mEditor.getTag();
                if (mLabel == mAddedLabel) {
                    new AddIssueLabelTask(labelName, color).execute();
                    mLabel = null;
                } else {
                    new EditIssueLabelTask(mCurrentLabelName, labelName, color).execute();
                }
                break;
            case Menu.FIRST + 1:
                new AlertDialog.Builder(IssueLabelListActivity.this)
                        .setTitle(getString(R.string.issue_dialog_delete_title, mCurrentLabelName))
                        .setMessage(R.string.issue_dialog_delete_message)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                new DeleteIssueLabelTask(mCurrentLabelName).execute();
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
                break;
            default:
                break;
            }

            mode.finish();
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode = null;
            if (mLabel == mAddedLabel) {
                mAdapter.remove(mLabel);
                mAddedLabel = null;
            }
            mAdapter.notifyDataSetChanged();
            updateFabVisibility();
        }
    }

    private class DeleteIssueLabelTask extends ProgressDialogTask<Void> {
        private String mLabelName;

        public DeleteIssueLabelTask(String labelName) {
            super(IssueLabelListActivity.this, 0, R.string.deleting_msg);
            mLabelName = labelName;
        }

        @Override
        protected Void run() throws IOException {
            LabelService labelService = (LabelService)
                    Gh4Application.get(mContext).getService(Gh4Application.LABEL_SERVICE);
            labelService.deleteLabel(mRepoOwner, mRepoName, URLEncoder.encode(mLabelName, "UTF-8"));
            return null;
        }

        @Override
        protected void onSuccess(Void result) {
            getSupportLoaderManager().getLoader(0).onContentChanged();
        }
    }

    private class EditIssueLabelTask extends ProgressDialogTask<Void> {
        private String mOldLabelName;
        private String mNewLabelName;
        private String mColor;

        public EditIssueLabelTask(String oldLabelName, String newLabelName, String color) {
            super(IssueLabelListActivity.this, 0, R.string.saving_msg);
            mOldLabelName = oldLabelName;
            mNewLabelName = newLabelName;
            mColor = color;
        }

        @Override
        protected Void run() throws IOException {
            LabelService labelService = (LabelService)
                    Gh4Application.get(mContext).getService(Gh4Application.LABEL_SERVICE);

            Label label = new Label();
            label.setName(mNewLabelName);
            label.setColor(mColor);

            labelService.editLabel(new RepositoryId(mRepoOwner, mRepoName),
                    URLEncoder.encode(mOldLabelName, "UTF-8"), label);
            return null;
        }

        @Override
        protected void onSuccess(Void result) {
            getSupportLoaderManager().getLoader(0).onContentChanged();
        }

        @Override
        protected void onError(Exception e) {
            ToastUtils.showMessage(mContext, R.string.issue_error_edit_label);
        }
    }

    private class AddIssueLabelTask extends ProgressDialogTask<Void> {
        private String mLabelName;
        private String mColor;

        public AddIssueLabelTask(String labelName, String color) {
            super(IssueLabelListActivity.this, 0, R.string.saving_msg);
            mLabelName = labelName;
            mColor = color;
        }

        @Override
        protected Void run() throws IOException {
            LabelService labelService = (LabelService)
                    Gh4Application.get(mContext).getService(Gh4Application.LABEL_SERVICE);

            Label label = new Label();
            label.setName(mLabelName);
            label.setColor(mColor);

            labelService.createLabel(mRepoOwner, mRepoName, label);
            return null;
        }

        @Override
        protected void onSuccess(Void result) {
            getSupportLoaderManager().getLoader(0).onContentChanged();
            mAddedLabel = null;
        }

        @Override
        protected void onError(Exception e) {
            ToastUtils.showMessage(mContext, R.string.issue_error_create_label);
            mAdapter.remove(mAddedLabel);
            mAddedLabel = null;
        }
    }
}