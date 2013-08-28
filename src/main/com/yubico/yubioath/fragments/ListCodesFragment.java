package com.yubico.yubioath.fragments;

import android.app.*;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.*;
import com.yubico.yubioath.MainActivity;
import com.yubico.yubioath.R;
import com.yubico.yubioath.model.KeyManager;
import com.yubico.yubioath.model.YubiKeyNeo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 8/26/13
 * Time: 11:08 AM
 * To change this template use File | Settings | File Templates.
 */
public class ListCodesFragment extends ListFragment implements MainActivity.OnYubiKeyNeoListener, ActionMode.Callback {
    private final TimeoutAnimation timeoutAnimation = new TimeoutAnimation();
    private CodeAdapter adapter;
    private ProgressBar timeoutBar;
    private ActionMode actionMode;
    private Map<String, String> selectedItem;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.list_codes_fragment, container, false);
        timeoutBar = (ProgressBar) view.findViewById(R.id.timeRemaining);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        adapter = new CodeAdapter(new ArrayList<Map<String, String>>());
        setListAdapter(adapter);

        getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                selectedItem = adapter.getItem(position);
                if (actionMode == null) {
                    actionMode = getActivity().startActionMode(ListCodesFragment.this);
                }
                actionMode.setTitle(selectedItem.get("label"));
                adapter.notifyDataSetChanged();
                view.setSelected(true);
                return true;
            }
        });
    }

    @Override
    public void onYubiKeyNeo(YubiKeyNeo neo) throws IOException {
        long timestamp = System.currentTimeMillis() / 1000 / 30;
        showCodes(neo.getCodes(timestamp));
    }

    public void showCodes(List<Map<String, String>> codes) {
        adapter.setAll(codes);

        if (codes.size() == 0) {
            Toast.makeText(getActivity(), R.string.empty_list, Toast.LENGTH_LONG).show();
        } else {
            timeoutBar.startAnimation(timeoutAnimation);
        }
    }

    @Override
    public void onPasswordMissing(KeyManager keyManager, byte[] id, boolean missing) {
        DialogFragment dialog = RequirePasswordDialog.newInstance(keyManager, id, missing);
        dialog.show(getFragmentManager(), "dialog");
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.copy_to_clipboard:
                ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText(selectedItem.get("label"), selectedItem.get("code"));
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getActivity(), R.string.copied, Toast.LENGTH_SHORT).show();
                break;
            default:
                return false;
        }
        actionMode.finish();
        return true;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.code_select_actions, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        actionMode = null;
        selectedItem = null;
        adapter.notifyDataSetChanged();
    }

    private class CodeAdapter extends ArrayAdapter<Map<String, String>> {
        private boolean expired = false;

        public CodeAdapter(List<Map<String, String>> codes) {
            super(getActivity(), R.layout.oath_code_view, codes);
        }

        public void setAll(List<Map<String, String>> codes) {
            clear();
            expired = false;
            addAll(codes);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = getActivity().getLayoutInflater();
            Map<String, String> code = getItem(position);

            View view = convertView != null ? convertView : inflater.inflate(R.layout.oath_code_view, null);
            view.setSelected(selectedItem == code);
            ((ViewGroup) view).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

            TextView labelView = (TextView) view.findViewById(R.id.label);
            TextView codeView = (TextView) view.findViewById(R.id.code);
            ImageButton actionButton = (ImageButton) view.findViewById(R.id.actionButton);

            labelView.setText(code.get("label"));
            codeView.setText(code.get("code"));
            codeView.setTextColor(getResources().getColor(expired ? android.R.color.secondary_text_light : android.R.color.primary_text_dark));
            actionButton.setOnClickListener(new CopyToClipboardAction(code));

            return view;
        }
    }

    private class TimeoutAnimation extends Animation {
        public TimeoutAnimation() {
            setDuration(30000);
            setAnimationListener(new AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    adapter.expired = true;
                    adapter.notifyDataSetChanged();
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            super.applyTransformation(interpolatedTime, t);
            timeoutBar.setProgress((int) ((1.0 - interpolatedTime) * 1000));
        }
    }

    private class CopyToClipboardAction implements View.OnClickListener {
        private final Map<String, String> code;

        public CopyToClipboardAction(Map<String,String> code) {
            this.code = code;
        }

        @Override
        public void onClick(View v) {
            Log.d("yubioath", "view: "+v+", id: "+v.getId());
            ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(code.get("label"), code.get("code"));
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getActivity(), R.string.copied, Toast.LENGTH_SHORT).show();
        }
    }
}
