package org.qii.weiciyuan.ui.browser;

import android.app.ActionBar;
import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.qii.weiciyuan.R;
import org.qii.weiciyuan.bean.CommentBean;
import org.qii.weiciyuan.bean.CommentListBean;
import org.qii.weiciyuan.dao.send.CommentNewMsgDao;
import org.qii.weiciyuan.dao.timeline.CommentsTimeLineByIdDao;
import org.qii.weiciyuan.support.error.WeiboException;
import org.qii.weiciyuan.support.utils.AppConfig;
import org.qii.weiciyuan.ui.Abstract.AbstractAppActivity;
import org.qii.weiciyuan.ui.adapter.CommentListAdapter;
import org.qii.weiciyuan.ui.basefragment.AbstractTimeLineFragment;
import org.qii.weiciyuan.ui.send.CommentNewActivity;
import org.qii.weiciyuan.ui.widgets.SendProgressFragment;

import java.util.List;

/**
 * User: qii
 * Date: 12-7-29
 */
public class CommentsByIdTimeLineFragment extends AbstractTimeLineFragment<CommentListBean> {


    protected void clearAndReplaceValue(CommentListBean value) {
        bean.getComments().clear();
        bean.getComments().addAll(value.getComments());
        bean.setTotal_number(value.getTotal_number());
    }

    private EditText et;


    public CommentListBean getList() {
        return bean;
    }

    private String token;
    private String id;

    public CommentsByIdTimeLineFragment(String token, String id) {
        this.token = token;
        this.id = id;
    }

    public CommentsByIdTimeLineFragment() {

    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("bean", bean);
        outState.putString("id", id);
        outState.putString("token", token);
    }

    //restore from activity destroy
    public void load() {
        String sss=token;
        if ((bean == null || bean.getComments().size() == 0) && newTask == null) {
            if (listView != null) {
                refresh();
            }
        }
    }

    private boolean canSend() {

        boolean haveContent = !TextUtils.isEmpty(et.getText().toString());
        boolean haveToken = !TextUtils.isEmpty(token);
        boolean contentNumBelow140 = (et.getText().toString().length() < 140);

        if (haveContent && haveToken && contentNumBelow140) {
            return true;
        } else {
            if (!haveContent && !haveToken) {
                Toast.makeText(getActivity(), getString(R.string.content_cant_be_empty_and_dont_have_account), Toast.LENGTH_SHORT).show();
            } else if (!haveContent) {
                et.setError(getString(R.string.content_cant_be_empty));
            } else if (!haveToken) {
                Toast.makeText(getActivity(), getString(R.string.dont_have_account), Toast.LENGTH_SHORT).show();
            }

            if (!contentNumBelow140) {
                et.setError(getString(R.string.content_words_number_too_many));
            }

        }

        return false;
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        commander = ((AbstractAppActivity) getActivity()).getCommander();
        ((BrowserWeiboMsgActivity)getActivity()).setCommentFragment(this);

        if (savedInstanceState != null && bean.getComments().size() == 0) {
            clearAndReplaceValue((CommentListBean) savedInstanceState.getSerializable("bean"));
            token = savedInstanceState.getString("token");
            id = savedInstanceState.getString("id");
            timeLineAdapter.notifyDataSetChanged();
            refreshLayout(bean);
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bean = new CommentListBean();
        setHasOptionsMenu(true);
        setRetainInstance(true);
    }


    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_comment_listview_layout, container, false);
        empty = (TextView) view.findViewById(R.id.empty);
        progressBar = (ProgressBar) view.findViewById(R.id.progressbar);
        listView = (ListView) view.findViewById(R.id.listView);
        listView.setScrollingCacheEnabled(false);
        headerView = inflater.inflate(R.layout.fragment_listview_header_layout, null);
        listView.addHeaderView(headerView);
        listView.setHeaderDividersEnabled(false);
        footerView = inflater.inflate(R.layout.fragment_listview_footer_layout, null);
        listView.addFooterView(footerView);

