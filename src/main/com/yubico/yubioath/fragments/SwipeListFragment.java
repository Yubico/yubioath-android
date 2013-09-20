package com.yubico.yubioath.fragments;

import android.app.Fragment;
import android.app.FragmentManager;
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
import java.util.ArrayList;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: dain
 * Date: 9/4/13
 * Time: 3:15 PM
 * To change this template use File | Settings | File Templates.
 */
public class SwipeListFragment extends Fragment implements MainActivity.OnYubiKeyNeoListener {
    private ListCodesFragment fragment = new ListCodesFragment();
    private ListCodesFragment emptyFragment1 = new ListCodesFragment();
    private ListCodesFragment emptyFragment2 = new ListCodesFragment();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.swipe_list_fragment, container, false);
        final ViewPager pager = (ViewPager) view.findViewById(R.id.pager);
        pager.setOffscreenPageLimit(0);
        pager.setAdapter(new ListAdapter(getFragmentManager()));
        pager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if(state == 0 && pager.getCurrentItem() != 1) {
                    fragment.showCodes(new ArrayList<Map<String, String>>());
                    pager.setCurrentItem(1, false);
                }

            }
        });

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

    public ListCodesFragment getCurrent() {
        return fragment;
    }

    private class ListAdapter extends FragmentStatePagerAdapter {
        public ListAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch(position) {
                case 0:
                    return emptyFragment1;
                case 1:
                    return fragment;
                default:
                    return emptyFragment2;
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    }
}
