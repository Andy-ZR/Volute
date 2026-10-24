package com.termux.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.terminal.TerminalSession;

import java.util.List;

public class TermuxSessionsListViewController extends ArrayAdapter<TerminalSession> implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    final TermuxActivity mActivity;

    final StyleSpan boldSpan = new StyleSpan(Typeface.BOLD);
    final StyleSpan italicSpan = new StyleSpan(Typeface.ITALIC);

    public TermuxSessionsListViewController(TermuxActivity activity, List<TerminalSession> sessionList) {
        super(activity.getApplicationContext(), R.layout.item_terminal_sessions_list, sessionList);
        this.mActivity = activity;
    }

    @SuppressLint("SetTextI18n")
    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View sessionRowView = convertView;
        if (sessionRowView == null) {
            LayoutInflater inflater = mActivity.getLayoutInflater();
            sessionRowView = inflater.inflate(R.layout.item_terminal_sessions_list, parent, false);
        }

        TextView sessionTitleView = sessionRowView.findViewById(R.id.session_title);

        var sessionAtRow = getItem(position);
        if (sessionAtRow == null) {
            sessionTitleView.setText("null session");
            return sessionRowView;
        }

        String name = sessionAtRow.mSessionName;
        String sessionTitle = sessionAtRow.getTitle();

        String numberPart = "[" + (position + 1) + "] ";
        String sessionNamePart = (TextUtils.isEmpty(name) ? "" : name);
        String sessionTitlePart = (TextUtils.isEmpty(sessionTitle) ? "" : ((sessionNamePart.isEmpty() ? "" : "\n") + sessionTitle));

        String fullSessionTitle = numberPart + sessionNamePart + sessionTitlePart;
        SpannableString fullSessionTitleStyled = new SpannableString(fullSessionTitle);
        fullSessionTitleStyled.setSpan(boldSpan, 0, numberPart.length() + sessionNamePart.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        fullSessionTitleStyled.setSpan(italicSpan, numberPart.length() + sessionNamePart.length(), fullSessionTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        sessionTitleView.setText(fullSessionTitleStyled);

        boolean sessionRunning = sessionAtRow.isRunning();

        if (sessionRunning) {
            sessionTitleView.setPaintFlags(sessionTitleView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            sessionTitleView.setPaintFlags(sessionTitleView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        int defaultColor = Color.BLACK;
        int color = sessionRunning || sessionAtRow.getExitStatus() == 0 ? defaultColor : Color.RED;
        sessionTitleView.setTextColor(color);
        return sessionRowView;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        var clickedSession = getItem(position);
        mActivity.getTermuxTerminalSessionClient().setCurrentSession(clickedSession);
        mActivity.getDrawer().closeDrawers();
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        var selectedSession = getItem(position);
        // 长按给出「重命名 / 关闭会话」——上游只有改名，没有关会话入口（2026-10-24 补）
        new AlertDialog.Builder(mActivity)
            .setItems(new CharSequence[]{
                mActivity.getString(R.string.action_rename_session),
                mActivity.getString(R.string.action_close_session),
            }, (dialog, which) -> {
                if (which == 0) {
                    mActivity.getTermuxTerminalSessionClient().renameSession(selectedSession);
                } else {
                    closeSession(selectedSession);
                }
            })
            .show();
        return true;
    }

    /** 进程还在跑 → 二次确认（与长按终端里的「结束进程」同语义）。 */
    private void closeSession(TerminalSession session) {
        if (session.isRunning()) {
            new AlertDialog.Builder(mActivity)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.dialog_close_session_title)
                .setMessage(R.string.dialog_close_session_message)
                .setPositiveButton(R.string.action_close_session, (dialog, id) -> doCloseSession(session, true))
                .setNegativeButton(R.string.cancel, null)
                .show();
        } else {
            doCloseSession(session, false);
        }
    }

    /** 真的把 tab 关掉：最后一个会话先补一个新的，避免直接把 Activity 带走。 */
    private void doCloseSession(TerminalSession session, boolean wasRunning) {
        var service = mActivity.getTermuxService();
        if (service == null) return;
        var client = mActivity.getTermuxTerminalSessionClient();
        if (service.getTermuxSessionsSize() <= 1) {
            client.addNewSession(false, null, null, null);
        }
        if (wasRunning) {
            session.finishIfRunning();          // SIGKILL，进程退出是异步的
            mActivity.getTerminalView().postDelayed(() -> client.removeFinishedSession(session), 300);
        } else {
            client.removeFinishedSession(session);
        }
    }

}
