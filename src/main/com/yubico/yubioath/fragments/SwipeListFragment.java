package com.yubico.yubioath.fragments;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.ListFragment;
import android.os.Bundle;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.yubico.yubioath.MainActivity;
import com.yubico.yubioath.R;
import com.yubico.yubioath.model.KeyManager;
import com.yubico.yubioath.model.YubiKeyNeo;

import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 9/4/13
 * Time: 3:15 PM
 * To change this template use File | Settings | File Templates.
 */
public class SwipeListFragment extends Fragment implements MainActivity.OnYubiKeyNeoListener {
    private ListCodesFragment fragment;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.swipe_list_fragment, container, false);
        ViewPager pager = (ViewPager) view.findViewById(R.id.pager);
        pager.setAdapter(new ListAdapter(getFragmentManager()));

        return view;
    }

    @Override
    public void onYubiKeyNeo(YubiKeyNeo neo) throws IOException {
        fragment.onYubiKeyNeo(neo);
    }

    @Override
    public void onPasswordMissing(KeyManager keyManager, byte[] id, boolean missing) {
        fragment.onPasswordMissing(keyManager, id, missing);
    }

    private class ListAdapter extends FragmentStatePagerAdapter {
        public ListAdapter(FragmentManager fm) {
            super(fm);
        }
        @Override
        public Fragment getItem(int position) {
            fragment = new ListCodesFragment();
            return fragment;
        }

        @Override
        public int getCount() {
            return 2;
        }
    }
}