        if (bean == null || bean.getComments().size() == 0) {
            footerView.findViewById(R.id.listview_footer).setVisibility(View.GONE);
        }


        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                if (position - 1 < getList().getComments().size() && position - 1 >= 0) {
                    listViewItemClick(parent, view, position - 1, id);
                } else if (position - 1 >= getList().getComments().size()) {
                    listViewFooterViewClick(view);
                }
            }
        });

        et = (EditText) view.findViewById(R.id.content);
        view.findViewById(R.id.send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendComment();
            }
        });
        buildListAdapter();
        return view;
    }

    @Override
    protected void buildListAdapter() {
        timeLineAdapter = new CommentListAdapter(getActivity(), ((AbstractAppActivity) getActivity()).getCommander(), getList().getComments(), listView, false);
        listView.setAdapter(timeLineAdapter);
    }

    private void sendComment() {

        if (canSend()) {
            new SimpleTask().execute();
        }
    }

    class SimpleTask extends AsyncTask<Void, Void, CommentBean> {
        WeiboException e;
        SendProgressFragment progressFragment = new SendProgressFragment();

        @Override
        protected void onPreExecute() {
            progressFragment.onCancel(new DialogInterface() {

                @Override
                public void cancel() {
                    SimpleTask.this.cancel(true);
                }

                @Override
                public void dismiss() {
                    SimpleTask.this.cancel(true);
                }
            });

            progressFragment.show(getFragmentManager(), "");

        }

        @Override
        protected CommentBean doInBackground(Void... params) {
            CommentNewMsgDao dao = new CommentNewMsgDao(token, id, et.getText().toString());
            try {
                return dao.sendNewMsg();
            } catch (WeiboException e) {
                this.e = e;
                cancel(true);
                return null;
            }
        }

        @Override
        protected void onCancelled(CommentBean commentBean) {
            super.onCancelled(commentBean);
            if (this.e != null) {
                Toast.makeText(getActivity(), e.getError(), Toast.LENGTH_SHORT).show();

            }
        }

        @Override
        protected void onPostExecute(CommentBean s) {
            progressFragment.dismissAllowingStateLoss();
            if (s != null) {
                et.setText("");
                refresh();
            } else {
                Toast.makeText(getActivity(), getString(R.string.send_failed), Toast.LENGTH_SHORT).show();
            }
            super.onPostExecute(s);

        }
    }


    protected void listViewItemClick(AdapterView parent, View view, int position, long id) {
        CommentOperatorDialog progressFragment = new CommentOperatorDialog(bean.getComments().get(position));
        progressFragment.show(getFragmentManager(), "");
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.commentsbyidtimelinefragment_menu, menu);
        SearchManager searchManager = (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) menu.findItem(R.id.commentsbyidtimelinefragment_search).getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getActivity().getComponentName()));
        searchView.setIconifiedByDefault(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.commentsbyidtimelinefragment_comment:

                Intent intent = new Intent(getActivity(), CommentNewActivity.class);
                intent.putExtra("token", token);
                intent.putExtra("id", id);
                startActivity(intent);

                break;

            case R.id.commentsbyidtimelinefragment_refresh:

                refresh();

                break;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected CommentListBean getDoInBackgroundNewData() throws WeiboException {
        CommentsTimeLineByIdDao dao = new CommentsTimeLineByIdDao(token, id);

        if (getList().getComments().size() > 0) {
            dao.setSince_id(getList().getComments().get(0).getId());
        }
        CommentListBean result = dao.getGSONMsgList();
        return result;
    }

    @Override
    protected CommentListBean getDoInBackgroundOldData() throws WeiboException {
        CommentsTimeLineByIdDao dao = new CommentsTimeLineByIdDao(token, id);
        if (getList().getComments().size() > 0) {
            dao.setMax_id(getList().getComments().get(getList().getComments().size() - 1).getId());
        }
        CommentListBean result = dao.getGSONMsgList();
        return result;
    }

    @Override
    protected void newMsgOnPostExecute(CommentListBean newValue) {
        if (newValue != null) {
            if (newValue.getComments().size() == 0) {
                Toast.makeText(getActivity(), getString(R.string.no_new_message), Toast.LENGTH_SHORT).show();

            } else {
                Toast.makeText(getActivity(), getString(R.string.total) + newValue.getComments().size() + getString(R.string.new_messages), Toast.LENGTH_SHORT).show();
                if (newValue.getComments().size() < AppConfig.DEFAULT_MSG_NUMBERS) {
                    newValue.getComments().addAll(getList().getComments());
                }

                clearAndReplaceValue(newValue);
                timeLineAdapter.notifyDataSetChanged();
                listView.setSelectionAfterHeaderView();
                headerView.findViewById(R.id.header_progress).clearAnimation();

            }
        }

        invlidateTabText();
    }

    @Override
    protected void oldMsgOnPostExecute(CommentListBean newValue) {
        if (newValue != null && newValue.getComments().size() > 1) {
            List<CommentBean> list = newValue.getComments();
            getList().getComments().addAll(list.subList(1, list.size() - 1));
            ((TextView) footerView.findViewById(R.id.listview_footer)).setText(getString(R.string.more));

        } else {
            ((TextView) footerView.findViewById(R.id.listview_footer)).setVisibility(View.GONE);

        }

        timeLineAdapter.notifyDataSetChanged();
        invlidateTabText();
    }


    private void invlidateTabText() {
        Activity activity = getActivity();
        if (activity != null) {
            ActionBar.Tab tab = activity.getActionBar().getTabAt(1);
            String num = getString(R.string.comments) + "(" + bean.getTotal_number() + ")";
            tab.setText(num);

        }
    }

}
