package com.tassadar.multirommgr;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import com.fima.cardsui.objects.Card;

import java.util.ArrayList;

public class UbuntuCard extends Card implements AdapterView.OnItemSelectedListener, View.OnClickListener {

    private static final int MIN_FREE_SPACE = 2300;

    public UbuntuCard(StartInstallListener listener, Manifest man, MultiROM multirom, Recovery recovery) {
        m_listener = listener;
        m_manifest = man;
        m_multirom = multirom;
        m_recovery = recovery;
    }

    @Override
    public View getCardContent(Context context) {
        m_view = LayoutInflater.from(context).inflate(R.layout.ubuntu_card, null);

        Spinner s = (Spinner) m_view.findViewById(R.id.channel);
        s.setOnItemSelectedListener(this);

        Button b = (Button) m_view.findViewById(R.id.install_btn);
        b.setOnClickListener(this);
        b = (Button) m_view.findViewById(R.id.back_btn);
        b.setOnClickListener(this);

        ImageButton ib = (ImageButton)m_view.findViewById(R.id.info_btn);
        ib.setOnClickListener(this);

        boolean error = false;
        TextView t = (TextView)m_view.findViewById(R.id.error_text);
        if(!m_manifest.hasUbuntuReqMultiROM(m_multirom)) {
            error = true;

            String f = t.getResources().getString(R.string.ubuntu_req_multirom);
            t.append(String.format(f, m_manifest.getUbuntuReqMultiROM()) + "\n");
        }
        if(!m_manifest.hasUbuntuReqRecovery(m_recovery)) {
            error = true;

            String f = t.getResources().getString(R.string.ubuntu_req_recovery);
            String ver = Recovery.DISPLAY_FMT.format(m_manifest.getUbuntuReqRecovery());
            t.append(String.format(f, ver) + "\n");
        }

        if(!error) {
            UbuntuManifestAsyncTask.instance().setCard(this);
            UbuntuManifestAsyncTask.instance().executeTask(StatusAsyncTask.instance().getDevice(), m_multirom);
        } else {
            t.setVisibility(View.VISIBLE);
            m_view.findViewById(R.id.progress_bar).setVisibility(View.GONE);
        }
        return m_view;
    }

    public void applyResult(UbuntuManifestAsyncTask.Result res) {
        if(m_view == null)
            return;

        View v = m_view.findViewById(R.id.progress_bar);
        v.setVisibility(View.GONE);

        if(res.code == UbuntuManifestAsyncTask.RES_CHANNELS_FAIL) {
            TextView t = (TextView)m_view.findViewById(R.id.error_text);
            t.setVisibility(View.VISIBLE);
            t.setText(R.string.ubuntu_man_failed);
            return;
        }

        if(res.freeSpace != -1 && res.freeSpace < MIN_FREE_SPACE) {
            TextView t = (TextView)m_view.findViewById(R.id.error_text);
            t.setText(Utils.getString(R.string.ubuntu_free_space, res.freeSpace, MIN_FREE_SPACE));
            t.setVisibility(View.VISIBLE);
        }

        final int[] views = { R.id.channel_layout, R.id.version_layout, /* R.id.destination_layout,*/ R.id.install_btn };
        for(int i = 0; i < views.length; ++i) {
            v = m_view.findViewById(views[i]);
            v.setVisibility(View.VISIBLE);
        }

        Spinner s = (Spinner) m_view.findViewById(R.id.channel);
        m_channelAdapter = new UbuntuChannelsAdapter(m_view.getContext(), res.manifest.getChannels());
        s.setAdapter(m_channelAdapter);

        // TODO: support installation to USB drive
        /*m_destAdapter = new ArrayAdapter<String>(m_view.getContext(),
                    android.R.layout.simple_spinner_dropdown_item);
        m_destAdapter.add(m_view.getResources().getString(R.string.internal_memory));
        m_destAdapter.addAll(res.destinations);

        s = (Spinner) m_view.findViewById(R.id.destination);
        s.setAdapter(m_destAdapter);*/
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        m_versionAdapter = new ArrayAdapter<Integer>(m_view.getContext(),
                android.R.layout.simple_spinner_dropdown_item);

        UbuntuChannel c = m_channelAdapter.getItem(position);
        m_versionAdapter.addAll(c.getImageVersions());

        Spinner s = (Spinner) m_view.findViewById(R.id.version);
        s.setAdapter(m_versionAdapter);
        s.setSelection(m_versionAdapter.getCount()-1);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        Spinner s = (Spinner) m_view.findViewById(R.id.version);
        s.setAdapter(null);
    }

    @Override
    public void onClick(View view) {
        switch(view.getId()) {
            case R.id.install_btn:
            {
                Bundle bundle = new Bundle();
                bundle.putString("installation_type", "ubuntu");

                UbuntuInstallInfo info = new UbuntuInstallInfo();

                Spinner s = (Spinner) m_view.findViewById(R.id.channel);
                UbuntuChannel chan = (UbuntuChannel)s.getSelectedItem();

                s = (Spinner) m_view.findViewById(R.id.version);
                Integer version = (Integer)s.getSelectedItem();

                chan.fillInstallFilesForVer(info.installFiles, version);
                info.channelName = chan.getRawName();

                UbuntuManifestAsyncTask.instance().putInstallInfo(info);

                m_listener.startActivity(bundle, MainActivity.ACT_INSTALL_UBUNTU, InstallActivity.class);
                break;
            }
            case R.id.info_btn:
            {
                rotateCard(true);
                break;
            }
            case R.id.back_btn:
            {
                rotateCard(false);
                break;
            }
        }
    }

    private void rotateCard(boolean frontToback) {
        AnimatorSet set = (AnimatorSet) AnimatorInflater.loadAnimator(m_view.getContext(),
                R.anim.card_flip);
        set.setTarget(mCardLayout);

        View front = m_view.findViewById(R.id.ubuntu_card_front);
        View back = m_view.findViewById(R.id.ubuntu_card_back);

        ArrayList<Animator> s = set.getChildAnimations();
        for(int i = 0; i < s.size(); ++i) {
            Animator a = s.get(i);
            if(a.getDuration() == 1) {
                a.addListener(new FlipAnimationListener(front, back, frontToback));
                break;
            }
        }
        set.start();
    }

    private static class FlipAnimationListener extends AnimatorListenerAdapter {
        private View m_front, m_back;
        private boolean m_frontToBack;

        public FlipAnimationListener(View front, View back, boolean frontToBack) {
            m_front = front;
            m_back = back;
            m_frontToBack = frontToBack;
        }

        @Override
        public void onAnimationStart(Animator animation) {
            super.onAnimationStart(animation);
            m_front.setVisibility(m_frontToBack ? View.GONE : View.VISIBLE);
            m_back.setVisibility(m_frontToBack ? View.VISIBLE : View.GONE);
        }
    }

    private View m_view;
    private UbuntuChannelsAdapter m_channelAdapter;
    private ArrayAdapter<Integer> m_versionAdapter;
    private ArrayAdapter<String> m_destAdapter;
    private StartInstallListener m_listener;
    private Manifest m_manifest;
    private MultiROM m_multirom;
    private Recovery m_recovery;
}
