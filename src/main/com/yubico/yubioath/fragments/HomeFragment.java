package com.yubico.yubioath.fragments;

import android.app.*;
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
public class HomeFragment extends ListFragment implements MainActivity.OnYubiKeyNeoListener, ActionMode.Callback {
    private static final String[] FROM = new String[]{"label", "code"};
    private static final int[] TO = new int[]{R.id.label, R.id.code};

    private final TimeoutAnimation timeoutAnimation = new TimeoutAnimation();
    private final List<Map<String, String>> codes = new ArrayList<Map<String, String>>();
    private SimpleAdapter adapter;
    private ProgressBar timeoutBar;
    private ActionMode actionMode;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.home_fragment, container, false);
        timeoutBar = (ProgressBar) view.findViewById(R.id.timeRemaining);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        adapter = new SimpleAdapter(getActivity(), codes, R.layout.totp_code_view, FROM, TO);
        setListAdapter(adapter);

        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Log.d("yubioath", "pos: " + position + ", id: " + id + ", view: " + view);
                Log.d("yubioath", "enabled: "+adapter.isEnabled(position)+ " all: "+adapter.areAllItemsEnabled());
                if(actionMode == null) {
                    //actionMode = getActivity().startActionMode(HomeFragment.this);
                    setSelection(position);
                    Log.d("yubioath", "!selected id: "+getSelectedItemId()+", pos: "+getSelectedItemPosition());
                    //getListView().setSelection(position);
                }
                return true;
            }
        });
    }

    @Override
    public void onYubiKeyNeo(YubiKeyNeo neo) throws IOException {
        long timestamp = System.currentTimeMillis() / 1000 / 30;
        showCodes(neo.getCodes(timestamp));
    }

    public void showCodes(List<Map<String, String>> newCodes) {
        codes.clear();
        codes.addAll(newCodes);
        adapter.notifyDataSetChanged();

        if (codes.size() == 0) {
            timeoutBar.setVisibility(View.GONE);
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
        Log.d("yubioath", "actionItemClicked: "+ item.getItemId());
        switch (item.getItemId()) {
            case R.id.copy_to_clipboard:
                Log.d("yubioath", "checked: "+getListView().getCheckedItemPosition());
                Log.d("yubioath", "pos: "+getSelectedItemPosition()+", id: "+getSelectedItemId());
                Log.d("yubioath", "COPY: "+codes.get(getSelectedItemPosition()).get("code"));
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
    }

    private class TimeoutAnimation extends Animation {
        public TimeoutAnimation() {
            setDuration(30000);
            setAnimationListener(new AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    timeoutBar.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    timeoutBar.setVisibility(View.GONE);
                    codes.clear();
                    adapter.notifyDataSetChanged();
                    if(actionMode != null) {
                        actionMode.finish();
                    }
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            super.applyTransformation(interpolatedTime, t);
            timeoutBar.setProgress((int)((1.0-interpolatedTime) * 1000));
        }
    }
}
