package com.jiguang.jbox.main;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toolbar;

import com.jiguang.jbox.R;
import com.jiguang.jbox.data.Channel;
import com.jiguang.jbox.data.Developer;
import com.jiguang.jbox.data.Message;
import com.jiguang.jbox.data.source.ChannelDataSource;
import com.jiguang.jbox.data.source.ChannelRepository;
import com.jiguang.jbox.data.source.MessageDataSource;
import com.jiguang.jbox.data.source.MessageRepository;
import com.jiguang.jbox.data.source.local.ChannelLocalDataSource;
import com.jiguang.jbox.data.source.local.MessagesLocalDataSource;
import com.jiguang.jbox.util.ViewHolder;

import java.util.ArrayList;
import java.util.List;

import cn.jpush.android.api.JPushInterface;

public class MainActivity extends Activity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    private Toolbar mTopBar;

    private ListView mMsgListView;

    private MessageListAdapter mAdapter;

    private MessageRepository mMessagesRepository;

    private List<Developer> mDevList;

    private List<Channel> mChannelList;

    private NavigationDrawerFragment mDrawerFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDrawerFragment = (NavigationDrawerFragment) getFragmentManager().findFragmentById(
                R.id.navigation_drawer);

        MessagesLocalDataSource msgLocalDataSource = MessagesLocalDataSource.getInstance();
        mMessagesRepository = MessageRepository.getInstance(msgLocalDataSource);

        mTopBar = (Toolbar) findViewById(R.id.toolbar);
        mTopBar.setNavigationIcon(R.drawable.ic_navigation);

        final DrawerLayout drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mTopBar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        mMsgListView = (ListView) findViewById(R.id.lv_msg);
        mAdapter = new MessageListAdapter(new ArrayList<Message>(0));
        mMsgListView.setAdapter(mAdapter);

        View emptyView = findViewById(R.id.tv_hint);
        mMsgListView.setEmptyView(emptyView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        JPushInterface.onResume(this);

        // init data.
        ChannelLocalDataSource channelLocalDataSource = ChannelLocalDataSource.getInstance();
        ChannelRepository channelRepository = ChannelRepository.getInstance(channelLocalDataSource);
        channelRepository.getChannels(true, new ChannelDataSource.LoadChannelsCallback() {
            @Override
            public void onChannelsLoaded(List<Channel> channels) {
                mChannelList = channels;
                mDrawerFragment.initData(channels);
//                if (channels != null && !channels.isEmpty()) {
//                    // 初始化侧边栏 Channel 列表数据。
//                    mDrawerFragment.initData(channels);
//                }
            }

            @Override
            public void onDataNotAvailable() {
                mChannelList = new ArrayList<>();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        JPushInterface.onPause(this);
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        if (mChannelList != null) {
            Channel channel = mChannelList.get(position);
            mTopBar.setTitle(channel.getName());
            // 加载指定 Channel 的 message 数据。
            mMessagesRepository.getMessages(channel.getDevKey(), channel.getName(),
                    new MessageDataSource.LoadMessagesCallback() {
                        @Override
                        public void onMessagesLoaded(List<Message> messages) {
                            mAdapter.replaceData(messages);
                        }

                        @Override
                        public void onDataNotAvailable() {

                        }
                    });
        }
    }


    private static class MessageListAdapter extends BaseAdapter {

        private List<Message> mMessages;

        MessageListAdapter(List<Message> list) {
            mMessages = list;
        }

        void replaceData(List<Message> list) {
            if (list != null && !list.isEmpty()) {
                mMessages = list;
                notifyDataSetChanged();
            }
        }

        public void addMessage(Message msg) {
            if (mMessages != null) {
                mMessages.add(0, msg);
                notifyDataSetChanged();
            }
        }

        @Override
        public int getCount() {
            return mMessages.size();
        }

        @Override
        public Object getItem(int position) {
            return mMessages.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = LayoutInflater.from(parent.getContext());
                convertView = inflater.inflate(R.layout.item_msg, parent, false);
            }

            final Message msg = mMessages.get(position);

            ImageView ivIcon = ViewHolder.get(convertView, R.id.iv_icon);

            TextView tvTitle = ViewHolder.get(convertView, R.id.tv_title);
            tvTitle.setText(msg.getTitle());

            TextView tvContent = ViewHolder.get(convertView, R.id.tv_content);
            tvContent.setText(msg.getContent());

            TextView tvTime = ViewHolder.get(convertView, R.id.tv_time);

            long timeMillis = Long.parseLong(msg.getTime());
            String formatTime = DateUtils.formatDateTime(parent.getContext(), timeMillis,
                    DateUtils.FORMAT_SHOW_TIME);
            tvTime.setText(formatTime);

            return convertView;
        }
    }


    public class MyReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(JPushInterface.ACTION_MESSAGE_RECEIVED)) {
                Bundle bundle = intent.getBundleExtra(JPushInterface.EXTRA_MESSAGE);

                String title = bundle.getString("title");
                String content = bundle.getString("content");
                String devKey = bundle.getString("dev_key");
                String channel = bundle.getString("channel");

                Message msg = new Message(title, content);
                msg.setChannelName(channel);
                msg.setDevKey(devKey);
                msg.setTime(String.valueOf(System.currentTimeMillis()));

                // 保存 msg 到本地，并刷新页面数据。
                MessagesLocalDataSource localDataSource = MessagesLocalDataSource.getInstance();
                MessageRepository repository = MessageRepository.getInstance(localDataSource);
                repository.saveMessage(msg);

                // 提醒 MainActivity 界面更新。
                mAdapter.addMessage(msg);
            }
        }
    }

}
