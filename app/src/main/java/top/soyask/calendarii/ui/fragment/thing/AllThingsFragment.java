package top.soyask.calendarii.ui.fragment.thing;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.recyclerview.widget.RecyclerView;
import top.soyask.calendarii.R;
import top.soyask.calendarii.database.dao.ThingDao;
import top.soyask.calendarii.entity.Thing;
import top.soyask.calendarii.ui.adapter.thing.ThingAdapter;
import top.soyask.calendarii.ui.fragment.base.BaseListFragment;


public class AllThingsFragment extends BaseListFragment
        implements ThingAdapter.ThingActionCallback, View.OnClickListener, DeleteFragment.OnDeleteConfirmListener {

    private static final int WAIT = 0x0;
    private static final int CANCEL = 0x1;
    private static final int DELETE_ALL = 0x2;
    private static final int DELETE_COMP = 0x3;
    private ThingDao mThingDao;
    private List<Thing> mThings = new ArrayList<>();
    private List<Thing> mDoneThings = new ArrayList<>();
    private ProgressDialog mProgressDialog;
    private Set<Thing> mDeletedThings = new HashSet<>();

    private Handler mHandler = new ThingHandler(this);

    private Comparator<Thing> mComparator = (o1, o2) -> {
        long time1 = o1.getTargetTime();
        long time2 = o2.getTargetTime();
        return -Long.compare(time1, time2);
    };
    private int currentPage = 0;
    private int mCount;

    public static AllThingsFragment newInstance() {
        AllThingsFragment fragment = new AllThingsFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    protected void init() {
        mThingDao = ThingDao.getInstance(mHostActivity);
        mCount = mThingDao.count();
    }

    @Override
    protected boolean canLoadMore() {
        return mAdapter.getItemCount() < mCount;
    }

    @Override
    protected void loadData() {
        currentPage++;
        List<Thing> things = mThingDao.list(currentPage);
        Collections.sort(mThings, mComparator);
        for (Thing thing : mThings) {
            if (thing.isDone()) {
                mDoneThings.add(thing);
            }
        }
        mThings.addAll(things);
    }

    @Override
    protected RecyclerView.Adapter getAdapter() {
        loadData();
        return new ThingAdapter(mThings, this);
    }


    @Override
    public void onEditClick(final int position, Thing thing) {
        EditThingFragment editThingFragment = EditThingFragment.newInstance(null, thing);
        editThingFragment.setOnUpdateListener(() -> mAdapter.notifyItemChanged(position));
        editThingFragment.setOnDeleteListener(() -> {
            mThings.remove(position);
            mAdapter.notifyItemRemoved(position);
        });
        replaceFragment(editThingFragment);
    }

    @Override
    public void onDeleteClick(final int position, final Thing thing) {
        mDeletedThings.add(thing);
        mThings.remove(thing);
        mAdapter.notifyItemRemoved(position);
        mAdapter.notifyItemRangeChanged(position, mThings.size());
        showSnackbar("删除成功^_~", "撤销", v -> {
            mDeletedThings.remove(thing);
            mThings.add(thing);
            Collections.sort(mThings, mComparator);
            mAdapter.notifyItemInserted(position);
            mAdapter.notifyItemRangeChanged(position, mThings.size());
            if (position == 0) {
                scrollToTop();
            }
        });
    }

    @Override
    public void onDone(int position, Thing thing) {
        thing.setDone(true);
        mThingDao.update(thing);
        mAdapter.notifyItemChanged(position);
        mDoneThings.add(thing);
    }

    @Override
    public void onDoneCancel(int position, Thing thing) {
        thing.setDone(false);
        mThingDao.update(thing);
        mAdapter.notifyItemChanged(position);
        mDoneThings.remove(thing);
    }

    @Override
    public void onShare(Thing event) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        String text = String.format("%s", event.getDetail());
        intent.putExtra(Intent.EXTRA_SUBJECT, ".");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        intent.setType("text/plain");
        startActivity(intent);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.ib_delete_all:
                DeleteFragment deleteFragment = DeleteFragment.newInstance();
                deleteFragment.setOnDeleteConfirmListener(this);
                getFragmentManager()
                        .beginTransaction()
                        .add(R.id.main, deleteFragment)
                        .addToBackStack(deleteFragment.getClass().getSimpleName())
                        .commit();
                break;
            default:
                removeFragment(this);
                break;
        }
    }

    public void showDeleteAllDialog(){
        DeleteFragment deleteFragment = DeleteFragment.newInstance();
        deleteFragment.setOnDeleteConfirmListener(this);
        getFragmentManager()
                .beginTransaction()
                .add(R.id.main, deleteFragment)
                .addToBackStack(deleteFragment.getClass().getSimpleName())
                .commit();
    }

    @Override
    public void onConfirm(int type) {
        if (type == DeleteFragment.ALL) {
            mHandler.sendEmptyMessage(DELETE_ALL);
        } else {
            mHandler.sendEmptyMessage(DELETE_COMP);
        }
    }

    private void deleteComplete() {
        final List<Thing> temp = new ArrayList<>(mDoneThings);
        mDeletedThings.addAll(temp);
        mThings.removeAll(mDoneThings);
        mAdapter.notifyDataSetChanged();

        mDoneThings.clear();
        showSnackbar("删除了划掉的事件。", "我要恢复", v -> {
            mHandler.sendEmptyMessage(WAIT);
            mDeletedThings.removeAll(temp);
            mThings.addAll(temp);
            mDoneThings.addAll(temp);
            Collections.sort(mThings, mComparator);
            mAdapter.notifyDataSetChanged();
            mHandler.sendEmptyMessage(CANCEL);
        });
    }

    private void deleteAll() {
        final List<Thing> temp = new ArrayList<>(mThings);
        mDeletedThings.addAll(temp);
        mThings.clear();
        mAdapter.notifyDataSetChanged();
        showSnackbar("删除了全部的事件。", "我要恢复", v -> {
            mHandler.sendEmptyMessage(WAIT);
            mThings.addAll(temp);
            mDeletedThings.removeAll(temp);
            Collections.sort(mThings, mComparator);
            mAdapter.notifyDataSetChanged();
            mHandler.sendEmptyMessage(CANCEL);
        });
    }

    public void doDelete(){
        for (Thing thing : mDeletedThings) {
            mThingDao.delete(thing);
        }
    }

    private static class ThingHandler extends Handler {
        private final WeakReference<AllThingsFragment> mFragment;

        private ThingHandler(AllThingsFragment fragment) {
            mFragment = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            AllThingsFragment fragment = mFragment.get();
            if (fragment == null) {
                return;
            }
            switch (msg.what) {
                case WAIT:
                    fragment.mProgressDialog = ProgressDialog.show(fragment.mHostActivity, null, "正在恢复，请稍等...");
                    break;
                case CANCEL:
                    if (fragment.mProgressDialog != null) {
                        fragment.mProgressDialog.dismiss();
                        fragment.mProgressDialog = null;
                    }
                    break;
                case DELETE_ALL:
                    fragment.deleteAll();
                    break;
                case DELETE_COMP:
                    fragment.deleteComplete();
                    break;
            }
        }
    }
}
